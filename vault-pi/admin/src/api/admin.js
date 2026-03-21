const API_BASE = import.meta.env.VITE_API_BASE || '/api'

/** 与后端 CsrfFilter 的 X-CSRF-TOKEN 对应；生产 profile 开启 csrf-enabled 时 POST 必带 */
export const ADMIN_CSRF_STORAGE_KEY = 'vaultpi-admin-csrf'

export function storeAdminCsrfToken(token) {
  if (token != null && token !== '') {
    sessionStorage.setItem(ADMIN_CSRF_STORAGE_KEY, String(token))
  } else {
    sessionStorage.removeItem(ADMIN_CSRF_STORAGE_KEY)
  }
}

const BACKEND_HINT = '请先启动后端：cd backend && mvn spring-boot:run（确认端口 8081 未被占用）'

function isMutatingMethod(method) {
  const m = (method || 'GET').toUpperCase()
  return !['GET', 'HEAD', 'OPTIONS'].includes(m)
}

/** 与路由守卫一致：从 /check/login 拉取 csrfToken 并写入 sessionStorage（会话无 token 时 POST 会 403） */
/** 登出（带 CSRF：头 + body + Cookie 兜底），请优先使用本方法替代裸 fetch */
export async function postAdminLogout() {
  return request('/logout', { method: 'POST', body: '{}' })
}

export async function refreshAdminCsrfFromServer() {
  try {
    const res = await fetch(`${API_BASE}/check/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    const text = await res.text()
    const json = JSON.parse(text)
    if (json.code !== 0 || !json.data || json.data === false) return null
    const token = json.data?.csrfToken
    if (token) {
      storeAdminCsrfToken(String(token))
      return String(token)
    }
    return null
  } catch (_) {
    return null
  }
}

function isCsrfForbiddenMessage(json) {
  const m = String(json?.message || json?.msg || json?.error || '').toLowerCase()
  return m.includes('csrf') || m.includes('令牌')
}

/** 与后端 CsrfFilter 一致：代理可能剥掉 X-CSRF-TOKEN，顶层 csrfToken 作兜底。始终覆盖确保使用最新 token */
function mergeCsrfIntoJsonBody(body, csrf) {
  if (body == null || typeof body !== 'string' || csrf == null || csrf === '') return body
  try {
    const parsed = JSON.parse(body)
    if (parsed != null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return JSON.stringify({ ...parsed, csrfToken: csrf })
    }
  } catch (_) {
    /* 非 JSON 则原样 */
  }
  return body
}

async function request(path, options = {}) {
  const { headers: optHeaders, body: optBody, ...restOptions } = options
  const method = restOptions.method || 'GET'

  for (let attempt = 0; attempt < 2; attempt++) {
    const headers = { 'Content-Type': 'application/json', ...optHeaders }
    let body = optBody
    if (isMutatingMethod(method)) {
      let csrf = sessionStorage.getItem(ADMIN_CSRF_STORAGE_KEY)
      if (!csrf) {
        csrf = await refreshAdminCsrfFromServer()
      }
      if (csrf) {
        headers['X-CSRF-TOKEN'] = csrf
        const ct = String(headers['Content-Type'] || 'application/json').toLowerCase()
        if (ct.includes('application/json')) {
          body = mergeCsrfIntoJsonBody(body, csrf)
        }
      }
    }
    let res
    try {
      res = await fetch(`${API_BASE}${path}`, {
        credentials: 'include',
        ...restOptions,
        headers,
        body,
      })
    } catch (e) {
      const msg = e?.message || ''
      if (msg.includes('fetch') || e?.name === 'TypeError') {
        throw new Error('无法连接后端（Failed to fetch）。' + BACKEND_HINT)
      }
      throw e
    }
    const text = await res.text()
    const ct = res.headers.get('content-type') || ''
    if (!ct.includes('application/json') && text.trimStart().startsWith('<')) {
      throw new Error('接口返回了 HTML 而非 JSON。' + BACKEND_HINT)
    }
    let json
    try {
      json = JSON.parse(text)
    } catch (_) {
      throw new Error('接口返回内容无法解析为 JSON。' + BACKEND_HINT)
    }
    if (res.status === 401) {
      storeAdminCsrfToken(null)
      window.location.href = window.location.origin + '/login'
      throw new Error(json.message || '请重新登录')
    }
    if (res.status === 403) {
      if (isMutatingMethod(method) && attempt === 0 && isCsrfForbiddenMessage(json)) {
        await refreshAdminCsrfFromServer()
        continue
      }
      const msg = json.message || json.msg || json.error || '禁止访问'
      const hint = String(msg).includes('CSRF') ? '（请刷新页面后重试）' : ''
      throw new Error(String(msg) + hint)
    }
    if (json.code === 401) {
      storeAdminCsrfToken(null)
      window.location.href = window.location.origin + '/login'
      throw new Error(json.message || '请重新登录')
    }
    if (json.code !== 0) throw new Error(json.message || 'request failed')
    return json.data
  }
  throw new Error('CSRF 重试后仍失败，请刷新页面')
}

export async function getAdminAnnouncementPage(pageNo = 1, pageSize = 20) {
  return request(`/admin/announcement/page?pageNo=${pageNo}&pageSize=${pageSize}`)
}

export async function postAdminAnnouncementAdd(body) {
  return request('/admin/announcement/add', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminAnnouncementUpdate(body) {
  return request('/admin/announcement/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminAnnouncementDelete(id) {
  return request('/admin/announcement/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function postAdminAnnouncementTop(id) {
  return request('/admin/announcement/top', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function postAdminAnnouncementUntop(id) {
  return request('/admin/announcement/untop', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function getAdminExchangeCoinList() {
  return request('/admin/exchange-coin/list')
}

/** 虚拟盘现价 Map（symbol → 价格字符串）；走管理端接口，不依赖 /market（避免网关只代理 admin 时 thumb 不可用） */
export async function getAdminVirtualLivePrices() {
  return request('/admin/exchange/coin/virtual-live-prices')
}

/** 与 C 端同源：单交易对行情快照（虚拟盘为引擎实时价） */
export async function getMarketSymbolThumbOne(symbol) {
  const s = encodeURIComponent(String(symbol || '').trim())
  return request(`/market/symbol-thumb-one?symbol=${s}`)
}

export async function postAdminExchangeCoinAdd(body) {
  return request('/admin/exchange/coin/add', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminExchangeCoinUpdate(body) {
  return request('/admin/exchange/coin/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminExchangeCoinDelete(id) {
  return request('/admin/exchange/coin/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

/**
 * 重新生成虚拟盘内存 K 线预填（默认约半年 1m→聚合多周期），与引擎 vaultpi.virtual.kline-history-months 一致。
 * @param {string} symbol 交易对如 BTC/USDT
 * @param {string|number|null} [endPrice] 可选：预填前将引擎现价钳位到该值（区间内）
 */
export async function postAdminExchangeCoinRefillKlineHistory(symbol, endPrice = null) {
  let path = `/admin/exchange/coin/refill-kline-history?symbol=${encodeURIComponent(String(symbol || '').trim())}`
  if (endPrice != null && endPrice !== '') path += `&endPrice=${encodeURIComponent(String(endPrice))}`
  return request(path, { method: 'POST', body: '{}' })
}

/** 设置虚拟盘行情趋势：direction=UP|DOWN, percent=涨跌幅%, duration=周期秒 */
export async function postAdminVirtualTrend(body) {
  return request('/admin/exchange/coin/trend', { method: 'POST', body: JSON.stringify(body) })
}

/** 清除虚拟盘行情趋势 */
export async function postAdminVirtualTrendClear(id) {
  return request('/admin/exchange/coin/trend/clear', { method: 'POST', body: JSON.stringify({ id }) })
}

/** 虚拟盘趋势设置审计日志分页（symbol、adminId 可选） */
export async function getAdminVirtualTrendAuditPage(pageNo = 1, pageSize = 20, symbol = '', adminId = null) {
  let path = `/admin/exchange/coin/trend/audit?pageNo=${pageNo}&pageSize=${pageSize}`
  if (symbol && symbol.trim()) path += `&symbol=${encodeURIComponent(symbol.trim())}`
  if (adminId != null && adminId !== '') path += `&adminId=${adminId}`
  return request(path)
}

export async function getAdminExchangeOrderPage(pageNo = 1, pageSize = 20, memberId = '', symbol = '') {
  let url = `/admin/exchange/order/page?pageNo=${pageNo}&pageSize=${pageSize}`
  if (memberId) url += `&memberId=${memberId}`
  if (symbol) url += `&symbol=${symbol}`
  return request(url)
}

export async function getAdminMemberPage(pageNo = 1, pageSize = 20, searchKey = '', statusFilter = '') {
  const params = new URLSearchParams({ pageNo, pageSize })
  if (searchKey) params.set('searchKey', searchKey)
  if (statusFilter) params.set('statusFilter', statusFilter)
  return request(`/admin/member/page?${params}`)
}

export async function getAdminMemberDetail(id) {
  return request(`/admin/member/detail?id=${id}`)
}

export async function getAdminVipLevelList() {
  return request('/admin/member/vip-level/list')
}

export async function postAdminVipLevelUpdate(body) {
  return request('/admin/member/vip-level/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberAdd(body) {
  return request('/admin/member/add', { method: 'POST', body: JSON.stringify(body) })
}

export async function getAdminMemberCoins() {
  return request('/admin/member/coins')
}

export async function postAdminMemberBonus(body) {
  return request('/admin/member/bonus', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberBalanceUpdate(body) {
  return request('/admin/member/balance/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberBalanceUnfreeze(body) {
  return request('/admin/member/balance/unfreeze', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberStatusUpdate(body) {
  return request('/admin/member/status', { method: 'POST', body: JSON.stringify(body) })
}

/** 更新会员：用户类型 userType(NORMAL/INTERNAL)、上级 parentId 或 parentInviteCode（空则清除） */
export async function postAdminMemberUpdateBase(body) {
  return request('/admin/member/update-base', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberPasswordReset(body) {
  return request('/admin/member/password/reset', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberWithdrawAddressAdd(body) {
  return request('/admin/member/withdraw-address/add', { method: 'POST', body: JSON.stringify(body) })
}
export async function postAdminMemberWithdrawAddressUpdate(body) {
  return request('/admin/member/withdraw-address/update', { method: 'POST', body: JSON.stringify(body) })
}
export async function postAdminMemberWithdrawAddressDelete(body) {
  return request('/admin/member/withdraw-address/delete', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminMemberWithdrawPasswordReset(body) {
  return request('/admin/member/withdraw-password/reset', { method: 'POST', body: JSON.stringify(body) })
}

export async function getAdminAuthenticatePage(pageNo = 1, pageSize = 20, auditStatus = '') {
  const q = auditStatus ? `&auditStatus=${encodeURIComponent(auditStatus)}` : ''
  return request(`/admin/authenticate/page?pageNo=${pageNo}&pageSize=${pageSize}${q}`)
}

export async function getAdminAuthenticateDetail(id) {
  return request(`/admin/authenticate/detail?id=${id}`)
}

export async function postAdminAuthenticateAudit(body) {
  return request('/admin/authenticate/audit', { method: 'POST', body: JSON.stringify(body) })
}

export async function getAdminInviteStat(pageNo = 1, pageSize = 20, kw = '') {
  const q = kw ? `&kw=${encodeURIComponent(kw)}` : ''
  return request(`/admin/invite/stat?pageNo=${pageNo}&pageSize=${pageSize}${q}`)
}

export async function getAdminInviteChildren(parentId) {
  return request(`/admin/invite/children?parentId=${parentId}`)
}

export async function getAdminInviteCommission(promoterId) {
  const q = promoterId != null ? `?promoterId=${promoterId}` : ''
  return request(`/admin/invite/commission${q}`)
}

export async function postAdminInviteCommission(body) {
  return request('/admin/invite/commission', { method: 'POST', body: JSON.stringify(body) })
}

export async function getAdminHelpPage(pageNo = 1, pageSize = 20) {
  return request(`/admin/help/page?pageNo=${pageNo}&pageSize=${pageSize}`)
}

export async function getAdminAdvertiseAll() {
  return request('/admin/advertise/all')
}

export async function postAdminAdvertiseAdd(body) {
  return request('/admin/advertise/add', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminAdvertiseUpdate(body) {
  return request('/admin/advertise/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminAdvertiseDelete(id) {
  return request('/admin/advertise/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

// Activity
export async function getAdminActivityAll() {
  return request('/admin/activity/all')
}
export async function postAdminActivityAdd(body) {
  return request('/admin/activity/add', { method: 'POST', body: JSON.stringify(body) })
}
export async function postAdminActivityUpdate(body) {
  return request('/admin/activity/update', { method: 'POST', body: JSON.stringify(body) })
}
export async function postAdminActivityDelete(id) {
  return request('/admin/activity/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

// Envelope
export async function getAdminEnvelopeAll() {
  return request('/admin/envelope/all')
}
export async function postAdminEnvelopeUpdate(body) {
  return request('/admin/envelope/update', { method: 'POST', body: JSON.stringify(body) })
}
export async function postAdminEnvelopeDelete(id) {
  return request('/admin/envelope/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function postAdminHelpAdd(body) {
  return request('/admin/help/add', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminHelpUpdate(body) {
  return request('/admin/help/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function postAdminHelpDelete(id) {
  return request('/admin/help/delete', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function postAdminHelpTop(id) {
  return request('/admin/help/top', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function postAdminHelpUntop(id) {
  return request('/admin/help/untop', { method: 'POST', body: JSON.stringify({ id }) })
}

export async function getAdminFinanceStats(unit = 'USDT') {
  return request(`/admin/finance/stats?unit=${unit}`)
}

export async function getAdminFinanceTrend(days = 30, unit = 'USDT') {
  return request(`/admin/finance/trend?days=${days}&unit=${unit}`)
}

/** 财务统计-真实会员分页：搜索(邮箱/UID/用户名)、时间筛选、表头合计 */
export async function getAdminFinanceStatsPage(params = {}) {
  const { pageNo = 1, pageSize = 20, searchKey = '', startDate = '', endDate = '', unit = 'USDT' } = params
  const q = new URLSearchParams()
  q.set('pageNo', pageNo)
  q.set('pageSize', pageSize)
  q.set('unit', unit)
  if (searchKey) q.set('searchKey', searchKey)
  if (startDate) q.set('startDate', startDate)
  if (endDate) q.set('endDate', endDate)
  return request(`/admin/finance/stats/page?${q}`)
}

// --- Finance Management ---
export async function getAdminWithdrawPage(pageNo = 1, pageSize = 20, status = '') {
  return request(`/admin/finance/withdraw/page?pageNo=${pageNo}&pageSize=${pageSize}&status=${status}`)
}

export async function postAdminWithdrawAudit(data) {
  return request('/admin/finance/withdraw/audit', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAdminDepositPage(pageNo = 1, pageSize = 20, memberId = '', coinId = '') {
  let url = `/admin/finance/deposit/page?pageNo=${pageNo}&pageSize=${pageSize}`
  if (memberId) url += `&memberId=${memberId}`
  if (coinId) url += `&coinId=${coinId}`
  return request(url)
}

export async function getAdminTransactionPage(pageNo = 1, pageSize = 20, memberId = '', symbol = '', type = '') {
  let url = `/admin/finance/transaction/page?pageNo=${pageNo}&pageSize=${pageSize}`
  if (memberId) url += `&memberId=${memberId}`
  if (symbol) url += `&symbol=${symbol}`
  if (type) url += `&type=${type}`
  return request(url)
}

export async function postAdminDepositManual(data) {
  return request('/admin/finance/deposit/manual', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function postAdminDepositConfirm(data) {
  return request('/admin/finance/deposit/confirm', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function postAdminDepositReject(data) {
  return request('/admin/finance/deposit/reject', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAdminDepositAddressList() {
  return request('/admin/finance/deposit-address/list')
}

export async function postAdminDepositAddressAdd(data) {
  return request('/admin/finance/deposit-address/add', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function postAdminDepositAddressUpdate(data) {
  return request('/admin/finance/deposit-address/update', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAdminFuturesOrders(pageNo = 1, pageSize = 20, memberId = '', symbol = '') {
  let url = `/admin/futures/orders?pageNo=${pageNo}&pageSize=${pageSize}`
  if (memberId) url += `&memberId=${memberId}`
  if (symbol) url += `&symbol=${symbol}`
  return request(url)
}

export async function getAdminFuturesPositions(pageNo = 1, pageSize = 20, memberId = '', symbol = '') {
  let url = `/admin/futures/positions?pageNo=${pageNo}&pageSize=${pageSize}`
  if (memberId) url += `&memberId=${memberId}`
  if (symbol) url += `&symbol=${symbol}`
  return request(url)
}

export async function getAdminFuturesStats() {
  return request('/admin/futures/stats')
}

export async function postAdminFuturesPositionForceClose(positionId) {
  return request('/admin/futures/position/close', {
    method: 'POST',
    body: JSON.stringify({ positionId }),
  })
}

export async function getAdminSystemConfigList() {
  return request('/admin/system/config/list')
}

/** 管理后台操作日志分页 */
export async function getAdminOperationLogPage(pageNo = 1, pageSize = 20) {
  return request(`/admin/system/operation-log/page?pageNo=${pageNo}&pageSize=${pageSize}`)
}

/** 服务端错误日志分页（由 GlobalExceptionHandler 异步落库） */
export async function getAdminErrorLogPage(pageNo = 1, pageSize = 20) {
  return request(`/admin/system/error-log/page?pageNo=${pageNo}&pageSize=${pageSize}`)
}

export async function postAdminSystemConfigUpdate(data) {
  return request('/admin/system/config/update', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getAdminMe() {
  return request('/admin/me')
}

export async function postAdminMeUpdate(body) {
  return request('/admin/me/update', { method: 'POST', body: JSON.stringify(body) })
}

export async function getAdminAdminsList() {
  return request('/admin/system/admins')
}

export async function postAdminAdminAdd(data) {
  return request('/admin/system/admin/add', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function postAdminAdminUpdate(data) {
  return request('/admin/system/admin/update', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 上传图片，返回可访问的图片 URL。file 为 File 对象。 */
export async function postAdminUploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)

  for (let attempt = 0; attempt < 2; attempt++) {
    let csrf = sessionStorage.getItem(ADMIN_CSRF_STORAGE_KEY)
    if (!csrf) {
      csrf = await refreshAdminCsrfFromServer()
    }
    const res = await fetch(`${API_BASE}/admin/upload/image`, {
      method: 'POST',
      credentials: 'include',
      body: formData,
      headers: csrf ? { 'X-CSRF-TOKEN': csrf } : {},
    })
    const text = await res.text()
    let json
    try {
      json = JSON.parse(text)
    } catch (_) {
      throw new Error('响应解析失败')
    }
    if (res.status === 401) {
      storeAdminCsrfToken(null)
      window.location.href = window.location.origin + '/login'
      throw new Error(json.message || '请重新登录')
    }
    if (res.status === 403) {
      if (attempt === 0 && isCsrfForbiddenMessage(json)) {
        await refreshAdminCsrfFromServer()
        continue
      }
      const msg = json.message || json.msg || json.error || '禁止访问'
      const hint = String(msg).includes('CSRF') ? '（请刷新页面后重试）' : ''
      throw new Error(String(msg) + hint)
    }
    if (json.code === 401) {
      storeAdminCsrfToken(null)
      window.location.href = window.location.origin + '/login'
      throw new Error(json.message || '请重新登录')
    }
    if (json.code !== 0) throw new Error(json.message || '上传失败')
    return json.data?.url ?? ''
  }
  throw new Error('上传失败：CSRF 重试后仍无效')
}

// ── AI控盘 ──────────────────────────────────────────────────

export async function getAdminAiPhrases() {
  return request('/admin/ai/phrases')
}
export async function postAdminAiPhraseAdd(data) {
  return request('/admin/ai/phrases/add', { method: 'POST', body: JSON.stringify(data) })
}
export async function postAdminAiPhraseUpdate(data) {
  return request('/admin/ai/phrases/update', { method: 'POST', body: JSON.stringify(data) })
}
export async function postAdminAiPhraseDelete(id) {
  return request('/admin/ai/phrases/delete', { method: 'POST', body: JSON.stringify({ id }) })
}
export async function getAdminAiUsers() {
  return request('/admin/ai/users')
}
export async function postAdminAiUserCancel(userId) {
  return request('/admin/ai/users/cancel', { method: 'POST', body: JSON.stringify({ userId }) })
}
export async function getAdminAiPlans() {
  return request('/admin/ai/plans')
}
export async function postAdminAiPlanAdd(data) {
  return request('/admin/ai/plans/add', { method: 'POST', body: JSON.stringify(data) })
}
export async function postAdminAiPlanUpdate(data) {
  return request('/admin/ai/plans/update', { method: 'POST', body: JSON.stringify(data) })
}
export async function postAdminAiPlanDelete(id) {
  return request('/admin/ai/plans/delete', { method: 'POST', body: JSON.stringify({ id }) })
}
