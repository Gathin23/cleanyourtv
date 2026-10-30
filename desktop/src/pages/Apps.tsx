import { useEffect, useMemo, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../api";
import type { Package, PackageList } from "../api";
import { useApp } from "../app-context";
import {
  Badge,
  Button,
  Card,
  Code,
  Empty,
  Icon,
  Input,
  Modal,
  Spinner,
  useToast,
} from "../ui";
import { kindLabel, kindText, PackageName, VerdictBadge } from "../parts";
import { Prose } from "../parts";
import { t } from "../i18n";

export default function AppsPage() {
  const { info } = useApp();
  const toast = useToast();
  const [list, setList] = useState<PackageList | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [kind, setKind] = useState("all");
  const [onlyDisabled, setOnlyDisabled] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ name: string; text: string; busy: boolean } | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setList(await api.packagesList());
    } catch (e) {
      toast.error(e, t("Paketler okunamadı"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const kinds = useMemo(() => {
    const set = new Set<string>();
    for (const p of list?.packages ?? []) set.add(p.kind);
    return ["all", ...[...set].sort()];
  }, [list]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (list?.packages ?? []).filter((p) => {
      if (onlyDisabled && !p.disabled) return false;
      if (kind !== "all" && p.kind !== kind) return false;
      if (!q) return true;
      return p.name.toLowerCase().includes(q) || p.reason.toLowerCase().includes(q);
    });
  }, [list, kind, onlyDisabled, search]);

  const act = async (name: string, fn: () => Promise<unknown>, okTitle: string, detailText?: string) => {
    setBusy(name);
    try {
      await fn();
      toast.push("ok", okTitle, detailText);
      await load();
    } catch (e) {
      toast.error(e, t("{what} olmadı", { what: t(okTitle) }));
    } finally {
      setBusy(null);
    }
  };

  const pull = async (p: Package) => {
    const folder = await openDialog({
      directory: true,
      multiple: false,
      title: t("APK'nın kaydedileceği klasörü seçin"),
    });
    if (typeof folder !== "string") return;
    await act(
      p.name,
      async () => {
        const out = await api.pullApk(p.name, folder);
        toast.push("info", "APK indirildi", out.local);
      },
      "APK indirildi",
      p.name,
    );
  };

  const openDetail = async (p: Package) => {
    setDetail({ name: p.name, text: "", busy: true });
    try {
      setDetail({ name: p.name, text: await api.packageDetails(p.name), busy: false });
    } catch (e) {
      setDetail({ name: p.name, text: api.message(e), busy: false });
    }
  };

  if (loading) {
    return (
      <Card>
        <Spinner label={t("Paketler okunuyor…")} />
      </Card>
    );
  }

  return (
    <div className="stack gap-5">
      <div className="grid grid-4">
        <div className="stat">
          <span>{t("Toplam paket")}</span>
          <strong>{list?.total ?? 0}</strong>
          <em>{list?.packages.filter((p) => p.system).length ?? 0} {t("sistem")}</em>
        </div>
        <div className="stat">
          <span>{t("Kapalı")}</span>
          <strong>{list?.disabled ?? 0}</strong>
          <em>{t("pm disable-user ile")}</em>
        </div>
        <div className="stat">
          <span>{t("Telemetri + reklam")}</span>
          <strong>
            {list?.packages.filter((p) => p.kind === "telemetry" || p.kind === "ads").length ?? 0}
          </strong>
          <em>{t("kapatılabilir")}</em>
        </div>
        <div className="stat">
          <span>{t("Korunan")}</span>
          <strong>{list?.packages.filter((p) => p.verdict === "refused").length ?? 0}</strong>
          <em>{t("asla kapatılmaz")}</em>
        </div>
      </div>

      <Card
        title={t("Uygulama ve paket listesi")}
        subtitle={t("Açın, durdurun, APK'yı indirin. Kapatma her zaman tek tek ve kurallardan geçerek yapılır.")}
        actions={
          <Button size="sm" variant="ghost" icon="refresh" onClick={load}>
            
            {t("Yenile")}
          </Button>
        }
      >
        <div className="filter-row">
          <div className="search">
            <Icon name="search" size={16} />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("paket adı ara")}
            />
          </div>
          <select className="input select" style={{ width: 190 }} value={kind} onChange={(e) => setKind(e.target.value)}>
            {kinds.map((k) => (
              <option key={k} value={k}>
                {k === "all" ? t("Tüm türler") : t(kindLabel[k as keyof typeof kindLabel] ?? k)}
              </option>
            ))}
          </select>
          <button className={`chip ${onlyDisabled ? "on" : ""}`} onClick={() => setOnlyDisabled((v) => !v)}>
            {t("Yalnızca kapalı")}
          </button>
          <span className="small muted">{visible.length} {t("sonuç")}</span>
        </div>

        <div className="table-wrap mt-3">
          <div className="table-scroll" style={{ maxHeight: 560 }}>
            <table className="table">
              <thead>
                <tr>
                  <th>{t("Paket")}</th>
                  <th style={{ width: 110 }}>{t("Tür")}</th>
                  <th style={{ width: 110 }}>{t("Kural")}</th>
                  <th style={{ width: 100 }}>{t("Durum")}</th>
                  <th style={{ width: 190 }} />
                </tr>
              </thead>
              <tbody>
                {visible.map((p) => (
                  <tr key={p.name} className={p.disabled ? "off" : ""}>
                    <td>
                      <PackageName name={p.name} reason={p.reason} />
                    </td>
                    <td className="small muted">{kindText(p.kind)}</td>
                    <td>
                      <VerdictBadge verdict={p.verdict} />
                    </td>
                    <td>
                      {p.disabled ? (
                        <Badge tone="muted">{t("kapalı")}</Badge>
                      ) : (
                        <Badge tone="ok">{t("açık")}</Badge>
                      )}
                    </td>
                    <td>
                      <div className="actions">
                        <Button
                          variant="bare"
                          size="sm"
                          title={t("Durdur")}
                          disabled={p.disabled}
                          loading={busy === p.name}
                          onClick={() => act(p.name, () => api.packageForceStop(p.name), t("Durduruldu"), p.name)}
                        >
                          <Icon name="stop" size={15} />
                        </Button>
                        <Button
                          variant="bare"
                          size="sm"
                          title={t("Başlat")}
                          disabled={p.disabled}
                          onClick={() => act(p.name, () => api.packageLaunch(p.name), t("Başlatılıyor"), p.name)}
                        >
                          <Icon name="play" size={15} />
                        </Button>
                        <Button
                          variant="bare"
                          size="sm"
                          title={t("APK indir")}
                          onClick={() => pull(p)}
                        >
                          <Icon name="download" size={15} />
                        </Button>
                        <Button
                          variant="bare"
                          size="sm"
                          title={p.disabled ? t("Etkinleştir") : t("Devre dışı bırak")}
                          disabled={p.verdict === "refused"}
                          onClick={() =>
                            act(
                              p.name,
                              () => api.packageSetEnabled(p.name, p.disabled),
                              p.disabled ? t("Açıldı") : t("Kapatıldı"),
                              p.name,
                            )
                          }
                        >
                          <Icon name={p.disabled ? "check" : "power"} size={15} />
                        </Button>
                        <Button variant="bare" size="sm" title={t("Ayrıntı")} onClick={() => openDetail(p)}>
                          <Icon name="info" size={15} />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        {visible.length === 0 && (
          <Empty icon="search" title={t("Paket bulunamadı")} text={t("Aramayı ya da tür filtresini değiştirin.")} />
        )}
      </Card>

      <Card title={t("APK kur")} subtitle={t("Bilgisayardaki bir APK'yı TV'ye kurar")}>
        {info && info.sdk < 26 && (
          <p className="small" style={{ color: "var(--warn)" }}>
            {t("Bu TV SDK {sdk}; temiz başlatıcı en az SDK 26 ister.", { sdk: info.sdk })}
          </p>
        )}
        <div className="row gap-2 wrap">
          <Button
            variant="soft"
            size="sm"
            icon="upload"
            onClick={async () => {
              const file = await openDialog({
                multiple: false,
                directory: false,
                title: t("APK seç"),
                filters: [{ name: t("Android paketi"), extensions: ["apk"] }],
              });
              if (typeof file !== "string") return;
              setBusy("apk");
              try {
                const out = await api.apkInstall(file);
                if (out.ok) {
                  toast.push(
                    "ok",
                    "Kuruldu",
                    out.fallback ? t("adb install beklemedi; pm install kullanıldı.") : t("adb install başarılı."),
                  );
                } else {
                  toast.push("error", t("Kurulamadı"), out.output.slice(-400));
                }
                await load();
              } catch (e) {
                toast.error(e, t("Kurulamadı"));
              } finally {
                setBusy(null);
              }
            }}
            loading={busy === "apk"}
          >
            {t("APK seç ve kur")}
          </Button>
          <span className="small muted">
            <Prose text="Android 14 onay penceresi takılırsa uygulama APK'yı `/data/local/tmp` üzerinden kurar." />
          </span>
        </div>
      </Card>

      <Modal
        open={!!detail}
        wide
        title={detail?.name ?? ""}
        subtitle="dumpsys package"
        onClose={() => setDetail(null)}
        footer={
          <Button variant="ghost" onClick={() => setDetail(null)}>
            
            {t("Kapat")}
          </Button>
        }
      >
        {detail?.busy ? <Spinner label={t("Okunuyor…")} /> : <Code className="tall">{detail?.text}</Code>}
      </Modal>
    </div>
  );
}
