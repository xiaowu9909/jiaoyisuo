<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getOrderCurrent, postOrderCancel } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'

const { t } = useI18n()
const list = ref([])
const loading = ref(true)
const errorMsg = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    list.value = await getOrderCurrent() || []
  } catch (e) {
    errorMsg.value = e.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function cancel(orderId) {
  try {
    await postOrderCancel(orderId)
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

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.entrustCurrent.title') }}</h2>
    <p v-if="errorMsg" class="uc-error">{{ errorMsg }}</p>
    <div v-else-if="loading" class="uc-loading">{{ t('common.loading') }}</div>
    <div v-else class="uc-table-wrap">
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
          <tr v-for="o in list" :key="o.orderId">
            <td>{{ formatTime(o.createTime) }}</td>
            <td>{{ o.symbol || '-' }}</td>
            <td>{{ o.direction === 'BUY' ? t('uc.entrustCurrent.buy') : t('uc.entrustCurrent.sell') }}</td>
            <td>{{ o.price }}</td>
            <td>{{ o.amount }}</td>
            <td>
              <MobileButton variant="danger" @click="cancel(o.orderId)">{{ t('uc.entrustCurrent.cancel') }}</MobileButton>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!list.length && !loading" class="no-data">{{ t('common.nodata') }}</p>
    </div>
  </div>
</template>

<style scoped>
.uc-error { color: #f87171; margin-bottom: 16px; font-size: 13px; }
.uc-loading { color: #64748b; padding: 32px 0; text-align: center; }
</style>
