#!/usr/bin/env python3
"""A tiny stand-in for the Apicurio v3 endpoints register-schemas.sh uses: artifacts, versions, rules.
Like Apicurio it refuses an existing version (409) and an existing rule (409). usage: fake-registry.py <port>"""
import json
import re
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

BASE = "/apis/registry/v3/groups/default/artifacts"
artifacts = {}  # id -> {"versions": set, "rules": set}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def answer(self, code, body=None):
        data = json.dumps(body or {}).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        return json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")

    def do_GET(self):
        m = re.fullmatch(BASE + r"/([^/]+)(?:/versions/([^/]+))?", self.path)
        if not m or m.group(1) not in artifacts:
            return self.answer(404)
        if m.group(2) and m.group(2) not in artifacts[m.group(1)]["versions"]:
            return self.answer(404)
        self.answer(200)

    def do_POST(self):
        body = self.body()
        if self.path == BASE:
            if body["artifactId"] in artifacts:
                return self.answer(409)
            artifacts[body["artifactId"]] = {"versions": {body["firstVersion"]["version"]}, "rules": set()}
            return self.answer(200)
        m = re.fullmatch(BASE + r"/([^/]+)/(versions|rules)", self.path)
        if not m or m.group(1) not in artifacts:
            return self.answer(404)
        bucket, key = (("versions", body.get("version")) if m.group(2) == "versions" else ("rules", body.get("ruleType")))
        if key in artifacts[m.group(1)][bucket]:
            return self.answer(409)
        artifacts[m.group(1)][bucket].add(key)
        self.answer(200 if bucket == "versions" else 204)

    def do_PUT(self):
        self.body()
        self.answer(200)


HTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
