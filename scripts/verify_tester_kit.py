#!/usr/bin/env python3
"""Verify exactly one tester APK and optional exact-run source provenance."""
import argparse, hashlib, re
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("kit", type=Path)
p.add_argument("--source-artifact", type=Path)
p.add_argument("--commit")
a = p.parse_args()
apks = list(a.kit.glob("NekoFlash-*-debug.apk"))
if len(apks) != 1 or {f.name for f in a.kit.iterdir()} != {apks[0].name}:
    raise SystemExit("Tester kit must contain only one APK (no HTML or extras)")
apk, = apks
if not re.fullmatch(r"NekoFlash-[\w.-]+-debug\.apk", apk.name):
    raise SystemExit("Unexpected APK name")
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
if a.commit and (not re.fullmatch(r"[0-9a-f]{40}", a.commit) or a.source_artifact is None):
    raise SystemExit("Exact source commit requires matching CI artifact")
if a.source_artifact:
    if (a.source_artifact / "checksums-sha256.txt").read_text().split() != [digest, apk.name]:
        raise SystemExit("APK checksum disagrees with source artifact")
    if (a.source_artifact / apk.name).read_bytes() != apk.read_bytes():
        raise SystemExit("APK bytes differ from source artifact")
print(f"Verified APK-only kit: {apk.name}, sha256={digest}")
