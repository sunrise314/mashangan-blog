package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.service.AdminCategoryService;
import com.mashangan.blog.web.admin.dto.CategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService service;

    @GetMapping
    public List<Category> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Category get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Category create(@RequestBody CategoryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @RequestBody CategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
