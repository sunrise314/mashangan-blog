package com.mashangan.blog.web.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mashangan.blog.domain.entity.PostRevision;
import com.mashangan.blog.service.AdminPostService;
import com.mashangan.blog.web.admin.dto.PostRequest;
import com.mashangan.blog.web.admin.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @GetMapping
    public IPage<PostResponse> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "false") boolean deleted,
                                    @RequestParam(required = false) Boolean published) {
        return adminPostService.page(page, size, keyword, deleted, published);
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id) {
        return adminPostService.get(id);
    }

    @PostMapping
    public PostResponse create(@RequestBody PostRequest req) {
        return adminPostService.create(req);
    }

    @PutMapping("/{id}")
    public PostResponse update(@PathVariable Long id, @RequestBody PostRequest req) {
        return adminPostService.update(id, req);
    }

    /** 软删除：移入回收站 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        adminPostService.delete(id);
    }

    /** 从回收站恢复 */
    @PostMapping("/{id}/restore")
    public void restore(@PathVariable Long id) {
        adminPostService.restore(id);
    }

    /** 彻底删除（不可恢复） */
    @DeleteMapping("/{id}/purge")
    public void purge(@PathVariable Long id) {
        adminPostService.purge(id);
    }

    // ---------- 修订历史 ----------

    @GetMapping("/{id}/revisions")
    public List<PostRevision> listRevisions(@PathVariable Long id) {
        return adminPostService.listRevisions(id);
    }

    @GetMapping("/{id}/revisions/{revisionId}")
    public PostRevision getRevision(@PathVariable Long id, @PathVariable Long revisionId) {
        return adminPostService.getRevision(id, revisionId);
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    public PostResponse restoreRevision(@PathVariable Long id, @PathVariable Long revisionId) {
        return adminPostService.restoreRevision(id, revisionId);
    }
}
