<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminSystemConfigList, postAdminSystemConfigUpdate } from '../api/admin'
import ImageUpload from '../components/ImageUpload.vue'

const CONFIG_KEY = 'home_app_download'

const loading = ref(false)
const submitting = ref(false)
const form = ref({
  scanText: '扫描二维码，下载APP',
  downloadText: '立即下载',
  slogan: 'Vault π App - 全球数字资产交易平台',
  imageUrl: '',
})

async function load() {
  loading.value = true
  try {
    const list = await getAdminSystemConfigList()
    const item = Array.isArray(list) ? list.find((c) => c.id === CONFIG_KEY) : null
    if (item?.value) {
      try {
        const data = JSON.parse(item.value)
        if (data.scanText != null) form.value.scanText = data.scanText
        if (data.downloadText != null) form.value.downloadText = data.downloadText
        if (data.slogan != null) form.value.slogan = data.slogan
        if (data.imageUrl != null) form.value.imageUrl = data.imageUrl
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
    const value = JSON.stringify({
      scanText: form.value.scanText,
      downloadText: form.value.downloadText,
      slogan: form.value.slogan,
      imageUrl: form.value.imageUrl || '',
    })
    await postAdminSystemConfigUpdate({
      id: CONFIG_KEY,
      value,
      groupName: '首页配置',
      remark: '首页下载APP区块：扫码提示、下载按钮文案、slogan',
    })
    message.success('保存成功，C 端首页将显示最新内容')
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
      <h2 class="page-title">下载APP配置</h2>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="load" :disabled="loading">刷新</button>
        <button class="btn btn-primary" @click="save" :disabled="submitting">{{ submitting ? '保存中…' : '保存' }}</button>
      </div>
    </div>
    <p class="page-desc">仅用简体中文维护即可；C 端英文界面由系统自动翻译。</p>

    <div v-if="loading" class="text-center py-20">正在加载…</div>

    <div v-else class="admin-card form-card">
      <div class="form-group">
        <label>扫码提示</label>
        <input v-model="form.scanText" class="item-input" placeholder="如：扫描二维码，下载APP" />
      </div>
      <div class="form-group">
        <label>下载按钮文案</label>
        <input v-model="form.downloadText" class="item-input" placeholder="如：立即下载" />
      </div>
      <div class="form-group">
        <label>Slogan</label>
        <input v-model="form.slogan" class="item-input" placeholder="如：Vault π App - 全球数字资产交易平台" />
      </div>
      <div class="form-group">
        <label>APP 展示图</label>
        <ImageUpload v-model="form.imageUrl" placeholder="上传 APP 展示图" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; }
.header-actions { display: flex; gap: 12px; }
.page-desc { color: #64748b; font-size: 14px; margin-bottom: 24px; }

.form-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; max-width: 720px; }
.form-group { margin-bottom: 24px; }
.form-group:last-child { margin-bottom: 0; }
.form-group > label { display: block; font-weight: 600; color: #374151; margin-bottom: 8px; font-size: 14px; }

.item-input { width: 100%; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; outline: none; }
.item-input:focus { border-color: var(--primary-color, #2d8cf0); }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
</style>
