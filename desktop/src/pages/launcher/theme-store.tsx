import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import * as api from "../../api";
import type { LauncherStatus, ThemeKey, ThemeReport } from "../../api";
import { useApp } from "../../app-context";
import { t } from "../../i18n";
import { useToast } from "../../ui";

type Values = Record<string, unknown>;

/** A named starting point: the keys a preset sets, and nothing else. */
export type Preset = { id: string; label: string; note: string; values: Values };

/**
 * Starting points for people who do not want to read 90 keys. Each one only sets the keys it is about,
 * so a preset on top of an existing overlay keeps everything else.
 */
export const PRESETS: Preset[] = [
  {
    id: "clear",
    label: "Dengeli",
    note: "Projede kullandığımız görünüm: sade üst bar, dört sütun, yumuşak hareket.",
    values: {
      columns: 4,
      rows_below_dock: 1,
      dock_row: true,
      dock_title: true,
      fill_dock: true,
      dock_glass: true,
      dock_opacity: 100,
      show_network_pill: true,
      show_inputs_pill: true,
      show_greeting: true,
      animations: true,
      advanced_effects: false,
      focus_overshoot: true,
      reduced_motion: false,
      app_glow: false,
      cinematic_home: false,
      glass_style: "glass",
      glow_style: "radial",
      background_color: "#000000",
    },
  },
  {
    id: "fast",
    label: "Zayıf TV",
    note: "Azaltılmış hareket, animasyonsuz, gölgesiz: 1 GB RAM'li setler için.",
    values: {
      reduced_motion: true,
      animations: false,
      advanced_effects: false,
      focus_overshoot: false,
      app_glow: false,
      ambient_glow: false,
      cinematic_home: false,
      sky_intensity: 0,
      fade_rows: false,
      wallpaper_true_color: false,
      glass_style: "rim",
    },
  },
  {
    id: "cinema",
    label: "Sinematik",
    note: "Üst raf, uygulama renginde parlama, buzlu cam dock.",
    values: {
      cinematic_home: true,
      cinematic_dots: true,
      cinematic_scrim: 80,
      cinematic_art: 60,
      dock_glass: true,
      dock_opacity: 70,
      ambient_glow: true,
      app_glow: true,
      fade_rows: true,
      sky_intensity: 35,
      rows_below_dock: 1,
    },
  },
  {
    id: "photo",
    label: "Fotoğraf odaklı",
    note: "Duvar kağıdı öne çıkar: hafif karartma, saydam dock, sade çerçeve.",
    values: {
      wallpaper: true,
      wallpaper_dim: 30,
      dock_glass: true,
      dock_opacity: 60,
      dock_band: true,
      ambient_glow: false,
      app_glow: false,
      sky_intensity: 0,
      header_white: true,
      glass_style: "rim",
      fade_rows: true,
    },
  },
  {
    id: "retro",
    label: "Retro siyah",
    note: "Bantsız dock, yalnız çerçeve odak, renk parlaması yok.",
    values: {
      background_color: "#000000",
      dock_band: false,
      dock_glass: false,
      dock_opacity: 100,
      dock_title: true,
      glass_style: "rim",
      glow_style: "radial",
      ambient_glow: false,
      app_glow: false,
      icon_only_buttons: true,
      icon_only_inputs: true,
      wallpaper_dim: 0,
    },
  },
  {
    id: "wide",
    label: "Geniş ızgara",
    note: "Altı sütun, iki satır: çok uygulamalı evler için.",
    values: {
      columns: 6,
      rows_below_dock: 2,
      always_show_names: false,
      uniform_tiles: true,
      fill_dock: true,
    },
  },
];

type Store = {
  keys: ThemeKey[];
  values: Values;
  extra: Values;
  status: LauncherStatus | null;
  loading: boolean;
  busy: string | null;
  report: ThemeReport | null;
  wallpaper: string | null;
  wallpapers: string[];
  groups: string[];
  payload: Values;
  changed: number;
  set: (key: string, value: unknown) => void;
  resetField: (key: string) => void;
  resetGroup: (group: string) => void;
  applyPreset: (preset: Preset) => void;
  setWallpaper: (path: string | null) => void;
  setWallpapers: (paths: string[]) => void;
  setReport: (report: ThemeReport | null) => void;
  reload: () => Promise<void>;
  push: () => Promise<void>;
  reset: () => Promise<void>;
};

const Ctx = createContext<Store | null>(null);

export function useThemeForm(): Store {
  const store = useContext(Ctx);
  if (!store) throw new Error("useThemeForm outside ThemeStore");
  return store;
}

function defaultFor(key: ThemeKey): unknown {
  if (key.default !== undefined) return key.default;
  if (key.type === "packages" || key.type === "list" || key.type === "ints") return [];
  if (key.type === "int") return key.min ?? 0;
  if (key.type === "json") return "";
  return "";
}

export function ThemeStore({ children }: { children: ReactNode }) {
  const toast = useToast();
  const { conn } = useApp();
  const [keys, setKeys] = useState<ThemeKey[]>([]);
  const [values, setValues] = useState<Values>({});
  const [extra, setExtra] = useState<Values>({});
  const [status, setStatus] = useState<LauncherStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [report, setReport] = useState<ThemeReport | null>(null);
  const [wallpaper, setWallpaper] = useState<string | null>(null);
  const [wallpapers, setWallpapers] = useState<string[]>([]);

  const hydrate = useCallback((schema: ThemeKey[], overlay: string | null) => {
    const initial: Values = {};
    for (const key of schema) initial[key.key] = defaultFor(key);
    const extras: Values = {};
    if (overlay) {
      try {
        const parsed = JSON.parse(overlay) as Values;
        for (const [key, value] of Object.entries(parsed)) {
          if (schema.some((k) => k.key === key)) initial[key] = value;
          else extras[key] = value;
        }
      } catch {
        /* a broken overlay is exactly why someone opens this page */
      }
    }
    setValues(initial);
    setExtra(extras);
  }, []);

  const load = useCallback(async () => {
    if (!conn?.connected) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [schema, current] = await Promise.all([api.themeKeys(), api.launcherStatus()]);
      setKeys(schema);
      setStatus(current);
      hydrate(schema, current.overlay);
    } catch (e) {
      toast.error(e, t("Tema şeması okunamadı"));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conn?.connected, hydrate]);

  useEffect(() => {
    load();
  }, [load]);

  const payload = useMemo(() => {
    const out: Values = { ...extra };
    for (const key of keys) {
      // A config.xml key is compiled into the APK; sending it would only be ignored.
      if (key.only) continue;
      const value = values[key.key];
      if (value === undefined || value === null || value === "") continue;
      if (Array.isArray(value) && value.length === 0) continue;
      if (key.type === "int") {
        const n = Number(value);
        if (!Number.isFinite(n)) continue;
        if (key.default !== undefined && n === Number(key.default)) continue;
        out[key.key] = n;
        continue;
      }
      if (key.default !== undefined && value === key.default) continue;
      out[key.key] = value;
    }
    return out;
  }, [extra, keys, values]);

  const groups = useMemo(() => {
    const order: string[] = [];
    for (const key of keys) if (!order.includes(key.group)) order.push(key.group);
    return order;
  }, [keys]);

  const set = useCallback((key: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [key]: value }));
  }, []);

  const resetField = useCallback(
    (key: string) => {
      const spec = keys.find((k) => k.key === key);
      if (spec) setValues((prev) => ({ ...prev, [key]: defaultFor(spec) }));
    },
    [keys],
  );

  const resetGroup = useCallback(
    (group: string) => {
      setValues((prev) => {
        const next = { ...prev };
        for (const key of keys.filter((k) => k.group === group)) next[key.key] = defaultFor(key);
        return next;
      });
    },
    [keys],
  );

  const applyPreset = useCallback((preset: Preset) => {
    setValues((prev) => {
      const next = { ...prev };
      for (const [key, value] of Object.entries(preset.values)) {
        if (key in next) next[key] = value;
      }
      return next;
    });
  }, []);

  const push = useCallback(async () => {
    setBusy("push");
    try {
      const result = await api.themePush(JSON.stringify(payload, null, 2), wallpaper, wallpapers);
      setReport(result);
      await load();
      toast.push("ok", t("Tema uygulandı"), t("{n} adım · Home yeniden başladı", { n: result.steps.length }));
    } catch (e) {
      toast.error(e, t("Tema uygulanamadı"));
    } finally {
      setBusy(null);
    }
  }, [load, payload, toast, wallpaper, wallpapers]);

  const reset = useCallback(async () => {
    setBusy("reset");
    try {
      const result = await api.themeReset();
      setReport(result);
      setWallpaper(null);
      setWallpapers([]);
      await load();
      toast.push("ok", t("Katman kaldırıldı"), t("Başlatıcı kendi config.xml'ine döndü."));
    } catch (e) {
      toast.error(e, t("Katman kaldırılamadı"));
    } finally {
      setBusy(null);
    }
  }, [load, toast]);

  const value: Store = {
    keys,
    values,
    extra,
    status,
    loading,
    busy,
    report,
    wallpaper,
    wallpapers,
    groups,
    payload,
    changed: Object.keys(payload).length,
    set,
    resetField,
    resetGroup,
    applyPreset,
    setWallpaper,
    setWallpapers,
    setReport,
    reload: load,
    push,
    reset,
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
