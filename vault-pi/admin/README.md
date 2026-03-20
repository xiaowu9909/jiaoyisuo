# Vault π 管理后台

独立 Vue 3 + Vite 项目，端口 5174。

## 启动

```bash
npm install
npm run dev
```

浏览器打开：http://localhost:5174

## 接口 404 说明

管理端请求 `/api/admin/member/page`、`/api/admin/announcement/page` 等会通过 **Vite 开发代理**转发到后端 `http://127.0.0.1:8080`。若出现 404：

1. **先启动后端**：在项目根目录执行 `cd backend && mvn spring-boot:run`，确保 `http://localhost:8080/api/health` 返回正常。
2. **必须用 `npm run dev` 启动本前端**：只有开发服务器会做 `/api` → 8080 的代理；直接打开打包后的 `index.html` 或通过 file:// 打开时没有代理，所有 `/api` 请求都会 404。

## 构建

```bash
npm run build
```

打包产物在 `dist/`。部署时需保证访问同源或 Nginx 等将 `/api` 反向代理到后端。
