/**
 * C 端：根据 /api/config/site-logo 应用浏览器标签图标与站点标题基准（与路由 meta.title 组合）
 */

import { resolveUploadUrlForDisplay } from './uploadAssetUrl'

export function applyFaviconFromConfig(faviconUrl) {
  if (typeof document === 'undefined') return
  const u = resolveUploadUrlForDisplay(String(faviconUrl ?? '').trim())
  if (!u) return
  let link = document.querySelector("link[rel~='icon']")
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  const sep = u.includes('?') ? '&' : '?'
  link.href = `${u}${sep}v=${Date.now()}`
}

/** 供路由组合标题使用，未配置时回退默认 */
export function setPageTitleBaseC(title) {
  const t = String(title ?? '').trim()
  if (typeof window !== 'undefined') {
    window.__VAULTPI_PAGE_TITLE_C__ = t || 'Vault π'
  }
}

export function getPageTitleBaseC() {
  if (typeof window === 'undefined') return 'Vault π'
  return window.__VAULTPI_PAGE_TITLE_C__ || 'Vault π'
}

/** 根据站点名 + 路由 meta.title 设置 document.title */
export function syncCTabTitle(routeMetaTitle) {
  const base = getPageTitleBaseC()
  const piece = routeMetaTitle != null && String(routeMetaTitle).trim() !== '' ? String(routeMetaTitle).trim() : ''
  document.title = piece ? `${piece} - ${base}` : base
}
