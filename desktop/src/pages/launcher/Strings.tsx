import { useCallback, useEffect, useMemo, useState } from "react";
import * as api from "../../api";
import type { RepoLayout, ResEntry, StringsDoc } from "../../api";
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
  TextArea,
  useToast,
} from "../../ui";
import { Prose } from "../../parts";
import { t } from "../../i18n";

const greetings = [
  { key: "good_morning", label: "Sabah", hint: "Günaydın" },
  { key: "good_afternoon", label: "Öğleden sonra", hint: "İyi günler" },
  { key: "good_evening", label: "Akşam", hint: "İyi akşamlar" },
  { key: "good_night", label: "Gece", hint: "İyi geceler" },
];

export default function StringsPage({ go }: { go: (tab: string) => void }) {
  const { repo, info } = useApp();
  const toast = useToast();
  const [layout, setLayout] = useState<RepoLayout | null>(null);
  const [locale, setLocale] = useState("values-tr");
  const [doc, setDoc] = useState<StringsDoc | null>(null);
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [allTo, setAllTo] = useState("");
  const [confirmAll, setConfirmAll] = useState(false);

  const loadLocale = useCallback(
    async (dir: string) => {
      if (!repo?.path) return;
      setLoading(true);
      try {
        const next = await api.stringsRead(repo.path, dir);
        setDoc(next);
        setEdits(Object.fromEntries(next.entries.map((e) => [e.name, e.value])));
      } catch (e) {
        setDoc(null);
        toast.error(e, t("strings.xml okunamadı"));
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
        toast.error(e);
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repo?.path]);

  const stringLocales = useMemo(
    () => (layout?.locales ?? []).filter((l) => l.strings),
    [layout],
  );

  const byName = useMemo(() => {
    const map = new Map<string, ResEntry>();
    for (const e of doc?.entries ?? []) map.set(e.name, e);
    return map;
  }, [doc]);

  const others = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (doc?.entries ?? []).filter(
      (e) =>
        !greetings.some((g) => g.key === e.name) &&
        (!q || e.name.toLowerCase().includes(q) || e.value.toLowerCase().includes(q)),
    );
  }, [doc, search]);

  const save = async (name: string) => {
    if (!doc || !repo?.path) return;
    setBusy(name);
    try {
      const next = await api.stringsWrite(repo.path, locale, name, edits[name] ?? "");
      setDoc({ path: next.path, entries: next.entries.filter((e) => e.kind === "string") });
      setEdits((prev) => ({ ...prev, ...Object.fromEntries(next.entries.map((e) => [e.name, e.value])) }));
      toast.push("ok", t("Kaydedildi"), t("{name} · yedek: {backup}", { name, backup: next.backup }));
    } catch (e) {
      toast.error(e, t("Yazılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const greetingValue = (key: string) => edits[key] ?? byName.get(key)?.value ?? "";

  const saveAllGreetings = async () => {
    const text = allTo.trim();
    const path = repo?.path;
    if (!text || !path) return;
    setBusy("all");
    try {
      for (const g of greetings) {
        await api.stringsWrite(path, locale, g.key, text);
      }
      await loadLocale(locale);
      setConfirmAll(false);
      setAllTo("");
      toast.push("ok", t("Dört selamlama da değişti"), text);
    } catch (e) {
      toast.error(e, t("Yazılamadı"));
    } finally {
      setBusy(null);
    }
  };

  if (!repo?.path) {
    return (
      <Card title={t("Repo gerekli")}>
        <Empty
          icon="folder"
          title={t("strings.xml kaynaktan okunur")}
          text={t("Ayarlar'dan CleanYourTV klasörünü gösterin.")}
        />
      </Card>
    );
  }

  return (
    <div className="stack gap-4">
      <Card
        tone="accent"
        title={t("Selamlama metinleri")}
        subtitle={t("Home ekranındaki “Günaydın” gibi metinler strings.xml içinde. Değişiklik APK'ya derlenir.")}
        actions={
          <Badge tone="warn">{t("derleme gerekir")}</Badge>
        }
      >
        <div className="row gap-2 wrap">
          <div style={{ width: 250 }}>
            <Select
              value={locale}
              onChange={(e) => {
                setLocale(e.target.value);
                loadLocale(e.target.value);
              }}
              options={stringLocales.map((l) => ({
                value: l.dir,
                label: l.dir === "values" ? t("Varsayılan (İngilizce)") : l.dir,
              }))}
            />
          </div>
          <Button size="sm" variant="ghost" icon="refresh" onClick={() => loadLocale(locale)}>
            
            {t("Yenile")}
          </Button>
          <div className="grow" />
          <span className="tiny muted mono ellipsis">{doc?.path}</span>
        </div>

        <div className="greeting-grid mt-4">
          {greetings.map((g) => {
            const entry = byName.get(g.key);
            const value = greetingValue(g.key);
            const changed = entry ? value !== entry.value : false;
            return (
              <div className="greeting-card" key={g.key}>
                <div className="row spread">
                  <span className="key">{g.key}</span>
                  <span className="tiny muted">{t(g.label)}</span>
                </div>
                <div
                  style={{
                    fontSize: 21,
                    fontWeight: 500,
                    letterSpacing: "-0.02em",
                    margin: "10px 0",
                    minHeight: 30,
                  }}
                >
                  {value || t(g.hint)}
                </div>
                <Input
                  value={value}
                  onChange={(e) => setEdits((prev) => ({ ...prev, [g.key]: e.target.value }))}
                  placeholder={t(g.hint)}
                />
                <div className="row gap-2 mt-2">
                  <Button
                    size="sm"
                    variant={changed ? "primary" : "ghost"}
                    icon="check"
                    disabled={!changed}
                    loading={busy === g.key}
                    onClick={() => save(g.key)}
                  >
                    
                    {t("Kaydet")}
                  </Button>
                  {entry && <span className="tiny muted mono">{t("şu an:")} {entry.value}</span>}
                </div>
              </div>
            );
          })}
        </div>

        <div className="row gap-2 wrap mt-4">
          <div className="grow" style={{ minWidth: 220 }}>
            <Input
              value={allTo}
              onChange={(e) => setAllTo(e.target.value)}
              placeholder={t("Dördünü de aynı metne çevir, ör. Merhaba KRALIM")}
            />
          </div>
          <Button
            size="sm"
            variant="soft"
            icon="sparkle"
            disabled={!allTo.trim()}
            onClick={() => setConfirmAll(true)}
          >
            
            {t("Hepsine uygula")}
          </Button>
          <Button size="sm" variant="primary" icon="box" onClick={() => go("build")}>
            
            {t("Derle ve kur")}
          </Button>
        </div>

        <p className="small muted mt-3">
          <Prose text="Bu metinler yalnızca o dilde görünür. TV dili Türkçe değilse `values/strings.xml` dosyasını da düzenleyin ya da launcher ayarlarından dili `tr` yapın (tema katmanındaki `locale` anahtarı)." />
        </p>
      </Card>

      <Card
        title={t("Diğer metinler ({n})", { n: others.length })}
        subtitle={t("Arama sonucu; her satır kendi kaydet düğmesiyle yazılır")}
        actions={
          <div className="search" style={{ width: 260 }}>
            <Icon name="search" size={16} />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("metin ara")}
            />
          </div>
        }
      >
        {loading ? (
          <Spinner label={t("strings.xml okunuyor…")} />
        ) : !doc ? (
          <Empty icon="file" title={t("strings.xml bulunamadı")} />
        ) : (
          <div className="col gap-3" style={{ maxHeight: 520, overflowY: "auto" }}>
            {others.map((e) => {
              const value = edits[e.name] ?? e.value;
              const changed = value !== e.value;
              return (
                <div key={e.name} className="row gap-3" style={{ alignItems: "flex-start" }}>
                  <span className="mono tiny muted" style={{ width: 210, flex: "0 0 auto", paddingTop: 9 }}>
                    {e.name}
                  </span>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <TextArea
                      style={{ minHeight: 44, height: 44 }}
                      value={value}
                      onChange={(ev) =>
                        setEdits((prev) => ({ ...prev, [e.name]: ev.target.value }))
                      }
                    />
                    {value.includes("%") && (
                      <span className="tiny muted">
                        {t("Yer tutucular ({list}) korunmalı.", {
                          list: value.match(/%\d*\$?[sd]/g)?.join(", ") ?? "",
                        })}
                      </span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    variant={changed ? "primary" : "ghost"}
                    icon="check"
                    disabled={!changed}
                    loading={busy === e.name}
                    onClick={() => save(e.name)}
                  >
                    
                    {t("Kaydet")}
                  </Button>
                </div>
              );
            })}
            {others.length === 0 && (
              <Empty icon="search" title={t("Eşleşen metin yok")} text={t("Aramayı değiştirin.")} />
            )}
          </div>
        )}
      </Card>

      <Card title={t("Değişiklik nasıl TV'ye gider?")} subtitle={t("Derleme ve kurulum")}>
        <div className="steps">
          <div className="step">
            <p>
              <Prose text="**Kaydet.** Yalnızca kaynak dosya değişir; her yazma öncesi `repo-edits` altına yedek alınır." />
            </p>
          </div>
          <div className="step">
            <p>
              <Prose text="**Derle.** `./gradlew assembleRelease` JDK 17 ve Android SDK 34 ile çalışır." />
            </p>
          </div>
          <div className="step">
            <p>
              <Prose text="**Kur ve Home yap.** Yeni APK eskisinin üzerine kurulur; ayarlar korunur. Home zaten sizdeyse yeniden devretmek gerekmez." />
            </p>
          </div>
        </div>
        {info && (
          <p className="tiny muted mt-3">
            {t("Hedef: {device} · Android {android} (SDK {sdk})", {
              device: `${info.brand} ${info.model}`.trim(),
              android: info.android,
              sdk: info.sdk,
            })}
          </p>
        )}
      </Card>

      <Modal
        open={confirmAll}
        title={t("Dört selamlama da değişsin mi?")}
        subtitle={allTo}
        onClose={() => setConfirmAll(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmAll(false)}>
              {t("Vazgeç")}
            </Button>
            <Button variant="primary" loading={busy === "all"} onClick={saveAllGreetings}>
              
              {t("Uygula")}
            </Button>
          </>
        }
      >
        <p className="small">
          <Prose
            text={t(
              "`good_morning`, `good_afternoon`, `good_evening` ve `good_night` metinlerinin hepsi **{name}** olacak. TV'de görünmesi için derleme gerekir.",
              { name: allTo },
            )}
          />
        </p>
      </Modal>
    </div>
  );
}
