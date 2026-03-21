import { createRouter, createWebHistory } from 'vue-router'
import { useAppStore } from '../stores/app'

const routes = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '首页' } },
  { path: '/index', redirect: '/' },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  { path: '/login/returnUrl/:returnUrl', name: 'LoginReturn', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'Register', component: () => import('../views/Login.vue'), meta: { title: '注册' } },
  { path: '/findPwd', name: 'FindPwd', component: () => import('../views/FindPwd.vue'), meta: { title: '找回密码' } },
  { path: '/exchange', name: 'ExchangeList', component: () => import('../views/ExchangeList.vue'), meta: { title: '交易' } },
  { path: '/exchange/:pair', name: 'Exchange', component: () => import('../views/Exchange.vue'), meta: { title: '交易' } },
  { path: '/announcement', name: 'AnnouncementList', component: () => import('../views/Announcement.vue'), meta: { title: '公告' } },
  { path: '/announcement/:id', name: 'AnnouncementDetail', component: () => import('../views/Announcement.vue'), meta: { title: '公告详情' } },
  { path: '/notice', name: 'Notice', component: () => import('../views/Announcement.vue'), meta: { title: '公告' } },
  { path: '/help', name: 'Help', component: () => import('../views/Help.vue'), meta: { title: '帮助中心' } },
  { path: '/helplist', name: 'HelpList', component: () => import('../views/HelpList.vue'), meta: { title: '帮助分类' } },
  { path: '/help/detail/:id', name: 'HelpDetail', component: () => import('../views/HelpDetail.vue'), meta: { title: '帮助详情' } },
  {
    path: '/uc',
    component: () => import('../views/UcLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Uc', component: () => import('../views/Uc.vue'), meta: { title: '资产' } },
      { path: 'money', name: 'UcMoney', component: () => import('../views/Uc.vue'), meta: { title: '资产' } },
      { path: 'safe', name: 'UcSafe', component: () => import('../views/uc/UcSafe.vue'), meta: { title: '安全设置' } },
      { path: 'account', name: 'UcAccount', component: () => import('../views/uc/UcAccount.vue'), meta: { title: '账户' } },
      { path: 'record', name: 'UcRecord', component: () => import('../views/uc/UcRecord.vue'), meta: { title: '流水' } },
      { path: 'recharge', name: 'UcRecharge', component: () => import('../views/uc/UcRecharge.vue'), meta: { title: '充值' } },
      { path: 'withdraw', name: 'UcWithdraw', component: () => import('../views/uc/UcWithdraw.vue'), meta: { title: '提现' } },
      { path: 'withdraw/address', name: 'UcWithdrawAddress', component: () => import('../views/uc/UcWithdrawAddress.vue'), meta: { title: '提现地址' } },
      { path: 'entrust/current', name: 'UcEntrustCurrent', component: () => import('../views/uc/UcEntrustCurrent.vue'), meta: { title: '当前委托' } },
      { path: 'entrust/history', name: 'UcEntrustHistory', component: () => import('../views/uc/UcEntrustHistory.vue'), meta: { title: '历史委托' } },
      { path: 'ai/stream', name: 'AIStatusStream', component: () => import('../views/uc/AIStatusStream.vue'), meta: { title: 'AI量化引擎' } },
      { path: 'ai/orders', name: 'AIOrderList', component: () => import('../views/uc/AIOrderList.vue'), meta: { title: 'AI订单记录' } },
      { path: 'ai/subscribe', name: 'AISubscribe', component: () => import('../views/uc/AISubscribe.vue'), meta: { title: 'AI订阅中心' } },
    ],
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { title: '管理仪表盘' } },
      { path: 'members', name: 'AdminMembers', component: () => import('../views/admin/Members.vue'), meta: { title: '会员管理' } },
      { path: 'assets', name: 'AdminAssets', component: () => import('../views/admin/Assets.vue'), meta: { title: '资产管理' } },
      { path: 'futures-orders', name: 'AdminFuturesOrders', component: () => import('../views/admin/FuturesOrders.vue'), meta: { title: '合约订单' } },
      { path: 'futures-positions', name: 'AdminFuturesPositions', component: () => import('../views/admin/FuturesPositions.vue'), meta: { title: '合约持仓' } },
      { path: 'announcements', name: 'AdminAnnouncements', component: () => import('../views/admin/Announcements.vue'), meta: { title: '公告管理' } },
      { path: 'help', name: 'AdminHelp', component: () => import('../views/admin/HelpArticles.vue'), meta: { title: '帮助文章' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '404' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const appStore = useAppStore()
  try {
    await appStore.checkLogin()
  } catch (_) {}
  if (to.meta.requiresAuth && !appStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta.requiresAdmin && !appStore.isAdmin) {
    next({ path: '/' })
    return
  }
  next()
})

export default router
