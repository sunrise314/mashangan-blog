package com.mashangan.blog.web.vip;

import com.mashangan.blog.service.VipService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 星球会员兑换：POST /api/vip/redeem 输码换 VIP JWT，GET /api/vip/me 查会员状态。
 * 会员状态始终 200 + member 布尔，前端无需处理 401。
 */
@RestController
@RequestMapping("/api/vip")
@RequiredArgsConstructor
public class VipController {

    private final VipService vipService;

    public record RedeemRequest(String code, String nickname) {
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody RedeemRequest body) {
        VipService.RedeemResult result = vipService.redeem(body.code(), body.nickname());
        return Map.of("token", result.token(), "nickname", result.nickname(), "role", "vip");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var member = vipService.memberOf(header.substring(7));
            if (member != null) {
                return Map.of("member", true, "nickname", member.getNickname());
            }
        }
        return Map.of("member", false);
    }

    /** 把 ResponseStatusException 的 reason 包装进响应体（Spring Boot 默认 include-message=never） */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
