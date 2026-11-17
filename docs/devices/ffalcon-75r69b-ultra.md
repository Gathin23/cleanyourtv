# FFalcon 75R69B Ultra (T966D5, Android 14): the boot ad goes, the launcher does not install

One owner report, 2026-11-17 ([#4122](https://github.com/Gathin23/cleanyourtv/issues/4122)), from a
single retail unit in China. Its build string is the one the
[TCL / FFalcon MT9655 China record](tcl-mt9655-cn-android14.md) carries on a different product, so read
that note for what the install lock is and this one for what a reader of *this* model gets: a working
network ADB and a launcher that the firmware refuses.

## Identify the unit

| Field | Reported value |
|---|---|
| Retail model | FFalcon (雷鸟) 75R69B Ultra, China |
| Product names | `T966D5`, `T966D5_4K_CN`; reported in the UI as `Smart TV Pro` |
| Android | 14 (API 34) |
| Build | `V8-0018T11-LF1V060.000666` |
| Fingerprint | `bu30a6_aosp-user 14 UTT2.250416.001 AU01 release-keys` |
| Stock Home | `com.tcl.cyberui/.MainActivity` |

`T966D5` was already the name in [#402](https://github.com/Gathin23/cleanyourtv/pull/402); no example
folder covers this product, and this report is the first one from a retail 75R69B Ultra.

## What worked

- **Network ADB.** This unit answers over the network, so the cable route the MT9655 record needs was not
  used here. The owner turned debugging on and the tools reached the set as usual.
- **The boot ad.** `com.tcl.bootadservice` - the full-screen ad before Home, which the TCL and FFalcon
  lists already carry ([the MT9655 list](../../examples/tcl-mt9655-cn-android14/debloat.txt) line 94) - was
  disabled with `pm disable-user --user 0` and was still disabled after one full reboot, with its process
  absent.
- **The rest of the set.** A Home-key test returned to the stock CyberUI, and Android still listed the
  TV inputs.

## What did not: the launcher

A launcher built from this repository - the v3.5.0 tag, `d11bd68`, `assembleRelease` - was installed with
a plain `adb install`, and the firmware refused it before any Home handover:

```
INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed
verifier: signature not match for tcl com.gathin.tvhome, will not be installed !
```

The unit reports its install configuration as `launcherStrategy` enabled with an empty package allowlist.
The app is absent after the refusal and the default Home is still `com.tcl.cyberui`, so on this firmware
the debloat is the whole of what this repository can do. The owner asked whether a supported way to
install and select the launcher exists; this note records the refusal rather than a route around it,
because working around a maker's install check is one of the things this repository keeps out.

## Undo

One package was disabled by hand and no list was applied, so it goes back the same way:

```bash
tools/tv.py 'pm enable --user 0 com.tcl.bootadservice'
```

## What was not checked

- The boot screen itself was not watched, so "the boot ad is gone" rests on the package and process state
  after the reboot, not on a seen boot.
- One package and one reboot: this is not a several-day list, so no `examples/` folder was made for it and
  the rest of the MT9655 list is untested on this model.
- Nothing was tried for the install refusal beyond the plain `adb install`, and nothing here should be
  read as one.
