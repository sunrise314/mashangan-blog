// 星球会员（VIP）状态管理：令牌存 localStorage，与后台 admin token 完全分离
export interface VipStatus {
  member: boolean;
  nickname?: string;
}

const TOKEN_KEY = "vip_token";

export function useVip() {
  function readToken(): string {
    if (import.meta.client) return localStorage.getItem(TOKEN_KEY) || "";
    return "";
  }

  /** 当前是否有效会员（令牌存在且服务端确认未冻结） */
  async function fetchStatus(): Promise<VipStatus> {
    const t = readToken();
    if (!t) return { member: false };
    try {
      return await $fetch<VipStatus>("/api/vip/me", {
        headers: { Authorization: `Bearer ${t}` },
      });
    } catch {
      return { member: false };
    }
  }

  /** 输码兑换：成功后令牌落盘 */
  async function redeem(
    code: string,
    nickname: string,
  ): Promise<{ ok: boolean; message?: string; nickname?: string }> {
    try {
      const res = await $fetch<{ token: string; nickname: string }>("/api/vip/redeem", {
        method: "POST",
        body: { code, nickname },
      });
      if (import.meta.client) localStorage.setItem(TOKEN_KEY, res.token);
      return { ok: true, nickname: res.nickname };
    } catch (e: any) {
      return { ok: false, message: e?.data?.message || "兑换失败，请稍后再试" };
    }
  }

  function logout() {
    if (import.meta.client) localStorage.removeItem(TOKEN_KEY);
  }

  return { readToken, fetchStatus, redeem, logout };
}
