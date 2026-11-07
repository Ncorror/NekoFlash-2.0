#!/usr/bin/env python3
"""Stage a field kit using the APK verified by the Android matrix in this run."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/insiders"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("debug", type=Path, help="extracted debug artifact")
parser.add_argument("output", type=Path, help="new output directory")
parser.add_argument("--commit", required=True)
parser.add_argument("--run", required=True)
args = parser.parse_args()
if not re.fullmatch(r"[0-9a-f]{40}", args.commit) or not re.fullmatch(r"[0-9]+", args.run):
    parser.error("expected a full Git SHA and numeric Actions run ID")

apk, = args.debug.glob("NekoFlash-*-debug.apk")
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
if (args.debug / "checksums-sha256.txt").read_text().split() != [digest, apk.name]:
    raise SystemExit("APK does not match the debug artifact checksum")

page = (DOCS / "testbook.html").read_text()
match = re.search(r"^const KIT = (\{[^\n]+\});$", page, re.M)
if not match:
    raise SystemExit("Missing embedded testbook build metadata")
build = json.loads(match[1])
# The standalone book identifies the previous reference APK. The kit always
# carries the APK and provenance from the just-tested run.
build.pop("referenceRevision", None)
build.update(sourceCommit=args.commit, testArtifactCommit=args.commit, runId=args.run,
             apkFilename=apk.name, apkSha256=digest)
page = page[:match.start(1)] + json.dumps(build) + page[match.end(1):]
out = args.output
out.mkdir(parents=True, exist_ok=False)
(out / "testbook.html").write_text(page)
shutil.copyfile(apk, out / apk.name)
