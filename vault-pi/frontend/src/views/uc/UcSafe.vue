<script setup>
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../../stores/app'
import {
  postUcSafeUpdatePassword,
  getUcWithdrawPasswordStatus,
  postUcSafeSetWithdrawPassword,
} from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'
import MobileInput from '../../components/mobile/MobileInput.vue'

const { t } = useI18n()
const app = useAppStore()
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

// 提现密码（仅允许设置，不允许修改）
const withdrawPasswordStatus = ref({ hasSet: false })
const withdrawNewPwd = ref('')
const withdrawConfirmPwd = ref('')
const withdrawSubmitting = ref(false)
const withdrawError = ref('')
const withdrawSuccess = ref('')

const member = computed(() => app.member)
const securityLevel = computed(() => {
  if (!member.value) return '—'
  if (member.value.email) return t('uc.safe.boundEmail')
  return t('uc.safe.normal')
})

async function loadWithdrawPasswordStatus() {
  try {
    withdrawPasswordStatus.value = await getUcWithdrawPasswordStatus() || { hasSet: false }
  } catch (_) {
    withdrawPasswordStatus.value = { hasSet: false }
  }
}

async function submitPassword() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!oldPassword.value || !newPassword.value) {
    errorMsg.value = t('uc.safe.fillOldNewPwd')
    return
  }
  if (newPassword.value.length < 6) {
    errorMsg.value = t('uc.safe.newPwdMin6')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMsg.value = t('uc.safe.pwdMismatch')
    return
  }
  submitting.value = true
  try {
    await postUcSafeUpdatePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    successMsg.value = t('uc.safe.pwdChangeSuccess')
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    errorMsg.value = e.message || t('uc.safe.pwdChangeFailed')
  } finally {
    submitting.value = false
  }
}

async function submitWithdrawPassword() {
  withdrawError.value = ''
  withdrawSuccess.value = ''
  if (!withdrawNewPwd.value || withdrawNewPwd.value.length < 6) {
    withdrawError.value = t('uc.safe.setWithdrawPwdMin6')
    return
  }
  if (withdrawNewPwd.value !== withdrawConfirmPwd.value) {
    withdrawError.value = t('uc.safe.withdrawPwdMismatch')
    return
  }
  withdrawSubmitting.value = true
  try {
    await postUcSafeSetWithdrawPassword({ newPassword: withdrawNewPwd.value })
    withdrawSuccess.value = t('uc.safe.withdrawPwdSetSuccess')
    withdrawNewPwd.value = ''
    withdrawConfirmPwd.value = ''
    await loadWithdrawPasswordStatus()
  } catch (e) {
    withdrawError.value = e.message || t('uc.safe.withdrawPwdSetFailed')
  } finally {
    withdrawSubmitting.value = false
  }
}

onMounted(loadWithdrawPasswordStatus)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.safe.title') }}</h2>
    <div class="safe-card">
      <h3 class="card-title">{{ t('uc.safe.basicTitle') }}</h3>
      <dl class="info-list">
        <dt>{{ t('uc.safe.username') }}</dt>
        <dd>{{ member?.username || '—' }}</dd>
        <dt>{{ t('uc.safe.email') }}</dt>
        <dd>{{ member?.email || t('uc.account.notBound') }}</dd>
        <dt>{{ t('uc.vipLevel') }}</dt>
        <dd class="vip-level">VIP {{ member?.vipLevel ?? 0 }}</dd>
      </dl>
    </div>
    <div class="safe-card">
      <h3 class="card-title">{{ t('uc.safe.levelTitle') }}</h3>
      <p class="security-level">{{ securityLevel }}</p>
      <h4 class="sub-title">{{ t('uc.safe.changePwd') }}</h4>
      <form class="pwd-form" @submit.prevent="submitPassword">
        <div class="form-item">
          <MobileInput v-model="oldPassword" type="password" :placeholder="t('uc.safe.oldPwd')" />
        </div>
        <div class="form-item">
          <MobileInput v-model="newPassword" type="password" :placeholder="t('uc.safe.newPwd')" />
        </div>
        <div class="form-item">
          <MobileInput v-model="confirmPassword" type="password" :placeholder="t('uc.safe.confirmPwd')" />
        </div>
        <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="msg success">{{ successMsg }}</p>
        <div class="form-item">
          <MobileButton variant="primary" :loading="submitting">{{ t('uc.safe.submit') }}</MobileButton>
        </div>
      </form>
    </div>
    <div class="safe-card">
      <h3 class="card-title">{{ t('uc.safe.withdrawPwdTitle') }}</h3>
      <p class="security-level">{{ withdrawPasswordStatus.hasSet ? t('uc.safe.withdrawPwdSet') : t('uc.safe.withdrawPwdNotSet') }}</p>
      <p class="uc-hint withdraw-hint">{{ t('uc.safe.withdrawPwdHint') }}</p>
      <template v-if="!withdrawPasswordStatus.hasSet">
        <h4 class="sub-title">{{ t('uc.safe.setWithdrawPwd') }}</h4>
        <form class="pwd-form" @submit.prevent="submitWithdrawPassword">
          <div class="form-item">
            <MobileInput v-model="withdrawNewPwd" type="password" :placeholder="t('uc.safe.withdrawPwdPlaceholder')" />
          </div>
          <div class="form-item">
            <MobileInput v-model="withdrawConfirmPwd" type="password" :placeholder="t('uc.safe.confirmPlaceholder')" />
          </div>
          <p v-if="withdrawError" class="msg error">{{ withdrawError }}</p>
          <p v-if="withdrawSuccess" class="msg success">{{ withdrawSuccess }}</p>
          <div class="form-item">
            <MobileButton variant="primary" :loading="withdrawSubmitting">
              {{ t('uc.safe.setWithdrawPwdBtn') }}
            </MobileButton>
          </div>
        </form>
      </template>
    </div>
    <div class="safe-card">
      <h3 class="card-title">{{ t('uc.safe.loginLogTitle') }}</h3>
      <p class="uc-hint">{{ t('uc.safe.noLoginLog') }}</p>
    </div>
  </div>
</template>

<style scoped>
.security-level { margin: 0 0 12px; font-size: 14px; color: #e2e8f0; }
.vip-level { color: #f0a70a; font-weight: 600; }
.sub-title { margin: 20px 0 10px; }
.pwd-form { margin-top: 12px; }
.pwd-form .form-item { margin-bottom: 14px; }
.pwd-form .input { max-width: 100%; }
.withdraw-hint { margin-bottom: 10px; font-size: 12px; }
</style>
