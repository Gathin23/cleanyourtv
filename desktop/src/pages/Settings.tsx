import { useEffect, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../api";
import type { BackupEntry, RestoreReport, Settings as SettingsType } from "../api";
import { useApp } from "../app-context";
import { t, resolveLang, type LangMode } from "../i18n";
import { useSystemTheme, type ThemeMode } from "../prefs";
import AppearanceCard from "./AppearanceCard";
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
  Row,
  Switch,
  useToast,
} from "../ui";

const empty: SettingsType = {
  adb_path: null,
  repo_path: null,
  backup_dir: null,
  last_host: null,
  last_port: 5555,
  ui_locale: null,
  ui_theme: null,
  confirm_before_reboot: true,
};

export default function SettingsPage() {
  const { adb, settings, saveSettings, refreshAdb, refreshConn, go } = useApp();
  const toast = useToast();
  const [draft, setDraft] = useState<SettingsType>(settings ?? empty);
  const [busy, setBusy] = useState<string | null>(null);
  const [backups, setBackups] = useState<BackupEntry[]>([]);
  const [restoreTarget, setRestoreTarget] = useState<BackupEntry | null>(null);
  const [restoreReport, setRestoreReport] = useState<RestoreReport | null>(null);
  const [about, setAbout] = useState(false);
  const systemTheme = useSystemTheme();

  useEffect(() => {
    if (settings) setDraft(settings);
  }, [settings]);

  const loadBackups = async () => {
    try {
      setBackups(await api.backupList());
    } catch (e) {
      toast.error(e, t("Yedekler okunamadı"));
    }
  };

  useEffect(() => {
    loadBackups();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const persist = async (label: string) => {
    setBusy(label);
    try {
      await saveSettings(draft);
      await refreshAdb();
      toast.push("ok", t("Ayarlar kaydedildi"));
    } catch (e) {
      toast.error(e, t("Kaydedilemedi"));
    } finally {
      setBusy(null);
    }
  };

  const patch = (part: Partial<SettingsType>) => setDraft((prev) => ({ ...prev, ...part }));

  /**
   * Language and theme are the two settings that should not need a Save button: the screen changes
   * under the pointer, so the click has to take effect at once and survive a restart. Everything
   * else on this page still goes through its card's own Save.
   */
  const patchNow = async (part: Partial<SettingsType>) => {
    const next = { ...draft, ...part };
    setDraft(next);
    try {
      await saveSettings(next);
    } catch (e) {
      toast.error(e, t("Kaydedilemedi"));
    }
  };

  return (
    <div className="stack gap-4">
      <div className="grid grid-2">
        <Card
          title="ADB"
          subtitle={t("Uygulama adb'yi PATH'te, Android SDK'da ve ortak klasörlerde arar.")}
          actions={
            <Badge tone={adb?.found ? "ok" : "warn"}>
              {adb?.found ? adb.version || t("bulundu") : t("bulunamadı")}
            </Badge>
          }
        >
          <Field label={t("adb yolu")} hint={adb?.found ? t("kaynak: {source}", { source: adb.source }) : "adb.exe / adb"}>
            <div className="row gap-2">
              <Input
                value={draft.adb_path ?? ""}
                onChange={(e) => patch({ adb_path: e.target.value || null })}
                placeholder="/usr/bin/adb"
                className="mono"
              />
              <Button
                size="sm"
                variant="soft"
                icon="folder"
                onClick={async () => {
                  const file = await openDialog({ multiple: false, directory: false, title: t("adb dosyasını seç") });
                  if (typeof file === "string") patch({ adb_path: file });
                }}
              >
                {t("Seç")}
              </Button>
            </div>
          </Field>

          {adb?.candidates && adb.candidates.length > 0 && (
            <div className="mt-3">
              <p className="tiny muted mb-2">{t("Bulunan adb dosyaları")}</p>
              <div className="col gap-1">
                {adb.candidates.map((c) => (
                  <button
                    key={c}
                    className="crumb mono tiny"
                    style={{ textAlign: "left", overflow: "hidden", textOverflow: "ellipsis" }}
                    onClick={() => patch({ adb_path: c })}
                    title={c}
                  >
                    {c}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="primary"
              icon="check"
              loading={busy === "adb"}
              onClick={() => persist("adb")}
            >
              {t("Kaydet ve dene")}
            </Button>
            <Button size="sm" variant="ghost" icon="refresh" onClick={refreshAdb}>
              {t("Yeniden ara")}
            </Button>
          </div>
          <p className="tiny muted mt-2">
            {t("Android platform-tools kurulu değilse {adb} indirip yukarıdaki alana gösterin. Uygulama ADB'yi kendisi uygulamaz.", { adb: "adb" })}
          </p>
        </Card>

        <Card
          title={t("CleanYourTV kaynağı")}
          subtitle={t("Hazır listeler, config.xml ve derleme bunun üzerinden çalışır.")}
          actions={
            <Button
              size="sm"
              variant="ghost"
              icon="search"
              onClick={async () => {
                const found = await api.repoDetect(draft.repo_path ?? undefined);
                if (found.path) {
                  patch({ repo_path: found.path });
                  toast.push("ok", t("Repo bulundu"), found.path);
                } else {
                  toast.push("error", t("Bulunamadı"), t("launcher/ ve examples/ içeren bir klasör seçin."));
                }
              }}
            >
              {t("Otomatik bul")}
            </Button>
          }
        >
          <Field label={t("Repo klasörü")} hint={t("CleanYourTV'nin klonlandığı klasör")}>
            <div className="row gap-2">
              <Input
                value={draft.repo_path ?? ""}
                onChange={(e) => patch({ repo_path: e.target.value || null })}
                placeholder="/home/user/cleanyourtv"
                className="mono"
              />
              <Button
                size="sm"
                variant="soft"
                icon="folder"
                onClick={async () => {
                  const dir = await openDialog({ directory: true, multiple: false, title: t("Klasör seç") });
                  if (typeof dir === "string") patch({ repo_path: dir });
                }}
              >
                {t("Seç")}
              </Button>
            </div>
          </Field>
          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="primary"
              icon="check"
              loading={busy === "repo"}
              onClick={() => persist("repo")}
            >
              {t("Kaydet")}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => go("launcher")}>
              {t("Başlatıcı sekmesi")}
            </Button>
          </div>
          <p className="tiny muted mt-2">
            {t("Klonlamak için:")} <span className="mono">git clone https://github.com/Gathin23/cleanyourtv</span>
          </p>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card title={t("Yedekler")} subtitle={t("Paket listesi, getprop, ayarlar ve geri yükleme betiği")}>
          <Field label={t("Yedek klasörü")} hint={t("Boş bırakılırsa ev klasörü altındaki CleanYourTV kullanılır.")}>
            <div className="row gap-2">
              <Input
                value={draft.backup_dir ?? ""}
                onChange={(e) => patch({ backup_dir: e.target.value || null })}
                placeholder={t("varsayılan")}
                className="mono"
              />
              <Button
                size="sm"
                variant="soft"
                icon="folder"
                onClick={async () => {
                  const dir = await openDialog({ directory: true, multiple: false, title: t("Yedek klasörü") });
                  if (typeof dir === "string") patch({ backup_dir: dir });
                }}
              >
                {t("Seç")}
              </Button>
            </div>
          </Field>
          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="primary"
              icon="check"
              loading={busy === "backup"}
              onClick={() => persist("backup")}
            >
              
              {t("Kaydet")}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              onClick={async () => {
                await loadBackups();
              }}
            >
              {t("Yenile")}
            </Button>
            <Button
              size="sm"
              variant="soft"
              icon="download"
              onClick={async () => {
                setBusy("newbackup");
                try {
                  const out = await api.backupCreate();
                  toast.push("ok", t("Yedek alındı"), out.path);
                  await loadBackups();
                } catch (e) {
                  toast.error(e, t("Yedek alınamadı"));
                } finally {
                  setBusy(null);
                }
              }}
              loading={busy === "newbackup"}
            >
              {t("Şimdi yedek al")}
            </Button>
          </div>

          <div className="list mt-3" style={{ maxHeight: 240, overflowY: "auto" }}>
            {backups.length === 0 ? (
              <Empty icon="box" title={t("Yedek yok")} text={t("Debloat ekranından ya da yukarıdaki düğmeyle alın.")} />
            ) : (
              backups.map((b) => (
                <div className="list-row" key={b.path}>
                  <span className="icon-wrap">
                    <Icon name="box" size={16} />
                  </span>
                  <div className="col grow" style={{ minWidth: 0 }}>
                    <span className="title small">
                      {b.model || b.host} · {b.created}
                    </span>
                    <span className="sub mono ellipsis">{b.path}</span>
                  </div>
                  <Badge tone={b.disabled ? "warn" : "muted"}>{t("{n} kapalı", { n: b.disabled })}</Badge>
                  <Button size="sm" variant="soft" onClick={() => setRestoreTarget(b)}>
                    {t("Geri yükle")}
                  </Button>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card title={t("Davranış")} subtitle={t("Bağlantı varsayılanları")}>
          <div className="row gap-3">
            <div className="grow">
              <Field label={t("Son TV IP")}>
                <Input
                  value={draft.last_host ?? ""}
                  onChange={(e) => patch({ last_host: e.target.value || null })}
                  placeholder="192.168.1.50"
                />
              </Field>
            </div>
            <div style={{ width: 110 }}>
              <Field label="Port">
                <Input
                  value={String(draft.last_port)}
                  onChange={(e) => patch({ last_port: Number(e.target.value) || 5555 })}
                />
              </Field>
            </div>
          </div>
          <div className="mt-3">
            <Switch
              checked={draft.confirm_before_reboot}
              onChange={(v) => patch({ confirm_before_reboot: v })}
              label={t("Yeniden başlatmadan önce sor")}
            />
          </div>
          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="primary"
              icon="check"
              loading={busy === "misc"}
              onClick={() => persist("misc")}
            >
              {t("Kaydet")}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              icon="refresh"
              onClick={async () => {
                await refreshConn();
                toast.push("info", t("Bağlantı yenilendi"));
              }}
            >
              {t("Bağlantıyı yenile")}
            </Button>
          </div>
        </Card>
      </div>

      <AppearanceCard
        locale={(draft.ui_locale as LangMode) ?? "system"}
        theme={(draft.ui_theme as ThemeMode) ?? "system"}
        systemLang={resolveLang("system") === "tr" ? t("Türkçe") : "English"}
        systemTheme={systemTheme === "dark" ? t("Koyu") : t("Açık")}
        onLocale={(next) => patchNow({ ui_locale: next })}
        onTheme={(next) => patchNow({ ui_theme: next })}
      />

      <Card title={t("Hakkında")} subtitle="CleanYourTV Desktop 0.1.0" actions={<Button size="sm" variant="ghost" onClick={() => setAbout((v) => !v)}>{about ? t("Gizle") : t("Ayrıntı")}</Button>}>
        <div className="row gap-2 wrap">
          <Badge tone="accent">Tauri 2</Badge>
          <Badge tone="neutral">Rust</Badge>
          <Badge tone="neutral">React 19</Badge>
          <Badge tone="ok">{t("ağ erişimi yok")}</Badge>
          <Badge tone="ok">{t("telemetri yok")}</Badge>
        </div>
        {about && (
          <div className="mt-3">
            <p className="small muted">
              {t("Bu uygulama CleanYourTV projesinin güvenlik kurallarını Rust tarafında uygular: çekirdek paketler reddedilir, tek Home uygulaması korunur, yedek alınmadan debloat başlamaz, fabrika test uygulamaları açılmaz. Uygulama internete çıkmaz; yalnızca yerel {adb} ile TV'ye konuşur.", { adb: "adb" })}
            </p>
            <Code className="mt-3">{t(`cleanyourtv desktop
├─ ADB keşfi ve oturum
├─ Yedek + geri yükleme
├─ Debloat (korumalı)
├─ Dosya tarayıcı (SD kart, uygulama verisi)
├─ Başlatıcı: config.json / config.xml / strings.xml
└─ ADB kapatma ve port doğrulama`)}</Code>
          </div>
        )}
      </Card>

      <Modal
        open={!!restoreTarget}
        title={t("Bu yedekten geri yüklensin mi?")}
        subtitle={restoreTarget?.path}
        onClose={() => setRestoreTarget(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setRestoreTarget(null)}>
              {t("Vazgeç")}
            </Button>
            <Button
              variant="primary"
              icon="refresh"
              loading={busy === "restore"}
              onClick={async () => {
                if (!restoreTarget) return;
                setBusy("restore");
                try {
                  const report = await api.backupRestore(restoreTarget.path);
                  setRestoreReport(report);
                  setRestoreTarget(null);
                  toast.push(
                    report.still_disabled.length ? "error" : "ok",
                    t("Geri yükleme bitti"),
                    t("{n} paket açıldı", { n: report.results.filter((r) => r.ok).length }),
                  );
                } catch (e) {
                  toast.error(e, t("Geri yüklenemedi"));
                } finally {
                  setBusy(null);
                }
              }}
            >
              {t("Paketleri aç")}
            </Button>
          </>
        }
      >
        <p className="small">
          {t("Yedekteki {n} kapalı paketin hepsi `pm enable` ile açılır. Hiçbir şey kurulmaz ya da silinmez.", {
            n: restoreTarget?.disabled ?? 0,
          })}
        </p>
        <Row label={t("Cihaz")} value={restoreTarget?.model || "—"} />
        <Row label={t("Tarih")} value={restoreTarget?.created || "—"} />
        <Row label={t("Paket")} value={t("{n} toplam", { n: restoreTarget?.packages ?? 0 })} />
      </Modal>

      <Modal
        open={!!restoreReport}
        title={t("Geri yükleme sonucu")}
        subtitle={
          restoreReport?.still_disabled.length
            ? t("Bazı paketler hâlâ kapalı")
            : t("Hepsi açıldı")
        }
        onClose={() => setRestoreReport(null)}
        footer={
          <Button variant="ghost" onClick={() => setRestoreReport(null)}>
            {t("Kapat")}
          </Button>
        }
      >
        {restoreReport?.results.length ? (
          <>
            <div className="row gap-2 mb-3">
              <Badge tone="ok">{t("{n} açıldı", { n: restoreReport.results.filter((r) => r.ok).length })}</Badge>
              {restoreReport.still_disabled.length > 0 && (
                <Badge tone="danger">{t("{n} hâlâ kapalı", { n: restoreReport.still_disabled.length })}</Badge>
              )}
            </div>
            <div className="progress-log" style={{ maxHeight: 300 }}>
              {restoreReport.results.map((r) => (
                <div className={`progress-line ${r.ok ? "ok" : "fail"}`} key={r.name}>
                  <span className="mono pkg">{r.name}</span>
                  <span className="muted small">{r.ok ? t("açıldı") : r.output.slice(0, 80)}</span>
                </div>
              ))}
            </div>
          </>
        ) : (
          <p className="small muted">{t("Bu yedekte açılacak kapalı paket yoktu.")}</p>
        )}
      </Modal>
    </div>
  );
}
