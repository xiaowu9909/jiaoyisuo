<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHelpPage } from '../api'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import MobileButton from '../components/mobile/MobileButton.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const app = useAppStore()
const list = ref([])
const total = ref(0)
const loading = ref(true)
const classification = computed(() => route.query.c || '')

async function load() {
  loading.value = true
  try {
    const lang = app.lang === 'en' ? 'EN' : 'CN'
    const data = await getHelpPage({
      pageNo: 1,
      pageSize: 50,
      lang: lang,
      classification: classification.value || undefined,
    })
    list.value = data?.content || []
    total.value = data?.totalElements || 0
  } catch (_) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push({ path: `/help/detail/${id}`, query: route.query.c ? { c: route.query.c } : {} })
}

onMounted(load)
watch(classification, load)
</script>

<template>
  <div class="help-list-page">
    <div class="header-row">
      <MobileButton variant="gray" size="sm" @click="router.back()">{{ t('announcement.back') }}</MobileButton>
      <h1 class="title">{{ classification ? classification : t('uc.help.all') }}</h1>
    </div>
    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <ul v-else class="items">
      <li v-for="item in list" :key="item.id" class="item" @click="goDetail(item.id)">
        <span class="item_title">{{ item.title }}</span>
        <span class="item_time">{{ item.createTime }}</span>
      </li>
      <li v-if="list.length === 0" class="empty">{{ t('uc.help.noArticle') }}</li>
    </ul>
  </div>
</template>

<style scoped>
.header-row { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.title { font-size: 20px; color: #fff; margin: 0; }
.loading { color: #828ea1; }
.items { list-style: none; padding: 0; margin: 0; }
.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 8px;
  background: #27313e;
  border-radius: 4px;
  color: #e5e7eb;
  cursor: pointer;
}
.item:hover { background: #2d3748; }
.item_title { flex: 1; }
.item_time { font-size: 13px; color: #828ea1; margin-left: 12px; }
.empty { color: #828ea1; padding: 20px; }
</style>
