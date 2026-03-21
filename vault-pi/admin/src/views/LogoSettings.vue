<script setup>
import { ref, onMounted } from 'vue'
import { message } from '../components/toast'
import { getAdminSystemConfigList, postAdminSystemConfigUpdate } from '../api/admin'
import ImageUpload from '../components/ImageUpload.vue'

const CONFIG_KEY = 'site_logo'

const loading = ref(false)
const submitting = ref(false)
const form = ref({
  headerLogoUrl: '',
  footerLogoUrl: '',
  faviconUrl: '',
  pageTitleC: 'Vault π',
  pageTitleB: 'Vault π 管理后台',
})

async function load() {
  loading.value = true
  try {
    const list = await getAdminSystemConfigList()
    const item = Array.isArray(list) ? list.find((c) => c.id === CONFIG_KEY) : null
    if (item?.value) {
      try {
        const data = JSON.parse(item.value)
        if (data.headerLogoUrl != null) form.value.headerLogoUrl = data.headerLogoUrl
        if (data.footerLogoUrl != null) form.value.footerLogoUrl = data.footerLogoUrl
        if (data.faviconUrl != null) form.value.faviconUrl = data.faviconUrl
        if (data.pageTitleC != null) form.value.pageTitleC = data.pageTitleC
        if (data.pageTitleB != null) form.value.pageTitleB = data.pageTitleB
      } catch (_) {}
    }
  } catch (e) {
    message.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  submitting.value = true
  try {
    await postAdminSystemConfigUpdate({
      id: CONFIG_KEY,
      value: JSON.stringify({
        headerLogoUrl: form.value.headerLogoUrl || '',
        footerLogoUrl: form.value.footerLogoUrl || '',
        faviconUrl: form.value.faviconUrl || '',
        pageTitleC: (form.value.pageTitleC || '').trim() || 'Vault π',
        pageTitleB: (form.value.pageTitleB || '').trim() || 'Vault π 管理后台',
      }),
      groupName: '系统基础',
      remark: 'C 端头/底 Logo、全站 favicon、C/B 端浏览器标签标题（JSON）',
    })
    message.success('保存成功。C 端用户需刷新页面后看到新 Logo；本页将刷新标签图标与标题。')
    try {
      window.dispatchEvent(new CustomEvent('vaultpi-site-branding-refresh'))
    } catch (_) {}
  } catch (e) {
    message.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">LOGO 设置</h2>
      <div class="header-actions">
        <button class="btn btn-ghost" type="button" @click="load" :disabled="loading">刷新</button>
        <button class="btn btn-primary" type="button" @click="save" :disabled="submitting">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
    <p class="page-desc">
      配置用户端（C 端）全站头部/底部 Logo；上传浏览器标签图标（favicon）；分别设置 C 端与后台（B 端）的标签页标题。留空 Logo 时 C 端使用默认静态资源。
    </p>

    <div v-if="loading" class="text-center py-20">正在加载…</div>

    <template v-else>
      <div class="admin-card form-card">
        <h3 class="card-title">C 端页面 Logo</h3>
        <div class="form-group">
          <label>头部 Logo</label>
          <ImageUpload v-model="form.headerLogoUrl" placeholder="上传头部 Logo" />
        </div>
        <div class="form-group">
          <label>底部 Logo</label>
          <ImageUpload v-model="form.footerLogoUrl" placeholder="上传底部 Logo" />
        </div>
      </div>

      <div class="admin-card form-card">
        <h3 class="card-title">浏览器标签（C 端与后台共用 favicon）</h3>
        <p class="card-hint">建议上传正方形 PNG/ICO/SVG，尺寸 32×32 或 64×64。</p>
        <div class="form-group">
          <label>标签页图标（Favicon）</label>
          <ImageUpload v-model="form.faviconUrl" placeholder="上传网站图标" />
        </div>
        <div class="form-group">
          <label>C 端标签页标题（站点名）</label>
          <input v-model="form.pageTitleC" class="item-input" placeholder="如：Vault π" />
          <p class="field-hint">与各页面名称组合显示为「页面名 - 站点名」。</p>
        </div>
        <div class="form-group">
          <label>管理后台标签页标题（站点名）</label>
          <input v-model="form.pageTitleB" class="item-input" placeholder="如：Vault π 管理后台" />
          <p class="field-hint">与各菜单页面名称组合显示为「页面名 - 站点名」。</p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text-main); }
.header-actions { display: flex; gap: 12px; }
.page-desc { color: var(--ui-muted); font-size: 14px; margin-bottom: 24px; max-width: 920px; line-height: 1.6; }

.form-card { background: var(--ui-surface-2); border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; max-width: 720px; margin-bottom: 24px; }
.card-title { font-size: 16px; font-weight: 700; color: var(--text-main); margin-bottom: 16px; padding-left: 12px; border-left: 4px solid var(--primary-color, var(--color-accent-blue)); }
.card-hint { font-size: 13px; color: var(--ui-muted); margin-bottom: 20px; }
.form-group { margin-bottom: 24px; }
.form-group:last-child { margin-bottom: 0; }
.form-group > label { display: block; font-weight: 600; color: var(--text-secondary); margin-bottom: 8px; font-size: 14px; }
.field-hint { font-size: 12px; color: var(--ui-muted); margin-top: 8px; margin-bottom: 0; }

.item-input { width: 100%; padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; font-size: 14px; outline: none; box-sizing: border-box; }
.item-input:focus { border-color: var(--primary-color, var(--color-accent-blue)); }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: var(--color-accent-blue); color: var(--text-on-primary); }
.btn-ghost { background: var(--table-th-bg); color: var(--text-secondary); border-color: var(--border-color); }
</style>
