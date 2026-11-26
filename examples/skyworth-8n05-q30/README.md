# Skyworth 8N05 Q30 / 65Q30 (Coocaa CCOS, Android 8.0)

A China-market Coocaa set whose Home cannot be replaced and whose debloat does not survive a boot. The
list is worth having anyway: the owner of this unit went through the report in
[#2337](https://github.com/Gathin23/cleanyourtv/issues/2337), and everything it disables is vendor
preload that no CCOS screen needs.

`debloat.txt` disables 17 packages. `com.tianci.appstore` is in the file commented out: the home
screen's My Apps button opens `AppStoreHomeActivityV3` in it, so with it off the button does nothing.
Uncomment the line if you do not use My Apps.

## What this set cannot do

- **Home stays Coocaa's.** `set-home-activity` answers `cannot be home on user 0`, the read-only
  `DEFAULT_HOMEPAGE` puts `persist.service.homepage.pkg` back at every boot, and the build binds no
  third-party accessibility service, so neither the Home-key redirect nor a homepage shim gets the
  reference launcher in front of it. Do not disable `com.tianci.movieplatform`: an owner on the same
  firmware family got a black screen at boot from that.
- **The debloat does not survive a reboot.** 17 of the 18 packages came back on by themselves at the
  next boot, and only the app store stayed off. Apply the list again after a boot, or accept it as a
  cleanup that lasts one session.
- **The boot ad is a file, not a package.** `persist.sys.bootvideo.time` goes back to 15 by itself and
  `/data/local/bootvideo.ts` cannot be removed without root.

Apply and undo with the list's own two lines:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/skyworth-8n05-q30/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/skyworth-8n05-q30/debloat.txt
```

The device, the three closed Home routes and what has to stay on the set are written down in
[docs/devices/skyworth-8n05-q30.md](../../docs/devices/skyworth-8n05-q30.md). The list is
@linzhijie30618's session on this unit, read before the first reboot; it is one session rather than a
several-day run, and nothing here was retested on a second CCOS build.
