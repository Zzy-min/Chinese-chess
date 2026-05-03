# main 集成 feat/vps-compose-caddy-deploy 实施计划

关联设计：`docs/superpowers/specs/2026-05-03-main-integrate-feat-vps-compose-caddy-deploy.md`

## 步骤
1. 分支预检：确认 `origin/main` 与 `origin/feat/vps-compose-caddy-deploy` 差异与提交序列。
2. 执行集成：优先 merge，必要时切换为 cherry-pick。
3. 本地验证：执行 Maven 测试（或最小关键测试集）。
4. 推送 `main`：仅在验证通过后推送远端。
5. 生产部署：运行 `tools/deploy_production_vps.py`（branch=main）并核对 `LOCAL_CHECK` 与 `PUBLIC_CHECK`。

## 回滚
1. 若验证失败，停止推送并在本地回退集成提交。
2. 若已推送且线上异常，使用上一个稳定 `main` 提交执行回滚部署。
