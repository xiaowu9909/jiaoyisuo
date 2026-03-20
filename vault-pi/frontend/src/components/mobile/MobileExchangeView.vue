<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, onDeactivated, defineProps, defineEmits, defineExpose } from 'vue'
import { useI18n } from 'vue-i18n'
import MobileButton from './MobileButton.vue'
import MobileInput from './MobileInput.vue'
import MobileModal from './MobileModal.vue'

const props = defineProps({
  currentSymbol: { type: String, default: '' },
  currentThumbDisplay: { type: Object, default: () => ({}) },
  filteredSymbols: { type: Array, default: () => [] },
  symbolSearch: String,
  plate: { type: Object, default: () => ({ ask: [], bid: [] }) },
  trades: { type: Array, default: () => [] },
  currentPositions: { type: Array, default: () => [] },
  currentOrders: { type: Array, default: () => [] },
  historyOrders: { type: Array, default: () => [] },
  orderTab: String,
  walletBase: Number,
  orderType: String,
  orderPrice: [String, Number],
  longAmount: [String, Number],
  leverage: [String, Number],
  submitLoading: Boolean,
  orderLoading: Boolean,
  period: String,
  isLogin: Boolean,
  errorMsg: { type: String, default: '' },
  coinSymbol: { type: String, default: 'BTC' }
})

const emit = defineEmits([
  'update:symbolSearch',
  'update:orderTab',
  'update:orderType',
  'update:orderPrice',
  'update:longAmount',
  'update:leverage',
  'switchSymbol',
  'setPeriod',
  'fillPrice',
  'closePosition',
  'cancelOrder',
  'setQuickLeverage',
  'setQuickAmount',
  'doLong',
  'doShort',
  'clearError'
])

const { t } = useI18n()

// Local mobile states
const mobileActiveTab = ref('chart')
const showTradingModal = ref(false)
const tradeDirection = ref('LONG')
const showSymbolList = ref(false)
const mobileChartContainer = ref(null)
defineExpose({ mobileChartContainer })

function openTradingModal(dir) {
  tradeDirection.value = dir
  showTradingModal.value = true
  emit('clearError')
}

// 交易模态框开启时阻止背景页面滚动（侧滑/滚动不穿透）
watch(showTradingModal, (open) => {
  if (open) document.body.style.overflow = 'hidden'
  else document.body.style.overflow = ''
})
onDeactivated(() => {
  document.body.style.overflow = ''
})
onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

function handleSwitchSymbol(symbol) {
  emit('switchSymbol', symbol)
  showSymbolList.value = false
}

// Formatters (simple versions or passed as props if needed)
const formatNum = (val, fixed = 2) => {
  if (val === undefined || val === null) return '--'
  return Number(val).toLocaleString(undefined, { minimumFractionDigits: fixed, maximumFractionDigits: fixed })
}

const formatTime = (time, format) => {
  if (!time) return '--'
  const d = new Date(time)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

const calcPnl = (p) => {
  const close = props.currentThumbDisplay?.close
  if (close == null || p?.entryPrice == null || p?.amount == null) return 0
  const side = p.direction === 'LONG' ? 1 : -1
  return (Number(close) - Number(p.entryPrice)) * Number(p.amount) * side
}

const plateAsk = computed(() => (props.plate?.ask || []).slice(0, 15).reverse())
const plateBid = computed(() => (props.plate?.bid || []).slice(0, 15))
const plateMaxAmount = computed(() => {
  const a = plateAsk.value
  const b = plateBid.value
  let max = 0
  a.forEach((x) => { const v = Number(x?.amount); if (v > max) max = v })
  b.forEach((x) => { const v = Number(x?.amount); if (v > max) max = v })
  return max || 1
})
</script>

<template>
  <div class="mobile-exchange-layout">
    <!-- Header -->
    <header class="m-ex-header" @click="showSymbolList = !showSymbolList">
       <i class="icon-menu-m"></i>
       <span class="m-symbol-main">{{ currentSymbol || '—' }}</span>
       <span class="m-price-main" :class="{ up: (currentThumbDisplay?.chg ?? 0) > 0, down: (currentThumbDisplay?.chg ?? 0) < 0 }">
         {{ formatNum(currentThumbDisplay?.close) }}
       </span>
       <span class="m-chg-main" :class="{ up: (currentThumbDisplay?.chg ?? 0) > 0, down: (currentThumbDisplay?.chg ?? 0) < 0 }">
         {{ (currentThumbDisplay?.chg ?? 0) > 0 ? '+' : '' }}{{ ((currentThumbDisplay?.chg ?? 0) * 100).toFixed(2) }}%
       </span>
    </header>

    <!-- 图表(左) / 深度(中) / 成交(右) 三个 Tab -->
    <nav class="m-ex-tabs">
      <span class="m-tab-cell m-tab-left">
        <MobileButton
          variant="tab"
          :class="{ active: mobileActiveTab === 'chart' }"
          @click="mobileActiveTab = 'chart'"
        >
          {{ t('exchange.chart') }}
        </MobileButton>
      </span>
      <span class="m-tab-cell m-tab-center">
        <MobileButton
          variant="tab"
          :class="{ active: mobileActiveTab === 'orderbook' }"
          @click="mobileActiveTab = 'orderbook'"
        >
          {{ t('exchange.orderbook') }}
        </MobileButton>
      </span>
      <span class="m-tab-cell m-tab-right">
        <MobileButton
          variant="tab"
          :class="{ active: mobileActiveTab === 'trades' }"
          @click="mobileActiveTab = 'trades'"
        >
          {{ t('exchange.trades') }}
        </MobileButton>
      </span>
    </nav>

    <div class="m-ex-content">
      <!-- Tab 内容：图表 -->
      <div v-show="mobileActiveTab === 'chart'" class="m-chart-section">
        <div class="m-chart-controls">
          <button
            v-for="p in ['15m','1h','4h','1d']"
            :key="p"
            class="m-btn tab"
            :class="{ active: period === p }"
            @click="emit('setPeriod', p)"
          >
            {{ p }}
          </button>
        </div>
        <div ref="mobileChartContainer" class="m-chart-view"></div>
      </div>

      <!-- Tab 内容：深度 -->
      <div v-show="mobileActiveTab === 'orderbook'" class="m-plate-section">
        <div class="m-plate-split">
           <div class="m-plate-half">
             <div class="m-plate-col">
               <div v-for="(a, i) in plateAsk" :key="'ask-' + i" class="m-ask-row" @click="emit('fillPrice', a.price)">
                  <span class="price">{{ formatNum(a.price) }}</span>
                  <span class="num">{{ formatNum(a.amount) }}</span>
                  <div class="depth-bar" :style="{ width: (Number(a.amount) / plateMaxAmount * 100) + '%' }"></div>
               </div>
             </div>
           </div>
           <div class="m-plate-half">
             <div class="m-plate-col">
               <div v-for="(b, i) in plateBid" :key="'bid-' + i" class="m-bid-row" @click="emit('fillPrice', b.price)">
                  <span class="price">{{ formatNum(b.price) }}</span>
                  <span class="num">{{ formatNum(b.amount) }}</span>
                  <div class="depth-bar" :style="{ width: (Number(b.amount) / plateMaxAmount * 100) + '%' }"></div>
               </div>
             </div>
           </div>
        </div>
      </div>

      <!-- Tab 内容：成交 -->
      <div v-show="mobileActiveTab === 'trades'" class="m-trades-section">
         <div class="m-trades-list">
           <div class="m-trade-row m-trade-header">
             <span class="time">{{ t('exchange.time') }}</span>
             <span class="price">{{ t('exchange.price') }}</span>
             <span class="num">{{ t('exchange.num') }}</span>
           </div>
           <div v-for="(tr, i) in (trades || [])" :key="i" class="m-trade-row" :class="tr.direction === 'BUY' ? 'up' : 'down'">
             <span class="time">{{ formatTime(tr.time, 'HH:mm:ss') }}</span>
             <span class="price">{{ formatNum(tr.price) }}</span>
             <span class="num">{{ formatNum(tr.amount) }}</span>
           </div>
         </div>
      </div>

      <!-- 交易模块：买卖按钮 -->
      <section class="m-ex-actions-inline">
        <MobileButton block variant="success" @click="openTradingModal('LONG')">
          {{ t('exchange.buyLong') }}
        </MobileButton>
        <MobileButton block variant="danger" @click="openTradingModal('SHORT')">
          {{ t('exchange.sellShort') }}
        </MobileButton>
      </section>

      <!-- 订单模块：持仓 / 当前委托 -->
      <div class="m-history-section">
       <div class="m-history-tabs">
         <button class="m-btn tab" :class="{ active: orderTab === 'position' }" @click="emit('update:orderTab', 'position')">{{ t('exchange.position') }}</button>
         <button class="m-btn tab" :class="{ active: orderTab === 'current' }" @click="emit('update:orderTab', 'current')">{{ t('exchange.current') }}</button>
       </div>
       <div class="m-history-list">
          <template v-if="orderTab === 'position'">
             <div v-for="p in currentPositions" :key="p.id" class="m-pos-card">
                <div class="m-card-top">
                  <span class="m-card-symbol">{{ p.symbol }}</span>
                  <span class="m-card-tag" :class="p.direction === 'LONG' ? 'buy' : 'sell'">
                    {{ p.direction === 'LONG' ? t('exchange.long') : t('exchange.short') }} {{ p.leverage }}x
                  </span>
                  <button class="m-btn gray m-card-close" @click="emit('closePosition', p.id)">{{ t('exchange.closePosition') }}</button>
                </div>
                <div class="m-card-body">
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.unrealizedPnl') }}</span>
                    <span class="val" :class="calcPnl(p) >= 0 ? 'up' : 'down'">{{ formatNum(calcPnl(p), 2) }}</span>
                  </div>
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.entryPrice') }}</span>
                    <span class="val">{{ formatNum(p.entryPrice) }}</span>
                  </div>
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.positionAmt') }}</span>
                    <span class="val">{{ formatNum(p.amount) }}</span>
                  </div>
                </div>
             </div>
             <div v-if="!currentPositions.length" class="m-nodata-small">{{ t('common.nodata') }}</div>
          </template>
          <template v-if="orderTab === 'current'">
             <div v-for="o in currentOrders" :key="o.orderId" class="m-order-card">
                <div class="m-card-top">
                  <span class="m-card-symbol">{{ o.symbol }}</span>
                  <span class="m-card-tag" :class="o.direction === 'LONG' ? 'buy' : 'sell'">
                    {{ o.direction === 'LONG' ? t('exchange.long') : t('exchange.short') }} {{ o.leverage }}x
                  </span>
                  <button class="m-btn gray m-card-close" @click="emit('cancelOrder', o.orderId)">{{ t('exchange.undo') }}</button>
                </div>
                <div class="m-card-body">
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.orderPrice') }}</span>
                    <span class="val">{{ formatNum(o.price) }}</span>
                  </div>
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.num') }}</span>
                    <span class="val">{{ formatNum(o.amount) }}</span>
                  </div>
                  <div class="m-grid-item">
                    <span class="label">{{ t('exchange.status') }}</span>
                    <span class="val">{{ o.status === 'TRADING' ? t('exchange.statusTrading') : o.status }}</span>
                  </div>
                </div>
             </div>
             <div v-if="!currentOrders.length" class="m-nodata-small">{{ t('common.nodata') }}</div>
          </template>
       </div>
    </div>
    </div>

    <!-- Trading Drawer (Modal) -->
    <MobileModal
      v-model="showTradingModal"
      type="drawer"
      :title="tradeDirection === 'LONG' ? t('exchange.buyLong') : t('exchange.sellShort')"
    >
      <div class="m-wallet-bar">
         <span>{{ t('exchange.available') }}: {{ formatNum(walletBase, 2) }} USDT</span>
      </div>
      <p v-if="errorMsg" class="m-trade-error">{{ errorMsg }}</p>
      <div class="m-form-group">
         <div class="m-type-switch">
            <MobileButton
              variant="gray"
              :class="{ active: orderType === 'LIMIT' }"
              @click="emit('update:orderType', 'LIMIT')"
            >
              {{ t('exchange.limit') }}
            </MobileButton>
            <MobileButton
              variant="gray"
              :class="{ active: orderType === 'MARKET' }"
              @click="emit('update:orderType', 'MARKET')"
            >
              {{ t('exchange.market') }}
            </MobileButton>
         </div>
      </div>
      <div v-show="orderType === 'LIMIT'" class="m-form-field">
         <MobileInput
           :model-value="orderPrice"
           @update:model-value="v => emit('update:orderPrice', v)"
           :label="t('exchange.price')"
           unit="USDT"
           placeholder="0.00"
         />
      </div>
      <div class="m-form-field">
         <MobileInput
           :model-value="longAmount"
           @update:model-value="v => emit('update:longAmount', v)"
           :label="t('exchange.num')"
           :unit="coinSymbol"
           placeholder="0.00"
         />
      </div>
      <div class="m-quick-pct">
         <MobileButton
           v-for="pct in [25, 50, 75, 100]"
           :key="pct"
           variant="gray"
           @click="emit('setQuickAmount', pct)"
         >
           {{ pct }}%
         </MobileButton>
      </div>
      <div class="m-form-field">
         <MobileInput
           :model-value="leverage"
           @update:model-value="v => emit('update:leverage', v)"
           :label="t('exchange.leverage')"
           unit="x"
           type="number"
         />
      </div>
      <div class="m-lev-row">
         <MobileButton
           v-for="l in [10, 20, 50, 100]"
           :key="l"
           variant="gray"
           @click="emit('setQuickLeverage', l)"
         >
           {{ l }}x
         </MobileButton>
      </div>
      
      <div class="m-final-action">
         <MobileButton
          block
          :variant="tradeDirection === 'LONG' ? 'success' : 'danger'"
          @click="tradeDirection === 'LONG' ? emit('doLong') : emit('doShort')"
          :loading="submitLoading"
         >
           {{ tradeDirection === 'LONG' ? t('exchange.buyLong') : t('exchange.sellShort') }}
         </MobileButton>
      </div>
    </MobileModal>

    <!-- Symbol Selection Sidebar/Modal -->
    <div v-if="showSymbolList" class="m-symbol-list-modal" @click.self="showSymbolList = false">
       <div class="m-modal-inner">
         <div class="m-modal-header">
           <h3>{{ t('exchange.mainboard') }}</h3>
           <input :value="symbolSearch" @input="e => emit('update:symbolSearch', e.target.value)" type="text" :placeholder="t('common.searchplaceholder')" />
         </div>
         <div class="m-modal-list">
            <div v-for="s in (filteredSymbols || [])" :key="s.symbol || s.coinSymbol + s.baseSymbol" class="m-modal-item" @click="handleSwitchSymbol(s.symbol)">
              <span class="s-name">{{ s.symbol }}</span>
              <div class="s-right">
                <span class="s-price">{{ formatNum(s.close) }}</span>
                <span class="s-chg" :class="{ up: (s.chg != null && Number(s.chg) > 0), down: (s.chg != null && Number(s.chg) < 0) }">
                  {{ s.chg != null ? (Number(s.chg) * 100).toFixed(2) + '%' : '--' }}
                </span>
              </div>
            </div>
         </div>
       </div>
    </div>
  </div>
</template>

<style scoped>
/* Mobile-specific styles moved here to reduce Exchange.vue size */
.mobile-exchange-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #0b1520;
  color: #fff;
  overflow: hidden;
}

/* Header */
.m-ex-header {
  height: 56px;
  background: #172636;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  border-bottom: 1px solid #27313e;
  flex-shrink: 0;
}
.m-symbol-main { font-weight: 700; font-size: 18px; }
.m-price-main { font-size: 16px; font-family: 'Roboto Mono', monospace; font-weight: 600; }
.m-price-main.up { color: #0ecb81; }
.m-price-main.down { color: #f6465d; }
.m-chg-main { font-size: 13px; font-weight: 500; }
.m-chg-main.up { color: #0ecb81; }
.m-chg-main.down { color: #f6465d; }

.icon-menu-m { display: inline-block; width: 18px; height: 14px; border-top: 2px solid #fff; border-bottom: 2px solid #fff; position: relative; }
.icon-menu-m::after { content: ''; position: absolute; top: 4px; left: 0; width: 100%; height: 2px; background: #fff; }

/* Tab 导航：图表左对齐、深度居中、成交右对齐 */
.m-ex-tabs {
  display: flex;
  align-items: stretch;
  width: 100%;
  background: #172636;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}
.m-tab-cell {
  display: flex;
  align-items: center;
}
.m-tab-left { flex: 1; justify-content: flex-start; }
.m-tab-center { flex: 0; justify-content: center; padding: 0 8px; }
.m-tab-right { flex: 1; justify-content: flex-end; }

/* Content Area：Tab 内容 + 交易模块 + 订单模块，上下滚动 */
.m-ex-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: #0b1520;
  display: flex;
  flex-direction: column;
}

/* Chart */
.m-chart-section {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.m-chart-controls {
  padding: 8px 16px;
  display: flex;
  gap: 10px;
  background: #0f172a;
  border-bottom: 1px solid #1e293b;
  overflow-x: auto;
}
.m-chart-controls .m-btn.tab { padding: 10px 14px; font-size: 13px; }
.m-chart-view {
  min-height: 280px;
  height: 280px;
  width: 100%;
}

/* Plate (Orderbook) 深度 */
.m-plate-section {
  flex-shrink: 0;
  padding: 12px;
}
.m-plate-split { display: flex; gap: 8px; }
.m-plate-half { flex: 1; }
.m-plate-col { display: flex; flex-direction: column; gap: 2px; }
.m-ask-row, .m-bid-row { 
  display: flex; 
  justify-content: space-between; 
  font-size: 12px; 
  padding: 6px 8px; 
  position: relative;
  min-height: 44px;
  align-items: center;
  box-sizing: border-box;
  height: 24px;
  align-items: center;
  border-radius: 2px;
}
.m-ask-row { color: #f6465d; background: rgba(246, 70, 93, 0.05); }
.m-bid-row { color: #0ecb81; background: rgba(14, 203, 129, 0.05); }
.depth-bar { position: absolute; right: 0; top: 0; height: 100%; opacity: 0.12; z-index: 0; transition: width 0.4s ease-out; }
.m-ask-row .depth-bar { background: #f6465d; }
.m-bid-row .depth-bar { background: #0ecb81; }
.m-ask-row span, .m-bid-row span { position: relative; z-index: 1; font-family: 'Roboto Mono', monospace; }

/* Trades 成交 */
.m-trades-section {
  flex-shrink: 0;
  padding: 12px;
}
.m-trades-list { display: flex; flex-direction: column; }

/* 交易模块（买卖按钮，在 Tab 下方、订单上方） */
.m-ex-actions-inline {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #0f172a;
  border-top: 1px solid #1e293b;
  flex-shrink: 0;
}
.m-ex-actions-inline .m-btn { flex: 1; }
.m-trade-row { 
  display: grid; 
  grid-template-columns: 1fr 1fr 1fr; 
  font-size: 13px; 
  padding: 10px 0; 
  border-bottom: 1px solid #1e293b; 
  align-items: center;
}
.m-trade-header { color: #64748b; font-size: 12px; font-weight: 600; padding: 6px 0; }
.m-trade-row .time { color: #64748b; }
.m-trade-row.up .price { color: #0ecb81; font-weight: 600; }
.m-trade-row.down .price { color: #f6465d; font-weight: 600; }
.m-trade-row .num { text-align: right; color: #94a3b8; font-family: 'Roboto Mono', monospace; }

/* 订单模块：持仓 / 当前委托 */
.m-history-section { 
  background: #0f172a; 
  padding-bottom: 24px; 
  border-top: 8px solid #172636;
  flex-shrink: 0;
}
.m-history-tabs { 
  display: flex; 
  padding: 12px 16px; 
  gap: 24px; 
  border-bottom: 1px solid #1e293b;
}
.m-history-tabs .m-btn.tab { font-size: 15px; }

.m-history-list { padding: 16px; }
.m-pos-card,
.m-order-card { 
  background: #1e293b; 
  border-radius: 12px; 
  padding: 16px; 
  margin-bottom: 12px;
  border: 1px solid #334155;
}
.m-card-top { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.m-card-symbol { font-weight: 700; font-size: 16px; color: #fff; }
.m-card-tag { font-size: 11px; padding: 2px 6px; border-radius: 4px; font-weight: 700; }
.m-card-tag.buy { background: rgba(14, 203, 129, 0.2); color: #0ecb81; }
.m-card-tag.sell { background: rgba(246, 70, 93, 0.2); color: #f6465d; }
.m-card-close { margin-left: auto; padding: 6px 12px; font-size: 12px; }
.m-card-body { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
.m-grid-item { display: flex; flex-direction: column; gap: 6px; }
.m-grid-item .label { font-size: 11px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; }
.m-grid-item .val { font-size: 14px; font-weight: 600; font-family: 'Roboto Mono', monospace; }
.m-grid-item .val.up { color: #0ecb81; }
.m-grid-item .val.down { color: #f6465d; }
.m-nodata-small { text-align: center; color: #475569; padding: 40px 0; font-size: 14px; }

.m-wallet-bar { font-size: 13px; color: #94a3b8; margin-bottom: 20px; font-weight: 500; }
.m-type-switch { display: flex; background: #0f172a; border-radius: 8px; padding: 4px; margin-bottom: 20px; gap: 4px; }
.m-type-switch .m-btn { flex: 1; }

.m-trade-error { color: #f6465d; font-size: 13px; margin: 0 0 12px; padding: 8px 12px; background: rgba(246, 70, 93, 0.1); border-radius: 8px; }
.m-form-field { margin-bottom: 16px; }
.m-quick-pct, .m-lev-row { display: flex; gap: 8px; margin-bottom: 20px; }
.m-final-action { margin-top: 10px; }

/* Symbol Selection Sidebar */
.m-symbol-list-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.7);
  z-index: 1100;
  display: flex;
  justify-content: flex-start;
  animation: fadeIn 0.2s ease-out;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
.m-modal-inner {
  width: 85%;
  max-width: 340px;
  height: 100%;
  background: #0f172a;
  padding-top: env(safe-area-inset-top);
  display: flex;
  flex-direction: column;
  animation: slideInLeft 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes slideInLeft { from { transform: translateX(-100%); } to { transform: translateX(0); } }
.m-modal-header { padding: 24px 20px; border-bottom: 1px solid #1e293b; }
.m-modal-header h3 { margin: 0 0 16px 0; font-size: 22px; font-weight: 800; }
.m-modal-header input { 
  width: 100%; 
  height: 44px; 
  background: #1e293b; 
  border: none; 
  border-radius: 22px; 
  padding: 0 20px; 
  color: #fff; 
  box-sizing: border-box; 
  font-size: 15px;
  border: 1px solid #334155;
}
.m-modal-list { flex: 1; overflow-y: auto; padding: 10px 0; }
.m-modal-item { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  padding: 16px 20px; 
  border-bottom: 1px solid rgba(255,255,255,0.03); 
  transition: background 0.2s;
}
.m-modal-item:active { background: #1e293b; }
.m-modal-item .s-name { font-weight: 700; font-size: 16px; }
.m-modal-item .s-right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.m-modal-item .s-price { font-size: 15px; font-family: 'Roboto Mono', monospace; font-weight: 600; }
.m-modal-item .s-chg { font-size: 12px; font-weight: 700; padding: 2px 6px; border-radius: 4px; }
.m-modal-item .s-chg.up { color: #0ecb81; background: rgba(14, 203, 129, 0.1); }
.m-modal-item .s-chg.down { color: #f6465d; background: rgba(246, 70, 93, 0.1); }
</style>
