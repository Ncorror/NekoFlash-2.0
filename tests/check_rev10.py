#!/usr/bin/env python3
"""REV10 screenshot regressions: permission action, readable navigation and terminal UI."""
from pathlib import Path
from xml.etree import ElementTree as ET
import hashlib
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root/'tests/check_rev9.py')], cwd=root, check=True)

res = root/'app/src/main/res'
wel = ET.parse(res/'layout/activity_welcome.xml').getroot()
main = ET.parse(res/'layout/activity_main.xml').getroot()
quick = ET.parse(res/'layout/page_fastboot.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'

def by_id(tree, name):
    hits = [x for x in tree.iter() if x.attrib.get(a+'id') == '@+id/'+name]
    assert len(hits) == 1, 'Missing or duplicated '+name
    return hits[0]

grant = by_id(wel,'tvStorageChip')
assert grant.attrib[a+'layout_width'] == 'match_parent'
assert grant.attrib[a+'clickable'] == 'true'
assert grant.attrib[a+'singleLine'] == 'true'
assert grant.attrib[a+'text'] == '@string/onboarding_storage_grant'
for key in ('tvNotificationsChip','tvBatteryChip','riskRow','tvWelcomeStatus'):
    assert by_id(wel,key).attrib[a+'visibility'] == 'gone', key

welcome_code = (root/'app/src/main/java/ru/forum/adbfastboottool/WelcomeActivity.kt').read_text()
assert 'tvStorageChip.setOnClickListener { openStoragePermissionSettings() }' in welcome_code
assert 'Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION' in welcome_code
assert 'PermissionGate.hasStorage(this)' in welcome_code
assert 'if (::checkbox.isInitialized) refreshGateState()' in welcome_code
assert 'R.string.onboarding_storage_granted' in welcome_code

terminal = by_id(main,'btnTerminalOpen')
assert terminal.tag.endswith('TextView'), 'MaterialButton min insets wrapped the terminal icon'
assert terminal.attrib[a+'singleLine'] == 'true'
assert terminal.attrib[a+'gravity'] == 'center'
assert by_id(main,'bottomNavigation').attrib[a+'layout_height'] == '62dp'
title = [x for x in by_id(main,'consoleHeader') if x.attrib.get(a+'text') == '@string/layout_console_title']
assert len(title) == 1 and title[0].attrib.get(a+'layout_weight') == '1'
for key in ('btnHistoryUp','btnHistoryDown'):
    assert by_id(main,key).attrib[a+'layout_width'] == '36dp'

quick_title = by_id(quick,'tvQuickHeading')
assert quick_title.attrib[a+'visibility'] == 'gone', 'Duplicate heading on small screens'
tab_path = root/'app/src/main/java/ru/forum/adbfastboottool/TabController.kt'
tab = tab_path.read_text()
assert 'button.alpha = 1.0f' in tab
assert 'if (selected) R.color.accent else R.color.text_secondary' in tab
raw_tab = tab_path.read_bytes()
assert hashlib.sha1(b'blob '+str(len(raw_tab)).encode()+b'\0'+raw_tab).hexdigest() == '361a03b255080465d65365df44509c788e7e466f'

for locale in ('values','values-ru'):
    names = {item.attrib['name']:item.text for item in ET.parse(res/locale/'strings.xml').getroot().findall('string')}
    for key in ('onboarding_storage_grant','onboarding_storage_granted','shell_usb_compact_idle','shell_usb_compact_active'):
        assert key in names, (locale,key)
    assert names['layout_console_title'] in ('TERMINAL','ТЕРМИНАЛ')
    assert names['shell_terminal_icon'] == '>_'

activity = (root/'app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt').read_text()
assert 'R.string.shell_usb_compact_idle' in activity
assert 'R.string.shell_usb_compact_active' in activity
print('PASS REV10: explicit storage action, nonblocking Welcome, compact USB, terminal and tab readability')
