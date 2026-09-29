package com.mashangan.blog.studio;

/** AI 配图提供商的状态快照。 */
public record ProviderStatus(
        String name,         // 内部标识：siliconflow / zhipu / pexels
        String displayName,  // 页面显示名
        boolean configured,  // 是否配置了 API Key
        String keyMasked,    // 脱敏后的 key
        String balance,      // 余额或免费额度说明
        String rateLimit,    // 实时限流/剩余量
        String error         // 连通性错误信息（null 表示正常或未测试）
) {
    public static ProviderStatus of(String name, String displayName, boolean configured, String keyMasked) {
        return new ProviderStatus(name, displayName, configured, keyMasked, null, null, null);
    }

    public static ProviderStatus unknown(String name) {
        return new ProviderStatus(name, "未知提供商", false, "—", null, null, "不支持的提供商");
    }

    public ProviderStatus withBalance(String balance) {
        return new ProviderStatus(name, displayName, configured, keyMasked, balance, rateLimit, error);
    }

    public ProviderStatus withRateLimit(String rateLimit) {
        return new ProviderStatus(name, displayName, configured, keyMasked, balance, rateLimit, error);
    }

    public ProviderStatus withError(String error) {
        return new ProviderStatus(name, displayName, configured, keyMasked, balance, rateLimit, error);
    }
}
