"""Integration tests: real PHP HTTP endpoint and a local fake sendmail, no real email."""
import contextlib
import http.client
import json
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import uuid


@contextlib.contextmanager
def receiver(mail_success=True, disable_mail=False, wordpress=False):
    preferred = "/tmp/opencode" if Path("/tmp/opencode").is_dir() else None
    with tempfile.TemporaryDirectory(prefix="ketel-receiver-", dir=preferred) as folder:
        root = Path(folder)
        shutil.copy2(Path(__file__).with_name("report.php"), root / "report.php")
        mailbox = root / "mailbox.txt"
        mailer = root / "sendmail"
        mailer.write_text(f"#!{sys.executable}\nimport sys\nfrom pathlib import Path\np=Path({str(mailbox)!r})\nwith p.open('ab') as f: f.write(sys.stdin.buffer.read())\nsys.exit({0 if mail_success else 1})\n")
        mailer.chmod(0o700)
        if wordpress:
            webroot = root / "web"
            endpoint = webroot / "dns-diagnose"
            endpoint.mkdir(parents=True)
            shutil.move(str(root / "report.php"), endpoint / "report.php")
            (webroot / "wp-load.php").write_text("<?php function wp_mail($to, $subject, $body, $headers) { file_put_contents(" + repr(str(mailbox)) + ", $to . \"\\n\" . $body); return true; }")
            docroot = endpoint
            state_root = endpoint
        else:
            docroot = root
            state_root = root
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        command = ["php", "-d", f"sendmail_path={mailer}"]
        if disable_mail:
            command += ["-d", "disable_functions=mail"]
        process = subprocess.Popen(command + ["-S", f"127.0.0.1:{port}", "-t", str(docroot)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            for _ in range(100):
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                        break
                except OSError:
                    time.sleep(0.02)
            else:
                raise RuntimeError("PHP server did not start")
            yield port, mailbox
        finally:
            process.terminate()
            process.wait(timeout=5)
            import hashlib
            state = Path(tempfile.gettempdir()) / ("heksenketel-dns-" + hashlib.sha256(str(state_root).encode()).hexdigest()[:16])
            shutil.rmtree(state, ignore_errors=True)


def request(port, body=None, method="POST", content_type="application/json"):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        conn.request(method, "/report.php", json.dumps(body) if body is not None else None, {"Content-Type": content_type})
        result = conn.getresponse()
        return result.status, json.loads(result.read())
    finally:
        conn.close()


def report():
    return {"schema": 1, "id": str(uuid.uuid4()), "target": "www.deheksenketel.com", "dns": [], "network": {}, "https": {}}


class ReceiverTests(unittest.TestCase):
    def test_mail_delivery_contract_deduplication_and_rate_limit(self):
        with receiver() as (port, mailbox):
            self.assertEqual(request(port, method="GET")[0], 405)
            self.assertEqual(request(port, report(), content_type="text/plain")[0], 415)
            self.assertEqual(request(port, {**report(), "target": "other.example"})[0], 400)
            first = report()
            self.assertEqual(request(port, first), (200, {"accepted": True}))
            self.assertEqual(request(port, first), (200, {"accepted": True, "duplicate": True}))
            for _ in range(4):
                self.assertEqual(request(port, report())[0], 200)
            self.assertEqual(request(port, report())[0], 429)
            contents = mailbox.read_text()
            self.assertEqual(contents.count("To: info@deheksenketel.com"), 5)
            self.assertIn(first["id"], contents)

    def test_mail_failure_is_not_reported_as_accepted(self):
        with receiver(mail_success=False) as (port, mailbox):
            code, body = request(port, report())
            self.assertEqual(code, 503)
            self.assertFalse(body["accepted"])

    def test_disabled_php_mail_returns_controlled_failure(self):
        with receiver(disable_mail=True) as (port, mailbox):
            code, body = request(port, report())
            self.assertEqual(code, 503)
            self.assertFalse(body["accepted"])

    def test_wordpress_mail_works_when_php_mail_is_disabled(self):
        with receiver(disable_mail=True, wordpress=True) as (port, mailbox):
            payload = report()
            self.assertEqual(request(port, payload), (200, {"accepted": True}))
            self.assertIn(payload["id"], mailbox.read_text())


if __name__ == "__main__":
    unittest.main()
