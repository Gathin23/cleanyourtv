import { useCallback, useEffect, useMemo, useState } from "react";
import * as api from "../../api";
import type { ConfigDoc, RepoLayout, ResEntry } from "../../api";
import { useApp } from "../../app-context";
import {
  Badge,
  Button,
  Card,
  Empty,
  Icon,
  Input,
  Modal,
  Select,
  Spinner,
  Switch,
  TextArea,
  useToast,
} from "../../ui";
import { t } from "../../i18n";

type Edit = { value: string; items: string };

const kindOrder = ["bool", "string", "integer", "dimen", "color", "string-array", "integer-array"];

function editsFrom(entries: ResEntry[]): Record<string, Edit> {
  const out: Record<string, Edit> = {};
  for (const e of entries) out[e.name] = { value: e.value, items: e.items.join("\n") };
  return out;
}

export default function ConfigXmlPage({ go }: { go: (tab: string) => void }) {
  const { repo } = useApp();
  const toast = useToast();
  const [layout, setLayout] = useState<RepoLayout | null>(null);
  const [locale, setLocale] = useState("values");
  const [doc, setDoc] = useState<ConfigDoc | null>(null);
  const [edits, setEdits] = useState<Record<string, Edit>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [raw, setRaw] = useState<{ xml: string; path: string } | null>(null);
  const [lastBackup, setLastBackup] = useState("");

  const loadLocale = useCallback(
    async (dir: string) => {
      if (!repo?.path) return;
      setLoading(true);
      try {
        const next = await api.configRead(repo.path, dir, "config.xml");
        setDoc(next);
        setEdits(editsFrom(next.entries));
      } catch (e) {
        setDoc(null);
        toast.error(e, t("config.xml okunamadı"));
      } finally {
        setLoading(false);
      }
    },
    [repo?.path, toast],
  );

  useEffect(() => {
    (async () => {
      if (!repo?.path) {
        setLoading(false);
        return;
      }
      try {
        const found = await api.configLayout(repo.path);
        setLayout(found);
        const preferred = found.locales.find((l) => l.dir === "values-tr") ?? found.locales[0];
        if (preferred) {
          setLocale(preferred.dir);
          await loadLocale(preferred.dir);
        }
      } catch (e) {
        toast.error(e, t("Repo düzeni okunamadı"));
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repo?.path]);

  const configLocales = useMemo(
    () => (layout?.locales ?? []).filter((l) => l.config),
    [layout],
  );

  const grouped = useMemo(() => {
    const entries = doc?.entries ?? [];
    const q = search.trim().toLowerCase();
    const filtered = q ? entries.filter((e) => e.name.toLowerCase().includes(q)) : entries;
    return kindOrder
      .map((kind) => ({ kind, entries: filtered.filter((e) => e.kind === kind) }))
      .filter((g) => g.entries.length > 0);
  }, [doc, search]);

  const dirty = (e: ResEntry) => {
    const edit = edits[e.name];
    if (!edit) return false;
    if (e.items.length > 0) {
      return edit.items !== e.items.join("\n");
    }
    return edit.value !== e.value;
  };

  const save = async (e: ResEntry) => {
    if (!doc || !repo?.path) return;
    const edit = edits[e.name];
    if (!edit) return;
    setBusy(e.name);
    try {
      const isArray = e.kind.endsWith("-array");
      const next = await api.configWrite(
        repo.path,
        doc.path,
        e.name,
        e.kind,
        isArray ? "" : edit.value,
        isArray ? edit.items.split("\n").map((s) => s.trimEnd()).filter((s) => s !== "") : [],
      );
      setDoc(next);
      setEdits(editsFrom(next.entries));
      setLastBackup(next.backup);
      toast.push("ok", t("config.xml yazıldı"), `${e.name} · yedek: ${next.backup}`);
    } catch (err) {
      toast.error(err, t("Yazılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const saveRaw = async () => {
    if (!raw || !repo?.path) return;
    setBusy("raw");
    try {
      const next = await api.configWriteRaw(repo.path, raw.path, raw.xml);
      setDoc(next);
      setEdits(editsFrom(next.entries));
      setLastBackup(next.backup);
      setRaw(null);
      toast.push("ok", t("Dosya yazıldı"), next.backup);
    } catch (e) {
      toast.error(e, t("XML geçersiz olabilir; dosya yazılmadı"));
    } finally {
      setBusy(null);
    }
  };

  if (!repo?.path) {
    return (
      <Card title={t("Repo gerekli")} subtitle={t("config.xml kaynaktan okunur")}>
        <Empty
          icon="folder"
          title={t("CleanYourTV checkout'u bulunamadı")}
          text={t("Bu sekme launcher/app/src/main/res/values*/config.xml dosyalarını düzenler. Ayarlar'dan repo klasörünü gösterin, ya da önce projeyi klonlayın.")}
          action={
            <Button size="sm" variant="soft" onClick={() => go("build")}>
              
              {t("Derleme sekmesine git")}
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <div className="stack gap-4">
      <Card
        tone="warn"
        title={t("config.xml — APK'nın içine derlenir")}
        subtitle={t("Buradaki değişiklik yalnızca kaynakta olur; TV'de görünmesi için derleyip kurmanız gerekir.")}
        actions={
          <Button size="sm" variant="soft" icon="terminal" onClick={() => go("build")}>
            
            {t("Derle ve kur")}
          </Button>
        }
      >
        <div className="row gap-2 wrap">
          <Badge tone="warn">{t("derleme gerekir")}</Badge>
          <span className="small muted">
            
            {t("Yol:")} <span className="mono">{doc?.path ?? layout?.res}</span>
          </span>
          {lastBackup && (
            <span className="tiny muted">
              
              {t("son yedek:")} <span className="mono">{lastBackup}</span>
            </span>
          )}
        </div>
        <div className="row gap-2 wrap mt-3">
          <div style={{ width: 240 }}>
            <Select
              value={locale}
              onChange={(e) => {
                setLocale(e.target.value);
                loadLocale(e.target.value);
              }}
              options={configLocales.map((l) => ({
                value: l.dir,
                label: l.dir === "values" ? t("Varsayılan (İngilizce)") : l.dir,
              }))}
            />
          </div>
          <div className="search grow" style={{ minWidth: 200 }}>
            <Icon name="search" size={16} />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("anahtar ara")}
            />
          </div>
          <Button size="sm" variant="ghost" icon="refresh" onClick={() => loadLocale(locale)}>
            
            {t("Yenile")}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            icon="edit"
            disabled={!doc}
            onClick={() => doc && setRaw({ xml: doc.xml, path: doc.path })}
          >
            
            {t("Ham XML")}
          </Button>
        </div>
      </Card>

      {loading ? (
        <Card>
          <Spinner label={t("config.xml okunuyor…")} />
        </Card>
      ) : !doc ? (
        <Card>
          <Empty icon="file" title={t("config.xml bulunamadı")} text={t("Bu dil klasöründe config.xml yok.")} />
        </Card>
      ) : (
        <>
          {grouped.map((group) => (
            <Card
              key={group.kind}
              title={`${group.kind} · ${group.entries.length}`}
              subtitle={
                group.kind === "bool"
                  ? "true / false"
                  : group.kind.endsWith("-array")
                    ? t("Her satır bir <item>")
                    : undefined
              }
            >
              <div className="col gap-3">
                {group.entries.map((e) => {
                  const edit = edits[e.name] ?? { value: e.value, items: e.items.join("\n") };
                  const changed = dirty(e);
                  return (
                    <div key={e.name} className="row gap-3" style={{ alignItems: "flex-start" }}>
                      <div style={{ width: 250, flex: "0 0 auto" }} className="col">
                        <span className="mono small">{e.name}</span>
                        {e.translatable === false && (
                          <span className="tiny muted">translatable=false</span>
                        )}
                      </div>
                      <div className="grow" style={{ minWidth: 0 }}>
                        {e.kind === "bool" ? (
                          <div className="row gap-2">
                            <Switch
                              checked={edit.value === "true"}
                              onChange={(v) =>
                                setEdits((prev) => ({
                                  ...prev,
                                  [e.name]: { ...edit, value: v ? "true" : "false" },
                                }))
                              }
                            />
                            <span className="small muted">{edit.value}</span>
                          </div>
                        ) : e.kind.endsWith("-array") ? (
                          <TextArea
                            style={{ minHeight: 70 }}
                            value={edit.items}
                            onChange={(ev) =>
                              setEdits((prev) => ({
                                ...prev,
                                [e.name]: { ...edit, items: ev.target.value },
                              }))
                            }
                          />
                        ) : e.kind === "color" ? (
                          <div className="row gap-2">
                            <input
                              type="color"
                              value={/^#[0-9a-fA-F]{6}$/.test(edit.value) ? edit.value : "#000000"}
                              onChange={(ev) =>
                                setEdits((prev) => ({
                                  ...prev,
                                  [e.name]: { ...edit, value: ev.target.value },
                                }))
                              }
                              style={{ width: 42, height: 36, border: "none", background: "none" }}
                            />
                            <Input
                              value={edit.value}
                              onChange={(ev) =>
                                setEdits((prev) => ({
                                  ...prev,
                                  [e.name]: { ...edit, value: ev.target.value },
                                }))
                              }
                            />
                          </div>
                        ) : (
                          <Input
                            value={edit.value}
                            onChange={(ev) =>
                              setEdits((prev) => ({
                                ...prev,
                                [e.name]: { ...edit, value: ev.target.value },
                              }))
                            }
                          />
                        )}
                      </div>
                      <Button
                        size="sm"
                        variant={changed ? "primary" : "ghost"}
                        icon="check"
                        disabled={!changed}
                        loading={busy === e.name}
                        onClick={() => save(e)}
                      >
                        
                        {t("Kaydet")}
                      </Button>
                    </div>
                  );
                })}
              </div>
            </Card>
          ))}
          {grouped.length === 0 && (
            <Card>
              <Empty icon="search" title={t("Eşleşen anahtar yok")} text={t("Aramayı temizleyin.")} />
            </Card>
          )}
        </>
      )}

      <Modal
        open={!!raw}
        wide
        title={t("Ham config.xml")}
        subtitle={raw?.path}
        onClose={() => setRaw(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRaw(null)}>
              {t("Vazgeç")}
            </Button>
            <Button variant="primary" icon="check" loading={busy === "raw"} onClick={saveRaw}>
              {t("Doğrula ve kaydet")}
            </Button>
          </>
        }
      >
        <p className="small muted mb-2">
          {t("Kaydetmeden önce XML doğrulanır. Bozuk bir dosya yazılmaz; her kayıt öncesi yedek alınır.")}
        </p>
        <TextArea
          value={raw?.xml ?? ""}
          onChange={(e) => raw && setRaw({ ...raw, xml: e.target.value })}
          style={{ minHeight: 420 }}
          spellCheck={false}
        />
      </Modal>

      <Card title={t("Sonraki adım")} subtitle={t("Derleme olmadan TV'de görünmez")}>
        <div className="row gap-2 wrap">
          <Button variant="primary" size="sm" icon="box" onClick={() => go("build")}>
            
            {t("Derle ve kur")}
          </Button>
          <Button variant="ghost" size="sm" icon="sparkle" onClick={() => go("theme")}>
            {t("Derlemesiz tema katmanı")}
          </Button>
          <span className="small muted">
            {t("Derleme JDK 17 ve Android SDK 34 ister; APK 576 KB sınırının altında kalmalı.")}
          </span>
        </div>
      </Card>
    </div>
  );
}
