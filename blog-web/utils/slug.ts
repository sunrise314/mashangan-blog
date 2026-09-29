/**
 * 解码路由参数中的 slug。
 * SSR 直链场景下 params 可能保持 URL 编码态（如 %E4%BD%9C%E6%97%B6%E9%97%B4），
 * 与 Halo 存储的原始中文 slug 不一致，需显式解码后再匹配。
 */
export function decodeSlug(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
