package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.mapper.SinglePageMapper;
import com.mashangan.blog.web.halo.dto.HaloSinglePage;
import com.mashangan.blog.web.halo.dto.Meta;
import com.mashangan.blog.web.halo.dto.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SinglePageQueryService {

    private final SinglePageMapper singlePageMapper;

    public List<SinglePage> listPublished() {
        return singlePageMapper.selectList(new QueryWrapper<SinglePage>()
                .eq("published", true)
                .eq("deleted", false)
                .orderByDesc("publish_time")
                .orderByDesc("id"));
    }

    public SinglePage getPublishedByHaloName(String haloName) {
        return singlePageMapper.selectOne(new QueryWrapper<SinglePage>()
                .eq("published", true)
                .eq("deleted", false)
                .eq("halo_name", haloName)
                .last("LIMIT 1"));
    }

    /** 列表项不含正文（前端随后按 name 拉取详情） */
    public HaloSinglePage toHaloSummary(SinglePage page) {
        return toHalo(page, null);
    }

    public HaloSinglePage toHaloDetail(SinglePage page) {
        var content = new HaloSinglePage.Content(page.getContentHtml(),
                page.getContentRaw() != null ? page.getContentRaw() : page.getContentHtml());
        return toHalo(page, content);
    }

    private HaloSinglePage toHalo(SinglePage page, HaloSinglePage.Content content) {
        Meta meta = new Meta(page.getHaloName(), Time.iso(page.getCreatedAt()), null, null);
        var spec = new HaloSinglePage.Spec(page.getTitle(), page.getSlug(),
                Time.iso(page.getPublishTime()));
        var status = new HaloSinglePage.Status(HaloSinglePage.permalink(page.getSlug()),
                Time.iso(page.getUpdatedAt()), "PUBLISHED");
        return new HaloSinglePage(meta, spec, status, content);
    }
}
