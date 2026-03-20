<script setup>
import { ref, shallowRef, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { createChart, CandlestickSeries, AreaSeries } from 'lightweight-charts'
import { useDevice } from '../hooks/useDevice'
import MobileExchangeView from '../components/mobile/MobileExchangeView.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const app = useAppStore()
const { isMobile } = useDevice()

// Mobile-specific UI state handled by MobileExchangeView component
import {
  getMarketSymbolThumb,
  getMarketSymbolThumbOne,
  getMarketSymbolInfo,
  getMarketKline,
  getMarketPlate,
  getMarketLatestTrade,
  getWalletList,
  postFuturesOrderAdd,
  postFuturesOrderCancel,
  getFuturesOrderCurrent,
  getFuturesOrderHistory,
  getFuturesPositionCurrent,
  postFuturesPositionClose,
  getOrderHistory,
  getOrderTradeHistory,
} from '../api'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { message } from '../components/toast'



const thumbs = ref([])
const currentSymbol = ref('BTC/USDT')
/** 交易对切换中，用于显示骨架屏 */
const switchingSymbol = ref(false)
/** 切换时全部加载失败，显示错误占位 */
const switchLoadFailed = ref(false)
/** 弱网（3g/2g/slow-2g）：降低请求频率与深度档位 */
const isWeakNetwork = ref(false)

function normalizeSymbol(pair) {
  if (!pair || typeof pair !== 'string') return 'BTC/USDT'
  const p = pair.toString().replace(/-/g, '/').trim()
  if (!p) return 'BTC/USDT'
  const parts = p.split('/')
  if (parts.length >= 2) {
    return (parts[0].toUpperCase() + '/' + parts[1].toUpperCase())
  }
  return p.toUpperCase().includes('USDT') ? p.toUpperCase() : 'BTC/USDT'
}

// Initialize symbol from route (normalize to BASE/QUOTE so thumbMap lookup matches backend)
if (route.params.pair) {
  currentSymbol.value = normalizeSymbol(route.params.pair)
}
const symbolInfo = ref(null)
const loading = ref(true)
const orderTab = ref('position') // 'position' | 'current' | 'history'
const longPrice = ref('')
const longAmount = ref('')
const shortPrice = ref('')
const shortAmount = ref('')
const leverage = ref(20)
const limitType = ref(true)
const orderType = computed({
  get: () => limitType.value ? 'LIMIT' : 'MARKET',
  set: (val) => { limitType.value = (val === 'LIMIT') }
})
const symbolSearch = ref('')
const filteredSymbols = computed(() => {
  if (!symbolSearch.value) return thumbs.value
  const search = symbolSearch.value.toLowerCase()
  return thumbs.value.filter(s => s.symbol.toLowerCase().includes(search))
})
const marginMode = ref('cross') // 'cross' | 'isolated'
const tpPrice = ref('')
const slPrice = ref('')
const showTpSl = ref(false)
const mobileExRef = ref(null)

const currentPositions = ref([])
const currentOrders = ref([])
const historyOrders = ref([])
const historyPage = ref({ content: [], totalElements: 0, totalPages: 0 })

const orderLoading = ref(false)
const submitLoading = ref(false)
const errorMsg = ref('')
/** 盘口点击填入价格时，输入框高亮动画 */
const priceInputHighlight = ref(false)
let highlightTimer = null
/** 虚拟盘下单确认：替代原生 confirm 的统一模态框 */
const showVirtualConfirmModal = ref(false)
const pendingOrderDirection = ref('') // 'long' | 'short'

const chartContainer = ref(null)
const depthContainer = ref(null)
let chartInstance = null
let depthInstance = null
const chartInterval = ref('5m')
const chartMode = ref('k') // 'k' | 'depth'
const chartIntervalOptions = computed(() => [
  { value: '1m', label: `1${t('exchange.min')}` },
  { value: '5m', label: `5${t('exchange.min')}` },
  { value: '15m', label: `15${t('exchange.min')}` },
  { value: '1h', label: `1${t('exchange.hour')}` },
])
const plate = shallowRef({ ask: [], bid: [] })
const latestTrades = shallowRef([])
/** 当前交易对从 Binance ticker WebSocket 的实时价格/涨跌，毫秒级 */
const liveTicker = ref(null)

const walletList = ref([])

const isLogin = computed(() => app.isLogin)

const thumbMap = computed(() => {
  const m = {}
  thumbs.value.forEach((t) => { m[t.symbol] = t })
  return m
})

const currentThumb = computed(() => thumbMap.value[currentSymbol.value] || {})
/** 当前交易对是否为虚拟盘（自设价格，行情由后端生成） */
const isVirtualSymbol = computed(() => !!currentThumb.value?.virtual)
/** 展示用：优先 liveTicker（实时），否则 currentThumb */
const currentThumbDisplay = computed(() => {
  const base = currentThumb.value
  const live = liveTicker.value
  if (!live) return base
  return {
    ...base,
    close: live.close,
    chg: live.chg,
    volume: live.volume,
    high: live.high,
    low: live.low,
    open: live.open,
  }
})

/** 连接状态文案（用户可理解的行动导向描述） */
const statusText = computed(() => {
  if (connecting.value) return '正在连接行情服务器…'
  if (liveTicker.value) return '价格实时更新中'
  return '行情连接断开，正在自动重试'
})

// Futures margin is always USDT
const walletBase = computed(() => {
  const unit = symbolInfo.value?.baseSymbol || 'USDT'
  const w = walletList.value.find((x) => (x.unit || '').toUpperCase() === unit.toUpperCase())
  return w ? Number(w.balance) - Number(w.frozenBalance || 0) : 0
})
const walletCoin = computed(() => {
  const unit = symbolInfo.value?.coinSymbol || 'BTC'
  const w = walletList.value.find((x) => (x.unit || '').toUpperCase() === unit.toUpperCase())
  return w ? Number(w.balance) - Number(w.frozenBalance || 0) : 0
})

// Order info computed
const marketPrice = computed(() => Number(currentThumbDisplay.value?.close || 0))
const marketPriceReady = computed(() => marketPrice.value > 0)
const marketPriceWaiting = computed(() => !limitType.value && !marketPriceReady.value)
const orderPrice = computed(() => {
  if (!limitType.value) return marketPrice.value
  return longPrice.value ? Number(longPrice.value) : 0
})
const orderValue = computed(() => {
  const amt = longAmount.value ? Number(longAmount.value) : 0
  return amt * orderPrice.value
})
const requiredMargin = computed(() => {
  if (!leverage.value || leverage.value <= 0) return 0
  return orderValue.value / Number(leverage.value)
})

// Account overview computed
const totalMarginUsed = computed(() => {
  return currentPositions.value.reduce((sum, p) => sum + Number(p.margin || 0), 0)
})
const totalUnrealizedPnl = computed(() => {
  return currentPositions.value.reduce((sum, p) => sum + calcPnl(p), 0)
})

function setQuickAmount(pct) {
  if (!orderPrice.value || orderPrice.value <= 0) return
  const available = walletBase.value
  const maxMargin = available * (pct / 100)
  const maxValue = maxMargin * Number(leverage.value)
  const maxAmount = maxValue / orderPrice.value
  longAmount.value = maxAmount > 0 ? maxAmount.toFixed(6) : ''
}

function setQuickLeverage(lev) {
  leverage.value = lev
}

function switchSymbol(symbol) {
  currentSymbol.value = symbol
  router.push({ params: { pair: symbol.replace('/', '-') } })
}

function setPeriod(p) {
  chartInterval.value = p
}

// Refresh logic for mobile HMR
async function loadThumbs() {
  try {
    const data = await getMarketSymbolThumb()
    thumbs.value = Array.isArray(data) ? data : []
    if (thumbs.value.length && !currentSymbol.value) currentSymbol.value = thumbs.value[0].symbol
    // 若当前为虚拟盘且尚未启动虚拟轮询，则关闭实盘 WS 并改为轮询后端
    const thumb = thumbs.value.find((t) => t.symbol === currentSymbol.value)
    if (thumb?.virtual && !virtualPollingTimerId) {
      closeRealtimeStreams()
      startVirtualPolling()
    }
  } catch (e) {
    thumbs.value = []
    if (e?.message) errorMsg.value = e.message
  }
}

async function loadSymbolInfo() {
  if (!currentSymbol.value) return
  try {
    symbolInfo.value = await getMarketSymbolInfo(currentSymbol.value)
  } catch (e) {
    symbolInfo.value = null
    console.warn('loadSymbolInfo failed:', currentSymbol.value, e instanceof Error ? e.message : e)
  }
}

/** symbolInfo 多次重试仍失败时，用户可手动重试（区分临时网络与真正下架） */
const symbolRetryLoading = ref(false)
async function retryAfterSymbolLoadFailure() {
  if (symbolRetryLoading.value || !currentSymbol.value) return
  symbolRetryLoading.value = true
  switchLoadFailed.value = false
  symbolInfoWaitRetryCount = 0
  switchingSymbol.value = true
  try {
    await loadSymbolInfo()
    if (!symbolInfo.value) {
      switchingSymbol.value = false
      switchLoadFailed.value = true
      message.warning('加载失败，请检查网络后重试')
      return
    }
    const results = await Promise.allSettled([loadChart(), loadPlate(), loadLatestTrade()])
    switchingSymbol.value = false
    const allRejected = results.every((r) => r.status === 'rejected')
    if (allRejected) switchLoadFailed.value = true
    else {
      connectRealtimeStreams()
      if (chartMode.value === 'depth') nextTick().then(() => drawDepthChart())
    }
  } finally {
    symbolRetryLoading.value = false
  }
}

let lastErrorToast = { msg: '', time: 0 }
const ERROR_TOAST_DEBOUNCE_MS = 10000
/** 持续故障时状态栏提示，避免 10s 内重复 toast 造成“静默死亡” */
const statusBarErrors = ref({ plate: '', trade: '', chart: '' })

function toastError(msg, key) {
  if (!msg) return
  const now = Date.now()
  const isRepeat = now - lastErrorToast.time < ERROR_TOAST_DEBOUNCE_MS && lastErrorToast.msg === msg
  if (key) statusBarErrors.value = { ...statusBarErrors.value, [key]: msg }
  if (!isRepeat) {
    lastErrorToast.msg = msg
    lastErrorToast.time = now
    message.error(msg)
  }
}

const statusBarErrorText = computed(() => {
  const e = statusBarErrors.value
  if (e.plate) return e.plate
  if (e.trade) return e.trade
  if (e.chart) return e.chart
  return ''
})

const plateLimit = computed(() => (isWeakNetwork.value ? 5 : 20))

async function loadPlate() {
  if (!currentSymbol.value) return
  try {
    const data = await getMarketPlate(currentSymbol.value, plateLimit.value)
    plate.value = {
      ask: Array.isArray(data?.ask) ? data.ask : [],
      bid: Array.isArray(data?.bid) ? data.bid : [],
    }
    statusBarErrors.value = { ...statusBarErrors.value, plate: '' }
    if (chartMode.value === 'depth') nextTick().then(() => drawDepthChart())
  } catch (_) {
    plate.value = { ask: [], bid: [] }
    const msg = t('exchange.errPlateLoad') || '盘口加载失败'
    toastError(msg, 'plate')
  }
}

async function loadLatestTrade() {
  if (!currentSymbol.value) return
  try {
    const data = await getMarketLatestTrade(currentSymbol.value, 30)
    latestTrades.value = Array.isArray(data) ? data : []
    statusBarErrors.value = { ...statusBarErrors.value, trade: '' }
  } catch (_) {
    latestTrades.value = []
    const msg = t('exchange.errTradeLoad') || '成交记录加载失败'
    toastError(msg, 'trade')
  }
}

async function loadWallet() {
  if (!isLogin.value) return
  try {
    walletList.value = await getWalletList()
  } catch (_) {
    walletList.value = []
  }
}

async function loadCurrentPositions() {
  if (!isLogin.value) return
  try {
    currentPositions.value = await getFuturesPositionCurrent()
  } catch (_) {
    currentPositions.value = []
  }
}

async function loadCurrentOrders() {
  if (!isLogin.value) return
  orderLoading.value = true
  try {
    currentOrders.value = await getFuturesOrderCurrent()
  } catch (_) {
    currentOrders.value = []
  } finally {
    orderLoading.value = false
  }
}

async function loadHistoryOrders() {
  if (!isLogin.value) return
  orderLoading.value = true
  try {
    historyPage.value = await getOrderTradeHistory(1, 20)
    historyOrders.value = historyPage.value?.content || []
    
    // Check for recent fills and push them into latestTrades briefly with a highlight
    if (historyOrders.value.length > 0) {
      const fiveMinsAgo = Date.now() - 5 * 60 * 1000
      historyOrders.value.forEach(ho => {
        if (ho.symbol === currentSymbol.value && new Date(ho.createTime).getTime() > fiveMinsAgo) {
          const exists = latestTrades.value.find(lt => lt.time === ho.createTime && lt.price === ho.price)
          if (!exists) {
            const userTrade = {
              price: Number(ho.price),
              amount: Number(ho.tradedAmount || ho.amount),
              direction: ho.direction,
              time: new Date(ho.createTime).getTime(),
              isUserTrade: true
            }
            latestTrades.value = [userTrade, ...latestTrades.value].slice(0, MAX_LATEST_TRADES)
          }
        }
      })
    }
  } catch (_) {
    historyOrders.value = []
  } finally {
    orderLoading.value = false
  }
}

async function doLong(skipConfirm = false) {
  errorMsg.value = ''
  if (isVirtualSymbol.value && !skipConfirm) {
    pendingOrderDirection.value = 'long'
    showVirtualConfirmModal.value = true
    return
  }
  const price = limitType.value ? (longPrice.value ? Number(longPrice.value) : 0) : Number(currentThumbDisplay.value.close || 0)
  const amount = longAmount.value ? Number(longAmount.value) : 0
  if (!amount || amount <= 0) {
    errorMsg.value = t('exchange.errEnterAmount')
    return
  }
  if (!price || price <= 0) {
    if (!limitType.value) {
      errorMsg.value = '等待行情更新'
    } else {
      errorMsg.value = t('exchange.errInvalidPrice')
    }
    return
  }
  submitLoading.value = true
  try {
    await postFuturesOrderAdd({
      symbol: currentSymbol.value,
      direction: 'LONG',
      type: limitType.value ? 'LIMIT' : 'MARKET',
      price: price,
      amount,
      leverage: Number(leverage.value)
    })
    message.success(t('exchange.orderSubmitted') || '订单已提交')
    longPrice.value = ''
    longAmount.value = ''
    await loadWallet()
    await loadCurrentOrders()
    orderTab.value = 'current'
    if (!limitType.value) {
      const userTrade = { price: Number(price), amount: Number(amount), direction: 'LONG', time: Date.now(), isUserTrade: true }
      latestTrades.value = [userTrade, ...latestTrades.value].slice(0, 50)
    }
  } catch (e) {
    errorMsg.value = e.message || t('exchange.errOrderFailed')
  } finally {
    submitLoading.value = false
  }
}

async function doShort(skipConfirm = false) {
  errorMsg.value = ''
  if (isVirtualSymbol.value && !skipConfirm) {
    pendingOrderDirection.value = 'short'
    showVirtualConfirmModal.value = true
    return
  }
  const price = limitType.value ? (longPrice.value ? Number(longPrice.value) : 0) : Number(currentThumbDisplay.value.close || 0)
  const amount = longAmount.value ? Number(longAmount.value) : 0
  if (!amount || amount <= 0) {
    errorMsg.value = t('exchange.errEnterAmount')
    return
  }
  if (!price || price <= 0) {
    if (!limitType.value) {
      errorMsg.value = '等待行情更新'
    } else {
      errorMsg.value = t('exchange.errInvalidPrice')
    }
    return
  }
  submitLoading.value = true
  try {
    await postFuturesOrderAdd({
      symbol: currentSymbol.value,
      direction: 'SHORT',
      type: limitType.value ? 'LIMIT' : 'MARKET',
      price: price,
      amount,
      leverage: Number(leverage.value)
    })
    message.success(t('exchange.orderSubmitted') || '订单已提交')
    longPrice.value = ''
    longAmount.value = ''
    await loadWallet()
    await loadCurrentOrders()
    orderTab.value = 'current'
    if (!limitType.value) {
      const userTrade = { price: Number(price), amount: Number(amount), direction: 'SHORT', time: Date.now(), isUserTrade: true }
      latestTrades.value = [userTrade, ...latestTrades.value].slice(0, 50)
    }
  } catch (e) {
    errorMsg.value = e.message || t('exchange.errOrderFailed')
  } finally {
    submitLoading.value = false
  }
}

function closeVirtualConfirmModal() {
  showVirtualConfirmModal.value = false
  pendingOrderDirection.value = ''
}

function confirmVirtualOrder() {
  const dir = pendingOrderDirection.value
  showVirtualConfirmModal.value = false
  pendingOrderDirection.value = ''
  if (dir === 'long') doLong(true)
  else if (dir === 'short') doShort(true)
}

async function cancelOrder(orderId) {
  try {
    await postFuturesOrderCancel(orderId)
    await loadCurrentOrders()
    await loadWallet()
  } catch (e) {
    errorMsg.value = e.message || t('exchange.errCancelFailed')
  }
}

async function closePosition(positionId) {
  try {
    const p = currentPositions.value.find(x => x.id === positionId)
    await postFuturesPositionClose(positionId)
    if (p) {
      const pPrice = currentThumbDisplay.value?.close || p.avgPrice
      const userTrade = { price: Number(pPrice), amount: Number(p.volume), direction: p.direction === 'LONG' ? 'SELL' : 'BUY', time: Date.now(), isUserTrade: true }
      latestTrades.value = [userTrade, ...latestTrades.value].slice(0, 50)
    }
    await loadCurrentPositions()
    await loadWallet()
  } catch(e) {
    errorMsg.value = e.message || t('exchange.errCloseFailed')
  }
}

function formatTime(strOrNum) {
  if (strOrNum == null) return '-'
  try {
    const d = typeof strOrNum === 'number' ? new Date(strOrNum) : new Date(strOrNum)
    if (Number.isNaN(d.getTime())) return '-'
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch (_) {
    return String(strOrNum)
  }
}

function formatTimeSplit(strOrNum) {
  if (strOrNum == null) return { date: '-', time: '' }
  try {
    const d = typeof strOrNum === 'number' ? new Date(strOrNum) : new Date(strOrNum)
    if (Number.isNaN(d.getTime())) return { date: '-', time: '' }
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mi = String(d.getMinutes()).padStart(2, '0')
    const ss = String(d.getSeconds()).padStart(2, '0')
    return { date: `${mm}/${dd}`, time: `${hh}:${mi}:${ss}` }
  } catch (_) {
    return { date: String(strOrNum), time: '' }
  }
}

function formatNum(n, digits = 4) {
  if (n == null) return '0'
  const x = Number(n)
  return Number.isFinite(x) ? x.toFixed(digits) : '0'
}

function tradeStatusText(status) {
  const map = {
    TRADING: t('exchange.statusTrading'),
    COMPLETED: t('exchange.statusCompleted'),
    CANCELLED: t('exchange.statusCancelled'),
    PARTIAL: t('exchange.statusPartial'),
  }
  return map[status] || status || '-'
}

function calcPnl(pos) {
  const currentPrice = currentThumbDisplay.value?.close || pos.avgPrice
  const dir = pos.direction
  const diff = dir === 'LONG' ? (currentPrice - pos.avgPrice) : (pos.avgPrice - currentPrice)
  return diff * pos.volume
}

function calcRoe(pos) {
  const pnl = calcPnl(pos)
  if (!pos.margin || pos.margin === 0) return 0
  return (pnl / pos.margin) * 100
}

// ===== Real-time chart（实盘用后端 Kraken 数据，虚拟盘用 WebSocket） =====
let candleSeries = null
let klineWs = null
let klineData = []  // cached kline array
let isLoadingMore = false

const WS_DELAY = 0

let chartLoadRunId = 0
let chartRetryTimeoutId = null
let chartRecreating = false
let windowErrorHandler = null

function binanceSymbol() {
  return (currentSymbol.value || 'BTC/USDT').replace('/', '').toUpperCase()
}

function binanceInterval() {
  return chartInterval.value || '1h'
}

/** 周期对应的毫秒数（用于本地换线） */
function getBarMs(interval) {
  const map = { '1m': 60000, '5m': 300000, '15m': 900000, '1h': 3600000 }
  return map[interval] || 3600000
}

/**
 * 用本地时钟算当前 K 线 bar 的起始时间（与 toLocalTs 一致的 chart 时间，秒）。
 * 换线不依赖服务器：例如 1m 线在本地时间走到 XX:00 秒时立刻开启下一根。
 */
function getCurrentBarStartUtcSeconds(interval) {
  const barMs = getBarMs(interval)
  const utcBarStartMs = Math.floor(Date.now() / barMs) * barMs
  return toUtcSeconds(utcBarStartMs)
}

/** 纯 UTC 秒，供 lightweight-charts 使用；库会按浏览器时区显示本地时间 */
function toUtcSeconds(utcMs) {
  return Math.floor(utcMs / 1000)
}

async function loadChart(retryAttempt = 0, runId = null) {
  if (runId == null) {
    if (chartRetryTimeoutId) {
      clearTimeout(chartRetryTimeoutId)
      chartRetryTimeoutId = null
    }
    runId = ++chartLoadRunId
    retryAttempt = 0
  }
  // 如果当前处于 depth 模式，图表容器由 v-show 隐藏；此时不要触发 lightweight-charts 初始化
  if (chartMode.value !== 'k') return
  chartRecreating = true
  try {
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 200))
    // 防止旧请求在 await 后继续执行（尤其是 chart 已被 remove 替换时）
    if (runId !== chartLoadRunId) return
    if (chartMode.value !== 'k') return
    const container = isMobile.value ? mobileExRef.value?.mobileChartContainer : chartContainer.value
    if (!container || !currentSymbol.value) return
    const w = container.clientWidth
    const h = container.clientHeight
    if (w <= 0 || h <= 0) {
      const chartWrapEl = container?.closest ? container.closest('.chart-wrap') : null
      const exchangeCenterEl = chartWrapEl?.closest ? chartWrapEl.closest('.exchange-center') : null
      const exchangeLayoutEl = exchangeCenterEl?.closest ? exchangeCenterEl.closest('.exchange-layout') : null
      const exchangePageEl = exchangeLayoutEl?.closest ? exchangeLayoutEl.closest('.exchange-page') : null
      const platePanelEl = exchangeLayoutEl?.querySelector ? exchangeLayoutEl.querySelector('.plate-panel') : null
      const chartWrapRect = chartWrapEl?.getBoundingClientRect?.()
      const exchangeCenterRect = exchangeCenterEl?.getBoundingClientRect?.()
      const exchangeLayoutRect = exchangeLayoutEl?.getBoundingClientRect?.()
      const exchangePageRect = exchangePageEl?.getBoundingClientRect?.()
      const platePanelRect = platePanelEl?.getBoundingClientRect?.()
      console.warn('Chart container has no dimensions:', w, h, {
        chartWrapHeight: chartWrapRect?.height,
        exchangeCenterHeight: exchangeCenterRect?.height,
        exchangeLayoutHeight: exchangeLayoutRect?.height,
        exchangePageHeight: exchangePageRect?.height,
        platePanelHeight: platePanelRect?.height,
        chartWrapStyle: chartWrapEl ? {
          flex: getComputedStyle(chartWrapEl).flex,
          minHeight: getComputedStyle(chartWrapEl).minHeight,
          overflow: getComputedStyle(chartWrapEl).overflow,
        } : null,
        exchangeCenterStyle: exchangeCenterEl ? {
          flex: getComputedStyle(exchangeCenterEl).flex,
          minHeight: getComputedStyle(exchangeCenterEl).minHeight,
          overflow: getComputedStyle(exchangeCenterEl).overflow,
          display: getComputedStyle(exchangeCenterEl).display,
        } : null,
        exchangeLayoutStyle: exchangeLayoutEl ? {
          flex: getComputedStyle(exchangeLayoutEl).flex,
          minHeight: getComputedStyle(exchangeLayoutEl).minHeight,
          overflow: getComputedStyle(exchangeLayoutEl).overflow,
          display: getComputedStyle(exchangeLayoutEl).display,
        } : null,
      })
      if (retryAttempt < 6 && runId === chartLoadRunId) {
        // 容器高度/布局尚未就绪时重试（避免首次渲染时 height=0）
        if (chartRetryTimeoutId) clearTimeout(chartRetryTimeoutId)
        chartRetryTimeoutId = setTimeout(() => {
          if (runId !== chartLoadRunId) return
          loadChart(retryAttempt + 1, runId).catch(() => {})
        }, 250)
      }
      return
    }
    // 行情图仅根据抓取到的价格数据绘制：初始来自接口 K 线，实时由 K 线 WS + ticker 更新
    const raw = await getMarketKline(currentSymbol.value, chartInterval.value, 500)
    const list = Array.isArray(raw) ? raw : []
    klineData = list.map((b) => ({
      time: toUtcSeconds(Number(b.time) || 0),
      open: Number(b.open) || 0,
      high: Number(b.high) || 0,
      low: Number(b.low) || 0,
      close: Number(b.close) || 0,
    })).filter((d) => d.time > 0)
    // De-duplicate and sort
    const seen = new Set()
    klineData = klineData.filter(d => { if (seen.has(d.time)) return false; seen.add(d.time); return true })
    klineData.sort((a, b) => a.time - b.time)

    if (chartInstance) {
      chartInstance.remove()
      chartInstance = null
      candleSeries = null
    }

    chartInstance = createChart(container, {
      layout: { background: { color: '#172636' }, textColor: '#8b9bb4', attributionLogo: false },
      grid: {
        vertLines: { color: 'rgba(42, 45, 54, 0.6)', style: 1, visible: true },
        horzLines: { color: 'rgba(42, 45, 54, 0.6)', style: 1, visible: true },
      },
      width: w,
      height: h,
      autoSize: true,
      crosshair: {
        mode: 1,
        vertLine: {
          width: 1,
          color: 'rgba(224, 227, 235, 0.1)',
          style: 0,
        },
        horzLine: {
          width: 1,
          color: 'rgba(224, 227, 235, 0.1)',
          style: 0,
        },
      },
      rightPriceScale: { borderColor: 'transparent', scaleMargins: { top: 0.1, bottom: 0.15 } },
      localization: { locale: app.lang || 'en' },
      timeScale: {
        borderColor: 'transparent',
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 5,
        shiftVisibleRangeOnNewBar: true,
      },
    })
    candleSeries = chartInstance.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderDownColor: '#ef5350',
      borderUpColor: '#26a69a',
      wickDownColor: '#ef5350',
      wickUpColor: '#26a69a',
    })
    candleSeries.setData(klineData)
    chartInstance.timeScale().scrollToRealTime()
    statusBarErrors.value = { ...statusBarErrors.value, chart: '' }

    // Subscribe to visible range change → load more data on zoom-out
    chartInstance.timeScale().subscribeVisibleLogicalRangeChange((logicalRange) => {
      if (!logicalRange || isLoadingMore) return
      if (logicalRange.from < 5) {
        loadMoreHistory()
      }
    })

    // 实盘：REST 历史 + ticker lastPrice 实时合成，按本地时钟换线（不连 Binance K 线 WS）
    // 虚拟盘：后端 K 线 + 轮询价格更新
  } catch (e) {
    if (chartInstance) { chartInstance.remove(); chartInstance = null; candleSeries = null }
    const msg = t('exchange.errChartLoad') || 'K线加载失败'
    toastError(msg, 'chart')
  } finally {
    chartRecreating = false
  }
}

async function loadMoreHistory() {
  if (isLoadingMore || !candleSeries || klineData.length === 0) return
  isLoadingMore = true
  try {
    // Chart 使用纯 UTC 秒，API 需要毫秒
    const oldestUtcMs = klineData[0].time * 1000
    const raw = await getMarketKline(currentSymbol.value, chartInterval.value, 500, oldestUtcMs)
    const moreList = Array.isArray(raw) ? raw : []
    if (moreList.length === 0) { isLoadingMore = false; return }
    const newData = moreList.map((b) => ({
      time: toUtcSeconds(Number(b.time)),
      open: Number(b.open),
      high: Number(b.high),
      low: Number(b.low),
      close: Number(b.close),
    })).filter(d => d.time < klineData[0].time)
    if (newData.length > 0) {
      klineData = [...newData, ...klineData]
      const seen = new Set()
      klineData = klineData.filter(d => { if (seen.has(d.time)) return false; seen.add(d.time); return true })
      klineData.sort((a, b) => a.time - b.time)
      candleSeries.setData(klineData)
    }
  } catch (_) {}
  isLoadingMore = false
}

function connectKlineWs() {
  closeKlineWs()
  // 已切换为后端数据源（实盘 Kraken / 虚拟盘引擎），不再使用 Binance WebSocket
}

function closeKlineWs() {
  if (klineWs) {
    safeCloseWs(klineWs)
    klineWs = null
  }
}

// ===== 实盘 K 线：毫秒级实时 LastPrice 直接驱动（不通过 setInterval 轮询） =====
function syncChartWithPrice(price) {
  if (chartMode.value !== 'k' || chartRecreating || !candleSeries || klineData.length === 0) return
  const last = klineData[klineData.length - 1]
  if (isNaN(price) || price <= 0) return

  if (last && last.close > 0) {
    const dropRatio = Math.abs(price - last.close) / last.close
    if (dropRatio > 0.5) return
  }
  const interval = chartInterval.value || '1h'
  const currentBarStart = getCurrentBarStartUtcSeconds(interval)

  if (last && last.time === currentBarStart) {
    const updated = {
      time: last.time,
      open: last.open,
      high: Math.max(last.high, price),
      low: Math.min(last.low, price),
      close: price,
    }
    try {
      candleSeries.update(updated)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e || '')
      if (msg.includes('disposed')) {
        // chart 已被 remove/dispose，停止更新避免重复抛错
        candleSeries = null
        chartInstance = null
      }
      return
    }
    klineData[klineData.length - 1] = updated
  } else if (last && last.time > currentBarStart) {
    const updated = {
      time: last.time,
      open: last.open,
      high: Math.max(last.high, price),
      low: Math.min(last.low, price),
      close: price,
    }
    try {
      candleSeries.update(updated)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e || '')
      if (msg.includes('disposed')) {
        candleSeries = null
        chartInstance = null
      }
      return
    }
    klineData[klineData.length - 1] = updated
  } else if (!last || currentBarStart > last.time) {
    const open = last ? last.close : price
    const newBar = {
      time: currentBarStart,
      open,
      high: price,
      low: price,
      close: price,
    }
    klineData.push(newBar)
    try {
      candleSeries.update(newBar)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e || '')
      if (msg.includes('disposed')) {
        candleSeries = null
        chartInstance = null
      }
      return
    }
  }
}

// ===== 实盘：后端 Kraken WebSocket 推送 /topic/market-thumb（已弃用 2s 轮询） =====
let tickerWs = null
let depthWs = null
let aggTradeWs = null
let realMarketPollingTimer = null
let realMarketStompClient = null
const MAX_LATEST_TRADES = 50
const marketMaintenance = ref(false)

let lastTickerTime = 0
let tickerWatchdog = null
let fastPollingTimer = null
// 实盘：毫秒级刷新单条 thumb（用于详情页高频真实价格）
let realMarketFastThumbPollingTimer = null
let realMarketFastThumbPollingInFlight = false
let fastThumbPollingEnabled = false
let realMarketWsConnected = false
let realMarketConnToken = 0
// 实盘：若 WS 已连上但长时间收不到推送，启用 REST 降级轮询
let realMarketNoMsgWatchdogTimer = null
let realMarketNoMsgWatchdogInFlight = false
const REAL_MARKET_NO_MSG_TIMEOUT_MS = 3500

// 实盘：WS 断线时，Depth 模式需要额外轮询 plate（否则柱状图/Depth 会静止）
let realMarketPlatePollingTimer = null
let realMarketPlatePollingInFlight = false
let lastPlateSig = ''

// UI 更新：requestAnimationFrame 驱动，仅在数据变化时更新 DOM
let pendingPlate = null
let pendingTrades = []
let pendingPlateRaf = false
let pendingTradeRaf = false
let lastRealTradeTime = Date.now()
let ghostTradeTimer = null

function schedulePlateUpdate() {
  if (pendingPlateRaf) return
  pendingPlateRaf = true
  requestAnimationFrame(() => {
    if (pendingPlate) {
      const msg = pendingPlate
      pendingPlate = null
      const a = Array.isArray(msg.a) ? msg.a : []
      const b = Array.isArray(msg.b) ? msg.b : []
      plate.value = {
        ask: a.map((x) => ({ price: parseFloat(x[0]) || 0, amount: parseFloat(x[1]) || 0 })),
        bid: b.map((x) => ({ price: parseFloat(x[0]) || 0, amount: parseFloat(x[1]) || 0 })),
      }
      const myOrders = currentOrders.value.filter(o => o.symbol === currentSymbol.value && o.price > 0 && o.amount > 0)
      if (myOrders.length > 0) {
        myOrders.forEach(o => {
          const targetArr = o.direction === 'BUY' || o.direction === 'LONG' ? plate.value.bid : plate.value.ask
          const exists = targetArr.find(v => v.price === Number(o.price))
          if (exists) {
            exists.amount += Number(o.amount)
            exists.isMine = true
          } else {
            targetArr.push({ price: Number(o.price), amount: Number(o.amount), isMine: true })
          }
        })
        plate.value.ask.sort((a, b) => a.price - b.price)
        plate.value.bid.sort((a, b) => b.price - a.price)
      }
      if (chartMode.value === 'depth' && depthInstance) nextTick().then(() => drawDepthChart())
    }
    pendingPlateRaf = false
  })
}

function scheduleTradeUpdate() {
  if (pendingTradeRaf) return
  pendingTradeRaf = true
  requestAnimationFrame(() => {
    if (pendingTrades.length > 0) {
      lastRealTradeTime = Date.now()
      const newItems = pendingTrades.sort((a, b) => b.time - a.time)
      pendingTrades = []
      latestTrades.value = [...newItems, ...latestTrades.value].slice(0, MAX_LATEST_TRADES)
    }
    pendingTradeRaf = false
  })
}

function startUiUpdaters() {
  stopUiUpdaters()
  lastRealTradeTime = Date.now()
  // 仅在有真实成交时更新；超过 3s 无成交时补一条 ghost，仍用 rAF 更新
  ghostTradeTimer = setInterval(() => {
    if (Date.now() - lastRealTradeTime > 3000 && currentThumbDisplay.value?.close > 0) {
      lastRealTradeTime = Date.now()
      const cPrice = Number(currentThumbDisplay.value.close)
      const ghostPrice = cPrice * (1 + (Math.random() - 0.5) * 0.001)
      const ghostAmount = Math.random() * 0.09 + 0.01
      pendingTrades.push({
        price: ghostPrice,
        amount: ghostAmount,
        direction: Math.random() > 0.5 ? 'BUY' : 'SELL',
        time: Date.now(),
        isGhost: true,
      })
      scheduleTradeUpdate()
    }
  }, 3000)
}

function stopUiUpdaters() {
  if (ghostTradeTimer) clearInterval(ghostTradeTimer)
  ghostTradeTimer = null
  pendingPlate = null
  pendingTrades = []
}

async function pollLatestPrice() {
  try {
    const raw = await getMarketKline(currentSymbol.value, '1m', 1)
    if (raw && Array.isArray(raw) && raw.length > 0) {
      const k = raw[raw.length - 1]
      const close = Number(k.close)
      // Simulate partial ticker data for chart update
      // Only update if WS is dead (liveTicker might be null or old)
      liveTicker.value = {
        close,
        chg: 0, 
        open: Number(k.open),
        high: Number(k.high),
        low: Number(k.low),
        volume: Number(k.volume),
        // Preserve other fields if possible?
        ...liveTicker.value,
        close, // override
      }
    }
  } catch (e) {}
}

function startFastPolling() {
  if (fastPollingTimer) return
  pollLatestPrice() // Immediate poll
  fastPollingTimer = setInterval(pollLatestPrice, 2000)
}

function stopFastPolling() {
  if (fastPollingTimer) {
    clearInterval(fastPollingTimer)
    fastPollingTimer = null
  }
}

function getRealMarketFastThumbPollMs() {
  // Depth 模式下如果高频更新 thumb，会导致感官上的“闪动”；因此降频。
  if (chartMode.value === 'depth') return isWeakNetwork.value ? 3000 : 1500
  // WS 断流时的降级轮询不需要 200ms，避免持续高频请求影响体验与资源
  if (isWeakNetwork.value) return 1500
  return 1000
}

function getRealMarketPlatePollMs() {
  if (isWeakNetwork.value) return 2500
  return 1000
}

async function pollRealMarketPlate() {
  if (realMarketPlatePollingInFlight) return
  if (document.hidden) return
  if (!currentSymbol.value) return
  if (chartMode.value !== 'depth') return
  // 仅在 WS 未稳定推送时才作为降级兜底
  if (realMarketWsConnected) return

  realMarketPlatePollingInFlight = true
  try {
    const data = await getMarketPlate(currentSymbol.value, plateLimit.value)
    const ask = Array.isArray(data?.ask) ? data.ask : []
    const bid = Array.isArray(data?.bid) ? data.bid : []
    const sig = `${ask?.[0]?.price ?? ''}|${bid?.[0]?.price ?? ''}|${ask.length}|${bid.length}`
    if (sig === lastPlateSig) return
    lastPlateSig = sig

    plate.value = { ask, bid }
    if (depthInstance) nextTick().then(() => drawDepthChart())
    statusBarErrors.value = { ...statusBarErrors.value, plate: '' }
  } catch (_) {}
  finally {
    realMarketPlatePollingInFlight = false
  }
}

function startRealMarketPlatePolling() {
  if (realMarketPlatePollingTimer) return
  if (document.hidden) return
  if (chartMode.value !== 'depth') return
  if (realMarketWsConnected) return

  pollRealMarketPlate().catch(() => {})
  realMarketPlatePollingTimer = setInterval(() => {
    pollRealMarketPlate().catch(() => {})
  }, getRealMarketPlatePollMs())
}

function stopRealMarketPlatePolling() {
  if (realMarketPlatePollingTimer) {
    clearInterval(realMarketPlatePollingTimer)
    realMarketPlatePollingTimer = null
  }
  realMarketPlatePollingInFlight = false
}

function stopRealMarketNoMsgWatchdog() {
  if (realMarketNoMsgWatchdogTimer) {
    clearInterval(realMarketNoMsgWatchdogTimer)
    realMarketNoMsgWatchdogTimer = null
  }
  realMarketNoMsgWatchdogInFlight = false
}

function startRealMarketNoMsgWatchdog(connToken) {
  stopRealMarketNoMsgWatchdog()
  if (document.hidden) return
  realMarketNoMsgWatchdogTimer = setInterval(() => {
    // 连接已切换：忽略旧 watchdog 回调
    if (connToken !== realMarketConnToken) return
    if (document.hidden) return
    if (!realMarketWsConnected) return
    if (!lastTickerTime) return
    const dt = Date.now() - lastTickerTime
    if (dt >= REAL_MARKET_NO_MSG_TIMEOUT_MS && !realMarketNoMsgWatchdogInFlight) {
      realMarketNoMsgWatchdogInFlight = true
      realMarketWsConnected = false
      // 降级轮询：让 liveTicker/Depth 不再“假死”
      stopRealMarketFastThumbPolling()
      startRealMarketFastThumbPolling()
      stopRealMarketNoMsgWatchdog()
      realMarketNoMsgWatchdogInFlight = false
    }
  }, 1000)
}

async function pollRealMarketThumbOne() {
  if (realMarketFastThumbPollingInFlight) return
  const sym = currentSymbol.value
  if (!sym) return
  if (document.hidden) return

  realMarketFastThumbPollingInFlight = true
  try {
    const data = await getMarketSymbolThumbOne(sym)
    if (!data) return
    // 切换 pair 后，丢弃旧响应
    if (sym !== currentSymbol.value) return

    const close = Number(data.close) || 0
    const prevClose = Number(liveTicker.value?.close ?? NaN)
    if (!Number.isNaN(prevClose) && prevClose === close) return
    if (close > 0) {
      liveTicker.value = {
        close,
        chg: Number(data.chg) || 0,
        open: Number(data.open) || 0,
        high: Number(data.high) || 0,
        low: Number(data.low) || 0,
        volume: Number(data.volume) || 0,
      }
      syncChartWithPrice(liveTicker.value.close)
    } else {
      // 即便 close 为 0 也维持 UI 状态，避免“假死”
      liveTicker.value = {
        close: 0,
        chg: Number(data.chg) || 0,
        open: Number(data.open) || 0,
        high: Number(data.high) || 0,
        low: Number(data.low) || 0,
        volume: Number(data.volume) || 0,
      }
    }
  } catch (_) {
    // 高频轮询失败不弹 toast，避免页面刷屏
  } finally {
    realMarketFastThumbPollingInFlight = false
  }
}

function startRealMarketFastThumbPolling() {
  if (fastThumbPollingEnabled) return
  if (document.hidden) return
  if (currentThumb.value?.virtual) return
  if (realMarketWsConnected) return

  fastThumbPollingEnabled = true
  const intervalMs = getRealMarketFastThumbPollMs()
  pollRealMarketThumbOne().catch(() => {})
  realMarketFastThumbPollingTimer = setInterval(() => {
    pollRealMarketThumbOne().catch(() => {})
  }, intervalMs)

  // Depth 模式需要额外轮询 plate，否则深度图会“静止”
  startRealMarketPlatePolling()
}

function stopRealMarketFastThumbPolling() {
  fastThumbPollingEnabled = false
  if (realMarketFastThumbPollingTimer) {
    clearInterval(realMarketFastThumbPollingTimer)
    realMarketFastThumbPollingTimer = null
  }
  realMarketFastThumbPollingInFlight = false
  stopRealMarketPlatePolling()
}

// 单一重连定时器 + 指数退避，避免多路同时重连导致 "Insufficient resources"
let reconnectTimerId = null
let reconnectDelayMs = 2000
const RECONNECT_DELAY_MAX = 30000

function scheduleReconnect() {
  if (reconnectTimerId || !currentSymbol.value) return
  const delay = reconnectDelayMs
  reconnectDelayMs = Math.min(RECONNECT_DELAY_MAX, reconnectDelayMs * 2)
  reconnectTimerId = setTimeout(() => {
    reconnectTimerId = null
    connectRealtimeStreams()
  }, delay)
}

// 虚拟盘：订阅 /topic/virtual-prices WebSocket 获取价格/深度/成交，轮询仅用于 loadThumbs + 定期刷新 K 线
let virtualPollingTimerId = null
let virtualPollTickCount = 0
let virtualStompClient = null

function getVirtualPollMs() {
  const a = currentThumb.value?.virtualActivity
  let ms = a === 'HOT' ? 500 : a === 'ACTIVE' ? 1000 : 2000
  if (isWeakNetwork.value) ms = Math.min(ms * 2, 4000)
  return ms
}

async function refreshVirtualKline() {
  if (!currentSymbol.value || !candleSeries) return
  try {
    const raw = await getMarketKline(currentSymbol.value, chartInterval.value, 500)
    const list = Array.isArray(raw) ? raw : []
    const nextData = list.map((b) => ({
      time: toUtcSeconds(Number(b.time) || 0),
      open: Number(b.open) || 0,
      high: Number(b.high) || 0,
      low: Number(b.low) || 0,
      close: Number(b.close) || 0,
    })).filter((d) => d.time > 0)
    const seen = new Set()
    const dedup = nextData.filter(d => { if (seen.has(d.time)) return false; seen.add(d.time); return true })
    dedup.sort((a, b) => a.time - b.time)
    klineData = dedup
    candleSeries.setData(klineData)
  } catch (_) {}
}

function startVirtualPolling() {
  if (document.hidden) return
  if (virtualPollingTimerId) return
  virtualPollTickCount = 0
  // 首次进入虚拟盘时拉一次盘口与成交，后续由 WebSocket 推送更新
  loadPlate().then(() => loadLatestTrade())
  function tick() {
    if (virtualPollingTimerId === null) return
    if (!currentSymbol.value) {
      virtualPollingTimerId = setTimeout(tick, getVirtualPollMs())
      return
    }
    loadThumbs()
    if (chartMode.value === 'depth' && depthInstance) nextTick().then(() => drawDepthChart())
    virtualPollTickCount++
    const interval = getVirtualPollMs()
    const ticksPerKlineRefresh = currentThumb.value?.virtualActivity === 'HOT' ? 6 : currentThumb.value?.virtualActivity === 'ACTIVE' ? 4 : 3
    if (virtualPollTickCount >= ticksPerKlineRefresh && chartMode.value === 'k') {
      virtualPollTickCount = 0
      refreshVirtualKline()
    }
    virtualPollingTimerId = setTimeout(tick, interval)
  }
  tick()
}

function getVirtualWsUrl() {
  const base = import.meta.env?.VITE_API_BASE || '/api'
  const origin = typeof base === 'string' && base.startsWith('http') ? base.replace(/\/api\/?$/, '') : window.location.origin
  return origin + '/ws/virtual-market'
}

function connectVirtualRealtime() {
  closeVirtualRealtime()
  const url = getVirtualWsUrl()
  const client = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 3000,
    heartbeatIncoming: 8000,
    heartbeatOutgoing: 8000,
  })
  client.onConnect = () => {
    client.subscribe('/topic/virtual-prices', (frame) => {
      try {
        const snapshotSym = currentSymbol.value
        if (!snapshotSym) return
        const list = JSON.parse(frame.body)
        if (!Array.isArray(list)) return
        const item = list.find((e) => (e.symbol || '').toUpperCase() === snapshotSym.toUpperCase())
        if (!item) return
        if (snapshotSym !== currentSymbol.value) return
        const price = Number(item.price)
        if (price > 0) {
          liveTicker.value = { close: price, chg: 0, open: price, high: price, low: price, volume: 0 }
          syncChartWithPrice(price)
        }
        if (item.plate && (item.plate.ask || item.plate.bid)) {
          const ask = (item.plate.ask || []).map((a) => ({ price: Number(a.price) || 0, amount: Number(a.amount) || 0 }))
          const bid = (item.plate.bid || []).map((b) => ({ price: Number(b.price) || 0, amount: Number(b.amount) || 0 }))
          plate.value = { ask, bid }
          if (chartMode.value === 'depth' && depthInstance) nextTick().then(() => drawDepthChart())
        }
        if (Array.isArray(item.trades) && item.trades.length > 0) {
          const items = item.trades.map((tr) => ({
            price: Number(tr.price) || 0,
            amount: Number(tr.amount) || 0,
            direction: tr.direction === 'BUY' ? 'BUY' : 'SELL',
            time: Number(tr.time) || Date.now(),
          }))
          latestTrades.value = [...items, ...latestTrades.value].slice(0, MAX_LATEST_TRADES)
        }
      } catch (_) {}
    })
  }
  client.activate()
  virtualStompClient = client
}

function closeVirtualRealtime() {
  if (virtualStompClient) {
    try {
      virtualStompClient.deactivate()
    } catch (_) {}
    virtualStompClient = null
  }
}

function stopVirtualPolling() {
  if (virtualPollingTimerId) {
    clearTimeout(virtualPollingTimerId)
    virtualPollingTimerId = null
  }
}

// 防抖：避免切换交易对时在旧连接未释放前就建新连接
let connectRealtimeStreamsTimerId = null
const CONNECT_DEBOUNCE_MS = 500
const CONNECT_DEBOUNCE_MS_WEAK = 1000
/** 连接状态锁，防止重入与连接泄漏；模板用于连接状态指示灯 */
const connecting = ref(false)
let symbolInfoWaitRetryCount = 0

function getConnectDebounceMs() {
  return isWeakNetwork.value ? CONNECT_DEBOUNCE_MS_WEAK : CONNECT_DEBOUNCE_MS
}

function connectRealtimeStreams() {
  if (connecting.value) return
  connecting.value = true
  closeRealtimeStreams()

  if (connectRealtimeStreamsTimerId) clearTimeout(connectRealtimeStreamsTimerId)
  const sym = (currentSymbol.value || 'BTC/USDT').replace('/', '').toLowerCase()
  if (!sym) return

  connectRealtimeStreamsTimerId = setTimeout(() => {
    connectRealtimeStreamsTimerId = null
    doConnectRealtimeStreams(sym)
  }, getConnectDebounceMs())
}

function doConnectRealtimeStreams(sym) {
  if (!currentSymbol.value) {
    connecting.value = false
    return
  }
  const symNow = (currentSymbol.value || '').replace('/', '').toLowerCase()
  if (symNow !== sym) {
    connecting.value = false
    return
  }

  // symbolInfo 仍未就绪时，虚拟/实盘判定可能串扰：先等待 symbolInfo
  if (!symbolInfo.value) {
    symbolInfoWaitRetryCount++
    if (symbolInfoWaitRetryCount <= 5) {
      connecting.value = false
      setTimeout(() => {
        // 只在当前交易对未变更时重试
        const nowSym = (currentSymbol.value || '').replace('/', '').toLowerCase()
        if (nowSym && nowSym === sym) connectRealtimeStreams()
      }, 200)
    } else {
      connecting.value = false
      switchLoadFailed.value = true
    }
    return
  }

  // 虚拟盘：订阅 WebSocket 获取价格/深度/成交，轮询仅做 loadThumbs + K 线刷新
  if (symbolInfo.value?.virtual) {
    startVirtualPolling()
    connectVirtualRealtime()
    connecting.value = false
    return
  }

  // 实盘：订阅后端 /topic/market-thumb 推送（500ms 由 Redis 广播），不再轮询
  stopRealMarketPolling()
  closeRealMarketRealtime()
  stopRealMarketFastThumbPolling()
  connectRealMarketRealtime()
  loadLatestTrade().catch(() => {})
  connecting.value = false
  startUiUpdaters()
}

function connectRealMarketRealtime() {
  const url = getVirtualWsUrl()
  const pairKey = (currentSymbol.value || '').replace('/', '-')
  if (!pairKey) return
  const pairDest = `/topic/market-pair-thumb/${pairKey}`

  // WS 连接状态：用于断线后降级轮询
  realMarketWsConnected = false
  const connToken = ++realMarketConnToken

  const client = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 3000,
    heartbeatIncoming: 8000,
    heartbeatOutgoing: 8000,
  })

  client.onConnect = () => {
    if (connToken !== realMarketConnToken) return
    realMarketWsConnected = true
    stopRealMarketFastThumbPolling()
    lastTickerTime = Date.now()
    startRealMarketNoMsgWatchdog(connToken)

    client.subscribe(pairDest, (frame) => {
      try {
        if (connToken !== realMarketConnToken) return
        const payload = JSON.parse(frame.body)
        const thumb = payload?.thumb || payload
        if (!thumb) return

        const serverSymbol = thumb.symbol
        if (serverSymbol && serverSymbol !== currentSymbol.value) return

        // 如果之前因为“无消息超时”降级进入轮询，这里一旦收到 WS 推送就立刻恢复 WS 状态并停止轮询。
        realMarketWsConnected = true
        stopRealMarketFastThumbPolling()
        stopRealMarketNoMsgWatchdog()

        const nextClose = Number(thumb.close) || 0
        const prevClose = Number(liveTicker.value?.close ?? NaN)

        lastTickerTime = Date.now()
        const shouldUpdateThumb = Number.isNaN(prevClose) || prevClose !== nextClose
        if (shouldUpdateThumb) {
          liveTicker.value = {
            close: nextClose,
            chg: Number(thumb.chg) || 0,
            open: Number(thumb.open) || 0,
            high: Number(thumb.high) || 0,
            low: Number(thumb.low) || 0,
            volume: Number(thumb.volume) || 0,
          }
          syncChartWithPrice(nextClose)
        }

        const p = payload?.plate
        if (p && typeof p === 'object') {
          const ask = (p.ask || []).map((x) => ({ price: Number(x.price) || 0, amount: Number(x.amount) || 0 }))
          const bid = (p.bid || []).map((x) => ({ price: Number(x.price) || 0, amount: Number(x.amount) || 0 }))
          plate.value = { ask, bid }
          if (chartMode.value === 'depth' && depthInstance) nextTick().then(() => drawDepthChart())
        }

        if (typeof payload?.marketMaintenance === 'boolean') marketMaintenance.value = payload.marketMaintenance
      } catch (_) {}
    })
  }

  client.onWebSocketClose = () => {
    if (connToken !== realMarketConnToken) return
    realMarketWsConnected = false
    stopRealMarketNoMsgWatchdog()
    // WS 断开时启动降级轮询，直到 WS 重连成功
    if (!document.hidden && !symbolInfo.value?.virtual) startRealMarketFastThumbPolling()
  }

  client.onStompError = () => {
    if (connToken !== realMarketConnToken) return
    realMarketWsConnected = false
    stopRealMarketNoMsgWatchdog()
    if (!document.hidden && !symbolInfo.value?.virtual) startRealMarketFastThumbPolling()
  }

  client.activate()
  realMarketStompClient = client
}

function closeRealMarketRealtime() {
  if (realMarketStompClient) {
    try {
      // invalidate pending callbacks
      realMarketConnToken++
      realMarketStompClient.deactivate()
    } catch (_) {}
    realMarketStompClient = null
  }
  stopRealMarketNoMsgWatchdog()
  stopRealMarketPlatePolling()
}

function stopRealMarketPolling() {
  if (realMarketPollingTimer) {
    clearInterval(realMarketPollingTimer)
    realMarketPollingTimer = null
  }
}

/** 安全关闭 WebSocket：清空 onerror 避免 "Ping received after close" 报错；CONNECTING 时在 onopen 后延迟关闭 */
function safeCloseWs(ws) {
  if (!ws) return
  const noop = () => {}
  const clearHandlers = (w) => {
    if (!w) return
    w.onerror = noop
    w.onclose = null
    w.onmessage = null
    w.onopen = null
  }
  if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CLOSING) {
    clearHandlers(ws)
    ws.close()
  } else if (ws.readyState === WebSocket.CONNECTING) {
    clearHandlers(ws)
    ws.onopen = () => {
      setTimeout(() => {
        if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CLOSING) {
          clearHandlers(ws)
          ws.close()
        }
      }, 80)
    }
  }
}

function closeRealtimeStreams() {
  if (reconnectTimerId) {
    clearTimeout(reconnectTimerId)
    reconnectTimerId = null
  }
  if (connectRealtimeStreamsTimerId) {
    clearTimeout(connectRealtimeStreamsTimerId)
    connectRealtimeStreamsTimerId = null
  }
  stopVirtualPolling()
  closeVirtualRealtime()
  stopRealMarketPolling()
  closeRealMarketRealtime()
  stopRealMarketFastThumbPolling()
  // invalidate any in-flight WS callbacks so they can't restart polling
  realMarketConnToken++
  stopUiUpdaters()
  stopFastPolling()
  if (tickerWatchdog) clearInterval(tickerWatchdog)
  if (chartRetryTimeoutId) {
    clearTimeout(chartRetryTimeoutId)
    chartRetryTimeoutId = null
  }
  liveTicker.value = null
  marketMaintenance.value = false
  safeCloseWs(tickerWs)
  tickerWs = null
  safeCloseWs(depthWs)
  depthWs = null
  safeCloseWs(aggTradeWs)
  aggTradeWs = null
}

function fillPrice(price) {
  if (limitType.value) {
    longPrice.value = price
    shortPrice.value = price
    if (highlightTimer) clearTimeout(highlightTimer)
    priceInputHighlight.value = true
    highlightTimer = setTimeout(() => { priceInputHighlight.value = false; highlightTimer = null }, 200)
  }
}

function drawDepthChart() {
  if (!depthContainer.value || ((!plate.value.ask || !plate.value.ask.length) && (!plate.value.bid || !plate.value.bid.length))) return
  const w = depthContainer.value.clientWidth
  const h = depthContainer.value.clientHeight
  if (w <= 0 || h <= 0) return
  const ask = plate.value.ask.map((a) => ({ price: Number(a.price), amount: Number(a.amount) }))
  const bid = plate.value.bid.map((b) => ({ price: Number(b.price), amount: Number(b.amount) }))
  let acc = 0
  const askLine = ask.slice().reverse().map((a) => { acc += a.amount; return { value: acc, price: a.price } })
  acc = 0
  const bidLine = bid.map((b) => { acc += b.amount; return { value: acc, price: b.price } })
  if (depthInstance) {
    depthInstance.remove()
    depthInstance = null
  }
  depthInstance = createChart(depthContainer.value, {
    layout: { background: { color: '#172636' }, textColor: '#8b9bb4', attributionLogo: false },
    grid: {
      vertLines: { color: 'rgba(42, 45, 54, 0.6)', style: 1 },
      horzLines: { color: 'rgba(42, 45, 54, 0.6)', style: 1 },
    },
    width: w,
    height: h,
    autoSize: true,
    rightPriceScale: { borderColor: 'transparent', scaleMargins: { top: 0.1, bottom: 0.2 } },
    timeScale: { visible: false, borderColor: 'transparent' },
  })
  const askSeries = depthInstance.addSeries(AreaSeries, {
    lineColor: '#ef5350',
    topColor: 'rgba(239, 83, 80, 0.4)',
    bottomColor: 'rgba(239, 83, 80, 0)',
    lineWidth: 2,
  })
  const bidSeries = depthInstance.addSeries(AreaSeries, {
    lineColor: '#26a69a',
    topColor: 'rgba(38, 166, 154, 0.4)',
    bottomColor: 'rgba(38, 166, 154, 0)',
    lineWidth: 2,
  })
  const askData = askLine.map((p, i) => ({ time: i, value: p.value }))
  const bidData = bidLine.map((p, i) => ({ time: i, value: p.value }))
  askSeries.setData(askData)
  bidSeries.setData(bidData)
  depthInstance.timeScale().fitContent()
}

// Simulated simple polling pulse
let pulseTimer = null;

let virtualConfirmEscHandler = null
watch(showVirtualConfirmModal, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (!open) {
    pendingOrderDirection.value = ''
    if (virtualConfirmEscHandler) {
      document.removeEventListener('keydown', virtualConfirmEscHandler)
      virtualConfirmEscHandler = null
    }
    return
  }
  virtualConfirmEscHandler = (e) => { if (e.key === 'Escape') closeVirtualConfirmModal() }
  document.addEventListener('keydown', virtualConfirmEscHandler)
})
watch(currentSymbol, async () => {
  switchingSymbol.value = true
  switchLoadFailed.value = false
  symbolInfoWaitRetryCount = 0
  await loadSymbolInfo()
  closeKlineWs()
  closeRealtimeStreams()
  const chartPromise = chartMode.value === 'k' ? loadChart() : Promise.resolve()
  Promise.allSettled([chartPromise, loadPlate(), loadLatestTrade()])
    .then((results) => {
      const allRejected = results.every((r) => r.status === 'rejected')
      switchingSymbol.value = false
      if (allRejected) switchLoadFailed.value = true
      else {
        connectRealtimeStreams()
        if (chartMode.value === 'depth') nextTick().then(() => drawDepthChart())
      }
    })
})

watch(chartInterval, () => {
  if (chartMode.value === 'k') {
    closeKlineWs()
    loadChart()
  }
})

watch(chartMode, () => {
  nextTick().then(() => {
    if (chartMode.value === 'depth') {
      closeKlineWs()
      loadPlate().then(() => drawDepthChart())
      // WS 推送不稳定时，Depth 需要 REST plate 降级轮询兜底
      if (!symbolInfo.value?.virtual && !realMarketWsConnected) startRealMarketPlatePolling()
    } else {
      if (depthInstance) { depthInstance.remove(); depthInstance = null }
      stopRealMarketPlatePolling()
      loadChart()
    }
  })
})

watch(() => app.lang, () => {
  if (chartMode.value === 'k' && chartContainer.value) loadChart()
})

watch(orderTab, (tab) => {
  if (tab === 'position') loadCurrentPositions()
  else if (tab === 'current') loadCurrentOrders()
  else loadHistoryOrders()
})

watch(
  () => route.params.pair,
  (newPair) => {
    if (newPair) {
      const p = normalizeSymbol(newPair)
      if (p !== currentSymbol.value) {
        currentSymbol.value = p
        // The existing watcher on currentSymbol will trigger logic like initChart
      }
    }
  }
)

function updateNetworkQuality() {
  const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  if (!conn || conn.effectiveType === undefined) return
  const t = String(conn.effectiveType).toLowerCase()
  isWeakNetwork.value = t === '3g' || t === '2g' || t === 'slow-2g'
}

/** 后台标签页暂停虚拟盘高频轮询，降低多开标签时的 CPU 占用 */
function onDocumentVisibilityChange() {
  if (document.hidden) {
    stopVirtualPolling()
    stopRealMarketFastThumbPolling()
  } else if (symbolInfo.value?.virtual && currentSymbol.value) {
    startVirtualPolling()
  } else if (!symbolInfo.value?.virtual && currentSymbol.value && !realMarketWsConnected) {
    startRealMarketFastThumbPolling()
  }
}

onMounted(async () => {
  updateNetworkQuality()
  const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  if (conn && typeof conn.addEventListener === 'function') {
    conn.addEventListener('change', updateNetworkQuality)
    onBeforeUnmount(() => conn.removeEventListener('change', updateNetworkQuality))
  }
  document.addEventListener('visibilitychange', onDocumentVisibilityChange)
  onBeforeUnmount(() => document.removeEventListener('visibilitychange', onDocumentVisibilityChange))

  // lightweight-charts 在销毁/重建的竞态下可能抛出 Object is disposed。
  // 这类错误不应中断行情更新流程，因此仅在该错误命中时吞掉。
  windowErrorHandler = (ev) => {
    try {
      const msg = String(ev?.message || '')
      const stack = String(ev?.error?.stack || ev?.stack || '')
      if (msg.includes('Object is disposed') && (stack.includes('lightweight-charts') || stack.includes('lightweight'))) {
        ev.preventDefault?.()
        // onerror：返回 true 可抑制控制台的 Uncaught Error 输出
        return true
      }
    } catch (_) {}
    return false
  }
  window.addEventListener('error', windowErrorHandler)
  onBeforeUnmount(() => window.removeEventListener('error', windowErrorHandler))

  loading.value = true
  await loadThumbs()
  await loadSymbolInfo()
  loading.value = false
  if (isLogin.value) {
    loadWallet()
    loadCurrentPositions()
  }
  await loadPlate()
  await loadLatestTrade()
  connectRealtimeStreams()
  if (chartMode.value === 'k') await loadChart()
  else nextTick().then(() => drawDepthChart())

  // 仅轮询：虚拟盘刷新 thumbs；实盘由 /topic/market-thumb 推送。钱包、订单/持仓轮询保留。
  pulseTimer = setInterval(() => {
    if (!document.hidden && currentThumb.value?.virtual) loadThumbs()
    if (orderTab.value === 'position') loadCurrentPositions()
    if (orderTab.value === 'current') loadCurrentOrders()
    if (orderTab.value === 'history') loadHistoryOrders()
    loadWallet()
  }, 3000)
})

let chartResizeObserver = null
let depthResizeObserver = null

onMounted(() => {
  const onResize = (el, instance) => {
    if (!el || !instance) return
    const w = el.clientWidth
    if (w > 0) instance.applyOptions({ autoSize: false, width: w })
  }
  nextTick().then(() => {
    if (chartContainer.value) {
      chartResizeObserver = new ResizeObserver(() => {
        if (!chartInstance || !chartContainer.value) return
        try {
          onResize(chartContainer.value, chartInstance)
        } catch (e) {
          // lightweight-charts 在 remove()/dispose 后的残留绘制可能抛 Object is disposed
          // 此处吞掉即可，下一次有效数据/Resize 会重建或更新
        }
      })
      chartResizeObserver.observe(chartContainer.value)
    }
    if (depthContainer.value) {
      depthResizeObserver = new ResizeObserver(() => {
        if (depthInstance) onResize(depthContainer.value, depthInstance)
      })
      depthResizeObserver.observe(depthContainer.value)
    }
  })
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  closeKlineWs()
  closeRealtimeStreams()
  if (pulseTimer) clearInterval(pulseTimer);
  if (highlightTimer) {
    clearTimeout(highlightTimer)
    highlightTimer = null
  }
  if (chartResizeObserver && chartContainer.value) {
    chartResizeObserver.disconnect()
    chartResizeObserver = null
  }
  if (depthResizeObserver && depthContainer.value) {
    depthResizeObserver.disconnect()
    depthResizeObserver = null
  }
  if (chartInstance) {
    chartInstance.remove()
    chartInstance = null
  }
  candleSeries = null
  klineData = []
  if (depthInstance) {
    depthInstance.remove()
    depthInstance = null
  }
})
</script>

<template>
  <div class="exchange-page" :class="{ 'mobile-view': isMobile }">
    <!-- DESKTOP VIEW -->
    <template v-if="!isMobile">
    <div class="exchange-layout">
      <aside class="plate-panel">
        <div class="plate-header">
          <span class="plate-title">{{ t('exchange.depth') }}</span>
          <span class="plate-price" :class="{ up: Number(currentThumbDisplay.chg) > 0, down: Number(currentThumbDisplay.chg) < 0 }">
            {{ formatNum(currentThumbDisplay.close) }}
          </span>
        </div>
        <div class="plate-table-wrap">
          <table class="plate-table">
            <thead>
              <tr>
                <th>{{ t('exchange.price') }}(USDT)</th>
                <th>{{ t('exchange.num') }}({{ symbolInfo?.coinSymbol || 'BTC' }})</th>
              </tr>
            </thead>
            <tbody>
              <template v-if="switchingSymbol">
                <tr v-for="i in 8" :key="'ph-' + i" class="plate-placeholder ask-row"><td colspan="2"></td></tr>
                <tr class="mid-row"><td colspan="2" class="mid-price">--</td></tr>
                <tr v-for="i in 8" :key="'ph-b-' + i" class="plate-placeholder bid-row"><td colspan="2"></td></tr>
              </template>
              <template v-else>
                <tr
                  v-for="(a, i) in (plate.ask || []).slice(0, 8).reverse()"
                  :key="'ask-' + i"
                  class="ask-row"
                  tabindex="0"
                  role="button"
                  :aria-label="t('exchange.fillPrice') + ' ' + formatNum(a.price)"
                  @click="fillPrice(a.price)"
                  @keydown.enter="fillPrice(a.price)"
                >
                  <td class="price">{{ formatNum(a.price) }}</td>
                  <td>{{ formatNum(a.amount) }}</td>
                </tr>
                <tr class="mid-row">
                  <td colspan="2" class="mid-price" :class="{ up: Number(currentThumbDisplay.chg) > 0, down: Number(currentThumbDisplay.chg) < 0 }">
                    {{ formatNum(currentThumbDisplay.close) }}
                  </td>
                </tr>
                <tr
                  v-for="(b, i) in (plate.bid || []).slice(0, 8)"
                  :key="'bid-' + i"
                  class="bid-row"
                  tabindex="0"
                  role="button"
                  :aria-label="t('exchange.fillPrice') + ' ' + formatNum(b.price)"
                  @click="fillPrice(b.price)"
                  @keydown.enter="fillPrice(b.price)"
                >
                  <td class="price">{{ formatNum(b.price) }}</td>
                  <td>{{ formatNum(b.amount) }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <div class="trade-list-header">{{ t('exchange.done') }}</div>
        <div class="trade-list-wrap">
          <table class="trade-list-table">
            <thead>
              <tr>
                <th>{{ t('exchange.price') }}</th>
                <th>{{ t('exchange.num') }}</th>
                <th>{{ t('exchange.time') }}</th>
              </tr>
            </thead>
            <tbody>
              <template v-if="switchingSymbol">
                <tr v-for="i in 10" :key="'tph-' + i" class="trade-placeholder"><td colspan="3"></td></tr>
              </template>
              <template v-else>
                <tr 
                  v-for="(tr, i) in latestTrades.slice(0, 10)" 
                  :key="i" 
                  :class="[tr.direction === 'BUY' ? 'buy' : 'sell', { 'user-trade-flash': tr.isUserTrade, 'ghost-trade': tr.isGhost }]"
                >
                  <td class="price">{{ formatNum(tr.price) }}</td>
                  <td>{{ formatNum(tr.amount) }}</td>
                  <td class="time-cell">
                    <span class="time-date">{{ formatTimeSplit(tr.time).date }}</span>
                    <span class="time-hms">{{ formatTimeSplit(tr.time).time }}</span>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </aside>

      <main class="exchange-center">
        <div class="symbol-header">
          <span class="symbol-name">{{ currentSymbol }}</span>
          <div
            class="ws-status"
            :class="{
              connected: liveTicker !== null,
              connecting: connecting,
              disconnected: liveTicker === null && !connecting
            }"
          >
            ● {{ statusText }}
          </div>
          <span class="symbol-price" :class="{ up: Number(currentThumbDisplay.chg) > 0, down: Number(currentThumbDisplay.chg) < 0 }">
            {{ formatNum(currentThumbDisplay.close) }}
          </span>
          <span class="symbol-chg" :class="{ up: Number(currentThumbDisplay.chg) > 0, down: Number(currentThumbDisplay.chg) < 0 }">
            {{ (Number(currentThumbDisplay.chg) * 100 || 0).toFixed(2) }}%
          </span>
          <span v-if="loading" class="symbol-loading">{{ t('common.loading') }}</span>
        </div>
        <div v-if="marketMaintenance" class="status-bar-error market-maintenance" role="status">{{ t('exchange.marketMaintenance', 'Market under maintenance') }}</div>
        <div v-if="statusBarErrorText && !marketMaintenance" class="status-bar-error" role="status">{{ statusBarErrorText }}</div>
        <div class="chart-wrap" :class="{ 'weak-network': isWeakNetwork }">
          <div class="chart-toolbar">
            <span
              class="chart-mode-btn"
              :class="{ active: chartMode === 'k' }"
              @click="chartMode = 'k'"
            >{{ t('exchange.kline') }}</span>
            <span
              class="chart-mode-btn"
              :class="{ active: chartMode === 'depth' }"
              @click="chartMode = 'depth'"
            >{{ t('exchange.depth') }}</span>
            <template v-if="chartMode === 'k'">
              <span
                v-for="opt in chartIntervalOptions"
                :key="opt.value"
                class="chart-mode-btn chart-interval-btn"
                :class="{ active: chartInterval === opt.value }"
                @click="chartInterval = opt.value"
              >{{ opt.label }}</span>
              <span
                class="chart-utc-tip"
                title="K线按全球统一的UTC时间计算"
              >ℹ️</span>
            </template>
          </div>
          <div v-show="chartMode === 'k'" ref="chartContainer" class="chart-container"></div>
          <div v-show="chartMode === 'depth'" ref="depthContainer" class="chart-container"></div>
          <div v-if="switchingSymbol" class="chart-skeleton" aria-busy="true" aria-label="正在切换交易对"></div>
          <div v-if="switchLoadFailed && !switchingSymbol" class="chart-error-placeholder" role="alert">
            <div class="chart-error-inner">
              <div>加载失败：可能是网络暂不可用，或交易对无效/已下架</div>
              <div class="chart-error-actions">
                <button
                  type="button"
                  class="chart-retry-btn"
                  :disabled="symbolRetryLoading"
                  @click="retryAfterSymbolLoadFailure"
                >
                  {{ symbolRetryLoading ? '重试中…' : '重试加载' }}
                </button>
                <router-link to="/exchange" class="retry-link">
                  返回交易对列表
                </router-link>
              </div>
            </div>
          </div>
        </div>
        
        <!-- Positions / Orders / History (below chart) -->
        <div class="order-section">
          <div class="order-tabs">
            <button type="button" :class="{ active: orderTab === 'position' }" @click="orderTab = 'position'">
              {{ t('exchange.position') }}
            </button>
            <button type="button" :class="{ active: orderTab === 'current' }" @click="orderTab = 'current'">
              {{ t('exchange.curdelegation') }}
            </button>
            <button type="button" :class="{ active: orderTab === 'history' }" @click="orderTab = 'history'">
              {{ t('exchange.history') }}
            </button>
          </div>
          <div v-if="!isLogin" class="order-login-tip">
            <router-link to="/login">{{ t('common.login') }}</router-link> {{ t('exchange.viewOrdersPositions') }}
          </div>
          <div v-else class="order-table-wrap">
          
            <template v-if="orderTab === 'position'">
              <table class="order-table">
                <thead>
                  <tr>
                    <th>{{ t('exchange.underlying') }}</th>
                    <th>{{ t('exchange.type') }}</th>
                    <th>{{ t('exchange.avgPrice') }}</th>
                    <th>{{ t('exchange.maintenanceMargin') }}</th>
                    <th>{{ t('exchange.unrealizedPnlRoe') }}</th>
                    <th>{{ t('exchange.action') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in currentPositions" :key="p.id">
                    <td>{{ p.symbol }}</td>
                    <td :class="p.direction === 'LONG' ? 'buy' : 'sell'">
                      {{ p.direction === 'LONG' ? t('exchange.long') : t('exchange.short') }} {{ p.leverage }}x
                    </td>
                    <td>{{ formatNum(p.avgPrice) }}</td>
                    <td>{{ formatNum(p.margin, 2) }}</td>
                    <td :class="calcPnl(p) >= 0 ? 'buy' : 'sell'">
                      {{ formatNum(calcPnl(p), 2) }} ({{ formatNum(calcRoe(p), 2) }}%)
                    </td>
                    <td><button type="button" class="btn-undo" @click="closePosition(p.id)">{{ t('exchange.closePos') }}</button></td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!currentPositions.length && !orderLoading" class="no-orders">{{ t('common.nodata') }}</p>
            </template>
          
            <template v-else-if="orderTab === 'current'">
              <table class="order-table">
                <thead>
                  <tr>
                    <th>{{ t('exchange.time') }}</th>
                    <th>{{ t('exchange.underlying') }}</th>
                    <th>{{ t('exchange.type') }}</th>
                    <th>{{ t('exchange.orderPrice') }}</th>
                    <th>{{ t('exchange.num') }}</th>
                    <th>{{ t('exchange.action') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="o in currentOrders" :key="o.orderId">
                    <td>{{ formatTime(o.createTime) }}</td>
                    <td>{{ o.symbol }}</td>
                    <td :class="o.direction === 'LONG' ? 'buy' : 'sell'">
                      {{ o.direction === 'LONG' ? t('exchange.long') : t('exchange.short') }} {{ o.leverage }}x
                    </td>
                    <td>{{ formatNum(o.price) }}</td>
                    <td>{{ formatNum(o.amount) }}</td>
                    <td><button type="button" class="btn-undo" @click="cancelOrder(o.orderId)">{{ t('exchange.undo') }}</button></td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!currentOrders.length && !orderLoading" class="no-orders">{{ t('common.nodata') }}</p>
            </template>
            
            <template v-else>
              <table class="order-table">
                <thead>
                  <tr>
                    <th>{{ t('exchange.tradeTime') }}</th>
                    <th>{{ t('exchange.symbol') }}</th>
                    <th>{{ t('exchange.direction') }}</th>
                    <th>{{ t('exchange.fillPrice') }}</th>
                    <th>{{ t('exchange.done') }}</th>
                    <th>{{ t('exchange.dealamount') }}</th>
                    <th>{{ t('exchange.status') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="o in historyOrders" :key="o.orderId">
                    <td>{{ formatTime(o.createTime) }}</td>
                    <td>{{ o.symbol || '-' }}</td>
                    <td :class="o.direction === 'BUY' ? 'buy' : 'sell'">{{ o.direction === 'BUY' ? t('exchange.buyin') : t('exchange.sellout') }}</td>
                    <td>{{ formatNum(o.price) }}</td>
                    <td>{{ formatNum(o.tradedAmount) }}</td>
                    <td>{{ formatNum(o.totalAmount) }}</td>
                    <td>{{ tradeStatusText(o.status) }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!historyOrders.length && !orderLoading" class="no-orders">{{ t('common.nodata') }}</p>
            </template>
          </div>
        </div>
      </main>

      <!-- RIGHT: Order Form -->
      <aside class="order-panel">
        <!-- Account Overview -->
        <div v-if="isLogin" class="account-bar">
          <div class="account-item">
            <span class="account-label">{{ t('exchange.canuse') }}</span>
            <span class="account-val">{{ formatNum(walletBase, 2) }}</span>
          </div>
          <div class="account-item">
            <span class="account-label">{{ t('exchange.marginUsed') }}</span>
            <span class="account-val">{{ formatNum(totalMarginUsed, 2) }}</span>
          </div>
          <div class="account-item">
            <span class="account-label">{{ t('exchange.unrealizedPnl') }}</span>
            <span class="account-val" :class="totalUnrealizedPnl >= 0 ? 'buy' : 'sell'">{{ totalUnrealizedPnl >= 0 ? '+' : '' }}{{ formatNum(totalUnrealizedPnl, 2) }}</span>
          </div>
        </div>

        <!-- Margin Mode + Order Type -->
        <div class="sidebar-row">
          <div class="margin-mode-toggle">
            <button type="button" :class="{ active: marginMode === 'cross' }" @click="marginMode = 'cross'">{{ t('exchange.cross') }}</button>
            <button type="button" :class="{ active: marginMode === 'isolated' }" @click="marginMode = 'isolated'">{{ t('exchange.isolated') }}</button>
          </div>
          <div class="trade-tabs">
            <button type="button" :class="{ active: limitType }" @click="limitType = true">{{ t('exchange.limitprice') }}</button>
            <button type="button" :class="{ active: !limitType }" @click="limitType = false">{{ t('exchange.marketprice') }}</button>
          </div>
        </div>

        <!-- Leverage -->
        <div class="leverage-section">
          <div class="leverage-header">
            <span class="leverage-label">{{ t('exchange.leverage') }}</span>
            <span class="lev-val">{{ leverage }}x</span>
          </div>
          <input type="range" min="1" max="125" v-model="leverage" class="leverage-slider" />
          <div class="quick-lev-row">
            <button v-for="lv in [1, 5, 10, 25, 50, 75, 100]" :key="lv" type="button"
              :class="{ active: Number(leverage) === lv }" @click="setQuickLeverage(lv)">{{ lv }}x</button>
          </div>
        </div>

        <p v-if="errorMsg" class="trade-error">{{ errorMsg }}</p>
        <p v-else-if="marketPriceWaiting" class="trade-error">等待行情更新</p>

        <!-- Price -->
        <div class="trade-form">
          <div class="form-row">
            <label>{{ t('exchange.price') }}</label>
            <input
              v-model="longPrice"
              type="text"
              :class="{ 'price-highlight': priceInputHighlight }"
              :placeholder="limitType ? t('exchange.orderPricePlaceholder') : t('exchange.marketprice')"
              :disabled="!limitType"
            />
            <span class="unit">USDT</span>
          </div>

          <!-- Amount + Quick % -->
          <div class="form-row">
            <label>{{ t('exchange.num') }}</label>
            <input v-model="longAmount" type="text" :placeholder="t('exchange.amountPlaceholder')" />
            <span class="unit">{{ symbolInfo?.coinSymbol || 'BTC' }}</span>
          </div>
          <div class="quick-amt-row">
            <button v-for="pct in [25, 50, 75, 100]" :key="pct" type="button" @click="setQuickAmount(pct)">{{ pct }}%</button>
          </div>

          <!-- Order Info -->
          <div v-if="orderValue > 0" class="order-info">
            <div class="info-line">
              <span>{{ t('exchange.orderValue') }}</span>
              <span>{{ formatNum(orderValue, 2) }} USDT</span>
            </div>
            <div class="info-line">
              <span>{{ t('exchange.requiredMargin') }}</span>
              <span>{{ formatNum(requiredMargin, 2) }} USDT</span>
            </div>
            <div class="info-line">
              <span>{{ t('exchange.feePct') }}</span>
              <span>{{ formatNum(orderValue * 0.0004, 4) }} USDT</span>
            </div>
          </div>

          <!-- TP/SL Toggle -->
          <div class="tpsl-toggle" @click="showTpSl = !showTpSl">
            <span>{{ t('exchange.tpsl') }}</span>
            <span class="tpsl-arrow">{{ showTpSl ? '▲' : '▼' }}</span>
          </div>
          <div v-if="showTpSl" class="tpsl-inputs">
            <div class="form-row">
              <label class="tp-label">{{ t('exchange.tp') }}</label>
              <input v-model="tpPrice" type="text" :placeholder="t('exchange.tpPricePlaceholder')" />
              <span class="unit">USDT</span>
            </div>
            <div class="form-row">
              <label class="sl-label">{{ t('exchange.sl') }}</label>
              <input v-model="slPrice" type="text" :placeholder="t('exchange.slPricePlaceholder')" />
              <span class="unit">USDT</span>
            </div>
          </div>

          <!-- Action buttons -->
          <div class="dual-btn-row">
            <button type="button" class="btn-buy" :disabled="submitLoading || marketPriceWaiting" @click="doLong">
              {{ t('exchange.buyLong') }}
            </button>
            <button type="button" class="btn-sell" :disabled="submitLoading || marketPriceWaiting" @click="doShort">
              {{ t('exchange.sellShort') }}
            </button>
          </div>
        </div>
      </aside>
    </div>

    <!-- Mobile Sticky Action Bar (desktop layout should not render it) -->
    <div v-if="isMobile" class="mobile-action-bar">
      <button type="button" class="btn-buy" :disabled="submitLoading || marketPriceWaiting" @click="doLong">
        {{ t('exchange.buyLong') }}
      </button>
      <button type="button" class="btn-sell" :disabled="submitLoading || marketPriceWaiting" @click="doShort">
        {{ t('exchange.sellShort') }}
      </button>
    </div>
    </template>

    <!-- 虚拟盘下单确认：平台统一模态框，支持 Escape 关闭 -->
    <div
      v-if="showVirtualConfirmModal"
      class="virtual-confirm-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="virtual-confirm-title"
      @click.self="closeVirtualConfirmModal"
    >
      <div class="virtual-confirm-modal">
        <h3 id="virtual-confirm-title" class="virtual-confirm-title">{{ t('exchange.virtualConfirm') || '此为模拟交易，价格由算法生成。是否继续？' }}</h3>
        <div class="virtual-confirm-actions">
          <button type="button" class="btn-cancel" @click="closeVirtualConfirmModal">{{ t('common.cancel') || '取消' }}</button>
          <button type="button" class="btn-confirm" @click="confirmVirtualOrder">{{ t('common.confirm') || '确认' }}</button>
        </div>
      </div>
    </div>

    <!-- MOBILE VIEW -->
    <template v-if="isMobile">
      <MobileExchangeView
        ref="mobileExRef"
        :currentSymbol="currentSymbol"
        :currentThumbDisplay="currentThumbDisplay"
        :filteredSymbols="filteredSymbols"
        v-model:symbolSearch="symbolSearch"
        :plate="plate"
        :trades="latestTrades"
        :currentPositions="currentPositions"
        :currentOrders="currentOrders"
        :historyOrders="historyOrders"
        v-model:orderTab="orderTab"
        :walletBase="walletBase"
        v-model:orderType="orderType"
        v-model:orderPrice="longPrice"
        v-model:longAmount="longAmount"
        v-model:leverage="leverage"
        :submitLoading="submitLoading"
        :orderLoading="orderLoading"
        :period="chartInterval"
        :isLogin="isLogin"
        :errorMsg="errorMsg"
        :coinSymbol="(symbolInfo?.coinSymbol || currentSymbol?.split('/')[0]) || 'BTC'"
        @switchSymbol="switchSymbol"
        @setPeriod="setPeriod"
        @fillPrice="fillPrice"
        @closePosition="closePosition"
        @cancelOrder="cancelOrder"
        @setQuickLeverage="setQuickLeverage"
        @setQuickAmount="setQuickAmount"
        @doLong="doLong"
        @doShort="doShort"
        @clearError="errorMsg = ''"
      />
    </template>
  </div>
</template>

<style scoped>
/* ===== Page Root: fills entire viewport, no scroll ===== */
.exchange-page {
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  width: 100%;
  background: #0b1520;
  color: #fff;
  padding: 56px 10px 10px 10px;
}

/* ===== 3-Column Layout ===== */
.exchange-layout {
  box-sizing: border-box;
  display: flex;
  gap: 10px;
  width: 100%;
  /* 避免 flex-basis=0 在某些布局时交叉轴被“收缩到 0” */
  flex: 1 1 auto;
  min-height: 0;
  /* 桌面端：避免 flex 交叉轴在某些嵌套布局下被压到 0 */
  height: calc(100vh - 66px); /* 56px(top padding) + 10px(bottom padding) */
  align-items: stretch;
  overflow: hidden;
}

/* ===== LEFT: Depth + Trades ===== */
.plate-panel {
  flex: 0 0 220px;
  height: 100%;
  background: #172636;
  border-radius: 4px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.plate-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  margin-bottom: 4px;
}
.plate-title { font-size: 12px; color: #828ea1; }
.plate-price { font-size: 13px; font-weight: 600; }
.plate-price.up { color: #0ecb81; }
.plate-price.down { color: #f6465d; }

.plate-table-wrap {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}
.plate-table { width: 100%; font-size: 11px; border-collapse: collapse; }
.plate-table th { padding: 2px 4px; color: #828ea1; text-align: left; font-size: 10px; }
.plate-table td { padding: 1px 4px; cursor: pointer; line-height: 1.4; }
.plate-table .price { font-weight: 500; }
.ask-row .price { color: #f6465d; }
.bid-row .price { color: #0ecb81; }
.mid-row td { text-align: center; font-weight: 600; padding: 3px; }
.mid-price.up { color: #0ecb81; }
.mid-price.down { color: #f6465d; }

.trade-list-header {
  font-size: 12px;
  color: #828ea1;
  flex-shrink: 0;
  margin: 4px 0 2px;
}
.trade-list-wrap {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}
.trade-list-table { width: 100%; font-size: 11px; border-collapse: collapse; }
.trade-list-table th { padding: 2px 4px; color: #828ea1; text-align: left; font-size: 10px; }
.trade-list-table th:last-child { text-align: right; }
.trade-list-table td { padding: 1px 4px; line-height: 1.4; }
.trade-list-table td:last-child { text-align: right; }
.trade-list-table tr.buy .price, .trade-list-table tr.buy td:first-child { color: #0ecb81; }
.trade-list-table tr.sell .price, .trade-list-table tr.sell td:first-child { color: #f6465d; }
.time-cell { display: flex; flex-direction: column; align-items: flex-end; line-height: 1.2; }
.time-date { font-size: 10px; color: #828ea1; }
.time-hms { font-size: 10px; color: #a1a1aa; }

/* ===== CENTER: Chart + Trade Form ===== */
.exchange-center {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}
.symbol-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
  padding: 4px 0;
}
.symbol-name { font-size: 16px; font-weight: 600; color: #fff; }
.ws-status {
  font-size: 12px;
  color: #828ea1;
}
.ws-status.connected { color: #0ecb81; }
.ws-status.connecting { color: #f0a70a; }
.ws-status.disconnected { color: #f6465d; }
.status-bar-error {
  font-size: 12px;
  color: #f0a70a;
  padding: 4px 0;
  flex-shrink: 0;
}
.symbol-price { font-size: 18px; font-weight: 600; }
.symbol-price.up { color: #0ecb81; }
.symbol-price.down { color: #f6465d; }
.symbol-chg { font-size: 13px; }
.symbol-chg.up { color: #0ecb81; }
.symbol-chg.down { color: #f6465d; }
.symbol-loading { font-size: 13px; color: #828ea1; }

.chart-wrap {
  position: relative;
  background: #172636;
  border-radius: 24px;
  padding: 12px 16px 4px;
  /* 关键：chart-wrap 作为 exchange-center 的 flex 子项，必须占据剩余高度 */
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}
.chart-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-shrink: 0;
}
.chart-label { color: #e4e4e7; font-size: 14px; }
.chart-mode-btn {
  padding: 3px 8px;
  font-size: 12px;
  color: #828ea1;
  cursor: pointer;
  border-radius: 4px;
}
.chart-mode-btn.active { color: #f0a70a; background: rgba(240, 167, 10, 0.15); }
.chart-utc-tip {
  margin-left: 4px;
  cursor: help;
  opacity: 0.7;
  font-size: 12px;
}
.chart-container {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  position: relative;
}
.chart-skeleton {
  position: absolute;
  inset: 0;
  background: #1a2332;
  opacity: 0.85;
  will-change: opacity;
  animation: chart-skeleton-pulse 1.5s ease-in-out infinite;
  pointer-events: none;
}
.chart-wrap.weak-network .chart-skeleton {
  animation: none;
  opacity: 0.8;
}
@keyframes chart-skeleton-pulse {
  0%, 100% { opacity: 0.85; }
  50% { opacity: 0.6; }
}
.chart-error-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #172636;
  color: #828ea1;
  font-size: 14px;
  pointer-events: auto;
}
.chart-error-inner {
  text-align: center;
  max-width: 320px;
  line-height: 1.5;
}
.chart-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  align-items: center;
  margin-top: 16px;
}
.chart-retry-btn {
  padding: 8px 16px;
  border-radius: 4px;
  border: 1px solid #4a90d9;
  background: #1e3a5f;
  color: #c7d8e8;
  cursor: pointer;
  font-size: 14px;
}
.chart-retry-btn:hover:not(:disabled) {
  background: #2a4a73;
}
.chart-retry-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.plate-placeholder td,
.trade-placeholder td {
  background: #1a2332;
  animation: chart-skeleton-pulse 1.5s ease-in-out infinite;
  height: 22px;
}
@media (max-width: 768px) {
  .plate-table tr.ask-row,
  .plate-table tr.bid-row {
    position: relative;
  }
  .plate-table tr.ask-row::after,
  .plate-table tr.bid-row::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: -8px;
    bottom: -8px;
    min-height: 44px;
    /* 扩展可点击区域至至少 44pt，满足 Apple HIG */
    pointer-events: auto;
    z-index: 0;
  }
  .plate-table tr.ask-row td,
  .plate-table tr.bid-row td { position: relative; z-index: 1; }
  .plate-table tr.ask-row td,
  .plate-table tr.bid-row td,
  .plate-placeholder.ask-row td,
  .plate-placeholder.bid-row td {
    min-height: 44px;
    padding-top: 10px;
    padding-bottom: 10px;
  }
  .plate-placeholder td,
  .trade-placeholder td { min-height: 44px; }
}
.form-row input.price-highlight {
  transition: background-color 0.2s ease;
  background-color: rgba(240, 167, 10, 0.2);
}

/* 虚拟盘确认模态框 */
.virtual-confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.virtual-confirm-modal {
  background: #172636;
  border-radius: 12px;
  padding: 20px 24px;
  min-width: 280px;
  max-width: 90vw;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}
.virtual-confirm-title {
  margin: 0 0 16px;
  font-size: 15px;
  color: #e4e4e7;
  line-height: 1.4;
}
.virtual-confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
.virtual-confirm-actions .btn-cancel {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid #27313e;
  color: #828ea1;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
}
.virtual-confirm-actions .btn-confirm {
  padding: 8px 16px;
  background: #f0a70a;
  border: none;
  color: #0b1520;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
}

/* ===== Order Section (positions/orders below chart) ===== */
.order-section {
  background: #172636;
  border-radius: 4px;
  padding: 6px 10px;
  flex: 0 0 auto;
  max-height: 180px;
  overflow-y: auto;
}

.trade-tabs {
  display: flex;
  gap: 6px;
}
.trade-tabs button {
  padding: 3px 8px;
  background: transparent;
  border: 1px solid #27313e;
  color: #828ea1;
  cursor: pointer;
  border-radius: 4px;
  font-size: 11px;
}
.trade-tabs button.active {
  border-color: #f0a70a;
  color: #f0a70a;
}
.leverage-control {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #0b1520;
  padding: 4px 8px;
  border-radius: 4px;
}
.leverage-control label { font-size: 11px; color: #828ea1; white-space: nowrap; }
.leverage-control input[type="range"] { flex: 1; accent-color: #f0a70a; }
.lev-val { font-size: 11px; color: #f0a70a; font-weight: bold; }

.trade-wallet {
  font-size: 11px;
  color: #828ea1;
  display: flex;
  gap: 8px;
}
.trade-error {
  grid-column: 1 / -1;
  color: #f6465d;
  font-size: 11px;
  margin: 0;
}
.trade-form {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 4px;
  align-items: stretch;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.form-row label {
  width: 40px;
  font-size: 11px;
  color: #828ea1;
  flex-shrink: 0;
}
.form-row input {
  flex: 1;
  min-width: 0;
  padding: 4px 6px;
  background: #0b1520;
  border: 1px solid #27313e;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
}
.form-row input:disabled { opacity: 0.6; }
.form-row .unit { font-size: 10px; color: #71717a; flex-shrink: 0; }

.dual-btn-row {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}
.btn-buy {
  flex: 1;
  padding: 8px 8px;
  background: #0ecb81;
  border: none;
  border-radius: 4px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-buy:hover:not(:disabled) { background: #0bb870; }
.btn-buy:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-sell {
  flex: 1;
  padding: 8px 8px;
  background: #f6465d;
  border: none;
  border-radius: 4px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-sell:hover:not(:disabled) { background: #e5354a; }
.btn-sell:disabled { opacity: 0.6; cursor: not-allowed; }

/* ===== RIGHT: Order Form Sidebar ===== */
.order-panel {
  flex: 0 0 280px;
  background: #172636;
  border-radius: 4px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
}

/* Account overview */
.account-bar {
  display: flex;
  justify-content: space-between;
  background: #0b1520;
  border-radius: 4px;
  padding: 6px 8px;
}
.account-item { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.account-label { font-size: 9px; color: #828ea1; }
.account-val { font-size: 11px; color: #e4e4e7; font-weight: 600; }

/* Sidebar row: margin mode + order type */
.sidebar-row { display: flex; gap: 6px; }
.margin-mode-toggle, .trade-tabs { display: flex; gap: 2px; flex: 1; }
.margin-mode-toggle button, .trade-tabs button {
  flex: 1; padding: 4px 0; background: #0b1520; border: 1px solid #27313e;
  color: #828ea1; cursor: pointer; border-radius: 4px; font-size: 11px; text-align: center;
}
.margin-mode-toggle button.active { border-color: #f0a70a; color: #f0a70a; background: rgba(240, 167, 10, 0.08); }
.trade-tabs button.active { border-color: #f0a70a; color: #f0a70a; background: rgba(240, 167, 10, 0.08); }

/* Leverage section */
.leverage-section {
  background: #0b1520; border-radius: 4px; padding: 6px 8px;
}
.leverage-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.leverage-label { font-size: 11px; color: #828ea1; }
.lev-val { font-size: 12px; color: #f0a70a; font-weight: bold; }
.leverage-slider { width: 100%; accent-color: #f0a70a; height: 4px; margin-bottom: 6px; }
.quick-lev-row { display: flex; gap: 3px; }
.quick-lev-row button {
  flex: 1; padding: 3px 0; background: #172636; border: 1px solid #27313e;
  color: #828ea1; cursor: pointer; border-radius: 3px; font-size: 9px; text-align: center;
}
.quick-lev-row button.active { border-color: #f0a70a; color: #f0a70a; }
.quick-lev-row button:hover { color: #e4e4e7; }

/* Quick amount row */
.quick-amt-row { display: flex; gap: 4px; }
.quick-amt-row button {
  flex: 1; padding: 3px 0; background: #0b1520; border: 1px solid #27313e;
  color: #828ea1; cursor: pointer; border-radius: 3px; font-size: 10px; text-align: center;
}
.quick-amt-row button:hover { color: #f0a70a; border-color: #f0a70a; }

/* Order info */
.order-info {
  background: #0b1520; border-radius: 4px; padding: 6px 8px; margin-top: 2px;
}
.info-line {
  display: flex; justify-content: space-between; font-size: 10px; color: #828ea1; line-height: 1.8;
}
.info-line span:last-child { color: #e4e4e7; }

/* TP/SL */
.tpsl-toggle {
  display: flex; justify-content: space-between; align-items: center;
  padding: 4px 0; cursor: pointer; font-size: 11px; color: #828ea1;
  border-top: 1px solid #27313e; margin-top: 2px;
}
.tpsl-toggle:hover { color: #e4e4e7; }
.tpsl-arrow { font-size: 8px; }
.tpsl-inputs { display: flex; flex-direction: column; gap: 4px; }
.tp-label { color: #0ecb81 !important; }
.sl-label { color: #f6465d !important; }
.order-tabs {
  display: flex;
  gap: 2px;
  margin-bottom: 8px;
  flex-shrink: 0;
}
.order-tabs button {
  padding: 4px 8px;
  background: transparent;
  border: none;
  color: #828ea1;
  cursor: pointer;
  font-size: 11px;
}
.order-tabs button.active { color: #f0a70a; }
.order-login-tip {
  padding: 20px;
  color: #828ea1;
  font-size: 12px;
}
.order-login-tip a { color: #f0a70a; text-decoration: none; }
.order-table-wrap {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
}
.order-table {
  width: 100%;
  font-size: 11px;
  border-collapse: collapse;
}
.order-table th,
.order-table td {
  padding: 4px 6px;
  text-align: left;
  border-bottom: 1px solid #27313e;
}
.order-table th { color: #71717a; font-weight: 600; }
.order-table .buy { color: #0ecb81; }
.order-table .sell { color: #f6465d; }
.btn-undo {
  padding: 2px 6px;
  background: transparent;
  border: 1px solid #f0a70a;
  color: #f0a70a;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
}
.btn-undo:hover { background: rgba(240, 167, 10, 0.15); }
.no-orders {
  padding: 20px;
  color: #71717a;
  font-size: 12px;
  margin: 0;
}

/* ===== Responsive ===== */
@media (max-width: 768px) {
  .exchange-page {
    padding: 56px 0 60px 0; /* Space for top header and bottom tab bar */
    height: auto;
    overflow: auto;
  }
  .exchange-layout {
    flex-direction: column;
    gap: 8px;
    padding: 8px 8px 80px 8px; /* Extra bottom padding for sticky buttons */
  }
  .exchange-center {
    flex: none;
    width: 100%;
    order: 1;
  }
  .chart-wrap {
    min-height: 350px;
  }
  .chart-container {
    min-height: 300px;
  }
  .plate-panel {
    flex: none;
    width: 100%;
    order: 2;
    margin: 0;
  }
  .order-panel {
    flex: none;
    width: 100%;
    order: 3;
    padding: 12px;
  }
  .order-table th,
  .order-table td {
    white-space: nowrap;
    padding: 8px 6px; /* Larger touch area */
  }
  .symbol-header {
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px;
  }
  
  /* Mobile Sticky Action Bar */
  .mobile-action-bar {
    display: flex;
    position: fixed;
    bottom: 60px; /* Above the App.vue tab bar */
    left: 0;
    right: 0;
    background: #172636;
    padding: 12px 16px;
    gap: 12px;
    border-top: 1px solid #27313e;
    box-shadow: 0 -4px 12px rgba(0,0,0,0.3);
    z-index: 240;
  }
  .mobile-action-bar button {
    flex: 1;
    height: 44px;
    font-size: 15px;
  }
}

.mobile-action-bar {
  display: none;
}

/* ===== Trade Animations ===== */
@keyframes flash-gold {
  0%   { background-color: rgba(240, 167, 10, 0.5); }
  50%  { background-color: rgba(240, 167, 10, 0.2); }
  100% { background-color: transparent; }
}

.user-trade-flash {
  animation: flash-gold 2s ease-out;
  border-left: 2px solid #f0a70a;
}

.ghost-trade {
  opacity: 0.85; /* Slightly deemphasize fake trades if desired, or keep identical */
}

.mine-star {
  color: #f0a70a;
  font-size: 10px;
  margin-left: 4px;
}

</style>
