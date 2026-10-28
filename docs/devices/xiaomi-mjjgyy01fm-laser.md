# Xiaomi MJJGYY01FM laser TV/projector: Android 6 launcher and firmware notes

[English](#english) · [简体中文](#简体中文)

## English

Tested on one Xiaomi MJJGYY01FM laser TV/projector on 2026-10-25. This is a
device note, not a debloat list or a proposal to lower the launcher's `minSdk`.
The stock Home, Settings, projector controls and media stack were intentionally
kept enabled.

### Identity

| Field | Observed value |
|---|---|
| Retail model | `MJGYY01FM` |
| `ro.product.model` | `MiProjA1` |
| `ro.product.device` / `ro.product.name` | `rainman` / `rainman` |
| Platform | Amlogic `gxbaby` |
| Android / SDK | 6.0.1 / 23 |
| Build | `7.10.25.761` |
| Fingerprint | `Xiaomi/rainman/rainman:6.0.1/MHC19J/761:user/release-keys` |
| Characteristics | `tv,projector` |
| Display | 1920x1080, density 320 |

### Why this set is not a debloat target

The stock Settings app owns keystone correction, motor focus, aspect-ratio
controls and the source popup. The stock system also exposes the Xiaomi player
and SMB playback. Disabling `com.xiaomi.mitv.settings`, `com.mitv.tvhome` or
the vendor player is therefore a poor default choice for this family.

A local, out-of-tree Android 6 build was used for the launcher test. It guarded
API 24+ calls such as `forceHasOverlappingRendering`, `getLocales`,
`registerDefaultNetworkCallback` and `isUserUnlocked`, and supplied fallbacks
for newer Java/resource APIs. This build is not part of the upstream launcher;
the project continues to require Android 8 (API 26), as discussed in #857.

### Home and source keys

The physical remote's Home and source keys did not reach a third-party
accessibility service on this firmware: no keycode 3 or 178 was observed while
the buttons were pressed. Treat both keys as firmware-owned. The tested setup
used explicit on-screen entries to move between the clean launcher and the
stock Home instead of claiming those keys could be captured.

### Private screensaver

`settings get secure screensaver_enabled` returned `0`, but the firmware still
started `com.mitv.screensaver/.RenderActivity` after several minutes. Its
private timeout was `screen_saver_timeout=240000`. This is not the standard
Android dream path.

On this unit, a device-local accessibility helper recognised that window,
sent `com.duokan.intent.action.exit.screensaver`, and returned to the launcher.
A deliberate `show.screensaver` test returned to the clean UI and the
screensaver's main process exited.

This suppresses the visual ad screen; it does not remove the vendor Home,
push services or the screensaver's persistent `:centralservice`. While the ad
was visible, its main process used roughly 50 MB PSS and 5-8% CPU; the stock
Home remained around 177 MB PSS. Do not describe this as debloating.

Reinstalling the launcher APK can clear its accessibility-service binding.
Check the enabled list after an APK update if this device-local behavior is
needed.

### Inputs

`dumpsys tv_input` showed eight bound `com.droidlogic.tvinput` services but an
empty `inputMap`, so `TvInputManager` returned no passthrough rows. The vendor
path on this firmware is:

```text
action: com.xiaomi.mitv.tvplayer.EXTSRC_PLAY
component: com.xiaomi.mitv.tvplayer/.ExternalSourceActivity
integer extra: input
```

Xiaomi Settings' `PortConverter` mapped TV indices 2, 3 and 5 to source numbers
23, 24 and 25. These are likely HDMI 1/2/3, but no HDMI source was connected
during this test, so the physical port order remains unverified. The merged
#722 mechanism and the #1655 discussion are the relevant existing work.

### ADB and remote-control limits

Network ADB is switched off after a reboot. While the set was awake, MiLink on
TCP 6091 and MiTV Assistant on TCP 6095 were reachable. Wake-on-LAN did not
wake it from deep standby; cold power-on needs IR or HDMI-CEC.

### Limits

One unit and one firmware build. HDMI port order, a full cold-boot regression,
and the accessibility binding after a full reboot still need separate checks.
No stock projector feature was removed, and no debloat list is claimed.

## 简体中文

本说明基于 2026-10-25 在一台小米 `MJGYY01FM` 激光电视/投影仪上的实测。
这不是 debloat 列表，也不是要求上游降低 launcher `minSdk` 的提案。原桌面、
原设置、投影控制和媒体栈均有意保持启用。

### 设备身份

| 字段 | 实测值 |
|---|---|
| 零售型号 | `MJGYY01FM` |
| `ro.product.model` | `MiProjA1` |
| `ro.product.device` / `ro.product.name` | `rainman` / `rainman` |
| 平台 | Amlogic（晶晨）`gxbaby` |
| Android / SDK | 6.0.1 / 23 |
| 固件 | `7.10.25.761` |
| 指纹 | `Xiaomi/rainman/rainman:6.0.1/MHC19J/761:user/release-keys` |
| 设备特征 | `tv,projector` |
| 显示 | 1920x1080，密度 320 |

### 为什么这台设备不适合照常 debloat

原设置负责梯形矫正、电动对焦、画面比例和信号源弹窗；原系统还提供小米
播放器和 SMB 播放。因此，对这台设备默认禁用 `com.xiaomi.mitv.settings`、
`com.mitv.tvhome` 或厂商播放器并不合适。

桌面测试使用了一个设备本地的 Android 6 独立构建。该构建为
`forceHasOverlappingRendering`、`getLocales`、
`registerDefaultNetworkCallback`、`isUserUnlocked` 等 API 24+ 调用增加了
版本判断，并为较新的 Java/资源 API 提供了回退。这个构建不属于上游
launcher；项目继续要求 Android 8（API 26），参见 #857 的讨论。

### Home 和信号源键

按真实遥控器时，第三方无障碍服务没有收到 Home 和信号源键：按键触发时没有
观察到 keycode 3 或 178。应把这两个键视为固件托管。测试方案使用屏幕上的
明确入口在新桌面和原桌面之间切换，不声称这些按键已被应用层捕获。

### 私有屏保

`settings get secure screensaver_enabled` 返回 `0`，但固件仍会在数分钟后
启动 `com.mitv.screensaver/.RenderActivity`。它自己的超时是
`screen_saver_timeout=240000`，并不是标准 Android dream 路径。

在本机上，一个设备本地的无障碍辅助服务识别到该窗口后发送
`com.duokan.intent.action.exit.screensaver`，并回到 launcher。主动触发
`show.screensaver` 的测试也回到了干净界面，屏保主进程随后退出。

这只是抑制广告画面，并不会移除厂商桌面、推送服务或屏保常驻的
`:centralservice`。广告显示时，主进程约占 50 MB PSS 和 5%～8% CPU；
原桌面仍约占 177 MB PSS。不要把它描述成 debloat。

重新安装 launcher APK 可能清除其无障碍服务绑定。如果依赖这个设备本地行为，
每次升级 APK 后都要检查启用列表。

### 输入源

`dumpsys tv_input` 显示八个 `com.droidlogic.tvinput` 服务已绑定，但
`inputMap` 为空，因此 `TvInputManager` 没有返回 passthrough 输入。这台固件的
厂商路径是：

```text
action: com.xiaomi.mitv.tvplayer.EXTSRC_PLAY
component: com.xiaomi.mitv.tvplayer/.ExternalSourceActivity
integer extra: input
```

小米设置中的 `PortConverter` 把电视输入索引 2、3、5 映射为源编号 23、24、25。
这些很可能是 HDMI 1/2/3，但测试时没有连接 HDMI 信号源，所以物理端口顺序仍
未验证。相关的现有工作是已合并的 #722 和正在讨论的 #1655。

### ADB 和远程控制限制

网络 ADB 在每次重启后关闭。设备唤醒时，MiLink 的 TCP 6091 和 MiTV Assistant
的 TCP 6095 可访问。Wake-on-LAN 无法从深度待机唤醒本机；冷启动需要红外或
HDMI-CEC。

### 限制

单台设备、单套固件。HDMI 端口顺序、完整冷启动回归以及完整重启后的无障碍
绑定仍需单独检查。没有移除任何原厂投影功能，也不声称存在 debloat 列表。
