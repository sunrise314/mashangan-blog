package com.mashangan.blog.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback：/admin/ 下不含点的路径转发到 index.html
 * 让 createWebHistory() 直接 URL 如 /admin/studio-config 不 404。
 *
 * 关键：每个路径段都用 [^\\.]+ 约束，确保含文件扩展名的静态资源
 * （如 /admin/assets/index-CLTkkuvQ.js）不会被转发，而是由 Spring
 * 默认静态资源处理器从 BOOT-INF/classes/static/admin/ 提供。
 */
@Controller
public class SpaForwardController {

    /** 单段路径：/admin/posts、/admin/admin-nav 等。 */
    @GetMapping("/admin/{spring:[^\\.]+}")
    public String forward() {
        return "forward:/admin/index.html";
    }

    /** 两段路径：/admin/posts/new、/admin/posts/123 等。每段都不含点。 */
    @GetMapping("/admin/{spring:[^\\.]+}/{tail:[^\\.]+}")
    public String forwardNested() {
        return "forward:/admin/index.html";
    }
}
