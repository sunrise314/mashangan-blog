/**
 * 访客采集（仅客户端）：PV / SPA 路由停留时长 / 外链点击。
 * 指纹：localStorage vid + sessionStorage sid，无 cookie、无跨站追踪。
 * 上报走 sendBeacon（降级 fetch keepalive），失败静默。
 */
export default defineNuxtPlugin((nuxtApp) => {
  const path = location.pathname;
  if (path.startsWith("/admin") || path.startsWith("/studio")) return;

  const vid = ensureId("analytics_vid", localStorage);
  const sid = ensureId("analytics_sid", sessionStorage);
  // 首个 PV 用 SSR 捕获的 Referer（搜索引擎来源），后续内部跳转为空
  const firstRef = String((nuxtApp.payload as Record<string, unknown>)?.ssrRef || "");

  const send = (payload: Record<string, unknown>): void => {
    const body = JSON.stringify({
      vid,
      sid,
      screen: `${screen.width}x${screen.height}`,
      lang: navigator.language,
      tz: Intl.DateTimeFormat().resolvedOptions().timeZone || "",
      ...payload,
    });
    try {
      const blob = new Blob([body], { type: "application/json" });
      if (navigator.sendBeacon && navigator.sendBeacon("/api/track", blob)) return;
      fetch("/api/track", {
        method: "POST",
        body,
        keepalive: true,
        headers: { "content-type": "application/json" },
      }).catch(() => {});
    } catch {
      // 采集失败不影响页面
    }
  };

  let lastPath = "";
  let lastRef = "";
  let enterTs = Date.now();
  let lastPvTs = 0;

  const trackPv = (): void => {
    const now = Date.now();
    const p = location.pathname;
    if (p === lastPath && now - lastPvTs < 2000) return; // 去重
    const ref = lastPath === "" ? firstRef : ""; // 仅会话首次带来源
    commitLeave();
    lastPath = p;
    lastRef = ref;
    enterTs = now;
    lastPvTs = now;
    send({ type: "pv", path: p, title: document.title.slice(0, 200), ref });
  };

  const commitLeave = (): void => {
    if (!lastPath) return;
    const dur = Math.round((Date.now() - enterTs) / 1000);
    if (dur >= 1) {
      send({ type: "pv", leave: true, path: lastPath, ref: lastRef, dur });
    }
  };

  // 首次 PV + 后续路由切换
  trackPv();
  nuxtApp.hook("page:finish", trackPv);

  // 离开页面时上报停留时长
  addEventListener("pagehide", commitLeave);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") commitLeave();
  });

  // 外链点击
  document.addEventListener(
    "click",
    (e) => {
      const target = e.target as HTMLElement | null;
      const a = target?.closest?.("a[href]") as HTMLAnchorElement | null;
      if (!a) return;
      try {
        const u = new URL(a.href, location.href);
        if (u.host !== location.host && /^https?:$/.test(u.protocol)) {
          send({
            type: "click",
            path: lastPath || location.pathname,
            extra: { href: a.href.slice(0, 500) },
          });
        }
      } catch {
        // 非 http(s) 链接忽略
      }
    },
    true,
  );

  function ensureId(key: string, store: Storage): string {
    let id = "";
    try {
      id = store.getItem(key) || "";
      if (!id) {
        id = crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
        store.setItem(key, id);
      }
    } catch {
      id = "anon";
    }
    return id;
  }
});
