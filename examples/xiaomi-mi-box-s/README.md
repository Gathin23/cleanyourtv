# Xiaomi Mi Box S configuration

This optional `config.json` is for the Xiaomi Mi Box S / Mi Box 4 on Android TV 9, build PI.3933.
It leaves the Live TV tile empty on this streaming box and opts into the launcher's remote-pairing
prompt. It is not applied by the debloat list.

The pairing switch checks for an external remote, keyboard or gamepad on the first Home of a boot.
If none is detected, it waits 30 seconds and checks again before opening the native pairing wizard.
The result is remembered for that boot.

This is an input-device check, not a Bluetooth bond-state check. A paired remote that reconnects
slowly can still be mistaken for a missing remote. Leave `prompt_remote_pairing` false or remove the
key if that happens on your box.
