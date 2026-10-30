//! The Home, setup-wizard and input/Settings guards from `tools/disable.sh`, as one snapshot plus a
//! pure decision, so the rules can be tested without a TV.
//!
//! `disable.sh` asks the TV three questions before it disables anything: who owns Home, which apps
//! claim Home, and which apps open the HDMI/AV inputs and Settings. The same three questions are
//! asked here, once per batch, and the answers decide every package in it.

use super::{current_home, current_user};
use crate::adb::{is_package_name, Adb};
use crate::rules;
use regex::Regex;
use std::sync::OnceLock;
use std::time::Duration;

fn activity_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)").expect("activity regex"))
}

/// Packages that claim HOME but are not launchers: the setup wizard, the settings apps and the
/// provisioning app. A fallback Home does not count either, nor a factory test app.
fn not_a_launcher(pkg: &str) -> bool {
    matches!(
        pkg,
        "com.google.android.tungsten.setupwraith"
            | "com.android.tv.settings"
            | "com.android.settings"
            | "com.android.provision"
    ) || rules::is_factory(pkg)
}

fn components(text: &str) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for line in text.lines() {
        if let Some(c) = activity_re().captures(line) {
            let name = c[1].to_string();
            if !out.contains(&name) {
                out.push(name);
            }
        }
    }
    out
}

/// What the TV said about Home and about the apps the remote's input and Settings buttons need.
#[derive(Debug, Default, Clone)]
pub struct Guards {
    /// The package Android would start for Home, when it named a single one. `None` means the
    /// chooser, or that the TV could not say.
    pub owner: Option<String>,
    /// Real launcher packages that claim HOME right now.
    pub launchers: Vec<String>,
    /// Whether `cmd package query-activities` answered at all.
    pub claims_ok: bool,
    /// Apps that open the HDMI/AV inputs (`ACTION_VIEW` on a channel URI).
    pub inputs: Vec<String>,
    /// Apps that open Settings.
    pub settings: Vec<String>,
    /// Whether the handler query answered. An empty answer is not "nothing to worry about".
    pub handlers_ok: bool,
    /// The setup wizard claims HOME too, ahead of an installed launcher (#1573).
    pub setupwraith_home: bool,
}

fn owner(adb: &Adb, target: &str) -> Option<String> {
    let raw = current_home(adb, target).ok()?;
    let pkg = raw.split('/').next().unwrap_or("").trim();
    if is_package_name(pkg) {
        return Some(pkg.to_string());
    }
    // Newer firmware, an older one, or the chooser: `dumpsys activity` still names the process
    // holding Home, in the same shape since Android 4.x.
    let dump = adb.shell_text(target, "dumpsys activity", Duration::from_secs(25)).ok()?;
    for line in dump.lines() {
        let Some(idx) = line.find("mHomeProcess:") else { continue };
        let rest = &line[idx + "mHomeProcess:".len()..];
        for token in rest.split_whitespace() {
            let Some((left, _)) = token.split_once('/') else { continue };
            let pkg = left.rsplit(':').next().unwrap_or(left);
            if is_package_name(pkg) {
                return Some(pkg.to_string());
            }
        }
    }
    None
}

fn handlers(adb: &Adb, target: &str) -> (Vec<String>, Vec<String>, bool) {
    // Both lists in one shell call, split at the "==" line; `; true` so a firmware without a match
    // still answers instead of failing the whole command.
    let query = "cmd package query-activities --brief -a android.intent.action.VIEW -t \
                 vnd.android.cursor.item/channel 2>/dev/null; echo ==; \
                 cmd package query-activities --brief -a android.settings.SETTINGS 2>/dev/null; true";
    let Ok(text) = adb.shell_text(target, query, Duration::from_secs(25)) else {
        return (Vec::new(), Vec::new(), false);
    };
    let (mut inputs, mut settings) = (Vec::new(), Vec::new());
    let mut after = false;
    for line in text.lines() {
        if line.trim() == "==" {
            after = true;
            continue;
        }
        for component in components(line) {
            let pkg = component.split('/').next().unwrap_or("");
            if !is_package_name(pkg) {
                continue;
            }
            let list = if after { &mut settings } else { &mut inputs };
            if !list.iter().any(|p| p == pkg) {
                list.push(pkg.to_string());
            }
        }
    }
    // A working query always has some app answering android.settings.SETTINGS.
    let ok = !settings.is_empty();
    (inputs, settings, ok)
}

/// Ask the TV once, before a batch or a single toggle.
pub fn collect(adb: &Adb, target: &str) -> Guards {
    let user = current_user(adb, target);
    let claims_text = adb
        .shell_text(
            target,
            &format!(
                "cmd package query-activities --brief --user {user} -a android.intent.action.MAIN \
                 -c android.intent.category.HOME"
            ),
            Duration::from_secs(20),
        )
        .unwrap_or_default();
    let claims = components(&claims_text);
    let claims_ok = !claims.is_empty();

    let mut launchers: Vec<String> = Vec::new();
    for claim in &claims {
        let pkg = claim.split('/').next().unwrap_or("");
        if !is_package_name(pkg) || claim.ends_with("FallbackHome") || not_a_launcher(pkg) {
            continue;
        }
        if !launchers.iter().any(|p| p == pkg) {
            launchers.push(pkg.to_string());
        }
    }
    let setupwraith_home = claims
        .iter()
        .any(|c| c.split('/').next() == Some(rules::SETUPWRAITH));

    let (inputs, settings, handlers_ok) = handlers(adb, target);
    Guards {
        owner: owner(adb, target),
        launchers,
        claims_ok,
        inputs,
        settings,
        handlers_ok,
        setupwraith_home,
    }
}

impl Guards {
    /// Why disabling `name` now would leave the TV with no way back, if it would.
    ///
    /// `disabling` is the whole batch and always contains `name`: a launcher that goes in the same
    /// run is not a way back to Home once it is gone. `allow` is the person's explicit confirmation
    /// for this package (the "ask first" checkbox).
    pub fn refusal(&self, name: &str, disabling: &[String], allow: bool) -> Option<String> {
        if let Some(reason) = self.handler_refusal(name, allow) {
            return Some(reason);
        }

        let others: Vec<&String> = self
            .launchers
            .iter()
            .filter(|p| p.as_str() != name && !disabling.iter().any(|d| d == *p))
            .collect();
        let claims_home = self.launchers.iter().any(|p| p == name);

        match self.owner.as_deref() {
            Some(owner) if owner == name => {
                if others.is_empty() {
                    return Some(format!(
                        "{name} owns Home and no other launcher would be left. Disabling it leaves \
                         the TV with no Home screen. Install the clean launcher, make it Home, \
                         confirm it, then disable this one."
                    ));
                }
                if self.setupwraith_home
                    && name != rules::SETUPWRAITH
                    && !disabling.iter().any(|d| d == rules::SETUPWRAITH)
                {
                    return Some(format!(
                        "{name} owns Home, and the setup wizard answers Home too and stays enabled, \
                         so Home would go to its RecoveryActivity: the black screen system_server \
                         can crash-loop on. Make the clean launcher Home first, or disable the \
                         setup wizard in the same run (#1573)."
                    ));
                }
                if !allow {
                    return Some(format!(
                        "{name} answers Home. Confirm this one explicitly, with the clean launcher \
                         already installed and confirmed as Home."
                    ));
                }
            }
            None if claims_home && others.is_empty() => {
                return Some(format!(
                    "Home has no default on this TV (or it could not say which app owns it), and \
                     {name} is the last app that can answer Home. Make the clean launcher Home \
                     first."
                ));
            }
            None if !self.claims_ok && rules::is_known_stock_launcher(name) => {
                return Some(format!(
                    "The TV did not say which apps answer Home, and {name} is a stock launcher, so \
                     it stays enabled. Try again once the TV answers."
                ));
            }
            _ => {}
        }
        None
    }

    /// The input and Settings apps: disabling one leaves the remote's input tiles or Settings dead,
    /// and on a set with no Settings button there is no way back to Developer options (#321, #974).
    fn handler_refusal(&self, name: &str, allow: bool) -> Option<String> {
        if allow || !self.handlers_ok {
            return None;
        }
        if self.settings.iter().any(|p| p == name) {
            return Some(format!(
                "{name} opens Settings on this TV. With it disabled Settings does not open, and on a \
                 remote without a Settings button there is no way back to Developer options. \
                 Confirm it explicitly only if you can still reach the TV another way."
            ));
        }
        if self.inputs.iter().any(|p| p == name) {
            return Some(format!(
                "{name} opens the HDMI/AV inputs on this TV. With it disabled the launcher's input \
                 tiles go black, and only a full restore brings them back. Confirm it explicitly \
                 only if you know the TV another way."
            ));
        }
        None
    }

    /// Whether this package is one the person should be asked about before it goes.
    pub fn is_handler(&self, name: &str) -> Option<&'static str> {
        if !self.handlers_ok {
            return None;
        }
        if self.settings.iter().any(|p| p == name) {
            return Some("this app opens Settings; disabled, Settings does not open");
        }
        if self.inputs.iter().any(|p| p == name) {
            return Some("this app opens the HDMI/AV inputs; disabled, the input tiles go black");
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn guards(owner: Option<&str>, launchers: &[&str]) -> Guards {
        Guards {
            owner: owner.map(str::to_string),
            launchers: launchers.iter().map(|s| s.to_string()).collect(),
            claims_ok: true,
            ..Default::default()
        }
    }

    #[test]
    fn last_launcher_is_refused() {
        let owner = guards(Some("com.google.android.tvlauncher"), &["com.google.android.tvlauncher"]);
        let name = "com.google.android.tvlauncher".to_string();
        assert!(owner.refusal(&name, &[name.clone()], true).is_some());

        // Home has no default (the chooser) and this is the only app left that can answer it.
        let chooser = guards(None, &["com.google.android.tvlauncher"]);
        assert!(chooser.refusal(&name, &[name.clone()], true).is_some());
    }

    #[test]
    fn owner_with_another_launcher_needs_confirmation() {
        let g = guards(
            Some("com.google.android.tvlauncher"),
            &["com.google.android.tvlauncher", "com.gathin.tvhome"],
        );
        let name = "com.google.android.tvlauncher".to_string();
        let reason = g.refusal(&name, &[name.clone()], false).expect("needs confirmation");
        assert!(reason.contains("answers Home"));
        assert!(g.refusal(&name, &[name.clone()], true).is_none());
    }

    #[test]
    fn a_launcher_in_the_same_batch_does_not_count() {
        let g = guards(
            Some("com.google.android.tvlauncher"),
            &["com.google.android.tvlauncher", "com.gathin.tvhome"],
        );
        let both = vec![
            "com.google.android.tvlauncher".to_string(),
            "com.gathin.tvhome".to_string(),
        ];
        assert!(g.refusal("com.google.android.tvlauncher", &both, true).is_some());
    }

    #[test]
    fn setup_wizard_answering_home_blocks_the_owner() {
        let mut g = guards(
            Some("com.google.android.tvlauncher"),
            &["com.google.android.tvlauncher", "com.gathin.tvhome"],
        );
        g.setupwraith_home = true;
        let name = "com.google.android.tvlauncher".to_string();
        let reason = g.refusal(&name, &[name.clone()], true).expect("refused");
        assert!(reason.contains("setup wizard"));
    }

    #[test]
    fn handler_guards_need_a_confirmation() {
        let mut g = guards(None, &[]);
        g.handlers_ok = true;
        g.settings = vec!["com.android.tv.settings".to_string()];
        g.inputs = vec!["com.mediatek.tvinput".to_string()];
        assert!(g.refusal("com.android.tv.settings", &[], false).is_some());
        assert!(g.refusal("com.android.tv.settings", &[], true).is_none());
        assert!(g.refusal("com.mediatek.tvinput", &[], false).is_some());
        assert!(g.is_handler("com.mediatek.tvinput").is_some());
        assert!(g.is_handler("com.netflix.ninja").is_none());
    }

    #[test]
    fn unknown_home_fails_closed_for_stock_launchers() {
        let mut g = guards(None, &[]);
        g.claims_ok = false;
        assert!(g
            .refusal("com.google.android.tvlauncher", &[], true)
            .is_some());
        assert!(g.refusal("com.example.notes", &[], true).is_none());
    }

    #[test]
    fn a_plain_app_is_never_blocked() {
        let g = guards(Some("com.google.android.tvlauncher"), &["com.google.android.tvlauncher"]);
        assert!(g.refusal("com.example.notes", &[], false).is_none());
    }

    #[test]
    fn fallback_home_is_not_a_launcher() {
        let claims = vec![
            "com.tcl.keycustomfunctionservice/.FallbackHome".to_string(),
            "com.android.tv.settings/.system.FallbackHome".to_string(),
            "com.google.android.tvlauncher/.MainActivity".to_string(),
        ];
        let mut launchers: Vec<String> = Vec::new();
        for claim in &claims {
            let pkg = claim.split('/').next().unwrap_or("");
            if claim.ends_with("FallbackHome") || not_a_launcher(pkg) {
                continue;
            }
            launchers.push(pkg.to_string());
        }
        assert_eq!(launchers, vec!["com.google.android.tvlauncher".to_string()]);
    }
}

#[cfg(all(test, unix))]
#[path = "guards_fake_tv.rs"]
mod fake_tv;
