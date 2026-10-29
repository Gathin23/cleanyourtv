# tools/screendiff.py

Check that a before and an after screenshot are identical outside the regions you mask, and write the pair
side by side with private details blurred. Every performance PR claims "no visible change"; this proves it.

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt     # Pillow
tools/venv/bin/python tools/screendiff.py shots/before.png shots/after.png \
    --mask 90,40,900,220 --blur 1265,125,1525,197 --out shots/before-after.png
```

| Exit status | Printed | Meaning |
|---|---|---|
| 0 | `identical (1 region masked): ...` | No pixel outside the masks differs by more than 8 |
| 1 | `different: N pixels differ ..., inside x0,y0,x1,y1` | Find out why before posting |
| 2 | an error | Different sizes, a box outside the picture, an unreadable file |

| Option | Notes |
|---|---|
| `--mask x0,y0,x1,y1` | Left out of the comparison. Repeatable. Only for what changes by itself: clock, greeting, stats line, wallpaper caption. |
| `--blur x0,y0,x1,y1` | Pixelated in both screenshots of the pair. Repeatable. For the Wi-Fi name, account pictures. It doesn't affect the comparison. |
| `--out PNG` | The labelled pair, at `--scale` (default 0.5). |
| `--diff PNG` | The differing pixels, white on black. |
| `--threshold N` | Channel difference to ignore (default 8, compression noise). |

## Boxes

Boxes are `x0,y0,x1,y1` in the screenshot's pixels. On a 1080p TV with the default layout:
- **Greeting, clock and date:** `90,40,900,220`.
- **Network name:** about `1265,125,1525,197`.

The top bar moves with config, and the Hebrew layout mirrors it. Read the Wi-Fi pill's bounds on your TV:

```bash
tools/venv/bin/python -I tools/tv.py 'uiautomator dump /data/local/tmp/ui.xml >/dev/null; grep -o "id/btnWifi\"[^>]*bounds=\"[^\"]*\"" /data/local/tmp/ui.xml; rm /data/local/tmp/ui.xml'
```

## Rules

- **Take both screenshots the same way.** Same key presses, same wait, same theme and wallpaper:
  `tools/tv.py --screenshot shots/before.png HOME DPAD_RIGHT`.
- **Never mask a region your change touches.**
- **Don't raise `--threshold` to make a difference go away.** If something did change, say what in the PR.
- **Compare Home, not Live TV, HDMI or Netflix.** Those capture as empty on most TVs.

The whole before/after method for a pull request is in [`docs/workflows/perf-ab.md`](../docs/workflows/perf-ab.md).
