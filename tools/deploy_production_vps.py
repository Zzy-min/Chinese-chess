#!/usr/bin/env python3
"""Detached, verifiable production deployment for 轻棋局.

The script uses the configured ``xiangqi-vps`` OpenSSH alias.  The build runs
under ``nohup`` on the server, so closing this client cannot cancel BuildKit.
No password, private key, cookie, or database credential is printed or stored.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import shlex
import subprocess
import time
import urllib.request
from pathlib import Path


DEFAULT_SSH_ALIAS = "xiangqi-vps"
DEFAULT_PROJECT_DIR = "/opt/chinese-chess"
DEFAULT_BRANCH = "main"
DEFAULT_PUBLIC_URL = "https://www.xiangqiarena.com/online"
DEFAULT_SOURCE_URL = "http://127.0.0.1:18388/online"
PUBLIC_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/json,*/*;q=0.8",
    "Cache-Control": "no-cache",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Deploy 轻棋局 through a detached VPS job")
    parser.add_argument("--ssh-alias", default=DEFAULT_SSH_ALIAS)
    parser.add_argument("--project-dir", default=DEFAULT_PROJECT_DIR)
    parser.add_argument("--branch", default=DEFAULT_BRANCH)
    parser.add_argument("--commit", help="Exact commit to deploy; defaults to local HEAD")
    parser.add_argument("--public-url", default=DEFAULT_PUBLIC_URL)
    parser.add_argument("--source-url", default=DEFAULT_SOURCE_URL)
    parser.add_argument("--release-id", help="Safe identifier used for remote log/status files")
    parser.add_argument("--poll-timeout", type=int, default=1_800)
    parser.add_argument("--status-only", action="store_true")
    return parser.parse_args()


def local_git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], check=True, text=True, capture_output=True
    ).stdout.strip()


def run_ssh(alias: str, command: str, timeout: int = 30) -> str:
    completed = subprocess.run(
        ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=12", alias, command],
        check=True,
        text=True,
        capture_output=True,
        timeout=timeout,
    )
    return completed.stdout.strip()


def safe_release_id(raw: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9_.-]", "-", raw or "")
    if not value or len(value) > 80:
        raise ValueError("release id must be 1-80 safe characters")
    return value


def remote_paths(release_id: str) -> tuple[str, str, str]:
    prefix = f"/tmp/xiangqi-deploy-{safe_release_id(release_id)}"
    return f"{prefix}.sh", f"{prefix}.log", f"{prefix}.status"


def remote_deploy_script(
    project_dir: str,
    branch: str,
    commit: str,
    source_url: str,
    public_url: str,
    status_path: str,
) -> str:
    qdir = shlex.quote(project_dir)
    qbranch = shlex.quote(branch)
    qcommit = shlex.quote(commit)
    qsource = shlex.quote(source_url)
    qpublic = shlex.quote(public_url)
    qstatus = shlex.quote(status_path)
    return f"""#!/bin/bash
set -eu
STATUS={qstatus}
STAGE=starting
PREVIOUS_IMAGE=
SOURCE_URL={qsource}
PUBLIC_URL={qpublic}
write_status() {{
  tmp="$STATUS.tmp"
  printf 'state=%s\\nstage=%s\\nhead=%s\\nmessage=%s\\n' "$1" "$STAGE" "${{2:-}}" "${{3:-}}" > "$tmp"
  mv "$tmp" "$STATUS"
}}
rollback() {{
  code=$?
  set +e
  if [ -n "$PREVIOUS_IMAGE" ] && [ "$STAGE" != "fetch" ] && [ "$STAGE" != "build" ]; then
    docker image tag "$PREVIOUS_IMAGE" xiangqi-stack-app:latest
    docker compose up -d --no-deps --force-recreate app
  fi
  write_status failed "$(git rev-parse --short HEAD 2>/dev/null)" "stage-$STAGE-exit-$code"
  exit "$code"
}}
trap rollback INT TERM HUP ERR
exec 9>/tmp/xiangqi-deploy.lock
if ! flock -n 9; then
  write_status failed '' deployment-already-running
  exit 75
fi
cd {qdir}
write_status running '' starting
STAGE=fetch
git fetch origin {qbranch}
git cat-file -e {qcommit}^{{commit}}
git reset --hard {qcommit}
[ "$(git rev-parse HEAD)" = {qcommit} ]
PREVIOUS_IMAGE="$(docker inspect --format '{{{{.Image}}}}' xiangqi-stack-app-1 2>/dev/null || true)"
STAGE=build
write_status running "$(git rev-parse --short HEAD)" building
docker compose build app
STAGE=start
write_status running "$(git rev-parse --short HEAD)" starting-app
docker compose up -d --no-deps app
STAGE=health
attempt=0
while [ "$attempt" -lt 24 ]; do
  health="$(docker inspect --format '{{{{if .State.Health}}}}{{{{.State.Health.Status}}}}{{{{else}}}}{{{{.State.Status}}}}{{{{end}}}}' xiangqi-stack-app-1 2>/dev/null || true)"
  [ "$health" = healthy ] && break
  attempt=$((attempt + 1))
  sleep 5
done
[ "$health" = healthy ]
STAGE=source
source_html="$(curl -fsS "$SOURCE_URL")"
curl -fsS "$SOURCE_URL/api/site/bootstrap" | grep -q '^{{'
version="$(printf '%s' "$source_html" | sed -n 's/.*app\.js?v=\([A-Za-z0-9_.-]*\).*/\\1/p' | head -n 1)"
[ -n "$version" ]
STAGE=public
public_html="$(curl -fsS -H 'Cache-Control: no-cache' "$PUBLIC_URL?_deploy_check=$(date +%s)")"
printf '%s' "$public_html" | grep -Fq "app.js?v=$version"
source_sha="$(curl -fsS "$SOURCE_URL/assets/site/app.js?v=$version" | sha256sum | cut -d' ' -f1)"
public_sha="$(curl -fsS -H 'Cache-Control: no-cache' "$PUBLIC_URL/assets/site/app.js?v=$version&_deploy_check=$(date +%s)" | sha256sum | cut -d' ' -f1)"
[ "$source_sha" = "$public_sha" ]
STAGE=complete
write_status succeeded "$(git rev-parse --short HEAD)" verified
"""


def start_remote_job(alias: str, script: str, release_id: str) -> None:
    script_path, log_path, status_path = remote_paths(release_id)
    encoded = base64.b64encode(script.encode("utf-8")).decode("ascii")
    command = (
        f"printf %s {shlex.quote(encoded)} | base64 -d > {shlex.quote(script_path)} && "
        f"chmod 700 {shlex.quote(script_path)} && "
        f"rm -f {shlex.quote(status_path)} && "
        f"(nohup {shlex.quote(script_path)} > {shlex.quote(log_path)} 2>&1 </dev/null & echo $!)"
    )
    pid = run_ssh(alias, command)
    print(f"REMOTE_JOB release={release_id} pid={pid} log={log_path}")


def read_status(alias: str, release_id: str) -> dict[str, str]:
    _, _, status_path = remote_paths(release_id)
    raw = run_ssh(alias, f"test -f {shlex.quote(status_path)} && cat {shlex.quote(status_path)} || true")
    result: dict[str, str] = {}
    for line in raw.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


def wait_for_remote_job(alias: str, release_id: str, timeout_seconds: int) -> dict[str, str]:
    deadline = time.monotonic() + timeout_seconds
    last_stage = ""
    while time.monotonic() < deadline:
        status = read_status(alias, release_id)
        stage = status.get("stage", "waiting")
        if stage != last_stage:
            print(f"REMOTE_STATUS state={status.get('state', 'pending')} stage={stage}")
            last_stage = stage
        if status.get("state") == "succeeded":
            return status
        if status.get("state") == "failed":
            _, log_path, _ = remote_paths(release_id)
            tail = run_ssh(alias, f"tail -n 80 {shlex.quote(log_path)} || true")
            raise RuntimeError(f"remote deployment failed: {status}\n{tail}")
        time.sleep(5)
    raise TimeoutError(f"remote deployment still running after {timeout_seconds}s; release={release_id}")


def fetch_bytes(url: str) -> bytes:
    separator = "&" if "?" in url else "?"
    request = urllib.request.Request(f"{url}{separator}_deploy_check={time.time_ns()}", headers=PUBLIC_HEADERS)
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError(f"unexpected HTTP {response.status}: {url}")
        return response.read()


def verify_release(alias: str, project_dir: str, expected_commit: str, public_url: str, source_url: str) -> None:
    remote_head = run_ssh(alias, f"cd {shlex.quote(project_dir)} && git rev-parse HEAD")
    if remote_head != expected_commit:
        raise RuntimeError(f"remote HEAD mismatch: expected {expected_commit}, got {remote_head}")
    source_html = run_ssh(alias, f"curl -fsS {shlex.quote(source_url)}")
    public_html = fetch_bytes(public_url).decode("utf-8", "replace")
    match = re.search(r"app\.js\?v=([a-zA-Z0-9_.-]+)", source_html)
    if not match:
        raise RuntimeError("source HTML has no versioned app.js")
    version = match.group(1)
    if f"app.js?v={version}" not in public_html:
        raise RuntimeError(f"public HTML does not expose source asset version {version}")
    source_asset_url = f"{source_url}/assets/site/app.js?v={version}"
    public_asset_url = f"{public_url}/assets/site/app.js?v={version}"
    source_sha = run_ssh(
        alias, f"curl -fsS {shlex.quote(source_asset_url)} | sha256sum | cut -d' ' -f1"
    )
    public_sha = hashlib.sha256(fetch_bytes(public_asset_url)).hexdigest()
    if source_sha != public_sha:
        raise RuntimeError(f"asset hash mismatch: source={source_sha} public={public_sha}")
    bootstrap = run_ssh(alias, f"curl -fsS {shlex.quote(source_url + '/api/site/bootstrap')}")
    if not isinstance(json.loads(bootstrap), dict):
        raise RuntimeError("bootstrap response is not a JSON object")
    print(f"VERIFY_OK head={remote_head[:7]} version={version} app_sha256={public_sha}")


def main() -> int:
    args = parse_args()
    if args.status_only and not args.release_id:
        raise SystemExit("--status-only requires --release-id")
    commit = args.commit or local_git("rev-parse", "HEAD")
    release_id = safe_release_id(args.release_id or f"{commit[:7]}-{int(time.time())}")
    if args.status_only:
        print(json.dumps(read_status(args.ssh_alias, release_id), ensure_ascii=False, indent=2))
        return 0
    local_git("merge-base", "--is-ancestor", commit, f"origin/{args.branch}")
    _, _, status_path = remote_paths(release_id)
    script = remote_deploy_script(
        args.project_dir,
        args.branch,
        commit,
        args.source_url,
        args.public_url,
        status_path,
    )
    start_remote_job(args.ssh_alias, script, release_id)
    wait_for_remote_job(args.ssh_alias, release_id, args.poll_timeout)
    verify_release(args.ssh_alias, args.project_dir, commit, args.public_url, args.source_url)
    print(f"DEPLOY_OK release={release_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
