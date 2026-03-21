<script setup>
import { ref, onMounted } from 'vue'
import { getAiOrders } from '../../api'

const list = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const data = await getAiOrders()
    list.value = Array.isArray(data) ? data : []
  } catch (_) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function formatNum(val) {
  const n = Number(val)
  return isNaN(n) ? '0.0000' : n.toFixed(4)
}

function formatTime(val) {
  if (!val) return ''
  return new Date(val).toLocaleString()
}

function profitClass(profit) {
  const n = Number(profit)
  if (n > 0) return 'profit-pos'
  if (n < 0) return 'profit-neg'
  return 'profit-zero'
}

function profitText(profit) {
  const n = Number(profit)
  if (n > 0) return `+${formatNum(n)} USDT`
  return `${formatNum(n)} USDT`
}

function directionText(dir) {
  if (!dir) return ''
  const d = String(dir).toUpperCase()
  if (d === 'BUY') return '买入'
  if (d === 'SELL') return '卖出'
  return dir
}

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">AI订单记录</h2>

    <div v-if="loading" class="uc-hint">加载中...</div>

    <div v-else>
      <div v-if="list.length === 0" class="empty-hint">暂无AI订单记录</div>

      <div v-else class="order-list">
        <div
          v-for="item in list"
          :key="item.id"
          class="order-row"
          :class="{ 'order-missed': item.is_missed == 1 }"
        >
          <!-- Normal order -->
          <template v-if="item.is_missed == 0">
            <div class="order-main">
              <span class="order-symbol">{{ item.symbol }}</span>
              <span class="order-direction">{{ directionText(item.direction) }}</span>
              <span class="order-price mono">{{ formatNum(item.price) }}</span>
              <span class="order-amount mono">{{ formatNum(item.amount) }} USDT</span>
              <span class="order-profit mono" :class="profitClass(item.profit)">{{ profitText(item.profit) }}</span>
            </div>
            <div class="order-meta">
              <span class="order-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <div v-if="item.ai_note" class="order-note">{{ item.ai_note }}</div>
          </template>

          <!-- Missed order -->
          <template v-else>
            <div class="order-main missed-main">
              <span class="order-symbol missed-text">{{ item.symbol }}</span>
              <span class="order-direction missed-text">{{ directionText(item.direction) }}</span>
              <span class="order-price mono missed-text">{{ formatNum(item.price) }}</span>
              <span class="order-amount mono missed-text">{{ formatNum(item.amount) }} USDT</span>
            </div>
            <div class="missed-msg">
              策略已跳过。要求最低余额 <span class="mono">{{ formatNum(item.required_balance) }}</span> USDT，当前余额不足。预估错失利润：<span class="mono">+{{ formatNum(item.profit) }}</span> USDT
            </div>
            <div class="order-meta">
              <span class="order-time missed-text">{{ formatTime(item.createTime) }}</span>
            </div>
            <div v-if="item.ai_note" class="order-note missed-text">{{ item.ai_note }}</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uc-page-title {
  font-size: 18px;
  font-weight: 600;
  color: #e0ecf8;
  margin-bottom: 20px;
}

.uc-hint,
.empty-hint {
  color: #6b8299;
  font-size: 14px;
  padding: 24px 0;
  text-align: center;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-row {
  background: var(--ui-surface, #172636);
  border: 1px solid #1e3448;
  border-radius: 8px;
  padding: 14px 16px;
}

.order-missed {
  opacity: 0.5;
  filter: grayscale(100%);
}

.order-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.order-symbol {
  font-size: 14px;
  font-weight: 600;
  color: #e0ecf8;
}

.order-direction {
  font-size: 13px;
  color: #a0b4c8;
  background: #1e3448;
  border-radius: 4px;
  padding: 2px 8px;
}

.order-price,
.order-amount {
  font-size: 13px;
  color: #a0b4c8;
}

.order-profit {
  font-size: 14px;
  font-weight: 600;
  margin-left: auto;
}

.profit-pos { color: #22c55e; }
.profit-neg { color: #f87171; }
.profit-zero { color: #a0b4c8; }

.order-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.order-time {
  font-size: 12px;
  color: #4a6275;
}

.order-note {
  font-size: 12px;
  color: #4a6275;
  line-height: 1.5;
  margin-top: 4px;
}

.missed-main {
  margin-bottom: 4px;
}

.missed-text {
  color: #6b8299 !important;
}

.missed-msg {
  font-size: 13px;
  color: #6b8299;
  line-height: 1.6;
  margin-bottom: 6px;
}

.mono {
  font-family: monospace;
}
</style>
