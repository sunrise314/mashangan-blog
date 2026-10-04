package com.mashangan.blog.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * SEO 推送辅助查询：最近发布的文章 + 首个分类/所属系列的 slug，
 * 用于在后台按 sitemap 同款规则拼最终 URL（系列章节 → /column/…，
 * 有分类 → /categories/…，无分类 → /archives/…）。
 */
public interface SeoPushMapper {

    @Select("""
            SELECT p.title,
                   p.slug,
                   s.slug AS series_slug,
                   (SELECT c.slug
                      FROM post_categories pc
                      JOIN categories c ON c.id = pc.category_id
                     WHERE pc.post_id = p.id
                     ORDER BY c.priority DESC, c.id
                     LIMIT 1) AS category_slug
              FROM posts p
              LEFT JOIN series s ON s.id = p.series_id
             WHERE p.published AND NOT p.deleted
             ORDER BY COALESCE(p.publish_time, p.created_at) DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> selectRecent(int limit);
}
