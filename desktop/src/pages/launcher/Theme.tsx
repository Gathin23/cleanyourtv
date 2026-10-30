import { useMemo, useState } from "react";
import { open as openDialog, save as saveDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../../api";
import type { ThemeKey } from "../../api";
import {
  Badge,
  Button,
  Card,
  Code,
  Empty,
  Field,
  Icon,
  Input,
  Modal,
  Select,
  Spinner,
  Switch,
  TextArea,
  useToast,
} from "../../ui";
import { Steps } from "../../parts";
import { useApp } from "../../app-context";
import PackagePicker from "./PackagePicker";
import { PRESETS, useThemeForm, type Preset } from "./theme-store";
import { Prose } from "../../parts";
import { t } from "../../i18n";

function asLines(v: unknown): string {
  if (Array.isArray(v)) return v.join("\n");
  if (v === undefined || v === null) return "";
  return String(v);
}

function fromLines(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

const PACKAGE_FIELDS = new Set([
  "dock_apps",
  "hidden_apps",
  "top_bar_apps",
  "library_apps",
  "library_excluded_apps",
  "icon_tiles",
]);

export default function ThemePage({ go }: { go: (tab: string) => void }) {
  const toast = useToast();
  const { conn } = useApp();
  const form = useThemeForm();
  const {
    keys,
    values,
    status,
    loading,
    busy,
    report,
    wallpaper,
    wallpapers,
    groups,
    payload,
    changed,
    set,
    resetField,
    resetGroup,
    applyPreset,
    setWallpaper,
    setWallpapers,
    setReport,
    reload,
    push,
    reset,
  } = form;

  const [group, setGroup] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [preview, setPreview] = useState(false);
  const [picker, setPicker] = useState<ThemeKey | null>(null);

  const dirty = (key: ThemeKey) => {
    const value = values[key.key];
    if (Array.isArray(value)) return value.length > 0;
    if (key.default !== undefined) return value !== key.default && value !== "";
    return value !== undefined && value !== null && value !== "";
  };

  const shown = useMemo(() => {
    const query = search.trim().toLowerCase();
    return keys.filter((k) => {
      if (query) {
        return (
          k.key.toLowerCase().includes(query) ||
          (k.label ?? "").toLowerCase().includes(query) ||
          (k.help ?? "").toLowerCase().includes(query)
        );
      }
      return group === null ? true : k.group === group;
    });
  }, [group, keys, search]);

  const shownGroups = useMemo(() => {
    const order: string[] = [];
    for (const key of shown) if (!order.includes(key.group)) order.push(key.group);
    return order;
  }, [shown]);

  const field = (key: ThemeKey) => {
    const value = values[key.key];
    const isOnly = !!key.only;
    const label = (
      <div className="label-row">
        <span className="theme-label" title={key.help ?? key.key}>
          {key.label ?? key.key}
        </span>
        <span className="key-pill" title={t("config.json anahtarı")} onClick={() => set(key.key, value)}>
          {key.key}
        </span>
      </div>
    );

    const control = () => {
      if (isOnly) {
        return (
          <div className="only-row">
            <Badge tone="warn">config.xml</Badge>
            <span className="tiny muted">{t("APK'ya derlenir")}</span>
          </div>
        );
      }
      if (key.type === "bool") {
        return (
          <Switch
            checked={!!value}
            onChange={(v) => set(key.key, v)}
            label={value ? t("açık") : t("kapalı")}
          />
        );
      }
      if (key.type === "int") {
        const n = Number(value) || 0;
        return (
          <div className="row gap-2">
            <input
              type="range"
              min={key.min ?? 0}
              max={key.max ?? 100}
              value={n}
              onChange={(e) => set(key.key, Number(e.target.value))}
              style={{ flex: 1, accentColor: "var(--accent)" }}
            />
            <input
              className="input"
              style={{ width: 78 }}
              type="number"
              min={key.min ?? 0}
              max={key.max ?? 100}
              value={n}
              onChange={(e) => set(key.key, Number(e.target.value))}
            />
          </div>
        );
      }
      if (key.type === "color") {
        const color =
          typeof value === "string" && /^#[0-9a-fA-F]{6}$/.test(value) ? value : "#000000";
        return (
          <div className="row gap-2">
            <input
              type="color"
              value={color}
              onChange={(e) => set(key.key, e.target.value)}
              style={{ width: 44, height: 36, border: "none", background: "none", padding: 0 }}
            />
            <Input value={String(value ?? "")} onChange={(e) => set(key.key, e.target.value)} />
          </div>
        );
      }
      if (key.type === "select") {
        return (
          <Select
            value={String(value ?? "")}
            onChange={(e) => set(key.key, e.target.value)}
            options={(key.options ?? []).map((o) => ({ value: o, label: o === "" ? "yok" : o }))}
          />
        );
      }
      if (PACKAGE_FIELDS.has(key.key)) {
        const list = Array.isArray(value) ? (value as string[]) : [];
        return (
          <div className="col gap-2">
            <div className="row gap-2 wrap">
              <Button size="sm" variant="soft" icon="grid" onClick={() => setPicker(key)}>
                {t("Uygulama seç")}
              </Button>
              <span className="tiny muted">{list.length} {t("uygulama")}</span>
              {list.length > 0 && (
                <Button size="sm" variant="bare" onClick={() => set(key.key, [])}>
                  
                  {t("Temizle")}
                </Button>
              )}
            </div>
            {list.length > 0 && (
              <div className="pkg-pills">
                {list.map((name) => (
                  <span key={name} className="pkg-pill mono">
                    {name}
                  </span>
                ))}
              </div>
            )}
          </div>
        );
      }
      if (key.type === "list" || key.type === "ints" || key.type === "json") {
        return (
          <TextArea
            style={{ minHeight: key.type === "json" ? 96 : 74 }}
            value={asLines(value)}
            onChange={(e) =>
              set(
                key.key,
                key.type === "ints"
                  ? fromLines(e.target.value)
                      .map(Number)
                      .filter((n) => Number.isFinite(n))
                  : key.type === "json"
                    ? e.target.value
                    : fromLines(e.target.value),
              )
            }
            placeholder={
              key.type === "json"
                ? '{ "morning": ["01.jpg"], "night": ["02.jpg"] }'
                : key.type === "ints"
                  ? "3\n4"
                  : key.key === "launch_actions"
                    ? "com.netflix.ninja=com.netflix.ninja.action.GUIDE"
                    : t("paket=değer, her satır bir giriş")
            }
          />
        );
      }
      return (
        <Input
          value={String(value ?? "")}
          onChange={(e) => set(key.key, e.target.value)}
          placeholder={
            key.key === "greeting_name"
              ? t("ör. Kralım")
              : key.key === "locale"
                ? "tr, de, pt-BR"
                : key.key === "private"
                  ? ""
                  : ""
          }
        />
      );
    };

    return (
      <div className={`theme-field ${dirty(key) ? "dirty" : ""}`} key={key.key}>
        {label}
        {control()}
        {key.help && <span className="theme-help">{key.help}</span>}
        <div className="field-foot">
          {dirty(key) && !isOnly && (
            <button className="link-btn" onClick={() => resetField(key.key)}>
              {t("varsayılana dön")}
            </button>
          )}
          {isOnly && (
            <button className="link-btn" onClick={() => go("config")}>
              {t("config.xml sekmesinde düzenle")}
            </button>
          )}
        </div>
      </div>
    );
  };

  if (loading) {
    return (
      <Card>
        <Spinner label={t("Tema ayarları okunuyor…")} />
      </Card>
    );
  }

  if (!conn?.connected) {
    return (
      <Card title={t("TV bağlı değil")}>
        <Empty
          icon="plug"
          title={t("Tema katmanı TV'ye yazılır")}
          text={t("Anahtarları görmek ve göndermek için önce TV'ye bağlanın. Selamlama metinleri ve config.xml bu bağlantıyı gerektirmez.")}
          action={
            <Button size="sm" variant="primary" icon="plug" onClick={() => go("connect")}>
              {t("Bağlan")}
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <div className="stack gap-4">
      <Card
        tone="accent"
        title={t("Anında tema (config.json)")}
        subtitle={t("Başlatıcı bu katmanı çalışma zamanında okur; APK derlemek gerekmez. Beğenmezseniz tek tuşla kaldırılır.")}
        actions={
          <>
            <Badge tone={status?.overlay_exists ? "ok" : "muted"}>
              {status?.overlay_exists ? "katman etkin" : "katman yok"}
            </Badge>
            <Button size="sm" variant="ghost" icon="refresh" onClick={reload}>
              
              {t("Yenile")}
            </Button>
          </>
        }
      >
        <div className="row gap-2 wrap">
          <Button variant="primary" size="sm" icon="sparkle" loading={busy === "push"} onClick={push}>
            {t("Temayı TV'ye uygula")}
          </Button>
          <Button variant="ghost" size="sm" icon="terminal" onClick={() => setPreview(true)}>
            {t("JSON önizle")}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            icon="copy"
            onClick={async () => {
              const file = await saveDialog({
                title: t("config.json olarak kaydet"),
                defaultPath: "config.json",
              });
              if (typeof file !== "string") return;
              try {
                await api.saveText(file, JSON.stringify(payload, null, 2));
                toast.push("ok", t("Kaydedildi"), file);
              } catch (e) {
                toast.error(e, t("Yazılamadı"));
              }
            }}
          >
            
            {t("JSON indir")}
          </Button>
          <Button variant="ghost" size="sm" icon="trash" loading={busy === "reset"} onClick={reset}>
            {t("Katmanı kaldır")}
          </Button>
          <div className="grow" />
          <span className="small muted">{changed} {t("anahtar değişti")}</span>
        </div>

        <div className="card card-warn" style={{ padding: 12, marginTop: 12 }}>
          <div className="row gap-2 small">
            <Icon name="info" size={16} />
            <span>
              <Prose text="**Selamlama metinleri burada değişmez.** “Günaydın” gibi metinler APK içindeki `strings.xml` dosyasında; onlar için **Selamlama** sekmesini kullanın. Buradaki `greeting` yalnızca festival sırasında geçerlidir." />
            </span>
          </div>
        </div>
      </Card>

      <Card
        title={t("Hazır başlangıçlar")}
        subtitle={t("Bir tanesine basın, ayarlar forma dolar; uygulamadan önce istediğinizi değiştirebilirsiniz.")}
      >
        <div className="preset-grid">
          {PRESETS.map((preset: Preset) => (
            <button key={preset.id} className="preset-card" onClick={() => applyPreset(preset)}>
              <strong>{t(preset.label)}</strong>
              <span>{t(preset.note)}</span>
            </button>
          ))}
        </div>
      </Card>

      <Card
        title={t("Tema ayarları")}
        subtitle={t("Aradığınızı yazın ya da soldan bir grup seçin.")}
        actions={
          <>
            <Button
              size="sm"
              variant="ghost"
              icon="folder"
              onClick={() => setGroup(null)}
            >
              {t("Tümü")}
            </Button>
            <Button size="sm" variant="ghost" icon="eye" onClick={() => go("preview")}>
              {t("Önizleme")}
            </Button>
          </>
        }
      >
        <div className="filter-row">
          <div className="search grow">
            <Icon name="search" size={16} />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("anahtar, ad veya açıklama ara")}
            />
          </div>
        </div>
        <div className="chips mt-3">
          <button
            className={`chip ${group === null ? "on" : ""}`}
            onClick={() => {
              setGroup(null);
              setSearch("");
            }}
          >
            {t("Tümü ·")} {keys.length}
          </button>
          {groups.map((g) => (
            <button
              key={g}
              className={`chip ${group === g ? "on" : ""}`}
              onClick={() => {
                setGroup(g);
                setSearch("");
              }}
            >
              {g} · {keys.filter((k) => k.group === g).length}
            </button>
          ))}
        </div>
      </Card>

      {shownGroups.map((groupName) => (
        <Card
          key={groupName}
          title={groupName}
          actions={
            <Button size="sm" variant="ghost" icon="refresh" onClick={() => resetGroup(groupName)}>
              {t("Grubu sıfırla")}
            </Button>
          }
        >
          {groupName === "Yalnızca config.xml" && (
            <p className="small muted mb-3">
              {t("Bu anahtarlar tema katmanıyla gönderilemez: APK'nın içine derlenirler. Değerleri")}{" "}
              <button className="crumb" onClick={() => go("config")}>
                
                {t("config.xml sekmesinden")}
              </button>{" "}
              {t("değiştirip Derle & kur adımını çalıştırın.")}
            </p>
          )}
          <div className="form-grid">{shown.filter((k) => k.group === groupName).map(field)}</div>
        </Card>
      ))}

      {shown.length === 0 && (
        <Card>
          <Empty icon="search" title={t("Eşleşen ayar yok")} text={t("Aramayı temizleyin.")} />
        </Card>
      )}

      {status && !status.installed && (
        <Card tone="warn" title={t("Temiz başlatıcı kurulu değil")}>
          <Empty
            icon="home"
            title={t("Önce başlatıcıyı kurun")}
            text={t("Tema katmanı com.gathin.tvhome paketine yazılır; paket yoksa gönderilemez.")}
            action={
              <Button size="sm" variant="primary" onClick={() => go("build")}>
                
                {t("Derle ve kur")}
              </Button>
            }
          />
        </Card>
      )}

      <div className="grid grid-2">
        <Card title={t("Duvar kağıdı")} subtitle={t("İsteğe bağlı; tema ile birlikte gönderilir")}>
          <Field
            label={t("Ana duvar kağıdı")}
            hint={t("JPEG veya PNG. TV tarafında wallpaper.jpg olarak yazılır.")}
          >
            <div className="row gap-2">
              <Input value={wallpaper ?? ""} readOnly placeholder={t("seçilmedi")} />
              <Button
                size="sm"
                variant="soft"
                icon="folder"
                onClick={async () => {
                  const file = await openDialog({
                    multiple: false,
                    directory: false,
                    title: t("Duvar kağıdı seç"),
                    filters: [{ name: t("Görsel"), extensions: ["jpg", "jpeg", "png", "webp"] }],
                  });
                  if (typeof file === "string") setWallpaper(file);
                }}
              >
                {t("Seç")}
              </Button>
              {wallpaper && (
                <Button size="sm" variant="bare" onClick={() => setWallpaper(null)}>
                  <Icon name="x" size={15} />
                </Button>
              )}
            </div>
          </Field>

          <Field
            label={t("Koleksiyon")}
            hint={t("wallpaper_rotation_minutes ile birlikte kullanın; dosyalar wallpapers/ altına gider.")}
          >
            <div className="row gap-2">
              <Button
                size="sm"
                variant="soft"
                icon="plus"
                onClick={async () => {
                  const files = await openDialog({
                    multiple: true,
                    directory: false,
                    title: t("Duvar kağıtları seç"),
                    filters: [{ name: t("Görsel"), extensions: ["jpg", "jpeg", "png", "webp"] }],
                  });
                  if (Array.isArray(files)) setWallpapers(files);
                  else if (typeof files === "string") setWallpapers([files]);
                }}
              >
                
                {t("Ekle")}
              </Button>
              <span className="small muted">{wallpapers.length} {t("dosya")}</span>
              {wallpapers.length > 0 && (
                <Button size="sm" variant="bare" onClick={() => setWallpapers([])}>
                  
                  {t("Temizle")}
                </Button>
              )}
            </div>
          </Field>
          {wallpapers.length > 0 && (
            <div className="col gap-1 mt-2">
              {wallpapers.map((w) => (
                <span key={w} className="tiny muted ellipsis mono">
                  {w.split(/[\\/]/).pop()}
                </span>
              ))}
            </div>
          )}
        </Card>

        <Card title={t("Nasıl çalışır?")} subtitle={t("İki ayrı yol var")}>
          <div className="steps">
            <div className="step">
              <p>
                <Prose text="**config.json (bu sekme).** Launcher'ın ThemeProvider'ına yazılır, `theme_revision` yenilenir ve Home yeniden başlar. Anında görürsünüz." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**config.xml + strings.xml.** APK'nın içine derlenir. Değişiklik için **Derle** adımı gerekir." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Katmanı kaldır.** Televizyonu fabrika ayarlarındaki görünüme döndürür; APK'ya dokunmaz." />
              </p>
            </div>
          </div>
        </Card>
      </div>

      {report && (
        <Card
          title={
            report.focused.startsWith("com.gathin.tvhome")
              ? t("Tema uygulandı")
              : t("Tema gönderildi, Home doğrulanamadı")
          }
          tone={report.focused.startsWith("com.gathin.tvhome") ? "ok" : "warn"}
          actions={
            <Button size="sm" variant="ghost" onClick={() => setReport(null)}>
              
              {t("Gizle")}
            </Button>
          }
        >
          <Steps steps={report.steps} />
        </Card>
      )}

      <PackagePicker
        open={!!picker}
        title={picker ? (picker.label ?? picker.key) : ""}
        subtitle={picker?.help}
        value={picker && Array.isArray(values[picker.key]) ? (values[picker.key] as string[]) : []}
        ordered={picker?.key === "dock_apps" || picker?.key === "top_bar_apps"}
        onChange={(next) => picker && set(picker.key, next)}
        onClose={() => setPicker(null)}
      />

      <Modal
        open={preview}
        wide
        title={t("Gönderilecek config.json")}
        subtitle={t("{n} anahtar. Varsayılandan farklı olanlar gönderilir.", { n: changed })}
        onClose={() => setPreview(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setPreview(false)}>
              
              {t("Kapat")}
            </Button>
            <Button variant="primary" icon="sparkle" loading={busy === "push"} onClick={push}>
              
              {t("Uygula")}
            </Button>
          </>
        }
      >
        <Code className="tall">{JSON.stringify(payload, null, 2)}</Code>
      </Modal>
    </div>
  );
}
