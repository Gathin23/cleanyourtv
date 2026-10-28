# Questions people ask: the long answers

[Back to the README](../README.md#questions-people-ask)

The README keeps a short answer to each question. These are the ones that need more room.

## Is this safe? Can it brick the TV?

Nothing is flashed, rooted or deleted; packages are disabled
in the user profile. The risk is disabling something the TV leans on, which is why lists are
applied in small batches with a restore script ready. The TV's own Settings → Apps page can
re-enable packages even without the tools.

If the TV no longer starts far enough for that, or for ADB, a factory reset from its recovery menu is
usually the way back ([#1578](https://github.com/Gathin23/cleanyourtv/issues/1578)):

- Everything these tools change lives in the user data the reset wipes, so every disabled app comes
  back and the new launcher is gone, along with your own apps and accounts.
- A TV that still restarts in a loop after the reset points to the hardware rather than the debloat
  ([#1592](https://github.com/Gathin23/cleanyourtv/issues/1592)).

[Troubleshooting](TROUBLESHOOTING.md#working-on-the-tv) has the cases seen so far.

## Isn't ADB access a security hole?

It is one explicit "allow this computer" tap on the TV, the
same as developing an Android app. Network ADB is plaintext on port 5555, and on firmware with
`ro.adb.secure=0` (or with it unset, on a debug build or Android 4.4) any device on the LAN gets a
shell, so close it when you are done:

1. On the TV: Developer options → turn **USB debugging**, **Network/ADB debugging** and
   **Wireless debugging** off, whichever the TV has, and revoke the USB debugging authorisations.
   - Wireless debugging listens on a port of its own and stays open when USB debugging goes off (seen
     on a Chromecast with Google TV 4K).
   - Note that port before you switch it off (`adb mdns services` lists it as `_adb-tls-connect._tcp`)
     and check it straight after, before the reboot: `python3 tools/adb-closed.py <tv ip> <port>` must
     say that port is closed.
   - After the reboot the old number reads closed either way: Wireless debugging takes a new port each
     time it starts.
2. Reboot the TV, then confirm the port is gone from your computer:
   `python3 tools/adb-closed.py <tv ip>` must say the port is closed.
   - Wireless debugging must still be off in Developer options, and `adb mdns services` must list no
     `_adb-tls-connect._tcp` entry for the TV; if it came back, switch it off and check its new port as
     in step 1.
   - With `nc -vz <tv ip> 5555`, only "Connection refused" counts: on macOS, Local Network privacy can
     block `nc` and make it fail with "No route to host" even while the port is open.

The launcher keeps working without debugging.

## Can it block ads inside apps too?

Partly, with Android's Private DNS (Android 9 and newer). Point
it at an ad-blocking resolver such as [AdGuard DNS](https://adguard-dns.io) and every app's ad and
tracker domains stop resolving, system-wide, surviving reboots. The trade-off: that resolver then
sees every DNS query the TV makes.

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

It does not remove YouTube ads, which come from the same servers as the videos.

When the TV reports "No internet", the resolver is down or unreachable and switching back restores
everything: `tools/tv.py 'settings put global private_dns_mode opportunistic'`.

- That line needs debugging back on, because Android TV's Settings app has no Private DNS page (not in
  AOSP, and not in Google TV 14 on a Chromecast with Google TV 4K).
- ADB goes to the TV's address, so it connects while the resolver is down.

A resolver that answers can still break one app on its own, so if a single app fails while the rest play, see
[one app refuses to play](TROUBLESHOOTING.md#one-app-refuses-to-play-while-the-rest-of-the-tv-is-online)
before you debug the app.

## Are there switches in the TV's own Settings worth flipping too?

A few. They need no ADB, the remote switches them back, and the debloat leaves them in place
(Usage & diagnostics and Apps only mode aside): their pages belong to Settings and Play services, which
every list keeps. The paths are Google TV's; other Android TV builds move some of them.

- **Settings → Privacy → Ads**: delete the advertising ID, or turn on Opt out of Ads Personalization. On
  Android TV it is under About → Legal information → Ads (in Device Preferences on some sets).
- **Settings → Privacy → Location** off. The launcher's network pill may then say "Wi-Fi" instead of the
  network's name.
- **Settings → Privacy → Usage & diagnostics** off, before the debloat. On a Chromecast with Google TV 4K
  the page comes from setupwraith, which most Google TV lists disable for the launcher swap, and on a
  [Hisense 50A6H4](../examples/hisense-smarttv-4k-ffm-android11/debloat.txt) it opened nothing after the
  debloat.
- **Settings → Network & Internet → Scanning always available** off. It lets Google's location service and
  other apps scan for Wi-Fi networks even while Wi-Fi is off, so it matters most on a TV on Ethernet.
- **Settings → Accounts & Sign In → your profile → Apps only mode**, where launcherx stays enabled
  (`stock_home_redirect`, or the Chromecast's `debloat-keep-cast.txt`). Google TV's own home then drops its
  personalised recommendations and casting still works, but Search and the Assistant switch off. The
  switch lives in launcherx, so a list that disables launcherx takes it away.

On Fire OS: Settings → Preferences → Privacy Settings, with Interest-based Ads, Device Usage Data and
Collect App Usage Data off. They sync with the Amazon account, so change them in the menu, not with
`settings put`.
