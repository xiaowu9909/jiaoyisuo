<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const positions = ref([])
const page = ref(1)
const totalPages = ref(0)
const totalElements = ref(0)

async function loadPositions() {
  try {
    const res = await fetch(`${API_BASE}/admin/futures/positions?pageNo=${page.value}&pageSize=20`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) {
      positions.value = json.data.content || []
      totalPages.value = json.data.totalPages
      totalElements.value = json.data.totalElements
    }
  } catch (_) {}
}

function gotoPage(p) { page.value = p; loadPositions() }
function fmtTime(t) { if (!t) return '-'; return new Date(t).toLocaleString('zh-CN') }
function fmtNum(n) { return n != null ? Number(n).toFixed(4) : '-' }

onMounted(loadPositions)
</script>

<template>
  <div class="admin-page">
    <h1 class="page-title">合约持仓</h1>
    <p class="total">共 {{ totalElements }} 条</p>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th><th>用户ID</th><th>交易对</th><th>方向</th><th>数量</th><th>开仓均价</th><th>保证金</th><th>杠杆</th><th>状态</th><th>开仓时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in positions" :key="p.id">
            <td>{{ p.id }}</td>
            <td>{{ p.memberId }}</td>
            <td>{{ p.symbol }}</td>
            <td :class="p.direction === 'LONG' ? 'c-green' : 'c-red'">{{ p.direction }}</td>
            <td>{{ fmtNum(p.volume) }}</td>
            <td>{{ fmtNum(p.avgPrice) }}</td>
            <td>{{ fmtNum(p.margin) }}</td>
            <td>{{ p.leverage }}x</td>
            <td><span :class="p.status === 'OPEN' ? 'c-green' : ''">{{ p.status }}</span></td>
            <td>{{ fmtTime(p.createTime) }}</td>
          </tr>
          <tr v-if="!positions.length"><td colspan="10" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
    <div class="pagination" v-if="totalPages > 1">
      <button v-for="pg in totalPages" :key="pg" :class="{ active: pg === page }" @click="gotoPage(pg)">{{ pg }}</button>
    </div>
  </div>
</template>

<style scoped>
.admin-page { max-width: 1200px; }
.page-title { font-size: 22px; font-weight: 700; margin: 0 0 4px; color: #fff; }
.total { color: #828ea1; font-size: 12px; margin: 0 0 16px; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 8px 10px; text-align: left; color: #828ea1; border-bottom: 1px solid #1e2d3d; font-weight: 600; white-space: nowrap; }
.data-table td { padding: 8px 10px; border-bottom: 1px solid #1e2d3d; color: #e4e4e7; white-space: nowrap; }
.c-green { color: #0ecb81; }
.c-red { color: #f6465d; }
.empty { color: #828ea1; text-align: center; padding: 40px; }
.pagination { display: flex; gap: 4px; margin-top: 16px; }
.pagination button { padding: 4px 10px; background: #172636; border: 1px solid #27313e; color: #828ea1; border-radius: 4px; cursor: pointer; font-size: 12px; }
.pagination button.active { border-color: #f0a70a; color: #f0a70a; }
</style>
