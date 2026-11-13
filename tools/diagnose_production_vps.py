#!/usr/bin/env python3
"""Read-only production health report for 轻棋局."""

from __future__ import annotations

import argparse
import subprocess
import urllib.request


def ssh(alias: str, command: str) -> str:
    return subprocess.run(
        ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=12", alias, command],
        check=True,
        text=True,
        capture_output=True,
        timeout=30,
    ).stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Print a credential-safe VPS diagnosis")
    parser.add_argument("--ssh-alias", default="xiangqi-vps")
    parser.add_argument("--public-url", default="https://www.xiangqiarena.com/online")
    args = parser.parse_args()

    command = r"""
set -u
cd /opt/chinese-chess
echo '== git =='
git rev-parse --short HEAD
echo '== containers =='
docker compose ps
echo '== memory =='
free -m
echo '== disk =='
df -h /
echo '== engines =='
ps -eo pid,rss,etimes,comm --sort=-rss | grep -E '[p]ikafish|[r]apfi|[j]ava' || true
echo '== docker storage =='
docker system df
echo '== recent oom =='
journalctl -k --since '24 hours ago' --no-pager 2>/dev/null | grep -Ei 'oom|out of memory|killed process' | tail -20 || true
echo '== source =='
curl -sS -o /dev/null -w '/online %{http_code} %{size_download}\n' http://127.0.0.1:18388/online
curl -sS -o /dev/null -w '/bootstrap %{http_code} %{size_download}\n' http://127.0.0.1:18388/online/api/bootstrap
"""
    print(ssh(args.ssh_alias, command))
    request = urllib.request.Request(args.public_url, headers={"User-Agent": "Mozilla/5.0", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(request, timeout=20) as response:
        print(f"== public ==\n{response.status} {response.geturl()} {response.headers.get('content-length', 'unknown')} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
