import type { HaloCategory, HaloPost } from "~/types/halo";

/**
 * 八股题库在 Halo 中的内容模型：
 * - 一个父分类（slug 为 java-interview，带 label haloweb.section=interview）
 * - 父分类 spec.children 挂载各专题子分类（Java 基础、集合、MySQL……）
 * - 每篇文章 = 一道题，归属到某个专题子分类
 * - 题号即题目在专题内按发布时间升序的序号（先发布的排前面，后台可用发布时间调整顺序）
 * - 分类不能设「列表隐藏」：Halo 会连带隐藏其下文章，导致公开接口取不到题目
 */
export const INTERVIEW_ROOT_SLUG = "java-interview";

export interface InterviewTopic {
  category: HaloCategory;
  posts: HaloPost[];
}

export interface InterviewBank {
  /** 父分类不存在时为 null，前端展示空状态引导 */
  root: HaloCategory | null;
  topics: InterviewTopic[];
  /** 全部题目（发布时间升序） */
  posts: HaloPost[];
  /** 最近更新时间（最新一篇题目的发布时间） */
  latestDate: string | null;
}

function postDate(post: HaloPost): number {
  return new Date(
    post.status.publishTime || post.spec.publishTime || post.metadata.creationTimestamp,
  ).getTime();
}

/** 专题内排序：置顶优先，其次发布时间升序（越早发布题号越靠前） */
function sortPostsInTopic(a: HaloPost, b: HaloPost): number {
  if (Boolean(a.spec.pinned) !== Boolean(b.spec.pinned)) {
    return a.spec.pinned ? -1 : 1;
  }
  return postDate(a) - postDate(b);
}

export function useInterviewBank() {
  const api = useHaloApi();

  async function fetchBank(): Promise<InterviewBank> {
    const categories = await api.getCategories({ includeSections: true });
    const root = categories.find((c) => c.spec.slug === INTERVIEW_ROOT_SLUG) ?? null;

    if (!root || !root.spec.children?.length) {
      return { root, topics: [], posts: [], latestDate: null };
    }

    const topicNames = root.spec.children;
    const topics: InterviewTopic[] = topicNames
      .map((name) => categories.find((c) => c.metadata.name === name))
      .filter((c): c is HaloCategory => Boolean(c))
      .sort((a, b) =>
        a.spec.priority !== b.spec.priority
          ? a.spec.priority - b.spec.priority
          : a.metadata.creationTimestamp.localeCompare(b.metadata.creationTimestamp),
      )
      .map((category) => ({ category, posts: [] as HaloPost[] }));

    const topicByName = new Map(topics.map((t) => [t.category.metadata.name, t]));
    const all = await api.getAllPosts();
    const questions = all.filter((post) =>
      post.spec.categories?.some((name) => topicByName.has(name)),
    );

    for (const post of questions) {
      for (const name of post.spec.categories ?? []) {
        topicByName.get(name)?.posts.push(post);
      }
    }
    for (const topic of topics) {
      topic.posts.sort(sortPostsInTopic);
    }

    const sortedAsc = [...questions].sort(sortPostsInTopic);
    const newest = questions.reduce<HaloPost | null>(
      (max, p) => (!max || postDate(p) > postDate(max) ? p : max),
      null,
    );
    const latestDate = newest ? newest.status.publishTime || newest.spec.publishTime || null : null;

    return { root, topics, posts: sortedAsc, latestDate };
  }

  /**
   * 在题库中按 slug 定位题目，返回题目所属专题及在该专题内的相邻题目。
   * 一道题归属多个专题时取第一个。
   */
  function locateQuestion(bank: InterviewBank, slug: string) {
    for (const topic of bank.topics) {
      const index = topic.posts.findIndex((p) => p.spec.slug === slug);
      if (index >= 0) {
        return {
          topic,
          post: topic.posts[index],
          prev: index > 0 ? topic.posts[index - 1] : undefined,
          next: index < topic.posts.length - 1 ? topic.posts[index + 1] : undefined,
        };
      }
    }
    return null;
  }

  return { fetchBank, locateQuestion, INTERVIEW_ROOT_SLUG };
}
