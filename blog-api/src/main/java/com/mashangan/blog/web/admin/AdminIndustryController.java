package com.mashangan.blog.web.admin;

import com.mashangan.blog.service.AdminIndustryService;
import com.mashangan.blog.web.admin.dto.IndustryAdminResponse;
import com.mashangan.blog.web.admin.dto.IndustryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 行业后台管理（前台 /column/industry/{slug} 的数据源） */
@RestController
@RequestMapping("/api/admin/industries")
@RequiredArgsConstructor
public class AdminIndustryController {

    private final AdminIndustryService service;

    @GetMapping
    public List<IndustryAdminResponse> list() {
        return service.list();
    }

    @PostMapping
    public IndustryAdminResponse create(@RequestBody IndustryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public IndustryAdminResponse update(@PathVariable Long id, @RequestBody IndustryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
