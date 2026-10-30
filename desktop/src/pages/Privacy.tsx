import { useEffect, useState } from "react";
import * as api from "../api";
import type { CloseReport, Step } from "../api";
import { useApp } from "../app-context";
import {
  Badge,
  Button,
  Card,
  Code,
  Confirm,
  Empty,
  Field,
  Icon,
  Input,
  Select,
  Spinner,
  Switch,
  useToast,
} from "../ui";
import { Steps } from "../parts";
import { t } from "../i18n";
import { Prose } from "../parts";

export default function Privacy() {
  const { conn, info, refreshConn, refreshInfo, go } = useApp();
  const toast = useToast();
  const [dns, setDns] = useState<{ mode: string; specifier: string } | null>(null);
  const [mode, setMode] = useState("off");
  const [specifier, setSpecifier] = useState("dns.adguard-dns.com");
  const [dnsBusy, setDnsBusy] = useState(false);
  const [dnsSteps, setDnsSteps] = useState<Step[]>([]);

  const [wireless, setWireless] = useState(true);
  const [usb, setUsb] = useState(true);
  const [askClose, setAskClose] = useState(false);
  const [closing, setClosing] = useState(false);
  const [report, setReport] = useState<CloseReport | null>(null);

  const [checkPort, setCheckPort] = useState("5555");
  const [portState, setPortState] = useState<{ open: boolean; detail: string } | null>(null);
  const [probeBusy, setProbeBusy] = useState(false);

  useEffect(() => {
    api
      .privateDnsGet()
      .then((state) => {
        setDns(state);
        setMode(state.mode === "hostname" || state.mode === "opportunistic" ? state.mode : "off");
        if (state.specifier) setSpecifier(state.specifier);
      })
      .catch(() => setDns(null));
  }, []);

  const applyDns = async () => {
    setDnsBusy(true);
    setDnsSteps([]);
    try {
      const steps = await api.privateDnsSet(mode, mode === "hostname" ? specifier.trim() : "");
      setDnsSteps(steps);
      setDns(await api.privateDnsGet());
      toast.push("ok", t("Private DNS ayarlandı"), `mod: ${mode}`);
    } catch (e) {
      toast.error(e, t("DNS ayarlanamadı"));
    } finally {
      setDnsBusy(false);
    }
  };

  const probe = async () => {
    if (!conn?.host) return;
    setProbeBusy(true);
    try {
      setPortState(await api.portCheck(conn.host, Number(checkPort) || 5555));
    } catch (e) {
      toast.error(e);
    } finally {
      setProbeBusy(false);
    }
  };

  const close = async () => {
    if (!conn) return;
    setClosing(true);
    try {
      const result = await api.closeAdb(
        conn.host,
        conn.port,
        wireless,
        usb,
        Number(checkPort) || undefined,
      );
      setReport(result);
      setAskClose(false);
      await refreshConn();
      await refreshInfo().catch(() => {});
      toast.push(
        result.port_open ? "error" : "ok",
        result.port_open ? t("Port hâlâ açık") : t("ADB kapatıldı"),
        result.port_detail,
      );
    } catch (e) {
      toast.error(e, t("Kapatılamadı"));
    } finally {
      setClosing(false);
    }
  };

  return (
    <div className="stack gap-4">
      <Card
        tone="warn"
        title={t("İş bitince ADB'yi kapatın")}
        subtitle={t("Port 5555 şifresizdir. Aynı ağdaki herkes, izin verdikten sonra TV'ye komut gönderebilir.")}
        actions={
          <Button variant="primary" size="sm" icon="lock" onClick={() => setAskClose(true)}>
            
            {t("ADB'yi kapat")}
          </Button>
        }
      >
        <div className="grid grid-3">
          <div className="stat">
            <span>{t("Kablosuz hata ayıklama")}</span>
            <strong>
              {info?.wireless_debugging === null || info?.wireless_debugging === undefined
                ? "bilinmiyor"
                : info.wireless_debugging
                  ? t("açık")
                  : t("kapalı")}
            </strong>
            <em>settings global adb_wifi_enabled</em>
          </div>
          <div className="stat">
            <span>ADB (USB)</span>
            <strong>{info?.adb_enabled ? t("açık") : t("kapalı")}</strong>
            <em>settings global adb_enabled</em>
          </div>
          <div className="stat">
            <span>{t("Bağlantı")}</span>
            <strong className="mono">{conn?.target || "yok"}</strong>
            <em>{t("bu oturum")}</em>
          </div>
        </div>

        {info && !info.adb_secure && (
          <div className="card card-danger" style={{ padding: 12, marginTop: 14 }}>
            <div className="row gap-2">
              <Icon name="alert" size={17} />
              <span className="small">
                <strong>{t("Bu TV'de ro.adb.secure=0.")}</strong> {t("5555 portu, izin penceresi sormadan aynı ağdaki herkese kabuk verir. İş bitince hata ayıklamayı mutlaka kapatın.")}
              </span>
            </div>
          </div>
        )}

        <div className="stack gap-2 mt-4">
          <Switch
            checked={wireless}
            onChange={setWireless}
            label={t("Kablosuz hata ayıklamayı kapat")}
            hint={t("Kablosuz hata ayıklama her açılışta yeni bir port açar; kapatınca ADB tümden düşer.")}
          />
          <Switch
            checked={usb}
            onChange={setUsb}
            label={t("USB hata ayıklamayı kapat")}
            hint={t("Kişi açık kalmasını istemediyse kapatın. Bu komut genelde bağlantıyı anında düşürür.")}
          />
        </div>

        <div className="row gap-2 wrap mt-3">
          <div style={{ width: 130 }}>
            <Field label={t("Kontrol edilecek port")}>
              <Input value={checkPort} onChange={(e) => setCheckPort(e.target.value)} />
            </Field>
          </div>
          <Button size="sm" variant="ghost" icon="search" loading={probeBusy} onClick={probe}>
            
            {t("Portu dene")}
          </Button>
          {portState && (
            <Badge tone={portState.open ? "danger" : "ok"}>
              {portState.open ? t("açık") : t("kapalı")}
            </Badge>
          )}
        </div>
        {portState && <p className="small muted">{portState.detail}</p>}
      </Card>

      {report && (
        <Card
          title={report.port_open ? t("Port hâlâ açık") : t("ADB kapandı ve doğrulandı")}
          tone={report.port_open ? "danger" : "ok"}
          subtitle={report.port_detail}
          actions={
            <Button size="sm" variant="ghost" onClick={() => setReport(null)}>
              
              {t("Gizle")}
            </Button>
          }
        >
          <Steps steps={report.steps} />
          <div className="mt-3">
            <p className="small muted">
              {t("Sıra: kapat → portu bu bilgisayardan dene → TV'yi yeniden başlat → portun hâlâ kapalı olduğunu doğrula. Yalnızca aracın “kapalı” demesi kanıttır.")}
            </p>
            <div className="row gap-2 mt-2">
              <Button size="sm" variant="soft" icon="refresh" onClick={() => go("dashboard")}>
                {t("Yeniden başlat")}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                icon="plug"
                onClick={() => {
                  setReport(null);
                  go("connect");
                }}
              >
                {t("Yeniden bağlan")}
              </Button>
            </div>
          </div>
        </Card>
      )}

      <div className="grid grid-2">
        <Card
          title="Private DNS"
          subtitle={t("Reklam ve izleme alan adlarını TV genelinde susturur")}
          actions={<Badge tone={dns?.mode === "off" ? "muted" : "ok"}>{dns?.mode ?? "?"}</Badge>}
        >
          <Field label={t("Mod")}>
            <Select
              value={mode}
              onChange={(e) => setMode(e.target.value)}
              options={[
                { value: "off", label: t("Kapalı") },
                { value: "opportunistic", label: t("Fırsatçı (sağlayıcı desteklıyorsa)") },
                { value: "hostname", label: t("Özel sağlayıcı (hostname)") },
              ]}
            />
          </Field>
          {mode === "hostname" && (
            <Field label={t("Sağlayıcı")} hint={t("Ör. dns.adguard-dns.com, dns.nextdns.io")}>
              <Input value={specifier} onChange={(e) => setSpecifier(e.target.value)} />
            </Field>
          )}
          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="primary"
              icon="globe"
              loading={dnsBusy}
              disabled={mode === "hostname" && !specifier.trim()}
              onClick={applyDns}
            >
              
              {t("Uygula")}
            </Button>
            <span className="small muted">
              {dns?.specifier ? t("şu an: {specifier}", { specifier: dns.specifier }) : t("sağlayıcı seçilmedi")}
            </span>
          </div>
          {dnsSteps.length > 0 && <Steps steps={dnsSteps} />}
          <p className="tiny muted mt-3">
            {t("Private DNS bazı uygulamalarda sorun çıkarabilir. Sorun olursa “Kapalı”ya alın. Bu app DNS kurmaz; yalnızca TV'nin kendi ayarını değiştirir.")}
          </p>
        </Card>

        <Card title={t("Neden önemli?")} subtitle={t("Kurallar ve geri dönüş")}>
          <div className="steps">
            <div className="step">
              <p>
                <Prose text="**Paketler silinmez.** Yalnızca `pm disable-user` kullanılır; her biri `pm enable` ile geri döner." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Çekirdek paketler korunur.** Sistem arayüzü, izin denetleyicisi, paket kurucu ve modül katmanları reddedilir." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Fabrika uygulamaları açılmaz.** Test menüleri bazı kartlarda geri alınamayan bir mod açıyor." />
              </p>
            </div>
            <div className="step">
              <p>
                <Prose text="**Home korunur.** Tek Home uygulaması kapatılamaz; yeni başlatıcı doğrulanmadan stok başlatıcıya dokunulmaz." />
              </p>
            </div>
          </div>
        </Card>
      </div>

      <Card title={t("Kapatma sonrası kontrol")} subtitle={t("Bu komutları elle de çalıştırabilirsiniz")}>
        <Code>{`adb -s ${conn?.target ?? "<tv ip>:5555"} shell settings put global adb_wifi_enabled 0
adb -s ${conn?.target ?? "<tv ip>:5555"} shell settings put global adb_enabled 0
adb disconnect ${conn?.target ?? "<tv ip>:5555"}

# Bu bilgisayardan port kapalı mı?
nc -z ${conn?.host ?? "<tv ip>"} 5555 && echo AÇIK || echo kapalı`}</Code>
        <p className="tiny muted mt-2">
          <Prose text="Kablosuz hata ayıklama portu her açılışta değişir; `adb mdns services` ile `_adb-tls-connect._tcp` satırına bakın." />
        </p>
      </Card>

      {!conn?.connected && (
        <Card>
          <Empty icon="plug" title={t("TV bağlı değil")} text={t("ADB'yi kapatmak için önce bağlanın.")} />
        </Card>
      )}

      {closing && <Spinner label={t("Kapatılıyor ve doğrulanıyor…")} />}

      <Confirm
        open={askClose}
        title={t("ADB kapatılsın mı?")}
        tone="danger"
        confirmLabel={t("Kapat ve doğrula")}
        busy={closing}
        onCancel={() => setAskClose(false)}
        onConfirm={close}
        body={
          <>
            <p>
              {t("{wireless} ve {usb} {what}", {
                wireless: wireless
                  ? t("Kablosuz hata ayıklama")
                  : t("Kablosuz hata ayıklama (kapalı bırakılacak)"),
                usb: usb ? t("USB hata ayıklama") : t("USB hata ayıklama (kapalı bırakılacak)"),
                what: t(
                  "ayarları değiştirilecek. Ardından bağlantı düşecek ve port bu bilgisayardan denenip sonuç gösterilecek.",
                ),
              })}
            </p>
            <p className="small muted">
              {t("TV'ye tekrar erişmek isterseniz Geliştirici seçeneklerinden hata ayıklamayı yeniden açmanız gerekir.")}
            </p>
          </>
        }
      />
    </div>
  );
}
