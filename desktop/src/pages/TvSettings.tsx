import { useCallback, useEffect, useMemo, useState } from "react";
import * as api from "../api";
import type { TvSettingSpec } from "../api";
import { useApp } from "../app-context";
import {
  Badge,
  Button,
  Card,
  Empty,
  Icon,
  Input,
  Select,
  Spinner,
  Switch,
  useToast,
} from "../ui";
import { Prose } from "../parts";
import { t } from "../i18n";

/** `settings get` answers `null` for a key nothing ever wrote. */
const isUnset = (value: string | undefined) =>
  value === undefined || value === "" || value === "null";

export default function TvSettings() {
  const { conn, go } = useApp();
  const toast = useToast();
  const [specs, setSpecs] = useState<TvSettingSpec[]>([]);
  const [current, setCurrent] = useState<Record<string, string>>({});
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [schema, values] = await Promise.all([api.tvSettingsSpecs(), api.tvSettingsRead()]);
      setSpecs(schema);
      const map: Record<string, string> = {};
      for (const item of values) map[item.key] = item.value;
      setCurrent(map);
      setDraft(map);
    } catch (e) {
      toast.error(e, t("TV ayarları okunamadı"));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (conn?.connected) load();
    else setLoading(false);
  }, [conn?.connected, load]);

  const groups = useMemo(() => {
    const order: string[] = [];
    for (const spec of specs) if (!order.includes(spec.group)) order.push(spec.group);
    return order;
  }, [specs]);

  const save = async (spec: TvSettingSpec) => {
    const value = draft[spec.key] ?? "";
    setBusy(spec.key);
    try {
      const result = await api.tvSettingWrite(spec.key, value);
      setCurrent((prev) => ({ ...prev, [spec.key]: result.after }));
      setDraft((prev) => ({ ...prev, [spec.key]: result.after }));
      if (result.ok) {
        toast.push("ok", t("Yazıldı"), `${spec.key}: ${result.before || "—"} → ${result.after}`);
      } else {
        toast.push(
          "info",
          t("TV değeri almadı"),
          t("{key} hâlâ {value}. Bu anahtar bu firmware'de kilitli olabilir.", { key: spec.key, value: result.after || t("boş") }),
        );
      }
    } catch (e) {
      toast.error(e, t("Yazılamadı"));
    } finally {
      setBusy(null);
    }
  };

  if (!conn?.connected) {
    return (
      <Card title={t("TV bağlı değil")}>
        <Empty
          icon="plug"
          title={t("Bu ayarlar TV'nin kendisinde")}
          text={t("Private DNS, uyku süresi ve animasyon hızları TV'ye yazılır. Önce TV'ye bağlanın.")}
          action={
            <Button size="sm" variant="primary" icon="plug" onClick={() => go("connect")}>
              {t("Bağlan")}
            </Button>
          }
        />
      </Card>
    );
  }

  if (loading && specs.length === 0) {
    return (
      <Card>
        <Spinner label={t("TV ayarları okunuyor…")} />
      </Card>
    );
  }

  return (
    <div className="stack gap-4">
      <Card
        tone="accent"
        title={t("TV ayarları")}
        subtitle={t("TV'nin kendi Android ayarlarından birkaçı. Her anahtar bu uygulamada tanımlı; listede olmayan bir ayar yazılmaz.")}
        actions={
          <Button size="sm" variant="ghost" icon="refresh" onClick={load} loading={loading}>
            
            {t("Yenile")}
          </Button>
        }
      >
        <div className="row gap-2 wrap">
          <Badge tone="neutral">{specs.length}  {t("ayar")}</Badge>
          <span className="small muted">
            {t("Yazılan her değer geri okunur; TV almadıysa burada görürsünüz. Tümü TV'nin kendi Ayarlar ekranından da geri alınabilir.")}
          </span>
        </div>
      </Card>

      {groups.map((group) => (
        <Card key={group} title={group}>
          <div className="col gap-4">
            {specs
              .filter((spec) => spec.group === group)
              .map((spec) => {
                const value = draft[spec.key] ?? "";
                const before = current[spec.key] ?? "";
                const changed = value !== before;
                return (
                  <div key={spec.key} className="setting-row">
                    <div className="col" style={{ minWidth: 0, flex: "1 1 240px" }}>
                      <span className="setting-label">{spec.label}</span>
                      <span className="tiny muted">{spec.help}</span>
                      <span className="tiny muted mono">
                        {spec.namespace} · {spec.key} · {isUnset(before) ? t("boş") : before}
                      </span>
                    </div>
                    <div className="setting-control">
                      {spec.kind === "bool" ? (
                        <Switch
                          checked={value === "1"}
                          onChange={(next) =>
                            setDraft((prev) => ({ ...prev, [spec.key]: next ? "1" : "0" }))
                          }
                          label={value === "1" ? t("açık") : t("kapalı")}
                        />
                      ) : spec.kind === "select" ? (
                        <Select
                          value={value}
                          onChange={(e) =>
                            setDraft((prev) => ({ ...prev, [spec.key]: e.target.value }))
                          }
                          options={spec.options.map((o) => ({ value: o, label: o }))}
                        />
                      ) : spec.kind === "int" ? (
                        <div className="row gap-2">
                          <Input
                            type="number"
                            min={spec.min}
                            max={spec.max}
                            value={value}
                            onChange={(e) =>
                              setDraft((prev) => ({ ...prev, [spec.key]: e.target.value }))
                            }
                            style={{ width: 110 }}
                          />
                          <span className="tiny muted">{spec.min}–{spec.max}</span>
                        </div>
                      ) : (
                        <Input
                          value={value}
                          placeholder={spec.key === "private_dns_specifier" ? "dns.example.com" : ""}
                          onChange={(e) =>
                            setDraft((prev) => ({ ...prev, [spec.key]: e.target.value }))
                          }
                        />
                      )}
                    </div>
                    <Button
                      size="sm"
                      variant={changed ? "primary" : "ghost"}
                      icon="check"
                      disabled={!changed}
                      loading={busy === spec.key}
                      onClick={() => save(spec)}
                    >
                      
                      {t("Kaydet")}
                    </Button>
                  </div>
                );
              })}
          </div>
        </Card>
      ))}

      <Card title={t("Nerede kullanılır?")}>
        <div className="steps">
          <div className="step">
            <p>
              <Prose text="**Private DNS**, reklam ve takip alan adlarını cihaz genelinde süzen bir çözümleyiciye yönlendirmenin en temiz yolu. Kipi `hostname` yapıp sunucuyu yazın." />
            </p>
          </div>
          <div className="step">
            <p>
              <Prose text="**Animasyon ölçekleri** 0.5 veya 0, zayıf GPU'lu TV'lerde menüleri hızlandırır; görünümü bozmaz." />
            </p>
          </div>
          <div className="step">
            <p>
              <Prose text="**Ekran ve uyku** satırları TV'nin kendi uyku davranışını değiştirir. Prizde sürekli çalışan bir set için “prize bağlıyken uyuma” kapatılabilir." />
            </p>
          </div>
        </div>
        <div className="row gap-2 mt-3">
          <Button size="sm" variant="ghost" icon="shield" onClick={() => go("privacy")}>
            
            {t("ADB ve gizlilik")}
          </Button>
          <span className="small muted">
            <Icon name="info" size={14} /> {t("Yalnız bu uygulamanın tanımladığı anahtarlar yazılabilir.")}
          </span>
        </div>
      </Card>
    </div>
  );
}
