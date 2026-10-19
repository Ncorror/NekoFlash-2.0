#!/usr/bin/env python3
"""REV14 full-audit cleanup contracts: no dead Welcome gate or silent metadata caps."""
from pathlib import Path
from xml.etree import ElementTree as ET
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev13.py')], cwd=root, check=True)

base = root/'app/src/main'
jvm = base/'java/ru/forum/adbfastboottool'
res = base/'res'
a = '{http://schemas.android.com/apk/res/android}'

# Welcome is now physically clean, not a pile of GONE compatibility controls.
welcome_xml = ET.parse(res/'layout/activity_welcome.xml').getroot()
ids = {x.attrib.get(a+'id') for x in welcome_xml.iter()}
assert '@+id/tvStorageChip' in ids and '@+id/btnPrimaryAction' in ids
for removed in ('checkboxAgree','riskRow','tvNotificationsChip','tvBatteryChip','tvWelcomeStatus'):
    assert '@+id/'+removed not in ids, 'Dead Welcome UI returned: '+removed

welcome = (jvm/'WelcomeActivity.kt').read_text()
gate = (jvm/'OnboardingGate.kt').read_text()
main = (jvm/'MainActivity.kt').read_text()
permission = (jvm/'PermissionGate.kt').read_text()
assert 'OnboardingGate.complete()' in welcome
assert 'OnboardingGate.canEnterMain()' in welcome
assert 'OnboardingGate.canEnterMain(this)' not in (welcome + main)
assert 'SharedPreferences' not in gate and 'risk_accepted' not in gate
assert 'requestNotificationPermissionOrSettings' not in welcome
assert 'openBatteryOptimizationSettings' not in welcome
assert 'Storage access is optional for entering the app' in permission

# No arbitrary queue metadata truncation. Actual Fastboot wire limits remain in transport.
draft = (jvm/'FlashOperationDraft.kt').read_text()
assert 'MAX_URI_LENGTH' not in draft
assert 'MAX_DISPLAY_NAME_LENGTH' not in draft
assert '.take(MAX_DISPLAY_NAME_LENGTH)' not in draft
assert 'Regex("^[A-Za-z0-9._:-]+$")' in draft
assert 'private val PARTITION_NAME_PATTERN = Regex("^[A-Za-z0-9._:-]+$")' in main
assert (root/'app/src/test/java/ru/forum/adbfastboottool/FlashOperationDraftPolicyTest.kt').is_file()

# Dead log segment cap removed; logs still never auto-prune.
store = (jvm/'DiagnosticLogStore.kt').read_text()
assert 'maxSegmentsPerStream' not in store
assert 'Automatic pruning destroys' in store
assert 'fun pruneDirectory(' in store

# Debug identity is stable and paths remain frozen.
gradle = (root/'app/build.gradle').read_text()
assert 'applicationId = "ru.forum.adbfastboottool"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert 'versionNameSuffix = "-dev"' in gradle
assert 'private val folderName = "NekoFlash"' in main

# Release tags must pass the same source and JVM gates before signing/publishing.
release = (root/'.github/workflows/release.yml').read_text()
release_gate = next(
    gate for gate in (
        'python3 tests/check_rev17.py',
        'python3 tests/check_rev16.py',
        'python3 tests/check_rev15.py',
        'python3 tests/check_rev14.py',
    )
    if gate in release
)
assert release_gate in release
assert 'python3 tests/check_i18n.py' in release
assert './gradlew :app:testDebugUnitTest' in release
assert release.index(release_gate) < release.index('Build signed Release APK')
assert release.index(':app:testDebugUnitTest') < release.index('Build signed Release APK')

# Protocol/safety limits remain transport-owned, not removed as "artificial".
fastboot = (jvm/'FastbootProtocol.kt').read_text()
assert 'cmdBytes.size > 64' in fastboot
size_policy = (jvm/'FastbootDownloadSizePolicy.kt').read_text()
assert 'WIRE_MAX_BYTES: Long = 0xFFFF_FFFFL' in size_policy
assert 'FastbootDownloadSizePolicy.validate(' in fastboot
assert 'markSessionBroken' in fastboot and 'NativeUsbfsBackend' in fastboot

print('PASS REV14: full-audit cleanup, clean Welcome, no silent queue caps, hardened release CI')
