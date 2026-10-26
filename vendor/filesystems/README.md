# Bundled filesystem generators

NekoFlash starts separate `mke2fs` (e2fsprogs 1.47.0) and `mkfs.f2fs`
(f2fs-tools 1.16.0) processes to create private regular-file images. They are
not linked into the Kotlin app or the USB JNI library. No root, network access,
Termux or host `/system/bin` formatter is required at runtime.

These versions match the filesystem compatibility baseline used for REV21.
They are pinned versions, not a claim that they are the latest upstream releases.
`manifest.json` records full upstream commits and SHA-256 of their `git archive`
source trees. The F2FS GitHub mirror contains the identical upstream v1.16.0 commit.
The build applies no source patches. Private support libraries are statically
linked into each executable; only Android platform libraries remain dynamic.

The APK contains the upstream notices/licenses under `assets/filesystem-tools/`.
The utilities retain their upstream GPL-2.0 licenses and component notices
(including e2fsprogs LGPL/BSD/MIT components); NekoFlash's existing license does
not relicense them. Every APK distribution produced by CI also includes
`filesystem-tools-source.tar.gz`, containing the complete corresponding source
archives, this manifest, and the build script. Preserve that companion archive
when redistributing the APK. Upstream authors and notices are retained in full.

## Build and offline rebuild

Use Linux x86_64 (or WSL2), Python 3.12+, git, make, gcc, autoconf, automake,
libtool and pkg-config. Install Android NDK `27.2.12479018` using SDK Manager.
Normal Gradle builds invoke the script and package all four Android ABIs.
First build downloads pinned sources; subsequent builds verify their checksums.

From the repository, or after extracting the companion source archive:

```sh
python3 scripts/build_filesystem_tools.py \
  --ndk /path/to/android-sdk/ndk/27.2.12479018 \
  --source-cache source-cache --output build/filesystem-tools
```

The companion archive's `source-cache` permits an offline rebuild with the
listed compiler/build prerequisites installed. Commands and logs are recorded
per ABI in `build/filesystem-tools/`; `build-info.json` records output hashes.
Pinned inputs enable reproducible rebuilds; byte-for-byte reproducibility
across different host tool versions is not asserted.

The PIE programs use `lib*.so` filenames so PackageManager installs them into
`nativeLibraryDir`. Gradle enables legacy JNI extraction for both build types.
The app executes only these installed files, never a cache/data-directory copy.
It sets a private mke2fs profile and root owner explicitly. It validates the
resulting filesystem and Android sparse image before the existing USB plan runs.
Raw images, subprocess logs and config are removed on success, failure or cancel.
