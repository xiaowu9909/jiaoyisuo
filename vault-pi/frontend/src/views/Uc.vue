<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { getWalletList, postAssetExchange, getMarketSymbolThumb } from '../api'
import { message } from '../components/toast'
import MobileButton from '../components/mobile/MobileButton.vue'
import MobileInput from '../components/mobile/MobileInput.vue'
import MobileModal from '../components/mobile/MobileModal.vue'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const { t } = useI18n()

const wallets = ref([])
const loading = ref(true)
const errorMsg = ref('')

const showExchangeModal = ref(false)
const exchangeLoading = ref(false)
const exchangeForm = ref({ fromUnit: '', toUnit: '', amount: '' })
const allCoins = ref(['BTC', 'ETH', 'USDT', 'SOL', 'BNB', 'DOGE', 'XRP', 'ADA', 'DOT', 'MATIC', 'LTC', 'BCH'])
const exchangeRate = ref(0)

const exchangeResult = computed(() => {
  if (!exchangeForm.value.amount || !exchangeRate.value) return 0
  return (Number(exchangeForm.value.amount) * exchangeRate.value).toFixed(8)
})

async function fetchWallets() {
  loading.value = true
  try {
    wallets.value = await getWalletList()
    if (wallets.value.length > 0 && !exchangeForm.value.fromUnit) {
      exchangeForm.value.fromUnit = wallets.value[0].unit
    }
  } catch (e) {
    errorMsg.value = e.message || t('common.error')
  } finally {
    loading.value = false
  }
}

async function openExchange() {
  showExchangeModal.value = true
  if (exchangeForm.value.fromUnit && exchangeForm.value.toUnit) {
    updateRate()
  }
}

async function updateRate() {
  const { fromUnit, toUnit } = exchangeForm.value
  if (!fromUnit || !toUnit || fromUnit === toUnit) {
    exchangeRate.value = 0
    return
  }
  try {
    const thumbs = await getMarketSymbolThumb()
    let fromPrice = 1
    let toPrice = 1
    if (fromUnit !== 'USDT') {
      const f = thumbs.find(t => t.symbol === `${fromUnit}/USDT`)
      if (f) fromPrice = f.close
    }
    if (toUnit !== 'USDT') {
      const t = thumbs.find(t => t.symbol === `${toUnit}/USDT`)
      if (t) toPrice = t.close
    }
    exchangeRate.value = fromPrice / toPrice
  } catch (e) {
    console.error('Rate fetch failed', e)
  }
}

watch(() => [exchangeForm.value.fromUnit, exchangeForm.value.toUnit], updateRate)

async function handleExchange() {
  if (!exchangeForm.value.fromUnit || !exchangeForm.value.toUnit || !exchangeForm.value.amount) {
    message.error(t('uc.exchangeModal.fillInfo'))
    return
  }
  exchangeLoading.value = true
  try {
    const res = await postAssetExchange(exchangeForm.value)
    if (res.code === 0) {
      message.success(t('common.success'))
      showExchangeModal.value = false
      fetchWallets()
    } else {
      message.error(res.message)
    }
  } catch (e) {
    message.error(e.message || t('uc.exchangeModal.exchangeFailed'))
  } finally {
    exchangeLoading.value = false
  }
}

onMounted(async () => {
  if (!app.isLogin) {
    router.replace('/login?redirect=' + encodeURIComponent(route.fullPath || '/uc'))
    return
  }
  await fetchWallets()
  if (route.query.open === 'convert') {
    showExchangeModal.value = true
    router.replace({ path: '/uc', query: {} })
  }
})

function formatNum(n) {
  if (n == null) return '0.00'
  const x = Number(n)
  return Number.isFinite(x) ? x.toFixed(8) : '0.00'
}
</script>

<template>
  <div class="uc-content">
    <div class="uc-header">
      <h2 class="uc-page-title">{{ t('header.assetmanage') }}</h2>
      <MobileButton variant="primary" @click="openExchange">{{ t('uc.exchangeModal.exchange') }}</MobileButton>
    </div>
    <p v-if="errorMsg" class="uc-error">{{ errorMsg }}</p>
    <div v-else-if="loading" class="uc-loading">{{ t('common.loading') }}</div>
    <div v-else class="uc-table-wrap">
      <table class="wallet-table uc-table">
        <thead>
          <tr>
            <th>{{ t('service.Coin') }}</th>
            <th>{{ t('exchange.canuse') }}</th>
            <th>{{ t('exchange.frozen') }}</th>
            <th>{{ t('exchange.dealamount') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="w in wallets" :key="w.id">
            <td class="unit">{{ w.unit }}</td>
            <td>{{ formatNum(w.balance) }}</td>
            <td>{{ formatNum(w.frozenBalance) }}</td>
            <td>{{ formatNum(w.allBalance) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!wallets.length && !loading" class="no-data">{{ t('common.nodata') }}</p>
    </div>

    <!-- Exchange Modal -->
    <MobileModal
      v-model="showExchangeModal"
      :title="t('uc.exchangeModal.exchangeTitle')"
    >
      <div class="form-item">
        <label>{{ t('uc.exchangeModal.from') }}</label>
        <select v-model="exchangeForm.fromUnit" class="m-select">
          <option v-for="w in wallets" :key="w.unit" :value="w.unit">{{ w.unit }} ({{ t('uc.exchangeModal.balance') }}: {{ formatNum(w.balance) }})</option>
        </select>
      </div>
      <div class="form-item">
        <label>{{ t('uc.exchangeModal.to') }}</label>
        <select v-model="exchangeForm.toUnit" class="m-select">
          <option v-for="c in allCoins" :key="c" :value="c">{{ c }}</option>
        </select>
      </div>
      <div class="form-item">
        <MobileInput
          v-model="exchangeForm.amount"
          type="number"
          :label="t('uc.exchangeModal.amount')"
          :placeholder="t('uc.exchangeModal.amountPlaceholder')"
        />
      </div>
      <div v-if="exchangeRate" class="exchange-info">
        <p>{{ t('uc.exchangeModal.rate') }}: 1 {{ exchangeForm.fromUnit }} ≈ {{ exchangeRate.toFixed(8) }} {{ exchangeForm.toUnit }}</p>
        <p>{{ t('uc.exchangeModal.estimated') }}: <span class="result">{{ exchangeResult }}</span> {{ exchangeForm.toUnit }}</p>
      </div>
      
      <div class="modal-footer-m">
        <MobileButton variant="gray" @click="showExchangeModal = false">{{ t('uc.exchangeModal.cancel') }}</MobileButton>
        <MobileButton variant="primary" :loading="exchangeLoading" @click="handleExchange">
          {{ t('uc.exchangeModal.submit') }}
        </MobileButton>
      </div>
    </MobileModal>
  </div>
</template>

<style scoped>
.wallet-table td.unit { color: #f0a70a; font-weight: 500; }

.m-select {
  width: 100%;
  background: #0f172a;
  border: 1px solid #334155;
  color: #fff;
  padding: 10px 12px;
  border-radius: 6px;
  outline: none;
  box-sizing: border-box;
}

.exchange-info {
  background: #0f172a;
  padding: 14px;
  border-radius: 8px;
  margin-top: 12px;
  border: 1px solid #334155;
}
.exchange-info p { margin: 6px 0; font-size: 13px; color: #94a3b8; }
.exchange-info .result { color: #f0a70a; font-weight: 600; font-size: 16px; }

.modal-footer-m {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
