#!/usr/bin/env python3
"""Static gates for signing provenance. Repository Settings are checked separately."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
build = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
owners = (ROOT / ".github/CODEOWNERS").read_text(encoding="utf-8")
# The two release paths must accept only the same approved replacement certificate.
import re
expected_cert = "3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2"
for workflow_name, workflow in (("build.yml", build), ("release.yml", release)):
    pins = re.findall("EXPECTED_RELEASE_CERT_SHA256: \"([0-9a-f]{64})\"", workflow)
    assert pins == [expected_cert], f"{workflow_name}: unexpected signing fingerprint"
    assert "Actual certificate:" in workflow
    assert "Expected certificate:" in workflow


# Third-party actions in build/release must be immutable full-SHA pins.
expected_actions = {
    "actions/checkout": "fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09",
    "actions/setup-java": "b6effb05e454b25005698d916606bdc6ffcbf961",
    "gradle/actions/setup-gradle": "8da46b539cd8fe6ab27ca75128919a18d757604c",
    "actions/upload-artifact": "b7c566a772e6b6bfb58ed0dc250532a479d7789f",
    "actions/download-artifact": "37930b1c2abaa49bbe596cd826c3c89aef350131",
    "reactivecircus/android-emulator-runner": "a421e43855164a8197daf9d8d40fe71c6996bb0d",
}
seen = {}
for workflow_name, workflow in (("build.yml", build), ("release.yml", release)):
    for action, ref in re.findall(r"uses:\s+([^@\s]+)@([0-9A-Za-z._/-]+)", workflow):
        assert re.fullmatch(r"[0-9a-f]{40}", ref), (
            f"{workflow_name}: action must use a full commit SHA: {action}@{ref}"
        )
        if action in expected_actions:
            assert ref == expected_actions[action], (
                f"{workflow_name}: unexpected pin for {action}: {ref}"
            )
        seen[action] = ref
for action, ref in expected_actions.items():
    if action in build or action in release:
        assert seen.get(action) == ref, f"Missing audited action pin: {action}"

assert "  tag-provenance:" in release
preflight = release.split("  tag-provenance:", 1)[1].split("\n  release:", 1)[0]
release_job = release.split("\n  release:", 1)[1]
assert "git merge-base --is-ancestor" in preflight
assert "refs/heads/main" in preflight
assert 'EXPECTED_TAG="v$VERSION"' in preflight
assert ".github/RELEASE_REQUEST" in preflight
assert '[ "$TAG_COMMIT" = "$MAIN_TIP" ]' in preflight
assert 'git tag -a "$TAG" "$TAG_COMMIT"' in preflight
assert 'git push origin "refs/tags/$TAG"' in preflight
assert "secrets." not in preflight
assert "contents: write" in preflight
assert "actions: read" in preflight
assert "Exact-SHA main CI PASS before tag creation" in preflight
assert "refusing to create $TAG" in preflight
assert preflight.index("Exact-SHA main CI PASS before tag creation") < preflight.index('git tag -a "$TAG" "$TAG_COMMIT"')
assert "needs: tag-provenance" in release_job
assert "environment: nekoflash-release" in release_job
assert "contents: write" in release_job

assert "concurrency:" in build
assert "group: build-${{ github.workflow }}-${{ github.ref }}" in build
assert "cancel-in-progress: true" in build
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

print("PASS: release provenance, signing isolation and immutable action pins verified")
print("NOTE: repository rulesets, environment reviewers and secret migration MUST be configured in GitHub Settings")
