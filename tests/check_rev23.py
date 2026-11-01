#!/usr/bin/env python3
"""Resparse integration contracts; byte reconstruction runs in JVM and Android CI."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev22.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
vm = (java / 'DeviceViewModel.kt').read_text()
image = (java / 'FastbootFlashImage.kt').read_text()
executor = (java / 'FastbootPlanExecutor.kt').read_text()
assert vm.count('flashImage(proto,') == 3, 'Terminal, Quick and queue must share resparse preparation'
assert 'proto.flashPartitionDetailed(partition, part)' in vm
assert 'proto.flashPartitionDetailed(partition, source, app.contentResolver)' in vm
# REV45 extracts the same conservative check into a shared helper so the
# lint exception applies only at the actual host-space preflight.
preflight = (java / 'StoragePreflight.kt').read_text()
assert 'require(hasImmediateSparseSpace(directory, image.largestPartBytes))' in vm
assert 'hasImmediateSparseSpace(directory, image.largestPartBytes)' in executor
assert 'directory.usableSpace >= requiredBytes' in preflight
assert 'StorageManager' in preflight and 'guaranteed allocation' in preflight
assert 'sparseProgress = null' in vm and 'rev23_image_part' in vm
assert 'FastbootFlashImage.prepare(it, max, sparseLimit, checkCancelled)' in executor
assert 'prepared.filter { it.payload != null }.distinctBy { it.payload to it.flash }' in executor
assert 'ByteArray(COPY_BYTES)' not in image  # buffers shrink for small extents
assert 'private const val COPY_BYTES = 1024 * 1024' in image
assert '.readBytes()' not in image and '.readText()' not in image
assert 'Math.addExact(wireBytes, used)' in image and 'crcEnabled = true' in image
assert 'Image structure changed after preflight' in image
assert 'override fun end(index: Long, bytes: Long, chunks: Long)' in image
assert image.index('out.fd.sync(); out.close()') < image.index('!send(')
for name in ('build.yml', 'release.yml'):
    assert 'tests/check_rev26.py' in (root / '.github/workflows' / name).read_text()
instrumentation = (root / 'app/src/androidTest/java/ru/forum/adbfastboottool/BundledFilesystemToolsTest.kt').read_text()
assert 'plan.forEachPart(' in instrumentation and '.parts.txt' in instrumentation
independent = (root / 'tests/verify_android_filesystems.py').read_text()
assert 'digest(recombined) == original_hash' in independent
assert 'overlay=True' in independent and 'fsck.f2fs' in independent
print('PASS REV23: shared bounded resparse, preflight, original packet transport, independent reconstruction/fsck')
