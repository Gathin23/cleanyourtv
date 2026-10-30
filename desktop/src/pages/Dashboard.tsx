import { useCallback, useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import * as api from "../api";
import type { Shot } from "../api";
import { useApp } from "../app-context";
import {
  Badge,
  Button,
  Card,
  Code,
  Confirm,
  Field,
  Icon,
  Input,
  Row,
  Spinner,
  Switch,
  useToast,
} from "../ui";
import { t } from "../i18n";
import { Prose } from "../parts";

const dpad = [
  { key: "DPAD_UP", icon: "arrowUp", label: "Yukarı" },
  { key: "DPAD_LEFT", icon: "arrowLeft", label: "Sol" },
  { key: "DPAD_CENTER", icon: "check", label: "Tamam" },
  { key: "DPAD_RIGHT", icon: "chevron", label: "Sağ" },
  { key: "DPAD_DOWN", icon: "down", label: "Aşağı" },
];

export default function Dashboard() {
  const { info, conn, refreshInfo, go } = useApp();
  const toast = useToast();
  const [shot, setShot] = useState<Shot | null>(null);
  const [shotBusy, setShotBusy] = useState(false);
  const [saveShot, setSaveShot] = useState(true);
  const [text, setText] = useState("");
  const [countdown, setCountdown] = useState<number | null>(null);
  const [askReboot, setAskReboot] = useState(false);
  const [rebootBusy, setRebootBusy] = useState(false);
  const [mark, setMark] = useState<{ x: number; y: number; long: boolean } | null>(null);
  const [refused, setRefused] = useState("");
  const [lastTap, setLastTap] = useState("");
  const press = useRef<{ timer: number; long: boolean; u: number; v: number } | null>(null);

  /**
   * A protected surface (Live TV, an HDMI input, a DRM player) makes `screencap` fail on the TV.
   * The backend names the app in front; the message here says so in Turkish instead of showing
   * adb's exit code, which tells the person nothing.
   */
  const shotError = (e: unknown) => {
    const raw = typeof e === "string" ? e : e instanceof Error ? e.message : String(e);
    if (!/screenshot/i.test(raw)) {
      return { title: t("Ekran görüntüsü alınamadı"), text: raw, app: "" };
    }
    const app = /while (\S+) is in front/.exec(raw)?.[1] ?? "";
    return {
      title: t("TV ekran görüntüsünü vermiyor"),
      text: t("{front}Canlı TV, HDMI ve bazı oynatıcılar görüntüyü kopyalamaya izin vermez; bu TV'nin koruması, uygulamanın hatası değil. Home'a dönüp “Yenile”ye basın.", {
        front: app ? `${t("Şu an önde: {app}. ", { app })}` : "",
      }),
      app,
    };
  };

  const grab = useCallback(
    async (report = true) => {
      setShotBusy(true);
      try {
        setShot(await api.screenshot(saveShot));
        setRefused("");
      } catch (e) {
        const problem = shotError(e);
        setRefused(problem.app || problem.text);
        if (report) toast.push("info", problem.title, problem.text);
      } finally {
        setShotBusy(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [saveShot, toast],
  );

  useEffect(() => {
    grab();
    // Screenshot every page visit is enough; the Refresh button is there for the rest.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const un = listen<number>("reboot://countdown", (e) => setCountdown(e.payload));
    return () => {
      un.then((f) => f()).catch(() => {});
    };
  }, []);

  const key = async (name: string) => {
    try {
      await api.sendKey(name);
      window.setTimeout(grab, 550);
    } catch (e) {
      toast.error(e, t("Tuş gönderilemedi"));
    }
  };

  /** Where a click landed on the drawn picture, as fractions of the TV screen. */
  const pointAt = (e: React.PointerEvent<HTMLImageElement>) => {
    if (!shot) return null;
    const rect = e.currentTarget.getBoundingClientRect();
    // object-fit: contain leaves bars around the picture; only the picture is the TV.
    const scale = Math.min(rect.width / shot.width, rect.height / shot.height);
    const drawnW = shot.width * scale;
    const drawnH = shot.height * scale;
    const left = rect.left + (rect.width - drawnW) / 2;
    const top = rect.top + (rect.height - drawnH) / 2;
    const u = (e.clientX - left) / drawnW;
    const v = (e.clientY - top) / drawnH;
    if (u < 0 || u > 1 || v < 0 || v > 1) return null;
    const wrap = e.currentTarget.parentElement?.getBoundingClientRect();
    return {
      u,
      v,
      x: wrap ? e.clientX - wrap.left : 0,
      y: wrap ? e.clientY - wrap.top : 0,
    };
  };

  const tap = async (u: number, v: number, mode: "tap" | "long") => {
    try {
      const detail = await api.tap(u, v, mode);
      setLastTap(detail);
      // A refused screenshot here is expected on Live TV: say nothing, the card already shows it.
      window.setTimeout(() => grab(false), 650);
    } catch (e) {
      toast.error(e, t("Dokunma gönderilemedi"));
    }
  };

  const pressDown = (e: React.PointerEvent<HTMLImageElement>) => {
    if (e.button !== 0) return;
    // A tap is a click; holding the same spot for 600 ms is the long press the TV menus want.
    const point = pointAt(e);
    if (!point) return;
    e.currentTarget.setPointerCapture?.(e.pointerId);
    setMark({ x: point.x, y: point.y, long: false });
    const timer = window.setTimeout(() => {
      if (!press.current) return;
      press.current.long = true;
      setMark((m) => (m ? { ...m, long: true } : m));
      void tap(point.u, point.v, "long");
    }, 600);
    press.current = { timer, long: false, u: point.u, v: point.v };
  };

  const pressUp = () => {
    const pending = press.current;
    if (!pending) return;
    window.clearTimeout(pending.timer);
    press.current = null;
    if (!pending.long) void tap(pending.u, pending.v, "tap");
    window.setTimeout(() => setMark(null), 620);
  };

  const pressCancel = () => {
    if (!press.current) return;
    window.clearTimeout(press.current.timer);
    press.current = null;
    setMark(null);
  };

  const reboot = async () => {
    setRebootBusy(true);
    setCountdown(null);
    try {
      toast.push("info", t("Yeniden başlatılıyor"), "Yazmalar diske inene kadar bekleniyor.");
      const out = await api.reboot(true);
      toast.push("ok", t("Yeniden başlatma istendi"), out);
      setAskReboot(false);
    } catch (e) {
      toast.error(e, t("Yeniden başlatılamadı"));
    } finally {
      setRebootBusy(false);
      setCountdown(null);
    }
  };

  if (!info) {
    return (
      <Card>
        <Spinner label={t("TV okunuyor…")} />
      </Card>
    );
  }

  return (
    <div className="stack gap-5">
      <div className="grid grid-4">
        <div className="stat">
          <span>{t("Model")}</span>
          <strong title={info.model}>{info.model || "—"}</strong>
          <em>{info.manufacturer || info.brand}</em>
        </div>
        <div className="stat">
          <span>Android</span>
          <strong>{info.android || "—"}</strong>
          <em>SDK {info.sdk || "?"}</em>
        </div>
        <div className="stat">
          <span>{t("Depolama")}</span>
          <strong>{info.storage_free || "—"} {t("boş")}</strong>
          <em>{info.storage_total}  {t("toplam")}</em>
        </div>
        <div className="stat">
          <span>{t("Çalışma süresi")}</span>
          <strong>{info.uptime || "—"}</strong>
          <em className="ellipsis" title={info.build_id}>
            {info.build_id}
          </em>
        </div>
      </div>

      <div className="grid" style={{ gridTemplateColumns: "minmax(0, 1.5fr) minmax(300px, 1fr)" }}>
        <Card
          title={t("TV ekranı")}
          subtitle={`${info.screen || t("ekran")} · ${info.density || t("yoğunluk")}`}
          actions={
            <>
              <Switch checked={saveShot} onChange={setSaveShot} label={t("Diske kaydet")} />
              <Button size="sm" variant="ghost" icon="camera" onClick={() => grab()} loading={shotBusy}>
                
                {t("Yenile")}
              </Button>
            </>
          }
        >
          <div className="shot-wrap">
            {shotBusy && !shot ? (
              <span className="shot-empty">{t("Ekran alınıyor…")}</span>
            ) : shot ? (
              <>
                <img
                  src={`data:image/png;base64,${shot.png}`}
                  alt={t("TV ekranı")}
                  className="shot-tap"
                  title={t("Tıkla: TV'de o noktaya dokunur. Basılı tut: uzun basma.")}
                  onPointerDown={pressDown}
                  onPointerUp={pressUp}
                  onPointerCancel={pressCancel}
                  onPointerLeave={pressCancel}
                  onContextMenu={(e) => e.preventDefault()}
                  draggable={false}
                />
                {mark && (
                  <span
                    className={`shot-mark ${mark.long ? "long" : ""}`}
                    style={{ left: mark.x, top: mark.y }}
                  />
                )}
              </>
            ) : refused ? (
              <div className="shot-empty col gap-3" style={{ alignItems: "center", maxWidth: 430 }}>
                <Icon name="alert" size={22} />
                <strong>{t("Bu ekranın görüntüsü alınamıyor")}</strong>
                <span>
                  {t("Canlı TV, HDMI girişi ya da korumalı bir oynatıcı açık. TV görüntüsünü kopyalatmaz; bu bir arıza değil.")}
                </span>
                <Button
                  size="sm"
                  variant="soft"
                  icon="home"
                  onClick={async () => {
                    try {
                      await api.sendKey("HOME");
                    } catch (e) {
                      toast.error(e);
                      return;
                    }
                    window.setTimeout(() => grab(), 1400);
                  }}
                >
                  {t("Home'a dön ve tekrar al")}
                </Button>
              </div>
            ) : (
              <span className="shot-empty">
                {t("Ekran görüntüsü yok. Canlı TV ve bazı uygulamalar siyah kare döndürür; bu TV'nin korumasıdır, bir arıza değil.")}
              </span>
            )}
          </div>
          {shot?.path && <p className="tiny muted mt-2 mono ellipsis">{shot.path}</p>}
          {refused && shot && (
            <div className="card card-warn mt-2" style={{ padding: 10 }}>
              <div className="row gap-2 small">
                <Icon name="alert" size={15} />
                <span>
                  <Prose text="TV şu anda ekran görüntüsü vermiyor. Canlı TV, HDMI girişi veya korumalı bir oynatıcı açıkken bu normaldir: TV görüntüyü kopyalatmaz. Home'a dönüp **Yenile**'ye basın." />
                  {refused && <span className="mono tiny"> · {t("önde:")} {refused}</span>}
                </span>
              </div>
            </div>
          )}
          <div className="row gap-2 wrap mt-2" style={{ alignItems: "center" }}>
            <Badge tone={lastTap ? "ok" : "muted"}>
              {lastTap ? t("son dokunma: {point}", { point: lastTap }) : t("ekrana tıkla → TV'de dokun")}
            </Badge>
            <span className="tiny muted">
              {t("Tek tık dokunur, basılı tutmak (0,6 sn) uzun basar. Görüntü donmuş bir kare; dokunmadan sonra kendini yeniler.")}
            </span>
          </div>

          <div className="row gap-3 mt-3 wrap">
            <div className="dpad">
              {dpad.map((d) => (
                <button
                  key={d.key}
                  className={d.key === "DPAD_CENTER" ? "ok" : d.icon === "arrowUp" ? "up" : ""}
                  onClick={() => key(d.key)}
                  title={t(d.label)}
                  aria-label={t(d.label)}
                >
                  <Icon name={d.icon} size={17} />
                </button>
              ))}
            </div>
            <div className="col gap-2 grow" style={{ minWidth: 190 }}>
              <div className="row gap-2">
                <Button size="sm" variant="soft" onClick={() => key("BACK")}>
                  
                  {t("Geri")}
                </Button>
                <Button size="sm" variant="soft" onClick={() => key("HOME")}>
                  Home
                </Button>
                <Button size="sm" variant="soft" onClick={() => key("MENU")}>
                  {t("Menü")}
                </Button>
              </div>
              <div className="row gap-2 wrap">
                <Field label={t("Metin gönder")}>
                  <Input
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder={t("arama kutusuna yazılacak metin")}
                    onKeyDown={async (e) => {
                      if (e.key === "Enter" && text.trim()) {
                        try {
                          await api.sendText(text.trim());
                          setText("");
                          window.setTimeout(grab, 500);
                        } catch (err) {
                          toast.error(err);
                        }
                      }
                    }}
                  />
                </Field>
              </div>
              <p className="tiny muted">
                {t("Enjekte edilen tuş bir yardımcı uygulamadan geçiyorsa kanıt sayılmaz: gerçek kumandayla da deneyin.")}
              </p>
            </div>
          </div>
        </Card>

        <div className="stack gap-4">
          <Card
            title={t("Home ekranı")}
            tone={info.home.startsWith("com.gathin.tvhome") ? "ok" : undefined}
            actions={
              <Button size="sm" variant="ghost" icon="home" onClick={() => go("launcher")}>
                {t("Başlatıcı")}
              </Button>
            }
          >
            <Row label={t("Şu an Home")} value={<span className="mono small">{info.home || "—"}</span>} />
            <Row
              label={t("Temiz başlatıcı")}
              value={
                info.launcher_installed ? (
                  <Badge tone="ok">{t("kurulu")} {info.launcher_version}</Badge>
                ) : (
                  <Badge tone="warn">{t("kurulu değil")}</Badge>
                )
              }
            />
            <Row
              label={t("Öndeki pencere")}
              value={<span className="mono small ellipsis">{info.focused || "—"}</span>}
            />
            {info.home_candidates.length > 0 && (
              <div className="mt-2">
                <p className="tiny muted mb-2">{t("Home'u açabilen uygulamalar")}</p>
                <div className="col gap-1">
                  {info.home_candidates.map((c) => (
                    <span key={c} className="mono tiny muted ellipsis">
                      {c}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </Card>

          <Card title={t("Cihaz")} subtitle={conn?.target}>
            <Row label={t("Üretici")} value={info.manufacturer || "—"} />
            <Row label={t("Kart")} value={<span className="mono small">{info.board || "—"}</span>} />
            <Row label="ABI" value={<span className="mono small">{info.abi || "—"}</span>} />
            <Row label={t("Seri")} value={<span className="mono small">{info.serial || "—"}</span>} />
            <Row label={t("Ağ")} value={info.transports.join(", ") || "—"} />
            <Row
              label={t("Kablosuz hata ayıklama")}
              value={
                info.wireless_debugging === null ? (
                  <Badge tone="neutral">{t("bilinmiyor")}</Badge>
                ) : info.wireless_debugging ? (
                  <Badge tone="warn">{t("açık")}</Badge>
                ) : (
                  <Badge tone="ok">{t("kapalı")}</Badge>
                )
              }
            />
          </Card>

          <Card title={t("Güç")} subtitle={t("Yazmalar diske indikten sonra yeniden başlatır")}>
            {countdown !== null ? (
              <div className="center">
                <strong style={{ fontSize: 30 }}>{countdown}</strong>
                <p className="small muted">{t("saniye sonra yeniden başlıyor…")}</p>
              </div>
            ) : (
              <div className="row gap-2 wrap">
                <Button
                  size="sm"
                  variant="soft"
                  icon="refresh"
                  onClick={async () => {
                    await refreshInfo();
                    toast.push("ok", t("Cihaz bilgisi yenilendi"));
                  }}
                >
                  
                  {t("Bilgileri yenile")}
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  icon="arrowUp"
                  disabled={rebootBusy}
                  onClick={() => setAskReboot(true)}
                >
                  {t("Yeniden başlat")}
                </Button>
              </div>
            )}
          </Card>
        </div>
      </div>

      <Card title={t("TV üzerinde çalıştırılan komutlar")} subtitle={t("Sorun gidermek için")}>
        <Code>{`adb -s ${conn?.target ?? "<tv ip>:5555"} shell getprop ro.product.model
adb -s ${conn?.target ?? "<tv ip>:5555"} shell cmd package resolve-activity --brief \\
  -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s ${conn?.target ?? "<tv ip>:5555"} exec-out screencap -p > screen.png`}</Code>
      </Card>

      <Confirm
        open={askReboot}
        title={t("TV'yi yeniden başlat?")}
        tone="danger"
        confirmLabel={t("Yeniden başlat")}
        busy={rebootBusy}
        onCancel={() => setAskReboot(false)}
        onConfirm={reboot}
        body={
          <>
            <p>
              {t("Yeniden başlatma, paket durumlarının diske yazılması için yaklaşık 12 saniye bekler. TV bir dakika içinde geri gelir; ADB bağlantısı kendiliğinden kurulmaz,")}{" "}
              <strong>{t("Bağlan")}</strong> {t("ekranından tekrar bağlanın.")}
            </p>
            <p className="small muted">
              {t("Devam eden bir yedek ya da kurulum varsa önce onun bitmesini bekleyin.")}
            </p>
          </>
        }
      />
    </div>
  );
}
