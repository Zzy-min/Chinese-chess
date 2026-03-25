# 四棋综合棋站清新版实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立新的 Node/TypeScript 四棋网站基础，并先交付共享四棋模型、服务骨架、清新版首页和中象/国象差异化棋盘渲染。

**Architecture:** 现有 Java 代码保留作为行为参考，新实现采用 `apps/web + apps/server + packages/*` 的 monorepo 结构。首批实现优先覆盖共享模型、主页 UI、棋盘 renderer 和服务骨架，为后续在线、练习、学习、回顾模块提供稳定基础。

**Tech Stack:** Next.js, React, TypeScript, Fastify, pnpm workspaces, Vitest, React Testing Library, chess.js

---

## Chunk 1: 文档与工作区初始化

### Task 1: 落盘设计与计划

**Files:**
- Create: `docs/superpowers/specs/2026-03-24-four-game-clean-web-platform-design.md`
- Create: `docs/superpowers/plans/2026-03-24-four-game-clean-web-platform-plan.md`

- [ ] 将本次确认的设计与实施计划保存到仓库。
- [ ] 保持命名、日期和范围与当前实现一致。

### Task 2: 建立 monorepo 根配置

**Files:**
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Create: `tsconfig.base.json`
- Modify: `.gitignore`

- [ ] 先写 workspace 配置相关测试或校验命令。
- [ ] 建立 root scripts：`build`、`test`、`lint`。
- [ ] 补充 `node_modules`、`.next`、`coverage` 等忽略项。

## Chunk 2: 共享模型与服务骨架

### Task 3: 四棋共享目录和类型

**Files:**
- Create: `packages/core/package.json`
- Create: `packages/core/tsconfig.json`
- Create: `packages/core/src/index.ts`
- Create: `packages/core/src/catalog.ts`
- Create: `packages/core/src/themes.ts`
- Create: `packages/core/src/types.ts`
- Create: `packages/core/src/catalog.test.ts`

- [ ] 先写失败测试，验证四棋目录完整、棋盘主题配置有效、象棋与国际象棋主题明确区分。
- [ ] 实现共享类型、棋种目录、基础主题。
- [ ] 跑测试确认通过。

### Task 4: 国际象棋规则适配器

**Files:**
- Create: `packages/engines-chess/package.json`
- Create: `packages/engines-chess/tsconfig.json`
- Create: `packages/engines-chess/src/index.ts`
- Create: `packages/engines-chess/src/chess-adapter.ts`
- Create: `packages/engines-chess/src/chess-adapter.test.ts`

- [ ] 先写失败测试，验证初始局面、合法走子、非法走子拦截。
- [ ] 用 `chess.js` 封装首个 `RulesAdapter`。
- [ ] 跑测试确认通过。

### Task 5: Fastify 服务骨架

**Files:**
- Create: `apps/server/package.json`
- Create: `apps/server/tsconfig.json`
- Create: `apps/server/src/index.ts`
- Create: `apps/server/src/app.ts`
- Create: `apps/server/src/routes/health.ts`
- Create: `apps/server/src/routes/catalog.ts`
- Create: `apps/server/src/app.test.ts`

- [ ] 先写失败测试，验证健康检查和四棋目录接口。
- [ ] 实现最小 Fastify 服务，直接消费 `packages/core`。
- [ ] 跑测试确认通过。

## Chunk 3: 清新版 Web 首页与棋盘渲染

### Task 6: Next.js 主站骨架

**Files:**
- Create: `apps/web/package.json`
- Create: `apps/web/tsconfig.json`
- Create: `apps/web/next.config.ts`
- Create: `apps/web/vitest.config.ts`
- Create: `apps/web/src/app/layout.tsx`
- Create: `apps/web/src/app/page.tsx`
- Create: `apps/web/src/app/globals.css`

- [ ] 建立最小 Next.js App Router 应用。
- [ ] 引入共享目录数据。
- [ ] 保持首页即使在无后端情况下也能独立渲染。

### Task 7: 四棋首页与差异化棋盘预览

**Files:**
- Create: `apps/web/src/components/home/game-catalog.tsx`
- Create: `apps/web/src/components/home/hero.tsx`
- Create: `apps/web/src/components/boards/xiangqi-board-preview.tsx`
- Create: `apps/web/src/components/boards/chess-board-preview.tsx`
- Create: `apps/web/src/components/boards/gomoku-board-preview.tsx`
- Create: `apps/web/src/components/boards/go-board-preview.tsx`
- Create: `apps/web/src/components/home/home-page.test.tsx`

- [ ] 先写失败测试，验证首页展示四棋、象棋/国际象棋棋盘有不同语义元素。
- [ ] 实现清新版首页。
- [ ] 确保中象和国象 preview 在视觉上明显不同。
- [ ] 跑测试确认通过。

### Task 8: 响应式与交互流畅性基线

**Files:**
- Modify: `apps/web/src/app/globals.css`
- Modify: `apps/web/src/app/page.tsx`
- Create: `apps/web/src/components/home/home-page.responsive.test.tsx`

- [ ] 增加桌面/移动断点样式。
- [ ] 控制首屏层级与卡片密度，保持轻量。
- [ ] 为高频 hover / focus / active 状态配置短时长动效。

## Chunk 4: 验证

### Task 9: 统一验证

**Files:**
- Modify: `README.md`

- [ ] 运行 `pnpm --dir . test` 或等效 workspace 测试命令。
- [ ] 运行 `pnpm --dir . build` 或等效 workspace 构建命令。
- [ ] 更新 README，说明新 workspace 的启动和验证方式。
