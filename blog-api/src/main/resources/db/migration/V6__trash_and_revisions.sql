-- 回收站：single_pages 补软删字段（posts 表在 V1 已有 deleted）
ALTER TABLE single_pages ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_single_pages_deleted ON single_pages (deleted, published);

-- ---------------------------------------------------------------------------
-- 文章修订历史：每次后台更新前把旧版本整体快照到本表，支持查看与回滚
-- 不存储分类/标签关联（关联在 post_categories/post_tags，回滚时不改动）
-- ---------------------------------------------------------------------------
CREATE TABLE post_revisions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      BIGINT NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    revision_no  INTEGER NOT NULL,
    title        VARCHAR(512) NOT NULL,
    slug         VARCHAR(255) NOT NULL,
    cover        VARCHAR(1024) NOT NULL DEFAULT '',
    excerpt      VARCHAR(1024) NOT NULL DEFAULT '',
    content_html TEXT NOT NULL DEFAULT '',
    content_raw  TEXT,
    raw_type     VARCHAR(16) NOT NULL DEFAULT 'HTML',
    published    BOOLEAN NOT NULL DEFAULT FALSE,
    pinned       BOOLEAN NOT NULL DEFAULT FALSE,
    priority     INTEGER NOT NULL DEFAULT 0,
    visible      VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    allow_comment BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, revision_no)
);
CREATE INDEX idx_post_revisions_post ON post_revisions (post_id, revision_no DESC);
