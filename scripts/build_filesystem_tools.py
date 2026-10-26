#!/usr/bin/env python3
"""Build separate Android PIE mkfs programs; never link GPL tools into the app.

Linux build host: git, make, gcc, autoconf, automake, libtool, pkg-config, Python 3.12.
Only pinned source archives are accepted, including for an offline source rebuild.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'vendor/filesystems/manifest.json'
ABIS = {
    'arm64-v8a': ('aarch64-linux-android', 'aarch64-linux-android'),
    'armeabi-v7a': ('armv7a-linux-androideabi', 'arm-linux-androideabi'),
    'x86': ('i686-linux-android', 'i686-linux-android'),
    'x86_64': ('x86_64-linux-android', 'x86_64-linux-android'),
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args, cwd=None, env=None, log=None):
    print('+', ' '.join(map(str, args)), flush=True)
    if log is None:
        subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)
    else:
        with log.open('a') as out:
            result = subprocess.run(list(map(str, args)), cwd=cwd, env=env,
                                    stdout=out, stderr=subprocess.STDOUT)
        if result.returncode:
            print(log.read_text(errors='replace')[-12000:], flush=True)
            result.check_returncode()


def sources(manifest, cache, output):
    cache.mkdir(parents=True, exist_ok=True)
    result = {}
    for name, spec in manifest['sources'].items():
        archive = cache / (name + '.tar')
        if not archive.exists():
            with tempfile.TemporaryDirectory() as temp:
                run(['git', 'init', '-q', temp])
                run(['git', '-C', temp, 'fetch', '--depth=1', spec['url'], spec['commit']])
                with archive.open('wb') as stream:
                    subprocess.run(['git', '-C', temp, 'archive', '--format=tar', 'FETCH_HEAD'],
                                   stdout=stream, check=True)
        if sha(archive) != spec['tar_sha256']:
            raise ValueError(f'Source archive checksum mismatch: {archive}')
        dest = output / 'src' / name
        dest.mkdir(parents=True)
        with tarfile.open(archive) as tar:
            tar.extractall(dest, filter='data')
        result[name] = dest
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/filesystem-tools')
    parser.add_argument('--source-cache', type=Path, default=ROOT / 'build/filesystem-source-cache')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    ndk = args.ndk.resolve()
    if f'Pkg.Revision = {manifest["ndk"]}' not in (ndk / 'source.properties').read_text():
        raise ValueError('Use the pinned NDK from vendor/filesystems/manifest.json')
    toolchain = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
    if not toolchain.is_dir():
        raise ValueError('Build the filesystem tools on Linux x86_64 (or WSL2)')
    output = args.output.resolve()
    stamp = output / 'build-info.json'
    inputs = [MANIFEST, Path(__file__), ROOT / 'vendor/filesystems/README.md']
    key = hashlib.sha256(b''.join(p.read_bytes() for p in inputs)).hexdigest()
    if stamp.is_file():
        info = json.loads(stamp.read_text())
        if info['key'] == key and all((output / p).is_file() and sha(output / p) == digest
                                      for p, digest in info['outputs'].items()):
            print('Verified filesystem tools are up to date')
            return
    # Only our dedicated build directory is removed; never the source cache or NDK.
    if output == ROOT or ROOT.is_relative_to(output) or ndk.is_relative_to(output):
        raise ValueError('Unsafe output directory')
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    src = sources(manifest, args.source_cache.resolve(), output)
    run(['autoreconf', '-fi'], cwd=src['f2fs-tools'], log=output / 'autoconf.log')
    jobs = str(min(os.cpu_count() or 2, 8))
    for abi, (clang_target, host) in ABIS.items():
        work = output / 'work' / abi
        ext = work / 'ext4'
        f2fs = work / 'f2fs'
        ext.mkdir(parents=True)
        f2fs.mkdir()
        log = output / (abi + '.log')
        env = os.environ.copy()
        env.update(CC=str(toolchain / f'{clang_target}{manifest["api"]}-clang'),
                   AR=str(toolchain / 'llvm-ar'), RANLIB=str(toolchain / 'llvm-ranlib'),
                   STRIP=str(toolchain / 'llvm-strip'), BUILD_CC='cc',
                   CFLAGS=f'-O2 -fPIE -D_FILE_OFFSET_BITS=64 -ffile-prefix-map={output}=/build',
                   LDFLAGS='-pie -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384',
                   LC_ALL='C', SOURCE_DATE_EPOCH='1681171200')
        run([src['e2fsprogs'] / 'configure', '--host=' + host, '--enable-libuuid',
             '--enable-libblkid', '--disable-nls', '--disable-elf-shlibs',
             '--disable-fuse2fs', '--disable-uuidd'], cwd=ext, env=env, log=log)
        run(['make', '-j' + jobs, 'libs'], cwd=ext, env=env, log=log)
        run(['make', '-C', 'misc', '-j' + jobs, 'mke2fs'], cwd=ext, env=env, log=log)
        env['CPPFLAGS'] = f'-I{ext}/lib -I{src["e2fsprogs"]}/lib'
        env['LDFLAGS'] += f' -L{ext}/lib'
        run([src['f2fs-tools'] / 'configure', '--host=' + host, '--disable-shared',
             '--enable-static', '--without-selinux', '--without-blkid', '--without-lzo2',
             '--without-lz4'], cwd=f2fs, env=env, log=log)
        if '#define HAVE_LIBUUID 1' not in (f2fs / 'config.h').read_text():
            raise ValueError('F2FS must use the private libuuid built for this ABI')
        run(['make', '-C', 'lib', '-j' + jobs], cwd=f2fs, env=env, log=log)
        run(['make', '-C', 'mkfs', '-j' + jobs, 'mkfs.f2fs'], cwd=f2fs, env=env, log=log)
        dest = output / 'jniLibs' / abi
        dest.mkdir(parents=True)
        for source, filename in [(ext / 'misc/mke2fs', 'libnekoflash_mke2fs.so'),
                                 (f2fs / 'mkfs/mkfs.f2fs', 'libnekoflash_mkfs_f2fs.so')]:
            target = dest / filename
            shutil.copy2(source, target)
            run([toolchain / 'llvm-strip', '--strip-unneeded', target])
    licenses = output / 'assets/filesystem-tools'
    licenses.mkdir(parents=True)
    for source, name in [(src['e2fsprogs'] / 'NOTICE', 'e2fsprogs-NOTICE.txt'),
                         (src['e2fsprogs'] / 'lib/uuid/COPYING', 'libuuid-COPYING.txt'),
                         (src['f2fs-tools'] / 'COPYING', 'f2fs-tools-COPYING.txt'),
                         (MANIFEST, 'manifest.json'),
                         (ROOT / 'vendor/filesystems/README.md', 'README.txt')]:
        shutil.copy2(source, licenses / name)
    # Ship exact corresponding sources and build instructions beside every APK.
    archive = output / 'filesystem-tools-source.tar.gz'
    with tarfile.open(archive, 'w:gz') as tar:
        for source in inputs:
            tar.add(source, arcname=str(source.relative_to(ROOT)))
        for name in manifest['sources']:
            tar.add(args.source_cache / (name + '.tar'), arcname='source-cache/' + name + '.tar')
    outputs = [p for folder in ('jniLibs', 'assets') for p in (output / folder).rglob('*') if p.is_file()]
    outputs.append(archive)
    stamp.write_text(json.dumps({'key': key, 'manifest': manifest,
        'outputs': {str(p.relative_to(output)): sha(p) for p in outputs}}, indent=2) + '\n')


if __name__ == '__main__':
    main()
