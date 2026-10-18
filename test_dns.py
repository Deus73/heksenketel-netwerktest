#!/usr/bin/env python3
"""DNS- en HTTPS-vergelijking. Alleen Python 3.10+ standaardbibliotheek nodig."""

import argparse
import concurrent.futures
import csv
import datetime as dt
import http.client
import ipaddress
import json
from pathlib import Path
import secrets
import socket
import struct
import time
from urllib.parse import urlsplit, urlunsplit


RCODES = {0: "NOERROR", 1: "FORMERR", 2: "SERVFAIL", 3: "NXDOMAIN", 4: "NOTIMP", 5: "REFUSED"}
LIMITS = (
    "Verschillende IP-adressen zijn op zichzelf geen bewijs van manipulatie (bijvoorbeeld CDN/geolocatie).",
    "REFUSED/time-out bij een externe provider-DNS bewijst geen websiteblokkade: de resolver kan alleen eigen klanten toelaten.",
    "DNS-verzoeken en HTTPS lopen via de huidige internetverbinding, niet via de internetverbinding van de bevraagde provider.",
    "DNS via poort 53 kan onderweg gefilterd of onderschept worden; antwoorden zijn niet cryptografisch geverifieerd.",
    "HTTPS valideert certificaten en gebruikt de oorspronkelijke hostnaam (SNI). Redirects worden vastgelegd, niet gevolgd.",
    "Een HTTPS-fout kan ook door serverbeveiliging, netwerkproblemen of ontbrekende IPv6-connectiviteit ontstaan.",
    "Dit onderzoekt geen zoekmachinerangschikking, zoekresultaatteksten of AI-antwoorden en bewijst geen opzet.",
    "De meegeleverde providerlijst is een gedocumenteerde startlijst, geen volledige of gegarandeerd actuele lijst van Belgische providers.",
)


def dns_name(data, offset):
    labels, seen, end = [], set(), None
    while True:
        if offset in seen or offset >= len(data):
            raise ValueError("Ongeldige DNS-naam/verwijzingslus")
        seen.add(offset)
        length = data[offset]
        if length & 0xC0 == 0xC0:
            if offset + 1 >= len(data):
                raise ValueError("Afgebroken DNS-verwijzing")
            if end is None:
                end = offset + 2
            offset = ((length & 0x3F) << 8) | data[offset + 1]
        elif length & 0xC0:
            raise ValueError("Ongeldig DNS-label")
        else:
            offset += 1
            if not length:
                return ".".join(labels).lower(), end if end is not None else offset
            if offset + length > len(data):
                raise ValueError("Afgebroken DNS-label")
            labels.append(data[offset:offset + length].decode("ascii"))
            offset += length


def parse_dns(data, query_id, host, qtype):
    if len(data) < 12:
        raise ValueError("Afgebroken DNS-header")
    ident, flags, qd, an, _, _ = struct.unpack("!6H", data[:12])
    if ident != query_id or not flags & 0x8000 or flags & 0x7800 or qd != 1:
        raise ValueError("DNS-antwoord komt niet overeen met verzoek")
    name, offset = dns_name(data, 12)
    typ, cls = struct.unpack_from("!HH", data, offset)
    if (name, typ, cls) != (host.lower(), qtype, 1):
        raise ValueError("Onverwachte DNS-vraag in antwoord")
    offset += 4
    records = []
    # Een afgekapt UDP-antwoord wordt opnieuw via TCP opgevraagd.
    if flags & 0x0200:
        return {"status": "TRUNCATED", "records": [], "truncated": True}
    for _ in range(an):
        owner, offset = dns_name(data, offset)
        typ, cls, ttl, length = struct.unpack_from("!HHIH", data, offset)
        offset += 10
        end = offset + length
        if end > len(data):
            raise ValueError("Afgebroken DNS-record")
        value = None
        if cls == 1 and typ in (1, 28):
            value = str(ipaddress.ip_address(data[offset:end]))
        elif cls == 1 and typ == 5:
            value, _ = dns_name(data, offset)
        if value is not None:
            records.append({"name": owner, "type": {1: "A", 28: "AAAA", 5: "CNAME"}[typ], "value": value, "ttl": ttl})
        offset = end
    return {"status": RCODES.get(flags & 15, str(flags & 15)), "records": records, "truncated": False}


def receive_exact(sock, size):
    result = bytearray()
    while len(result) < size:
        part = sock.recv(size - len(result))
        if not part:
            raise OSError("TCP DNS-verbinding vroegtijdig gesloten")
        result.extend(part)
    return bytes(result)


def dns_query(server, host, qtype, timeout):
    ident = secrets.randbits(16)
    encoded = b"".join(bytes([len(label)]) + label.encode("ascii") for label in host.split(".")) + b"\0"
    packet = struct.pack("!6H", ident, 0x0100, 1, 0, 0, 0) + encoded + struct.pack("!HH", qtype, 1)
    family = socket.AF_INET6 if ipaddress.ip_address(server).version == 6 else socket.AF_INET
    with socket.socket(family, socket.SOCK_DGRAM) as sock:
        sock.settimeout(timeout)
        sock.connect((server, 53))
        sock.send(packet)
        result = parse_dns(sock.recv(65535), ident, host, qtype)
    transport = "UDP"
    if result["truncated"]:
        with socket.socket(family, socket.SOCK_STREAM) as sock:
            sock.settimeout(timeout)
            sock.connect((server, 53))
            sock.sendall(struct.pack("!H", len(packet)) + packet)
            size = struct.unpack("!H", receive_exact(sock, 2))[0]
            result = parse_dns(receive_exact(sock, size), ident, host, qtype)
        transport = "TCP"
    result["transport"] = transport
    return result


def resolve(resolver, host, qtype, timeout):
    started = time.monotonic()
    result = {"resolver": resolver["name"], "server": resolver["ip"], "category": resolver.get("category", "custom"),
              "source": resolver.get("source", ""), "type": "A" if qtype == 1 else "AAAA", "records": [], "queries": []}
    current, visited = host, set()
    try:
        for _ in range(8):
            if current in visited:
                raise ValueError("CNAME-lus")
            visited.add(current)
            reply = dns_query(resolver["ip"], current, qtype, timeout)
            result["queries"].append({"name": current, **reply})
            result["records"].extend(reply["records"])
            result["status"] = reply["status"]
            if reply["status"] != "NOERROR":
                break
            # Accepteer adressen uitsluitend uit de gevraagde naam/CNAME-keten.
            aliases = {r["name"]: r["value"] for r in reply["records"] if r["type"] == "CNAME"}
            chain_seen = set()
            while current in aliases:
                if current in chain_seen:
                    raise ValueError("CNAME-lus")
                chain_seen.add(current)
                current = aliases[current]
            addresses = [r["value"] for r in reply["records"] if r["name"] == current and r["type"] == result["type"]]
            if addresses:
                result["addresses"] = sorted(set(addresses))
                break
            if not chain_seen:
                result["status"] = "NODATA"
                break
        else:
            raise ValueError("CNAME-keten te lang")
    except Exception as exc:
        result.update(status="TIMEOUT" if isinstance(exc, TimeoutError) else "ERROR", error=str(exc))
    result.setdefault("addresses", [])
    result["duration_ms"] = round((time.monotonic() - started) * 1000)
    return result


def https_probe(host, port, path, address, timeout):
    started = time.monotonic()
    result = {"address": address or "systeem-DNS", "tls_verified": False}
    conn = http.client.HTTPSConnection(host, port, timeout=timeout)
    if address:
        # HTTPSConnection blijft zelf de originele hostnaam voor certificaat en SNI gebruiken.
        conn._create_connection = lambda destination, timeout, source_address=None: socket.create_connection((address, destination[1]), timeout, source_address)
    try:
        conn.request("GET", path, headers={"User-Agent": "DNS-Provider-Diagnostic/1.0", "Range": "bytes=0-4095"})
        response = conn.getresponse()
        result.update(status="HTTP", http_status=response.status, tls_verified=True,
                      location=response.getheader("Location"), server=response.getheader("Server"),
                      peer=conn.sock.getpeername()[0] if conn.sock else None)
    except Exception as exc:
        result.update(status="ERROR", error=f"{type(exc).__name__}: {exc}")
    finally:
        conn.close()
    result["duration_ms"] = round((time.monotonic() - started) * 1000)
    return result


def normalize_url(value):
    parsed = urlsplit(value if "://" in value else "https://" + value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Gebruik een HTTPS-webadres zonder gebruikersnaam/wachtwoord")
    host = parsed.hostname.rstrip(".").encode("idna").decode("ascii").lower()
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise ValueError("Geef een domeinnaam op, geen IP-adres")
    labels = host.split(".")
    if len(host) > 253 or any(not label or len(label) > 63 or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in label) for label in labels):
        raise ValueError("Ongeldige domeinnaam")
    port = parsed.port or 443
    path = urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
    path.encode("ascii")  # Gebruik %-codering voor niet-ASCII URL-paden.
    return host, port, path


def markdown(report):
    def cell(value):
        return str(value if value is not None else "").replace("|", "\\|").replace("\n", " ").replace("\r", " ")
    lines = ["# DNS- en HTTPS-vergelijking", "", f"- Doel: {report['url']}", f"- Tijd (UTC): {report['timestamp']}",
             f"- Aansluiting (zelf ingevuld): {report['connection']}", "", "## DNS-antwoorden", "",
             "| Resolver | DNS-server | Type | Uitkomst | Adressen |", "|---|---|---|---|---|"]
    for row in report["dns"]:
        lines.append("| " + " | ".join(cell(v) for v in (row["resolver"], row["server"], row["type"], row["status"], ", ".join(row["addresses"]))) + " |")
    lines += ["", "## HTTPS-bereikbaarheid (eerste antwoord, redirects niet gevolgd)", "",
              "| Doel-IP / systeem-DNS | Uitkomst | HTTP | TLS geldig | Redirect / fout |", "|---|---|---|---|---|"]
    for row in report["https"]:
        lines.append("| " + " | ".join(cell(v) for v in (row["address"], row["status"], row.get("http_status", ""), row["tls_verified"], row.get("location") or row.get("error", ""))) + " |")
    lines += ["", "## Interpretatie en beperkingen", ""] + ["- " + line for line in LIMITS]
    lines += ["", "Volledige DNS-records, foutdetails en bronnen staan in report.json.", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", nargs="?", help="Domeinnaam of volledige HTTPS-URL")
    parser.add_argument("--connection", default="Niet opgegeven", help="Eigen label, bijvoorbeeld 'Telenet thuis'")
    parser.add_argument("--resolvers", type=Path, default=Path(__file__).with_name("resolvers.json"))
    parser.add_argument("--resolver", action="append", default=[], metavar="NAAM=IP", help="Extra DNS-server, herhaalbaar")
    parser.add_argument("--timeout", type=float, default=4)
    parser.add_argument("--output", type=Path, help="Nieuwe uitvoermap (mag nog niet bestaan)")
    args = parser.parse_args()
    try:
        url = args.url or input("Officieel webadres van De Heksenketel: ").strip()
        host, port, path = normalize_url(url)
        if not 0 < args.timeout <= 60:
            raise ValueError("Timeout moet tussen 0 en 60 seconden liggen")
        resolvers = json.loads(args.resolvers.read_text(encoding="utf-8"))
        for extra in args.resolver:
            name, address = extra.split("=", 1)
            resolvers.append({"name": name, "ip": address, "category": "custom"})
        for resolver in resolvers:
            ipaddress.ip_address(resolver["ip"])
            if not resolver["name"]:
                raise ValueError("Resolvernaam ontbreekt")
        stamp = dt.datetime.now(dt.timezone.utc)
        output = args.output or Path.cwd() / ("dns-report-" + stamp.strftime("%Y%m%dT%H%M%S%fZ"))
        output.mkdir(parents=True, exist_ok=False)
    except (ValueError, OSError, KeyError, TypeError, EOFError) as exc:
        parser.error(str(exc))
    print(f"Test {host} via {len(resolvers)} DNS-servers; huidige verbinding: {args.connection}")
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        jobs = [pool.submit(resolve, resolver, host, qtype, args.timeout) for resolver in resolvers for qtype in (1, 28)]
        dns = [job.result() for job in jobs]
        addresses = sorted({address for row in dns for address in row["addresses"]})
        jobs = [pool.submit(https_probe, host, port, path, address, args.timeout) for address in [None, *addresses]]
        https = [job.result() for job in jobs]
    report = {"url": f"https://{host}" + (f":{port}" if port != 443 else "") + path,
              "timestamp": stamp.isoformat(), "connection": args.connection, "dns": dns, "https": https, "limitations": LIMITS}
    (output / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "report.md").write_text(markdown(report), encoding="utf-8")
    with (output / "dns.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["resolver", "server", "type", "status", "addresses", "duration_ms", "error"])
        for row in dns:
            writer.writerow([row["resolver"], row["server"], row["type"], row["status"], ";".join(row["addresses"]), row["duration_ms"], row.get("error", "")])
    print(f"Klaar. Lees: {output / 'report.md'}")
    print("Afwijkingen zijn onderzoekssignalen, geen automatische vaststelling van een blokkade.")


if __name__ == "__main__":
    main()
