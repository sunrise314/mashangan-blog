-- 阶段2 迁移校准：spec.excerpt.raw 与 status.excerpt 分离。
-- Halo 中自动摘要文章 spec.excerpt.raw 为空串，生成文本只在 status.excerpt；
-- excerpt 列存 status.excerpt（最终摘要文本），spec_excerpt 存 spec 原文（可能为空）。
ALTER TABLE posts ADD COLUMN spec_excerpt TEXT NOT NULL DEFAULT '';
