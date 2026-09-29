package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.SeriesMapper;
import com.mashangan.blog.web.halo.dto.SeriesCard;
import com.mashangan.blog.web.halo.dto.SeriesDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeriesQueryService {

    private final SeriesMapper seriesMapper;
    private final PostMapper postMapper;

    /** 全部系列，按 sort_order 升序、id 升序（用于 /column 卡片列表） */
    public List<Series> listAll() {
        return seriesMapper.selectList(new QueryWrapper<Series>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    /** /column 卡片：系列基本信息 + 已发布章节数 */
    public List<SeriesCard> listCards() {
        List<Series> all = listAll();
        if (all.isEmpty()) return List.of();
        List<Long> ids = all.stream().map(Series::getId).toList();
        List<Map<String, Object>> rows = postMapper.selectMaps(new QueryWrapper<Post>()
                .in("series_id", ids)
                .eq("published", true)
                .eq("deleted", false)
                .eq("visible", "PUBLIC")
                .select("series_id AS sid, COUNT(*) AS cnt")
                .groupBy("series_id"));
        java.util.Map<Long, Integer> cntBySeries = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            cntBySeries.put(((Number) row.get("sid")).longValue(),
                    ((Number) row.get("cnt")).intValue());
        }
        return all.stream().map(s -> new SeriesCard(
                s.getSlug(), s.getTitle(), s.getCover(), s.getDescription(),
                s.getStatus(), cntBySeries.getOrDefault(s.getId(), 0))).toList();
    }

    public Series getBySlug(String slug) {
        return seriesMapper.selectOne(new QueryWrapper<Series>().eq("slug", slug));
    }

    /** 系列详情：元信息 + 章节列表（已发布、未删除）。index < freeChapterCount 的章节标记免费 */
    public SeriesDetail getDetail(String slug) {
        Series series = getBySlug(slug);
        if (series == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>()
                .eq("series_id", series.getId())
                .eq("published", true)
                .eq("deleted", false)
                .eq("visible", "PUBLIC")
                .orderByAsc("publish_time")
                .orderByAsc("id"));
        int freeCount = series.getFreeChapterCount() == null ? 0 : series.getFreeChapterCount();
        List<SeriesDetail.Chapter> chapters = posts.stream().map(p ->
                new SeriesDetail.Chapter(
                        p.getHaloName(), p.getTitle(), p.getSlug(), p.getCover(), p.getExcerpt(),
                        false, 0)).toList();
        // 按顺序重建并标记免费
        List<SeriesDetail.Chapter> ordered = new java.util.ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            Post p = posts.get(i);
            ordered.add(new SeriesDetail.Chapter(
                    p.getHaloName(), p.getTitle(), p.getSlug(), p.getCover(), p.getExcerpt(),
                    i < freeCount, i + 1));
        }
        return new SeriesDetail(series.getSlug(), series.getTitle(), series.getCover(),
                series.getDescription(), series.getStatus(), freeCount, ordered.size(), ordered);
    }
}
