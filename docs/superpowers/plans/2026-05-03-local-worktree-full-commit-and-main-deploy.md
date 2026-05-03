# 本地工作树全量提交并部署计划

关联设计：`docs/superpowers/specs/2026-05-03-local-worktree-full-commit-and-main-deploy.md`

## 步骤
1. 在 `feat/vps-compose-caddy-deploy` 执行全量 `git add` 并提交。  
2. 推送该分支，保留本地改动快照。  
3. 基于最新 `origin/main` 集成本次提交（优先 cherry-pick）。  
4. 本地构建验证（至少 `mvn -DskipTests clean package`）。  
5. 推送 `main` 并执行生产部署脚本。  
6. 验证公网页面与关键接口。

## 回滚
1. 若集成失败，停止推送 `main`，保留 `feat` 提交作为快照。  
2. 若部署后异常，回退到上一个稳定 `main` 提交并重部署。
