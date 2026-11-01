#!/usr/bin/env python3
"""Inspect actual packaged ELF programs, not only Gradle declarations."""
from pathlib import Path
import argparse
import json
import re
import struct
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
args = parser.parse_args()
machines = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86': (1, 3), 'x86_64': (2, 62)}
with zipfile.ZipFile(args.apk) as apk, tempfile.TemporaryDirectory() as temp:
    manifest = json.loads(apk.read('assets/filesystem-tools/manifest.json'))
    assert manifest['sources']['e2fsprogs']['version'] == '1.47.0'
    assert manifest['sources']['f2fs-tools']['version'] == '1.16.0'
    for name in ('e2fsprogs-NOTICE.txt', 'libuuid-COPYING.txt', 'f2fs-tools-COPYING.txt', 'README.txt'):
        assert len(apk.read('assets/filesystem-tools/' + name)) > 100
    for abi, (elf_class, machine) in machines.items():
        for name in ('libnekoflash_mke2fs.so', 'libnekoflash_mkfs_f2fs.so'):
            data = apk.read(f'lib/{abi}/{name}')
            assert data[:6] == b'\x7fELF' + bytes([elf_class, 1])
            assert struct.unpack_from('<HH', data, 16) == (3, machine), (abi, name)  # ET_DYN PIE
            entry = struct.unpack_from('<Q' if elf_class == 2 else '<I', data, 24)[0]
            assert entry != 0, 'A shared library cannot be executed as a mkfs program'
            path = Path(temp) / name
            path.write_bytes(data)
            info = subprocess.check_output(['readelf', '-W', '-l', '-d', str(path)], text=True)
            assert 'Requesting program interpreter: /system/bin/linker' in info
            assert '(RPATH)' not in info and '(RUNPATH)' not in info and '(TEXTREL)' not in info
            needed = re.findall(r'\(NEEDED\).*\[(.*?)\]', info)
            assert set(needed) <= {'libc.so', 'libm.so', 'libdl.so', 'liblog.so'}, needed
            loads = [line for line in info.splitlines() if line.strip().startswith('LOAD ')]
            assert loads and all(int(line.split()[-1], 16) >= 16384 for line in loads)
            print(f'PASS {abi}/{name}: PIE, 16K alignment, NEEDED={needed}, {len(data)} bytes')
print('PASS filesystem executables and upstream license assets in APK')
