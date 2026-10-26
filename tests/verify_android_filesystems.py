#!/usr/bin/env python3
"""Pull private test images from an emulator and independently decode/fsck them."""
from pathlib import Path
import argparse
import shutil
import struct
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def expand(source, target):
    with source.open('rb') as inp, target.open('wb') as out:
        magic, major, minor, header, chunk_header, block, blocks, chunks, crc = struct.unpack('<IHHHHIIII', inp.read(28))
        assert (magic, major, block) == (0xed26ff3a, 1, 4096)
        inp.seek(header)
        for _ in range(chunks):
            start = inp.tell()
            kind, reserved, count, total = struct.unpack('<HHII', inp.read(12))
            inp.seek(start + chunk_header)
            size = count * block
            if kind == 0xcac1:
                assert total == chunk_header + size
                while size:
                    data = inp.read(min(size, 1024 * 1024))
                    assert data
                    out.write(data)
                    size -= len(data)
            elif kind == 0xcac2:
                assert total == chunk_header + 4
                pattern = inp.read(4)
                if pattern == b'\0' * 4:
                    out.seek(size, 1)
                else:
                    buffer = pattern * (min(size, 1024 * 1024) // 4)
                    while size:
                        n = min(len(buffer), size)
                        out.write(buffer[:n])
                        size -= n
            else:
                raise AssertionError(f'Unexpected chunk {kind:x}; formatting must not leave DONT_CARE blocks')
            assert inp.tell() == start + total
        assert out.tell() == blocks * block
        assert inp.read(1) == b''
        out.truncate()


for fs in ('ext4', 'f2fs'):
    for variant in ('default', 'options'):
        name = f'{fs}-{variant}'
        sparse = args.output / (name + '.sparse')
        with sparse.open('wb') as stream:
            subprocess.run(['adb', 'exec-out', 'run-as', 'ru.forum.adbfastboottool.dev', 'cat',
                            f'files/mkfs-test-output/{name}.sparse'], stdout=stream, check=True)
        raw = args.output / (name + '.raw')
        expand(sparse, raw)
        tool = shutil.which('e2fsck' if fs == 'ext4' else 'fsck.f2fs')
        command = [tool, '-f', '-n', str(raw)] if fs == 'ext4' else [tool, '-f', str(raw)]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (args.output / (name + '.fsck.txt')).write_text(result.stdout)
        assert result.returncode == 0, result.stdout
        if fs == 'ext4':
            stat = subprocess.check_output(['debugfs', '-R', 'stat <2>', str(raw)], text=True)
            import re
            assert re.search(r'User:\s+0\s+Group:\s+0', stat), stat
        print(f'PASS Android {name}: expanded={raw.stat().st_size}, sparse={sparse.stat().st_size}, fsck=0')
        raw.unlink()
