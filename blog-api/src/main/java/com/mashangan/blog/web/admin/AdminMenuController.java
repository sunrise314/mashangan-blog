package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Menu;
import com.mashangan.blog.domain.entity.MenuItem;
import com.mashangan.blog.service.AdminMenuService;
import com.mashangan.blog.web.admin.dto.MenuRequest;
import com.mashangan.blog.web.admin.dto.MenuItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单与菜单项管理。公开读仍由 Halo 兼容端点 GET /menus/- 提供（{@code MenuQueryService}），
 * 本控制器仅负责后台 CRUD。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMenuController {

    private final AdminMenuService service;

    // ---------------- 菜单 ----------------

    @GetMapping("/menus")
    public List<Menu> listMenus() {
        return service.listMenus();
    }

    @GetMapping("/menus/{id}")
    public Menu getMenu(@PathVariable Long id) {
        return service.getMenu(id);
    }

    @PostMapping("/menus")
    public Menu createMenu(@RequestBody MenuRequest req) {
        return service.createMenu(req);
    }

    @PutMapping("/menus/{id}")
    public Menu updateMenu(@PathVariable Long id, @RequestBody MenuRequest req) {
        return service.updateMenu(id, req);
    }

    @DeleteMapping("/menus/{id}")
    public void deleteMenu(@PathVariable Long id) {
        service.deleteMenu(id);
    }

    // ---------------- 菜单项 ----------------

    /** 返回指定菜单下扁平菜单项列表（按 priority 升序）。 */
    @GetMapping("/menus/{menuId}/items")
    public List<MenuItem> listItems(@PathVariable Long menuId) {
        return service.listItems(menuId);
    }

    /** 树形视图，便于后台展示嵌套结构。 */
    @GetMapping("/menus/{menuId}/items/tree")
    public List<AdminMenuService.MenuNode> listItemsAsTree(@PathVariable Long menuId) {
        return service.listItemsAsTree(menuId);
    }

    @PostMapping("/menu-items")
    public MenuItem createItem(@RequestBody MenuItemRequest req) {
        return service.createItem(req);
    }

    @PutMapping("/menu-items/{id}")
    public MenuItem updateItem(@PathVariable Long id, @RequestBody MenuItemRequest req) {
        return service.updateItem(id, req);
    }

    @DeleteMapping("/menu-items/{id}")
    public void deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
    }
}
