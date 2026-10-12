-- 项目专栏导航合并：项目管理(/admin/projects) 与 项目专栏(/admin/series) 两页合一，
-- 侧边栏只保留一个入口「项目专栏」，页内以「项目卡片 / 内容专栏」标签切换。
-- 两个旧路径都仍指向同一合并组件，此处只清理多余的导航行（幂等，兼容手动加过导航的情况）。

-- 情况一：已存在 /admin/projects 导航行 → 删除多余的 /admin/series 行
DELETE FROM admin_nav
WHERE path = '/admin/series'
  AND EXISTS (SELECT 1 FROM admin_nav nav_projects WHERE nav_projects.path = '/admin/projects');

-- 情况二：只有 /admin/series 行 → 原地改为 /admin/projects
UPDATE admin_nav
SET path = '/admin/projects'
WHERE path = '/admin/series'
  AND NOT EXISTS (SELECT 1 FROM admin_nav nav_projects WHERE nav_projects.path = '/admin/projects');

-- 统一入口名称与图标
UPDATE admin_nav SET menu_name = '项目专栏', icon = '📚', sort_order = 45 WHERE path = '/admin/projects';
