"""Build the hosting upload ZIP, Apple profile and release checksums."""
from pathlib import Path
import hashlib
import shutil
import zipfile
import plistlib


def main():
    root = Path(__file__).resolve().parents[1]
    output = root / "downloads"
    output.mkdir(exist_ok=True)
    archive = output / "Heksenketel-Webapp-v1.3.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as package:
        for source in sorted((root / "webapp").iterdir()):
            if source.is_file() and source.suffix != ".md":
                package.write(source, "dns-diagnose/app/" + source.name)
        for name in ("diagnose.php", "dns.php"):
            package.write(root / "server" / name, "dns-diagnose/" + name)
        package.write(root / "webapp/README.md", "INSTALLATIE-WEBAPP.md")
    profile = root / "apple/De-Heksenketel.mobileconfig"
    data = plistlib.loads(profile.read_bytes())
    assert data["PayloadContent"][0]["URL"] == "https://www.deheksenketel.com/dns-diagnose/app/index.html"
    shutil.copy2(profile, output / profile.name)
    assets = [archive, output / profile.name]
    android = output / "Heksenketel-Netwerktest-v1.2.apk"
    if android.is_file():
        assets.append(android)
    checksums = "".join(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name + "\n" for path in assets)
    (output / "SHA256SUMS.txt").write_text(checksums)
    with zipfile.ZipFile(archive) as package:
        assert "dns-diagnose/report.php" not in package.namelist()
        assert package.testzip() is None
    print(checksums)


if __name__ == "__main__":
    main()
