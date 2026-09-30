/**
 * blog-api admin 代理：Nuxt server → blog-api:8090
 * 用 BLOG_ADMIN_USERNAME / BLOG_ADMIN_PASSWORD 登录拿 JWT，缓存到进程内存。
 */
const API_BASE = (process.env.BLOG_API_BASE || "http://blog-api:8090").replace(/\/$/, "");
const ADMIN_USER = process.env.BLOG_ADMIN_USERNAME || "admin";
const ADMIN_PASS = process.env.BLOG_ADMIN_PASSWORD || "";

let cachedJwt: string = "";
let jwtExpiry = 0;

/** 登录 blog-api 拿 JWT（缓存 50 分钟） */
async function getJwt(): Promise<string> {
  if (cachedJwt && Date.now() < jwtExpiry) return cachedJwt;
  const resp = await fetch(`${API_BASE}/api/admin/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!resp.ok) throw createError({ statusCode: 502, statusMessage: "blog-api 登录失败" });
  const data = await resp.json() as { token: string };
  cachedJwt = data.token;
  jwtExpiry = Date.now() + 50 * 60 * 1000;
  return cachedJwt;
}

/** 带 JWT 调用 blog-api 的 admin 端点 */
export async function blogApiAdmin<T = any>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const jwt = await getJwt();
  const opts: RequestInit = {
    method,
    headers: {
      Authorization: `Bearer ${jwt}`,
      "Content-Type": "application/json",
    },
    signal: AbortSignal.timeout(30_000),
  };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const resp = await fetch(`${API_BASE}${path}`, opts);
  if (resp.status === 401 || resp.status === 403) {
    // JWT 过期，清缓存重试一次
    cachedJwt = "";
    jwtExpiry = 0;
    const jwt2 = await getJwt();
    (opts.headers as Record<string, string>).Authorization = `Bearer ${jwt2}`;
    const resp2 = await fetch(`${API_BASE}${path}`, opts);
    if (!resp2.ok) throw createError({ statusCode: resp2.status, statusMessage: await resp2.text() });
    const text2 = await resp2.text();
    return (text2 ? JSON.parse(text2) : null) as T;
  }
  if (!resp.ok) throw createError({ statusCode: resp.status, statusMessage: await resp.text() });
  const text = await resp.text();
  return (text ? JSON.parse(text) : null) as T;
}
