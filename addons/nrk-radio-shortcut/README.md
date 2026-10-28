# NRK Radio TV shortcut

NRK Radio is a phone/tablet app and does not publish an Android TV
`LEANBACK_LAUNCHER` activity. Plume therefore does not list it. This tiny
launcher-visible app supplies the TV category and opens the installed NRK Radio
package; it does not copy, modify or replace NRK Radio.

Build the debug APK:

```sh
./gradlew assembleDebug
```

Install it after `no.nrk.mobil.radio` is installed:

```sh
adb -s <tv-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

The shortcut package is `no.cleanyourtv.nrkradioshortcut`. Removing that package
removes only the shortcut and leaves NRK Radio installed.
