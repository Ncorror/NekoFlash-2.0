# NekoFlash 2.0 — USB/OTG troubleshooting

## Expected roles

For phone-to-phone service:
- NekoFlash device: USB host / data host;
- target device: USB peripheral;
- power normally flows from host to target.

With USB-C to USB-C, Android can negotiate the wrong role. Do not start an operation until NekoFlash shows the intended target.

## If the target is not detected

1. Disconnect and reconnect the cable.
2. Wait for role negotiation and USB permission.
3. Swap cable ends or rotate the USB-C connector.
4. Try a short, known-good data cable.
5. Verify the target is actually in Android ADB, Recovery/Sideload, Fastboot or Fastbootd as expected.
6. Confirm RSA authorization for normal Android ADB.

Do not repeatedly resend a destructive command merely because the device disappeared.

## POCO F7 / onyx

POCO F7/onyx has historical and current evidence of intermittent long inbound USB failures when used as host. The same vayu target and files passed on a POCO X3 Pro host.

For a large Pull:
- if transport fails, NekoFlash stops fail-closed;
- reconnect manually;
- run the same Pull again;
- the current implementation resumes from the last complete durable checkpoint;
- accept the result only after final SHA-256 verification.

A powered hub can be useful as a diagnostic if OTG power is suspected, but the recorded tests did not measure VBUS voltage/current. Do not document “weak power” as a proven root cause.

## Fastboot

Fastboot sessions become unusable after protocol desync, short transfer, transport failure or disconnect. Reconnect before the next operation. Slow bootloader processing is not automatically a timeout; NekoFlash waits for legitimate final responses according to the current transport policy.

## Recovery/Sideload

Recovery ADB/Sideload is a different mode from normal Android ADB. A disconnect near 100% can be expected when Recovery reboots or replaces itself. Transfer percentage alone does not prove installation success.

## Evidence

When a new failure is reproducible, export a fresh NekoFlash diagnostics ZIP and keep the original raw evidence private. Do not replace a current failure report with an old REV checklist.
