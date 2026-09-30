package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.AdminNav;
import com.mashangan.blog.service.AdminNavService;
import com.mashangan.blog.web.admin.dto.AdminNavRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台 SPA 侧边栏导航 CRUD。
 * - GET /api/admin/admin-nav      返回可见项（侧边栏使用，按 sort_order 升序）
 * - GET /api/admin/admin-nav/all  返回全部项（含隐藏，管理端使用）
 * - POST/PUT/DELETE               管理端增删改
 */
@RestController
@RequestMapping("/api/admin/admin-nav")
@RequiredArgsConstructor
public class AdminNavController {

    private final AdminNavService service;

    /** 侧边栏使用：仅返回 visible=true 的项。 */
    @GetMapping
    public List<AdminNav> listVisible() {
        return service.listVisible();
    }

    /** 管理端使用：返回全部项（含隐藏）。 */
    @GetMapping("/all")
    public List<AdminNav> listAll() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public AdminNav get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public AdminNav create(@RequestBody AdminNavRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public AdminNav update(@PathVariable Long id, @RequestBody AdminNavRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
