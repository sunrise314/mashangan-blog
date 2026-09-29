export interface HaloCategory {
  metadata: {
    name: string;
    creationTimestamp: string;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
  };
  spec: {
    displayName: string;
    slug: string;
    cover: string;
    description: string;
    priority: number;
    hideFromList: boolean;
    /** 子分类的 metadata.name 列表（父分类通过该字段挂子分类） */
    children?: string[];
    preventParentPostCascadeQuery?: boolean;
    template?: string;
  };
  status: {
    permalink: string;
    postCount: number;
    visiblePostCount: number;
  };
  postCount: number;
}

export interface HaloPost {
  metadata: { name: string; creationTimestamp: string; annotations?: Record<string, string> };
  spec: {
    title: string;
    slug: string;
    cover: string;
    excerpt: { raw: string; autoGenerate: boolean };
    publishTime?: string;
    /** 所属分类的 metadata.name 列表 */
    categories?: string[];
    priority?: number;
    pinned?: boolean;
  };
  status: {
    permalink: string;
    excerpt: string;
    publishTime: string;
    lastModifyTime: string;
    phase: string;
  };
  categories?: HaloCategory[];
  tags?: { metadata: { name: string }; spec: { displayName: string; slug: string } }[];
}

export interface HaloPostDetail extends HaloPost {
  content: {
    content: string;
    raw: string;
  };
}

export interface HaloPageResult<T> {
  page: number;
  size: number;
  total: number;
  items: T[];
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
  totalPages: number;
}

/** 菜单项指向的资源（分类 / 文章 / 单页 / 标签等） */
export interface HaloMenuTargetRef {
  group?: string;
  kind?: string;
  name?: string;
  version?: string;
}

/** 导航菜单项，children 为已组装好的树形子菜单 */
export interface HaloMenuItem {
  metadata: { name: string };
  spec: {
    displayName: string;
    href?: string;
    target?: "_blank" | "_self" | "_parent" | "_top";
    priority?: number;
    menuName?: string;
    parent?: string;
    targetRef?: HaloMenuTargetRef;
  };
  /** status 中的 href / displayName 是解析 targetRef 后的最终值，优先使用 */
  status?: {
    displayName?: string;
    href?: string;
  };
  children: HaloMenuItem[];
  parentName?: string;
}

/** 内置全文检索（POST /indices/-/search）命中项，title/description 含 <B> 高亮标签 */
export interface HaloSearchHit {
  metadataName: string;
  title: string;
  description: string;
  /** Halo 原生固定链接 /archives/{slug} */
  permalink: string;
  /** 命中文章所属分类的 slug 列表 */
  categories: string[];
  tags: string[];
  published: boolean;
  creationTimestamp: string;
  updateTimestamp: string;
}

/** Halo 菜单（外观 - 菜单中维护） */
export interface HaloMenu {
  metadata: { name: string };
  spec: {
    displayName: string;
    menuItems?: string[];
  };
  menuItems: HaloMenuItem[];
}

/** 独立页面（关于、隐私政策等） */
export interface HaloSinglePage {
  metadata: { name: string };
  spec: {
    title: string;
    slug: string;
  };
  status: {
    permalink: string;
    phase: string;
    publishTime?: string;
  };
  content: {
    content: string;
    raw: string;
  };
}
