<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { 
  getAdminActivityAll, 
  postAdminActivityAdd, 
  postAdminActivityUpdate, 
  postAdminActivityDelete 
} from '../api/admin'

const list = ref([])
const loading = ref(false)
const errorMsg = ref('')

const modalVisible = ref(false)
const modalSubmitting = ref(false)
const modalError = ref('')
const isEdit = ref(false)
const formData = ref({
  id: null,
  title: '',
  content: '',
  bannerUrl: '',
  startTime: '',
  endTime: '',
  status: 1
})

async function load() {
  loading.value = true
  try {
    list.value = await getAdminActivityAll()
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isEdit.value = false
  formData.value = { id: null, title: '', content: '', bannerUrl: '', startTime: '', endTime: '', status: 1 }
  modalError.value = ''
  modalVisible.value = true
}

function openEdit(act) {
  isEdit.value = true
  // Handle ISO strings for local input if needed, but here we just pass them
  formData.value = { ...act }
  modalError.value = ''
  modalVisible.value = true
}

async function submitForm() {
  if (!formData.value.title) {
    modalError.value = '活动标题必填'
    return
  }
  modalSubmitting.value = true
  try {
    if (isEdit.value) {
      await postAdminActivityUpdate(formData.value)
    } else {
      await postAdminActivityAdd(formData.value)
    }
    modalVisible.value = false
    load()
  } catch (e) {
    modalError.value = e.message
  } finally {
    modalSubmitting.value = false
  }
}

async function doDelete(id) {
  if (!confirm('锁定该活动记录将不可恢复，确认删除？')) return
  try {
    await postAdminActivityDelete(id)
    load()
  } catch (e) {
    message.error(e.message)
  }
}

function formatStatus(s) {
  const map = { 0: '预热中', 1: '进行中', 2: '已结束', 3: '已下线' }
  return map[s] || '未知'
}

function getStatusClass(s) {
  const map = { 0: 'tag-warm', 1: 'tag-active', 2: 'tag-end', 3: 'tag-off' }
  return map[s] || ''
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">营销中心 - 活动全生命周期管理</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load"><SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新看板</button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <button type="button" class="btn btn-primary btn-with-icon" @click="openAdd"><SvgIcon name="plus" :size="16" class="btn-icon" /> 策划新活动</button>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>活动预览</th>
                <th>活动标题</th>
                <th>展现状态</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th style="text-align: right">多维运维</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="6" class="loading-cell">正在拉取全量活动索引...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="6" class="no-data-cell">暂无策划中的活动项目</td>
              </tr>
              <tr v-for="act in list" :key="act.id">
                <td>
                  <div class="banner-box" :style="{ backgroundImage: `url(${act.bannerUrl})` }"></div>
                </td>
                <td><b style="color: #111827">{{ act.title }}</b></td>
                <td>
                  <span :class="['status-tag', getStatusClass(act.status)]">{{ formatStatus(act.status) }}</span>
                </td>
                <td class="time-cell">{{ act.startTime ? new Date(act.startTime).toLocaleString() : '—' }}</td>
                <td class="time-cell">{{ act.endTime ? new Date(act.endTime).toLocaleString() : '—' }}</td>
                <td style="text-align: right">
                  <button type="button" class="btn-sm btn-info" @click="openEdit(act)">编辑配置</button>
                  <button type="button" class="btn-sm btn-danger-lite" @click="doDelete(act.id)">移除</button>
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
          <div class="modal-title">{{ isEdit ? '活动资产重构' : '发起新营销战役' }}</div>
          <span class="modal-close" @click="modalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>活动主标题</label>
            <input v-model="formData.title" type="text" class="input" placeholder="输入极具吸引力的标题" />
          </div>
          <div class="form-item">
            <label>Banner 主视觉 URL</label>
            <input v-model="formData.bannerUrl" type="text" class="input" placeholder="支持 CDN 地址" />
          </div>
          <div class="form-grid">
            <div class="form-item">
              <label>开始时间 (ISO)</label>
              <input v-model="formData.startTime" type="text" class="input" placeholder="2024-01-01T00:00:00Z" />
            </div>
            <div class="form-item">
              <label>结束时间 (ISO)</label>
              <input v-model="formData.endTime" type="text" class="input" placeholder="2024-02-01T00:00:00Z" />
            </div>
          </div>
          <div class="form-item">
            <label>发布状态</label>
            <select v-model="formData.status" class="input">
              <option :value="0">预热 (Warm-up)</option>
              <option :value="1">正在进行 (Active)</option>
              <option :value="2">已结束 (Finished)</option>
              <option :value="3">强制下线 (Off-line)</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="modalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitForm" :disabled="modalSubmitting">确认部署</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.banner-box { width: 120px; height: 44px; border-radius: 6px; background: #f1f5f9 center/cover; border: 1px solid #e2e8f0; }
.time-cell { font-size: 12px; color: #64748b; white-space: nowrap; }
.status-tag { padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; color: #fff; }
.tag-warm { background: #f59e0b; }
.tag-active { background: #10b981; }
.tag-end { background: #94a3b8; }
.tag-off { background: #ef4444; }

.form-item { margin-bottom: 20px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.form-item label { display: block; margin-bottom: 8px; font-size: 13px; font-weight: 600; color: #475569; }
.input { width: 100%; padding: 10px 14px; border: 1px solid #e2e8f0; border-radius: 8px; outline: none; transition: border 0.2s; box-sizing: border-box; }
.input:focus { border-color: #2d8cf0; }

.btn-sm { padding: 5px 12px; border-radius: 4px; font-size: 11px; cursor: pointer; border: none; font-weight: 600; margin-left: 8px; }
.btn-danger-lite { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

/* Modal */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 3000; }
.modal-wrap { width: 500px; background: #fff; border-radius: 12px; box-shadow: 0 20px 40px rgba(0,0,0,0.2); }
.modal-header { padding: 18px 24px; border-bottom: 1px solid #f1f5f9; display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-weight: 700; color: #1e293b; }
.modal-body { padding: 24px; }
.modal-footer { padding: 16px 24px; background: #f8fafc; border-top: 1px solid #f1f5f9; text-align: right; border-radius: 0 0 12px 12px; }
.btn-cancel { padding: 8px 20px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; margin-right: 12px; cursor: pointer; }

.loading-cell, .no-data-cell { text-align: center; color: #94a3b8; padding: 50px; font-size: 14px; }
</style>
