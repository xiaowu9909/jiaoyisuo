<script setup>
import { ref, onMounted, computed } from 'vue'
import { getAdminMemberPage, getAdminFuturesStats, getAdminFinanceStats, getAdminFinanceTrend } from '../api/admin'

const userStats = ref({
  registrationNum: 0,
  yesterdayRegistrationNum: 0,
  applicationNum: 0,
  yesterdayApplicationNum: 0,
  bussinessNum: 0,
  yesterdayBussinessNum: 0,
})

const futuresStats = ref({
  totalOrders: 0,
  activePositions: 0,
  pendingOrders: 0,
  totalVolume: '0'
})
const exchangeStats = ref({
  todayPerformance: 0,
  yesterdayPerformance: 0,
  monthPerformance: 0,
  unit: 'USDT',
})

const trendData = ref([])
const chartLoading = ref(false)

const trendPoints = computed(() => {
  if (!trendData.value.length) return ''
  const vals = trendData.value.map(d => Number(d.value))
  // Add padding to range
  let max = Math.max(...vals)
  let min = Math.min(...vals)
  if (max === min) {
    max += 100
    min -= 100
  }
  const range = max - min
  
  // ViewBox 1000 x 300
  return trendData.value.map((d, i) => {
    const x = i * (1000 / (trendData.value.length - 1))
    // Invert Y axis (0 is top)
    const normalized = (Number(d.value) - min) / range
    const y = 300 - (normalized * 260 + 20) // padding 20px top/bottom
    return `${x},${y}`
  }).join(' ')
})

const xAxisLabels = computed(() => {
  // Show only ~5-6 labels
  if (!trendData.value.length) return []
  const step = Math.ceil(trendData.value.length / 6)
  return trendData.value.filter((_, i) => i % step === 0 || i === trendData.value.length - 1)
})

const yAxisLabels = computed(() => {
   if (!trendData.value.length) return []
   const vals = trendData.value.map(d => Number(d.value))
   let max = Math.max(...vals)
   let min = Math.min(...vals)
   if (max === min) {
      max += 100
      min -= 100
   }
   // Return min, mid, max
   return [max, (max+min)/2, min].map(v => v.toFixed(2))
})

async function fetchFinanceStats() {
  try {
    const data = await getAdminFinanceStats(exchangeStats.value.unit)
    exchangeStats.value.todayPerformance = data.todayPerformance
    exchangeStats.value.yesterdayPerformance = data.yesterdayPerformance
    exchangeStats.value.monthPerformance = data.monthPerformance
    
    chartLoading.value = true
    const trend = await getAdminFinanceTrend(30, exchangeStats.value.unit)
    trendData.value = trend || []
  } catch (e) {
    console.error(e)
  } finally {
    chartLoading.value = false
  }
}

onMounted(async () => {
  try {
    const [memberData, fStats] = await Promise.all([
      getAdminMemberPage(1, 1),
      getAdminFuturesStats()
    ])
    userStats.value.registrationNum = memberData.totalElements || 0
    futuresStats.value = fStats
    
    await fetchFinanceStats()
  } catch (_) {}
})
</script>

<template>
  <UiPage
    title="系统总览"
    subtitle="实时洞察 Vault π 运行状态"
  >
    <template #actions>
      <span class="page-chip">控制中心</span>
    </template>

    <div class="dashboard-row">
      <UiCard
        kind="primary"
        title="用户指标"
        description="注册、实名与商家入驻情况"
      >
        <div class="card-grid">
          <div class="metric">
            <p class="metric-label">注册用户</p>
            <p class="metric-main">{{ userStats.registrationNum }}</p>
            <p class="metric-delta">+{{ userStats.yesterdayRegistrationNum }} / 24 小时</p>
          </div>
          <div class="metric">
            <p class="metric-label">实名用户</p>
            <p class="metric-main">{{ userStats.applicationNum }}</p>
            <p class="metric-delta">+{{ userStats.yesterdayApplicationNum }} / 24 小时</p>
          </div>
          <div class="metric">
            <p class="metric-label">认证商家</p>
            <p class="metric-main">{{ userStats.bussinessNum }}</p>
            <p class="metric-delta">+{{ userStats.yesterdayBussinessNum }} / 24 小时</p>
          </div>
        </div>
      </UiCard>

      <UiCard
        kind="warning"
        title="合约活跃度"
        description="持仓与委托实时状态"
      >
        <div class="card-grid">
          <div class="metric">
            <p class="metric-label">当前持仓</p>
            <p class="metric-main">{{ futuresStats.activePositions }}</p>
            <p class="metric-tag metric-tag-live">实时</p>
          </div>
          <div class="metric">
            <p class="metric-label">委托订单</p>
            <p class="metric-main">{{ futuresStats.pendingOrders }}</p>
            <p class="metric-tag metric-tag-pending">挂单中</p>
          </div>
          <div class="metric">
            <p class="metric-label">累计订单</p>
            <p class="metric-main">{{ futuresStats.totalOrders }}</p>
            <p class="metric-tag metric-tag-all">汇总</p>
          </div>
        </div>
      </UiCard>

      <UiCard
        kind="success"
        title="业绩表现"
        description="核心业务在不同时间维度的表现"
      >
        <div class="performance-head">
          <div>
            <p class="metric-label">当日盈亏</p>
            <p
              class="big-num"
              :class="Number(exchangeStats.todayPerformance) >= 0 ? 'text-success' : 'text-danger'"
            >
              {{ Number(exchangeStats.todayPerformance) > 0 ? '+' : '' }}{{ exchangeStats.todayPerformance }}
            </p>
            <p class="metric-note">口径：正常用户的充值 / 提现 / 手动加减余额</p>
          </div>
          <div class="unit-select">
            <label>统计币种</label>
            <select v-model="exchangeStats.unit" @change="fetchFinanceStats">
              <option value="USDT">USDT</option>
              <option value="BTC">BTC</option>
              <option value="ETH">ETH</option>
            </select>
          </div>
        </div>
        <div class="performance-footer">
          <div class="footer-col">
            <p>昨日业绩</p>
            <span :class="Number(exchangeStats.yesterdayPerformance) >= 0 ? 'text-success' : 'text-danger'">
              {{ Number(exchangeStats.yesterdayPerformance) > 0 ? '+' : '' }}{{ exchangeStats.yesterdayPerformance }}
            </span>
          </div>
          <div class="footer-col">
            <p>本月业绩</p>
            <span :class="Number(exchangeStats.monthPerformance) >= 0 ? 'text-success' : 'text-danger'">
              {{ Number(exchangeStats.monthPerformance) > 0 ? '+' : '' }}{{ exchangeStats.monthPerformance }}
            </span>
          </div>
        </div>
      </UiCard>
    </div>

    <UiCard
      kind="primary"
      title="30 日业绩曲线"
      description="基于当前币种的平滑盈亏轮廓"
    >
      <div class="chart-section">
        <div class="chart-toolbar">
          <span class="hint">近 30 日 · 充值 / 提现 / 手动加减余额</span>
        </div>
        <div class="chart-placeholder" v-if="chartLoading">
          <span>趋势加载中...</span>
        </div>
        <div class="chart-container" v-else-if="trendData.length">
          <div class="chart-wrap">
            <svg viewBox="0 0 1000 300" preserveAspectRatio="none">
              <defs>
                <linearGradient id="lineGradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stop-color="#38bdf8" />
                  <stop offset="100%" stop-color="#6366f1" />
                </linearGradient>
              </defs>

              <line x1="0" y1="20" x2="1000" y2="20" stroke="#1f2937" stroke-width="1" />
              <line x1="0" y1="150" x2="1000" y2="150" stroke="#111827" stroke-width="1" />
              <line x1="0" y1="280" x2="1000" y2="280" stroke="#1f2937" stroke-width="1" />

              <polyline
                :points="trendPoints"
                fill="none"
                stroke="url(#lineGradient)"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
              />

              <circle
                v-for="(p, i) in trendPoints.split(' ')"
                :key="i"
                :cx="p.split(',')[0]"
                :cy="p.split(',')[1]"
                r="4"
                fill="#020617"
                stroke="#38bdf8"
                stroke-width="2"
              />
            </svg>
          </div>

          <div class="x-axis">
            <span v-for="item in xAxisLabels" :key="item.date">{{ item.date.slice(5) }}</span>
          </div>
          <div class="y-axis">
            <span
              v-for="(v, i) in yAxisLabels"
              :key="i"
              :style="{ top: (i * 50) + '%' }"
            >
              {{ v }}
            </span>
          </div>
        </div>
        <div class="chart-placeholder" v-else>
          <span>暂无趋势数据</span>
        </div>
      </div>
    </UiCard>
  </UiPage>
</template>

<style scoped>
.admin-home { padding: 10px 0; }
.page-title { font-size: 20px; margin: 0 0 24px; font-weight: 700; color: #1f2937; }

.dashboard-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--box-shadow);
  overflow: hidden;
  border: none;
  display: flex;
  flex-direction: column;
}

.card-user { border-top: 4px solid #3b82f6; }
.card-otc { border-top: 4px solid #10b981; }
.card-exchange { border-top: 4px solid var(--primary-color); }

.card-head {
  display: flex;
  justify-content: space-between;
  padding: 16px 20px;
  background: #f9fafb;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-title {
  padding: 16px 20px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #f0f0f0;
}

.card-body {
  padding: 20px;
  flex: 1;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.card-row:last-child { border-bottom: none; }
.card-row span:first-child { color: #6b7280; font-size: 14px; }
.card-row span:nth-child(2) { font-size: 18px; font-weight: 700; color: #111827; }
.card-row span:last-child { 
  font-size: 12px; 
  padding: 2px 8px; 
  background: #ecfdf5; 
  color: #059669; 
  border-radius: 10px;
  font-weight: 600;
}

.big-num-wrap { text-align: center; padding: 10px 0; }
.big-num { font-size: 32px; font-weight: 800; color: #111827; margin: 8px 0; font-variant-numeric: tabular-nums; }
.unit-label { font-size: 12px; color: #9ca3af; margin-bottom: 15px; display: block; }

.fee-info { display: flex; justify-content: space-between; font-size: 14px; margin-top: 10px; color: #6b7280; }
.fee-info span { color: #111827; font-weight: 600; }

.form-select {
  width: 100%;
  padding: 8px;
  margin-top: 15px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
}

.card-footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #f9fafb;
  border-top: 1px solid #f0f0f0;
}
.footer-col { padding: 12px 16px; text-align: center; }
.footer-col:first-child { border-right: 1px solid #f0f0f0; }
.footer-col p { margin: 0 0 4px; color: #9ca3af; font-size: 11px; text-transform: uppercase; }
.footer-col span { font-weight: 700; color: #4b5563; font-size: 14px; }

.chart-section {
  margin-top: 6px;
  padding: 14px 16px 10px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top left, rgba(15, 23, 42, 0.9), rgba(15, 23, 42, 0.85)),
    radial-gradient(circle at bottom right, rgba(15, 23, 42, 0.9), rgba(15, 23, 42, 0.85));
  border: 1px solid rgba(15, 23, 42, 0.95);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.9);
}

.chart-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 11px;
  color: #6b7280;
}
.toolbar-left { display: flex; gap: 8px; }
.time-btn {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  cursor: pointer;
  background: #f3f4f6;
  color: #4b5563;
}
.time-btn.active { background: var(--primary-color); color: #fff; }

.date-input {
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
}

.chart-placeholder {
  padding: 32px 0;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 13px;
}
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }

.chart-container {
  height: 220px;
  position: relative;
}
.chart-wrap {
  width: 100%;
  height: 100%;
}
.chart-wrap svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}
.x-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  color: #6b7280;
  font-size: 11px;
}
.y-axis {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  color: #6b7280;
  font-size: 11px;
}
</style>
