package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Industry;
import com.mashangan.blog.domain.entity.Project;
import com.mashangan.blog.mapper.IndustryMapper;
import com.mashangan.blog.mapper.ProjectMapper;
import com.mashangan.blog.web.admin.dto.IndustryAdminResponse;
import com.mashangan.blog.web.admin.dto.IndustryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 行业后台管理。删除保护：行业下有项目卡时直接拒绝
 * （project.industry_id 外键是 CASCADE，误删行业会连卡片一起没了，不做 force）。
 */
@Service
@RequiredArgsConstructor
public class AdminIndustryService {

    private final IndustryMapper industryMapper;
    private final ProjectMapper projectMapper;

    public List<IndustryAdminResponse> list() {
        List<Industry> industries = industryMapper.selectList(new QueryWrapper<Industry>()
                .orderByAsc("sort")
                .orderByAsc("id"));
        return industries.stream()
                .map(i -> new IndustryAdminResponse(i.getId(), i.getSlug(), i.getName(),
                        i.getIntro(), i.getSort(), i.getVisible(), i.getCreatedAt(),
                        projectMapper.selectCount(new QueryWrapper<Project>().eq("industry_id", i.getId()))))
                .toList();
    }

    @Transactional
    public IndustryAdminResponse create(IndustryRequest req) {
        Industry i = new Industry();
        apply(i, req);
        i.setCreatedAt(OffsetDateTime.now());
        industryMapper.insert(i);
        return toResponse(i);
    }

    @Transactional
    public IndustryAdminResponse update(Long id, IndustryRequest req) {
        Industry i = industryMapper.selectById(id);
        if (i == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        apply(i, req);
        industryMapper.updateById(i);
        return toResponse(i);
    }

    @Transactional
    public void delete(Long id) {
        Industry i = industryMapper.selectById(id);
        if (i == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        long count = projectMapper.selectCount(new QueryWrapper<Project>().eq("industry_id", id));
        if (count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该行业下有 " + count + " 张项目卡片，请先移走或删除卡片后再删除行业。");
        }
        industryMapper.deleteById(id);
    }

    private IndustryAdminResponse toResponse(Industry i) {
        return new IndustryAdminResponse(i.getId(), i.getSlug(), i.getName(),
                i.getIntro(), i.getSort(), i.getVisible(), i.getCreatedAt(),
                projectMapper.selectCount(new QueryWrapper<Project>().eq("industry_id", i.getId())));
    }

    private void apply(Industry i, IndustryRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行业名称不能为空");
        }
        i.setName(req.name().trim());
        i.setSlug(req.slug() != null ? req.slug().trim() : "");
        i.setIntro(req.intro() != null ? req.intro() : "");
        i.setSort(req.sort() != null ? req.sort() : 0);
        i.setVisible(req.visible() == null || req.visible());
    }
}
