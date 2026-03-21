<script setup>
import { ref, onMounted } from 'vue'
import { message } from '../components/toast'
import {
  getAdminAiPhrases,
  postAdminAiPhraseAdd,
  postAdminAiPhraseUpdate,
  postAdminAiPhraseDelete,
} from '../api/admin'

const list = ref([])
const loading = ref(false)
const showModal = ref(false)
const isEdit = ref(false)
const submitting = ref(false)

const defaultForm = () => ({ id: null, type: 1, content: '', status: 1 })
const form = ref(defaultForm())

const typeMap = {
  1: { label: '运行状态流', bg: '#ebf8ff', color: '#2b6cb0' },
  2: { label: '盈利理由',   bg: '#f0fff4', color: '#276749' },
  3: { label: '亏损理由',   bg: '#fff5f5', color: '#c53030' },
  4: { label: '错过理由',   bg: '#fffaf0', color: '#c05621' },
}

function typeStyle(type) {
  const t = typeMap[type] || typeMap[1]
  return { background: t.bg, color: t.color }
}
function typeLabel(type) {
  return typeMap[type]?.label || '未知'
}

async function load() {
  loading.value = true
  try {
    const data = await getAdminAiPhrases()
    list.value = Array.isArray(data) ? data : (data?.list || data?.content || [])
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isEdit.value = false
  form.value = defaultForm()
  showModal.value = true
}

function openEdit(item) {
  isEdit.value = true
  form.value = { id: item.id, type: item.type, content: item.content || '', status: item.status }
  showModal.value = true
}

async function submit() {
  if (!form.value.content.trim()) return message.warning('请输入话术内容')
  submitting.value = true
  try {
    if (isEdit.value) {
      await postAdminAiPhraseUpdate({ ...form.value })
    } else {
      await postAdminAiPhraseAdd({ type: form.value.type, content: form.value.content, status: form.value.status })
    }
    message.success(isEdit.value ? '更新成功' : '新增成功')
    showModal.value = false
    await load()
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(item) {
  try {
    const newStatus = item.status === 1 ? 0 : 1
    await postAdminAiPhraseUpdate({ id: item.id, type: item.type, content: item.content, status: newStatus })
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

async function removeOne(id) {
  if (!confirm('确定删除此话术？该操作不可恢复。')) return
  try {
    await postAdminAiPhraseDelete(id)
    message.success('已删除')
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

function truncate(str, len = 60) {
  if (!str) return '—'
  return str.length > len ? str.slice(0, len) + '…' : str
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">AI话术管理</h2>
      <button type="button" class="btn btn-primary btn-with-icon" @click="openAdd">
        <SvgIcon name="plus" :size="16" class="btn-icon" /> 新增话术
      </button>
    </div>

    <div class="admin-card">
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>话术类型</th>
              <th>话术内容</th>
              <th>启用状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5" class="text-center py-8">正在加载...</td>
            </tr>
            <tr v-else-if="!list.length">
              <td colspan="5" class="text-center py-8">暂无话术数据</td>
            </tr>
            <tr v-for="item in list" :key="item.id">
              <td class="col-id">{{ item.id }}</td>
              <td>
                <span class="type-badge" :style="typeStyle(item.type)">{{ typeLabel(item.type) }}</span>
              </td>
              <td class="col-content">{{ truncate(item.content) }}</td>
              <td>
                <label class="switch">
                  <input type="checkbox" :checked="item.status === 1" @change="toggleStatus(item)" />
                  <span class="slider"></span>
                </label>
              </td>
              <td class="action-cell">
                <button class="btn-text" @click="openEdit(item)">编辑</button>
                <button class="btn-text danger" @click="removeOne(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modal -->
    <div v-if="showModal" class="modal-overlay" @click="showModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑话术' : '新增话术' }}</h3>
          <button type="button" class="close-btn" @click="showModal = false" aria-label="关闭">
            <SvgIcon name="close" :size="18" />
          </button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <div class="form-group">
              <label>话术类型</label>
              <select v-model.number="form.type">
                <option :value="1">运行状态流</option>
                <option :value="2">盈利理由</option>
                <option :value="3">亏损理由</option>
                <option :value="4">错过理由</option>
              </select>
            </div>
            <div class="form-group">
              <label>启用状态</label>
              <label class="switch switch-inline">
                <input type="checkbox" :checked="form.status === 1" @change="form.status = form.status === 1 ? 0 : 1" />
                <span class="slider"></span>
              </label>
              <span class="status-hint">{{ form.status === 1 ? '已启用' : '已禁用' }}</span>
            </div>
          </div>
          <div class="form-group">
            <label>话术内容</label>
            <textarea v-model="form.content" rows="6" placeholder="请输入话术内容..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showModal = false">取消</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ submitting ? '保存中...' : '提交保存' }}
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
td { padding: 14px 16px; border-bottom: 1px solid var(--border-color); vertical-align: middle; }
.text-center { text-align: center; }
.py-8 { padding-top: 32px; padding-bottom: 32px; color: var(--text-muted); }

.col-id { color: var(--text-muted); font-size: 12px; width: 60px; }
.col-content { color: var(--text-main); max-width: 400px; line-height: 1.5; }

.type-badge { display: inline-block; padding: 3px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; white-space: nowrap; }

/* Toggle Switch */
.switch { position: relative; display: inline-block; width: 40px; height: 22px; }
.switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
  background: #cbd5e0; border-radius: 22px; transition: 0.3s;
}
.slider::before {
  position: absolute; content: ''; height: 16px; width: 16px; left: 3px; bottom: 3px;
  background: white; border-radius: 50%; transition: 0.3s;
}
input:checked + .slider { background: var(--color-emerald, #38a169); }
input:checked + .slider::before { transform: translateX(18px); }

.switch-inline { vertical-align: middle; }
.status-hint { margin-left: 8px; font-size: 13px; color: var(--text-muted); }

.action-cell { white-space: nowrap; }
.btn-text { background: none; border: none; color: var(--color-accent-blue); cursor: pointer; padding: 0 8px; font-size: 13px; font-weight: 500; }
.btn-text:hover { text-decoration: underline; }
.btn-text.danger { color: var(--color-danger-alt); }

/* Modal */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: var(--ui-surface-2); border-radius: 12px; width: 600px; max-width: 95%; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.15); }
.modal-header { padding: 16px 24px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }
.modal-header h3 { margin: 0; font-size: 18px; color: var(--text-main); }
.close-btn { background: none; border: none; cursor: pointer; color: var(--text-muted); padding: 4px; }
.modal-body { padding: 24px; overflow-y: auto; flex: 1; }
.form-group { margin-bottom: 20px; display: flex; flex-direction: column; gap: 8px; }
.form-group label { font-weight: 600; font-size: 14px; color: var(--text-secondary); }
.form-group input, .form-group select, .form-group textarea { padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; font-size: 14px; outline: none; background: var(--ui-surface-2); color: var(--text-main); }
.form-group input:focus, .form-group textarea:focus { border-color: var(--color-accent-blue); }
.form-row { display: flex; gap: 20px; align-items: flex-start; }
.form-row .form-group { flex: 1; }
.modal-footer { padding: 16px 24px; border-top: 1px solid var(--border-color); display: flex; justify-content: flex-end; gap: 12px; }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; font-size: 14px; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: var(--color-accent-blue); color: var(--text-on-primary); }
.btn-ghost { background: var(--table-th-bg); color: var(--text-secondary); border-color: var(--border-color); }
.btn-with-icon { display: inline-flex; align-items: center; gap: 6px; }
.btn-icon { flex-shrink: 0; }
</style>
