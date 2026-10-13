-- ---------------------------------------------------------------------------
-- 星球付费会员兑换码：星球管收钱，博客站内输码解锁付费专栏。
-- vip_codes：统一码 + 激活名额上限（max_uses），星主码不限量（max_uses 很大）。
-- vip_members：每次成功兑换生成一条会员记录（VIP JWT 的 sub 指向它），
--              enabled=false 即冻结，/api/vip/me 与正文解锁都会实时复查。
-- ---------------------------------------------------------------------------
CREATE TABLE vip_codes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(64)  UNIQUE NOT NULL,
    max_uses    INTEGER      NOT NULL DEFAULT 1,
    used_count  INTEGER      NOT NULL DEFAULT 0,
    note        VARCHAR(255) NOT NULL DEFAULT '',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE vip_members (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_id     BIGINT       NOT NULL REFERENCES vip_codes (id),
    nickname    VARCHAR(128) NOT NULL DEFAULT '',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_vip_members_code ON vip_members (code_id);
