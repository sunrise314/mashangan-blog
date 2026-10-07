package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.service.AdminSeriesService;
import com.mashangan.blog.web.admin.dto.SeriesRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
    public void delete(@PathVariable Long id,
                       @RequestParam(defaultValue = "false") boolean force) {
        service.delete(id, force);
    }

    /**
     * 把 ResponseStatusException 的 reason 包装进响应体（Spring Boot 默认
     * include-message=never，中文提示不会出现在 body 里，前端拿不到具体原因）。
     * 与 AttachmentController 同款。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
