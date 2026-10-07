package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.SeriesMapper;
import com.mashangan.blog.web.admin.dto.SeriesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSeriesService {

    private final SeriesMapper seriesMapper;
    private final PostMapper postMapper;

    public List<Series> list() {
        return seriesMapper.selectList(new QueryWrapper<Series>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public Series get(Long id) {
        Series s = seriesMapper.selectById(id);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return s;
    }

    @Transactional
    public Series create(SeriesRequest req) {
        Series s = new Series();
        apply(s, req);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        seriesMapper.insert(s);
        return s;
    }

    @Transactional
    public Series update(Long id, SeriesRequest req) {
        Series s = get(id);
        apply(s, req);
        s.setUpdatedAt(OffsetDateTime.now());
        seriesMapper.updateById(s);
        return s;
    }

    /**
     * 删除专栏。有关联章节时默认拒绝并列出引用方（与附件删除同一硬规则）；
     * force=true 时强制删除，章节经 posts.series_id 外键 ON DELETE SET NULL 自动解除关联，
     * 文章本身保留并回归普通文章。
     */
    @Transactional
    public void delete(Long id, boolean force) {
        List<Post> chapters = postMapper.selectList(new QueryWrapper<Post>()
                .eq("series_id", id)
                .select("id", "title"));
        if (!chapters.isEmpty() && !force) {
            String preview = chapters.stream().limit(5).map(Post::getTitle)
                    .collect(Collectors.joining("、"));
            String suffix = chapters.size() > 5 ? " 等 " + chapters.size() + " 篇" : "";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该专栏下有 " + chapters.size() + " 篇关联章节：" + preview + suffix
                            + "。删除会解除这些章节的专栏关联（文章本身保留），确认请强制删除。");
        }
        seriesMapper.deleteById(id);
    }

    private void apply(Series s, SeriesRequest req) {
        s.setSlug(req.slug());
        s.setTitle(req.title());
        s.setCover(req.cover() != null ? req.cover() : "");
        s.setDescription(req.description() != null ? req.description() : "");
        s.setStatus(req.status() != null ? req.status() : "updating");
        s.setFreeChapterCount(req.freeChapterCount() != null ? req.freeChapterCount() : 0);
        s.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        s.setHidden(req.hidden() != null && req.hidden());
    }
}
