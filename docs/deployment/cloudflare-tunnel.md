# Cloudflare Tunnel 部署说明

本文档说明如何将 `xiangqiarena.com` 接入 Cloudflare，并把本机 `run_web.bat` 启动的 Java 站点通过正式 Tunnel 暴露到公网。

## 适用场景

- 域名注册商：`Spaceship`
- DNS / HTTPS / 公网入口：Cloudflare
- 源站：本机 Windows 电脑
- 站点入口：`com.xiangqi.web.PublicWebMain`

当前推荐把 `www.xiangqiarena.com` 作为主站入口，再按需要让根域 `xiangqiarena.com` 跳转到 `www`。

## 1. 前置条件

- 已拥有 `xiangqiarena.com`
- 已可登录 Cloudflare
- 本机已安装 Java 11+ 与 Maven
- 本机仓库路径：`D:\claude项目\XiangqiGame`

可选但推荐：

- 先安装 `cloudflared`
- 在公网发布前，用 `run_web.bat` 确认本机 `http://127.0.0.1:18388/` 可访问

## 2. 将域名接入 Cloudflare

1. 打开 Cloudflare Dashboard
2. 选择 `Add a domain`
3. 输入 `xiangqiarena.com`
4. 完成站点添加后，记录 Cloudflare 分配的两条 nameservers

然后到 Spaceship：

1. 打开该域名的 nameserver 设置
2. 把当前 nameservers 替换为 Cloudflare 提供的两条值
3. 保存

返回 Cloudflare，等待站点状态变为 `Active`。

## 3. 安装与登录 cloudflared

如果系统里还没有 `cloudflared`，推荐在 PowerShell 中安装：

```powershell
winget install --id Cloudflare.cloudflared -e
```

安装完成后，先确认命令可用：

```powershell
cloudflared --version
```

再登录 Cloudflare：

```powershell
cloudflared tunnel login
```

该命令会打开浏览器，让你选择 `xiangqiarena.com` 所在的 Cloudflare 账号与域名。

## 4. 创建正式 Tunnel

创建 Tunnel：

```powershell
cloudflared tunnel create xiangqiarena
```

成功后会得到一个 Tunnel ID，并在当前用户目录下生成凭据文件，通常位于：

```text
C:\Users\<你的用户名>\.cloudflared\
```

## 5. 绑定 DNS

创建 `www` 子域到 Tunnel 的绑定：

```powershell
cloudflared tunnel route dns xiangqiarena www.xiangqiarena.com
```

如果你也想让根域直接接到 Tunnel，可额外配置：

```powershell
cloudflared tunnel route dns xiangqiarena xiangqiarena.com
```

更稳妥的做法通常是：

- `www.xiangqiarena.com` 直连 Tunnel
- 根域在 Cloudflare 里做重定向到 `https://www.xiangqiarena.com/`

## 6. 启动本机源站

在仓库根目录启动公共站点：

```powershell
run_web.bat
```

如果你不希望每次启动都自动打开浏览器：

```powershell
$env:NO_BROWSER = "1"
run_web.bat
```

成功后，本机地址应能访问：

```text
http://127.0.0.1:18388/
```

## 7. 启动 Tunnel

仓库内提供了简化脚本：

```powershell
run_cloudflare_tunnel.bat
```

或直接运行：

```powershell
cloudflared tunnel run xiangqiarena
```

如果你使用的是自定义配置文件，也可以：

```powershell
cloudflared tunnel --config docs/deployment/cloudflared-config.example.yml run xiangqiarena
```

注意：示例配置文件中的 `tunnel` 与 `credentials-file` 需要改成你自己的值，不能直接原样使用。

## 8. 验证

先验证本机源站：

```powershell
curl.exe -I http://127.0.0.1:18388/
```

再验证公网入口：

```powershell
curl.exe -I https://www.xiangqiarena.com/
```

至少应满足：

- 本机返回 `200`
- 公网域名可建立 HTTPS 连接
- 浏览器中可以打开首页

## 9. 常见问题

### 1) Cloudflare 显示域名未激活

优先检查：

- Spaceship 的 nameservers 是否已改成 Cloudflare 提供值
- DNS 生效是否还在传播中

### 2) `cloudflared tunnel login` 后看不到域名

通常是：

- 登录的 Cloudflare 账号不对
- 域名还没在该账号中变成 `Active`

### 3) Tunnel 起来了，但站点打不开

优先检查：

- `run_web.bat` 是否真的把本机服务拉起来
- `http://127.0.0.1:18388/` 是否能本机访问
- Tunnel 是否绑定到了正确 hostname
- Windows 防火墙或本机网络是否拦截

### 4) 启动脚本报类找不到

先执行：

```powershell
mvn -q -DskipTests package
```

这一步会同时生成 `target\classes` 与 `target\dependency`，`run_web.bat` 依赖这两个目录。
