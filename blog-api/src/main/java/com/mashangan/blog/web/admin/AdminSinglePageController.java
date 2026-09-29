package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.service.AdminSinglePageService;
import com.mashangan.blog.web.admin.dto.SinglePageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/singlepages")
@RequiredArgsConstructor
public class AdminSinglePageController {

    private final AdminSinglePageService service;

    @GetMapping
    public List<SinglePage> list(@RequestParam(defaultValue = "false") boolean deleted) {
        return service.list(deleted);
    }

    @GetMapping("/{id}")
    public SinglePage get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public SinglePage create(@RequestBody SinglePageRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public SinglePage update(@PathVariable Long id, @RequestBody SinglePageRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/restore")
    public SinglePage restore(@PathVariable Long id) {
        return service.restore(id);
    }

    @DeleteMapping("/{id}/purge")
    public void purge(@PathVariable Long id) {
        service.purge(id);
    }
}
