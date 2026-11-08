"""Publish a picture the way `img.save(path)` cannot: encode it beside the file, then rename it into place.

The three scripts that draw the README card - `tools/readme_stats.py`, `tools/readme_featured.py` and
`tools/readme_card.py` - each ended with `img.save(OUT, ...)`, and Pillow opens the destination before it
encodes, so a full disk, a failing encoder or a file it may not rewrite left whatever had been written
under the name the repository ships the picture at. `docs/stats.png` cut short is not only a broken card:
`tools/readme_card.py` loads that file to stack the card, so the card cannot be redrawn until the picture
is restored from git. The run still said "Nothing was written."

`save()` is the shape `tools/scenes_wallpapers.py`, `tools/blocky_wallpapers.py` and
`tools/wallpaper_prep.py` settled on for a picture that must survive a failed write:

  * the picture is encoded into a staging folder beside its destination and `os.replace`d over it, so the
    destination is either the file that was there or the complete new one, never half of one;
  * a destination that is a symlink keeps the link and its target is replaced, as `img.save(path)` would
    have written through it;
  * a destination that is not a regular file, or that has a second hard link, is refused: a rename would
    replace a device, or leave the other name on the old picture;
  * an existing file's mode is carried onto the new one, and a read-only one is refused without being
    truncated - `os.replace` alone can replace a file its caller may not write;
  * a fresh file keeps the mode `open()` left, masked by the umask, rather than a staging file's;
  * a staging folder that cannot be removed after the rename is a warning on stderr, because the picture is
    in place and the caller's message must not say nothing was written.

`save()` raises `OSError` with a sentence naming the reason, and the caller prints the destination it was
given, so a reader sees their own path even when the file was reached through a symlink.
"""
import os
import shutil
import stat
import sys
import tempfile


def save(image, path, **options):
    """`image.save(...)` at `path`, published by rename; `OSError` says why there is no picture.

    `options` are handed to the encoder as `image.save()` takes them (`optimize=True`, `quality=88`), and
    the staging file carries the destination's own name, so the format is chosen the same way.
    """
    target = os.path.realpath(path)      # Follow a destination symlink, as img.save(path) did.
    try:
        previous = os.stat(target)
    except FileNotFoundError:
        previous = None
    if previous is not None:
        if not stat.S_ISREG(previous.st_mode):
            raise OSError(f"{path} is not a regular file, and a picture cannot replace it")
        if previous.st_nlink > 1:
            raise OSError(f"{path} is one of {previous.st_nlink} hard links to the same file, and replacing "
                          f"it would leave the others on the old picture")
        # Rename alone can replace a read-only file; check write access without truncating it.
        try:
            os.close(os.open(target, os.O_WRONLY))
        except OSError as e:
            raise OSError(f"{path} cannot be written ({e.strerror or e}), so the picture already there "
                          f"was left as it is") from e
    published = False
    try:
        with tempfile.TemporaryDirectory(prefix=".picture-", dir=os.path.dirname(target)) as staging:
            part = os.path.join(staging, os.path.basename(target))
            image.save(part, **options)
            if previous is not None:
                shutil.copymode(target, part)
            os.replace(part, target)
            published = True
    except OSError as e:
        if not published:
            raise
        # The picture is in place; only the staging folder is left. Saying "nothing was written" here
        # would send the reader looking for a file that is there.
        print(f"warning: {path} was written, but its staging folder could not be removed "
              f"({e.strerror or e}).", file=sys.stderr)
