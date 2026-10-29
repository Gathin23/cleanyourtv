# Helping someone in another home

Everything in this repo assumes your computer and the TV are on the same Wi-Fi. When the TV is at a
parent's or a friend's house, put a private [Tailscale](https://tailscale.com) link between your
computer and their home. The TV then behaves exactly as if it were on your network: the prompt,
`tools/tv.py`, screenshots and the launcher install all work unchanged, with a `100.x.y.z` address
instead of the local one. Tailscale's personal plan is free.

**Never forward port 5555 on their router to the internet.** ADB is full control of the TV, and
bots actively scan the internet for open ADB ports.

## What you need

- Tailscale on your computer, signed in. The Mac and Windows apps pick up subnet routes on their own;
  on Linux also run `sudo tailscale set --accept-routes`. Your computer must not be a
  [tagged](https://tailscale.com/kb/1068/tags) device, since tagged machines can't reach shared ones.
- Someone at the TV with the remote, on the phone with you for about twenty minutes.
- One of the two setups below.

## Option A: Tailscale on the TV

The simplest setup when the TV can run it:

- **Android TV and Google TV:** Tailscale from the Play Store.
- **Fire TV:** Tailscale from the Amazon Appstore, on most devices from 2018 on. Not on the Fire TV
  Stick 4K Select, which runs Vega OS (and that one can't be debloated over ADB anyway).

Opening the app shows a QR code. Then either:

1. **They keep it in their own tailnet and share the TV with you** (recommended for family). They
   scan the code with their phone and sign in with their own free account. Then, in the admin
   console (Machines → the TV → **Share**), they send you an invite and you accept it. You see that
   one TV and nothing else in their home, and they can revoke it any time.
2. **It joins your tailnet.** They scan the code and you sign in on their phone with your account.
   Quicker, but the TV is now one of your devices until you remove it.

The TV's `100.x.y.z` address is shown in the Tailscale app on the TV and in your machine list.

## Option B: a helper device as a subnet router

Use this when Tailscale won't install on the TV, or they'd rather not have it there. Another device
in their home forwards your connection to the TV's normal local address:

- a Mac, Windows PC or Linux machine that stays on during the session, or a Raspberry Pi for a
  permanent setup;
- an Apple TV, through the Subnet Router option in its Tailscale app;
- a Synology NAS, with Tailscale from Package Center. Its Tailscale page (DSM, or port 5252) has a
  Subnet router section, but it's read-only when you open it from a tagged device. Without SSH, a
  one-off Task Scheduler task (Control Panel → Task Scheduler → Create → Triggered Task →
  User-defined script, user root, then Run and delete it) runs
  `/var/packages/Tailscale/target/bin/tailscale set --advertise-routes=192.168.1.0/24`; the
  setting persists;
- not a phone: the Android and iOS apps can't route their home network.

The helper has to be in **your** tailnet, because shared machines don't carry their subnet routes
into other tailnets. (If they already run a tailnet, at an office say, the reverse works too: they
add you as a user, you add their tailnet as a second account in your Tailscale app and switch to
it for the session.) Install Tailscale on it and send yourself the login link it shows; open it
signed in to your account. Then advertise their home network (the first three numbers of the TV's
address, from the TV's network settings):

```bash
# Mac (standalone app without the CLI: /Applications/Tailscale.app/Contents/MacOS/Tailscale set ...)
sudo tailscale set --advertise-routes=192.168.1.0/24
# Windows, PowerShell as administrator, after enabling IP forwarding
tailscale set --advertise-routes=192.168.1.0/24
# Linux, after enabling IP forwarding (https://tailscale.com/kb/1019/subnets)
sudo tailscale set --advertise-routes=192.168.1.0/24
```

Approve the route in your admin console: Machines → the helper → Subnets → Edit. You then reach the
TV at its local address, e.g. `TV_HOST=192.168.1.50`.

**The two networks must not overlap.** If their home uses the same range as yours (both
`192.168.1.x`, say), your computer can't tell their TV from a device at home. Use option A, or
connect from another network.

## Connecting

Ask them to turn on debugging exactly as in the [Quick start](../README.md#quick-start), then:

```bash
TV_HOST=100.x.y.z tools/tv.py --probe
```

They accept "Allow USB debugging?" on the TV while you're on the phone. The rest of the prompt
runs as normal; tell your assistant the TV is remote and give it this address, so it doesn't
scan your own network for it.

**TVs that only offer Wireless debugging** (some Google TV sets): mDNS discovery doesn't cross the
tunnel, so they read you three things from Developer options > Wireless debugging: the IP address
and port on that screen, and, under Pair device with pairing code, the pairing port and the code.
With the stock `adb`:

```bash
adb pair 100.x.y.z:<pairing port> <code>
adb connect 100.x.y.z:<connect port>
adb -s 100.x.y.z:<connect port> tcpip 5555
TV_HOST=100.x.y.z tools/tv.py --probe
```

With option B, use the TV's local address instead of `100.x.y.z`.

## Things that matter more when you aren't there

- **Wi-Fi is your only way in.** Anything that drops the TV off Wi-Fi drops you too, and only
  someone at the TV can fix it. On Google TV, check who owns the saved network before disabling the
  setup wizard (`dumpsys wifi | grep -o 'cname=[^ ]*'`, see
  [TROUBLESHOOTING.md](TROUBLESHOOTING.md)). If it says setupwraith, have them forget and re-join
  the network in Settings first.
- **Ask, don't guess.** The prompt's checklist questions (which apps, the dock, casting, voice,
  updates) are theirs to answer, and so is what's plugged into the USB ports.
- **Leave the restore script with them**, or at least keep it where you can run it the next time
  you're connected.

## Afterwards

Close ADB as the [README](../README.md) FAQ "Isn't ADB access a security hole?" does, and check it
before you remove the access: once the tunnel is gone, nobody can check from outside their home.
`<tv ip>` is the address you connected to: `100.x.y.z`, or the TV's local address with option B.

1. They switch off **USB debugging**, **Network/ADB debugging** and **Wireless debugging** in
   Developer options, whichever the TV has, and revoke the USB debugging authorisations. The launcher
   keeps working. If you connected through Wireless debugging, have them read you the port on the
   Wireless debugging screen again just before they switch it off; mDNS doesn't cross the tunnel, so
   only they can see it. Don't reuse the connect port from Connecting: Wireless debugging takes a new
   port each time it starts, and on a
   [TCL 65Q7D Pro](../examples/tcl-q7c-google-tv/README.md#connecting-wireless-debugging-only-and-its-port-moves)
   it changed as soon as `tcpip 5555` restarted adbd. Check that port straight after Wireless
   debugging goes off, before the reboot: `python3 tools/adb-closed.py <tv ip> <port>` must say that
   port is closed.
2. They reboot the TV. Once it is back on the tunnel, `python3 tools/adb-closed.py <tv ip>` must say
   the port is closed, and Wireless debugging must still be off in Developer options. If it came
   back, they switch it off and you check its new port as in step 1. With option A they may have to
   open Tailscale on the TV again first: on an
   [XGIMI Horizon S Pro](../examples/xgimi-horizon-s-pro/debloat.txt) it did not come back after a
   reboot by itself. Until it is back, "did not answer" proves nothing.
3. Then remove the access: they revoke the share, or you remove the TV or the helper from your tailnet
   (or turn off its routes) in the admin console. Uninstall Tailscale from the TV unless they use it.

Did this on a TV where something worked differently? Add a line here in a pull request.
