import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const port = env.VITE_BACKEND_PORT || '8081'
  // 生产构建用相对路径，便于独立域名部署（如 admin.vault314.com）；开发或挂在同一域名下时可改为 /admin/
  const base = mode === 'production' ? './' : '/admin/'
  return {
    base,
    plugins: [vue()],
    server: {
      port: 5173,
      hmr: { host: 'localhost', port: 5173 },
      proxy: {
        '/api': { target: `http://127.0.0.1:${port}`, changeOrigin: true },
      },
    },
  }
})
