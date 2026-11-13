#!/usr/bin/env python3
"""Validate current NekoFlash documentation without network access."""
from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]

CURRENT_FILES = {
    "README.md", "README_ru.md", "PRIVACY.md", "THIRD_PARTY_NOTICES.md",
    "ASSETS_LICENSE.md", "CHANGELOG.md",
    "docs/README.md", "docs/STATUS.md", "docs/AUDIT_2026-11-13.md",
    "docs/HARDWARE_VALIDATION.md", "docs/USB_CONNECTION_TROUBLESHOOTING.md",
    "docs/SECURITY.md", "docs/REPOSITORY_SECURITY.md", "docs/LEGAL_REVIEW.md",
    "docs/PROFESSIONAL_RECOVERY_POLICY.md",
    "docs/index.html", "docs/ru/index.html", "docs/404.html",
    "vendor/filesystems/README.md",
}
CURRENT_PREFIXES = (
    "docs/guides/",
    "docs/ru/guides/",
)
MD_LINK = re.compile(r"!?(?:\[[^\]\n]*\])\(([^)\n]+)\)")
HTML_LINK = re.compile(r"""\b(?:href|src)\s*=\s*["']([^"']+)["']""", re.IGNORECASE)
IGNORE_SCHEMES = {"http", "https", "mailto", "tel", "data", "javascript", "file"}

FORBIDDEN_PUBLIC_TEXT = (
    "https://ncorror.github.io/NekoFlash/",
    "https://github.com/Ncorror/NekoFlash"",
)
FORBIDDEN_SIDELOAD_TEXT = (
    "immediately starts sending it",
    "сразу запускает его отправку",
    "PR #71",
)

def is_current(relative: Path) -> bool:
    posix = relative.as_posix()
    return posix in CURRENT_FILES or any(posix.startswith(prefix) for prefix in CURRENT_PREFIXES)

def target_of(raw: str) -> str | None:
    raw = raw.strip()
    if raw.startswith("<") and ">" in raw:
        raw = raw[1:raw.index(">")]
    else:
        raw = raw.split(" ", 1)[0]
    if not raw or raw.startswith(("#", "//", "/")):
        return None
    parsed = urlsplit(raw)
    if parsed.scheme.lower() in IGNORE_SCHEMES or parsed.netloc or parsed.scheme:
        return None
    path = unquote(parsed.path)
    return path.replace("\\", "/") if path else None

def scan_file(path: Path) -> list[tuple[int, str]]:
    data = path.read_text(encoding="utf-8")
    failures = []
    patterns = [MD_LINK, HTML_LINK] if path.suffix.lower() == ".md" else [HTML_LINK]
    for pattern in patterns:
        for match in pattern.finditer(data):
            target = target_of(match.group(1))
            if target is None:
                continue
            location = (path.parent / target).resolve()
            if not location.is_relative_to(ROOT.resolve()) or not location.exists():
                failures.append((data.count("\n", 0, match.start()) + 1, target))
    return failures

def main() -> None:
    checked = 0
    warnings: list[str] = []
    errors: list[str] = []
    current_text: dict[str, str] = {}

    for suffix in ("*.md", "*.html"):
        for path in sorted(ROOT.rglob(suffix)):
            relative = path.relative_to(ROOT)
            if any(part.startswith(".") or part in {"build", "node_modules"} for part in relative.parts):
                continue
            checked += 1
            if is_current(relative):
                current_text[relative.as_posix()] = path.read_text(encoding="utf-8")
            for line, target in scan_file(path):
                item = f"{relative}:{line} -> {target}"
                (errors if is_current(relative) else warnings).append(item)

    for path, data in current_text.items():
        for forbidden in FORBIDDEN_PUBLIC_TEXT:
            if forbidden in data:
                errors.append(f"{path}: stale public URL/text: {forbidden}")

    for path in (
        "docs/guides/adb-sideload/index.html",
        "docs/ru/guides/adb-sideload/index.html",
    ):
        data = current_text.get(path, "")
        for forbidden in FORBIDDEN_SIDELOAD_TEXT:
            if forbidden in data:
                errors.append(f"{path}: obsolete Sideload instruction: {forbidden}")

    print(f"Documentation link check: {checked} Markdown/HTML files")
    for item in warnings:
        print("HISTORICAL/OTHER LINK REVIEW:", item)
    for item in errors:
        print("BROKEN/STALE CURRENT DOC:", item)
    print(f"Current errors: {len(errors)}; historical/other warnings: {len(warnings)}")
    if errors:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
