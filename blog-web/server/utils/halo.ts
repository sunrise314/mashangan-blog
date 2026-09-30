/**
 * 【已废弃 DEPRECATED 2026-09-30】旧 Halo 容器已从服务器删除，本文件所有接口均不可用。
 * /studio 配图流水线 v1 建文链路随之失效 —— 请勿调用或扩展本文件；
 * 流水线 v2 应改直连 blog-api（附件上传端点 + md→html 渲染），见 AGENTS.md。
 *
 * Halo console API 写链路封装（/studio 配图流水线专用）。
 *
 * 链路已于 2026-09-16 全程实测验证（_md2html/probe_halo_v2.py）：
 * 1. 附件上传  POST {base}/apis/console.api.storage.halo.run/v1alpha1/attachments/-/upload
 * 2. 建文发布  POST {base}/apis/api.console.halo.run/v1alpha1/markdown/publish（front-matter + commonmark 渲染，幂等 name=md-<md5(slug)>）
 * 3. 内容转 HTML  PUT {base}/apis/api.console.halo.run/v1alpha1/posts/{name}/content
 * 4. spec.cover + preferred-editor 注解  核心 GET + console PUT posts/{name}（乐观锁 409 需重试）
 * 5. 发布  PUT {base}/apis/api.console.halo.run/v1alpha1/posts/{name}/publish
 */

const CONSOLE_BASE = "/apis/api.console.halo.run/v1alpha1";
const STORAGE_CONSOLE_BASE = "/apis/console.api.storage.halo.run/v1alpha1";
const POST_CORE_BASE = "/apis/content.halo.run/v1alpha1";

/** Pexels 图片 CDN 会拦截默认 UA，附件与外源图片下载统一用浏览器 UA */
export const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

interface HaloMetadata {
  name: string;
  version?: number;
  annotations?: Record<string, string>;
  [key: string]: unknown;
}

interface HaloPostExt {
  metadata: HaloMetadata;
  spec: Record<string, unknown>;
  status?: Record<string, unknown>;
}

export interface MarkdownPublishResult {
  postName: string;
  title: string;
  permalink: string | null;
  action: "created" | "updated";
}

function env(name: string): string {
  return (process.env[name] || "").trim();
}

function requireEnv(name: string): string {
  const v = env(name);
  if (!v) throw new Error(`Missing env ${name}`);
  return v;
}

/** Halo API 地址（容器内 http://halo:8090，本地默认本机 8090） */
export function haloBase(): string {
  return (env("HALO_API_BASE") || "http://127.0.0.1:8090").replace(/\/$/, "");
}

/** 站外可访问的 Halo 地址：附件相对路径 /upload/x 需拼成绝对 URL */
export function haloPublicBase(): string {
  return (env("HALO_PUBLIC_BASE") || "http://49.235.136.65:8090").replace(/\/$/, "");
}

function pat(): string {
  return requireEnv("HALO_PAT");
}

async function haloFetch<T>(
  path: string,
  init: { method?: string; body?: unknown; timeoutMs?: number } = {},
): Promise<{ status: number; data: T | string }> {
  const headers: Record<string, string> = { Authorization: `Bearer ${pat()}` };
  let payload: string | undefined;
  if (init.body !== undefined) {
    payload = typeof init.body === "string" ? init.body : JSON.stringify(init.body);
    headers["Content-Type"] = "application/json";
  }
  const resp = await fetch(`${haloBase()}${path}`, {
    method: init.method || "GET",
    headers,
    body: payload,
    signal: AbortSignal.timeout(init.timeoutMs ?? 60_000),
  });
  const text = await resp.text();
  let data: T | string = text;
  if (text) {
    try {
      data = JSON.parse(text) as T;
    } catch {
      // 保留原始文本（错误响应可能是非 JSON）
    }
  }
  return { status: resp.status, data };
}

/** 上传附件（multipart），返回本站绝对 URL（图片转存 Halo 附件库防外链失效） */
export async function uploadAttachment(
  bytes: ArrayBuffer,
  filename: string,
  contentType: string,
): Promise<string> {
  const boundary = `----studio${Date.now()}${Math.random().toString(36).slice(2, 8)}`;
  const head = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: ${contentType}\r\n\r\n`;
  const tail = `\r\n--${boundary}--\r\n`;
  const body = Buffer.concat([
    Buffer.from(head, "utf-8"),
    Buffer.from(bytes),
    Buffer.from(tail, "utf-8"),
  ]);
  const resp = await fetch(`${haloBase()}${STORAGE_CONSOLE_BASE}/attachments/-/upload`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${pat()}`,
      "Content-Type": `multipart/form-data; boundary=${boundary}`,
    },
    body: new Uint8Array(body),
  });
  const text = await resp.text();
  if (!resp.ok) throw new Error(`uploadAttachment failed: ${resp.status} ${text.slice(0, 300)}`);
  const data = JSON.parse(text) as { status?: { permalink?: string } };
  const permalink = data.status?.permalink;
  if (!permalink) throw new Error(`uploadAttachment: no permalink in response`);
  return permalink.startsWith("http") ? permalink : `${haloPublicBase()}${permalink}`;
}

/**
 * 通过 Halo 自带 Markdown 发布端点建文：
 * 服务端解析 front-matter（title/slug/categories）、commonmark 渲染 HTML、按 slug 幂等创建并发布。
 * 返回 action=updated 时说明同 slug 文章已存在并被覆盖，调用方应提示用户。
 */
export async function publishMarkdownPost(
  markdown: string,
  slug: string,
): Promise<MarkdownPublishResult> {
  const boundary = `----studiomd${Date.now()}${Math.random().toString(36).slice(2, 8)}`;
  const head = `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${encodeURIComponent(slug)}.md"\r\nContent-Type: text/markdown\r\n\r\n`;
  const tail = `\r\n--${boundary}--\r\n`;
  const body = Buffer.concat([
    Buffer.from(head, "utf-8"),
    Buffer.from(markdown, "utf-8"),
    Buffer.from(tail, "utf-8"),
  ]);
  const resp = await fetch(`${haloBase()}${CONSOLE_BASE}/markdown/publish`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${pat()}`,
      "Content-Type": `multipart/form-data; boundary=${boundary}`,
    },
    body: new Uint8Array(body),
  });
  const text = await resp.text();
  if (!resp.ok) throw new Error(`markdown/publish failed: ${resp.status} ${text.slice(0, 300)}`);
  const data = JSON.parse(text) as {
    postName?: string;
    action?: string;
    title?: string;
    permalink?: string | null;
  };
  if (!data.postName) throw new Error(`markdown/publish: no postName in response`);
  return {
    postName: data.postName,
    title: data.title || slug,
    permalink: data.permalink ?? null,
    action: data.action === "updated" ? "updated" : "created",
  };
}

/** 拉取 head 内容（content 字段即 Halo 渲染好的 HTML） */
export async function getHeadContent(
  postName: string,
): Promise<{ rawType: string; raw: string; content: string }> {
  const { status, data } = await haloFetch<{ rawType?: string; raw?: string; content?: string }>(
    `${CONSOLE_BASE}/posts/${encodeURIComponent(postName)}/head-content`,
  );
  if (status !== 200 || typeof data === "string") {
    throw new Error(`getHeadContent failed: ${status} ${String(data).slice(0, 200)}`);
  }
  return { rawType: data.rawType || "", raw: data.raw || "", content: data.content || "" };
}

/** 将内容覆写为 HTML rawType（raw 存 HTML，后续默认编辑器可直接打开） */
export async function updateContentToHtml(postName: string, html: string): Promise<void> {
  const { status, data } = await haloFetch(
    `${CONSOLE_BASE}/posts/${encodeURIComponent(postName)}/content`,
    {
      method: "PUT",
      body: { raw: html, content: html, rawType: "HTML" },
    },
  );
  if (status !== 200)
    throw new Error(`updateContentToHtml failed: ${status} ${String(data).slice(0, 200)}`);
}

/**
 * 更新 spec.cover 与 preferred-editor 注解。
 * 乐观锁 409（reconciler 并发刷新 status）时自动重取重试。
 */
export async function updatePostSpec(
  postName: string,
  patch: { cover?: string; preferredEditor?: string },
  contentHtml: string,
): Promise<void> {
  let lastErr = "";
  for (let attempt = 0; attempt < 4; attempt++) {
    const { status, data } = await haloFetch<HaloPostExt>(
      `${POST_CORE_BASE}/posts/${encodeURIComponent(postName)}`,
    );
    if (status !== 200 || typeof data === "string") {
      throw new Error(`getPost failed: ${status} ${String(data).slice(0, 200)}`);
    }
    const post = data;
    if (patch.cover !== undefined) post.spec.cover = patch.cover;
    if (patch.preferredEditor) {
      post.metadata.annotations = { ...(post.metadata.annotations || {}) };
      post.metadata.annotations["content.halo.run/preferred-editor"] = patch.preferredEditor;
    }
    const put = await haloFetch(`${CONSOLE_BASE}/posts/${encodeURIComponent(postName)}`, {
      method: "PUT",
      body: { post, content: { raw: contentHtml, content: contentHtml, rawType: "HTML" } },
    });
    if (put.status === 200) return;
    lastErr = `${put.status} ${String(put.data).slice(0, 200)}`;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`updatePostSpec failed after retries: ${lastErr}`);
}

/** 发布（等待 release snapshot 就绪） */
export async function publishPost(postName: string): Promise<void> {
  const { status, data } = await haloFetch(
    `${CONSOLE_BASE}/posts/${encodeURIComponent(postName)}/publish`,
    {
      method: "PUT",
      timeoutMs: 120_000,
    },
  );
  if (status !== 200)
    throw new Error(`publishPost failed: ${status} ${String(data).slice(0, 200)}`);
}

/**
 * 按分类 slug 解析 PostSpec.categories 所需的 metadata.name。
 * PostSpec.categories 存的是分类的 metadata.name（随机 ID）而非 slug/displayName。
 */
export async function resolveCategoryName(categorySlug: string): Promise<string | null> {
  const resp = await fetch(
    `${haloBase()}/apis/api.content.halo.run/v1alpha1/categories?size=200&page=1`,
    {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    },
  );
  if (!resp.ok) return null;
  const data = (await resp.json()) as {
    items?: { metadata: { name: string }; spec?: { slug?: string } }[];
  };
  const hit = (data.items || []).find((c) => c.spec?.slug === categorySlug);
  return hit?.metadata.name || null;
}
