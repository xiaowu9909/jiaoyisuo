<script setup>
import { message } from '../components/toast';

import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getAdminExchangeCoinList, postAdminVirtualTrend, postAdminVirtualTrendClear } from '../api/admin'

const list = ref([])
const tick = ref(0)
let tickTimer = null
const loading = ref(false)
const errorMsg = ref('')
const modalVisible = ref(false)
const modalSubmitting = ref(false)
const modalError = ref('')

const form = ref({
  symbol: '',
  direction: 'UP',
  percent: 5,
  duration: 1800
})

const virtualCoins = computed(() => (list.value || []).filter(c => c.virtual))

// 预期盈利计算器：投资金额、预期盈利、杠杆 → 需涨/跌的百分比（盈利 ≈ 投资 × 涨跌幅% × 杠杆）
const calcInvestment = ref('')
const calcProfit = ref('')
const calcLeverage = ref(1)
const calcPercent = computed(() => {
  const inv = Number(calcInvestment.value)
  const profit = Number(calcProfit.value)
  const lev = Number(calcLeverage.value)
  if (inv <= 0 || !Number.isFinite(inv)) return null
  if (!Number.isFinite(profit)) return null
  const leverage = lev > 0 && Number.isFinite(lev) ? lev : 1
  return (profit / (inv * leverage)) * 100
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminExchangeCoinList()
    list.value = data || []
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function openSetTrend() {
  form.value = { symbol: virtualCoins.value[0]?.symbol || '', direction: 'UP', percent: 5, duration: 1800 }
  modalError.value = ''
  modalVisible.value = true
}

async function submitTrend() {
  if (!form.value.symbol) {
    modalError.value = '请选择交易对'
    return
  }
  const percent = Number(form.value.percent)
  const duration = Number(form.value.duration)
  if (!(percent > 0 && percent <= 100)) {
    modalError.value = '涨跌幅须在 0～100 之间'
    return
  }
  if (duration < 60) {
    modalError.value = '周期至少 60 秒'
    return
  }
  modalSubmitting.value = true
  try {
    await postAdminVirtualTrend({
      symbol: form.value.symbol,
      direction: form.value.direction,
      percent,
      duration
    })
    modalVisible.value = false
    await load()
  } catch (e) {
    modalError.value = e.message
  } finally {
    modalSubmitting.value = false
  }
}

async function clearTrend(coin) {
  if (!confirm(`确定清除 ${coin.symbol} 的行情趋势？`)) return
  try {
    await postAdminVirtualTrendClear(coin.id)
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

function trendStatus(coin) {
  void tick.value
  if (!coin.trendDirection || coin.trendStartTime == null || coin.trendDuration == null) return null
  const endMs = coin.trendStartTime + coin.trendDuration * 1000
  const now = Date.now()
  if (now >= endMs) return { text: '已结束', remaining: 0 }
  const remaining = Math.ceil((endMs - now) / 1000)
  const dir = coin.trendDirection === 'UP' ? '上涨' : '下跌'
  return { text: `${dir} ${coin.trendPercent}%，剩余 ${remaining} 秒`, remaining }
}

onMounted(() => {
  load()
  tickTimer = setInterval(() => { tick.value++ }, 1000)
})
onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<template>
  <div class="admin-page virtual-market-page">
    <!-- 预期盈利计算器 -->
    <div class="admin-card calc-card">
      <div class="card-head">
        <span class="card-title">预期盈利计算器</span>
      </div>
      <div class="card-body">
        <p class="desc">输入投资金额、预期盈利与杠杆倍数，可得出达到该盈利需要价格<strong>上涨</strong>或<strong>下跌</strong>的百分比。公式：盈利 ≈ 投资金额 × 涨跌幅% × 杠杆（杠杆为 1 即按现货计）。</p>
        <div class="calc-row">
          <div class="modal-form-item">
            <label>投资金额（结算币，如 USDT）</label>
            <input v-model="calcInvestment" type="number" min="0" step="any" class="input" placeholder="例如 10000" />
          </div>
          <div class="modal-form-item">
            <label>预期盈利（结算币）</label>
            <input v-model="calcProfit" type="number" step="any" class="input" placeholder="例如 500" />
          </div>
          <div class="modal-form-item">
            <label>杠杆倍数</label>
            <input v-model="calcLeverage" type="number" min="1" step="1" class="input" placeholder="例如 10" />
          </div>
        </div>
        <div v-if="calcPercent != null" class="calc-result">
          <span class="result-label">达到预期盈利需要：</span>
          <span class="result-value up">上涨 {{ calcPercent.toFixed(2) }}%</span>
          <span class="result-sep">或</span>
          <span class="result-value down">下跌 {{ calcPercent.toFixed(2) }}%</span>
        </div>
        <p v-else class="calc-hint">请输入投资金额（大于 0）、预期盈利及杠杆倍数（≥1）后自动计算。</p>
      </div>
    </div>

    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">虚拟盘行情控制</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load">
          <SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新
        </button>
      </div>
      <div class="card-body">
        <p class="desc">为虚拟盘设置在一段时间内的涨跌目标，行情将在周期内自然上浮/下跌（带心跳波动）。例如：ZRX/USDT 上涨 5%，周期 1800 秒（30 分钟）。</p>
        <div class="toolbar">
          <button type="button" class="btn btn-primary" @click="openSetTrend" :disabled="!virtualCoins.length">
            设置行情趋势
          </button>
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>交易对</th>
                <th>价格区间/自设价</th>
                <th>当前趋势</th>
                <th>剩余时间</th>
                <th style="text-align: right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="5" class="loading-cell">加载中...</td>
              </tr>
              <tr v-else-if="!virtualCoins.length">
                <td colspan="5" class="no-data-cell">暂无虚拟盘交易对，请先在「交易对列表」中新增并勾选虚拟盘</td>
              </tr>
              <tr v-for="c in virtualCoins" :key="c.id">
                <td><b>{{ c.symbol }}</b></td>
                <td>{{ c.customPriceLow != null && c.customPriceHigh != null ? `${c.customPriceLow} - ${c.customPriceHigh}` : (c.customPrice ?? '—') }}</td>
                <td>
                  <template v-if="c.trendDirection">
                    <span :class="['trend-tag', c.trendDirection === 'UP' ? 'up' : 'down']">
                      {{ c.trendDirection === 'UP' ? '上涨' : '下跌' }} {{ c.trendPercent }}%
                    </span>
                    <span class="trend-meta">周期 {{ c.trendDuration }} 秒</span>
                  </template>
                  <span v-else class="muted">—</span>
                </td>
                <td>
                  <template v-if="trendStatus(c)">
                    <span :class="{ 'text-success': trendStatus(c).remaining > 0, 'muted': trendStatus(c).remaining === 0 }">
                      {{ trendStatus(c).text }}
                    </span>
                  </template>
                  <span v-else class="muted">—</span>
                </td>
                <td style="text-align: right">
                  <button v-if="c.trendDirection" type="button" class="btn-sm btn-danger-lite" @click="clearTrend(c)">清除趋势</button>
                  <span v-else class="muted">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="modalVisible" class="modal-mask" @click.self="modalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">设置行情趋势</div>
          <span class="modal-close" @click="modalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>交易对</label>
            <select v-model="form.symbol" class="input">
              <option value="">请选择</option>
              <option v-for="c in virtualCoins" :key="c.id" :value="c.symbol">{{ c.symbol }}</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>方向</label>
            <select v-model="form.direction" class="input">
              <option value="UP">上涨</option>
              <option value="DOWN">下跌</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>涨跌幅（%）</label>
            <input v-model.number="form.percent" type="number" min="0.01" max="100" step="0.1" class="input" placeholder="例如 5" />
          </div>
          <div class="modal-form-item">
            <label>周期（秒）</label>
            <input v-model.number="form.duration" type="number" min="60" class="input" placeholder="1800 = 30 分钟" />
            <p class="hint">在此时长内达到目标涨跌幅，期间行情带心跳自然波动</p>
          </div>
          <p v-if="modalError" class="error-tip">{{ modalError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="modalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitTrend" :disabled="modalSubmitting">
            {{ modalSubmitting ? '提交中...' : '确认设置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-market-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }
.desc { color: #64748b; font-size: 13px; margin-bottom: 16px; }
.calc-card { margin-bottom: 20px; }
.calc-row { display: flex; gap: 20px; margin-bottom: 16px; }
.calc-row .modal-form-item { flex: 1; margin-bottom: 0; }
.calc-result { padding: 12px 16px; background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; font-size: 14px; }
.calc-result .result-label { color: #166534; margin-right: 8px; }
.calc-result .result-value { font-weight: 600; }
.calc-result .result-value.up { color: #16a34a; }
.calc-result .result-value.down { color: #dc2626; }
.calc-result .result-sep { margin: 0 10px; color: #64748b; }
.calc-hint { color: #94a3b8; font-size: 13px; }
.toolbar { margin-bottom: 20px; }
.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-sm { padding: 4px 10px; font-size: 12px; border-radius: 4px; cursor: pointer; border: none; margin-left: 6px; }
.btn-danger-lite { background: rgba(229, 62, 62, 0.1); color: #e53e3e; }
.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; }
.trend-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.trend-tag.up { background: #c6f6d5; color: #22543d; }
.trend-tag.down { background: #fed7d7; color: #822727; }
.trend-meta { margin-left: 8px; color: #718096; font-size: 12px; }
.muted { color: #a0aec0; }
.text-success { color: #38a169; }
.error { color: #e53e3e; padding: 10px; font-size: 13px; }
.loading-cell, .no-data-cell { text-align: center; color: #a0aec0; padding: 30px; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(2px); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal-wrap { width: 400px; background: #fff; border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
.modal-header { padding: 16px 20px; border-bottom: 1px solid #edf2f7; display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-size: 14px; font-weight: 600; }
.modal-body { padding: 20px; }
.modal-form-item { margin-bottom: 16px; }
.modal-form-item label { display: block; margin-bottom: 6px; color: #4a5568; font-size: 13px; }
.input { width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; }
select.input { cursor: pointer; }
.hint { font-size: 12px; color: #718096; margin-top: 4px; }
.modal-footer { padding: 16px 20px; background: #f8fafc; text-align: right; border-top: 1px solid #edf2f7; }
.btn-cancel { padding: 7px 18px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; margin-right: 10px; cursor: pointer; }
.error-tip { color: #e53e3e; font-size: 12px; margin-top: 10px; }
</style>
