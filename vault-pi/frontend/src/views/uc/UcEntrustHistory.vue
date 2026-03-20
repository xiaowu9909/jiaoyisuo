<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOrderTradeHistory } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'

const { t } = useI18n()
const list = ref([])
const loading = ref(true)
const errorMsg = ref('')
const total = ref(0)
const page = ref(1)
const pageSize = 20

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getOrderTradeHistory(page.value, pageSize)
    list.value = data?.content || []
    total.value = data?.totalElements || 0
  } catch (e) {
    errorMsg.value = e.message || t('common.error')
  } finally {
    loading.value = false
  }
}

function changePage(p) {
  page.value = p
  load()
}

function formatTime(str) {
  if (!str) return '-'
  try {
    return new Date(str).toLocaleString()
  } catch (_) {
    return str
  }
}

function tradeStatusText(status) {
  const map = {
    TRADING: t('uc.entrustHistory.statusTrading'),
    COMPLETED: t('uc.entrustHistory.statusCompleted'),
    CANCELLED: t('uc.entrustHistory.statusCancelled'),
    PARTIAL: t('uc.entrustHistory.statusPartial'),
  }
  return map[status] || status || '-'
}

function formatNum(n) {
  if (n == null) return '-'
  const x = Number(n)
  return Number.isFinite(x) ? (x >= 1 ? x.toFixed(4) : x.toFixed(8)) : '-'
}

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.entrustHistory.title') }}</h2>
    <p class="page-hint">{{ t('uc.entrustHistory.hint') }}</p>
    <p v-if="errorMsg" class="uc-error">{{ errorMsg }}</p>
    <div v-else-if="loading" class="uc-loading">{{ t('common.loading') }}</div>
    <div v-else class="uc-table-wrap">
      <table class="uc-table">
        <thead>
          <tr>
            <th>{{ t('uc.entrustHistory.fillTime') }}</th>
            <th>{{ t('uc.entrustHistory.symbol') }}</th>
            <th>{{ t('uc.entrustHistory.direction') }}</th>
            <th>{{ t('uc.entrustHistory.price') }}</th>
            <th>{{ t('uc.entrustHistory.filled') }}</th>
            <th>{{ t('uc.entrustHistory.total') }}</th>
            <th>{{ t('uc.entrustHistory.status') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in list" :key="o.orderId">
            <td>{{ formatTime(o.createTime) }}</td>
            <td>{{ o.symbol || '-' }}</td>
            <td :class="o.direction === 'BUY' ? 'dir-buy' : 'dir-sell'">{{ o.direction === 'BUY' ? t('uc.entrustHistory.buy') : t('uc.entrustHistory.sell') }}</td>
            <td>{{ formatNum(o.price) }}</td>
            <td>{{ formatNum(o.tradedAmount) }}</td>
            <td>{{ formatNum(o.totalAmount) }}</td>
            <td>{{ tradeStatusText(o.status) }}</td>
          </tr>
          <tr v-if="!list.length && !loading"><td colspan="7" class="empty">{{ t('common.nodata') }}</td></tr>
        </tbody>
      </table>
      <div v-if="list.length" class="pagination">
        <MobileButton variant="gray" :disabled="page <= 1" @click="changePage(page - 1)">{{ t('uc.entrustHistory.prev') }}</MobileButton>
        <span class="page-info">{{ t('uc.entrustHistory.page') }} {{ page }} / {{ Math.max(1, Math.ceil(total / pageSize)) }}, {{ total }} {{ t('uc.entrustHistory.totalRows') }}</span>
        <MobileButton variant="gray" :disabled="page >= Math.ceil(total / pageSize)" @click="changePage(page + 1)">{{ t('uc.entrustHistory.next') }}</MobileButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uc-error { color: #f87171; margin-bottom: 16px; font-size: 13px; }
.uc-loading { color: #64748b; padding: 32px 0; text-align: center; }
.empty { text-align: center; padding: 24px; color: #64748b; }
.dir-buy { color: #22c55e; }
.dir-sell { color: #ef4444; }
</style>
