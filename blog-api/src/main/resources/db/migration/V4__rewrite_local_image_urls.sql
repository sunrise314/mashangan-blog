-- 切换前归一化：把迁移自 Halo 的本地附件绝对 URL 改写为相对路径。
-- 现网 Halo 图片全部为 http://49.235.136.65:8090/upload/...，无外链域名。
-- 改写后统一由 blog-api 的 /upload/** 静态映射提供，Halo 停服不影响。
UPDATE posts
SET cover = REPLACE(cover, 'http://49.235.136.65:8090/upload/', '/upload/')
WHERE cover LIKE 'http://49.235.136.65:8090/upload/%';

UPDATE posts
SET content_html = REPLACE(content_html, 'http://49.235.136.65:8090/upload/', '/upload/'),
    content_raw  = REPLACE(content_raw,  'http://49.235.136.65:8090/upload/', '/upload/');

UPDATE single_pages
SET content_html = REPLACE(content_html, 'http://49.235.136.65:8090/upload/', '/upload/'),
    content_raw  = REPLACE(content_raw,  'http://49.235.136.65:8090/upload/', '/upload/');
