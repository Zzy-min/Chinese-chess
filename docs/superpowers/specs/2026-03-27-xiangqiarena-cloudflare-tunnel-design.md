# XiangqiArena Cloudflare Tunnel 发布与仓库包装设计

## 1. 背景与目标

当前项目已具备可运行的 Java Web 对局能力，但现有公开呈现与部署方式存在两个直接问题：

- Render 部署链路因付费限制不可持续。
- GitHub 对外包装仍停留在旧定位，无法支撑新域名与正式公开入口。

已确认业务前提：

- 已购买域名：`xiangqiarena.com`
- 域名注册商：`Spaceship`
- 可登录 Cloudflare 账号已就绪
- `D:\claude项目\XiangqiGame` 所在 Windows 机器愿意作为临时线上机常开

本期目标：

1. 将项目发布路线收敛为 `Cloudflare DNS + Cloudflare Tunnel + 本机 Java 服务`
2. 同步升级 GitHub 对外包装，使仓库首页与新域名一致
3. 产出可重复执行的本机发布文档与脚本

本期“完成”定义：

- Cloudflare 侧接入路线明确并有仓库内文档
- 本机服务可通过 Cloudflare Tunnel 以正式域名访问
- GitHub 仓库 README 与部署文档已同步更新
- 所有“完成”结论均基于当次新鲜验证

## 2. 范围与非目标

### 2.1 本期范围

- 更新仓库首页文案与中英 README 首屏定位
- 新增 Cloudflare 接入与 Tunnel 发布说明
- 新增或调整本机启动脚本，确保 Java 服务与 Tunnel 有明确启动顺序
- 处理本地分支与远端 `origin/main` 的同步问题后再推送 GitHub
- 完成一次正式域名访问验证

### 2.2 非目标

- 不将现有 Java 服务重构为 Cloudflare Workers / Pages 原生架构
- 不迁移到 Cloudflare Containers
- 不重写项目核心后端逻辑、AI 逻辑或前端交互
- 不承诺中国大陆“高可用低延迟”正式商用体验；本期只保证可访问与可重复发布

## 3. 现状摘要

当前仓库与运行形态：

- Web 入口为 `com.xiangqi.web.PublicWebMain`
- 服务实现为 JDK `HttpServer` 单体 Java 进程
- 本地默认端口为 `18388`
- 当前仓库 `main` 相对 `origin/main` 落后 `17` 个提交，不能直接盲推
- `README.md`、`README.zh-CN.md` 与实际站点定位不完全一致
- 现有部署文件以 `render.yaml` 与 `Dockerfile` 为主

## 4. 方案对比与结论

候选方案：

1. `Cloudflare Tunnel + 本机常驻 + 现有 Java 服务`（采用）
2. 继续寻找其他免费 Java/Docker 托管平台（暂缓）
3. 重构为 Workers / Pages 架构（不采用）

采用方案 1 的原因：

- 不要求重写现有服务架构
- 不依赖 Render 付费层
- 与当前域名、Cloudflare 账号和本机常驻前提完全匹配
- 可以在最短路径上得到正式 HTTPS 域名入口

不采用方案 3 的原因：

- 当前服务是长期运行 JVM 进程，不适合直接迁入免费 Workers / Pages 运行模型
- 改造成本远高于本期“先上线”的目标

## 5. 设计与架构边界

### 5.1 域名与 DNS

职责：

- `Spaceship` 只负责域名注册
- Cloudflare 负责权威 DNS、HTTPS、代理入口与 Tunnel 绑定

强制约束：

- `xiangqiarena.com` 必须先在 Cloudflare 中添加成功
- `Spaceship` nameservers 必须切换到 Cloudflare 分配值
- 仅在 Cloudflare 仪表盘状态变为 active 后继续配置正式 Tunnel

推荐域名策略：

- `www.xiangqiarena.com` 作为主应用入口
- `xiangqiarena.com` 做同站点接入或 301 跳转到 `www`

### 5.2 本机源站

职责：

- 继续由 `PublicWebMain` 提供 HTTP 服务

强制约束：

- 源站监听端口保持 `18388`
- Tunnel 指向本机 HTTP 地址，例如 `http://127.0.0.1:18388`
- 不要求在本期引入额外反向代理（Nginx/Caddy）

运行模式：

- 初版先追求“手动可重复发布”
- 后续如需稳定性增强，再考虑 Windows 服务化

### 5.3 Cloudflare Tunnel

职责：

- 将公网域名流量转发到本地 Java 服务

强制约束：

- 使用正式 Tunnel，而非 Quick Tunnel
- `cloudflared` 登录与 Tunnel 绑定的 Cloudflare 账号必须和域名所在账号一致
- Tunnel 配置必须落入仓库文档，必要时提供不含敏感信息的样例配置

### 5.4 GitHub 对外包装

职责：

- 让仓库首页与新域名对外叙事一致

改动边界：

- 更新 `README.md`
- 更新 `README.zh-CN.md`
- 视必要程度更新 `README.en.md`
- 新增独立部署文档，避免将 Cloudflare 操作细节堆在首页

强制要求：

- README 首屏必须出现：`Xiangqi`、`Chinese Chess`、`Java`、`Web`
- 首屏必须出现正式域名或预留正式域名位
- 文案需明确当前主要公开入口是 Web，不再把桌面端放在首页主叙事中心

### 5.5 Git 同步策略

职责：

- 在不覆盖远端已有工作的前提下整合本地改动

强制约束：

- 先 `fetch/pull` 远端，再开始推送
- 若 README、部署文档或脚本与远端冲突，先合并冲突再继续
- 不允许使用破坏性命令覆盖远端或本地未知改动

## 6. 文件与产物设计

本期预期改动产物：

- `README.md`
- `README.zh-CN.md`
- 可能的 `README.en.md`
- 新部署文档，例如 `docs/deployment/cloudflare-tunnel.md`
- 如有必要，新增或更新 Windows 启动脚本
- `docs/superpowers/plans/2026-03-27-xiangqiarena-cloudflare-tunnel-implementation-plan.md`

本期不要求新增敏感信息文件：

- 不把 Cloudflare Token、Tunnel 凭据、账号信息提交到仓库
- 若需要示例配置，只提交模板

## 7. 操作流程设计

### 7.1 域名接入流程

1. 在 Cloudflare 添加 `xiangqiarena.com`
2. 获取 Cloudflare 分配的 nameservers
3. 到 `Spaceship` 修改 nameservers
4. 等待 Cloudflare 域名状态变为 active

### 7.2 Tunnel 建立流程

1. 安装或确认存在 `cloudflared`
2. 登录 Cloudflare：`cloudflared tunnel login`
3. 创建正式 Tunnel
4. 将 `www.xiangqiarena.com` 或根域绑定到该 Tunnel
5. 将本机 `http://127.0.0.1:18388` 作为 Tunnel 转发目标

### 7.3 应用发布流程

1. 本地编译/启动 Java 服务
2. 确认本机 `18388` 可访问
3. 启动 `cloudflared tunnel run`
4. 通过正式域名访问站点并验证首页与基础 API

### 7.4 GitHub 同步流程

1. 获取远端最新提交
2. 合并或快进到最新 `origin/main`
3. 应用 README / 文档 / 脚本改动
4. 本地验证后提交并推送

## 8. 错误处理与风险

### 8.1 域名接入风险

- nameserver 尚未生效时，Tunnel 绑定会失败或不稳定
- 需要明确区分“域名注册成功”和“Cloudflare 托管已激活”

### 8.2 本机可用性风险

- 电脑关机、睡眠、网络切换都会导致站点不可用
- 本期文档必须明确这是“临时线上机”模式，而非托管平台 SLA

### 8.3 Git 同步风险

- 本地分支落后远端 17 提交，直接提交会造成推送失败或冲突
- 必须先同步远端后再落本期改动

### 8.4 中国大陆访问风险

- 本期目标是可访问，不承诺中国大陆网络环境下的稳定低延迟
- 若后续目标升级为正式面向中国大陆运营，需要单独评估备案、链路与长期托管方案

## 9. 验证与验收

本期验收必须包含以下新鲜验证：

1. Git 验证
- `git status`
- `git log --oneline --decorate -n 5`
- 确认本地分支已与远端同步到可推送状态

2. 本机服务验证
- `mvn -q -DskipTests compile` 或等价可执行构建命令成功
- 本机 `http://127.0.0.1:18388/` 可访问

3. Tunnel 验证
- `cloudflared` 登录状态可用
- Tunnel 启动成功
- 正式域名返回可用页面

4. 基础功能验证
- 首页加载成功
- 至少一个基础 API 可响应，例如 `/api/state`
- HTTPS 证书与浏览器访问正常

5. 文档验证
- README 首屏文案与部署文档一致
- 文档中不包含敏感凭据

## 10. 结论

本期采用 `Cloudflare DNS + 正式 Tunnel + 本机 Java 服务` 路线，以最低改造成本恢复公网访问能力；同时通过 README 与部署文档更新，统一项目对外叙事与实际发布入口。该方案满足“先上线、低成本、可重复执行”的目标，但不等价于托管平台级高可用部署。
