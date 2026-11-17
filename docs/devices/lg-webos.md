# LG webOS: NU850B (webOS 26) reconnaissance, settings and DNS filtering

> **TL;DR: this repo cannot debloat an LG webOS TV or replace LG's Home.** There is no ADB on
> retail firmware, so nothing in `tools/` or `examples/` applies. What works without root is the
> TV's own privacy settings plus DNS filtering on your network, with a firewall rule so the TV
> cannot go around the filter. For the settings, see
> [cleanyour.tv/not-android](https://cleanyour.tv/not-android#lg-2026). For alternative Home screens
> built for webOS, see [SHOWCASE.md](../../SHOWCASE.md#other-platforms-fire-tv-and-lg-webos).

One owner's notes from a Mac on the same network, reported in
[#3092](https://github.com/Gathin23/cleanyourtv/issues/3092) by
[@joaomj](https://github.com/joaomj) between 2026-10-30 and 2026-11-04. This is not a port and not a tested
list. Nothing in `tools/` speaks webOS: `tools/tv.py` drives `adb`, and this set has no `adb` to
connect to.

| Field | Observed value |
|---|---|
| Retail model | 65NU850BPSA, 65" NANO, 2026 |
| System | webOS 26, build `11.0.1-8912` |
| Software version | `43.21.87` (updated from `43.04.24` on 2026-10-30) |
| Platform id | `HE_DTV_W26N_AFADATAA` |
| Network | Wi-Fi |

## What answers without ADB

| Port | State | What it is |
|---|---|---|
| 3000 | open | SSAP, LG's remote-control websocket |
| 3001 | open | SSAP over TLS |
| 8008 | open | the built-in Google Cast receiver (`/setup/eureka_info`) |
| 5555 | closed | no ADB on retail firmware |

- **SSAP** answers after the owner accepts the pairing prompt on the TV. `listApps` returned 151
  installed apps. The same socket sends remote keys and launches apps by id.
- **Google Cast** on 8008 reports crash and usage reporting as on
  (`opt_in: {crash: true, stats: true}`). A `set_eureka_info` request to turn them off returned
  HTTP 403, and the TV settings have no entry for them.

## What that rules out

- **No `pm disable-user`.** LG's apps cannot be disabled from outside on retail firmware, and every
  list in `examples/` depends on that.
- **No screenshot.** There is no capture API over SSAP, so the remote-key-and-screenshot loop
  that `tools/tv.py --screenshot` drives has no webOS counterpart.
- **No Home takeover.** No third-party app can take the Home button on webOS. The webOS editions in
  [SHOWCASE.md](../../SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) open from the app bar or a
  Quick Access key, not from Home.

## The settings, with the remote

Every switch on the LG 2026 list on
[cleanyour.tv/not-android](https://cleanyour.tv/not-android#lg-2026) was found and applied on this set
on 2026-11-04:

- Live Plus (ACR) off.
- The optional User Agreements declined: viewing information, voice information, interest-based
  advertising, marketing, Who.Where.What?. Do Not Sell turned on.
- Limit AD Tracking on and the AD ID reset.
- Home Promotion, Content Recommendation, Screen Saver Promotion and AI Recommendations off.
- SDDP and Wake On LAN off, under Network.

This set has no Hands-free Voice setting. Turning off voice information disables voice search.
A firmware update can reset these switches, so check them again after each update.

## AdGuard Home for the TV only

The settings stop collection on the TV. DNS filtering stops the uploads that remain. This set
resolves through an [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome) instance that the router
gives out over DHCP. The TV is a named client, so its rules do not affect the other devices on the network:

1. In AdGuard Home, go to Settings > Client settings > Add client. Use the TV's fixed address as the
   identifier and `lg-tv` as the name.
2. Use the TV normally for a few days, then read its query log before you add rules.
3. Add the rules under Filters > Custom filtering rules, one domain at a time, and test each app
   after each rule.

These rules cover the advertising and measurement names in this set's query log. Each name answers `0.0.0.0`
when it is resolved from the TV:

```text
||lgads.tv^$client='lg-tv'
||lgsmartad.com^$client='lg-tv'
||ad.lgappstv.com^$client='lg-tv',important
||alphonso.tv^$client='lg-tv'
||lgsmartplatform.com^$client='lg-tv'
||ottera.tv^$client='lg-tv'
||elastic.lgwebostv.com^$client='lg-tv'
```

On this set, the global blocklists already blocked `lgsmartad.com`, `ad.lgappstv.com` and `alphonso.tv`,
so the owner added only the other four as custom rules.
`ottera.tv` also serves LG's free channels, so blocking it can stop them.

**Keep these resolving**, or add an `@@` exception for the TV if a global list catches them:
`us.security.lgtvsdp.com` (the clock and service discovery), `lgappstv.com` (Content Store),
`ngfts.lge.com` and `aic-ngfts.lge.com` (store images), and `connectivitycheck.gstatic.com`. The not-android guide
reports that blocking `us.lgtvsdp.com` resets the clock to 2019 after a reboot and breaks several
apps.

**Do not block `wiselg.com`.** It is LG's Home recommendation service, and it is the largest source of
lookups from this set. Blocking it looks like a good way to cut LG data, but webOS checks it before other
features work:

- **Apps screen.** While `wiselg.com` is blocked, Apps shows
  `Service is temporarily unavailable. Retrying E5.48.XV`.
- **Prime Video.** After a TV restart with `wiselg.com` blocked, every title played for about 5 seconds
  and then froze. The pre-roll ad played. No Amazon name was blocked at that point. Allowing
  `wiselg.com` for the TV fixed playback. The query log then showed the TV fetching video from Prime
  Video's servers, subtitles and seek previews.
- **LG account and app services.** `br.membership.lgwebostv.com` and `br.app.lgwebostv.com` appeared in
  the query log only after `wiselg.com` was allowed.

To keep the other rules, add `@@||wiselg.com^$client='lg-tv'`. The cost is that LG gets the Home
recommendation traffic again.

Prime Video also reports playback to `global.telemetry.insights.video.a2z.com`, and global blocklists
block it. The owner added `@@||telemetry.insights.video.a2z.com^$client='lg-tv'` while looking for the
cause. Playback still froze with that exception until `wiselg.com` was allowed. Whether Prime Video needs
the exception after that was not tested. `minerva.devices.a2z.com` (Alexa device metrics) stayed
blocked, and playback worked.

Two things that misled testing on AdGuard Home `v0.107.79`:

- Turning off filtering for the client did not stop the `$client` rules. Only an `@@` exception or
  removing the rule changed the answer.
- The TV caches DNS answers itself. A name blocked after the TV looked it up can still resolve on
  the TV until the cache expires. Send a raw query to the AdGuard address to see the real answer.

## The firewall rule: stop the TV reaching other resolvers

A DNS filter holds only if the TV cannot ask another resolver. Smart TVs can use resolvers built into
their firmware instead of the one that DHCP gives them. On your router, add a rule for the TV's address,
LAN to WAN, action drop:

| Protocol | Destination | Port | Blocks |
|---|---|---|---|
| UDP | any | 53 | plain DNS to public resolvers |
| TCP | any | 53 | plain DNS over TCP |
| TCP | any | 853 | DNS-over-TLS |

AdGuard Home is on the same subnet, so the TV's queries to it do not go through the router's firewall,
and the rule does not block them. If the TV has a global IPv6 address, add the same rule for
IPv6. Also make sure that the router does not advertise its own IPv6 DNS servers to the TV.

Check: after a TV restart, the AdGuard query log still shows the TV's lookups, and every app you
use still plays.

**Tested on this set on 2026-11-11.** The three rules were added on the router as IPv4 rules for the
TV's address. After a TV restart, the TV's LG, Amazon and Google lookups still appeared in the AdGuard
query log, the LG advertising and measurement names were still blocked, and Prime Video played
for more than a minute. Netflix and YouTube were not tested with the rules on. No hardcoded resolver
was seen in the query log, so this page does not claim that this firmware has one. The rules are
here because without them, the filter depends on the firmware choosing to use it.

On the router used here, the rule form set the source port to `1` by default, so the rule matched
only packets from port 1 and blocked nothing. Set the source port to any, and leave the TCP flags
unmarked.

## What this report does not establish

- One set, one firmware, one region, one Wi-Fi network.
- DNS sees names, not connections. A service that connects by IP address does not show in the
  query log and is not blocked by these rules.
- Whether SDDP off breaks casting from a phone to this set.
- Whether blocking `wiselg.com` also stops the automatic clock. The owner saw automatic time fail
  with the rules on, but the query log never showed the TV asking for its clock server, so the cause is
  not confirmed.
- The domain lists reflect this set's query log from 2026-10-30 to 2026-11-01. Names change.

If you have a webOS set from another year or region, the query log and the results with the firewall
rule in place are the useful data points.
