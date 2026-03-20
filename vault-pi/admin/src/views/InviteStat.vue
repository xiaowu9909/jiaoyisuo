<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import {
  getAdminInviteStat,
  getAdminInviteChildren,
  getAdminInviteCommission,
  postAdminInviteCommission,
} from '../api/admin'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const canEditCommission = ref(true)
const errorMsg = ref('')
const pageNo = ref(1)
const pageSize = ref(15)
const searchKw = ref('')

// 下级弹窗
const childrenModalVisible = ref(false)
const childrenLoading = ref(false)
const childrenList = ref([])
const activePromoter = ref(null)

// 推广员单独佣金弹窗
const promoterCommissionModalVisible = ref(false)
const promoterCommissionTarget = ref(null) // { memberId, username }
const promoterCommissionSaving = ref(false)
const promoterCommissionSaveMsg = ref('')
// 推广员单独佣金弹窗内表单
const promoterFixedPercent = ref(5)
const promoterTierRows = ref([])

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminInviteStat(pageNo.value, pageSize.value, searchKw.value)
    list.value = data.content || []
    total.value = data.totalElements ?? 0
    canEditCommission.value = data.canEditCommission !== false
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  load()
}

function handlePageChange(p) {
  pageNo.value = p
  load()
}

function openPromoterCommission(item) {
  promoterCommissionTarget.value = { memberId: item.memberId, username: item.username }
  promoterCommissionModalVisible.value = true
  promoterCommissionSaveMsg.value = ''
  loadPromoterCommissionForm(item.memberId)
}

function closePromoterCommissionModal() {
  promoterCommissionModalVisible.value = false
  promoterCommissionTarget.value = null
  load()
}

async function loadPromoterCommissionForm(promoterId) {
  try {
    const c = await getAdminInviteCommission(promoterId)
    promoterFixedPercent.value = c.fixedRate != null ? Math.round(Number(c.fixedRate) * 100) : 5
    promoterTierRows.value = (c.tiers && c.tiers.length) ? c.tiers.map(t => ({
      minPerformance: t.minPerformance != null ? Number(t.minPerformance) : 0,
      maxPerformance: t.maxPerformance != null ? Number(t.maxPerformance) : null,
      rate: t.rate != null ? Math.round(Number(t.rate) * 100) : 5,
    })) : []
  } catch (_) {
    promoterFixedPercent.value = 5
    promoterTierRows.value = []
  }
}

function addPromoterTier() {
  const last = promoterTierRows.value[promoterTierRows.value.length - 1]
  const nextMin = last ? (last.maxPerformance ?? last.minPerformance) : 0
  promoterTierRows.value.push({ minPerformance: nextMin, maxPerformance: null, rate: 5 })
}

function removePromoterTier(i) {
  promoterTierRows.value.splice(i, 1)
}

async function savePromoterCommission() {
  if (!promoterCommissionTarget.value) return
  promoterCommissionSaving.value = true
  promoterCommissionSaveMsg.value = ''
  try {
    const tiers = promoterTierRows.value.map(r => ({
      minPerformance: Number(r.minPerformance) || 0,
      maxPerformance: r.maxPerformance === '' || r.maxPerformance == null ? null : Number(r.maxPerformance),
      rate: (Number(r.rate) || 0) / 100,
    }))
    await postAdminInviteCommission({
      promoterId: promoterCommissionTarget.value.memberId,
      fixedRate: (Number(promoterFixedPercent.value) || 0) / 100,
      tiers,
    })
    promoterCommissionSaveMsg.value = '已保存'
    setTimeout(() => {
      promoterCommissionSaveMsg.value = ''
      closePromoterCommissionModal()
    }, 800)
  } catch (e) {
    promoterCommissionSaveMsg.value = e.message || '保存失败'
  } finally {
    promoterCommissionSaving.value = false
  }
}

async function showChildren(item) {
  activePromoter.value = item
  childrenList.value = []
  childrenLoading.value = true
  childrenModalVisible.value = true
  try {
    childrenList.value = await getAdminInviteChildren(item.memberId)
  } catch (e) {
    message.error(e.message)
  } finally {
    childrenLoading.value = false
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

function formatNum(n) {
  if (n == null) return '0'
  const x = Number(n)
  return Number.isFinite(x) ? x.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) : '0'
}

onMounted(load)
</script>

<template>
  <div class="admin-page invite-page">
    <h1 class="page-title">推广员</h1>
    <p class="page-desc">内部用户默认为推广员；使用其邀请码注册的用户（含正常与内部）均为其下级。点击「设置」可为该推广员配置固定佣金比例及按下级总业绩的档位比例。</p>

    <!-- 推广员列表 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">推广员列表（内部用户）</span>
        <button type="button" class="btn btn-primary btn-sm" @click="load"><SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新</button>
      </div>
      <div class="card-body">
        <div class="toolbar">
          <input v-model="searchKw" type="text" class="search-input" placeholder="按用户名 / ID 搜索" @keyup.enter="onSearch" />
          <button type="button" class="btn btn-default" @click="onSearch">搜索</button>
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>推广员 ID</th>
                <th>用户名</th>
                <th>邀请码</th>
                <th>下级人数</th>
                <th>下级总业绩</th>
                <th>已发佣金</th>
                <th v-if="canEditCommission">佣金设置</th>
                <th style="text-align: right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td :colspan="canEditCommission ? 8 : 7" class="loading-cell">加载中…</td>
              </tr>
              <tr v-else-if="!list.length">
                <td :colspan="canEditCommission ? 8 : 7" class="no-data-cell">暂无推广员（内部用户）或无匹配结果</td>
              </tr>
              <tr v-for="item in list" :key="item.memberId">
                <td>#{{ item.memberId }}</td>
                <td><b>{{ item.username }}</b></td>
                <td><code class="invite-code">{{ item.inviteCode || '—' }}</code></td>
                <td><span class="badge count">{{ item.directCount }}</span></td>
                <td class="num">{{ formatNum(item.totalSubordinatePerformance) }}</td>
                <td class="num commission">{{ formatNum(item.totalCommission) }}</td>
                <td v-if="canEditCommission">
                  <span :class="['badge', item.hasCustomCommission ? 'badge-custom' : 'badge-global']">
                    {{ item.hasCustomCommission ? '单独' : '全局' }}
                  </span>
                  <button type="button" class="btn-link btn-set" @click="openPromoterCommission(item)">设置</button>
                </td>
                <td style="text-align: right">
                  <button type="button" class="btn-link" @click="showChildren(item)">查看下级</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination-wrapper" v-if="total > pageSize">
          <button :disabled="pageNo === 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <span class="page-info">第 {{ pageNo }} / {{ Math.max(1, Math.ceil(total / pageSize)) }} 页，共 {{ total }} 条</span>
          <button :disabled="pageNo * pageSize >= total" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>

    <!-- 下级列表弹窗 -->
    <div v-if="childrenModalVisible" class="modal-mask" @click.self="childrenModalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">下级成员（使用「{{ activePromoter?.inviteCode }}」注册的用户，含正常与内部）</div>
          <span class="modal-close" @click="childrenModalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div v-if="childrenLoading" class="modal-loading">加载中…</div>
          <table v-else class="inner-table">
            <thead>
              <tr>
                <th>用户 ID</th>
                <th>用户名</th>
                <th>类型</th>
                <th>总业绩</th>
                <th>适用佣金比例</th>
                <th>注册时间</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!childrenList.length">
                <td colspan="7" class="no-data-cell">暂无下级</td>
              </tr>
              <tr v-for="c in childrenList" :key="c.memberId">
                <td>#{{ c.memberId }}</td>
                <td>{{ c.username }}</td>
                <td><span :class="['tag-sm', c.userType === 'INTERNAL' ? 'internal' : 'normal']">{{ c.userType === 'INTERNAL' ? '内部' : '正常' }}</span></td>
                <td class="num">{{ formatNum(c.subordinatePerformance) }}</td>
                <td>{{ (c.appliedCommissionRate != null ? Number(c.appliedCommissionRate) * 100 : 0).toFixed(1) }}%</td>
                <td class="time">{{ formatTime(c.registrationTime) }}</td>
                <td>
                  <span :class="['tag-sm', c.status === 'NORMAL' ? 'ok' : 'err']">
                    {{ c.status === 'NORMAL' ? '正常' : '禁用' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="childrenModalVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 推广员单独佣金设置弹窗 -->
    <div v-if="promoterCommissionModalVisible" class="modal-mask" @click.self="closePromoterCommissionModal">
      <div class="modal-wrap modal-commission">
        <div class="modal-header">
          <div class="modal-title">为推广员「{{ promoterCommissionTarget?.username }}」设置佣金比例</div>
          <span class="modal-close" @click="closePromoterCommissionModal" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label>固定佣金比例（未命中档位时使用）</label>
            <div class="input-group">
              <input v-model.number="promoterFixedPercent" type="number" min="0" max="100" step="0.5" class="input num" />
              <span class="unit">%</span>
            </div>
          </div>
          <div class="tier-section">
            <div class="tier-head">
              <span>按下级总业绩档位设置比例</span>
              <button type="button" class="btn btn-ghost btn-sm" @click="addPromoterTier">+ 添加档位</button>
            </div>
            <table class="tier-table">
              <thead>
                <tr>
                  <th>业绩下限</th>
                  <th>业绩上限（空=不限）</th>
                  <th>佣金比例 %</th>
                  <th width="80"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in promoterTierRows" :key="i">
                  <td><input v-model.number="row.minPerformance" type="number" min="0" class="input sm" /></td>
                  <td><input v-model.number="row.maxPerformance" type="number" min="0" placeholder="不限" class="input sm" /></td>
                  <td><input v-model.number="row.rate" type="number" min="0" max="100" step="0.5" class="input sm" /></td>
                  <td><button type="button" class="btn-link danger" @click="removePromoterTier(i)">删除</button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="promoterCommissionSaveMsg" :class="promoterCommissionSaveMsg === '已保存' ? 'save-ok' : 'save-err'">{{ promoterCommissionSaveMsg }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="closePromoterCommissionModal">取消</button>
          <button type="button" class="btn btn-primary" :disabled="promoterCommissionSaving" @click="savePromoterCommission">
            {{ promoterCommissionSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title { font-size: 18px; font-weight: 700; margin: 0 0 6px; color: #111827; }
.page-desc { font-size: 13px; color: #6b7280; margin: 0 0 20px; line-height: 1.5; }

.card { border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; background: #fff; margin-bottom: 20px; }
.card-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; background: #f9fafb; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
.card-title { font-size: 15px; font-weight: 600; color: #111827; }
.card-body { padding: 18px; }
.save-ok { color: #059669; font-size: 13px; }
.save-err { color: #dc2626; font-size: 13px; }

.form-row { margin-bottom: 16px; }
.form-row label { display: block; font-size: 13px; color: #374151; margin-bottom: 6px; }
.input-group { display: flex; align-items: center; gap: 8px; }
.input { padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; }
.input.num { width: 100px; }
.input.sm { width: 100%; max-width: 120px; }
.unit { font-size: 14px; color: #6b7280; }

.tier-section { margin-top: 20px; }
.tier-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px; }
.tier-head span { font-size: 13px; color: #374151; }
.tier-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.tier-table th, .tier-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f3f4f6; }
.tier-table th { background: #f9fafb; color: #6b7280; font-weight: 600; }
.btn-link { background: none; border: none; color: #2563eb; cursor: pointer; font-size: 13px; padding: 0; }
.btn-link.danger { color: #dc2626; }
.btn-link:hover { text-decoration: underline; }

.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center; }
.search-input { width: 220px; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 13px; }
.table-wrap { border: 1px solid #e5e7eb; border-radius: 8px; overflow: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 12px 14px; background: #f9fafb; color: #6b7280; font-weight: 600; border-bottom: 1px solid #e5e7eb; }
.data-table td { padding: 12px 14px; border-bottom: 1px solid #f3f4f6; }
.data-table .num { font-variant-numeric: tabular-nums; }
.data-table .commission { color: #059669; font-weight: 600; }
.invite-code { font-size: 12px; color: #4b5563; background: #f3f4f6; padding: 2px 6px; border-radius: 4px; }
.badge.count { background: #dbeafe; color: #1d4ed8; padding: 2px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; }
.badge-global { background: #f3f4f6; color: #6b7280; padding: 2px 6px; border-radius: 4px; font-size: 11px; margin-right: 6px; }
.badge-custom { background: #dbeafe; color: #1d4ed8; padding: 2px 6px; border-radius: 4px; font-size: 11px; margin-right: 6px; }
.btn-set { margin-left: 0; }
.tag-sm.normal { background: #e0e7ff; color: #3730a3; }
.tag-sm.internal { background: #fef3c7; color: #92400e; }
.modal-wrap.modal-commission { max-width: 560px; }

.pagination-wrapper { display: flex; align-items: center; gap: 12px; margin-top: 16px; flex-wrap: wrap; }
.pagination-wrapper button { padding: 6px 14px; border: 1px solid #d1d5db; background: #fff; border-radius: 6px; cursor: pointer; font-size: 13px; }
.pagination-wrapper button:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: #6b7280; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal-wrap { width: 90%; max-width: 720px; max-height: 85vh; background: #fff; border-radius: 10px; box-shadow: 0 10px 40px rgba(0,0,0,0.15); overflow: hidden; display: flex; flex-direction: column; }
.modal-header { padding: 14px 18px; border-bottom: 1px solid #e5e7eb; display: flex; justify-content: space-between; align-items: center; background: #f9fafb; }
.modal-title { font-size: 14px; font-weight: 600; }
.modal-body { padding: 0; overflow: auto; max-height: 60vh; }
.inner-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.inner-table th { text-align: left; padding: 10px 14px; background: #f9fafb; border-bottom: 1px solid #e5e7eb; color: #6b7280; }
.inner-table td { padding: 10px 14px; border-bottom: 1px solid #f3f4f6; }
.inner-table .num { font-variant-numeric: tabular-nums; }
.inner-table .time { color: #6b7280; font-size: 12px; }
.tag-sm { padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.tag-sm.ok { background: #d1fae5; color: #065f46; }
.tag-sm.err { background: #fee2e2; color: #991b1b; }
.modal-footer { padding: 12px 18px; background: #f9fafb; border-top: 1px solid #e5e7eb; text-align: right; }
.btn-cancel { padding: 8px 18px; border: 1px solid #d1d5db; border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px; }
.modal-close { cursor: pointer; color: #6b7280; padding: 4px; }
.modal-close:hover { color: #111827; }

.loading-cell, .no-data-cell { text-align: center; color: #9ca3af; padding: 32px; font-size: 13px; }
.modal-loading { padding: 40px; text-align: center; color: #9ca3af; }
.error { color: #dc2626; font-size: 13px; padding: 10px; background: #fef2f2; border-radius: 6px; margin-bottom: 12px; }

.btn { padding: 8px 14px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; }
.btn-sm { padding: 6px 12px; font-size: 12px; }
.btn-primary { background: #2563eb; color: #fff; }
.btn-default { background: #f3f4f6; color: #374151; border: 1px solid #e5e7eb; }
.btn-ghost { background: transparent; color: #6b7280; border: 1px solid #e5e7eb; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-icon { vertical-align: middle; margin-right: 4px; }
</style>
