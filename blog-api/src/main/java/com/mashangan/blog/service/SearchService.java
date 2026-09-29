package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.PostCategoryRel;
import com.mashangan.blog.domain.entity.PostTagRel;
import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.mapper.PostCategoryRelMapper;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.PostTagRelMapper;
import com.mashangan.blog.mapper.TagMapper;
import com.mashangan.blog.web.halo.dto.SearchDtos;
import com.mashangan.blog.web.halo.dto.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_LIMIT = 100;
    private static final int SNIPPET_LENGTH = 120;

    private final PostMapper postMapper;
    private final PostCategoryRelMapper postCategoryRelMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public SearchDtos.Response search(String keyword, int requestedLimit) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchDtos.Response(List.of(), 0);
        }
        String kw = keyword.trim();
        int limit = Math.max(1, Math.min(requestedLimit <= 0 ? 20 : requestedLimit, MAX_LIMIT));
        String like = "%" + kw.replace("%", "\\%").replace("_", "\\_") + "%";

        // gin_trgm_ops 支持带前导通配符的 ILIKE 走索引
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>()
                .eq("published", true)
                .eq("deleted", false)
                .eq("visible", "PUBLIC")
                .and(w -> w.apply("title ILIKE {0}", like)
                        .or().apply("excerpt ILIKE {0}", like)
                        .or().apply("content_html ILIKE {0}", like))
                .orderByDesc("pinned")
                .orderByDesc("publish_time")
                .last("LIMIT " + limit));

        if (posts.isEmpty()) {
            return new SearchDtos.Response(List.of(), 0);
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<String>> categorySlugs = slugsByPost(postIds, true);
        Map<Long, List<String>> tagSlugs = slugsByPost(postIds, false);

        List<SearchDtos.Hit> hits = posts.stream()
                .map(p -> new SearchDtos.Hit(
                        p.getHaloName(),
                        highlight(p.getTitle(), kw),
                        buildDescription(p, kw),
                        "/archives/" + p.getSlug(),
                        categorySlugs.getOrDefault(p.getId(), List.of()),
                        tagSlugs.getOrDefault(p.getId(), List.of()),
                        Boolean.TRUE.equals(p.getPublished()),
                        Time.iso(p.getCreatedAt()),
                        Time.iso(p.getUpdatedAt())))
                .toList();
        return new SearchDtos.Response(hits, hits.size());
    }

    private Map<Long, List<String>> slugsByPost(List<Long> postIds, boolean category) {
        if (category) {
            List<PostCategoryRel> rels = postCategoryRelMapper.selectList(
                    new QueryWrapper<PostCategoryRel>().in("post_id", postIds));
            Map<Long, String> idToSlug = rels.stream()
                    .map(PostCategoryRel::getCategoryId).distinct().toList().isEmpty()
                    ? Map.of()
                    : categoryMapper.selectByIds(rels.stream()
                                    .map(PostCategoryRel::getCategoryId).distinct().toList())
                            .stream().collect(Collectors.toMap(Category::getId, Category::getSlug));
            return rels.stream().collect(Collectors.groupingBy(PostCategoryRel::getPostId,
                    Collectors.mapping(r -> idToSlug.get(r.getCategoryId()),
                            Collectors.filtering(java.util.Objects::nonNull, Collectors.toList()))));
        } else {
            List<PostTagRel> rels = postTagRelMapper.selectList(
                    new QueryWrapper<PostTagRel>().in("post_id", postIds));
            Map<Long, String> idToSlug = rels.stream()
                    .map(PostTagRel::getTagId).distinct().toList().isEmpty()
                    ? Map.of()
                    : tagMapper.selectByIds(rels.stream()
                                    .map(PostTagRel::getTagId).distinct().toList())
                            .stream().collect(Collectors.toMap(Tag::getId, Tag::getSlug));
            return rels.stream().collect(Collectors.groupingBy(PostTagRel::getPostId,
                    Collectors.mapping(r -> idToSlug.get(r.getTagId()),
                            Collectors.filtering(java.util.Objects::nonNull, Collectors.toList()))));
        }
    }

    /** 描述优先取摘要并高亮；摘要为空时从正文匹配处截取片段 */
    private String buildDescription(Post post, String kw) {
        if (post.getExcerpt() != null && !post.getExcerpt().isBlank()) {
            return highlight(post.getExcerpt(), kw);
        }
        String plain = stripHtml(post.getContentHtml() == null ? "" : post.getContentHtml());
        Pattern p = Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(plain);
        String snippet;
        if (m.find()) {
            int start = Math.max(0, m.start() - SNIPPET_LENGTH / 2);
            int end = Math.min(plain.length(), start + SNIPPET_LENGTH);
            snippet = (start > 0 ? "…" : "") + plain.substring(start, end).trim()
                    + (end < plain.length() ? "…" : "");
        } else {
            snippet = plain.length() > SNIPPET_LENGTH
                    ? plain.substring(0, SNIPPET_LENGTH).trim() + "…"
                    : plain;
        }
        return highlight(snippet, kw);
    }

    private String highlight(String text, String kw) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Pattern p = Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("<B>" + m.group() + "</B>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String stripHtml(String html) {
        String noTags = html.replaceAll("(?s)<[^>]+>", "");
        String normalized = noTags.replaceAll("\\s+", " ").trim();
        return normalized
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
