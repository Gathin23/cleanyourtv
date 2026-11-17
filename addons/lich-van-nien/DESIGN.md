# Lịch Vạn Niên add-on: design

Status: implemented (03/11/2026); the user-facing summary is in README.md.

## Intent

The owner wants to glance at Home from the bed and know whether today is a good day (hoàng đạo) or a
bad one (hắc đạo) and which hours are good, without pressing anything. Pressing OK on the tile opens
a calm full screen with the lunar date, the can chi of day/month/year, the 12 double-hours with the good
ones marked, and what the day suits or should avoid.

Success: the tile always shows today's verdict and good hours in Vietnamese with diacritics, it changes by
itself after midnight Vietnam time, the numbers agree with published Vietnamese lịch vạn niên, and the
screen is readable from the bed and driven only by the D-pad.

## Goals

- Home tile: today's "Hoàng đạo" / "Hắc đạo" plus the six good double-hours, visible without focus.
- Detail screen (layout approved: "Bản phác 3", see Visual below).
- Fully offline, UTC+7, no INTERNET permission, no background service.
- Unit tests for can chi, hoàng đạo day, giờ hoàng đạo, trực, checked against published days.

## Non-goals

- No changes to `launcher/`, `config.xml`, `work/theme-*`; no `tools/theme.sh`; no reinstalling or
  force-stopping `com.gathin.tvhome`. Tile placement on Home belongs to the theme session.
- No accessibility service (on this firmware a key-filtering one breaks hold-to-repeat volume).
- No screensaver, no notifications, no settings screen, no other locales (Vietnamese UI only).
- No 28 sao (nhị thập bát tú), no tuổi xung/hợp, no personal horoscope.
- Upstream: one folder under `addons/`, a row in `addons/README.md`; no change under `launcher/`.

## Anti-goals

- Wrong verdicts. A day shown as hoàng đạo that published calendars call hắc đạo is a failure even if
  everything else works.
- A tile that is stale (yesterday's verdict) after the owner wakes up.
- Anything that wakes the TV, runs continuously, or costs RAM while not on screen (TV has 1.8 GB).
- Busy, cheap-looking UI. The owner rejected the first two mockups as ugly; the approved look is restrained.

## Constraints (from the launcher contract, verified in source)

- Card rules (`docs/specs/card.md`): one card per package; label 1–24 and caption ≤32 chars from
  `A-Z a-z 0-9 space . & + ' -` only (no diacritics, no colon). Changing the label re-prompts the owner,
  so the label is fixed: **`Lich Van Nien`**. The decision is keyed by package + signer + folded label,
  not by component, so switching which alias carries the card does not re-prompt.
- The card must sit on an exported, enabled activity with `MAIN` + `LEANBACK_LAUNCHER`; an
  `activity-alias` qualifies (it is returned by the package-manager query like an activity).
- Caption only shows while the tile is focused and is cut to ~26 chars with the label, so the
  information lives in the **banner**. The launcher loads the banner per activity
  (`AppAdapter.loadBanner`: `ri.activityInfo.banner`, else the application banner), so each alias can
  carry its own banner.
- The launcher listens for `PACKAGE_CHANGED` and rebuilds tiles; enabling/disabling an alias with
  `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)` sends it. To be verified on the TV.
- Gradle project of its own like `addons/jewish-calendar`: minSdk 26, target/compile 34, Java 17,
  AGP 8.5.2, JAVA_HOME openjdk@17.
  Copy `gradlew`, `gradlew.bat`, `gradle/`, `.gitignore` from `addons/jewish-calendar`.

## How the tile changes daily

- 12 `activity-alias` entries, all targeting `CalendarActivity`, each with both intent filters
  (`LEANBACK_LAUNCHER`, `com.gathin.tvhome.category.DOCK_CARD`), the card `meta-data`, and its own
  `android:banner`. Variant = (good/bad day) × (hour set). Giờ hoàng đạo depend only on the day's chi,
  and chi pairs six apart share a set, so there are 6 hour sets:
  - Tý/Ngọ: Tý Sửu Mão Ngọ Thân Dậu
  - Sửu/Mùi: Dần Mão Tỵ Thân Tuất Hợi
  - Dần/Thân: Tý Sửu Thìn Tỵ Mùi Tuất
  - Mão/Dậu: Tý Dần Mão Ngọ Mùi Dậu
  - Thìn/Tuất: Dần Thìn Tỵ Thân Dậu Hợi
  - Tỵ/Hợi: Sửu Thìn Ngọ Mùi Tuất Hợi
- Exactly one alias enabled at a time (runtime state). `CalendarActivity` itself has no launcher filter
  (only aliases), so there is never a second tile. **All 12 aliases are declared `enabled="true"` in the
  manifest** and the app disables 11 at runtime. Reason (builder check): `Cards.java:293` rejects a card whose
  `ActivityInfo.enabled` is false, and that field may carry the manifest value rather than the runtime
  state; declaring all true makes the check pass whichever it is, while the package-manager query
  (flags 0) still skips runtime-disabled aliases. Cost: right after install, before the first run, all 12
  match; the launcher dedupes tiles by package + label and reads only the first card, so at worst the
  first-listed banner shows until the app runs once. The install step opens the app once with
  `am start` so the receivers and alarm are armed (a never-launched app is in stopped state).
- Known limit (builder check): the launcher keys saved tile order, dock rank and focus return by
  component (`AppAdapter.java:316`, `Order.compare`). The alias changes daily, so a position set with
  "Di chuyển" does not stick; the tile relies on the launcher's default placement (cards right after the
  dock apps). If `uniform_tiles` were ever on, banners would not show; the current theme does not set it.
- Order of operations when switching: enable the new alias first, then disable the old one, so the
  package never has zero launcher entries (which would drop the tile briefly or lose its place).
- When it runs (`DayUpdater`, idempotent; computes today in UTC+7 and enables the matching alias):
  1. a `BroadcastReceiver` for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`,
     `MY_PACKAGE_REPLACED`;
  2. an `AlarmManager.set(RTC, nextMidnightUtc7 + 1 min)` (inexact, no exact-alarm permission, does not
     wake the TV) re-armed on every run;
  3. `CalendarActivity.onResume`.
  A few minutes late after midnight is acceptable; the owner looks in the morning.
- Caption per alias (optional, ASCII): `Hoang dao` / `Hac dao`. Needs 2 card XML files.

## Calendar rules

All dates are civil dates in UTC+7. The day (and its can chi, verdict and trực) changes at midnight, as on
Vietnamese printed calendars; see "Left to the builder" for how 23:00–24:00 is drawn. Lunar date and leap months from the launcher's
algorithm (Hồ Ngọc Đức), copied into the add-on, not linked (`launcher/.../Lunar.java`, MIT, same repo).

- **Can chi of day**: JDN based. can = (JDN + 9) mod 10, chi = (JDN + 1) mod 12 (Giáp=0, Tý=0).
  Check: 03/11/2026 → Tân Tỵ.
- **Can chi of lunar year**: can = (Y + 6) mod 10, chi = (Y + 8) mod 12. 2026 → Bính Ngọ.
- **Can chi of lunar month**: chi = (month + 1) mod 12 (month 1 = Dần); can = (Y·12 + month + 3) mod 10.
  Leap month: same can chi as the month it repeats. 9/2026 → Mậu Tuất.
- **Hoàng đạo / hắc đạo day**: 12 stars in order Thanh Long, Minh Đường, Thiên Hình, Chu Tước,
  Kim Quỹ, Kim Đường (Bảo Quang), Bạch Hổ, Ngọc Đường, Thiên Lao, Huyền Vũ, Tư Mệnh, Câu Trận; the
  good ones are Thanh Long, Minh Đường, Kim Quỹ, Kim Đường, Ngọc Đường, Tư Mệnh. Star index =
  (dayChi − start) mod 12, where Thanh Long starts at Tý for lunar months 1 and 7, Dần for 2/8, Thìn for 3/9,
  Ngọ for 4/10, Thân for 5/11, Tuất for 6/12. Indexed by **lunar month number** (leap month uses its
  number), which is what most Vietnamese online calendars do. My call. Source of truth for tests: two
  published Vietnamese online lịch vạn niên sites; where they disagree on a date, that date is dropped from
  the tests and noted, and the rule follows the majority of checked dates.
  Check: 03/11/2026, month 9, Tỵ → Minh Đường → hoàng đạo.
- **Giờ hoàng đạo**: same 12-star cycle over the 12 hours, Thanh Long starting at Thân for Tý/Ngọ days,
  Tuất for Sửu/Mùi, Tý for Dần/Thân, Dần for Mão/Dậu, Thìn for Thìn/Tuất, Ngọ for Tỵ/Hợi. Yields
  the six sets above.
- **12 trực** (for nên/kiêng): Kiến, Trừ, Mãn, Bình, Định, Chấp, Phá, Nguy, Thành, Thu, Khai, Bế.
  index = (dayChi − solarMonthChi) mod 12, where the solar month follows the 12 tiết (Lập Xuân 315° starts
  Dần, Kinh Trập 345° Mão, Thanh Minh 15° Thìn, … Tiểu Hàn 285° Sửu). The tiết is the one in force at the
  start of the civil day in UTC+7 (sun longitude at 00:00 local; Meeus low-precision formula is enough).
  This makes the trực repeat on the day a tiết begins, as the books say.
- **Nên / kiêng**: a fixed table per trực, short Vietnamese phrases (3–5 "nên", 2–4 "kiêng"), written
  as a summary of the standard trực descriptions, not quoted from a book. The screen says nothing more
  authoritative than "theo trực".

## Detail screen (approved look: "Bản phác 3")

The mockup was a scratch page drawn outside the repository and is not kept here; [`screenshot.jpg`](screenshot.jpg)
in this folder is that screen on the tested TV, and the bullets below are what the build follows.

- Background: dark, soft (blurred photo darkened to ~35 % with a vignette in the approved look). The add-on
  cannot read the launcher's wallpaper; it ships **one** bundled background of its own (a dark gradient,
  or one small blurred JPEG < 150 KB of a nature photo the owner has rights to, e.g. from
  `work/theme-nature-calm/wallpapers` if its CREDITS allow; otherwise gradient).
- Type: serif (Android `serif` = Noto Serif, has Vietnamese) for the calendar words and numbers; light
  sans (`sans-serif-light`) letter-spaced uppercase for small captions, like the launcher header. One
  accent colour, muted gold `#E8C77D`; muted red `#D99A8A` only for "Kiêng".
- Left column: caption "THỨ BA · 3 THÁNG 11 · 2026"; huge lunar day "25" with "tháng Chín" and caption
  "ÂM LỊCH · <moon phase word>"; thin gold rule; "Ngày Hoàng đạo" (gold) or "Ngày Hắc đạo" (silver)
  with "sao <star> chiếu"; three columns Ngày / Tháng / Năm with can chi.
- Right: a 12-segment ring like a clock face, Tý at top, each segment labelled with the chi and the
  starting hour outside; good hours filled gold, others faint; a dot at the current hour; centre text
  "BÂY GIỜ / giờ <chi> / giờ tốt|giờ xấu · đến <h>:00". Drawn with a custom `View` on `Canvas`.
- Zodiac animals (owner's request, 03/11/2026): each ring segment shows a cute cartoon animal above the
  chi name, Vietnamese set 🐭 Tý, 🐃 Sửu, 🐯 Dần, 🐱 Mão, 🐲 Thìn, 🐍 Tỵ, 🐴 Ngọ, 🐐 Mùi, 🐵 Thân,
  🐔 Dậu, 🐶 Tuất, 🐷 Hợi; the current hour's animal is drawn large in the ring's centre. Art is the
  TV's own Noto Color Emoji font (`/system/fonts/NotoColorEmoji.ttf`, present on this TV), drawn with
  `Canvas.drawText`, so nothing is bundled. Bad-hour animals are drawn at reduced alpha.
- Bottom line: "Nên …" and "Kiêng …" for the trực, with "trực <name>"; right side hint
  "◀ ▶ đổi ngày · OK về hôm nay".
- Keys: Left/Right = previous/next day (ring then has no "bây giờ" dot, centre shows the date instead);
  OK/Center = back to today; Back = exit. Up/Down do nothing. No focusable widgets, the activity handles keys.
- Sizes for 3 m viewing on a 1920×1080 surface: smallest text ≥ 24 px, lunar day ~280 px, verdict ~100 px.
  Ring hour labels ≥ 30 px. Refresh the current-hour dot each minute while visible (Handler, stops onPause).
- Moon phase word from lunar day: 1 Sóc, 2–7 trăng non, 8 thượng huyền, 9–14 trăng khuyết, 15 rằm/vọng,
  16–22 trăng khuyết, 23 hạ huyền, 24–30 trăng tàn (simple mapping, my call).

## Banners (the 12 tile pictures)

- Approved look: dark warm gradient (good) or cool grey (bad); small "HÔM NAY" letter-spaced caption;
  big serif "Hoàng đạo" in gold `#ECD08E` or "Hắc đạo" in silver `#C2C7D2`; thin rule; the six good
  hours "Sửu · Thìn · Ngọ · Mùi · Tuất · Hợi" on one line below. No date and no moon on the tile
  (banners are fixed per variant).
- Generated as PNGs (640×360, `res/drawable-nodpi/banner_<good|bad>_<0..5>.png`) by a small script in the
  add-on folder (`tools/make_banners.py`, Pillow, a Vietnamese-capable serif/sans font from macOS or
  Google Fonts Noto Serif). Generated files are checked in to the add-on folder so the Gradle build does not
  need Python.
- App icon: a simple drawable (gold ring segment on dark) for the App info page / library.

## Package and files

- Package `com.tungpv.lichvannien` (my call), app label "Lịch Vạn Niên" (app label may have diacritics;
  only the card label is ASCII).
- `addons/lich-van-nien/`: `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew*`, `gradle/`,
  `.gitignore`, `local.properties`, `app/` (manifest, `CalendarActivity`, `RingView`, `DayUpdater`,
  `DayReceiver`, `VnCalendar` (pure Java, no Android imports, holds all the rules), `Lunar` copy, res),
  `README.md` with a Permissions section (`RECEIVE_BOOT_COMPLETED` only), `DESIGN.md`.
- Release build signed with the debug key like the neighbours.

## Tests (JUnit, `VnCalendarTest`)

Expected values must come from a published Vietnamese lịch vạn niên (e.g. lichvannien / xemngay sites,
or a printed lịch bloc), recorded in the test with the source and date checked. At least 6 dates spread
over 2025–2027, including: today 03/11/2026 (Tân Tỵ, Mậu Tuất, Bính Ngọ, Minh Đường hoàng đạo, giờ Sửu
Thìn Ngọ Mùi Tuất Hợi), a Tết day, a day in a leap month (06/2025), a day a tiết begins (trực repeats).
Plus exhaustive structural tests: every day of 2026 maps to exactly one of the 12 aliases; exactly six
good hours per day; day can chi advances by one each day.

## Verification on the TV

Build `./gradlew --no-daemon testReleaseUnitTest assembleRelease`, `adb -s <tv ip>:5555 install -r`,
press Home, answer "Show card" (or Settings hold-OK → Module cards → Shown), screenshot to
`shots/lvn-home.png` and the detail screen to `shots/lvn-detail.png`, send both to the owner. Simulate the
midnight switch by broadcasting the receiver's action via adb (explicit component) and confirm the tile
changes without reinstalling. Check `adb logcat -s TvHomeCards` for rejections. Add a short section at
the end of `work/README.md`.

## Left to the builder

- Exact wording of the 12 nên/kiêng lists (keep short, plain, no superstition beyond the trực tables).
- Whether giờ Tý 23:00–24:00 is counted on the current date's ring (default: ring shows hours of the civil
  date; 23–24 belongs to the Tý segment of the same date).
- Fine typography, spacing and gradient colours, within the approved look.
- Whether the background is a gradient or a bundled photo (prefer gradient if photo rights are unclear).
