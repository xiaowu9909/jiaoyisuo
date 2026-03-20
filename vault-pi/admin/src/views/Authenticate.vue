<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminAuthenticatePage, getAdminAuthenticateDetail, postAdminAuthenticateAudit } from '../api/admin'

const router = useRouter()
const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorMsg = ref('')
const auditStatusFilter = ref('PENDING')
const currentPage = ref(1)
const pageSize = 20

const detailModal = ref(false)
const detailRow = ref(null)
const detailLoading = ref(false)
const auditSubmitting = ref(false)
const auditResult = ref('APPROVED')
const rejectReason = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminAuthenticatePage(currentPage.value, pageSize, auditStatusFilter.value || '')
    list.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function formatTime(str) {
  if (!str) return '—'
  try {
    return new Date(str).toLocaleString('zh-CN')
  } catch (_) {
    return str
  }
}

function statusText(s) {
  const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
  return map[s] || s
}

function isUrl(s) {
  if (!s || typeof s !== 'string') return false
  return s.startsWith('http://') || s.startsWith('https://')
}

async function openDetail(row) {
  detailModal.value = true
  detailRow.value = { ...row }
  detailLoading.value = true
  auditResult.value = 'APPROVED'
  rejectReason.value = ''
  try {
    const full = await getAdminAuthenticateDetail(row.id)
    detailRow.value = full
  } catch (e) {
    detailRow.value = { ...row }
    message.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailModal.value = false
  detailRow.value = null
}

async function submitAudit() {
  if (!detailRow.value) return
  if (detailRow.value.auditStatus !== 'PENDING') {
    message.error('该申请已审核')
    return
  }
  if (auditResult.value === 'REJECTED' && !rejectReason.value.trim()) {
    message.error('拒绝时请填写拒绝原因')
    return
  }
  auditSubmitting.value = true
  try {
    await postAdminAuthenticateAudit({
      id: detailRow.value.id,
      auditStatus: auditResult.value,
      rejectReason: rejectReason.value.trim() || undefined,
    })
    closeDetail()
    await load()
  } catch (e) {
    message.error(e.message || '操作失败')
  } finally {
    auditSubmitting.value = false
  }
}

function onFilter() {
  currentPage.value = 1
  load()
}

function changePage(p) {
  currentPage.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page authenticate-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">实名审核</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load"><SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新</button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <span class="label">审核状态：</span>
          <select v-model="auditStatusFilter" class="form-select" @change="onFilter">
            <option value="">全部</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已拒绝</option>
          </select>
          <button type="button" class="btn btn-info" @click="onFilter">查询</button>
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div class="table-wrap">
          <table class="data-table data-table-border">
            <thead>
              <tr>
                <th>ID</th>
                <th>会员ID</th>
                <th>用户名</th>
                <th>真实姓名</th>
                <th>身份证号</th>
                <th>状态</th>
                <th>申请时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8" class="loading-cell">加载中...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="8" class="no-data-cell">暂无数据</td>
              </tr>
              <tr v-for="row in list" :key="row.id">
                <td>{{ row.id }}</td>
                <td>{{ row.memberId }}</td>
                <td>{{ row.username }}</td>
                <td>{{ row.realName || '—' }}</td>
                <td>{{ row.idCard || '—' }}</td>
                <td>{{ statusText(row.auditStatus) }}</td>
                <td>{{ formatTime(row.createTime) }}</td>
                <td class="action-cell">
                  <button type="button" class="btn-link primary" @click="openDetail(row)">查看/审核</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="page-wrapper">
          <div class="pagination">
            <button type="button" class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
            <span class="page-info">第 {{ currentPage }} / {{ total ? Math.ceil(total / pageSize) || 1 : 1 }} 页，共 {{ total }} 条</span>
            <button type="button" class="page-btn" :disabled="currentPage >= (Math.ceil(total / pageSize) || 1)" @click="changePage(currentPage + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="detailModal" class="modal-mask" @click.self="closeDetail">
      <div class="modal-box">
        <div class="modal-head">
          <span>实名申请详情</span>
          <button type="button" class="modal-close" @click="closeDetail" aria-label="关闭"><SvgIcon name="close" :size="18" /></button>
        </div>
        <div v-if="detailRow" class="modal-body">
          <p v-if="detailLoading" class="loading-inline">加载详情中…</p>
          <template v-else>
            <p><strong>会员ID</strong> {{ detailRow.memberId }} &nbsp; <strong>用户名</strong> {{ detailRow.username }}</p>
            <p><strong>真实姓名</strong> {{ detailRow.realName }} &nbsp; <strong>身份证号</strong> {{ detailRow.idCard }}</p>
            <p><strong>状态</strong> {{ statusText(detailRow.auditStatus) }} &nbsp; <strong>申请时间</strong> {{ formatTime(detailRow.createTime) }}</p>
            <p v-if="detailRow.rejectReason"><strong>拒绝原因</strong> {{ detailRow.rejectReason }}</p>
            <div v-if="detailRow.identityCardImgFront || detailRow.identityCardImgReverse || detailRow.identityCardImgInHand" class="cert-images">
              <h4>证件图片（C 端提交的身份证/驾驶证）</h4>
              <div class="cert-grid">
                <div v-if="detailRow.identityCardImgFront" class="cert-item">
                  <span class="cert-label">正面</span>
                  <a v-if="isUrl(detailRow.identityCardImgFront)" :href="detailRow.identityCardImgFront" target="_blank" rel="noopener" class="cert-link">查看图片</a>
                  <img v-else :src="detailRow.identityCardImgFront" alt="正面" class="cert-img" @error="$event.target.style.display='none'" />
                </div>
                <div v-if="detailRow.identityCardImgReverse" class="cert-item">
                  <span class="cert-label">反面</span>
                  <a v-if="isUrl(detailRow.identityCardImgReverse)" :href="detailRow.identityCardImgReverse" target="_blank" rel="noopener" class="cert-link">查看图片</a>
                  <img v-else :src="detailRow.identityCardImgReverse" alt="反面" class="cert-img" @error="$event.target.style.display='none'" />
                </div>
                <div v-if="detailRow.identityCardImgInHand" class="cert-item">
                  <span class="cert-label">手持</span>
                  <a v-if="isUrl(detailRow.identityCardImgInHand)" :href="detailRow.identityCardImgInHand" target="_blank" rel="noopener" class="cert-link">查看图片</a>
                  <img v-else :src="detailRow.identityCardImgInHand" alt="手持" class="cert-img" @error="$event.target.style.display='none'" />
                </div>
              </div>
            </div>
          <template v-if="!detailLoading && detailRow.auditStatus === 'PENDING'">
            <hr />
            <div class="audit-form">
              <label>审核结果：</label>
              <select v-model="auditResult" class="form-select">
                <option value="APPROVED">通过</option>
                <option value="REJECTED">拒绝</option>
              </select>
              <div v-if="auditResult === 'REJECTED'" class="form-row">
                <label>拒绝原因：</label>
                <input v-model="rejectReason" type="text" class="form-input" placeholder="选填" />
              </div>
              <div class="modal-actions">
                <button type="button" class="btn" @click="closeDetail">取消</button>
                <button type="button" class="btn btn-primary" :disabled="auditSubmitting" @click="submitAudit">提交</button>
              </div>
            </div>
          </template>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.authenticate-page { color: #333; }
.admin-card { border: 1px solid #e8eaec; border-radius: 4px; overflow: hidden; background: #fff; }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: #f8f8f9; border-bottom: 1px solid #e8eaec; }
.card-title { font-size: 14px; font-weight: 600; }
.card-body { padding: 16px; }
.function-wrapper { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.label { font-size: 14px; color: #666; }
.form-select { padding: 8px 12px; border: 1px solid #dcdee2; border-radius: 4px; min-width: 120px; }
.btn { padding: 8px 16px; border-radius: 4px; cursor: pointer; border: 1px solid #dcdee2; background: #fff; }
.btn-small { padding: 6px 12px; font-size: 12px; }
.btn-primary { background: #2d8cf0; color: #fff; border-color: #2d8cf0; }
.btn-info { background: #2d8cf0; color: #fff; border-color: #2d8cf0; }
.error { color: #ed4014; margin-bottom: 8px; }
.table-wrap { overflow-x: auto; margin-bottom: 16px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table-border th, .data-table-border td { border: 1px solid #e8eaec; padding: 10px 12px; text-align: left; }
.data-table th { background: #f8f8f9; }
.loading-cell, .no-data-cell { text-align: center; color: #999; padding: 24px; }
.action-cell { white-space: nowrap; }
.btn-link { color: #2d8cf0; background: none; border: none; cursor: pointer; padding: 0; font-size: 13px; }
.page-wrapper { margin-top: 16px; text-align: right; }
.pagination { display: flex; align-items: center; justify-content: flex-end; gap: 16px; }
.page-btn { padding: 6px 12px; font-size: 13px; cursor: pointer; border: 1px solid #dcdee2; border-radius: 4px; background: #fff; }
.page-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: #666; }

.loading-inline { color: #666; margin: 8px 0; }
.cert-images { margin-top: 16px; padding-top: 12px; border-top: 1px solid #eee; }
.cert-images h4 { margin: 0 0 10px; font-size: 13px; color: #333; }
.cert-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.cert-item { display: flex; flex-direction: column; gap: 6px; }
.cert-label { font-size: 12px; color: #666; }
.cert-link { color: #2d8cf0; font-size: 13px; }
.cert-img { max-width: 100%; max-height: 160px; object-fit: contain; border: 1px solid #eee; border-radius: 4px; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-box { background: #fff; border-radius: 8px; min-width: 400px; max-width: 560px; }
.modal-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid #e8eaec; font-weight: 600; }
.modal-body { padding: 16px; font-size: 13px; }
.modal-body p { margin: 8px 0; }
.modal-body hr { margin: 16px 0; border: none; border-top: 1px solid #e8eaec; }
.audit-form .form-row { margin-top: 12px; }
.audit-form .form-input { width: 100%; padding: 8px 12px; border: 1px solid #dcdee2; border-radius: 4px; margin-top: 4px; }
.modal-actions { display: flex; gap: 12px; margin-top: 16px; }
</style>
