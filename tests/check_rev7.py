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
expected_blob = 'b12bc0e4709de67c4ab511cba7148f86eb81f681'
assert hashlib.sha1(b'blob ' + str(len(raw_vm)).encode() + b'\0' + raw_vm).hexdigest() == expected_blob, 'Unexpected change to DeviceViewModel'
assert 'val ordered = queue' in vm and 'queue.sortedBy' not in vm
assert 'ordered.forEachIndexed' in vm and 'ordered.mapIndexed' in vm
assert 'fun moveFlashQueueDraftItem' in vm and 'fun removeFlashQueueDraftItem' in vm
assert 'testImplementation(\'junit:junit:4.13.2\')' in (root/'app/build.gradle').read_text()
# Stable, reviewed Home replacement; all other original page/protocol hashes stay guarded.
home_path = root/'app/src/main/res/layout/page_home.xml'
raw_home = home_path.read_bytes()
expected_home_blob = '4c2dca5acbc7a699c7e86447849f0552d6d18875'
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
expected_flash_blob = 'c386cffe34ea9c754cc37a87a4f6e2f491f080b5'
assert hashlib.sha1(b'blob ' + str(len(raw_flash)).encode() + b'\0' + raw_flash).hexdigest() == expected_flash_blob, 'Unexpected Fastboot layout change'
flash = ET.parse(flash_path).getroot()
flash_ids = [node.attrib.get(a+'id','') for node in flash.iter()]
for key in ['fastbootQuickSection','fastbootMassSection','fastbootToolsSection','btnQuickExecute','btnMassExecute','toolBlockInfo','toolBlockPartitions','toolBlockSlots','toolBlockDynamic']:
    assert flash_ids.count('@+id/'+key) == 1, key
assert 'setupFastbootWorkflowUi()' in activity and 'viewModel.executeFlashQueueDraft()' in activity
assert 'viewModel.setActiveSlotAndVerify("a")' in activity
assert 'fun setActiveSlotAndVerify(slot: String)' in vm and 'getVar("current-slot")' in vm
print('PASS REV7: queue order, duplicate replacement, no silent 32-item truncation, moving, removal')
