# Google TV Streamer: keep Cast and stock Home enabled

I tested this on my Google TV Streamer, device `kirkwood`, Google TV 14, SDK 34, build
`UTTK.260317.003`, from 20 through 28 October 2026. I had no everyday stability issues during
that period. This five-package [Cast-preserving list](debloat-keep-cast.txt) is a subset of
[the original list](debloat.txt). I kept Google's launcher and setup wizard enabled.

## Choose the list

Use `debloat-keep-cast.txt` to disable feedback, remote usage logging and advertising services
while keeping casting and firmware-update services enabled. It also leaves streaming apps,
Assistant, ambient mode, the phone remote, Bluetooth, keyboard, Play Store and Play Services enabled.

The original `debloat.txt` documents a different Home handover, including disabling setupwraith
and restoring Cast credentials after receiver restarts. I did not use that handover during this
test. Do not apply both lists if you want to keep setupwraith enabled.

Apply and undo commands are in each list's header. Only disable packages that were enabled in
your backup. I left the factory-disabled recommendations stub unchanged; it is not in this list.

## Home behavior on my unit

When I disabled `com.google.android.apps.tv.launcherx`, setupwraith's `RecoveryActivity` claimed
HOME, even though `cmd package set-home-activity` reported success. I immediately re-enabled the
stock launcher and never disabled setupwraith. Keeping both enabled preserved Cast and the stock
remote buttons without the credential enable/wait/disable ritual.

I used a locally modified launcher and the existing Home accessibility helper for custom Home,
boot and standby-wake behavior. Those launcher changes are outside this contribution and are
not needed for the five-package list. This list alone leaves Google TV as default Home; it does
not configure automatic custom-launcher startup with the unmodified reference launcher.

## Verification

I inspected the packages and their activities/services before disabling them. I applied changes
in small batches, one package command per shell call, and checked screenshots and navigation on
the device. I tested the physical Home, Netflix, YouTube, Settings/dashboard and Input buttons,
and phone-to-TV YouTube casting after reboot. All worked.

On 28 October, I checked that all five packages were still disabled, the Cast receiver and both
stock Home packages were enabled, and the Home helper was bound with no crashed services.
The device had the same boot ID as 21 October. A screenshot showed the custom launcher, and its
process-exit history contained only my earlier package updates and deliberate force-stops, with
no later crash or ANR exit recorded.
