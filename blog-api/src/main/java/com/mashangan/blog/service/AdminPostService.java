package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangan.blog.domain.entity.*;
import com.mashangan.blog.mapper.*;
import com.mashangan.blog.web.admin.dto.PostRequest;
import com.mashangan.blog.web.admin.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPostService {

    private final PostMapper postMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostCategoryRelMapper postCategoryRelMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final MarkdownService markdownService;
    private final PostRevisionMapper postRevisionMapper;
    private final CachePurgeService cachePurgeService;

    /** 后台列表：含草稿，按更新时间倒序，支持关键字/发布状态过滤；deleted=true 时为回收站视图 */
    public IPage<PostResponse> page(int page, int size, String keyword, boolean deleted, Boolean published) {
        QueryWrapper<Post> wrapper = new QueryWrapper<Post>()
                .eq("deleted", deleted)
                .orderByDesc("updated_at")
                .orderByDesc("id");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("slug", keyword));
        }
        if (published != null) {
            wrapper.eq("published", published);
        }
        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, List<String>> catNames = relNames(result.getRecords(), true);
        Map<Long, List<String>> tagNames = relNames(result.getRecords(), false);
        return result.convert(p -> toResponse(p, catNames.get(p.getId()), tagNames.get(p.getId())));
    }

    public PostResponse get(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toResponse(post, categoryNames(id), tagNames(id));
    }

    @Transactional
    public PostResponse create(PostRequest req) {
        Post post = new Post();
        applyRequest(post, req, true);
        postMapper.insert(post);
        saveAssociations(post.getId(), req);
        cachePurgeService.purgeAll();
        return get(post.getId());
    }

    @Transactional
    public PostResponse update(Long id, PostRequest req) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 更新前保存旧版本快照（修订历史）
        saveRevision(post);
        boolean wasPublished = Boolean.TRUE.equals(post.getPublished());
        applyRequest(post, req, false);
        // 首次发布时设置发布时间
        if (!wasPublished && Boolean.TRUE.equals(post.getPublished()) && post.getPublishTime() == null) {
            post.setPublishTime(OffsetDateTime.now());
        }
        postMapper.updateById(post);
        // 重建关联
        postCategoryRelMapper.delete(new QueryWrapper<PostCategoryRel>().eq("post_id", id));
        postTagRelMapper.delete(new QueryWrapper<PostTagRel>().eq("post_id", id));
        saveAssociations(id, req);
        cachePurgeService.purgeAll();
        return get(id);
    }

    /** 软删除：移入回收站（公开查询已过滤 deleted=true） */
    @Transactional
    public void delete(Long id) {
        // 先加载完整实体再更新：seriesId/freeOverride 为 ALWAYS 策略，部分构造会把这两列清空
        Post post = postMapper.selectById(id);
        if (post == null) return;
        post.setDeleted(true);
        postMapper.updateById(post);
        cachePurgeService.purgeAll();
    }

    /** 从回收站恢复 */
    @Transactional
    public void restore(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        post.setDeleted(false);
        postMapper.updateById(post);
        cachePurgeService.purgeAll();
    }

    /** 彻底删除：物理删除文章及其关联 */
    @Transactional
    public void purge(Long id) {
        postCategoryRelMapper.delete(new QueryWrapper<PostCategoryRel>().eq("post_id", id));
        postTagRelMapper.delete(new QueryWrapper<PostTagRel>().eq("post_id", id));
        postRevisionMapper.delete(new QueryWrapper<PostRevision>().eq("post_id", id));
        postMapper.deleteById(id);
        cachePurgeService.purgeAll();
    }

    // ---------- 修订历史 ----------

    /** 列出某文章的修订版本（不含正文，列表用） */
    public List<PostRevision> listRevisions(Long postId) {
        return postRevisionMapper.selectList(new QueryWrapper<PostRevision>()
                .eq("post_id", postId)
                .orderByDesc("revision_no"));
    }

    /** 查看某个修订版本详情（含正文） */
    public PostRevision getRevision(Long postId, Long revisionId) {
        PostRevision rev = postRevisionMapper.selectById(revisionId);
        if (rev == null || !rev.getPostId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return rev;
    }

    /**
     * 回滚到指定修订版本：
     * 先把当前状态也存成一个修订（保证回滚可撤销），再把目标版本字段写回文章。
     */
    @Transactional
    public PostResponse restoreRevision(Long postId, Long revisionId) {
        Post post = postMapper.selectById(postId);
        if (post == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        PostRevision rev = getRevision(postId, revisionId);
        // 当前状态留档
        saveRevision(post);
        post.setTitle(rev.getTitle());
        post.setSlug(rev.getSlug());
        post.setCover(rev.getCover());
        post.setExcerpt(rev.getExcerpt());
        post.setSpecExcerpt(rev.getExcerpt());
        post.setContentHtml(rev.getContentHtml());
        post.setContentRaw(rev.getContentRaw());
        post.setRawType(rev.getRawType());
        post.setPublished(rev.getPublished());
        post.setPinned(rev.getPinned());
        post.setPriority(rev.getPriority());
        post.setVisible(rev.getVisible());
        post.setAllowComment(rev.getAllowComment());
        post.setUpdatedAt(OffsetDateTime.now());
        postMapper.updateById(post);
        cachePurgeService.purgeAll();
        return get(postId);
    }

    /** 把文章当前状态存为一个修订版本 */
    private void saveRevision(Post post) {
        Long max = postRevisionMapper.selectCount(new QueryWrapper<PostRevision>().eq("post_id", post.getId()));
        PostRevision rev = new PostRevision();
        rev.setPostId(post.getId());
        rev.setRevisionNo((max == null ? 0 : max.intValue()) + 1);
        rev.setTitle(post.getTitle());
        rev.setSlug(post.getSlug());
        rev.setCover(post.getCover() == null ? "" : post.getCover());
        rev.setExcerpt(post.getExcerpt() == null ? "" : post.getExcerpt());
        rev.setContentHtml(post.getContentHtml() == null ? "" : post.getContentHtml());
        rev.setContentRaw(post.getContentRaw());
        rev.setRawType(post.getRawType());
        rev.setPublished(Boolean.TRUE.equals(post.getPublished()));
        rev.setPinned(Boolean.TRUE.equals(post.getPinned()));
        rev.setPriority(post.getPriority() == null ? 0 : post.getPriority());
        rev.setVisible(post.getVisible() == null ? "PUBLIC" : post.getVisible());
        rev.setAllowComment(post.getAllowComment() == null || post.getAllowComment());
        rev.setCreatedAt(OffsetDateTime.now());
        postRevisionMapper.insert(rev);
    }

    // ---------- 内部 ----------

    private void applyRequest(Post post, PostRequest req, boolean isNew) {
        post.setTitle(req.title());
        post.setSlug(req.slug());
        post.setCover(req.cover() != null && !req.cover().isBlank() ? req.cover() : null);
        // 摘要：未指定则取正文前 120 字（纯文本近似）
        String excerpt = req.excerpt();
        if (excerpt == null || excerpt.isBlank()) {
            excerpt = plainExcerpt(req.content());
        }
        post.setExcerpt(excerpt);
        post.setSpecExcerpt(excerpt);
        post.setContentRaw(req.content());
        post.setContentHtml(markdownService.renderToHtml(req.content()));
        post.setRawType("MARKDOWN");
        post.setPublished(req.published() != null && req.published());
        post.setPinned(req.pinned() != null && req.pinned());
        post.setPriority(req.priority() != null ? req.priority() : 0);
        post.setVisible(req.visible() != null ? req.visible() : "PUBLIC");
        post.setAllowComment(req.allowComment() == null || req.allowComment());
        post.setSeriesId(req.seriesId());
        post.setFreeOverride(req.freeOverride());
        post.setDeleted(false);
        if (isNew) {
            post.setHaloName(generateHaloName(req.slug()));
            post.setCreatedAt(OffsetDateTime.now());
            if (Boolean.TRUE.equals(post.getPublished())) {
                post.setPublishTime(OffsetDateTime.now());
            }
        }
        post.setUpdatedAt(OffsetDateTime.now());
    }

    private String generateHaloName(String slug) {
        String base = "post-" + (slug != null && !slug.isBlank() ? slug : UUID.randomUUID().toString().substring(0, 8));
        String candidate = base;
        int i = 1;
        while (postMapper.selectCount(new QueryWrapper<Post>().eq("halo_name", candidate)) > 0) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private static String plainExcerpt(String markdown) {
        if (markdown == null) return "";
        String text = markdown.replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("[#>*_\\-\\[\\]()!]", " ")
                .replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) : text;
    }

    private void saveAssociations(Long postId, PostRequest req) {
        if (req.categories() != null) {
            for (String name : req.categories()) {
                Category c = categoryMapper.selectOne(new QueryWrapper<Category>().eq("halo_name", name));
                if (c != null) {
                    PostCategoryRel rel = new PostCategoryRel();
                    rel.setPostId(postId);
                    rel.setCategoryId(c.getId());
                    postCategoryRelMapper.insert(rel);
                }
            }
        }
        if (req.tags() != null) {
            for (String name : req.tags()) {
                Tag t = tagMapper.selectOne(new QueryWrapper<Tag>().eq("halo_name", name));
                if (t != null) {
                    PostTagRel rel = new PostTagRel();
                    rel.setPostId(postId);
                    rel.setTagId(t.getId());
                    postTagRelMapper.insert(rel);
                }
            }
        }
    }

    private Map<Long, List<String>> relNames(List<Post> posts, boolean category) {
        Map<Long, List<String>> result = new HashMap<>();
        if (posts.isEmpty()) return result;
        List<Long> ids = posts.stream().map(Post::getId).toList();
        if (category) {
            Map<Long, String> idToName = categoryMapper.selectList(null).stream()
                    .collect(Collectors.toMap(Category::getId, Category::getHaloName));
            postCategoryRelMapper.selectList(new QueryWrapper<PostCategoryRel>().in("post_id", ids))
                    .forEach(r -> result.computeIfAbsent(r.getPostId(), k -> new ArrayList<>())
                            .add(idToName.getOrDefault(r.getCategoryId(), "")));
        } else {
            Map<Long, String> idToName = tagMapper.selectList(null).stream()
                    .collect(Collectors.toMap(Tag::getId, Tag::getHaloName));
            postTagRelMapper.selectList(new QueryWrapper<PostTagRel>().in("post_id", ids))
                    .forEach(r -> result.computeIfAbsent(r.getPostId(), k -> new ArrayList<>())
                            .add(idToName.getOrDefault(r.getTagId(), "")));
        }
        return result;
    }

    private List<String> categoryNames(Long postId) {
        return namesByRel(postId, true);
    }

    private List<String> tagNames(Long postId) {
        return namesByRel(postId, false);
    }

    private List<String> namesByRel(Long postId, boolean category) {
        List<String> result = new ArrayList<>();
        if (category) {
            List<Long> catIds = postCategoryRelMapper.selectList(
                            new QueryWrapper<PostCategoryRel>().eq("post_id", postId))
                    .stream().map(PostCategoryRel::getCategoryId).toList();
            if (!catIds.isEmpty()) {
                categoryMapper.selectBatchIds(catIds).forEach(c -> result.add(c.getHaloName()));
            }
        } else {
            List<Long> tagIds = postTagRelMapper.selectList(
                            new QueryWrapper<PostTagRel>().eq("post_id", postId))
                    .stream().map(PostTagRel::getTagId).toList();
            if (!tagIds.isEmpty()) {
                tagMapper.selectBatchIds(tagIds).forEach(t -> result.add(t.getHaloName()));
            }
        }
        return result;
    }

    private PostResponse toResponse(Post p, List<String> cats, List<String> tags) {
        return new PostResponse(p.getId(), p.getHaloName(), p.getTitle(), p.getSlug(),
                p.getCover(), p.getExcerpt(), p.getContentRaw(), p.getContentHtml(),
                p.getRawType(), p.getPublished(), p.getPinned(), p.getPriority(),
                p.getVisible(), p.getAllowComment(),
                cats != null ? cats : List.of(), tags != null ? tags : List.of(),
                p.getSeriesId(), p.getFreeOverride(),
                p.getPublishTime(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
