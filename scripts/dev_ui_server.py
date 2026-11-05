#!/usr/bin/env python3
"""Loopback-only, read-only synthetic server for NAS Watchdog UI development.

It serves watchdog/ui plus fixture JSON. It never contacts a host, Docker socket,
NAS, firmware, or external service. All POST action requests return HTTP 403.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import re
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "watchdog" / "ui"
FIXTURE = json.loads((ROOT / "watchdog" / "testdata" / "ui-fixture.json").read_text())
FIXTURE_TIME = datetime.fromisoformat(FIXTURE["stats"]["ts"].replace("Z", "+00:00"))
RANGE_MINUTES = {"5m": 5, "1h": 60, "6h": 360, "24h": 1440}


def json_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False).encode("utf-8")


class FixtureHandler(BaseHTTPRequestHandler):
    server_version = "NASWatchdogSyntheticUI/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        # Avoid logging query strings: only the route class is useful in fixture testing.
        route = urlparse(self.path).path
        print(
            f"{self.address_string()} {self.command} {route} {args[1] if len(args) > 1 else ''}"
        )

    def send_data(self, status: int, data: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, value: object, status: int = 200) -> None:
        self.send_data(status, json_bytes(value), "application/json; charset=utf-8")

    def require_fixture_read(self) -> bool:
        # No fixture credential is required. If a token is supplied for UI testing,
        # verify that normal API requests use Authorization rather than a query token.
        if "token" in parse_qs(urlparse(self.path).query):
            self.send_json(
                {
                    "error": "Synthetic fixture accepts token bootstrap only on the document URL"
                },
                401,
            )
            return False
        return True

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler API
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path.startswith("/api/") and not self.require_fixture_read():
            return
        if path == "/api/v1/stats":
            self.send_json(FIXTURE["stats"])
            return
        if path == "/api/v1/history":
            selected_range = query.get("range", ["1h"])[0]
            minutes = RANGE_MINUTES.get(selected_range, 60)
            cutoff = FIXTURE_TIME - timedelta(minutes=minutes)
            samples = []
            for sample in FIXTURE["history"]:
                timestamp = datetime.fromisoformat(sample["ts"].replace("Z", "+00:00"))
                if timestamp >= cutoff:
                    samples.append(sample)
            self.send_json({"samples": samples})
            return
        if path == "/api/v1/events":
            self.send_json({"events": FIXTURE["events"]})
            return
        if path in ("/api/v1/journal", "/api/v1/dmesg", "/api/v1/logs"):
            self.handle_log(path, query)
            return
        if path == "/api/v1/containers/logs":
            name = query.get("name", [""])[0]
            if name not in {
                container["name"] for container in FIXTURE["stats"]["containers"]
            }:
                self.send_json({"error": "synthetic container not found"}, 404)
                return
            text = "\n".join(
                f"2026-01-02T12:{index % 10:02d}:00Z synthetic {name} output line {index + 1}"
                for index in range(80)
            )
            self.send_json({"name": name, "text": text})
            return
        if path == "/api/v1/health":
            self.send_json({"ok": True, "hostname": "sample-nas", "synthetic": True})
            return
        if path.startswith("/api/"):
            self.send_json({"error": "Unknown synthetic endpoint"}, 404)
            return
        self.serve_static(path)

    def handle_log(self, path: str, query: dict[str, list[str]]) -> None:
        try:
            line_limit = min(2000, max(10, int(query.get("lines", ["300"])[0])))
        except ValueError:
            self.send_json({"error": "invalid line limit"}, 400)
            return
        pattern = query.get("q", [""])[0]
        try:
            matcher = re.compile(pattern, re.IGNORECASE) if pattern else None
        except re.error as error:
            self.send_json({"error": f"invalid synthetic regex: {error}"}, 400)
            return
        source = (
            "journal"
            if path.endswith("journal")
            else "kernel"
            if path.endswith("dmesg")
            else query.get("source", [""])[0]
        )
        known = {entry["source"] for entry in FIXTURE["stats"]["logs"]}
        if source not in known | {"journal", "kernel"}:
            self.send_json({"error": "unknown synthetic log source"}, 404)
            return
        lines = [
            f"2026-01-02T12:{index % 10:02d}:{index % 60:02d}Z sample-nas {source}[{100 + index}]: "
            + (
                "WARNING synthetic check pending"
                if index % 7 == 0
                else "INFO synthetic fixture message"
            )
            for index in range(90)
        ]
        if matcher:
            lines = [line for line in lines if matcher.search(line)]
        self.send_json({"lines": lines[-line_limit:], "source": source})

    def serve_static(self, request_path: str) -> None:
        relative = unquote(request_path).lstrip("/") or "index.html"
        candidate = (UI_ROOT / relative).resolve()
        if (
            UI_ROOT.resolve() not in candidate.parents
            and candidate != UI_ROOT.resolve()
        ):
            self.send_json({"error": "not found"}, 404)
            return
        if not candidate.is_file():
            self.send_json({"error": "not found"}, 404)
            return
        mime = mimetypes.guess_type(candidate.name)[0] or "application/octet-stream"
        if mime.startswith("text/") or candidate.suffix in (".js", ".json"):
            mime += "; charset=utf-8"
        self.send_data(200, candidate.read_bytes(), mime)

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler API
        if urlparse(self.path).path == "/api/v1/actions":
            self.send_json(
                {
                    "ok": False,
                    "message": "Synthetic fixture never executes host actions",
                },
                403,
            )
            return
        self.send_json({"error": "read-only synthetic fixture"}, 405)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1", choices=("127.0.0.1", "::1"))
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    print(
        f"Synthetic NAS Watchdog UI: http://{args.host}:{args.port}/ (actions always refused)"
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
