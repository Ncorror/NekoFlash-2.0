# NekoFlash 2.0 — release readiness

**Final audit:** 13 November 2026  
**Target:** `v6.0.0-alpha11` · versionCode `234`

## Decision

**READY FOR SIGNED ALPHA RELEASE**, subject to the protected release workflow completing successfully on the exact tagged main commit.

No destructive hardware rerun is required for this release gate.

## Completed gates

- Permanent release key is enforced by certificate SHA-256 `3d9d8ec98ff5a0aa5e72a257f02babe19c0ff91f0af431955aa9afe8901cf5b2`.
- Release packaging is fail-closed when key inputs are absent.
- Release tag/version provenance is enforced against `main`.
- Build/release GitHub Actions are pinned to exact commit SHAs.
- #64 Fastboot wipe/format hardware path is closed with real Android boot after wipe.
- #72 ADB Pull investigation is closed: generic Pull passes on X3 Pro host; F7/onyx is classified as host-specific intermittent USB receive behavior, with checkpoint/resume + SHA verification merged.
- Recovery/Sideload, Mass Flash, Fastboot mode transitions, terminal rotation/input, Xiaomi login and ZIP selection/clear have accepted field evidence on the tested devices.
- Old TestBook/REV campaign documents and automatic Insiders prerelease pinning are removed from the current release path.

## Signed release workflow

Publication is initiated only from `main` by changing `.github/RELEASE_REQUEST` to the exact expected tag, or by an already-approved matching `v*` tag.

The workflow:
1. checks `versionName` / versionCode;
2. requires the requested tag to equal `v$versionName`;
3. requires the commit to be current/approved `main`;
4. creates the tag from current main when the request path is used;
5. enters protected environment `nekoflash-release`;
6. verifies the permanent signing certificate;
7. runs source/docs/native/JVM/lint gates;
8. builds and verifies the signed APK;
9. verifies bundled filesystem executables/source evidence;
10. publishes APK, SHA-256, signature, build metadata and filesystem companion source to GitHub Releases.

Alpha/beta/rc versions are marked GitHub prereleases automatically.

## Hardware qualification boundary

FIELD PASS is scoped to tested devices/firmware. It is not a promise that every Android USB host/controller behaves identically.

### POCO F7 / onyx

F7 has historical and current evidence of intermittent inbound USB receive failures as host. The same target/files pass on an X3 Pro host. NekoFlash keeps transport fail-closed and large Pull is resumable from fully synced 256 KiB checkpoints.

Weak/unstable OTG power is plausible field context, but VBUS voltage/current were not measured and power is not claimed as the proven root cause.

## Release smoke

The release workflow is allowed to publish after its own gates pass. After publication, a non-destructive install/launch sanity check is sufficient; do not repeat format/wipe/flash merely because the APK is signed.

Check version/build identity, launch/navigation, terminal input, absence of startup mutation, non-mutating Sideload selection and diagnostic export.

## Historical material

Old Insiders/TestBook campaign files were removed from the active tree. The authoritative technical history remains in Git commits/issues and the physical evidence ledgers.
