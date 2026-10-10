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
print(f"PASS I18N: {len(en)} paired strings, {len(plural_en)} plural keys, "
      "format-safe RU/EN, package and workspace paths unchanged")
