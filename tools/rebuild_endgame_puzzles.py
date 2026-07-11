#!/usr/bin/env python3
"""
Rebuild learn-content.seed.json puzzles with valid full-board Xiangqi FENs.

Sources (local first, then optional web):
- data/xqipu_fens.txt
- data/event_fens.txt
- src/main/resources/online/endgames.json
- EndgameStudySet.java BOARD_PARTS
- handcrafted classic tactics with FENs
"""

from __future__ import annotations

import json
import re
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED_PATH = ROOT / "src/main/resources/online/learn-content.seed.json"
ENDGAMES_PATH = ROOT / "src/main/resources/online/endgames.json"
XQIPU = ROOT / "data/xqipu_fens.txt"
EVENT = ROOT / "data/event_fens.txt"
WEB = ROOT / "data/web_scraped_fens.txt"
STUDY_JAVA = ROOT / "src/main/java/com/xiangqi/ai/EndgameStudySet.java"

random.seed(20260711)


def is_valid_xiangqi_fen(fen: str) -> bool:
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


def normalize_fen(fen: str) -> str:
    fen = fen.strip()
    parts = fen.split()
    board = parts[0]
    side = parts[1] if len(parts) > 1 and parts[1] in ("w", "b") else "w"
    return f"{board} {side}"


def piece_count(fen: str) -> int:
    board = fen.split()[0]
    return sum(1 for ch in board if ch.isalpha())


def difficulty_from_pieces(n: int) -> str:
    if n <= 8:
        return "EASY"
    if n <= 14:
        return "MEDIUM"
    if n <= 20:
        return "HARD"
    return "EXPERT"


def theme_from_pieces(n: int, source: str) -> str:
    if source == "classic":
        return "ENDGAME_FEN"
    if n <= 10:
        return "MATE"
    if n <= 16:
        return "TACTIC"
    if n >= 24:
        return "POSITION"
    return "ENDGAME_FEN"


def load_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    out = []
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        out.append(line)
    return out


def load_endgames_json() -> list[dict]:
    if not ENDGAMES_PATH.exists():
        return []
    return json.loads(ENDGAMES_PATH.read_text(encoding="utf-8"))


def load_study_set() -> list[str]:
    if not STUDY_JAVA.exists():
        return []
    text = STUDY_JAVA.read_text(encoding="utf-8", errors="ignore")
    return re.findall(r'BOARD_PARTS\.add\("([^"]+)"\)', text)


def handcrafted_puzzles() -> list[dict]:
    return [
        {
            "id": "xq-puzzle-fork-001",
            "gameType": "XIANGQI",
            "title": "双车牵制",
            "summary": "通过双车协调制造先手牵制并扩大子力优势。",
            "difficulty": "EASY",
            "theme": "TACTIC",
            "position": "红先，双车在中后场，黑将受限。",
            "goal": "3 步内形成持续先手，将黑方车将分离。",
            "hints": ["先手将军不一定马上得子，先逼位。", "观察黑将可走格数，先封最短逃线。"],
            "solution": ["红车平中将军，逼黑将入角。", "另一车切断退路，形成双车同线压制。"],
            "fen": "2bak4/4a4/4b4/9/9/9/4R4/4B4/4A4/3RK4 w",
            "source": "handcrafted",
        },
        {
            "id": "xq-puzzle-mate-net-002",
            "gameType": "XIANGQI",
            "title": "中路杀网",
            "summary": "利用中炮与车马配合形成连续将军。",
            "difficulty": "MEDIUM",
            "theme": "MATE",
            "position": "红先，黑将中宫偏后，红方车炮马可在中路发力。",
            "goal": "构建连续将军网，迫使黑方只能被动应手。",
            "hints": ["先固定黑将落点，再考虑重子跟进。", "马的跳点要与炮架形成一前一后。"],
            "solution": ["红先车将，黑将平移。", "红炮打中线形成二次将军。", "红马跳将封宫门。"],
            "fen": "4k4/4a4/4b4/4N4/4R4/4C4/9/9/9/4K4 w",
            "source": "handcrafted",
        },
        {
            "id": "gomoku-puzzle-center-001",
            "gameType": "GOMOKU",
            "title": "中心争夺",
            "summary": "通过中心落点构建双向威胁。",
            "difficulty": "EASY",
            "theme": "POSITION",
            "position": "黑先，中心附近已有两条三连雏形，白子分布偏边。",
            "goal": "2 手内形成至少一个冲四与一个活三的联动威胁。",
            "hints": ["优先找“同时连两边”的落点。", "别急着成冲四，先制造双重选择题。"],
            "solution": ["黑在中心斜线上补点，形成双活三雏形。", "白只能单线防守后，黑再补另一端成冲四。"],
            "source": "handcrafted",
        },
    ]


def make_puzzle(idx: int, fen: str, source: str, title: str | None = None, summary: str | None = None) -> dict:
    fen_n = normalize_fen(fen)
    n = piece_count(fen_n)
    diff = difficulty_from_pieces(n)
    theme = theme_from_pieces(n, source)
    side = "红先" if fen_n.endswith(" w") or " w" in fen_n else "黑先"
    return {
        "id": f"endgame-{source}-{idx:04d}",
        "gameType": "XIANGQI",
        "title": title or f"残局训练 {idx}",
        "summary": summary or f"来自 {source} 的有效残局局面（{n} 子），可直接进入 AI 练习复现。",
        "difficulty": diff,
        "theme": theme,
        "position": f"{side}，完整 10 路局面，共 {n} 子。",
        "goal": "在该局面下寻找先手进攻、杀棋或稳妥防守方案。",
        "hints": [
            "先判断将帅安全与对脸/将军威胁。",
            "优先检查将军、得子、兑子三类强制手。",
            "残局阶段注意兵卒推进与士象协同。",
        ],
        "solution": [
            "本题提供标准可复现 FEN，未绑定唯一官方着法。",
            "建议点击「按此题开局」用人机练习验证思路。",
        ],
        "fen": fen_n if fen_n.count(" ") >= 1 else f"{fen_n} w",
        "source": source,
        "pieceCount": n,
    }


def stratified_sample(fens: list[str], limit: int) -> list[str]:
    buckets = {"EASY": [], "MEDIUM": [], "HARD": [], "EXPERT": []}
    for fen in fens:
        fen_n = normalize_fen(fen)
        if not is_valid_xiangqi_fen(fen_n):
            continue
        buckets[difficulty_from_pieces(piece_count(fen_n))].append(fen_n)
    for k in buckets:
        random.shuffle(buckets[k])
    # 25% each tier when possible
    quotas = {
        "EASY": limit // 4,
        "MEDIUM": limit // 4,
        "HARD": limit // 4,
        "EXPERT": limit - 3 * (limit // 4),
    }
    picked: list[str] = []
    for tier, q in quotas.items():
        picked.extend(buckets[tier][:q])
    # fill remainder
    rest = []
    for tier, arr in buckets.items():
        rest.extend(arr[quotas[tier] :])
    random.shuffle(rest)
    for fen in rest:
        if len(picked) >= limit:
            break
        if fen not in picked:
            picked.append(fen)
    return picked[:limit]


def main() -> None:
    seed = json.loads(SEED_PATH.read_text(encoding="utf-8-sig"))
    tutorials = seed.get("tutorials") or []
    recommended = seed.get("recommendedPractice") or []

    seen: set[str] = set()
    puzzles: list[dict] = []

    # 1) handcrafted
    for p in handcrafted_puzzles():
        fen = p.get("fen")
        if fen:
            fen_n = normalize_fen(fen)
            if is_valid_xiangqi_fen(fen_n):
                p["fen"] = fen_n if " " in fen_n else f"{fen_n} w"
                seen.add(p["fen"].split()[0])
            else:
                p.pop("fen", None)
        puzzles.append(p)

    # 2) classic endgames.json
    for i, eg in enumerate(load_endgames_json(), start=1):
        fen = normalize_fen(eg.get("fen", ""))
        if not is_valid_xiangqi_fen(fen):
            continue
        key = fen.split()[0]
        if key in seen:
            continue
        seen.add(key)
        puzzles.append(
            make_puzzle(
                i,
                fen,
                "classic",
                title=eg.get("name") or f"经典残局 {i}",
                summary=eg.get("description") or "经典象棋残局。",
            )
            | {
                "id": f"classic-{eg.get('id') or i}",
                "source": eg.get("source") or "classic",
                "category": eg.get("category") or "经典残局",
            }
        )

    # 3) EndgameStudySet
    study = []
    for board in load_study_set():
        fen = normalize_fen(board + " w")
        if is_valid_xiangqi_fen(fen) and fen.split()[0] not in seen:
            study.append(fen)
    for i, fen in enumerate(study, start=1):
        key = fen.split()[0]
        seen.add(key)
        puzzles.append(make_puzzle(i, fen, "xqipu-study", title=f"残局古谱精选 {i}"))

    # 4) xqipu bulk stratified
    xqipu_all = [normalize_fen(x) for x in load_lines(XQIPU)]
    xqipu_unique = []
    for fen in xqipu_all:
        if not is_valid_xiangqi_fen(fen):
            continue
        key = fen.split()[0]
        if key in seen:
            continue
        xqipu_unique.append(fen)
    xqipu_pick = stratified_sample(xqipu_unique, 420)
    for i, fen in enumerate(xqipu_pick, start=1):
        seen.add(fen.split()[0])
        puzzles.append(make_puzzle(i, fen, "xqipu", title=f"亚艾元残局 {i}"))

    # 5) event bulk stratified (midgame-ish positions good for POSITION theme)
    event_all = [normalize_fen(x) for x in load_lines(EVENT)]
    event_unique = []
    for fen in event_all:
        if not is_valid_xiangqi_fen(fen):
            continue
        key = fen.split()[0]
        if key in seen:
            continue
        # prefer richer boards for event
        if piece_count(fen) < 16:
            continue
        event_unique.append(fen)
    event_pick = stratified_sample(event_unique, 120)
    for i, fen in enumerate(event_pick, start=1):
        seen.add(fen.split()[0])
        p = make_puzzle(i, fen, "event", title=f"赛事局面 {i}")
        p["theme"] = "POSITION"
        p["summary"] = "来自赛事棋谱的实战局面，适合中局转残局思路训练。"
        puzzles.append(p)

    # 6) web scrape extras
    web_all = [normalize_fen(x) for x in load_lines(WEB)]
    web_i = 0
    for fen in web_all:
        if not is_valid_xiangqi_fen(fen):
            continue
        key = fen.split()[0]
        if key in seen:
            continue
        seen.add(key)
        web_i += 1
        puzzles.append(make_puzzle(web_i, fen, "web", title=f"全网补全残局 {web_i}"))

    # stats
    with_fen = sum(1 for p in puzzles if p.get("fen") and is_valid_xiangqi_fen(p["fen"]))
    by_theme: dict[str, int] = {}
    by_diff: dict[str, int] = {}
    by_src: dict[str, int] = {}
    for p in puzzles:
        by_theme[p.get("theme", "?")] = by_theme.get(p.get("theme", "?"), 0) + 1
        by_diff[p.get("difficulty", "?")] = by_diff.get(p.get("difficulty", "?"), 0) + 1
        by_src[str(p.get("source", "?"))] = by_src.get(str(p.get("source", "?")), 0) + 1

    out = {
        "tutorials": tutorials,
        "puzzles": puzzles,
        "recommendedPractice": recommended,
        "meta": {
            "rebuiltAt": "2026-07-11",
            "totalPuzzles": len(puzzles),
            "validFenPuzzles": with_fen,
            "themes": by_theme,
            "difficulties": by_diff,
            "sources": by_src,
            "notes": [
                "Replaced broken xqbase partial FENs with validated full 10-row FENs.",
                "Primary sources: xqipu canju/event dumps, classic endgames.json, EndgameStudySet.",
            ],
        },
    }

    SEED_PATH.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "wrote": str(SEED_PATH),
        "totalPuzzles": len(puzzles),
        "validFen": with_fen,
        "themes": by_theme,
        "difficulties": by_diff,
        "sources": by_src,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
