/**
 * POST /api/admin/purge-cache
 * 由 blog-api 在文章写操作后调用，清除 Nitro SWR 缓存。
 * 安全：请求必须带 x-purge-token 头，值与 HALO_PURGE_SECRET 环境变量一致。
 *
 * Nitro 2.13 (Nuxt 3.21) 的缓存机制：
 * - SWR / routeRules 缓存在**默认 storage**（useStorage() 无参数）
 * - cacheKey = [base, group, name, key + ".json"].filter(Boolean).join(":")
 * - 常见 key: nitro:functions:<name>:<path>.json
 * - maxAge / ttl 通过 storage.setItem 的第三个参数传入
 *
 * 策略：直接用 useStorage() 拿默认 storage，getKeys() 全删。
 * purgeAll 清空默认 storage 全部 key，最稳妥，成本可接受。
 */
export default defineEventHandler(async (event) => {
  // --- 鉴权 ---
  const secret = (process.env.HALO_PURGE_SECRET || "").trim();
  const token = String(getHeader(event, "x-purge-token") || "").trim();
  if (!secret || token !== secret) {
    throw createError({ statusCode: 401, statusMessage: "Unauthorized" });
  }

  const body = await readBody(event);
  const paths: string[] = (body?.paths || []).filter((p: string) => typeof p === "string" && p.length > 0);

  // --- 清 Nitro 默认 storage ---
  // Nitro 把所有 cache（SWR + useAsyncData + defineCachedEventHandler）
  // 都放在 useStorage() 的默认 storage 里
  const store = useStorage();
  const allKeys = await store.getKeys();

  let purged = 0;
  if (paths.length === 0) {
    // 清空全部默认 storage（最彻底）
    for (const key of allKeys) {
      await store.removeItem(key);
      purged++;
    }
  } else {
    // 定向清除：key 包含任一 path 的条目
    for (const key of allKeys) {
      if (paths.some((p) => key.includes(p))) {
        await store.removeItem(key);
        purged++;
      }
    }
  }

  // 也尝试用 nitro.storage API 清一遍（某些版本下两个入口可能不同）
  try {
    const nitro = useNitroApp();
    const nitroStore = nitro.storage.useStorage();
    const extraKeys = await nitroStore.getKeys();
    for (const key of extraKeys) {
      if (paths.length === 0 || paths.some((p) => key.includes(p))) {
        await nitroStore.removeItem(key);
        purged++;
      }
    }
  } catch {
    // 某些版本下 useNitroApp().storage 不存在，跳过
  }

  return { ok: true, purged, scope: paths.length ? "targeted" : "all", totalKeysChecked: allKeys.length };
});
