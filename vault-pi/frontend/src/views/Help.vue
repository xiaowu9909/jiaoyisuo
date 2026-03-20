<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHelpClassifications } from '../api'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'

const router = useRouter()
const { t } = useI18n()
const app = useAppStore()
const classifications = ref([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const lang = app.lang === 'en' ? 'EN' : 'CN'
    classifications.value = await getHelpClassifications(lang) || []
  } catch (_) {
    classifications.value = []
  } finally {
    loading.value = false
  }
}

function goList(c) {
  router.push({ path: '/helplist', query: { c: c || '' } })
}

onMounted(load)
</script>

<template>
  <div class="help-page">
    <h1 class="title">{{ t('uc.help.title') }}</h1>
    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <ul v-else class="cat-list">
      <li v-for="cat in classifications" :key="cat" class="cat-item" @click="goList(cat)">
        {{ cat }}
      </li>
      <li v-if="classifications.length === 0" class="empty">{{ t('uc.help.noCat') }}</li>
    </ul>
    <p class="hint">{{ t('uc.help.hint') }}</p>
  </div>
</template>

<style scoped>
.help-page { padding: 24px; max-width: 800px; margin: 0 auto; }
.title { font-size: 22px; margin: 0 0 20px; color: #fff; }
.loading { color: #828ea1; }
.cat-list { list-style: none; padding: 0; margin: 0; }
.cat-item {
  padding: 14px 16px;
  margin-bottom: 8px;
  background: #27313e;
  border-radius: 4px;
  color: #e5e7eb;
  cursor: pointer;
}
.cat-item:hover { background: #2d3748; }
.empty { color: #828ea1; padding: 20px; }
.hint { color: #828ea1; font-size: 14px; margin-top: 16px; }
</style>

