# Production VPS Deploy Script

This repository now includes a reusable deploy script for the fixed production VPS origin:

- Script: `tools/deploy_production_vps.py`
- Default target host: `47.80.60.26`
- Default SSH user: `root`
- Default project directory: `/opt/chinese-chess`
- Default branch: `main`

The script performs the production flow that was used manually:

1. SSH into the production host
2. `git fetch` and `git pull --ff-only origin main`
3. `docker compose build app`
4. `docker compose up -d app`
5. Check the server-local app on `http://127.0.0.1:18388/`
6. Check the public site on `https://www.xiangqiarena.com/`

## Security

- The server password is not stored in the repository.
- Provide it with an environment variable or enter it interactively.

Recommended environment variable:

```powershell
$env:XQ_DEPLOY_PASSWORD = "your-server-password"
```

## Usage

From the repository root:

```powershell
python tools/deploy_production_vps.py
```

If you want to skip the public URL verification:

```powershell
python tools/deploy_production_vps.py --skip-public-check
```

If the production path changes later, override the defaults:

```powershell
python tools/deploy_production_vps.py --host 47.80.60.26 --user root --project-dir /opt/chinese-chess --branch main
```

## Dependency

The script requires `paramiko`.

Install it if needed:

```powershell
python -m pip install paramiko
```
