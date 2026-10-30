/**
 * A deliberately small translation layer. The Turkish text is the key: a screen writes what it
 * says, and `t()` swaps in the English wording when English is chosen. A string with no English
 * entry falls back to the Turkish one, so a screen that is not translated yet still reads, instead
 * of showing a key like `dashboard.title`.
 *
 * `t()` is a plain module function, not a hook, so it can be called in arrays, helpers and event
 * handlers without threading a context through every file. The chosen language is pushed in by
 * `setCurrentLang()` from the app root, which also re-renders the tree on a change.
 */
import { chromeStrings } from "./i18n/en/chrome";
import { connectStrings } from "./i18n/en/connect";
import { dashboardStrings } from "./i18n/en/dashboard";
import { debloatStrings } from "./i18n/en/debloat";
import { appsStrings } from "./i18n/en/apps";
import { privacyStrings } from "./i18n/en/privacy";
import { launcherStrings } from "./i18n/en/launcher";
import { themeStrings } from "./i18n/en/theme";
import { schemaStrings } from "./i18n/en/schema";

export type Lang = "tr" | "en";
/** What the setting holds: a fixed language, or `system` to follow the operating system. */
export type LangMode = "system" | Lang;

const EN: Record<string, string> = {
  ...chromeStrings,
  ...connectStrings,
  ...dashboardStrings,
  ...debloatStrings,
  ...appsStrings,
  ...privacyStrings,
  ...launcherStrings,
  ...themeStrings,
  ...schemaStrings,
};

let current: Lang = "tr";

export function setCurrentLang(lang: Lang) {
  current = lang;
}

export function getCurrentLang(): Lang {
  return current;
}

/** The language this computer is set to, as far as the webview can tell. */
export function systemLang(): Lang {
  const list = navigator.languages?.length ? navigator.languages : [navigator.language];
  const first = (list[0] ?? "en").toLowerCase();
  return first.startsWith("tr") ? "tr" : "en";
}

export function resolveLang(mode: LangMode | null | undefined): Lang {
  if (mode === "tr" || mode === "en") return mode;
  return systemLang();
}

/**
 * Translate one string. `{name}` placeholders are filled from `vars`, so a sentence that counts
 * something can be reordered by the translation without touching the code.
 */
export function t(text: string, vars?: Record<string, string | number>): string {
  const out = current === "tr" ? text : EN[text] ?? text;
  if (!vars) return out;
  return out.replace(/\{(\w+)\}/g, (whole, key: string) =>
    vars[key] === undefined ? whole : String(vars[key]),
  );
}

/** Every English entry, for the rare screen that wants to look one up by hand. */
export function englishFor(text: string): string | undefined {
  return EN[text];
}
