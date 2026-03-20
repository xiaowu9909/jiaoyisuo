<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminFuturesPositions } from '../api/admin'

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
    const data = await getAdminFuturesPositions(page.value, PAGE_SIZE, filterUid.value, filterSymbol.value)
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

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">全站持仓实时监控</h2>
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
              <th>ID</th>
              <th>用户信息</th>
              <th>交易对</th>
              <th>方向 / 杠杆</th>
              <th>持仓数量</th>
              <th>开仓均价 / 标记价</th>
              <th>强平价格</th>
              <th>保证金</th>
              <th>未实现盈亏</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="10" class="text-center py-8">实时同步中...</td></tr>
            <tr v-else-if="!list.length"><td colspan="10" class="text-center py-8">当前无活跃持仓</td></tr>
            <tr v-for="row in list" :key="row.id">
              <td>{{ row.id }}</td>
              <td>
                <div class="user-info">
                  <span class="name">{{ row.username }}</span>
                  <span class="uid">UID: {{ row.memberId }}</span>
                </div>
              </td>
              <td><span class="symbol">{{ row.symbol }}</span></td>
              <td>
                <div class="dir-stack">
                  <span :class="['direction', row.direction.toLowerCase()]">
                    {{ row.direction === 'BUY' ? '做多' : '做空' }}
                  </span>
                  <span class="leverage">{{ row.leverage }}x</span>
                </div>
              </td>
              <td><strong>{{ row.amount }}</strong></td>
              <td>
                <div class="price-stack">
                  <span class="entry">Entry: {{ row.entryPrice }}</span>
                  <span class="mark">Mark: {{ row.markPrice }}</span>
                </div>
              </td>
              <td><span class="liq-price">{{ row.liquidationPrice }}</span></td>
              <td>{{ row.margin }}</td>
              <td>
                <span :class="['pnl', row.unrealizedPnl >= 0 ? 'plus' : 'minus']">
                  {{ row.unrealizedPnl >= 0 ? '+' : '' }}{{ row.unrealizedPnl }}
                </span>
              </td>
              <td>
                <span :class="['badge', row.status === 'OPEN' ? 'success' : 'default']">
                  {{ row.status === 'OPEN' ? '持仓中' : '已平仓' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button :disabled="page <= 1" @click="page--; loadData()">上一页</button>
        <span class="page-info">第 {{ page }} / {{ Math.ceil(total / PAGE_SIZE) || 1 }} 页</span>
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

.table-container { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; padding: 12px 16px; background: #f7fafc; border-bottom: 1px solid #edf2f7; }
td { padding: 12px 16px; border-bottom: 1px solid #edf2f7; }

.user-info { display: flex; flex-direction: column; }
.user-info .name { font-weight: 600; }
.user-info .uid { font-size: 11px; color: #a0aec0; }

.symbol { font-weight: 700; color: #3182ce; }
.dir-stack { display: flex; flex-direction: column; }
.direction.buy { color: #38a169; }
.direction.sell { color: #e53e3e; }
.leverage { font-size: 11px; color: #718096; }

.price-stack { display: flex; flex-direction: column; font-size: 11px; }
.entry { color: #2d3748; }
.mark { color: #3182ce; }
.liq-price { color: #e53e3e; font-weight: 600; }

.pnl { font-weight: 700; }
.pnl.plus { color: #38a169; }
.pnl.minus { color: #e53e3e; }

.badge { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.badge.success { background: #f0fff4; color: #38a169; }

.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 20px; }
.pagination button { padding: 6px 16px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; cursor: pointer; }
.page-info { font-size: 13px; color: #718096; }

.btn { padding: 8px 16px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
</style>
