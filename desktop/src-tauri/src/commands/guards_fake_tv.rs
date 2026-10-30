//! Run the guards against a fake TV: a shell script standing in for `adb`, so the exact queries this
//! module sends and the parsing of their answers are tested without a TV on the desk.

#![cfg(all(test, unix))]

use super::*;
use crate::adb::Adb;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};

static NEXT: AtomicU32 = AtomicU32::new(0);

struct FakeTv {
    dir: PathBuf,
    adb: Adb,
}

impl Drop for FakeTv {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.dir);
    }
}

/// A fake `adb` that answers the four questions the guards ask from flat files.
fn fake_tv(claims: &str, owner: &str, dumpsys: &str, handlers: &str) -> FakeTv {
    let dir = std::env::temp_dir().join(format!(
        "cyt-guards-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::SeqCst)
    ));
    fs::create_dir_all(&dir).expect("temp dir");
    fs::write(dir.join("claims.txt"), claims).unwrap();
    fs::write(dir.join("owner.txt"), owner).unwrap();
    fs::write(dir.join("dumpsys.txt"), dumpsys).unwrap();
    fs::write(dir.join("handlers.txt"), handlers).unwrap();

    let script = format!(
        r#"#!/bin/sh
DIR="{dir}"
last=""
for a in "$@"; do last="$a"; done
if [ "$last" = "version" ]; then
  echo "Android Debug Bridge version 1.0.41"
  exit 0
fi
case "$last" in
  *query-activities*category.HOME*) cat "$DIR/claims.txt" ;;
  *resolve-activity*) cat "$DIR/owner.txt" ;;
  *dumpsys*activity*) cat "$DIR/dumpsys.txt" ;;
  *cursor.item/channel*) cat "$DIR/handlers.txt" ;;
  *) : ;;
esac
exit 0
"#,
        dir = dir.display()
    );
    let adb_path = dir.join("adb");
    fs::write(&adb_path, script).unwrap();
    let mut perms = fs::metadata(&adb_path).unwrap().permissions();
    perms.set_mode(0o755);
    fs::set_permissions(&adb_path, perms).unwrap();

    let adb = Adb::locate(Some(adb_path.to_str().unwrap())).expect("the fake adb answers `version`");
    FakeTv { dir, adb }
}

const TV_LAUNCHER: &str = "com.google.android.tvlauncher";
const OUR_LAUNCHER: &str = "com.gathin.tvhome";

#[test]
fn reads_home_launchers_and_handlers_from_the_tv() {
    let tv = fake_tv(
        &format!(
            "{TV_LAUNCHER}/.MainActivity\n\
             {OUR_LAUNCHER}/.MainActivity\n\
             com.google.android.tungsten.setupwraith/.RecoveryActivity\n\
             com.tcl.keycustomfunctionservice/.FallbackHome\n"
        ),
        &format!("{TV_LAUNCHER}/.MainActivity"),
        "",
        "com.mediatek.tvinput/.TvInputService\n\
         ==\n\
         com.android.tv.settings/.Settings\n",
    );

    let guards = collect(&tv.adb, "192.0.2.10:5555");

    assert_eq!(
        guards.launchers,
        vec![TV_LAUNCHER.to_string(), OUR_LAUNCHER.to_string()]
    );
    assert!(guards.claims_ok);
    assert!(guards.setupwraith_home, "the wizard claims HOME");
    assert_eq!(guards.owner.as_deref(), Some(TV_LAUNCHER));
    assert_eq!(guards.inputs, vec!["com.mediatek.tvinput".to_string()]);
    assert_eq!(guards.settings, vec!["com.android.tv.settings".to_string()]);
    assert!(guards.handlers_ok);

    // Another launcher is installed and Home is the stock one: the person has to say so.
    let name = TV_LAUNCHER.to_string();
    let reason = guards
        .refusal(&name, std::slice::from_ref(&name), false)
        .expect("needs confirmation");
    assert!(reason.contains("answers Home"), "{reason}");

    // But the setup wizard answers Home too here, so even a confirmed disable is refused (#1573).
    let reason = guards
        .refusal(&name, std::slice::from_ref(&name), true)
        .expect("setup wizard blocks it");
    assert!(reason.contains("setup wizard"), "{reason}");

    // Disabling the wizard in the same run is the way the project says to do it.
    let both = vec![name.clone(), crate::rules::SETUPWRAITH.to_string()];
    assert!(guards.refusal(&name, &both, true).is_none());

    // The Settings app and the input app are "ask first", never silent.
    assert!(guards.is_handler("com.android.tv.settings").is_some());
    assert!(guards.refusal("com.android.tv.settings", &[], false).is_some());
    assert!(guards.refusal("com.android.tv.settings", &[], true).is_none());
    assert!(guards.is_handler("com.mediatek.tvinput").is_some());
    assert!(guards.is_handler("com.netflix.ninja").is_none());
}

#[test]
fn falls_back_to_dumpsys_when_home_has_no_default() {
    let tv = fake_tv(
        &format!("{TV_LAUNCHER}/.MainActivity\n"),
        // The chooser answered: Android 7 and older print the owning component as "android".
        "android\n",
        "  mHomeProcess: ProcessRecord{a1b2c3d4 2f4b:com.vendor.home/u0a56}\n",
        "==\ncom.android.tv.settings/.Settings\n",
    );

    let guards = collect(&tv.adb, "192.0.2.10:5555");
    assert_eq!(guards.owner.as_deref(), Some("com.vendor.home"));
}

#[test]
fn a_tv_that_answers_nothing_fails_closed() {
    let tv = fake_tv("", "", "", "");
    let guards = collect(&tv.adb, "192.0.2.10:5555");

    assert!(!guards.claims_ok);
    assert!(!guards.handlers_ok);
    assert!(guards.launchers.is_empty());
    // A stock launcher stays enabled when the TV cannot say who answers Home.
    assert!(guards.refusal(TV_LAUNCHER, &[], true).is_some());
    // Everything else is judged by the static rules alone.
    assert!(guards.refusal("com.netflix.ninja", &[], false).is_none());
}
