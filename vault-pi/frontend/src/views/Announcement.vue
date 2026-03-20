<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '../stores/app'
import { getAnnouncementPage, getAnnouncementDetail } from '../api'
import MobileButton from '../components/mobile/MobileButton.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const app = useAppStore()

const listLoading = ref(false)
const detailLoading = ref(false)
const pageData = ref({ content: [], totalElements: 0, totalPages: 0 })
const detail = ref(null)

const langParam = computed(() => (app.lang === 'en' ? 'EN' : 'CN'))
const hasId = computed(() => !!route.params.id)

async function loadList() {
  listLoading.value = true
  try {
    const data = await getAnnouncementPage({
      pageNo: 1,
      pageSize: 20,
      lang: langParam.value,
    })
    pageData.value = data || { content: [], totalElements: 0, totalPages: 0 }
  } catch (_) {
    pageData.value = { content: [], totalElements: 0, totalPages: 0 }
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(id) {
  if (!id) {
    detail.value = null
    return
  }
  detailLoading.value = true
  try {
    const data = await getAnnouncementDetail(id, langParam.value)
    detail.value = data
  } catch (_) {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function goDetail(id) {
  router.push({ name: 'AnnouncementDetail', params: { id } })
}

function backToList() {
  router.push({ name: 'AnnouncementList' })
}

onMounted(() => {
  if (hasId.value) {
    loadDetail(route.params.id)
  } else {
    loadList()
  }
})

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      loadDetail(id)
    } else {
      loadList()
    }
  }
)

watch(() => app.lang, () => {
  if (hasId.value) {
    loadDetail(route.params.id)
  } else {
    loadList()
  }
})
</script>

<template>
  <div class="announcement">
    <div v-if="!hasId" class="list">
      <h1 class="title">{{ t('uc.announcement.title') }}</h1>
      <div v-if="listLoading" class="loading">{{ t('common.loading') }}</div>
      <ul v-else class="items">
        <li v-for="item in pageData.content" :key="item.id" class="item" @click="goDetail(item.id)">
          <div class="item_title">{{ item.title }}</div>
          <div class="item_meta">{{ item.createTime }}</div>
        </li>
        <li v-if="!pageData.content || pageData.content.length === 0" class="empty">
          {{ t('uc.announcement.noData') }}
        </li>
      </ul>
    </div>

    <div v-else class="detail">
      <MobileButton variant="gray" @click="backToList">
        {{ t('uc.announcement.back') }}
      </MobileButton>
      <div v-if="detailLoading" class="loading">{{ t('common.loading') }}</div>
      <div v-else-if="detail" class="detail_body">
        <h1 class="detail_title">{{ detail.title }}</h1>
        <div class="detail_meta">{{ detail.createTime }}</div>
        <p class="detail_content">
          {{ detail.content }}
        </p>
      </div>
      <div v-else class="empty">
        {{ t('uc.announcement.noData') }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.announcement {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 16px;
  color: #fff;
}
.title {
  font-size: 24px;
  margin-bottom: 24px;
}
.loading {
  color: #828ea1;
  font-size: 14px;
}
.items {
  list-style: none;
  padding: 0;
  margin: 0;
  border-top: 1px solid #27313e;
}
.item {
  padding: 12px 0;
  border-bottom: 1px solid #27313e;
  cursor: pointer;
}
.item:hover .item_title {
  color: #f0a70a;
}
.item_title {
  font-size: 15px;
}
.item_meta {
  font-size: 12px;
  color: #828ea1;
  margin-top: 4px;
}
.empty {
  padding: 24px 0;
  text-align: center;
  color: #828ea1;
}
.detail {
  padding-top: 8px;
}
.back_btn_wrap {
  margin-bottom: 20px;
}
.detail_title {
  font-size: 22px;
  margin-bottom: 8px;
}
.detail_meta {
  font-size: 12px;
  color: #828ea1;
  margin-bottom: 16px;
}
.detail_content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>

