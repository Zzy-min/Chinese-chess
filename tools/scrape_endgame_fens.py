#!/usr/bin/env python3
"""Bounded web scrape for Xiangqi endgame FENs (xqbase + xqipu)."""

from __future__ import annotations

import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data/web_scraped_fens.txt"


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 XiangqiArenaBot/1.0"})
    with urllib.request.urlopen(req, timeout=25) as resp:
        return resp.read().decode("utf-8", "ignore")


def valid(fen: str) -> bool:
    fen = (fen or "").strip()
    if not fen:
        return False
    board = fen.split()[0]
    rows = board.split("/")
    if len(rows) != 10:
        return False
    red_k = black_k = 0
    for row in rows:
        col = 0
        for ch in row:
            if ch.isdigit():
                col += int(ch)
            elif ch in "rnhcabkpRNHCABKP":
                col += 1
                if ch == "K":
                    red_k += 1
                elif ch == "k":
                    black_k += 1
            else:
                return False
        if col != 9:
            return False
    return red_k == 1 and black_k == 1


def normalize(fen: str) -> str:
    fen = urllib.parse.unquote_plus(fen).replace("+", " ").strip()
    parts = fen.split()
    board = parts[0]
    side = parts[1] if len(parts) > 1 and parts[1] in ("w", "b") else "w"
    return f"{board} {side}"


def main() -> None:
    found: set[str] = set()

    # xqbase homepage embeds sample endgame FENs
    try:
        html = fetch("https://www.xqbase.com/")
        for m in re.findall(r"endgame=([^\"&\s]+)", html):
            fen = normalize(m)
            if valid(fen):
                found.add(fen)
        print("xqbase home", len(found))
    except Exception as exc:
        print("xqbase fail", exc)

    # xqipu residual pages
    paths = [
        "/canjugupu",
        "/canjugupu?page=1",
        "/canjugupu/1606",
        "/canjugupu/1606?page=1",
        "/canjugupu/1606?page=2",
        "/qipus?page=0",
        "/qipus?page=1",
    ]
    for path in paths:
        try:
            html = fetch("https://www.xqipu.com" + path)
            for m in re.findall(r'data-fen="([^"]+)"', html):
                fen = normalize(m)
                if valid(fen):
                    found.add(fen)
            print(path, "total", len(found))
            time.sleep(0.25)
        except Exception as exc:
            print(path, "fail", exc)

    OUT.write_text("\n".join(sorted(found)) + "\n", encoding="utf-8")
    print("wrote", OUT, "count", len(found))


if __name__ == "__main__":
    main()
