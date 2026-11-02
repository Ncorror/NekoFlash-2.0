#!/usr/bin/env python3
"""Capture the English gallery from an installed field APK on a fresh API 34 emulator."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "ru.forum.adbfastboottool.dev"
OUT = Path(sys.argv[1])


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=35)


def shell(*args):
    return adb("shell", *args).decode().strip()


def wait_for(*, resource=None, text=None):
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        shell("uiautomator", "dump", "/sdcard/field-gallery.xml")
        root = ET.fromstring(adb("exec-out", "cat", "/sdcard/field-gallery.xml"))
        for node in root.iter("node"):
            if node.get("package") != PACKAGE:
                continue
            if resource and node.get("resource-id") != f"{PACKAGE}:id/{resource}":
                continue
            if text and text not in node.get("text", ""):
                continue
            return node, root
    shown = [n.get("text") for n in root.iter("node") if n.get("text")]
    raise RuntimeError(f"Missing {resource or text!r}. Visible text: {shown}")


def tap(resource=None, *, text=None):
    node, _ = wait_for(resource=resource, text=text)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(name, **visible):
    _, root = wait_for(**visible)
    labels = [n.get("text", "") + " " + n.get("content-desc", "") for n in root.iter("node")
              if n.get("package") == PACKAGE]
    if re.search(r"[А-Яа-яЁё]", " ".join(labels)):
        raise RuntimeError(f"Russian UI in {name}: {labels}")
    image = adb("exec-out", "screencap", "-p")
    if image[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"Invalid PNG for {name}")
    (OUT / f"{name}.png").write_bytes(image)
    print(f"Captured {name}", flush=True)


if shell("getprop", "ro.kernel.qemu") != "1" or shell("getprop", "ro.build.version.sdk") != "34":
    sys.exit("Use a disposable API 34 emulator")
OUT.mkdir(parents=True, exist_ok=True)
shell("wm", "size", "720x1440")
shell("wm", "density", "320")
shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", "en-US")
shell("appops", "set", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
shell("am", "start", "-W", "-n", PACKAGE + "/ru.forum.adbfastboottool.WelcomeActivity")
tap("btnPrimaryAction", text="Continue")
capture("portrait-home", resource="tabHome", text="Home")
tap("tabFastboot")
capture("portrait-quick", resource="tvQuickSelectedImage", text="No image selected")
tap("btnFastbootSectionMass")
# A disposable local fixture is queued for display. No USB operation is started.
folder = "/sdcard/Download/NekoFlash"
shell("mkdir", "-p", folder)
shell("dd", "if=/dev/zero", f"of={folder}/sample-boot.img", "bs=1024", "count=8")
tap("btnMassSelectImage")
tap(text="NekoFlash workspace")
tap(text="sample-boot.img")
capture("mass-queue", text="sample-boot.img")
tap("btnFastbootSectionTools")
capture("portrait-tools", resource="btnToolGetvar", text="Read getvar")
tap("tabAdb")
capture("portrait-sideload", resource="btnSideloadImport", text="Import file")
tap("tabHome")
tap("btnTerminalOpen")
tap("etCommand")
shell("input", "text", "adb%sdevices")
tap("btnSend")
capture("terminal-command", text="> adb devices")
