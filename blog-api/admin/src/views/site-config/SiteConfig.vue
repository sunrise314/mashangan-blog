<template>
  <LayoutShell>
    <div class="topbar">
      <h2>站点设置</h2>
      <button class="btn btn-primary" @click="save" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">已保存</div>

    <div class="card">
      <h3 style="margin-bottom:12px">基本信息</h3>
      <div class="form-row"><label>站点标题</label><input v-model="form.title"></div>
      <div class="form-row"><label>副标题</label><input v-model="form.subtitle"></div>
      <div class="grid-2">
        <div class="form-row"><label>Logo URL</label><input v-model="form.logoUrl"></div>
        <div class="form-row"><label>Favicon URL</label><input v-model="form.faviconUrl"></div>
      </div>
      <div class="form-row"><label>页脚文字</label><input v-model="form.footerText"></div>
      <div class="grid-2">
        <div class="form-row"><label>备案号（ICP）</label><input v-model="form.beianIcp"></div>
        <div class="form-row"><label>公安备案号</label><input v-model="form.beianPublicSecurity"></div>
      </div>
    </div>

    <div class="card">
      <h3 style="margin-bottom:12px">SEO</h3>
      <div class="form-row"><label>首页标题</label><input v-model="form.homepageTitle"></div>
      <div class="form-row"><label>首页副标题</label><input v-model="form.homepageSubtitle"></div>
      <div class="form-row"><label>SEO 描述</label><textarea v-model="form.seoDescription" rows="2"></textarea></div>
      <div class="form-row"><label>SEO 关键词</label><input v-model="form.seoKeywords"></div>
      <div class="form-row"><label>head 统计代码（如百度统计/Google Analytics 原始 script）</label>
        <textarea v-model="form.analyticsHeadCode" rows="4" style="font-family:monospace;font-size:12px"></textarea>
      </div>
    </div>

    <div class="card" style="background:#f8fafc;border-left:3px solid #94a3b8">
      <h3 style="margin-bottom:4px">AI 配图提供商</h3>
      <p style="color:#64748b;font-size:13px;margin-bottom:0">
        API Key 配置、连通性测试与额度查询已移至
        <RouterLink to="/admin/studio-config" style="color:#2563eb;font-weight:500">AI 提供商配置</RouterLink> 页面。
      </p>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { siteConfigApi, type SiteConfig } from '../../api/siteConfig'

const form = ref<SiteConfig>({})
const saving = ref(false)
const error = ref('')
const saved = ref('')

async function load() {
  form.value = await siteConfigApi.get()
}
async function save() {
  error.value = ''; saved.value = ''; saving.value = true
  try {
    form.value = await siteConfigApi.update(form.value)
    saved.value = '已保存'
  } catch (e: any) { error.value = e.message }
  finally { saving.value = false }
}
load()
</script>
