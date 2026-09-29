-- blog-api 初始结构：标准关系表，替代 Halo 的 extensions 单表序列化存储。
-- 命名与业务字段对齐前端契约（web/types/halo.ts），halo_name 仅用于一次性数据迁移映射。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- 用户（后台管理，单一管理员起步）
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL DEFAULT '',
    role          VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 分类：parent_id 自关联（替代 Halo 的 children 名称数组）
-- section 标记栏目分区，如 interview（八股题库）
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name                    VARCHAR(128) UNIQUE,
    slug                         VARCHAR(255) NOT NULL UNIQUE,
    display_name                 VARCHAR(255) NOT NULL,
    cover                        VARCHAR(1024) NOT NULL DEFAULT '',
    description                  TEXT         NOT NULL DEFAULT '',
    priority                     INTEGER      NOT NULL DEFAULT 0,
    hide_from_list               BOOLEAN      NOT NULL DEFAULT FALSE,
    parent_id                    BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    section                      VARCHAR(64),
    template                     VARCHAR(255),
    prevent_parent_cascade_query BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_parent ON categories (parent_id);
CREATE INDEX idx_categories_section ON categories (section);

-- ---------------------------------------------------------------------------
-- 标签
-- ---------------------------------------------------------------------------
CREATE TABLE tags (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name   VARCHAR(128) UNIQUE,
    slug        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 文章：历史导入内容 raw_type=HTML 且 content_html=content_raw
-- ---------------------------------------------------------------------------
CREATE TABLE posts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name     VARCHAR(128) UNIQUE,
    title         VARCHAR(512) NOT NULL,
    slug          VARCHAR(255) NOT NULL,
    cover         VARCHAR(1024) NOT NULL DEFAULT '',
    excerpt       VARCHAR(1024) NOT NULL DEFAULT '',
    auto_excerpt  BOOLEAN      NOT NULL DEFAULT TRUE,
    content_html  TEXT         NOT NULL DEFAULT '',
    content_raw   TEXT,
    raw_type      VARCHAR(16)  NOT NULL DEFAULT 'HTML',
    published     BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned        BOOLEAN      NOT NULL DEFAULT FALSE,
    priority      INTEGER      NOT NULL DEFAULT 0,
    visible       VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC',
    allow_comment BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    publish_time  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 已发布文章 slug 唯一（草稿不约束）
CREATE UNIQUE INDEX uk_posts_slug_published
    ON posts (slug) WHERE published AND NOT deleted;
CREATE INDEX idx_posts_list ON posts (published, deleted, pinned DESC, publish_time DESC);

-- 三元组索引：支持中文子串检索与高亮
CREATE INDEX idx_posts_trgm_title   ON posts USING gin (title gin_trgm_ops);
CREATE INDEX idx_posts_trgm_excerpt ON posts USING gin (excerpt gin_trgm_ops);
CREATE INDEX idx_posts_trgm_content ON posts USING gin (content_html gin_trgm_ops);

CREATE TABLE post_categories (
    post_id     BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, category_id)
);
CREATE INDEX idx_post_categories_category ON post_categories (category_id);

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX idx_post_tags_tag ON post_tags (tag_id);

-- ---------------------------------------------------------------------------
-- 独立页面（关于、隐私政策等）
-- ---------------------------------------------------------------------------
CREATE TABLE single_pages (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name    VARCHAR(128) UNIQUE,
    title        VARCHAR(512) NOT NULL,
    slug         VARCHAR(255) NOT NULL UNIQUE,
    content_html TEXT         NOT NULL DEFAULT '',
    content_raw  TEXT,
    raw_type     VARCHAR(16)  NOT NULL DEFAULT 'HTML',
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    publish_time TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_single_pages_list ON single_pages (published);

-- ---------------------------------------------------------------------------
-- 菜单与菜单项（parent_id 自关联形成树；ref_kind/ref_id 指向分类/文章/页面）
-- ---------------------------------------------------------------------------
CREATE TABLE menus (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name    VARCHAR(128) UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE menu_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name    VARCHAR(128) UNIQUE,
    menu_id      BIGINT NOT NULL REFERENCES menus (id) ON DELETE CASCADE,
    parent_id    BIGINT REFERENCES menu_items (id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    href         VARCHAR(1024),
    target       VARCHAR(16),
    priority     INTEGER      NOT NULL DEFAULT 0,
    ref_kind     VARCHAR(16),
    ref_id       BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_menu_items_menu ON menu_items (menu_id);
CREATE INDEX idx_menu_items_parent ON menu_items (parent_id);

-- ---------------------------------------------------------------------------
-- 附件元数据（文件本体在挂载卷 ATTACHMENT_DIR）
-- ---------------------------------------------------------------------------
CREATE TABLE attachments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    halo_name     VARCHAR(128) UNIQUE,
    storage_path  VARCHAR(1024) NOT NULL,
    url_path      VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(128)  NOT NULL DEFAULT '',
    size          BIGINT        NOT NULL DEFAULT 0,
    uploader_id   BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 站点设置（站名、Logo、页脚等键值配置）
-- ---------------------------------------------------------------------------
CREATE TABLE site_settings (
    setting_key VARCHAR(64)  PRIMARY KEY,
    value       TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
