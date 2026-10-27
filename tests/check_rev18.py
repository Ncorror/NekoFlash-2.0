#!/usr/bin/env python3
"""REV18 integration guards; behavior is covered by Kotlin JVM regression tests."""
from pathlib import Path
import subprocess
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
subprocess.run(["python3", str(root / "tests/check_rev17.py")], cwd=root, check=True)
java = root / "app/src/main/java/ru/forum/adbfastboottool"
adb = (java / "AdbProtocol.kt").read_text()
fastboot = (java / "FastbootProtocol.kt").read_text()
main = (java / "MainActivity.kt").read_text()
vm = (java / "DeviceViewModel.kt").read_text()
publication = (java / "FilePublication.kt").read_text()
assert "StandardCopyOption.ATOMIC_MOVE" in publication
assert "target.delete()" not in publication
assert "localFile.delete()" not in adb and "outputFile.delete()" not in fastboot
assert "AdbSyncSizePolicy.matchesLegacyStat(expectedSize, receivedBytes)" in adb
assert "AdbPullPathPolicy.parseListing" in adb and "find . -type f -print0" in adb
assert "coverage.acknowledge(blockNum)" in adb
assert "FastbootFetchPolicy.dataSize(dataPacket.payload, expectedChunkBytes)" in fastboot
assert 'val normalized = partition.trim().lowercase()' not in fastboot
assert "FilePublication.publishNew(staged, target)" in main
assert "target.delete()" not in main.split("private fun importFirmwareFile")[1].split("private fun ")[0]
assert 'to "reboot-fastboot"' in main and 'to "reboot:fastboot"' not in main
assert "runWorkspaceMaintenanceIfIdle" in main and "synchronized(operationLaunchLock)" in vm
assert "expectedSizeBytes = item.selection.expectedSizeBytes" in vm
assert "requireSize(opened.sizeBytes)" in vm
a = "{http://schemas.android.com/apk/res/android}"
layout = ET.parse(root / "app/src/main/res/layout/activity_main.xml").getroot()
for name in ("usbHeader", "bottomNavigation", "consoleHeader", "consoleInputBar"):
    view = next(v for v in layout.iter() if v.get(a + "id") == "@+id/" + name)
    assert view.get(a + "layout_height") == "wrap_content", name
for name in ("FilePublicationTest", "AdbPullPathPolicyTest", "TransferReceivePolicyTest", "SideloadCoverageTest"):
    assert (root / f"app/src/test/java/ru/forum/adbfastboottool/{name}.kt").is_file()
print("PASS REV18: safe file publication, receive integrity, literal targets, workspace guard, adaptive UI")
