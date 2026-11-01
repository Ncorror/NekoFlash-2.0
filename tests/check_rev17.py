#!/usr/bin/env python3
"""REV17: never send a Fastboot DATA payload unless acknowledgement size matches."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(["python3", str(root / "tests/check_rev16.py")], cwd=root, check=True)

java = root / "app/src/main/java/ru/forum/adbfastboottool"
protocol = (java / "FastbootProtocol.kt").read_text()
policy = (java / "FastbootDataReplyPolicy.kt").read_text()

assert 'reply.length != 8' in policy
assert 'toLongOrNull(16)' in policy
assert 'Verdict.Mismatch' in policy and 'Verdict.Invalid' in policy
assert 'markSessionBroken("Invalid Fastboot DATA acknowledgement' in protocol
assert 'markSessionBroken(' in protocol
assert protocol.count('verifyDownloadDataReply(downloadPacket.payload, ') == 3

flash = protocol.split("private fun flashPartitionDetailedInternal(")[1]
assert 'verifyDownloadDataReply(downloadPacket.payload, imageSize)' in flash.split('transferDownloadPayload(')[0]

download = protocol.split("fun downloadAndRun(file: File, commandAfterDownload: String)")[1]
assert 'verifyDownloadDataReply(downloadPacket.payload, file.length())' in download.split('transferDownloadPayload(')[0]

unlock = protocol.split("fun stageAndOemUnlock(encryptDataFile: File)")[1]
assert 'FastbootDownloadSizePolicy.validate(' in unlock.split('writeCommand("download:')[0]
assert 'verifyDownloadDataReply(downloadPacket.payload, unlockBytes)' in unlock.split('transferDownloadPayload(')[0]

assert (root / "app/src/test/java/ru/forum/adbfastboottool/FastbootDataReplyPolicyTest.kt").is_file()
print("PASS REV17: strict DATA length/hex validation, three flows guarded before USB payload")
