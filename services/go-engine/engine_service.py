from __future__ import annotations

import json
import os
import queue
import shlex
import subprocess
import tempfile
import threading
import time
from dataclasses import dataclass, field
from typing import Iterable, Optional


LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"


class EngineConfigError(RuntimeError):
    pass


@dataclass
class MoveRecord:
    color: str
    row: Optional[int]
    col: Optional[int]
    pass_move: bool

    @classmethod
    def from_payload(cls, payload: dict) -> "MoveRecord":
        color = normalize_color(payload.get("color"))
        pass_move = bool(payload.get("pass"))
        row = payload.get("row")
        col = payload.get("col")
        if not pass_move and (row is None or col is None):
            raise ValueError("non-pass move requires row/col")
        return cls(color=color, row=row, col=col, pass_move=pass_move)


@dataclass
class EngineRequest:
    size: int
    komi: float
    moves: list[MoveRecord] = field(default_factory=list)
    rows: list[str] = field(default_factory=list)
    current_turn: str = "BLACK"
    to_play: str = "BLACK"
    ai_stone: str = "BLACK"
    difficulty: str = "MEDIUM"

    @classmethod
    def from_payload(cls, payload: dict) -> "EngineRequest":
        size = int(payload.get("size") or 19)
        komi = float(payload.get("komi") or 7.5)
        rows = [str(row) for row in (payload.get("rows") or [])]
        moves = [MoveRecord.from_payload(item) for item in (payload.get("moves") or [])]
        current_turn = normalize_color(
            payload.get("currentTurn") or payload.get("toPlay") or infer_turn_from_moves(moves),
        )
        to_play = normalize_color(payload.get("toPlay") or current_turn)
        ai_stone = normalize_color(payload.get("aiStone") or to_play)
        difficulty = str(payload.get("difficulty") or "MEDIUM").upper()
        return cls(
            size=size,
            komi=komi,
            moves=moves,
            rows=rows,
            current_turn=current_turn,
            to_play=to_play,
            ai_stone=ai_stone,
            difficulty=difficulty,
        )


@dataclass
class ParsedFinalScore:
    winner: str
    final_score: float
    raw: str

    @property
    def result_text(self) -> str:
        if self.final_score == 0.0:
            return "双方持平"
        side = "黑胜" if self.final_score > 0 else "白胜"
        return f"{side}{abs(self.final_score):.1f}"


def normalize_color(raw: Optional[str]) -> str:
    text = str(raw or "").strip().upper()
    if text in {"B", "BLACK"}:
        return "BLACK"
    if text in {"W", "WHITE"}:
        return "WHITE"
    raise ValueError(f"unsupported color: {raw!r}")


def other_color(color: str) -> str:
    return "WHITE" if normalize_color(color) == "BLACK" else "BLACK"


def infer_turn_from_moves(moves: Iterable[MoveRecord]) -> str:
    moves = list(moves)
    if not moves:
        return "BLACK"
    return other_color(moves[-1].color)


def parse_final_score(raw: str) -> ParsedFinalScore:
    text = str(raw or "").strip().upper()
    if not text or text in {"0", "DRAW", "JIGO"}:
        return ParsedFinalScore(winner="", final_score=0.0, raw=str(raw or ""))
    if "+" not in text:
        raise ValueError(f"unsupported final_score payload: {raw!r}")
    side, margin_text = text.split("+", 1)
    winner = normalize_color(side)
    margin_text = margin_text.strip()
    if margin_text in {"R", "RESIGN"}:
        margin = 999.0
    else:
        margin = float(margin_text)
    score = margin if winner == "BLACK" else -margin
    return ParsedFinalScore(winner=winner, final_score=score, raw=str(raw or ""))


class GtpCoordinateCodec:
    @staticmethod
    def to_gtp(size: int, row: int, col: int) -> str:
        if row < 0 or row >= size or col < 0 or col >= size:
            raise ValueError("coordinate out of bounds")
        return f"{LETTERS[col]}{size - row}"

    @staticmethod
    def from_gtp(size: int, coord: str) -> Optional[tuple[int, int]]:
        text = str(coord or "").strip().upper()
        if text in {"PASS", "RESIGN"}:
            return None
        if len(text) < 2:
            raise ValueError(f"invalid GTP coordinate: {coord!r}")
        column = text[0]
        if column not in LETTERS:
            raise ValueError(f"invalid GTP column: {coord!r}")
        row_num = int(text[1:])
        row = size - row_num
        col = LETTERS.index(column)
        if row < 0 or row >= size:
            raise ValueError(f"invalid GTP row: {coord!r}")
        return row, col


class LocalGoBoard:
    def __init__(self, size: int, komi: float):
        self.size = size
        self.komi = komi
        self.grid = [["." for _ in range(size)] for _ in range(size)]

    @classmethod
    def from_request(cls, request: EngineRequest) -> "LocalGoBoard":
        board = cls(request.size, request.komi)
        if request.rows:
            board.load_rows(request.rows)
            return board
        for move in request.moves:
            board.play(move)
        return board

    def load_rows(self, rows: Iterable[str]) -> None:
        for row_index, row_text in enumerate(list(rows)[: self.size]):
            line = str(row_text or "")
            for col_index, char in enumerate(line[: self.size]):
                if char.upper() == "B":
                    self.grid[row_index][col_index] = "B"
                elif char.upper() == "W":
                    self.grid[row_index][col_index] = "W"
                else:
                    self.grid[row_index][col_index] = "."

    def play(self, move: MoveRecord) -> None:
        if move.pass_move:
            return
        row = int(move.row)
        col = int(move.col)
        stone = normalize_color(move.color)[0]
        if not self._inside(row, col):
            raise ValueError("move out of bounds")
        if self.grid[row][col] != ".":
            raise ValueError("point already occupied")
        snapshot = [line[:] for line in self.grid]
        self.grid[row][col] = stone
        opponent = "W" if stone == "B" else "B"
        for nr, nc in self._neighbors(row, col):
            if self.grid[nr][nc] != opponent:
                continue
            group = self._collect_group(nr, nc)
            if self._count_liberties(group) == 0:
                self._remove_group(group)
        own_group = self._collect_group(row, col)
        if self._count_liberties(own_group) == 0:
            self.grid = snapshot
            raise ValueError("suicide move while rebuilding board")

    def area_score(self) -> tuple[int, int]:
        black = sum(cell == "B" for row in self.grid for cell in row)
        white = sum(cell == "W" for row in self.grid for cell in row)
        visited: set[tuple[int, int]] = set()
        for row in range(self.size):
            for col in range(self.size):
                if self.grid[row][col] != "." or (row, col) in visited:
                    continue
                points, borders = self._collect_territory(row, col, visited)
                if borders == {"B"}:
                    black += len(points)
                elif borders == {"W"}:
                    white += len(points)
        return black, white

    def _collect_territory(
        self,
        row: int,
        col: int,
        visited: set[tuple[int, int]],
    ) -> tuple[set[tuple[int, int]], set[str]]:
        stack = [(row, col)]
        points: set[tuple[int, int]] = set()
        borders: set[str] = set()
        while stack:
            cur_row, cur_col = stack.pop()
            if (cur_row, cur_col) in visited:
                continue
            visited.add((cur_row, cur_col))
            if self.grid[cur_row][cur_col] != ".":
                borders.add(self.grid[cur_row][cur_col])
                continue
            points.add((cur_row, cur_col))
            for nr, nc in self._neighbors(cur_row, cur_col):
                if self.grid[nr][nc] == ".":
                    if (nr, nc) not in visited:
                        stack.append((nr, nc))
                else:
                    borders.add(self.grid[nr][nc])
        return points, borders

    def _collect_group(self, row: int, col: int) -> set[tuple[int, int]]:
        stone = self.grid[row][col]
        group: set[tuple[int, int]] = set()
        stack = [(row, col)]
        while stack:
            cur_row, cur_col = stack.pop()
            if (cur_row, cur_col) in group:
                continue
            if self.grid[cur_row][cur_col] != stone:
                continue
            group.add((cur_row, cur_col))
            for nr, nc in self._neighbors(cur_row, cur_col):
                if self.grid[nr][nc] == stone:
                    stack.append((nr, nc))
        return group

    def _count_liberties(self, group: set[tuple[int, int]]) -> int:
        liberties: set[tuple[int, int]] = set()
        for row, col in group:
            for nr, nc in self._neighbors(row, col):
                if self.grid[nr][nc] == ".":
                    liberties.add((nr, nc))
        return len(liberties)

    def _remove_group(self, group: set[tuple[int, int]]) -> None:
        for row, col in group:
            self.grid[row][col] = "."

    def _neighbors(self, row: int, col: int) -> list[tuple[int, int]]:
        result: list[tuple[int, int]] = []
        if row > 0:
            result.append((row - 1, col))
        if row + 1 < self.size:
            result.append((row + 1, col))
        if col > 0:
            result.append((row, col - 1))
        if col + 1 < self.size:
            result.append((row, col + 1))
        return result

    def _inside(self, row: int, col: int) -> bool:
        return 0 <= row < self.size and 0 <= col < self.size


@dataclass
class ServiceConfig:
    bind_host: str
    port: int
    engine_name: str
    rules: str
    startup_timeout_sec: float
    command_timeout_sec: float
    difficulty_visits: dict[str, int]
    command: list[str]

    @classmethod
    def from_env(cls) -> "ServiceConfig":
        bind_host = os.getenv("BIND_HOST", "0.0.0.0").strip() or "0.0.0.0"
        port = int(os.getenv("PORT", "2718"))
        engine_name = os.getenv("GO_ENGINE_NAME", "KataGo").strip() or "KataGo"
        rules = os.getenv("GO_ENGINE_RULES", "chinese").strip() or "chinese"
        startup_timeout_sec = float(os.getenv("GO_ENGINE_STARTUP_TIMEOUT_SEC", "60"))
        command_timeout_sec = float(os.getenv("GO_ENGINE_COMMAND_TIMEOUT_SEC", "20"))
        difficulty_visits = {
            "EASY": int(os.getenv("GO_ENGINE_VISITS_EASY", "0")),
            "MEDIUM": int(os.getenv("GO_ENGINE_VISITS_MEDIUM", "0")),
            "HARD": int(os.getenv("GO_ENGINE_VISITS_HARD", "0")),
        }
        return cls(
            bind_host=bind_host,
            port=port,
            engine_name=engine_name,
            rules=rules,
            startup_timeout_sec=startup_timeout_sec,
            command_timeout_sec=command_timeout_sec,
            difficulty_visits=difficulty_visits,
            command=build_engine_command(),
        )


def build_engine_command() -> list[str]:
    raw_cmd = os.getenv("KATAGO_CMD", "").strip()
    if raw_cmd:
        return shlex.split(raw_cmd, posix=os.name != "nt")
    binary = os.getenv("KATAGO_BIN", "").strip()
    if not binary:
        return []
    command = [binary, "gtp"]
    config_path = os.getenv("KATAGO_CONFIG", "").strip()
    model_path = os.getenv("KATAGO_MODEL", "").strip()
    extra_args = os.getenv("KATAGO_ARGS", "").strip()
    if config_path:
        command.extend(["-config", config_path])
    if model_path:
        command.extend(["-model", model_path])
    if extra_args:
        command.extend(shlex.split(extra_args, posix=os.name != "nt"))
    return command


class KataGoSession:
    def __init__(self, config: ServiceConfig):
        self.config = config
        self._lock = threading.Lock()
        self._proc: Optional[subprocess.Popen[str]] = None
        self._stdout_queue: "queue.Queue[Optional[str]]" = queue.Queue()
        self._stderr_tail: list[str] = []
        self._engine_name = config.engine_name

    @property
    def engine_name(self) -> str:
        return self._engine_name

    def stderr_tail(self) -> list[str]:
        return list(self._stderr_tail[-20:])

    def quick_health(self) -> dict:
        configured = bool(self.config.command)
        running = self._proc is not None and self._proc.poll() is None
        payload = {
            "ok": configured,
            "engine": self.engine_name,
            "configured": configured,
            "ready": running,
        }
        if not configured:
            payload["error"] = "missing KATAGO_CMD or KATAGO_BIN"
        return payload

    def stop(self) -> None:
        with self._lock:
            proc = self._proc
            self._proc = None
            if proc is None:
                return
            try:
                proc.terminate()
                proc.wait(timeout=5)
            except Exception:
                proc.kill()

    def ensure_started(self) -> None:
        with self._lock:
            self._start_locked()

    def health(self) -> dict:
        try:
            with self._lock:
                name = self._query_name_locked()
                version = self._run_command_locked("version", timeout_sec=5)
            return {
                "ok": True,
                "engine": name or self.engine_name,
                "version": version.strip(),
            }
        except Exception as exc:
            return {
                "ok": False,
                "engine": self.engine_name,
                "error": str(exc),
                "stderrTail": self.stderr_tail(),
            }

    def genmove(self, request: EngineRequest) -> dict:
        with self._lock:
            self._prepare_board_locked(request)
            visits = self.config.difficulty_visits.get(request.difficulty, 0)
            if visits > 0:
                self._try_run_optional_locked(f"kata-set-param maxVisits {visits}")
            response = self._run_command_locked(
                f"genmove {request.to_play.lower()}",
                timeout_sec=self.config.command_timeout_sec,
            ).strip()
        if response.lower() in {"pass", "resign"}:
            return {"pass": True, "engine": self.engine_name}
        row_col = GtpCoordinateCodec.from_gtp(request.size, response)
        if row_col is None:
            return {"pass": True, "engine": self.engine_name}
        row, col = row_col
        return {"pass": False, "row": row, "col": col, "engine": self.engine_name}

    def score(self, request: EngineRequest) -> dict:
        local_board = LocalGoBoard.from_request(request)
        black_area, white_area = local_board.area_score()
        with self._lock:
            self._prepare_board_locked(request)
            response = self._run_command_locked(
                "final_score",
                timeout_sec=self.config.command_timeout_sec,
            ).strip()
        try:
            parsed = parse_final_score(response)
        except ValueError:
            local_final = round(black_area - (white_area + request.komi), 1)
            parsed = ParsedFinalScore(
                winner="BLACK" if local_final > 0 else "WHITE" if local_final < 0 else "",
                final_score=local_final,
                raw=response,
            )
        if parsed.winner == "" and parsed.final_score == 0.0 and response.strip() not in {"0", "draw", "Draw", "jigo", "JIGO"}:
            local_final = round(black_area - (white_area + request.komi), 1)
            parsed = ParsedFinalScore(
                winner="BLACK" if local_final > 0 else "WHITE" if local_final < 0 else "",
                final_score=local_final,
                raw=response,
            )
        return {
            "blackArea": black_area,
            "whiteArea": white_area,
            "komi": request.komi,
            "finalScore": parsed.final_score,
            "winner": parsed.winner,
            "resultText": parsed.result_text,
            "engine": self.engine_name,
            "rawResult": parsed.raw,
        }

    def _prepare_board_locked(self, request: EngineRequest) -> None:
        self._query_name_locked()
        if request.rows:
            self._load_snapshot_locked(request)
            return
        self._run_command_locked(f"boardsize {request.size}", timeout_sec=5)
        self._run_command_locked("clear_board", timeout_sec=5)
        self._try_run_optional_locked(f"kata-set-rules {self.config.rules}")
        self._run_command_locked(f"komi {request.komi}", timeout_sec=5)
        for move in request.moves:
            self._play_move_locked(request.size, move)

    def _load_snapshot_locked(self, request: EngineRequest) -> None:
        sgf = build_snapshot_sgf(request)
        temp_path = None
        try:
            with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sgf", delete=False) as temp_file:
                temp_file.write(sgf)
                temp_path = temp_file.name
            gtp_path = temp_path.replace("\\", "/")
            self._run_command_locked(f"loadsgf {gtp_path}", timeout_sec=5)
        finally:
            if temp_path:
                try:
                    os.remove(temp_path)
                except OSError:
                    pass

    def _play_move_locked(self, size: int, move: MoveRecord) -> None:
        color = move.color.lower()
        if move.pass_move:
            self._run_command_locked(f"play {color} pass", timeout_sec=5)
            return
        coord = GtpCoordinateCodec.to_gtp(size, int(move.row), int(move.col))
        self._run_command_locked(f"play {color} {coord}", timeout_sec=5)

    def _query_name_locked(self) -> str:
        if self._proc is None or self._proc.poll() is not None:
            self._start_locked()
        name = self._run_command_locked("name", timeout_sec=self.config.startup_timeout_sec)
        if name.strip():
            self._engine_name = name.strip()
        return self._engine_name

    def _try_run_optional_locked(self, command: str) -> None:
        try:
            self._run_command_locked(command, timeout_sec=5)
        except Exception:
            pass

    def _start_locked(self) -> None:
        if self._proc is not None and self._proc.poll() is None:
            return
        if not self.config.command:
            raise EngineConfigError("missing KATAGO_CMD or KATAGO_BIN")
        self._stdout_queue = queue.Queue()
        self._stderr_tail = []
        try:
            self._proc = subprocess.Popen(
                self.config.command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                bufsize=1,
            )
        except FileNotFoundError as exc:
            raise EngineConfigError(f"failed to start engine: {exc}") from exc
        assert self._proc.stdout is not None
        assert self._proc.stderr is not None
        threading.Thread(
            target=self._pump_stdout,
            args=(self._proc.stdout, self._stdout_queue),
            daemon=True,
        ).start()
        threading.Thread(
            target=self._pump_stderr,
            args=(self._proc.stderr,),
            daemon=True,
        ).start()

    def _pump_stdout(self, stream, out_queue: "queue.Queue[Optional[str]]") -> None:
        try:
            for line in stream:
                out_queue.put(line)
        finally:
            out_queue.put(None)

    def _pump_stderr(self, stream) -> None:
        for line in stream:
            text = line.rstrip("\r\n")
            if text:
                self._stderr_tail.append(text)
                if len(self._stderr_tail) > 100:
                    self._stderr_tail = self._stderr_tail[-100:]

    def _run_command_locked(self, command: str, timeout_sec: float) -> str:
        if self._proc is None or self._proc.poll() is not None:
            self._start_locked()
        assert self._proc is not None
        assert self._proc.stdin is not None
        try:
            self._proc.stdin.write(command + "\n")
            self._proc.stdin.flush()
        except Exception as exc:
            self.stop()
            raise RuntimeError(f"failed to write GTP command {command!r}: {exc}") from exc
        deadline = time.time() + timeout_sec
        response_lines: list[str] = []
        is_error = False
        started = False
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                self.stop()
                raise TimeoutError(f"GTP command timed out: {command}")
            line = self._stdout_queue.get(timeout=remaining)
            if line is None:
                self.stop()
                raise RuntimeError("engine process exited")
            text = line.rstrip("\r\n")
            if not started:
                if not text:
                    continue
                if text.startswith("="):
                    started = True
                    payload = text[1:].strip()
                    if payload:
                        response_lines.append(payload)
                    continue
                if text.startswith("?"):
                    started = True
                    is_error = True
                    payload = text[1:].strip()
                    if payload:
                        response_lines.append(payload)
                    continue
                continue
            if text == "":
                message = "\n".join(response_lines).strip()
                if is_error:
                    raise RuntimeError(message or f"GTP command failed: {command}")
                return message
            response_lines.append(text)


class EngineService:
    def __init__(self, config: Optional[ServiceConfig] = None):
        self.config = config or ServiceConfig.from_env()
        self.session = KataGoSession(self.config)

    def health(self) -> tuple[int, dict]:
        report = self.session.quick_health()
        return (200 if report.get("ok") else 503), report

    def warm_up(self) -> None:
        try:
            self.session.ensure_started()
        except Exception as exc:
            print(f"go-engine warm up skipped: {exc}", flush=True)
            return

    def genmove(self, payload: dict) -> tuple[int, dict]:
        request = EngineRequest.from_payload(payload)
        return 200, self.session.genmove(request)

    def score(self, payload: dict) -> tuple[int, dict]:
        request = EngineRequest.from_payload(payload)
        return 200, self.session.score(request)


def build_snapshot_sgf(request: EngineRequest) -> str:
    black_points: list[str] = []
    white_points: list[str] = []
    for row_index, row_text in enumerate(request.rows[: request.size]):
        line = str(row_text or "")
        for col_index, char in enumerate(line[: request.size]):
            if char.upper() == "B":
                black_points.append(to_sgf_coord(row_index, col_index))
            elif char.upper() == "W":
                white_points.append(to_sgf_coord(row_index, col_index))
    parts = [
        "(;",
        "FF[4]",
        "GM[1]",
        "CA[UTF-8]",
        f"SZ[{request.size}]",
        f"KM[{request.komi}]",
        "RU[Chinese]",
        f"PL[{'B' if request.current_turn == 'BLACK' else 'W'}]",
    ]
    if black_points:
        parts.append("AB" + "".join(f"[{point}]" for point in black_points))
    if white_points:
        parts.append("AW" + "".join(f"[{point}]" for point in white_points))
    parts.append(")")
    return "".join(parts)


def to_sgf_coord(row: int, col: int) -> str:
    base = ord("a")
    return f"{chr(base + col)}{chr(base + row)}"
