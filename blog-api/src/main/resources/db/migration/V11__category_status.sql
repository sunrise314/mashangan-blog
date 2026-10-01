-- 分类连载状态：updating（连载中，默认）| completed（已完结）
-- 公开响应会把该值写入 metadata.annotations["tutorial.halo.run/status"]，前端徽章据此渲染
ALTER TABLE categories ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'updating';
