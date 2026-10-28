# Google TV Streamer: keep Cast and stock Home enabled

A second Google TV Streamer, device `kirkwood`, Google TV 14, SDK 34, build `UTTK.260317.003`,
was tested from 20 through 28 October 2026. The owner reported no everyday instability during
that period. The five-package [Cast-preserving list](debloat-keep-cast.txt) is a subset of
[the original list](debloat.txt). It leaves Google's launcher and setup wizard enabled.

## Choose the list

Use `debloat-keep-cast.txt` if keeping casting and firmware-update services available matters more
than taking over Android's default HOME resolver. It disables only feedback, remote usage logging
and advertising services. It leaves streaming apps, Assistant, ambient mode, the phone remote,
Bluetooth, keyboard, Play Store and Play Services enabled.

The original `debloat.txt` documents a different Home handover, including disabling setupwraith
and restoring Cast credentials after receiver restarts. That handover was not used for this
second unit's observation period. Do not apply both lists if you want to keep setupwraith enabled.

Apply and undo commands are in each list's header. Only disable packages that were enabled in
your backup. The factory-disabled recommendations stub was left unchanged and is not in this list.

## Home behavior on the second unit

Disabling `com.google.android.apps.tv.launcherx` made setupwraith's `RecoveryActivity` claim HOME,
even though `cmd package set-home-activity` reported success. The stock launcher was immediately
re-enabled; setupwraith was never disabled. Keeping both enabled preserved Cast and the stock
remote buttons without the credential enable/wait/disable ritual.

A locally modified launcher and the existing Home accessibility helper were used for custom Home,
boot and standby-wake behavior. Those local launcher changes are outside this contribution and
are not needed for the five-package list. This list alone leaves Google TV as default Home; it
does not promise automatic custom-launcher startup with the unmodified reference launcher.

## Verification and limits

Packages and their activities/services were inspected before disabling. Changes were applied in
small batches, one package command per shell call, with real-device screenshots and navigation
checks. The owner tested physical Home, Netflix, YouTube, Settings/dashboard and Input buttons,
and phone-to-TV YouTube casting after reboot. All worked.

On 28 October, device readbacks showed the same boot ID as 21 October, all five packages still
disabled, the Cast receiver and both stock Home packages enabled, and the Home helper bound with
no crashed services. A pulled screenshot showed the custom launcher. Its historical process exits
contained only the earlier package updates and deliberate force-stops, with no later crash or ANR
exit recorded. This supports the reported several-day result, not a guarantee for other units.

## Bluetooth speaker announcements during standby

Before the observation period, a paired Echo announced the TV connection at night even though the
display stayed off. At 04:44 on 21 October, the retained logs showed the Cast receiver exiting itself
with status 0, restarting, creating an audio track and activating the speaker's A2DP stream. No
interactive power wake was recorded at that time. Similar nightly Cast self-exits predated this test.

The speaker's Bluetooth audio link was disconnected without disabling Bluetooth or Cast. The owner
reported no further overnight announcements. On 28 October, retained Bluetooth state-machine history
still ended with that speaker's disconnection on 21 October, and Bluetooth reported zero crashes.
Cast's exit history continued to show nightly status-0 self-exits on 22-28 October.

That supports a background Cast/audio-link explanation for this unit. It does not identify why
Cast restarts or prove how the speaker decides to announce a connection. Disconnecting the audio
link is an observed workaround, not an automatic reconnect solution. No automation is included.

Only these summarized observations are shared here. Raw logs, backups, screenshots with personal
details and device identifiers remain private.
