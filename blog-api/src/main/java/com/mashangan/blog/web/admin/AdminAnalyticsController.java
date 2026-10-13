package com.mashangan.blog.web.admin;

import com.mashangan.blog.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 数据看板接口（JWT 鉴权，SecurityConfig /api/admin/** 统一管控）。
 * 2026-10-13 自 halo-web(Nuxt) 迁入，原 x-analytics-token 口令机制随 ANALYTICS_PASSWORD 一并废弃。
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return analyticsService.summary();
    }

    @GetMapping("/visits")
    public Map<String, Object> visits(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return analyticsService.visits(page, size);
    }

    @GetMapping("/visitor/{vid}")
    public Map<String, Object> visitor(@PathVariable String vid) {
        String v = vid == null ? "" : vid.trim();
        if (v.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vid is required");
        }
        return analyticsService.visitor(v);
    }
}
