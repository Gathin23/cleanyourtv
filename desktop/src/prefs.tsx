/**
 * How the window is drawn: the language and the light/dark theme. Both follow the operating system
 * until someone picks a fixed value in Settings, and both are stored in the app's own settings file
 * next to the ADB path, so they survive a restart and are the same on every screen.
 */
import { useEffect, useMemo, useState } from "react";
import { resolveLang, setCurrentLang, type Lang, type LangMode } from "./i18n";

export type Theme = "dark" | "light";
export type ThemeMode = "system" | Theme;

type Prefs = {
  /** The language in use, after resolving `system`. */
  lang: Lang;
  langMode: LangMode;
  /** The theme in use, after resolving `system`. */
  theme: Theme;
  themeMode: ThemeMode;
};

function systemTheme(): Theme {
  return window.matchMedia?.("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

export function resolveTheme(mode: ThemeMode | null | undefined): Theme {
  if (mode === "dark" || mode === "light") return mode;
  return systemTheme();
}

/** The OS colour scheme, kept live: switching it in the system settings moves the app with it. */
export function useSystemTheme(): Theme {
  const [theme, setTheme] = useState<Theme>(systemTheme);
  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: light)");
    const onChange = () => setTheme(media.matches ? "light" : "dark");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);
  return theme;
}

export function usePrefs(settings: {
  ui_locale?: string | null;
  ui_theme?: string | null;
} | null): Prefs {
  const system = useSystemTheme();
  return useMemo(() => {
    const langMode = (settings?.ui_locale as LangMode | undefined) ?? "system";
    const themeMode = (settings?.ui_theme as ThemeMode | undefined) ?? "system";
    const lang = resolveLang(langMode);
    // `t()` reads this, so it has to be right before the tree below paints.
    setCurrentLang(lang);
    return {
      lang,
      langMode,
      theme: themeMode === "system" ? system : themeMode,
      themeMode,
    };
  }, [settings?.ui_locale, settings?.ui_theme, system]);
}

/**
 * The first paint, before the saved settings have been read: follow the system, so the window never
 * flashes the wrong theme while the settings file is being opened.
 */
export function bootstrapPrefs() {
  applyPrefs({
    lang: resolveLang("system"),
    langMode: "system",
    theme: resolveTheme("system"),
    themeMode: "system",
  });
}

/** Push the resolved language and theme onto the document; called from the app root. */
export function applyPrefs(prefs: Prefs) {
  const root = document.documentElement;
  root.dataset.theme = prefs.theme;
  root.lang = prefs.lang;
  root.style.colorScheme = prefs.theme;
}
