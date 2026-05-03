# 恢复线上功能集实施计划

关联设计：`docs/superpowers/specs/2026-05-03-restore-online-feature-set-from-working-tree.md`

## 步骤
1. 从 `D:\claude项目\XiangqiGame` 复制以下文件变更到隔离克隆：
   - `src/main/java/com/xiangqi/...`
   - `src/main/resources/online/...`
   - `src/test/java/com/xiangqi/...`
   - 新增：`XiangqiFenParser.java`、`learn-content.seed.json`
2. 在隔离克隆执行 `mvn -DskipTests clean package` 验证可构建。  
3. 提交到 `main` 并 `push origin main`。  
4. 执行生产部署脚本，验收本地源站 + 公网 `online` 页面。

## 回滚
若上线后异常，使用上一稳定提交重新部署（`git reset --hard <stable_sha>` + 部署脚本）。
