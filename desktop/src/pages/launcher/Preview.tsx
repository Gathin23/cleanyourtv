import { convertFileSrc } from "@tauri-apps/api/core";
import { useMemo } from "react";
import { Badge, Button, Card, Icon } from "../../ui";
import { useApp } from "../../app-context";
import { useThemeForm } from "./theme-store";
import { t } from "../../i18n";

const SAMPLE_APPS = [
  { name: "Netflix", color: "#e50914" },
  { name: "YouTube", color: "#ff0033" },
  { name: "Prime Video", color: "#00a8e1" },
  { name: "Disney+", color: "#113ccf" },
  { name: "Max", color: "#5b2bd9" },
  { name: "Spotify", color: "#1db954" },
  { name: "Plex", color: "#e5a00d" },
  { name: "Kodi", color: "#17b2e7" },
];

const GREETINGS: Record<string, [string, string, string, string]> = {
  tr: ["Günaydın", "İyi günler", "İyi akşamlar", "İyi geceler"],
  en: ["Good morning", "Good afternoon", "Good evening", "Good night"],
  de: ["Guten Morgen", "Guten Tag", "Guten Abend", "Gute Nacht"],
  fr: ["Bonjour", "Bon après-midi", "Bonsoir", "Bonne nuit"],
};

function greeting(locale: string, hour: number): string {
  const lang = (locale || "tr").split(/[-_]/)[0].toLowerCase();
  const words = GREETINGS[lang] ?? GREETINGS.en;
  if (hour < 6) return words[3];
  if (hour < 12) return words[0];
  if (hour < 18) return words[1];
  return words[2];
}

function hex(value: unknown, fallback: string): string {
  return typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value) ? value : fallback;
}

/**
 * A mock Home drawn from the values in the form: no TV, no write. It is not pixel-perfect, it is
 * there to answer "what will the dock/clock/focus look like before I push this".
 */
export default function PreviewPage({ go }: { go: (tab: string) => void }) {
  const { conn } = useApp();
  const { values, changed, wallpaper, status } = useThemeForm();

  const now = new Date();
  const hour = now.getHours();
  const clock = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  const date = now.toLocaleDateString("tr-TR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  });

  const background = hex(values.background_color, "#000000");
  const columns = Math.min(8, Math.max(1, Number(values.columns) || 4));
  const rows = Math.min(3, Math.max(0, Number(values.rows_below_dock) || 0));
  const dockOpacity = Math.min(100, Math.max(30, Number(values.dock_opacity) || 100)) / 100;
  const dim = Math.min(90, Math.max(0, Number(values.wallpaper_dim) || 0)) / 100;
  const sky = Math.min(100, Math.max(0, Number(values.sky_intensity) || 0)) / 100;
  const dockApps = useMemo(() => {
    const chosen = Array.isArray(values.dock_apps) ? (values.dock_apps as string[]) : [];
    const known = chosen
      .map((pkg) => SAMPLE_APPS.find((a) => pkg.includes(a.name.toLowerCase().replace(/\W/g, ""))) ?? null)
      .filter(Boolean) as { name: string; color: string }[];
    const merged = [...known];
    for (const app of SAMPLE_APPS) {
      if (merged.length >= columns) break;
      if (!merged.some((m) => m.name === app.name)) merged.push(app);
    }
    return merged.slice(0, Math.max(columns, known.length));
  }, [columns, values.dock_apps]);

  const glass = String(values.glass_style ?? "glass");
  const focus = {
    glass: "0 0 0 2px rgba(255,255,255,0.9), 0 18px 44px rgba(0,0,0,0.55)",
    rim: "0 0 0 2px rgba(255,255,255,0.75)",
    halo: "0 0 0 2px rgba(255,255,255,0.8), 0 0 34px rgba(124,212,255,0.45)",
  }[glass] ?? "none";
  const showNames = !!values.always_show_names;
  const dockBand = values.dock_band !== false;
  const dockGlass = !!values.dock_glass;
  const dockRow = values.dock_row !== false;
  const cinematic = !!values.cinematic_home;
  const showGreeting = values.show_greeting !== false;
  const name = String(values.greeting_name ?? "").trim();
  const locale = String(values.locale ?? "tr");
  const hero = dockApps[0] ?? SAMPLE_APPS[0];

  const tile = (app: { name: string; color: string }, i: number) => (
    <div
      key={`${app.name}-${i}`}
      className="preview-tile"
      style={{
        background: `linear-gradient(155deg, ${app.color}dd, ${app.color}66 60%, #14161c)`,
        boxShadow: i === 0 ? focus : undefined,
      }}
    >
      {showNames && <span>{app.name}</span>}
    </div>
  );

  return (
    <div className="stack gap-4">
      <Card
        tone="accent"
        title={t("Önizleme")}
        subtitle={t("Bu, formdaki değerlerden çizilen bir maket. TV'ye hiçbir şey yazmaz, TV bağlı olmasa da çalışır.")}
        actions={
          <>
            <Badge tone={changed > 0 ? "ok" : "muted"}>{changed} {t("değişiklik")}</Badge>
            <Button size="sm" variant="ghost" icon="edit" onClick={() => go("theme")}>
              {t("Ayarlara dön")}
            </Button>
          </>
        }
      >
        <div className="preview-frame">
          <div className="preview-screen" style={{ background }}>
            {wallpaper && (
              <img
                className="preview-wallpaper"
                src={convertFileSrc(wallpaper)}
                alt=""
                style={{ opacity: values.wallpaper === false ? 0 : 1, filter: `brightness(${1 - dim})` }}
              />
            )}
            {sky > 0 && (
              <div
                className="preview-sky"
                style={{ opacity: sky, background: "linear-gradient(180deg, #3a2a55, #05060a 70%)" }}
              />
            )}

            <div className="preview-top">
              {cinematic && (
                <div
                  className="preview-hero"
                  style={{
                    background: `radial-gradient(60% 120% at 20% 0%, ${hero.color}55, transparent 70%)`,
                  }}
                />
              )}
              <div className="preview-head">
                <div className="col">
                  {showGreeting && (
                    <span className="preview-greeting">
                      {greeting(locale, hour)}
                      {name ? `, ${name.toLocaleUpperCase("tr-TR")}` : ""}
                    </span>
                  )}
                  <span className="preview-clock">{clock}</span>
                  <span className="preview-date">{date}</span>
                  {!!values.show_stats && (
                    <span className="preview-stats">{t("%12 CPU · 640 MB boş · 3.1 GB boş alan")}</span>
                  )}
                </div>
                <div className="preview-pills">
                  {cinematic && dockApps.length > 1 && (
                    <div className="preview-dots">
                      {dockApps.map((app, i) => (
                        <span key={app.name + i} className={i === 0 ? "on" : ""} />
                      ))}
                    </div>
                  )}
                  {values.show_network_pill !== false && (
                    <span className="preview-pill">
                      <Icon name="wifi" size={13} />
                      {!values.icon_only_buttons && <span>{t("Ev Wi-Fi")}</span>}
                    </span>
                  )}
                  {values.show_inputs_pill !== false && (
                    <span className="preview-pill">
                      <Icon name="input" size={13} />
                      {!values.icon_only_inputs && <span>{t("Girişler")}</span>}
                    </span>
                  )}
                  <span className="preview-pill">
                    <Icon name={values.launcher_settings_button ? "home" : "settings"} size={13} />
                  </span>
                </div>
              </div>

              {values.dock_title !== false && (
                <span className="preview-dock-title">{hero.name}</span>
              )}
            </div>

            <div className="preview-bottom">
              {rows > 0 && (
                <div
                  className="preview-grid"
                  style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
                >
                  {SAMPLE_APPS.slice(0, columns * rows).map((app, i) => tile(app, i + 1))}
                </div>
              )}
              <div
                className={`preview-dock ${dockRow ? "" : "plain"} ${dockBand ? "" : "bandless"} ${
                  dockGlass ? "glass" : ""
                }`}
                style={{
                  background: dockBand
                    ? dockGlass
                      ? `rgba(255,255,255,${0.1 * dockOpacity})`
                      : `rgba(10,11,15,${0.92 * dockOpacity})`
                    : "transparent",
                  borderColor: dockBand ? "rgba(255,255,255,0.14)" : "transparent",
                  gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                }}
              >
                {dockApps.map(tile)}
                {Array.from({ length: Math.max(0, columns - dockApps.length) }).map((_, i) => (
                  <div key={`empty-${i}`} className="preview-tile empty" />
                ))}
              </div>
            </div>
          </div>
        </div>

        <p className="small muted mt-3">
          {t("Maket; TV'nin kendi çizimi değil. Duvar kağıdı yalnız bu bilgisayardan seçtiğiniz dosyayla gösterilir, TV'deki koleksiyon burada görünmez.")}
        </p>
      </Card>

      <Card title={t("Bu makette ne var?")}>
        <div className="grid grid-2">
          <div className="col gap-1">
            <span className="small">
              <strong>{t("Dock:")}</strong> {dockRow ? t("ayrı satır") : t("düz ızgara")},{" "}
              {dockBand ? (dockGlass ? "buzlu cam bant" : "koyu bant") : t("bantsız")}, %{Math.round(dockOpacity * 100)}  {t("opak")}
            </span>
            <span className="small">
              <strong>{t("Izgara:")}</strong> {columns} {t("sütun,")} {rows} {t("satır")}
            </span>
            <span className="small">
              <strong>{t("Odak:")}</strong> {glass} · {String(values.glow_style ?? "radial")}
            </span>
          </div>
          <div className="col gap-1">
            <span className="small">
              <strong>{t("Saat:")}</strong> {showGreeting ? t("selamlama açık") : t("selamlama kapalı")}
              {name ? ` · ${name}` : ""}
            </span>
            <span className="small">
              <strong>{t("Üst raf:")}</strong> {cinematic ? "sinematik" : t("düz")}
            </span>
            <span className="small">
              <strong>{t("Katman:")}</strong>{" "}
              {conn?.connected
                ? status?.overlay_exists
                  ? "TV'de bir katman var"
                  : t("TV'de katman yok")
                : t("TV bağlı değil")}
            </span>
          </div>
        </div>
      </Card>
    </div>
  );
}
