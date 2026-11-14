# Noblex NoblexTV (shinjuku): optical output and volume

Notes from a second NoblexTV (Skyworth, codename `shinjuku`, MediaTek m5621, Android TV 11, build
`RTO9.230812.001`), debloated with [`debloat.txt`](debloat.txt) and with `launcher/` as Home. They are about sound
through the optical (S/PDIF) output, measured over ADB on 2026-11-13.

## The optical output ignores the TV volume

With speakers or a DAC on the optical output, the remote's volume keys change nothing there:

- Android sees a single output device, `speaker`. The volume keys only move `STREAM_MUSIC`, and the audio HAL applies
  it to the TV speakers alone; S/PDIF leaves at a fixed level (`dumpsys media.audio_flinger` shows master volume
  1.0 and no attenuation on the mix).
- In the Sound settings, **Speakers: External audio system** only sets that volume to 0. The next Vol+ brings the
  TV speakers back. The only other choice is TV speakers.
- **Digital output** must be **PCM** for any of the below: an encoded (passthrough) stream cannot be attenuated on
  the TV.
- The volume keys never reach apps or an accessibility key filter: `adjustStreamVolume() - always send ... to CEC`
  in logcat, the system routes them straight to AudioService.

## What reaches Android's mix

| Source | Path | Can an app change its level? |
|---|---|---|
| Netflix, YouTube | `AudioOut_D` mixer thread, PCM | Yes |
| Disney+ (5.1 E-AC3) | `OFFLOAD` thread, `DIRECT\|COMPRESS_OFFLOAD`, format `0x0A000001`: decoded on the chip | No |
| HDMI, AV, Live TV | `AudioPatch device:hdmi-in -> device:speaker`, "No software bridge" | No: a hardware patch |

Setting the surround sound formats to None (`settings put global encoded_surround_output 1`) did not move Disney+
off the offload output; it was put back to Auto. Holding the direct and offload outputs busy from another app only made
Disney+ stop with `AudioTrack init failed` for `audio/eac3`: it does not fall back to PCM. Turning Dolby Atmos off in its
settings does not change the track. Its stereo extras play
through the mixer.

## A workaround for apps

[Digital Volume](https://github.com/JPabloLazo/android-tv-digital-volume) is a small separate app built on this TV.
It puts a `DynamicsProcessing` gain on the global output mix (session 0), which reaches apps that play PCM, reads each volume key press from the
system's volume broadcasts and puts `STREAM_MUSIC` straight back to 0. In apps the remote then controls the optical
output and the TV speakers stay silent; on HDMI, AV and Live TV it either hands the keys back to the TV or leaves
the TV speakers off. It uses an accessibility service (window events only) and has no internet permission.

## Other notes

- This set came with `com.google.android.tungsten.setupwraith` installed, but it does not claim Home: once
  `launcher/` was installed only `com.android.tv.settings/.system.FallbackHome` was left besides it. The saved Wi-Fi
  network belongs to the shared Google uid, not to setupwraith.
- The UI runs at 1280x720 (`wm size` with no override); video from apps, HDMI and Live TV is drawn on the separate
  video plane.
