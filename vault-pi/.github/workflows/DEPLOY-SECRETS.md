## Git Push 自动发布 - Secrets 配置

为使 `deploy-prod.yml` 生效，请在 GitHub 仓库中配置以下 `Actions secrets and variables` -> `Secrets`：

- `DEPLOY_HOST`：`107.175.124.214`
- `DEPLOY_PORT`：`22`
- `DEPLOY_USER`：`root`
- `DEPLOY_PASSWORD`：88r0ggf5YL0TKmRbL7
- `DEPLOY_APP_DIR`：`/www/wwwroot/vault314.com`

配置完成后：

1. 提交并推送到 `main`；
2. GitHub Actions 自动执行 `Deploy Production`；
3. 发布日志在仓库 `Actions` 页面查看。

> 建议后续将 `DEPLOY_PASSWORD` 方案升级为 SSH Key（更安全）。
