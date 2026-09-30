package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.AdminNav;
import com.mashangan.blog.mapper.AdminNavMapper;
import com.mashangan.blog.web.admin.dto.AdminNavRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 后台 SPA 侧边栏导航 CRUD。仅可见项会返回给前端，但后台管理端可看全部。
 */
@Service
@RequiredArgsConstructor
public class AdminNavService {

    private final AdminNavMapper mapper;

    /** 侧边栏可见导航项（按 sortOrder 升序）。 */
    public List<AdminNav> listVisible() {
        return mapper.selectList(new QueryWrapper<AdminNav>()
                .eq("visible", true)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    /** 全部导航项（含隐藏，管理端使用）。 */
    public List<AdminNav> listAll() {
        return mapper.selectList(new QueryWrapper<AdminNav>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public AdminNav get(Long id) {
        AdminNav nav = mapper.selectById(id);
        if (nav == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导航项不存在");
        return nav;
    }

    @Transactional
    public AdminNav create(AdminNavRequest req) {
        AdminNav nav = new AdminNav();
        apply(nav, req);
        mapper.insert(nav);
        return nav;
    }

    @Transactional
    public AdminNav update(Long id, AdminNavRequest req) {
        AdminNav nav = get(id);
        apply(nav, req);
        mapper.updateById(nav);
        return nav;
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导航项不存在");
        }
        // parent_id ON DELETE CASCADE，子节点会被一起删
        mapper.deleteById(id);
    }

    private void apply(AdminNav nav, AdminNavRequest req) {
        nav.setParentId(req.parentId());
        nav.setMenuName(req.menuName());
        nav.setPath(req.path());
        nav.setIcon(req.icon() == null ? "" : req.icon());
        nav.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        nav.setVisible(req.visible() == null ? Boolean.TRUE : req.visible());
    }
}
