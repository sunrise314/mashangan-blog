package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.SocialLink;
import com.mashangan.blog.mapper.SocialLinkMapper;
import com.mashangan.blog.web.admin.dto.SocialLinkRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialLinkService {

    private final SocialLinkMapper socialLinkMapper;

    public List<SocialLink> list() {
        return socialLinkMapper.selectList(new QueryWrapper<SocialLink>()
                .orderByDesc("enabled").orderByAsc("priority").orderByDesc("created_at"));
    }

    public SocialLink create(SocialLinkRequest req) {
        SocialLink s = new SocialLink();
        apply(s, req);
        s.setCreatedAt(OffsetDateTime.now());
        socialLinkMapper.insert(s);
        return s;
    }

    public SocialLink update(Long id, SocialLinkRequest req) {
        SocialLink s = socialLinkMapper.selectById(id);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        apply(s, req);
        socialLinkMapper.updateById(s);
        return s;
    }

    public void delete(Long id) {
        socialLinkMapper.deleteById(id);
    }

    private void apply(SocialLink s, SocialLinkRequest req) {
        s.setPlatform(req.platform());
        s.setLabel(req.label());
        s.setUrl(req.url());
        s.setIconClass(req.iconClass() == null ? "" : req.iconClass());
        s.setPriority(req.priority() == null ? 0 : req.priority());
        s.setEnabled(req.enabled() == null || req.enabled());
    }
}
