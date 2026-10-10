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
import com.mashangan.blog.web.halo.dto.IndustryDetail;
import com.mashangan.blog.web.halo.dto.ProjectDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行业项目地图查询：/column 行业 chips 筛选、行业详情页与筹备中项目考点清单页。
 * 数据量小（12 行业 / 数十项目），全量加载后内存组装即可。
 */
@Service
@RequiredArgsConstructor
public class IndustryProjectService {

    private final IndustryMapper industryMapper;
    private final ProjectMapper projectMapper;
    private final ProjectExamPointMapper examPointMapper;
    private final SeriesMapper seriesMapper;

    /** 全部行业（含各行业项目卡片），/column 总览页一次拉全做前端筛选 */
    public List<IndustryDetail> listIndustryDetails() {
        List<Industry> industries = listIndustries();
        List<Project> projects = listProjects();
        Map<Long, List<Project>> byIndustry = projects.stream()
                .collect(Collectors.groupingBy(Project::getIndustryId));
        return industries.stream()
                .map(i -> toDetail(i, byIndustry.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    /** 单个行业详情（含项目卡片），行业页使用 */
    public IndustryDetail getIndustryDetail(String slug) {
        Industry industry = industryMapper.selectOne(new QueryWrapper<Industry>().eq("slug", slug));
        if (industry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Project> projects = projectMapper.selectList(new QueryWrapper<Project>()
                .eq("industry_id", industry.getId())
                .orderByAsc("sort")
                .orderByAsc("id"));
        return toDetail(industry, projects);
    }

    /** 项目详情（含考点清单），筹备中项目落地页使用 */
    public ProjectDetail getProjectDetail(String industrySlug, String projectSlug) {
        Project project = projectMapper.selectOne(new QueryWrapper<Project>().eq("slug", projectSlug));
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Industry industry = industryMapper.selectById(project.getIndustryId());
        if (industry == null || !industry.getSlug().equals(industrySlug)) {
            // 行业与项目不匹配（避免同一项目在错误行业 URL 下被收录）
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Series series = project.getSeriesId() == null ? null
                : seriesMapper.selectById(project.getSeriesId());
        List<ProjectExamPoint> points = examPointMapper.selectList(new QueryWrapper<ProjectExamPoint>()
                .eq("project_id", project.getId())
                .orderByAsc("sort")
                .orderByAsc("id"));
        return new ProjectDetail(
                project.getSlug(), project.getTitle(), project.getJdFreq(), project.getSummary(),
                project.getSeriesId() != null,
                series == null ? null : series.getSlug(),
                series == null ? null : series.getTitle(),
                industry.getSlug(), industry.getName(),
                points.stream()
                        .map(p -> new ProjectDetail.ExamPoint(p.getSort(), p.getPoint(), p.getDetail()))
                        .toList());
    }

    private List<Industry> listIndustries() {
        return industryMapper.selectList(new QueryWrapper<Industry>()
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    private List<Project> listProjects() {
        return projectMapper.selectList(new QueryWrapper<Project>()
                .orderByAsc("industry_id")
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    /** 组装行业详情：项目卡片 + 已开更系列的 slug/title + 各项目考点数 */
    private IndustryDetail toDetail(Industry industry, List<Project> projects) {
        List<Long> seriesIds = projects.stream().map(Project::getSeriesId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Series> seriesById = seriesIds.isEmpty() ? Map.of()
                : seriesMapper.selectBatchIds(seriesIds).stream()
                        .collect(Collectors.toMap(Series::getId, Function.identity()));
        Map<Long, Integer> examCount = examPointCounts(projects);
        List<IndustryDetail.ProjectCard> cards = projects.stream()
                .map(p -> {
                    Series s = p.getSeriesId() == null ? null : seriesById.get(p.getSeriesId());
                    return new IndustryDetail.ProjectCard(
                            p.getSlug(), p.getTitle(), p.getJdFreq(), p.getSummary(),
                            p.getSeriesId() != null,
                            s == null ? null : s.getSlug(),
                            s == null ? null : s.getTitle(),
                            examCount.getOrDefault(p.getId(), 0));
                })
                .toList();
        return new IndustryDetail(industry.getSlug(), industry.getName(),
                industry.getIntro(), cards);
    }

    private Map<Long, Integer> examPointCounts(List<Project> projects) {
        if (projects.isEmpty()) return Map.of();
        List<Long> ids = projects.stream().map(Project::getId).toList();
        List<Map<String, Object>> rows = examPointMapper.selectMaps(new QueryWrapper<ProjectExamPoint>()
                .in("project_id", ids)
                .select("project_id AS pid, COUNT(*) AS cnt")
                .groupBy("project_id"));
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("pid")).longValue(),
                    ((Number) row.get("cnt")).intValue());
        }
        return result;
    }
}
