<script setup>
import { ref, onMounted } from 'vue'
import { getAdminVipLevelList, postAdminVipLevelUpdate } from '../api/admin'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminVipLevelList()
    list.value = Array.isArray(data) ? data : []
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!list.value.length) return
  saving.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const payload = list.value.map((item) => ({
      id: item.id,
      level: item.level,
      rechargeThreshold: item.rechargeThreshold,
      leverageMultiplier: item.leverageMultiplier
    }))
    await postAdminVipLevelUpdate(payload)
    successMsg.value = '保存成功'
    await load()
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page member-level-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">会员等级配置</span>
        <button type="button" class="btn btn-primary" @click="save" :disabled="saving || loading">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
      <div class="card-body">
        <p class="desc">默认会员等级为 VIP0。设置 VIP1～VIP6 的累计充值金额（USDT）门槛，达到后自动晋升。等级与合约最大杠杆倍数挂钩。</p>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="success">{{ successMsg }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>等级</th>
                <th>累计充值门槛（USDT）</th>
                <th>杠杆倍数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="3" class="loading-cell">加载中...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="3" class="no-data-cell">暂无配置，请刷新</td>
              </tr>
              <tr v-for="item in list" :key="item.level">
                <td><strong>VIP{{ item.level }}</strong></td>
                <td>
                  <input
                    v-model.number="item.rechargeThreshold"
                    type="number"
                    min="0"
                    step="any"
                    class="input input-sm"
                  />
                </td>
                <td>
                  <input
                    v-model.number="item.leverageMultiplier"
                    type="number"
                    min="1"
                    max="125"
                    class="input input-sm"
                  />
                  <span class="unit">倍</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.member-level-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }
.desc { color: #64748b; font-size: 13px; margin-bottom: 16px; }
.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; }
.input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; }
.input-sm { width: 140px; }
.unit { margin-left: 6px; color: #718096; }
.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; }
.btn-primary { background: #2d8cf0; color: #fff; }
.error { color: #e53e3e; padding: 10px; font-size: 13px; }
.success { color: #38a169; padding: 10px; font-size: 13px; }
.loading-cell, .no-data-cell { text-align: center; color: #a0aec0; padding: 30px; }
</style>
