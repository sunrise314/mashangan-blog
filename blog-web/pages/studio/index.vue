<script setup lang="ts">
// 内部工具页：md 一键 AI 配图发布。口令保护 + noindex。
interface ImagePointView {
  id: string;
  type: "cover" | "content";
  blockNo: number;
  scene: string;
  caption: string;
  kind: "photo" | "illustration";
  status: "pending" | "done" | "failed";
  source?: "pexels" | "ai";
  imageUrl?: string;
  error?: string;
}

interface TaskView {
  id: string;
  status: "running" | "done" | "error";
  step: string;
  percent: number;
  message: string;
  title: string;
  slug: string;
  points: ImagePointView[];
  coverUrl?: string;
  postName?: string;
  permalink?: string;
  warnings: string[];
  error?: string;
}

interface CategoryView {
  name: string;
  displayName: string;
  slug: string;
}

const STORAGE_KEY = "studio_token";

const token = ref("");
const authed = ref(false);
const markdown = ref("");
const title = ref("");
const slug = ref("");
const categorySlug = ref("default");
const categories = ref<CategoryView[]>([]);
const imageCount = ref(3);
const style = ref("简约通用");
const task = ref<TaskView | null>(null);
const error = ref("");
const busy = ref(false);
let pollTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(() => {
  token.value = localStorage.getItem(STORAGE_KEY) || "";
  if (token.value) void tryAuth();
});

async function tryAuth() {
  try {
    const res = await $fetch<{ categories: CategoryView[] }>("/api/studio/categories", {
      headers: { "x-studio-token": token.value },
    });
    categories.value = res.categories;
    authed.value = true;
    localStorage.setItem(STORAGE_KEY, token.value);
  } catch {
    authed.value = false;
  }
}

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    markdown.value = String(reader.result || "");
    if (!title.value) title.value = file.name.replace(/\.(md|markdown)$/i, "");
  };
  reader.readAsText(file, "utf-8");
}

function authHeaders() {
  return { "x-studio-token": token.value };
}

function pollTask(id: string) {
  pollTimer = setTimeout(async () => {
    try {
      const t = await $fetch<TaskView>(`/api/studio/status/${id}`, { headers: authHeaders() });
      task.value = t;
      if (t.status === "running") pollTask(id);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "轮询失败";
    }
  }, 1000);
}

async function submit() {
  error.value = "";
  if (!markdown.value.trim()) {
    error.value = "请输入或选择 Markdown 文章内容";
    return;
  }
  busy.value = true;
  task.value = null;
  try {
    const res = await $fetch<{ taskId: string }>("/api/studio/import", {
      method: "POST",
      headers: authHeaders(),
      body: {
        markdown: markdown.value,
        title: title.value || undefined,
        slug: slug.value || undefined,
        categorySlug: categorySlug.value || undefined,
        imageCount: imageCount.value,
        style: style.value,
      },
    });
    pollTask(res.taskId);
  } catch (e: unknown) {
    const err = e as { statusMessage?: string; data?: { statusMessage?: string } };
    error.value = err.statusMessage || err.data?.statusMessage || "提交失败";
  } finally {
    busy.value = false;
  }
}

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer);
});

useHead({ title: "AI 配图工作台", meta: [{ name: "robots", content: "noindex, nofollow" }] });
definePageMeta({ layout: false });
</script>

<template>
  <div class="min-h-screen bg-gray-50 text-gray-900">
    <div class="mx-auto max-w-4xl px-4 py-8">
      <header class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-bold">AI 配图工作台</h1>
        <NuxtLink to="/" class="text-sm text-gray-500 hover:text-gray-800">← 返回站点</NuxtLink>
      </header>

      <!-- 口令 -->
      <div v-if="!authed" class="mx-auto max-w-sm rounded-lg border bg-white p-6">
        <label class="mb-2 block text-sm font-medium">访问口令</label>
        <input
          v-model="token"
          type="password"
          class="mb-3 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
          placeholder="请输入工具口令"
          @keyup.enter="tryAuth"
        />
        <button
          class="w-full rounded bg-blue-600 py-2 text-white hover:bg-blue-700"
          @click="tryAuth"
        >
          进入
        </button>
      </div>

      <template v-else>
        <!-- 提交表单 -->
        <div class="rounded-lg border bg-white p-6">
          <div class="mb-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium">标题（留空自动取首个 # 标题）</label>
              <input v-model="title" class="w-full rounded border border-gray-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium">slug（留空自动生成）</label>
              <input v-model="slug" class="w-full rounded border border-gray-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium">分类</label>
              <select v-model="categorySlug" class="w-full rounded border border-gray-300 px-3 py-2 text-sm">
                <option v-for="c in categories" :key="c.slug" :value="c.slug">{{ c.displayName }}</option>
              </select>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="mb-1 block text-sm font-medium">配图数（含封面）</label>
                <input
                  v-model.number="imageCount"
                  type="number"
                  min="1"
                  max="10"
                  class="w-full rounded border border-gray-300 px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium">风格</label>
                <select v-model="style" class="w-full rounded border border-gray-300 px-3 py-2 text-sm">
                  <option>简约通用</option>
                  <option>写实照片</option>
                  <option>扁平插画</option>
                  <option>科技线稿</option>
                  <option>水墨风</option>
                </select>
              </div>
            </div>
          </div>

          <label class="mb-1 block text-sm font-medium">Markdown 正文</label>
          <textarea
            v-model="markdown"
            rows="12"
            class="w-full rounded border border-gray-300 px-3 py-2 font-mono text-sm focus:border-blue-500 focus:outline-none"
            placeholder="粘贴 Markdown 内容，或点击下方选择 .md 文件"
          ></textarea>
          <div class="mt-2 flex items-center justify-between">
            <label class="cursor-pointer text-sm text-blue-600 hover:underline">
              选择 .md 文件
              <input type="file" accept=".md,.markdown,text/markdown" class="hidden" @change="onFileChange" />
            </label>
            <span class="text-xs text-gray-400">{{ markdown.length }} 字符</span>
          </div>

          <div class="mt-4 flex items-center gap-3">
            <button
              class="rounded bg-blue-600 px-6 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              :disabled="busy || task?.status === 'running'"
              @click="submit"
            >
              开始 AI 配图并发布
            </button>
            <button
              v-if="task && task.status !== 'running'"
              class="rounded border px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
              @click="task = null; error = ''"
            >
              清除结果
            </button>
          </div>
          <p v-if="error" class="mt-3 rounded bg-red-50 px-3 py-2 text-sm text-red-600">{{ error }}</p>
        </div>

        <!-- 任务进度 -->
        <div v-if="task" class="mt-6 rounded-lg border bg-white p-6">
          <div class="mb-2 flex items-center justify-between">
            <span class="font-medium">
              {{ task.status === "done" ? "✅ 发布成功" : task.status === "error" ? "❌ 失败" : "⏳ 进行中" }}
            </span>
            <span class="text-sm text-gray-500">{{ task.message }}</span>
          </div>
          <div class="h-2 w-full overflow-hidden rounded bg-gray-100">
            <div
              class="h-full rounded bg-blue-600 transition-all duration-500"
              :style="{ width: task.percent + '%' }"
              :class="{ 'bg-green-600': task.status === 'done', 'bg-red-600': task.status === 'error' }"
            ></div>
          </div>

          <ul v-if="task.warnings?.length" class="mt-3 list-disc pl-5 text-sm text-amber-600">
            <li v-for="(w, i) in task.warnings" :key="i">{{ w }}</li>
          </ul>

          <p v-if="task.status === 'error'" class="mt-3 rounded bg-red-50 px-3 py-2 text-sm text-red-600">
            {{ task.error }}
          </p>

          <div v-if="task.status === 'done' && task.permalink" class="mt-4 rounded bg-green-50 px-4 py-3">
            <p class="text-sm text-gray-700">
              文章已发布：
              <NuxtLink :to="task.permalink" class="font-medium text-blue-600 hover:underline" target="_blank">
                {{ task.permalink }}
              </NuxtLink>
            </p>
            <p class="mt-1 text-xs text-gray-500">
              控制台编辑：
              <a
                :href="`http://49.235.136.65:8090/console/posts/editor?name=${task.postName}`"
                class="text-blue-600 hover:underline"
                target="_blank"
                rel="noopener"
              >打开编辑器</a>
            </p>
          </div>

          <!-- 点位明细 -->
          <div v-if="task.points?.length" class="mt-4 space-y-3">
            <div
              v-for="p in task.points"
              :key="p.id"
              class="flex items-start gap-3 rounded border p-3"
            >
              <img
                v-if="p.imageUrl"
                :src="p.imageUrl"
                class="h-16 w-28 shrink-0 rounded object-cover"
                loading="lazy"
              />
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium">
                  {{ p.type === "cover" ? "🖼 封面" : `📍 插图 · 块 ${p.blockNo}` }}
                  <span class="ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500">
                    {{ p.source === "pexels" ? "图库" : p.source === "ai" ? "AI 生成" : p.kind }}
                  </span>
                  <span
                    v-if="p.status === 'failed'"
                    class="ml-1 rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-600"
                  >失败</span>
                </p>
                <p class="truncate text-xs text-gray-500" :title="p.scene">{{ p.scene }}</p>
                <p v-if="p.error" class="text-xs text-red-500">{{ p.error }}</p>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
