package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mashangan.blog.domain.entity.VipCode;
import com.mashangan.blog.domain.entity.VipMember;
import com.mashangan.blog.mapper.VipCodeMapper;
import com.mashangan.blog.mapper.VipMemberMapper;
import com.mashangan.blog.security.AdminPrincipal;
import com.mashangan.blog.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VipService {

    private final VipCodeMapper codeMapper;
    private final VipMemberMapper memberMapper;
    private final JwtService jwtService;

    public record RedeemResult(String token, String nickname) {
    }

    /**
     * 兑换：校验码（启用且名额未满）→ 原子占用名额（used_count+1 带 used_count<max_uses 条件，
     * 并发下只有未满者成功）→ 建会员记录 → 签发 VIP JWT。
     */
    public RedeemResult redeem(String rawCode, String nickname) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入兑换码");
        }
        VipCode vipCode = codeMapper.selectOne(new QueryWrapper<VipCode>().eq("code", code));
        if (vipCode == null || !Boolean.TRUE.equals(vipCode.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兑换码无效");
        }
        int occupied = codeMapper.update(null, new UpdateWrapper<VipCode>()
                .setSql("used_count = used_count + 1")
                .eq("id", vipCode.getId())
                .eq("enabled", true)
                .apply("used_count < max_uses"));
        if (occupied == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该兑换码激活名额已用完");
        }
        VipMember member = new VipMember();
        member.setCodeId(vipCode.getId());
        String name = nickname == null || nickname.isBlank() ? "星球会员" : nickname.trim();
        if (name.length() > 128) name = name.substring(0, 128);
        member.setNickname(name);
        member.setEnabled(true);
        memberMapper.insert(member);
        return new RedeemResult(jwtService.generateVip(member.getId(), name), name);
    }

    /** Bearer 令牌 → 有效会员（存在且未冻结），否则 null。 */
    public VipMember memberOf(String token) {
        var claims = jwtService.parse(token);
        if (claims == null || !"vip".equals(claims.get("role", String.class))) return null;
        try {
            VipMember member = memberMapper.selectById(Long.valueOf(claims.getSubject()));
            return member != null && Boolean.TRUE.equals(member.getEnabled()) ? member : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 当前请求是否携带有效 VIP 身份（JwtAuthFilter 已把 role=vip 的令牌设进 SecurityContext），
     * 实时复查冻结状态，供付费墙解锁判断使用。
     */
    public boolean currentRequestIsVip() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AdminPrincipal principal)) return false;
        if (!"vip".equals(principal.role())) return false;
        VipMember member = memberMapper.selectById(principal.id());
        return member != null && Boolean.TRUE.equals(member.getEnabled());
    }
}
