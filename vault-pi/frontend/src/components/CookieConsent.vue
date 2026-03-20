<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const visible = ref(false)
const STORAGE_KEY = 'vaultpi_cookie_consent'

onMounted(() => {
  try {
    if (!localStorage.getItem(STORAGE_KEY)) visible.value = true
  } catch (_) {
    visible.value = true
  }
})

function accept() {
  try {
    localStorage.setItem(STORAGE_KEY, 'accepted')
  } catch (_) {}
  visible.value = false
}

function decline() {
  try {
    localStorage.setItem(STORAGE_KEY, 'declined')
  } catch (_) {}
  visible.value = false
}
</script>

<template>
  <Transition name="slide-up">
    <div v-if="visible" class="cookie-consent" role="dialog" aria-label="Cookie consent">
      <p class="cookie-consent-text">
        {{ t('cookie.message', 'We use cookies to provide services and improve experience. By continuing you agree to our use of cookies. For EU users: we only collect data necessary for the service (GDPR).') }}
      </p>
      <div class="cookie-consent-actions">
        <button type="button" class="cookie-consent-btn decline" @click="decline">
          {{ t('cookie.decline', 'Decline') }}
        </button>
        <button type="button" class="cookie-consent-btn accept" @click="accept">
          {{ t('cookie.accept', 'Accept') }}
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.cookie-consent {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px 20px;
  background: var(--bg-secondary, #1a1d29);
  border-top: 1px solid var(--border-color, #2b2f3d);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  z-index: 9999;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.2);
}
.cookie-consent-text {
  flex: 1;
  min-width: 200px;
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary, #828ea1);
  line-height: 1.5;
}
.cookie-consent-actions {
  display: flex;
  gap: 8px;
}
.cookie-consent-btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: none;
}
.cookie-consent-btn.decline {
  background: transparent;
  color: var(--text-secondary, #828ea1);
  border: 1px solid var(--border-color, #2b2f3d);
}
.cookie-consent-btn.accept {
  background: var(--primary, #3b82f6);
  color: #fff;
}
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
