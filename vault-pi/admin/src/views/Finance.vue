<script setup>
import { message } from '../components/toast';

import { ref, onMounted, computed } from 'vue'
import { 
  getAdminWithdrawPage, 
  postAdminWithdrawAudit, 
  getAdminDepositPage, 
  getAdminTransactionPage,
  postAdminDepositManual,
  postAdminDepositConfirm,
  postAdminDepositReject,
  getAdminExchangeCoinList
} from '../api/admin'

const activeTab = ref('withdraw')
const coins = ref([])

const tabMeta = {
  withdraw: {
    title: '提现审核',
    desc: '统一处理会员出金申请，追踪审核状态与出账结果。',
  },
  deposit: {
    title: '充值记录',
    desc: '查看链上到账与人工补单记录，核对资金入账明细。',
  },
  transaction: {
    title: '全站流水',
    desc: '汇总平台资产变动流水，支持按用户、币种、类型审计。',
  },
}

// Shared filter/loading
const errorMsg = ref('')

// Withdraw state
const withdrawList = ref([])
const withdrawTotal = ref(0)
const withdrawLoading = ref(false)
const withdrawFilterStatus = ref('PENDING')
const withdrawPage = ref(1)
const W_PAGE_SIZE = 20

// Deposit state
const depositList = ref([])
const depositTotal = ref(0)
const depositLoading = ref(false)
const depositUid = ref('')
const depositCoinId = ref('')
const depositPage = ref(1)
const D_PAGE_SIZE = 20

// Transaction state
const txList = ref([])
const txTotal = ref(0)
const txLoading = ref(false)
const txUid = ref('')
const txSymbol = ref('')
const txType = ref('')
const txPage = ref(1)
const T_PAGE_SIZE = 20

// Modals
const auditModal = ref(false)
const auditRow = ref(null)
const auditSubmitting = ref(false)
const auditResult = ref('APPROVED')
const auditRemark = ref('')

const manualDepositModal = ref(false)
const manualForm = ref({ memberId: '', coinId: '', amount: '', remark: '' })
const manualSubmitting = ref(false)

const depositImageModal = ref(false)
const depositImageSrc = ref('')
const confirmDepositLoading = ref(null)
const rejectDepositModal = ref(false)
const rejectDepositRow = ref(null)
const rejectReason = ref('')
const rejectDepositLoading = ref(false)

function formatTime(str) {
  if (!str) return '—'
  try {
    return new Date(str).toLocaleString('zh-CN')
  } catch (_) {
    return str
  }
}

function withdrawStatusText(s) {
  const map = { PENDING: '待审核', PROCESSING: '处理中', APPROVED: '已通过', REJECTED: '已拒绝' }
  return map[s] || s
}

function depositStatusText(s) {
  const map = { PENDING: '待审核', CONFIRMED: '已到账', REJECTED: '已拒绝', SUCCESS: '已成功', FAILED: '已失败' }
  return map[s] || s
}

function withdrawStatusClass(s) {
  const map = {
    PENDING: 'pending',
    PROCESSING: 'processing',
    APPROVED: 'approved',
    REJECTED: 'rejected',
  }
  return map[s] || 'default'
}

function depositStatusClass(s) {
  const map = {
    PENDING: 'pending',
    CONFIRMED: 'success',
    REJECTED: 'rejected',
    SUCCESS: 'success',
    FAILED: 'rejected',
  }
  return map[s] || 'default'
}

function txTypeText(type) {
  const map = {
    RECHARGE: '充值',
    WITHDRAW: '提现申请',
    WITHDRAW_REFUND: '提现退回',
    EXCHANGE: '币币成交',
    ADMIN_RECHARGE: '管理充值',
    ADMIN_DEDUCT: '管理扣减',
    CONTRACT_OPEN: '开仓',
    CONTRACT_CLOSE: '平仓',
    PROMOTION: '推广返佣',
  }
  return map[type] || type
}

function txTypeClass(type) {
  if (type === 'ADMIN_RECHARGE' || type === 'RECHARGE') return 'income'
  if (type === 'ADMIN_DEDUCT' || type === 'WITHDRAW') return 'expense'
  return 'neutral'
}

const currentTabTitle = computed(() => tabMeta[activeTab.value].title)
const currentTabDesc = computed(() => tabMeta[activeTab.value].desc)
const currentTotal = computed(() => {
  if (activeTab.value === 'withdraw') return withdrawTotal.value
  if (activeTab.value === 'deposit') return depositTotal.value
  return txTotal.value
})
const pendingWithdrawCount = computed(
  () => withdrawList.value.filter((item) => item.status === 'PENDING' || item.status === 'PROCESSING').length,
)
const successDepositCount = computed(
  () => depositList.value.filter((item) => item.status === 'SUCCESS').length,
)
const positiveTxCount = computed(
  () => txList.value.filter((item) => Number(item.amount) >= 0).length,
)

async function loadCoins() {
  try { coins.value = await getAdminExchangeCoinList() } catch (_) {}
}

// --- Loaders ---
async function loadWithdrawals() {
  withdrawLoading.value = true
  try {
    const data = await getAdminWithdrawPage(withdrawPage.value, W_PAGE_SIZE, withdrawFilterStatus.value)
    withdrawList.value = data.content || []
    withdrawTotal.value = data.totalElements || 0
  } catch (e) { errorMsg.value = e.message }
  finally { withdrawLoading.value = false }
}

async function loadDeposits() {
  depositLoading.value = true
  try {
    const data = await getAdminDepositPage(depositPage.value, D_PAGE_SIZE, depositUid.value, depositCoinId.value)
    depositList.value = data.content || []
    depositTotal.value = data.totalElements || 0
  } catch (e) { errorMsg.value = e.message }
  finally { depositLoading.value = false }
}

function openDepositImage(row) {
  if (row.transferImage) {
    depositImageSrc.value = row.transferImage
    depositImageModal.value = true
  }
}

async function confirmDeposit(row) {
  if (row.status !== 'PENDING' || !row.transferImage) return
  confirmDepositLoading.value = row.id
  try {
    await postAdminDepositConfirm({ id: row.id })
    message.success('确认成功，用户资金已到账')
    await loadDeposits()
  } catch (e) {
    message.error(e.message || '确认失败')
  } finally {
    confirmDepositLoading.value = null
  }
}

function openRejectDeposit(row) {
  if (row.status !== 'PENDING' || !row.transferImage) return
  rejectDepositRow.value = row
  rejectReason.value = ''
  rejectDepositModal.value = true
}

async function submitRejectDeposit() {
  if (!rejectDepositRow.value) return
  rejectDepositLoading.value = true
  try {
    await postAdminDepositReject({ id: rejectDepositRow.value.id, reason: rejectReason.value })
    message.error('已拒绝该笔充值')
    rejectDepositModal.value = false
    rejectDepositRow.value = null
    await loadDeposits()
  } catch (e) {
    message.error(e.message || '操作失败')
  } finally {
    rejectDepositLoading.value = false
  }
}

async function loadTransactions() {
  txLoading.value = true
  try {
    const data = await getAdminTransactionPage(txPage.value, T_PAGE_SIZE, txUid.value, txSymbol.value, txType.value)
    txList.value = data.content || []
    txTotal.value = data.totalElements || 0
  } catch (e) { errorMsg.value = e.message }
  finally { txLoading.value = false }
}

// --- Actions ---
function openAudit(row) {
  auditRow.value = row
  auditModal.value = true
  auditResult.value = 'APPROVED'
  auditRemark.value = ''
}

async function submitAudit() {
  if (auditResult.value === 'REJECTED' && !auditRemark.value.trim()) return message.error('请填写拒绝原因')
  auditSubmitting.value = true
  try {
    await postAdminWithdrawAudit({ id: auditRow.value.id, status: auditResult.value, remark: auditRemark.value })
    auditModal.value = false
    loadWithdrawals()
  } catch (e) { message.error(e.message) }
  finally { auditSubmitting.value = false }
}

async function submitManualDeposit() {
  if (!manualForm.value.memberId || !manualForm.value.coinId || !manualForm.value.amount) return message.error('请完善表单')
  manualSubmitting.value = true
  try {
    await postAdminDepositManual(manualForm.value)
    manualDepositModal.value = false
    if (activeTab.value === 'deposit') loadDeposits()
    message.success('充值成功')
  } catch (e) { message.error(e.message) }
  finally { manualSubmitting.value = false }
}

function openManualDeposit() {
  manualForm.value = { memberId: '', coinId: '', amount: '', remark: '' }
  manualDepositModal.value = true
}

function switchTab(tab) {
  activeTab.value = tab
  errorMsg.value = ''
  if (tab === 'withdraw') loadWithdrawals()
  else if (tab === 'deposit') loadDeposits()
  else if (tab === 'transaction') loadTransactions()
}

onMounted(() => {
  loadCoins()
  loadWithdrawals()
})
</script>

<template>
  <div class="finance-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">财务管理</h2>
        <p class="page-subtitle">围绕提现、充值与流水三类核心资金链路，提供统一的后台审计与操作入口。</p>
      </div>
      <div class="header-actions">
        <button v-if="activeTab === 'deposit'" class="btn btn-primary" @click="openManualDeposit">+ 手动充值</button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card primary">
        <span class="summary-label">当前模块</span>
        <strong class="summary-value">{{ currentTabTitle }}</strong>
        <span class="summary-hint">{{ currentTabDesc }}</span>
      </div>
      <div class="summary-card">
        <span class="summary-label">当前记录数</span>
        <strong class="summary-value">{{ currentTotal }}</strong>
        <span class="summary-hint">按当前筛选条件统计</span>
      </div>
      <div class="summary-card">
        <span class="summary-label">待处理提现</span>
        <strong class="summary-value">{{ pendingWithdrawCount }}</strong>
        <span class="summary-hint">当前页待审核 / 处理中</span>
      </div>
      <div class="summary-card">
        <span class="summary-label">到账 / 正向流水</span>
        <strong class="summary-value">{{ activeTab === 'deposit' ? successDepositCount : positiveTxCount }}</strong>
        <span class="summary-hint">{{ activeTab === 'deposit' ? '当前页成功充值笔数' : '当前页正向变动笔数' }}</span>
      </div>
    </div>

    <div class="tab-scaffold">
      <div class="tab-nav">
        <button :class="['tab-item', { active: activeTab === 'withdraw' }]" @click="switchTab('withdraw')">
          <span class="tab-title">提现审核</span>
          <span class="tab-desc">出金工单处理</span>
        </button>
        <button :class="['tab-item', { active: activeTab === 'deposit' }]" @click="switchTab('deposit')">
          <span class="tab-title">充值记录</span>
          <span class="tab-desc">到账与补单查询</span>
        </button>
        <button :class="['tab-item', { active: activeTab === 'transaction' }]" @click="switchTab('transaction')">
          <span class="tab-title">全站流水</span>
          <span class="tab-desc">平台资产变动审计</span>
        </button>
      </div>

      <div class="tab-body">
        <div v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</div>

        <!-- Withdrawals -->
        <div v-if="activeTab === 'withdraw'" class="pane">
          <div class="section-head">
            <div>
              <h3>提现工单列表</h3>
              <p>聚焦审核状态、地址信息与到账金额，便于财务快速处理出金工单。</p>
            </div>
          </div>

          <div class="filter-panel">
            <div class="filter-bar">
              <div class="filter-item">
                <label>状态</label>
                <select v-model="withdrawFilterStatus" @change="withdrawPage=1; loadWithdrawals()">
                  <option value="">全部</option>
                  <option value="PENDING">待审核</option>
                  <option value="PROCESSING">处理中</option>
                  <option value="APPROVED">已通过</option>
                  <option value="REJECTED">已拒绝</option>
                </select>
              </div>
              <div class="filter-actions">
                <button class="btn btn-primary" @click="withdrawPage=1; loadWithdrawals()">查询</button>
                <button class="btn btn-ghost" @click="loadWithdrawals">刷新</button>
              </div>
            </div>
          </div>

          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>会员信息</th>
                  <th>币种</th>
                  <th>申请 / 到账</th>
                  <th>手续费</th>
                  <th>地址</th>
                  <th>申请时间</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="withdrawLoading"><td colspan="9" class="state-row">正在加载提现工单...</td></tr>
                <tr v-else-if="!withdrawList.length"><td colspan="9" class="state-row">暂无符合条件的提现工单</td></tr>
                <tr v-for="row in withdrawList" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td>
                    <div class="user-info">
                      <span class="uid">UID: {{ row.memberId }}</span>
                      <span class="name">{{ row.username }}</span>
                    </div>
                  </td>
                  <td><span class="coin-tag">{{ row.unit }}</span></td>
                  <td>
                    <div class="amount-stack">
                      <span class="total">{{ row.totalAmount }}</span>
                      <span class="arrived">→ {{ row.arrivedAmount }}</span>
                    </div>
                  </td>
                  <td>{{ row.fee }}</td>
                  <td class="address-cell" :title="row.address">{{ row.address }}</td>
                  <td>{{ formatTime(row.createTime) }}</td>
                  <td>
                    <span :class="['badge', withdrawStatusClass(row.status)]">{{ withdrawStatusText(row.status) }}</span>
                  </td>
                  <td>
                    <button v-if="row.status === 'PENDING' || row.status === 'PROCESSING'" class="btn-text" @click="openAudit(row)">去审核</button>
                    <span v-else class="done-text">已结项</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <button :disabled="withdrawPage <= 1" @click="withdrawPage--; loadWithdrawals()">上一页</button>
            <span>第 {{ withdrawPage }} 页 / 共 {{ Math.ceil(withdrawTotal/W_PAGE_SIZE) || 1 }} 页</span>
            <button :disabled="withdrawPage >= Math.ceil(withdrawTotal/W_PAGE_SIZE)" @click="withdrawPage++; loadWithdrawals()">下一页</button>
          </div>
        </div>

        <!-- Deposits -->
        <div v-if="activeTab === 'deposit'" class="pane">
          <div class="section-head">
            <div>
              <h3>充值到账记录</h3>
              <p>支持查看链上充值与后台手工补单，快速完成到账核对与异常补录。</p>
            </div>
          </div>

          <div class="filter-panel">
            <div class="filter-bar">
              <div class="filter-item">
                <label>会员 UID</label>
                <input v-model="depositUid" placeholder="输入会员UID" @keyup.enter="depositPage=1; loadDeposits()" />
              </div>
              <div class="filter-item">
                <label>币种筛选</label>
                <select v-model="depositCoinId" @change="depositPage=1; loadDeposits()">
                  <option value="">全部币种</option>
                  <option v-for="c in coins" :key="c.id" :value="c.id">{{ c.unit }}</option>
                </select>
              </div>
              <div class="filter-actions">
                <button class="btn btn-primary" @click="depositPage=1; loadDeposits()">查询</button>
              </div>
            </div>
          </div>

          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>充值ID</th>
                  <th>会员</th>
                  <th>币种</th>
                  <th>充值金额</th>
                  <th>交易哈希 (TxID)</th>
                  <th>转账详情图</th>
                  <th>状态</th>
                  <th>确认时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="depositLoading"><td colspan="9" class="state-row">正在加载充值记录...</td></tr>
                <tr v-else-if="!depositList.length"><td colspan="9" class="state-row">暂无符合条件的充值记录</td></tr>
                <tr v-for="row in depositList" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td>{{ row.memberId }} ({{ row.username }})</td>
                  <td><span class="coin-tag">{{ row.unit }}</span></td>
                  <td class="text-success">+{{ row.amount }}</td>
                  <td class="hash-cell">{{ row.txId || '—' }}</td>
                  <td>
                    <button v-if="row.transferImage" type="button" class="btn-link" @click="openDepositImage(row)">查看</button>
                    <span v-else>—</span>
                  </td>
                  <td><span :class="['badge', depositStatusClass(row.status)]">{{ depositStatusText(row.status) }}</span></td>
                  <td>{{ formatTime(row.createTime) }}</td>
                  <td>
                    <template v-if="row.status === 'PENDING' && row.transferImage">
                      <button
                        type="button"
                        class="btn btn-sm btn-primary"
                        :disabled="confirmDepositLoading === row.id"
                        @click="confirmDeposit(row)"
                      >
                        {{ confirmDepositLoading === row.id ? '处理中…' : '确认' }}
                      </button>
                      <button
                        type="button"
                        class="btn btn-sm btn-danger"
                        :disabled="rejectDepositLoading"
                        @click="openRejectDeposit(row)"
                      >
                        拒绝
                      </button>
                    </template>
                    <span v-else>—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <button :disabled="depositPage <= 1" @click="depositPage--; loadDeposits()">上一页</button>
            <span>共 {{ depositTotal }} 条记录</span>
            <button :disabled="depositPage >= Math.ceil(depositTotal/D_PAGE_SIZE)" @click="depositPage++; loadDeposits()">下一页</button>
          </div>
        </div>

        <!-- Transactions -->
        <div v-if="activeTab === 'transaction'" class="pane">
          <div class="section-head">
            <div>
              <h3>全站资产流水</h3>
              <p>按会员、币种、流水类型检索资金变动，适用于审计追踪与问题定位。</p>
            </div>
          </div>

          <div class="filter-panel">
            <div class="filter-bar">
              <div class="filter-item">
                <label>会员 UID</label>
                <input v-model="txUid" placeholder="输入会员UID" />
              </div>
              <div class="filter-item">
                <label>币种</label>
                <select v-model="txSymbol">
                  <option value="">全部币种</option>
                  <option v-for="c in coins" :key="c.id" :value="c.unit">{{ c.unit }}</option>
                </select>
              </div>
              <div class="filter-item">
                <label>流水类型</label>
                <select v-model="txType">
                  <option value="">全部类型</option>
                  <option value="RECHARGE">充值</option>
                  <option value="WITHDRAW">提现申请</option>
                  <option value="WITHDRAW_REFUND">提现退回</option>
                  <option value="EXCHANGE">币币成交</option>
                  <option value="ADMIN_RECHARGE">管理充值</option>
                  <option value="ADMIN_DEDUCT">管理扣减</option>
                  <option value="CONTRACT_OPEN">开仓</option>
                  <option value="CONTRACT_CLOSE">平仓</option>
                </select>
              </div>
              <div class="filter-actions">
                <button class="btn btn-primary" @click="txPage=1; loadTransactions()">搜索流水</button>
              </div>
            </div>
          </div>

          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>流水ID</th>
                  <th>用户名</th>
                  <th>币种</th>
                  <th>变动金额</th>
                  <th>类型</th>
                  <th>手续费</th>
                  <th>变动时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="txLoading"><td colspan="7" class="state-row">正在加载全站流水...</td></tr>
                <tr v-else-if="!txList.length"><td colspan="7" class="state-row">暂无符合条件的流水记录</td></tr>
                <tr v-for="row in txList" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td>{{ row.username }} ({{ row.memberId }})</td>
                  <td><strong>{{ row.symbol }}</strong></td>
                  <td :class="row.amount >= 0 ? 'text-success' : 'text-danger'">{{ row.amount >= 0 ? '+' : '' }}{{ row.amount }}</td>
                  <td><span :class="['tx-type', txTypeClass(row.type)]">{{ txTypeText(row.type) }}</span></td>
                  <td>{{ row.fee }}</td>
                  <td>{{ formatTime(row.createTime) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <button :disabled="txPage <= 1" @click="txPage--; loadTransactions()">上一页</button>
            <span>共 {{ txTotal }} 条流水</span>
            <button :disabled="txPage >= Math.ceil(txTotal/T_PAGE_SIZE)" @click="txPage++; loadTransactions()">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Audit Modal -->
    <div v-if="auditModal" class="modal-overlay">
      <div class="modal-content small">
        <div class="modal-header">提现工单审核</div>
        <div class="modal-body p-4">
          <div class="audit-summary mb-4">
            <div class="summary-item"><span>待汇币种:</span> <strong>{{ auditRow.unit }}</strong></div>
            <div class="summary-item"><span>到账数量:</span> <strong class="text-warning">{{ auditRow.arrivedAmount }}</strong></div>
          </div>
          <div class="form-group mb-4">
            <label>审核结果</label>
            <div class="radio-group">
              <label><input v-model="auditResult" type="radio" value="APPROVED" /> 通过并汇出</label>
              <label><input v-model="auditResult" type="radio" value="REJECTED" /> 驳回并退款</label>
            </div>
          </div>
          <div class="form-group">
            <label>{{ auditResult === 'REJECTED' ? '驳回原因 (必填)' : '内部备注' }}</label>
            <textarea v-model="auditRemark" rows="3"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="auditModal = false">取消</button>
          <button class="btn btn-primary" :disabled="auditSubmitting" @click="submitAudit">提交处理</button>
        </div>
      </div>
    </div>

    <!-- Manual Deposit Modal -->
    <div v-if="manualDepositModal" class="modal-overlay">
      <div class="modal-content small">
        <div class="modal-header">手动资产补单</div>
        <div class="modal-body p-4">
          <div class="modal-tip">用于补录链下或异常到账场景，建议填写备注以便后续审计追踪。</div>
          <div class="form-group mb-3">
            <label>会员UID</label>
            <input v-model="manualForm.memberId" type="number" placeholder="请输入会员ID" />
          </div>
          <div class="form-group mb-3">
            <label>选择币种</label>
            <select v-model="manualForm.coinId">
              <option value="">请选择币种</option>
              <option v-for="c in coins" :key="c.id" :value="c.id">{{ c.unit }}</option>
            </select>
          </div>
          <div class="form-group mb-3">
            <label>补单数量</label>
            <input v-model="manualForm.amount" type="number" placeholder="0.00" />
          </div>
          <div class="form-group">
            <label>业务备注</label>
            <input v-model="manualForm.remark" type="text" placeholder="选填，建议注明补单原因" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="manualDepositModal = false">关闭</button>
          <button class="btn btn-primary" :disabled="manualSubmitting" @click="submitManualDeposit">确认补单</button>
        </div>
      </div>
    </div>

    <!-- 转账详情图预览 -->
    <div v-if="depositImageModal" class="modal-overlay" @click.self="depositImageModal = false">
      <div class="modal-content">
        <div class="modal-header">转账详情图</div>
        <div class="modal-body p-4 text-center">
          <img v-if="depositImageSrc" :src="depositImageSrc" alt="转账详情" class="deposit-image-preview" />
        </div>
        <div class="modal-footer">
          <button class="btn" @click="depositImageModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 拒绝充值原因 -->
    <div v-if="rejectDepositModal" class="modal-overlay" @click.self="rejectDepositModal = false">
      <div class="modal-content small">
        <div class="modal-header">拒绝充值订单</div>
        <div class="modal-body p-4">
          <p class="modal-tip">请填写拒绝原因，用户将在充币记录中看到该原因。</p>
          <div class="form-group">
            <label>拒绝原因</label>
            <textarea v-model="rejectReason" rows="4" placeholder="请输入拒绝原因（选填，不填将显示「未注明原因」）"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="rejectDepositModal = false">取消</button>
          <button class="btn btn-danger" :disabled="rejectDepositLoading" @click="submitRejectDeposit">
            {{ rejectDepositLoading ? '提交中…' : '确认拒绝' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.finance-container { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text-color); }
.page-subtitle { margin: 6px 0 0; color: #718096; font-size: 13px; }
.btn { padding: 10px 18px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-danger { background: #e53e3e; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }
.btn:hover { transform: translateY(-1px); }

.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.summary-card { background: #fff; border-radius: 12px; padding: 18px 20px; box-shadow: var(--box-shadow-sm); border: 1px solid #edf2f7; display: flex; flex-direction: column; gap: 6px; }
.summary-card.primary { background: linear-gradient(135deg, #1d4ed8, #2563eb); border-color: transparent; }
.summary-card.primary .summary-label,
.summary-card.primary .summary-value,
.summary-card.primary .summary-hint { color: #fff; }
.summary-label { font-size: 12px; color: #718096; }
.summary-value { font-size: 24px; line-height: 1.2; color: #1a202c; }
.summary-hint { font-size: 12px; color: #94a3b8; }

.tab-scaffold { background: #fff; border-radius: 12px; box-shadow: var(--box-shadow-sm); overflow: hidden; }
.tab-nav { display: flex; background: #f8f9fb; padding: 0 16px; border-bottom: 1px solid #edf2f7; }
.tab-item { padding: 16px 24px; font-size: 14px; font-weight: 600; color: #718096; cursor: pointer; border: none; background: none; border-bottom: 2px solid transparent; transition: 0.2s; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
.tab-item.active { color: var(--primary-color); border-bottom-color: var(--primary-color); }
.tab-item:hover { color: var(--primary-color); }
.tab-title { font-size: 14px; font-weight: 700; }
.tab-desc { font-size: 12px; color: #94a3b8; }

.tab-body { padding: 20px; }
.section-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.section-head h3 { margin: 0; font-size: 16px; color: #1a202c; }
.section-head p { margin: 6px 0 0; font-size: 13px; color: #718096; }
.filter-panel { margin-bottom: 20px; padding: 16px; border-radius: 12px; background: #f8fafc; border: 1px solid #edf2f7; }
.filter-bar { display: flex; gap: 12px; flex-wrap: wrap; align-items: flex-end; }
.filter-item { display: flex; flex-direction: column; gap: 8px; min-width: 180px; }
.filter-item label { font-size: 12px; color: #718096; font-weight: 600; }
.filter-item input, .filter-item select { padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; outline: none; transition: 0.2s; background: #fff; }
.filter-item input:focus, .filter-item select:focus { border-color: var(--primary-color); }
.filter-actions { display: flex; gap: 8px; align-items: center; }

.table-container { width: 100%; overflow-x: auto; border: 1px solid #edf2f7; border-radius: 12px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; border-bottom: 1px solid #edf2f7; }
td { padding: 12px 16px; border-bottom: 1px solid #edf2f7; color: #2d3748; }
.state-row { text-align: center; color: #94a3b8; padding: 40px 0; }

.user-info { display: flex; flex-direction: column; }
.user-info .uid { font-size: 10px; color: #a0aec0; }
.user-info .name { font-weight: 600; }
.coin-tag { background: #ebf4ff; color: #3182ce; padding: 2px 8px; border-radius: 4px; font-weight: 700; font-size: 11px; }

.amount-stack { display: flex; flex-direction: column; }
.amount-stack .total { color: #a0aec0; text-decoration: line-through; font-size: 11px; }
.amount-stack .arrived { font-weight: 700; color: #2d3748; }

.address-cell { max-width: 150px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-family: monospace; color: #718096; }
.hash-cell { font-family: monospace; font-size: 11px; color: #718096; }

.badge { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; text-transform: uppercase; }
.badge.pending { background: #fffaf0; color: #dd6b20; border: 1px solid #fbd38d; }
.badge.processing { background: #ebf8ff; color: #3182ce; border: 1px solid #90cdf4; }
.badge.approved, .badge.success { background: #f0fff4; color: #38a169; border: 1px solid #9ae6b4; }
.badge.rejected { background: #fff5f5; color: #e53e3e; border: 1px solid #feb2b2; }
.badge.default { background: #f7fafc; color: #64748b; border: 1px solid #e2e8f0; }

.btn-text { color: var(--primary-color); font-weight: 600; background: none; border: none; cursor: pointer; padding: 0; }
.btn-text:hover { text-decoration: underline; }
.btn-link { color: var(--primary-color); font-weight: 600; background: none; border: none; cursor: pointer; padding: 0; font-size: 13px; }
.btn-link:hover { text-decoration: underline; }
.btn-sm { padding: 6px 12px; font-size: 12px; }
.deposit-image-preview { max-width: 100%; max-height: 70vh; border-radius: 8px; border: 1px solid #edf2f7; }
.done-text { color: #cbd5e0; font-size: 11px; }

.pagination { display: flex; justify-content: flex-end; align-items: center; gap: 16px; margin-top: 24px; padding-top: 20px; border-top: 1px solid #edf2f7; }
.pagination button { padding: 6px 16px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; cursor: pointer; transition: 0.2s; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
.pagination button:not(:disabled):hover { background: #f7fafc; border-color: var(--primary-color); color: var(--primary-color); }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; backdrop-filter: blur(4px); }
.modal-content { background: #fff; border-radius: 12px; box-shadow: var(--box-shadow-lg); overflow: hidden; }
.modal-content.small { width: 440px; }
.modal-header { padding: 16px 20px; font-weight: 700; border-bottom: 1px solid #edf2f7; font-size: 16px; color: var(--text-color); }
.modal-tip { margin-bottom: 16px; padding: 10px 12px; background: #eff6ff; border: 1px solid #bfdbfe; color: #1d4ed8; border-radius: 8px; font-size: 12px; }
.form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #4a5568; }
.form-group input, .form-group select, .form-group textarea { width: 100%; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; box-sizing: border-box; }
.form-group textarea { resize: none; }
.radio-group { display: flex; gap: 20px; padding: 10px 0; }
.radio-group label { font-weight: 400; cursor: pointer; display: flex; align-items: center; gap: 6px; }

.modal-footer { padding: 16px 20px; background: #f8fafc; display: flex; justify-content: flex-end; gap: 12px; }

.text-success { color: #38a169 !important; font-weight: 700; }
.text-danger { color: #e53e3e !important; font-weight: 700; }
.tx-type { color: #718096; font-size: 11px; font-weight: 600; border: 1px solid #e2e8f0; padding: 2px 6px; border-radius: 999px; display: inline-flex; align-items: center; }
.tx-type.income { background: #f0fff4; color: #2f855a; border-color: #9ae6b4; }
.tx-type.expense { background: #fff5f5; color: #c53030; border-color: #feb2b2; }
.tx-type.neutral { background: #f8fafc; color: #64748b; border-color: #e2e8f0; }

@media (max-width: 1200px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .finance-container { padding: 16px; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .summary-grid { grid-template-columns: 1fr; }
  .tab-nav { overflow-x: auto; }
  .tab-item { min-width: 160px; }
  .filter-item { min-width: 100%; }
  .filter-actions { width: 100%; }
}
</style>
