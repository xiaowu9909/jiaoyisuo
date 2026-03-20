<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../../stores/app'
import { getUcAccount, postUcAccount, getUcAuthenticateStatus, postUcAuthenticate } from '../../api'
import MobileButton from '../../components/mobile/MobileButton.vue'
import MobileInput from '../../components/mobile/MobileInput.vue'

const { t } = useI18n()
const app = useAppStore()
const account = ref({ username: '', email: '', phone: '', nickname: '', realName: '' })
const nicknameEdit = ref('')
const authStatus = ref(null)
const authForm = ref({
  realName: '',
  idCard: '',
  identityCardImgFront: '',
  identityCardImgReverse: '',
  identityCardImgInHand: '',
})
const certPreview = ref({ front: '', reverse: '', inHand: '' })
const fileFront = ref(null)
const fileReverse = ref(null)
const fileInHand = ref(null)
const loading = ref(true)
const MAX_IMAGE_SIZE = 1200
const JPEG_QUALITY = 0.82

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.onerror = () => reject(new Error('Image read failed'))
    r.readAsDataURL(file)
  })
}

function compressImage(dataUrl) {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => {
      const w = img.naturalWidth
      const h = img.naturalHeight
      let tw = w
      let th = h
      if (w > MAX_IMAGE_SIZE || h > MAX_IMAGE_SIZE) {
        if (w >= h) {
          tw = MAX_IMAGE_SIZE
          th = Math.round((h * MAX_IMAGE_SIZE) / w)
        } else {
          th = MAX_IMAGE_SIZE
          tw = Math.round((w * MAX_IMAGE_SIZE) / h)
        }
      }
      const canvas = document.createElement('canvas')
      canvas.width = tw
      canvas.height = th
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, tw, th)
      try {
        const out = canvas.toDataURL('image/jpeg', JPEG_QUALITY)
        resolve(out)
      } catch (_) {
        resolve(dataUrl)
      }
    }
    img.onerror = () => resolve(dataUrl)
    img.src = dataUrl
  })
}

async function onCertFile(field, e) {
  const file = e.target.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  try {
    let dataUrl = await readFileAsDataUrl(file)
    dataUrl = await compressImage(dataUrl)
    if (field === 'front') {
      authForm.value.identityCardImgFront = dataUrl
      certPreview.value.front = dataUrl
    } else if (field === 'reverse') {
      authForm.value.identityCardImgReverse = dataUrl
      certPreview.value.reverse = dataUrl
    } else {
      authForm.value.identityCardImgInHand = dataUrl
      certPreview.value.inHand = dataUrl
    }
  } catch (_) {
    errorMsg.value = t('uc.account.imageErrorRetry')
  }
  e.target.value = ''
}

function clearCert(field) {
  if (field === 'front') {
    authForm.value.identityCardImgFront = ''
    certPreview.value.front = ''
  } else if (field === 'reverse') {
    authForm.value.identityCardImgReverse = ''
    certPreview.value.reverse = ''
  } else {
    authForm.value.identityCardImgInHand = ''
    certPreview.value.inHand = ''
  }
}
const submitting = ref(false)
const authSubmitting = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

async function load() {
  loading.value = true
  try {
    account.value = await getUcAccount() || {}
    nicknameEdit.value = account.value.nickname || ''
    authStatus.value = await getUcAuthenticateStatus().catch(() => null)
  } catch (_) {
    account.value = {}
  } finally {
    loading.value = false
  }
}

async function submitAuth() {
  if (!authForm.value.realName?.trim() || !authForm.value.idCard?.trim()) {
    errorMsg.value = t('uc.account.realNameIdRequired')
    return
  }
  if (!authForm.value.identityCardImgFront?.trim() || !authForm.value.identityCardImgReverse?.trim() || !authForm.value.identityCardImgInHand?.trim()) {
    errorMsg.value = t('uc.account.certImagesRequired')
    return
  }
  errorMsg.value = ''
  authSubmitting.value = true
  try {
    const body = {
      realName: authForm.value.realName.trim(),
      idCard: authForm.value.idCard.trim(),
      identityCardImgFront: authForm.value.identityCardImgFront.trim(),
      identityCardImgReverse: authForm.value.identityCardImgReverse.trim(),
      identityCardImgInHand: authForm.value.identityCardImgInHand.trim(),
    }
    await postUcAuthenticate(body)
    successMsg.value = t('uc.account.kycSubmitSuccess')
    authStatus.value = await getUcAuthenticateStatus()
  } catch (e) {
    errorMsg.value = e.message || t('uc.account.submitFailed')
  } finally {
    authSubmitting.value = false
  }
}

async function saveNickname() {
  if (!nicknameEdit.value.trim()) return
  errorMsg.value = ''
  successMsg.value = ''
  submitting.value = true
  try {
    await postUcAccount({ nickname: nicknameEdit.value.trim() })
    successMsg.value = t('uc.account.saveSuccess')
    account.value.nickname = nicknameEdit.value.trim()
    if (app.member) {
      app.setMember({ ...app.member, nickname: nicknameEdit.value.trim() })
    }
  } catch (e) {
    errorMsg.value = e.message || t('uc.account.saveFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ t('uc.account.title') }}</h2>
    <div v-if="loading" class="uc-hint">{{ t('common.loading') }}</div>
    <div v-else class="safe-card">
      <dl class="info-list">
        <dt>{{ t('uc.account.username') }}</dt>
        <dd>{{ account.username || '—' }}</dd>
        <dt>{{ t('uc.account.email') }}</dt>
        <dd>{{ account.email || t('uc.account.notBound') }}</dd>
        <dt>{{ t('uc.account.nickname') }}</dt>
        <dd class="nickname-row">
          <MobileInput v-model="nicknameEdit" :placeholder="t('uc.account.nicknamePlaceholder')" />
          <MobileButton variant="primary" :loading="submitting" @click="saveNickname">{{ t('uc.account.save') }}</MobileButton>
        </dd>
      </dl>
      <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="msg success">{{ successMsg }}</p>
    </div>
    <div class="safe-card">
      <h3 class="card-title">{{ t('uc.account.kyc') }}</h3>
      <p v-if="authStatus" class="uc-hint">
        {{ t('uc.account.status') }}: {{ authStatus.status === 'APPROVED' ? t('uc.account.statusApproved') : authStatus.status === 'REJECTED' ? t('uc.account.statusRejected') : authStatus.status === 'PENDING' ? t('uc.account.statusPending') : t('uc.account.statusNone') }}
        <span v-if="authStatus.rejectReason">（{{ authStatus.rejectReason }}）</span>
      </p>
      <div v-if="!authStatus || authStatus.status === 'NONE' || authStatus.status === 'REJECTED'" class="auth-form-wrap">
        <div class="auth-form">
          <div class="form-item-m">
            <MobileInput v-model="authForm.realName" :placeholder="t('uc.account.realName')" />
          </div>
          <div class="form-item-m">
            <MobileInput v-model="authForm.idCard" :placeholder="t('uc.account.idCard')" />
          </div>
        </div>
        <div class="cert-upload-section">
          <h4 class="cert-section-title">{{ t('uc.account.certTitle') }}</h4>
          <p class="cert-hint">{{ t('uc.account.certHint') }}</p>
          <div class="cert-upload-grid">
            <div class="cert-slot">
              <span class="cert-slot-label">{{ t('uc.account.certFront') }}</span>
              <input ref="fileFront" type="file" accept="image/*" class="cert-file-input hidden" @change="onCertFile('front', $event)" />
              <div v-if="certPreview.front" class="cert-preview-wrap" @click="fileFront?.click()">
                <img :src="certPreview.front" :alt="t('uc.account.certFront')" class="cert-preview-img" />
                <button type="button" class="cert-remove" :aria-label="t('uc.account.delete')" @click.stop="clearCert('front')">{{ t('uc.account.delete') }}</button>
              </div>
              <div v-else class="cert-placeholder" @click="fileFront?.click()">
                <span class="cert-placeholder-text">{{ t('uc.account.upload') }}</span>
              </div>
            </div>
            <div class="cert-slot">
              <span class="cert-slot-label">{{ t('uc.account.certBack') }}</span>
              <input ref="fileReverse" type="file" accept="image/*" class="cert-file-input hidden" @change="onCertFile('reverse', $event)" />
              <div v-if="certPreview.reverse" class="cert-preview-wrap" @click="fileReverse?.click()">
                <img :src="certPreview.reverse" :alt="t('uc.account.certBack')" class="cert-preview-img" />
                <button type="button" class="cert-remove" :aria-label="t('uc.account.delete')" @click.stop="clearCert('reverse')">{{ t('uc.account.delete') }}</button>
              </div>
              <div v-else class="cert-placeholder" @click="fileReverse?.click()">
                <span class="cert-placeholder-text">{{ t('uc.account.upload') }}</span>
              </div>
            </div>
            <div class="cert-slot">
              <span class="cert-slot-label">{{ t('uc.account.certHand') }}</span>
              <input ref="fileInHand" type="file" accept="image/*" class="cert-file-input hidden" @change="onCertFile('inHand', $event)" />
              <div v-if="certPreview.inHand" class="cert-preview-wrap" @click="fileInHand?.click()">
                <img :src="certPreview.inHand" :alt="t('uc.account.certHand')" class="cert-preview-img" />
                <button type="button" class="cert-remove" :aria-label="t('uc.account.delete')" @click.stop="clearCert('inHand')">{{ t('uc.account.delete') }}</button>
              </div>
              <div v-else class="cert-placeholder" @click="fileInHand?.click()">
                <span class="cert-placeholder-text">{{ t('uc.account.upload') }}</span>
              </div>
            </div>
          </div>
        </div>
        <MobileButton block variant="primary" :loading="authSubmitting" @click="submitAuth">
          {{ t('uc.account.submitKyc') }}
        </MobileButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-form-wrap { display: flex; flex-direction: column; gap: 20px; }
.auth-form { display: flex; flex-direction: column; gap: 12px; }
.form-item-m { width: 100%; }
.nickname-row { display: flex; gap: 8px; width: 100%; flex-wrap: wrap; }
.nickname-row .m-input { flex: 1; min-width: 140px; }
.cert-upload-section { padding: 20px 0 0; border-top: 1px solid #334155; margin-top: 4px; }
.cert-upload-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
@media (max-width: 560px) { .cert-upload-grid { grid-template-columns: 1fr; } }
.cert-slot { display: flex; flex-direction: column; gap: 8px; }
.cert-slot-label { font-size: 12px; color: #94a3b8; font-weight: 500; }
.cert-file-input.hidden { position: absolute; width: 0; height: 0; opacity: 0; pointer-events: none; }
.cert-placeholder,
.cert-preview-wrap { min-height: 108px; border: 1px dashed #475569; border-radius: 8px; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #0f172a; position: relative; transition: background 0.2s, border-color 0.2s; }
.cert-placeholder:hover,
.cert-preview-wrap:hover { background: #1e2936; border-color: #64748b; }
.cert-placeholder:active,
.cert-preview-wrap:active { background: #334155; }
.cert-placeholder-text { font-size: 13px; color: #64748b; }
.cert-preview-img { width: 100%; height: 100%; min-height: 100px; max-height: 140px; object-fit: contain; border-radius: 6px; display: block; }
.cert-preview-wrap { padding: 6px; }
.cert-remove { position: absolute; top: 6px; right: 6px; padding: 4px 8px; font-size: 12px; border: none; border-radius: 6px; background: rgba(0,0,0,.7); color: #fff; cursor: pointer; }
.cert-remove:hover { background: rgba(0,0,0,.9); }
.btn-auth { align-self: flex-start; margin-top: 4px; }
</style>
