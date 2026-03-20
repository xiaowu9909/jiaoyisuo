<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHelpDetail } from '../api'
import { useI18n } from 'vue-i18n'
import MobileButton from '../components/mobile/MobileButton.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const detail = ref(null)
const loading = ref(true)

async function load() {
  const id = route.params.id
  if (!id) {
    detail.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    detail.value = await getHelpDetail(id)
  } catch (_) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function back() {
  router.push({ path: '/helplist', query: route.query.c ? { c: route.query.c } : {} })
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <div class="help-detail-page">
    <div class="back-btn-wrap">
      <MobileButton variant="gray" @click="back">{{ t('uc.help.back') }}</MobileButton>
    </div>
    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <template v-else-if="detail">
      <h1 class="title">{{ detail.title }}</h1>
      <div class="meta">{{ detail.createTime }}</div>
      <div class="content" v-html="detail.content"></div>
    </template>
    <div v-else class="empty">{{ t('uc.help.articleNotFound') }}</div>
  </div>
</template>

<style scoped>
.help-detail-page { padding: 24px; max-width: 800px; margin: 0 auto; }
.back-btn-wrap { margin-bottom: 20px; }
.title { font-size: 22px; margin: 0 0 12px; color: #fff; }
.meta { color: #828ea1; font-size: 14px; margin-bottom: 20px; }
.content { color: #d1d5db; line-height: 1.6; white-space: pre-wrap; }
.content :deep(a) { color: #a77200; }
.loading, .empty { color: #828ea1; }
</style>
