import path from 'path'
import { fileURLToPath } from 'url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const port = env.VITE_BACKEND_PORT || '8081'
  const backend = `http://127.0.0.1:${port}`
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': path.resolve(__dirname, 'src') },
    },
    // sockjs-client 等库在浏览器中会访问 Node 的 global，需 polyfill
    define: {
      global: 'globalThis',
    },
    server: {
      port: 5174,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
        '/ws': { target: backend, ws: true },
      },
    },
  }
})
