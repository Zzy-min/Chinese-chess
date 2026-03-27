# PublicSiteServer 登录注册初始化缺陷设计

## 1. 背景与问题

线上 `xiangqiarena.com` 的登录与注册交互异常。根因排查结果如下：

- `GET /online/api/site/bootstrap` 可稳定返回 `500`
- 服务端日志明确报错：`Table "users" not found`
- `PublicSiteServer` 启动公共在线站点时没有调用 `OnlineStore.initSchema()`
- 同仓库内的 `OnlineSiteServer` 在启动时会先调用 `store.initSchema()`，因此该路径不会触发同类问题

登录/注册交互之所以失效，不是前端点击事件本身损坏，而是公共在线站点对应的数据表根本没有初始化。

## 2. 目标

- 修复 `PublicSiteServer` 默认公共站点路径的数据库 schema 初始化缺失
- 让 `/online/api/site/bootstrap`、注册、登录在空数据库首次启动时可正常工作
- 补回归测试，防止再次出现“公共站点构造成功但表未建好”的回归

## 3. 范围与非目标

### 3.1 本期范围

- 修改 `PublicSiteServer` 的初始化逻辑
- 为 `PublicSiteServer` 增加未预初始化 store 的回归测试
- 本地验证 bootstrap / register / login 主链路

### 3.2 非目标

- 不改前端交互文案与样式
- 不改认证协议、Cookie 名或接口路径
- 不改 `OnlineStore` schema 内容

## 4. 方案

采用最小修复：

1. 在 `PublicSiteServer` 构造阶段统一调用 `store.initSchema()`
2. 若 schema 初始化失败，直接抛出明确异常，避免站点以坏状态启动
3. 新增测试覆盖“传入一个未手动 `initSchema()` 的 `OnlineStore`，公共站点启动后 bootstrap 仍返回 200”

## 5. 验收

- `PublicSiteServerTest` 新增回归测试先失败后通过
- `mvn -q test` 通过
- 本地 `GET /online/api/site/bootstrap` 返回 `200`
- 本地注册/登录接口在空数据库首次启动场景可完成请求
