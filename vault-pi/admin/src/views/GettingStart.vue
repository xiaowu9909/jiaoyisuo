<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminSystemConfigList, postAdminSystemConfigUpdate } from '../api/admin'
import ImageUpload from '../components/ImageUpload.vue'

const CONFIG_KEY = 'home_getting_start'

const loading = ref(false)
const submitting = ref(false)
const form = ref({
  title: '新手入门 | 极速买币',
  subtitle: 'Vault π 官方新手入门辅助通道',
  items: [
    { name: '法币通道', tips: '用人民币买卖比特币等', imageUrl: '' },
    { name: '交易入门', tips: '新手币币交易基础入门', imageUrl: '' },
    { name: '区块链基础', tips: '区块链、比特币基础入门', imageUrl: '' },
    { name: '新人社群', tips: '经验交流、信息共享', imageUrl: '' },
  ],
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
        if (data.subtitle != null) form.value.subtitle = data.subtitle
        if (Array.isArray(data.items) && data.items.length > 0) {
          form.value.items = data.items.slice(0, 4).map((it) => ({
            name: it.name != null ? it.name : '',
            tips: it.tips != null ? it.tips : '',
            imageUrl: it.imageUrl != null ? it.imageUrl : '',
          }))
          while (form.value.items.length < 4) {
            form.value.items.push({ name: '', tips: '', imageUrl: '' })
          }
        }
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
      title: form.value.title,
      subtitle: form.value.subtitle,
      items: form.value.items.map((it) => ({
        name: it.name || '',
        tips: it.tips || '',
        imageUrl: it.imageUrl || '',
      })),
    })
    await postAdminSystemConfigUpdate({ id: CONFIG_KEY, value, groupName: '首页配置', remark: '首页新手入门区块：标题、副标题、四个卡片名称与描述' })
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
      <h2 class="page-title">新手入门配置</h2>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="load" :disabled="loading">刷新</button>
        <button class="btn btn-primary" @click="save" :disabled="submitting">{{ submitting ? '保存中…' : '保存' }}</button>
      </div>
    </div>
    <p class="page-desc">仅用简体中文维护即可；C 端英文界面由系统自动翻译为英文。</p>

    <div v-if="loading" class="text-center py-20">正在加载…</div>

    <div v-else class="admin-card form-card">
      <div class="form-group">
        <label>区块标题</label>
        <input v-model="form.title" class="item-input" placeholder="如：新手入门 | 极速买币" />
      </div>
      <div class="form-group">
        <label>区块副标题</label>
        <input v-model="form.subtitle" class="item-input" placeholder="如：Vault π 官方新手入门辅助通道" />
      </div>
      <div class="form-group">
        <label>四个卡片（名称、描述、图片）</label>
        <div class="items-list">
          <div v-for="(item, idx) in form.items" :key="idx" class="item-row card-row">
            <span class="item-label">卡片 {{ idx + 1 }}</span>
            <input v-model="item.name" class="item-input short" placeholder="名称" />
            <input v-model="item.tips" class="item-input flex" placeholder="描述" />
            <div class="card-upload">
              <ImageUpload v-model="item.imageUrl" :placeholder="`卡片${idx + 1} 图片`" />
            </div>
          </div>
        </div>
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

.items-list { display: flex; flex-direction: column; gap: 12px; }
.item-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.item-label { font-size: 13px; color: #64748b; min-width: 56px; }
.item-input.short { flex: 0 0 140px; }
.item-input.flex { flex: 1; min-width: 180px; }
.card-row { align-items: flex-start; }
.card-upload { flex-shrink: 0; }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
</style>
