# Noblex DB58X7550 (AI PONT, ISDB93CH, sindorim)

A 58" Noblex set for Argentina (ISDB-T) built by Changhong: model **DB58X7550**, Android model `AI PONT`,
product `ISDB93CH`, board `sindorim`, MediaTek `m7322`, Android TV 11 (API 30), firmware `RTO2.231030.002`,
about 1.3 GB of RAM. The vendor layer is Homwee, as on the CHiQ and OK. sets in
`examples/chiq-ai-pont/README.md`, and the list started from that folder. It is a different set from
`examples/noblex-shinjuku/`, which is Skyworth.

## The list

The debloat list disables 14 packages. The tested set had five more off (casting and two streaming apps,
now comments in the list), all applied in four batches with a screenshot between them and a reboot
after the last one. The TV Manager cleaner (`com.homwee.chtvmanager`) runs as system and went
alone, in the last batch. Compared with the CHiQ list, this firmware has no Anoki ACR, no FAST channel
app and no Alexa control, so those lines are gone. Nothing was re-enabled afterwards.

Kept on purpose: the firmware updater `com.homwee.dmt.upgrade`, every `com.mediatek.*` package (Live TV,
HDMI, the remote), `com.google.android.leanbacklauncher.partnercustomizer` (the Input key depends on it
after a reboot on this family), the Play Store, Play Services, Gboard and YouTube.

`com.homwee.dmt.upgrade` crashes once at every boot. That is the firmware: the same happens on other sets
of this family with nothing disabled.

## Power-on and the volume key

Without a helper, the TV opens Live TV (`com.mediatek.wwtv.tvcenter`) at every power-on, even with the
reference launcher as Home. The fix is the launcher's key helper, enabled with `-HomeKeyService` on
`launcher/build-and-install.ps1` (`HOME_KEY_SERVICE=1` with the shell twin), and these options, which
are in `config.json` in this folder and can be pushed with `tools/theme.ps1`:

```json
{
  "live_tv_package": "com.mediatek.wwtv.tvcenter",
  "power_on_home": false,
  "power_on_home_always": true,
  "key_filter": false,
  "consume_volume_keys": false
}
```

The options come from `examples/chiq-ai-pont/README.md`, which documents them for the power-on behaviour,
not for the volume key.

The one other setting tried on this set was `power_on_home: true` with `key_filter` left at its default:
holding Volume Up or Down on the remote then moved the volume one step only. With the options above the
TV comes up on Home and a held volume key ramps again. No other combination was tried.

The `config.json` here only sets the dock to Live TV and YouTube; add your own apps to `dock_apps`.

## Other notes

- HDMI and Live TV screenshots come out black (protected video layer). That is normal.
- Wi-Fi goes down in standby: if ADB does not answer, turn the TV on with the remote first.
- Private DNS works on this set.

## Test limits

Everything here was applied and checked on the same day, 2026-11-02: the four batches, a reboot, the
launcher install, the power-on settings and the held volume key. This is not a several-day test.
