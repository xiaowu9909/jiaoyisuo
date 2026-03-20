<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const members = ref([])
const selectedMember = ref(null)
const wallets = ref([])
const rechargeForm = ref({ memberId: '', unit: 'USDT', amount: '' })
const msg = ref('')

async function loadMembers() {
  try {
    const res = await fetch(`${API_BASE}/admin/member/page?pageNo=1&pageSize=100`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) members.value = json.data.content || []
  } catch (_) {}
}

async function loadWallets(memberId) {
  selectedMember.value = memberId
  rechargeForm.value.memberId = memberId
  try {
    const res = await fetch(`${API_BASE}/admin/asset/wallet?memberId=${memberId}`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) wallets.value = json.data || []
  } catch (_) {
    wallets.value = []
  }
}

async function doRecharge() {
  msg.value = ''
  if (!rechargeForm.value.memberId || !rechargeForm.value.amount) {
    msg.value = '请填写完整'; return
  }
  try {
    const res = await fetch(`${API_BASE}/admin/asset/recharge`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(rechargeForm.value)
    })
    const json = await res.json()
    msg.value = json.code === 0 ? '充值成功' : (json.message || '失败')
    if (json.code === 0) {
      loadWallets(rechargeForm.value.memberId)
      rechargeForm.value.amount = ''
    }
  } catch (e) { msg.value = e.message }
}

onMounted(loadMembers)
</script>

<template>
  <div class="admin-page">
    <h1 class="page-title">资产管理</h1>
    <div class="two-col">
      <div class="member-list">
        <h3>选择用户</h3>
        <div class="member-scroll">
          <div v-for="m in members" :key="m.id"
               class="member-row" :class="{ active: selectedMember === m.id }"
               @click="loadWallets(m.id)">
            <span>{{ m.username || m.email || m.id }}</span>
            <span class="member-id">#{{ m.id }}</span>
          </div>
        </div>
      </div>
      <div class="wallet-detail">
        <template v-if="selectedMember">
          <h3>钱包余额 (用户 #{{ selectedMember }})</h3>
          <table class="data-table">
            <thead><tr><th>币种</th><th>余额</th><th>冻结</th></tr></thead>
            <tbody>
              <tr v-for="w in wallets" :key="w.id">
                <td>{{ w.unit }}</td>
                <td>{{ Number(w.balance).toFixed(4) }}</td>
                <td>{{ Number(w.frozenBalance || 0).toFixed(4) }}</td>
              </tr>
              <tr v-if="!wallets.length"><td colspan="3" style="color:#828ea1">暂无钱包数据</td></tr>
            </tbody>
          </table>
          <h3 style="margin-top:20px">手动充值</h3>
          <div class="recharge-form">
            <select v-model="rechargeForm.unit" class="form-input"><option>USDT</option><option>BTC</option><option>ETH</option></select>
            <input v-model="rechargeForm.amount" type="number" placeholder="金额" class="form-input" />
            <button class="btn-primary" @click="doRecharge">充值</button>
          </div>
          <p v-if="msg" class="msg">{{ msg }}</p>
        </template>
        <p v-else class="hint">← 请先选择用户</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { max-width: 1100px; }
.page-title { font-size: 22px; font-weight: 700; margin: 0 0 20px; color: #fff; }
.two-col { display: flex; gap: 20px; }
.member-list { width: 260px; flex-shrink: 0; }
.member-list h3 { font-size: 14px; color: #828ea1; margin: 0 0 10px; }
.member-scroll { max-height: 500px; overflow-y: auto; }
.member-row {
  padding: 8px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center;
  border-radius: 4px; font-size: 13px; color: #e4e4e7; transition: background 0.1s;
}
.member-row:hover { background: rgba(255,255,255,0.04); }
.member-row.active { background: rgba(240,167,10,0.1); color: #f0a70a; }
.member-id { font-size: 11px; color: #828ea1; }
.wallet-detail { flex: 1; }
.wallet-detail h3 { font-size: 14px; color: #828ea1; margin: 0 0 10px; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 8px 10px; text-align: left; color: #828ea1; border-bottom: 1px solid #1e2d3d; }
.data-table td { padding: 8px 10px; border-bottom: 1px solid #1e2d3d; color: #e4e4e7; }
.recharge-form { display: flex; gap: 10px; align-items: center; }
.form-input { padding: 6px 10px; background: #172636; border: 1px solid #27313e; border-radius: 4px; color: #fff; font-size: 13px; }
.btn-primary { padding: 6px 16px; background: #f0a70a; border: none; border-radius: 4px; color: #000; font-weight: 600; cursor: pointer; font-size: 13px; }
.msg { color: #f0a70a; font-size: 12px; margin-top: 8px; }
.hint { color: #828ea1; font-size: 14px; margin-top: 60px; }
</style>
