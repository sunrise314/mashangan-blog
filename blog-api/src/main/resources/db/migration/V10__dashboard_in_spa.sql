-- 把"数据看板"路径从 /dashboard 改为 /admin/dashboard（融入主后台 SPA，不再做跳转）
UPDATE admin_nav SET path = '/admin/dashboard' WHERE path = '/dashboard';
