package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Industry;
import com.mashangan.blog.domain.entity.Project;
import com.mashangan.blog.domain.entity.ProjectExamPoint;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.mapper.IndustryMapper;
import com.mashangan.blog.mapper.ProjectExamPointMapper;
import com.mashangan.blog.mapper.ProjectMapper;
import com.mashangan.blog.mapper.SeriesMapper;
import com.mashangan.blog.web.admin.dto.ExamPointRequest;
import com.mashangan.blog.web.admin.dto.ProjectAdminResponse;
import com.mashangan.blog.web.admin.dto.ProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目卡片后台管理：行业专栏下的项目卡（project）+ 面试考点（project_exam_point）。
 * seriesId 非空 = 已开更（挂真实系列），NULL = 占位（筹备中），与前台展示逻辑一致。
 */
@Service
@RequiredArgsConstructor
public class AdminProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectExamPointMapper examPointMapper;
    private final IndustryMapper industryMapper;
    private final SeriesMapper seriesMapper;

    public List<ProjectAdminResponse> list() {
        List<Project> projects = projectMapper.selectList(new QueryWrapper<Project>()
                .orderByAsc("industry_id")
                .orderByAsc("sort")
                .orderByAsc("id"));
        Map<Long, Industry> industryById = industryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Industry::getId, Function.identity()));
        Map<Long, Series> seriesById = seriesMapper.selectList(null).stream()
                .collect(Collectors.toMap(Series::getId, Function.identity()));
        Map<Long, Long> pointCounts = examPointMapper.selectMaps(new QueryWrapper<ProjectExamPoint>()
                        .select("project_id AS pid, COUNT(*) AS cnt")
                        .groupBy("project_id")).stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("pid")).longValue(),
                        m -> ((Number) m.get("cnt")).longValue()));
        return projects.stream()
                .map(p -> toResponse(p, industryById, seriesById,
                        pointCounts.getOrDefault(p.getId(), 0L).intValue()))
                .toList();
    }

    public ProjectAdminResponse get(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        int count = Math.toIntExact(examPointMapper.selectCount(
                new QueryWrapper<ProjectExamPoint>().eq("project_id", id)));
        return toResponse(p,
                Map.of(p.getIndustryId(), industryMapper.selectById(p.getIndustryId())),
                p.getSeriesId() == null ? Map.of() : Map.of(p.getSeriesId(), seriesMapper.selectById(p.getSeriesId())),
                count);
    }

    @Transactional
    public ProjectAdminResponse create(ProjectRequest req) {
        Project p = new Project();
        apply(p, req);
        p.setCreatedAt(OffsetDateTime.now());
        projectMapper.insert(p);
        replaceExamPoints(p.getId(), req.examPoints());
        return get(p.getId());
    }

    @Transactional
    public ProjectAdminResponse update(Long id, ProjectRequest req) {
        Project p = projectMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        apply(p, req);
        projectMapper.updateById(p);
        replaceExamPoints(id, req.examPoints());
        return get(id);
    }

    /**
     * 删除项目卡：考点经外键 ON DELETE CASCADE 自动清理；
     * 关联系列经 posts/project 的 series_id ON DELETE SET NULL 解除，文章保留。
     */
    @Transactional
    public void delete(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        projectMapper.deleteById(id);
    }

    /** 考点整批替换：删旧插新，前端弹窗一次提交全量。 */
    @Transactional
    public void replaceExamPoints(Long projectId, List<ExamPointRequest> points) {
        examPointMapper.delete(new QueryWrapper<ProjectExamPoint>().eq("project_id", projectId));
        if (points == null) return;
        int i = 0;
        for (ExamPointRequest e : points) {
            if (e.point() == null || e.point().isBlank()) continue;
            ProjectExamPoint row = new ProjectExamPoint();
            row.setProjectId(projectId);
            row.setSort(e.sort() != null ? e.sort() : ++i);
            row.setPoint(e.point().trim());
            row.setDetail(e.detail() != null ? e.detail() : "");
            examPointMapper.insert(row);
        }
    }

    private ProjectAdminResponse toResponse(Project p, Map<Long, Industry> industryById,
                                            Map<Long, Series> seriesById, int pointCount) {
        Industry ind = industryById.get(p.getIndustryId());
        Series s = p.getSeriesId() == null ? null : seriesById.get(p.getSeriesId());
        List<ExamPointRequest> points = examPointMapper.selectList(new QueryWrapper<ProjectExamPoint>()
                        .eq("project_id", p.getId()).orderByAsc("sort")).stream()
                .map(e -> new ExamPointRequest(e.getSort(), e.getPoint(), e.getDetail()))
                .toList();
        return new ProjectAdminResponse(p.getId(), p.getIndustryId(),
                ind == null ? null : ind.getName(),
                p.getSeriesId(), s == null ? null : s.getTitle(),
                p.getSlug(), p.getTitle(), p.getJdFreq(), p.getSummary(), p.getSort(),
                p.getVisible(), p.getCreatedAt(), points);
    }

    private void apply(Project p, ProjectRequest req) {
        if (req.industryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须选择所属行业");
        }
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        p.setIndustryId(req.industryId());
        p.setSeriesId(req.seriesId());
        p.setSlug(req.slug() != null ? req.slug().trim() : "");
        p.setTitle(req.title().trim());
        p.setJdFreq(req.jdFreq() != null ? req.jdFreq() : "高频");
        p.setSummary(req.summary() != null ? req.summary() : "");
        p.setSort(req.sort() != null ? req.sort() : 0);
        p.setVisible(req.visible() == null || req.visible());
    }
}
