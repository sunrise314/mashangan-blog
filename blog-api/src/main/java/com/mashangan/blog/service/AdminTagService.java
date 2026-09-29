package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.mapper.TagMapper;
import com.mashangan.blog.web.admin.dto.TagRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTagService {

    private final TagMapper tagMapper;

    public List<Tag> list() {
        return tagMapper.selectList(new QueryWrapper<Tag>().orderByDesc("created_at"));
    }

    public Tag create(TagRequest req) {
        if (req.displayName() == null || req.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "显示名称不能为空");
        }
        Tag t = new Tag();
        t.setDisplayName(req.displayName());
        t.setSlug(resolveUniqueSlug(req.slug(), req.displayName(), null));
        t.setHaloName(req.haloName() != null && !req.haloName().isBlank()
                ? req.haloName() : "tag-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCreatedAt(OffsetDateTime.now());
        tagMapper.insert(t);
        return t;
    }

    public Tag update(Long id, TagRequest req) {
        Tag t = tagMapper.selectById(id);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (req.displayName() == null || req.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "显示名称不能为空");
        }
        t.setDisplayName(req.displayName());
        if (req.slug() != null && !req.slug().isBlank()) {
            String slug = resolveUniqueSlug(req.slug(), req.displayName(), id);
            t.setSlug(slug);
        }
        tagMapper.updateById(t);
        return t;
    }

    /**
     * slug 唯一约束处理（slug 列 UNIQUE）：留空 → 取显示名称；
     * 被占用 → 留空时自动追加 -2/-3 序号，显式指定时返回 400。
     */
    private String resolveUniqueSlug(String slugInput, String displayName, Long selfId) {
        String explicit = slugInput != null && !slugInput.isBlank() ? slugInput.trim() : null;
        String desired = explicit != null ? explicit : displayName.trim();
        if (isSlugFree(desired, selfId)) {
            return desired;
        }
        if (explicit != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug「" + desired + "」已被占用");
        }
        String candidate;
        int i = 1;
        do {
            candidate = desired + "-" + ++i;
        } while (!isSlugFree(candidate, selfId));
        return candidate;
    }

    private boolean isSlugFree(String slug, Long selfId) {
        return tagMapper.selectCount(new QueryWrapper<Tag>()
                .eq("slug", slug)
                .ne(selfId != null, "id", selfId)) == 0;
    }

    public void delete(Long id) {
        tagMapper.deleteById(id);
    }
}
