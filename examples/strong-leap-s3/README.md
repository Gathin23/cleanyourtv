# Strong LEAP-S3 (Skyworth YDA, Google TV 14)

A set-top box on the same Skyworth firmware family as [`wanbo-x5-pro`](../wanbo-x5-pro/debloat.txt), on build
`C3.0.12`. The ordering that avoids a black screen, the remote's keycodes and what was kept are in the
[device note](../../docs/devices/strong-leap-s3.md); the list here is what was applied and lived with.

- `debloat.txt`: 10 packages, including `setupwraith` and `launcherx`. The author's unit also has the Assistant, the
  Cast receiver and the official YouTube app off by choice (13 in all); those three are not listed.
- `config.json`: the gear button on the remote sends `KEYCODE_NOTIFICATION` (83), so it opens Settings once
  `launcherx` is gone. The remote's YouTube button sends `KEYCODE_STEM_2` (266): add
  `"app_keys": ["266:<your YouTube replacement package>"]` to point it at the app you use.
- `HomeKeyService` has to be enabled for these two buttons, and a key injected with `adb shell input keyevent` does not
  reach it: test with the real remote.
- No hardware reset button. Pair Wireless debugging before disabling anything.
