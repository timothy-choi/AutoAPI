#!/usr/bin/env python3
"""Minimal mock upstream for AutoAPI Phase 1 demos and Compose."""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        self._handle()

    def do_POST(self):  # noqa: N802
        self._handle()

    def do_PUT(self):  # noqa: N802
        self._handle()

    def do_DELETE(self):  # noqa: N802
        self._handle()

    def _handle(self) -> None:
        request_id = self.headers.get("X-Request-ID", "")
        body = {
            "service": "upstream-v1",
            "method": self.command,
            "path": self.path,
            "requestId": request_id,
            "receivedHost": self.headers.get("Host", ""),
            "receivedForwardedHost": self.headers.get("X-Forwarded-Host", ""),
            "receivedForwardedFor": self.headers.get("X-Forwarded-For", ""),
            "receivedForwardedProto": self.headers.get("X-Forwarded-Proto", ""),
        }
        payload = json.dumps(body).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return


def main() -> None:
    port = int(os.environ.get("PORT", "8080"))
    host = os.environ.get("HOST", "0.0.0.0")
    server = ThreadingHTTPServer((host, port), Handler)
    print(f"mock-upstream listening on {host}:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
