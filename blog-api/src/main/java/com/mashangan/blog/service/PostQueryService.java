package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.PostCategoryRel;
import com.mashangan.blog.domain.entity.PostTagRel;
import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.mapper.PostCategoryRelMapper;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.PostTagRelMapper;
import com.mashangan.blog.mapper.TagMapper;
import com.mashangan.blog.web.halo.dto.HaloCategory;
import com.mashangan.blog.web.halo.dto.HaloPost;
import com.mashangan.blog.web.halo.dto.Meta;
import com.mashangan.blog.web.halo.dto.TagRef;
import com.mashangan.blog.web.halo.dto.Time;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.mapper.SeriesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostQueryService {

    private final PostMapper postMapper;
    private final PostCategoryRelMapper postCategoryRelMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final TagMapper tagMapper;
    private final CategoryQueryService categoryQueryService;
    private final SeriesMapper seriesMapper;

    public IPage<Post> pagePublished(long page, long size) {
        // 已归属系列的文章只在「项目实战」栏目（/series 卡片）展示，不进普通文章流
        return postMapper.selectPage(new Page<>(page, size),
                publishedWrapper().isNull("series_id"));
    }

    /** 分类下文章：仅直接归属（与 Halo 公开行为一致，不级联子孙分类） */
    public IPage<Post> pageByCategory(Category category, List<Category> allCategories,
                                      long page, long size) {
        QueryWrapper<Post> wrapper = publishedWrapper()
                .inSql("id", "SELECT post_id FROM post_categories WHERE category_id = "
                        + category.getId());
        return postMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Post getPublishedByHaloName(String haloName) {
        return postMapper.selectOne(publishedWrapper().eq("halo_name", haloName).last("LIMIT 1"));
    }

    /** 按 slug 查已发布文章（含系列文章，供 /archives 固定链接解析） */
    public Post getPublishedBySlug(String slug) {
        return postMapper.selectOne(publishedWrapper().eq("slug", slug).last("LIMIT 1"));
    }

    /** Halo 公开列表默认：置顶优先，发布时间降序 */
    private QueryWrapper<Post> publishedWrapper() {
        return new QueryWrapper<Post>()
                .eq("published", true)
                .eq("deleted", false)
                .eq("visible", "PUBLIC")
                .isNotNull("publish_time")
                .orderByDesc("pinned")
                .orderByDesc("publish_time")
                .orderByDesc("id");
    }

    /** 批量把文章实体装配为 Halo 列表结构（4 条批量查询，无 N+1） */
    public List<HaloPost> toHaloList(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        List<Category> allCategories = categoryQueryService.listAllEntities();
        Map<Long, List<String>> childrenByParent = categoryQueryService.childNamesAsc(allCategories);
        Map<Long, Integer> counts = categoryQueryService.countVisiblePosts();
        Map<Long, Category> categoryById = allCategories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        List<Tag> allTags = tagMapper.selectList(null);
        Map<Long, Tag> tagById = allTags.stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<Long, List<Long>> catIdsByPost = groupRel(postIds, true);
        Map<Long, List<Long>> tagIdsByPost = groupRel(postIds, false);

        // 批量查 series，避免 N+1
        List<Long> seriesIds = posts.stream().map(Post::getSeriesId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> seriesSlugById = new HashMap<>();
        if (!seriesIds.isEmpty()) {
            seriesMapper.selectBatchIds(seriesIds).forEach(s ->
                    seriesSlugById.put(s.getId(), s.getSlug()));
        }

        List<HaloPost> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            List<Category> cats = catIdsByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(categoryById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            List<Tag> tags = tagIdsByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(tagById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            List<HaloCategory> haloCats = cats.stream()
                    .map(c -> categoryQueryService.toHalo(c, childrenByParent, counts))
                    .toList();
            List<TagRef> tagRefs = tags.stream()
                    .map(t -> new TagRef(new TagRef.MetaRef(t.getHaloName()),
                            new TagRef.Spec(t.getDisplayName(), t.getSlug())))
                    .toList();

            String seriesSlug = post.getSeriesId() != null ? seriesSlugById.get(post.getSeriesId()) : null;
            result.add(toHalo(post,
                    cats.stream().map(Category::getHaloName).toList(),
                    tags.stream().map(Tag::getHaloName).toList(),
                    haloCats, tagRefs, seriesSlug));
        }
        return result;
    }

    public HaloPost.Detail toHaloDetail(Post post) {
        HaloPost listed = toHaloList(List.of(post)).getFirst();
        var content = new HaloPost.Content(post.getContentHtml(),
                post.getContentRaw() != null ? post.getContentRaw() : post.getContentHtml());
        return new HaloPost.Detail(listed.metadata(), listed.spec(), listed.status(),
                listed.categories(), listed.tags(), content, null);
    }

    private HaloPost toHalo(Post post, List<String> categoryNames, List<String> tagNames,
                            List<HaloCategory> categories, List<TagRef> tags, String seriesSlug) {
        Map<String, String> annotations = null;
        if (seriesSlug != null && !seriesSlug.isBlank()) {
            annotations = new HashMap<>();
            annotations.put("haloweb/series", seriesSlug);
        }
        Meta meta = new Meta(post.getHaloName(), Time.iso(post.getCreatedAt()), null, annotations);
        var excerpt = new HaloPost.Excerpt(post.getSpecExcerpt(), post.getAutoExcerpt());
        var spec = new HaloPost.Spec(post.getTitle(), post.getSlug(), post.getCover(), excerpt,
                Time.iso(post.getPublishTime()), categoryNames, tagNames, post.getPriority(),
                post.getPinned(), post.getDeleted(), post.getVisible(), post.getAllowComment());
        var status = new HaloPost.Status(HaloPost.permalink(post.getSlug()), post.getExcerpt(),
                Time.iso(post.getUpdatedAt()), "PUBLISHED");
        return new HaloPost(meta, spec, status,
                categories.isEmpty() ? null : categories,
                tags.isEmpty() ? null : tags);
    }

    private Map<Long, List<Long>> groupRel(List<Long> postIds, boolean category) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (category) {
            postCategoryRelMapper.selectList(new QueryWrapper<PostCategoryRel>()
                            .in("post_id", postIds))
                    .forEach(r -> map.computeIfAbsent(r.getPostId(), k -> new ArrayList<>())
                            .add(r.getCategoryId()));
        } else {
            postTagRelMapper.selectList(new QueryWrapper<PostTagRel>().in("post_id", postIds))
                    .forEach(r -> map.computeIfAbsent(r.getPostId(), k -> new ArrayList<>())
                            .add(r.getTagId()));
        }
        return map;
    }
}
