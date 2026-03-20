import { message } from '../components/toast'

const API_BASE: string = (import.meta as any).env?.VITE_API_BASE || '/api'

/** 由 main.js 注入，用于请求时更新全局 loading 状态 */
let _loadingStore: { addLoading?: () => void; removeLoading?: () => void } | null = null
export function setRequestLoadingStore(store: typeof _loadingStore): void {
  _loadingStore = store
}

/** 开发环境显示启动提示，生产环境不暴露运维信息 */
const BACKEND_HINT = typeof import.meta !== 'undefined' && (import.meta as any).env?.DEV
  ? '请先启动后端：cd backend && mvn spring-boot:run（确认端口 8081 未被占用）'
  : '服务暂时不可用，请稍后重试'

/** 避免将后端内部错误、堆栈、路径直接展示给用户（XSS/信息泄露） */
function sanitizeErrorMessage(raw: string | undefined): string {
  if (raw == null || typeof raw !== 'string') return '请求失败，请重试'
  const s = raw.trim().slice(0, 200)
  if (/^[a-z].*exception|error|at\s+[\w.$]+\(|java\.|spring\.|sql/i.test(s)) return '服务暂时异常，请稍后重试'
  return s
}

/** CSRF 仅存内存，避免 XSS 窃取 localStorage 导致一键收割 */
let _csrfToken: string = ''

export function getCsrfToken(): string {
  return _csrfToken
}

export function setCsrfToken(token: string | null): void {
  _csrfToken = token != null ? token : ''
}

export interface ApiResponse<T = unknown> {
  code: number
  message?: string
  data?: T
}

interface RequestOptions extends RequestInit {
  method?: string
  headers?: Record<string, string>
  /** 传入 AbortSignal 可在页面切换等场景取消请求，避免 pending 占用带宽与内存 */
  signal?: AbortSignal
}

export async function request(url: string, options: RequestOptions = {}): Promise<ApiResponse> {
  // 高频行情拉取会导致全局 loading/刷新图标闪烁
  // `symbol-thumb-one` 由 Exchange.vue 做降级轮询时非常频繁，因此跳过 loading 状态
  const skipGlobalLoading = (
    url.includes('/market/symbol-thumb-one')
    || url.includes('/market/plate')
    || url.includes('/market/latest-trade')
  )
  skipGlobalLoading ? null : _loadingStore?.addLoading?.()
  try {
    const method = (options.method || 'GET').toUpperCase()
    const headers: Record<string, string> = { 'Content-Type': 'application/json', ...options.headers }
    if (method !== 'GET' && method !== 'HEAD') {
      const csrf = getCsrfToken()
      if (csrf) headers['X-CSRF-TOKEN'] = csrf
    }
    let res: Response
    try {
      res = await fetch(url, {
        credentials: 'include',
        headers,
        signal: options.signal,
        ...options,
      })
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : ''
      const errMsg = (typeof msg === 'string' && (msg.includes('fetch') || (e as Error)?.name === 'TypeError'))
        ? '无法连接后端（Failed to fetch）。' + BACKEND_HINT
        : (msg || '网络异常')
      message.error(errMsg)
      throw new Error(errMsg)
    }
    const text = await res.text()
    const ct = res.headers.get('content-type') || ''
    if (!ct.includes('application/json') && text.trimStart().startsWith('<')) {
      const errMsg = '接口返回了 HTML 而非 JSON。' + BACKEND_HINT
      message.error(errMsg)
      throw new Error(errMsg)
    }
    let json: ApiResponse
    try {
      json = JSON.parse(text) as ApiResponse
    } catch (_) {
      const errMsg = '接口返回内容无法解析为 JSON。' + BACKEND_HINT
      message.error(errMsg)
      throw new Error(errMsg)
    }
    if (json?.code !== 0 && json?.message) {
      const safeMsg = sanitizeErrorMessage(json.message)
      message.error(safeMsg)
    }
    if ((json as any)?.data?.csrfToken) setCsrfToken((json as any).data.csrfToken)
    return json
  } finally {
    if (!skipGlobalLoading) _loadingStore?.removeLoading?.()
  }
}

export async function getAnnouncementPage(params: { pageNo?: number; pageSize?: number; lang?: string } = {}): Promise<any> {
  const pageNo = params.pageNo ?? 1
  const pageSize = params.pageSize ?? 10
  const lang = params.lang ?? 'CN'
  const json = await request(
    `${API_BASE}/announcement/page?pageNo=${pageNo}&pageSize=${pageSize}&lang=${lang}`
  )
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getAnnouncementDetail(id: string | number, lang = 'CN'): Promise<any> {
  const json = await request(`${API_BASE}/announcement/${id}?lang=${lang}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getHomeGettingStartConfig(lang = ''): Promise<any> {
  try {
    const q = lang ? `&lang=${encodeURIComponent(lang)}` : ''
    const json = await request(`${API_BASE}/config/home-getting-start?_=${Date.now()}${q}`)
    if (json.code !== 0) return null
    return json.data ?? null
  } catch (_) {
    return null
  }
}

export async function getHomeAboutBrandConfig(lang = ''): Promise<any> {
  try {
    const q = lang ? `&lang=${encodeURIComponent(lang)}` : ''
    const json = await request(`${API_BASE}/config/home-about-brand?_=${Date.now()}${q}`)
    if (json.code !== 0) return null
    return json.data ?? null
  } catch (_) {
    return null
  }
}

export async function getHomeAppDownloadConfig(lang = ''): Promise<any> {
  try {
    const q = lang ? `&lang=${encodeURIComponent(lang)}` : ''
    const json = await request(`${API_BASE}/config/home-app-download?_=${Date.now()}${q}`)
    if (json.code !== 0) return null
    return json.data ?? null
  } catch (_) {
    return null
  }
}

export async function getSiteLogoConfig(): Promise<any> {
  try {
    const json = await request(`${API_BASE}/config/site-logo?_=${Date.now()}`)
    if (json.code !== 0) return null
    return json.data ?? null
  } catch (_) {
    return null
  }
}

export async function postLogin(username: string, password: string): Promise<any> {
  const json = await request(`${API_BASE}/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (json.code !== 0) throw new Error(json.message || 'login failed')
  if ((json as any).data?.csrfToken) setCsrfToken((json as any).data.csrfToken)
  return (json as any).data
}

export async function postRegister(email: string, username: string, password: string, inviteCode?: string): Promise<any> {
  const body: Record<string, string> = { email, username, password }
  if (inviteCode) body.inviteCode = inviteCode
  const json = await request(`${API_BASE}/register`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || 'register failed')
  return (json as any).data
}

export async function getUcInviteInfo(): Promise<any> {
  const json = await request(`${API_BASE}/uc/invite/info`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getUcInviteRank(limit = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/invite/rank?limit=${limit}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getWalletList(): Promise<any> {
  const json = await request(`${API_BASE}/uc/wallet/list`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getMarketSymbol(): Promise<any> {
  const json = await request(`${API_BASE}/market/symbol`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getMarketSymbolThumb(): Promise<any[]> {
  const json = await request(`${API_BASE}/market/symbol-thumb`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return Array.isArray(json.data) ? json.data : []
}

export async function getMarketSymbolThumbOne(symbol: string): Promise<any> {
  const normalized = typeof symbol === 'string' ? symbol.replace(/\//g, '-') : symbol
  const json = await request(`${API_BASE}/market/symbol-thumb-one?symbol=${encodeURIComponent(normalized)}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data ?? null
}

export async function getMarketSymbolInfo(symbol: string): Promise<any> {
  // Some server/proxy layers may not correctly parse '/' in query params.
  // `symbol-info` 在当前环境下使用 '-' 格式更稳（例如 ETH-USDT）。
  const normalized = typeof symbol === 'string' ? symbol.replace(/\//g, '-') : symbol
  const json = await request(`${API_BASE}/market/symbol-info?symbol=${encodeURIComponent(normalized)}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getMarketKline(symbol: string, interval = '1h', limit = 100, endTime: string | number | null = null): Promise<any[]> {
  const normalized = typeof symbol === 'string' ? symbol.replace(/\//g, '-') : symbol
  let url = `${API_BASE}/market/kline?symbol=${encodeURIComponent(normalized)}&interval=${encodeURIComponent(interval)}&limit=${limit}`
  if (endTime != null) url += `&endTime=${endTime}`
  const json = await request(url)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return Array.isArray(json.data) ? json.data : []
}

export async function postAssetExchange(data: Record<string, unknown>): Promise<ApiResponse> {
  const json = await request(`${API_BASE}/uc/asset/exchange`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
  return json as ApiResponse
}

export async function getMarketPlate(symbol: string, limit = 20): Promise<{ ask: any[]; bid: any[] }> {
  const normalized = typeof symbol === 'string' ? symbol.replace(/\//g, '-') : symbol
  const json = await request(
    `${API_BASE}/market/plate?symbol=${encodeURIComponent(normalized)}&limit=${limit}`
  )
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  const d = json.data
  return d && typeof d === 'object' ? d as { ask: any[]; bid: any[] } : { ask: [], bid: [] }
}

export async function getMarketLatestTrade(symbol: string, size = 30): Promise<any[]> {
  const normalized = typeof symbol === 'string' ? symbol.replace(/\//g, '-') : symbol
  const json = await request(
    `${API_BASE}/market/latest-trade?symbol=${encodeURIComponent(normalized)}&size=${size}`
  )
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return Array.isArray(json.data) ? json.data : []
}

export async function postOrderAdd(params: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/order/add`, {
    method: 'POST',
    body: JSON.stringify(params),
  })
  if (json.code !== 0) throw new Error(json.message || '下单失败')
  return json.data
}

export async function postOrderCancel(orderId: string): Promise<any> {
  const json = await request(`${API_BASE}/order/cancel`, {
    method: 'POST',
    body: JSON.stringify({ orderId }),
  })
  if (json.code !== 0) throw new Error(json.message || '撤单失败')
  return json.data
}

export async function getOrderCurrent(): Promise<any> {
  const json = await request(`${API_BASE}/uc/order/current`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getOrderHistory(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/order/history?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getOrderTradeHistory(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/order/trade-history?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postFuturesOrderAdd(params: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/futures/order/add`, {
    method: 'POST',
    body: JSON.stringify(params),
  })
  if (json.code !== 0) throw new Error(json.message || '下单失败')
  return json.data
}

export async function postFuturesOrderCancel(orderId: string): Promise<any> {
  const json = await request(`${API_BASE}/futures/order/cancel`, {
    method: 'POST',
    body: JSON.stringify({ orderId }),
  })
  if (json.code !== 0) throw new Error(json.message || '撤单失败')
  return json.data
}

export async function getFuturesOrderCurrent(): Promise<any> {
  const json = await request(`${API_BASE}/futures/order/current`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getFuturesPositionCurrent(): Promise<any> {
  const json = await request(`${API_BASE}/futures/position/current`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getFuturesOrderHistory(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/futures/order/history?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postFuturesPositionClose(positionId: string): Promise<any> {
  const json = await request(`${API_BASE}/futures/position/close`, {
    method: 'POST',
    body: JSON.stringify({ positionId }),
  })
  if (json.code !== 0) throw new Error(json.message || '平仓失败')
  return json.data
}

export async function getHelpPage(params: { pageNo?: number; pageSize?: number; lang?: string; classification?: string } = {}): Promise<any> {
  const pageNo = params.pageNo ?? 1
  const pageSize = params.pageSize ?? 20
  const lang = params.lang ?? 'CN'
  let url = `${API_BASE}/help/page?pageNo=${pageNo}&pageSize=${pageSize}&lang=${lang}`
  if (params.classification) url += `&classification=${encodeURIComponent(params.classification)}`
  const json = await request(url)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getHelpClassifications(lang = 'CN'): Promise<any> {
  const json = await request(`${API_BASE}/help/classifications?lang=${lang}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getHelpDetail(id: string | number): Promise<any> {
  const json = await request(`${API_BASE}/help/${id}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postResetPasswordSendCode(email: string): Promise<any> {
  const json = await request(`${API_BASE}/uc/reset/email/code`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
  if (json.code !== 0) throw new Error(json.message || '发送失败')
  return json.data
}

export async function postResetPassword(body: Record<string, string>): Promise<any> {
  const json = await request(`${API_BASE}/uc/reset/login/password`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '重置失败')
  return json.data
}

export async function getUcAccount(): Promise<any> {
  const json = await request(`${API_BASE}/uc/account`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postUcAccount(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/account`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '保存失败')
  return json.data
}

export async function postUcSafeUpdatePassword(body: Record<string, string>): Promise<any> {
  const json = await request(`${API_BASE}/uc/safe/update-password`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '修改失败')
  return json.data
}

export async function getUcWithdrawPasswordStatus(): Promise<any> {
  const json = await request(`${API_BASE}/uc/safe/withdraw-password/status`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postUcSafeSetWithdrawPassword(body: Record<string, string>): Promise<any> {
  const json = await request(`${API_BASE}/uc/safe/set-withdraw-password`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '设置失败')
  return json.data
}

export async function postUcSafeUpdateWithdrawPassword(body: Record<string, string>): Promise<any> {
  const json = await request(`${API_BASE}/uc/safe/update-withdraw-password`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '修改失败')
  return json.data
}

export async function getUcAuthenticateStatus(): Promise<any> {
  const json = await request(`${API_BASE}/uc/authenticate/status`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postUcAuthenticate(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/authenticate`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '提交失败')
  return json.data
}

export async function getUcTransactionPage(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/transaction/page?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getUcRechargeCoins(): Promise<any> {
  const json = await request(`${API_BASE}/uc/recharge/coins`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getUcRechargeAddress(unit: string): Promise<any> {
  const json = await request(`${API_BASE}/uc/recharge/address?unit=${encodeURIComponent(unit)}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postUcRechargeSubmit(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/recharge/submit`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getUcRechargeRecord(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/recharge/record?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function getUcWithdrawAddressList(): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw/address/list`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}

export async function postUcWithdrawAddressAdd(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw/address/add`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '添加失败')
  return json.data
}

export async function postUcWithdrawAddressDelete(id: string | number): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw/address/delete`, {
    method: 'POST',
    body: JSON.stringify({ id }),
  })
  if (json.code !== 0) throw new Error(json.message || '删除失败')
  return json.data
}

export async function postUcWithdrawAddressUpdate(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw/address/update`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '更新失败')
  return json.data
}

export async function postUcWithdraw(body: Record<string, unknown>): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  if (json.code !== 0) throw new Error(json.message || '提交失败')
  return json.data
}

export async function getUcWithdrawRecord(pageNo = 1, pageSize = 20): Promise<any> {
  const json = await request(`${API_BASE}/uc/withdraw/record?pageNo=${pageNo}&pageSize=${pageSize}`)
  if (json.code !== 0) throw new Error(json.message || 'request failed')
  return json.data
}
