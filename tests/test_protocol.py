import struct
import unittest
from unittest.mock import patch

from test_dns import dns_name, normalize_url, parse_dns, resolve


class DNSProtocolTests(unittest.TestCase):
    def packet(self, flags=0x8180, answer=None):
        question = b"\x07example\x03org\x00" + struct.pack("!HH", 1, 1)
        answer = answer if answer is not None else b"\xc0\x0c" + struct.pack("!HHIH", 1, 1, 60, 4) + b"\x5d\xb8\xd8\x22"
        return struct.pack("!6H", 1234, flags, 1, 1, 0, 0) + question + answer

    def test_compressed_answer(self):
        reply = parse_dns(self.packet(), 1234, "example.org", 1)
        self.assertEqual(reply["records"], [{"name": "example.org", "type": "A", "value": "93.184.216.34", "ttl": 60}])

    def test_reject_wrong_transaction_and_question(self):
        for ident, host in [(42, "example.org"), (1234, "other.org")]:
            with self.assertRaises(ValueError):
                parse_dns(self.packet(), ident, host, 1)

    def test_pointer_loop(self):
        with self.assertRaises(ValueError):
            dns_name(b"\xc0\x00", 0)

    def test_truncation_and_short_record(self):
        self.assertTrue(parse_dns(self.packet(flags=0x8380, answer=b""), 1234, "example.org", 1)["truncated"])
        with self.assertRaises(ValueError):
            parse_dns(self.packet()[:-1], 1234, "example.org", 1)

    def test_nxdomain(self):
        packet = struct.pack("!6H", 1234, 0x8183, 1, 0, 0, 0) + b"\x07example\x03org\x00" + struct.pack("!HH", 1, 1)
        self.assertEqual(parse_dns(packet, 1234, "example.org", 1)["status"], "NXDOMAIN")

    def test_cname_followup_ignores_unrelated_address(self):
        replies = [
            {"status": "NOERROR", "records": [
                {"name": "example.org", "type": "CNAME", "value": "cdn.example.org", "ttl": 60},
                {"name": "unrelated.org", "type": "A", "value": "192.0.2.1", "ttl": 60}]},
            {"status": "NOERROR", "records": [
                {"name": "cdn.example.org", "type": "A", "value": "192.0.2.2", "ttl": 60}]},
        ]
        with patch("test_dns.dns_query", side_effect=replies) as query:
            result = resolve({"name": "test", "ip": "192.0.2.53"}, "example.org", 1, 1)
        self.assertEqual(result["addresses"], ["192.0.2.2"])
        self.assertEqual(query.call_args_list[1].args[1], "cdn.example.org")

    def test_url_normalization_and_rejection(self):
        self.assertEqual(normalize_url("https://EXAMPLE.org:8443/a?x=1#fragment"), ("example.org", 8443, "/a?x=1"))
        for url in ["http://example.org", "https://user:pass@example.org", "https://127.0.0.1", "https://bad..org"]:
            with self.assertRaises(ValueError):
                normalize_url(url)


if __name__ == "__main__":
    unittest.main()
