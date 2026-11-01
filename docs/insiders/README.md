# Insiders — NekoFlash 2.0 REV43 field testbook

**Telegram tester community: NekoFlash Insiders**. The testbook and debug APK are being made available to testers there; ZIP evidence is shared privately. No verified public invite URL was provided yet. Direct GitHub downloads below remain available.

- **[Open the offline RU/EN HTML testbook](testbook.html)**.
- **[Download the standalone HTML file from main](https://raw.githubusercontent.com/Ncorror/NekoFlash-2.0/main/docs/insiders/testbook.html)**. On an Android browser choose Save link / Download file. Then open the saved `.html` locally — no server is required.
- **[RU test manual](../testing/INSIDERS-FIELD-TEST-REV43-RU.md)** / **[EN test manual](../testing/INSIDERS-FIELD-TEST-REV43-EN.md)**.
- **[REV43 tested Debug APK from GitHub Actions](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391/artifacts/13060555171)**. The Actions artifact is a ZIP; extract the APK. Original NekoFlash remains installed.
- GitHub Actions also uploads `NekoFlash-Insiders-Testbook-REV43-<SHA>` independently of the APK. The archive can be downloaded from the matching workflow run.

## Functionality preserved from AutoLogs BIN REV32

The existing Black & Orange interface, Russian/English language switch, search and category/stage filters, checkboxes, PASS/FAIL/BLOCKED/NOT TESTED statuses, free notes, local state, JSON import/export, ZIP report, and byte-exact `trace-*.bin` / `log-*` / `session-summary-*` log attachment collection are retained. The user must grant Android folder access; some pickers provide a snapshot only, so choose the folder again before export. Reports are **not** uploaded automatically.

Eight new REV38–43 field cases cover keyboard composition and Unicode, selecting text during output, ADB peer maxdata, 512-file recursive push, cancellation, OTG detach/reconnect, read-only Fastboot and CI-versus-hardware evidence.

The report ZIP is not encrypted; store and share raw logs **only in a trusted private testing channel**, not public Issues. No automatic log removal, truncation or redaction. ZIP32 cannot exceed its format size limit; split oversized log sessions while keeping originals.

## Testing truth

- [REV43 PR CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41247325391) — build/API26/API34 PASS.
- [Merged DEV CI](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147) — build/API34 PASS, **API26 FAIL (2 UI timeouts)**.
- No new REV43 physical HOST→TARGET PASS is recorded yet. Older alpha10 hardware results are historical.
- Testbook source is checked for JavaScript syntax and required AutoLogs/report contracts by `tests/check_insiders_testbook.py` in CI.

This file belongs to the public testing `main` branch. It is **not** a NekoFlash 2.0 production release or an automatic update of `main`.
