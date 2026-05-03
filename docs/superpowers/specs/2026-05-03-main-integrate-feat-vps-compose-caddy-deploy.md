# main 集成 feat/vps-compose-caddy-deploy 设计

日期：2026-05-03

## 背景
生产部署脚本默认部署 `main`。当前用户确认应继续走 `main`，将 `feat/vps-compose-caddy-deploy` 的改动合入后再部署。

## 目标
1. 在不回退 `main` 已有提交的前提下，集成 `feat/vps-compose-caddy-deploy` 的有效变更。
2. 处理潜在冲突并保留 `main` 上已验证修复。
3. 推送到 `origin/main` 后执行生产部署与公网验证。

## 非目标
1. 不直接部署 `feat` 分支。
2. 不改写远端历史（不 force push）。
3. 不做超出本次集成目的的额外重构。

## 集成策略
1. 在隔离临时克隆中操作，避免污染现有工作目录。
2. 以 `main` 为基线，优先尝试 `merge --no-ff origin/feat/vps-compose-caddy-deploy`。
3. 若冲突复杂，则改为逐提交 `cherry-pick`（仅挑选需上线提交）。

## 验收标准
1. `origin/main` 包含本次需要上线的 `feat` 变更。
2. 本地至少通过核心测试/构建验证。
3. 生产部署脚本输出 `Deploy completed.` 且公网检查成功。
