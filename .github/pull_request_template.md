<!-- Thanks. One or two sentences on what this changes and the TV it was tried on is plenty. -->

**Tested on:** <!-- model, Android version -->

### If this adds or changes a TV list
- [ ] `examples/<brand>-<model>/debloat.txt`, header with model, Android version and firmware build
- [ ] Only packages I actually disabled and lived with; anything I had to re-enable is noted
- [ ] A row in `examples/README.md` and one in the README's TV table
- [ ] Nothing in the list uninstalls

### If this touches the launcher
- [ ] Verified on a real TV, screenshot attached
- [ ] D-pad checked from every edge (Up, Down, Left, Right)
- [ ] No network, no background service, no new permission
- [ ] New strings in `values/strings.xml` and the other `values-*` folders
- [ ] Anything TV-specific is in `config.xml` or a theme, not in code

### If this adds or changes a theme
- [ ] `themes/<name>/config.json` and `themes/<name>/screenshot.jpg`: Home on a real TV with the theme, 1280×720 JPEG under 300 KB
- [ ] A row in `themes/README.md` showing the screenshot

### If this adds a theme key (a switch)
- [ ] A screenshot of the key on a real TV and its row in `docs/switches.md`

### If this adds an add-on
- [ ] `addons/<name>/screenshot.jpg`: the add-on on a real TV, 16:9 JPEG under 300 KB, shown in its row in `addons/README.md`
