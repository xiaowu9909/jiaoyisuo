<script setup>
import { ref, onMounted } from 'vue'
import { getAdminFinanceStatsPage } from '../api/admin'

const unit = ref('USDT')
const searchKey = ref('')
const startDate = ref('')
const endDate = ref('')
const list = ref([])
const summary = ref({
  totalMembers: 0,
  totalPeriodRecharge: 0,
  totalPeriodWithdraw: 0,
  totalPeriodPerformance: 0,
  startDate: '',
  endDate: '',
  unit: 'USDT',
})
const total = ref(0)
const totalPages = ref(0)
const currentPage = ref(1)
const pageSize = 20
const loading = ref(false)
const errorMsg = ref('')

function defaultDateRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 29)
  startDate.value = start.toISOString().slice(0, 10)
  endDate.value = end.toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminFinanceStatsPage({
      pageNo: currentPage.value,
      pageSize,
      searchKey: searchKey.value.trim(),
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      unit: unit.value,
    })
    list.value = data.content || []
    total.value = data.totalElements ?? 0
    totalPages.value = data.totalPages ?? 0
    summary.value = data.summary || summary.value
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function onSearch() {
  currentPage.value = 1
  load()
}

function changePage(p) {
  currentPage.value = p
  load()
}

function formatNum(v) {
  if (v == null || v === '') return '0'
  const n = Number(v)
  if (isNaN(n)) return String(v)
  if (n >= 1e8 || n <= -1e8) return n.toExponential(2)
  if (Math.abs(n) < 0.01 && n !== 0) return n.toFixed(6)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 8 })
}

onMounted(() => {
  defaultDateRange()
  load()
})
</script>

<template>
  <div class="finance-stats-page">
    <h1 class="page-title">财务统计</h1>
    <p class="page-desc">统计所有真实会员（非管理员、非内部）的财务数据，支持按用户名、邮箱、UID 查找与按时间筛选。</p>

    <div class="toolbar">
      <div class="toolbar-left">
        <input
          v-model="searchKey"
          type="text"
          class="form-input search-input"
          placeholder="用户名 / 邮箱 / UID"
          @keyup.enter="onSearch"
        />
        <span class="label">时间</span>
        <input v-model="startDate" type="date" class="form-input date-input" />
        <span class="label">至</span>
        <input v-model="endDate" type="date" class="form-input date-input" />
        <span class="label">币种</span>
        <select v-model="unit" class="form-select">
          <option value="USDT">USDT</option>
          <option value="BTC">BTC</option>
          <option value="ETH">ETH</option>
        </select>
        <button type="button" class="btn btn-primary" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

    <!-- 表头上方：总数 -->
    <div class="summary-row">
      <div class="summary-item">
        <span class="summary-label">会员总数</span>
        <span class="summary-value">{{ summary.totalMembers }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">期间充值合计 ({{ unit }})</span>
        <span class="summary-value num">{{ formatNum(summary.totalPeriodRecharge) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">期间提现合计 ({{ unit }})</span>
        <span class="summary-value num">{{ formatNum(summary.totalPeriodWithdraw) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">期间业绩合计 ({{ unit }})</span>
        <span class="summary-value num" :class="Number(summary.totalPeriodPerformance) >= 0 ? 'text-success' : 'text-danger'">
          {{ formatNum(summary.totalPeriodPerformance) }}
        </span>
      </div>
      <div class="summary-item range">
        <span class="summary-label">统计区间</span>
        <span class="summary-value">{{ summary.startDate || startDate }} 至 {{ summary.endDate || endDate }}</span>
      </div>
    </div>

    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>UID</th>
            <th>用户名</th>
            <th>邮箱</th>
            <th>期间充值 ({{ unit }})</th>
            <th>期间提现 ({{ unit }})</th>
            <th>期间业绩 ({{ unit }})</th>
            <th>累计充值 ({{ unit }})</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="loading-cell">加载中…</td>
          </tr>
          <tr v-else-if="!list.length">
            <td colspan="7" class="no-data-cell">暂无数据</td>
          </tr>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.uid ?? '—' }}</td>
            <td><b>{{ row.username || '—' }}</b></td>
            <td>{{ row.email || '—' }}</td>
            <td class="num">{{ formatNum(row.periodRecharge) }}</td>
            <td class="num">{{ formatNum(row.periodWithdraw) }}</td>
            <td class="num" :class="Number(row.periodPerformance) >= 0 ? 'text-success' : 'text-danger'">
              {{ formatNum(row.periodPerformance) }}
            </td>
            <td class="num">{{ formatNum(row.totalRecharge) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination-wrapper" v-if="total > pageSize">
      <button type="button" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ Math.max(1, totalPages) }} 页，共 {{ total }} 条</span>
      <button type="button" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.finance-stats-page { padding: 24px; max-width: 1400px; }
.page-title { font-size: 20px; margin: 0 0 8px; font-weight: 700; color: #1f2937; }
.page-desc { color: #6b7280; font-size: 14px; margin: 0 0 20px; }

.toolbar { margin-bottom: 16px; }
.toolbar-left { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.search-input { width: 200px; }
.date-input { width: 140px; }
.label { font-size: 13px; color: #6b7280; }
.form-input, .form-select { padding: 8px 12px; border-radius: 8px; border: 1px solid #e5e7eb; font-size: 13px; }
.form-select { min-width: 90px; }
.btn { padding: 8px 16px; border-radius: 8px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: var(--primary-color, #6366f1); color: #fff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.error-msg { color: #dc2626; margin-bottom: 12px; }

.summary-row {
  display: flex; flex-wrap: wrap; gap: 20px; align-items: center;
  padding: 16px 20px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px;
  margin-bottom: 16px;
}
.summary-item { display: flex; flex-direction: column; gap: 4px; }
.summary-item.range { margin-left: auto; }
.summary-label { font-size: 12px; color: #64748b; }
.summary-value { font-size: 16px; font-weight: 600; color: #0f172a; }
.summary-value.num { font-variant-numeric: tabular-nums; }
.text-success { color: #059669; }
.text-danger { color: #dc2626; }

.table-wrap { overflow-x: auto; background: #fff; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px 14px; text-align: left; border-bottom: 1px solid #f1f5f9; }
.data-table th { background: #f8fafc; font-size: 12px; font-weight: 600; color: #475569; }
.data-table td.num { font-variant-numeric: tabular-nums; text-align: right; }
.loading-cell, .no-data-cell { text-align: center; color: #94a3b8; padding: 32px !important; }

.pagination-wrapper { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.pagination-wrapper button { padding: 6px 14px; border-radius: 8px; border: 1px solid #e2e8f0; background: #fff; cursor: pointer; font-size: 13px; }
.pagination-wrapper button:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: #64748b; }
</style>
