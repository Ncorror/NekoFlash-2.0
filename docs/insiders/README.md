# NekoFlash Insiders · REV45

**[Скачать комплект / Download tester ZIP](https://github.com/Ncorror/NekoFlash-2.0/releases/download/insiders-rev45/NekoFlash-Insiders-REV45.zip)** · [GitHub Actions](https://github.com/Ncorror/NekoFlash-2.0/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) · [Telegram](https://t.me/+NR_FiUmvXlViOTUy)

**RU.** Распакуй ZIP и начни с `START-HERE.txt`. В комплекте лежат проверенная DEV APK, автономный тестбук, две инструкции, сведения о сборке и контрольные суммы. Название артефакта в GitHub Actions — `NekoFlash-Insiders-Tester-Kit`. Его публикация зависит от успешного завершения сборки и обеих эмуляторных проверок. Ссылка выше ведёт на один закреплённый ZIP у тега insiders-rev45.

**EN.** Extract the ZIP and open `START-HERE.txt`. The kit contains the debug APK, offline RU/EN checklist, two field guides, build metadata, checksums, notices and corresponding filesystem-tool sources. The link above downloads the pinned field-test ZIP. For a newer CI snapshot, sign in, open a successful main run and select `NekoFlash-Insiders-Tester-Kit` under Artifacts.

| File | Use |
| --- | --- |
| [START-HERE.txt](START-HERE.txt) | First session / первый запуск |
| [FIELD-GUIDE-RU.md](FIELD-GUIDE-RU.md) · [FIELD-GUIDE-EN.md](FIELD-GUIDE-EN.md) | Commands, expected evidence and lab boundaries |
| [testbook.html](testbook.html) | Offline checklist, JSON backup and raw evidence ZIP |
| BUILD.txt · SHA256SUMS.txt | Exact kit build and integrity checks, included in the ZIP |

The standalone testbook in the repository identifies the [reference build](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41354284165). The copy in a CI kit identifies that kit’s own APK. Open the HTML from the same ZIP as the installed APK. Earlier REV25, REV29 and REV43 progress JSON may be imported explicitly. Imported reports retain their original build details.

Передавай исходные журналы только в доверенный личный канал. Архив отчёта не зашифрован. Share raw report ZIPs privately. [Аудит / audit](AUDIT-2026-11-02.md).
