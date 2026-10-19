#!/usr/bin/env python3
"""REV16: Fastboot single-download size policy and explicit sparse-option behavior."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev15.py')], cwd=root, check=True)

jvm = root/'app/src/main/java/ru/forum/adbfastboottool'
fastboot = (jvm/'FastbootProtocol.kt').read_text()
policy = (jvm/'FastbootDownloadSizePolicy.kt').read_text()
main = (jvm/'MainActivity.kt').read_text()

# Actual protocol limits are explicit, not disguised as arbitrary GUI caps.
assert 'WIRE_MAX_BYTES: Long = 0xFFFF_FFFFL' in policy
assert 'Reason.WIRE_LIMIT' in policy and 'Reason.DEVICE_LIMIT' in policy
assert 'deviceMaxBytes?.takeIf { it > 0L }' in policy
assert 'FastbootDownloadSizePolicy.validate(' in fastboot
assert 'effectiveMaxDownloadSizeBytes()' in fastboot
assert 'getVar("max-download-size")' in fastboot
assert 'cmdBytes.size > 64' in fastboot

# Device-reported max is checked before download DATA, for flash and download+run.
flash_validation = fastboot.split('private fun flashPartitionDetailedInternal(')[1].split('val hexSize =')[0]
assert 'FastbootDownloadSizePolicy.validate(' in flash_validation
download_run = fastboot.split('fun downloadAndRun(file: File, commandAfterDownload: String)')[1]
assert 'FastbootDownloadSizePolicy.validate(' in download_run.split('val hexSize =')[0]

# -S must never be silently accepted while sparse splitting is unimplemented.
assert 'Sparse splitting (-S) is not required' not in main
sparse = main.split('token == "-S" || lower == "--sparse-limit" ->')[1].split('token.startsWith("-S")')[0]
assert 'R.string.rev16_sparse_option_not_implemented' in sparse
assert 'return null' in sparse
assert (root/'app/src/test/java/ru/forum/adbfastboottool/FastbootDownloadSizePolicyTest.kt').is_file()
print('PASS REV16: device max-download preflight, 32-bit wire limit preserved, -S not silently ignored')
