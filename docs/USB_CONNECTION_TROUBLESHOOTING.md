<div align="center">

<img src="artwork/nekoflash_launcher_circle_cat_v6.0.0.png" width="160" height="160" style="display: block; margin: 0 auto;" alt="NekoFlash Logo" />

# USB Connection Troubleshooting

### USB-C role negotiation, OTG power flow, and device detection diagnostics

English

</div>


## USB-C role negotiation

When using a direct USB-C to USB-C cable, Android may sometimes negotiate the wrong USB data role or USB power role.

## Expected connection

- NekoFlash phone: USB host
- NekoFlash phone: power source
- target/patient phone: USB device
- target/patient phone: power sink
- power direction: from the NekoFlash host phone to the target/patient phone

In this state, the target/patient phone may show that it is charging from the NekoFlash host phone. That is expected.

## Incorrect USB role

The connection is wrong if:

- the NekoFlash host phone shows that it is charging from the target/patient device;
- the target/patient device behaves as the USB host;
- an unexpected device appears first;
- normal Android ADB `DEVICE` mode does not attach to the intended target.

Do not start flash, sideload or shell operations while the detected device is not the intended target.

## Recovery steps

1. Unplug and reconnect the cable.
2. Swap cable ends.
3. Rotate one or both USB-C connectors.
4. Try another data-capable USB-C cable.
5. Wait until NekoFlash shows the intended target device.
6. Confirm that the NekoFlash phone is the host and the target/patient phone is the connected USB device.
7. Only then start ADB shell, sideload or flash operations.

## POCO F7 / onyx host compatibility

POCO F7 (`onyx`, Android 16) is a special host case in the current evidence set. Long inbound ADB transfers can fail intermittently with a real host USB receive error even when the target, file and NekoFlash build work on a POCO X3 Pro host. Legacy NekoFlash recorded similar intermittent inbound payload failures on the same onyx host.

For large Pulls NekoFlash stays fail-closed: it does not retry a failed USB packet. Fully completed 256 KiB ranges are fsynced, and after a manual reconnect the same Pull can resume from the durable checkpoint. The completed file is published only after the remote source is rechecked and SHA-256 matches.

If onyx or another host is unstable, try a short known-good cable. An externally powered USB hub is a useful diagnostic when OTG power is suspected. This is not a claim that power is the root cause: VBUS voltage/current were not measured in the recorded tests.

## REV43 Android HOST → TARGET field tests

**Historical REV43 round (not today's assignment):** the standalone RU/EN field-plan files were removed from the active release tree. Their results remain in Git history and the [real-device evidence ledger](testing/PHYSICAL-TEST-EVIDENCE-LEDGER-2026-11-08-EN.md). The 1023/1024/1025, 512-file Push/Pull and 16 MiB acceptance already passed on the tested locked Android12 vayu; do not assign identical retries without a new device, code change or missing result.

If USB is detached during `adb push`, the TARGET file can be incomplete: verify length/hash before using it. Do not claim success from a progress percentage. Do not unplug while Fastboot DATA, flash, unlock, erase or format is active. Full diagnostic logs and `trace-*.bin` go to a trusted private channel, not a public GitHub issue.

Merged DEV REV43 has [API26 UI test failures](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/41248168147); this is not evidence of a USB hardware failure. Keep the UI/CI and physical USB results separate.

## Scope

This issue is documented for normal Android ADB `DEVICE` mode.

Fastboot and Recovery/SIDELOAD are separate modes and remain covered by their own validation tests.
