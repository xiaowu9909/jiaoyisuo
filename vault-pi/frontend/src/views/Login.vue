<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { postLogin, postRegister } from '../api'
import MobileButton from '../components/mobile/MobileButton.vue'
import MobileInput from '../components/mobile/MobileInput.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const app = useAppStore()

const user = ref('')
const password = ref('')
const submitting = ref(false)
const errorMsg = ref('')

const isRegister = computed(() => route.path === '/register')

const email = ref('')
const username = ref('')
const confirmPassword = ref('')
const inviteCode = ref((route.query.inviteCode || '').trim())

async function handleSubmit() {
  errorMsg.value = ''
  if (isRegister.value) {
    const e = (email.value || '').trim()
    const u = (username.value || '').trim()
    const p = password.value || ''
    const cp = confirmPassword.value || ''
    const inv = (inviteCode.value || '').trim() || undefined
    if (!e) {
      errorMsg.value = t('uc.login.registerEmailValidate')
      return
    }
    if (!u) {
      errorMsg.value = t('uc.login.registerUsernameValidate')
      return
    }
    if (!p) {
      errorMsg.value = t('uc.login.pwdvalidate1')
      return
    }
    if (p.length < 6) {
      errorMsg.value = t('uc.login.pwdvalidate2')
      return
    }
    if (p !== cp) {
      errorMsg.value = t('uc.login.confirmpwdvalidate')
      return
    }
    submitting.value = true
    try {
      await postRegister(e, u, p, inv)
      router.replace('/login')
    } catch (err) {
      errorMsg.value = err.message || t('uc.login.validatemsg')
    } finally {
      submitting.value = false
    }
    return
  }
  const u = (user.value || '').trim()
  const p = password.value || ''
  if (!u) {
    errorMsg.value = t('uc.login.loginvalidate')
    return
  }
  if (!p) {
    errorMsg.value = t('uc.login.pwdvalidate1')
    return
  }
  if (p.length < 6) {
    errorMsg.value = t('uc.login.pwdvalidate2')
    return
  }
  submitting.value = true
  try {
    const data = await postLogin(u, p)
    app.setMember(data)
    router.replace(route.query.redirect || '/')
  } catch (e) {
    errorMsg.value = e.message || t('uc.login.validatemsg')
  } finally {
    submitting.value = false
  }
}

function onKeyup(ev) {
  if (ev.key === 'Enter') handleSubmit()
}
</script>

<template>
  <div class="login_form">
    <div class="login_right">
      <div class="login_title">{{ isRegister ? t('common.register') : t('uc.login.login') }}</div>
      <form @submit.prevent="handleSubmit" class="form_inline">
        <template v-if="isRegister">
          <div class="form_item">
            <MobileInput
              v-model="email"
              type="email"
              :placeholder="t('uc.login.emailplaceholder')"
              autocomplete="email"
            />
          </div>
          <div class="form_item">
            <MobileInput
              v-model="username"
              type="text"
              :placeholder="t('uc.login.usernameplaceholder')"
              autocomplete="username"
            />
          </div>
          <div class="form_item">
            <MobileInput
              v-model="password"
              type="password"
              :placeholder="t('uc.login.pwdtip')"
              autocomplete="new-password"
            />
          </div>
          <div class="form_item">
            <MobileInput
              v-model="confirmPassword"
              type="password"
              :placeholder="t('uc.login.confirmpwdtip')"
              autocomplete="new-password"
            />
          </div>
          <div class="form_item">
            <MobileInput
              v-model="inviteCode"
              type="text"
              :placeholder="t('uc.login.invitecodetip')"
              autocomplete="off"
              @keyup="onKeyup"
            />
          </div>
        </template>
        <template v-else>
          <div class="form_item">
            <MobileInput
              v-model="user"
              type="text"
              :placeholder="t('uc.login.usertip')"
              autocomplete="username"
            />
          </div>
          <div class="form_item">
            <MobileInput
              v-model="password"
              type="password"
              :placeholder="t('uc.login.pwdtip')"
              autocomplete="current-password"
              @keyup="onKeyup"
            />
          </div>
        </template>
        <p v-show="errorMsg" class="notice">{{ errorMsg }}</p>
        <p class="forget_row">
          <router-link v-if="!isRegister" to="/findPwd" class="forget_link">{{ t('uc.login.forget') }}</router-link>
        </p>
        <div class="form_item">
          <MobileButton block variant="primary" :loading="submitting" @click="handleSubmit">
            {{ isRegister ? t('common.register') : t('uc.login.login') }}
          </MobileButton>
        </div>
        <div class="to_register">
          <span>{{ isRegister ? t('uc.login.goregister') : t('uc.login.noaccount') }}</span>
          <router-link :to="isRegister ? '/login' : '/register'">
            {{ isRegister ? t('uc.login.login') : t('uc.login.goregister') }}
          </router-link>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login_form {
  background: #0b1520 url(/images/login_bg.png) no-repeat center center;
  background-size: cover;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
}
@media (max-width: 768px) {
  .login_form {
    padding: 40px 10px;
    min-height: auto;
  }
}
.login_right {
  padding: 20px 30px 24px;
  background: #17212e;
  width: 350px;
  max-width: 100%;
  border-top: 4px solid #f0ac19;
  border-radius: 5px;
  box-sizing: border-box;
}
.login_title {
  height: 70px;
  color: #fff;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.form_inline {
  margin: 0;
}
.form_item {
  margin-bottom: 20px;
}
.notice {
  color: #f15057;
  font-size: 12px;
  margin: 0 0 8px;
  min-height: 18px;
}
.forget_row {
  height: 30px;
  margin: 0 0 8px;
}
.forget_link {
  color: #979797;
  float: right;
  padding-right: 10px;
  font-size: 12px;
  text-decoration: none;
}
.forget_link:hover {
  color: #f0a70a;
}
.login_btn {
  width: 100%;
  padding: 12px;
  font-size: 18px;
  background-color: #f0ac19;
  border: none;
  border-radius: 5px;
  color: #fff;
  cursor: pointer;
  margin-bottom: 8px;
}
.login_btn:hover:not(:disabled) {
  background-color: #f0a70a;
}
.login_btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.to_register {
  overflow: hidden;
  font-size: 12px;
  margin-top: 12px;
}
.to_register span {
  float: left;
  color: #828ea1;
}
.to_register a {
  float: right;
  color: #f0ac19;
  text-decoration: none;
}
.to_register a:hover {
  color: #f0a70a;
}
</style>
