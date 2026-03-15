from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from engine_service import EngineConfigError, EngineService


APP = None


class GoEngineHandler(BaseHTTPRequestHandler):
    server_version = "GoEngineHTTP/0.1"

    def do_GET(self) -> None:
        if self.path.rstrip("/") == "/health":
            status, payload = APP.health()
            self._write_json(status, payload)
            return
        if self.path in {"", "/"}:
            self._write_json(
                200,
                {
                    "service": "go-engine",
                    "endpoints": ["/health", "/genmove", "/score"],
                },
            )
            return
        self._write_json(404, {"error": "not_found"})

    def do_POST(self) -> None:
        try:
            payload = self._read_json()
        except ValueError as exc:
            self._write_json(400, {"error": str(exc)})
            return
        try:
            if self.path.rstrip("/") == "/genmove":
                status, body = APP.genmove(payload)
                self._write_json(status, body)
                return
            if self.path.rstrip("/") == "/score":
                status, body = APP.score(payload)
                self._write_json(status, body)
                return
            self._write_json(404, {"error": "not_found"})
        except EngineConfigError as exc:
            self._write_json(503, {"error": str(exc)})
        except ValueError as exc:
            self._write_json(400, {"error": str(exc)})
        except Exception as exc:
            self._write_json(500, {"error": str(exc)})

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return

    def _read_json(self) -> dict:
        length_text = self.headers.get("Content-Length", "0").strip() or "0"
        length = int(length_text)
        raw = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(raw or "{}")
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid_json: {exc.msg}") from exc
        if not isinstance(payload, dict):
            raise ValueError("request body must be a JSON object")
        return payload

    def _write_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    global APP
    APP = EngineService()
    threading.Thread(target=APP.warm_up, daemon=True).start()
    host = APP.config.bind_host
    port = APP.config.port
    server = ThreadingHTTPServer((host, port), GoEngineHandler)
    try:
        print(f"go-engine listening on http://{host}:{port}")
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        APP.session.stop()
        server.server_close()


if __name__ == "__main__":
    main()
