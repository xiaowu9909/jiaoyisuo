<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { 
  getAdminAdvertiseAll, 
  postAdminAdvertiseAdd, 
  postAdminAdvertiseUpdate, 
  postAdminAdvertiseDelete 
} from '../api/admin'

const list = ref([])
const loading = ref(false)
const errorMsg = ref('')

// Modal
const modalVisible = ref(false)
const modalSubmitting = ref(false)
const modalError = ref('')
const isEdit = ref(false)
const formData = ref({
  id: null,
  name: '',
  url: '',
  linkUrl: '',
  sortOrder: 0,
  status: 0,
  lang: 'CN'
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    list.value = await getAdminAdvertiseAll()
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isEdit.value = false
  formData.value = { id: null, name: '', url: '', linkUrl: '', sortOrder: 0, status: 0, lang: 'CN' }
  modalError.value = ''
  modalVisible.value = true
}

function openEdit(ad) {
  isEdit.value = true
  formData.value = { ...ad }
  modalError.value = ''
  modalVisible.value = true
}

async function submitForm() {
  if (!formData.value.name || !formData.value.url) {
    modalError.value = '名称和图片地址为必填项'
    return
  }
  modalSubmitting.value = true
  try {
    if (isEdit.value) {
      await postAdminAdvertiseUpdate(formData.value)
    } else {
      await postAdminAdvertiseAdd(formData.value)
    }
    modalVisible.value = false
    await load()
  } catch (e) {
    modalError.value = e.message
  } finally {
    modalSubmitting.value = false
  }
}

async function doDelete(id) {
  if (!confirm('确定要删除该广告吗？')) return
  try {
    await postAdminAdvertiseDelete(id)
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

async function toggleStatus(ad) {
  const newStatus = ad.status === 0 ? 1 : 0
  try {
    await postAdminAdvertiseUpdate({ id: ad.id, status: newStatus })
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page advertise-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">平台 Banner 与广告位管理系统</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load"><SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新资产</button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <button type="button" class="btn btn-primary btn-with-icon" @click="openAdd"><SvgIcon name="plus" :size="16" class="btn-icon" /> 投放新广告</button>
        </div>

        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>权重</th>
                <th>广告预览</th>
                <th>广告名称</th>
                <th>投放语种</th>
                <th>跳转链接</th>
                <th>展现状态</th>
                <th style="text-align: right">资产运维</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="loading-cell">正在拉取广告位快照...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="7" class="no-data-cell">当前暂无可投放的视觉资产</td>
              </tr>
              <tr v-for="ad in list" :key="ad.id">
                <td>#{{ ad.sortOrder }}</td>
                <td>
                  <div class="ad-preview" :style="{ backgroundImage: `url(${ad.url})` }"></div>
                </td>
                <td><b style="color: #111827">{{ ad.name }}</b></td>
                <td><span class="lang-tag">{{ ad.lang }}</span></td>
                <td><small style="color: #94a3b8">{{ ad.linkUrl || '—' }}</small></td>
                <td>
                  <span :class="['status-tag', ad.status === 0 ? 'ok' : 'err']" @click="toggleStatus(ad)" style="cursor: pointer">
                    {{ ad.status === 0 ? '正在展现' : '已下线' }}
                  </span>
                </td>
                <td style="text-align: right">
                  <button type="button" class="btn-sm btn-info" @click="openEdit(ad)">编辑配置</button>
                  <button type="button" class="btn-sm btn-danger-lite" @click="doDelete(ad.id)">销毁</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Modal -->
    <div v-if="modalVisible" class="modal-mask" @click.self="modalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">{{ isEdit ? '修改广告资产配置' : '投放全球新广告' }}</div>
          <span class="modal-close" @click="modalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>资产名称 (内部管理用)</label>
            <input v-model="formData.name" type="text" class="input" placeholder="输入广告位名称" />
          </div>
          <div class="modal-form-item">
            <label>视觉素材地址 (Image URL)</label>
            <input v-model="formData.url" type="text" class="input" placeholder="输入合法的图片 URL 地址" />
          </div>
          <div class="modal-form-item">
            <label>应用跳转链接 (Link URL)</label>
            <input v-model="formData.linkUrl" type="text" class="input" placeholder="点击后跳转的内部/外部地址" />
          </div>
          <div class="modal-form-item-grid">
            <div class="modal-form-item">
              <label>展现排序 (权重越大越靠前)</label>
              <input v-model="formData.sortOrder" type="number" class="input" />
            </div>
            <div class="modal-form-item">
              <label>投放语种</label>
              <select v-model="formData.lang" class="input">
                <option value="CN">简体中文 (CN)</option>
                <option value="EN">English (EN)</option>
              </select>
            </div>
          </div>
          <p v-if="modalError" class="error-tip">{{ modalError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="modalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitForm" :disabled="modalSubmitting">
            {{ modalSubmitting ? '正在同步云端数据...' : '提交上线' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.advertise-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }

.function-wrapper { margin-bottom: 20px; text-align: right; }
.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; transition: all 0.2s; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-info { background: #f7fafc; color: #4a5568; border: 1px solid #e2e8f0; }

.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 14px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; vertical-align: middle; }

.ad-preview { width: 100px; height: 40px; border-radius: 4px; background-size: cover; background-position: center; border: 1px solid #edf2f7; background-color: #f8fafc; }
.lang-tag { padding: 2px 6px; background: #edf2f7; color: #4a5568; border-radius: 4px; font-size: 11px; font-weight: 700; }

.status-tag { padding: 2px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.status-tag.ok { background: #c6f6d5; color: #22543d; }
.status-tag.err { background: #fed7d7; color: #822727; }

.btn-sm { padding: 5px 12px; border-radius: 4px; font-size: 12px; cursor: pointer; border: none; font-weight: 500; margin-left: 6px; }
.btn-danger-lite { background: rgba(229, 62, 62, 0.1); color: #e53e3e; }

/* Modal */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(2px); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal-wrap { width: 480px; background: #fff; border-radius: 10px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
.modal-header { padding: 16px 20px; border-bottom: 1px solid #edf2f7; display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-size: 15px; font-weight: 600; }
.modal-body { padding: 20px; }
.modal-form-item { margin-bottom: 16px; }
.modal-form-item-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.modal-form-item label { display: block; margin-bottom: 6px; color: #4a5568; font-size: 13px; font-weight: 500; }
.input { width: 100%; padding: 9px 12px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; box-sizing: border-box; }
.input:focus { border-color: #2d8cf0; }
.modal-footer { padding: 16px 20px; background: #f8fafc; text-align: right; border-top: 1px solid #edf2f7; border-bottom-left-radius: 10px; border-bottom-right-radius: 10px; }
.btn-cancel { padding: 8px 20px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; margin-right: 10px; cursor: pointer; }

.loading-cell, .no-data-cell { text-align: center; color: #a0aec0; padding: 40px; }
.error { color: #e53e3e; font-size: 13px; margin-bottom: 15px; }
.error-tip { color: #e53e3e; font-size: 12px; margin-top: 10px; }
</style>
