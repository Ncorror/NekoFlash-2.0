#!/usr/bin/env python3
"""Regression: Insiders kit is APK-only with verified source SHA-256."""
import hashlib, subprocess, tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
workflow = (root / ".github/workflows/build.yml").read_text()
assert "tests/check_insiders_apk_kit.py" in workflow
assert "docs/insiders/testbook.html" not in workflow
for old in ("docs/insiders/testbook.html", "tests/check_insiders_testbook.py", "scripts/repack_insiders_release.sh"):
    assert not (root / old).exists(), old
with tempfile.TemporaryDirectory() as td:
    base = Path(td)
    source, kit = base / "source", base / "kit"
    source.mkdir()
    name = "NekoFlash-6.0.0-alpha11-debug.apk"
    content = b"synthetic CI provenance fixture"
    (source / name).write_bytes(content)
    (source / "checksums-sha256.txt").write_text(hashlib.sha256(content).hexdigest() + "  " + name + "\n")
    pack = ["python3", str(root / "scripts/package_insiders.py"), str(source), str(kit), "--commit", "a"*40, "--run", "123"]
    verify = ["python3", str(root / "scripts/verify_insiders_kit.py"), str(kit), "--source-artifact", str(source), "--commit", "a"*40]
    subprocess.run(pack, check=True)
    subprocess.run(verify, check=True)
    (kit / "testbook.html").write_text("not allowed")
    assert subprocess.run(verify, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
    (kit / "testbook.html").unlink()
    (kit / name).write_bytes(b"corrupt")
    assert subprocess.run(verify, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
print("PASS Insiders: APK only, SHA-256 verified, no HTML or corruption")
