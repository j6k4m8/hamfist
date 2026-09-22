# Validation — 2026-09-21

## Verified

- Phone and Wear debug APKs build successfully with JDK 21, Gradle 8.14.3 and Android SDK 36.
- Android lint: no errors; three phone and seven Wear warnings, all dependency-update suggestions. Backup and device transfer are explicitly disabled on both Android 11 and Android 12+.
- Eleven JVM tests pass: exact SOS pulses, word-gap replacement, PARIS CPM calibration, custom/Farnsworth timing, Unicode normalization, whole-character duration limits, overnight quiet hours, duplicate/cooldown handling, original message timestamps, deferred authorization revocation and monotonic delivery deadlines.
- Pixel 9 emulator, Android 16/API 36: seven integration checks pass with the screen put to sleep before notifications: real notification listener to completed vibration, duplicate suppression, changed-message delivery, allowlist filtering, pause, quiet hours, and cooldown.
- Pixel Watch 3 emulator, Android 14/API 34: the same seven checks pass with the screen put to sleep before notifications, including a rerun after the self-review fixes.
- Three Wear instrumentation tests pass: a synthetic Data Layer payload enters the production watch handler, completes an SOS waveform, and leaves `PowerManager.isInteractive == false` before and after playback; a blocked UI does not delay playback or CPU-hold release; and stopping a pending signal prevents vibration and releases its CPU hold. Duplicate, expired, future-dated, malformed, oversized and wrong-path payloads produce no extra vibration, observed beyond the full handoff and waveform duration.
- Phone Signal and Timing screens and round-watch Signal/preview controls were rendered and visually inspected. Screenshots are in `docs/screenshots/`.

The watch test exposed two real integration issues that are fixed: Wear’s notification collector suppresses direct generic notification-usage vibrations, and a source notification’s normal buzz can win vibration priority. Watch playback therefore uses the public communication-request vibration usage with explicit DND checks, plus a configurable brief start delay. No privileged flags, screen wake, or foreground service are used. A bounded partial wake lock covers only the handoff.

The subsequent [self-review](SELF_REVIEW.md) fixed deferred-delivery races, moved playback handoff off the UI thread, and strengthened regression checks. The phone integration run predates these fixes; both final APKs were rebuilt and linted, and the shared notification/playback path was rerun on the watch.

## Not yet verified on physical hardware

- Paired phone-to-watch MessageClient radio delivery/reconnection. The devices in this session were separate emulators; the screen-off test invokes the production receiver handler with representative payload bytes. The USB phone was unauthorized and no physical watch was connected.
- Physical haptic legibility, amplitude differences between models, or measured battery drain. Emulator vibration records prove API completion, not feel or power consumption.
- Long Doze/standby periods, OEM battery management, reboot/rebind behavior, actual Pixel Watch bedtime/off-wrist/charging restrictions, large-font accessibility, and a signed Play Store release.

## Physical acceptance pass

1. Install phone/watch APKs signed by the same key; grant phone notification access and select one messaging app. Leave the native watch app allowlist empty.
2. Set 40 CPM, confirm `SOS` and `PARIS`, then try 100 CPM and custom pulse widths. Adjust durations for comfortable tactile distinction.
3. Leave both displays off, send a real message, verify the watch plays Morse without waking. Repeat after an extended idle period. The original app may still independently wake the screen; configure that app’s normal alerts separately.
4. Repeat with phone-only and both destinations. Confirm quiet hours, pause, DND, silent mode, low battery, and Battery Saver. Confirm repeated updates and notification bursts do not build a queue.
5. Disconnect the watch, send a message, reconnect, and verify it is not replayed. Send a fresh message and confirm delivery resumes.
6. Compare an idle day and a typical notification day with the same brightness/connectivity settings. Record signal count, average vibration duration and battery delta before changing limits.

No physical-device or paired-radio success is implied by the emulator results.
