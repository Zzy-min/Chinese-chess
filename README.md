# 轻·棋局 XiangqiArena

Java web board-game platform. Online main path ships Xiangqi and Gomoku (rooms, quick-match, AI practice, review); Go is reserved in the shell and available via legacy local mode / go-engine, not as Online multiplayer yet.

- Production domain: `https://www.xiangqiarena.com/`
- Chinese docs: [README.zh-CN.md](README.zh-CN.md)
- English docs: [README.en.md](README.en.md)
- Cloudflare Worker front door deployment: [docs/deployment/cloudflare-java-proxy.md](docs/deployment/cloudflare-java-proxy.md)
- Cloudflare Tunnel deployment: [docs/deployment/cloudflare-tunnel.md](docs/deployment/cloudflare-tunnel.md)

Worker deployment compatibility note:

- The real Worker project lives in `deploy/cloudflare-java-proxy/`
- A repo-root `wrangler.jsonc` is intentionally kept as a compatibility shim so an accidental repository-root `npx wrangler deploy` still deploys the same proxy instead of failing against the wrong project shape

This repository is now web-first. The public online site is served by `com.xiangqi.web.PublicWebMain`, with a legacy local browser mode retained for local-only gameplay.

## What It Includes

- Public online site for Xiangqi and Gomoku room play
- Unified web shell for Xiangqi, Gomoku, and Go
- AI play, review, endgame practice, and analysis
- External engine integration for Pikafish, Rapfi, AlphaGomoku, and KataGo-backed Go service
- Cloudflare Worker front door + Java origin deployment path
- Cloudflare Tunnel remains available as an origin publishing option

## Runtime Entry Points

- Public site entry: `com.xiangqi.web.PublicWebMain`
- Legacy local mode: `com.xiangqi.web.BrowserModeMain`
- Local startup script: `run_web.bat`
- Go engine helper: `run_go_engine.bat`
- Default local URL: `http://127.0.0.1:18388/`

## Common Commands

```powershell
mvn -q -DskipTests package
mvn -q test
run_web.bat
```

## Notes

- `render.yaml` and `Dockerfile` are kept as historical deployment artifacts, but the current primary publishing path is Cloudflare Worker front door + fixed Java origin.
- Detailed local setup, engine configuration, and deployment steps live in the language-specific READMEs and the deployment guide.
