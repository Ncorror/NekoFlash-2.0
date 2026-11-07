#!/usr/bin/env python3
"""Read-only NekoFlash diagnostic ZIP verifier, Python 3 standard library only.

Verifies every archive file's SHA-256/length against EXPORT-MANIFEST.txt.
Optional --source-root ID=PATH compares original filesystem files byte-for-byte
by SHA-256. No extraction, rewriting, deleting or network uploads.
"""
from __future__ import annotations

import argparse
import hashlib
import posixpath
import re
import sys
from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile

ENTRY_RE = re.compile(r"^file=(logs/([1-9][0-9]*)/(.+))\tbytes=([0-9]+)\tsha256=([0-9a-f]{64})$")
MANIFEST_NAME = "EXPORT-MANIFEST.txt"
CHUNK = 128 * 1024


def digest_stream(stream):
    sha = hashlib.sha256()
    length = 0
    while True:
        chunk = stream.read(CHUNK)
        if not chunk:
            break
        sha.update(chunk)
        length += len(chunk)
    return length, sha.hexdigest()


def digest_file(path: Path):
    with path.open("rb") as inp:
        return digest_stream(inp)


def parse_roots(values):
    roots = {}
    for item in values:
        if "=" not in item:
            raise ValueError("Source mapping must be ID=/absolute/path")
        ident, path = item.split("=", 1)
        if not ident.isdecimal() or int(ident) < 1 or not path:
            raise ValueError("Invalid source mapping: " + item)
        if not Path(path).is_absolute():
            raise ValueError("Source root must be absolute: " + item)
        if str(int(ident)) in roots:
            raise ValueError("Duplicate source ID: " + ident)
        roots[str(int(ident))] = Path(path).resolve()
    return roots


def safe_source(root: Path, relative: str) -> Path:
    parts = PurePosixPath(relative).parts
    if not parts or any(p in (".", "..", "") for p in parts) or relative.startswith("/"):
        raise ValueError("Unsafe path in diagnostic manifest")
    candidate = root.joinpath(*parts).resolve()
    if not candidate.is_relative_to(root):
        raise ValueError("Source path escapes the mapped directory")
    return candidate


def verify(path: Path, roots) -> int:
    errors = []
    warnings = []
    verified = 0
    originals = 0
    with ZipFile(path) as z:
        names = z.namelist()
        if len(names) != len(set(names)):
            errors.append("duplicate ZIP entry names")
        if MANIFEST_NAME not in names:
            # REV48 and older had no manifest. We can validate only ZIP CRC
            # and cannot independently establish archive source completeness.
            damaged = z.testzip()
            if damaged is not None:
                errors.append("bad CRC: " + damaged)
            if errors:
                print("CORRUPT: " + "; ".join(errors))
                return 1
            print("LEGACY ZIP: CRC OK, but no SHA-256 manifest. Historical completeness is NOT VERIFIED.")
            return 2
        text = z.read(MANIFEST_NAME).decode("utf-8")
        lines = text.splitlines()
        statuses = [x.removeprefix("status=") for x in lines if x.startswith("status=")]
        counts = [x.removeprefix("file_count=") for x in lines if x.startswith("file_count=")]
        if len(statuses) != 1 or statuses[0] not in ("CONSISTENT_SNAPSHOT", "INCOMPLETE_OR_UNVERIFIED"):
            errors.append("invalid or missing manifest status")
        if len(counts) != 1 or not counts[0].isdecimal():
            errors.append("invalid or missing file_count")
        for line in lines:
            if line.startswith("warning="):
                warnings.append(line[8:])
        manifest_entries = {}
        for line in lines:
            if not line.startswith("file="):
                continue
            match = ENTRY_RE.fullmatch(line)
            if match is None:
                errors.append("malformed manifest entry")
                continue
            name, ident, relative, length, sha = match.groups()
            if name in manifest_entries:
                errors.append("duplicate manifest record: " + name)
            elif (relative.startswith("/") or
                    posixpath.normpath(relative) != relative or
                    any(part in (".", "..") for part in PurePosixPath(relative).parts)):
                errors.append("unsafe manifest path: " + name)
            else:
                manifest_entries[name] = (ident, relative, int(length), sha)
        actual = {name for name in names if name not in (MANIFEST_NAME, "NO_LOGS.txt") and not name.endswith("/")}
        if actual != set(manifest_entries):
            errors.append("ZIP entries differ from manifest: missing=" +
                          str(len(set(manifest_entries) - actual)) + " extra=" +
                          str(len(actual - set(manifest_entries))))
        if len(counts) == 1 and counts[0].isdecimal() and int(counts[0]) != len(manifest_entries):
            errors.append("manifest file_count disagrees with file records")
        for name, (ident, relative, expected_len, expected_hash) in manifest_entries.items():
            if name not in actual:
                continue
            with z.open(name) as raw:
                got_len, got_hash = digest_stream(raw)
            if (got_len, got_hash) != (expected_len, expected_hash):
                errors.append("ZIP SHA-256/size mismatch: " + name)
            else:
                verified += 1
            if ident in roots:
                original_path = safe_source(roots[ident], relative)
                if not original_path.is_file():
                    errors.append("source missing or unreadable: " + str(original_path))
                else:
                    original_len, original_hash = digest_file(original_path)
                    if (original_len, original_hash) != (expected_len, expected_hash):
                        errors.append("SOURCE differs from exported ZIP: " + str(original_path))
                    else:
                        originals += 1

    print(f"ZIP: {verified} files passed SHA-256/size checks; source files matched: {originals}.")
    for warning in warnings:
        print("WARNING (export): " + warning)
    if errors:
        for error in errors:
            print("ERROR: " + error)
        return 1
    if statuses == ["INCOMPLETE_OR_UNVERIFIED"] or warnings or verified == 0:
        print("INCOMPLETE/UNVERIFIED: archive is readable, but not complete evidence.")
        return 2
    print("CONSISTENT SNAPSHOT (not proof that every earlier app event was recorded).")
    if not roots:
        print("NOTE: original files were not compared; pass --source-root ID=DIR to do so.")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("zip_file", type=Path, help="NekoFlash-diagnostics-*.zip")
    ap.add_argument("--source-root", action="append", default=[], metavar="ID=/absolute/dir",
                    help="Compare originals matching ZIP logs/ID/... against this directory (repeatable)")
    args = ap.parse_args()
    try:
        roots = parse_roots(args.source_root)
        return verify(args.zip_file, roots)
    except (OSError, BadZipFile, UnicodeError, ValueError, KeyError) as error:
        print("VERIFICATION ERROR: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
