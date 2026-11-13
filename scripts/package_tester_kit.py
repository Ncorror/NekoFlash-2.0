#!/usr/bin/env python3
"""Stage an APK-only tester kit from the exact verified CI artifact."""
import argparse, hashlib, re, shutil
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("debug", type=Path)
p.add_argument("output", type=Path)
p.add_argument("--commit", required=True)
p.add_argument("--run", required=True)
a = p.parse_args()
if not re.fullmatch(r"[0-9a-f]{40}", a.commit) or not re.fullmatch(r"[0-9]+", a.run):
    p.error("expected full source SHA and numeric run ID")
apks = list(a.debug.glob("NekoFlash-*-debug.apk"))
if len(apks) != 1:
    raise SystemExit("Expected exactly one debug APK in source artifact")
apk, = apks
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
if (a.debug / "checksums-sha256.txt").read_text().split() != [digest, apk.name]:
    raise SystemExit("APK source SHA-256 mismatch")
a.output.mkdir(parents=True, exist_ok=False)
shutil.copyfile(apk, a.output / apk.name)
print(f"Tester APK-only kit: commit={a.commit} run={a.run} apk={apk.name} sha256={digest}")
