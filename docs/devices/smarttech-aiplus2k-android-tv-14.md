# Smarttech AiPlus2K (Android TV 14)

CleanYourTV 3.6 was tested on one AiPlus2K on 22 November 2026. The launcher and four
package changes were checked after one reboot in the same session. Use this report to
identify the firmware and understand the observed behavior.

| Field | Observed value |
| --- | --- |
| Brand / Android model | Smarttech / `AiPlus2K` |
| Manufacturer | Expressluck |
| Product / device | `SMARTTECH_FHD_ATV_UK` / `martin` |
| Platform | MediaTek `mt5867` |
| Android TV / API | 14 / 34 |
| Firmware build | `UKNV.260602.001` (`15547440`) |
| Security patch | 2026-06-01 |
| Application ABI | `armeabi-v7a` (32-bit) |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV package present | `com.mediatek.wwtv.tvcenter` |

## Home handover

On Windows, `launcher/build-and-install.ps1` installed and opened CleanYourTV with
`-StockLauncher none`, but Home still resolved to Google's launcher. A second run naming
`com.google.android.tvlauncher` as the stock launcher started CleanYourTV, disabled the
stock Home for user 0 and verified the handover. After the reboot, Home resolved to
`com.gathin.tvhome/.MainActivity` and opening Home displayed the launcher. No accessibility
key helpers were enabled.

This firmware contains CVTE factory packages, and the observed stock Home is Google's
launcher. The result applies to the firmware above. The [Qilive AiPlus2K report](qilive-fhd-android-tv-11.md)
covers a different board and Android TV build.

## Package changes and Wi-Fi

The Windows disable tool recorded the original state and disabled these four packages for
user 0. All four were still disabled after the reboot.

| Package | Role |
| --- | --- |
| `tv.anoki.acr.anokiacroptin` | Automatic content recognition opt-in |
| `com.google.android.feedback` | Google feedback |
| `com.android.tv.feedbackconsent` | Android TV feedback consent |
| `fusion.android.tv.demo` | Retail demo mode |

`com.google.android.tungsten.setupwraith` owned a saved Wi-Fi network and was kept enabled.
Playback, casting, HDMI input, firmware updates, and physical remote behavior are
unverified in this report.
