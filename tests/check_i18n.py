#!/usr/bin/env python3
"""NekoFlash 2.0 RU/EN contract; do not rename package or workspace paths."""
from pathlib import Path
from collections import Counter
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
res = ROOT / "app/src/main/res"
NS = "{http://schemas.android.com/apk/res/android}"
EN = ET.parse(res / "values/strings.xml").getroot()
RU = ET.parse(res / "values-ru/strings.xml").getroot()
locale = ET.parse(res / "xml/locales_config.xml").getroot()
assert {x.attrib[NS+"name"] for x in locale.findall("locale")} == {"ru", "en"}

def strings(root):
    out = {}
    for node in root.findall("string"):
        key = node.attrib["name"]
        assert key not in out, "Duplicate key: " + key
        out[key] = node.text or ""
    return out

en, ru = strings(EN), strings(RU)
assert set(en) == set(ru), "Missing RU/EN translation keys: " + str(sorted(set(en) ^ set(ru)))
assert all(val.strip() for val in en.values()), "Empty English string resource"
assert all(val.strip() for val in ru.values()), "Empty Russian string resource"

# Compare order/position and format type across translations. '%' literals
# and platform resource references are not replacement parameters.
FORMAT = re.compile(r"%(?!%)(?:(\d+)\$)?[-+ 0,#(]*\d*(?:\.\d+)?([a-zA-Z])")
def params(value):
    return Counter((arg or str(n + 1), kind.lower()) for n, (arg, kind)
                   in enumerate(FORMAT.findall(value)))
for key in sorted(en):
    assert params(en[key]) == params(ru[key]), ("Format mismatch", key, en[key], ru[key])

def plural(root):
    result = {}
    for node in root.findall("plurals"):
        name = node.attrib["name"]
        assert name not in result, name
        variants = {x.attrib["quantity"]: x.text for x in node.findall("item")}
        assert "other" in variants, "Missing 'other' plural: " + name
        result[name] = variants
    return result

plural_en, plural_ru = plural(EN), plural(RU)
assert set(plural_en) == set(plural_ru), "Plural resource keys differ"
for key in plural_en:
    for variants in (plural_en[key], plural_ru[key]):
        assert all(text and text.strip() for text in variants.values()), ("Empty plural", key)

# No package/source/workspace migration was approved.
gradle = (ROOT/"app/build.gradle").read_text()
main = (ROOT/"app/src/main/java/ru/forum/adbfastboottool/MainActivity.kt").read_text()
assert 'namespace = \'ru.forum.adbfastboottool\'' in gradle
assert 'applicationId = "ru.forum.adbfastboottool"' in gradle
assert 'private val folderName = "NekoFlash"' in main
assert 'Environment.DIRECTORY_DOWNLOADS' in main
assert 'workspacePath = File(downloadsDir, folderName)' in main

# User can select RU and EN, independent of Android system locale.
assert 'val tags = arrayOf("", "ru", "en")' in main
assert 'AppCompatDelegate.setApplicationLocales' in main
for key in ("rev11_slot_switch_failed", "rev11_slot_switch_unverified",
            "rev11_slot_switch_verified"):
    assert key in en and key in ru, key
# Every new user-facing message is available in both locales and referenced in Kotlin.
required_localized_messages = (
    "rev11_import_started",
    "rev11_import_expected_size",
    "rev11_import_finished",
    "rev11_import_failed",
    "rev11_picker_open_failed",
    "rev11_workspace_permission_needed",
    "rev11_workspace_create_failed",
    "rev11_workspace_location",
    "rev11_workspace_select_unavailable",
    "rev11_workspace_no_files",
    "rev11_usb_missing_from_system",
    "rev11_usb_access_granted",
    "rev11_usb_disconnected_unknown",
    "rev11_usb_disconnected",
    "rev11_usb_attach_missing",
    "rev11_usb_unsupported_interface",
    "rev11_usb_already_authorized",
    "rev11_log_folder_failed",
    "rev11_log_store_failed",
    "rev11_log_trace_location_info",
    "rev11_fastboot_command_failed",
    "rev11_fastboot_download_failed",
    "rev11_fastboot_logical_failed",
    "rev11_fastboot_logical_info_failed",
)
vm = (ROOT/"app/src/main/java/ru/forum/adbfastboottool/DeviceViewModel.kt").read_text()
for key in required_localized_messages:
    assert key in en and key in ru, key
    assert "R.string." + key in (main + vm), "Unreferenced translation: " + key

# Guard against reintroducing the known fixed English-only messages.
for literal in (
    'viewModel.log("File import: ',
    'viewModel.log("Workspace folder: ',
    'viewModel.log("USB access already granted")',
    'failOperation("Fastboot logical command failed:',
    'failOperation("Fastboot command failed:',
):
    assert literal not in (main + vm), "English-only status returned: " + literal

rev12_keys = (
    "rev12_sideload_no_uri",
    "rev12_sideload_unnamed",
    "rev12_sideload_source_selected",
    "rev12_sideload_verify_pending",
    "rev12_sideload_disconnect_pending",
    "rev12_sideload_cancelled",
    "rev12_sideload_mode_inactive",
    "rev12_sideload_failed",
    "rev12_sideload_saf_unsupported",
)
for key in rev12_keys:
    assert key in en and key in ru
    assert "R.string." + key in (main + vm), key

print(f"PASS I18N: {len(en)} paired strings, {len(plural_en)} plural keys, "
      "format-safe RU/EN, package and workspace paths unchanged")
