-- 系列/项目下架标记：hidden=true 的系列不在前台 /column「项目实战」卡片列表展示，
-- 但系列详情页、章节页与 sitemap/rss 不受影响（章节仍是公开免费内容）。
ALTER TABLE series ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
