<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminFuturesPositions, postAdminFuturesPositionForceClose } from '../api/admin'

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

async function forceClose(row) {
  if (!confirm(`确定要强制平仓持仓 #${row.id}（${row.username} / ${row.symbol}）？此操作将按当前市价结算并立即平仓。`)) return
  try {
    const res = await postAdminFuturesPositionForceClose(row.id)
    message.success(typeof res === 'string' ? res : '强制平仓成功')
    await loadData()
  } catch (e) {
    message.error(e.message || '操作失败')
  }
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
              <th>未实现盈亏</th>
              <th>状态</th>
              <th>操作</th>
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
              <td>
                <button v-if="row.status === 'OPEN'" class="btn-force-close" @click="forceClose(row)">强制平仓</button>
                <span v-else class="text-muted">—</span>
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
.page-title { font-size: 20px; font-weight: 700; color: var(--text-main); }

.admin-card { background: var(--ui-surface-2); border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 20px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 20px; }
.filter-bar input { padding: 8px 12px; border: 1px solid var(--border-color); border-radius: 6px; font-size: 14px; outline: none; }

.table-container { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; padding: 12px 16px; background: var(--table-th-bg); border-bottom: 1px solid var(--border-color); }
td { padding: 12px 16px; border-bottom: 1px solid var(--border-color); }

.user-info { display: flex; flex-direction: column; }
.user-info .name { font-weight: 600; }
.user-info .uid { font-size: 11px; color: var(--text-muted); }

.symbol { font-weight: 700; color: var(--color-accent-blue); }
.dir-stack { display: flex; flex-direction: column; }
.direction.buy { color: var(--color-emerald); }
.direction.sell { color: var(--color-danger-alt); }
.leverage { font-size: 11px; color: var(--ui-muted); }

.price-stack { display: flex; flex-direction: column; font-size: 11px; }
.entry { color: var(--text-main); }
.mark { color: var(--color-accent-blue); }
.liq-price { color: var(--color-danger-alt); font-weight: 600; }

.pnl { font-weight: 700; }
.pnl.plus { color: var(--color-emerald); }
.pnl.minus { color: var(--color-danger-alt); }

.badge { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; }
.badge.success { background: #f0fff4; color: var(--color-emerald); }

.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 20px; }
.pagination button { padding: 6px 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--ui-surface-2); cursor: pointer; }
.page-info { font-size: 13px; color: var(--ui-muted); }

.btn { padding: 8px 16px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: var(--color-accent-blue); color: var(--text-on-primary); }
.btn-ghost { background: var(--table-th-bg); color: var(--text-secondary); border-color: var(--border-color); }
.btn-force-close { padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; background: rgba(229, 62, 62, 0.1); color: var(--color-danger-alt); border: 1px solid rgba(229, 62, 62, 0.3); }
.btn-force-close:hover { background: rgba(229, 62, 62, 0.2); }
.text-muted { color: var(--text-muted); font-size: 13px; }
</style>
