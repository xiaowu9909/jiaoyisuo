<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getUcTransactionPage } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'

const { t } = useI18n()
const list = ref([])
const total = ref(0)
const loading = ref(true)
const pageNo = ref(1)
const pageSize = ref(20)

async function load() {
  loading.value = true
  try {
    const data = await getUcTransactionPage(pageNo.value, pageSize.value)
    list.value = data?.content || []
    total.value = data?.totalElements || 0
  } catch (_) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function typeText(type) {
  const map = {
    RECHARGE: t('uc.record.typeRecharge'),
    WITHDRAW: t('uc.record.typeWithdraw'),
    TRADE: t('uc.record.typeTrade'),
    TRANSFER: t('uc.record.typeTransfer'),
    ADMIN_RECHARGE: t('uc.record.typeAdminRecharge'),
  }
  return map[type] || type
}

function changePage(p) {
  pageNo.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.record.title') }}</h2>
    <div v-if="loading" class="uc-hint">{{ t('common.loading') }}</div>
    <div v-else>
      <div class="uc-table-wrap">
        <table class="uc-table">
          <thead>
            <tr>
              <th>{{ t('uc.record.type') }}</th>
              <th>{{ t('uc.record.coin') }}</th>
              <th>{{ t('uc.record.amount') }}</th>
              <th>{{ t('uc.record.fee') }}</th>
              <th>{{ t('uc.record.time') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td>{{ typeText(item.type) }}</td>
              <td>{{ item.symbol }}</td>
              <td :class="Number(item.amount) < 0 ? 'neg' : ''">{{ item.amount }}</td>
              <td>{{ item.fee }}</td>
              <td>{{ item.createTime ? item.createTime.replace('T', ' ').slice(0, 19) : '' }}</td>
            </tr>
            <tr v-if="list.length === 0">
              <td colspan="5" class="empty">{{ t('uc.record.noRecord') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <MobileButton variant="gray" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)">{{ t('uc.record.prev') }}</MobileButton>
        <span class="page-info">{{ pageNo }} / {{ Math.ceil(total / pageSize) || 1 }}</span>
        <MobileButton variant="gray" :disabled="pageNo >= Math.ceil(total / pageSize)" @click="changePage(pageNo + 1)">{{ t('uc.record.next') }}</MobileButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uc-table .neg { color: #f87171; }
</style>
