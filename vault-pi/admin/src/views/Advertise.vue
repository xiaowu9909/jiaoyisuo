<script setup>
import { message } from '../components/toast';

import { ref, computed, onMounted } from 'vue'
import {
  getAdminAdvertiseAll,
  postAdminAdvertiseAdd,
  postAdminAdvertiseUpdate,
  postAdminAdvertiseDelete
} from '../api/admin'

const allList = ref([])
const loading = ref(false)
const errorMsg = ref('')

// 分页
const currentPage = ref(1)
const pageSize = 10
const total = computed(() => allList.value.length)
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)
const list = computed(() =>
  allList.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize)
)

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
    allList.value = await getAdminAdvertiseAll()
    currentPage.value = 1
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
                <td><b style="color: var(--text-main)">{{ ad.name }}</b></td>
                <td><span class="lang-tag">{{ ad.lang }}</span></td>
                <td><small style="color: var(--text-muted)">{{ ad.linkUrl || '—' }}</small></td>
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

        <!-- 分页 -->
        <div v-if="total > pageSize" class="pagination">
          <button :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
          <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页（共 {{ total }} 条）</span>
          <button :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
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
.advertise-page { color: var(--text-main); }
.admin-card { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; background: var(--ui-surface-2); box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: var(--table-th-bg); border-bottom: 1px solid var(--border-color); }
.card-title { font-size: 15px; font-weight: 600; color: var(--text-main); }
.card-body { padding: 20px; }

.function-wrapper { margin-bottom: 20px; text-align: right; }
.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; transition: all 0.2s; }
.btn-primary { background: var(--color-accent-blue); color: var(--text-on-primary); }
.btn-info { background: var(--table-th-bg); color: var(--text-secondary); border: 1px solid var(--border-color); }

.table-wrap { border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 14px 16px; background: var(--table-th-bg); color: var(--text-secondary); font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid var(--border-color); vertical-align: middle; }

.ad-preview { width: 100px; height: 40px; border-radius: 4px; background-size: cover; background-position: center; border: 1px solid var(--border-color); background-color: var(--table-th-bg); }
.lang-tag { padding: 2px 6px; background: var(--border-color); color: var(--text-secondary); border-radius: 4px; font-size: 11px; font-weight: 700; }

.status-tag { padding: 2px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.status-tag.ok { background: var(--color-success-bg); color: var(--color-success); }
.status-tag.err { background: #fed7d7; color: var(--color-danger-strong); }

.btn-sm { padding: 5px 12px; border-radius: 4px; font-size: 12px; cursor: pointer; border: none; font-weight: 500; margin-left: 6px; }
.btn-danger-lite { background: rgba(229, 62, 62, 0.1); color: var(--color-danger-alt); }

/* Modal */
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); backdrop-filter: blur(2px); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal-wrap { width: 480px; background: var(--ui-surface-2); border-radius: 10px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
.modal-header { padding: 16px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-size: 15px; font-weight: 600; }
.modal-body { padding: 20px; }
.modal-form-item { margin-bottom: 16px; }
.modal-form-item-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
.modal-form-item label { display: block; margin-bottom: 6px; color: var(--text-secondary); font-size: 13px; font-weight: 500; }
.input { width: 100%; padding: 9px 12px; border: 1px solid var(--border-color); border-radius: 6px; outline: none; box-sizing: border-box; }
.input:focus { border-color: var(--color-accent-blue); }
.modal-footer { padding: 16px 20px; background: var(--table-th-bg); text-align: right; border-top: 1px solid var(--border-color); border-bottom-left-radius: 10px; border-bottom-right-radius: 10px; }
.btn-cancel { padding: 8px 20px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--ui-surface-2); margin-right: 10px; cursor: pointer; }

.loading-cell, .no-data-cell { text-align: center; color: var(--text-muted); padding: 40px; }
.error { color: var(--color-danger-alt); font-size: 13px; margin-bottom: 15px; }
.error-tip { color: var(--color-danger-alt); font-size: 12px; margin-top: 10px; }
.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border-color); }
.pagination button { padding: 6px 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--ui-surface-2); cursor: pointer; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: var(--text-muted); }
</style>
