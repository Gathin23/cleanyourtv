# Wanbo X2 Pro (`WPA22`): a `wtprovision` deadlock, and two panel revisions behind one model name

From @666xgod's report and follow-up in [#2809](https://github.com/Gathin23/cleanyourtv/issues/2809). This is a
device report: no package was disabled for it, no image is linked or hosted here, and nothing on this page was
measured by the repo. It is recorded because a factory image applied from a USB stick was the only thing that
brought a stuck unit back, and because which panel a unit carries decides whether an image is the right one for
it.

| Field | Observed value |
|---|---|
| Retail model | Wanbo X2 Pro, `WPA22` |
| Mainboard | `RG_5H045_I20_3D` |
| SoC | HiSilicon Hi3751V352F (HiSilicon DTV platform) |
| Android | 9 |
| Panels in the wild | 1080p 4.41" / 5.0" with the ST5892B controller; 720p 3.93" / 3.96" BOE panel with the EK79202 controller |

## The unit that stopped at `wtprovision`

The reporter's projector hung in a `wtprovision` deadlock and never finished booting. A factory image written to a
USB stick cleared it: the projector booted, the remote worked, the autofocus motor ran and the sensors came back.
Why it hung is not established by the report, and this page does not guess; what the report does establish is that
the update ran from the stick, without opening the case and without UART.

A projector that still boots does not need any of this. The cleanup flow in this repo never writes firmware, and
[`tools/restore.sh`](../restore.md) cannot undo a flash: it restores packages and settings, nothing below them.

## Two panels, one model name

Units of both revisions report the same model and the same mainboard. What differs is the panel, and the panel
descriptor the firmware is told to use:

| Revision | Panel | Descriptor, as reported |
|---|---|---|
| A | 4.41" / 5.0", 1080p, ST5892B controller | `QY50015FOG_ST5892B_1080P_FM`, panel index 0 |
| B, newer batches | 3.93" / 3.96", 720p BOE panel, EK79202 controller | `FPC_QY40091FOG_01_EK79202_BOE3.96_720P_M`, panel index 102 |

### What an image for the other panel looks like

The bootloader's Wanbo logo draws correctly, and then the picture goes: heavy flicker and horizontal artifacts, or a
black screen with the backlight on while audio keeps playing. The unit is otherwise alive and reachable.

The reporter's reading is that the display driver asks the panel parameters for an index, the stock image leaves it
at 0 - the 1080p descriptor - and the 720p panel's controller cannot lock to that timing. He then produced a build
with index 102 in its place and reported that it fixed the scrambled-screen units he tried it on. That build is his,
not Wanbo's, and the repo cannot check what is in it.

## An OTA puts the panel index back

Wanbo's update server pushes the generic 1080p image, so an update accepted from the projector's own Settings menu
returns a 720p-panel unit to flicker or to a black picture. In the reporter's case, writing the 720p build from the
stick again restored the display.

## The USB update path

The layout below is what the reporter used, for an image obtained from Wanbo support or from the unit's seller.
Neither the image nor a link to one belongs in this repo.

| Item | Value |
|---|---|
| Stick | FAT32, MBR partition table, 32 GB or less, in a USB 2.0 port |
| Firmware file | `newlink_update.bin`, in the root of the stick |
| Trigger file | `nlusbautoupdate.txt`, in the root, holding the single word `update` |

1. Put the image on the stick as `newlink_update.bin`, at the root.
2. Create `nlusbautoupdate.txt` at the root, containing `update` and nothing else.
3. Unplug the projector's power cable.
4. Plug the stick into the projector's USB port.
5. Reconnect the power and switch the projector on. The blue upgrade screen comes up by itself.
6. Let it run and reboot on its own; 3 to 5 minutes in the reporter's run.

## Which panel a unit has

Nothing in the report separates the two revisions before an image is written: the model number, the mainboard and
the build are the same for both, and no settings screen is named that shows the panel. Ask Wanbo support, or the
seller, which panel the serial number carries, and say which revision the answer is for when reporting back here.
If that answer cannot be had, the symptom above is the only field evidence in this report, and it appears only
after the image is on the unit.

## Scope and limits

- One reporter. His own unit is described in one comment as the 4.5" ST5892B variant; the build he produced targets
  index 102, for the 3.96" panel. This page does not try to reconcile the two, and the combinations actually
  flashed are not enumerated in the report.
- Nothing was measured here: no unit of this page's author, no package list, no launcher route. The report's own
  answer to "how far did you get" is "Debloat worked, launcher did not", with no package names, so there is nothing
  [`tools/disable.sh`](../../tools/disable.sh) could apply to this model.
- No image is linked, mirrored or recommended, and no download link from the issue is repeated here: the repo cannot
  check what any of them contains.
