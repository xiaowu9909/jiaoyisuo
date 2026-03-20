import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'

const routes = [
  { path: '/login', name: 'AdminLogin', component: () => import('../views/Login.vue'), meta: { title: '登录', public: true } },
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '首页' } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'member', name: 'Member', component: () => import('../views/Member.vue'), meta: { title: '会员管理' } },
      { path: 'member-level', name: 'MemberLevel', component: () => import('../views/MemberLevel.vue'), meta: { title: '会员等级' } },
      { path: 'member/detail/:id', name: 'MemberDetail', component: () => import('../views/MemberDetail.vue'), meta: { title: '会员详情' } },
      { path: 'authenticate', name: 'Authenticate', component: () => import('../views/Authenticate.vue'), meta: { title: '实名审核' } },
      { path: 'invite', name: 'Invite', component: () => import('../views/InviteStat.vue'), meta: { title: '邀请管理' } },
      { path: 'advertise', name: 'Advertise', component: () => import('../views/Advertise.vue'), meta: { title: '广告管理' } },
      { path: 'help', name: 'Help', component: () => import('../views/HelpManage.vue'), meta: { title: '帮助管理' } },
      { path: 'announcement', name: 'Announcement', component: () => import('../views/Announcement.vue'), meta: { title: '公告管理' } },
      { path: 'finance', name: 'Finance', component: () => import('../views/Finance.vue'), meta: { title: '财务管理' } },
      { path: 'finance/stats', name: 'FinanceStats', component: () => import('../views/FinanceStats.vue'), meta: { title: '财务统计' } },
      { path: 'finance/recharge-address', name: 'RechargeAddress', component: () => import('../views/RechargeAddress.vue'), meta: { title: '充币地址' } },
      { path: 'exchange-coin', name: 'ExchangeCoin', component: () => import('../views/ExchangeCoin.vue'), meta: { title: '交易对' } },
      { path: 'virtual-market', name: 'VirtualMarket', component: () => import('../views/VirtualMarketControl.vue'), meta: { title: '虚拟盘行情' } },
      { path: 'virtual-market/audit', name: 'VirtualTrendAudit', component: () => import('../views/VirtualTrendAudit.vue'), meta: { title: '虚拟盘趋势审计' } },
      { path: 'exchange-order', name: 'ExchangeOrder', component: () => import('../views/ExchangeOrder.vue'), meta: { title: '订单管理' } },
      { path: 'futures-orders', name: 'FuturesOrders', component: () => import('../views/FuturesOrders.vue'), meta: { title: '合约订单' } },
      { path: 'futures-positions', name: 'FuturesPositions', component: () => import('../views/FuturesPositions.vue'), meta: { title: '合约持仓' } },
      { path: 'activity', name: 'Activity', component: () => import('../views/ActivityManage.vue'), meta: { title: '活动管理' } },
      { path: 'envelope', name: 'Envelope', component: () => import('../views/EnvelopeManage.vue'), meta: { title: '红包管理' } },
      { path: 'system', name: 'System', component: () => import('../views/System.vue'), meta: { title: '系统配置' } },
      { path: 'system/getting-start', name: 'GettingStart', component: () => import('../views/GettingStart.vue'), meta: { title: '新手入门配置' } },
      { path: 'system/about-brand', name: 'AboutBrand', component: () => import('../views/AboutBrand.vue'), meta: { title: '关于我们配置' } },
      { path: 'system/download-app', name: 'AppDownload', component: () => import('../views/AppDownload.vue'), meta: { title: '下载APP配置' } },
      { path: 'system/admins', name: 'Admins', component: () => import('../views/Admins.vue'), meta: { title: '管理员管理' } },
      { path: 'system/operation-log', name: 'OperationLog', component: () => import('../views/OperationLog.vue'), meta: { title: '操作日志' } },
      { path: 'system/error-log', name: 'ErrorLog', component: () => import('../views/ErrorLog.vue'), meta: { title: '错误日志' } },
      { path: 'bond', name: 'Bond', component: () => import('../views/Placeholder.vue'), meta: { title: '保证金管理' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta?.public) return true
  try {
    const res = await fetch(`${API_BASE}/check/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    const json = await res.json()
    if (json.code !== 0 || !json.data || json.data === false) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (json.data.role !== 'ADMIN') {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  } catch (_) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
