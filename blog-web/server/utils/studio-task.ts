/**
 * /studio 一键配图流水线：分析 → 配图 → 转存 → 建文 → 发布。
 * Nitro 单实例内存任务表，前端轮询 /api/studio/status/:id 取进度。
 */
import { analyzeBlocks, downloadImage, generateAiImage, searchPexels, type ImagePoint } from "./ai";
import {
  getHeadContent,
  publishMarkdownPost,
  publishPost,
  resolveCategoryName,
  updateContentToHtml,
  updatePostSpec,
  uploadAttachment,
} from "./halo";

export interface MdBlock {
  no: number;
  text: string;
  /** fenced code / 表格块不允许插图 */
  illustratable: boolean;
}

export interface StudioTask {
  id: string;
  status: "running" | "done" | "error";
  step: string;
  percent: number;
  message: string;
  title: string;
  slug: string;
  categorySlug: string;
  points: ImagePoint[];
  coverUrl?: string;
  postName?: string;
  permalink?: string;
  /** markdown/publish 返回 updated 时提示 slug 冲突覆盖 */
  warnings: string[];
  error?: string;
  createdAt: number;
}

export interface ImportOptions {
  markdown: string;
  title?: string;
  slug?: string;
  categorySlug?: string;
  imageCount?: number;
  style?: string;
}

// 单实例内存任务表；按创建时间排序淘汰，最多保留 20 个
const tasks = new Map<string, StudioTask>();
const MAX_TASKS = 20;

export function getTask(id: string): StudioTask | undefined {
  return tasks.get(id);
}

function createTask(options: ImportOptions): StudioTask {
  const id = `t${Date.now()}${Math.random().toString(36).slice(2, 6)}`;
  const task: StudioTask = {
    id,
    status: "running",
    step: "queued",
    percent: 0,
    message: "排队中",
    title: options.title || "",
    slug: options.slug || "",
    categorySlug: options.categorySlug || "",
    points: [],
    warnings: [],
    createdAt: Date.now(),
  };
  tasks.set(id, task);
  if (tasks.size > MAX_TASKS) {
    const oldest = [...tasks.values()].sort((a, b) => a.createdAt - b.createdAt)[0];
    if (oldest) tasks.delete(oldest.id);
  }
  return task;
}

function progress(task: StudioTask, step: string, percent: number, message: string) {
  task.step = step;
  task.percent = percent;
  task.message = message;
}

/**
 * Markdown 分块：fenced code 块整体成块且不可插图；其余按空行分块。
 * 供 AI 以块序号锚定插图位置。
 */
export function splitBlocks(markdown: string): MdBlock[] {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const blocks: MdBlock[] = [];
  let current: string[] = [];
  let inCode = false;
  const flush = (illustratable: boolean) => {
    const text = current.join("\n").trim();
    if (text) blocks.push({ no: blocks.length + 1, text, illustratable });
    current = [];
  };
  for (const line of lines) {
    if (line.trimStart().startsWith("```")) {
      // 围栏开合：先把围栏前内容按普通块收掉
      if (!inCode) {
        flush(true);
        current = [line];
        inCode = true;
      } else {
        current.push(line);
        flush(false);
        inCode = false;
      }
      continue;
    }
    if (inCode) {
      current.push(line);
      continue;
    }
    if (line.trim() === "") {
      flush(true);
    } else {
      current.push(line);
    }
  }
  flush(!inCode);
  return blocks;
}

/** 默认 slug：标题转小写连字符（保留中文，与站内既有 slug 风格一致） */
function fallbackSlug(title: string): string {
  const base = sanitizeSlug(title);
  return base || `post-${Date.now()}`;
}

/** slug 合法化：剔除 Halo 不接受的字符 */
function sanitizeSlug(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[\\/:*?"<>|'#%&{}$!@+=[\];,.^~`\s]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/**
 * 剥离 md 自带 front-matter（控制台上传的文件常带），避免被当正文渲染成水平线；
 * title/slug 采纳为默认值（显式传入的 options 优先）。
 */
function stripFrontMatter(markdown: string): {
  attrs: { title?: string; slug?: string };
  body: string;
} {
  const m = markdown.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!m) return { attrs: {}, body: markdown };
  const attrs: { title?: string; slug?: string } = {};
  const lines = m[1].split("\n");
  let inCategories = false;
  for (const line of lines) {
    if (/^categories\s*:/.test(line)) {
      inCategories = true;
      continue;
    }
    if (inCategories) {
      if (/^\s+-/.test(line) || line.trim() === "") continue;
      inCategories = false;
    }
    const kv = line.match(/^(title|slug)\s*:\s*(.+)$/);
    if (kv) {
      const value = kv[2].trim().replace(/^["']|["']$/g, "");
      if (value) attrs[kv[1] as "title" | "slug"] = value;
    }
  }
  return { attrs, body: markdown.slice(m[0].length) };
}

function extractTitle(markdown: string): string {
  const m = markdown.match(/^#\s+(.+)$/m);
  return (m?.[1] || "未命名文章").trim().slice(0, 120);
}

/** 组装插图后的 markdown：cover 不入正文，content 按块锚点插入 */
function composeMarkdown(markdown: string, blocks: MdBlock[], points: ImagePoint[]): string {
  const insertByBlock = new Map<number, ImagePoint[]>();
  for (const p of points) {
    if (p.type !== "content" || !p.imageUrl) continue;
    const list = insertByBlock.get(p.blockNo) || [];
    list.push(p);
    insertByBlock.set(p.blockNo, list);
  }
  const imgLine = (p: ImagePoint) => {
    const alt = p.caption.replace(/[\]"[\n\r]/g, "");
    return `![${alt}](${p.imageUrl})`;
  };

  // 与 splitBlocks 相同的状态机遍历，保证块序号对齐；
  // 块结束时按 before/after 插入插图，空行与代码块原样保留
  const originalLines = markdown.replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let current: string[] = [];
  let blockIdx = 0;
  let inCode = false;
  const flush = (illustratable: boolean) => {
    const text = current.join("\n").trim();
    if (!text) {
      out.push(...current);
      current = [];
      return;
    }
    const block = blocks[blockIdx];
    blockIdx += 1;
    const pts = block && illustratable ? insertByBlock.get(block.no) || [] : [];
    for (const p of pts.filter((x) => x.position === "before")) out.push(imgLine(p));
    out.push(...current);
    for (const p of pts.filter((x) => x.position !== "before")) out.push("", imgLine(p));
    current = [];
  };
  for (const line of originalLines) {
    if (line.trimStart().startsWith("```")) {
      if (!inCode) {
        flush(true);
        current = [line];
        inCode = true;
      } else {
        current.push(line);
        flush(false);
        inCode = false;
      }
      continue;
    }
    if (inCode) {
      current.push(line);
      continue;
    }
    if (line.trim() === "") {
      flush(true);
      out.push(line);
    } else {
      current.push(line);
    }
  }
  flush(!inCode);
  return out.join("\n");
}

/** 执行完整流水线（fire-and-forget，调用方不 await） */
export async function runPipeline(taskId: string, options: ImportOptions): Promise<void> {
  const task = getTask(taskId);
  if (!task) return;
  try {
    // 0. 预处理（剥离自带 front-matter，title/slug 作为默认值）
    const { attrs: fmAttrs, body: fmBody } = stripFrontMatter(
      options.markdown.replace(/\r\n/g, "\n"),
    );
    const markdown = fmBody.trim();
    const blocks = splitBlocks(markdown);
    if (blocks.length === 0) throw new Error("文章内容为空");
    const title = (options.title || fmAttrs.title || extractTitle(markdown)).trim();
    const slug =
      sanitizeSlug(options.slug || fmAttrs.slug || fallbackSlug(title)).slice(0, 80) ||
      fallbackSlug(title);
    const categorySlug = options.categorySlug || "default";
    const imageCount = Math.min(10, Math.max(1, options.imageCount || 3));
    const style = options.style || "简约通用";
    task.title = title;
    task.slug = slug;
    task.categorySlug = categorySlug;

    // 1. AI 分析点位（超长文均匀采样块，保证点位分布覆盖全文）
    progress(task, "analyze", 10, "AI 正在分析文章配图点位…");
    const ANALYSIS_BUDGET = 24_000;
    const allNumbered = blocks.map((b) => `[${b.no}] ${b.text.slice(0, 1500)}`);
    let numbered = allNumbered.join("\n\n");
    if (numbered.length > ANALYSIS_BUDGET) {
      const avgBlock = numbered.length / blocks.length;
      const keep = Math.max(4, Math.floor(ANALYSIS_BUDGET / avgBlock));
      const step = blocks.length / keep;
      const picked: string[] = [];
      for (let i = 0; i < keep; i++) {
        picked.push(allNumbered[Math.min(blocks.length - 1, Math.floor(i * step))]);
      }
      picked[picked.length - 1] = allNumbered[blocks.length - 1];
      numbered =
        picked.join("\n\n") +
        `\n\n（注：全文共 ${blocks.length} 个文本块，以上为均匀采样结果，blockNo 仍按原文块号。）`;
    }
    const points = await analyzeBlocks(numbered, { imageCount, style, blockCount: blocks.length });
    task.points = points;

    // 2. 逐点配图（photo 优先图库、illustration 优先 AI，互为兜底）
    progress(task, "illustrate", 25, "正在为各点位获取配图…");
    for (const [idx, point] of points.entries()) {
      const tryPexels = async () => {
        const url = await searchPexels(point.keywords || point.scene);
        if (url) {
          point.imageUrl = url;
          point.source = "pexels";
        }
      };
      const tryAi = async () => {
        const url = await generateAiImage(point.prompt || point.scene);
        point.imageUrl = url;
        point.source = "ai";
      };
      try {
        if (point.kind === "photo") {
          await tryPexels().catch(() => {});
          if (!point.imageUrl) await tryAi();
        } else {
          await tryAi().catch(() => {});
          if (!point.imageUrl) await tryPexels();
        }
        if (!point.imageUrl) throw new Error("图库与 AI 均未返回结果");
        point.status = "done";
      } catch (e) {
        point.status = "failed";
        point.error = e instanceof Error ? e.message : String(e);
      }
      progress(
        task,
        "illustrate",
        25 + Math.round(((idx + 1) / points.length) * 25),
        `配图进度 ${idx + 1}/${points.length}`,
      );
    }
    const okPoints = points.filter((p) => p.status === "done" && p.imageUrl);
    if (okPoints.length === 0) throw new Error("所有点位配图失败");
    // 封面点位若失败，转存后从正文点位中借用第一张
    const hasCover = okPoints.some((p) => p.type === "cover");
    if (!hasCover && okPoints.length > 1) {
      task.warnings.push("封面配图失败，已复用首个正文配图作为封面");
    }

    // 3. 转存 Halo 附件库（防外链失效）
    progress(task, "transfer", 55, "正在转存图片到站点附件库…");
    for (const [i, point] of okPoints.entries()) {
      try {
        const { bytes, contentType } = await downloadImage(point.imageUrl!);
        const ext = contentType.includes("png") ? "png" : "jpg";
        const filename = `studio-${task.id}-${point.id}.${ext}`;
        point.imageUrl = await uploadAttachment(bytes, filename, contentType);
      } catch (e) {
        point.status = "failed";
        point.error = e instanceof Error ? e.message : String(e);
      }
      progress(
        task,
        "transfer",
        55 + Math.round(((i + 1) / okPoints.length) * 15),
        `转存进度 ${i + 1}/${okPoints.length}`,
      );
    }
    const transferred = points.filter((p) => p.status === "done" && p.imageUrl);
    if (transferred.length === 0) throw new Error("图片转存全部失败");
    const failedCount = points.filter((p) => p.status === "failed").length;
    if (failedCount > 0) task.warnings.push(`${failedCount} 个点位配图失败已跳过`);

    // 4. 组装 markdown（插图写回正文）
    const cover = transferred.find((p) => p.type === "cover") || transferred[0];
    task.coverUrl = cover?.imageUrl;
    const finalMd = composeMarkdown(markdown, blocks, transferred);
    // PostSpec.categories 需要分类 metadata.name；解析不到时省略，由 Halo 自动挂默认分类
    const categoryName = await resolveCategoryName(categorySlug);
    const frontMatterLines = [
      "---",
      `title: ${JSON.stringify(title)}`,
      `slug: ${JSON.stringify(slug)}`,
    ];
    if (categoryName) frontMatterLines.push("categories:", `  - ${categoryName}`);
    frontMatterLines.push("---", "");
    const frontMatter = frontMatterLines.join("\n");

    // 5. 建文（Halo 渲染 md → HTML 并发布）
    progress(task, "publish", 72, "正在创建文章…");
    const pub = await publishMarkdownPost(frontMatter + finalMd, slug);
    task.postName = pub.postName;
    if (pub.action === "updated") {
      task.warnings.push(`已存在相同 slug 的文章，内容已被覆盖：${slug}`);
    }

    // 6. 转 HTML rawType + 封面/编辑器注解 + 重新发布
    progress(task, "publish", 85, "正在规范化内容并发布…");
    const head = await getHeadContent(pub.postName);
    const html = head.content.trim() ? head.content : finalMd;
    await updateContentToHtml(pub.postName, html);
    await updatePostSpec(
      pub.postName,
      {
        cover: task.coverUrl,
        preferredEditor: "default",
      },
      html,
    );
    await publishPost(pub.postName);

    // 7. 完成
    task.status = "done";
    task.percent = 100;
    task.step = "done";
    task.message = "发布成功";
    task.permalink = `/archives/${encodeURIComponent(slug)}`;
  } catch (e) {
    task.status = "error";
    task.step = "error";
    task.message = e instanceof Error ? e.message : String(e);
    task.error = e instanceof Error ? e.message : String(e);
  }
}

/** 创建任务并异步执行 */
export function startImport(options: ImportOptions): StudioTask {
  const task = createTask(options);
  void runPipeline(task.id, options);
  return task;
}
