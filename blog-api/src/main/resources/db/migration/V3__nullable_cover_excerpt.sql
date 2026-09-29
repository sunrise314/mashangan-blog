-- cover/spec_excerpt 需要区分"Halo 快照中无此键"(NULL) 与"空串"("")：
-- Halo 对无 cover 的快照省略键，个别文章 excerpt 只有 autoGenerate 无 raw。
ALTER TABLE posts ALTER COLUMN cover DROP NOT NULL;
ALTER TABLE posts ALTER COLUMN spec_excerpt DROP NOT NULL;
