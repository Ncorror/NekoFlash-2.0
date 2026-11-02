#!/usr/bin/env python3
"""REV44: offline Insiders HTML source contract (no remote uploads)."""
from pathlib import Path
import json
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
page = root / "docs/insiders/testbook.html"
source = page.read_text(encoding="utf-8")
assert source.startswith("<!doctype html>") and source.rstrip().endswith("</html>")
assert source.count("<script>") == 1 and source.count("</script>") == 1

# Keep previous tester's core features: RU/EN, local state, select/copy,
# offline ZIP builder, full original binary logs and explicit file permissions.
for marker in (
    "NekoFlash 2.0", "const T = {", "ru: {", "en: {",
    "const TESTS = [", "nekoflash-rev45-testkit-v4",
    "nekoflash-rev43-testkit-v3", "nekoflash-rev29-testkit-v2", "nekoflash-rev25-testkit-v1", "function makeStoreZip(",
    "async function blobCRC(", "async function generateZip(",
    "async function chosenLogs(", "webkitdirectory",
    "trace-", "evidence-manifest.json", "progress.json",
    "downloadBlob(", "localStorage", "zipCreating",
):
    assert marker in source, f"Missing tester feature/provenance: {marker}"

for old in ("485f0e0f314d32dc94ee31524ad53c2a856ab484f2d4f39593a6c6a1480f729a",
            "41021696162", "12961957458"):
    assert old not in source, f"Old REV29 release metadata leaked: {old}"
build = json.loads(re.search(r"^const KIT = (.+);$", source, re.M)[1])
assert re.fullmatch(r"[0-9a-f]{40}", build["sourceCommit"])
assert build["sourceCommit"] == build["testArtifactCommit"]
assert re.fullmatch(r"[0-9a-f]{64}", build["apkSha256"]) and build["runId"].isdigit()
assert "schema: SCHEMA," in source and "IMPORT_SCHEMAS = [SCHEMA," in source
assert "!IMPORT_SCHEMAS.includes(v.schema)" in source
assert "API26 FAIL" not in source

# Ensure scenario IDs unique. This test parses only id properties in TESTS,
# not DOM IDs or unrelated metadata.
scenarios = source.split("const TESTS = [", 1)[1].split("\n];\n", 1)[0]
ids = re.findall(r"(?:\bid\s*:\s*'|\"id\"\s*:\s*\")([A-Z]+-\d+)", scenarios)
assert len(ids) >= 40, f"Scenario count unusually low: {len(ids)}"
assert len(ids) == len(set(ids)), f"Duplicate test IDs: {len(ids) - len(set(ids))}"
for id_ in ("TERM-06", "TERM-05", "ADB-07", "ADB-08", "ADB-09", "USB-03", "FB-04", "CI-02", "LOG-03"):
    assert id_ in ids, f"Missing REV43 field test: {id_}"

node = shutil.which("node")
assert node, "Node.js is required on GitHub Actions runner to validate inline JavaScript syntax"
script = source.split("<script>", 1)[1].split("</script>", 1)[0]
with tempfile.TemporaryDirectory() as temp:
    js_file = Path(temp) / "testbook.js"
    js_file.write_text(script, encoding="utf-8")
    subprocess.run([node, "--check", str(js_file)], check=True, cwd=root)
print(f"PASS Insiders: offline RU/EN testbook, {len(ids)} scenarios, JS parse, AutoLogs BIN and exact CI metadata")

tag = (root / "docs/insiders/campaign-tag.txt").read_text().strip()
assert re.fullmatch(r"insiders-rev[1-9][0-9]*", tag)
assert tag == "insiders-" + build["revision"].lower()
