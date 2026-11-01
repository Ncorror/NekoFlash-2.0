# REV12 — direct SAF Sideload (EN)

**Paths remain unchanged:** package `ru.forum.adbfastboottool`, Kotlin source folder `app/src/main/java/ru/forum/adbfastboottool/`, workspace `/sdcard/Download/NekoFlash`.

The approved Sideload page still has two buttons. **Choose ZIP** uses the Android system document picker and transfers a seekable `content://` document via a read-only file descriptor, **without copying it into the workspace**. **Import** remains the explicit legacy action that copies a selected document into the workspace.

ADB sideload-host requests ZIP blocks out of order. Before any USB data is sent, `FirmwareSource.SafDocument` requires a readable descriptor, random-access seek support and a positive known document size. Pipe-only or sequential-only providers are **not** silently streamed as if seekable: the operation fails with an explanation and the user can explicitly import the ZIP instead.

The existing ADB USB transport and Recovery verification semantics remain intact. A completed data transfer is **not** proof of successful ZIP installation. The Recovery result must be verified, and `UNKNOWN` is never treated as `SUCCESS`.

New source selection, transfer status and error messages have **English and Russian** Android resources. Technical commands, paths, URIs and partition names are not translated.

**Testing:** pinned ADB source diff, bilingual resource checks, JVM out-of-order file reads and existing USB/queue/diagnostic tests. On a device, choose a small local ZIP through DocumentsUI and verify that no workspace copy is made. Separately, exercise a pipe-only cloud provider and confirm a clear fallback message. Actual Recovery sideload requires a compatible benign ZIP, an unlocked dedicated test device, backup and recovery plan; do not use a daily driver.

**Not covered by REV12:** Fastboot direct SAF, >4 GiB RAW/sparse stress, or USB transport endurance tests. PR remains Draft until device validation.