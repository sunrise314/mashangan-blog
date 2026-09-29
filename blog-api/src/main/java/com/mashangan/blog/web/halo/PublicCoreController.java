package com.mashangan.blog.web.halo;

import com.mashangan.blog.service.MenuQueryService;
import com.mashangan.blog.service.SearchService;
import com.mashangan.blog.web.halo.dto.HaloMenu;
import com.mashangan.blog.web.halo.dto.SearchDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Halo api.halo.run 公开接口：主菜单与搜索 */
@RestController
@RequestMapping("/apis/api.halo.run/v1alpha1")
@RequiredArgsConstructor
public class PublicCoreController {

    private final MenuQueryService menuQueryService;
    private final SearchService searchService;

    @GetMapping("/menus/-")
    public HaloMenu primaryMenu() {
        return menuQueryService.getPrimaryMenu();
    }

    @PostMapping("/indices/-/search")
    public SearchDtos.Response search(@RequestBody SearchDtos.Request request) {
        return searchService.search(request.keyword(), request.limit());
    }
}
