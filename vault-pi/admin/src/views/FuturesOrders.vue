<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminFuturesOrders } from '../api/admin'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 20

const filterUid = ref('')
const filterSymbol = ref('')

async function loadData() {
  loading.value = true
  try {
    const data = await getAdminFuturesOrders(page.value, PAGE_SIZE, filterUid.value, filterSymbol.value)
    list.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function formatTime(str) {
  if (!str) return '—'
  return new Date(str).toLocaleString('zh-CN')
}

function getStatusBadge(status) {
  const map = {
    'TRADING': 'pending',
    'COMPLETED': 'success',
    'CANCELLED': 'rejected'
  }
  return map[status] || 'default'
}

function getStatusText(status) {
  const map = {
    'TRADING': '交易中',
    'COMPLETED': '已成交',
    'CANCELLED': '已撤单'
  }
  return map[status] || status
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">合约委托管理</h2>
    </div>

    <div class="admin-card">
      <div class="filter-bar">
        <input v-model="filterUid" placeholder="会员UID" @keyup.enter="page=1; loadData()" />
        <input v-model="filterSymbol" placeholder="交易对 (如 BTC/USDT)" @keyup.enter="page=1; loadData()" />
        <button class="btn btn-primary" @click="page=1; loadData()">查询</button>
        <button class="btn btn-ghost" @click="page=1; filterUid=''; filterSymbol=''; loadData()">重置</button>
      </div>

      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>订单ID</th>
              <th>用户信息</th>
              <th>交易对</th>
              <th>方向</th>
              <th>委托价 / 实成交</th>
              <th>委托量 / 成交量</th>
              <th>杠杆</th>
              <th>时间</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="9" class="text-center py-8">正在加载数据...</td></tr>
            <tr v-else-if="!list.length"><td colspan="9" class="text-center py-8">暂无原始成交数据</td></tr>
            <tr v-for="row in list" :key="row.orderId">
              <td><span class="order-id">{{ row.orderId }}</span></td>
              <td>
                <div class="user-info">
                  <span class="name">{{ row.username }}</span>
                  <span class="uid">UID: {{ row.memberId }}</span>
                </div>
              </td>
              <td><span class="symbol">{{ row.symbol }}</span></td>
              <td>
                <span :class="['direction', row.direction.toLowerCase()]">
                  {{ row.direction === 'BUY' ? '做多' : '做空' }}
                </span>
              </td>
              <td>
                <div class="amount-stack">
                  <span>{{ row.entrustPrice }}</span>
                  <span class="trades" v-if="row.tradedAmount > 0">Avg: {{ row.entrustPrice }}</span>
                </div>
              </td>
              <td>
                <div class="amount-stack">
                  <span class="total">{{ row.amount }}</span>
                  <span class="traded">Filled: {{ row.tradedAmount }}</span>
                </div>
              </td>
              <td><span class="leverage">{{ row.leverage }}x</span></td>
              <td>{{ formatTime(row.createTime) }}</td>
              <td>
                <span :class="['badge', getStatusBadge(row.status)]">{{ getStatusText(row.status) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button :disabled="page <= 1" @click="page--; loadData()">上一页</button>
        <span class="page-info">第 {{ page }} / {{ Math.ceil(total / PAGE_SIZE) || 1 }} 页 (共 {{ total }} 条)</span>
        <button :disabled="page >= Math.ceil(total / PAGE_SIZE)" @click="page++; loadData()">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; }

.admin-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 20px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 20px; }
.filter-bar input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; outline: none; }
.filter-bar input:focus { border-color: var(--primary-color); }

.table-container { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; border-bottom: 1px solid #edf2f7; }
td { padding: 12px 16px; border-bottom: 1px solid #edf2f7; vertical-align: middle; }

.order-id { font-family: monospace; color: #718096; font-size: 11px; }
.user-info { display: flex; flex-direction: column; }
.user-info .name { font-weight: 600; color: #2d3748; }
.user-info .uid { font-size: 11px; color: #a0aec0; }

.symbol { font-weight: 700; color: #3182ce; }
.direction.buy { color: #38a169; }
.direction.sell { color: #e53e3e; }

.amount-stack { display: flex; flex-direction: column; }
.amount-stack .trades, .amount-stack .traded { font-size: 11px; color: #a0aec0; }
.leverage { background: #edf2f7; padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; }

.badge { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.badge.pending { background: #fffaf0; color: #dd6b20; border: 1px solid #fbd38d; }
.badge.success { background: #f0fff4; color: #38a169; border: 1px solid #9ae6b4; }
.badge.rejected { background: #fff5f5; color: #e53e3e; border: 1px solid #feb2b2; }

.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 20px; }
.pagination button { padding: 6px 16px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; cursor: pointer; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: #718096; }

.btn { padding: 8px 16px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
</style>
