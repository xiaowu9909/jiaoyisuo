<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { getAnnouncementPage, getMarketSymbolThumb, getHomeGettingStartConfig, getHomeAboutBrandConfig, getHomeAppDownloadConfig } from '../api'
import { useDevice } from '../hooks/useDevice'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import MobileHomeView from '../components/mobile/MobileHomeView.vue'

const { t, locale } = useI18n()
const app = useAppStore()
const router = useRouter()
const { isMobile } = useDevice()

const FAQList = ref([])
/** 首页「新手入门」区块配置（来自 B 端），无则用 i18n 兜底 */
const gettingStartConfig = ref(null)
/** 首页「关于 Vault π」区块配置（来自 B 端），无则用 i18n 兜底 */
const aboutBrandConfig = ref(null)
/** 首页「扫描二维码，下载APP」区块配置（来自 B 端），无则用 i18n 兜底 */
const appDownloadConfig = ref(null)
const loading = ref(false)
const searchKey = ref('')
const dataIndex = ref([])
/** 主板交易对元信息（来自首次接口） */
const symbolMeta = ref([])
let timer = null
let marketThumbStompClient = null

const langPram = computed(() => (app.lang === 'en' ? 'EN' : 'CN'))

const FAVORITE_STORAGE_KEY = 'FAVORITE_SYMBOLS'

function loadFavorites() {
  try {
    const raw = localStorage.getItem(FAVORITE_STORAGE_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(arr) ? arr : [])
  } catch (_) {
    return new Set()
  }
}

const favoriteSymbols = ref(loadFavorites())

function toggleFavorite(symbol) {
  if (!symbol) return
  const set = new Set(favoriteSymbols.value)
  if (set.has(symbol)) {
    set.delete(symbol)
  } else {
    set.add(symbol)
  }
  favoriteSymbols.value = set
  try {
    localStorage.setItem(FAVORITE_STORAGE_KEY, JSON.stringify([...set]))
  } catch (_) {}
}

function isFavorite(symbol) {
  return favoriteSymbols.value.has(symbol)
}

// Filtered data based on search input; favorites first (desktop 主板表格用)
const filteredData = computed(() => {
  let list = dataIndex.value
  if (searchKey.value) {
    const key = searchKey.value.toLowerCase()
    list = list.filter(
      (item) =>
        item.symbol.toLowerCase().includes(key) ||
        item.coinSymbol.toLowerCase().includes(key)
    )
  }
  return [...list].sort((a, b) => {
    const fa = isFavorite(a.symbol)
    const fb = isFavorite(b.symbol)
    if (fa && !fb) return -1
    if (!fa && fb) return 1
    return 0
  })
})

// 移动端首页 Main Board：ZRX/USDT + 6 个热门（按成交量），可再按搜索过滤
const PINNED_SYMBOL = 'ZRX/USDT'
const HOT_COUNT = 6

const mobileMainBoardData = computed(() => {
  const full = dataIndex.value
  const zrx = full.find((item) => (item.symbol || '').toUpperCase() === PINNED_SYMBOL)
  const rest = full.filter((item) => (item.symbol || '').toUpperCase() !== PINNED_SYMBOL)
  const byVolume = [...rest].sort((a, b) => (Number(b.volume) || 0) - (Number(a.volume) || 0))
  const hot = byVolume.slice(0, HOT_COUNT)
  let list = [...(zrx ? [zrx] : []), ...hot]
  if (searchKey.value) {
    const key = searchKey.value.toLowerCase()
    list = list.filter(
      (item) =>
        (item.symbol && item.symbol.toLowerCase().includes(key)) ||
        (item.coinSymbol && item.coinSymbol.toLowerCase().includes(key)) ||
        (item.baseSymbol && item.baseSymbol.toLowerCase().includes(key))
    )
  }
  return list
})

const fallbackItemKeys = [
  { nameKey: 'sectionPage.oneminutebuy', tipsKey: 'sectionPage.oneminutebuytips' },
  { nameKey: 'sectionPage.baseexchange', tipsKey: 'sectionPage.baseexchangetips' },
  { nameKey: 'sectionPage.baseknow', tipsKey: 'sectionPage.baseknowtips' },
  { nameKey: 'sectionPage.usersocial', tipsKey: 'sectionPage.usersocialtips' },
]

/** 是否包含中文（后端未翻译成功时英文界面用 i18n 兜底） */
function hasChinese(str) {
  if (typeof str !== 'string' || !str.trim()) return false
  return /[\u4e00-\u9fff]/.test(str)
}

// 与语言切换一致：用 store.lang 请求配置，英文时后端自动翻译
const configLang = computed(() => (app.lang === 'en' ? 'en' : ''))
const isEn = computed(() => app.lang === 'en')

const gettingStartItems = computed(() =>
  fallbackItemKeys.map((f, i) => {
    const item = gettingStartConfig.value?.items?.[i]
    const name = item?.name ?? t(f.nameKey)
    const tips = item?.tips ?? t(f.tipsKey)
    return {
      name: hasChinese(name) ? t(f.nameKey) : name,
      tips: hasChinese(tips) ? t(f.tipsKey) : tips,
      imageUrl: item?.imageUrl ?? '',
    }
  })
)
const gettingStartTitle = computed(() => {
  const raw = gettingStartConfig.value?.title ?? t('sectionPage.gettingstart')
  return hasChinese(raw) ? t('sectionPage.gettingstart') : raw
})
const gettingStartSubtitle = computed(() => {
  const raw = gettingStartConfig.value?.subtitle ?? t('sectionPage.officialstart')
  return hasChinese(raw) ? t('sectionPage.officialstart') : raw
})

/** 关于我们：有中文时用 i18n 兜底 */
const aboutBrandTitle = computed(() => {
  const raw = aboutBrandConfig.value?.title ?? t('sectionPage.brandTitle')
  if (hasChinese(raw) || (raw && raw.includes('BIZZAN'))) return t('sectionPage.brandTitle')
  return raw
})
// 固定用 i18n 副标题，避免后端只返回 "Fair" 等残缺翻译
const aboutBrandDetail = computed(() => {
  return t('sectionPage.brandDetail')
})
const aboutBrandDesc1 = computed(() => {
  const raw = aboutBrandConfig.value?.desc1 ?? t('sectionPage.brandDesc1')
  if (hasChinese(raw) || (raw && raw.includes('BIZZAN'))) return t('sectionPage.brandDesc1')
  return raw
})
const aboutBrandDesc2 = computed(() => {
  const raw = aboutBrandConfig.value?.desc2 ?? t('sectionPage.brandDesc2')
  if (hasChinese(raw) || (raw && raw.includes('BIZZAN'))) return t('sectionPage.brandDesc2')
  return raw
})

/** 下载APP：有中文时用 i18n 兜底 */
const appDownloadScanText = computed(() => {
  const raw = appDownloadConfig.value?.scanText ?? t('description.scanqrcode')
  return hasChinese(raw) ? t('description.scanqrcode') : raw
})
const appDownloadDownloadText = computed(() => {
  const raw = appDownloadConfig.value?.downloadText ?? t('cms.download')
  return hasChinese(raw) ? t('cms.download') : raw
})
function defaultCardImageUrl(idx) {
  if (idx === 0) return langPram.value === 'EN' ? '/images/new_1usd.png' : '/images/new_1cny.png'
  const urls = ['/images/new_3.png', '/images/new_2.png', '/images/new_4.png']
  return urls[idx - 1] || '/images/new_4.png'
}

async function fetchMarketData() {
  try {
    const data = await getMarketSymbolThumb()
    const list = Array.isArray(data) ? data : []
    dataIndex.value = list
    symbolMeta.value = list.map((d) => ({ symbol: d.symbol, baseSymbol: d.baseSymbol, coinSymbol: d.coinSymbol }))
  } catch (e) {
    console.error('Failed to fetch market data:', e)
  }
}

/** 全市场 ticker：订阅后端 /topic/market-thumb 推送（已弃用 3s 轮询） */
function getMarketThumbWsUrl() {
  const base = import.meta.env?.VITE_API_BASE || '/api'
  const origin = typeof base === 'string' && base.startsWith('http') ? base.replace(/\/api\/?$/, '') : window.location.origin
  return origin + '/ws/virtual-market'
}

function connectTickerArrWs() {
  closeTickerArrWs()
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
        const list = payload?.thumbs
        if (Array.isArray(list) && list.length > 0) {
          dataIndex.value = list
          symbolMeta.value = list.map((d) => ({ symbol: d.symbol, baseSymbol: d.baseSymbol, coinSymbol: d.coinSymbol }))
        }
      } catch (_) {}
    })
  }
  client.activate()
  marketThumbStompClient = client
}

function closeTickerArrWs() {
  if (marketThumbStompClient) {
    try {
      marketThumbStompClient.deactivate()
    } catch (_) {}
    marketThumbStompClient = null
  }
}

async function fetchHomeConfigs() {
  gettingStartConfig.value = await getHomeGettingStartConfig(configLang.value)
  aboutBrandConfig.value = await getHomeAboutBrandConfig(configLang.value)
  appDownloadConfig.value = await getHomeAppDownloadConfig(configLang.value)
}

async function fetchAnnouncements() {
  try {
    const annData = await getAnnouncementPage({ pageNo: 1, pageSize: 6, lang: langPram.value })
    const content = annData?.content || []
    FAQList.value = Array.isArray(content) ? content : []
  } catch (_) {
    FAQList.value = []
  }
}

watch(() => app.lang, () => {
  fetchAnnouncements()
  fetchHomeConfigs()
})

onMounted(async () => {
  loading.value = true
  try {
    await fetchAnnouncements()
  } catch (_) {
    FAQList.value = []
  }
  try {
    await fetchHomeConfigs()
  } catch (_) {}
  try {
    await fetchMarketData()
  } catch (_) {}
  connectTickerArrWs()
  loading.value = false
})

onUnmounted(() => {
  closeTickerArrWs()
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

</script>

<template>
  <div class="fullpage">
    <!-- MOBILE VIEW -->
    <template v-if="isMobile">
      <MobileHomeView
        v-model:searchKey="searchKey"
        :FAQList="FAQList"
        :filteredData="mobileMainBoardData"
        :loading="loading"
      />
    </template>

    <!-- DESKTOP VIEW -->
    <template v-else>
      <div id="pagetips" class="topnav_wrap">
        <div class="topnav">
          <div class="carl">
            <div class="notice-scroll-wrap">
              <div v-if="!FAQList.length && !loading" class="notice-scroll-inner no_notice">{{ t('common.nodata') }}</div>
              <div v-else class="notice-scroll-inner">
                <template v-for="(item, index) in [...FAQList, ...FAQList]" :key="index">
                  <span class="notice-txt">{{ item.title }}</span>
                  <span class="notice-sep"> · </span>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="section banner-panel" id="page1">
        <img src="/images/bannerbg.png" alt="banner" class="banner_img" />
        <p class="banner_slogan">{{ t('common.slogan') }}</p>
        <p class="banner_subslogan">{{ t('common.subslogan') }}</p>
      </div>

      <div id="pagetips" class="agent_wrap">
        <div class="agent-panel">
          <div class="title">
            <div class="gettingstart">{{ gettingStartTitle }}</div>
            <div class="tips">{{ gettingStartSubtitle }}</div>
          </div>
          <div class="agent-list">
            <div v-for="(item, idx) in gettingStartItems" :key="idx" class="agent-item">
              <div class="agent-img">
                <img :src="(item.imageUrl || '').trim() || defaultCardImageUrl(idx)" alt="" />
              </div>
              <div class="agent-detail">
                <p class="agent-name">{{ item.name }}</p>
                <p class="agent-count">{{ item.tips }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="section page2" id="page2">
        <div class="page2nav">
          <ul class="brclearfix">
            <li class="active">{{ t('sectionPage.mainboard') }}</li>
            <li style="float: right; padding-right: 6px;">
              <input
                v-model="searchKey"
                type="text"
                class="search_input"
                :placeholder="t('common.searchplaceholder')"
              />
            </li>
          </ul>
        </div>
        <div class="ptjy">
          <table class="tables">
            <thead>
              <tr>
                <th>{{ t('service.favor') }}</th>
                <th>{{ t('service.COIN') }}</th>
                <th>{{ t('service.symbol') }}</th>
                <th>{{ t('service.NewPrice') }}</th>
                <th>{{ t('service.ExchangeNum') }}</th>
                <th>{{ t('service.Change') }}</th>
                <th>{{ t('service.Operate') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="7" class="nodata">{{ t('common.loading') }}</td>
              </tr>
              <tr v-else-if="!dataIndex.length">
                <td colspan="7" class="nodata">{{ t('common.nodata') }}</td>
              </tr>
              <tr v-for="row in filteredData" :key="row.symbol">
                <td>
                  <span
                    class="star"
                    :class="{ 'star-active': isFavorite(row.symbol) }"
                    :title="isFavorite(row.symbol) ? t('service.favorRemove') : t('service.favorAdd')"
                    role="button"
                    tabindex="0"
                    @click="toggleFavorite(row.symbol)"
                    @keydown.enter="toggleFavorite(row.symbol)"
                  >{{ isFavorite(row.symbol) ? '★' : '☆' }}</span>
                </td>
                <td>{{ row.baseSymbol }}</td>
                <td>{{ row.symbol }}<span v-if="row.virtual" class="virtual-tag"> [模拟]</span></td>
                <td>{{ row.close }}</td>
                <td>{{ row.volume }}</td>
                <td :class="row.chg != null && Number(row.chg) >= 0 ? 'green' : 'red'">{{ row.chg != null ? (Number(row.chg) * 100).toFixed(2) + '%' : '-' }}</td>
                <td><router-link :to="'/exchange/' + row.symbol.replace('/', '-')" class="link_trade">{{ t('service.Exchange') }}</router-link></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="section bg-light page6" id="page6">
        <p class="title">{{ aboutBrandTitle }}</p>
        <p class="subtitle">{{ aboutBrandDetail }}</p>
        <div class="detail">{{ aboutBrandDesc1 }}</div>
        <div class="detail">{{ aboutBrandDesc2 }}</div>
      </div>

      <div class="section page4" id="page4">
        <ul>
          <li>
            <div><img src="/images/feature_safe.png" alt="" /></div>
            <p class="title">{{ t('description.title1') }}</p>
            <p>{{ t('description.message1') }}</p>
          </li>
          <li>
            <div><img src="/images/feature_fast.png" alt="" /></div>
            <p class="title">{{ t('description.title2') }}</p>
            <p>{{ t('description.message2') }}</p>
          </li>
          <li>
            <div><img src="/images/feature_global.png" alt="" /></div>
            <p class="title">{{ t('description.title3') }}</p>
            <p>{{ t('description.message3') }}</p>
          </li>
          <li>
            <div><img src="/images/feature_choose.png" alt="" /></div>
            <p class="title">{{ t('description.title4') }}</p>
            <p>{{ t('description.message4') }}</p>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>

<style scoped>
.fullpage {
  padding-top: 0;
}
#pagetips.topnav_wrap {
  border-bottom: 1px solid rgba(148, 163, 184, 0.3);
  background: radial-gradient(circle at top, #1f2937 0, #020617 55%);
  padding: 0;
  overflow: hidden;
}
.topnav {
  width: 100%;
  line-height: 1;
  margin: 0 auto;
}
.carl {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: stretch;
}
.notice-scroll-wrap {
  width: 100%;
  height: 44px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 10%;
  box-sizing: border-box;
  mask-image: linear-gradient(to right, transparent 0, black 8%, black 92%, transparent 100%);
  -webkit-mask-image: linear-gradient(to right, transparent 0, black 8%, black 92%, transparent 100%);
  mask-size: 100% 100%;
  -webkit-mask-size: 100% 100%;
}
.notice-scroll-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  animation: notice-scroll 30s linear infinite;
  white-space: nowrap;
  padding-right: 24px;
}
.notice-scroll-inner.no_notice {
  animation: none;
  color: #666;
  font-size: 12px;
  justify-content: flex-start;
}
.notice-txt {
  color: rgba(130, 142, 161, 1);
  font-size: 12px;
  flex-shrink: 0;
}
.notice-sep {
  color: rgba(130, 142, 161, 0.6);
  font-size: 12px;
  flex-shrink: 0;
}
@keyframes notice-scroll {
  0% { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}
.banner-panel {
  height: 380px;
  background: radial-gradient(circle at top, #1f2937 0, #020617 60%);
  overflow: hidden;
  position: relative;
}
.banner_img {
  height: 100%;
  width: 100%;
  object-fit: cover;
}
.banner_slogan {
  text-align: center;
  font-size: 40px;
  color: #fff;
  position: absolute;
  top: 70px;
  width: 100%;
  letter-spacing: 5px;
  text-shadow: 0 0 10px #000;
  margin: 0;
}
.banner_subslogan {
  text-align: center;
  font-size: 20px;
  color: #828ea1;
  position: absolute;
  top: 130px;
  width: 100%;
  letter-spacing: 2px;
  margin: 0;
}
#pagetips.agent_wrap {
  background: transparent;
}
.agent-panel {
  display: flex;
  flex-direction: row;
  overflow: hidden;
  position: relative;
  justify-content: center;
  min-width: 320px;
  padding: 16px 10%;
  flex-wrap: wrap;
  gap: 12px;
}
.agent-panel .title {
  margin-right: 10px;
  width: 220px;
  padding: 10px 0;
  border-right: 1px solid rgb(28, 44, 72);
  letter-spacing: 3px;
}
.gettingstart {
  color: #fff;
  font-size: 14px;
}
.tips {
  font-size: 10px;
  color: #869ec9;
  letter-spacing: 2px;
  margin-top: 5px;
}
.agent-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.agent-item {
  height: 54px;
  background: rgba(15, 23, 42, 0.85);
  width: 210px;
  padding: 0 15px 0 0;
  border: 1px solid #151f2c;
  transition: all 0.5s;
  display: flex;
  align-items: center;
  cursor: pointer;
}
.agent-item:hover {
  border-color: rgb(240, 185, 11);
}
.agent-img {
  padding: 7px;
  margin-left: 7px;
}
.agent-img img {
  height: 40px;
  width: 40px;
  border-radius: 40px;
  display: block;
}
.agent-detail {
  padding: 10px 0 0 10px;
  max-width: 130px;
}
.agent-name {
  font-size: 13px;
  color: #ffa800;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-count {
  font-size: 10px;
  color: rgb(103, 122, 153);
  margin-top: 5px;
  margin: 4px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.page2 {
  background: transparent;
  min-height: 320px;
  padding: 40px 14%;
}
.page2nav {
  line-height: 50px;
  font-size: 20px;
  background: #1e2834;
  min-width: 320px;
}
.brclearfix {
  width: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.brclearfix li {
  cursor: pointer;
  color: #fff;
  background: #1e2834;
  font-size: 16px;
  padding: 5px 24px;
}
.brclearfix li:hover {
  background: #222b38;
}
.brclearfix li.active {
  background: #27313e;
  color: #f0a70a;
  border-bottom: 2px solid #f0a70a;
}
.search_input {
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid #27313e;
  background: #141e2c;
  color: #fff;
  font-size: 14px;
  min-width: 160px;
}
.search_input::placeholder {
  color: #666;
}
.ptjy {
  min-width: 320px;
  background-color: rgba(15, 23, 42, 0.9);
  border: 1px solid #27313e;
  border-top: none;
}
.tables {
  width: 100%;
  border-collapse: collapse;
  background: #141e2c;
}
.tables th {
  background: #27313e;
  color: #888;
  padding: 10px 8px;
  text-align: left;
  font-weight: normal;
  border-bottom: 1px solid #27313e;
}
.tables td {
  padding: 8px;
  border-bottom: 1px solid #26303d;
  color: #fff;
}
.tables td.nodata {
  text-align: center;
  color: #666;
  padding: 40px;
}
.tables .green {
  color: #00b275;
}
.tables .red {
  color: #f15057;
}
.star {
  cursor: pointer;
  user-select: none;
  font-size: 1.2em;
  color: #666;
  display: inline-block;
  padding: 2px 4px;
  border-radius: 2px;
}
.star:hover {
  color: #b8860b;
}
.star-active {
  color: #f0a70a;
}
.star-active:hover {
  color: #ffc107;
}
.link_trade {
  color: #f0a70a;
  text-decoration: none;
}
.link_trade:hover {
  text-decoration: underline;
}
.virtual-tag {
  font-size: 12px;
  color: #94a3b8;
}
.page6 {
  min-height: 360px;
  padding: 80px 14%;
  background: radial-gradient(circle at top, #0b1120 0, #020617 60%);
}
.page6 .title {
  font-size: 30px;
  text-align: center;
  width: 100%;
  letter-spacing: 6px;
  margin: 0 0 12px;
}
.page6 .subtitle {
  margin-bottom: 40px;
  color: #828ea1;
  font-size: 13px;
  text-align: center;
  width: 100%;
  margin: 0 0 40px;
}
.page6 .detail {
  line-height: 40px;
  letter-spacing: 2px;
  text-indent: 45px;
  font-size: 16px;
  margin-bottom: 20px;
  color: #828ea1;
  text-align: justify;
}
.page4 {
  background: transparent;
  padding: 80px 0;
}
.page4 ul {
  width: 88%;
  margin: 0 auto;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 24px;
}
.page4 ul li {
  flex: 0 0 22%;
  min-width: 200px;
  padding: 0 15px;
  text-align: center;
}
.page4 ul li div {
  width: 130px;
  height: 130px;
  border-radius: 50%;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1e2834;
}
.page4 ul li div img {
  height: 80px;
  width: auto;
}
.page4 ul li p {
  font-size: 14px;
  margin: 12px 0;
  color: #828ea1;
}
.page4 ul li p.title {
  color: #fff;
  font-size: 18px;
  font-weight: 400;
}
.page5 {
  height: 320px;
  padding: 0 14%;
  position: relative;
  background: #192330 url(/images/app-download.jpg) no-repeat 0 0;
  background-size: cover;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
.phone_image {
  display: none;
}
.page5 .download {
  list-style: none;
  padding: 0;
  margin: 0;
}
.page5 .qrcode {
  color: #fff;
  font-size: 18px;
  font-weight: 500;
  margin-bottom: 14px;
  font-weight: 700;
}
.page5 .wrapper {
  width: 190px;
}
.download_app img {
  width: 100%;
  border-radius: 5px;
}
.section {
  box-sizing: border-box;
}


@media (max-width: 768px) {
  /* Simplified desktop section cleanup for better mobile-only view */
  .topnav_wrap, .banner-panel, .agent_wrap, .page2, .page6, .page4 {
    display: none !important;
  }
}
</style>
