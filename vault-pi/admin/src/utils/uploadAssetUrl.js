/**
 * 将上传接口返回的 `/uploads/…` 转为浏览器可请求的绝对 URL。
 * 管理后台若在 admin 子域，相对 `/uploads` 会请求到 admin，若未配置 Nginx 会 404；
 * 若 `VITE_API_BASE` 为 `https://主站/api`，则用主站 origin + 路径（文件实际在同一后端）。
 * 可选 `VITE_ASSET_ORIGIN=https://主站` 强制指定。
 */
export function resolveUploadUrlForDisplay(path) {
  if (path == null || path === '') return ''
  const p = String(path).trim()
  if (!p) return ''
  if (/^https?:\/\//i.test(p)) return p
  const normalized = p.startsWith('/') ? p : `/${p}`

  const explicit = import.meta.env.VITE_ASSET_ORIGIN
  if (explicit) {
    return `${String(explicit).replace(/\/$/, '')}${normalized}`
  }

  const apiBase = import.meta.env.VITE_API_BASE || '/api'
  if (apiBase.startsWith('http://') || apiBase.startsWith('https://')) {
    try {
      return `${new URL(apiBase).origin}${normalized}`
    } catch (_) {
      /* fall through */
    }
  }

  return normalized
}
