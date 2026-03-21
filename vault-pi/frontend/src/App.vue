<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from './stores/app'
import { getSiteLogoConfig } from './api'
import { useDevice } from './hooks/useDevice'
import { applyFaviconFromConfig, setPageTitleBaseC, syncCTabTitle } from './utils/siteBranding'
import { resolveUploadUrlForDisplay } from './utils/uploadAssetUrl'
import CookieConsent from './components/CookieConsent.vue'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const app = useAppStore()
const { isMobile } = useDevice()

/** C 端全局 Logo 配置（B 端可修改），无则使用默认路径 */
const siteLogoConfig = ref(null)
/** 配置加载后刷新一次，避免浏览器缓存旧 Logo 图 */
const logoBust = ref(0)
let configPollTimer = null

function logoSrcWithBust(url, fallback) {
  const raw = resolveUploadUrlForDisplay((url ?? '').trim())
  if (!raw) return fallback
  const sep = raw.includes('?') ? '&' : '?'
  return `${raw}${sep}cb=${logoBust.value}`
}

onMounted(async () => {
  // 刷新后内存无 CSRF，但会话 Cookie 可能仍有效；先拉取 /check/login 写回 csrfToken，避免合约/现货等 POST 403
  if (app.isLogin) {
    try {
      await app.checkLogin()
    } catch (_) {}
  }
  siteLogoConfig.value = await getSiteLogoConfig()
  logoBust.value = Date.now()
  if (siteLogoConfig.value) {
    applyFaviconFromConfig(siteLogoConfig.value.faviconUrl)
    const c = String(siteLogoConfig.value.pageTitleC ?? '').trim()
    setPageTitleBaseC(c || 'Vault π')
  } else {
    setPageTitleBaseC('Vault π')
  }
  syncCTabTitle(route.meta?.title)
  // Poll for config changes every 60s so B-end updates reflect without page reload
  configPollTimer = setInterval(async () => {
    try {
      const cfg = await getSiteLogoConfig()
      if (cfg) {
        siteLogoConfig.value = cfg
        logoBust.value = Date.now()
        applyFaviconFromConfig(cfg.faviconUrl)
        const c = String(cfg.pageTitleC ?? '').trim()
        setPageTitleBaseC(c || 'Vault π')
      }
    } catch (_) {}
  }, 60000)
  if (isMobile.value && typeof window !== 'undefined') {
    try {
      window.__ssc = window.__ssc || {}
      window.__ssc.setting = { ...(window.__ssc.setting || {}), hideIcon: true }
    } catch (_) {}
  }
})

onUnmounted(() => {
  if (configPollTimer) clearInterval(configPollTimer)
})

const navOpen = ref(false)
const userOpen = ref(false)
const langOpen = ref(false)

const isLogin = computed(() => app.isLogin)
const member = computed(() => app.member)
const isLoading = computed(() => app.isLoading)
const isExchange = computed(() => route.path.startsWith('/exchange'))
/** 仅交易对页禁用整页滚动；/exchange 列表页允许下滑 */
const isExchangePairPage = computed(() => /^\/exchange\/[^/]+$/.test(route.path))
const isHome = computed(() => route.path === '/')

/** 移动端二级页：显示返回按钮，不显示 logo */
const showMobileBack = computed(() => {
  const p = route.path
  if (p.startsWith('/uc/') && p !== '/uc' && p !== '/uc/') return true
  if (/^\/exchange\/[^/]+$/.test(p)) return true
  return false
})
const mobileBackTo = computed(() => {
  const p = route.path
  if (p.startsWith('/uc')) return '/uc'
  if (p.startsWith('/exchange/')) return '/exchange'
  return '/'
})
function mobileBack() {
  router.push(mobileBackTo.value)
}

function strpo(str) {
  if (!str) return ''
  return str.length > 4 ? str.slice(0, 4) + '···' : str
}

function toggleNav() {
  navOpen.value = !navOpen.value
}


function goLogout() {
  app.logout()
  userOpen.value = false
  router.push('/')
}

function closeMenus() {
  navOpen.value = false
  userOpen.value = false
  langOpen.value = false
}

function switchLang(l) {
  app.setLang(l)
  locale.value = l
  langOpen.value = false
}

watch(
  () => app.lang,
  (val) => {
    locale.value = val
  },
  { immediate: true }
)

watch(
  () => [route.path, route.meta?.title],
  () => syncCTabTitle(route.meta?.title),
  { immediate: true },
)
</script>

<template>
  <div class="page-view">
    <div class="page-content" :class="{ 'no-scroll': isExchangePairPage }">
      <!-- Mobile Header：左上角不显示 logo；二级页显示返回 -->
      <header v-if="isMobile" class="mobile-layout-header">
        <div class="header-left">
          <button v-if="showMobileBack" type="button" class="mobile-back-btn" aria-label="Back" @click="mobileBack">
            <span class="mobile-back-icon">←</span>
          </button>
        </div>
        <div class="header-right">
          <div class="header_lang_wrap">
            <button type="button" class="lang_btn" @click="langOpen = !langOpen">
               <span>{{ app.languageValue }}</span>
            </button>
            <div v-show="langOpen" class="lang_drop">
              <button type="button" @click="switchLang('en')">EN</button>
              <button type="button" @click="switchLang('es')">ES</button>
              <button type="button" @click="switchLang('fr')">FR</button>
              <button type="button" @click="switchLang('de')">DE</button>
              <button type="button" @click="switchLang('it')">IT</button>
            </div>
          </div>
          <template v-if="!isLogin">
            <router-link to="/login" class="mobile-login-btn">{{ t('common.login') }}</router-link>
          </template>
          <template v-else>
            <div class="mobile-user-icon" @click="router.push('/uc/safe')">
              <i class="icon-user-circle"></i>
            </div>
          </template>
        </div>
      </header>

      <!-- Desktop Header -->
      <header v-if="!isMobile" class="layout">
        <div class="layout-ceiling">
          <router-link to="/" class="layout-logo" @click="closeMenus">
            <img :src="logoSrcWithBust(siteLogoConfig?.headerLogoUrl, '/images/logo.png')" alt="logo" class="layout-logo-img" />
          </router-link>
          <div class="layout-ceiling-main">
            <!-- Desktop Nav -->
            <nav class="header_nav">
            </nav>
            
            <button type="button" class="header_nav_mobile_triggle" aria-label="menu" @click="toggleNav" />
            
            <div class="header_lang_wrap">
              <button type="button" class="lang_btn" @click="langOpen = !langOpen">
                 <span style="color:#828ea1; font-size:14px; display:flex; align-items:center; gap:4px">
                   {{ app.languageValue }} <span style="font-size:10px">▼</span>
                 </span>
              </button>
              <div v-show="langOpen" class="lang_drop">
                <button type="button" @click="switchLang('en')">English</button>
                <button type="button" @click="switchLang('es')">Español</button>
                <button type="button" @click="switchLang('fr')">Français</button>
                <button type="button" @click="switchLang('de')">Deutsch</button>
                <button type="button" @click="switchLang('it')">Italiano</button>
              </div>
            </div>

            <div class="rr login-container">
              <template v-if="isLogin">
                <router-link to="/uc" class="mymsg" @click="closeMenus">{{ t('header.usercenter') }}</router-link>
                <div class="user_drop_wrap">
                  <button type="button" class="user_btn" @click="userOpen = !userOpen">
                    <span class="user_name">{{ strpo(member?.username) }}</span>
                    <span class="arrow">▼</span>
                  </button>
                  <div v-show="userOpen" class="user_drop">
                    <router-link to="/exchange" @click="closeMenus">{{ t('header.exchange') }}</router-link>
                    <button type="button" @click="goLogout">{{ t('common.logout') }}</button>
                  </div>
                </div>
              </template>
              <template v-else>
                <router-link to="/login" class="nav_login" @click="closeMenus">{{ t('common.login') }}</router-link>
                <router-link to="/register" class="nav_register" @click="closeMenus">{{ t('common.register') }}</router-link>
              </template>
            </div>
          </div>
        </div>
      </header>

      <main class="main-wrap" :class="{ 'no-scroll': isExchangePairPage }" @click="closeMenus">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </main>

      <footer v-if="!isExchange" class="footer">
        <div class="footer_content">
          <img :src="logoSrcWithBust(siteLogoConfig?.footerLogoUrl, '/images/logo-bottom.png')" alt="logo" class="footer_logo" />
          <div class="footer_text">
            <p class="footer_gsmc">Vault π</p>
            <p class="footer_copy">Copyright © Vault π (Vault314.com) All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>

    <aside v-show="navOpen" class="drawer" @click.self="toggleNav">
      <div class="drawer_inner">

        
        <div class="drawer_divider"></div>
        <template v-if="!isLogin">
          <router-link to="/login" @click="toggleNav">{{ t('common.login') }}</router-link>
          <router-link to="/register" @click="toggleNav">{{ t('common.register') }}</router-link>
        </template>
        <template v-else>
          <router-link to="/uc" @click="toggleNav">{{ t('header.usercenter') }}</router-link>
          <button type="button" class="drawer_logout" @click="goLogout">{{ t('common.logout') }}</button>
        </template>
      </div>
    </aside>

    <!-- 全局请求 loading 遮罩 -->
    <div v-show="isLoading" class="global-loading-overlay" aria-hidden="true">
      <div class="global-loading-spinner"></div>
    </div>

    <!-- Cookie 确认（GDPR 友好）：欧洲用户首次访问可同意/拒绝 -->
    <CookieConsent />

    <!-- Mobile Bottom Tab Bar -->
    <nav class="mobile-tab-bar">
      <router-link to="/" class="tab-item" exact-active-class="active">
        <i class="icon-home"></i>
        <span>{{ t('header.home') }}</span>
      </router-link>
      <router-link to="/exchange" class="tab-item" active-class="active">
        <i class="icon-exchange"></i>
        <span>{{ t('header.exchange') }}</span>
      </router-link>
      <router-link to="/uc" class="tab-item" active-class="active">
        <i class="icon-user"></i>
        <span>{{ t('header.usercenter') }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.page-view {
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  background: #0b1520;
  color: #fff;
  display: flex;
  flex-direction: column;
}
.page-content {
  position: relative;
  padding-top: 60px;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.page-content.no-scroll {
  height: 100vh;
  height: 100dvh;
  max-height: 100vh;
  max-height: 100dvh;
  overflow: hidden;
}
.layout {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: #172636;
  box-shadow: 0 0 5px 5px rgba(0, 0, 0, 0.1);
}
.layout-ceiling {
  padding: 5px 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.layout-logo {
  width: 220px;
  height: 48px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  text-decoration: none;
}
.layout-logo-img {
  max-width: 100%;
  max-height: 48px;
  object-fit: contain;
  display: block;
}
.layout-ceiling-main {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.header_nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: auto;
}
.header_nav .nav-item {
  padding: 0 16px;
  color: #828ea1;
  text-decoration: none;
  font-size: 14px;
}
.header_nav .nav-item:hover,
.header_nav .nav-item.router-link-active {
  color: #f0a70a;
}
.header_nav_mobile_triggle {
  display: none;
  width: 36px;
  height: 36px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23ccc'%3E%3Cpath d='M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z'/%3E%3C/svg%3E") center/24px no-repeat;
  border: none;
  cursor: pointer;
}
.header_lang_wrap {
  position: relative;
}
.lang_btn {
  padding: 4px 8px;
  background: transparent;
  border: none;
  cursor: pointer;
}
.lang_btn .lang-img {
  width: 24px;
  height: 24px;
  display: block;
}
.lang_drop {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  min-width: 140px;
  background: #27313e;
  border-radius: 4px;
  padding: 8px 0;
  z-index: 200;
}
.lang_drop button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 16px;
  background: none;
  border: none;
  color: #ccc;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}
.lang_drop button:hover {
  color: #f0a70a;
}
.lang_drop .lang-img {
  width: 20px;
  height: 20px;
}
.rr {
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 1px solid #273c55;
  padding-left: 12px;
}
.mymsg {
  color: #828ea1;
  text-decoration: none;
  font-size: 14px;
  padding-right: 12px;
  border-right: 1px solid #828ea1;
  margin-right: 8px;
}
.mymsg:hover {
  color: #fff;
}
.user_drop_wrap {
  position: relative;
}
.user_btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: none;
  border: none;
  color: #828ea1;
  cursor: pointer;
  font-size: 14px;
}
.user_btn:hover {
  color: #fff;
}
.user_btn .arrow {
  font-size: 10px;
}
.user_drop {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  min-width: 120px;
  background: #27313e;
  border-radius: 4px;
  padding: 8px 0;
  z-index: 200;
}
.user_drop a,
.user_drop button {
  display: block;
  width: 100%;
  padding: 8px 16px;
  background: none;
  border: none;
  color: #ccc;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
  text-decoration: none;
}
.user_drop a:hover,
.user_drop button:hover {
  color: #f0a70a;
}
.nav_login {
  color: #828ea1;
  text-decoration: none;
  padding: 0 12px;
  font-size: 14px;
  border-right: 1px solid #273c55;
}
.nav_login:hover {
  color: #fff;
}
.nav_register {
  color: #f0a70a;
  text-decoration: none;
  padding: 0 12px;
  font-size: 14px;
}
.nav_register:hover {
  color: #fff;
}
.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.main-wrap.no-scroll {
  overflow: hidden;
}
.footer {
  background: #0b1520;
  border-top: 1px solid #243051;
  padding: 40px 5% 30px;
  margin-top: 40px;
}
.footer_content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 24px;
}
.footer_logo {
  display: block;
  flex-shrink: 0;
}
.footer_text {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.footer_gsmc {
  letter-spacing: 2px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.footer_copy {
  color: #828ea1;
  font-size: 12px;
  margin: 0;
}
.footer_right {
  display: flex;
  flex-wrap: wrap;
  gap: 40px;
  margin-left: 5%;
  padding-left: 5%;
  border-left: 1px solid #243051;
}
.footer_right ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.footer_title {
  font-weight: 600;
  margin-bottom: 12px;
  color: #fff;
}
.footer_right li a {
  color: #828ea1;
  text-decoration: none;
  font-size: 14px;
  display: block;
  margin-bottom: 6px;
}
.footer_right li a:hover {
  color: #f0a70a;
}
.footer_tip {
  color: #666;
  font-size: 12px;
}
.drawer {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.5);
}
.drawer_inner {
  position: absolute;
  top: 0;
  right: 0;
  width: 240px;
  height: 100%;
  background: #172636;
  padding: 60px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.drawer_inner a {
  color: #bdc2ca;
  text-decoration: none;
  padding: 12px;
  text-align: left;
}
.drawer_inner a:hover {
  color: #f0a70a;
}
.drawer_lang {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #27313e;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.drawer_lang button {
  padding: 8px 12px;
  background: none;
  border: none;
  color: #bdc2ca;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
}
.drawer_lang button:hover {
  color: #f0a70a;
}
.drawer_divider {
  width: 100%;
  height: 1px;
  background: #273c55;
  margin: 10px 0;
}
.drawer_logout {
  background: none;
  border: none;
  color: #bdc2ca;
  text-align: left;
  padding: 12px;
  font-size: 16px;
  cursor: pointer;
}
.drawer_logout:hover {
  color: #f0a70a;
}
.drawer_logout:hover {
  color: #f0a70a;
}

/* Mobile Header Styles */
.mobile-layout-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: #172636;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  z-index: 200;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}
.mobile-logo {
  height: 32px;
  object-fit: contain;
}
.header-left {
  min-width: 44px;
  display: flex;
  align-items: center;
}
.mobile-back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin: -8px 0 0 -8px;
  background: none;
  border: none;
  color: #e2e8f0;
  font-size: 24px;
  cursor: pointer;
  transition: color 0.15s;
}
.mobile-back-btn:hover {
  color: #f0a70a;
}
.mobile-back-icon {
  line-height: 1;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.mobile-login-btn {
  background: #f0a70a;
  color: #0f172a;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}
.mobile-user-icon {
  font-size: 24px;
  color: #828ea1;
  cursor: pointer;
}
.icon-user-circle {
  display: inline-block;
  width: 28px;
  height: 28px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23828ea1'%3E%3Cpath d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}

/* Mobile Tab Bar Styles */
.mobile-tab-bar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: #172636;
  border-top: 1px solid #273c55;
  z-index: 250;
  justify-content: space-around;
  align-items: center;
  padding-bottom: env(safe-area-inset-bottom);
}
.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #828ea1;
  text-decoration: none;
  font-size: 11px;
  gap: 2px;
  flex: 1;
}
.tab-item.active {
  color: #f0a70a;
}
.tab-item i {
  width: 24px;
  height: 24px;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}
/* Inline SVGs for icons */
.icon-home { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23828ea1'%3E%3Cpath d='M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z'/%3E%3C/svg%3E"); }
.icon-exchange { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23828ea1'%3E%3Cpath d='M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z'/%3E%3C/svg%3E"); }
.icon-user { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23828ea1'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E"); }

.tab-item.active .icon-home { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23f0a70a'%3E%3Cpath d='M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z'/%3E%3C/svg%3E"); }
.tab-item.active .icon-exchange { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23f0a70a'%3E%3Cpath d='M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z'/%3E%3C/svg%3E"); }
.tab-item.active .icon-user { background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23f0a70a'%3E%3Cpath d='M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'/%3E%3C/svg%3E"); }

.global-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}
.global-loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(240, 167, 10, 0.3);
  border-top-color: #f0a70a;
  border-radius: 50%;
  animation: global-loading-spin 0.8s linear infinite;
}
@keyframes global-loading-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .mobile-tab-bar {
    display: flex;
  }
  .page-view {
    padding-bottom: 60px; /* Space for tab bar */
  }
  .page-content {
    padding-top: 56px; /* Space for mobile header */
  }
  .layout {
    display: none; /* Hide desktop header */
  }
  .footer {
    padding-bottom: 80px; /* Extra space for tab bar */
  }
}
</style>
