# XiangqiArena Cloudflare Tunnel Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 `xiangqiarena.com` 为正式入口，将项目从 Render 叙事迁移到 `Cloudflare DNS + Cloudflare Tunnel + 本机 Java 服务`，并同步更新 GitHub 对外包装与可重复执行的本机发布文档。

**Architecture:** 采用“仓库包装更新 + 本机启动脚本补齐 + Cloudflare Tunnel 文档化 + 真实域名验证”四层实现。核心 Java 服务保持不变，新增的都是对外文案、操作脚本与部署说明；Cloudflare 只负责 DNS、HTTPS 和入口代理，本机 `PublicWebMain` 继续作为源站。

**Tech Stack:** Java 11, Maven 3.9+, PowerShell, Windows Batch, Git, GitHub CLI, Cloudflare Dashboard, `cloudflared`

---

## File Structure

### Existing files to modify
- `README.md`: 改成面向公开访问者的仓库落地页，首屏指向 `xiangqiarena.com` 与中英文说明。
- `README.zh-CN.md`: 从“Render 主入口”改为“Cloudflare + 本机源站”叙事，补充中文部署说明索引。
- `README.en.md`: 与中文说明对齐，去掉 Render 作为当前主路径的表述。
- `run_web.bat`: 保持本地浏览器模式定位不变，但必要时补充与公共入口脚本的关系说明。

### New files to create
- `docs/deployment/cloudflare-tunnel.md`: Cloudflare 接入、Tunnel 创建、DNS 绑定与本机运行的主说明文档。
- `docs/deployment/cloudflared-config.example.yml`: 不含敏感信息的示例 Tunnel 配置。
- `run_public_web.bat`: 启动 `com.xiangqi.web.PublicWebMain` 并等待本机 `18388` 就绪。
- `run_cloudflare_tunnel.bat`: 检查 `cloudflared` 可用性并启动指定 Tunnel。

### Environment / operator steps (no committed files)
- Cloudflare 仪表盘添加站点 `xiangqiarena.com`
- Spaceship 将 nameservers 切换到 Cloudflare
- GitHub 仓库 `About` 信息同步到正式域名与新描述

---

## Chunk 1: 仓库同步与环境预检

### Task 1: 获取远端最新提交并把本地分支同步到可推送状态

**Files:**
- None (git state only)

- [ ] **Step 1: 获取远端提交信息**

Run: `git -C "D:\claude项目\XiangqiGame" fetch origin`
Expected: fetch 成功，无 fatal 错误

- [ ] **Step 2: 查看本地与远端分叉情况**

Run: `git -C "D:\claude项目\XiangqiGame" status --short --branch`
Expected: 看到 `main...origin/main` 的最新状态，确认是否仍然落后远端

- [ ] **Step 3: 查看远端最近提交，确认变更集中区域**

Run: `git -C "D:\claude项目\XiangqiGame" log --oneline --decorate --graph -n 20 --all`
Expected: 能看见 `origin/main` 最近 20 条提交，便于预判 README / 脚本冲突

- [ ] **Step 4: 将本地分支合并或快进到最新远端**

Run: `git -C "D:\claude项目\XiangqiGame" pull --ff-only origin main`
Expected: 成功快进；若失败则改用显式合并流程并记录冲突文件

- [ ] **Step 5: 再次确认工作树状态**

Run: `git -C "D:\claude项目\XiangqiGame" status --short --branch`
Expected: 分支不再显示 `behind`，仅保留本期新增 spec/plan 文件与后续改动

### Task 2: 预检必需命令与登录状态

**Files:**
- None (environment checks)

- [ ] **Step 1: 确认 GitHub CLI 可用**

Run: `gh auth status`
Expected: 输出已登录账号；若未登录，则记录为人工登录前置步骤

- [ ] **Step 2: 确认 Cloudflare Tunnel CLI 可用**

Run: `cloudflared --version`
Expected: 输出版本号；若命令不存在，则记录为安装前置步骤

- [ ] **Step 3: 确认 Maven 构建工具可用**

Run: `mvn -version`
Expected: 输出 Java / Maven 版本，满足 Java 11+ 与 Maven 3.9+（或本机可兼容版本）

- [ ] **Step 4: 检查 Cloudflare 身份状态**

Run: `cloudflared tunnel list`
Expected: 若已登录则列出 Tunnel 或空列表；若未登录，则提示后续执行 `cloudflared tunnel login`

---

## Chunk 2: GitHub 对外包装与部署文档

### Task 3: 重写仓库入口 README，让正式域名成为首屏主入口

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 先写一个最小首屏草稿，明确产品定位与正式域名**

```md
# XiangqiArena

Java web Xiangqi / Chinese Chess project with a public browser entry.

- Live site: `https://xiangqiarena.com/`
- Chinese docs: [`README.zh-CN.md`](README.zh-CN.md)
- English docs: [`README.en.md`](README.en.md)
```

- [ ] **Step 2: 用 `apply_patch` 将 `README.md` 改成入口页结构**

Run: file edit only
Expected: `README.md` 首屏出现 `Xiangqi`、`Chinese Chess`、`Java`、`Web` 与 `xiangqiarena.com`

- [ ] **Step 3: 本地检查 README 首屏是否简洁且无 Render 主叙事残留**

Run: `Get-Content -Path "D:\claude项目\XiangqiGame\README.md" -TotalCount 80`
Expected: 首页不再把 Render 作为当前主入口

- [ ] **Step 4: 暂存并检查 diff**

Run: `git -C "D:\claude项目\XiangqiGame" diff -- README.md`
Expected: diff 仅包含 README 入口页改动

- [ ] **Step 5: 提交**

```bash
git -C "D:\claude项目\XiangqiGame" add README.md
git -C "D:\claude项目\XiangqiGame" commit -m "docs: refresh repository landing page for xiangqiarena"
```

### Task 4: 更新中英文 README，迁移部署叙事到 Cloudflare + 本机源站

**Files:**
- Modify: `README.zh-CN.md`
- Modify: `README.en.md`

- [ ] **Step 1: 在中文 README 中替换当前线上入口与部署说明**

```md
## 在线地址

- 正式域名：`https://xiangqiarena.com/`
- 部署说明：[`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
```

- [ ] **Step 2: 在英文 README 中同步替换正式域名与部署说明**

```md
## Live URL

- Production: `https://xiangqiarena.com/`
- Deployment guide: [`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
```

- [ ] **Step 3: 删除或降级 Render 为历史配置，不再当作主部署路径**

Run: file edit only
Expected: `render.yaml` / `Dockerfile` 仍可保留，但 README 中改为 historical/legacy 描述

- [ ] **Step 4: 本地预览两份 README 关键段落**

Run: `Get-Content -Path "D:\claude项目\XiangqiGame\README.zh-CN.md" -TotalCount 140`
Expected: 中文版已出现正式域名与 Cloudflare 文档入口

Run: `Get-Content -Path "D:\claude项目\XiangqiGame\README.en.md" -TotalCount 140`
Expected: 英文版与中文定位一致

- [ ] **Step 5: 提交**

```bash
git -C "D:\claude项目\XiangqiGame" add README.zh-CN.md README.en.md
git -C "D:\claude项目\XiangqiGame" commit -m "docs: switch deployment narrative to cloudflare tunnel"
```

### Task 5: 新增 Cloudflare Tunnel 部署主文档与示例配置

**Files:**
- Create: `docs/deployment/cloudflare-tunnel.md`
- Create: `docs/deployment/cloudflared-config.example.yml`

- [ ] **Step 1: 写中文部署文档，覆盖 Cloudflare 与 Spaceship 的操作顺序**

```md
1. 在 Cloudflare 添加 `xiangqiarena.com`
2. 复制 Cloudflare nameservers 到 Spaceship
3. 等待站点状态 active
4. 登录 `cloudflared`
5. 创建 Tunnel 并绑定 `www.xiangqiarena.com`
```

- [ ] **Step 2: 写不含敏感信息的 `cloudflared` 示例配置**

```yaml
tunnel: YOUR_TUNNEL_ID
credentials-file: C:\Users\<user>\.cloudflared\YOUR_TUNNEL_ID.json

ingress:
  - hostname: www.xiangqiarena.com
    service: http://127.0.0.1:18388
  - service: http_status:404
```

- [ ] **Step 3: 在文档中明确哪些步骤必须人工在 Dashboard 完成**

Run: file edit only
Expected: 文档清楚标注“自动化可做”和“必须人工点击”的边界

- [ ] **Step 4: 本地检查新文档路径与示例文件路径**

Run: `Get-ChildItem -LiteralPath "D:\claude项目\XiangqiGame\docs\deployment"`
Expected: 同时看到 `cloudflare-tunnel.md` 与 `cloudflared-config.example.yml`

- [ ] **Step 5: 提交**

```bash
git -C "D:\claude项目\XiangqiGame" add docs/deployment/cloudflare-tunnel.md docs/deployment/cloudflared-config.example.yml
git -C "D:\claude项目\XiangqiGame" commit -m "docs: add cloudflare tunnel deployment guide"
```

---

## Chunk 3: 本机公共入口脚本

### Task 6: 新增 `run_public_web.bat`，启动 PublicWebMain 并等待端口就绪

**Files:**
- Create: `run_public_web.bat`

- [ ] **Step 1: 写失败前置检查，确保脚本在类文件缺失时会走编译**

```bat
if not exist "target\classes\com\xiangqi\web\PublicWebMain.class" goto :compile
if not exist "target\classes\com\xiangqi\web\WebXiangqiServer.class" goto :compile
```

- [ ] **Step 2: 用 `apply_patch` 创建 `run_public_web.bat`**

```bat
set "URL=http://127.0.0.1:18388/"
set "MAIN_CLASS=com.xiangqi.web.PublicWebMain"
set "BIND_HOST=127.0.0.1"
```

- [ ] **Step 3: 让脚本在后台启动服务并轮询端口 readiness**

Run: file edit only
Expected: 脚本能在 10~15 秒内检测 `http://127.0.0.1:18388/` 是否可用

- [ ] **Step 4: 本地运行脚本验证服务可启动**

Run: `cmd /c "D:\claude项目\XiangqiGame\run_public_web.bat --rebuild"`
Expected: 服务启动成功，并提示 `Service is ready`

- [ ] **Step 5: 提交**

```bash
git -C "D:\claude项目\XiangqiGame" add run_public_web.bat
git -C "D:\claude项目\XiangqiGame" commit -m "chore: add public web startup script"
```

### Task 7: 新增 `run_cloudflare_tunnel.bat`，统一 Tunnel 启动方式

**Files:**
- Create: `run_cloudflare_tunnel.bat`

- [ ] **Step 1: 写脚本用法与前置检查**

```bat
where cloudflared >nul 2>nul || (
  echo cloudflared is not installed.
  exit /b 1
)
```

- [ ] **Step 2: 支持从参数读取 Tunnel 名称，默认可填 `xiangqiarena`**

```bat
set "TUNNEL_NAME=%~1"
if "%TUNNEL_NAME%"=="" set "TUNNEL_NAME=xiangqiarena"
```

- [ ] **Step 3: 启动 Tunnel 并保留日志输出**

Run: file edit only
Expected: 脚本执行 `cloudflared tunnel run <name>`，失败时输出清晰错误

- [ ] **Step 4: 本地检查脚本帮助与错误提示**

Run: `cmd /c "D:\claude项目\XiangqiGame\run_cloudflare_tunnel.bat"`
Expected: 若 Tunnel 尚未创建，至少能得到明确的 `cloudflared` 错误信息

- [ ] **Step 5: 提交**

```bash
git -C "D:\claude项目\XiangqiGame" add run_cloudflare_tunnel.bat
git -C "D:\claude项目\XiangqiGame" commit -m "chore: add cloudflare tunnel runner script"
```

---

## Chunk 4: Cloudflare 接入、仓库元数据与真实访问验证

### Task 8: 在 Cloudflare 激活 `xiangqiarena.com`

**Files:**
- None (dashboard + registrar actions)

- [ ] **Step 1: 在 Cloudflare Dashboard 添加站点**

Manual: Add site `xiangqiarena.com`
Expected: Cloudflare 分配两条 nameservers

- [ ] **Step 2: 在 Spaceship 替换 nameservers**

Manual: Replace current nameservers with the Cloudflare pair
Expected: Spaceship 保存成功

- [ ] **Step 3: 等待 Cloudflare 状态变为 active**

Run: Cloudflare dashboard refresh
Expected: site status = active

- [ ] **Step 4: 记录正式入口策略**

Manual: Decide whether `www.xiangqiarena.com` or root domain is the primary app hostname
Expected: 文档和后续 DNS 绑定保持一致

- [ ] **Step 5: 保存操作结果**

Run: take local notes only
Expected: 已知道 nameservers 已生效且站点 active

### Task 9: 创建 Tunnel、绑定 DNS 并验证本机源站

**Files:**
- None (cloudflared state only)

- [ ] **Step 1: 启动本机公共源站**

Run: `cmd /c "D:\claude项目\XiangqiGame\run_public_web.bat --rebuild"`
Expected: 本机 `http://127.0.0.1:18388/` 返回 200

- [ ] **Step 2: 登录 Cloudflare 并创建 Tunnel**

Run: `cloudflared tunnel login`
Expected: 浏览器授权成功

Run: `cloudflared tunnel create xiangqiarena`
Expected: 返回 Tunnel ID，并生成本地凭据文件

- [ ] **Step 3: 将 DNS 记录绑定到 Tunnel**

Run: `cloudflared tunnel route dns xiangqiarena www.xiangqiarena.com`
Expected: DNS 记录创建成功

- [ ] **Step 4: 使用示例配置或默认命令启动 Tunnel**

Run: `cloudflared tunnel run xiangqiarena`
Expected: 控制台显示已连接 Cloudflare edge

- [ ] **Step 5: 访问正式域名验证**

Run: `curl.exe -I https://www.xiangqiarena.com/`
Expected: 返回 `200` 或可接受的重定向链，证书正常

### Task 10: 更新 GitHub 仓库 About 信息并推送全部改动

**Files:**
- None (GitHub metadata + git push)

- [ ] **Step 1: 更新 GitHub 仓库描述与主页**

Run: `gh repo edit Zzy-min/Chinese-chess --description "Java web Xiangqi (Chinese Chess) project with a public browser experience and Cloudflare Tunnel deployment." --homepage "https://www.xiangqiarena.com/"`
Expected: 仓库 About 信息更新成功

- [ ] **Step 2: 如需，补 topics**

Run: `gh repo edit Zzy-min/Chinese-chess --add-topic xiangqi --add-topic chinese-chess --add-topic java --add-topic web --add-topic cloudflare-tunnel`
Expected: topics 更新成功

- [ ] **Step 3: 查看最终 git 状态**

Run: `git -C "D:\claude项目\XiangqiGame" status --short --branch`
Expected: 仅剩待推送提交，无未预期文件

- [ ] **Step 4: 推送到 GitHub**

Run: `git -C "D:\claude项目\XiangqiGame" push origin main`
Expected: push 成功

- [ ] **Step 5: 打开仓库主页与正式域名做最终验收**

Run: visit `https://github.com/Zzy-min/Chinese-chess` and `https://www.xiangqiarena.com/`
Expected: 仓库首页与正式域名叙事一致，站点可访问
