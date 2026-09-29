-- ---------------------------------------------------------------------------
-- 系列/项目：把多篇文章聚合成一个项目卡片（对标 quanxiaoha.com 的栏目模型）
-- 一篇文章最多归属一个系列，series 拥有独立的封面/简介/状态/免费章节数。
-- ---------------------------------------------------------------------------
CREATE TABLE series (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug                VARCHAR(128) UNIQUE NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    cover               VARCHAR(1024) NOT NULL DEFAULT '',
    description         TEXT          NOT NULL DEFAULT '',
    status              VARCHAR(16)   NOT NULL DEFAULT 'updating',  -- updating | complete
    free_chapter_count  INTEGER       NOT NULL DEFAULT 0,           -- 0 = 全部免费
    sort_order          INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_series_sort ON series (sort_order, id);

-- 文章归属系列（可空）。系列删除时置空，避免连带文章被删。
ALTER TABLE posts ADD COLUMN IF NOT EXISTS series_id BIGINT REFERENCES series (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_posts_series ON posts (series_id);

-- 项目介绍页（知识星球）的站点级配置，复用 site_config 强类型单行表
ALTER TABLE site_config ADD COLUMN IF NOT EXISTS planet_qrcode_url   VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE site_config ADD COLUMN IF NOT EXISTS planet_intro_html   TEXT         NOT NULL DEFAULT '';
ALTER TABLE site_config ADD COLUMN IF NOT EXISTS planet_cta_text     VARCHAR(128) NOT NULL DEFAULT '扫码加入知识星球';
