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

# The source file is pinned as a complete Git blob. A reviewed terminal
# extension is accepted below only with explicit admission, cancellation,
# workspace isolation, and confirmation invariants asserted.
vm_blob = git_blob_sha(raw_vm)


# REV31 allows ONE exact raw-shell evidence directory argument in the ADB
# constructor. Rebuild the old blob in memory; unrelated orchestration edits
# and changes elsewhere in DeviceViewModel must continue to fail this guard.
# REV51 reviews the complete DeviceViewModel snapshot with lossless
# persistent event logging. Pin the exact Git blob; unknown changes still fail.
rev51_lossless_vm_blob = '1c31cf4e1b024e3a602aa2445f8d8ebfc82ad9f3'
# REV52 review: only admission before FGS and Fastboot-plan mode check
# were added to this exact orchestrator blob. Unknown changes still fail.
rev52_admission_vm_blob = 'a0e2f3c6ef4271a124bcf80a7280d55edbb9de3b'
rev52_explicit_transport_vm_blob = '25c0302750d40cf713833339dc492289bbec3aae'
# REV55 reviews a narrow Sideload Recovery-transition postcondition with
# strict same-device and same-attempt binding. Pin the COMPLETE source blob.
rev55_recovery_transition_vm_blob = '34c22e904c731594084ccd725ffeb6516f235aa7'
# Narrow reviewed 11 Nov addition: non-destructive, connection-scoped Android
# release metadata fetch. Complete new blob remains pinned here.
rev56_android_release_vm_blob = '1c2f535d8a4b99baf9e30df180e5e75e1dd2eb36'
# REV57: preserve near-complete Sideload verification when Recovery USB detach
# races with the generic transport-shutdown cancellation path.
rev57_sideload_verdict_vm_blob = '9029971de3792a2a4ef04760e29b20fa8c5f412b'
# Mi Unlock diagnostics add connection-scoped error reporting to this exact blob.
mi_unlock_diagnostics_vm_blob = 'f408c6b03977b508de737183feaae869722a50bb'
# REV59: host-side stdout redirect; pin the entire reviewed ViewModel blob.
rev59_shell_redirect_vm_blob = '90bb79853e6221d20b9006477175c4ed35fa2e33'
# Local terminal commands share admission with USB and workspace operations.
local_terminal_vm_blob = 'f729ec96acd1b48a069df24390acf8dd8cde67e9'
# Local copies and overwrite confirmation survive an Activity configuration change.
retained_local_terminal_vm_blob = '5115ac3dbe1ea7c40a7122de33d921df10bcf082'
# Multi-file transfers, device file listing, Wireless debugging, and batch Mass Flash selection.
adb_workflows_vm_blob = '9a8a81aa6b4aaf9cd106f752195fd591455439c4'
# Queue slot selection and ordered physical flash targets.
mass_flash_slots_vm_blob = 'dbdd17dcec9508eb3831e0608d5fb8901f1c3699'
# EXEC-TEST welcome banner plus ViewModel-owned, confirmation-gated rm support.
merged_local_terminal_vm_blob = 'dfe58474085a5cb4678b56176d5b734007967f07'
# PR #108 review: propagate nonzero exit status without claiming false success.
reviewed_terminal_exit_vm_blob = 'f763572f3a83f76f26a13d9194d699fcaea0ad47'
# Combined Mass Flash and ADB workflows with the terminal changes on main.
integrated_workflows_vm_blob = 'c308da05c4846f6f308d6035e656fa9af726186e'
# Live slot resolution, Mass Flash plan/report, and ADB session and browser follow-ups.
workflow_followups_vm_blob = 'fdb0b9dbf32f44bdef2fc037aaa2abb221df9a3f'
# Reviewed merge of the workflow-followups source with the EXEC-TEST terminal/ROM-script source.
merged_workflows_terminal_vm_blob = 'ce79e1734ceac7e1dd6a00a1b0a7e4acdfe81658'
# Reviewed fix: the localized no-Fastboot string already contains its error label.
localized_transport_error_vm_blob = '0425e625bbb42988a046c274aca2d28ab54b2f19'
reviewed_kotlin_warning_cleanup_vm_blob = '928a7fc2118ac8962fbd7d8700cc56d61c4b41ff'
workflow_followups_vm_blobs = (workflow_followups_vm_blob, merged_workflows_terminal_vm_blob, localized_transport_error_vm_blob, reviewed_kotlin_warning_cleanup_vm_blob)
mass_flash_slots_vm_blobs = (mass_flash_slots_vm_blob, integrated_workflows_vm_blob)
terminal_exit_vm_blobs = (reviewed_terminal_exit_vm_blob, integrated_workflows_vm_blob, workflow_followups_vm_blob)
shell_redirect_vm_blobs = (
    rev59_shell_redirect_vm_blob, local_terminal_vm_blob, retained_local_terminal_vm_blob,
    adb_workflows_vm_blob, mass_flash_slots_vm_blob, merged_local_terminal_vm_blob, *terminal_exit_vm_blobs,
    merged_workflows_terminal_vm_blob, localized_transport_error_vm_blob, reviewed_kotlin_warning_cleanup_vm_blob,
)
adb_operation_count = {
    adb_workflows_vm_blob: 8, mass_flash_slots_vm_blob: 8, integrated_workflows_vm_blob: 8,
    workflow_followups_vm_blob: 9, merged_workflows_terminal_vm_blob: 9,
    localized_transport_error_vm_blob: 9,
    reviewed_kotlin_warning_cleanup_vm_blob: 9,
}.get(vm_blob, 7)
# Mi Unlock terminal OKAY receipt; durable success before USB detach, exact reviewed snapshot.
mi_unlock_terminal_vm_blob = '50d14ede8d84159c93835ee82d29a9b930869ca2'
# Reviewed universal terminal navigation, private HOME, system shell, persisted binary mode, and foreground Fastboot script bridge.
terminal_shell_v2_vm_blob = '517359a09f5ca61de9f2e2cf7d226a51b6ad656c'
# Build fix: use the AndroidViewModel application context for the vbmeta cache.
terminal_shell_v2_cache_context_vm_blob = 'ef5bf1292287c16a5570d58e5fcf09ffe465c724'
terminal_shell_v2_vm_blobs = (
    terminal_shell_v2_vm_blob, terminal_shell_v2_cache_context_vm_blob,
    merged_workflows_terminal_vm_blob, localized_transport_error_vm_blob, reviewed_kotlin_warning_cleanup_vm_blob,
)
if vm_blob in (rev51_lossless_vm_blob, rev52_admission_vm_blob, rev52_explicit_transport_vm_blob, rev55_recovery_transition_vm_blob, rev56_android_release_vm_blob, rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs, *terminal_shell_v2_vm_blobs):
    journal_body = vm.split('private fun logInternal(')[1].split('fun clearLog()')[0]
    assert 'appendCompactMessageLocked(message, declaredLevel)' in journal_body
    assert 'if (duplicate)' not in journal_body
    assert 'recordDuplicateSuppressed()' not in journal_body
    if vm_blob in (rev52_admission_vm_blob, rev52_explicit_transport_vm_blob, rev55_recovery_transition_vm_blob, rev56_android_release_vm_blob, rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs, *terminal_shell_v2_vm_blobs):
        admission = vm.split('private fun startOperation(')[1].split('val gen = operationGeneration.incrementAndGet()')[0]
        assert 'UsbOperationAdmission.allowed(' in admission
        assert 'FlashOperationService.submit' not in admission
        assert 'No connected ADB/Fastboot device' in admission
        assert 'requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT' in vm
        if vm_blob in (rev52_explicit_transport_vm_blob, rev55_recovery_transition_vm_blob, rev56_android_release_vm_blob, rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs):
            expected_fastboot_calls = 13 if vm_blob in terminal_shell_v2_vm_blobs else 12
            assert vm.count('requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT') == expected_fastboot_calls
            assert vm.count('requiredTransport = UsbOperationAdmission.RequiredTransport.ADB') == adb_operation_count
            assert vm.count('startOperation(') == expected_fastboot_calls + adb_operation_count + 1
    if vm_blob in (rev55_recovery_transition_vm_blob, rev56_android_release_vm_blob, rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs):
        policy_path = root/'app/src/main/java/ru/forum/adbfastboottool/SideloadRecoveryTransitionPolicy.kt'
        policy = policy_path.read_text()
        # Ensure the reviewed recovery switch cannot be promoted into a false
        # install SUCCESS, and is bound to the completed same-USB attempt.
        assert vm.count('observePendingSideloadRecoveryTransition(proto)') == 1
        assert 'sameUsbSerial = pending.serial != null && pending.serial == currentUsbSerial()' in vm
        assert 'OperationOutcomeKind.VERIFY_PENDING' in vm.split('private fun observePendingSideloadRecoveryTransition(')[1].split('private fun verifyPendingSideloadIfReady(')[0]
        assert 'before.vendorId == after.vendorId' in policy
        assert 'before.deviceCodename.equals(after.deviceCodename, true)' in policy
        assert 'before.productName.equals(after.productName, true)' in policy
        assert 'transferFinished' in policy
        assert 'fun sideloadZip(' in (root/'app/src/main/java/ru/forum/adbfastboottool/AdbProtocol.kt').read_text()
    if vm_blob in (rev56_android_release_vm_blob, rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs):
        probe = 'proto.captureShellCommand("getprop ro.build.version.release"'
        assert vm.count(probe) == 1
        if vm_blob in workflow_followups_vm_blobs:
            connect = vm.split('private fun connectAdbLocked(')[1].split('private suspend fun awaitNativeUsbfsIdle')[0]
            assert connect.index(probe) < connect.index('_connectionState.postValue(ConnectionState.ADB)')
        assert 'proto.peerMode == AdbProtocol.PeerMode.DEVICE' in vm
        assert 'generation == connectionGeneration.get()' in vm
        assert vm.count('_adbAndroidRelease.postValue(null)') >= 3
        assert '_adbAndroidRelease.postValue(release)' in vm
    if vm_blob in (rev57_sideload_verdict_vm_blob, mi_unlock_diagnostics_vm_blob, mi_unlock_terminal_vm_blob, *shell_redirect_vm_blobs, *terminal_shell_v2_vm_blobs):
        finish = vm.split('private fun finishServiceOperation(')[1].split('private fun releaseOperationWakeLock(')[0]
        assert 'reported is OperationOutcome.VerifyPending -> reported' in finish
        assert finish.index('reported is OperationOutcome.VerifyPending -> reported') < finish.index('cause is CancellationException')
    if vm_blob in shell_redirect_vm_blobs or vm_blob in terminal_shell_v2_vm_blobs:
        assert 'fun runAdbShell(command: String, redirect: AdbProtocol.ShellRedirect? = null)' in vm
        assert vm.count('AdbProtocol.ShellRedirect') == 1
        assert vm.count('redirect = redirect') == 1
        assert vm.count('requiredTransport = UsbOperationAdmission.RequiredTransport.ADB') == adb_operation_count
    if vm_blob in terminal_shell_v2_vm_blobs:
        local_shell = (root/'app/src/main/java/ru/forum/adbfastboottool/LocalShell.kt').read_text()
        assert 'initialHome = localTerminalHome' in vm
        assert 'initialDeviceBinariesEnabled = terminalPreferences.getBoolean("device_binaries_enabled", false)' in vm
        assert 'putBoolean("device_binaries_enabled", enabled)' in vm
        assert 'fun configureHome(homeDirectory: File, publicWorkspace: File?)' in local_shell
        assert 'Files.createSymbolicLink(alias.toPath(), shared.toPath())' in local_shell
        assert 'internal fun submitLocalTerminalScriptCommand(' in vm
        assert 'prepareFastbootSession = false' in vm
        assert 'requiredTransport = UsbOperationAdmission.RequiredTransport.FASTBOOT' in vm
        assert 'localScriptExecutionActive' in vm and 'cancelActiveOperation()' in vm
        assert 'private val localTerminalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)' in vm
        assert 'confirmDelete = ::requestLocalDelete' in vm
        assert 'TerminalWelcomeMessage.build(application, deviceBinariesEnabled = localShell.isDeviceBinariesEnabled)' in vm
        assert 'FastbootVbmeta.createPatchedCopy(' in vm
        assert 'values[index] == "--disable-verity"' in vm
        assert 'values[index] == "--disable-verification"' in vm
        assert 'FastbootShellBridge' in vm
        start_operation = vm.split('private fun startOperation(')[1].split('/** Persist the received terminal result')[0]
        assert start_operation.count('): ServiceOwnedOperation? {') == 1
        assert '): Boolean {' not in start_operation
        assert 'return false\n                return null' not in start_operation
        assert 'return true\n            return task' not in start_operation
        assert start_operation.count('return task') == 1
        if vm_blob in (localized_transport_error_vm_blob, reviewed_kotlin_warning_cleanup_vm_blob):
            assert 'log(DiagnosticLogPolicy.Level.ERROR, "$reason Command was not sent.")' in start_operation
            assert 'log(DiagnosticLogPolicy.Level.ERROR, "ERROR: $reason Command was not sent.")' not in start_operation
        script_submit = vm.split('internal fun submitLocalTerminalScriptCommand(')[1].split('private suspend fun requestLocalOverwrite(')[0]
        assert script_submit.index('val started = startOperation(') < script_submit.index('if (!started) localScriptServiceOwned = false')
        assert script_submit.index(') {') < script_submit.index('if (!started) localScriptServiceOwned = false')
        assert '} != null\n            if (!started) localScriptServiceOwned = false' in script_submit
    if vm_blob in (merged_local_terminal_vm_blob, reviewed_terminal_exit_vm_blob):
        assert vm.count('requiredTransport = UsbOperationAdmission.RequiredTransport.ADB') == adb_operation_count
    if vm_blob in (merged_local_terminal_vm_blob, *terminal_exit_vm_blobs):
        assert 'internal val localShell = LocalShell { StatFs(it.absolutePath).availableBytes }' in vm
        assert 'TerminalWelcomeMessage.build(application).forEach(::append)' in vm
        assert 'private val localTerminalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)' in vm
        assert 'private suspend fun requestLocalDelete(file: File): Boolean' in vm
        assert 'confirmDelete = ::requestLocalDelete' in vm
        local_admission = vm.split('internal fun launchLocalTerminalCommand(')[1].split('internal fun cancelLocalTerminalCommand()')[0]
        assert 'localTerminalOperation != null' in local_admission
        assert 'hasUnfinishedUsbOperation()' in local_admission
        assert 'workspaceImport.isBusy' in local_admission
        assert 'internal fun answerLocalDelete(request: LocalDeleteRequest, delete: Boolean)' in vm
        if vm_blob in terminal_exit_vm_blobs:
            assert 'when (output.exitCode)' in vm
            assert '0 -> logLocalTerminal("✓ ' in vm
            assert '130 -> logLocalTerminal("! ' in vm
            assert 'R.string.local_terminal_exit_code' in vm
    if vm_blob == reviewed_kotlin_warning_cleanup_vm_blob:
        assert 'setActiveOption!!' not in vm
        assert 'current?.outcome != OperationOutcomeKind.VERIFY_PENDING' not in vm
elif vm_blob != expected_blob:
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
assert 'queue.sortedBy' not in vm and 'ordered.forEachIndexed' in vm
if vm_blob in workflow_followups_vm_blobs:
    assert 'val ordered = draft.items' in vm and 'MassFlashPlan.resolve(draft)' in vm
    assert 'resolved.forEachIndexed' in vm and 'publishQueueSteps(resolved)' in vm
    assert 'proto.resolveSlotPartitionTargets(partition, slot)' in vm
elif vm_blob in mass_flash_slots_vm_blobs:
    assert 'val ordered = items.filter' in vm and 'ordered.flatMapIndexed' in vm
    assert 'resolved.forEachIndexed' in vm and 'publishQueueSteps(resolved)' in vm
    assert 'resolveTargets(item.partition, slot, proto::resolveSlotPartitionTargets)' in vm
else:
    assert 'val ordered = queue' in vm and 'ordered.mapIndexed' in vm
if vm_blob in (merged_workflows_terminal_vm_blob, localized_transport_error_vm_blob, reviewed_kotlin_warning_cleanup_vm_blob):
    assert 'when (output.exitCode)' in vm
    assert '0 -> logLocalTerminal("✓ ' in vm
    assert '130 -> logLocalTerminal("! ' in vm
    assert 'R.string.local_terminal_exit_code' in vm
assert 'fun moveFlashQueueDraftItem' in vm and 'fun removeFlashQueueDraftItem' in vm
assert 'testImplementation(\'junit:junit:4.13.2\')' in (root/'app/build.gradle').read_text()
# Stable, reviewed Home replacement; all other original page/protocol hashes stay guarded.
home_path = root/'app/src/main/res/layout/page_home.xml'
raw_home = home_path.read_bytes()
# User-approved Home ADB shortcuts replace the previous facts-only layout.
expected_home_blob = '345a37db2747b650228b7d20814a3906bd3637ad'
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
mass_flash_slots_layout_blob = '3ce51efd1195fe07fbaf9421d958ea8336a5283e'
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
# REV48 hides only the Tools tab; all existing controls/handlers stay intact.
for i, line in enumerate(normalized_lines):
    if b'android:id="@+id/btnFastbootSectionTools"' in line:
        assert line.count(b' android:visibility="gone"') == 1
        normalized_lines[i] = line.replace(b' android:visibility="gone"', b'', 1)
original_flash = b''.join(normalized_lines)
# The approved Mass Flash selector now stays above the scrolling queue.
# Keep the full new file pinned while retaining normalized older snapshots.
approved_sticky_slot_flash_blob = 'cd07dd0ab9b6efffe2bb6a8b4ecc239a800581cb'
assert git_blob_sha(original_flash) in (expected_flash_blob, mass_flash_slots_layout_blob) or (
    git_blob_sha(raw_flash) == approved_sticky_slot_flash_blob
), 'Unexpected Fastboot layout change'
flash = ET.parse(flash_path).getroot()
flash_ids = [node.attrib.get(a+'id','') for node in flash.iter()]
for key in ['fastbootQuickSection','fastbootMassSection','fastbootToolsSection','btnQuickExecute','btnMassExecute','toolBlockInfo','toolBlockPartitions','toolBlockSlots','toolBlockDynamic']:
    assert flash_ids.count('@+id/'+key) == 1, key
assert 'setupFastbootWorkflowUi()' in activity and 'viewModel.executeFlashQueueDraft()' in activity
assert 'viewModel.setActiveSlotAndVerify("a")' in activity
assert 'fun setActiveSlotAndVerify(slot: String)' in vm and 'getVar("current-slot")' in vm
print('PASS REV7: queue order, duplicate replacement, no silent 32-item truncation, moving, removal')
