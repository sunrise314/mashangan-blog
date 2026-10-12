-- 行业/项目展示开关：visible=false 的行业或项目不在前台 /column 列表与详情页展示，
-- 后台仍可编辑。默认 TRUE，保持 V15 种子数据全部展示的现有行为。
ALTER TABLE industry ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE project  ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
