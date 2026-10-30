//! The safety rules from `tools/disable.sh`, `tools/check.py` and `PROMPT.md`, in one place.
//!
//! Every debloat action in the app goes through [`assess`] before a command reaches the TV, and the UI
//! shows the same verdict. The rules are kept in step with the scripts: a package the scripts refuse is
//! refused here too, with the same reasoning, so the app cannot be a way around them.

use regex::Regex;
use serde::Serialize;
use std::sync::OnceLock;

fn core_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(
            r"^(android|android\.ext\.(services|shared)|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$",
        )
        .expect("core regex")
    })
}

fn core_ok_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(r"^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$")
            .expect("core ok regex")
    })
}

/// A factory or burn-in test app. These are never started, not even to look at them: opening one on
/// some boards flips a factory-mode flag the adb shell cannot undo (#741, PROMPT.md).
pub fn is_factory(pkg: &str) -> bool {
    let lower = pkg.to_ascii_lowercase();
    lower.contains("factorytest")
        || lower.contains("factory_test")
        || lower.contains("factorymode")
        || lower.contains("factorymenu")
        || lower.contains("hkcfac")
        || lower.contains(".fac.")
        || lower.starts_with("com.at.factory")
        || lower.contains("burnin")
        || lower.contains("agingtest")
}

/// Android's own core packages and the TV middleware: disabling one can stop the set booting.
pub fn is_core(pkg: &str) -> bool {
    core_re().is_match(pkg) && !core_ok_re().is_match(pkg)
}

/// Packages that answer Home on a stock set. Used by the Home guard.
pub fn is_known_stock_launcher(pkg: &str) -> bool {
    matches!(
        pkg,
        "com.google.android.tvlauncher"
            | "com.google.android.apps.tv.launcherx"
            | "com.google.android.leanbacklauncher"
            | "com.mitv.tvhome"
            | "com.tcl.tv"
            | "com.tcl.tvlauncher"
    ) || pkg.starts_with("com.cvte.tv.launcher")
}

pub const OUR_LAUNCHER: &str = "com.gathin.tvhome";
pub const SETUPWRAITH: &str = "com.google.android.tungsten.setupwraith";

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Core,
    Middleware,
    Launcher,
    Factory,
    Telemetry,
    Ads,
    Media,
    Voice,
    Update,
    System,
    Vendor,
    Unknown,
}

impl Kind {
    pub fn label(self) -> &'static str {
        match self {
            Kind::Core => "core",
            Kind::Middleware => "middleware",
            Kind::Launcher => "launcher",
            Kind::Factory => "factory",
            Kind::Telemetry => "telemetry",
            Kind::Ads => "ads",
            Kind::Media => "media",
            Kind::Voice => "voice",
            Kind::Update => "update",
            Kind::System => "system",
            Kind::Vendor => "vendor",
            Kind::Unknown => "unknown",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Verdict {
    /// Safe to disable, and the project's lists usually do.
    Recommended,
    /// Disabling is allowed but the app must ask first (streaming, voice, casting, updates).
    AskFirst,
    /// Needs an explicit, informed confirmation (Home owner, setup wizard).
    Danger,
    /// Refused here, with no override.
    Refused,
}

#[derive(Debug, Clone, Serialize)]
pub struct Assessment {
    pub package: String,
    pub kind: Kind,
    pub verdict: Verdict,
    pub reason: String,
}

fn matches_any(pkg: &str, needles: &[&str]) -> bool {
    let p = pkg.to_ascii_lowercase();
    needles.iter().any(|n| p.contains(n))
}

/// The static half of the assessment. The dynamic half (Home owner, setup wizard, Wi-Fi owner) needs a
/// live TV and lives in `commands::debloat`.
pub fn assess(pkg: &str) -> Assessment {
    let verdict = |kind, verdict, reason: &str| Assessment {
        package: pkg.to_string(),
        kind,
        verdict,
        reason: reason.to_string(),
    };

    if !crate::adb::is_package_name(pkg) && pkg != "android" {
        return verdict(Kind::Unknown, Verdict::Refused, "Not a valid package name.");
    }

    if is_core(pkg) {
        return verdict(
            if pkg.starts_with("com.cltv") || pkg.starts_with("com.mediatek") {
                Kind::Middleware
            } else {
                Kind::Core
            },
            Verdict::Refused,
            "Core Android or TV middleware. Without it the TV may not boot again (#884, #1578), \
             so the app refuses this even when a list asks for it.",
        );
    }

    if is_factory(pkg) {
        return verdict(
            Kind::Factory,
            Verdict::Danger,
            "Factory or burn-in test app. Never open it: on some boards it flips a factory-mode \
             flag the shell cannot undo. Disabling it is allowed, but leave it alone unless you \
             know the set.",
        );
    }

    if pkg == OUR_LAUNCHER {
        return verdict(Kind::Launcher, Verdict::Refused, "This is the clean launcher itself.");
    }

    if pkg == SETUPWRAITH {
        return verdict(
            Kind::System,
            Verdict::Danger,
            "The Google TV setup wizard. If it owns the saved Wi-Fi network, disabling it deletes \
             that network and takes the TV off Wi-Fi, and ADB goes with it. The app checks first.",
        );
    }

    if is_known_stock_launcher(pkg) || matches_any(pkg, &["launcher", ".home", "tvhome"]) {
        return verdict(
            Kind::Launcher,
            Verdict::Danger,
            "A Home screen. Disable it only after the new launcher is installed and confirmed as \
             the Home app, or the TV has nothing to show.",
        );
    }

    if matches_any(
        pkg,
        &[
            "netflix", "youtube", "primevideo", "amazonvideo", "hotstar", "disney", "spotify",
            "hbomax", "max.com", "wbd.stream", "smarters", "plex", "jellyfin", "kodi", "vlc",
            "twitch", "shahid", "tod.tv", "bein", "exxen", "blutv", "foxplay", "globoplay",
            "paramount", "peacock", "hulu", "crunchyroll", "deezer", "tidal", "iplayer", "mubi",
            "appletv", "rakuten", "viaplay", "sky", "nowtv", "canal", "claro", "dazn",
        ],
    ) {
        return verdict(
            Kind::Media,
            Verdict::AskFirst,
            "A streaming app. Ask the household first: disabling it removes it from the TV.",
        );
    }

    if matches_any(
        pkg,
        &[
            "vizzini", "alexa", "assistant", "googlequicksearchbox", "voice", "speech",
            "mediashell", "cast", "chromecast", "miracast", "leakcanary",
        ],
    ) && !matches_any(pkg, &["tts", "texttospeech"])
    {
        return verdict(
            Kind::Voice,
            Verdict::AskFirst,
            "Voice assistant, casting or a media shell. Disable it only if nobody uses it.",
        );
    }

    if matches_any(pkg, &["ota", "update", "updater", "firmware"])
        && !matches_any(pkg, &["updateservice", "updatechecker.disabled"])
    {
        return verdict(
            Kind::Update,
            Verdict::AskFirst,
            "A firmware updater. CleanYourTV keeps these on by default: an update can also fix a \
             TV, and a disabled updater is hard to bring back later.",
        );
    }

    if matches_any(
        pkg,
        &[
            "analytics", "telemetry", "metrics", "reporter", "logger", "logging", "tracking",
            "tracker", "acr", "advert", "ads", "adsdk", "adsdk", "adservice", "recommendation",
            "recommend", "sponsored", "promo", "demo", "tutorial", "feedback", "metrics",
            "ad_id", "advertising", "audience", "measurement", "consent",
        ],
    ) {
        return verdict(
            Kind::Telemetry,
            Verdict::Recommended,
            "Telemetry, advertising or recommendation code. Disabling it is what this project does \
             best, and the stock launcher is not needed for it.",
        );
    }

    if matches_any(pkg, &["cleaner", "speedup", "booster", "antivirus", "filemanager", "share"]) {
        return verdict(
            Kind::Vendor,
            Verdict::Recommended,
            "A vendor utility or duplicate share/cast app. Usually safe to disable; check nobody \
             uses it.",
        );
    }

    if pkg.starts_with("com.android.") || pkg.starts_with("com.google.android.") {
        return verdict(
            Kind::System,
            Verdict::AskFirst,
            "An Android or Google system app that is not on the core list. It is not needed for \
             boot, but check what it does with \"App details\" before disabling it.",
        );
    }

    verdict(
        Kind::Vendor,
        Verdict::AskFirst,
        "A vendor app. Package names often do not say what they do: open App details, and ask when \
         it is still unclear.",
    )
}

/// Group a package list the way PROMPT.md asks the debloat half to be planned.
pub fn summarize(packages: &[String]) -> Vec<(Kind, usize)> {
    let mut counts = std::collections::BTreeMap::new();
    for pkg in packages {
        let kind = assess(pkg).kind;
        *counts.entry(kind).or_insert(0usize) += 1;
    }
    counts.into_iter().collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_packages_are_refused() {
        for pkg in [
            "android",
            "android.ext.services",
            "android.ext.shared",
            "com.android.systemui",
            "com.android.shell",
            "com.android.phone",
            "com.android.settings",
            "com.android.tv.settings",
            "com.android.providers.media",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.google.android.overlay.modules.permissioncontroller",
            "com.cltv.mal",
            "com.mediatek.tis",
            "com.mediatek.tv.service",
            "com.mediatek.tv.agent",
        ] {
            let a = assess(pkg);
            assert_eq!(a.verdict, Verdict::Refused, "{pkg}: {}", a.reason);
        }
    }

    #[test]
    fn provider_exceptions_are_allowed() {
        for pkg in [
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.providers.userdictionary",
            "com.android.providers.blockednumber",
        ] {
            assert_ne!(assess(pkg).verdict, Verdict::Refused, "{pkg}");
        }
    }

    #[test]
    fn the_launcher_and_setup_wizard_are_guarded() {
        assert_eq!(assess(OUR_LAUNCHER).verdict, Verdict::Refused);
        assert_eq!(assess("com.google.android.tvlauncher").verdict, Verdict::Danger);
        assert_eq!(assess(SETUPWRAITH).verdict, Verdict::Danger);
        assert_eq!(assess("com.cvte.tv.launcher.happy").verdict, Verdict::Danger);
    }

    #[test]
    fn streaming_and_updaters_ask_first() {
        assert_eq!(assess("com.netflix.ninja").verdict, Verdict::AskFirst);
        assert_eq!(assess("com.amazon.amazonvideo.livingroom").verdict, Verdict::AskFirst);
        assert_eq!(assess("com.mediatek.ota").verdict, Verdict::AskFirst);
        assert_eq!(assess("com.google.android.tvrecommendations").kind, Kind::Telemetry);
        assert_eq!(assess("com.google.android.tvrecommendations").verdict, Verdict::Recommended);
    }

    #[test]
    fn factory_apps_are_marked() {
        for pkg in [
            "com.cvte.tv.hkcfac",
            "mediatek.factorymenu.ui",
            "com.at.factory.menu",
            "com.duokan.factorytest",
        ] {
            assert!(is_factory(pkg), "{pkg}");
        }
        assert!(!is_factory("com.google.android.tvlauncher"));
    }
}
