package com.mashangan.blog.web.admin;

import com.mashangan.blog.service.AdminProjectService;
import com.mashangan.blog.web.admin.dto.ProjectAdminResponse;
import com.mashangan.blog.web.admin.dto.ProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 项目卡片后台管理（行业专栏 → 项目卡 → 面试考点） */
@RestController
@RequestMapping("/api/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final AdminProjectService service;

    @GetMapping
    public List<ProjectAdminResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProjectAdminResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ProjectAdminResponse create(@RequestBody ProjectRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ProjectAdminResponse update(@PathVariable Long id, @RequestBody ProjectRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** 与 AdminSeriesController 同款：把 409/400 的中文原因放进响应体 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
