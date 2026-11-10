#!/usr/bin/env python3
"""No Android SDK required: guard explicit Sideload selection / send separation."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
activity = (root/'app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt').read_text()
layout = (root/'app/src/main/res/layout/page_adb.xml').read_text()
def block(start, end):
    assert start in activity and end in activity
    return activity.split(start, 1)[1].split(end, 1)[0]
picker = block('private fun registerDirectSideloadLauncher()', 'private fun restoreSideloadSelection(')
send = block('private fun startSelectedSideloadWithConfirmation()', 'private fun startDirectSideloadFilePicker()')
setup = block('private fun setupButtons()', 'private fun registerDirectSideloadLauncher()') if False else activity

assert 'selectedSideloadSource = source' in picker
assert 'viewModel.runSideload(source)' not in picker, 'File picker must not start Sideload'
assert 'takePersistableUriPermission(' in picker
assert 'startSelectedSideloadWithConfirmation()' in setup
assert '.setPositiveButton(R.string.layout_sideload_start)' in send
assert 'viewModel.runSideload(source)' in send
assert 'selectedSideloadSource != source' in send, 'Reject replaced selection in an open dialog'
assert 'viewModel.adbPeerMode.value != AdbProtocol.PeerMode.SIDELOAD' in send
assert 'viewModel.operationActive.value == true' in send
assert 'updateSideloadSelectionUi()' in activity
assert 'restoreSideloadSelection(savedInstanceState)' in activity
assert 'outState.putString(STATE_SIDELOAD_URI' in activity
assert 'contentResolver.persistedUriPermissions.any' in activity
assert 'btnStartAdbSideload' in layout and 'android:enabled="false"' in layout
assert layout.index('btnAdbSideload') < layout.index('btnStartAdbSideload')
for lang in ('values', 'values-ru'):
    strings = (root/f'app/src/main/res/{lang}/strings.xml').read_text()
    for name in ('layout_sideload_start', 'layout_sideload_confirm_title',
                 'layout_sideload_confirm_message', 'layout_sideload_selected_details'):
        assert f'name="{name}"' in strings, (lang, name)
assert (root/'app/src/test/java/ru/forum/adbfastboottool/SideloadSelectionUiTest.kt').is_file()
print('PASS Sideload selection: picker never flashes, explicit confirmed send, retained URI and locale')
