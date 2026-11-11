#!/usr/bin/env python3
"""Integration contracts; byte/plan behavior and real mkfs/fsck run in JVM CI."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev20.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
main = (java / 'MainActivity.kt').read_text()
vm = (java / 'DeviceViewModel.kt').read_text()
executor = (java / 'FastbootPlanExecutor.kt').read_text()
assert 'TerminalAction.RawFastboot("erase:userdata")' not in main
assert 'TerminalAction.FastbootPartitionCommand("format"' not in main
assert '"wipe-super" -> TerminalAction.RawFastboot' not in main
assert 'FastbootHostSyntax.format(' in main and 'FastbootHostSyntax.compose(' in main
plan = vm.split('internal fun runFastbootTerminalPlan(')[1].split('fun setActiveSlotAndVerify(')[0]
assert plan.count('startOperation(') == 1
assert 'expectedFastbootSerial = expectedSerial' in plan
assert 'context.ensureActive()' in plan and 'directory.deleteRecursively()' in plan
assert 'flashImage(proto, partition, FirmwareSource.WorkspaceFile(file), sparseLimit)' in plan
assert 'proto.flashPartitionDetailed(partition, source.file)' in vm
assert 'override fun downloadLimitBytes() = proto.hostDownloadLimitBytes()' in plan
assert 'internal fun hostDownloadLimitBytes(): Long? = transactionLock.withLock { effectiveMaxDownloadSizeBytes() }' in (java / 'FastbootProtocol.kt').read_text()
assert executor.index('val prepared =') < executor.index('prepared.forEachIndexed')
assert 'FastbootDownloadSizePolicy.validate(' in executor
assert 'listOf("userdata", "metadata", "cache")' in executor
assert 'if (target == "userdata" && type == "f2fs") options + "projid"' in executor
assert 'SuperEmptyImage.load(' in executor
assert 'ProcessBuilder(args)' in (java / 'FastbootFilesystemFormatter.kt').read_text()
assert 'output.fill(0, (aligned - offset) / blockSize)' in (java / 'AndroidSparseImage.kt').read_text()
layout = ET.parse(root / 'app/src/main/res/layout/page_fastboot.xml').getroot()
ids = [x.get('{http://schemas.android.com/apk/res/android}id') for x in layout.iter()]
assert ids.count('@+id/btnToolFormat') == 1
for name in ('AndroidSparseImageTest', 'SuperEmptyImageTest', 'FastbootPlanExecutorTest',
             'FastbootFilesystemFormatterTest', 'FastbootHostSyntaxTest', 'FastbootTerminalPlanTest'):
    assert (root / f'app/src/test/java/ru/forum/adbfastboottool/{name}.kt').is_file()
for workflow in ('build.yml', 'release.yml'):
    # Hosted Ubuntu source mirror selection moved into a shared helper. Keep
    # the same mandatory host filesystem packages in both release paths.
    content = (root / '.github/workflows' / workflow).read_text()
    assert 'bash scripts/ci-install-filesystem-deps.sh e2fsprogs f2fs-tools' in content
installer = (root / 'scripts/ci-install-filesystem-deps.sh').read_text()
assert 'sudo apt-get install -y --no-install-recommends' in installer
assert '"$@"' in installer and 'archive.ubuntu.com' in installer
print('PASS REV21: one serial-bound plan, real host format/wipe/super images, no fake wire aliases, existing transport retained')
