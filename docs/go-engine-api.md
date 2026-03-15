# go-engine API Contract

主站围棋 PvE 通过一个独立 HTTP 服务接入。当前 Java 客户端期望如下接口：

## `GET /health`

用途：

- 判断服务是否在线
- 决定前端是否开放围棋人机模式

成功返回示例：

```json
{
  "ok": true,
  "engine": "KataGo"
}
```

## `POST /genmove`

请求体示例：

```json
{
  "size": 19,
  "komi": 7.5,
  "currentTurn": "BLACK",
  "toPlay": "BLACK",
  "aiStone": "BLACK",
  "difficulty": "MEDIUM",
  "rows": [
    "...................",
    "...................",
    "..................."
  ],
  "moves": [
    { "color": "BLACK", "row": 3, "col": 3, "pass": false },
    { "color": "WHITE", "row": 15, "col": 15, "pass": false }
  ]
}
```

成功返回示例：

```json
{
  "pass": false,
  "row": 16,
  "col": 3,
  "engine": "KataGo"
}
```

停一手返回示例：

```json
{
  "pass": true,
  "engine": "KataGo"
}
```

## `POST /score`

请求体示例：

```json
{
  "size": 19,
  "komi": 7.5,
  "currentTurn": "BLACK",
  "rows": [
    "...................",
    "...................",
    "..................."
  ],
  "moves": [
    { "color": "BLACK", "row": 3, "col": 3, "pass": false },
    { "color": "WHITE", "row": 15, "col": 15, "pass": false }
  ]
}
```

成功返回示例：

```json
{
  "blackArea": 182,
  "whiteArea": 174.5,
  "komi": 7.5,
  "finalScore": -7.5,
  "winner": "WHITE",
  "resultText": "白胜 7.5 目"
}
```

## 约定

- `row` / `col` 使用 0-based 坐标
- `color` 仅接受 `BLACK` 或 `WHITE`
- `rows` 是当前盘面快照；若存在，服务会优先用它还原局面
- `moves` 为按时间顺序排列的落子历史；当没有 `rows` 时，服务会按它重放
- `pass=true` 时可省略 `row` / `col`
- 服务不可用时，主站必须继续保留围棋 PvP 与题库能力
