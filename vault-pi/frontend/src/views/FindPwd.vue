<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { postResetPasswordSendCode, postResetPassword } from '../api'
import { useI18n } from 'vue-i18n'
import MobileButton from '../components/mobile/MobileButton.vue'
import MobileInput from '../components/mobile/MobileInput.vue'

const router = useRouter()
const { t } = useI18n()
const email = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const sendCodeLoading = ref(false)
const submitLoading = ref(false)
const errorMsg = ref('')
const countdown = ref(0)
let countdownTimer = null

async function sendCode() {
  const e = (email.value || '').trim()
  if (!e) {
    errorMsg.value = '请输入邮箱'
    return
  }
  errorMsg.value = ''
  sendCodeLoading.value = true
  try {
    await postResetPasswordSendCode(e)
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
  } catch (e) {
    errorMsg.value = e.message || t('uc.findPwd.failed')
  } finally {
    sendCodeLoading.value = false
  }
}

async function submit() {
  errorMsg.value = ''
  const e = (email.value || '').trim()
  const c = (code.value || '').trim()
  const p = newPassword.value || ''
  const cp = confirmPassword.value || ''
  if (!e) {
    errorMsg.value = t('uc.findPwd.errorEmail')
    return
  }
  if (!c) {
    errorMsg.value = t('uc.findPwd.errorCode')
    return
  }
  if (!p || p.length < 6) {
    errorMsg.value = t('uc.findPwd.errorPwdLen')
    return
  }
  if (p !== cp) {
    errorMsg.value = t('uc.findPwd.errorPwdMatch')
    return
  }
  submitLoading.value = true
  try {
    await postResetPassword({ email: e, code: c, newPassword: p })
    router.replace('/login')
  } catch (e) {
    errorMsg.value = e.message || t('uc.findPwd.failed')
  } finally {
    submitLoading.value = false
  }
}
</script>

<template>
  <div class="find-pwd">
    <div class="card">
      <h1 class="title">{{ t('uc.findPwd.title') }}</h1>
      <p class="hint">{{ t('uc.findPwd.hint') }}</p>
      <div class="form">
        <div class="form-item">
          <MobileInput v-model="email" type="email" :placeholder="t('uc.findPwd.email')" />
        </div>
        <div class="form-item row">
          <MobileInput v-model="code" type="text" :placeholder="t('uc.findPwd.code')" maxlength="6" style="flex:1" />
          <MobileButton
            variant="gray"
            :disabled="sendCodeLoading || countdown > 0"
            @click="sendCode"
          >
            {{ countdown > 0 ? countdown + t('uc.findPwd.retry') : (sendCodeLoading ? t('uc.findPwd.sending') : t('uc.findPwd.getCode')) }}
          </MobileButton>
        </div>
        <div class="form-item">
          <MobileInput v-model="newPassword" type="password" :placeholder="t('uc.findPwd.newPwd')" />
        </div>
        <div class="form-item">
          <MobileInput v-model="confirmPassword" type="password" :placeholder="t('uc.findPwd.confirmNewPwd')" />
        </div>
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <MobileButton block variant="primary" :loading="submitLoading" @click="submit">
          {{ t('uc.findPwd.submit') }}
        </MobileButton>
      </div>
      <p class="back">
        <router-link to="/login">{{ t('uc.findPwd.back') }}</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.find-pwd { min-height: 60vh; display: flex; align-items: center; justify-content: center; padding: 24px; }
.card { width: 100%; max-width: 400px; background: #17212e; border-radius: 8px; padding: 28px; border-top: 4px solid #a77200; }
.title { font-size: 22px; margin: 0 0 8px; color: #fff; text-align: center; }
.hint { color: #828ea1; font-size: 14px; margin: 0 0 24px; text-align: center; }
.form-item.row { display: flex; gap: 8px; align-items: flex-end; }
.error { color: #ef4444; font-size: 13px; margin: 0 0 12px; }
.back { text-align: center; margin-top: 16px; }
.back a { color: #f0a70a; text-decoration: none; font-size: 14px; }
</style>
