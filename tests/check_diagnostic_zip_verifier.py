#!/usr/bin/env python3
"""Source-independent smoke/regression test of the read-only REV51 ZIP verifier."""
from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "verify_diagnostic_zip", ROOT / "scripts/verify_diagnostic_zip.py"
)
assert spec is not None and spec.loader is not None
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def run_quiet(zip_path, source_roots=None, check_source_inventory=False):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        result = mod.verify(zip_path, source_roots or {},
                            check_source_inventory=check_source_inventory)
    return result, buf.getvalue()


def build_zip(path: Path, data: bytes, status="CONSISTENT_SNAPSHOT", warnings=(), hash_override=None):
    name = "logs/1/log-2026-11-07-13-51-12.txt"
    digest = hash_override or hashlib.sha256(data).hexdigest()
    manifest = (
        "NekoFlash diagnostic export manifest v1\n"
        f"status={status}\nfile_count=1\n"
        "note=Source-history completeness cannot be proven from a ZIP alone.\n"
        f"file={name}\tbytes={len(data)}\tsha256={digest}\n"
        + "".join(f"warning={s}\n" for s in warnings)
    )
    with ZipFile(path, "w") as out:
        out.writestr(name, data)
        out.writestr("EXPORT-MANIFEST.txt", manifest)


with tempfile.TemporaryDirectory(prefix="nekoflash-verifier-") as td:
    root = Path(td)
    source = root / "source"
    source.mkdir()
    content = b"# NekoFlash compact log\n" + b"[test] repeated event\n" * 400
    (source / "log-2026-11-07-13-51-12.txt").write_bytes(content)
    zpath = root / "diagnostics.zip"

    build_zip(zpath, content)
    status, output = run_quiet(zpath, {"1": source})
    assert status == 0 and "source files matched: 1" in output, output

    # A valid manifest and correct hashes previously hid diagnostic files
    # missing entirely from the archive. This opt-in inventory audit catches
    # those omissions while excluding unrelated firmware/data in the folder.
    (source / "trace-2026-11-07-13-51-12.txt").write_bytes(b"# NekoFlash protocol trace\n")
    (source / "boot.img").write_bytes(b"unrelated firmware must not be audited")
    status, output = run_quiet(zpath, {"1": source}, check_source_inventory=True)
    assert status == 1 and "UNARCHIVED_DIAGNOSTIC_SOURCE root=1 count=1" in output, output
    (source / "trace-2026-11-07-13-51-12.txt").unlink()
    status, output = run_quiet(zpath, {"1": source}, check_source_inventory=True)
    assert status == 0 and "CONSISTENT SNAPSHOT" in output, output

    # Never treat a diagnostic-named symlink outside its mapped source root
    # as acceptable evidence. The verifier must only inspect safe local paths.
    outside = root / "external-diagnostic.txt"
    outside.write_bytes(b"private external file")
    escaped = source / "trace-escaped.txt"
    escaped.symlink_to(outside)
    status, output = run_quiet(zpath, {"1": source}, check_source_inventory=True)
    assert status == 1 and "diagnostic source escapes mapped root" in output, output
    escaped.unlink()

    status, output = run_quiet(zpath, check_source_inventory=True)
    assert status == 1 and "requires at least one --source-root" in output, output

    build_zip(zpath, content, hash_override="a" * 64)
    status, output = run_quiet(zpath)
    assert status == 1 and "SHA-256/size mismatch" in output, output

    build_zip(zpath, content, status="INCOMPLETE_OR_UNVERIFIED",
              warnings=["MISSING_SESSION_PROLOGUE logs/1/log-test.txt"])
    status, output = run_quiet(zpath)
    assert status == 2 and "INCOMPLETE/UNVERIFIED" in output, output

    build_zip(zpath, content)
    (source / "log-2026-11-07-13-51-12.txt").write_bytes(b"external truncation")
    status, output = run_quiet(zpath, {"1": source})
    assert status == 1 and "SOURCE differs" in output, output

    with ZipFile(zpath, "w") as out:
        out.writestr("logs/1/log-legacy.txt", b"old format\n")
    status, output = run_quiet(zpath)
    assert status == 2 and "LEGACY ZIP" in output, output

print("PASS read-only diagnostic ZIP verifier: SHA-256, source inventory omissions, source file, warnings, legacy ZIP")
