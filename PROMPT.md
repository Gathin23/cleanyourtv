# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer that is on the same network as the Fire TV. This prompt is for Amazon Fire TV devices and uses Homestead:
https://github.com/Sarg338/Homestead

Do not use the Android TV launcher/debloat project from this repository. Clone and follow Homestead's README and SECURITY.md instead. Homestead is the replacement home screen and debloater for Fire TV. Prefer its latest signed release APK and its built-in setup/debloat controls over writing a new launcher or inventing package lists.

Before you start, on the Fire TV: Settings → My Fire TV → About → select the device name seven times if Developer options is hidden, then open Developer options and enable ADB debugging. Keep the TV on and connected to the same network as the computer. Tell me when the TV will show an authorization prompt so I can accept it.

---

I want to improve the performance and privacy of my Amazon Fire TV by installing and configuring Homestead.
My Fire TV model is: <MODEL NAME HERE>.
Work on the real device, step by step, and verify every change with screenshots pulled from the TV. Ask me before anything that depends on my preferences, especially which apps I use, which Amazon features I need, whether to keep Live TV, voice control, casting, app buttons, firmware updates, and whether to use Simple mode.

If Homestead is not already in the working folder, clone it first:

git clone https://github.com/Sarg338/Homestead

Work from that checkout. Read the repository README.md and SECURITY.md before installing or changing anything. Do not replace Homestead with a custom launcher unless Homestead cannot run on this exact device and I explicitly approve another approach.

Connect and inspect
- Find the Fire TV on my network or ask me for its IP address. Connect with ADB, normally on port 5555. Do not scan another home network; I will provide a Tailscale or other reachable address if needed.
- Confirm the device identity, Fire OS version, Android SDK level, model, architecture, storage, and available memory before changing anything.
- Save the complete package list, enabled/disabled package state, relevant getprop output, current launcher/home activity, and Homestead version to a dated backup folder.
- Check whether ADB is using Wi-Fi and warn me that network ADB is plaintext on some Fire OS versions. Do not leave debugging enabled when the job is complete unless I explicitly ask.
- If ADB cannot connect, stop and explain the smallest next step. Do not repeatedly retry or guess credentials.

Install Homestead
- Download the latest Homestead APK from the repository's Releases page, or build it only if the README requires that for this device. Verify the release source and package before installing.
- Check that Fire TV accepts the APK. If installation fails because of an incompatible SDK, signature, architecture, or Fire OS restriction, stop and report the exact error instead of modifying the APK.
- Install Homestead over ADB, launch it, and complete its first-run setup on the TV. Confirm every Android VPN, ADB, accessibility, or permission prompt with me before accepting it.
- Follow Homestead's documented setup for making it handle the Home button. Verify the result with the actual remote Home button, not only an injected ADB key.
- Never disable Amazon's stock launcher until Homestead opens reliably, Home works, ADB remains available, and I know how to restore the stock launcher.

Back up and debloat
- Use Homestead's Debloat screen and its vetted package descriptions. Do not create a broad package list from names alone and do not uninstall system packages.
- Before changing anything, show me the proposed packages grouped by purpose and ask which streaming apps, voice assistant features, casting, Live TV, app-button behavior, updates, and Amazon services I want to keep.
- Disable only packages I approve, in small batches. Take a TV screenshot after each batch and verify that Home, Settings, Wi-Fi, Bluetooth remote control, HDMI/input switching, app launching, audio, and the streaming apps I kept still work.
- Treat the Amazon launcher as risky. Disable it only through Homestead's documented control, only after the replacement Home screen has been tested, and only with a restore path ready.
- Do not disable packages needed for Fire TV settings, package installation, the input framework, the remote, Bluetooth, Wi-Fi, media playback, updates I chose to keep, or Homestead itself.
- If Fire OS refuses a package change, stop. Do not substitute uninstall, hide, or undocumented shell commands without explaining the risk and asking me.

Configure Homestead
- Ask how I want the home screen organized: Classic or Dock design, dock size, pinned apps and order, hidden apps, inputs, Live TV placement, background, columns, clock/date, greeting, language, and Simple mode.
- Keep the interface light for Fire TV hardware: no unnecessary video backgrounds, blur, or network polling.
- Use Homestead's own Settings controls and documented configuration. Do not edit files or add services when the app already provides the setting.
- If a feature is unavailable on this Fire OS version, say so plainly and leave the device in the last known-good state.

Verify and recover
- After every meaningful change, capture a screenshot from the TV and inspect it. Also verify the current Home activity and that ADB still responds.
- Test the real remote: Home, Back, directional navigation, Select, Menu, Settings, volume, app buttons, input/source switching, and the apps I asked to keep.
- If a screenshot is blank or stale, confirm the foreground activity and capture again. Do not assume the debloat worked from an empty screenshot.
- After the final batch, wait for Fire OS to persist package state, reboot once, and repeat the same checks. If Home returns to Amazon's launcher or the device loses ADB, restore the stock launcher first and document what happened.
- Give me a restore procedure that re-enables every package changed, restores the stock launcher/Home behavior, and uninstalls Homestead safely if needed.
- Give me a concise record of the model, Fire OS version, Homestead version, packages changed, settings changed, screenshots taken, remaining risks, and the exact commands used.

When finished, turn off ADB debugging and revoke debugging authorizations unless I explicitly ask to keep them enabled. Everything must remain reversible, and do not proceed past a preference or permission prompt without asking me.
