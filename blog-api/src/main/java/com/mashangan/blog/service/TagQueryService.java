package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.PostTagRel;
import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.PostTagRelMapper;
import com.mashangan.blog.mapper.TagMapper;
import com.mashangan.blog.web.halo.dto.TagCard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TagQueryService {

    private final TagMapper tagMapper;
    private final PostMapper postMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final PostQueryService postQueryService;

    /** 标签云：全量标签 + 各自挂载的已发布公开文章数，按文章数降序 */
    public List<TagCard> listCards() {
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>().orderByAsc("id"));
        if (tags.isEmpty()) return List.of();

        // 已发布公开文章 id 集合（约 350 行，内存计数即可，避免跨表聚合 SQL）
        List<Object> publishedIds = postMapper.selectObjs(
                postQueryService.publishedWrapper().select("id"));
        List<Long> ids = publishedIds.stream()
                .map(o -> ((Number) o).longValue()).toList();

        Map<Long, Integer> countByTag = new HashMap<>();
        if (!ids.isEmpty()) {
            postTagRelMapper.selectMaps(new QueryWrapper<PostTagRel>()
                            .in("post_id", ids)
                            .select("tag_id AS tid, COUNT(*) AS cnt")
                            .groupBy("tag_id"))
                    .forEach(row -> countByTag.put(
                            ((Number) row.get("tid")).longValue(),
                            ((Number) row.get("cnt")).intValue()));
        }

        return tags.stream()
                .map(t -> new TagCard(
                        new TagCard.MetaRef(t.getHaloName()),
                        new TagCard.Spec(t.getDisplayName(), t.getSlug()),
                        countByTag.getOrDefault(t.getId(), 0)))
                .sorted((a, b) -> b.postCount() - a.postCount())
                .toList();
    }

    public Tag getBySlug(String slug) {
        Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("slug", slug));
        if (tag == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return tag;
    }

    /** 标签下文章：仅已发布公开文章，排序与 Halo 公开列表一致 */
    public IPage<Post> pagePostsByTag(Tag tag, long page, long size) {
        return postMapper.selectPage(new Page<>(page, size),
                postQueryService.publishedWrapper()
                        .inSql("id", "SELECT post_id FROM post_tags WHERE tag_id = " + tag.getId()));
    }
}
