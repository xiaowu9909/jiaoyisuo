<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const stats = ref({ totalMembers: 0, totalOrders: 0, openPositions: 0, pendingOrders: 0 })

async function loadStats() {
  try {
    const res = await fetch(`${API_BASE}/admin/futures/stats`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) stats.value = json.data
  } catch (_) {}
}

onMounted(loadStats)
</script>

<template>
  <div class="dashboard">
    <h1 class="page-title">仪表盘</h1>
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon">👥</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.totalMembers }}</div>
          <div class="stat-label">注册用户</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">📋</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.totalOrders }}</div>
          <div class="stat-label">总订单数</div>
        </div>
      </div>
      <div class="stat-card accent">
        <div class="stat-icon">📈</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.openPositions }}</div>
          <div class="stat-label">持仓中</div>
        </div>
      </div>
      <div class="stat-card warn">
        <div class="stat-icon">⏳</div>
        <div class="stat-body">
          <div class="stat-value">{{ stats.pendingOrders }}</div>
          <div class="stat-label">挂单中</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title { font-size: 22px; font-weight: 700; margin: 0 0 24px; color: #fff; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.stat-card {
  background: #172636;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid #1e2d3d;
  transition: transform 0.15s;
}
.stat-card:hover { transform: translateY(-2px); }
.stat-icon { font-size: 32px; }
.stat-value { font-size: 28px; font-weight: 700; color: #fff; }
.stat-label { font-size: 12px; color: #828ea1; margin-top: 2px; }
.stat-card.accent .stat-value { color: #0ecb81; }
.stat-card.warn .stat-value { color: #f0a70a; }
</style>
