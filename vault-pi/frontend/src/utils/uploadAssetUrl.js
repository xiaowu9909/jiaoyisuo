/**
 * Logo/favicon 配置中的 `/uploads/…` 转为可加载的 URL（与 admin 一致）。
 * 跨子域时可通过 `VITE_ASSET_ORIGIN` 指定静态资源所在主域。
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
