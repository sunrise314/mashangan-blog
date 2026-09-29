package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.SocialLink;
import com.mashangan.blog.service.SocialLinkService;
import com.mashangan.blog.web.admin.dto.SocialLinkRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/social-links")
@RequiredArgsConstructor
public class AdminSocialLinkController {

    private final SocialLinkService service;

    @GetMapping
    public List<SocialLink> list() {
        return service.list();
    }

    @PostMapping
    public SocialLink create(@RequestBody SocialLinkRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public SocialLink update(@PathVariable Long id, @RequestBody SocialLinkRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
