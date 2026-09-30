import type {
  HaloCategory,
  HaloMenu,
  HaloPageResult,
  HaloPost,
  HaloPostDetail,
  HaloSearchHit,
  HaloSinglePage,
  SeriesCard,
  SeriesDetail,
} from "~/types/halo";
import { SECTION_INTERVIEW, SECTION_LABEL } from "~/utils/section";

const CONTENT_API_BASE = "/apis/api.content.halo.run/v1alpha1";
// Menu / 搜索的 GVK group 为空，公开 API 注册在 api.halo.run 分组下
const CORE_API_BASE = "/apis/api.halo.run/v1alpha1";

/** 单次列表请求的页大小：替代旧的 size=1000，配合分页循环/分页 UI 使用 */
const PAGE_SIZE = 100;
const CATEGORY_PAGE_SIZE = 200;

export function useHaloApi() {
  // 必须在 setup 同步阶段取一次配置：handler 中经过多次 await 后
  // Nuxt 实例上下文会丢失，届时再调用 useRuntimeConfig 会抛错。
  // 客户端必须走同源相对路径（nginx 80 站点反代 /apis/ → halo:8090），
  // 否则浏览器跨源直连 8090 会被 CORS 拦截，SPA 导航拿不到数据（页面空白）；
  // 服务端（SSR / nitro）无跨域问题，直连 Halo 完整地址
  const base = import.meta.server ? (useRuntimeConfig().public.haloApiBase as string) : "";
  // 图片 CDN base：服务端读容器 env；客户端读内联配置。为空则 cdnizeDeep 原样返回
  const imgCdnBase = import.meta.server
    ? (process.env.IMG_CDN_BASE || "").replace(/\/$/, "")
    : ((useRuntimeConfig().public.imgCdnBase as string) || "").replace(/\/$/, "");

  async function apiFetch<T>(path: string, apiBase: string = CONTENT_API_BASE): Promise<T> {
    const url = `${base}${apiBase}${path}`;
    const data = await $fetch<T>(url, {
      headers: { Accept: "application/json" },
    });
    // 数据出口统一改写图片地址为 CDN（覆盖 SSR 与客户端 SPA 导航两条路径）
    return cdnizeDeep(data, imgCdnBase);
  }

  /** 翻页拉取一个列表资源的全部页（单页 100 条），供确实需要全量数据的场景使用 */
  async function fetchAllPages<T>(path: string, pageSize: number = PAGE_SIZE): Promise<T[]> {
    const sep = path.includes("?") ? "&" : "?";
    const first = await apiFetch<HaloPageResult<T>>(`${path}${sep}size=${pageSize}&page=1`);
    const items = [...first.items];
    for (let page = 2; page <= first.totalPages; page++) {
      const next = await apiFetch<HaloPageResult<T>>(`${path}${sep}size=${pageSize}&page=${page}`);
      items.push(...next.items);
    }
    return items;
  }

  /** 获取主菜单（后台「外观 - 菜单」中设置为主菜单的菜单，含树形菜单项） */
  async function getPrimaryMenu(): Promise<HaloMenu> {
    return await apiFetch<HaloMenu>("/menus/-", CORE_API_BASE);
  }

  /** 获取全部分类（含隐藏、各栏目分区），仅题库等内部数据层使用 */
  async function getAllCategories(): Promise<HaloCategory[]> {
    return await fetchAllPages<HaloCategory>("/categories", CATEGORY_PAGE_SIZE);
  }

  /**
   * 获取通用分类（专栏）：默认排除「列表隐藏」和独立栏目分区（如八股题库），
   * includeSections=true 时返回含栏目分区在内的可见分类。
   */
  async function getCategories(
    options: { includeSections?: boolean } = {},
  ): Promise<HaloCategory[]> {
    const items = await getAllCategories();
    return items.filter(
      (c) =>
        !c.spec.hideFromList &&
        (options.includeSections || c.metadata.labels?.[SECTION_LABEL] !== SECTION_INTERVIEW),
    );
  }

  /** 获取某个栏目分区下所有分类的 metadata.name 集合（用于从通用文章列表中排除） */
  async function getSectionCategoryNames(
    section: string = SECTION_INTERVIEW,
  ): Promise<Set<string>> {
    const items = await getAllCategories();
    return new Set(
      items
        .filter((c) => c.metadata.labels?.[SECTION_LABEL] === section)
        .map((c) => c.metadata.name),
    );
  }

  /** 获取某个栏目分区下所有分类的 slug 集合（搜索接口命中项里带的是分类 slug） */
  async function getSectionCategorySlugs(
    section: string = SECTION_INTERVIEW,
  ): Promise<Set<string>> {
    const items = await getAllCategories();
    return new Set(
      items.filter((c) => c.metadata.labels?.[SECTION_LABEL] === section).map((c) => c.spec.slug),
    );
  }

  /**
   * 根据 slug 获取分类（按 slug 的直查场景，栏目分区分类也能命中）。
   * includeHidden=true 时允许命中「列表隐藏」分类（如未正式上线的栏目）。
   */
  async function getCategoryBySlug(
    slug: string,
    includeHidden: boolean = false,
  ): Promise<HaloCategory | undefined> {
    const items = await getAllCategories();
    return items.find((c) => (includeHidden || !c.spec.hideFromList) && c.spec.slug === slug);
  }

  /** 分页获取直接归属某分类的文章（公开接口 /posts 不支持 category 过滤，须走分类子资源） */
  async function getPostsByCategoryPage(
    categoryName: string,
    page: number,
    size: number,
  ): Promise<HaloPageResult<HaloPost>> {
    return await apiFetch<HaloPageResult<HaloPost>>(
      `/categories/${categoryName}/posts?size=${size}&page=${page}`,
    );
  }

  /** 获取直接归属某分类的全部文章（需要完整顺序的场景，如详情页上下篇） */
  async function getPostsByCategory(categoryName: string): Promise<HaloPost[]> {
    return await fetchAllPages<HaloPost>(`/categories/${categoryName}/posts`);
  }

  /** 分页获取全部已发布文章 */
  async function getPostsPage(page: number, size: number): Promise<HaloPageResult<HaloPost>> {
    return await apiFetch<HaloPageResult<HaloPost>>(`/posts?size=${size}&page=${page}`);
  }

  /**
   * 获取全部已发布文章（分页循环，单页 100 条）；
   * excludeCategoryNames 传入时，排除归属于这些分类的文章（首页/归档排除题库题目）。
   */
  async function getAllPosts(
    options: { excludeCategoryNames?: Set<string> } = {},
  ): Promise<HaloPost[]> {
    const items = await fetchAllPages<HaloPost>("/posts");
    const excluded = options.excludeCategoryNames;
    if (!excluded) return items;
    return items.filter((post) => !post.spec.categories?.some((name) => excluded.has(name)));
  }

  /** 获取单篇文章详情（含正文） */
  async function getPostDetail(postName: string): Promise<HaloPostDetail> {
    return await apiFetch<HaloPostDetail>(`/posts/${postName}`);
  }

  /** 获取全部已发布独立页面 */
  async function getAllSinglePages(): Promise<HaloSinglePage[]> {
    return await fetchAllPages<HaloSinglePage>("/singlepages");
  }

  /** 根据名称获取独立页面详情（含正文） */
  async function getSinglePage(name: string): Promise<HaloSinglePage> {
    return await apiFetch<HaloSinglePage>(`/singlepages/${name}`);
  }

  /**
   * 全文检索（Halo 内置 Lucene 索引），返回命中项（标题/摘要带 <B> 高亮）。
   * 注意：公开 /posts 列表接口的 keyword 参数无效，搜索必须走该端点。
   */
  async function searchPosts(keyword: string, limit: number = 50): Promise<HaloSearchHit[]> {
    const result = await $fetch<{ hits: HaloSearchHit[]; total: number }>(
      `${base}${CORE_API_BASE}/indices/-/search`,
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: { keyword, limit },
      },
    );
    return result.hits ?? [];
  }

  /** 根据分类 slug 和文章 slug 查找文章 */
  async function findPostBySlugs(
    categorySlug: string,
    postSlug: string,
  ): Promise<{ category: HaloCategory; post: HaloPost } | null> {
    const category = await getCategoryBySlug(categorySlug, true);
    if (!category) return null;
    const posts = await getPostsByCategory(category.metadata.name);
    const post = posts.find((p) => p.spec.slug === postSlug);
    if (!post) return null;
    return { category, post };
  }

  /** 跨分类按文章 slug 查找文章（用于 /archives/{slug} 这类 Halo 原生固定链接） */
  async function findPostBySlug(postSlug: string): Promise<HaloPost | undefined> {
    const posts = await getAllPosts();
    return posts.find((p) => p.spec.slug === postSlug);
  }

  /** 按 slug 查找任意已发布文章（含系列文章，全局 /posts 会排除系列文章） */
  async function findAnyPostBySlug(postSlug: string): Promise<HaloPost | undefined> {
    try {
      return await apiFetch<HaloPost>(`/posts/by-slug/${encodeURIComponent(postSlug)}`);
    } catch {
      return undefined;
    }
  }

  /** 项目实战卡片列表（一个卡片 = 一个系列/项目） */
  async function getSeries(): Promise<SeriesCard[]> {
    return await apiFetch<SeriesCard[]>("/series");
  }

  /** 项目大纲：系列元信息 + 章节列表（含免费/付费标记） */
  async function getSeriesDetail(slug: string): Promise<SeriesDetail> {
    return await apiFetch<SeriesDetail>(`/series/${encodeURIComponent(slug)}`);
  }

  return {
    getPrimaryMenu,
    getAllCategories,
    getCategories,
    getSectionCategoryNames,
    getSectionCategorySlugs,
    getCategoryBySlug,
    getPostsByCategory,
    getPostsByCategoryPage,
    getPostsPage,
    getAllPosts,
    getPostDetail,
    getAllSinglePages,
    getSinglePage,
    searchPosts,
    findPostBySlugs,
    findPostBySlug,
    findAnyPostBySlug,
    getSeries,
    getSeriesDetail,
  };
}
