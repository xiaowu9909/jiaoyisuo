<script setup>
import { ref, onMounted } from 'vue'
import { getAdminMe, postAdminMeUpdate } from '../api/admin'

const me = ref(null)
const loading = ref(true)
const errorMsg = ref('')
const successMsg = ref('')

const displayName = ref('')
const newPassword = ref('')
const newPasswordConfirm = ref('')
const profileSubmitting = ref(false)
const pwdSubmitting = ref(false)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    me.value = await getAdminMe()
    displayName.value = me.value?.adminDisplayName ?? me.value?.username ?? ''
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!me.value) return
  profileSubmitting.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await postAdminMeUpdate({ adminDisplayName: displayName.value || '' })
    successMsg.value = '显示名称已保存'
    await load()
  } catch (e) {
    errorMsg.value = e.message || '保存失败'
  } finally {
    profileSubmitting.value = false
  }
}

async function savePassword() {
  const pwd = newPassword.value
  const confirm = newPasswordConfirm.value
  if (!pwd || pwd.length < 6) {
    errorMsg.value = '新密码至少 6 位'
    return
  }
  if (pwd !== confirm) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  pwdSubmitting.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await postAdminMeUpdate({ newPassword: pwd })
    successMsg.value = '密码已修改，请使用新密码登录'
    newPassword.value = ''
    newPasswordConfirm.value = ''
  } catch (e) {
    errorMsg.value = e.message || '修改失败'
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page profile-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">个人中心</span>
      </div>
      <div class="card-body">
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="success">{{ successMsg }}</p>
        <div v-if="loading" class="loading">加载中...</div>
        <template v-else-if="me">
          <div class="profile-section">
            <h4>账号信息</h4>
            <div class="info-row">
              <span class="label">登录名</span>
              <span class="value mono">{{ me.username }}</span>
            </div>
            <div class="info-row edit-row">
              <span class="label">显示名称</span>
              <div class="edit-inline">
                <input v-model="displayName" type="text" class="input" placeholder="用于顶部展示，如：运营管理员" maxlength="64" />
                <button type="button" class="btn btn-primary" :disabled="profileSubmitting" @click="saveProfile">
                  {{ profileSubmitting ? '保存中…' : '保存' }}
                </button>
              </div>
            </div>
            <div class="info-row">
              <span class="label">权限范围</span>
              <span class="value">{{ (me.adminPermissions && me.adminPermissions.length) ? me.adminPermissions.length + ' 项' : '全部' }}</span>
            </div>
          </div>
          <div class="profile-section">
            <h4>修改密码</h4>
            <div class="form-group">
              <label>新密码</label>
              <input v-model="newPassword" type="password" class="input" placeholder="至少 6 位" autocomplete="new-password" />
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input v-model="newPasswordConfirm" type="password" class="input" placeholder="再次输入" autocomplete="new-password" />
            </div>
            <button type="button" class="btn btn-primary" :disabled="pwdSubmitting || !newPassword || !newPasswordConfirm" @click="savePassword">
              {{ pwdSubmitting ? '提交中…' : '修改密码' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); max-width: 560px; }
.card-head { padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }
.profile-section { margin-bottom: 28px; }
.profile-section:last-of-type { margin-bottom: 0; }
.profile-section h4 { font-size: 14px; color: #374151; margin: 0 0 14px 0; padding-bottom: 8px; border-bottom: 1px solid #e5e7eb; }
.info-row { display: flex; align-items: center; margin-bottom: 12px; }
.info-row .label { width: 100px; flex-shrink: 0; color: #6b7280; font-size: 13px; }
.info-row .value { color: #111827; }
.info-row.edit-row { align-items: flex-start; }
.edit-inline { display: flex; align-items: center; gap: 10px; flex: 1; }
.input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; font-size: 13px; width: 220px; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; margin-bottom: 6px; color: #4a5568; font-size: 13px; }
.form-group .input { width: 100%; max-width: 280px; }
.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.mono { font-family: monospace; color: #4b5563; }
.error { color: #e53e3e; font-size: 13px; margin-bottom: 12px; }
.success { color: #38a169; font-size: 13px; margin-bottom: 12px; }
.loading { color: #6b7280; padding: 20px; }
</style>
