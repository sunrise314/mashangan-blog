/**
 * 站点「栏目分区」约定：
 * Halo 分类一旦设置 hideFromList=true，其下文章也会被公开接口隐藏，
 * 因此题库这类独立栏目的分类保持可见，改用 metadata.labels 打分区标记，
 * 前台在首页教程列表、文章归档等通用展示位按标签排除。
 */
export const SECTION_LABEL = "haloweb.section";

/** Java 面试八股栏目（父分类 slug: java-interview） */
export const SECTION_INTERVIEW = "interview";
