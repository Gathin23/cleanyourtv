import { useCallback, useEffect, useState } from "react";
import * as api from "../../api";
import type { HomeReport, LauncherStatus } from "../../api";
import { useApp } from "../../app-context";
import {
  Badge,
  Button,
  Card,
  Code,
  Empty,
  Icon,
  Row,
  Spinner,
  useToast,
} from "../../ui";
import { CopyLine, Steps } from "../../parts";
import { Prose } from "../../parts";
import { t } from "../../i18n";

export default function LauncherStatusPage({ go }: { go: (tab: string) => void }) {
  const { repo, info, conn, refreshInfo } = useApp();
  const toast = useToast();
  const [status, setStatus] = useState<LauncherStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [home, setHome] = useState<HomeReport | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await api.launcherStatus());
    } catch (e) {
      toast.error(e, t("Başlatıcı durumu okunamadı"));
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    if (conn?.connected) load();
    else setLoading(false);
  }, [conn?.connected, load]);

  const setAsHome = async () => {
    setBusy("home");
    try {
      const report = await api.launcherSetHome();
      setHome(report);
      await load();
      await refreshInfo();
      toast.push(
        report.ok ? "ok" : "error",
        report.ok ? "Home devredildi" : "Home devredilemedi",
        report.home,
      );
    } catch (e) {
      toast.error(e, "Home devredilemedi");
    } finally {
      setBusy(null);
    }
  };

  if (loading) {
    return (
      <Card>
        <Spinner label={t("Başlatıcı durumu okunuyor…")} />
      </Card>
    );
  }

  if (!conn?.connected) {
    return (
      <Card title={t("TV bağlı değil")}>
        <Empty
          icon="plug"
          title={t("Bu sekme TV'yi okur")}
          text={t("Home devri, tema katmanı ve kurulum için önce bağlanın. config.xml, Selamlama ve Derleme sekmeleri bağlantı olmadan da çalışır.")}
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
      <div className="grid grid-4">
        <div className="stat">
          <span>{t("Kurulum")}</span>
          <strong>{status?.installed ? t("kurulu") : t("yok")}</strong>
          <em>{status?.version ? t("sürüm {version}", { version: status.version }) : "com.gathin.tvhome"}</em>
        </div>
        <div className="stat">
          <span>Android</span>
          <strong>SDK {status?.sdk ?? "?"}</strong>
          <em>{status?.supported
              ? t("en az {sdk} gerekir", { sdk: status?.min_sdk ?? "?" })
              : t("{sdk} gerekir, bu TV desteklemiyor", { sdk: status?.min_sdk ?? "?" })}</em>
        </div>
        <div className="stat">
          <span>Home</span>
          <strong>{status?.is_home ? t("temiz başlatıcı") : t("stok")}</strong>
          <em className="ellipsis">{status?.home || "—"}</em>
        </div>
        <div className="stat">
          <span>{t("Tema katmanı")}</span>
          <strong>{status?.overlay_exists ? t("var") : t("yok")}</strong>
          <em>config.json</em>
        </div>
      </div>

      {!status?.supported && (
        <Card tone="danger" title={t("Bu TV temiz başlatıcıyı çalıştıramaz")} subtitle={`SDK ${status?.sdk}`}>
          <p className="small">
            {t("Başlatıcı en az Android 8.0 (SDK 26) ister. Debloat ve dosya işleri çalışmaya devam eder; başlatıcı kurulmaz ve Home devredilmez.")}
          </p>
        </Card>
      )}

      <div className="grid grid-2">
        <Card
          title={t("Home devri")}
          subtitle={t("Stok başlatıcıyı kapatmadan önce Home'un gerçekten devralındığını doğrular")}
          actions={
            <Button size="sm" variant="ghost" icon="refresh" onClick={load}>
              
              {t("Yenile")}
            </Button>
          }
        >
          <Row label={t("Şu an Home")} value={<span className="mono small ellipsis">{status?.home || "—"}</span>} />
          <Row label={t("Öndeki pencere")} value={<span className="mono small ellipsis">{status?.focused || "—"}</span>} />
          <Row
            label={t("Temiz başlatıcı")}
            value={
              status?.installed ? (
                <Badge tone="ok">{t("kurulu")}</Badge>
              ) : (
                <Badge tone="warn">{t("kurulu değil")}</Badge>
              )
            }
          />

          <div className="row gap-2 wrap mt-3">
            {status?.installed ? (
              <Button variant="primary" size="sm" icon="home" loading={busy === "home"} onClick={setAsHome}>
                
                {t("Home yap")}
              </Button>
            ) : (
              <Button variant="primary" size="sm" icon="upload" onClick={() => go("build")}>
                
                {t("Derle ve kur")}
              </Button>
            )}
            <Button variant="ghost" size="sm" icon="sparkle" onClick={() => go("theme")}>
              
              {t("Tema uygula")}
            </Button>
            <Button variant="ghost" size="sm" icon="edit" onClick={() => go("config")}>
              config.xml
            </Button>
          </div>

          {status?.candidates && status.candidates.length > 0 && (
            <div className="mt-3">
              <p className="tiny muted mb-2">{t("Home'u açabilenler")}</p>
              {status.candidates.map((c) => (
                <div key={c} className="mono tiny muted ellipsis">
                  {c}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title={t("Kurallar")} subtitle={t("Her adım doğrulanır")}>
          <div className="steps">
            <div className="step">
              <p>
                <Prose text="**Home önce devralınır.** Stok başlatıcı ancak `mFocusedApp` yeni başlatıcıyı gösterdikten sonra kapatılabilir." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**CVTE kartlarında ekstra adım.** Home devrinden sonra bir kez yeniden başlatın; Home ve ADB geri gelmeden stok başlatıcıya ya da uzaktan kumanda yardımcısına dokunmayın (#741)." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Kurulum sihirbazı.** Wi-Fi ağının sahibi oysa kapatmak TV'yi ağdan ve ADB'den düşürür; uygulama bunu önce sorar." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Geri dönüş var.** Paketler silinmez; `pm enable` ile hepsi geri açılır." />
              </p>
            </div>
          </div>
        </Card>
      </div>

      {home && (
        <Card
          title={home.ok ? t("Home devri tamam") : t("Home devri doğrulanamadı")}
          tone={home.ok ? "ok" : "warn"}
          actions={
            <Button size="sm" variant="ghost" onClick={() => setHome(null)}>
              
              {t("Gizle")}
            </Button>
          }
        >
          <Steps steps={home.steps} />
        </Card>
      )}

      {status?.overlay && (
        <Card
          title={t("Şu anki tema katmanı")}
          subtitle={t("config.json — derleme gerektirmez, anında etki eder")}
          actions={<CopyLine text={status.overlay} />}
        >
          <Code className="tall">{status.overlay}</Code>
        </Card>
      )}

      {!repo?.path && (
        <Card title={t("Repo bulunamadı")} subtitle={t("config.xml ve derleme için gerekli")}>
          <Empty
            icon="folder"
            title={t("CleanYourTV klasörünü gösterin")}
            text={t("Ayarlar sayfasından repo klasörünü seçin; config.xml, strings.xml ve derleme bunun üzerinden çalışır.")}
            action={
              <Button size="sm" variant="soft" onClick={() => go("build")}>
                
                {t("Derleme sekmesi")}
              </Button>
            }
          />
        </Card>
      )}

      {info && (
        <Card title={t("Cihaz notu")} subtitle={t("Hangi TV'de çalıştığınız")}>
          <Row label={t("Model")} value={`${info.brand} ${info.model}`.trim() || "—"} />
          <Row label="Android" value={`${info.android} · SDK ${info.sdk}`} />
          <Row label={t("Derleme")} value={<span className="mono small">{info.build_id}</span>} />
          <Row
            label={t("Uyarı")}
            value={
              <span className="small muted">
                <Icon name="info" size={13} /> {t("Ekran görüntüsü paylaşmadan önce Wi-Fi adını ve selamlama ismini kırpın.")}
              </span>
            }
          />
        </Card>
      )}
    </div>
  );
}
