#!/usr/bin/env python3
"""Pull private test images from an emulator and independently decode/fsck them."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def expand(source, target, overlay=False):
    with source.open('rb') as inp, target.open('r+b' if overlay else 'wb') as out:
        magic, major, minor, header, chunk_header, block, blocks, chunks, crc = struct.unpack('<IHHHHIIII', inp.read(28))
        assert (magic, major, block) == (0xed26ff3a, 1, 4096)
        if overlay:
            assert target.stat().st_size == blocks * block
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
                assert len(pattern) == 4
                if pattern == b'\0' * 4 and not overlay:
                    out.seek(size, 1)
                else:
                    buffer = pattern * (min(size, 1024 * 1024) // 4)
                    while size:
                        n = min(len(buffer), size)
                        out.write(buffer[:n])
                        size -= n
            elif kind == 0xcac3 and overlay:
                assert total == chunk_header
                out.seek(size, 1)
            else:
                raise AssertionError(f'Unexpected chunk {kind:x}; formatting must not leave DONT_CARE blocks')
            assert inp.tell() == start + total
        assert out.tell() == blocks * block
        assert inp.read(1) == b''
        out.truncate()


def pull(name):
    target = args.output / name
    with target.open('wb') as stream:
        subprocess.run(['adb', 'exec-out', 'run-as', 'ru.forum.adbfastboottool.dev', 'cat',
                        f'files/mkfs-test-output/{name}'], stdout=stream, check=True)
    return target


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


for fs in ('ext4', 'f2fs'):
    for variant in ('default', 'options'):
        name = f'{fs}-{variant}'
        sparse = pull(name + '.sparse')
        raw = args.output / (name + '.raw')
        expand(sparse, raw)
        original_hash = digest(raw)
        manifest = pull(name + '.parts.txt').read_text().splitlines()
        limit = int(manifest[0])
        assert limit == 16384 and len(manifest) > 2
        recombined = args.output / (name + '.resparse.raw')
        # Poison catches both lost data between downloads and zero-FILL incorrectly treated as SKIP.
        with recombined.open('wb') as out:
            poison = b'\x5a' * (1024 * 1024)
            left = raw.stat().st_size
            while left:
                size = min(left, len(poison))
                out.write(poison[:size])
                left -= size
        sizes = []
        for index, part_name in enumerate(manifest[1:], 1):
            assert part_name == f'{name}-part-{index}.sparse'
            part = pull(part_name)
            sizes.append(part.stat().st_size)
            assert 0 < sizes[-1] <= limit
            expand(part, recombined, overlay=True)
        assert digest(recombined) == original_hash, 'Resparse changed the expanded filesystem'
        tool = shutil.which('e2fsck' if fs == 'ext4' else 'fsck.f2fs')
        command = [tool, '-f', '-n', str(recombined)] if fs == 'ext4' else [tool, '-f', str(recombined)]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (args.output / (name + '.fsck.txt')).write_text(result.stdout)
        assert result.returncode == 0, result.stdout
        if fs == 'ext4':
            stat = subprocess.check_output(['debugfs', '-R', 'stat <2>', str(recombined)], text=True)
            assert re.search(r'User:\s+0\s+Group:\s+0', stat), stat
        evidence = dict(image=name, expanded=raw.stat().st_size, original_sparse=sparse.stat().st_size,
                        sha256=original_hash, resparse_equal=True, part_count=len(sizes), part_sizes=sizes,
                        download_limit=limit, fsck=result.returncode)
        (args.output / (name + '.resparse.json')).write_text(json.dumps(evidence, indent=2) + '\n')
        print(f'PASS Android {name}: expanded={raw.stat().st_size}, sparse={sparse.stat().st_size}, parts={len(sizes)}, resparse SHA256 identical, fsck=0')
        raw.unlink()
        recombined.unlink()
