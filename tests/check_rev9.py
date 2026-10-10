#!/usr/bin/env python3
"""REV9 readiness contracts: no false Recovery verdict, unchanged USB protocols."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev8.py')], cwd=root, check=True)
vm = (root/'app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt').read_text()
main = (root/'app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt').read_text()
ci = (root/'.github/workflows/build.yml').read_text()

assert 'private fun publishSideloadRecoveryVerdict(' in vm
result_block = vm.split('private fun verifyPendingSideloadIfReady(')[1].split('private fun verifyPendingUnlockIfReady(')[0]
assert 'RecoveryInstallVerifier.Verdict.SUCCESS' in result_block
assert 'RecoveryInstallVerifier.Verdict.FAILED' in result_block
assert 'RecoveryInstallVerifier.Verdict.UNKNOWN' in result_block
assert result_block.count('publishSideloadRecoveryVerdict(') == 2
assert 'OperationOutcomeKind.SUCCESS' in result_block
assert 'OperationOutcomeKind.FAILED' in result_block
unknown = result_block.split('RecoveryInstallVerifier.Verdict.UNKNOWN ->')[1]
assert 'publishSideloadRecoveryVerdict(' not in unknown, 'UNKNOWN must not be finalized as success'
assert 'current.outcome == OperationOutcomeKind.VERIFY_PENDING' in vm
assert '_operationActive.value == true' in vm
assert 'val lockStateVerified = fastbootReady' in main
assert 'val canRunUnlock = fastbootReady && lockStateVerified && !operationActive' in main
assert 'Kotlin JVM queue and diagnostic regression tests' in ci
assert ':app:testDebugUnitTest' in ci
assert (root/'docs/testing/REV9-BIG-DEVICE-TEST-RU.md').is_file()
print('PASS REV9: Recovery verdict gated, UNKNOWN preserved, Unlock verified, JVM tests wired')
