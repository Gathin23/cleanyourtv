# Next Start Pro 4K: notes

Google TV 14 box sold in Turkey (Realtek RTD1325, 2 GB). Chain on the test unit: box → Samsung HW-Q990C soundbar
(HDMI in) → Epson LS300 projector (HDMI ARC). The list and its header are in [`debloat.txt`](debloat.txt).

## Dolby audio: stutter with passthrough, stereo without it

The firmware decodes Dolby through Realtek's MS12 stack. Two Android settings and one hidden Realtek setting
decide what reaches the soundbar.

| Surround sound (`encoded_surround_output`) | MS12 preferred output | What the Q990C got |
|---|---|---|
| Always (`2`) | as shipped | Dolby Atmos, but the sound stutters about once a second in Stremio and Nuvio |
| Never (`1`) | as shipped | No stutter, but 2-channel PCM ("stereo" on the soundbar) |
| Never (`1`) | Dolby Digital Plus/Dolby Atmos | **No stutter, Dolby Atmos** |

What the box logged in the stuttering case, while Stremio played an E-AC3 5.1 Atmos stream:

- `dumpsys media.audio_flinger`: a `DIRECT` output thread in `AUDIO_FORMAT_E_AC3`, with
  `Timestamp stats ... rate=0.618 jitterMs(... min=-56.8 max=55.2)`. After the fix the same stream shows
  `rate=1.000 jitterMs(... min=-6.7 max=6.5)`.
- `audio_hw_primary_v5.12.20ms: out_get_presentation_position 5432 out->mRefClock null`, about 260 times in
  half a minute.
- `AudioFlinger: pause because of UNDERRUN, framesReady = 110592, ... mFormat = 0xa000000`.
- `ExoPlaybackException: MediaCodecAudioRenderer error ... audio/eac3 ... format_supported=YES`.

The hidden pages are slices served by `com.realtek.slices` and shown by TV Settings' slice activity. They
do not appear in the normal menus:

```sh
adb shell am start -n com.android.tv.settings/.slice.SliceActivity \
  -d content://com.realtek.slices.sliceprovider/ms12 \
  --es slice_uri content://com.realtek.slices.sliceprovider/ms12
```

`ms12` is "Preferred MS12 output": MAT, Dolby Digital Plus/Dolby Atmos, PCM, DD/AC3. Choose the second entry
with the remote or `input keyevent DPAD_DOWN` then `DPAD_CENTER`; logcat confirms with
`GetAudioMs12OutputMode ... mode=2`. The setting survived two reboots on the test unit.

Other paths on the same provider, found in the APK's `RtkSlicesUtils`: `output` (HDMI output: Auto, Stereo
LPCM 2Ch, S/PDIF bitstream), `hdmi_pcm` (HDMI PCM output: Stereo, Multichannel), `ddp`, `output_channel_config`,
`audio`, `video`, `display`, `hdr`, `resolution`. Setting `hdmi_pcm` to Multichannel did not change the stereo
report on the Q990C and did not survive a reboot, so the list does not rely on it.

## Video

The Epson LS300 is a 1080p projector that accepts 4K. The box's EDID shows modes up to 2160p60 with HDR10 and
HLG through the soundbar, and with resolution on Automatic it picks 1080p59.94, the projector's preferred mode.
Match content frame rate is on Always out of the box.
