#!/usr/bin/env python3
"""
Deploy the public site to the fixed production VPS origin.

Defaults:
- host: 47.80.60.26
- user: root
- project dir: /opt/chinese-chess
- branch: main

Password is never stored in the repository.
Provide it with XQ_DEPLOY_PASSWORD or enter it interactively.
"""

from __future__ import annotations

import argparse
import getpass
import os
import shlex
import sys
import time
import urllib.error
import urllib.request

try:
    import paramiko
except ImportError as exc:  # pragma: no cover - runtime-only dependency check
    raise SystemExit(
        "Missing dependency: paramiko\n"
        "Install it with: python -m pip install paramiko"
    ) from exc


DEFAULT_HOST = "47.80.60.26"
DEFAULT_USER = "root"
DEFAULT_PROJECT_DIR = "/opt/chinese-chess"
DEFAULT_BRANCH = "main"
DEFAULT_PUBLIC_URL = "https://www.xiangqiarena.com/"
DEFAULT_LOCAL_HEALTH_URL = "http://127.0.0.1:18388/"
DEFAULT_PUBLIC_CHECK_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Cache-Control": "no-cache",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deploy the production VPS origin for xiangqiarena.com."
    )
    parser.add_argument("--host", default=DEFAULT_HOST, help="Production VPS host")
    parser.add_argument("--user", default=DEFAULT_USER, help="SSH username")
    parser.add_argument(
        "--project-dir",
        default=DEFAULT_PROJECT_DIR,
        help="Project directory on the server",
    )
    parser.add_argument("--branch", default=DEFAULT_BRANCH, help="Git branch to deploy")
    parser.add_argument(
        "--public-url",
        default=DEFAULT_PUBLIC_URL,
        help="Public URL to verify after deploy",
    )
    parser.add_argument(
        "--local-health-url",
        default=DEFAULT_LOCAL_HEALTH_URL,
        help="Server-local health URL checked through SSH",
    )
    parser.add_argument(
        "--skip-public-check",
        action="store_true",
        help="Skip public URL verification",
    )
    parser.add_argument(
        "--password-env",
        default="XQ_DEPLOY_PASSWORD",
        help="Environment variable holding the SSH password",
    )
    return parser.parse_args()


def read_password(env_name: str, host: str, user: str) -> str:
    password = os.getenv(env_name)
    if password:
        return password
    prompt = f"SSH password for {user}@{host}: "
    password = getpass.getpass(prompt)
    if not password:
        raise SystemExit("Empty password; aborting deploy.")
    return password


def open_ssh(host: str, user: str, password: str) -> paramiko.SSHClient:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(hostname=host, username=user, password=password, timeout=20)
    return client


def run_remote(client: paramiko.SSHClient, command: str, timeout: int = 1800) -> str:
    stdin, stdout, stderr = client.exec_command(command, get_pty=True, timeout=timeout)
    output = []
    while True:
        if stdout.channel.recv_ready():
            chunk = stdout.channel.recv(4096).decode("utf-8", "replace")
            output.append(chunk)
            sys.stdout.write(chunk)
            sys.stdout.flush()
        if stdout.channel.exit_status_ready() and not stdout.channel.recv_ready():
            break
        time.sleep(0.1)

    err = stderr.read().decode("utf-8", "replace")
    if err:
        output.append(err)
        sys.stdout.write(err)
        sys.stdout.flush()

    status = stdout.channel.recv_exit_status()
    joined = "".join(output)
    if status != 0:
        raise RuntimeError(f"Remote command failed with exit code {status}\n{joined}")
    return joined


def deploy_command(project_dir: str, branch: str, local_health_url: str) -> str:
    quoted_dir = shlex.quote(project_dir)
    quoted_branch = shlex.quote(branch)
    quoted_local_health = shlex.quote(local_health_url)
    return f"""
set -e
cd {quoted_dir}
echo "BEFORE_HEAD=$(git rev-parse --short HEAD)"
git fetch origin {quoted_branch}
git reset --hard origin/{quoted_branch}
echo "AFTER_HEAD=$(git rev-parse --short HEAD)"
docker compose build app
docker compose up -d app
sleep 8
echo "APP_STATUS"
docker compose ps app
echo "LOCAL_CHECK"
curl -fsS {quoted_local_health} >/dev/null
curl -s {quoted_local_health} | head -c 400
echo
"""


def public_check(url: str, retries: int = 10, sleep_sec: int = 6) -> str:
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            request = urllib.request.Request(url, headers=DEFAULT_PUBLIC_CHECK_HEADERS)
            with urllib.request.urlopen(request, timeout=20) as response:
                html = response.read().decode("utf-8", "replace")
                status = getattr(response, "status", "unknown")
                final_url = response.geturl()
            print(f"PUBLIC_CHECK attempt={attempt} ok status={status} url={final_url}")
            return html
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
            print(f"PUBLIC_CHECK attempt={attempt} failed: {exc}")
            time.sleep(sleep_sec)
    raise RuntimeError(f"Public URL check failed after {retries} attempts: {last_error}")


def summarize_public_html(html: str) -> None:
    markers = [
        "现在开始下棋",
        "进入 AI 棋桌",
        "进入在线大厅",
        "首页承接 AI 对局与在线对局两条入口",
    ]
    for marker in markers:
        print(f"PUBLIC_MARKER {marker} -> {marker in html}")


def main() -> int:
    args = parse_args()
    password = read_password(args.password_env, args.host, args.user)

    print(f"Deploying {args.branch} to {args.user}@{args.host}:{args.project_dir}")
    client = open_ssh(args.host, args.user, password)
    try:
        run_remote(
            client,
            deploy_command(args.project_dir, args.branch, args.local_health_url),
        )
    finally:
        client.close()

    if not args.skip_public_check:
        html = public_check(args.public_url)
        summarize_public_html(html)

    print("Deploy completed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
