<script setup>
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '../../stores/app'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()
const app = useAppStore()

const menuItems = [
  { path: '/admin', label: '仪表盘', icon: '📊' },
  { path: '/admin/members', label: '会员管理', icon: '👥' },
  { path: '/admin/assets', label: '资产管理', icon: '💰' },
  { path: '/admin/futures-orders', label: '合约订单', icon: '📋' },
  { path: '/admin/futures-positions', label: '合约持仓', icon: '📈' },
  { path: '/admin/announcements', label: '公告管理', icon: '📢' },
  { path: '/admin/help', label: '帮助文章', icon: '❓' },
]

const currentPath = computed(() => route.path)

function logout() {
  app.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="sidebar-brand">
        <span class="brand-icon">⚙️</span>
        <span class="brand-text">Vault-Pi 管理</span>
      </div>
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: currentPath === item.path }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <router-link to="/" class="nav-item">
          <span class="nav-icon">🏠</span>
          <span class="nav-label">返回前台</span>
        </router-link>
        <button class="nav-item logout-btn" @click="logout">
          <span class="nav-icon">🚪</span>
          <span class="nav-label">退出登录</span>
        </button>
      </div>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: #0b1520;
  color: #e4e4e7;
  overflow: hidden;
}
.admin-sidebar {
  width: 220px;
  background: #111b27;
  border-right: 1px solid #1e2d3d;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-brand {
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #1e2d3d;
}
.brand-icon { font-size: 22px; }
.brand-text {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, #f0a70a, #f7c948);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  color: #828ea1;
  text-decoration: none;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  border: none;
  background: none;
  width: 100%;
  text-align: left;
}
.nav-item:hover { color: #e4e4e7; background: rgba(255,255,255,0.04); }
.nav-item.active {
  color: #f0a70a;
  background: rgba(240, 167, 10, 0.08);
  border-right: 3px solid #f0a70a;
}
.nav-icon { font-size: 16px; width: 22px; text-align: center; }
.nav-label { font-size: 13px; }
.sidebar-footer {
  border-top: 1px solid #1e2d3d;
  padding: 8px 0;
}
.logout-btn { font-family: inherit; }
.admin-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  min-width: 0;
}
</style>
