import { useEffect, useMemo, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { open as openDialog, save as saveDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../api";
import type { BatchReport, ExampleList, Package, PackageList, Progress } from "../api";
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
  Progress as Bar,
  Spinner,
  Switch,
  useToast,
} from "../ui";
import { CopyLine, kindText, PackageName, VerdictBadge, verdictLabel } from "../parts";
import { Prose } from "../parts";
import { t } from "../i18n";

type Filter = "all" | "recommended" | "askfirst" | "danger" | "refused" | "disabled";

const filters: { id: Filter; label: string }[] = [
  { id: "all", label: "Hepsi" },
  { id: "recommended", label: "Önerilen" },
  { id: "askfirst", label: "Önce sor" },
  { id: "danger", label: "Riskli" },
  { id: "refused", label: "Korunuyor" },
  { id: "disabled", label: "Kapalı" },
];

export default function Debloat() {
  const { repo, conn, info, go } = useApp();
  const toast = useToast();

  const [list, setList] = useState<PackageList | null>(null);
  const [examples, setExamples] = useState<ExampleList[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<Filter>("all");

  const [backups, setBackups] = useState<api.BackupEntry[]>([]);
  const [backedUp, setBackedUp] = useState(false);
  const [backupBusy, setBackupBusy] = useState(false);
  const [lastBackup, setLastBackup] = useState<api.BackupResult | null>(null);

  const [runOpen, setRunOpen] = useState(false);
  const [running, setRunning] = useState(false);
  const [log, setLog] = useState<Progress[]>([]);
  const [report, setReport] = useState<BatchReport | null>(null);
  const [allow, setAllow] = useState<Set<string>>(new Set());
  const [allowSetupwraith, setAllowSetupwraith] = useState(false);

  const [detail, setDetail] = useState<{ name: string; text: string; busy: boolean } | null>(null);
  const [scripts, setScripts] = useState<api.RestoreScripts | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setList(await api.packagesList());
    } catch (e) {
      toast.error(e, t("Paket listesi okunamadı"));
    } finally {
      setLoading(false);
    }
    try {
      const [saved, hasBackup] = await Promise.all([api.backupList(), api.backupState()]);
      setBackups(saved);
      setBackedUp(hasBackup);
    } catch {
      /* an empty backup folder is not an error */
    }
  };

  useEffect(() => {
    load();
    if (repo?.path) {
      api
        .examplesList(repo.path)
        .then(setExamples)
        .catch(() => setExamples([]));
    }
    const un = listen<Progress>("debloat://progress", (e) => {
      setLog((prev) => {
        const rest = prev.filter((p) => p.index !== e.payload.index);
        return [...rest, e.payload].sort((a, b) => a.index - b.index);
      });
    });
    return () => {
      un.then((f) => f()).catch(() => {});
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repo?.path]);

  const packages = list?.packages ?? [];

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return packages.filter((p) => {
      if (filter === "disabled" && !p.disabled) return false;
      if (filter !== "all" && filter !== "disabled" && p.verdict !== filter) return false;
      if (!q) return true;
      return p.name.toLowerCase().includes(q) || p.reason.toLowerCase().includes(q);
    });
  }, [packages, filter, search]);

  const selectedPackages = useMemo(
    () => packages.filter((p) => selected.has(p.name)),
    [packages, selected],
  );
  const questions = selectedPackages.filter(
    (p) => p.verdict === "askfirst" || p.verdict === "danger",
  );
  const setupwraith = selected.has("com.google.android.setupwraith");

  const toggle = (name: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  const backup = async () => {
    setBackupBusy(true);
    try {
      const result = await api.backupCreate();
      setLastBackup(result);
      setBackedUp(true);
      setBackups(await api.backupList());
      toast.push(
        "ok",
        t("Yedek alındı"),
        t("{total} paket, {disabled} kapalı paket · {path}", { total: result.packages, disabled: result.disabled.length, path: result.path }),
      );
    } catch (e) {
      toast.error(e, t("Yedek alınamadı"));
    } finally {
      setBackupBusy(false);
    }
  };

  const applyExample = (ex: ExampleList) => {
    const byName = new Map(packages.map((p) => [p.name, p]));
    const next = new Set(selected);
    let added = 0;
    let missing = 0;
    let blocked = 0;
    for (const name of ex.packages) {
      const p = byName.get(name);
      if (!p) {
        missing += 1;
        continue;
      }
      if (p.disabled) continue;
      if (p.verdict === "refused") {
        blocked += 1;
        continue;
      }
      next.add(name);
      added += 1;
    }
    setSelected(next);
    toast.push(
      "info",
      ex.folder,
      t("{n} paket seçildi{missing}{blocked}.", {
        n: added,
        missing: missing ? `, ${t("{n} bu TV'de yok", { n: missing })}` : "",
        blocked: blocked ? `, ${t("{n} korunuyor", { n: blocked })}` : "",
      }),
    );
  };

  const importFile = async () => {
    const file = await openDialog({
      multiple: false,
      directory: false,
      title: t("Debloat listesi seç (.txt)"),
      filters: [{ name: "Liste", extensions: ["txt"] }],
    });
    if (typeof file !== "string") return;
    try {
      const imported = await api.importList(file);
      const names = imported.raw
        .split("\n")
        .map((l) => l.trim())
        .filter((l) => l && !l.startsWith("#"))
        .map((l) => l.split("#")[0].trim().split(/\s+/)[0]);
      applyExample({
        folder: imported.name,
        tv: "",
        os: "",
        brand: "",
        notes: "",
        path: file,
        packages: names,
        raw: imported.raw,
      });
    } catch (e) {
      toast.error(e, t("Liste okunamadı"));
    }
  };

  const openRun = () => {
    if (selected.size === 0) return;
    if (!backedUp) {
      toast.push(
        "error",
        t("Önce yedek alın"),
        t("Temizlik, geri dönüşü olan bir yedek olmadan başlamaz."),
      );
      return;
    }
    setLog([]);
    setReport(null);
    setAllow(new Set());
    setAllowSetupwraith(false);
    setRunOpen(true);
  };

  const runBatch = async () => {
    setRunning(true);
    setLog([]);
    try {
      const result = await api.batchDisable([...selected], {
        allow: [...allow],
        allowSetupwraith,
        screenshot: true,
      });
      setReport(result);
      await load();
      const blocked = result.skipped.length;
      toast.push(
        result.aborted ? "error" : "ok",
        result.aborted ? "Parti durduruldu" : "Temizlik tamam",
        t("{n} paket kapatıldı{daha}.", {
          n: result.disabled_now,
          daha: blocked ? `, ${t("{n} atlandı", { n: blocked })}` : "",
        }),
      );
    } catch (e) {
      toast.error(e, t("Temizlik yapılamadı"));
    } finally {
      setRunning(false);
    }
  };

  const exportScript = async () => {
    const names = (report?.results ?? [])
      .filter((r) => r.ok)
      .map((r) => r.name);
    if (!names.length) {
      toast.push("info", t("Betik için paket yok"));
      return;
    }
    try {
      const bundle = await api.restoreScripts(names, conn?.host ?? "");
      setScripts(bundle);
    } catch (e) {
      toast.error(e);
    }
  };

  const saveScript = async (kind: "sh" | "ps1") => {
    if (!scripts) return;
    const file = await saveDialog({
      title: t("Geri yükleme betiğini kaydet"),
      defaultPath: `cleanyourtv-restore.${kind}`,
    });
    if (typeof file !== "string") return;
    try {
      await api.saveText(file, kind === "sh" ? scripts.sh : scripts.ps1);
      toast.push("ok", "Betik kaydedildi", file);
    } catch (e) {
      toast.error(e, t("Betik yazılamadı"));
    }
  };

  const openDetail = async (p: Package) => {
    setDetail({ name: p.name, text: "", busy: true });
    try {
      const text = await api.packageDetails(p.name);
      setDetail({ name: p.name, text, busy: false });
    } catch (e) {
      setDetail({ name: p.name, text: api.message(e), busy: false });
    }
  };

  const toggleOne = async (p: Package) => {
    try {
      await api.packageSetEnabled(p.name, p.disabled);
      toast.push("ok", p.disabled ? t("Açıldı") : t("Kapatıldı"), p.name);
      await load();
    } catch (e) {
      toast.error(e, t("Değiştirilemedi"));
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
      <Card
        tone={backedUp ? "ok" : "warn"}
        title={backedUp ? t("Yedek hazır") : t("Önce yedek alın")}
        subtitle={
          backedUp
            ? t("Bu oturumda bir yedek yazıldı{zaten}.", {
                zaten: lastBackup ? `: ${lastBackup.path}` : "",
              })
            : t("Paket listesi, kapalı paketler, getprop ve ayarlar bilgisayara yazılır. Temizlik bundan önce başlamaz.")
        }
        actions={
          <Button
            variant={backedUp ? "soft" : "primary"}
            size="sm"
            icon="download"
            loading={backupBusy}
            onClick={backup}
          >
            {backedUp ? t("Yeniden yedekle") : t("Yedek al")}
          </Button>
        }
      >
        <div className="row gap-3 wrap">
          <span className="small muted">
            {backups.length
              ? `Diskte ${backups.length} yedek var, en yenisi ${backups[0].created}.`
              : t("Diskte henüz yedek yok.")}
          </span>
          {lastBackup && (
            <Badge tone="ok">
              {lastBackup.files.length}  {t("dosya ·")} {lastBackup.disabled.length} {t("kapalı paket")}
            </Badge>
          )}
          <div className="grow" />
          <Button size="sm" variant="ghost" icon="refresh" onClick={load}>
            
            {t("Paketleri yenile")}
          </Button>
          {backups.length > 0 && (
            <Button size="sm" variant="ghost" onClick={() => go("settings")}>
              {t("Yedekler ve geri yükleme")}
            </Button>
          )}
        </div>
      </Card>

      <div className="grid grid-2">
        <Card
          title={t("Hazır listeler")}
          subtitle={
            repo?.path
              ? t("{n} test edilmiş TV listesi · {path}", { n: repo.examples, path: repo.path })
              : t("Repo klasörü bulunamadı; kendi listenizi içe aktarabilirsiniz")
          }
          actions={
            <Button size="sm" variant="ghost" icon="upload" onClick={importFile}>
              {t("Liste içe aktar")}
            </Button>
          }
        >
          {examples.length === 0 ? (
            <Empty
              icon="box"
              title={t("Hazır liste yok")}
              text={t("Ayarlar'dan CleanYourTV klasörünü gösterin ya da .txt bir liste içe aktarın.")}
            />
          ) : (
            <div className="table-wrap">
              <div className="table-scroll" style={{ maxHeight: 320 }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>TV</th>
                      <th>Android</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {examples.map((ex) => (
                      <tr key={ex.folder}>
                        <td>
                          <div className="pkg-name">
                            <span>{ex.tv || ex.folder}</span>
                            <span className="sub">{ex.notes}</span>
                          </div>
                        </td>
                        <td className="nowrap small muted">{ex.os}</td>
                        <td className="right">
                          <Button size="sm" variant="soft" onClick={() => applyExample(ex)}>
                            {ex.packages.length}  {t("paket")}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </Card>

        <Card title={t("Bu TV'ye uygun öneriler")} subtitle={t("Kurallara göre güvenli olanlar")}>
          <div className="row gap-2 wrap">
            <Button
              size="sm"
              variant="primary"
              icon="sparkle"
              onClick={() => {
                const next = new Set(selected);
                let n = 0;
                for (const p of packages) {
                  if (p.verdict === "recommended" && !p.disabled) {
                    next.add(p.name);
                    n += 1;
                  }
                }
                setSelected(next);
                toast.push("info", t("{n} önerilen paket seçildi", { n }));
              }}
            >
              {t("Önerilenleri seç")}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                const next = new Set(selected);
                for (const p of packages) {
                  if (p.kind === "telemetry" || p.kind === "ads") next.add(p.name);
                }
                setSelected(next);
                toast.push("info", t("Telemetri ve reklam paketleri seçildi"));
              }}
            >
              
              {t("Telemetri + reklam")}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>
              {t("Seçimi temizle")}
            </Button>
          </div>
          <div className="stack gap-2 mt-3">
            <div className="row gap-2 small muted">
              <Icon name="shield" size={16} />
              <span>
                {t("Çekirdek paketler, kurulum sihirbazı ve tek Home uygulaması asla kapatılmaz.")}
              </span>
            </div>
            <div className="row gap-2 small muted">
              <Icon name="info" size={16} />
              <span>
                {list?.launcher_installed
                  ? t("Temiz başlatıcı kurulu. Home devri için Başlatıcı sayfasını kullanın.")
                  : t("Temiz başlatıcı kurulu değil; stok başlatıcı silinmez, yalnızca Home devredilir.")}
              </span>
            </div>
            <div className="row gap-2 small muted">
              <Icon name="clock" size={16} />
              <span>
                <Prose text="Her paket tek tek gönderilir, sonra `sync` çalışır ve liste doğrulanır." />
              </span>
            </div>
          </div>
        </Card>
      </div>

      <Card
        title={t("Paketler ({count})", { count: packages.length })}
        subtitle={t("{disabled} paket şu an kapalı · {selected} seçili", { disabled: packages.filter((p) => p.disabled).length, selected: selected.size })}
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
              placeholder={t("paket adı veya açıklama ara")}
            />
          </div>
          <div className="chips">
            {filters.map((f) => (
              <button
                key={f.id}
                className={`chip ${filter === f.id ? "on" : ""}`}
                onClick={() => setFilter(f.id)}
              >
                {t(f.label)}
              </button>
            ))}
          </div>
        </div>

        <div className="table-wrap mt-3">
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th>{t("Paket")}</th>
                  <th style={{ width: 110 }}>{t("Tür")}</th>
                  <th style={{ width: 110 }}>{t("Kural")}</th>
                  <th style={{ width: 86 }}>{t("Durum")}</th>
                  <th style={{ width: 90 }} />
                </tr>
              </thead>
              <tbody>
                {visible.map((p) => {
                  const locked = p.disabled || p.verdict === "refused";
                  return (
                    <tr key={p.name} className={p.disabled ? "off" : ""}>
                      <td>
                        <button
                          className={`check ${selected.has(p.name) ? "on" : ""}`}
                          disabled={locked}
                          onClick={() => toggle(p.name)}
                          aria-label={t("{name} seç", { name: p.name })}
                          title={
                            p.verdict === "refused"
                              ? t("Bu paket korunuyor, kapatılamaz")
                              : p.disabled
                                ? t("Zaten kapalı")
                                : t("Seç")
                          }
                        >
                          <Icon name="check" size={12} />
                        </button>
                      </td>
                      <td>
                        <PackageName name={p.name} reason={p.reason} />
                      </td>
                      <td>
                        <span className="small muted">{kindText(p.kind)}</span>
                      </td>
                      <td>
                        <VerdictBadge verdict={p.verdict} />
                      </td>
                      <td>
                        {p.disabled ? (
                          <Badge tone="muted">{t("kapalı")}</Badge>
                        ) : p.system ? (
                          <Badge tone="neutral">{t("sistem")}</Badge>
                        ) : (
                          <Badge tone="accent">{t("kullanıcı")}</Badge>
                        )}
                      </td>
                      <td>
                        <div className="actions">
                          <Button variant="bare" size="sm" title={t("Ayrıntı")} onClick={() => openDetail(p)}>
                            <Icon name="info" size={15} />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
        {visible.length === 0 && (
          <Empty icon="search" title={t("Eşleşen paket yok")} text={t("Filtreyi ya da aramayı değiştirin.")} />
        )}

        {selected.size > 0 && (
          <div className="selection-bar">
            <span className="count">
              <strong>{selected.size}</strong> {t("paket seçildi")}
              {questions.length > 0 && ` · ${questions.length} tanesi onay istiyor`}
            </span>
            {!backedUp && <Badge tone="warn">{t("yedek yok")}</Badge>}
            <div className="grow" />
            <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>
              
              {t("Temizle")}
            </Button>
            <Button size="sm" variant="primary" icon="broom" onClick={openRun}>
              
              {t("Uygula")}
            </Button>
          </div>
        )}
      </Card>

      <Modal
        open={runOpen}
        wide
        title={
          report
            ? t("Sonuç")
            : running
              ? t("Uygulanıyor")
              : t("{n} paket kapatılacak", { n: selected.size })
        }
        subtitle={
          report
            ? t("{done} kapatıldı · {skipped} atlandı", { done: report.disabled_now, skipped: report.skipped.length })
            : running
              ? t("Her paket tek tek gönderiliyor, sonra doğrulanıyor.")
              : t("Önce yedek alındı. Bu adım geri alınabilir: paketler silinmez, yalnızca kapatılır.")
        }
        onClose={() => {
          if (running) return;
          setRunOpen(false);
        }}
        footer={
          report ? (
            <>
              <Button variant="ghost" onClick={exportScript}>
                {t("Geri yükleme betiği")}
              </Button>
              <Button variant="ghost" onClick={() => setRunOpen(false)}>
                
                {t("Kapat")}
              </Button>
              <Button
                variant="soft"
                icon="refresh"
                onClick={() => {
                  setRunOpen(false);
                  go("dashboard");
                }}
              >
                {t("Yeniden başlat")}
              </Button>
            </>
          ) : running ? null : (
            <>
              <Button variant="ghost" onClick={() => setRunOpen(false)}>
                {t("Vazgeç")}
              </Button>
              <Button variant="primary" icon="broom" onClick={runBatch}>
                
                {t("Kapat")}
              </Button>
            </>
          )
        }
      >
        {report ? (
          <div className="stack gap-3">
            {report.aborted && (
              <div className="card card-danger" style={{ padding: 14 }}>
                <strong>{t("Parti durduruldu")}</strong>
                <p className="small muted mt-2">{report.aborted}</p>
              </div>
            )}
            <div className="row gap-2 wrap">
              <Badge tone="ok">{report.disabled_now} {t("kapatıldı")}</Badge>
              <Badge tone="warn">{report.skipped.length} {t("atlandı")}</Badge>
              {report.still_enabled.length > 0 && (
                <Badge tone="danger">{report.still_enabled.length} {t("hâlâ açık")}</Badge>
              )}
            </div>
            {report.results.length > 0 && (
              <div className="table-wrap">
                <div className="table-scroll" style={{ maxHeight: 240 }}>
                  <table className="table">
                    <thead>
                      <tr>
                        <th>{t("Paket")}</th>
                        <th style={{ width: 90 }}>{t("Sonuç")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.results.map((r) => (
                        <tr key={r.name}>
                          <td className="mono small">{r.name}</td>
                          <td>
                            {r.ok ? <Badge tone="ok">{t("kapandı")}</Badge> : <Badge tone="danger">{t("olmadı")}</Badge>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
            {report.skipped.length > 0 && (
              <div>
                <p className="small muted mb-2">{t("Atlananlar ve nedeni")}</p>
                <div className="progress-log">
                  {report.skipped.map((s) => (
                    <div className="progress-line skip" key={s.name}>
                      <span className="mono pkg">{s.name}</span>
                      <span className="muted small">— {s.reason}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <p className="small muted">
              <Prose text="TV'nin bir sonraki açılışta yeni durumu okuması için yeniden başlatın; `sync` zaten çalıştı." />
            </p>
          </div>
        ) : running ? (
          <div className="stack gap-3">
            <Bar
              value={log.filter((l) => l.phase === "done" || l.phase === "failed").length}
              max={log[0]?.total || selected.size}
            />
            <div className="progress-log">
              {log.length === 0 && <Spinner label={t("İlk paket gönderiliyor…")} />}
              {log.map((l) => (
                <div
                  key={l.index}
                  className={`progress-line ${
                    l.phase === "done" ? "ok" : l.phase === "failed" ? "fail" : l.phase === "running" ? "run" : "skip"
                  }`}
                >
                  <span className="mono pkg">
                    {l.index}/{l.total}
                  </span>
                  <span className="mono pkg">{l.name}</span>
                  <span className="muted small">{l.phase}</span>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="stack gap-4">
            <div className="row gap-2 wrap">
              <Badge tone="ok">{selected.size - questions.length} {t("güvenli paket")}</Badge>
              {questions.length > 0 && (
                <Badge tone="warn">{questions.length}  {t("onay gerekiyor")}</Badge>
              )}
            </div>

            {questions.length > 0 && (
              <div>
                <p className="small muted mb-2">
                  {t("Aşağıdaki paketler aile tarafından kullanılabilir. Yalnızca bu TV'de kullanmadığınızı bildiklerinizi işaretleyin.")}
                </p>
                <div className="list">
                  {questions.map((p) => (
                    <div className="list-row" key={p.name}>
                      <button
                        className={`check ${allow.has(p.name) ? "on" : ""}`}
                        onClick={() =>
                          setAllow((prev) => {
                            const next = new Set(prev);
                            if (next.has(p.name)) next.delete(p.name);
                            else next.add(p.name);
                            return next;
                          })
                        }
                        aria-label={`${p.name} onayla`}
                      >
                        <Icon name="check" size={12} />
                      </button>
                      <div className="col grow" style={{ minWidth: 0 }}>
                        <span className="mono small">{p.name}</span>
                        <span className="sub">{p.reason}</span>
                      </div>
                      <VerdictBadge verdict={p.verdict} />
                    </div>
                  ))}
                </div>
              </div>
            )}

            {setupwraith && (
              <div className="card card-warn" style={{ padding: 14 }}>
                <Switch
                  checked={allowSetupwraith}
                  onChange={setAllowSetupwraith}
                  label={t("Kurulum sihirbazını yine de kapat")}
                  hint={t("Wi-Fi ağının sahibi bu paketse kapatmak TV'yi ağdan ve ADB'den düşürür. Uygulama bunu önce kontrol eder ve sahibiyse yine de reddeder.")}
                />
              </div>
            )}

            <div>
              <p className="small muted mb-2">{t("Önizleme")}</p>
              <div className="progress-log">
                {selectedPackages.map((p) => (
                  <div className="progress-line" key={p.name}>
                    <span className="mono pkg">{p.name}</span>
                    <span className="muted small">{verdictLabel[p.verdict]}</span>
                    {p.verdict === "refused" && <Badge tone="muted">{t("atlanacak")}</Badge>}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!detail}
        wide
        title={detail?.name ?? ""}
        subtitle="dumpsys package"
        onClose={() => setDetail(null)}
        footer={
          <>
            <Button
              variant="ghost"
              onClick={async () => {
                if (!detail) return;
                try {
                  await api.packageForceStop(detail.name);
                  toast.push("ok", t("Durduruldu"), detail.name);
                } catch (e) {
                  toast.error(e);
                }
              }}
            >
              
              {t("Durdur")}
            </Button>
            <Button
              variant="ghost"
              onClick={async () => {
                if (!detail) return;
                try {
                  await api.packageLaunch(detail.name);
                  toast.push("ok", t("Başlatılıyor"), detail.name);
                } catch (e) {
                  toast.error(e, t("Başlatılamadı"));
                }
              }}
            >
              {t("Başlat")}
            </Button>
            <Button variant="ghost" onClick={() => setDetail(null)}>
              {t("Kapat")}
            </Button>
          </>
        }
      >
        {detail?.busy ? (
          <Spinner label={t("Paket okunuyor…")} />
        ) : (
          <Code className="tall">{detail?.text ?? ""}</Code>
        )}
        {detail && (
          <div className="row gap-2 mt-3">
            <Button
              size="sm"
              variant="soft"
              onClick={() => {
                const p = packages.find((x) => x.name === detail.name);
                if (p) toggleOne(p);
              }}
            >
              {t("Kapat / aç")}
            </Button>
            <span className="tiny muted">
              {t("Kapatma işlemi güvenlik kurallarından geçer; çekirdek paketler reddedilir.")}
            </span>
          </div>
        )}
      </Modal>

      <Modal
        open={!!scripts}
        wide
        title={t("Geri yükleme betiği")}
        subtitle={t("Bu oturumda kapatılan paketleri geri açar; hiçbir şey silinmez.")}
        onClose={() => setScripts(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => saveScript("sh")}>
              restore.sh
            </Button>
            <Button variant="ghost" onClick={() => saveScript("ps1")}>
              restore.ps1
            </Button>
            <Button variant="soft" onClick={() => setScripts(null)}>
              {t("Kapat")}
            </Button>
          </>
        }
      >
        <div className="row spread">
          <span className="small muted">
            {t("Çalıştırma:")} <span className="mono">TV_HOST=&lt;tv ip&gt; sh restore.sh</span>
          </span>
          <CopyLine text={scripts?.sh ?? ""} />
        </div>
        <Code className="tall mt-2">{scripts?.sh ?? ""}</Code>
      </Modal>

      {info?.sdk !== undefined && info.sdk < 26 && (
        <Card tone="warn" title={t("Bu TV launcher için çok eski")} subtitle={`SDK ${info.sdk}`}>
          <p className="small muted">
            {t("Temiz başlatıcı en az Android 8.0 (SDK 26) ister. Debloat tarafı çalışır, başlatıcı kurulamaz.")}
          </p>
        </Card>
      )}
    </div>
  );
}
