import { useCallback, useEffect, useMemo, useState } from "react";
import * as api from "./api";
import type { AdbStatus, Connection, DeviceInfo, RepoInfo, Settings } from "./api";
import { AppContext, type Page } from "./app-context";
import { t } from "./i18n";
import { applyPrefs, usePrefs } from "./prefs";
import { Badge, Button, Icon, Spinner, useToast } from "./ui";
import Connect from "./pages/Connect";
import Dashboard from "./pages/Dashboard";
import Debloat from "./pages/Debloat";
import AppsPage from "./pages/Apps";
import Files from "./pages/Files";
import Launcher from "./pages/Launcher";
import TvSettings from "./pages/TvSettings";
import Privacy from "./pages/Privacy";
import SettingsPage from "./pages/Settings";

const pages: {
  id: Page;
  label: string;
  icon: string;
  title: string;
  sub: string;
  group: string;
}[] = [
  {
    id: "connect",
    label: "Bağlan",
    icon: "plug",
    title: "TV'ye bağlan",
    sub: "Aynı Wi-Fi'daki Android TV veya Google TV'yi bulun.",
    group: "TV",
  },
  {
    id: "dashboard",
    label: "Genel Bakış",
    icon: "tv",
    title: "Genel bakış",
    sub: "TV'nin durumu, Home ekranı ve uzaktan kumanda.",
    group: "TV",
  },
  {
    id: "debloat",
    label: "Debloat",
    icon: "broom",
    title: "Temizlik",
    sub: "Gereksiz paketleri, güvenlik kurallarıyla, parti parti kapatın.",
    group: "TV",
  },
  {
    id: "apps",
    label: "Uygulamalar",
    icon: "grid",
    title: "Uygulamalar",
    sub: "Her paketi inceleyin, açın, durdurun veya kapatın.",
    group: "TV",
  },
  {
    id: "files",
    label: "Dosyalar",
    icon: "folder",
    title: "Dosyalar",
    sub: "TV'nin klasörlerine göz atın; dosya çekin, gönderin, düzenleyin.",
    group: "TV",
  },
  {
    id: "launcher",
    label: "Başlatıcı",
    icon: "home",
    title: "Başlatıcı ve görünüm",
    sub: "Tema, config.xml, selamlama metinleri ve derleme.",
    group: "TV",
  },
  {
    id: "tv",
    label: "TV Ayarları",
    icon: "sliders",
    title: "TV ayarları",
    sub: "Private DNS, uyku, saat ve animasyon hızları: TV'nin kendi ayarları.",
    group: "TV",
  },
  {
    id: "privacy",
    label: "Gizlilik",
    icon: "shield",
    title: "Gizlilik ve güvenlik",
    sub: "Private DNS, ADB'yi kapatma ve doğrulama.",
    group: "Sistem",
  },
  {
    id: "settings",
    label: "Ayarlar",
    icon: "settings",
    title: "Ayarlar",
    sub: "ADB yolu, repo klasörü ve yedekler.",
    group: "Sistem",
  },
];

export default function App() {
  const toast = useToast();
  const [page, setPage] = useState<Page>("connect");
  const [adb, setAdb] = useState<AdbStatus | null>(null);
  const [conn, setConn] = useState<Connection | null>(null);
  const [info, setInfo] = useState<DeviceInfo | null>(null);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [repo, setRepo] = useState<RepoInfo | null>(null);
  const [booting, setBooting] = useState(true);
  // Language and light/dark follow the system until Settings says otherwise.
  const prefs = usePrefs(settings);
  useEffect(() => applyPrefs(prefs), [prefs]);

  const refreshInfo = useCallback(async () => {
    try {
      const next = await api.deviceInfo();
      setInfo(next);
    } catch (e) {
      setInfo(null);
      toast.error(e, t("TV okunamadı"));
    }
  }, [toast]);

  const refreshAdb = useCallback(async () => {
    try {
      setAdb(await api.adbStatus());
    } catch (e) {
      toast.error(e);
    }
  }, [toast]);

  const refreshRepo = useCallback(
    async (path?: string) => {
      try {
        const detected = await api.repoDetect(path);
        setRepo(detected);
      } catch (e) {
        toast.error(e, t("Repo bulunamadı"));
      }
    },
    [toast],
  );

  const refreshConn = useCallback(async () => {
    try {
      const next = await api.connection();
      setConn(next);
      if (next.connected) {
        await refreshInfo();
      } else {
        setInfo(null);
      }
    } catch (e) {
      toast.error(e);
    }
  }, [refreshInfo, toast]);

  const saveSettings = useCallback(
    async (next: Settings) => {
      const saved = await api.settingsSave(next);
      setSettings(saved);
      await refreshAdb();
      await refreshRepo(saved.repo_path ?? undefined);
    },
    [refreshAdb, refreshRepo],
  );

  useEffect(() => {
    (async () => {
      try {
        const [s, a, c] = await Promise.all([
          api.settingsGet(),
          api.adbStatus(),
          api.connection(),
        ]);
        setSettings(s);
        setAdb(a);
        setConn(c);
        const detected = await api.repoDetect(s.repo_path ?? undefined);
        setRepo(detected);
        if (c.connected) {
          try {
            setInfo(await api.deviceInfo());
          } catch (e) {
            toast.error(e, t("TV bağlı görünüyor ama okunamadı"));
          }
        }
      } catch (e) {
        toast.error(e);
      } finally {
        setBooting(false);
      }
    })();
  }, [toast]);

  const value = useMemo(
    () => ({
      page,
      go: setPage,
      adb,
      conn,
      info,
      settings,
      repo,
      refreshAdb,
      refreshConn,
      refreshInfo,
      refreshRepo,
      saveSettings,
    }),
    [page, adb, conn, info, settings, repo, refreshAdb, refreshConn, refreshInfo, refreshRepo, saveSettings],
  );

  const current = pages.find((p) => p.id === page)!;
  const connected = !!conn?.connected;
  // The launcher screen is reachable without a TV: its config.xml, strings.xml and build tabs work
  // on this computer. Every other screen needs the TV.
  const offline: Page[] = ["connect", "settings", "launcher"];

  return (
    <AppContext.Provider value={value}>
      <div className="app">
        <aside className="sidebar">
          <div className="logo">
            <span className="logo-mark">
              <Icon name="tv" size={21} />
            </span>
            <span className="logo-text">
              <strong>CleanYourTV</strong>
              <span>Desktop</span>
            </span>
          </div>

          <nav className="nav">
            {pages.map((p, i) => (
              <div key={p.id}>
                {(i === 0 || pages[i - 1].group !== p.group) && (
                  <div className="nav-label">{t(p.group)}</div>
                )}
                <button
                  className={`nav-item ${page === p.id ? "active" : ""}`}
                  onClick={() => setPage(p.id)}
                  disabled={!connected && !offline.includes(p.id)}
                  title={
                    !connected && !offline.includes(p.id) ? t("Önce TV'ye bağlanın") : t(p.label)
                  }
                >
                  <Icon name={p.icon} size={18} />
                  <span>{t(p.label)}</span>
                </button>
              </div>
            ))}
          </nav>

          <div className="sidebar-foot">
            <div className="device-card" onClick={() => setPage("connect")}>
              <span className={`dot ${connected ? "on" : "off"}`} />
              <div className="col" style={{ minWidth: 0 }}>
                <span className="small ellipsis" style={{ color: "var(--text)" }}>
                  {connected ? info?.model || t("TV bağlı") : t("Bağlı değil")}
                </span>
                <span className="tiny muted ellipsis">
                  {connected
                    ? `Android ${info?.android || "?"} · ${conn?.host}`
                    : t("Bağlanmak için tıklayın")}
                </span>
              </div>
            </div>
          </div>
        </aside>

        <div className="main">
          <header className="topbar">
            <div>
              <h1>{t(current.title)}</h1>
              <div className="sub">{t(current.sub)}</div>
            </div>
            <div className="topbar-actions">
              {connected ? (
                <>
                  <div className="device-chip">
                    <span className="dot on" />
                    <strong className="ellipsis">{info?.model || conn?.host}</strong>
                    <span className="muted">·</span>
                    <span className="mono tiny">{conn?.target}</span>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon="refresh"
                    onClick={async () => {
                      await refreshConn();
                      toast.push("ok", t("TV yenilendi"));
                    }}
                  >
                    {t("Yenile")}
                  </Button>
                </>
              ) : (
                <>
                  <Badge tone={adb?.found ? "neutral" : "warn"}>
                    {adb?.found ? t("ADB hazır") : t("ADB bulunamadı")}
                  </Badge>
                  <Button variant="primary" size="sm" icon="plug" onClick={() => setPage("connect")}>
                    {t("Bağlan")}
                  </Button>
                </>
              )}
            </div>
          </header>

          <main className="content">
            {booting ? (
              <div className="center mt-6">
                <Spinner label={t("Hazırlanıyor…")} />
              </div>
            ) : page === "connect" ? (
              <Connect />
            ) : page === "dashboard" ? (
              <Dashboard />
            ) : page === "debloat" ? (
              <Debloat />
            ) : page === "apps" ? (
              <AppsPage />
            ) : page === "files" ? (
              <Files />
            ) : page === "launcher" ? (
              <Launcher />
            ) : page === "tv" ? (
              <TvSettings />
            ) : page === "privacy" ? (
              <Privacy />
            ) : (
              <SettingsPage />
            )}
          </main>
        </div>
      </div>
    </AppContext.Provider>
  );
}
