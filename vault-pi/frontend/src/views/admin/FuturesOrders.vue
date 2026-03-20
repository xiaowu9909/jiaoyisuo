<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const orders = ref([])
const page = ref(1)
const totalPages = ref(0)
const totalElements = ref(0)

async function loadOrders() {
  try {
    const res = await fetch(`${API_BASE}/admin/futures/orders?pageNo=${page.value}&pageSize=20`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) {
      orders.value = json.data.content || []
      totalPages.value = json.data.totalPages
      totalElements.value = json.data.totalElements
    }
  } catch (_) {}
}

function gotoPage(p) { page.value = p; loadOrders() }
function fmtTime(t) { if (!t) return '-'; return new Date(t).toLocaleString('zh-CN') }
function fmtNum(n) { return n != null ? Number(n).toFixed(4) : '-' }

onMounted(loadOrders)
</script>

<template>
  <div class="admin-page">
    <h1 class="page-title">合约订单</h1>
    <p class="total">共 {{ totalElements }} 条</p>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>订单ID</th><th>用户ID</th><th>交易对</th><th>方向</th><th>类型</th><th>价格</th><th>数量</th><th>杠杆</th><th>状态</th><th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in orders" :key="o.id">
            <td>{{ o.id?.substring(0, 8) }}...</td>
            <td>{{ o.memberId }}</td>
            <td>{{ o.symbol }}</td>
            <td :class="o.direction === 'LONG' ? 'c-green' : 'c-red'">{{ o.direction }}</td>
            <td>{{ o.type }}</td>
            <td>{{ fmtNum(o.price) }}</td>
            <td>{{ fmtNum(o.amount) }}</td>
            <td>{{ o.leverage }}x</td>
            <td>{{ o.status }}</td>
            <td>{{ fmtTime(o.createTime) }}</td>
          </tr>
          <tr v-if="!orders.length"><td colspan="10" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
    <div class="pagination" v-if="totalPages > 1">
      <button v-for="p in totalPages" :key="p" :class="{ active: p === page }" @click="gotoPage(p)">{{ p }}</button>
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
