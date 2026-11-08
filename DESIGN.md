---
name: elppA TV
description: Cinematic native Android TV Home with horizontal app and media rails.
colors:
  text: "#F4F2EF"
  muted: "#CDCBC8"
  surface: "#191D25"
  background: "#090B10"
  control-initial: "#34384077"
  icon-initial: "#292D3544"
  control-rest: "#22262E77"
  control-focus: "#353943CC"
  media-label: "#090B10BB"
  icon: "#FFFFFF"
  dialog-accent: "#EEECE8"
typography:
  display:
    fontFamily: "Outfit, sans-serif"
    fontSize: "46px"
    fontWeight: 400
  clock:
    fontFamily: "Outfit, sans-serif"
    fontSize: "25px"
    fontWeight: 400
  heading:
    fontFamily: "Outfit, sans-serif"
    fontSize: "15px"
    fontWeight: 400
  action:
    fontFamily: "Outfit, sans-serif"
    fontSize: "14px"
    fontWeight: 400
  body:
    fontFamily: "Outfit, sans-serif"
    fontSize: "13px"
    fontWeight: 400
  app-label:
    fontFamily: "Outfit, sans-serif"
    fontSize: "10.5px"
    fontWeight: 400
  media-label:
    fontFamily: "Outfit, sans-serif"
    fontSize: "10px"
    fontWeight: 400
rounded:
  image: "8px"
  card: "10px"
  empty-focus: "12px"
  action-focus: "22px"
  icon-focus: "24px"
spacing:
  image-inset: "3px"
  label-inset: "4px"
  card-trailing: "10px"
  control-icon-inset: "10px"
  text-left: "48px"
components:
  hero-action:
    backgroundColor: "{colors.control-initial}"
    textColor: "{colors.text}"
    typography: "{typography.action}"
    width: "155px"
    height: "36px"
  next-pick:
    backgroundColor: "{colors.control-initial}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    width: "112px"
    height: "36px"
  icon-control:
    backgroundColor: "{colors.icon-initial}"
    padding: "{spacing.control-icon-inset}"
    size: "42px"
  app-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    width: "116px"
    height: "76px"
  media-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    width: "172px"
    height: "97px"
---

# Design System: elppA TV

## Overview

**Creative North Star: "Cinematic Home"**

The supplied cinematic Apple-TV-like mockup remains the visual authority: landscape artwork fills the television, a left/bottom scrim carries readable text, and horizontal app and media rails keep the next action close. This is a remote-first native Android TV surface for the Sony KD-50X75, viewed from a sofa.

Warm-white Outfit text, charcoal surfaces and restrained focus feedback let provider artwork supply the colour. Native Android dialogs handle occasional settings and recovery tasks. This document records the implemented source; it does not establish visual acceptance or certify the Sony device.

**Key Characteristics:**

- Full-bleed still artwork with left and bottom scrims.
- A large show title, explicit action and horizontal landscape rails.
- Visible D-pad focus, remembered row position and bounded motion.
- Honest loading, permission, empty and opt-in sample states.

Source of truth: `launcher/elppa/src/main/java/com/gathin/tvhome/ElppaUi.java`, `ElppaHomeActivity.java`, `HomeRail.java`, `ArtworkLoader.java` and `PreviewContent.java`; bundled fonts and vector icons come from `launcher/app`. Scope is the separately installable `:elppa` fork, not the upstream `:app`.

## Colors

The palette is warm white on cool black and charcoal, with translucent neutral controls over artwork. Frontmatter hex values use CSS `#RRGGBBAA` where alpha is present; source Android integers use `0xAARRGGBB`.

### Primary

- **Warm-white text:** Primary title, controls, progress and focus outline use `text`.
- **Provider artwork:** The image supplies the changing colour; there is no fixed chromatic brand accent.

### Neutral

- **Midnight background:** `background` remains behind missing or loading artwork.
- **Charcoal card:** `surface` is the initial/rebound card fill.
- **Quiet supporting text:** `muted` carries metadata and Preview/status text.
- **Translucent control states:** `control-initial` and `icon-initial` are construction fills; focus changes use `control-focus` and `control-rest`.
- **Media label scrim:** `media-label` sits behind the focused media card's caption.
- **Platform accents:** Vector icons use `icon`; the native dialog theme uses `dialog-accent`.

**The Artwork Colour Rule.** Provider artwork supplies colour; chrome stays neutral.

## Typography

Bundled **Outfit Regular (400)** is the Home face, resolved through `UiFont`. Existing Vietnamese resources request Android `sans-serif`; missing-glyph coverage otherwise follows Android font fallback. Native dialogs inherit `Theme.Material.NoActionBar` and its font resource. There is no separate bold display treatment, explicit line-height token or tracking token.

Frontmatter dimensions describe **logical pixels at the 960×540 reference canvas and font scale 1**. They are not Android dp/sp values or a web stylesheet. Home text uses pixel sizes after viewport scaling. `TextView` font padding is disabled by `ElppaUi.text`; the separately constructed clock retains its platform default.

| Role | Implemented use and fitting |
| --- | --- |
| Display | Hero title; two lines, end ellipsis; auto-size from 32 logical units to `46 × fontScale` before viewport scaling. |
| Clock | 12/24-hour locale setting, with `h:mm a` or `HH:mm`; no date or decorative label. |
| Heading | Continue watching & Play next shelf heading. |
| Action | Hero action and empty-state message; action is one line with end ellipsis after selecting a title. |
| Body | Subtitle, Next pick and media fallback title; subtitle has two lines and auto-sizes from 12 to `13 × fontScale`. |
| App label | Single-line caption, visible when the app card is focused. |
| Media label | Single-line focused media caption and top Preview/status label. |

Most text follows `logical × viewportScale × min(fontScale, 1.5)`. Title and subtitle subsequently use Android uniform auto-size with the uncapped configured font scale as the maximum; available space can shrink them to the stated minimum. This is adaptive behaviour, not proof that every title or accessibility scale fits.

## Layout

The native `FrameLayout` uses a centred 960×540 landscape canvas. `viewportScale = min(width / 960, height / 540)`; positions and sizes are rounded after scaling and centred in any unused viewport area. The backdrop and both scrims fill the entire window. The reference canvas provides fixed safe insets; there is no web breakpoint system or touch/mobile layout.

| Region | Default logical bounds `(x, y, width, height)` | When `fontScale > 1.05` |
| --- | --- | --- |
| Clock | `(36, 24, 250, 32)` | Height 44 |
| Inputs / network / settings | x 762 / 820 / 878, y 20; each 42×42 | Unchanged |
| Hero title | `(48, 100, 515, 112)` | y 82, height 140 |
| Subtitle | `(50, 216, 500, 28)` | y 222, height 36 |
| Hero action / Next pick | x 48 / 216, y 253; 155×36 / 112×36 | y 264 |
| App rail | `(42, 301, 882, 87)` | y 308 |
| Shelf heading | `(48, 394, 850, 20)` | y 399, height 24 |
| Media rail | `(42, 416, 882, 106)` | y 423 |
| Empty-state action | `(48, 426, 864, 77)` | Unchanged |
| Preview/status | `(330, 28, 400, 28)` | Unchanged |

Both rails are horizontal AndroidX `RecyclerView`s. App tiles are compact landscape containers; media tiles are approximately 16:9, with centre-cropped art inset inside the frame. Tile margins are 3 left, 5 top, 10 right and 4 bottom logical units. App art is fitted and padded rather than cropped. Enlarged-text layout moves the lower rails down; fixed tile and caption heights remain a device review point.

## Elevation & Depth

Depth comes from photographic artwork, scrims and focus scale; Home defines no shadow or blur. The left-to-right scrim uses midnight at alpha F2, 99 and 00. The top-to-bottom scrim uses alpha 00, 00, D9 and FF. These are Android `GradientDrawable` colour sequences across the window.

Focus uses a warm-white **2 device-pixel** stroke, brighter translucent fill and **1.035×** scale. The scale animates over **130 ms** using Android's animator default interpolator; no custom easing is declared. It snaps to the same focus scale when motion is disabled. Cards disable RecyclerView item animations and allow focus enlargement outside their bounds.

**The Remote Focus Rule.** Every actionable Home control has a visible focus treatment and a D-pad route back to the app dock.

## Shapes

Rounded rectangles frame cards and cropped art. The frontmatter radii describe the scaled image/card and focus treatments. Hero controls become softly rounded pills; the utility controls are round at their reference size. Icons are bundled vector paths, not text glyphs.

**Not canonized:** button/icon construction currently uses a literal 24-pixel radius, while focus callbacks use scaled radii; the empty-state horizontal padding is a literal 20 pixels. The focus stroke is also fixed in device pixels. These source inconsistencies are recorded for review, not prescribed for new surfaces. No Java changes are part of this documentation pass.

## Components

### Hero and actions

A full-window `CENTER_CROP` still image sits behind left-aligned title, provider metadata and one explicit action. Its label is `Continue` when progress exists, otherwise `Open in <provider>`; Next pick appears only with multiple recommendations. With no content, the action opens content access or All apps. There is no decorative eyebrow.

Hero changes crossfade over **250 ms** when a previous image exists, the app Animations preference is on and system animators are enabled. Explicit media selection can crossfade too. Automatic rotation is a separate preference (default on): every **15 seconds**, only while Home is active, has window focus and the app rail holds focus. It pauses over hero controls, the media rail and dialogs. Turning animations off removes interpolation; it does not itself turn rotation off.

### Horizontal app dock

Each tile opens an app; long press offers move left/right, pin, hide/show and app info. All apps remains at the end of the dock. The dock remembers its last position, initially the first tile. Labels become visible on focus, while accessible names remain attached to tiles. Up goes to the hero action; Down goes to the media rail or its empty-state action.

### Continue watching & Play next

Landscape tiles use source artwork, title fallback and a focused title/provider caption. Published progress appears as a two-unit-high warm-white bar; it is not inferred from viewing. Focusing a tile updates the hero. Click opens the title, and long press offers local dismissal. Horizontal edges retain focus; Up returns to the dock and Down stays within the bottom region. Updates preserve focused item identity where possible and otherwise clamp the remembered index.

### Utility controls and native dialogs

Inputs, network and settings are focusable vector buttons above the hero. Network state uses Wi-Fi/Ethernet artwork and reduced icon alpha when offline. D-pad Down enters the hero action; hero Up returns to Settings. Settings, app management, source selection, diagnostics, access explanations and error messages use native `AlertDialog` surfaces, not a custom modal system. Platform dialogs and external settings may differ by firmware.

### Honest empty, error and Preview states

Missing permission explains app-published TV listings and offers access recovery. Loading, unreadable content, no shared titles and failed launch each have distinct text or actions. Artwork failure leaves the dark background and title fallback usable. Preview mode is **off by default**, enabled only in Settings and labelled **“Preview · sample content”** at the top. Each bundled title is identified as a sample; activating it opens a Preview explanation instead of playback.

**The Honest Preview Rule.** Sample titles require explicit Preview mode and remain visibly labelled; they never imply real watch history or launch playback.

### Native platform assumptions and evidence

The implementation is Java on Android API 26+, compiled/targeted at API 34, with a landscape Leanback/Home activity and AndroidX RecyclerView. It reads accessible `TvContract` Watch Next/preview rows; availability and permission behaviour depend on TV firmware and source apps. Artwork accepts HTTPS, content and app-resource URIs, with two workers, a bounded queue, a 16 MiB decoded cache, a 48 MiB private disk cache and capped decoding. Pausing Home invalidates queued result delivery; the system does not render video or realtime blur.

Stock Google Assistant microphone handling and phone Remote Service remain platform-owned. Only a SEARCH key actually delivered to this activity invokes `ACTION_ASSIST`. Inputs use the native picker when available, then Android TV input discovery; Sony settings open through system intents. These routes are implementation assumptions until checked on the KD-50X75.

Debug and release builds passed for elppA; both app modules passed release unit tests (322 total) and lint with zero errors. The software Android TV emulator did not reach a usable ADB state, so it supplied no valid native captures. Native visual acceptance, enlarged-text readability, actual Sony rendering, physical mic/remote, phone remote, input/settings routes, standby and reboot remain **pending**. No browser detector or HTML screenshot is evidence for the native app.

## Do's and Don'ts

### Do:

- Do preserve the supplied cinematic composition and horizontal cards.
- Do reuse bundled Outfit through UiFont, including the existing system-family locale fallback.
- Do retain readable title/action text when artwork is missing, permission is denied or a provider publishes no usable rows.
- Do respect the app animation preference and system animator setting; keep automatic rotation separate from animation.
- Do test both default and enlarged text on the actual television before accepting the visual result.
- Do leave physical microphone, phone remote, TV inputs and Sony settings with their platform services.

### Don't:

- Don't use Space Grotesk, decorative eyebrows, neon glow or realtime blur.
- Don't replace the horizontal shelf with a portrait poster grid.
- Don't present app-published recommendations as Google's personalised feed or universal OTT history.
- Don't infer Sony device compatibility, physical remote success, readability or performance from a successful build or emulator run.
- Don't copy the literal-pixel inconsistencies noted below into new components.
