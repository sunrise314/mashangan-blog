package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.service.AdminSeriesService;
import com.mashangan.blog.web.admin.dto.SeriesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/series")
@RequiredArgsConstructor
public class AdminSeriesController {

    private final AdminSeriesService service;

    @GetMapping
    public List<Series> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Series get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Series create(@RequestBody SeriesRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Series update(@PathVariable Long id, @RequestBody SeriesRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
