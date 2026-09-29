package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Menu;
import com.mashangan.blog.domain.entity.MenuItem;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.mapper.MenuItemMapper;
import com.mashangan.blog.mapper.MenuMapper;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.SinglePageMapper;
import com.mashangan.blog.web.halo.dto.HaloMenu;
import com.mashangan.blog.web.halo.dto.HaloMenuItem;
import com.mashangan.blog.web.halo.dto.Meta;
import com.mashangan.blog.web.halo.dto.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class MenuQueryService {

    private final MenuMapper menuMapper;
    private final MenuItemMapper menuItemMapper;
    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;
    private final SinglePageMapper singlePageMapper;

    /** 对齐 GET /menus/-：返回主菜单及完整菜单项树 */
    public HaloMenu getPrimaryMenu() {
        Menu menu = menuMapper.selectOne(new QueryWrapper<Menu>().eq("is_primary", true).last("LIMIT 1"));
        if (menu == null) {
            menu = menuMapper.selectOne(new QueryWrapper<Menu>().orderByAsc("id").last("LIMIT 1"));
        }
        if (menu == null) {
            return null;
        }
        List<MenuItem> items = menuItemMapper.selectList(new QueryWrapper<MenuItem>()
                .eq("menu_id", menu.getId()));

        Map<String, String> refHrefs = resolveRefHrefs(items);

        List<HaloMenuItem> tree = buildTree(items, refHrefs, menu.getHaloName());
        Meta meta = new Meta(menu.getHaloName(), Time.iso(menu.getCreatedAt()), null, null);
        // spec.menuItems 为全部菜单项（含子项）的扁平名表，按 priority 排序
        List<String> flatNames = items.stream()
                .sorted(Comparator.comparing(MenuItem::getPriority,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MenuItem::getId))
                .map(MenuItem::getHaloName)
                .toList();
        return new HaloMenu(meta, new HaloMenu.Spec(menu.getDisplayName(), flatNames), tree);
    }

    /** href 按 kind:id 复合键存放，避免不同资源类型主键数值相同而互相覆盖 */
    private Map<String, String> resolveRefHrefs(List<MenuItem> items) {
        Map<String, String> hrefs = new HashMap<>();
        collectHrefs(items, "category", hrefs, id -> {
            Category c = categoryMapper.selectById(id);
            return c == null ? null : "/categories/" + c.getSlug();
        });
        collectHrefs(items, "post", hrefs, id -> {
            Post p = postMapper.selectById(id);
            return p == null ? null : "/archives/" + p.getSlug();
        });
        collectHrefs(items, "page", hrefs, id -> {
            SinglePage p = singlePageMapper.selectById(id);
            return p == null ? null : "/" + p.getSlug();
        });
        return hrefs;
    }

    private void collectHrefs(List<MenuItem> items, String kind, Map<String, String> hrefs,
                              Function<Long, String> resolver) {
        items.stream()
                .filter(i -> kind.equals(i.getRefKind()) && i.getRefId() != null && i.getHref() == null)
                .map(MenuItem::getRefId)
                .distinct()
                .forEach(id -> {
                    String href = resolver.apply(id);
                    if (href != null) {
                        hrefs.put(kind + ":" + id, href);
                    }
                });
    }

    private List<HaloMenuItem> buildTree(List<MenuItem> items, Map<String, String> refHrefs,
                                         String menuName) {
        Map<Long, List<MenuItem>> byParent = new HashMap<>();
        for (MenuItem item : items) {
            byParent.computeIfAbsent(item.getParentId(), k -> new ArrayList<>()).add(item);
        }
        return buildLevel(null, byParent, refHrefs, menuName);
    }

    private List<HaloMenuItem> buildLevel(Long parentId, Map<Long, List<MenuItem>> byParent,
                                          Map<String, String> refHrefs, String menuName) {
        List<MenuItem> level = byParent.getOrDefault(parentId, List.of());
        return level.stream()
                .sorted(Comparator.comparing(MenuItem::getPriority,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MenuItem::getId))
                .map(item -> {
                    Meta meta = new Meta(item.getHaloName(), Time.iso(item.getCreatedAt()), null, null);
                    String href = item.getHref();
                    if (href == null && item.getRefId() != null) {
                        href = refHrefs.get(item.getRefKind() + ":" + item.getRefId());
                    }
                    var spec = new HaloMenuItem.Spec(item.getDisplayName(), href, item.getTarget(),
                            item.getPriority(), menuName);
                    List<HaloMenuItem> children = buildLevel(item.getId(), byParent, refHrefs, menuName);
                    var status = new HaloMenuItem.Status(item.getDisplayName(), href);
                    return new HaloMenuItem(item.getDisplayName(), meta, spec, status, children);
                })
                .toList();
    }
}
