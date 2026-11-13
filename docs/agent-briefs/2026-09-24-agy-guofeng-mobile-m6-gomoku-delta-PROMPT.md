# 轻棋局（QingQiJu）移动端国风 m6→m7 五子棋屏布局轻改 PROMPT（A-04 专章）

> 提供给 Antigravity（agy）或手工修改执行。  
> 本文件只输出修改提示词，不改码、不生成图片、不执行部署任务。  
> 目标：在 m6 基础上，对 **A-04 五子棋对局屏** 做轻量布局校准，使其与 A-02 象棋、A-03 AI 练习保持同一移动端节奏。

---

# 一、任务总则（最高优先级）

## A / B 视觉来源定义

### A = chatgpt-refs（主视觉真源）

本轮主目标：

```

A-04-gomoku-match.png

```

即：

- 五子棋对局视觉真源

布局参照：

```

A-02-xiangqi-match.png
A-03-ai-practice.png

```

说明：

- A-02 / A-03 仅作为移动端布局节奏参照（LAYOUT-REF）
- 不作为本轮整页重做目标

---

### B = shots-m6（当前实拍）

本轮主对照：

```

B-04-gomoku-match.png

```

一致性参照：

```

B-02-xiangqi-match.png
B-03-ai-practice.png

```

说明：

- B-02 / B-03 用于检查统一 chrome 节奏
- 禁止使用 shots / shots-m2 / shots-m3 / shots-m4 作为验收目标

---

# 二、本轮任务定义

任务：

```

m6 → m7 五子棋屏布局轻改

```

范围：

仅：

```

A-04 / boardPage--strict.gomokuTheme

```

目标：

解决五子棋页面与其他棋类页面布局节奏不一致问题。

---

## 核心目标顺序（必须闭合）

m7 五子棋移动端最终结构必须：

```

标题
↓
玩家双卡
↓
棋盘
↓
操作按钮
↓
棋盘 / 棋谱 Tab
↓
底部导航

```

禁止：

```

标题
↓
玩家
↓
棋盘
↓
Tab
↓
操作

```

当前错序必须修正。

---

# 三、方案 A（必须采用）

保持：

- 宣纸背景
- 水墨留白
- 朱砂强调
- 竹青墨绿辅助
- 木纹棋盘
- 国风棋类气质

本轮目标：

不是重新设计五子棋。

而是：

将五子棋 chrome 拉齐：

```

A-02 象棋对局节奏
+
A-03 AI练习节奏
+
A-04 五子棋视觉特色

```

---

# 四、资源版本

当前：

```

?v=20260924m6

```

建议：

```

?v=20260924m7

````

版本 bump 仅用于：

- 静态缓存刷新

禁止：

通过版本号掩盖布局未完成。

---

# 五、严格范围

## 主修改区域

优先：

```css
.boardPage--strict.gomokuTheme
````

其次：

```css
.mobileMatchChrome
.woodActions
.boardMobileTabs
```

必要时：

```js
renderMobileMatchChrome()
renderBoardPaneTabs()
```

---

## 允许修改

仅：

* mobile.css 展示层
* app.js 展示层顺序
* class / wrapper 展示结构

---

## 禁止扩展

不要修改：

* 首页
* 首页底栏整体设计
* 其他页面视觉系统
* 游戏业务结构

---

# 六、严格禁区（必须遵守）

## 业务逻辑禁区

禁止修改：

* WebSocket
* 走棋逻辑
* 胜负判断
* 匹配逻辑
* 棋盘数据结构
* 棋规
* AI 行棋逻辑

---

## AI / 身份真实性禁区

必须保留真实状态：

当前：

```
B-04 实拍 = 人机练习
```

允许：

```
玩家
AI 棋桌
MEDIUM
```

禁止：

* 制造第二真人
* 把 AI 棋桌改成真人头像
* 把人机练习伪装成在线双人比赛

---

## 产品真实性禁区

禁止：

* 虚构会员墙
* 新增 VIP
* 新增付费入口
* 新增广告
* 新增运营入口

---

## 工程禁区

禁止：

* git push
* 公网部署
* 上线发布

---

# 七、当前 CSS 错序问题（必须写死）

## 当前问题

当前：

```css
.boardPage--strict.gomokuTheme
```

下移动端 order 存在错序。

目前：

```css
.boardMobileTabs {
    order: 3;
}
```

同时：

```css
.woodActions {
    order: 4;
}
```

导致实际移动端结构：

```
标题
 ↓
玩家双卡
 ↓
棋盘
 ↓
棋盘/棋谱 Tab
 ↓
操作按钮
 ↓
底栏
```

即：

### 当前错误：

```
Tab 在操作按钮之前
```

与 A-02 / A-03 参考节奏不一致。

---

# 八、m7 必须修正的布局顺序

## 目标顺序

调整为：

```
标题
 ↓
玩家双卡
 ↓
棋盘
 ↓
操作按钮
 ↓
棋盘/棋谱 Tab
 ↓
底栏
```

---

## 推荐修正方向

允许：

### 方案 1：gomoku 专属 CSS order 修正

例如：

```css
.boardPage--strict.gomokuTheme .woodActions {
    order: 3;
}

.boardPage--strict.gomokuTheme .boardMobileTabs {
    order: 4;
}
```

要求：

仅影响：

```
gomokuTheme
```

不要污染：

* 象棋
* AI练习

---

### 方案 2：展示层结构调整

必要时：

调整：

```js
renderPracticeView()
renderMobileMatchChrome()
renderBoardPaneTabs()
```

输出顺序。

要求：

最终 DOM 顺序符合：

```
board
→ woodActions
→ boardMobileTabs
```

---

禁止：

为了调整顺序修改：

* 游戏状态
* 数据模型
* AI逻辑
* 棋盘逻辑

---

# 九、五子棋专章：B-04 → A-04 轻改要求

# 1. 顶部标题区域

目标：

保持：

```
五子棋对局
```

状态：

根据真实模式展示：

```
人机练习
```

或者：

```
在线对战
```

但必须匹配真实数据。

禁止：

人机练习模式显示虚假的在线双人。

---

目标：

与：

A-02 / A-03

统一：

* 顶栏高度
* 标题间距
* 呼吸空间

---

# 2. 玩家双卡

目标选择器：

```css
.boardPlayerCard
.clockGrid
.practiceGrid
```

要求：

统一：

* 双卡结构
* 圆角
* 背景
* 间距

保持：

头像印章圆。

保持：

红黑角标。

禁止：

角标挤压用户名。

---

## 时钟

保持：

展示占位：

例如：

```
14:32
13:48
```

要求：

* 居中
* 层级一致
* 不新增计时系统

---

# 3. 木纹棋盘区域

目标：

```css
.boardStage
.gomokuTheme
```

保持：

* 木纹盘壳
* 五子棋专属棋盘

调整：

* 上下空间
* 与玩家卡距离
* 与操作按钮距离

目标：

棋盘成为视觉主体。

---

# 4. 操作按钮

目标：

```css
.woodActions
```

保持：

```
悔棋
求和
认输
离开
```

风格：

* ghost
* 浅米色
* 弱边框
* 克制阴影

要求：

操作按钮必须位于：

```
棋盘之后
Tab之前
```

---

# 5. 棋盘 / 棋谱 Tab

目标：

```css
.boardMobileTabs
```

保持：

```
棋盘
棋谱
```

允许：

五子棋保留自己的：

* 朱砂实心选中态
* 胶囊形态

允许区别于象棋 underline。

但是：

位置必须统一：

```
操作按钮之后
底栏之前
```

---

# 十、共用组件约束

## renderMobileMatchChrome()

允许：

展示调整。

禁止：

改变：

* gameType 判断
* 模式逻辑
* 数据来源

---

## renderBoardPaneTabs()

允许：

调整：

* class
* 展示位置

禁止：

改变：

tab 功能。

---

# 十一、诚实局限必须保留

## A-04 双人样例 vs B-04 人机练习

事实：

```
A-04 = 双人在线视觉样例
B-04 = 人机练习实拍
```

处理：

保持真实。

禁止：

造第二真人。

---

## 练习时钟

保持：

展示占位。

禁止：

新增假计时。

---

## 头像

保持：

首字印章圆。

禁止：

生成真人写真。

---

# 十二、Tokens

继续复用：

```text
--bg-paper
--bg-paper-soft
--bg-panel
--brand-cinnabar
--brand-cinnabar-deep
--text-ink
--text-muted
--wood-board
--wood-border
```

五子辅助：

```text
#3B5B4F
```

视觉原则：

```
宣纸 + 水墨 + 朱砂 + 竹青 + 木纹
```

禁止：

重新建立颜色体系。

---

# 十三、M7 验收 Checklist

## 五子棋 A-04

* [ ] 只修改五子棋屏布局
* [ ] 不改首页
* [ ] 不改 WS
* [ ] 不改规则
* [ ] 不改 AI
* [ ] 不改匹配逻辑
* [ ] 不造第二真人
* [ ] 保留真实人机练习身份

---

## 布局检查

* [ ] 标题在顶部
* [ ] 玩家双卡在标题之后
* [ ] 棋盘在玩家之后
* [ ] 操作按钮在棋盘之后
* [ ] Tab 在操作按钮之后
* [ ] 底栏保持底部

---

## CSS order 检查

确认：

修正：

当前：

```css
.boardMobileTabs {
 order:3;
}

.woodActions {
 order:4;
}
```

造成的：

```
Tab → 操作
```

问题。

m7 必须：

```
操作 → Tab
```

---

## 回归检查

确认：

* [ ] A-02 象棋不破坏
* [ ] A-03 AI练习不破坏
* [ ] renderMobileBottomNav 不受污染
* [ ] renderMobileMatchChrome 其他模式正常

---

# 十四、交付要求

截图：

生成：

```
shots-m7/
```

至少：

```
04-gomoku-match.png
```

建议回归：

```
02-xiangqi-match.png
03-ai-practice.png
```

---

输出：

```
m7-RESULT.md
```

记录：

* m6 → m7 五子棋调整项
* CSS order 修正项
* 已完成项
* 保留限制
* 未修改业务范围

---

# 十五、最终执行原则

本轮不是重做五子棋。

唯一目标：

在：

```
不改变 WS
不改变规则
不改变 AI
不改变身份真实性
```

前提下，

修正：

```
标题
→ 玩家
→ 棋盘
→ 操作
→ Tab
→ 底栏
```

移动端节奏。

完成：

```
m6 → m7 五子棋布局轻改闭合。
```
