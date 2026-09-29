package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.mapper.SeriesMapper;
import com.mashangan.blog.web.admin.dto.SeriesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSeriesService {

    private final SeriesMapper seriesMapper;

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

    @Transactional
    public void delete(Long id) {
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
    }
}
