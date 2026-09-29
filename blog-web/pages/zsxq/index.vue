<template>
  <div class="bg-slate-50 min-h-full">
    <!-- Hero -->
    <div class="bg-gradient-to-br from-[#0b4f8a] to-[#0562a9] text-white">
      <div class="max-w-5xl mx-auto px-4 py-14 text-center">
        <h1 class="text-3xl sm:text-4xl font-bold">{{ zsxqConfig.title }}</h1>
        <p class="mt-4 max-w-2xl mx-auto text-blue-100 leading-relaxed">
          {{ zsxqConfig.subtitle }}
        </p>
        <div class="mt-6 flex flex-wrap justify-center gap-3">
          <span
            v-for="s in zsxqConfig.stats"
            :key="s.label"
            class="px-4 py-2 rounded-full bg-white/10 backdrop-blur text-sm"
          >
            <strong class="text-lg mr-1">{{ s.number }}</strong>{{ s.label }}
          </span>
        </div>
        <a
          :href="zsxqConfig.joinUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="mt-8 inline-block px-8 py-3 rounded-full bg-white text-[#0562a9] font-semibold hover:bg-blue-50 transition-colors"
        >
          {{ zsxqConfig.joinButtonText }}
        </a>
      </div>
    </div>

    <div class="max-w-5xl mx-auto px-4 py-12 space-y-12">
      <!-- 实战项目 -->
      <section>
        <h2 class="text-2xl font-bold text-slate-900 mb-6 flex items-center gap-2">
          <span class="w-1.5 h-7 bg-[#0562a9] rounded-full"></span>
          实战项目
        </h2>
        <div
          v-for="p in zsxqConfig.projects"
          :key="p.name"
          class="bg-white rounded-xl border border-slate-200 p-6"
        >
          <div class="flex items-center flex-wrap gap-3">
            <h3 class="text-lg font-semibold text-slate-900">{{ p.name }}</h3>
            <span
              :class="[
                'text-xs px-2.5 py-1 rounded-full font-medium',
                p.status === 'complete'
                  ? 'bg-green-50 text-green-600'
                  : 'bg-orange-50 text-orange-600',
              ]"
            >
              {{ p.status === "complete" ? "已完结" : "更新中" }}
            </span>
          </div>
          <p class="mt-3 text-slate-600 leading-relaxed">{{ p.desc }}</p>
          <div class="mt-4 flex flex-wrap gap-2">
            <span
              v-for="tag in p.tags"
              :key="tag"
              class="text-xs px-2.5 py-1 rounded-md bg-slate-100 text-slate-600"
            >
              {{ tag }}
            </span>
          </div>
        </div>
      </section>

      <!-- 权益 -->
      <section>
        <h2 class="text-2xl font-bold text-slate-900 mb-6 flex items-center gap-2">
          <span class="w-1.5 h-7 bg-[#0562a9] rounded-full"></span>
          加入后你将获得
        </h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div
            v-for="b in zsxqConfig.benefits"
            :key="b.title"
            class="bg-white rounded-xl border border-slate-200 p-5 hover:border-[#0562a9] transition-colors"
          >
            <h3 class="font-semibold text-slate-900">{{ b.title }}</h3>
            <p class="mt-2 text-sm text-slate-500 leading-relaxed">{{ b.desc }}</p>
          </div>
        </div>
      </section>

      <!-- 底部 CTA -->
      <div class="text-center pb-4">
        <a
          :href="zsxqConfig.joinUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="inline-block px-10 py-3.5 rounded-full bg-[#0562a9] text-white font-semibold hover:bg-[#0b4f8a] transition-colors shadow-lg shadow-[#0562a9]/25"
        >
          {{ zsxqConfig.joinButtonText }} →
        </a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { zsxqConfig } from "~/data/zsxq";

useHead(() => ({ title: zsxqConfig.title }));
</script>
