# Implementation Plan: Guofeng Replica Tournament Removal & Deep Refinement

本计划描述了将轻棋局站点的 HTML、CSS 和 JavaScript 彻底重构为最新效果图水墨界面的具体实施步骤，特别着重于“赛事活动”的彻底移除与各页面的深度复刻。

## 实施路线图

### 1. 修改导航与公共页面结构 (Topbar & Routes)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)：
*   修改 `renderTopbar`，将导航栏更改为：首页、对局、棋谱、排行榜、社区、帮助。
*   修改 `pageRegistry`，将 `learn` 页重定向为棋谱库渲染函数 `renderLearnPage`；确保移除了任何“赛事”路由关联。

### 2. 重写首页渲染函数 (Home Page Refinement)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js) 的 `renderHomePageGuofeng()`：
*   快捷圆钮（`deskQuickGrid`）：删掉赛事，将四个按钮重新定为快速匹配、好友对弈、棋谱库、个人中心。
*   三栏组件（`deskHomeThreeCol`）：
    *   左栏重绘为“每日任务”与“每日签到”的合并区域。任务条带朱红色“去完成”微型动作按钮。
    *   中栏与右栏分别绘制“象棋排行榜”与“五子棋排行榜”，前三名加挂特殊的 `.rankNum.num-1/2/3` 古风徽章色。

### 3. 重写对局大厅渲染函数 (Lobby Refinement)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js) 的 `renderPlayLobbyDesk()`：
*   左侧侧边栏：除去赛事活动选项，保留其它八个菜单，调整为精美古风的圆角挂载项。
*   右侧辅助栏：增加“推荐高手”卡片，通过双 Tab 切换象棋与五子棋周榜前 4 名的高手头像和分数。
*   大厅顶部：加设带胶囊背景的快速分类过滤（全部、象棋、五子棋、人机、好友、房间）。

### 4. 重写棋桌信息与走子记录 (Game Table Tab & Playback controls)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)：
*   修改 `renderOnlineGameView()`、`renderPracticeView()` 的左侧卡片，添加局时与回合数信息的渲染。
*   修改走子记录栏 Tab 头为：局势、棋谱、分析、设置。
*   在 `棋谱` 激活状态下，展示格式化的每步中文棋谱走法（象棋）与行列代号（五子棋）。
*   底部渲染包含播放进度跳转的木纹按钮组。

### 5. 个人中心页与棋谱库页面重构 (Profile & Learn Library Page)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)：
*   **`renderProfilePage()`**: 渲染左侧菜单导航，右侧包含头像ID、总局数、胜率、积分大卡片以及 4 个勋章大挂件。
*   **`renderLearnPage()`**: 渲染顶部大分类 Tab，下方卡片列表形式展示“经典飞相局”、“中局战术大全”等条目。

### 6. 追加配套样式 (CSS Stylings)
编辑 [app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)：
*   新增排行榜前 3 名徽章底色背景。
*   新增每日任务进度条、去完成按钮、勋章卡片网格样式。
*   微调大厅顶部胶囊过滤和侧边栏样式。

### 7. 构建与验证 (Build & Verification)
*   执行 `mvn -q -DskipTests compile` 编译。
*   运行 `screenshot.py` 脚本重新抓取 6 个路由的最终效果图并核对。
