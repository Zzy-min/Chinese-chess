# 轻棋局项目全站视觉优化与游戏运行性能深度治理设计规范

- 日期：2026-11-06
- 阶段：Spec / Design
- 仓库路径：`C:\Users\Lenovo\Chinese-chess`
- 目标：全面治理轻棋局（XiangqiArena）站点的资源加载异常、五子棋/象棋双棋种角色标识偏差、棋盘移动防抖与触控体验、跨端（桌面至 320px 小屏）视觉质感与运行流畅性。

---

## 1. 背景与现状诊断

经过对本机运行环境与当前主分支代码（`C:\Users\Lenovo\Chinese-chess`，端口 18388）的端到端浏览器自动化探测与代码审查，定位出以下核心问题：

1. **Base64 WebP 图片数据损坏导致的 `net::ERR_INVALID_URL` 控制台报错**：
   - `src/main/resources/online/app.css` 中存在手误误触输入的非 Base64 字符：
     - 行 1287（`.deskHero` 桌面背景图）：尾部附带中文字符 `安`；
     - 行 2080（`.xiangqiTheme` 象棋背景图）：尾部附带中文字符 `起`；
     - 行 2086（`.gomokuTheme` 五子棋背景图）：尾部附带多余分号 `;`。
   - 导致 Chromium 在解析 Data URL 时拒绝解析，控制台持续抛出 `Failed to load resource: net::ERR_INVALID_URL`。

2. **五子棋人机练习界面双方阵营标识错乱**：
   - 在 `src/main/resources/online/app.js` 的 `renderPracticeView` 中：
     - 人类玩家卡片硬编码仅区分 `viewerSide === 'BLACK' ? '黑' : '红'`，忽略了五子棋对阵双方为「黑」与「白」；
     - AI 棋桌卡片硬编码了 `data-clock-card="BLACK"` 和 `<span class="mobileSideBadge is-black">黑</span>`。
   - 当用户在五子棋练习执黑先手时，AI 卡片同样显示为「黑」，出现双方皆为黑棋的违和显示。

3. **小屏移动端（320px~375px）触控与对局体验治理**：
   - 象棋与五子棋棋盘在超小屏（320px 如 iPhone SE 第一代 / 特殊手持设备）下需要确保格子按钮点击热区不小于 40~44px，并避免任何局部横向溢出。
   - 走棋动作在移动端弱网络或快速连击时，需具备严格的 `moveInFlight` / 动作并发防护，防止多次落子冲突或产生重叠请求。

4. **国风视觉与动效微调**：
   - 维持既有水墨国风设计主色调（竹青 `#4D6756`、米黄 `#F6F1E8`、赭石 `#8B2E2E`），确保落子辅助光圈、选中高亮、步时计时状态等元素在浅色与水墨主题下均有清晰层次。

---

## 2. 详细设计方案

### 2.1 CSS 资源纯净化
- 修复 `src/main/resources/online/app.css` 中损坏的三处 WebP Base64 编码，移除 `安`、`起` 及多余 `;`。
- 确保所有 data URL 解码还原后的数据符合标准 WebP (RIFF/WEBP) 格式。

### 2.2 双棋种阵营与角色徽标统一化
- 在 `src/main/resources/online/app.js` 中动态识别游戏类型 `game.gameType`：
  - 象棋（XIANGQI）：人类默认红方（`RED`），AI 为黑方（`BLACK`），徽章显示为「红」/「黑」；
  - 五子棋（GOMOKU）：人类默认黑方（`BLACK`），AI 为白方（`WHITE`），徽章显示为「黑」/「白」，样式类对应 `is-black` / `is-white`；
  - 动态适配 AI 阵营，使 AI 卡片的 `data-clock-card` 与徽章随对局真实 `aiSide` 响应。

### 2.3 操作响应与防抖控制
- 对人机对局和在线对局中的落子点击实行严格的本地状态锁：在人类已发起合法着法且等待 AI 或服务器应答阶段，禁用重复提交，走子动画使用 `requestAnimationFrame` 统一调度。

### 2.4 多视口响应式防溢出保证
- 桌面端（1440px+）：左右分栏布局舒适，背景水墨纹理平滑居中；
- 平板端（768px~1024px）：棋盘自适应居中缩放；
- 手机端（320px~375px）：棋盘与上下控制区采用弹性高度计算，隐藏无关冗余元素，安全区自适应 `env(safe-area-inset-bottom)`。

---

## 3. 验证准则
1. `mvn test` 109 项既有单元测试与资源契约测试 100% 通过；
2. 真实浏览器 Playwright 自动化巡检，桌面端与移动端 4 种典型视口全路由无 `net::ERR_INVALID_URL` 报错；
3. 象棋与五子棋练习对局实测落子、AI 思考响应、悔棋、认输均正常。
