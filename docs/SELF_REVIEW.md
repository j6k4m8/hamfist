# Self-review — 2026-09-21

Direct source review of notification filtering, timing, preferences, background vibration, Wear transport, manifests, UI controls and test coverage. Both debug APKs were rebuilt after the fixes below. This was a self-review, not an independent reviewer sign-off.

## Fixed findings

1. **Medium: watch lookup could outlive authorization or freshness.** Capability lookup is asynchronous. A notification could pass the initial filters, then be sent after pause, app deselection or notification-access revocation. Its timestamp was assigned after lookup, making an old notification look fresh. `WatchLink` now preserves the original timestamp, imposes a monotonic 30-second deadline and rechecks current eligibility immediately before sending. Three JVM regression tests cover expiration, revoked eligibility and deadline boundaries. An already-submitted transport message cannot be recalled; the receiving watch still applies its own current controls.
2. **Medium: delayed playback depended on the UI thread.** A blocked UI could delay the handoff until well after the intended time. Playback now uses a background handler with one pending signal, an explicit deadline and a CPU-hold check; late work is discarded. It also rechecks destination, safety settings and, for local notifications, app selection and access. Device tests confirm playback and CPU-hold release while the UI is blocked, plus cancellation before playback.
3. **Test gap: invalid-message observation ended before playback could start.** The previous check waited only 300 ms despite a 500 ms default handoff. Cleanup could cancel an incorrectly accepted message before the assertion detected it. The test now waits beyond the handoff plus the complete SOS waveform. Wake-lock assertions examine active locks rather than matching historical acquisition/release entries.
4. **Low: Android 11 backup configuration was implicit.** Added explicit legacy backup disabling alongside the existing Android 12+ transfer and backup exclusions. The backup lint warning is resolved.

## Verification after fixes

- Eleven JVM tests: passed.
- Three Wear device tests: passed, including display asleep before/after completed Morse, invalid/replayed payload rejection, UI independence and pending-signal cancellation.
- Seven real notification-listener smoke checks on the watch emulator: passed, with the display put to sleep before notifications. These cover selected apps, duplicate updates, changed content, unselected apps, pause, quiet hours and cooldown.
- Phone and Wear debug builds: passed.
- Phone and Wear lint: zero errors; remaining warnings only suggest newer dependency versions.
- Updated APKs and XML/lint evidence are in `artifacts/`.

The earlier seven-check phone integration run also passed; it was not rerun after these shared-code fixes. The final phone build and lint passed, and the shared notification/playback path was exercised on the watch.

## Remaining acceptance work

Real paired phone/watch radio delivery, extended Doze, physical haptic legibility and actual battery drain remain unmeasured. The receiver test injects representative payload bytes into the production handler; it does not prove radio transport. Bedtime/off-wrist restrictions and the original notification app's own screen-wake behavior also need a physical Pixel Watch check. See [VALIDATION.md](VALIDATION.md) for the acceptance steps.

No other actionable defect was found in this pass. Emulator results are not a claim of physical-device battery performance or release readiness.
