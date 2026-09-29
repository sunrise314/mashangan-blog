package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/site-config")
@RequiredArgsConstructor
public class AdminSiteConfigController {

    private final SiteConfigService service;

    @GetMapping
    public SiteConfig get() {
        return service.getConfig();
    }

    @PutMapping
    public SiteConfig update(@RequestBody SiteConfig patch) {
        return service.update(patch);
    }
}
