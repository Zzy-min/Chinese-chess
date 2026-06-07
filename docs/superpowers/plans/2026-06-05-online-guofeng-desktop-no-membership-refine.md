# Implementation Plan: Online Guofeng Desktop Refinement Without Membership

## 1. Scope Lock
- 只改当前命中的桌面国风渲染路径：
  - `renderTopbar`
  - `renderHomePageGuofeng`
  - `renderPlayLobbyDesk`
  - `renderLearnPage`
  - `renderProfile`
  - `renderOnlineGameView`
  - `renderPracticeView`
  - `renderClockCard`
- 不改接口协议，不碰棋局计算逻辑。

## 2. JS Structure Changes
- `renderTopbar`
  - 导航文案改为：首页、对局、练习、排行榜、社区、帮助
  - `learn` 从“棋谱”调整为“练习”
  - `watch` 从“俱乐部”调整为“社区”
- `renderHomePageGuofeng`
  - 移除“每日签到/每日任务”主块
  - 改为“快捷入口 / 对局实况 / 排行榜”三栏
  - 保留双主入口与近期对局数据
- `renderPlayLobbyDesk`
  - 左栏从“等级+签到+俱乐部”调整为免费能力导航
  - 用户摘要改为中性状态，不出现 `LV` 或 `VIP`
  - 右栏保留建房、入房、排行榜
- `renderLearnPage`
  - 保持练习/题库导向
  - 文案从“棋谱库包”收敛为公开学习资源
- `renderProfile`
  - 去掉硬编码等级和虚构 ID
  - 用户摘要改为真实用户名、对局数、胜率、积分
- `renderOnlineGameView` / `renderPracticeView` / `renderClockCard`
  - 左栏身份标签去掉 `vipBadge` 的会员感文案
  - 改为阵营、状态、难度、实时对局信息

## 3. CSS Changes
- 精修 `topbar`、`nav`、`userBar` 间距，让顶部更贴近参考图的轻桌面窗口感。
- 调整首页 `deskHero`、`deskQuickGrid`、`deskHomeThreeCol` 的栅格关系。
- 调整大厅 `deskSidebar`、`deskLobbyMain`、`deskLobbyAside`，移除签到卡片依赖。
- 为新的中性身份标签补样式，弱化 `vipBadge` 的视觉语义，必要时改名但尽量少扰动。
- 保持棋桌区现有自适应、右栏滚动与按钮状态样式。

## 4. Data Usage Policy
- 能用真实数据的地方优先使用：
  - `state.bootstrap`
  - `state.communityLeaderboard`
  - `state.profile`
  - `state.me`
- 无真实后端字段支撑时，用中性文案，不引入新的伪功能承诺。

## 5. Verification Steps
1. `node --check src/main/resources/online/app.js`
2. `mvn -q -DskipTests compile`
3. 本地启动网页并抓取至少：
   - 首页
   - 对局大厅
   - 练习棋桌
4. 人工核对：
   - 是否已经去掉会员/VIP/LV/俱乐部等会员感元素
   - 主要入口是否仍然可点击可达
   - 棋盘页布局是否未回退
