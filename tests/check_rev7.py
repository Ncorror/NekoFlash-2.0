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
expected_blob = 'b80db90bf4aa40edaa29d0dd6a70a698c5a06d45'
def git_blob_sha(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

# REV31 allows ONE exact raw-shell evidence directory argument in the ADB
# constructor. Rebuild the old blob in memory; unrelated orchestration edits
# and changes elsewhere in DeviceViewModel must continue to fail this guard.
if git_blob_sha(raw_vm) != expected_blob:
    reviewed_change = (
        b'                keyDirectory = adbKeyDir,\n'
        b'                // The exact binary shell streams live beside the public\n'
        b'                // compact/trace logs, so a tester can include them in a ZIP.\n'
        b'                // No rotation, sanitization or automatic pruning of raw bytes.\n'
        b'                rawShellLogsDir = File(workspaceRoot ?: getApplication<Application>().filesDir, "logs"),\n'
    )
    original = b'                keyDirectory = adbKeyDir,\n'
    assert raw_vm.count(reviewed_change) == 1, 'Unreviewed ADB evidence path change'
    # REV45 moves the unchanged conservative disk-space condition into a
    # documented helper. The preflight must still happen *before* any flash.
    # Normalize exactly this reviewed call-site change; all other VM edits
    # remain forbidden by the original Git blob integrity check.
    sparse_before = b'                require(directory.usableSpace >= image.largestPartBytes) {\n'
    sparse_after = b'                require(hasImmediateSparseSpace(directory, image.largestPartBytes)) {\n'
    assert raw_vm.count(sparse_after) == 1, 'Unexpected REV45 sparse preflight call-site'
    protected_vm = raw_vm.replace(reviewed_change, original, 1)
    protected_vm = protected_vm.replace(sparse_after, sparse_before, 1)
    assert git_blob_sha(protected_vm) == expected_blob, (
        'DeviceViewModel changed outside the reviewed REV31 evidence path'
    )
assert 'val ordered = queue' in vm and 'queue.sortedBy' not in vm
assert 'ordered.forEachIndexed' in vm and 'ordered.mapIndexed' in vm
assert 'fun moveFlashQueueDraftItem' in vm and 'fun removeFlashQueueDraftItem' in vm
assert 'testImplementation(\'junit:junit:4.13.2\')' in (root/'app/build.gradle').read_text()
# Stable, reviewed Home replacement; all other original page/protocol hashes stay guarded.
home_path = root/'app/src/main/res/layout/page_home.xml'
raw_home = home_path.read_bytes()
expected_home_blob = '575c48b6d74a9d0b86fb6a60bd1180847d05c06c'
assert hashlib.sha1(b'blob ' + str(len(raw_home)).encode() + b'\0' + raw_home).hexdigest() == expected_home_blob, 'Unexpected Home layout change'
from xml.etree import ElementTree as ET
home = ET.parse(home_path).getroot()
a = '{http://schemas.android.com/apk/res/android}'
home_ids = [node.attrib.get(a+'id','') for node in home.iter()]
for expected in ['tvHomeCodename','tvDeviceAndroidValue','btnHomeAdvancedToggle','homeAdvancedInfo','btnHomeSpecsToggle','homeModelSpecs']:
    assert home_ids.count('@+id/'+expected) == 1, expected
activity = (root/'app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt').read_text()
assert 'initializeOperationCenterDialog()' in activity and 'parent.removeView(cardOperationCenter)' in activity
assert 'val cancelButton = cardOperationCenter.findViewById' in activity
assert 'switchTab("home")' not in activity.split('private fun openOperationCenter()')[1].split('private fun requestOperationCancelFromUi()')[0]
flash_path = root/'app/src/main/res/layout/page_fastboot.xml'
raw_flash = flash_path.read_bytes()
expected_flash_blob = '382822aeb5920f9f73910d0b04e1a55011e4bc05'
# REV45 makes exactly four non-personal text inputs opt out of Autofill.
# Strip ONLY those reviewed attributes before comparing against the original
# protected REV7 layout; every other layout change still fails integrity.
reviewed_fields = (b'edQuickManualPartition', b'edGetvarKey',
                   b'edToolPartition', b'edToolLogicalPartition')
normalized_lines = raw_flash.splitlines(keepends=True)
for field in reviewed_fields:
    matches = [i for i, line in enumerate(normalized_lines)
               if b'<EditText android:id="@+id/' + field + b'"' in line]
    assert len(matches) == 1, 'Missing or duplicated REV45 Autofill field: ' + field.decode()
    index = matches[0]
    attr = b' android:importantForAutofill="no"'
    assert normalized_lines[index].count(attr) == 1, 'Unexpected REV45 Autofill configuration'
    normalized_lines[index] = normalized_lines[index].replace(attr, b'', 1)
original_flash = b''.join(normalized_lines)
assert hashlib.sha1(b'blob ' + str(len(original_flash)).encode() + b'\0' + original_flash).hexdigest() == expected_flash_blob, 'Unexpected Fastboot layout change'
flash = ET.parse(flash_path).getroot()
flash_ids = [node.attrib.get(a+'id','') for node in flash.iter()]
for key in ['fastbootQuickSection','fastbootMassSection','fastbootToolsSection','btnQuickExecute','btnMassExecute','toolBlockInfo','toolBlockPartitions','toolBlockSlots','toolBlockDynamic']:
    assert flash_ids.count('@+id/'+key) == 1, key
assert 'setupFastbootWorkflowUi()' in activity and 'viewModel.executeFlashQueueDraft()' in activity
assert 'viewModel.setActiveSlotAndVerify("a")' in activity
assert 'fun setActiveSlotAndVerify(slot: String)' in vm and 'getVar("current-slot")' in vm
print('PASS REV7: queue order, duplicate replacement, no silent 32-item truncation, moving, removal')
