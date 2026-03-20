<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../../stores/app'
import { message } from '../../components/toast'
import { getUcRechargeCoins, getUcRechargeAddress, getUcRechargeRecord, postUcRechargeSubmit } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'
import MobileInput from '../../components/mobile/MobileInput.vue'

const { t } = useI18n()
const app = useAppStore()
const vipLevel = computed(() => app.member?.vipLevel ?? 0)

const coins = ref([])
const selectedUnit = ref('')
const addressInfo = ref(null)
const recordList = ref([])
const recordTotal = ref(0)
const loading = ref(true)
const recordPage = ref(1)

const submitAmount = ref('')
const transferImage = ref('')
const transferImageFile = ref(null)
const submitLoading = ref(false)
const submitError = ref('')

async function loadCoins() {
  try {
    const list = await getUcRechargeCoins()
    coins.value = (list || []).map(c => c.unit)
    if (coins.value.length && !selectedUnit.value) selectedUnit.value = coins.value[0]
  } catch (_) {
    coins.value = []
  }
}

async function loadAddress() {
  if (!selectedUnit.value) return
  try {
    addressInfo.value = await getUcRechargeAddress(selectedUnit.value)
  } catch (_) {
    addressInfo.value = null
  }
}

async function loadRecord() {
  loading.value = true
  try {
    const data = await getUcRechargeRecord(recordPage.value, 20)
    recordList.value = data?.content || []
    recordTotal.value = data?.totalElements || 0
  } catch (_) {
    recordList.value = []
  } finally {
    loading.value = false
  }
}

function onUnitChange() {
  loadAddress()
}

async function copyAddress() {
  if (!addressInfo.value?.address) return
  const text = addressInfo.value.address
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      message.success(t('msg.copied'))
      return
    } catch (err) { }
  }
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
    message.success(t('msg.copied'))
  } catch (err) {
    message.error(t('msg.copyFailed'))
  }
  document.body.removeChild(ta)
}

function onImageChange(e) {
  const file = e.target?.files?.[0]
  if (!file) return
  transferImageFile.value = file
  const reader = new FileReader()
  reader.onload = () => {
    let data = reader.result
    if (typeof data === 'string' && data.length > 400000) {
      data = data.substring(0, 400000)
    }
    transferImage.value = data
  }
  reader.readAsDataURL(file)
}

async function submitRecharge() {
  submitError.value = ''
  const unit = selectedUnit.value
  const amountStr = submitAmount.value != null ? String(submitAmount.value).trim() : ''
  if (!unit) {
    submitError.value = t('uc.recharge.selectCoin')
    return
  }
  const num = Number(amountStr)
  if (!amountStr || !Number.isFinite(num) || num <= 0) {
    submitError.value = t('uc.recharge.errInvalidAmount')
    return
  }
  if (!transferImage.value) {
    submitError.value = t('uc.recharge.errProofRequired')
    return
  }
  submitLoading.value = true
  try {
    await postUcRechargeSubmit({ unit: selectedUnit.value, amount: num, transferImage: transferImage.value })
    message.success(t('uc.recharge.submitSuccess'))
    submitAmount.value = ''
    transferImage.value = ''
    transferImageFile.value = null
    await loadRecord()
  } catch (e) {
    submitError.value = e.message || t('uc.account.submitFailed')
  } finally {
    submitLoading.value = false
  }
}

onMounted(async () => {
  await loadCoins()
  await loadAddress()
  await loadRecord()
})
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.recharge.title') }}</h2>
    <p class="uc-vip-row">{{ t('uc.vipLevel') }}: <span class="vip-value">VIP {{ vipLevel }}</span></p>
    <div class="recharge-address card">
      <h3 class="card-title">{{ t('uc.recharge.addressTitle') }}</h3>
      <div class="form-row">
        <label>{{ t('uc.recharge.selectCoin') }}</label>
        <select v-model="selectedUnit" class="input" @change="onUnitChange">
          <option v-for="u in coins" :key="u" :value="u">{{ u }}</option>
        </select>
      </div>
      <div v-if="addressInfo" class="address-block">
        <p class="label">{{ t('uc.recharge.address') }}</p>
        <div class="address-row">
          <p class="value mono">{{ addressInfo.address }}</p>
          <MobileButton variant="gray" @click="copyAddress">{{ t('uc.recharge.copy') }}</MobileButton>
        </div>
      </div>
    </div>

    <div class="recharge-submit card">
      <h3 class="card-title">{{ t('uc.recharge.submitTitle') }}</h3>
      <p class="hint">{{ t('uc.recharge.hint') }}</p>
      <div class="form-row field">
        <MobileInput
          v-model="submitAmount"
          type="number"
          :label="t('uc.recharge.amount')"
          :placeholder="t('uc.recharge.amountPlaceholder')"
        />
      </div>
      <div class="form-row">
        <label>{{ t('uc.recharge.proof') }}</label>
        <div class="image-upload">
          <input type="file" accept="image/*" class="file-input" @change="onImageChange" />
          <span v-if="!transferImage" class="upload-placeholder">{{ t('uc.recharge.proofPlaceholder') }}</span>
          <div v-else class="image-preview-wrap">
            <img :src="transferImage" :alt="t('uc.recharge.proof')" class="image-preview" />
            <button type="button" class="remove-img" @click="transferImage=''; transferImageFile=null">{{ t('uc.recharge.remove') }}</button>
          </div>
        </div>
      </div>
      <p v-if="submitError" class="uc-error">{{ submitError }}</p>
      <div class="form-row">
        <MobileButton
          block
          variant="success"
          :loading="submitLoading"
          @click="submitRecharge"
        >
          {{ t('uc.recharge.submit') }}
        </MobileButton>
      </div>
    </div>

    <div class="record card">
      <h3 class="card-title">{{ t('uc.recharge.history') }}</h3>
      <div v-if="loading" class="uc-hint">{{ t('common.loading') }}</div>
      <div v-else class="uc-table-wrap">
        <table class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.recharge.amountCol') }}</th>
              <th>{{ t('uc.recharge.status') }}</th>
              <th>{{ t('uc.recharge.time') }}</th>
              <th>{{ t('uc.recharge.note') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recordList" :key="r.id">
              <td>{{ r.amount }}</td>
              <td>{{ r.status === 'CONFIRMED' ? t('uc.recharge.statusConfirmed') : r.status === 'REJECTED' ? t('uc.recharge.statusRejected') : t('uc.recharge.statusPending') }}</td>
              <td>{{ r.createTime ? r.createTime.replace('T', ' ').slice(0, 19) : '' }}</td>
              <td>{{ r.status === 'REJECTED' && r.rejectReason ? r.rejectReason : '—' }}</td>
            </tr>
            <tr v-if="recordList.length === 0">
              <td colspan="4" class="empty">{{ t('uc.recharge.noRecord') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.recharge-address .form-row label { flex: 0 0 80px; }
.address-block { margin-top: 16px; padding: 14px; background: rgba(15, 23, 42, 0.6); border-radius: 8px; border: 1px solid #334155; }
.address-block .label { color: #94a3b8; font-size: 13px; margin: 0 0 6px; }
.address-row { display: flex; align-items: flex-start; gap: 12px; }
.address-row .value.mono { flex: 1; font-family: ui-monospace, monospace; word-break: break-all; font-size: 13px; color: #e2e8f0; margin: 0; min-width: 0; }
.copy-btn { flex-shrink: 0; padding: 6px 14px; font-size: 13px; color: #e2e8f0; background: #475569; border: 1px solid #64748b; border-radius: 6px; cursor: pointer; }
.copy-btn:hover { background: #64748b; }

.recharge-submit { margin-top: 24px; }
.recharge-submit .hint { font-size: 13px; color: #94a3b8; margin: 0 0 16px 0; }
.recharge-submit .image-upload { position: relative; }
.recharge-submit .file-input { position: absolute; opacity: 0; width: 100%; height: 100%; cursor: pointer; }
.recharge-submit .upload-placeholder { display: inline-block; padding: 24px; border: 1px dashed #475569; border-radius: 8px; color: #94a3b8; font-size: 13px; }
.recharge-submit .image-preview-wrap { display: inline-flex; flex-direction: column; gap: 8px; }
.recharge-submit .image-preview { max-width: 200px; max-height: 160px; border-radius: 8px; border: 1px solid #334155; }
.recharge-submit .remove-img { align-self: flex-start; padding: 4px 10px; font-size: 12px; color: #94a3b8; background: transparent; border: 1px solid #475569; border-radius: 4px; cursor: pointer; }
.recharge-submit .btn-submit { padding: 10px 24px; font-size: 14px; color: #fff; background: #22c55e; border: none; border-radius: 8px; cursor: pointer; }
.recharge-submit .btn-submit:disabled { opacity: 0.7; cursor: not-allowed; }
.recharge-submit .uc-error { margin-top: 8px; color: #f87171; font-size: 13px; }
.uc-vip-row { margin: 0 0 16px; font-size: 14px; color: #94a3b8; }
.uc-vip-row .vip-value { color: #f0a70a; font-weight: 600; }
</style>
