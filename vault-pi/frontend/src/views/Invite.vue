<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '../components/toast'
import { getUcInviteInfo, getUcInviteRank } from '../api'
import { useI18n } from 'vue-i18n'
import MobileButton from '../components/mobile/MobileButton.vue'
import MobileInput from '../components/mobile/MobileInput.vue'

const router = useRouter()
const { t } = useI18n()
const inviteInfo = ref(null)
const rankList = ref([])
const loading = ref(true)
const loggedIn = ref(false)

async function load() {
  loading.value = true
  inviteInfo.value = null
  rankList.value = []
  try {
    const info = await getUcInviteInfo()
    inviteInfo.value = info
    loggedIn.value = true
  } catch (e) {
    loggedIn.value = false
  }
  try {
    rankList.value = await getUcInviteRank(20) || []
  } catch (_) {
    rankList.value = []
  }
  loading.value = false
}

function inviteUrl() {
  const code = inviteInfo.value?.inviteCode
  if (!code) return ''
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  return `${origin}/register?inviteCode=${encodeURIComponent(code)}`
}

function copyUrl() {
  const url = inviteUrl()
  if (!url) return
  try {
    const textArea = document.createElement('textarea')
    textArea.value = url
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    message.success(t('uc.invite.copySuccess'))
  } catch (err) {
    console.error('拷贝失败', err)
    // Fallback for older browsers or if execCommand fails
    prompt('请手动复制', url)
  }
}

function copyCode() {
  if (!inviteInfo.value?.inviteCode) return
  try {
    const textArea = document.createElement('textarea')
    textArea.value = inviteInfo.value.inviteCode
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    message.success(t('uc.invite.copyCodeSuccess'))
  } catch (err) {
    console.error('拷贝失败', err)
    // Fallback for older browsers or if execCommand fails
    prompt('请手动复制', inviteInfo.value.inviteCode)
  }
}

function goRegister() {
  const code = inviteInfo.value?.inviteCode
  router.push(code ? `/register?inviteCode=${encodeURIComponent(code)}` : '/register')
}

onMounted(load)
</script>

<template>
  <div class="invite-page">
    <div class="invite-wrap">
      <h1 class="invite-title">{{ t('uc.invite.title') }}</h1>
      <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
      <template v-else>
        <div v-if="loggedIn && inviteInfo" class="invite-card">
          <h3>{{ t('uc.invite.myInvite') }}</h3>
          <div class="invite-row">
            <span class="label">{{ t('uc.invite.inviteCode') }}</span>
            <span class="value mono">{{ inviteInfo.inviteCode }}</span>
            <MobileButton variant="gray" @click="copyCode">{{ t('uc.invite.copy') }}</MobileButton>
          </div>
          <div class="invite-row">
            <span class="label">{{ t('uc.invite.inviteUrl') }}</span>
            <MobileInput :value="inviteUrl()" readonly style="flex:1" />
            <MobileButton variant="gray" @click="copyUrl">{{ t('uc.invite.copyLink') }}</MobileButton>
          </div>
          <div class="invite-row">
            <span class="label">{{ t('uc.invite.inviteCount') }}</span>
            <span class="value">{{ inviteInfo.inviteCount }}</span>
          </div>
          <p class="invite-hint">{{ t('uc.invite.hint') }}</p>
        </div>
        <div v-else class="invite-card">
          <p class="invite-hint">{{ t('uc.invite.loginHint') }}</p>
          <div class="btn-group-m">
            <MobileButton variant="primary" @click="router.push('/login')">{{ t('uc.invite.goLogin') }}</MobileButton>
            <MobileButton variant="gray" @click="goRegister">{{ t('uc.invite.goRegister') }}</MobileButton>
          </div>
        </div>
        <div class="rank-card">
          <h3>{{ t('uc.invite.rankTitle') }}</h3>
          <table v-if="rankList.length" class="rank-table">
            <thead>
              <tr>
                <th>{{ t('uc.invite.rank') }}</th>
                <th>{{ t('uc.invite.username') }}</th>
                <th>{{ t('uc.invite.count') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, i) in rankList" :key="item.memberId">
                <td>{{ i + 1 }}</td>
                <td>{{ item.username }}</td>
                <td>{{ item.inviteCount }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="empty">{{ t('uc.invite.empty') }}</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.invite-page { min-height: 60vh; padding: 40px 8%; background: #0b1520; color: #fff; }
.invite-wrap { max-width: 640px; margin: 0 auto; }
.invite-title { font-size: 22px; margin: 0 0 24px; }
.loading { padding: 24px; color: #828ea1; }
.invite-card, .rank-card { background: rgba(255,255,255,.06); border-radius: 8px; padding: 20px; margin-bottom: 20px; }
.invite-card h3, .rank-card h3 { margin: 0 0 16px; font-size: 16px; }
.invite-row { display: flex; align-items: flex-end; gap: 12px; margin-bottom: 20px; }
.invite-row .label { min-width: 80px; color: #828ea1; font-size: 13px; margin-bottom: 8px; }
.invite-row .value { flex: 1; font-size: 16px; font-weight: 600; color: #f0a70a; }
.mono { font-family: monospace; }
.invite-hint { color: #828ea1; font-size: 13px; margin: 12px 0 0; line-height: 1.5; }
.btn-group-m { display: flex; gap: 12px; margin-top: 16px; }
.rank-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.rank-table th, .rank-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #333; }
.rank-table th { color: #828ea1; font-weight: 600; }
.empty { color: #828ea1; padding: 16px; margin: 0; }
</style>
