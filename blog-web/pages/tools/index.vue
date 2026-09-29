<template>
  <div class="bg-slate-50 min-h-full">
    <!-- 页头 + 锚点导航 -->
    <div class="bg-white border-b border-slate-200">
      <div class="max-w-5xl mx-auto px-4 py-10">
        <h1 class="text-3xl font-bold text-slate-900">在线工具大全</h1>
        <p class="mt-2 text-slate-500">
          收录开发与日常高频使用的在线工具，免费免安装，点开即用。工具持续收录中…
        </p>
        <nav class="mt-6 flex flex-wrap gap-2">
          <a
            v-for="g in toolGroups"
            :key="g.id"
            :href="'#' + g.id"
            class="px-3 py-1.5 rounded-full text-sm bg-slate-100 text-slate-600 hover:bg-[#0562a9] hover:text-white transition-colors"
          >
            {{ g.name }}
          </a>
        </nav>
      </div>
    </div>

    <!-- 分组卡片 -->
    <div class="max-w-5xl mx-auto px-4 py-10 space-y-10">
      <section
        v-for="g in toolGroups"
        :id="g.id"
        :key="g.id"
        class="scroll-mt-24"
      >
        <h2 class="text-xl font-bold text-slate-900 mb-4 flex items-center gap-2">
          <span class="w-1.5 h-6 bg-[#0562a9] rounded-full"></span>
          {{ g.name }}
        </h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <a
            v-for="t in g.items"
            :key="t.url"
            :href="t.url"
            target="_blank"
            rel="noopener noreferrer"
            class="group bg-white rounded-xl border border-slate-200 p-4 hover:border-[#0562a9] hover:shadow-md transition-all"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="font-medium text-slate-900 group-hover:text-[#0562a9] transition-colors">
                {{ t.name }}
              </span>
              <span
                v-if="t.tag"
                class="shrink-0 text-xs px-2 py-0.5 rounded-full bg-blue-50 text-[#0562a9]"
              >
                {{ t.tag }}
              </span>
            </div>
            <p class="mt-2 text-sm text-slate-500 leading-relaxed">{{ t.desc }}</p>
            <span
              class="mt-3 inline-flex items-center gap-1 text-xs text-slate-400 group-hover:text-[#0562a9] transition-colors"
            >
              访问工具 <span aria-hidden="true">↗</span>
            </span>
          </a>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { toolGroups } from "~/data/tools";

useHead(() => ({ title: "在线工具大全" }));
</script>
