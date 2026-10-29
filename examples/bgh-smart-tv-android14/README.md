# BGH Smart TV: Android TV 14, UKNV.260821.001

This records one real BGH set. Android reports the model only as `BGH Smart TV`; the retail model number was not recorded. Do not treat this list as applying to all BGH TVs.

| Field | Observed value |
|---|---|
| Android-reported model | `BGH Smart TV` |
| Android / SDK | 14 / 34 |
| Firmware display ID | `UKNV.260821.001` |
| Physical RAM reported by the kernel | Approximately 0.9 GiB |
| Original Home | `com.google.android.tvlauncher/.MainActivity` |
| Manufacturer Live TV | `com.mediatek.wwtv.tvcenter` |

## Package changes and follow-up

The seven packages in `debloat.txt` were disabled for user 0 on 2026-10-20. A live package-state check on 2026-10-25 confirmed all seven remained disabled. The owner continued using the customized Home during that period; this is not a controlled five-day stress test.

Assistant was disabled by the owner's choice, with loss of voice search expected. Keep that entry commented out if voice controls are used. No listed package was re-enabled during the recorded period.

`com.smartdevice.recommendation` was already disabled before this work, so it is deliberately excluded. Other packages seen disabled at follow-up without a matching initial change record are also excluded.

The stock launcher was disabled only after the replacement was installed, selected as Home, and verified. Play Store, Play Services, keyboard, casting and input services were left out of the list. Manufacturer Live TV was kept enabled. An attempt to disable an updater component was refused by Android and made no component-state change; the component is not included in this list. A separate owner-selected automatic-update setting is not reproduced here.

## Home and boot behavior

Normal Home navigation worked with a separately customized launcher. A later firmware startup could still bring up manufacturer Live TV despite the replacement being the default Home.

On 2026-10-25 an optional accessibility helper in that custom launcher was tested during a real reboot: it recorded one redirect from manufacturer Live TV to Home, then became inactive. The owner confirmed the clock Home was displayed after reboot. The helper was limited to the startup/wake window and stopped intervening on the first user key; it did not consume keys or retrieve window contents.

This observation may be useful when testing upstream boot-to-Home handling on this firmware. It does **not** establish that the current upstream launcher or its helper was installed or tested on this set. No helper implementation, APK, accessibility setting or automatic permission grant is shipped in this contribution.

## Validation and limits

- Home, the app grid, YouTube and Android Settings were opened during the initial setup. An HDMI input switch was exercised; protected playback could not be confirmed from a screenshot.
- The updated custom Home returned after reboot. Opening Settings paused its widget queries and background rendering, then Home navigation returned to the launcher.
- The customized UI, online widgets and device-control integrations are outside the upstream launcher and are not distributed here.
- No before/after benchmark establishes a speed improvement. Reducing local artwork-cache limits and visible-widget refresh frequency in the custom launcher is not evidence that streaming apps became faster.
- Tuner playback, every HDMI port, casting, Bluetooth pairing and every physical remote shortcut were not comprehensively regression-tested after the latest update. An injected key alone is not proof of a physical remote shortcut.
- Disable debugging and revoke debugging authorizations when maintenance is finished, following the repository's normal closeout procedure.

This contribution contains only model/firmware facts, package names and test notes. It intentionally excludes screenshots, raw backups, personal names, locations, network details, device identifiers and credentials.
