import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import engine_service  # noqa: E402
from engine_service import (  # noqa: E402
    EngineRequest,
    GtpCoordinateCodec,
    KataGoSession,
    LocalGoBoard,
    MoveRecord,
    ServiceConfig,
    build_snapshot_sgf,
    parse_final_score,
)


class GtpCoordinateCodecTest(unittest.TestCase):
    def test_zero_based_to_gtp_and_back(self):
        self.assertEqual("D16", GtpCoordinateCodec.to_gtp(19, 3, 3))
        self.assertEqual((3, 3), GtpCoordinateCodec.from_gtp(19, "D16"))
        self.assertEqual("T1", GtpCoordinateCodec.to_gtp(19, 18, 18))
        self.assertEqual((18, 18), GtpCoordinateCodec.from_gtp(19, "T1"))

    def test_skips_i_column(self):
        self.assertEqual("J19", GtpCoordinateCodec.to_gtp(19, 0, 8))
        self.assertEqual((0, 8), GtpCoordinateCodec.from_gtp(19, "J19"))

    def test_pass_round_trip(self):
        self.assertIsNone(GtpCoordinateCodec.from_gtp(19, "pass"))


class LocalGoBoardTest(unittest.TestCase):
    def test_replay_counts_area_for_single_stones(self):
        request = EngineRequest(
            size=19,
            komi=7.5,
            moves=[
                MoveRecord("BLACK", 3, 3, False),
                MoveRecord("WHITE", 15, 15, False),
            ],
            current_turn="BLACK",
            to_play="BLACK",
            ai_stone="BLACK",
            difficulty="MEDIUM",
        )
        board = LocalGoBoard.from_request(request)
        black_area, white_area = board.area_score()
        self.assertEqual(1, black_area)
        self.assertEqual(1, white_area)

    def test_capture_removes_surrounded_stone(self):
        request = EngineRequest(
            size=19,
            komi=7.5,
            moves=[
                MoveRecord("BLACK", 0, 1, False),
                MoveRecord("WHITE", 0, 0, False),
                MoveRecord("BLACK", 1, 0, False),
            ],
            current_turn="WHITE",
            to_play="WHITE",
            ai_stone="WHITE",
            difficulty="MEDIUM",
        )
        board = LocalGoBoard.from_request(request)
        self.assertEqual(".", board.grid[0][0])
        self.assertEqual("B", board.grid[0][1])
        self.assertEqual("B", board.grid[1][0])

    def test_rows_snapshot_is_authoritative(self):
        request = EngineRequest(
            size=19,
            komi=7.5,
            rows=[
                ".B.................",
                "B..................",
            ],
            moves=[MoveRecord("BLACK", 10, 10, False)],
            current_turn="WHITE",
            to_play="WHITE",
            ai_stone="WHITE",
            difficulty="MEDIUM",
        )
        board = LocalGoBoard.from_request(request)
        self.assertEqual("B", board.grid[0][1])
        self.assertEqual("B", board.grid[1][0])
        self.assertEqual(".", board.grid[10][10])


class SnapshotSgfTest(unittest.TestCase):
    def test_build_snapshot_sgf_contains_setup_and_player(self):
        request = EngineRequest(
            size=19,
            komi=7.5,
            rows=[
                ".B.................",
                "W..................",
            ],
            current_turn="WHITE",
            to_play="WHITE",
            ai_stone="WHITE",
            difficulty="MEDIUM",
        )
        sgf = build_snapshot_sgf(request)
        self.assertIn("SZ[19]", sgf)
        self.assertIn("KM[7.5]", sgf)
        self.assertIn("PL[W]", sgf)
        self.assertIn("AB[ba]", sgf)
        self.assertIn("AW[ab]", sgf)

    def test_snapshot_file_is_closed_before_loadsgf(self):
        request = EngineRequest(
            size=19,
            komi=7.5,
            rows=[
                ".B.................",
                "W..................",
            ],
            current_turn="WHITE",
            to_play="WHITE",
            ai_stone="WHITE",
            difficulty="MEDIUM",
        )
        session = KataGoSession(
            ServiceConfig(
                bind_host="127.0.0.1",
                port=2718,
                engine_name="KataGo",
                rules="chinese",
                startup_timeout_sec=5,
                command_timeout_sec=5,
                difficulty_visits={"EASY": 0, "MEDIUM": 0, "HARD": 0},
                command=["katago"],
            )
        )

        class FakeTempFile:
            def __init__(self) -> None:
                self.name = "C:/temp/fake-board.sgf"
                self.closed = False
                self.content = ""

            def write(self, text: str) -> None:
                self.content += text

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb) -> None:
                self.closed = True

        fake_file = FakeTempFile()
        states: dict[str, bool] = {}
        original_named_tempfile = engine_service.tempfile.NamedTemporaryFile
        original_remove = engine_service.os.remove
        original_run = session._run_command_locked
        try:
            engine_service.tempfile.NamedTemporaryFile = lambda *args, **kwargs: fake_file
            engine_service.os.remove = lambda path: None

            def fake_run_command(command: str, timeout_sec: float) -> str:
                states["closed_at_load"] = fake_file.closed
                self.assertEqual("loadsgf C:/temp/fake-board.sgf", command)
                return ""

            session._run_command_locked = fake_run_command
            session._load_snapshot_locked(request)
        finally:
            engine_service.tempfile.NamedTemporaryFile = original_named_tempfile
            engine_service.os.remove = original_remove
            session._run_command_locked = original_run

        self.assertTrue(states.get("closed_at_load"))


class FinalScoreParserTest(unittest.TestCase):
    def test_black_win(self):
        parsed = parse_final_score("B+7.5")
        self.assertEqual("BLACK", parsed.winner)
        self.assertAlmostEqual(7.5, parsed.final_score)

    def test_white_win(self):
        parsed = parse_final_score("W+1.0")
        self.assertEqual("WHITE", parsed.winner)
        self.assertAlmostEqual(-1.0, parsed.final_score)

    def test_fallback_for_draw(self):
        parsed = parse_final_score("0")
        self.assertEqual("", parsed.winner)
        self.assertAlmostEqual(0.0, parsed.final_score)


if __name__ == "__main__":
    unittest.main()
