package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.PostCategoryRel;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.mapper.PostCategoryRelMapper;
import com.mashangan.blog.web.halo.dto.HaloCategory;
import com.mashangan.blog.web.halo.dto.Meta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryMapper categoryMapper;
    private final PostCategoryRelMapper postCategoryRelMapper;

    /** Halo 公开分类顺序：树的前序遍历，每层 priority 降序、创建时间降序 */
    public List<Category> listAllEntities() {
        List<Category> all = categoryMapper.selectList(new QueryWrapper<Category>()
                .orderByDesc("priority")
                .orderByDesc("created_at")
                .orderByDesc("id"));
        Map<Long, List<Category>> childrenByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        List<Category> ordered = new ArrayList<>();
        for (Category root : all.stream().filter(c -> c.getParentId() == null).toList()) {
            appendPreOrder(root, childrenByParent, ordered);
        }
        return ordered;
    }

    private void appendPreOrder(Category node, Map<Long, List<Category>> childrenByParent,
                                List<Category> out) {
        out.add(node);
        childrenByParent.getOrDefault(node.getId(), Collections.emptyList())
                .forEach(child -> appendPreOrder(child, childrenByParent, out));
    }

    public Category getByHaloName(String haloName) {
        return categoryMapper.selectOne(new QueryWrapper<Category>().eq("halo_name", haloName));
    }

    /** categoryId -> 公开可见文章数（已发布、未删除） */
    public Map<Long, Integer> countVisiblePosts() {
        List<Map<String, Object>> rows = postCategoryRelMapper.selectMaps(new QueryWrapper<PostCategoryRel>()
                .select("category_id AS cid, COUNT(*) AS cnt")
                .inSql("post_id",
                        "SELECT id FROM posts WHERE published = TRUE AND deleted = FALSE")
                .groupBy("category_id"));
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("cid")).longValue(), ((Number) row.get("cnt")).intValue());
        }
        return result;
    }

    /** spec.children 名称数组顺序：priority 升序、创建时间升序（与列表的前序降序相反） */
    public Map<Long, List<String>> childNamesAsc(List<Category> all) {
        return all.stream()
                .filter(c -> c.getParentId() != null)
                .sorted(Comparator.comparing(Category::getPriority,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Category::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Category::getId))
                .collect(Collectors.groupingBy(Category::getParentId,
                        Collectors.mapping(Category::getHaloName, Collectors.toList())));
    }

    public List<HaloCategory> listAllHalo() {
        List<Category> all = listAllEntities();
        Map<Long, List<String>> childrenByParent = childNamesAsc(all);
        Map<Long, Integer> counts = countVisiblePosts();
        return all.stream()
                .map(c -> toHalo(c, childrenByParent, counts))
                .toList();
    }

    public HaloCategory toHalo(Category c, Map<Long, List<String>> childrenByParent,
                               Map<Long, Integer> counts) {
        List<String> children = childrenByParent.get(c.getId());
        Meta meta = HaloCategory.meta(c.getHaloName(), c.getCreatedAt(), c.getSection());
        // Halo 仅在直接归属文章数 > 0 时输出计数；纯父分类/空分类省略
        Integer count = counts.getOrDefault(c.getId(), 0);
        Integer shown = count > 0 ? count : null;
        var spec = new HaloCategory.Spec(c.getDisplayName(), c.getSlug(), c.getCover(),
                c.getDescription(), c.getPriority(), c.getHideFromList(),
                children, c.getPreventParentCascadeQuery(),
                c.getTemplate());
        var status = new HaloCategory.Status(HaloCategory.permalink(c.getSlug()), shown, shown);
        return new HaloCategory(meta, spec, status, shown);
    }
}
