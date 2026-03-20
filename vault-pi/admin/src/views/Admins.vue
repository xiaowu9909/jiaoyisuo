<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminAdminsList, postAdminAdminAdd, postAdminAdminUpdate } from '../api/admin'

const PERMISSION_OPTIONS = [
  { key: 'home', label: '首页' },
  { key: 'member', label: '会员列表' },
  { key: 'member-add', label: '会员列表-添加用户' },
  { key: 'member-operate', label: '会员列表-操作(送彩金/状态等)' },
  { key: 'member-level', label: '会员等级' },
  { key: 'authenticate', label: '实名审核' },
  { key: 'invite', label: '邀请统计' },
  { key: 'announcement', label: '公告管理' },
  { key: 'help', label: '帮助管理' },
  { key: 'advertise', label: '广告管理' },
  { key: 'finance', label: '资产审核' },
  { key: 'finance-stats', label: '财务统计' },
  { key: 'recharge-address', label: '充币地址' },
  { key: 'exchange-coin', label: '交易对列表' },
  { key: 'virtual-market', label: '虚拟盘行情' },
  { key: 'exchange-order', label: '委托订单' },
  { key: 'futures-orders', label: '合约订单监控' },
  { key: 'futures-positions', label: '持仓风险监控' },
  { key: 'activity', label: '优惠活动' },
  { key: 'envelope', label: '红包统计' },
  { key: 'system-params', label: '全局参数配置' },
  { key: 'admins', label: '管理员管理' },
  { key: 'operation-log', label: '操作日志' },
  { key: 'error-log', label: '错误日志' },
  { key: 'bond', label: '保证金记录' },
]

const ADMIN_ROLE_OPTIONS = [
  { value: '', label: '全量（默认）' },
  { value: 'KEFU', label: '客服' },
  { value: 'ZHUGUAN', label: '主管（仅自己推广数据）' },
]

const ROLE_PRESETS = {
  KEFU: {
    permissions: ['home', 'member', 'member-operate', 'authenticate', 'announcement', 'finance', 'virtual-market'],
    adminRole: 'KEFU',
  },
  ZHUGUAN: {
    permissions: ['home', 'member', 'invite'],
    adminRole: 'ZHUGUAN',
  },
  CAIWU: {
    permissions: ['home', 'finance-stats'],
    adminRole: '',
  },
}

const list = ref([])
const loading = ref(false)
const showAdd = ref(false)
const showEdit = ref(false)
const addForm = ref({ username: '', password: '', adminDisplayName: '', permissions: [], adminRole: '', boundMemberId: '' })
const editForm = ref({ id: null, adminDisplayName: '', password: '', permissions: [], adminRole: '', boundMemberId: '' })
const saving = ref(false)
const msg = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await getAdminAdminsList()
  } catch (e) {
    msg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function applyPreset(role) {
  const preset = ROLE_PRESETS[role]
  if (!preset) return
  if (showAdd.value) {
    addForm.value.permissions = [...preset.permissions]
    addForm.value.adminRole = preset.adminRole
  } else {
    editForm.value.permissions = [...preset.permissions]
    editForm.value.adminRole = preset.adminRole
  }
}

function openAdd() {
  addForm.value = { username: '', password: '', adminDisplayName: '', permissions: [], adminRole: '', boundMemberId: '' }
  msg.value = ''
  showAdd.value = true
}

async function submitAdd() {
  const { username, password, adminDisplayName, permissions } = addForm.value
  if (!username || !username.trim()) {
    msg.value = '请填写登录名'
    return
  }
  if (!password || password.length < 6) {
    msg.value = '密码至少 6 位'
    return
  }
  saving.value = true
  msg.value = ''
  const boundId = addForm.value.boundMemberId != null && String(addForm.value.boundMemberId).trim() !== '' ? Number(String(addForm.value.boundMemberId).trim()) : null
  try {
    await postAdminAdminAdd({ username: username.trim(), password, adminDisplayName: (adminDisplayName || '').trim(), permissions: permissions || [], adminRole: addForm.value.adminRole || null, boundMemberId: boundId || null })
    message.success('添加成功')
    showAdd.value = false
    await load()
  } catch (e) {
    msg.value = e.message || '添加失败'
  } finally {
    saving.value = false
  }
}

function openEdit(row) {
  editForm.value = {
    id: row.id,
    adminDisplayName: row.adminDisplayName || '',
    password: '',
    permissions: Array.isArray(row.adminPermissions) ? [...row.adminPermissions] : [],
    adminRole: row.adminRole || '',
    boundMemberId: row.boundMemberId != null ? String(row.boundMemberId) : '',
  }
  msg.value = ''
  showEdit.value = true
}

async function submitEdit() {
  const { id, adminDisplayName, password, permissions } = editForm.value
  saving.value = true
  msg.value = ''
  const boundId = editForm.value.boundMemberId != null && String(editForm.value.boundMemberId).trim() !== '' ? Number(String(editForm.value.boundMemberId).trim()) : null
  try {
    const body = { id, adminDisplayName: (adminDisplayName || '').trim(), permissions: permissions || [], adminRole: editForm.value.adminRole || null, boundMemberId: boundId }
    if (password && password.length >= 6) body.password = password
    await postAdminAdminUpdate(body)
    message.success('保存成功')
    showEdit.value = false
    await load()
  } catch (e) {
    msg.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function togglePerm(permKey, arr) {
  if (!arr) return
  const i = arr.indexOf(permKey)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(permKey)
}

function isPermChecked(permKey, arr) {
  return Array.isArray(arr) && arr.includes(permKey)
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">管理员管理</h2>
      <p class="page-desc">创建二级管理员并为其命名，勾选其可访问的菜单权限；不勾选任何权限表示拥有全部权限（超级管理员）。</p>
      <button class="btn btn-primary" @click="openAdd">+ 添加管理员</button>
    </div>

    <p v-if="msg" class="error-msg">{{ msg }}</p>

    <div class="admin-card table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>登录名</th>
            <th>显示名称</th>
            <th>角色</th>
            <th>绑定前台账号</th>
            <th>权限范围</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.username }}</td>
            <td>{{ row.adminDisplayName || '—' }}</td>
            <td>{{ row.adminRole === 'KEFU' ? '客服' : row.adminRole === 'ZHUGUAN' ? '主管' : '—' }}</td>
            <td>{{ row.boundMemberUsername ? (row.boundMemberUsername + (row.boundMemberUid != null ? ' (UID ' + row.boundMemberUid + ')' : '')) : '—' }}</td>
            <td>{{ (row.adminPermissions && row.adminPermissions.length) ? row.adminPermissions.length + ' 项' : '全部' }}</td>
            <td>
              <button type="button" class="btn btn-sm btn-ghost" @click="openEdit(row)">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!list.length && !loading" class="empty-hint">暂无管理员数据</p>
    </div>

    <!-- 新增弹窗 -->
    <div v-if="showAdd" class="modal-mask" @click.self="showAdd = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <h3 class="modal-title">添加管理员</h3>
          <button type="button" class="modal-close" @click="showAdd = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>登录名</label>
            <input v-model="addForm.username" type="text" placeholder="用于登录" class="form-input" />
          </div>
          <div class="form-item">
            <label>登录密码</label>
            <input v-model="addForm.password" type="password" placeholder="至少 6 位" class="form-input" />
          </div>
          <div class="form-item">
            <label>显示名称</label>
            <input v-model="addForm.adminDisplayName" type="text" placeholder="如：运营管理员" class="form-input" />
          </div>
          <div class="form-item">
            <label>管理员角色</label>
            <select v-model="addForm.adminRole" class="form-input">
              <option v-for="opt in ADMIN_ROLE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <div class="preset-btns">
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('KEFU')">应用客服预设</button>
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('ZHUGUAN')">应用主管预设</button>
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('CAIWU')">应用财务管理员预设</button>
            </div>
          </div>
          <div class="form-item">
            <label>绑定前台账号（可选）</label>
            <input v-model.trim="addForm.boundMemberId" type="text" class="form-input" placeholder="填写前台会员 ID，便于区分部门；可在「会员列表」中查看 ID" />
          </div>
          <div class="form-item">
            <label>可见菜单（勾选可访问的项，不勾选表示全部）</label>
            <div class="perm-grid">
              <label v-for="opt in PERMISSION_OPTIONS" :key="opt.key" class="perm-check">
                <input type="checkbox" :checked="isPermChecked(opt.key, addForm.permissions)" @change="togglePerm(opt.key, addForm.permissions)" />
                {{ opt.label }}
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-ghost" @click="showAdd = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitAdd">{{ saving ? '提交中…' : '添加' }}</button>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showEdit" class="modal-mask" @click.self="showEdit = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <h3 class="modal-title">编辑管理员</h3>
          <button type="button" class="modal-close" @click="showEdit = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>显示名称</label>
            <input v-model="editForm.adminDisplayName" type="text" placeholder="如：运营管理员" class="form-input" />
          </div>
          <div class="form-item">
            <label>新密码（不填则不修改）</label>
            <input v-model="editForm.password" type="password" placeholder="至少 6 位" class="form-input" />
          </div>
          <div class="form-item">
            <label>管理员角色</label>
            <select v-model="editForm.adminRole" class="form-input">
              <option v-for="opt in ADMIN_ROLE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <div class="preset-btns">
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('KEFU')">应用客服预设</button>
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('ZHUGUAN')">应用主管预设</button>
              <button type="button" class="btn btn-small btn-ghost" @click="applyPreset('CAIWU')">应用财务管理员预设</button>
            </div>
          </div>
          <div class="form-item">
            <label>绑定前台账号（可选）</label>
            <input v-model.trim="editForm.boundMemberId" type="text" class="form-input" placeholder="填写前台会员 ID，便于区分部门；清空则解除绑定" />
          </div>
          <div class="form-item">
            <label>可见菜单</label>
            <div class="perm-grid">
              <label v-for="opt in PERMISSION_OPTIONS" :key="opt.key" class="perm-check">
                <input type="checkbox" :checked="isPermChecked(opt.key, editForm.permissions)" @change="togglePerm(opt.key, editForm.permissions)" />
                {{ opt.label }}
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-ghost" @click="showEdit = false">取消</button>
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitEdit">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; width: 100%; }
.page-desc { font-size: 14px; color: #64748b; margin: 0 0 12px 0; }
.error-msg { color: #dc2626; margin-bottom: 12px; font-size: 14px; }
.preset-btns { display: flex; gap: 8px; margin-top: 8px; }
.btn-small { padding: 4px 10px; font-size: 12px; }

.admin-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #f1f5f9; }
.data-table th { background: #f8fafc; font-size: 13px; color: #475569; }
.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
.btn-sm { padding: 6px 12px; font-size: 13px; }
.empty-hint { text-align: center; color: #94a3b8; padding: 24px; margin: 0; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; }
.modal-wrap { background: #fff; border-radius: 12px; min-width: 420px; max-width: 560px; max-height: 90vh; overflow: auto; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #e2e8f0; }
.modal-title { margin: 0; font-size: 16px; font-weight: 600; }
.modal-close { background: none; border: none; font-size: 24px; cursor: pointer; color: #64748b; line-height: 1; }
.modal-body { padding: 20px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 16px 20px; border-top: 1px solid #e2e8f0; }
.form-item { margin-bottom: 16px; }
.form-item label { display: block; font-size: 13px; color: #475569; margin-bottom: 6px; }
.form-input { width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; }
.perm-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.perm-check { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }
</style>
