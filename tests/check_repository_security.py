#!/usr/bin/env python3
"""Static gates for signing provenance. Repository Settings are checked separately."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
build = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
owners = (ROOT / ".github/CODEOWNERS").read_text(encoding="utf-8")

# Only the approved post-loss signing identity can produce a Release APK.
# Both manual and tag-based workflows must pin the exact same certificate.
import re
expected_cert = "3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2"
for workflow_name, workflow in (("build.yml", build), ("release.yml", release)):
    pins = re.findall(r'^  EXPECTED_RELEASE_CERT_SHA256: "([0-9a-f]{64})"

assert "  tag-provenance:" in release
preflight = release.split("  tag-provenance:", 1)[1].split("\n  release:", 1)[0]
release_job = release.split("\n  release:", 1)[1]
assert "git merge-base --is-ancestor" in preflight
assert "refs/heads/main" in preflight
assert "Release tag must match versionName" in preflight
assert "secrets." not in preflight
assert "contents: read" in preflight
assert "needs: tag-provenance" in release_job
assert "environment: nekoflash-release" in release_job
assert "contents: write" in release_job

assert "environment: ${{ github.event_name == 'workflow_dispatch'" in build
assert "'nekoflash-release' || 'nekoflash-ci'" in build
assert "inputs.sign_release && github.ref == 'refs/heads/main'" in build
assert "type: boolean" in build
assert "default: false" in build

# Every step that previously signed, verified or uploaded a release remains
# available but needs an explicit owner-reviewed manual run on main.
guard = "if: ${{ github.event_name == 'workflow_dispatch' && inputs.sign_release && github.ref == 'refs/heads/main' }}"
assert build.count(guard) == 5, "Unexpected count of protected signing steps"
assert "if: ${{ github.event_name == 'workflow_dispatch' }}" not in build
assert "secrets.NEKOFLASH_KEYSTORE_BASE64" in build
assert "secrets.NEKOFLASH_KEYSTORE_BASE64" in release_job

for path in ("/.github/workflows/", "/app/build.gradle", "/gradle/wrapper/"):
    assert path in owners and "@Ncorror" in owners

print("PASS: signing requires main, explicit opt-in, protected environment and tag provenance")
print("NOTE: repository rulesets, environment reviewers and secret migration MUST be configured in GitHub Settings")
,
                      workflow, re.MULTILINE)
    assert pins == [expected_cert], f"{workflow_name} signing fingerprint drift"
    assert "Actual certificate:" in workflow and "Expected certificate:" in workflow


assert "  tag-provenance:" in release
preflight = release.split("  tag-provenance:", 1)[1].split("\n  release:", 1)[0]
release_job = release.split("\n  release:", 1)[1]
assert "git merge-base --is-ancestor" in preflight
assert "refs/heads/main" in preflight
assert "Release tag must match versionName" in preflight
assert "secrets." not in preflight
assert "contents: read" in preflight
assert "needs: tag-provenance" in release_job
assert "environment: nekoflash-release" in release_job
assert "contents: write" in release_job

assert "environment: ${{ github.event_name == 'workflow_dispatch'" in build
assert "'nekoflash-release' || 'nekoflash-ci'" in build
assert "inputs.sign_release && github.ref == 'refs/heads/main'" in build
assert "type: boolean" in build
assert "default: false" in build

# Every step that previously signed, verified or uploaded a release remains
# available but needs an explicit owner-reviewed manual run on main.
guard = "if: ${{ github.event_name == 'workflow_dispatch' && inputs.sign_release && github.ref == 'refs/heads/main' }}"
assert build.count(guard) == 5, "Unexpected count of protected signing steps"
assert "if: ${{ github.event_name == 'workflow_dispatch' }}" not in build
assert "secrets.NEKOFLASH_KEYSTORE_BASE64" in build
assert "secrets.NEKOFLASH_KEYSTORE_BASE64" in release_job

for path in ("/.github/workflows/", "/app/build.gradle", "/gradle/wrapper/"):
    assert path in owners and "@Ncorror" in owners

print("PASS: signing requires main, explicit opt-in, protected environment and tag provenance")
print("NOTE: repository rulesets, environment reviewers and secret migration MUST be configured in GitHub Settings")
