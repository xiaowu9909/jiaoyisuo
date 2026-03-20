<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useI18n } from 'vue-i18n'
import { useDevice } from '../hooks/useDevice'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const { t } = useI18n()
const { isMobile } = useDevice()

onMounted(() => {
  if (!app.isLogin) {
    router.replace('/login?redirect=' + encodeURIComponent(route.fullPath || '/uc'))
  }
})

/* 资产(finance)模块置顶，其次账户、交易 */
const navItems = computed(() => [
  {
    group: 'assets',
    label: t('uc.layout.assets'),
    icon: 'assets',
    children: [
      { path: '/uc', label: t('uc.layout.overview'), exact: true },
      { path: '/uc/record', label: t('uc.layout.record') },
      { path: '/uc/recharge', label: t('uc.layout.recharge') },
      { path: '/uc/withdraw', label: t('uc.layout.withdraw') },
      { path: '/uc/withdraw/address', label: t('uc.layout.withdrawAddress') },
    ],
  },
  {
    group: 'account',
    label: t('uc.layout.account'),
    icon: 'account',
    children: [
      { path: '/uc/safe', label: t('uc.layout.safe') },
      { path: '/uc/account', label: t('uc.layout.accountSettings') },
    ],
  },
  {
    group: 'exchange',
    label: t('uc.layout.exchange'),
    icon: 'exchange',
    children: [
      { path: '/uc/entrust/current', label: t('uc.layout.curEntrust') },
      { path: '/uc/entrust/history', label: t('uc.layout.hisEntrust') },
    ],
  },
])

const isUcRoot = computed(() => route.path === '/uc' || route.path === '/uc/')

function isActive(item) {
  if (item.exact) return isUcRoot.value
  return route.path === item.path || route.path.startsWith(item.path + '/')
}

function goBack() {
  router.push('/uc')
}
</script>

<style src="../uc-buttons.css"></style>
<style src="../uc-layout.css"></style>
<template>
  <div class="uc-wrap">
    <div class="uc-layout">
      <!-- 非首页：返回 + 二级页面（移动端顶部已有全局返回，此处不重复显示） -->
      <template v-if="!isUcRoot">
        <div v-if="!isMobile" class="uc-secondary-header">
          <button type="button" class="uc-back" @click="goBack" aria-label="Back">
            <span class="uc-back-icon">←</span>
            <span>{{ t('header.usercenter') }}</span>
          </button>
        </div>
        <main class="uc-main uc-main-secondary">
          <router-view />
        </main>
      </template>

      <!-- 首页：Finance(资产概览) 在上，User Center 标题 + 模块卡片 在下 -->
      <template v-else>
        <main class="uc-main uc-main-overview">
          <router-view />
        </main>
        <header class="uc-dashboard-header">
          <h1 class="uc-dashboard-title">{{ t('header.usercenter') }}</h1>
        </header>
        <div class="uc-modules-grid">
          <div
            v-for="group in navItems"
            :key="group.group"
            class="uc-module-card"
          >
            <h3 class="uc-module-title">{{ group.label }}</h3>
            <nav class="uc-module-links">
              <router-link
                v-for="item in group.children"
                :key="item.path"
                :to="item.path"
                class="uc-module-link"
                :class="{ active: isActive(item) }"
              >
                {{ item.label }}
              </router-link>
            </nav>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.uc-wrap {
  padding: 72px 5% 48px;
  min-height: 100vh;
  background: #0f172a;
  color: #fff;
}
.uc-layout {
  max-width: 900px;
  margin: 0 auto;
}
.uc-dashboard-header {
  margin-top: 32px;
  margin-bottom: 24px;
}
.uc-dashboard-title {
  font-size: 24px;
  font-weight: 700;
  color: #f8fafc;
  margin: 0;
  letter-spacing: 0.02em;
}
.uc-modules-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}
.uc-module-card {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.uc-module-card:hover {
  border-color: #475569;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}
.uc-module-title {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #334155;
}
.uc-module-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.uc-module-link {
  display: block;
  padding: 10px 12px;
  font-size: 14px;
  color: #e2e8f0;
  text-decoration: none;
  border-radius: 8px;
  transition: background 0.15s, color 0.15s;
}
.uc-module-link:hover {
  background: rgba(248, 247, 245, 0.06);
  color: #f0a70a;
}
.uc-module-link.active {
  background: rgba(240, 167, 10, 0.12);
  color: #f0a70a;
  font-weight: 600;
}
.uc-main {
  min-width: 0;
}
.uc-main-overview {
  padding: 0;
}
.uc-main-secondary {
  padding: 0;
}
.uc-secondary-header {
  margin-bottom: 20px;
}
.uc-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.15s;
}
.uc-back:hover {
  color: #f0a70a;
}
.uc-back-icon {
  font-size: 18px;
  line-height: 1;
}
@media (max-width: 768px) {
  .uc-wrap {
    padding: 60px 16px 32px;
  }
  .uc-dashboard-title {
    font-size: 20px;
  }
  .uc-modules-grid {
    grid-template-columns: 1fr;
    gap: 16px;
    margin-bottom: 24px;
  }
  .uc-module-card {
    padding: 16px;
  }
}
</style>
