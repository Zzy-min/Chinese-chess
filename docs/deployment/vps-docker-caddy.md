# VPS + Docker Compose + Caddy 部署说明

本文档给出 `Chinese-chess` 的低成本正式部署方案：

- 1 台 Ubuntu 24.04 VPS
- Docker Compose 管理 `app + db + caddy`
- Cloudflare 继续负责 DNS / 代理
- `Postgres` 与主站共机
- 暂不部署 `go-engine`

这套方案适合先把主站稳定上线，再按需要追加分析引擎或监控。

## 1. 服务结构

- `app`
  - 使用仓库根目录 `Dockerfile`
  - 启动 `com.xiangqi.web.PublicWebMain`
  - 容器内监听 `18388`
  - 通过 `XQ_DATABASE_URL` + `XQ_DATABASE_USER` + `XQ_DATABASE_PASSWORD` 连接 Postgres
- `db`
  - `postgres:16-alpine`
  - 保存账号、会话、在线对局等数据
- `caddy`
  - 监听公网 `80/443`
  - 为 `www.xiangqiarena.com` 自动签发 HTTPS
  - 将根域 `xiangqiarena.com` 永久重定向到 `www`

## 2. 前置条件

- 一台 Ubuntu 24.04 VPS
- 域名已托管到 Cloudflare
- VPS 已开放 `22`、`80`、`443`
- 已安装 Git、Docker Engine、Docker Compose Plugin

如果你追求最低成本，这套部署也可以放在低配 VPS 或免费层 Linux 主机上，只要能稳定运行 Docker 即可。

## 3. 服务器准备

推荐目录：

```bash
sudo mkdir -p /opt/chinese-chess
sudo chown "$USER":"$USER" /opt/chinese-chess
cd /opt/chinese-chess
```

拉取代码：

```bash
git clone https://github.com/Zzy-min/Chinese-chess .
```

复制环境变量模板：

```bash
cp .env.example .env
```

编辑 `.env`，至少修改：

- `POSTGRES_PASSWORD`

默认值已适配 `xiangqiarena.com` / `www.xiangqiarena.com`。
如果后续要换域名，再修改 `deploy/Caddyfile` 即可。

## 4. Cloudflare 配置

在 Cloudflare 中准备两条记录：

- `A` / `AAAA`：`xiangqiarena.com` -> VPS 公网 IP
- `A` / `AAAA`：`www.xiangqiarena.com` -> VPS 公网 IP

建议：

- 先使用 `DNS only` 完成首次证书签发
- Caddy 成功获取证书后，再切回 Cloudflare 代理

这样可以避免首次 ACME 验证被代理层干扰。

## 5. 启动服务

构建并后台启动：

```bash
docker compose build app
docker compose up -d
```

查看状态：

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f caddy
```

## 6. 验证

先在 VPS 本机验证：

```bash
curl -I http://127.0.0.1:18388
docker compose exec db sh -lc 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

再在公网验证：

```bash
curl -I https://www.xiangqiarena.com
curl -I https://xiangqiarena.com
```

预期结果：

- `https://www.xiangqiarena.com` 返回站点响应
- `https://xiangqiarena.com` 返回 `301/308` 到 `www`
- `http://127.0.0.1:18388` 可直接返回应用响应
- `app` 日志里不再回退到本地 H2

## 7. 升级流程

更新代码并重建：

```bash
git pull
docker compose build app
docker compose up -d
```

如果只更新 Caddy 配置：

```bash
docker compose up -d caddy
```

## 8. 备份建议

最低要求：

- 定期导出 Postgres
- 把导出文件放到 VPS 之外

仓库已提供最小备份脚本：

```bash
bash tools/backup_postgres.sh
```

不要把生产环境长期建立在 H2 文件库上；该项目已经支持 `XQ_DATABASE_URL`，线上默认应使用 Postgres。

脚本行为：

- 在仓库根目录创建 `backups/`
- 生成 UTC 时间戳命名的 `postgres-*.sql.gz`
- 默认只保留最近 `7` 份备份

如需调整保留数量：

```bash
KEEP_COUNT=14 bash tools/backup_postgres.sh
```

如需自动执行，可以先用 cron 做最小方案：

```bash
0 3 * * * cd /opt/chinese-chess && /usr/bin/env bash tools/backup_postgres.sh >> /var/log/xiangqi-backup.log 2>&1
```

## 9. 常见问题

### 1) 域名返回 502

优先检查：

- `docker compose ps`
- `docker compose logs app`
- `docker compose logs caddy`
- `app` 是否已在容器内启动并监听 `18388`

### 2) Caddy 没有签出证书

优先检查：

- Cloudflare 记录是否已指向 VPS
- `80/443` 是否开放
- 是否需要先把 Cloudflare 代理临时切成 `DNS only`

### 3) 应用启动后写入了 H2

说明 `XQ_DATABASE_URL` 未生效。检查：

- `.env` 是否存在
- `compose.yaml` 是否实际加载了 `.env`
- `POSTGRES_*` 是否为空

### 4) 数据库容器已启动但应用仍连不上

优先检查：

- `docker compose exec db sh -lc 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`
- `docker compose logs app`
- 是否误改了 `XQ_DATABASE_URL`
