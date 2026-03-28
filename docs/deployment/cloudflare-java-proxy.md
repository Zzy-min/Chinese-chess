# Cloudflare Worker + Java Origin 部署说明

当前生产站点不再尝试把 Java 应用直接部署到 Cloudflare Worker。正式发布路径是：

- Cloudflare Worker 作为公网入口
- `com.xiangqi.web.PublicWebMain` 作为固定 Java 源站

## 仓库内 Worker 项目

- 目录：`deploy/cloudflare-java-proxy/`
- Cloudflare Git 构建根目录必须指向这个目录

## Cloudflare 项目固定配置

- `Production branch = main`
- `Root directory = deploy/cloudflare-java-proxy`
- `Build command = npm ci`
- `Deploy command = npx wrangler deploy`
- 非生产分支构建默认关闭
- 路径监听默认只包含 `deploy/cloudflare-java-proxy/**`

## 运行时变量

Worker 运行时必须配置：

- `ORIGIN_BASE_URL`

要求：

- 必须直接指向 Java 源站
- 不能指向 Worker 自己的公网域名
- 推荐使用固定源站地址，例如 `http://<origin-ip>:18388`

可选变量：

- `STATIC_CACHE_TTL_SEC`

## 本地联调

1. 在仓库根运行 Java 主站：

```powershell
mvn -q -DskipTests package
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

2. 在 `deploy/cloudflare-java-proxy/` 下复制 `.dev.vars.example` 为 `.dev.vars`

3. 启动 Worker 本地代理：

```powershell
npm ci
npm run dev -- --port 8788
```

4. 验证：

- `http://127.0.0.1:8788/`
- `http://127.0.0.1:8788/api/auth/me`
- `http://127.0.0.1:8788/online/api/site/bootstrap`
- `ws://127.0.0.1:8788/online/ws`

## 事故预防规则

- 不允许再让 Cloudflare 直接从仓库根目录执行 `npx wrangler deploy`
- 不允许再让非生产分支默认触发 Worker 构建
- Java 主站变更不要求 Worker 重新发布，除非 Worker 子项目本身发生修改
