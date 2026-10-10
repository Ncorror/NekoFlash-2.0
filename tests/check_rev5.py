#!/usr/bin/env python3
"""Offline integrity and screen contract checks for the NekoFlash 2.0 REV5 starter."""
import hashlib
import json
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / 'docs/source-audit/LEGACY-SOURCE-SHA256.json'
AND = '{http://schemas.android.com/apk/res/android}'


def require(cond, message):
    if not cond:
        raise AssertionError(message)


def run():
    manifest = json.loads(BASELINE.read_text(encoding='utf-8'))['file_sha256']
    base = ROOT / 'app/src/main'
    for rel in [
        'app/src/main/java/ru/forum/adbfastboottool/AdbProtocol.kt',
        'app/src/main/java/ru/forum/adbfastboottool/FastbootProtocol.kt',
        # REV7 changes only queue ordering in DeviceViewModel; see check_rev7.py.
        'app/src/main/java/ru/forum/adbfastboottool/MiUnlockClient.kt',
        'app/src/main/java/ru/forum/adbfastboottool/MiAccountClient.kt',
        'app/src/main/java/ru/forum/adbfastboottool/TabController.kt',
        'app/src/main/cpp/native_usbfs.cpp',
        # REV7 Fastboot screen is pinned by check_rev7.py (transport guard unaffected).
        # REV7 Home informational-only screen is now guarded by check_rev7.py.
    ]:
        require(hashlib.sha256((ROOT / rel).read_bytes()).hexdigest() == manifest[rel], 'Changed guarded original: ' + rel)

    # Parse every unchanged and changed Android XML resource, not just the new page.
    xmls = list(base.rglob('*.xml'))
    for file in xmls:
        ET.parse(file)
    page = ET.parse(base / 'res/layout/page_adb.xml').getroot()
    texts = list(page.iter())
    ids = [el.attrib.get(AND + 'id', '') for el in texts]
    buttons = [el for el in texts if el.tag.endswith('MaterialButton')]
    require(len(buttons) == 2, 'Sideload must show exactly two buttons')
    for value in ['containerAdb', 'btnAdbSideload', 'btnSideloadImport', 'tvSideloadSelectedZip']:
        require(ids.count('@+id/' + value) == 1, 'Required UI ID not unique: ' + value)
    for res in ['values', 'values-ru']:
        strings = ET.parse(base / ('res/' + res + '/strings.xml')).getroot()
        found = [s.attrib['name'] for s in strings.findall('string')]
        for key in ['layout_sideload_page_title','layout_sideload_no_file','layout_sideload_selected_file','layout_sideload_verdict_note']:
            require(found.count(key)==1, f'String {key} missing/duplicated in {res}')
    main = (base / 'java/ru/forum/adbfastboottool/MainActivity.kt').read_text(encoding='utf-8')
    require('R.id.tvSideloadSelectedZip' in main, 'Missing chosen-file UI binding')
    require('viewModel.runSideload(file)' in main, 'Original Sideload operation disconnected')
    require('R.id.btnSideloadImport).setOnClickListener { startImportFilePicker() }' in main, 'Import behavior changed')
    nav = (base / 'res/layout/activity_main.xml').read_text(encoding='utf-8')
    for tab in ['tabHome','tabFastboot','tabAdb','tabUnlock','tabSettings']:
        require(nav.count('android:id="@+id/' + tab + '"') == 1, 'Missing fifth-tab shell ID: ' + tab)
    require('Sideload' in (base/'res/values-ru/strings.xml').read_text(encoding='utf-8'), 'Sideload tab label missing')
    print(f'PASS: {len(xmls)} XML resources parsed; Sideload REV3 2-button contract; RU/EN; original transport hashes; 5 tabs')

if __name__ == '__main__':
    run()
