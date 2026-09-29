package com.mashangan.blog.web.halo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.service.CategoryQueryService;
import com.mashangan.blog.service.PostQueryService;
import com.mashangan.blog.service.SeriesQueryService;
import com.mashangan.blog.service.SinglePageQueryService;
import com.mashangan.blog.web.halo.dto.HaloCategory;
import com.mashangan.blog.web.halo.dto.HaloPageResult;
import com.mashangan.blog.web.halo.dto.HaloPost;
import com.mashangan.blog.web.halo.dto.HaloSinglePage;
import com.mashangan.blog.web.halo.dto.SeriesCard;
import com.mashangan.blog.web.halo.dto.SeriesDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Halo api.content.halo.run 公开只读接口的兼容实现，
 * 路径、参数（1-based 分页）、JSON 结构与 Halo 保持一致，Nuxt 前端零改动。
 */
@RestController
@RequestMapping("/apis/api.content.halo.run/v1alpha1")
@RequiredArgsConstructor
public class PublicContentController {

    private final PostQueryService postQueryService;
    private final CategoryQueryService categoryQueryService;
    private final SinglePageQueryService singlePageQueryService;
    private final SeriesQueryService seriesQueryService;

    /** 项目实战卡片列表（一个卡片 = 一个系列/项目） */
    @GetMapping("/series")
    public List<SeriesCard> series() {
        return seriesQueryService.listCards();
    }

    /** 项目大纲页：系列元信息 + 章节列表（含免费/付费标记） */
    @GetMapping("/series/{slug}")
    public SeriesDetail seriesDetail(@PathVariable("slug") String slug) {
        return seriesQueryService.getDetail(slug);
    }

    @GetMapping("/posts")
    public HaloPageResult<HaloPost> posts(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        IPage<Post> result = postQueryService.pagePublished(page, size);
        return HaloPageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                postQueryService.toHaloList(result.getRecords()));
    }

    @GetMapping("/posts/{name}")
    public HaloPost.Detail postDetail(@PathVariable("name") String name) {
        Post post = postQueryService.getPublishedByHaloName(name);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return postQueryService.toHaloDetail(post);
    }

    @GetMapping("/categories")
    public HaloPageResult<HaloCategory> categories(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        List<HaloCategory> all = categoryQueryService.listAllHalo();
        return slice(all, page, size);
    }

    @GetMapping("/categories/{name}/posts")
    public HaloPageResult<HaloPost> categoryPosts(@PathVariable("name") String name,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        Category category = categoryQueryService.getByHaloName(name);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Category> all = categoryQueryService.listAllEntities();
        IPage<Post> result = postQueryService.pageByCategory(category, all, page, size);
        return HaloPageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                postQueryService.toHaloList(result.getRecords()));
    }

    @GetMapping("/singlepages")
    public HaloPageResult<HaloSinglePage> singlePages(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        List<HaloSinglePage> all = singlePageQueryService.listPublished().stream()
                .map(singlePageQueryService::toHaloSummary)
                .toList();
        return slice(all, page, size);
    }

    @GetMapping("/singlepages/{name}")
    public HaloSinglePage singlePageDetail(@PathVariable("name") String name) {
        SinglePage page = singlePageQueryService.getPublishedByHaloName(name);
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return singlePageQueryService.toHaloDetail(page);
    }

    /** 分类/页面等小表内存分页，保证与 Halo 一致的信封结构 */
    private <T> HaloPageResult<T> slice(List<T> all, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? all.size() : size;
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return HaloPageResult.of(safePage, safeSize, all.size(), all.subList(from, to));
    }
}
