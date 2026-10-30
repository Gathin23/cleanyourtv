import { useEffect, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../api";
import type { ScanResult } from "../api";
import { useApp } from "../app-context";
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
  useToast,
} from "../ui";
import { t } from "../i18n";
import { Prose } from "../parts";

export default function Connect() {
  const { adb, conn, settings, refreshAdb, refreshConn, refreshInfo, go } = useApp();
  const toast = useToast();
  const [host, setHost] = useState(settings?.last_host ?? "");
  const [port, setPort] = useState(String(settings?.last_port ?? 5555));
  const [busy, setBusy] = useState<string | null>(null);
  const [scan, setScan] = useState<ScanResult | null>(null);
  const [pairHost, setPairHost] = useState("");
  const [pairPort, setPairPort] = useState("");
  const [pairCode, setPairCode] = useState("");
  const [probe, setProbe] = useState<{ open: boolean; detail: string } | null>(null);

  useEffect(() => {
    if (settings?.last_host && !host) setHost(settings.last_host);
    if (settings?.last_port && port === "5555") setPort(String(settings.last_port));
  }, [settings, host, port]);

  const connect = async () => {
    const p = Number(port);
    if (!host.trim() || !Number.isFinite(p) || p < 1) {
      toast.push("error", "Eksik bilgi", "TV'nin IP adresini ve portu girin.");
      return;
    }
    setBusy("connect");
    try {
      await api.connect(host.trim(), p);
      await refreshConn();
      await refreshInfo();
      toast.push("ok", t("Bağlandı"), t("{target} üzerinden TV'ye erişiliyor.", { target: `${host.trim()}:${p}` }));
      go("dashboard");
    } catch (e) {
      toast.error(e, t("Bağlanılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const doScan = async () => {
    setBusy("scan");
    try {
      const found = await api.discover();
      setScan(found);
    } catch (e) {
      toast.error(e, t("Tarama başarısız"));
    } finally {
      setBusy(null);
    }
  };

  const doPair = async () => {
    const p = Number(pairPort);
    if (!pairHost.trim() || !Number.isFinite(p) || p < 1 || pairCode.trim().length < 4) {
      toast.push("error", "Eksik bilgi", t("Eşleştirme portu ve kodu gerekli."));
      return;
    }
    setBusy("pair");
    try {
      const out = await api.pair(pairHost.trim(), p, pairCode.trim());
      toast.push("ok", t("Eşleştirildi"), out);
      setPairCode("");
    } catch (e) {
      toast.error(e, t("Eşleştirilemedi"));
    } finally {
      setBusy(null);
    }
  };

  const checkPort = async () => {
    const p = Number(port);
    if (!host.trim() || !Number.isFinite(p)) return;
    setBusy("probe");
    try {
      setProbe(await api.portCheck(host.trim(), p));
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(null);
    }
  };

  const pickAdb = async () => {
    const file = await openDialog({
      multiple: false,
      directory: false,
      title: t("adb (adb.exe) dosyasını seçin"),
    });
    if (typeof file !== "string") return;
    try {
      await api.adbSetPath(file);
      await refreshAdb();
      toast.push("ok", t("ADB ayarlandı"), file);
    } catch (e) {
      toast.error(e, t("Bu dosya adb değil"));
    }
  };

  const disconnect = async () => {
    setBusy("disconnect");
    try {
      await api.disconnect();
      await refreshConn();
      toast.push("info", t("Bağlantı kapatıldı"));
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="stack gap-5">
      {conn?.connected ? (
        <div className="hero">
          <div className="hero-art" />
          <div className="eyebrow">{t("Bağlı")}</div>
          <h2>{t("TV hazır: {host}", { host: conn.host })}</h2>
          <p>
            {t("{target} adresine ADB ile bağlısınız. Artık yedek alabilir, paketleri kapatabilir, dosyalara göz atabilir ve başlatıcıyı özelleştirebilirsiniz.", {
              target: conn.target,
            })}
          </p>
          <div className="hero-actions">
            <Button variant="primary" size="lg" icon="tv" onClick={() => go("dashboard")}>
              {t("Genel bakışa git")}
            </Button>
            <Button variant="ghost" onClick={disconnect} loading={busy === "disconnect"}>
              {t("Bağlantıyı kapat")}
            </Button>
          </div>
        </div>
      ) : (
        <div className="hero">
          <div className="hero-art" />
          <div className="eyebrow">{t("İlk adım")}</div>
          <h2>{t("TV'nizi bilgisayardan yönetin")}</h2>
          <p>
            {t("Aynı Wi-Fi ağındaki Android TV veya Google TV'ye bağlanın. Uygulama önce yedek alır, yalnızca güvenli paketleri kapatır ve her adımı doğrular.")}
          </p>
          <div className="hero-actions">
            <Button variant="primary" size="lg" icon="plug" onClick={doScan} loading={busy === "scan"}>
              
              {t("TV'leri tara")}
            </Button>
            <Button variant="ghost" onClick={() => go("settings")}>
              
              {t("ADB yolu")}
            </Button>
          </div>
        </div>
      )}

      <div className="grid grid-2">
        <Card title={t("Bağlantı")} subtitle={t("TV'nin IP adresi ve ADB portu")}>
          {!adb?.found && (
            <div className="card card-warn" style={{ padding: 14 }}>
              <div className="row gap-2">
                <Icon name="alert" size={18} />
                <strong>{t("ADB bulunamadı")}</strong>
              </div>
              <p className="small muted mt-2">
                {t("Android platform-tools kurun veya aşağıdaki düğmeyle adb dosyasını gösterin.")}
              </p>
              <Button size="sm" variant="soft" icon="folder" className="mt-3" onClick={pickAdb}>
                {t("adb dosyasını seç")}
              </Button>
            </div>
          )}

          <div className="row gap-3 wrap">
            <div className="grow">
              <Field label={t("TV IP adresi")}>
                <Input
                  value={host}
                  onChange={(e) => setHost(e.target.value)}
                  placeholder="192.168.1.50"
                  autoFocus
                />
              </Field>
            </div>
            <div style={{ width: 110 }}>
              <Field label="Port">
                <Input value={port} onChange={(e) => setPort(e.target.value)} />
              </Field>
            </div>
          </div>

          <div className="row gap-2 wrap">
            <Button variant="primary" icon="plug" onClick={connect} loading={busy === "connect"}>
              {t("Bağlan")}
            </Button>
            <Button variant="ghost" size="sm" onClick={checkPort} loading={busy === "probe"}>
              
              {t("Portu kontrol et")}
            </Button>
            {probe && (
              <Badge tone={probe.open ? "ok" : "danger"}>
                {probe.open ? t("Port açık") : t("Port kapalı")}
              </Badge>
            )}
          </div>
          {probe && <p className="small muted">{probe.detail}</p>}

          <div className="row gap-2 mt-2">
            <span className={`dot ${adb?.found ? "on" : "off"}`} />
            <span className="small muted">
              {adb?.found ? `${adb.version || "adb"} · ${adb.path}` : t("adb bulunamadı")}
            </span>
          </div>
        </Card>

        <Card
          title={t("Kablosuz hata ayıklama (eşleştirme)")}
          subtitle={t("Google TV çoğu zaman yalnızca bu yolu sunar")}
        >
          <p className="small muted">
            <Prose text="TV'de `Geliştirici seçenekleri → Kablosuz hata ayıklama → Cihazla eşleştir` ekranındaki **eşleştirme portunu** ve 6 haneli kodu girin. Eşleştirme portu, 5555'ten farklıdır." />
          </p>
          <div className="row gap-3 wrap mt-3">
            <div className="grow">
              <Field label={t("IP adresi")}>
                <Input
                  value={pairHost}
                  onChange={(e) => setPairHost(e.target.value)}
                  placeholder={host || "192.168.1.50"}
                />
              </Field>
            </div>
            <div style={{ width: 130 }}>
              <Field label={t("Eşleştirme portu")}>
                <Input
                  value={pairPort}
                  onChange={(e) => setPairPort(e.target.value)}
                  placeholder="37123"
                />
              </Field>
            </div>
            <div style={{ width: 130 }}>
              <Field label={t("Kod")}>
                <Input
                  value={pairCode}
                  onChange={(e) => setPairCode(e.target.value)}
                  placeholder="123456"
                />
              </Field>
            </div>
          </div>
          <Button
            variant="soft"
            icon="key"
            className="mt-3"
            onClick={doPair}
            loading={busy === "pair"}
          >
            {t("Eşleştir")}
          </Button>

          <div className="mt-4">
            <p className="small muted">
              <Prose text="Eşleştirdikten sonra **Tara** düğmesiyle açılan `_adb-tls-connect` adresine bağlanın. TV'nin kendi portu açıksa doğrudan IP:5555 de çalışır." />
            </p>
          </div>
        </Card>
      </div>

      <Card
        title={t("Bulunan cihazlar")}
        subtitle={t("Kablosuz hata ayıklama servisleri ve bağlı cihazlar")}
        actions={
          <Button size="sm" variant="ghost" icon="refresh" onClick={doScan} loading={busy === "scan"}>
            
            {t("Yeniden tara")}
          </Button>
        }
      >
        {!scan ? (
          <Empty
            icon="wifi"
            title={t("Henüz tarama yapılmadı")}
            text={t("TV'nin kablosuz hata ayıklaması açıksa ve aynı ağdaysa burada görünür.")}
          />
        ) : scan.mdns.length === 0 && scan.devices.length === 0 ? (
          <Empty icon="wifi" title={t("Cihaz bulunamadı")} text={scan.note} />
        ) : (
          <div className="list">
            {scan.mdns.map((m) => {
              const [h, p] = m.address.split(":");
              return (
                <div className="list-row" key={m.name}>
                  <span className="icon-wrap">
                    <Icon name="wifi" size={17} />
                  </span>
                  <div className="col grow" style={{ minWidth: 0 }}>
                    <span className="title mono">{m.address}</span>
                    <span className="sub">
                      {m.name} · {m.kind}
                    </span>
                  </div>
                  <Button
                    size="sm"
                    variant="soft"
                    onClick={() => {
                      setHost(h);
                      setPort(p || "5555");
                    }}
                  >
                    
                    {t("Adresi kullan")}
                  </Button>
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={async () => {
                      setHost(h);
                      setPort(p || "5555");
                      setBusy("connect");
                      try {
                        await api.connect(h, Number(p || 5555));
                        await refreshConn();
                        await refreshInfo();
                        toast.push("ok", t("Bağlandı"), `${h}:${p}`);
                        go("dashboard");
                      } catch (e) {
                        toast.error(e, t("Bağlanılamadı; eşleştirme gerekebilir"));
                      } finally {
                        setBusy(null);
                      }
                    }}
                  >
                    {t("Bağlan")}
                  </Button>
                </div>
              );
            })}
            {scan.devices.map((d) => (
              <div className="list-row" key={d.serial}>
                <span className="icon-wrap">
                  <Icon name="tv" size={17} />
                </span>
                <div className="col grow" style={{ minWidth: 0 }}>
                  <span className="title mono">{d.serial}</span>
                  <span className="sub">
                    {d.model || d.product || "Android TV"} · {d.connection} · {d.state}
                  </span>
                </div>
                <Badge tone={d.state === "device" ? "ok" : "warn"}>{d.state}</Badge>
              </div>
            ))}
          </div>
        )}
        {scan && <p className="small muted mt-2">{scan.note}</p>}
      </Card>

      <div className="grid grid-2">
        <Card title={t("TV'de ne yapmalı?")} subtitle={t("İlk kurulum, bir kez")}>
          <div className="steps">
            <div className="step">
              <p>
                <Prose text="**Ayarlar → Cihaz tercihleri → Hakkında** bölümünde “Derleme” satırına 7 kez OK deyin." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Geliştirici seçenekleri** bölümüne girin; USB hata ayıklamayı açın (varsa Ağ/ADB hata ayıklamayı da)." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="TV'de **“USB hata ayıklamaya izin ver?”** penceresi çıkınca **“Her zaman izin ver”**i işaretleyip onaylayın." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="İşiniz bitince **Gizlilik → ADB'yi kapat** ekranından hata ayıklamayı kapatın. 5555 şifresizdir." />
              </p>
            </div>
          </div>
        </Card>

        <Card title={t("Sorun giderme")} subtitle={t("Bağlanmıyorsa")}>
          <div className="stack gap-2">
            <Row
              label={t("Port kapalı")}
              value={<span className="small">{t("TV'de hata ayıklama kapalı olabilir")}</span>}
            />
            <Row
              label={t("Bağlandı ama komut yok")}
              value={<span className="small">{t("TV'deki izin penceresini onaylayın")}</span>}
            />
            <Row
              label={t("Başka ağ")}
              value={<span className="small">{t("Misafir ağı / izolasyon kapalı olmalı")}</span>}
            />
            <Row
              label="USB Ethernet"
              value={<span className="small">{t("Hata ayıklama açıkken portun gücü kesilebilir")}</span>}
            />
          </div>
          <div className="mt-4">
            <Code>{`adb connect <tv ip>:5555
adb devices -l
adb mdns services`}</Code>
          </div>
        </Card>
      </div>

      {busy && (
        <div className="center">
          <Spinner label={t("Çalışıyor…")} />
        </div>
      )}
    </div>
  );
}
