# Workflow: Add or fix a launcher translation

**When to use this:** a new launcher language, or strings missing from existing ones.

1. Create `launcher/app/src/main/res/values-<qualifier>/strings.xml` and translate every string in
   `launcher/app/src/main/res/values/strings.xml`, skipping the ones marked `translatable="false"`. Keep
   placeholders (`%1$s`, `%d`) and XML escapes intact.
2. Add the locale to `launcher/app/src/main/res/xml/locales_config.xml`.
3. Add the language to the Languages list in `README.md`.
4. To fix a missing string in existing locales, add it to every `values-*` folder you can, in the same order as
   the default `strings.xml`. A missing string falls back to English (`launcher/app/lint.xml`), but a new switch
   needs its strings in every locale.
5. Moving hard-coded text into resources is welcome; do not bundle unrelated build changes with it.
6. Build the launcher, set the TV's language (Android 13+: `tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales <tag>'`)
   and attach a screenshot. Right-to-left languages mirror the layout like Hebrew; `tests/test_hebrew_resources.py`
   checks that `layout-iw` keeps every view.

Then run the tests: `python3 -m unittest discover -s tests -v`.
