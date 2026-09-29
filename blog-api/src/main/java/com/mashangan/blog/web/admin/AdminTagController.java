package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.service.AdminTagService;
import com.mashangan.blog.web.admin.dto.TagRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final AdminTagService service;

    @GetMapping
    public List<Tag> list() {
        return service.list();
    }

    @PostMapping
    public Tag create(@RequestBody TagRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Tag update(@PathVariable Long id, @RequestBody TagRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
