# Workflow: Add an off-by-default launcher switch

**When to use this:** a launcher behaviour or look that existing config keys can't carry.

The core takes a switch only if it changes nothing until a theme or `config.xml` turns it on:

1. Add the key to `launcher/app/src/main/res/values/config.xml` with a comment that says what it does and
   that a theme can set it, defaulting to off (`false`, `0` or the current behaviour).
2. Read it in `launcher/app/src/main/java/com/gathin/tvhome/Config.java`, from the resource and from the
   theme JSON, the way existing keys such as `show_stats` are read.
3. Add a row to the Keys table in `themes/README.md`, and the key's screenshot row in `docs/switches.md`
   (`docs/switches/<key>.jpg`, from a real TV with the key on: 16:9, under 300 KB, personal things blurred);
   `tests/test_switches.py` refuses a Keys row without one, so the two go in the same pull request and the
   site's Switches tab picks it up from there.
4. If it adds a row to Launcher settings, put its strings in `launcher/app/src/main/res/values/strings.xml`
   and every `values-*` folder.
5. With the key off, nothing new runs: no receiver, no service, no write, no permission grant in the install
   script. The bot checks this in the diff, not in the description.
6. Add a JVM unit test under `launcher/app/src/test/java/com/gathin/tvhome/` for any logic that can be tested
   without a TV.
7. Keep it small: a switch should cost a few hundred bytes to about 1 KB of APK. Build and check the size
   gate; the gate is hard.
8. Verify on a real TV with the switch on and off, D-pad from every edge, and attach screenshots.
9. If you can, run the regression check in [`perf-ab.md`](perf-ab.md) with the
   switch on: a baseline before you start, your build after, and the numbers in the PR. It's optional, but welcome.

Anything TV-specific goes in `config.xml` or a theme, not in code. A bug fix that corrects wrong behaviour needs
no switch. No new dependencies in `launcher/app/build.gradle` without an issue first.

Then run the tests: `python3 -m unittest discover -s tests -v`.
