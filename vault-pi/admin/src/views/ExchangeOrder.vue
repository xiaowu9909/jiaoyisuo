<script setup>
import { ref, onMounted } from 'vue'
import { getAdminExchangeOrderPage } from '../api/admin'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorMsg = ref('')
const pageNo = ref(1)
const pageSize = ref(20)

// Filters
const filterSymbol = ref('')
const filterMemberId = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminExchangeOrderPage(
      pageNo.value, 
      pageSize.value, 
      filterMemberId.value, 
      filterSymbol.value
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

function formatTime(str) {
  if (!str) return '—'
  return new Date(str).toLocaleString('zh-CN')
}

function getStatusClass(status) {
  if (status === 'TRADING') return 'tag-trading'
  if (status === 'COMPLETED') return 'tag-completed'
  return 'tag-cancelled'
}

function getStatusText(status) {
  if (status === 'TRADING') return '交易中'
  if (status === 'COMPLETED') return '已成交'
  if (status === 'CANCELED' || status === 'CANCELLED') return '已撤销'
  return status || '—'
}

onMounted(load)
</script>

<template>
  <div class="admin-page exchange-order-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">全站币币委托订单审计</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load">
          <SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新数据
        </button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <div class="search-wrapper">
            <input v-model="filterMemberId" type="text" class="search-input" placeholder="按会员 UID 查询" />
            <input v-model="filterSymbol" type="text" class="search-input" placeholder="按交易对(BTC/USDT)" />
            <button type="button" class="btn btn-info" @click="handleSearch">检索订单</button>
          </div>
        </div>

        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>订单编号</th>
                <th>UID</th>
                <th>会员名称</th>
                <th>交易对</th>
                <th>方向/类型</th>
                <th>委托价格</th>
                <th>委托数量</th>
                <th>成交进度</th>
                <th>状态</th>
                <th>下单时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="10" class="loading-cell">正在穿透审计交易数据库...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="10" class="no-data-cell">未监测到符合审计条件的订单记录</td>
              </tr>
              <tr v-for="o in list" :key="o.orderId">
                <td class="order-id">{{ o.orderId }}</td>
                <td>#{{ o.memberId }}</td>
                <td>{{ o.username }}</td>
                <td><b style="color: #111827">{{ o.symbol }}</b></td>
                <td>
                  <span :class="['dir-tag', o.direction === 'BUY' ? 'buy' : 'sell']">
                    {{ o.direction === 'BUY' ? '买入' : '卖出' }}
                  </span>
                  <span class="type-text">/ {{ o.type === 'LIMIT_PRICE' ? '限价' : '市价' }}</span>
                </td>
                <td>{{ o.price }}</td>
                <td>{{ o.amount }}</td>
                <td>
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill" :style="{ width: (o.amount > 0 ? (o.tradedAmount / o.amount) * 100 : 0) + '%' }"></div>
                  </div>
                  <div class="progress-text">{{ o.tradedAmount }} / {{ o.amount }}</div>
                </td>
                <td>
                  <span :class="['status-tag', getStatusClass(o.status)]">
                    {{ getStatusText(o.status) }}
                  </span>
                </td>
                <td class="time-cell">{{ formatTime(o.createTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination-wrapper" v-if="total > pageSize">
          <button :disabled="pageNo === 1" @click="handlePageChange(pageNo - 1)">上一页</button>
          <span class="page-info">第 {{ pageNo }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页 (总计 {{ total }} 条)</span>
          <button :disabled="pageNo * pageSize >= total" @click="handlePageChange(pageNo + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exchange-order-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }

.function-wrapper { margin-bottom: 20px; }
.search-wrapper { display: flex; align-items: center; gap: 10px; }
.search-input { width: 180px; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 13px; }

.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; transition: all 0.2s; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-info { background: #f7fafc; color: #4a5568; border: 1px solid #e2e8f0; }
.btn:hover { opacity: 0.9; transform: translateY(-1px); }

.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 1000px; }
.data-table th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; border-bottom: 1px solid #edf2f7; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; vertical-align: middle; }

.order-id { font-family: monospace; color: #718096; font-size: 12px; }
.dir-tag { padding: 2px 6px; border-radius: 3px; font-weight: 700; font-size: 11px; margin-right: 4px; }
.dir-tag.buy { background: rgba(0, 178, 117, 0.1); color: #00b275; }
.dir-tag.sell { background: rgba(241, 80, 87, 0.1); color: #f15057; }
.type-text { color: #a0aec0; font-size: 12px; }

.progress-bar-bg { height: 6px; background: #edf2f7; border-radius: 3px; overflow: hidden; width: 80px; margin-bottom: 4px; }
.progress-bar-fill { height: 100%; background: #2d8cf0; transition: width 0.3s; }
.progress-text { font-size: 11px; color: #94a3b8; }

.status-tag { padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.tag-trading { background: #ebf8ff; color: #2b6cb0; }
.tag-completed { background: #f0fff4; color: #2f855a; }
.tag-cancelled { background: #f7fafc; color: #718096; }

.time-cell { color: #718096; white-space: nowrap; font-size: 12px; }

.pagination-wrapper { display: flex; justify-content: center; align-items: center; gap: 15px; margin-top: 24px; padding: 10px; }
.pagination-wrapper button { padding: 6px 16px; border: 1px solid #e2e8f0; background: #fff; border-radius: 4px; cursor: pointer; }
.pagination-wrapper button:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: #64748b; }

.error { color: #e53e3e; padding: 10px; font-size: 13px; }
.loading-cell, .no-data-cell { text-align: center; color: #a0aec0; padding: 40px; }
</style>
