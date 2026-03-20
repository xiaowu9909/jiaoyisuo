<script setup>
import { message } from '../components/toast';

import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAdminMemberDetail,
  getAdminMemberCoins,
  postAdminMemberBalanceUpdate,
  postAdminMemberBalanceUnfreeze,
  postAdminMemberStatusUpdate,
  postAdminMemberPasswordReset,
  postAdminMemberWithdrawPasswordReset,
  postAdminMemberUpdateBase,
  postAdminMemberWithdrawAddressAdd,
  postAdminMemberWithdrawAddressUpdate,
  postAdminMemberWithdrawAddressDelete
} from '../api/admin'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(true)
const errorMsg = ref('')

// Balance Modal
const modalVisible = ref(false)
const modalSubmitting = ref(false)
const modalError = ref('')
const selectedWallet = ref(null)
const formData = ref({ type: 0, action: 0, amount: '', remark: '' })

// Status & Password
const statusSubmitting = ref(false)
const pwdModalVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdError = ref('')
const newPassword = ref('')
const withdrawPwdModalVisible = ref(false)
const withdrawPwdValue = ref('')
const withdrawPwdSubmitting = ref(false)
const withdrawPwdError = ref('')

// 用户类型 / 上级 / VIP 编辑
const baseUpdateSubmitting = ref(false)
const editUserType = ref('')   // NORMAL | INTERNAL，与 detail 同步
const editParentInviteCode = ref('')  // 用于更改上级（邀请码，空表示清除）
const editVipLevel = ref(0)    // 0-6，与 detail 同步
const vipUpdateSubmitting = ref(false)

// Withdraw addresses
const coins = ref([])
const addrAddVisible = ref(false)
const addrEditTarget = ref(null)
const addrAddSubmitting = ref(false)
const addrEditSubmitting = ref(false)
const addrError = ref('')
const addrForm = ref({ coinId: '', address: '', remark: '' })
const addrEditForm = ref({ address: '', remark: '' })

const memberId = computed(() => route.params.id)

async function load() {
  if (!memberId.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    detail.value = await getAdminMemberDetail(Number(memberId.value))
    if (detail.value) {
      editUserType.value = detail.value.userType === 'INTERNAL' ? 'INTERNAL' : 'NORMAL'
      editParentInviteCode.value = ''
      editVipLevel.value = detail.value.vipLevel != null ? detail.value.vipLevel : 0
    }
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function saveUserType() {
  if (!detail.value || baseUpdateSubmitting.value) return
  const ut = editUserType.value === 'INTERNAL' ? 'INTERNAL' : 'NORMAL'
  if (ut === (detail.value.userType || 'NORMAL')) return
  baseUpdateSubmitting.value = true
  try {
    await postAdminMemberUpdateBase({ id: detail.value.id, userType: ut })
    await load()
  } catch (e) {
    message.error(e.message || '更新失败')
  } finally {
    baseUpdateSubmitting.value = false
  }
}

async function saveParent() {
  if (!detail.value || baseUpdateSubmitting.value) return
  const code = (editParentInviteCode.value || '').trim()
  baseUpdateSubmitting.value = true
  try {
    if (code) {
      await postAdminMemberUpdateBase({ id: detail.value.id, parentInviteCode: code })
    } else {
      await postAdminMemberUpdateBase({ id: detail.value.id, parentId: null })
    }
    editParentInviteCode.value = ''
    await load()
  } catch (e) {
    message.error(e.message || '更新失败')
  } finally {
    baseUpdateSubmitting.value = false
  }
}

async function saveVipLevel() {
  if (!detail.value || vipUpdateSubmitting.value) return
  const lv = Number(editVipLevel.value)
  if (lv < 0 || lv > 6) {
    message.warning('VIP 等级须为 0～6')
    return
  }
  if (lv === (detail.value.vipLevel != null ? detail.value.vipLevel : 0)) return
  vipUpdateSubmitting.value = true
  try {
    await postAdminMemberUpdateBase({ id: detail.value.id, vipLevel: lv })
    await load()
  } catch (e) {
    message.error(e.message || '更新失败')
  } finally {
    vipUpdateSubmitting.value = false
  }
}

async function loadCoins() {
  try {
    coins.value = await getAdminMemberCoins()
  } catch (_) {
    coins.value = []
  }
}

async function toggleStatus() {
  if (!detail.value) return
  const newStatus = detail.value.status === 'NORMAL' ? 'DISABLED' : 'NORMAL'
  if (!confirm(`确定要将会员状态修改为 ${newStatus === 'NORMAL' ? '正常' : '禁用'} 吗？`)) return
  statusSubmitting.value = true
  try {
    await postAdminMemberStatusUpdate({ id: detail.value.id, status: newStatus })
    await load()
  } catch (e) {
    message.error(e.message)
  } finally {
    statusSubmitting.value = false
  }
}

function openPwdModal() {
  newPassword.value = ''
  pwdError.value = ''
  pwdModalVisible.value = true
}

async function submitPwdReset() {
  if (!newPassword.value || newPassword.value.length < 6) {
    pwdError.value = '密码至少 6 位'
    return
  }
  pwdSubmitting.value = true
  try {
    await postAdminMemberPasswordReset({ id: detail.value.id, password: newPassword.value })
    pwdModalVisible.value = false
    message.success('密码重置成功')
  } catch (e) {
    pwdError.value = e.message
  } finally {
    pwdSubmitting.value = false
  }
}

function openWithdrawPwdModal() {
  withdrawPwdValue.value = ''
  withdrawPwdError.value = ''
  withdrawPwdModalVisible.value = true
}

async function submitWithdrawPwdReset() {
  withdrawPwdError.value = ''
  if (!withdrawPwdValue.value || withdrawPwdValue.value.length < 6) {
    withdrawPwdError.value = '提现密码至少 6 位'
    return
  }
  withdrawPwdSubmitting.value = true
  try {
    await postAdminMemberWithdrawPasswordReset({ id: detail.value.id, password: withdrawPwdValue.value })
    withdrawPwdModalVisible.value = false
    await load()
    message.success('提现密码已重置')
  } catch (e) {
    withdrawPwdError.value = e.message || '重置失败'
  } finally {
    withdrawPwdSubmitting.value = false
  }
}

function formatTime(str) {
  if (!str) return '—'
  try {
    return new Date(str).toLocaleString('zh-CN')
  } catch (_) {
    return str
  }
}

function back() {
  router.push('/member')
}

function openModal(wallet) {
  selectedWallet.value = wallet
  formData.value = { type: 0, action: 0, amount: '', remark: '' }
  modalError.value = ''
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
}

async function submitBalanceUpdate() {
  modalError.value = ''
  if (!formData.value.amount || Number(formData.value.amount) <= 0) {
    modalError.value = '请输入有效金额'
    return
  }
  modalSubmitting.value = true
  try {
    await postAdminMemberBalanceUpdate({
      memberId: Number(memberId.value),
      coinId: selectedWallet.value.coinId,
      type: formData.value.type,
      action: formData.value.action,
      amount: formData.value.amount,
      remark: formData.value.remark
    })
    closeModal()
    await load()
  } catch (e) {
    modalError.value = e.message || '更新失败'
  } finally {
    modalSubmitting.value = false
  }
}

// Withdraw address: add
function openAddrAdd() {
  addrForm.value = { coinId: coins.value[0]?.id ?? '', address: '', remark: '' }
  addrError.value = ''
  addrAddVisible.value = true
}

async function submitAddrAdd() {
  addrError.value = ''
  if (!addrForm.value.coinId || !addrForm.value.address?.trim()) {
    addrError.value = '请选择币种并填写地址'
    return
  }
  addrAddSubmitting.value = true
  try {
    await postAdminMemberWithdrawAddressAdd({
      memberId: Number(memberId.value),
      coinId: Number(addrForm.value.coinId),
      address: addrForm.value.address.trim(),
      remark: (addrForm.value.remark || '').trim()
    })
    addrAddVisible.value = false
    await load()
  } catch (e) {
    addrError.value = e.message || '添加失败'
  } finally {
    addrAddSubmitting.value = false
  }
}

// Withdraw address: edit
function openAddrEdit(addr) {
  addrEditTarget.value = addr
  addrEditForm.value = { address: addr.address, remark: addr.remark || '' }
  addrError.value = ''
}

function closeAddrEdit() {
  addrEditTarget.value = null
}

async function submitAddrEdit() {
  if (!addrEditTarget.value) return
  addrError.value = ''
  if (!addrEditForm.value.address?.trim()) {
    addrError.value = '地址不能为空'
    return
  }
  addrEditSubmitting.value = true
  try {
    await postAdminMemberWithdrawAddressUpdate({
      id: addrEditTarget.value.id,
      memberId: Number(memberId.value),
      address: addrEditForm.value.address.trim(),
      remark: (addrEditForm.value.remark || '').trim()
    })
    closeAddrEdit()
    await load()
  } catch (e) {
    addrError.value = e.message || '更新失败'
  } finally {
    addrEditSubmitting.value = false
  }
}

async function deleteAddr(addr) {
  if (!confirm('确定删除该提现地址？')) return
  try {
    await postAdminMemberWithdrawAddressDelete({ id: addr.id, memberId: Number(memberId.value) })
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

function hasFrozen(w) {
  const v = w.frozenBalance
  if (v == null) return false
  const n = Number(v)
  return !Number.isNaN(n) && n > 0
}

const unfreezing = ref(false)
async function unfreezeWallet(w) {
  if (!hasFrozen(w)) return
  const amount = w.frozenBalance != null ? String(w.frozenBalance) : ''
  if (!confirm(`确定将 ${w.unit} 的冻结余额（${amount}）全部解除并退回可用？`)) return
  unfreezing.value = true
  try {
    await postAdminMemberBalanceUnfreeze({
      memberId: Number(memberId.value),
      coinId: w.coinId,
      amount: amount,
    })
    await load()
  } catch (e) {
    message.error(e.message || '解除冻结失败')
  } finally {
    unfreezing.value = false
  }
}

onMounted(() => {
  load()
  loadCoins()
})
</script>

<template>
  <div class="admin-page member-detail-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">会员详情<template v-if="detail">（ID {{ detail.id }} · {{ detail.username }}）</template></span>
        <div class="head-btns">
          <button type="button" class="btn btn-small" @click="load" :disabled="loading">刷新</button>
          <button type="button" class="btn btn-small btn-with-icon" @click="back"><SvgIcon name="back" :size="16" class="btn-icon" /> 返回列表</button>
        </div>
      </div>
      <div class="card-body">
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div v-else-if="loading" class="loading">加载中...</div>
        <template v-else-if="detail">
          <div class="detail-grid">
            <!-- 左列：基本资料 + 账号状态与密码 -->
            <div class="col-left">
              <div class="info-section">
                <h4>基本资料</h4>
                <div class="info-dl">
                  <div class="info-item"><dt>UID</dt><dd>{{ detail.uid ?? '—' }}</dd></div>
                  <div class="info-item info-item-edit">
                    <dt>类型</dt>
                    <dd class="edit-row">
                      <select v-model="editUserType" class="input-select">
                        <option value="NORMAL">正常用户</option>
                        <option value="INTERNAL">内部用户</option>
                      </select>
                      <button type="button" class="btn-sm btn-save" :disabled="baseUpdateSubmitting || editUserType === (detail.userType || 'NORMAL')" @click="saveUserType">
                        {{ baseUpdateSubmitting ? '保存中…' : '保存' }}
                      </button>
                    </dd>
                  </div>
                  <div class="info-item"><dt>登录名</dt><dd>{{ detail.username }}</dd></div>
                  <div class="info-item"><dt>手机</dt><dd>{{ detail.phone || '—' }}</dd></div>
                  <div class="info-item"><dt>邮箱</dt><dd>{{ detail.email || '—' }}</dd></div>
                  <div class="info-item"><dt>实名</dt><dd>{{ detail.realName || '—' }}</dd></div>
                  <div class="info-item"><dt>邀请码</dt><dd><span class="mono">{{ detail.inviteCode || '—' }}</span></dd></div>
                  <div class="info-item info-item-edit">
                    <dt>VIP 等级</dt>
                    <dd class="edit-row">
                      <select v-model.number="editVipLevel" class="input-select">
                        <option v-for="n in 7" :key="n - 1" :value="n - 1">VIP{{ n - 1 }}</option>
                      </select>
                      <button type="button" class="btn-sm btn-save" :disabled="vipUpdateSubmitting || editVipLevel === (detail.vipLevel != null ? detail.vipLevel : 0)" @click="saveVipLevel">
                        {{ vipUpdateSubmitting ? '保存中…' : '保存' }}
                      </button>
                      <span class="muted" style="margin-left:8px">累计充值(USDT): {{ detail.totalRecharge != null ? detail.totalRecharge : 0 }}</span>
                    </dd>
                  </div>
                  <div class="info-item info-item-edit">
                    <dt>推荐人</dt>
                    <dd>
                      <template v-if="detail.parentId != null">
                        <router-link :to="'/member/detail/' + detail.parentId" class="parent-link">{{ detail.parentUsername || ('UID ' + detail.parentUid) }}</router-link>
                      </template>
                      <span v-else class="muted">无</span>
                      <div class="edit-row parent-edit">
                        <input v-model="editParentInviteCode" type="text" class="input-inline" placeholder="填写新上级的邀请码，留空并保存则清除" maxlength="32" />
                        <button type="button" class="btn-sm btn-save" :disabled="baseUpdateSubmitting" @click="saveParent">保存上级</button>
                      </div>
                    </dd>
                  </div>
                  <div class="info-item"><dt>注册</dt><dd>{{ formatTime(detail.registrationTime) }}</dd></div>
                  <div class="info-item"><dt>登录</dt><dd>{{ formatTime(detail.lastLoginTime) }}</dd></div>
                  <div class="info-item"><dt>角色</dt><dd>{{ detail.role === 'ADMIN' ? 'ADMIN' : 'USER' }}</dd></div>
                  <div class="info-item"><dt>状态</dt><dd>
                    <span :class="['status-tag', detail.status === 'NORMAL' ? 'ok' : 'err']">{{ detail.status === 'NORMAL' ? '正常' : '禁用' }}</span>
                  </dd></div>
                </div>
              </div>
              <div class="action-section">
                <h4>账号与密码</h4>
                <div class="action-row">
                  <button type="button" :class="['btn-sm', detail.status === 'NORMAL' ? 'btn-danger' : 'btn-success']" @click="toggleStatus" :disabled="statusSubmitting">
                    {{ detail.status === 'NORMAL' ? '禁用账号' : '解冻账号' }}
                  </button>
                  <button type="button" class="btn-sm btn-warning" @click="openPwdModal">重置登录密码</button>
                  <button type="button" class="btn-sm" @click="openWithdrawPwdModal">重置提现密码</button>
                </div>
                <p class="hint">提现密码：{{ detail.hasWithdrawPassword ? '已设置' : '未设置' }}。禁用后该会员无法在 C 端登录及下单。</p>
              </div>
            </div>

            <!-- 右列：资产 + 提现地址 -->
            <div class="col-right">
              <div class="info-section">
                <h4>资产账户</h4>
                <div class="table-wrap">
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th>币种</th>
                        <th>可用</th>
                        <th>冻结</th>
                        <th style="text-align: right">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-if="!detail.wallets?.length"><td colspan="4" class="no-data-cell">暂无</td></tr>
                      <tr v-for="w in (detail.wallets || [])" :key="w.id">
                        <td><b>{{ w.unit }}</b></td>
                        <td>{{ w.balance }}</td>
                        <td class="muted">
                          {{ w.frozenBalance }}
                          <button v-if="hasFrozen(w)" type="button" class="btn-link unfreeze-btn" :disabled="unfreezing" @click="unfreezeWallet(w)">解除冻结</button>
                        </td>
                        <td style="text-align: right">
                          <button type="button" class="btn-link" @click="openModal(w)">调余额</button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <div class="info-section">
                <h4>提现加密货币地址</h4>
                <p class="section-hint">当前会员（ID {{ detail.id }} / {{ detail.username }}）的提现地址，与 C 端<strong>该账号</strong>登录后「提现地址」页一致。若在 C 端添加后未显示，请确认 C 端登录的是本会员（同一用户名），并点击上方「刷新」。</p>
                <div class="table-wrap">
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th>币种</th>
                        <th>地址</th>
                        <th>备注</th>
                        <th style="text-align: right">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <template v-for="addr in (detail.withdrawAddresses || [])" :key="addr.id">
                        <tr v-if="addrEditTarget && addrEditTarget.id === addr.id">
                          <td>{{ addr.unit }}</td>
                          <td colspan="2">
                            <input v-model="addrEditForm.address" class="input-inline" placeholder="地址" />
                            <input v-model="addrEditForm.remark" class="input-inline" placeholder="备注" />
                          </td>
                          <td style="text-align: right">
                            <button type="button" class="btn-link" @click="submitAddrEdit" :disabled="addrEditSubmitting">保存</button>
                            <button type="button" class="btn-link" @click="closeAddrEdit">取消</button>
                          </td>
                        </tr>
                        <tr v-else>
                          <td>{{ addr.unit }}</td>
                          <td class="mono cell-address">{{ addr.address }}</td>
                          <td class="muted">{{ addr.remark || '—' }}</td>
                          <td style="text-align: right">
                            <button type="button" class="btn-link" @click="openAddrEdit(addr)">编辑</button>
                            <button type="button" class="btn-link danger" @click="deleteAddr(addr)">删除</button>
                          </td>
                        </tr>
                      </template>
                      <tr v-if="!detail.withdrawAddresses?.length && !addrEditTarget">
                        <td colspan="4" class="no-data-cell">暂无提现地址</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div class="section-footer">
                  <button type="button" class="btn-sm btn-primary" @click="openAddrAdd">添加地址</button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Balance Modal -->
    <div v-if="modalVisible" class="modal-mask" @click.self="closeModal">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">调整余额 ({{ selectedWallet?.unit }})</div>
          <span class="modal-close" @click="closeModal" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>类型</label>
            <select v-model="formData.type" class="input">
              <option :value="0">可用</option>
              <option :value="1">冻结</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>动作</label>
            <select v-model="formData.action" class="input">
              <option :value="0">充值 (+)</option>
              <option :value="1">扣除 (-)</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>数量</label>
            <input v-model="formData.amount" type="number" step="any" min="0" class="input" placeholder="数量" />
          </div>
          <div class="modal-form-item">
            <label>备注</label>
            <input v-model="formData.remark" type="text" class="input" placeholder="备注" />
          </div>
          <p v-if="modalError" class="error-tip">{{ modalError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="closeModal">取消</button>
          <button type="button" class="btn-primary" @click="submitBalanceUpdate" :disabled="modalSubmitting">确认</button>
        </div>
      </div>
    </div>

    <!-- Password Reset Modal -->
    <div v-if="pwdModalVisible" class="modal-mask" @click.self="pwdModalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">重置登录密码</div>
          <span class="modal-close" @click="pwdModalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>新密码（至少 6 位）</label>
            <input v-model="newPassword" type="text" class="input" placeholder="新密码" />
          </div>
          <p v-if="pwdError" class="error-tip">{{ pwdError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="pwdModalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitPwdReset" :disabled="pwdSubmitting">重置</button>
        </div>
      </div>
    </div>

    <!-- Withdraw Password Reset Modal -->
    <div v-if="withdrawPwdModalVisible" class="modal-mask" @click.self="withdrawPwdModalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">重置提现密码</div>
          <span class="modal-close" @click="withdrawPwdModalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <p class="modal-hint">重置后该会员将使用新密码在 C 端「提现」页面进行验证。请将新密码告知会员。</p>
          <div class="modal-form-item">
            <label>新提现密码（至少 6 位）</label>
            <input v-model="withdrawPwdValue" type="password" class="input" placeholder="新提现密码" />
          </div>
          <p v-if="withdrawPwdError" class="error-tip">{{ withdrawPwdError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="withdrawPwdModalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitWithdrawPwdReset" :disabled="withdrawPwdSubmitting">重置</button>
        </div>
      </div>
    </div>

    <!-- Add Withdraw Address Modal -->
    <div v-if="addrAddVisible" class="modal-mask" @click.self="addrAddVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">添加提现地址</div>
          <span class="modal-close" @click="addrAddVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>币种</label>
            <select v-model="addrForm.coinId" class="input">
              <option v-for="c in coins" :key="c.id" :value="c.id">{{ c.unit }}</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>地址</label>
            <input v-model="addrForm.address" type="text" class="input" placeholder="加密货币地址" />
          </div>
          <div class="modal-form-item">
            <label>备注</label>
            <input v-model="addrForm.remark" type="text" class="input" placeholder="选填" />
          </div>
          <p v-if="addrError" class="error-tip">{{ addrError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="addrAddVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitAddrAdd" :disabled="addrAddSubmitting">添加</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.member-detail-page { color: #333; }
.admin-card { border: 1px solid #e8eaec; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 16px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 14px; font-weight: 600; color: #1a202c; }
.card-body { padding: 12px 16px; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 16px; }
.col-left, .col-right { min-width: 0; }

.info-section { margin-bottom: 10px; }
.info-section h4 { margin: 0 0 6px 0; font-size: 12px; font-weight: 600; color: #4a5568; }
.info-dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px 16px; }
.info-item { display: flex; align-items: baseline; font-size: 12px; }
.info-item dt { width: 52px; flex-shrink: 0; color: #718096; }
.info-item dd { margin: 0; font-weight: 500; color: #2d3748; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info-item-edit dd { white-space: normal; display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.edit-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.input-select { padding: 4px 8px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 12px; min-width: 100px; }
.btn-save { background: #2d8cf0; color: #fff; }
.btn-save:disabled { opacity: 0.6; cursor: not-allowed; }
.parent-edit { margin-top: 4px; }
.parent-edit .input-inline { min-width: 160px; }
.muted { color: #718096; }
.parent-link { color: #2d8cf0; text-decoration: none; }
.parent-link:hover { text-decoration: underline; }
.mono { font-family: ui-monospace, monospace; }

.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.status-tag.ok { background: #c6f6d5; color: #22543d; }
.status-tag.err { background: #fed7d7; color: #822727; }

.action-section { margin-top: 8px; padding: 8px 10px; background: #f7fafc; border-radius: 6px; border: 1px solid #edf2f7; }
.action-section h4 { margin: 0 0 6px 0; font-size: 12px; color: #4a5568; }
.action-row { display: flex; gap: 8px; flex-wrap: wrap; }
.hint { margin: 6px 0 0 0; font-size: 11px; color: #718096; }

.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { text-align: left; padding: 6px 10px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 6px 10px; border-top: 1px solid #edf2f7; }
.data-table .no-data-cell { color: #718096; text-align: center; }
.data-table .muted { color: #718096; }
.data-table .cell-address { max-width: 140px; overflow: hidden; text-overflow: ellipsis; }
.unfreeze-btn { margin-left: 8px; }
.input-inline { width: 120px; margin-right: 6px; padding: 4px 8px; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 12px; }

.btn-sm { padding: 4px 10px; border-radius: 4px; font-size: 12px; cursor: pointer; border: none; font-weight: 500; }
.btn-danger { background: #e53e3e; color: #fff; }
.btn-success { background: #38a169; color: #fff; }
.btn-warning { background: #dd6b20; color: #fff; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-link { background: none; border: none; color: #2d8cf0; cursor: pointer; font-size: 12px; padding: 0 4px; }
.btn-link.danger { color: #e53e3e; }
.btn-link:hover { text-decoration: underline; }
.section-footer { margin-top: 6px; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal-wrap { width: 400px; background: #fff; border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); }
.modal-header { padding: 12px 16px; border-bottom: 1px solid #edf2f7; display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-size: 13px; font-weight: 600; }
.modal-body { padding: 16px; }
.modal-form-item { margin-bottom: 12px; }
.modal-form-item label { display: block; margin-bottom: 4px; color: #4a5568; font-size: 12px; }
.input { width: 100%; padding: 6px 10px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; font-size: 13px; }
.input:focus { border-color: #2d8cf0; }
.modal-footer { padding: 12px 16px; background: #f8fafc; text-align: right; border-top: 1px solid #edf2f7; }
.btn-cancel { padding: 6px 14px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; margin-right: 8px; cursor: pointer; font-size: 12px; }
.btn-primary { padding: 6px 18px; background: #2d8cf0; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 12px; }
.error-tip { color: #e53e3e; font-size: 12px; margin-top: 8px; }
.modal-hint { font-size: 12px; color: #718096; margin: 0 0 12px; }
.section-hint { font-size: 11px; color: #718096; margin: 0 0 6px; }
</style>
