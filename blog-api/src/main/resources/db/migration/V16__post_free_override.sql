-- 单章免费覆盖：后台文章编辑页可对挂系列的文章单独控制免费/锁定
-- NULL=跟随系列规则（前 free_chapter_count 章免费）, 1=强制免费, 0=强制锁定
ALTER TABLE posts ADD COLUMN free_override SMALLINT;
COMMENT ON COLUMN posts.free_override IS '单章免费覆盖：NULL=跟随系列规则, 1=强制免费, 0=强制锁定';
