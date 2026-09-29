package com.mashangan.blog.studio;

/** POST /api/admin/studio/import 的入参。 */
public record StudioImportOptions(
        String markdown,
        String title,
        String slug,
        String categorySlug,
        Integer imageCount,
        String style
) {}
