import type { HaloCategory, HaloPost } from "~/types/halo";

/** 分类完结状态注解键：completed | updating（后台数据维护，前台只读） */
export const CATEGORY_STATUS_ANNOTATION = "tutorial.halo.run/status";

export type TutorialStatus = "completed" | "updating";

export const TUTORIAL_STATUS_LABEL: Record<TutorialStatus, string> = {
  completed: "已完结",
  updating: "连载中",
};

/**
 * 教程状态：优先读分类注解 tutorial.halo.run/status，
 * 回退旧约定（描述含「已完结」字样），默认连载中。
 */
export function getTutorialStatus(category: HaloCategory): TutorialStatus {
  const anno = category.metadata.annotations?.[CATEGORY_STATUS_ANNOTATION];
  if (anno === "completed" || anno === "updating") return anno;
  return (category.spec.description || "").includes("已完结") ? "completed" : "updating";
}

/** ISO 时间 → YYYY-MM-DD 日期部分（无效值返回空串，不做时区换算避免日期漂移） */
export function formatDateCN(iso?: string | null): string {
  if (!iso) return "";
  const m = /^(\d{4}-\d{2}-\d{2})/.exec(iso);
  return m ? m[1] : "";
}

/**
 * 系列排序：按发布时间升序。发刊词最早发布自然排最前，章节按发布顺序 01→N 排列。
 * Halo 分类文章接口默认按发布时间倒序返回，直接渲染会导致章节 19→01 反着排、
 * 上一篇/下一篇语义颠倒，凡按「课程目录」呈现的分类都必须过一遍本函数。
 * publishTime 缺失时回退 creationTimestamp，仍相同则按 slug 字典序兜底。
 */
export function sortPostsBySeries<T extends HaloPost>(posts: T[]): T[] {
  return [...posts].sort((a, b) => {
    const ta = a.spec.publishTime || a.metadata.creationTimestamp || "";
    const tb = b.spec.publishTime || b.metadata.creationTimestamp || "";
    if (ta !== tb) return ta < tb ? -1 : 1;
    return (a.spec.slug || "").localeCompare(b.spec.slug || "");
  });
}

/** 从 slug 提取章节编号（digital-twin-05-connectors → 5），无编号返回 undefined */
export function getChapterNumber(slug?: string): number | undefined {
  const m = /-(\d{1,3})(?:-|$)/.exec(slug || "");
  if (!m) return undefined;
  const n = Number.parseInt(m[1], 10);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * 是否为章节式连载：≥3 篇文章 slug 带编号，且带编号数不少于总数减一
 * （容忍 1 篇不带编号的发刊词）。普通分类（杂文/题库等）不适用系列 UI。
 */
export function isSeriesPosts(posts: HaloPost[]): boolean {
  const numbered = posts.filter((p) => getChapterNumber(p.spec.slug) != null).length;
  return numbered >= 3 && numbered >= posts.length - 1;
}
