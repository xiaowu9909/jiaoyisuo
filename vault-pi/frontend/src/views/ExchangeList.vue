<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getMarketSymbolThumb } from '../api'
import { useDevice } from '../hooks/useDevice'
import MobileInput from '../components/mobile/MobileInput.vue'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

const router = useRouter()
const { t } = useI18n()
const { isMobile } = useDevice()

const loading = ref(true)
const list = ref([])
const searchKey = ref('')

const filteredList = computed(() => {
  if (!searchKey.value) return list.value
  const key = searchKey.value.toLowerCase()
  return list.value.filter(
    (item) =>
      (item.symbol && item.symbol.toLowerCase().includes(key)) ||
      (item.coinSymbol && item.coinSymbol.toLowerCase().includes(key)) ||
      (item.baseSymbol && item.baseSymbol.toLowerCase().includes(key))
  )
})

function goTrade(row) {
  const pair = (row.symbol || '').replace(/\//g, '-')
  if (pair) router.push('/exchange/' + pair)
}

function formatNum(val, fixed = 2) {
  if (val === undefined || val === null) return '--'
  return Number(val).toLocaleString(undefined, { minimumFractionDigits: fixed, maximumFractionDigits: fixed })
}

async function fetchThumbs() {
  try {
    const data = await getMarketSymbolThumb()
    list.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (list.value.length === 0) console.error('Failed to load symbols:', e)
  }
}

let refreshTimer = null
let marketThumbStompClient = null

function getMarketThumbWsUrl() {
  const base = import.meta.env?.VITE_API_BASE || '/api'
  const origin = typeof base === 'string' && base.startsWith('http') ? base.replace(/\/api\/?$/, '') : window.location.origin
  return origin + '/ws/virtual-market'
}

function connectMarketThumbWs() {
  closeMarketThumbWs()
  const url = getMarketThumbWsUrl()
  const client = new Client({
    webSocketFactory: () => new SockJS(url),
    reconnectDelay: 3000,
    heartbeatIncoming: 8000,
    heartbeatOutgoing: 8000,
  })
  client.onConnect = () => {
    client.subscribe('/topic/market-thumb', (frame) => {
      try {
        const payload = JSON.parse(frame.body)
        const thumbs = payload?.thumbs
        if (Array.isArray(thumbs) && thumbs.length > 0) list.value = thumbs
      } catch (_) {}
    })
  }
  client.activate()
  marketThumbStompClient = client
}

function closeMarketThumbWs() {
  if (!marketThumbStompClient) return
  try {
    marketThumbStompClient.deactivate()
  } catch (_) {}
  marketThumbStompClient = null
}

onMounted(async () => {
  loading.value = true
  await fetchThumbs()
  loading.value = false
  connectMarketThumbWs()
  refreshTimer = setInterval(fetchThumbs, 15000)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  closeMarketThumbWs()
})
</script>

<template>
  <div class="exchange-list-page" :class="{ 'mobile-view': isMobile }">
    <!-- 移动端 -->
    <template v-if="isMobile">
      <div class="m-exlist">
        <header class="m-exlist-header">
          <h1 class="m-exlist-title">{{ t('exchange.mainboard') }}</h1>
          <p class="m-exlist-desc">{{ t('sectionPage.baseexchangetips') }}</p>
        </header>
        <div class="m-exlist-search">
          <MobileInput v-model="searchKey" search :placeholder="t('common.searchplaceholder')" />
        </div>
        <div class="m-exlist-body">
          <div v-if="loading" class="m-loading">{{ t('common.loading') }}</div>
          <div v-else-if="!filteredList.length" class="m-nodata">{{ t('common.nodata') }}</div>
          <div
            v-else
            v-for="row in filteredList"
            :key="row.symbol"
            class="m-market-row"
            @click="goTrade(row)"
          >
            <div class="m-row-left">
              <span class="m-symbol">{{ row.coinSymbol || row.symbol?.split('/')[0] }}</span>
              <span class="m-base">/{{ row.baseSymbol || row.symbol?.split('/')[1] || 'USDT' }}</span>
              <span v-if="row.virtual" class="m-virtual-tag">[模拟]</span>
              <span class="m-vol">Vol {{ formatNum(row.volume, 0) }}</span>
            </div>
            <div class="m-row-right">
              <span class="m-price">{{ row.close != null ? formatNum(row.close) : '--' }}</span>
              <span class="m-chg-box" :class="Number(row.chg) >= 0 ? 'up' : 'down'">
                {{ row.chg != null ? (Number(row.chg) >= 0 ? '+' : '') + (Number(row.chg) * 100).toFixed(2) + '%' : '--' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 桌面端 -->
    <template v-else>
      <div class="exlist-wrap">
        <div class="exlist-header">
          <h1>{{ t('exchange.mainboard') }}</h1>
          <p class="exlist-desc">{{ t('sectionPage.baseexchangetips') }}</p>
          <div class="exlist-search">
            <input v-model="searchKey" type="text" :placeholder="t('common.searchplaceholder')" class="exlist-input" />
          </div>
        </div>
        <div class="exlist-table-wrap">
          <div v-if="loading" class="exlist-loading">{{ t('common.loading') }}</div>
          <table v-else class="exlist-table">
            <thead>
              <tr>
                <th>{{ t('service.symbol') }}</th>
                <th>{{ t('service.NewPrice') }}</th>
                <th>{{ t('service.Change') }}</th>
                <th>{{ t('service.ExchangeNum') }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredList.length">
                <td colspan="5" class="exlist-empty">{{ t('common.nodata') }}</td>
              </tr>
              <tr v-for="row in filteredList" :key="row.symbol" class="exlist-row" @click="goTrade(row)">
                <td>
                  <span class="exlist-symbol">{{ row.coinSymbol || row.symbol?.split('/')[0] }}</span>
                  <span class="exlist-base">/{{ row.baseSymbol || row.symbol?.split('/')[1] || 'USDT' }}</span>
                  <span v-if="row.virtual" class="exlist-virtual-tag">[模拟]</span>
                </td>
                <td>{{ row.close != null ? formatNum(row.close) : '--' }}</td>
                <td :class="Number(row.chg) >= 0 ? 'up' : 'down'">
                  {{ row.chg != null ? (Number(row.chg) >= 0 ? '+' : '') + (Number(row.chg) * 100).toFixed(2) + '%' : '--' }}
                </td>
                <td>{{ formatNum(row.volume, 0) }}</td>
                <td>
                  <router-link :to="'/exchange/' + (row.symbol || '').replace(/\//g, '-')" class="exlist-link" @click.prevent="goTrade(row)">
                    {{ t('service.Exchange') }}
                  </router-link>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.exchange-list-page {
  min-height: 100vh;
  background: #0b1520;
}

/* ========== 移动端 ========== */
.m-exlist {
  padding: 16px;
  padding-top: env(safe-area-inset-top);
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}
.m-exlist-header { margin-bottom: 16px; }
.m-exlist-title {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 6px;
}
.m-exlist-desc {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}
.m-exlist-search { margin-bottom: 16px; }
.m-exlist-body { background: #0f172a; border-radius: 12px; overflow: hidden; border: 1px solid #1e293b; max-height: none; }
.m-loading, .m-nodata {
  text-align: center;
  padding: 40px 16px;
  color: #64748b;
  font-size: 14px;
}
.m-market-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #1e293b;
  cursor: pointer;
  transition: background 0.15s;
}
.m-market-row:last-child { border-bottom: none; }
.m-market-row:active { background: #1e293b; }
.m-row-left { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.m-symbol { font-weight: 700; font-size: 16px; color: #fff; }
.m-base { font-size: 14px; color: #64748b; }
.m-virtual-tag { font-size: 11px; color: #94a3b8; margin-left: 4px; }
.m-vol { font-size: 12px; color: #64748b; }
.m-row-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.m-price { font-size: 15px; font-weight: 600; font-family: 'Roboto Mono', monospace; color: #fff; }
.m-chg-box { font-size: 13px; font-weight: 600; }
.m-chg-box.up { color: #0ecb81; }
.m-chg-box.down { color: #f6465d; }

/* ========== 桌面端 ========== */
.exlist-wrap {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px;
}
.exlist-header { margin-bottom: 24px; }
.exlist-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 8px;
}
.exlist-desc { font-size: 14px; color: #94a3b8; margin: 0 0 16px; }
.exlist-search { max-width: 320px; }
.exlist-input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  box-sizing: border-box;
}
.exlist-input::placeholder { color: #64748b; }
.exlist-input:focus { outline: none; border-color: #3b82f6; }
.exlist-table-wrap { background: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; }
.exlist-loading { text-align: center; padding: 48px; color: #64748b; }
.exlist-table { width: 100%; border-collapse: collapse; }
.exlist-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: #172636;
  border-bottom: 1px solid #1e293b;
}
.exlist-table td {
  padding: 14px 16px;
  font-size: 14px;
  color: #e2e8f0;
  border-bottom: 1px solid #1e293b;
}
.exlist-table tbody tr:last-child td { border-bottom: none; }
.exlist-table tbody tr { cursor: pointer; transition: background 0.15s; }
.exlist-table tbody tr:hover { background: #1e293b; }
.exlist-empty { text-align: center; color: #64748b; padding: 40px !important; }
.exlist-symbol { font-weight: 700; color: #fff; }
.exlist-base { color: #64748b; margin-left: 2px; }
.exlist-virtual-tag { font-size: 12px; color: #94a3b8; margin-left: 6px; }
.exlist-table td.up { color: #0ecb81; font-weight: 600; }
.exlist-table td.down { color: #f6465d; font-weight: 600; }
.exlist-link {
  color: #3b82f6;
  font-weight: 600;
  text-decoration: none;
}
.exlist-link:hover { text-decoration: underline; }
</style>
