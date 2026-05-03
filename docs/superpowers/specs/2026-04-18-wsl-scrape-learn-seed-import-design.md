# WSL 抓取学习资源导入 Online 学习模块设计

## 背景

用户已在 WSL 路径 `~/xiangqi_scrape/` 完成象棋学习资源抓取，核心文件包含：

- `xqbase/残局题_FEN.json`（222 条）
- `README_学习资源汇总.md`

当前 Online 学习模块题库条目较少，无法体现已抓取资源规模。

## 目标

1. 将 WSL 抓取的 222 条残局题导入 `online` 学习种子数据。
2. 每条题目保留来源与原始 FEN 文本，避免抓取信息丢失。
3. 学习页可直接看到题目的 FEN 字段（便于后续复现与校验）。

## 非目标

1. 本轮不实现 FEN 自动落盘到可交互棋盘。
2. 本轮不实现官方答案自动求解。
3. 不改后端 API 协议。

## 方案

1. 读取 WSL 文件 `/home/lenovo/xiangqi_scrape/xqbase/残局题_FEN.json`。
2. 将题目转换为 `learn-content.seed.json` 的 `puzzles` 项，并附加：
   - `fen`（清洗后的原始 FEN 字符串）
   - `source`（`xqbase.com`）
3. 保留已有手工题库条目，在其后追加导入条目。
4. 前端 `renderLearnPuzzles` 增加对 `item.fen` 的可视展示块。

## 验证

1. `GET /online/api/learn/content` 返回的 `puzzles` 数量显著增加（>= 200）。
2. 学习页题目卡可看到 FEN 文本。
3. `mvn -q test` 通过。
