-- ---------------------------------------------------------------------------
-- SEO 主动推送：token 与站点 URL 存 site_config（强类型列，与 studio key 同模式）。
-- 后台「SEO 提交」页直推百度普通收录 + IndexNow（Bing/Yandex 等），
-- Google 无公开提交 API，页面提供 GSC 网址检查深链（resource_id 可配）。
-- 公开响应 PublicSiteConfigDto 为白名单字段，这些列不会泄露到前台。
-- ---------------------------------------------------------------------------
ALTER TABLE site_config ADD COLUMN seo_baidu_token  VARCHAR(2048) NOT NULL DEFAULT '';
ALTER TABLE site_config ADD COLUMN seo_indexnow_key VARCHAR(128)  NOT NULL DEFAULT 'a2783e9f05a412865e4f640739153f79'; -- 与已部署的 public/<key>.txt 一致
ALTER TABLE site_config ADD COLUMN seo_site_url     VARCHAR(255)  NOT NULL DEFAULT 'https://www.mashangan.com';
ALTER TABLE site_config ADD COLUMN seo_gsc_resource VARCHAR(512)  NOT NULL DEFAULT 'sc-domain:www.mashangan.com';

-- 后台侧边栏入口（排在 AI 提供商配置 30 与 文章管理 40 之间）
INSERT INTO admin_nav (menu_name, path, icon, sort_order) VALUES ('SEO 提交', '/admin/seo-push', '🚀', 35);
