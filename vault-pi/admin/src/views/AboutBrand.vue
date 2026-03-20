<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminSystemConfigList, postAdminSystemConfigUpdate } from '../api/admin'
import ImageUpload from '../components/ImageUpload.vue'

const CONFIG_KEY = 'home_about_brand'
const CONFIG_KEY_LOGO = 'site_logo'

const loading = ref(false)
const submitting = ref(false)
const form = ref({
  title: '关于 Vault π',
  detail: '诚实 | 公平 | 热情 | 开放',
  desc1: 'Vault π 由一群专注数字资产与安全技术的从业者发起，核心团队长期深耕交易系统与风控基础设施。',
  desc2: 'Vault134.com定位于区块链基础服务商，致力于为全球用户提供优质加密资产交易平台，秉承着"不作恶"的基本原则，坚持诚实、公正、热情的服务于客户，以开放的态度迎接一切有利于用户根本利益的伙伴/项目。',
})
const logoForm = ref({
  headerLogoUrl: '',
  footerLogoUrl: '',
})

async function load() {
  loading.value = true
  try {
    const list = await getAdminSystemConfigList()
    const item = Array.isArray(list) ? list.find((c) => c.id === CONFIG_KEY) : null
    if (item?.value) {
      try {
        const data = JSON.parse(item.value)
        if (data.title != null) form.value.title = data.title
        if (data.detail != null) form.value.detail = data.detail
        if (data.desc1 != null) form.value.desc1 = data.desc1
        if (data.desc2 != null) form.value.desc2 = data.desc2
      } catch (_) {}
    }
    const logoItem = Array.isArray(list) ? list.find((c) => c.id === CONFIG_KEY_LOGO) : null
    if (logoItem?.value) {
      try {
        const data = JSON.parse(logoItem.value)
        if (data.headerLogoUrl != null) logoForm.value.headerLogoUrl = data.headerLogoUrl
        if (data.footerLogoUrl != null) logoForm.value.footerLogoUrl = data.footerLogoUrl
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
        title: form.value.title,
        detail: form.value.detail,
        desc1: form.value.desc1,
        desc2: form.value.desc2,
      }),
      groupName: '首页配置',
      remark: '首页关于我们区块：标题、副标题、两段描述',
    })
    await postAdminSystemConfigUpdate({
      id: CONFIG_KEY_LOGO,
      value: JSON.stringify({
        headerLogoUrl: logoForm.value.headerLogoUrl || '',
        footerLogoUrl: logoForm.value.footerLogoUrl || '',
      }),
      groupName: '首页配置',
      remark: 'C 端全局 Logo：头部、底部图片地址，留空使用默认',
    })
    message.success('保存成功，C 端首页与全局 Logo 将显示最新内容')
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
      <h2 class="page-title">关于我们配置</h2>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="load" :disabled="loading">刷新</button>
        <button class="btn btn-primary" @click="save" :disabled="submitting">{{ submitting ? '保存中…' : '保存' }}</button>
      </div>
    </div>
    <p class="page-desc">仅用简体中文维护即可；C 端英文界面由系统自动翻译。并可配置 C 端全局 Logo。</p>

    <div v-if="loading" class="text-center py-20">正在加载…</div>

    <template v-else>
      <div class="admin-card form-card">
        <h3 class="card-title">关于我们区块</h3>
        <div class="form-group">
          <label>区块标题</label>
          <input v-model="form.title" class="item-input" placeholder="如：关于 Vault π" />
        </div>
        <div class="form-group">
          <label>副标题</label>
          <input v-model="form.detail" class="item-input" placeholder="诚实 | 公平 | 热情 | 开放" />
        </div>
        <div class="form-group">
          <label>第一段描述</label>
          <textarea v-model="form.desc1" class="item-textarea" rows="3" placeholder="Vault π 由一群专注数字资产与安全技术的从业者发起…"></textarea>
        </div>
        <div class="form-group">
          <label>第二段描述</label>
          <textarea v-model="form.desc2" class="item-textarea" rows="4" placeholder="BIZZAN.COM定位于区块链基础服务商…"></textarea>
        </div>
      </div>

      <div class="admin-card form-card logo-card">
        <h3 class="card-title">C 端全局 Logo</h3>
        <p class="card-hint">上传图片后 C 端头部与底部将显示该 Logo，留空则使用站点默认 Logo。</p>
        <div class="form-group">
          <label>头部 Logo</label>
          <ImageUpload v-model="logoForm.headerLogoUrl" placeholder="上传头部 Logo" />
        </div>
        <div class="form-group">
          <label>底部 Logo</label>
          <ImageUpload v-model="logoForm.footerLogoUrl" placeholder="上传底部 Logo" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; }
.header-actions { display: flex; gap: 12px; }
.page-desc { color: #64748b; font-size: 14px; margin-bottom: 24px; }

.form-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; max-width: 720px; margin-bottom: 24px; }
.form-card:last-child { margin-bottom: 0; }
.logo-card { margin-top: 0; }
.card-title { font-size: 16px; font-weight: 700; color: #2d3748; margin-bottom: 16px; padding-left: 12px; border-left: 4px solid var(--primary-color, #2d8cf0); }
.card-hint { font-size: 13px; color: #64748b; margin-bottom: 20px; }
.form-group { margin-bottom: 24px; }
.form-group:last-child { margin-bottom: 0; }
.form-group > label { display: block; font-weight: 600; color: #374151; margin-bottom: 8px; font-size: 14px; }

.item-input { width: 100%; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; outline: none; }
.item-input:focus { border-color: var(--primary-color, #2d8cf0); }
.item-textarea { width: 100%; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; outline: none; resize: vertical; min-height: 80px; }
.item-textarea:focus { border-color: var(--primary-color, #2d8cf0); }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
</style>
