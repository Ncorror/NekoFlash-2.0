#!/usr/bin/env python3
"""Collect every required installed-app UI screenshot; a missing capture fails CI."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
names = ['mass-1000-header', 'mass-999-restored'] + [
    f'{configuration}-{page}'
    for configuration in ('portrait', 'large', 'landscape-large')
    for page in ('home', 'quick', 'sideload', 'unlock', 'settings', 'mass-empty', 'tools')
]
for name in names:
    data = subprocess.check_output([
        'adb', 'exec-out', 'run-as', 'ru.forum.adbfastboottool.dev',
        'cat', f'files/ui-test-output/{name}.png'
    ], timeout=30)
    assert data.startswith(b'\x89PNG\r\n\x1a\n') and len(data) > 1000, name
    (args.output / f'{name}.png').write_bytes(data)
print(f'PASS: collected {len(names)} real interface captures')
