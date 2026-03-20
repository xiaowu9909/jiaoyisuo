<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const router = useRouter()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMsg = ref('')

async function handleSubmit() {
  errorMsg.value = ''
  const u = (username.value || '').trim()
  const p = password.value || ''
  if (!u) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (!p) {
    errorMsg.value = '请输入密码'
    return
  }
  submitting.value = true
  try {
    const loginRes = await fetch(`${API_BASE}/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: u, password: p }),
    })
    const loginJson = await loginRes.json()
    if (loginJson.code !== 0) {
      errorMsg.value = loginJson.message || '登录失败'
      return
    }
    const checkRes = await fetch(`${API_BASE}/check/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    const checkJson = await checkRes.json()
    if (checkJson.code !== 0 || !checkJson.data || checkJson.data === false) {
      errorMsg.value = '登录状态异常，请重试'
      return
    }
    if (checkJson.data.role !== 'ADMIN') {
      errorMsg.value = '非管理员账号，请使用管理员账号登录'
      return
    }
    router.replace('/home')
  } catch (e) {
    errorMsg.value = e?.message || '网络错误，请检查后端是否已启动'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="login-card">
      <h1 class="title">Vault π 管理后台</h1>
      <p class="hint">请使用管理员账号登录</p>
      <form @submit.prevent="handleSubmit" class="form">
        <div class="form-item">
          <input
            v-model="username"
            type="text"
            placeholder="用户名"
            autocomplete="username"
            class="input"
          />
        </div>
        <div class="form-item">
          <input
            v-model="password"
            type="password"
            placeholder="密码"
            autocomplete="current-password"
            class="input"
          />
        </div>
        <p v-show="errorMsg" class="error">{{ errorMsg }}</p>
        <button type="submit" class="btn" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>
      <p class="foot">默认账号：admin / admin123（首次运行自动创建，登录后请及时修改密码）</p>
    </div>
  </div>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at top right, #2d3748, #1a202c);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: rgba(33, 36, 44, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.title { 
  font-size: 24px; 
  font-weight: 800; 
  margin: 0 0 10px; 
  color: #fff; 
  text-align: center;
  letter-spacing: 1px;
}
.hint { 
  color: #94a3b8; 
  font-size: 14px; 
  margin: 0 0 32px; 
  text-align: center;
}
.form-item { margin-bottom: 20px; }
.input {
  width: 100%;
  padding: 12px 16px;
  font-size: 15px;
  border: 1px solid #334155;
  border-radius: 8px;
  background: #1e293b;
  color: #fff;
  box-sizing: border-box;
  transition: all 0.2s;
}
.input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(240, 167, 10, 0.2);
}
.input::placeholder { color: #64748b; }
.error { 
  color: #f87171; 
  font-size: 13px; 
  margin: -10px 0 15px; 
  background: rgba(239, 68, 68, 0.1);
  padding: 8px 12px;
  border-radius: 6px;
}
.btn {
  width: 100%;
  padding: 12px;
  font-size: 16px;
  font-weight: 700;
  background: var(--primary-color);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(240, 167, 10, 0.3);
}
.btn:hover:not(:disabled) { 
  background: var(--primary-hover); 
  transform: translateY(-1px);
  box-shadow: 0 6px 15px rgba(240, 167, 10, 0.4);
}
.btn:active:not(:disabled) { transform: translateY(0); }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.foot { 
  color: #64748b; 
  font-size: 12px; 
  margin: 24px 0 0; 
  text-align: center;
  line-height: 1.6;
}
</style>
