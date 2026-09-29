import type { HaloSinglePage } from "~/types/halo";

/**
 * 独立页共享取数：[slug].vue 渲染与菜单悬停预取共 用同一 useAsyncData key
 * （singlepage-{slug}），预取写入 payload 缓存后，点击导航时直接复用、零请求。
 */

/** 列表定位 slug → 拉详情；slug 不存在返回 null（404 判断由调用方在 setup 上下文抛出） */
export async function fetchSinglePageBySlug(slug: string): Promise<HaloSinglePage | null> {
  const { getAllSinglePages, getSinglePage } = useHaloApi();
  const pages = await getAllSinglePages();
  const summary = pages.find((p) => p.spec.slug === slug);
  if (!summary) return null;
  return await getSinglePage(summary.metadata.name);
}

/** 阻塞取数（await）：SSR 404 正确、客户端导航等待数据；预取命中缓存时同步复用零等待 */
export function useSinglePageBySlug(slug: string) {
  return useAsyncData<HaloSinglePage | null>(`singlepage-${slug}`, () =>
    fetchSinglePageBySlug(slug),
  );
}

const inflight = new Map<string, Promise<HaloSinglePage | null>>();

/** 菜单悬停预取：结果写入 payload.data[同 key]，点击时 useAsyncData 零请求命中 */
export function prefetchSinglePage(slug: string) {
  const nuxtApp = useNuxtApp();
  const key = `singlepage-${slug}`;
  if (nuxtApp.payload.data[key] !== undefined || inflight.has(key)) return;
  const p = fetchSinglePageBySlug(slug)
    .then((v) => {
      nuxtApp.payload.data[key] = v;
    })
    .catch(() => {})
    .finally(() => inflight.delete(key));
  inflight.set(key, p);
}
