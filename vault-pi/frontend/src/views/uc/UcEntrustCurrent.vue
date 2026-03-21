<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getFuturesOrderCurrent, postFuturesOrderCancel, getOrderCurrent, postOrderCancel } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'

const { t } = useI18n()
const activeTab = ref('spot') // 'spot' | 'futures'

const spotList = ref([])
const futuresList = ref([])
const loading = ref(true)
const errorMsg = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [spot, futures] = await Promise.all([
      getOrderCurrent().catch(() => []),
      getFuturesOrderCurrent().catch(() => []),
    ])
    spotList.value = spot || []
    futuresList.value = futures || []
  } catch (e) {
    errorMsg.value = e.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function cancelSpot(orderId) {
  try {
    await postOrderCancel(orderId)
    await load()
  } catch (e) {
    errorMsg.value = e.message || t('exchange.errCancelFailed')
  }
}

async function cancelFutures(orderId) {
  try {
    await postFuturesOrderCancel(orderId)
    await load()
  } catch (e) {
    errorMsg.value = e.message || t('exchange.errCancelFailed')
  }
}

function formatTime(str) {
  if (!str) return '-'
  try {
    return new Date(str).toLocaleString()
  } catch (_) {
    return str
  }
}

function directionText(o) {
  if (o.direction === 'LONG') {
    const lv = o.leverage != null ? ` ${o.leverage}x` : ''
    return `${t('exchange.long')}${lv}`
  }
  if (o.direction === 'SHORT') {
    const lv = o.leverage != null ? ` ${o.leverage}x` : ''
    return `${t('exchange.short')}${lv}`
  }
  return o.direction === 'BUY' ? t('uc.entrustCurrent.buy') : t('uc.entrustCurrent.sell')
}

function directionClass(o) {
  return o.direction === 'LONG' || o.direction === 'BUY' ? 'dir-long' : 'dir-short'
}

const spotCount = computed(() => spotList.value.length)
const futuresCount = computed(() => futuresList.value.length)

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.entrustCurrent.title') }}</h2>
    <p v-if="errorMsg" class="uc-error">{{ errorMsg }}</p>

    <!-- Tab 切换 -->
    <div class="tab-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'spot' }"
        @click="activeTab = 'spot'"
      >
        {{ t('uc.entrustCurrent.spot') || '现货' }}
        <span v-if="spotCount > 0" class="tab-count">{{ spotCount }}</span>
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'futures' }"
        @click="activeTab = 'futures'"
      >
        {{ t('uc.entrustCurrent.futures') || '合约' }}
        <span v-if="futuresCount > 0" class="tab-count">{{ futuresCount }}</span>
      </button>
    </div>

    <div v-if="loading" class="uc-loading">{{ t('common.loading') }}</div>
    <div v-else class="uc-table-wrap">

      <!-- 现货委托 -->
      <template v-if="activeTab === 'spot'">
        <table class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.entrustCurrent.time') }}</th>
              <th>{{ t('uc.entrustCurrent.symbol') }}</th>
              <th>{{ t('uc.entrustCurrent.direction') }}</th>
              <th>{{ t('uc.entrustCurrent.type') || '类型' }}</th>
              <th>{{ t('uc.entrustCurrent.price') }}</th>
              <th>{{ t('uc.entrustCurrent.amount') }}</th>
              <th>{{ t('uc.entrustCurrent.action') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in spotList" :key="o.orderId">
              <td>{{ formatTime(o.createTime) }}</td>
              <td>{{ o.symbol || '-' }}</td>
              <td :class="directionClass(o)">{{ directionText(o) }}</td>
              <td>{{ o.type === 'LIMIT_PRICE' ? (t('exchange.limitPrice') || '限价') : (t('exchange.marketPrice') || '市价') }}</td>
              <td>{{ o.price }}</td>
              <td>{{ o.amount }}</td>
              <td>
                <MobileButton variant="danger" @click="cancelSpot(o.orderId)">{{ t('uc.entrustCurrent.cancel') }}</MobileButton>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!spotList.length" class="no-data">{{ t('common.nodata') }}</p>
      </template>

      <!-- 合约委托 -->
      <template v-if="activeTab === 'futures'">
        <table class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.entrustCurrent.time') }}</th>
              <th>{{ t('uc.entrustCurrent.symbol') }}</th>
              <th>{{ t('uc.entrustCurrent.direction') }}</th>
              <th>{{ t('uc.entrustCurrent.price') }}</th>
              <th>{{ t('uc.entrustCurrent.amount') }}</th>
              <th>{{ t('uc.entrustCurrent.action') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in futuresList" :key="o.orderId">
              <td>{{ formatTime(o.createTime) }}</td>
              <td>{{ o.symbol || '-' }}</td>
              <td :class="directionClass(o)">{{ directionText(o) }}</td>
              <td>{{ o.price }}</td>
              <td>{{ o.amount }}</td>
              <td>
                <MobileButton variant="danger" @click="cancelFutures(o.orderId)">{{ t('uc.entrustCurrent.cancel') }}</MobileButton>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="!futuresList.length" class="no-data">{{ t('common.nodata') }}</p>
      </template>

    </div>
  </div>
</template>

<style scoped>
.uc-error { color: #f87171; margin-bottom: 16px; font-size: 13px; }
.uc-loading { color: #64748b; padding: 32px 0; text-align: center; }
.dir-long { color: #22c55e; }
.dir-short { color: #ef4444; }

.tab-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  padding-bottom: 0;
}
.tab-btn {
  padding: 8px 18px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.tab-btn.active {
  color: #f0a70a;
  border-bottom-color: #f0a70a;
  font-weight: 600;
}
.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f0a70a;
  color: #000;
  font-size: 11px;
  font-weight: 700;
}
</style>
