<script setup>
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="content">
      <div class="code">404</div>
      <h1 class="title">{{ t('notFound.title') || 'Page Not Found' }}</h1>
      <p class="desc">{{ t('notFound.desc') || 'The page you requested does not exist.' }}</p>
      <button class="home-btn" @click="router.replace('/')">
        {{ t('notFound.back') || 'Back to Home' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}
.content {
  text-align: center;
}
.code {
  font-size: 96px;
  font-weight: 800;
  color: #f0a70a;
  line-height: 1;
  margin-bottom: 16px;
  opacity: 0.85;
}
.title {
  font-size: 24px;
  color: #fff;
  margin: 0 0 12px;
}
.desc {
  font-size: 15px;
  color: #828ea1;
  margin: 0 0 32px;
}
.home-btn {
  padding: 12px 32px;
  background: #f0a70a;
  color: #000;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
.home-btn:hover {
  background: #d4920a;
}
</style>
