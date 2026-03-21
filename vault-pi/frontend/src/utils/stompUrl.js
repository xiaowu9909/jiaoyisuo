/**
 * 默认使用 SockJS（/ws/virtual-market），与生产 Nginx/Cloudflare 配置兼容。
 * 若生产环境确认原生 WebSocket（/ws/stomp）可用，可设 VITE_USE_NATIVE_STOMP=true 切换。
 */
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

export function getStompBrokerUrl() {
  const v = import.meta.env?.VITE_STOMP_BROKER_URL
  if (v && String(v).trim()) return String(v).trim().replace(/\/$/, '')
  if (typeof window === 'undefined') return 'ws://127.0.0.1:8081/ws/stomp'
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/stomp`
}

/** SockJS 入口（与后端 /ws/virtual-market 对应） */
export function getSockJsStompUrl() {
  const base = import.meta.env?.VITE_API_BASE || '/api'
  const origin =
    typeof base === 'string' && base.startsWith('http')
      ? base.replace(/\/api\/?$/, '')
      : typeof window !== 'undefined'
        ? window.location.origin
        : ''
  return origin + '/ws/virtual-market'
}

export function createStompClient(extra = {}) {
  if (import.meta.env.VITE_USE_NATIVE_STOMP === 'true') {
    return new Client({
      brokerURL: getStompBrokerUrl(),
      ...extra,
    })
  }
  return new Client({
    webSocketFactory: () => new SockJS(getSockJsStompUrl()),
    ...extra,
  })
}
