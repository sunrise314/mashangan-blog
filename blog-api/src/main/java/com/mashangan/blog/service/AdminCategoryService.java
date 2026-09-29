package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.web.admin.dto.CategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> list() {
        return categoryMapper.selectList(new QueryWrapper<Category>()
                .orderByDesc("priority").orderByDesc("created_at"));
    }

    public Category get(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return c;
    }

    public Category create(CategoryRequest req) {
        Category c = new Category();
        apply(c, req, true);
        categoryMapper.insert(c);
        return c;
    }

    public Category update(Long id, CategoryRequest req) {
        Category c = get(id);
        apply(c, req, false);
        categoryMapper.updateById(c);
        return c;
    }

    public void delete(Long id) {
        // 解除子分类的父关联（实体方式 null 字段不进 SET 子句，必须 UpdateWrapper 显式 set null），再删除
        categoryMapper.update(null, new UpdateWrapper<Category>()
                .eq("parent_id", id)
                .set("parent_id", null));
        categoryMapper.deleteById(id);
    }

    private void apply(Category c, CategoryRequest req, boolean isNew) {
        if (req.displayName() == null || req.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "显示名称不能为空");
        }
        c.setDisplayName(req.displayName());
        applySlug(c, req.slug(), req.displayName(), isNew);
        c.setCover(req.cover() != null && !req.cover().isBlank() ? req.cover() : null);
        c.setDescription(req.description() != null ? req.description() : "");
        c.setPriority(req.priority() != null ? req.priority() : 0);
        c.setHideFromList(req.hideFromList() != null && req.hideFromList());
        c.setSection(req.section());
        c.setTemplate(req.template());
        c.setPreventParentCascadeQuery(req.preventParentCascadeQuery() != null && req.preventParentCascadeQuery());
        if (req.parentHaloName() != null && !req.parentHaloName().isBlank()) {
            Category parent = categoryMapper.selectOne(
                    new QueryWrapper<Category>().eq("halo_name", req.parentHaloName()));
            c.setParentId(parent != null ? parent.getId() : null);
        } else {
            c.setParentId(null);
        }
        if (isNew) {
            c.setHaloName(generateHaloName(req.slug()));
            c.setCreatedAt(OffsetDateTime.now());
        }
    }

    private String generateHaloName(String slug) {
        String base = "cat-" + (slug != null && !slug.isBlank() ? slug : UUID.randomUUID().toString().substring(0, 8));
        String candidate = base;
        int i = 1;
        while (categoryMapper.selectCount(new QueryWrapper<Category>().eq("halo_name", candidate)) > 0) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    /**
     * slug 唯一约束处理（slug 列 UNIQUE，且 NOT NULL）：
     * 留空 → 取显示名称；被占用 → 留空时自动追加 -2/-3 序号，显式指定时返回 400。
     */
    private void applySlug(Category c, String slugInput, String displayName, boolean isNew) {
        String explicit = slugInput != null && !slugInput.isBlank() ? slugInput.trim() : null;
        String desired = explicit != null ? explicit : displayName.trim();
        boolean changed = isNew || !desired.equals(c.getSlug());
        if (!changed) {
            return;
        }
        if (isSlugFree(desired, c.getId())) {
            c.setSlug(desired);
            return;
        }
        if (explicit != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug「" + desired + "」已被占用");
        }
        String base = desired;
        int i = 2;
        String candidate = base + "-" + i;
        while (!isSlugFree(candidate, c.getId())) {
            candidate = base + "-" + ++i;
        }
        c.setSlug(candidate);
    }

    private boolean isSlugFree(String slug, Long selfId) {
        return categoryMapper.selectCount(new QueryWrapper<Category>()
                .eq("slug", slug)
                .ne(selfId != null, "id", selfId)) == 0;
    }
}
