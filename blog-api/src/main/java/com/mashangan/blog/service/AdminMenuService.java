package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mashangan.blog.domain.entity.Menu;
import com.mashangan.blog.domain.entity.MenuItem;
import com.mashangan.blog.mapper.MenuItemMapper;
import com.mashangan.blog.mapper.MenuMapper;
import com.mashangan.blog.web.admin.dto.MenuRequest;
import com.mashangan.blog.web.admin.dto.MenuItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 菜单与菜单项管理。公开读仍由 {@link MenuQueryService#getPrimaryMenu()} 提供，
 * 本服务仅负责后台 CRUD；保存后前台 /menus/- 立即反映。
 */
@Service
@RequiredArgsConstructor
public class AdminMenuService {

    private final MenuMapper menuMapper;
    private final MenuItemMapper menuItemMapper;

    // ---------------- 菜单 ----------------

    public List<Menu> listMenus() {
        return menuMapper.selectList(new QueryWrapper<Menu>().orderByDesc("is_primary").orderByAsc("id"));
    }

    public Menu getMenu(Long id) {
        Menu m = menuMapper.selectById(id);
        if (m == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在");
        return m;
    }

    @Transactional
    public Menu createMenu(MenuRequest req) {
        Menu m = new Menu();
        m.setDisplayName(req.displayName());
        boolean primary = Boolean.TRUE.equals(req.isPrimary());
        m.setIsPrimary(primary);
        m.setHaloName("menu-" + UUID.randomUUID().toString().substring(0, 8));
        m.setCreatedAt(OffsetDateTime.now());
        if (primary) {
            clearOtherPrimary(null);
        }
        menuMapper.insert(m);
        return m;
    }

    @Transactional
    public Menu updateMenu(Long id, MenuRequest req) {
        Menu m = getMenu(id);
        if (req.displayName() != null) m.setDisplayName(req.displayName());
        if (req.isPrimary() != null) {
            boolean primary = req.isPrimary();
            if (primary) clearOtherPrimary(id);
            m.setIsPrimary(primary);
        }
        menuMapper.updateById(m);
        return m;
    }

    public void deleteMenu(Long id) {
        menuMapper.deleteById(id);
        // menu_items.menu_id ON DELETE CASCADE 已处理子项
    }

    /** 设某菜单为主菜单时，先把其它菜单的 is_primary 清掉，保证全局只有一个主菜单。 */
    private void clearOtherPrimary(Long excludeId) {
        var uw = new UpdateWrapper<Menu>().eq("is_primary", true);
        if (excludeId != null) uw.ne("id", excludeId);
        uw.set("is_primary", false);
        menuMapper.update(null, uw);
    }

    // ---------------- 菜单项 ----------------

    /** 返回指定菜单下全部菜单项（扁平），按 priority 升序、id 升序，供前端自行构建树。 */
    public List<MenuItem> listItems(Long menuId) {
        return menuItemMapper.selectList(new QueryWrapper<MenuItem>()
                .eq("menu_id", menuId)
                .orderByAsc("priority").orderByAsc("id"));
    }

    public MenuItem createItem(MenuItemRequest req) {
        MenuItem it = new MenuItem();
        apply(it, req);
        it.setHaloName("menuitem-" + UUID.randomUUID().toString().substring(0, 8));
        it.setCreatedAt(OffsetDateTime.now());
        menuItemMapper.insert(it);
        return it;
    }

    public MenuItem updateItem(Long id, MenuItemRequest req) {
        MenuItem it = menuItemMapper.selectById(id);
        if (it == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单项不存在");
        apply(it, req);
        menuItemMapper.updateById(it);
        return it;
    }

    public void deleteItem(Long id) {
        menuItemMapper.deleteById(id);
    }

    private void apply(MenuItem it, MenuItemRequest req) {
        if (req.menuId() != null) it.setMenuId(req.menuId());
        it.setParentId(req.parentId());
        it.setDisplayName(req.displayName());
        it.setHref(req.href());
        it.setTarget(req.target());
        it.setPriority(req.priority() == null ? 0 : req.priority());
        it.setRefKind(req.refKind());
        it.setRefId(req.refId());
    }

    /**
     * 树形视图：返回带 children 的节点列表，便于后台展示嵌套结构。
     */
    public List<MenuNode> listItemsAsTree(Long menuId) {
        List<MenuItem> items = listItems(menuId);
        Map<Long, List<MenuNode>> byParent = new HashMap<>();
        for (MenuItem it : items) {
            byParent.computeIfAbsent(it.getParentId() == null ? 0L : it.getParentId(),
                    k -> new java.util.ArrayList<>()).add(MenuNode.from(it));
        }
        List<MenuNode> roots = byParent.getOrDefault(0L, List.of());
        // 同层按 priority 升序
        roots.sort(Comparator.comparing(n -> n.priority == null ? Integer.MAX_VALUE : n.priority));
        attachChildren(roots, byParent);
        return roots;
    }

    private void attachChildren(List<MenuNode> level, Map<Long, List<MenuNode>> byParent) {
        for (MenuNode n : level) {
            List<MenuNode> children = byParent.getOrDefault(n.id, List.of());
            children.sort(Comparator.comparing(c -> c.priority == null ? Integer.MAX_VALUE : c.priority));
            n.children = children;
            attachChildren(children, byParent);
        }
    }

    /**
     * 菜单节点视图（带 children），用于后台树形展示。
     */
    public static class MenuNode {
        public Long id;
        public Long parentId;
        public String displayName;
        public String href;
        public String target;
        public Integer priority;
        public String refKind;
        public Long refId;
        public List<MenuNode> children;

        static MenuNode from(MenuItem it) {
            MenuNode n = new MenuNode();
            n.id = it.getId();
            n.parentId = it.getParentId();
            n.displayName = it.getDisplayName();
            n.href = it.getHref();
            n.target = it.getTarget();
            n.priority = it.getPriority();
            n.refKind = it.getRefKind();
            n.refId = it.getRefId();
            return n;
        }
    }
}
