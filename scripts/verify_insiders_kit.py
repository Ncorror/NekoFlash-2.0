#!/usr/bin/env python3
"""Check the two-file field kit against its embedded build metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("kit", type=Path)
parser.add_argument("--commit", help="expected source commit of the APK")
args = parser.parse_args()
book = (args.kit / "testbook.html").read_text(encoding="utf-8")
info = json.loads(re.search(r"^const KIT = (.+);$", book, re.M)[1])
name = info["apkFilename"]
if not re.fullmatch(r"NekoFlash-[\w.-]+-debug\.apk", name):
    raise SystemExit("Unexpected APK filename")
if {p.name for p in args.kit.iterdir()} != {name, "testbook.html"}:
    raise SystemExit("The tester ZIP must contain exactly one APK and testbook.html")
if hashlib.sha256((args.kit / name).read_bytes()).hexdigest() != info["apkSha256"]:
    raise SystemExit("APK does not match the SHA-256 in the testbook")
if args.commit and info["sourceCommit"] != args.commit:
    raise SystemExit("APK and expected commit differ")
if info["testArtifactCommit"] != info["sourceCommit"] or not info["runId"].isdigit():
    raise SystemExit("Invalid build reference")
print(f'Commit: {info["sourceCommit"]}\nRun: {info["runId"]}\nAPK: {name}\nAPK SHA-256: {info["apkSha256"]}')
