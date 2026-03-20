<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../../stores/app'
import { getWalletList, getUcWithdrawAddressList, getUcWithdrawRecord, postUcWithdraw, getUcWithdrawPasswordStatus, getUcAuthenticateStatus } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'
import MobileInput from '../../components/mobile/MobileInput.vue'

const { t } = useI18n()
const app = useAppStore()
const vipLevel = computed(() => app.member?.vipLevel ?? 0)

const realNameVerified = ref(false)
const coins = ref([])
const addressList = ref([])
const selectedCoinId = ref('')
const selectedAddress = ref('')
const amount = ref('')
const recordList = ref([])
const recordTotal = ref(0)
const loading = ref(true)
const submitting = ref(false)
const errorMsg = ref('')
const recordPage = ref(1)
const needWithdrawPassword = ref(false)
const withdrawPassword = ref('')

async function loadCoins() {
  try {
    const list = await getWalletList()
    coins.value = (list || []).map(w => ({ id: w.coinId, unit: w.unit }))
    if (coins.value.length && !selectedCoinId.value) selectedCoinId.value = coins.value[0].id
  } catch (_) {
    coins.value = []
  }
}

async function loadAddresses() {
  try {
    addressList.value = await getUcWithdrawAddressList() || []
  } catch (_) {
    addressList.value = []
  }
}

async function loadRecord() {
  loading.value = true
  try {
    const data = await getUcWithdrawRecord(recordPage.value, 20)
    recordList.value = data?.content || []
    recordTotal.value = data?.totalElements || 0
  } catch (_) {
    recordList.value = []
  } finally {
    loading.value = false
  }
}

async function loadWithdrawPasswordStatus() {
  try {
    const st = await getUcWithdrawPasswordStatus()
    needWithdrawPassword.value = st?.hasSet === true
  } catch (_) {
    needWithdrawPassword.value = false
  }
}

async function loadAuthStatus() {
  try {
    const st = await getUcAuthenticateStatus()
    realNameVerified.value = st?.status === 'APPROVED'
  } catch (_) {
    realNameVerified.value = false
  }
}

async function submit() {
  errorMsg.value = ''
  if (!realNameVerified.value) {
    errorMsg.value = t('uc.withdraw.kycFirst')
    return
  }
  if (!needWithdrawPassword.value) {
    errorMsg.value = t('uc.withdraw.setWithdrawPwdFirst')
    return
  }
  if (!(withdrawPassword.value || '').trim()) {
    errorMsg.value = t('uc.withdraw.enterWithdrawPwd')
    return
  }
  const a = Number(amount.value)
  if (!selectedCoinId.value || !selectedAddress.value || !a || a <= 0) {
    errorMsg.value = t('uc.withdraw.enterCoinAddressAmount')
    return
  }
  submitting.value = true
  try {
    const body = {
      coinId: selectedCoinId.value,
      address: selectedAddress.value,
      amount: a,
      withdrawPassword: withdrawPassword.value,
    }
    await postUcWithdraw(body)
    amount.value = ''
    withdrawPassword.value = ''
    await loadRecord()
  } catch (e) {
    errorMsg.value = e.message || t('uc.withdraw.submitFailed')
  } finally {
    submitting.value = false
  }
}

function addressesForCoin() {
  return addressList.value.filter(a => String(a.coinId) === String(selectedCoinId.value))
}

function onCoinChange() {
  const addrs = addressesForCoin()
  selectedAddress.value = addrs[0]?.address || ''
}

function recordStatusText(status) {
  const map = {
    PENDING: t('uc.withdraw.statusPending'),
    PROCESSING: t('uc.withdraw.statusProcessing'),
    APPROVED: t('uc.withdraw.statusApproved'),
    REJECTED: t('uc.withdraw.statusRejected'),
  }
  return map[status] || status
}

onMounted(async () => {
  await loadAuthStatus()
  await loadCoins()
  await loadAddresses()
  await loadWithdrawPasswordStatus()
  await loadRecord()
  const addrs = addressesForCoin()
  selectedAddress.value = addrs[0]?.address || ''
})
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.withdraw.title') }}</h2>
    <p class="uc-vip-row">{{ t('uc.vipLevel') }}: <span class="vip-value">VIP {{ vipLevel }}</span></p>
    <div class="card">
      <h3 class="card-title">{{ t('uc.withdraw.applyTitle') }}</h3>
      <p v-if="!realNameVerified" class="uc-hint real-name-required">
        <strong>{{ t('uc.withdraw.needKyc') }}</strong>
        <router-link to="/uc/account" class="link">{{ t('uc.withdraw.goKyc') }}</router-link>
      </p>
      <p v-else-if="!needWithdrawPassword" class="uc-hint real-name-required">
        <strong>{{ t('uc.withdraw.needWithdrawPwd') }}</strong>
        <router-link to="/uc/safe" class="link">{{ t('uc.withdraw.goSafe') }}</router-link>
      </p>
      <p v-else class="uc-hint">{{ t('uc.withdraw.hint') }}</p>
      <div class="form-row">
        <label>{{ t('uc.withdraw.coin') }}</label>
        <select v-model="selectedCoinId" class="input" @change="onCoinChange">
          <option v-for="c in coins" :key="c.id" :value="c.id">{{ c.unit }}</option>
        </select>
      </div>
      <div class="form-row">
        <label>{{ t('uc.withdraw.address') }}</label>
        <span class="form-with-hint">
          <select v-model="selectedAddress" class="input">
            <option v-for="a in addressesForCoin()" :key="a.id" :value="a.address">{{ a.address }} {{ a.remark ? '(' + a.remark + ')' : '' }}</option>
            <option v-if="addressesForCoin().length === 0" value="">{{ t('uc.withdraw.addAddressFirst') }}</option>
          </select>
          <span v-if="addressesForCoin().length === 0" class="inline-hint">{{ t('uc.withdraw.goAddressHint') }} <router-link to="/uc/withdraw/address" class="link">{{ t('uc.layout.withdrawAddress') }}</router-link></span>
        </span>
      </div>
      <div class="form-row field">
        <MobileInput
          v-model="amount"
          type="number"
          :label="t('uc.withdraw.amount')"
          placeholder="0"
        />
      </div>
      <div v-if="needWithdrawPassword" class="form-row field">
        <MobileInput
          v-model="withdrawPassword"
          type="password"
          :label="t('uc.withdraw.withdrawPassword')"
          :placeholder="t('uc.withdraw.withdrawPasswordPlaceholder')"
        />
      </div>
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <div class="form-row">
        <MobileButton
          block
          variant="primary"
          :loading="submitting"
          :disabled="!realNameVerified || !needWithdrawPassword"
          @click="submit"
        >
          {{ t('uc.withdraw.submit') }}
        </MobileButton>
      </div>
    </div>
    <div class="card">
      <h3 class="card-title">{{ t('uc.withdraw.history') }}</h3>
      <div v-if="loading" class="uc-hint">{{ t('common.loading') }}</div>
      <div v-else class="uc-table-wrap">
        <table class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.withdraw.coin') }}</th>
              <th>{{ t('uc.withdraw.amount') }}</th>
              <th>{{ t('uc.withdraw.status') }}</th>
              <th>{{ t('uc.recharge.time') }}</th>
              <th>{{ t('uc.withdraw.reason') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recordList" :key="r.id">
              <td>{{ r.unit }}</td>
              <td>{{ r.arrivedAmount }}</td>
              <td>{{ recordStatusText(r.status) }}</td>
              <td>{{ r.createTime ? r.createTime.replace('T', ' ').slice(0, 19) : '' }}</td>
              <td class="reject-reason-cell">{{ r.status === 'REJECTED' && r.remark ? r.remark : '—' }}</td>
            </tr>
            <tr v-if="recordList.length === 0">
              <td colspan="5" class="empty">{{ t('uc.withdraw.noRecord') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-with-hint { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.inline-hint { font-size: 12px; color: #64748b; }
.real-name-required { color: #fbbf24; }
.real-name-required .link { margin-left: 8px; }
.card .btn { margin-top: 16px; }
.reject-reason-cell { max-width: 200px; word-break: break-word; color: #94a3b8; font-size: 13px; }
.uc-vip-row { margin: 0 0 16px; font-size: 14px; color: #94a3b8; }
.uc-vip-row .vip-value { color: #f0a70a; font-weight: 600; }
</style>
