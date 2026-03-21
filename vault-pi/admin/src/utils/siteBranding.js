/**
 * 管理后台：根据公开接口 /api/config/site-logo 应用标签页图标与标题基准
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
  // 浏览器会强缓存 favicon，更新后需破坏缓存才能看到新图标
  const sep = u.includes('?') ? '&' : '?'
  link.href = `${u}${sep}v=${Date.now()}`
}

export function setPageTitleBaseB(title) {
  const t = String(title ?? '').trim()
  if (typeof window !== 'undefined') {
    window.__VAULTPI_PAGE_TITLE_B__ = t || 'Vault π 管理后台'
  }
}

export function getPageTitleBaseB() {
  if (typeof window === 'undefined') return 'Vault π 管理后台'
  return window.__VAULTPI_PAGE_TITLE_B__ || 'Vault π 管理后台'
}

export function syncAdminTabTitle(routeMetaTitle) {
  const base = getPageTitleBaseB()
  const piece = routeMetaTitle != null && String(routeMetaTitle).trim() !== '' ? String(routeMetaTitle).trim() : ''
  document.title = piece ? `${piece} - ${base}` : base
}
