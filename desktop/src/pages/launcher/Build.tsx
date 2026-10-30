import { useState } from "react";
import * as api from "../../api";
import type { BuildResult, HomeReport } from "../../api";
import { useApp } from "../../app-context";
import {
  Badge,
  Button,
  Card,
  Code,
  Empty,
  Field,
  Icon,
  Input,
  Row,
  Spinner,
  Switch,
  bytes,
  useToast,
} from "../../ui";
import { Steps } from "../../parts";
import { Prose } from "../../parts";
import { t } from "../../i18n";

const SIZE_GATE = 589_824;

export default function BuildPage({ goTab }: { goTab: (tab: string) => void }) {
  const { repo, info, refreshRepo, refreshInfo, go } = useApp();
  const toast = useToast();
  const [path, setPath] = useState(repo?.path ?? "");
  const [build, setBuild] = useState<BuildResult | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [setHome, setSetHome] = useState(true);
  const [install, setInstall] = useState<HomeReport | null>(null);

  const run = async () => {
    if (!repo?.path) {
      toast.push("error", "Repo yok", t("Önce CleanYourTV klasörünü gösterin."));
      return;
    }
    setBusy("build");
    setBuild(null);
    setInstall(null);
    try {
      const result = await api.launcherBuild(repo.path);
      setBuild(result);
      if (result.ok) {
        toast.push("ok", "Derleme tamam", `${bytes(result.size)} · ${result.duration} sn`);
      } else {
        toast.push("error", t("Derleme başarısız"), result.log.split("\n").slice(-4).join("\n"));
      }
    } catch (e) {
      toast.error(e, t("Derleme başlatılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const doInstall = async () => {
    if (!build?.apk) return;
    setBusy("install");
    setInstall(null);
    try {
      const report = await api.launcherInstall(build.apk, setHome);
      setInstall(report);
      await refreshInfo();
      toast.push(
        report.ok ? "ok" : "error",
        report.ok ? t("Kuruldu") : t("Kurulum doğrulanamadı"),
        report.home || report.steps.map((s) => s.label).join(", "),
      );
    } catch (e) {
      toast.error(e, t("Kurulamadı"));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="stack gap-4">
      <Card
        title={t("Kaynaktan derle")}
        subtitle={t("launcher/ projesini bu bilgisayarda derler; APK'yı TV'ye kurar.")}
        actions={
          <Badge tone={repo?.path ? "ok" : "warn"}>
            {repo?.path ? t("repo bulundu") : t("repo yok")}
          </Badge>
        }
      >
        <Field
          label={t("CleanYourTV klasörü")}
          hint={t("İçinde launcher/ ve examples/ olan klasör. Ayarlar'dan da değiştirilebilir.")}
        >
          <div className="row gap-2">
            <Input
              value={path}
              onChange={(e) => setPath(e.target.value)}
              placeholder={t("/home/kullanici/cleanyourtv")}
            />
            <Button
              size="sm"
              variant="soft"
              icon="search"
              onClick={async () => {
                await refreshRepo(path);
                toast.push("info", t("Repo kontrol edildi"));
              }}
            >
              
              {t("Kontrol et")}
            </Button>
          </div>
        </Field>

        <div className="row gap-2 wrap mt-3">
          <Button
            variant="primary"
            icon="box"
            loading={busy === "build"}
            disabled={!repo?.path}
            onClick={run}
          >
            
            {t("Derle (assembleRelease)")}
          </Button>
          {repo?.path && (
            <span className="small muted">
              {repo.launcher ? t("launcher kaynağı var") : t("launcher/config.xml yok")} ·{" "}
              {repo.examples} {t("örnek")}
            </span>
          )}
        </div>

        <div className="card card-accent" style={{ padding: 12, marginTop: 14 }}>
          <div className="row gap-2 small">
            <Icon name="info" size={16} />
            <span>
              <Prose text="**Derleme** `JDK 17` ve `Android SDK platform 34` ister. APK `576 KB` sınırının altında kalmalı." />
            </span>
          </div>
        </div>

        {busy === "build" && (
          <p className="small muted mt-3">
            {t("Gradle çalışıyor… İlk derleme bağımlılıkları indirdiği için birkaç dakika sürebilir.")}
          </p>
        )}
      </Card>

      {build && (
        <Card
          title={build.ok ? t("Derleme başarılı") : t("Derleme başarısız")}
          tone={build.ok ? "ok" : "danger"}
          subtitle={t("{seconds} sn · {java}", { seconds: build.duration, java: build.java || t("java bulunamadı") })}
          actions={
            build.ok ? (
              <Badge tone={build.size < SIZE_GATE ? "ok" : "danger"}>
                {bytes(build.size)} / 576 KB
              </Badge>
            ) : undefined
          }
        >
          {build.ok ? (
            <>
              <Row label="APK" value={<span className="mono small ellipsis">{build.apk}</span>} />
              <Row
                label={t("Boyut sınırı")}
                value={
                  build.size < SIZE_GATE ? (
                    <Badge tone="ok">{t("sınırın altında")}</Badge>
                  ) : (
                    <Badge tone="danger">{t("576 KB üstünde")}</Badge>
                  )
                }
              />
              <div className="row gap-2 wrap mt-3">
                <Switch checked={setHome} onChange={setSetHome} label={t("Kurduktan sonra Home yap")} />
              </div>
              <div className="row gap-2 wrap mt-3">
                <Button
                  variant="primary"
                  icon="upload"
                  loading={busy === "install"}
                  onClick={doInstall}
                >
                  
                  {t("TV'ye kur")}
                </Button>
                <Button variant="ghost" size="sm" icon="sparkle" onClick={() => goTab("theme")}>
                  {t("Tema katmanı")}
                </Button>
                <Button variant="ghost" size="sm" icon="edit" onClick={() => goTab("strings")}>
                  
                  {t("Selamlamalar")}
                </Button>
              </div>
              <p className="tiny muted mt-3">
                <Prose text="Android 14'te `adb install` onay penceresinde takılırsa uygulama APK'yı `/data/local/tmp` üzerinden `pm install` ile kurar." />
              </p>
            </>
          ) : (
            <p className="small">
              <Prose text="Gradle çıktısının son satırları aşağıda. JDK 17 kurulu mu, `ANDROID_HOME` doğru mu, platform 34 indirilmiş mi?" />
            </p>
          )}
          <Code className="tall mt-3">{build.log}</Code>
        </Card>
      )}

      {install && (
        <Card
          title={install.ok ? "Kurulum ve Home tamam" : t("Kurulum tamamlanmadı")}
          tone={install.ok ? "ok" : "warn"}
          actions={
            <Button size="sm" variant="ghost" onClick={() => setInstall(null)}>
              
              {t("Gizle")}
            </Button>
          }
        >
          <Steps steps={install.steps} />
          {install.cvte && (
            <p className="small mt-3" style={{ color: "var(--warn)" }}>
              {t("CVTE kartı: şimdi bir kez yeniden başlatın, Home ve ADB geri gelsin, ondan sonra stok başlatıcıya dokunun.")}
            </p>
          )}
        </Card>
      )}

      <div className="grid grid-2">
        <Card title={t("Bu TV")} subtitle={t("Hedef cihaz")}>
          {info ? (
            <>
              <Row label={t("Model")} value={`${info.brand} ${info.model}`.trim() || "—"} />
              <Row label="Android" value={`${info.android} · SDK ${info.sdk}`} />
              <Row label={t("Derleme")} value={<span className="mono small">{info.build_id}</span>} />
              <Row
                label={t("Kurulu launcher")}
                value={
                  info.launcher_installed ? (
                    <Badge tone="ok">{info.launcher_version || "kurulu"}</Badge>
                  ) : (
                    <Badge tone="warn">{t("yok")}</Badge>
                  )
                }
              />
              {info.sdk < 26 && (
                <p className="small mt-2" style={{ color: "var(--danger)" }}>
                  {t("SDK {sdk}: başlatıcı en az 26 ister, kurulum reddedilir. Debloat çalışır.", { sdk: info.sdk })}
                </p>
              )}
            </>
          ) : (
            <Spinner label={t("Cihaz okunuyor…")} />
          )}
        </Card>

        <Card title={t("Kurulum sırası")} subtitle={t("Güvenli sıra")}>
          <div className="steps">
            <div className="step">
              <p>
                <Prose text="**Yedek alın.** Debloat sayfasındaki tek tuş." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Yeni başlatıcıyı kurun** ve Home yapın; stok başlatıcı hâlâ açık kalsın." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Yeniden başlatın**, Home ve ADB'nin geri geldiğini doğrulayın." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Şimdi debloat edin.** Stok başlatıcıyı ve setupwraith'i en sona bırakın." />
              </p>
            </div>
          </div>
        </Card>
      </div>

      {!repo?.path && (
        <Card>
          <Empty
            icon="folder"
            title={t("Repo klasörü seçilmedi")}
            text={t("Derleme için CleanYourTV kaynağının bu bilgisayarda olması gerekir.")}
            action={
              <Button size="sm" variant="soft" onClick={() => go("settings")}>
                
                {t("Ayarlar")}
              </Button>
            }
          />
        </Card>
      )}
    </div>
  );
}
