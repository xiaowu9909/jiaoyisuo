<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import MobileInput from './MobileInput.vue'

const props = defineProps({
  FAQList: { type: Array, default: () => [] },
  filteredData: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const searchKey = defineModel('searchKey')
const router = useRouter()
const { t } = useI18n()

const supportUrl = typeof import.meta !== 'undefined' && import.meta.env?.VITE_SUPPORT_URL
  ? String(import.meta.env.VITE_SUPPORT_URL).trim()
  : ''

function formatNum(val, fixed = 2) {
  if (val === undefined || val === null) return '--'
  return Number(val).toLocaleString(undefined, { minimumFractionDigits: fixed, maximumFractionDigits: fixed })
}

function goTrade(row) {
  const pair = (row?.symbol || '').toString().replace(/\//g, '-')
  if (pair) router.push('/exchange/' + pair)
}

function openOnlineSupport() {
  if (typeof window !== 'undefined' && window.ssq && typeof window.ssq.push === 'function') {
    window.ssq.push('chatOpen')
  } else if (supportUrl) {
    window.open(supportUrl, '_blank', 'noopener,noreferrer')
  } else {
    router.push('/help')
  }
}
</script>

<template>
  <div class="mobile-home">
    <!-- Banner -->
    <div class="mobile-banner">
      <div class="banner-content">
        <h1 class="m-slogan">{{ t('common.slogan') }}</h1>
        <p class="m-subslogan">{{ t('common.subslogan') }}</p>
      </div>
      <img src="/images/bannerbg.png" alt="banner" class="m-banner-img" />
    </div>

    <!-- Announcement -->
    <div class="mobile-notice">
      <i class="icon-notice"></i>
      <div class="notice-slider">
        <template v-if="FAQList.length">
          <span class="notice-item">{{ FAQList[0].title }}</span>
        </template>
        <span v-else>{{ t('common.nodata') }}</span>
      </div>
      <i class="icon-arrow-right" @click="router.push('/help')"></i>
    </div>

    <!-- Quick Actions -->
    <div class="mobile-quick-actions">
      <div class="action-item" @click="router.push('/uc/recharge')">
        <div class="action-icon circle-orange"><i class="icon-m-recharge"></i></div>
        <span>{{ t('uc.layout.recharge') }}</span>
      </div>
      <div class="action-item" @click="router.push('/uc/withdraw')">
        <div class="action-icon circle-blue"><i class="icon-m-withdraw"></i></div>
        <span>{{ t('uc.layout.withdraw') }}</span>
      </div>
      <div class="action-item" @click="router.push('/uc?open=convert')">
        <div class="action-icon circle-green"><i class="icon-m-exchange"></i></div>
        <span>{{ t('header.trade') }}</span>
      </div>
      <div class="action-item" @click="openOnlineSupport">
        <div class="action-icon circle-purple"><i class="icon-m-help"></i></div>
        <span>{{ t('header.onlineSupport') }}</span>
      </div>
    </div>

    <!-- Market List -->
    <div class="mobile-market-section">
      <div class="section-header">
        <h3 class="section-title">{{ t('sectionPage.mainboard') }}</h3>
        <div class="search-mini">
          <MobileInput v-model="searchKey" search :placeholder="t('common.searchplaceholder')" />
        </div>
      </div>
      <div class="m-market-list">
        <div v-if="loading" class="m-loading">{{ t('common.loading') }}</div>
        <div v-else-if="!filteredData.length" class="m-nodata">{{ t('common.nodata') }}</div>
        <div v-for="row in filteredData" :key="row.symbol || row.coinSymbol + '/' + row.baseSymbol" class="m-market-row" @click="goTrade(row)">
          <div class="m-row-left">
            <span class="m-symbol">{{ row.coinSymbol || row.symbol?.split('/')[0] }}</span>
            <span class="m-base">/{{ row.baseSymbol || row.symbol?.split('/')[1] || 'USDT' }}</span>
            <span v-if="row.virtual" class="m-virtual-tag">[模拟]</span>
            <span class="m-vol">Vol {{ formatNum(row.volume, 0) }}</span>
          </div>
          <div class="m-row-right">
            <div class="m-price-box">
              <span class="m-price">{{ row.close != null ? formatNum(row.close) : '--' }}</span>
            </div>
            <div class="m-chg-box" :class="(row.chg != null && Number(row.chg) >= 0) ? 'up' : 'down'">
              {{ row.chg != null ? (Number(row.chg) >= 0 ? '+' : '') + (Number(row.chg) * 100).toFixed(2) + '%' : '--' }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mobile-home {
  background: #0b1520;
  min-height: calc(100vh - 116px);
  padding-bottom: 20px;
}
.mobile-banner {
  position: relative;
  height: 180px;
  overflow: hidden;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  display: flex;
  align-items: center;
  padding: 0 20px;
}
.banner-content {
  position: relative;
  z-index: 2;
  flex: 1;
}
.m-slogan {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 8px;
  line-height: 1.2;
}
.m-subslogan {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}
.m-banner-img {
  position: absolute;
  right: -20px;
  bottom: -20px;
  height: 140px;
  opacity: 0.6;
  z-index: 1;
}

.mobile-notice {
  background: #1e2936;
  margin: 12px 16px;
  padding: 10px 12px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #bdc2ca;
}
.icon-notice {
  width: 18px;
  height: 18px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23f0a70a'%3E%3Cpath d='M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}
.icon-arrow-right {
  width: 16px;
  height: 16px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%2364748b'%3E%3Cpath d='M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}
.notice-slider {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mobile-quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 8px 16px;
  margin-bottom: 20px;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.action-item span {
  font-size: 11px;
  color: #94a3b8;
}
.action-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.circle-orange { background: rgba(240, 167, 10, 0.15); }
.circle-blue { background: rgba(59, 130, 246, 0.15); }
.circle-green { background: rgba(34, 197, 94, 0.15); }
.circle-purple { background: rgba(168, 85, 247, 0.15); }

.action-icon i {
  width: 24px;
  height: 24px;
  background-size: contain;
  background-repeat: no-repeat;
}
.icon-m-recharge { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23f0a70a'%3E%3Cpath d='M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z'/%3E%3C/svg%3E"); }
.icon-m-withdraw { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%233b82f6'%3E%3Cpath d='M5 19h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z'/%3E%3C/svg%3E"); }
.icon-m-exchange { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%2322c55e'%3E%3Cpath d='M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z'/%3E%3C/svg%3E"); }
.icon-m-help { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23a855f7'%3E%3Cpath d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z'/%3E%3C/svg%3E"); }

.mobile-market-section {
  padding: 0 16px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.search-mini {
  width: 140px;
}

.m-market-list {
  background: #1e2936;
  border-radius: 12px;
  overflow: hidden;
}
.m-market-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #27313e;
}
.m-market-row:last-child { border-bottom: none; }
.m-market-row:active { background: #27313e; }

.m-row-left {
  display: flex;
  flex-direction: column;
}
.m-symbol {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}
.m-base {
  font-size: 11px;
  color: #64748b;
}
.m-virtual-tag {
  font-size: 11px;
  color: #94a3b8;
  margin-left: 4px;
}
.m-vol {
  font-size: 10px;
  color: #475569;
  margin-top: 2px;
}

.m-row-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.m-price-box {
  text-align: right;
  min-width: 80px;
}
.m-price {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}
.m-chg-box {
  min-width: 68px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}
.m-chg-box.up { background: #22c55e; }
.m-chg-box.down { background: #ef4444; }

.m-loading, .m-nodata {
  padding: 40px;
  text-align: center;
  color: #64748b;
  font-size: 13px;
}
</style>
