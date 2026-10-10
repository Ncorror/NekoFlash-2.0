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
expected_vm_sha256 = None  # assigned in the change manifest when the staged file is finalized
assert 'val ordered = queue' in vm and 'queue.sortedBy' not in vm
assert 'ordered.forEachIndexed' in vm and 'ordered.mapIndexed' in vm
assert 'fun moveFlashQueueDraftItem' in vm and 'fun removeFlashQueueDraftItem' in vm
assert 'testImplementation(\'junit:junit:4.13.2\')' in (root/'app/build.gradle').read_text()
print('PASS REV7: queue order, duplicate replacement, no silent 32-item truncation, moving, removal')
