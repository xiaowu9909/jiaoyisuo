<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAdminMemberPage, getAdminMe, postAdminMemberAdd, postAdminMemberBonus } from '../api/admin'

const router = useRouter()
const adminMe = ref(null)
const list = ref([])
const total = ref(0)
const loading = ref(false)
const errorMsg = ref('')
const searchKey = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = 20

// 添加用户弹窗
const addModalVisible = ref(false)
const addSubmitting = ref(false)
const addError = ref('')
const addForm = ref({ username: '', password: '', userType: 'NORMAL', referrerInviteCode: '' })

// 送彩金弹窗
const bonusModalVisible = ref(false)
const bonusSubmitting = ref(false)
const bonusError = ref('')
const bonusForm = ref({ memberId: null, memberDisplay: '', amount: '', remark: '送彩金' })

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminMemberPage(currentPage.value, pageSize, searchKey.value, statusFilter.value)
    list.value = data.content || []
    total.value = data.totalElements || 0
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function formatTime(str) {
  if (!str) return '-'
  try {
    return new Date(str).toLocaleString('zh-CN')
  } catch (_) {
    return str
  }
}

function onSearch() {
  currentPage.value = 1
  load()
}

function changePage(p) {
  currentPage.value = p
  load()
}

function goDetail(m) {
  router.push('/member/detail/' + m.id)
}

function exportExcel() {
  errorMsg.value = '导出功能需后端支持'
}

function openAddModal() {
  addForm.value = { username: '', password: '', userType: 'NORMAL', referrerInviteCode: '' }
  addError.value = ''
  addModalVisible.value = true
}

function closeAddModal() {
  addModalVisible.value = false
}

function openBonusModal(m) {
  bonusForm.value = { memberId: m.id, memberDisplay: `${m.username}${m.uid != null ? ' (UID ' + m.uid + ')' : ''}`, amount: '', remark: '送彩金' }
  bonusError.value = ''
  bonusModalVisible.value = true
}

function closeBonusModal() {
  bonusModalVisible.value = false
}

async function submitBonus() {
  const { memberId, amount, remark } = bonusForm.value
  if (!memberId) {
    bonusError.value = '请选择会员'
    return
  }
  const a = parseFloat(amount)
  if (!amount || isNaN(a) || a <= 0) {
    bonusError.value = '请输入有效金额（大于 0）'
    return
  }
  bonusError.value = ''
  bonusSubmitting.value = true
  try {
    await postAdminMemberBonus({ memberId, amount: String(amount), remark: remark || '送彩金' })
    closeBonusModal()
    load()
  } catch (e) {
    bonusError.value = e.message || '操作失败'
  } finally {
    bonusSubmitting.value = false
  }
}

async function submitAddUser() {
  const { username, password, userType, referrerInviteCode } = addForm.value
  if (!username || !username.trim()) {
    addError.value = '请输入用户名'
    return
  }
  if (!password || password.length < 6) {
    addError.value = '密码至少 6 位'
    return
  }
  addError.value = ''
  addSubmitting.value = true
  try {
    const body = { username: username.trim(), password, userType: userType || 'NORMAL' }
    if (referrerInviteCode && referrerInviteCode.trim()) body.referrerInviteCode = referrerInviteCode.trim()
    await postAdminMemberAdd(body)
    closeAddModal()
    load()
  } catch (e) {
    addError.value = e.message || '添加失败'
  } finally {
    addSubmitting.value = false
  }
}

const canAddUser = ref(true)
const canOperate = ref(true)

onMounted(async () => {
  try {
    adminMe.value = await getAdminMe()
    const perms = adminMe.value?.adminPermissions || []
    canAddUser.value = perms.length === 0 || perms.includes('member-add')
    canOperate.value = perms.length === 0 || perms.includes('member-operate')
  } catch (_) {
    canAddUser.value = true
    canOperate.value = true
  }
  load()
})
</script>

<template>
  <div class="admin-page member-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">会员管理</span>
        <div style="display: flex; gap: 8px">
          <button v-if="canAddUser" type="button" class="btn btn-small btn-success btn-with-icon" @click="openAddModal">
            <SvgIcon name="userAdd" :size="16" class="btn-icon" /> 添加用户
          </button>
          <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load">
            <SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新
          </button>
        </div>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <div class="search-wrapper">
            <input
              v-model="searchKey"
              type="text"
              class="search-input"
              placeholder="搜索用户名/邮箱/手机号..."
              @keyup.enter="onSearch"
            />
            <span class="label">状态:</span>
            <select v-model="statusFilter" class="form-select">
              <option value="">全部状态</option>
              <option value="NORMAL">正常运行</option>
              <option value="ILLEGAL">账户异常</option>
            </select>
            <button type="button" class="btn btn-info" @click="onSearch">
              查询
            </button>
          </div>
          <div class="btns-wrapper">
            <button type="button" class="btn btn-success btn-with-icon" @click="exportExcel">
              <SvgIcon name="download" :size="16" class="btn-icon" /> 导出报表
            </button>
          </div>
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width: 40px"><input type="checkbox" /></th>
                <th>UID</th>
                <th>会员信息</th>
                <th>邀请码</th>
                <th>姓名/手机</th>
                <th>VIP等级</th>
                <th>累计充值(USDT)</th>
                <th>注册时间</th>
                <th>状态</th>
                <th>推荐人</th>
                <th style="text-align: right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="11" class="loading-cell">数据加载中...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="11" class="no-data-cell">未找到匹配的会员数据</td>
              </tr>
              <tr v-for="m in list" :key="m.id">
                <td><input type="checkbox" /></td>
                <td><span style="font-family: monospace; color: #6b7280">{{ m.uid != null ? m.uid : '—' }}</span></td>
                <td>
                  <div style="font-weight: 600; color: #111827">{{ m.username }}</div>
                  <div style="font-size: 11px; color: #9ca3af">{{ m.email || '无邮箱' }}</div>
                </td>
                <td><span style="font-family: monospace; font-size: 12px; color: #4b5563">{{ m.inviteCode || '—' }}</span></td>
                <td>
                  <div>{{ m.realName || '未实名' }}</div>
                  <div style="font-size: 11px; color: #9ca3af">{{ m.phone || '—' }}</div>
                </td>
                <td>
                  <span class="vip-tag">VIP{{ m.vipLevel != null ? m.vipLevel : 0 }}</span>
                </td>
                <td style="font-size: 12px; color: #374151">{{ m.totalRecharge != null ? Number(m.totalRecharge) : 0 }}</td>
                <td style="font-size: 12px; color: #6b7280">{{ formatTime(m.registrationTime) }}</td>
                <td>
                  <span class="status-tag" :class="m.status === 'NORMAL' ? 'normal' : 'illegal'">
                    {{ m.status === 'NORMAL' ? '正常' : '异常' }}
                  </span>
                </td>
                <td>
                  <template v-if="m.parentId != null">
                    <span v-if="m.parentUsername" style="color: #374151">{{ m.parentUsername }}</span>
                    <span v-if="m.parentUid != null" style="font-size: 11px; color: #6b7280; margin-left: 4px">({{ m.parentUid }})</span>
                    <span v-if="!m.parentUsername && m.parentUid == null">#{{ m.parentId }}</span>
                  </template>
                  <span v-else>—</span>
                </td>
                <td class="action-cell" style="justify-content: flex-end">
                  <button type="button" class="btn-link primary" @click="goDetail(m)">详情</button>
                  <button v-if="canOperate" type="button" class="btn-link bonus" @click="openBonusModal(m)">送彩金</button>
                  <button v-if="canOperate" type="button" class="btn-link">拉黑</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="page-wrapper">
          <div class="pagination">
            <span class="page-info">共 <b>{{ total }}</b> 条数据，当前第 {{ currentPage }} 页</span>
            <div style="display: flex; gap: 8px">
              <button type="button" class="page-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
              <button type="button" class="page-btn" :disabled="currentPage >= (Math.ceil(total / pageSize) || 1)" @click="changePage(currentPage + 1)">下一页</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 送彩金弹窗 -->
    <div v-if="bonusModalVisible" class="modal-mask" @click.self="closeBonusModal">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">送彩金</div>
          <span class="modal-close" @click="closeBonusModal" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>会员</label>
            <input :value="bonusForm.memberDisplay" type="text" class="input" readonly disabled />
          </div>
          <div class="modal-form-item">
            <label>赠送金额 (USDT)</label>
            <input v-model="bonusForm.amount" type="number" step="any" min="0" class="input" placeholder="大于 0" />
          </div>
          <div class="modal-form-item">
            <label>备注</label>
            <input v-model="bonusForm.remark" type="text" class="input" placeholder="选填，后台有记录" />
          </div>
          <p class="modal-hint">赠送金额将增加用户可用余额，不参与充值统计，流水类型为 BONUS。</p>
          <p v-if="bonusError" class="error-tip">{{ bonusError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="closeBonusModal">取消</button>
          <button type="button" class="btn-primary" @click="submitBonus" :disabled="bonusSubmitting">{{ bonusSubmitting ? '提交中...' : '确认赠送' }}</button>
        </div>
      </div>
    </div>

    <!-- 添加用户弹窗 -->
    <div v-if="addModalVisible" class="modal-mask" @click.self="closeAddModal">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">添加用户</div>
          <span class="modal-close" @click="closeAddModal" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>用户名</label>
            <input v-model="addForm.username" type="text" class="input" placeholder="登录用户名（唯一）" />
          </div>
          <div class="modal-form-item">
            <label>密码</label>
            <input v-model="addForm.password" type="password" class="input" placeholder="至少 6 位" />
          </div>
          <div class="modal-form-item">
            <label>用户类型</label>
            <select v-model="addForm.userType" class="input">
              <option value="NORMAL">正常用户（参与统计）</option>
              <option value="INTERNAL">内部用户（不参与统计，6 位专属邀请码）</option>
            </select>
          </div>
          <div class="modal-form-item">
            <label>推荐人邀请码（选填）</label>
            <input v-model="addForm.referrerInviteCode" type="text" class="input" placeholder="填写推荐人的邀请码，新用户将绑定该推荐人" maxlength="32" />
          </div>
          <p v-if="addError" class="error-tip">{{ addError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="closeAddModal">取消</button>
          <button type="button" class="btn-primary" @click="submitAddUser" :disabled="addSubmitting">{{ addSubmitting ? '提交中...' : '确定添加' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-wrap { background: #fff; border-radius: 8px; min-width: 360px; max-width: 420px; box-shadow: 0 8px 24px rgba(0,0,0,0.15); }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; border-bottom: 1px solid #eee; }
.modal-title { font-size: 15px; font-weight: 600; }
.modal-body { padding: 18px; }
.modal-form-item { margin-bottom: 14px; }
.modal-form-item label { display: block; font-size: 13px; color: #374151; margin-bottom: 4px; }
.modal-form-item .input { width: 100%; padding: 8px 12px; font-size: 14px; border: 1px solid #d1d5db; border-radius: 6px; box-sizing: border-box; }
.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 12px 18px; border-top: 1px solid #eee; }
.btn-cancel { padding: 8px 16px; border: 1px solid #d1d5db; background: #fff; border-radius: 6px; cursor: pointer; }
.btn-primary { padding: 8px 16px; background: #3b82f6; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.error-tip { color: #dc2626; font-size: 13px; margin-top: 8px; }
.user-type-tag { display: inline-flex; padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 500; }
.user-type-tag.normal { background: #eff6ff; color: #2563eb; }
.user-type-tag.internal { background: #f3f4f6; color: #6b7280; }
.btn-link.bonus { color: #059669; }
.btn-link.bonus:hover { background: #ecfdf5; }
.modal-hint { font-size: 12px; color: #6b7280; margin-top: 8px; }

.member-page { padding: 10px 0; }
.admin-card { margin-bottom: 20px; }
.function-wrapper { 
  display: flex; 
  flex-wrap: wrap; 
  justify-content: space-between; 
  align-items: center; 
  gap: 16px;
  margin-bottom: 20px; 
}
.search-wrapper { display: flex; align-items: center; gap: 12px; }
.search-input { width: 320px; padding: 8px 14px; font-size: 14px; }
.label { font-size: 14px; color: var(--text-secondary); font-weight: 500; }
.form-select { padding: 8px 12px; font-size: 14px; min-width: 120px; }

.btn-info { background: #3b82f6; color: #fff; }
.btn-info:hover { background: #2563eb; }
.btn-success { background: #10b981; color: #fff; }
.btn-success:hover { background: #059669; }

.status-tag {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}
.status-tag.normal { background: #ecfdf5; color: #059669; }
.status-tag.illegal { background: #fef2f2; color: #dc2626; }
.vip-tag { display: inline-flex; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: #fef3c7; color: #b45309; }

.action-cell { display: flex; gap: 8px; }
.btn-link { 
  padding: 4px 8px; 
  border-radius: 4px; 
  font-size: 13px; 
  font-weight: 500;
  text-decoration: none;
  background: transparent;
  border: none;
  cursor: pointer;
}
.btn-link.primary { color: #3b82f6; background: #eff6ff; }
.btn-link.primary:hover { background: #dbeafe; }

.page-wrapper { margin-top: 24px; padding: 0 4px; }
.pagination { display: flex; align-items: center; justify-content: flex-end; gap: 20px; }
.page-btn { 
  padding: 6px 14px; 
  font-size: 13px; 
  font-weight: 500; 
  color: #4b5563;
  border: 1px solid #e5e7eb; 
  background: #fff;
  border-radius: 6px;
}
.page-btn:not(:disabled):hover { border-color: var(--primary-color); color: var(--primary-color); }
.page-info { font-size: 13px; color: #6b7280; }
</style>
