package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.mapper.SinglePageMapper;
import com.mashangan.blog.web.admin.dto.SinglePageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminSinglePageService {

    private final SinglePageMapper singlePageMapper;
    private final MarkdownService markdownService;

    public List<SinglePage> list(boolean deleted) {
        return singlePageMapper.selectList(new QueryWrapper<SinglePage>()
                .eq("deleted", deleted)
                .orderByDesc("updated_at"));
    }

    public SinglePage get(Long id) {
        SinglePage p = singlePageMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return p;
    }

    public SinglePage create(SinglePageRequest req) {
        SinglePage p = new SinglePage();
        apply(p, req, true);
        singlePageMapper.insert(p);
        return p;
    }

    public SinglePage update(Long id, SinglePageRequest req) {
        SinglePage p = get(id);
        apply(p, req, false);
        singlePageMapper.updateById(p);
        return p;
    }

    /** 软删除：移入回收站 */
    public void delete(Long id) {
        SinglePage p = new SinglePage();
        p.setId(id);
        p.setDeleted(true);
        singlePageMapper.updateById(p);
    }

    /** 从回收站恢复 */
    public SinglePage restore(Long id) {
        SinglePage p = singlePageMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        p.setDeleted(false);
        singlePageMapper.updateById(p);
        return p;
    }

    /** 彻底删除 */
    public void purge(Long id) {
        singlePageMapper.deleteById(id);
    }

    private void apply(SinglePage p, SinglePageRequest req, boolean isNew) {
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        p.setTitle(req.title());
        applySlug(p, req.slug(), req.title(), isNew);
        p.setContentRaw(req.content());
        p.setContentHtml(markdownService.renderToHtml(req.content()));
        p.setRawType("MARKDOWN");
        p.setPublished(req.published() != null && req.published());
        p.setDeleted(false);
        if (Boolean.TRUE.equals(p.getPublished()) && p.getPublishTime() == null) {
            p.setPublishTime(OffsetDateTime.now());
        }
        if (isNew) {
            // applySlug 已保证 slug 非空，此处仅兜底
            p.setHaloName("page-" + (p.getSlug() != null && !p.getSlug().isBlank()
                    ? p.getSlug() : UUID.randomUUID().toString().substring(0, 8)));
            p.setCreatedAt(OffsetDateTime.now());
        }
        p.setUpdatedAt(OffsetDateTime.now());
    }

    /**
     * slug 唯一约束处理（single_pages.slug UNIQUE 且 NOT NULL）：
     * 留空 → 取标题；被占用 → 留空时自动追加 -2/-3 序号，显式指定时返回 400。
     */
    private void applySlug(SinglePage p, String slugInput, String title, boolean isNew) {
        String explicit = slugInput != null && !slugInput.isBlank() ? slugInput.trim() : null;
        String desired = explicit != null ? explicit : title.trim();
        boolean changed = isNew || !desired.equals(p.getSlug());
        if (!changed) {
            return;
        }
        if (isSlugFree(desired, p.getId())) {
            p.setSlug(desired);
            return;
        }
        if (explicit != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug「" + desired + "」已被占用");
        }
        String base = desired;
        int i = 2;
        String candidate = base + "-" + i;
        while (!isSlugFree(candidate, p.getId())) {
            candidate = base + "-" + ++i;
        }
        p.setSlug(candidate);
    }

    private boolean isSlugFree(String slug, Long selfId) {
        return singlePageMapper.selectCount(new QueryWrapper<SinglePage>()
                .eq("slug", slug)
                .ne(selfId != null, "id", selfId)) == 0;
    }
}
