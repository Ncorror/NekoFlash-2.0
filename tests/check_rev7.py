#!/usr/bin/env python3
"""No Android SDK needed: order/duplicate/limit invariants for the approved mass-flash queue."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev6.py')], cwd=root, check=True)
policy = (root/'app/src/main/java/ru/forum/adbfastboottool/FlashOperationDraft.kt').read_text()
vm = (root/'app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt').read_text()
assert 'MAX_QUEUE_ITEMS' not in policy, 'Legacy queue cap must not truncate the operator plan'
assert '.take(FlashOperationDraftPolicy.MAX_QUEUE_ITEMS)' not in policy
assert 'draft.items.forEach { existing -> next[existing.partition] = existing }' in policy
# Audit the edited orchestration file as an explicit checkpoint, not an unguarded exception.
import hashlib
# Git blob identity pins the narrow orchestrator change; protocol files remain
# guarded by LEGACY-SOURCE-SHA256.json in REV5.
raw_vm = (root/'app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt').read_bytes()
expected_blob = '3b68a4739c8492c1521466e5f47f2adb96e7ee1a'
assert hashlib.sha1(b'blob ' + str(len(raw_vm)).encode() + b'\0' + raw_vm).hexdigest() == expected_blob, 'Unexpected change to DeviceViewModel'
assert 'val ordered = queue' in vm and 'queue.sortedBy' not in vm
assert 'ordered.forEachIndexed' in vm and 'ordered.mapIndexed' in vm
assert 'fun moveFlashQueueDraftItem' in vm and 'fun removeFlashQueueDraftItem' in vm
assert 'testImplementation(\'junit:junit:4.13.2\')' in (root/'app/build.gradle').read_text()
print('PASS REV7: queue order, duplicate replacement, no silent 32-item truncation, moving, removal')
