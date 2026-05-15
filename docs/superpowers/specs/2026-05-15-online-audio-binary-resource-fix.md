# Online 音效二进制资源修复设计

日期：2026-05-15

## 背景

用户反馈在线站点“没有音效”。前端 UI 中音效开关显示为开启，practice 落子流程也能正常走完，但实际没有任何落子音或终局音。

## 已确认事实

1. `/online` 前端仍保留完整音效逻辑：音效开关、首次交互解锁、落子音与终局音触发链都在。
2. 浏览器运行时里 `state.audioUnlocked === true`，说明不是“未交互导致未解锁”。
3. 浏览器确实请求了：
   1. `/assets/audio/move.wav`
   2. `/assets/audio/mate.wav`
4. 两个请求都返回 `200`，但 `HTMLAudioElement.error.code === 4`，报错为 `FFmpegDemuxer: open context failed`。
5. 将线上返回的 `move.wav` 下载到本地后，`ffprobe` 无法识别；与仓库内源文件对比可见，部分高位字节被替换成 UTF-8 replacement bytes `EF BF BD`。

## 根因

`PublicSiteServer.sendResource()` 当前会把资源字节先转成 UTF-8 字符串，再通过字符串响应发送。这个做法对 HTML/CSS/JS 文本资源可用，但会破坏 WAV 这类二进制资源，导致音频文件内容被改写，浏览器收到 200 响应也无法解码。

## 方案

### 1. 资源响应改为按字节发送

将 `PublicSiteServer.sendResource()` 改成直接输出原始字节，不再走 `new String(..., UTF_8)`。

### 2. 保持现有前端音效逻辑不变

本次不改 `online/app.js` 的音频解锁和触发逻辑，因为当前证据显示前端链路已经执行到请求资源这一步，真正故障点在服务端字节输出。

### 3. 增加二进制资源回归测试

在 `PublicSiteServerTest` 中新增音频资源测试，直接请求 `/assets/audio/move.wav`，校验：

1. HTTP 200
2. `Content-Type` 为 `audio/wav`
3. 响应体前几个字节与类路径中的 `/audio/move.wav` 完全一致

## 验收标准

1. 浏览器进入 `#/learn/practice` 后，交互落子能够听到音效。
2. `move.wav` 线上响应体可被浏览器正常解码，不再出现 `DEMUXER_ERROR_COULD_NOT_OPEN`。
3. `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest" test` 通过。
4. 线上重新部署后，Playwright 运行时中 `onlineMoveAudio.error === null` 或至少不再是解码失败。
