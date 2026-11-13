# NekoFlash 2.0 — legal/distribution review

This is a maintainer checklist, not legal advice.

## Project code

NekoFlash project source is distributed under the root Apache License 2.0 unless a file or component states otherwise.

Project artwork listed in [ASSETS_LICENSE.md](../ASSETS_LICENSE.md) is excluded from the Apache-2.0 branding/artwork grant.

## Third-party material

The APK includes separate filesystem executables:
- e2fsprogs / `mke2fs`;
- f2fs-tools / `mkfs.f2fs`.

They retain their upstream GPL-2.0/component-specific licensing and are not relicensed as Apache-2.0 NekoFlash code.

The current release model therefore keeps `filesystem-tools-source.tar.gz` alongside the APK as corresponding source. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) and [vendor/filesystems/README.md](../vendor/filesystems/README.md).

## Current public release

`v6.0.0-alpha11` publishes custom assets:
- `NekoFlash-6.0.0-alpha11.apk`;
- `NekoFlash-6.0.0-alpha11.apk.sha256`;
- `filesystem-tools-source.tar.gz`.

GitHub additionally exposes automatic source-code archives for the tag.

## Independence

NekoFlash is independent and is not affiliated with or endorsed by Google, Xiaomi, Termux, MiForge or the upstream open-source projects referenced by the implementation.

Android, Xiaomi and other names/trademarks remain the property of their respective owners.

## Distribution checklist

Before every signed release:
1. verify the exact source/tag and permanent APK certificate;
2. verify third-party notices and pinned filesystem source manifest;
3. publish the corresponding-source archive with the APK;
4. do not include proprietary firmware/ROMs or account material;
5. keep private hardware logs/serials/tokens outside public GitHub;
6. ensure public release notes do not overstate hardware qualification.
