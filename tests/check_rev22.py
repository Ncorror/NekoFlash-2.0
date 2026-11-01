#!/usr/bin/env python3
"""Package-owned mkfs wiring; runtime/ELF tests check the delivered artifact."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev21.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
formatter = (java / 'FastbootFilesystemFormatter.kt').read_text()
vm = (java / 'DeviceViewModel.kt').read_text()
assert 'discoverTools' not in formatter
assert 'MKE2FS_CONFIG' in formatter and 'root_owner=0:0' in formatter
assert 'profile?.delete()' in formatter
assert 'BundledFilesystemTools.fromDirectory(File(getApplication<Application>().applicationInfo.nativeLibraryDir))' in vm
gradle = (root / 'app/build.gradle').read_text()
assert 'useLegacyPackaging = true' in gradle
assert "tasks.named('preBuild').configure { dependsOn(buildFilesystemTools) }" in gradle
for name in ('build.yml', 'release.yml'):
    workflow = (root / '.github/workflows' / name).read_text()
    assert 'filesystem-tools-source.tar.gz' in workflow
    assert 'tests/check_filesystem_apk.py' in workflow
print('PASS REV22: APK-owned mkfs, explicit profile, four-ABI delivery/source and runtime CI')
