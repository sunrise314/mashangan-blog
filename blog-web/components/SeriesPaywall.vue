<template>
  <div class="paywall">
    <!-- 渐变遮罩衔接正文（预览场景） -->
    <div class="paywall-fade" aria-hidden="true"></div>

    <div class="paywall-card">
      <div class="paywall-lock">
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <rect x="3" y="11" width="18" height="11" rx="2" />
          <path d="M7 11V7a5 5 0 0110 0v4" stroke-linecap="round" />
        </svg>
      </div>
      <h2>第 {{ chapterOrder }} 章为知识星球专属内容</h2>
      <p class="paywall-desc">
        《{{ seriesTitle }}》共 {{ totalChapters }} 章，前 {{ freeChapterCount }} 章可免费阅读，
        加入知识星球即可解锁全部章节（含后续更新），并附带完整源码、答疑与配套资料。
      </p>

      <a :href="joinUrl" target="_blank" rel="noopener" class="paywall-btn">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8z" stroke-linejoin="round" />
        </svg>
        加入知识星球，解锁全部 {{ totalChapters }} 章
      </a>
      <div class="paywall-sub">
        加入后永久可读 · 支持微信/支付宝
      </div>

      <NuxtLink :to="`/column/${seriesSlug}`" class="paywall-back">
        ← 返回章节目录
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { zsxqConfig } from "~/data/zsxq";

defineProps<{
  seriesSlug: string;
  seriesTitle: string;
  freeChapterCount: number;
  chapterOrder: number;
  totalChapters: number;
}>();

// 知识星球加入链接（星球专属付费通道）
const joinUrl = zsxqConfig.joinUrl;
</script>

<style scoped>
.paywall {
  position: relative;
  margin-top: 28px;
}
.paywall-fade {
  height: 140px;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0), #fff 92%);
  margin-bottom: -140px;
  position: relative;
  z-index: 1;
  pointer-events: none;
}
.paywall-card {
  position: relative;
  z-index: 2;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbfe 0%, #eef6fd 100%);
  padding: 36px 28px 30px;
  text-align: center;
}
.paywall-lock {
  width: 60px;
  height: 60px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0962a9;
  background: #fff;
  border: 1px solid #d6e9f8;
  box-shadow: 0 4px 14px rgba(9, 98, 169, 0.12);
}
.paywall-card h2 {
  margin: 0 0 10px;
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
}
.paywall-desc {
  margin: 0 auto 22px;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.9;
  color: #64748b;
}
.paywall-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 30px;
  border-radius: 999px;
  background: linear-gradient(135deg, #0962a9, #2b8ad4);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  box-shadow: 0 6px 18px rgba(9, 98, 169, 0.3);
  transition: transform 0.15s, box-shadow 0.15s;
}
.paywall-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(9, 98, 169, 0.38);
}
.paywall-sub {
  margin-top: 12px;
  font-size: 12px;
  color: #94a3b8;
}
.paywall-back {
  display: inline-block;
  margin-top: 18px;
  font-size: 13px;
  color: #0962a9;
  text-decoration: none;
}
.paywall-back:hover {
  text-decoration: underline;
}
</style>
