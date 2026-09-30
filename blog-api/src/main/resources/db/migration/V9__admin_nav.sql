-- ---------------------------------------------------------------------------
-- 后台 SPA 侧边栏导航：数据库可配置化（参考若依 RuoYi 的 menu 设计简化版）
-- 加导航项只需 INSERT 一行，无需改 LayoutShell.vue 硬编码。
-- path 字段约定：
--   - 以 /admin/ 开头 → SPA 内部路由（vue-router RouterLink）
--   - 其他（如 /dashboard、/）→ 外部链接（<a href> 跳转或新标签页）
-- ---------------------------------------------------------------------------

CREATE TABLE admin_nav (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id    BIGINT REFERENCES admin_nav (id) ON DELETE CASCADE,
    menu_name    VARCHAR(64)  NOT NULL,             -- 显示名称
    path         VARCHAR(255) NOT NULL,             -- 路由路径
    icon         VARCHAR(64)  NOT NULL DEFAULT '',  -- emoji 或图标
    sort_order   INTEGER      NOT NULL DEFAULT 0,   -- 升序排列
    visible      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_nav_parent ON admin_nav (parent_id);
CREATE INDEX idx_admin_nav_sort   ON admin_nav (sort_order, id);

-- 种子数据：与当前 LayoutShell.vue 硬编码 navItems 保持一致
INSERT INTO admin_nav (menu_name, path, icon, sort_order) VALUES
    ('数据看板',     '/dashboard',           '📊',  10),
    ('AI 一键发文',  '/admin/studio',        '🤖',  20),
    ('AI 提供商配置', '/admin/studio-config', '🔧',  30),
    ('文章管理',     '/admin/posts',         '📝',  40),
    ('分类管理',     '/admin/categories',    '📁',  50),
    ('标签管理',     '/admin/tags',           '🏷️', 60),
    ('单页管理',     '/admin/singlepages',    '📄',  70),
    ('附件管理',     '/admin/attachments',    '📎',  80),
    ('前台导航菜单', '/admin/menus',         '🧭',  90),
    ('站点设置',     '/admin/site-config',    '⚙️', 100),
    ('社交链接',     '/admin/social-links',   '🔗', 110),
    ('后台导航菜单', '/admin/admin-nav',      '📋', 115),
    ('返回前台',     '/',                     '🌐', 999);
