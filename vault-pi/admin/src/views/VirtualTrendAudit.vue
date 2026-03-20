<script setup>
import { ref, onMounted } from 'vue'
import { getAdminVirtualTrendAuditPage } from '../api/admin'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorMsg = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const filterSymbol = ref('')
const filterAdminId = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminVirtualTrendAuditPage(
      pageNo.value,
      pageSize.value,
      filterSymbol.value,
      filterAdminId.value || null
    )
    list.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  load()
}

function handlePageChange(p) {
  pageNo.value = p
  load()
}

function formatTime(ts) {
  if (ts == null) return '—'
  return new Date(Number(ts)).toLocaleString('zh-CN')
}

onMounted(load)
</script>

<template>
  <div class="admin-page virtual-trend-audit-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">虚拟盘趋势设置审计</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load">
          <SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新
        </button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <div class="search-wrapper">
            <input v-model="filterSymbol" type="text" class="search-input" placeholder="交易对 (如 ZRX/USDT)" />
            <input v-model="filterAdminId" type="text" class="search-input" placeholder="管理员 UID" />
            <button type="button" class="btn btn-info" @click="handleSearch">查询</button>
          </div>
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>操作时间</th>
                <th>管理员</th>
                <th>交易对</th>
                <th>方向</th>
                <th>涨跌幅%</th>
                <th>周期(秒)</th>
                <th>起始价</th>
                <th>操作IP</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="9" class="text-center">加载中...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="9" class="text-center">暂无记录</td>
              </tr>
              <tr v-for="row in list" :key="row.id">
                <td>{{ row.id }}</td>
                <td>{{ formatTime(row.operationTime) }}</td>
                <td>{{ row.adminUsername || ('UID ' + row.adminId) }}</td>
                <td>{{ row.symbol }}</td>
                <td>{{ row.direction }}</td>
                <td>{{ row.percent }}</td>
                <td>{{ row.duration }}</td>
                <td>{{ row.startPrice }}</td>
                <td>{{ row.ip || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="total > 0" class="pagination-wrap">
          <button type="button" class="btn btn-small" :disabled="pageNo <= 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <span class="pagination-info">第 {{ pageNo }} 页 / 共 {{ total }} 条</span>
          <button type="button" class="btn btn-small" :disabled="pageNo * pageSize >= total" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pagination-wrap { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.pagination-info { color: #64748b; font-size: 14px; }
.text-center { text-align: center; padding: 24px; color: #94a3b8; }
</style>
