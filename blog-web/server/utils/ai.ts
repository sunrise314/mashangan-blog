/**
 * /studio 配图流水线的 AI 与图源能力：
 * - GLM-4-Flash：文章解析，输出配图点位 JSON
 * - Pexels：CC0 商用图库检索（必须带浏览器 UA，Cloudflare 拦截默认 UA）
 * - SiliconFlow（Kolors）：AI 生图，watermark=false（智谱 CogView 强制水印，已弃用）
 */

const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

const ZHIPU_CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
const SILICONFLOW_IMAGE_URL = "https://api.siliconflow.cn/v1/images/generations";
const PEXELS_SEARCH_URL = "https://api.pexels.com/v1/search";

/** 单个配图点位（AI 分析结果 + 配图执行结果） */
export interface ImagePoint {
  id: string;
  /** cover=封面（最多 1 个，不插入正文）；content=正文插图 */
  type: "cover" | "content";
  /** 插图锚定的 md 块序号（cover 无意义） */
  blockNo: number;
  /** 在块后（默认）还是块前插入 */
  position: "after" | "before";
  scene: string;
  /** 中文图注 */
  caption: string;
  /** 推荐图源类型：photo 优先图库，illustration 优先 AI */
  kind: "photo" | "illustration";
  prompt: string;
  keywords: string;
  status: "pending" | "done" | "failed";
  source?: "pexels" | "ai";
  imageUrl?: string;
  error?: string;
}

export interface AnalyzeOptions {
  imageCount: number;
  style: string;
  blockCount: number;
}

function env(name: string): string {
  return (process.env[name] || "").trim();
}

async function postJson(
  url: string,
  body: unknown,
  headers: Record<string, string>,
  timeoutMs: number,
) {
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await resp.text();
  let data: unknown = text;
  try {
    data = JSON.parse(text);
  } catch {
    // 非 JSON 响应保留文本
  }
  return { status: resp.status, data };
}

/** 从 LLM 输出中提取 JSON（容忍 ```json 围栏与前后杂文字） */
function extractJson(text: string): unknown | null {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const candidate = (fenced ? fenced[1] : text).trim();
  const start = candidate.indexOf("{");
  const end = candidate.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(candidate.slice(start, end + 1));
  } catch {
    return null;
  }
}

/**
 * 调用 GLM-4-Flash 解析文章，产出配图点位。
 * blocks 已由调用方分块并编号（1..n），LLM 通过 blockNo 锚定插图位置。
 */
export async function analyzeBlocks(
  numberedBlocks: string,
  options: AnalyzeOptions,
): Promise<ImagePoint[]> {
  const key = env("ZHIPU_API_KEY");
  if (!key) throw new Error("Missing env ZHIPU_API_KEY");
  const contentCount = Math.max(0, options.imageCount - 1);
  const system =
    "你是技术博客配图助手。只输出一个 JSON 对象，不要输出任何解释文字或 markdown 围栏。" +
    'JSON 格式：{"points":[{"type":"cover|content","blockNo":数字,"position":"after|before","scene":"中文画面描述","caption":"中文图注(10字内)","kind":"photo|illustration","prompt":"英文AI绘图提示词","keywords":"英文图库检索词,逗号分隔"}]}';
  const user =
    `以下是文章的文本块列表，每块以 [序号] 开头（共 ${options.blockCount} 块）。\n` +
    `需求：挑选 ${options.imageCount} 个配图点位（含 1 个 type=cover 封面，blockNo 固定填 1；` +
    `其余 ${contentCount} 个 type=content 分布在文中最有代表性的讲解段落）。\n` +
    `规则：\n` +
    `1. 代码块、表格、纯列表块不适合配图，禁止选择。\n` +
    `2. kind：讲解真实工具/环境/界面/写代码场景用 photo；抽象概念/架构思想/流程原理用 illustration。\n` +
    `3. prompt 用英文描述画面，${options.style} 风格，无文字无水印；keywords 用 2~4 个英文单词。\n` +
    `4. 若文章适合配图的内容不足，可以少给点位。\n` +
    `5. blockNo 必须是 1 到 ${options.blockCount} 之间的整数。\n\n` +
    numberedBlocks;

  const { status, data } = await postJson(
    ZHIPU_CHAT_URL,
    {
      model: "glm-4-flash",
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      temperature: 0.3,
      max_tokens: 4096,
    },
    { Authorization: `Bearer ${key}` },
    60_000,
  );
  if (status !== 200 || typeof data === "string") {
    throw new Error(`GLM analyze failed: ${status} ${String(data).slice(0, 300)}`);
  }
  const content =
    (data as { choices?: { message?: { content?: string } }[] }).choices?.[0]?.message?.content ||
    "";
  const parsed = extractJson(content) as { points?: unknown[] } | null;
  if (!parsed || !Array.isArray(parsed.points)) {
    throw new Error("GLM analyze: no valid JSON points in response");
  }

  const points: ImagePoint[] = [];
  let coverSeen = false;
  for (const raw of parsed.points) {
    const p = raw as Record<string, unknown>;
    const type = p.type === "cover" && !coverSeen ? "cover" : "content";
    if (type === "cover") coverSeen = true;
    const blockNo = Math.min(options.blockCount, Math.max(1, Number(p.blockNo) || 1));
    const kind = p.kind === "illustration" ? "illustration" : "photo";
    points.push({
      id: `p${points.length + 1}`,
      type,
      blockNo: type === "cover" ? 1 : blockNo,
      position: p.position === "before" ? "before" : "after",
      scene: String(p.scene || "").slice(0, 200),
      caption: String(p.caption || "配图").slice(0, 60),
      kind,
      prompt: String(p.prompt || p.scene || "").slice(0, 900),
      keywords: String(p.keywords || "").slice(0, 120),
      status: "pending",
    });
  }
  if (points.length === 0) throw new Error("GLM analyze: empty points");
  return points;
}

interface PexelsPhoto {
  src?: Record<string, string>;
  photographer?: string;
  alt?: string;
}

/** Pexels 图库检索，返回第一张 landscape 裁剪图的 URL；无结果返回 null */
export async function searchPexels(keywords: string): Promise<string | null> {
  const key = env("PEXELS_API_KEY");
  if (!key || !keywords.trim()) return null;
  const url = `${PEXELS_SEARCH_URL}?query=${encodeURIComponent(keywords.trim())}&per_page=4&orientation=landscape`;
  const resp = await fetch(url, { headers: { Authorization: key, "User-Agent": BROWSER_UA } });
  if (!resp.ok) return null;
  const data = (await resp.json()) as { photos?: PexelsPhoto[] };
  const photo = data.photos?.[0];
  return photo?.src?.landscape || photo?.src?.large || null;
}

/** SiliconFlow Kolors 生图（无水印），返回图片 URL */
export async function generateAiImage(prompt: string): Promise<string> {
  const key = env("SILICONFLOW_API_KEY");
  if (!key) throw new Error("Missing env SILICONFLOW_API_KEY");
  const { status, data } = await postJson(
    SILICONFLOW_IMAGE_URL,
    {
      model: "Kwai-Kolors/Kolors",
      prompt: `${prompt}, clean modern style, no text, no watermark`,
      image_size: "1440x720",
      watermark: false,
      batch_size: 1,
    },
    { Authorization: `Bearer ${key}` },
    90_000,
  );
  if (status !== 200 || typeof data === "string") {
    throw new Error(`AI image failed: ${status} ${String(data).slice(0, 300)}`);
  }
  const d = data as { images?: { url?: string }[]; data?: { url?: string }[] };
  const url = d.images?.[0]?.url || d.data?.[0]?.url;
  if (!url) throw new Error("AI image: no url in response");
  return url;
}

/** 下载外源图片为字节（供转存 Halo 附件库） */
export async function downloadImage(
  url: string,
): Promise<{ bytes: ArrayBuffer; contentType: string }> {
  const resp = await fetch(url, { headers: { "User-Agent": BROWSER_UA } });
  if (!resp.ok) throw new Error(`downloadImage failed: ${resp.status} ${url.slice(0, 120)}`);
  const bytes = await resp.arrayBuffer();
  if (bytes.byteLength === 0) throw new Error(`downloadImage: empty body ${url.slice(0, 120)}`);
  const contentType = resp.headers.get("content-type") || "image/jpeg";
  return { bytes, contentType };
}
