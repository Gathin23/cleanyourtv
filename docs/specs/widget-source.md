# Home widget source, spec 1

The home widget (`home_widgets`) is one glass panel under the top buttons that turns through pages: a debugging
warning, RAM and storage in use, and a page from any add-on that offers one. The panel has one fixed size for every
page; a `line` too long for one line at the normal size is shown on two lines in a smaller font, then cut. It is not
an Android AppWidget: the launcher draws every page itself, from text.

This document is the contract for an add-on that offers a page. It extends the module card
([`card.md`](card.md)): an add-on that already declares a card adds one attribute and one read-only provider. A
launcher that does not know this spec ignores both, so the card keeps working everywhere.

## Declaration

In the card's XML resource, add `live="true"`. Keep `spec="1"`: a launcher drops a card whose spec it does not read.

```xml
<card spec="1" label="News" caption="Headlines" accent="#FF8A5B" live="true" />
```

Then declare a provider at the authority `<package>.card`, exported, with no permission:

```xml
<provider
    android:name=".CardProvider"
    android:authorities="com.example.news.card"
    android:exported="true" />
```

The launcher reads the provider only if the authority is exactly `<package>.card` and the provider belongs to the
package that declares the card. No other address is ever read.

## The row

`query(content://<package>.card)` returns the page to show now as its first row; further rows are ignored. No row
means "nothing to show now", and the widget skips the page.

| Column | Type | Required | Rule |
| :- | :- | :- | :- |
| `caption` | text | yes | A short line under the card rules: at most 32 characters from `A-Z a-z 0-9` space `. & + ' -`. The widget shows it when `line` is missing. |
| `line` | text | no | The widget's main line, at most 80 characters, any script. |
| `kicker` | text | no | The small heading above it, at most 32 characters. Missing: the card's label. |
| `meta` | text | no | The detail line under it, at most 60 characters. |
| `alert` | integer | no | `1` shows the page in red: something needs a look. |
| `urgent` | integer | no | `1` holds the widget on this page until the row says `0` again; use it for minutes, not hours. |
| `kind` | text | no | Picks the launcher's own icon: `news`, `event`, `alert`. Anything else gets the default icon. |

Every value is text the launcher cleans before it shows it: control and formatting characters are removed, runs of
spaces become one, and anything past the limit is cut. Nothing in a row can name an intent, a URI, a package,
a resource or a colour, and the launcher loads no drawable, code or layout from the add-on.

## When the launcher reads it

- Only while Home is on screen and only for a source the TV owner ticked in **Launcher settings > Home widget**.
  An unticked source is never read. The tick is the owner's consent; it is separate from the card's Show answer.
- When Home comes to the front and then at most once a minute, whatever the page speed (6, 10, 15 or 30 seconds,
  the owner's choice); between reads the widget shows the last row. In the background, with a 1.5 second limit per
  read. A slow or failing provider loses its page for that turn, with one log line under `TvHomeWidgets`; while its
  read has not returned it is not asked again. The pages keep turning in the meantime.
- The live cards are looked up again each time Home comes to the front, so an add-on installed later shows up in the
  settings list the next time it is opened.

## Choosing and ordering

`config.xml` `widget_sources` sets the default list and order, for example `adb`, `memory`,
`app:com.example.news`. On the TV, **Launcher settings > Home widget** ticks sources on and off, **Show first** moves
one to the front, and **Home widget page** sets the seconds per page. **Follow theme** clears both choices.
