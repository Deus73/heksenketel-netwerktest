"""Generate the PWA's PNG icons using only Python's standard library."""
from pathlib import Path
import math
import struct
import zlib


def icon(size):
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    rows = bytearray()
    for y in range(size):
        rows.append(0)
        for x in range(size):
            px, py = (x + 0.5) * 512 / size, (y + 0.5) * 512 / size
            radius = math.hypot(px - 256, py - 324)
            signal = py < 290 and ((170 < radius < 198 and abs(px - 256) < 150 and py < 222) or
                                   (86 < radius < 114 and abs(px - 256) < 90 and py < 276))
            dot = (px - 256) ** 2 + (py - 340) ** 2 <= 26 ** 2
            rows.extend((255, 255, 255) if signal or dot else (18, 117, 99))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!2I5B", size, size, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


if __name__ == "__main__":
    destination = Path(__file__).resolve().parents[1] / "webapp"
    for size in (180, 192, 512):
        (destination / f"icon-{size}.png").write_bytes(icon(size))
