#!/usr/bin/env python3
"""REV44: offline Insiders HTML source contract (no remote uploads)."""
from pathlib import Path
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
    "const TESTS = [", "const KEY = 'nekoflash_insiders_rev43_testkit_v3'",
    "LEGACY_KEY", "LEGACY_OLD_KEY", "function makeStoreZip(",
    "async function blobCRC(", "async function generateZip(",
    "async function chosenLogs(", "webkitdirectory",
    "trace-", "evidence-manifest.json", "progress.json",
    "downloadBlob(", "localStorage", "zipCreating",
    "bd0d1fbf0ff5ba69634df5281fa87e2ff1289bf5",
    "27ab796d100387b3530bd88a6faf28e48d1c717c",
    "41247325391", "41248168147",
):
    assert marker in source, f"Missing tester feature/provenance: {marker}"

for old in ("485f0e0f314d32dc94ee31524ad53c2a856ab484f2d4f39593a6c6a1480f729a",
            "41021696162", "12961957458"):
    assert old not in source, f"Old REV29 release metadata leaked: {old}"
assert "const APK_SHA256 = ''" in source, "Do not pin an unverified old APK hash"
assert "API26 FAIL" in source and "API34 PASS" in source

# Ensure scenario IDs unique. This test parses only id properties in TESTS,
# not DOM IDs or unrelated metadata.
scenarios = source.split("const TESTS = [", 1)[1].split("\n];\n", 1)[0]
ids = re.findall(r"(?:\bid\s*:\s*'|\"id\"\s*:\s*\")([A-Z]+-\d+)", scenarios)
assert len(ids) >= 40, f"Scenario count unusually low: {len(ids)}"
assert len(ids) == len(set(ids)), f"Duplicate test IDs: {len(ids) - len(set(ids))}"
for id_ in ("TERM-04", "TERM-05", "ADB-04", "ADB-05", "ADB-06", "USB-02", "FB-04", "CI-02", "LOG-03"):
    assert id_ in ids, f"Missing REV43 field test: {id_}"

node = shutil.which("node")
assert node, "Node.js is required on GitHub Actions runner to validate inline JavaScript syntax"
script = source.split("<script>", 1)[1].split("</script>", 1)[0]
with tempfile.TemporaryDirectory() as temp:
    js_file = Path(temp) / "testbook.js"
    js_file.write_text(script, encoding="utf-8")
    subprocess.run([node, "--check", str(js_file)], check=True, cwd=root)
print(f"PASS REV44: offline RU/EN testbook, {len(ids)} scenarios, JS parse, AutoLogs BIN and exact CI metadata")
