package com.mashangan.blog.web.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.service.AdminCategoryService;
import com.mashangan.blog.web.admin.dto.CategoryPostSummary;
import com.mashangan.blog.web.admin.dto.CategoryRequest;
import com.mashangan.blog.web.admin.dto.ReorderRequest;
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

    @GetMapping("/page")
    public IPage<Category> page(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.page(page, size);
    }

    @PutMapping("/reorder")
    public void reorder(@RequestBody ReorderRequest req) {
        service.reorder(req.ids());
    }

    @GetMapping("/{id}")
    public Category get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/posts")
    public List<CategoryPostSummary> posts(@PathVariable Long id) {
        return service.posts(id);
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
