-- 站点级配置：强类型单行表（id 恒为 1，CHECK 约束保证全表仅一行）
-- 取代 Halo 的 site_settings 键值表，符合「直接 SQL 控制、非序列化」的偏好。
CREATE TABLE site_config (
    id                    SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    title                 VARCHAR(128) NOT NULL DEFAULT '',
    subtitle              VARCHAR(255) NOT NULL DEFAULT '',
    logo_url              VARCHAR(512) NOT NULL DEFAULT '',
    favicon_url           VARCHAR(512) NOT NULL DEFAULT '',
    footer_text           VARCHAR(255) NOT NULL DEFAULT '',
    beian_icp             VARCHAR(128) NOT NULL DEFAULT '',
    beian_public_security VARCHAR(128) NOT NULL DEFAULT '',
    seo_description       VARCHAR(512) NOT NULL DEFAULT '',
    seo_keywords          VARCHAR(512) NOT NULL DEFAULT '',
    homepage_title        VARCHAR(128) NOT NULL DEFAULT '',
    homepage_subtitle     VARCHAR(255) NOT NULL DEFAULT '',
    analytics_head_code   TEXT         NOT NULL DEFAULT '',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 初始化唯一一行
INSERT INTO site_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 社交链接（GitHub/微博/Twitter/Email/RSS 等）
-- ---------------------------------------------------------------------------
CREATE TABLE social_links (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform    VARCHAR(32)  NOT NULL,
    label       VARCHAR(64)  NOT NULL,
    url         VARCHAR(512) NOT NULL,
    icon_class  VARCHAR(128) NOT NULL DEFAULT '',
    priority    INTEGER      NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_social_links_enabled ON social_links (enabled, priority);
