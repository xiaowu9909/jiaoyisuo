<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getWalletList, getUcWithdrawAddressList, postUcWithdrawAddressAdd } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'
import MobileInput from '../../components/mobile/MobileInput.vue'

const { t } = useI18n()

const list = ref([])
const coins = ref([])
const showAdd = ref(false)
const addCoinId = ref('')
const addAddress = ref('')
const addRemark = ref('')
const submitting = ref(false)
const errorMsg = ref('')

async function loadCoins() {
  try {
    const wallets = await getWalletList()
    coins.value = (wallets || []).map(w => ({ id: w.coinId, unit: w.unit }))
    if (coins.value.length && !addCoinId.value) addCoinId.value = coins.value[0].id
  } catch (_) {
    coins.value = []
  }
}

async function loadList() {
  try {
    list.value = await getUcWithdrawAddressList() || []
  } catch (_) {
    list.value = []
  }
}

async function doAdd() {
  errorMsg.value = ''
  const addr = (addAddress.value || '').trim()
  if (!addCoinId.value || !addr) {
    errorMsg.value = t('uc.withdrawAddress.selectCoinAndAddress')
    return
  }
  submitting.value = true
  try {
    await postUcWithdrawAddressAdd({
      coinId: addCoinId.value,
      address: addr,
      remark: (addRemark.value || '').trim() || undefined,
    })
    addAddress.value = ''
    addRemark.value = ''
    showAdd.value = false
    await loadList()
  } catch (e) {
    errorMsg.value = e.message || t('uc.withdrawAddress.addFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadCoins()
  await loadList()
})
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.withdrawAddress.title') }}</h2>
    <p class="page-hint">{{ t('uc.withdrawAddress.hint') }}</p>
    <div class="card">
      <div class="card-head">
        <h3 class="card-title">{{ t('uc.withdrawAddress.listTitle') }}</h3>
        <MobileButton variant="primary" @click="showAdd = true">{{ t('uc.withdrawAddress.add') }}</MobileButton>
      </div>
      <div v-if="showAdd" class="add-form">
        <div class="form-row">
          <label>{{ t('uc.withdrawAddress.coin') }}</label>
          <select v-model="addCoinId" class="input">
            <option v-for="c in coins" :key="c.id" :value="c.id">{{ c.unit }}</option>
          </select>
        </div>
        <div class="form-row field">
          <MobileInput v-model="addAddress" :label="t('uc.withdrawAddress.address')" :placeholder="t('uc.withdrawAddress.placeholder')" />
        </div>
        <div class="form-row field">
          <MobileInput v-model="addRemark" :label="t('uc.withdrawAddress.remark')" :placeholder="t('uc.withdrawAddress.remarkPlaceholder')" />
        </div>
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
        <div class="form-actions">
          <MobileButton variant="gray" @click="showAdd = false">{{ t('uc.withdrawAddress.cancel') }}</MobileButton>
          <MobileButton variant="primary" :loading="submitting" @click="doAdd">{{ t('uc.withdrawAddress.confirm') }}</MobileButton>
        </div>
      </div>
      <div class="table-wrap">
        <table v-if="list.length" class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.withdrawAddress.coin') }}</th>
              <th>{{ t('uc.withdrawAddress.address') }}</th>
              <th>{{ t('uc.withdrawAddress.remark') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td>{{ item.unit }}</td>
              <td class="addr-cell">{{ item.address }}</td>
              <td>{{ item.remark || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!list.length" class="empty-hint">{{ t('uc.withdrawAddress.noAddress') }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.add-form { margin-bottom: 24px; padding: 20px; background: rgba(15, 23, 42, 0.6); border-radius: 8px; border: 1px solid #334155; }
.add-form .form-row label { flex: 0 0 80px; }
.add-form .input { max-width: 100%; }
.form-actions { display: flex; gap: 12px; margin-top: 16px; }
.table-wrap { overflow-x: auto; margin-top: 8px; }
.addr-cell { word-break: break-all; max-width: 320px; }
.error-msg { color: #f87171; font-size: 13px; margin: 8px 0 0; }
</style>
