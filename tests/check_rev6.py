#!/usr/bin/env python3
"""REV6 shell contracts, deliberately without an Android SDK/device dependency."""
from pathlib import Path
from xml.etree import ElementTree as ET
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
KOTLIN = ROOT / 'app/src/main/java/ru/forum/adbfastboottool'
A = '{http://schemas.android.com/apk/res/android}'

def check(condition, message):
    if not condition:
        raise AssertionError(message)

subprocess.run(['python3', str(ROOT/'tests/check_rev5.py')], cwd=ROOT, check=True)
main = ET.parse(RES/'layout/activity_main.xml').getroot()
content = main.find(f'.//*[@{A}id="@+id/mainContent"]')
check(content is not None, 'mainContent missing')
children = list(content)
ids = [x.attrib.get(A+'id','') for x in children]
check(ids.index('@+id/usbHeader') < ids.index('@+id/contentContainer'), 'USB not persistent at top')
check(ids.index('@+id/contentContainer') < ids.index('@+id/cardOperationStrip') < ids.index('@+id/bottomNavigation'), 'operation strip not above bottom navigation')
check(ids[-1] == '@+id/bottomNavigation', 'bottom navigation must be last in main vertical content')
all_ids = [x.attrib.get(A+'id') for x in main.iter() if A+'id' in x.attrib]
for key in ['usbPanel','tvStatus','tvOtgStatus','btnTerminalOpen','btnScan','btnCancel','bottomNavigation','consolePanel']:
    check(all_ids.count('@+id/'+key) == 1, f'{key} ID missing/duplicated')
nav = next(x for x in main.iter() if x.attrib.get(A+'id') == '@+id/bottomNavigation')
tab_ids = [x.attrib.get(A+'id') for x in nav.iter() if x.attrib.get(A+'id','').startswith('@+id/tab')]
check(tab_ids == ['@+id/tabHome','@+id/tabFastboot','@+id/tabAdb','@+id/tabUnlock','@+id/tabSettings'], 'Wrong bottom tab order')
console = next(x for x in main.iter() if x.attrib.get(A+'id')=='@+id/consolePanel')
check(console.attrib['{http://schemas.android.com/apk/res-auto}behavior_peekHeight'] == '0dp', 'console retains old peek')
check(next(x for x in main.iter() if x.attrib.get(A+'id')=='@+id/contentContainer').attrib[A+'paddingBottom'] == '0dp', 'console viewport offset retained')
controller = (KOTLIN/'ConsoleDockController.kt').read_text()
check('R.dimen.usb_panel_height' in controller and 'peekHeight = 0' in controller, 'terminal has wrong bounds')
check('behavior.isDraggable = false' in controller, 'fullscreen terminal could shrink via drag')
activity = (KOTLIN/'MainActivity.kt').read_text()
for expected in ['WindowCompat.setDecorFitsSystemWindows(window, true)', 'R.id.usbPanel).setOnClickListener { showUsbPanel() }','R.id.btnTerminalOpen).setOnClickListener', 'openConsole(requestCommandFocus = true)', 'private fun showUsbPanel()', 'usbManager.deviceList.values.toList()', 'scanForDevices()', 'viewModel.runSideload(file)']:
    check(expected in activity, 'Shell/legacy handler missing: '+expected)
for locale in ['values','values-ru']:
    resources = ET.parse(RES/locale/'strings.xml').getroot()
    keys = [x.attrib.get('name') for x in resources.findall('string')]
    for key in ['shell_usb_panel_desc','shell_terminal_open_desc','shell_usb_details_title','shell_usb_device_entry','shell_usb_refresh','shell_usb_diagnostics']:
        check(keys.count(key)==1,f'{locale}: missing or duplicate {key}')
gradle = (ROOT/'app/build.gradle').read_text()
check('applicationIdSuffix = ".dev"' in gradle, 'Debug builds must coexist with the original signed NekoFlash')
check('versionNameSuffix = "-rev6-dev"' in gradle, 'DEV version must be clearly identified')
print('PASS REV6: single USB header, full-screen terminal, bottom nav, operation strip, existing handlers, RU/EN')
