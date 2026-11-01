#!/usr/bin/env python3
"""Check repository-local documentation links without network access.

Scan all Markdown/HTML documents, report historical dead links, and fail on
broken links in current public entry points. Release of old snapshots is not
silently blocked by intentionally missing ZIP paths in archival documents.
"""
from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
CURRENT = {
    "README_ru.md", "docs/index.html", "docs/ru/index.html",
    "docs/insiders/README.md", "docs/insiders/AUDIT-2026-11-02.md",
    "docs/insiders/FIELD-GUIDE-RU.md", "docs/insiders/FIELD-GUIDE-EN.md",
    "README.md", "PRIVACY.md", "THIRD_PARTY_NOTICES.md",
    "ASSETS_LICENSE.md", "CHANGELOG.md",
    "docs/DEVELOPMENT_STATUS.md", "docs/SECURITY_REVIEW_BACKLOG.md",
    "docs/HARDWARE_VALIDATION.md", "docs/LEGAL_REVIEW.md",
    "docs/UI_UX_AUDIT.md", "docs/USB_CONNECTION_TROUBLESHOOTING.md",
    "vendor/filesystems/README.md",
}
MD_LINK = re.compile(r"!?(?:\[[^\]\n]*\])\(([^)\n]+)\)")
HTML_LINK = re.compile(r"""\b(?:href|src)\s*=\s*["']([^"']+)["']""", re.IGNORECASE)
IGNORE_SCHEMES = {"http", "https", "mailto", "tel", "data", "javascript", "file"}

def target_of(raw: str) -> str | None:
    raw = raw.strip()
    if raw.startswith("<") and ">" in raw:
        raw = raw[1:raw.index(">")]
    else:
        raw = raw.split(" ", 1)[0]
    if not raw or raw.startswith(("#", "//", "/")):
        return None  # document anchors and site-root links
    parsed = urlsplit(raw)
    if parsed.scheme.lower() in IGNORE_SCHEMES or parsed.netloc:
        return None
    if parsed.scheme:
        return None
    path = unquote(parsed.path)
    if not path:
        return None
    return path.replace("\\", "/")

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
    warnings = []
    errors = []
    for suffix in ("*.md", "*.html"):
        for path in sorted(ROOT.rglob(suffix)):
            relative = path.relative_to(ROOT)
            if any(part.startswith(".") or part in {"build", "node_modules"} for part in relative.parts):
                continue
            checked += 1
            for line, target in scan_file(path):
                item = f"{relative}:{line} -> {target}"
                if relative.as_posix() in CURRENT:
                    errors.append(item)
                else:
                    warnings.append(item)
    print(f"Documentation link check: {checked} Markdown/HTML files")
    for item in warnings:
        print("ARCHIVE/OTHER LINK REVIEW:", item)
    for item in errors:
        print("BROKEN CURRENT DOC LINK:", item)
    print(f"Current broken links: {len(errors)}; historical/other links to review: {len(warnings)}")
    if errors:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
