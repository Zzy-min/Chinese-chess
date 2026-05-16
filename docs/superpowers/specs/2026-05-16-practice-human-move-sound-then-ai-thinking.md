# Practice 人类落子音效先于 AI 思考设计

日期：2026-05-16

## 背景

用户进一步明确了 practice 验收标准：

1. 人类点击目标格后，棋子要先真实落到目标位置
2. 人类这一步落下时要立即播放落子音
3. 然后才进入 AI 思考
4. AI 应手出现时再单独播放 AI 落子音

上一轮 optimistic practice move 已让人类棋子能够在请求返回前先落位，但体验仍缺两点：

1. optimistic 人类落子时没有立即播放人类落子音
2. `moveInFlight` 期间状态文案仍优先显示 `正在提交走子...`，会掩盖“人类已落子，接下来才是 AI 思考”的阶段感

## 已确认事实

1. optimistic 人类落子不会走 `applyServerGameSnapshot()`，因此不会触发 `maybePlayMoveSound()`
2. 当前落子音仍只会在服务端 snapshot 流入时触发
3. 对于 practice 人类这一步，服务端 snapshot 返回时 `moveCount` 仍是 1，所以如果 optimistic 阶段手动播音，只要后续 snapshot 不重复触发即可
4. 当前 `practiceStatusText()` 在 `moveInFlight` 时固定返回 `正在提交走子...`

## 根因

practice 前端已经有 optimistic 棋盘落位，但没有把“人类已落子”的音效和状态阶段也一起前移。

## 方案

### 1. optimistic 人类落子立即播音

在 practice optimistic move 生效后，立即播放一次 `onlineMoveAudio`，对应“人类这一步已落下”。

### 2. 避免服务端回包重复播放人类这一步

在 optimistic 播音时同步推进：

1. `state.lastMoveSoundGameId`
2. `state.lastMoveSoundIndex`

这样服务端返回同一手 `moveCount=1` snapshot 时，不会再把人类这一步重复播一次。

### 3. 细化 practice moveInFlight 状态

当 practice 已应用 optimistic 人类落子且请求仍在飞行中时，状态不再显示 `正在提交走子...`，而改成强调“人类这一步已经落下、正在等待 AI 阶段”的文案。  
服务端确认返回 `aiPending=true` 后，再进入真正的 `AI 思考中...`。

### 4. 保持 AI 应手音效独立

AI 应手仍由后续 snapshot 通过 `maybePlayMoveSound()` 触发，不改现有逻辑。这样可以保持：

1. 人类步一声
2. AI 步一声

## 测试策略

### 1. 前端合同测试

继续约束：

1. 存在 `applyOptimisticPracticeMove`
2. practice 非 immediate 首轮 polling
3. 默认异步思考文案

并新增约束：

1. optimistic 链路里存在对 `onlineMoveAudio` 的显式播放

### 2. 浏览器真实验证

验证 4 个阶段：

1. 点击后 50ms 内：棋子已落位，并已触发一次人类落子音
2. 请求返回前：不显示 AI 应手，也不播放第二声
3. 请求返回后：进入 `AI 思考中...`
4. AI 应手返回后：出现第二声落子音

## 验收标准

1. 人类落子先落位。
2. 人类落子时立即有一声落子音。
3. 随后才进入 `AI 思考中...`。
4. AI 应手出现时再有一声独立落子音。
5. 不出现人类这一步的重复双响。
