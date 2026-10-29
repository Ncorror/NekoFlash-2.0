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
names += ['terminal-command', 'terminal-keyboard', 'terminal-landscape', 'import-copying', 'import-cancelled']
missing = []
for name in names:
    result = subprocess.run([
        'adb', 'exec-out', 'run-as', 'ru.forum.adbfastboottool.dev',
        'cat', f'files/ui-test-output/{name}.png'
    ], timeout=30, capture_output=True)
    data = result.stdout
    if result.returncode or not data.startswith(b'\x89PNG\r\n\x1a\n') or len(data) <= 1000:
        missing.append(name)
        continue
    (args.output / f'{name}.png').write_bytes(data)
assert not missing, f'Missing UI captures: {missing}'
print(f'PASS: collected {len(names)} real interface captures')
