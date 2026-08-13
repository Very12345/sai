"""Small build-only HTTP proxy used to prepare the signed offline ARM64 rootfs.

It is intentionally not packaged in the Android application. Android connects to
it through `adb reverse`, allowing Debian packages to be staged on the build
machine even when the device itself cannot reach a mirror reliably.
"""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

OPENER = build_opener(ProxyHandler({}))


class Proxy(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_HEAD(self):
        self._forward(False)

    def do_GET(self):
        self._forward(True)

    def _forward(self, include_body):
        target = self.path
        if not target.startswith(("http://", "https://")):
            self.send_error(400, "absolute URL required")
            return
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in {"host", "connection", "proxy-connection", "accept-encoding"}
        }
        try:
            response = OPENER.open(Request(target, headers=headers, method=self.command), timeout=90)
        except HTTPError as error:
            response = error
        except Exception as error:
            self.send_error(502, str(error))
            return
        self.send_response(response.status)
        for key, value in response.headers.items():
            if key.lower() not in {"connection", "transfer-encoding", "content-encoding"}:
                self.send_header(key, value)
        self.send_header("Connection", "close")
        self.end_headers()
        if include_body:
            while True:
                block = response.read(256 * 1024)
                if not block:
                    break
                self.wfile.write(block)
        response.close()

    def log_message(self, format, *args):
        print("apt-proxy:", format % args, flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 18080), Proxy).serve_forever()
