//! Settings that live on the TV itself, not in the launcher: Private DNS, the clocks, sleep, the
//! animation speeds that make a weak GPU usable, and the system screensaver.
//!
//! Every key is on a fixed allowlist. The app refuses anything else, so a typo in the UI, a theme or a
//! pasted value cannot write an arbitrary `settings` key on someone's TV. Values are validated by type
//! and range before they reach `settings put`, and the write is read back to prove it landed.

use super::{on_tv, session, shell_quote, Res};
use serde::Serialize;
use std::time::Duration;
use tauri::State;

use crate::state::AppState;

#[derive(Serialize, Clone, Copy)]
pub struct SettingSpec {
    pub key: &'static str,
    pub namespace: &'static str,
    /// bool, int, select or text
    pub kind: &'static str,
    pub group: &'static str,
    pub label: &'static str,
    pub help: &'static str,
    /// Allowed values for `select`.
    pub options: &'static [&'static str],
    /// Range for `int`; both 0 when the key has no range.
    pub min: i64,
    pub max: i64,
}

const fn spec(
    key: &'static str,
    namespace: &'static str,
    kind: &'static str,
    group: &'static str,
    label: &'static str,
    help: &'static str,
) -> SettingSpec {
    SettingSpec { key, namespace, kind, group, label, help, options: &[], min: 0, max: 0 }
}

const fn range(mut spec: SettingSpec, min: i64, max: i64) -> SettingSpec {
    spec.min = min;
    spec.max = max;
    spec
}

const fn choices(mut spec: SettingSpec, options: &'static [&'static str]) -> SettingSpec {
    spec.options = options;
    spec
}

/// The whole writable surface. Keep this list short on purpose: it is a TV, not a phone, and every key
/// here is one a person can undo from the TV's own Settings app.
pub const SETTINGS: &[SettingSpec] = &[
    choices(
        spec(
            "private_dns_mode",
            "global",
            "select",
            "DNS ve ağ",
            "Private DNS kipi",
            "off kapalı, opportunistic otomatik, hostname seçilen sunucu. hostname için önce sunucu adını yazın.",
        ),
        &["off", "opportunistic", "hostname"],
    ),
    spec(
        "private_dns_specifier",
        "global",
        "text",
        "DNS ve ağ",
        "Private DNS sunucusu",
        "dns.example.com gibi bir ad. Yalnız kip hostname iken kullanılır.",
    ),
    spec(
        "auto_time",
        "global",
        "bool",
        "Saat",
        "Saati ağdan al",
        "Kapalıyken saati elle kurabilirsiniz; uydu yayını saat dilimini yine düzeltebilir.",
    ),
    spec(
        "auto_time_zone",
        "global",
        "bool",
        "Saat",
        "Saat dilimini ağdan al",
        "Kapalıyken saat dilimi elle seçilir. Türkiye için Europe/Istanbul.",
    ),
    choices(
        spec(
            "time_12_24",
            "system",
            "select",
            "Saat",
            "Saat biçimi",
            "12 saatlik (AM/PM) veya 24 saatlik gösterim.",
        ),
        &["12", "24"],
    ),
    range(
        spec(
            "screen_off_timeout",
            "system",
            "int",
            "Ekran ve uyku",
            "Hareketsizlikte uyku (dakika)",
            "Ekranın kaç dakika sonra kapanacağı. Dokunmadan izlerken kapanmasın diye artırın.",
        ),
        1,
        180,
    ),
    choices(
        spec(
            "stay_on_while_plugged_in",
            "global",
            "select",
            "Ekran ve uyku",
            "Prize bağlıyken uyuma",
            "TV sürekli prizde olduğu için burada \"hiç uyuma\" seçmek ekranın kendi kendine kapanmasını durdurur.",
        ),
        &["0", "7"],
    ),
    spec(
        "screensaver_enabled",
        "secure",
        "bool",
        "Ekran ve uyku",
        "Sistem ekran koruyucusu",
        "Kapalıyken Android'in kendi ekran koruyucusu hiç başlamaz.",
    ),
    choices(
        spec(
            "window_animation_scale",
            "global",
            "select",
            "Animasyonlar",
            "Pencere animasyonu",
            "Zayıf GPU'lu TV'lerde 0.5 veya 0 belirgin biçimde akıcılık kazandırır.",
        ),
        &["0", "0.5", "1", "1.5", "2"],
    ),
    choices(
        spec(
            "transition_animation_scale",
            "global",
            "select",
            "Animasyonlar",
            "Geçiş animasyonu",
            "Uygulamalar arası geçişin hızı.",
        ),
        &["0", "0.5", "1", "1.5", "2"],
    ),
    choices(
        spec(
            "animator_duration_scale",
            "global",
            "select",
            "Animasyonlar",
            "Animasyon süresi ölçeği",
            "Uygulama içi animasyonların hızı.",
        ),
        &["0", "0.5", "1", "1.5", "2"],
    ),
    choices(
        spec(
            "font_scale",
            "system",
            "select",
            "Erişilebilirlik",
            "Yazı boyutu",
            "TV arayüzünün yazı ölçeği; büyük yazı bazı ekranlarda taşabilir.",
        ),
        &["0.85", "1", "1.15", "1.3"],
    ),
    spec(
        "sound_effects_enabled",
        "system",
        "bool",
        "Erişilebilirlik",
        "Dokunma sesleri",
        "Menülerde gezinirken çıkan tık sesleri.",
    ),
];

#[derive(Serialize)]
pub struct SettingValue {
    pub key: String,
    pub value: String,
}

/// The keys, groups and labels the TV settings screen draws itself from.
#[tauri::command]
pub fn tv_settings_specs() -> Vec<SettingSpec> {
    SETTINGS.to_vec()
}

/// A value the TV would accept for `key`, or the reason it would not.
pub fn check(key: &str, raw: &str) -> Res<(&'static SettingSpec, String)> {
    let spec = SETTINGS
        .iter()
        .find(|s| s.key == key)
        .ok_or_else(|| format!("{key} is not a setting this app writes."))?;
    let value = raw.trim();
    match spec.kind {
        "bool" => {
            let value = match value {
                "1" | "true" | "on" => "1",
                "0" | "false" | "off" => "0",
                other => return Err(format!("{key} takes 0 or 1, not {other}.")),
            };
            Ok((spec, value.to_string()))
        }
        "int" => {
            let number: i64 = value
                .parse()
                .map_err(|_| format!("{key} takes a whole number, not {value}."))?;
            if number < spec.min || number > spec.max {
                return Err(format!("{key} must be between {} and {}.", spec.min, spec.max));
            }
            Ok((spec, number.to_string()))
        }
        "select" => {
            if !spec.options.contains(&value) {
                return Err(format!("{key} takes one of: {}.", spec.options.join(", ")));
            }
            Ok((spec, value.to_string()))
        }
        _ => {
            if value.len() > 253 || value.chars().any(|c| c.is_whitespace() || c.is_control()) {
                return Err(format!("{key} takes a single word under 253 characters."));
            }
            Ok((spec, value.to_string()))
        }
    }
}

fn read(adb: &crate::adb::Adb, target: &str, spec: &SettingSpec) -> String {
    adb.shell_text(
        target,
        &format!("settings get {} {}", spec.namespace, spec.key),
        Duration::from_secs(10),
    )
    .map(|t| t.trim().to_string())
    .unwrap_or_default()
}

#[tauri::command]
pub async fn tv_settings_read(state: State<'_, AppState>) -> Res<Vec<SettingValue>> {
    let (adb, target) = session(&state)?;
    on_tv(adb, target, |adb, target| {
        let mut out = Vec::new();
        for spec in SETTINGS {
            out.push(SettingValue {
                key: spec.key.to_string(),
                value: read(adb, target, spec),
            });
        }
        Ok(out)
    })
    .await
}

#[derive(Serialize)]
pub struct SettingWrite {
    pub key: String,
    pub before: String,
    pub after: String,
    pub ok: bool,
}

#[tauri::command]
pub async fn tv_setting_write(
    state: State<'_, AppState>,
    key: String,
    value: String,
) -> Res<SettingWrite> {
    let (spec, value) = check(&key, &value)?;
    let (adb, target) = session(&state)?;
    on_tv(adb, target, move |adb, target| {
        let before = read(adb, target, spec);
        // The value is already one of the allowlisted literals, but it still goes through the shell
        // quoter so a namespace or key with an odd character could never change the command.
        adb.shell_text(
            target,
            &format!(
                "settings put {} {} {}",
                shell_quote(spec.namespace),
                shell_quote(spec.key),
                shell_quote(&value)
            ),
            Duration::from_secs(12),
        )?;
        let after = read(adb, target, spec);
        Ok(SettingWrite {
            key: spec.key.to_string(),
            before,
            after: after.clone(),
            ok: after == value,
        })
    })
    .await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_allowlisted_keys_are_written() {
        assert!(check("private_dns_mode", "off").is_ok());
        assert!(check("window_animation_scale", "0.5").is_ok());
        // Anything a pasted command might aim at is refused.
        for bad in ["install_non_market_apps", "adb_enabled", "any_other_key", ""] {
            assert!(check(bad, "1").is_err(), "{bad} should be refused");
        }
    }

    #[test]
    fn values_are_checked_by_type() {
        assert_eq!(check("private_dns_mode", "hostname").unwrap().1, "hostname");
        assert!(check("private_dns_mode", "stealth").is_err());
        assert_eq!(check("screensaver_enabled", "off").unwrap().1, "0");
        assert_eq!(check("screen_off_timeout", " 30 ").unwrap().1, "30");
        assert!(check("screen_off_timeout", "0").is_err(), "one minute is the floor");
        assert!(check("screen_off_timeout", "9999").is_err());
        assert!(check("screen_off_timeout", "abc").is_err());
        assert!(check("private_dns_specifier", "two words").is_err());
        assert!(check("private_dns_specifier", "dns.example.com").is_ok());
    }

    #[test]
    fn the_specs_are_complete() {
        let mut seen = std::collections::HashSet::new();
        for spec in SETTINGS {
            assert!(seen.insert(spec.key), "{} is listed twice", spec.key);
            assert!(!spec.label.is_empty() && !spec.help.is_empty());
            assert!(matches!(spec.kind, "bool" | "int" | "select" | "text"));
            if spec.kind == "select" {
                assert!(!spec.options.is_empty(), "{} has no options", spec.key);
            }
            if spec.kind == "int" {
                assert!(spec.min < spec.max, "{} has no range", spec.key);
            }
        }
    }
}
