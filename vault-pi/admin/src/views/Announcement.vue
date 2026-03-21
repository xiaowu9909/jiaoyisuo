<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { 
  getAdminAnnouncementPage, 
  postAdminAnnouncementAdd, 
  postAdminAnnouncementDelete,
  postAdminAnnouncementUpdate,
  postAdminAnnouncementTop,
  postAdminAnnouncementUntop
} from '../api/admin'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

const showModal = ref(false)
const isEdit = ref(false)
const form = ref({ id: null, title: '', content: '', lang: 'CN', isTop: '1', status: 'NORMAL' })
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await getAdminAnnouncementPage(currentPage.value, pageSize.value)
    list.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isEdit.value = false
  form.value = { id: null, title: '', content: '', lang: 'CN', isTop: '1', status: 'NORMAL' }
  showModal.value = true
}

function openEdit(item) {
  isEdit.value = true
  form.value = { 
    id: item.id, 
    title: item.title, 
    content: item.content, 
    lang: item.lang || 'CN', 
    isTop: item.isTop || '1', 
    status: item.status || 'NORMAL' 
  }
  showModal.value = true
}

async function submit() {
  if (!form.value.title.trim()) return message.warning('请输入标题')
  submitting.value = true
  try {
    const payload = { ...form.value }
    if (isEdit.value) {
      await postAdminAnnouncementUpdate(payload)
    } else {
      await postAdminAnnouncementAdd(payload)
    }
    showModal.value = false
    await load()
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function toggleTop(item) {
  try {
    if (item.isTop === '1') {
      await postAdminAnnouncementUntop(item.id)
    } else {
      await postAdminAnnouncementTop(item.id)
    }
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

async function toggleStatus(item) {
  try {
    const newStatus = item.status === 'NORMAL' ? 'DISABLED' : 'NORMAL'
    await postAdminAnnouncementUpdate({ id: item.id, status: newStatus })
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

async function removeOne(id) {
  if (!confirm('确定删除此公告？')) return
  try {
    await postAdminAnnouncementDelete(id)
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

function formatTime(str) {
  if (!str) return '—'
  return new Date(str).toLocaleString('zh-CN')
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">全站公告管理</h2>
      <button type="button" class="btn btn-primary btn-with-icon" @click="openAdd"><SvgIcon name="plus" :size="16" class="btn-icon" /> 发布公告</button>
    </div>

    <div class="admin-card">
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>置顶</th>
              <th>标题</th>
              <th>语言</th>
              <th>发布时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="7" class="text-center py-8">正在拉取内容...</td></tr>
            <tr v-else-if="!list.length"><td colspan="7" class="text-center py-8">暂无公告数据</td></tr>
            <tr v-for="a in list" :key="a.id" :class="{ 'row-top': a.isTop === '1' }">
              <td>{{ a.id }}</td>
              <td>
                <span v-if="a.isTop === '1'" class="badge badge-warning">置顶</span>
                <span v-else class="text-muted">—</span>
              </td>
              <td class="col-title">{{ a.title }}</td>
              <td><span class="lang-tag">{{ a.lang }}</span></td>
              <td>{{ formatTime(a.createTime) }}</td>
              <td>
                <span :class="['badge', a.status === 'NORMAL' ? 'badge-success' : 'badge-danger']">
                  {{ a.status === 'NORMAL' ? '可见' : '已隐藏' }}
                </span>
              </td>
              <td class="action-cell">
                <button class="btn-text" @click="openEdit(a)">编辑</button>
                <button class="btn-text" @click="toggleTop(a)">{{ a.isTop === '1' ? '取消置顶' : '置顶' }}</button>
                <button class="btn-text" @click="toggleStatus(a)">{{ a.status === 'NORMAL' ? '隐藏' : '显示' }}</button>
                <button class="btn-text danger" @click="removeOne(a.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button :disabled="currentPage <= 1" @click="currentPage--; load()">上一页</button>
        <span class="page-info">第 {{ currentPage }} 页 (共 {{ total }} 条)</span>
        <button :disabled="currentPage >= Math.ceil(total / pageSize)" @click="currentPage++; load()">下一页</button>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="showModal" class="modal-overlay" @click="showModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑公告' : '发布新公告' }}</h3>
          <button type="button" class="close-btn" @click="showModal = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>公告标题</label>
            <input v-model="form.title" placeholder="输入公告标题" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>展示语言</label>
              <select v-model="form.lang">
                <option value="CN">简体中文</option>
                <option value="EN">English</option>
              </select>
            </div>
            <div class="form-group">
              <label>初始状态</label>
              <select v-model="form.status">
                <option value="NORMAL">立即显示</option>
                <option value="DISABLED">暂不发布</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>正文内容 (支持简易排版)</label>
            <textarea v-model="form.content" rows="12" placeholder="输入公告详细内容..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showModal = false">取消</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中...' : '提交保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text-main); }

.admin-card { background: var(--ui-surface-2); border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 20px; }
.table-container { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px 16px; background: var(--table-th-bg); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); }
td { padding: 14px 16px; border-bottom: 1px solid var(--border-color); }

.row-top { background: #fffef2; }
.col-title { font-weight: 500; color: var(--text-main); max-width: 400px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.lang-tag { background: #ebf8ff; color: var(--color-accent-blue); padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 700; }

.badge { padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.badge-warning { background: #fffaf0; color: #dd6b20; }
.badge-success { background: #f0fff4; color: var(--color-emerald); }
.badge-danger { background: #fff5f5; color: var(--color-danger-alt); }

.action-cell { white-space: nowrap; }
.btn-text { background: none; border: none; color: var(--color-accent-blue); cursor: pointer; padding: 0 8px; font-size: 13px; font-weight: 500; }
.btn-text:hover { color: var(--color-accent-blue); text-decoration: underline; }
.btn-text.danger { color: var(--color-danger-alt); }

/* Modal */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--ui-surface-2); border-radius: 12px; width: 800px; max-width: 90%; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1); }
.modal-header { padding: 16px 24px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }
.modal-header h3 { margin: 0; font-size: 18px; }

.modal-body { padding: 24px; overflow-y: auto; flex: 1; }
.form-group { margin-bottom: 20px; display: flex; flex-direction: column; gap: 8px; }
.form-group label { font-weight: 600; font-size: 14px; color: var(--text-secondary); }
.form-group input, .form-group select, .form-group textarea { padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; font-size: 14px; outline: none; }
.form-group input:focus, .form-group textarea:focus { border-color: var(--primary-color); }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

.modal-footer { padding: 16px 24px; border-top: 1px solid var(--border-color); display: flex; justify-content: flex-end; gap: 12px; }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; }
.btn-primary { background: var(--color-accent-blue); color: var(--text-on-primary); }
.btn-primary:active { transform: translateY(1px); }
.btn-ghost { background: var(--table-th-bg); color: var(--text-secondary); border-color: var(--border-color); }

.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 20px; }
.pagination button { padding: 6px 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--ui-surface-2); }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
