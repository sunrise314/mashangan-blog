/**
 * 正文代码块增强（仅客户端）：highlight.js 语法高亮 + 语言标签 + 一键复制工具条。
 * highlight.js 走动态 import，不进首屏 bundle；工具条 DOM 由客户端注入，可重复调用。
 */

type HLJSApi = (typeof import("highlight.js"))["default"];

let hljsPromise: Promise<HLJSApi> | null = null;

async function loadHljs(): Promise<HLJSApi> {
  if (!hljsPromise) {
    hljsPromise = Promise.all([
      import("highlight.js/lib/common"),
      import("highlight.js/lib/languages/dockerfile"),
      import("highlight.js/lib/languages/nginx"),
    ]).then(([common, dockerfile, nginx]) => {
      const hljs = common.default;
      hljs.registerLanguage("dockerfile", dockerfile.default);
      hljs.registerLanguage("nginx", nginx.default);
      return hljs;
    });
  }
  return hljsPromise;
}

/** 语言别名：展示名更友好 */
const LANG_ALIAS: Record<string, string> = {
  xml: "html",
  shell: "bash",
  plaintext: "text",
  ini: "conf",
};

export function useCodeEnhance() {
  /** 处理容器内所有 .prose-halo pre：高亮 + 包工具条；已处理过的跳过 */
  async function enhance(root: HTMLElement | null) {
    if (!root || import.meta.server) return;
    const pres = Array.from(root.querySelectorAll<HTMLElement>(".prose-halo pre"));
    if (!pres.length) return;

    let hljs: HLJSApi;
    try {
      hljs = await loadHljs();
    } catch {
      return; // 加载失败降级为纯文本展示
    }

    for (const pre of pres) {
      const code = pre.querySelector("code");
      if (!code || pre.closest(".code-block")) continue;

      // 语法高亮：有 language-xxx class 按指定语言，否则自动检测
      let lang = /language-([\w+-]+)/.exec(code.className)?.[1]?.toLowerCase() ?? "";
      if (!code.classList.contains("hljs")) {
        try {
          hljs.highlightElement(code);
          lang =
            lang ||
            /language-([\w+-]+)/.exec(code.className)?.[1]?.toLowerCase() ||
            "";
        } catch {
          // 单块高亮失败不影响其他块
        }
      }

      // 包一层工具条：语言名 + 复制按钮
      const wrapper = document.createElement("div");
      wrapper.className = "code-block";
      const head = document.createElement("div");
      head.className = "code-head";
      const langEl = document.createElement("span");
      langEl.className = "code-lang";
      langEl.textContent = LANG_ALIAS[lang] ?? lang;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "code-copy";
      btn.textContent = "复制";
      btn.setAttribute("aria-label", "复制代码");
      btn.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(pre.innerText);
          btn.textContent = "已复制";
        } catch {
          btn.textContent = "复制失败";
        }
        setTimeout(() => {
          btn.textContent = "复制";
        }, 2000);
      });
      head.append(langEl, btn);
      pre.replaceWith(wrapper);
      wrapper.append(head, pre);
    }
  }

  return { enhance };
}
