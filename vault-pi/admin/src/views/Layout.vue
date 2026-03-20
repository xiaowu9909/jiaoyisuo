<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SvgIcon from '../components/SvgIcon.vue'
import { getAdminMe } from '../api/admin'

const route = useRoute()
const router = useRouter()
const sidebarShrink = ref(false)
const userDropdownOpen = ref(false)
const tagsDropdownOpen = ref(false)
const adminMe = ref(null)
const theme = ref(document.documentElement.getAttribute('data-theme') || 'dark')

// 菜单数据结构（每个叶子带 permission，用于二级管理员可见范围）；顺序：首页→会员管理→币币管理→财务管理→邀请管理→内容管理→主页管理→合约管理→活动管理→红包管理→保证金管理→系统管理
const menuList = [
  { title: '首页', path: '/home', icon: 'home', permission: 'home' },
  {
    title: '会员管理',
    icon: 'member',
    children: [
      { title: '会员列表', path: '/member', permission: 'member' },
      { title: '会员等级', path: '/member-level', permission: 'member-level' },
      { title: '实名审核', path: '/authenticate', permission: 'authenticate' },
    ],
  },
  {
    title: '币币管理',
    icon: 'exchange',
    children: [
      { title: '交易对列表', path: '/exchange-coin', permission: 'exchange-coin' },
      { title: '虚拟盘行情', path: '/virtual-market', permission: 'virtual-market' },
      { title: '趋势设置审计', path: '/virtual-market/audit', permission: 'virtual-market' },
      { title: '委托订单', path: '/exchange-order', permission: 'exchange-order' },
    ],
  },
  {
    title: '财务管理',
    icon: 'finance',
    children: [
      { title: '资产审核', path: '/finance', permission: 'finance' },
      { title: '财务统计', path: '/finance/stats', permission: 'finance-stats' },
      { title: '充币地址', path: '/finance/recharge-address', permission: 'recharge-address' },
    ],
  },
  {
    title: '邀请管理',
    icon: 'invite',
    children: [
      { title: '邀请统计', path: '/invite', permission: 'invite' },
    ],
  },
  {
    title: '内容管理',
    icon: 'content',
    children: [
      { title: '公告管理', path: '/announcement', permission: 'announcement' },
      { title: '帮助管理', path: '/help', permission: 'help' },
      { title: '广告管理', path: '/advertise', permission: 'advertise' },
    ],
  },
  {
    title: '主页管理',
    icon: 'document',
    children: [
      { title: '新手入门配置', path: '/system/getting-start', permission: 'system-params' },
      { title: '关于我们配置', path: '/system/about-brand', permission: 'system-params' },
      { title: '下载APP配置', path: '/system/download-app', permission: 'system-params' },
    ],
  },
  {
    title: '合约管理',
    icon: 'futures',
    children: [
      { title: '合约订单监控', path: '/futures-orders', permission: 'futures-orders' },
      { title: '持仓风险监控', path: '/futures-positions', permission: 'futures-positions' },
    ],
  },
  {
    title: '活动管理',
    icon: 'activity',
    children: [
      { title: '优惠活动', path: '/activity', permission: 'activity' },
    ],
  },
  {
    title: '红包管理',
    icon: 'envelope',
    children: [
      { title: '红包统计', path: '/envelope', permission: 'envelope' },
    ],
  },
  {
    title: '保证金管理',
    icon: 'bond',
    children: [
      { title: '保证金记录', path: '/bond', permission: 'bond' },
    ],
  },
  {
    title: '系统管理',
    icon: 'system',
    children: [
      { title: '全局参数配置', path: '/system', permission: 'system-params' },
      { title: '管理员管理', path: '/system/admins', permission: 'admins' },
      { title: '操作日志', path: '/system/operation-log', permission: 'operation-log' },
      { title: '错误日志', path: '/system/error-log', permission: 'error-log' },
    ],
  },
]

function canSeePermission(perm) {
  const perms = adminMe.value?.adminPermissions
  if (!perms || perms.length === 0) return true
  return perms.includes(perm)
}

const filteredMenuList = computed(() => {
  if (!adminMe.value) return menuList
  const perms = adminMe.value.adminPermissions
  if (!perms || perms.length === 0) return menuList
  return menuList.map(menu => {
    if (!menu.children) return canSeePermission(menu.permission) ? menu : null
    const visibleChildren = menu.children.filter(c => canSeePermission(c.permission))
    if (visibleChildren.length === 0) return null
    return { ...menu, children: visibleChildren }
  }).filter(Boolean)
})

onMounted(async () => {
  try {
    adminMe.value = await getAdminMe()
  } catch (_) {
    adminMe.value = null
  }
})

// 记录展开的一级菜单标题
const expandedMenus = ref(new Set())

function toggleMenu(menu) {
  if (sidebarShrink.value) {
    sidebarShrink.value = false
  }
  
  const title = menu.title
  if (expandedMenus.value.has(title)) {
    expandedMenus.value.delete(title)
  } else {
    // Accordion mode: clear other expanded menus
    expandedMenus.value.clear()
    expandedMenus.value.add(title)
  }
}

// 自动展开包含当前激活路由的父菜单
function autoExpand() {
  menuList.forEach(m => {
    if (m.children?.some(c => route.path.startsWith(c.path))) {
      expandedMenus.value.add(m.title)
    }
  })
}

// 已打开的标签页
const openedTags = ref([
  { title: '首页', path: '/home', name: 'home' },
])

function ensureTag() {
  const path = route.path
  if (path === '/' || path === '/home') return
  const exists = openedTags.value.some((t) => t.path === path)
  if (!exists) {
    const name = route.name || path
    const title = route.meta?.title || String(name)
    openedTags.value.push({ title, path, name })
  }
}

watch(() => route.path, () => {
  ensureTag()
  autoExpand()
}, { immediate: true })

const breadcrumbs = computed(() => {
  const list = [{ title: '首页', path: '/home' }]
  if (route.path === '/' || route.path === '/home') return list
  const title = route.meta?.title || route.name || '页面'
  list.push({ title, path: route.path })
  return list
})

const currentPageTitle = computed(() => {
  if (route.path === '/' || route.path === '/home') return '首页'
  return route.meta?.title || route.name || '页面'
})

function toggleSidebar() {
  sidebarShrink.value = !sidebarShrink.value
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('vaultpi-admin-theme', theme.value)
}

function closeTag(tag) {
  if (tag.path === '/home' || tag.path === '/') return
  const idx = openedTags.value.findIndex((t) => t.path === tag.path)
  if (idx < 0) return
  openedTags.value.splice(idx, 1)
  if (route.path === tag.path) {
    const next = openedTags.value[idx] || openedTags.value[idx - 1] || openedTags.value[0]
    router.push(next?.path || '/home')
  }
}

function goToTag(tag) {
  router.push(tag.path)
  tagsDropdownOpen.value = false
}

function closeAllTags() {
  openedTags.value = [{ title: '首页', path: '/home', name: 'home' }]
  router.push('/home')
  tagsDropdownOpen.value = false
}

function closeOtherTags() {
  const current = { title: currentPageTitle.value, path: route.path, name: route.name }
  openedTags.value = [{ title: '首页', path: '/home', name: 'home' }, current]
  tagsDropdownOpen.value = false
}

// --- Icons removed, using SvgIcon component ---

function onUserAction(action) {
  userDropdownOpen.value = false
  if (action === 'ownSpace') {
    router.push('/profile')
  } else if (action === 'loginout') {
    fetch((import.meta.env.VITE_API_BASE || '/api') + '/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    }).finally(() => router.push('/login'))
  }
}

function onDocumentClick() {
  userDropdownOpen.value = false
  tagsDropdownOpen.value = false
}
onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <div class="main">
    <div class="sidebar-menu-con" :class="{ shrink: sidebarShrink }">
      <div class="logo-con">
        <span class="logo-mark">π</span>
        <span v-show="!sidebarShrink" class="logo-text">Vault Admin</span>
      </div>
      <nav class="sidebar-menu">
        <div v-for="menu in filteredMenuList" :key="menu.title">
          <!-- 一级菜单项 (无子菜单) -->
          <router-link v-if="!menu.children" :to="menu.path" class="menu-item" active-class="active">
            <SvgIcon :name="menu.icon" :size="20" class="menu-icon" />
            <span v-show="!sidebarShrink" class="layout-text">{{ menu.title }}</span>
          </router-link>

          <!-- 一级菜单项 (带子菜单) -->
          <div v-else class="menu-group" :class="{ expanded: expandedMenus.has(menu.title) }">
            <div class="menu-header" @click="toggleMenu(menu)">
              <div class="header-left">
                <SvgIcon :name="menu.icon" :size="20" class="menu-icon" />
                <span v-show="!sidebarShrink" class="layout-text">{{ menu.title }}</span>
              </div>
              <SvgIcon v-show="!sidebarShrink" name="arrow" :size="14" class="arrow-icon" />
            </div>
            
            <!-- 二级菜单列表 (带动画过渡) -->
            <div class="sub-menu-wrapper" :class="{ open: !sidebarShrink && expandedMenus.has(menu.title) }">
              <div class="sub-menu-inner">
                <router-link
                  v-for="sub in menu.children"
                  :key="sub.path"
                  :to="sub.path"
                  class="menu-item sub"
                  active-class="active"
                >
                  <SvgIcon name="dot" :size="6" class="menu-icon sub-dot" />
                  <span class="layout-text">{{ sub.title }}</span>
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </nav>
    </div>

    <div class="main-header-con" :class="{ shrink: sidebarShrink }">
      <div class="main-header">
        <div class="navicon-con">
          <button type="button" class="navicon-btn" :class="{ rotated: sidebarShrink }" aria-label="菜单" @click="toggleSidebar">
            <span class="navicon-bar" />
            <span class="navicon-bar" />
            <span class="navicon-bar" />
          </button>
        </div>
        <div class="header-middle-con">
          <div class="main-breadcrumb">
            <template v-for="(b, i) in breadcrumbs" :key="b.path">
              <router-link v-if="i < breadcrumbs.length - 1" :to="b.path" class="breadcrumb-link">{{ b.title }}</router-link>
              <span v-else class="breadcrumb-current">{{ b.title }}</span>
              <span v-if="i < breadcrumbs.length - 1" class="breadcrumb-sep"> / </span>
            </template>
          </div>
        </div>
        <div class="header-avator-con">
          <button
            type="button"
            class="theme-toggle-btn"
            :aria-label="theme === 'dark' ? '切换为亮色' : '切换为暗色'"
            @click.stop="toggleTheme"
          >
            <SvgIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="16" />
          </button>
          <div class="user-dropdown-wrap">
            <button type="button" class="user-dropdown-btn" :class="{ open: userDropdownOpen }" @click.stop="userDropdownOpen = !userDropdownOpen">
              <span class="main-user-name">{{ adminMe?.adminDisplayName || adminMe?.username || '管理员' }}</span>
              <span class="arrow">▼</span>
            </button>
            <div v-show="userDropdownOpen" class="user-dropdown-menu" @click.stop>
              <a href="javascript:void(0)" class="dropdown-item" @click="onUserAction('ownSpace')">个人中心</a>
              <a href="javascript:void(0)" class="dropdown-item divided" @click="onUserAction('loginout')">退出登录</a>
            </div>
          </div>
          <span class="avator-circle" />
        </div>
      </div>
      <div class="tags-con">
        <div class="tags-inner-scroll-body">
          <span
            v-for="tag in openedTags"
            :key="tag.path"
            class="tag"
            :class="{ 'tag-current': route.path === tag.path }"
            @click="goToTag(tag)"
          >
            {{ tag.title }}
            <span v-if="tag.path !== '/home' && tag.path !== '/'" class="tag-close" @click.stop="closeTag(tag)" aria-label="关闭"><SvgIcon name="close" :size="12" /></span>
          </span>
        </div>
        <div class="close-all-tag-con">
          <div class="tags-dropdown-wrap">
            <button type="button" class="tags-option-btn" :class="{ open: tagsDropdownOpen }" @click.stop="tagsDropdownOpen = !tagsDropdownOpen">
              标签选项 <span class="arrow">▼</span>
            </button>
            <div v-show="tagsDropdownOpen" class="tags-dropdown-menu" @click.stop>
              <a href="javascript:void(0)" class="dropdown-item" @click="closeAllTags">关闭所有</a>
              <a href="javascript:void(0)" class="dropdown-item" @click="closeOtherTags">关闭其他</a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="single-page-con" :class="{ shrink: sidebarShrink }">
      <div class="single-page">
        <div class="content-inner">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.main {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.15), transparent 55%),
    radial-gradient(circle at bottom right, rgba(129, 140, 248, 0.15), transparent 55%),
    #020617;
  color: #e5e7eb;
}
.sidebar-menu-con {
  height: 100%;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 21;
  width: 220px;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow-x: hidden; overflow-y: auto;
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 12px 0 40px rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(28px);
  border-right: 1px solid rgba(148, 163, 184, 0.18);
}
.sidebar-menu-con.shrink {
  width: 76px;
}
.logo-con {
  padding: 20px 16px 18px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}
.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 18px;
  color: #0f172a;
  background: conic-gradient(from 210deg, #38bdf8, #6366f1, #22c55e, #38bdf8);
  box-shadow: 0 0 24px rgba(59, 130, 246, 0.5);
}
.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #e5e7eb;
  white-space: nowrap;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.85;
}

.sidebar-menu { padding: 8px 0; }
.menu-item, .menu-header {
  display: flex; align-items: center; padding: 14px 18px;
  color: #94a3b8; text-decoration: none; font-size: 13px; border: none;
  cursor: pointer; width: 100%; text-align: left; box-sizing: border-box;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  margin: 2px 0;
}
/* 一级菜单项（如首页）：图标与文字左对齐 */
.sidebar-menu > div > .menu-item { justify-content: flex-start; }
.menu-header { justify-content: space-between; }
.header-left { display: flex; align-items: center; }
.menu-item:hover, .menu-header:hover { 
  background: rgba(15, 23, 42, 0.9);
  color: #f9fafb; 
}
.menu-item.active { 
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.22), rgba(129, 140, 248, 0.04)) !important; 
  color: #e0f2fe !important; 
  font-weight: 600; 
  border-right: 3px solid #38bdf8; 
}

.menu-group { margin-bottom: 2px; }
.arrow-icon { 
  width: 14px; height: 14px; 
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1); 
  opacity: 0.4; 
}
.expanded .arrow-icon { transform: rotate(180deg); opacity: 1; color: var(--primary-color); }

/* Accordion Smooth Transition */
.sub-menu-wrapper {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  background: rgba(15, 23, 42, 0.9);
}
.sub-menu-wrapper.open {
  grid-template-rows: 1fr;
}
.sub-menu-inner {
  min-height: 0;
  padding: 4px 0;
}

.menu-item.sub { 
  padding-left: 50px; 
  font-size: 12.5px; 
  color: #64748b;
}
.menu-item.sub:hover { color: #e5e7eb; }
.menu-item.sub.active { color: #38bdf8 !important; }
.sub-dot { width: 4px !important; height: 4px !important; opacity: 0.6; }

.menu-icon { margin-right: 12px; }
/* .menu-icon.sub-dot handled via component props now, but keep margin */
.sub-dot { opacity: 0.6; }

.main-header-con {
  box-sizing: border-box;
  position: fixed;
  width: 100%;
  height: 100px;
  z-index: 20;
  padding-left: 220px;
  transition: padding-left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.main-header-con.shrink {
  padding-left: 76px;
}
.main-header {
  height: 60px;
  background: radial-gradient(circle at top left, rgba(56, 189, 248, 0.22), transparent 55%), rgba(15, 23, 42, 0.96);
  backdrop-filter: blur(22px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.35);
  position: relative;
  z-index: 11;
  display: flex; align-items: center;
}
.navicon-con { margin-left: 10px; }
.navicon-btn {
  width: 40px; height: 40px; display: flex; flex-direction: column; justify-content: center;
  align-items: center; gap: 4px; background: transparent; border: none; cursor: pointer;
}
.navicon-bar { display: block; width: 18px; height: 2px; background: #666; border-radius: 2px; transition: all 0.3s; }
.navicon-btn:hover .navicon-bar { background: var(--primary-color); }
.navicon-btn.rotated .navicon-bar:nth-child(1) { transform: translateY(6px) rotate(45deg); }
.navicon-btn.rotated .navicon-bar:nth-child(2) { opacity: 0; }
.navicon-btn.rotated .navicon-bar:nth-child(3) { transform: translateY(-6px) rotate(-45deg); }

.header-middle-con { flex: 1; padding: 0 20px; display: flex; align-items: center; }
.main-breadcrumb { font-size: 13px; color: #9ca3af; }
.breadcrumb-link { color: #e5e7eb; text-decoration: none; opacity: 0.7; }
.breadcrumb-link:hover { opacity: 1; color: #e0f2fe; }
.breadcrumb-current { color: #f9fafb; font-weight: 500; }

.header-avator-con { display: flex; align-items: center; padding-right: 20px; gap: 15px; }
.theme-toggle-btn {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: rgba(15, 23, 42, 0.8);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #e5e7eb;
}
.theme-toggle-btn:hover {
  background: rgba(15, 23, 42, 1);
  box-shadow: 0 0 18px rgba(148, 163, 184, 0.45);
}
.user-dropdown-btn {
  display: flex; align-items: center; gap: 6px; background: #f3f4f6; border: none;
  color: #0f172a; cursor: pointer; padding: 6px 12px; font-size: 13px; border-radius: 999px;
  background: #e5e7eb;
}
.user-dropdown-btn:hover { background: #e5e7eb; }
.user-dropdown-menu {
  position: absolute; right: 0; top: 100%; margin-top: 8px; min-width: 150px;
  background: #020617;
  border-radius: 10px;
  box-shadow: 0 18px 35px rgba(15, 23, 42, 0.9);
  z-index: 100; padding: 6px 0; border: 1px solid rgba(148, 163, 184, 0.45);
}
.dropdown-item { display: block; padding: 10px 16px; color: #4b5563; text-decoration: none; font-size: 14px; }
.dropdown-item:hover { background: rgba(15, 23, 42, 0.95); color: #e0f2fe; }
.dropdown-item.divided { border-top: 1px solid #f3f4f6; margin-top: 4px; padding-top: 10px; }
.avator-circle { width: 34px; height: 34px; border-radius: 50%; background: linear-gradient(135deg, var(--primary-color), #e96500); flex-shrink: 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }

.tags-con {
  height: 40px;
  background: rgba(15, 23, 42, 0.92);
  border-bottom: 1px solid rgba(148, 163, 184, 0.4);
  display: flex; align-items: center; padding: 0 10px;
}
.tags-inner-scroll-body { flex: 1; overflow-x: auto; display: flex; gap: 6px; scrollbar-width: none; }
.tags-inner-scroll-body::-webkit-scrollbar { display: none; }
.tag {
  display: inline-flex; align-items: center; gap: 6px; padding: 4px 12px; font-size: 11px;
  border-radius: 999px; background: rgba(15, 23, 42, 0.9); color: #9ca3af; cursor: pointer; border: 1px solid rgba(148, 163, 184, 0.5);
}
.tag:hover { background: rgba(30, 64, 175, 0.7); border-color: #38bdf8; color: #e0f2fe; }
.tag-current { background: linear-gradient(90deg, #38bdf8, #6366f1); border-color: transparent; color: #0b1120; }
.tag-close { font-size: 16px; width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; border-radius: 50%; }
.tag-close:hover { background: rgba(0,0,0,0.1); }
.tag-current .tag-close:hover { background: rgba(255,255,255,0.2); }

.tags-option-btn {
  padding: 5px 12px; font-size: 11px; background: transparent; color: #9ca3af; border: 1px solid rgba(148, 163, 184, 0.6); border-radius: 999px; cursor: pointer;
}
.tags-option-btn:hover { border-color: #38bdf8; color: #e0f2fe; }

.single-page-con {
  position: absolute; z-index: 1; top: 100px; right: 0; bottom: 0; overflow: auto;
  background-color: transparent;
  transition: left 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  left: 220px;
}
.single-page-con.shrink {
  left: 76px;
}
.single-page { margin: 20px; }
.content-inner {
  background: rgba(15, 23, 42, 0.85);
  border-radius: 24px;
  padding: 24px 24px 28px;
  min-height: calc(100vh - 160px);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.35);
}
</style>
