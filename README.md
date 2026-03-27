# 轻·棋局 XiangqiArena

Java web board-game platform for Xiangqi (Chinese Chess), Gomoku, and Go/Weiqi.

- Production domain: `https://www.xiangqiarena.com/`
- Chinese docs: [README.zh-CN.md](README.zh-CN.md)
- English docs: [README.en.md](README.en.md)
- Cloudflare deployment: [docs/deployment/cloudflare-tunnel.md](docs/deployment/cloudflare-tunnel.md)

This repository is now web-first. The public online site is served by `com.xiangqi.web.PublicWebMain`, with a legacy local browser mode retained for local-only gameplay.

## What It Includes

- Public online site for Xiangqi and Gomoku room play
- Unified web shell for Xiangqi, Gomoku, and Go
- AI play, review, endgame practice, and analysis
- External engine integration for Pikafish, Rapfi, AlphaGomoku, and KataGo-backed Go service
- Cloudflare Tunnel deployment path for a self-hosted Windows source machine

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

- `render.yaml` and `Dockerfile` are kept as historical deployment artifacts, but the current primary publishing path is Cloudflare DNS + Cloudflare Tunnel.
- Detailed local setup, engine configuration, and deployment steps live in the language-specific READMEs and the deployment guide.
