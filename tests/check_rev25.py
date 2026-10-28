#!/usr/bin/env python3
"""UI composition contracts; installed-app tests cover recycling and recreation."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
subprocess.run(['python3', str(root / 'tests/check_rev24.py')], cwd=root, check=True)
java = root / 'app/src/main/java/ru/forum/adbfastboottool'
ui = (java / 'MassFlashQueueUi.kt').read_text()
main = (java / 'MainActivity.kt').read_text()
assert 'removeAllViews()' not in ui
assert 'ListAdapter<FlashQueueRow' in ui and 'ConcatAdapter(HeaderAdapter(header), rows)' in ui
assert 'old.item.partition == new.item.partition' in ui
assert 'bindingAdapterPosition' in ui and 'RecyclerView.NO_POSITION' in ui
assert 'header.saveHierarchyState(state)' in ui and 'header::restoreHierarchyState' in ui
assert 'massQueueUi.saveFormState()' in main and 'massQueueUi.restoreFormState(restoredMassQueueState)' in main
a = '{http://schemas.android.com/apk/res/android}'
app = '{http://schemas.android.com/apk/res-auto}'
layout = ET.parse(root / 'app/src/main/res/layout/page_fastboot.xml').getroot()
parents = {child: parent for parent in layout.iter() for child in parent}
queue = next(node for node in layout.iter() if node.get(a+'id') == '@+id/rvMassQueue')
node = queue
while node in parents:
    node = parents[node]
    assert 'ScrollView' not in node.tag, 'The queue must own scrolling to recycle offscreen rows'
assert queue.get(a+'layout_height') == '0dp' and queue.get(a+'layout_weight') == '1'
execute = next(node for node in layout.iter() if node.get(a+'id') == '@+id/btnMassExecute')
assert parents[queue] is parents[execute]
activity = ET.parse(root / 'app/src/main/res/layout/activity_main.xml').getroot()
navigation = next(node for node in activity.iter() if node.get(a+'id') == '@+id/bottomNavigation')
tabs = [node for node in navigation if node.tag.endswith('MaterialButton')]
assert len(tabs) == 5 and all(node.get(app+'icon') for node in tabs)
assert all(node.get(app+'iconGravity') == 'textTop' for node in tabs)
assert 'iconTint' in (java / 'TabController.kt').read_text()
for workflow in ('build.yml', 'release.yml'):
    assert 'tests/check_rev25.py' in (root / '.github/workflows' / workflow).read_text()
assert 'tests/collect_android_ui.py' in (root / '.github/workflows/build.yml').read_text()
print('PASS REV25: recycled ordered queue, independent scrolling, explicit form restoration, five icon tabs')
