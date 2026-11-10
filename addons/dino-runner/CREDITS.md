The game in `app/src/main/assets/dino/` is the T-Rex runner from Chrome's offline error page, by The
Chromium Authors, taken from [wayou/t-rex-runner](https://github.com/wayou/t-rex-runner) at commit
`5455bfa408ec6b707c7300ff194b7390733a766d`, which packages it as a standalone page.

- `index.js` and `index.css`: the game's code and style, unchanged ("Copyright (c) 2014 The Chromium
  Authors", BSD-style licence).
- `100-offline-sprite.png` and `200-offline-sprite.png`: the game's sprite sheets (dino, cacti, birds,
  clouds, ground, digits), unchanged.
- The three `<audio>` sounds in `index.html` (jump, hit, score): copied from the same page.
- `LICENSE-t-rex-runner.txt`: wayou/t-rex-runner's BSD 3-Clause licence, kept with the files as it
  requires.

Our own work, under the repository's MIT licence: the rest of `index.html` (the dark page around the
game), `bot.js`, the Java code, the banner and the icon (drawn for this add-on, not from the sprites)
and these pages.

The Chromium files are under the BSD licences above, not the repository's MIT licence. Google does not
endorse this project.
