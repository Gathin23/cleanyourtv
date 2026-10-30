import { useEffect, useMemo, useState } from "react";
import { open as openDialog, save as saveDialog } from "@tauri-apps/plugin-dialog";
import * as api from "../api";
import type { Entry, Listing, Root } from "../api";
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
  Spinner,
  TextArea,
  bytes,
  useToast,
} from "../ui";
import { t } from "../i18n";

const defaultPath = "/sdcard";

function parent(path: string) {
  const parts = path.split("/").filter(Boolean);
  parts.pop();
  return `/${parts.join("/")}` || "/";
}

export default function Files() {
  const toast = useToast();
  const [path, setPath] = useState(defaultPath);
  const [draft, setDraft] = useState(defaultPath);
  const [listing, setListing] = useState<Listing | null>(null);
  const [roots, setRoots] = useState<Root[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [editor, setEditor] = useState<{
    path: string;
    text: string;
    original: string;
    size: number;
    binary: boolean;
  } | null>(null);
  const [mkdirOpen, setMkdirOpen] = useState(false);
  const [mkdirName, setMkdirName] = useState("");
  const [confirmDelete, setConfirmDelete] = useState<Entry | null>(null);

  const open = async (next: string) => {
    setLoading(true);
    try {
      const result = await api.fsList(next);
      setListing(result);
      setPath(result.path);
      setDraft(result.path);
    } catch (e) {
      toast.error(e, t("Klasör okunamadı"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    open(defaultPath);
    api
      .fsRoots()
      .then(setRoots)
      .catch(() => setRoots([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const crumbs = useMemo(() => {
    const parts = path.split("/").filter(Boolean);
    const out = [{ label: "/", path: "/" }];
    let acc = "";
    for (const p of parts) {
      acc += `/${p}`;
      out.push({ label: p, path: acc });
    }
    return out;
  }, [path]);

  const readFile = async (entry: Entry) => {
    setBusy(entry.path);
    try {
      const file = await api.fsRead(entry.path);
      setEditor({
        path: file.path,
        text: file.text,
        original: file.text,
        size: file.size,
        binary: file.binary,
      });
    } catch (e) {
      toast.error(e, t("Dosya açılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const saveFile = async () => {
    if (!editor) return;
    setBusy("save");
    try {
      await api.fsWrite(editor.path, editor.text);
      toast.push("ok", t("Dosya yazıldı"), editor.path);
      setEditor({ ...editor, original: editor.text });
      await open(path);
    } catch (e) {
      toast.error(e, t("Yazılamadı"));
    } finally {
      setBusy(null);
    }
  };

  const pull = async (entry: Entry) => {
    const file = await saveDialog({
      title: t("TV'den bilgisayara kaydet"),
      defaultPath: entry.name,
    });
    if (typeof file !== "string") return;
    setBusy(entry.path);
    try {
      const out = await api.fsPull(entry.path, file);
      toast.push("ok", t("İndirildi"), `${out.path} · ${bytes(out.size)} · md5 ${out.md5.slice(0, 8)}`);
    } catch (e) {
      toast.error(e, t("İndirilemedi"));
    } finally {
      setBusy(null);
    }
  };

  const push = async () => {
    const file = await openDialog({ multiple: false, directory: false, title: t("Gönderilecek dosya") });
    if (typeof file !== "string") return;
    const name = file.split(/[\\/]/).pop() || "dosya";
    const remote = path === "/" ? `/${name}` : `${path}/${name}`;
    setBusy("push");
    try {
      const out = await api.fsPush(file, remote, true);
      toast.push(
        out.verified ? "ok" : "error",
        out.verified ? t("Gönderildi ve doğrulandı") : t("Gönderildi ama md5 tutmadı"),
        `${out.path} · ${bytes(out.size)}`,
      );
      await open(path);
    } catch (e) {
      toast.error(e, t("Gönderilemedi"));
    } finally {
      setBusy(null);
    }
  };

  const install = async (entry: Entry) => {
    setBusy(entry.path);
    try {
      const folder = await openDialog({
        directory: true,
        multiple: false,
        title: t("APK'yı bilgisayara indir, sonra kur"),
      });
      if (typeof folder !== "string") return;
      const pulled = await api.fsPullTo(entry.path, folder);
      const out = await api.apkInstall(pulled.local);
      if (out.ok) toast.push("ok", t("Kuruldu"), entry.name);
      else toast.push("error", t("Kurulamadı"), out.output.slice(-300));
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(null);
    }
  };

  const remove = async (entry: Entry) => {
    setBusy(entry.path);
    try {
      await api.fsDelete(entry.path);
      toast.push("ok", "Silindi", entry.name);
      setConfirmDelete(null);
      await open(path);
    } catch (e) {
      toast.error(e, "Silinemedi");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="stack gap-4">
      <Card
        title={t("TV dosyaları")}
        subtitle={listing ? t("{n} öğe · {free} boş", { n: listing.entries.length, free: listing.free }) : t("Yükleniyor…")}
        actions={
          <>
            <Button size="sm" variant="ghost" icon="refresh" onClick={() => open(path)}>
              
              {t("Yenile")}
            </Button>
            <Button size="sm" variant="ghost" icon="upload" loading={busy === "push"} onClick={push}>
              {t("Dosya gönder")}
            </Button>
            <Button size="sm" variant="soft" icon="plus" onClick={() => setMkdirOpen(true)}>
              {t("Klasör")}
            </Button>
          </>
        }
      >
        <div className="row gap-2">
          <Button
            size="sm"
            variant="ghost"
            icon="arrowUp"
            title={t("Üst klasör")}
            disabled={path === "/"}
            onClick={() => open(parent(path))}
          >
            {t("Üst")}
          </Button>
          <div className="grow">
            <Input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && open(draft)}
              className="mono"
              placeholder="/sdcard"
            />
          </div>
          <Button size="sm" variant="soft" onClick={() => open(draft)}>
            
            {t("Git")}
          </Button>
        </div>

        <div className="crumbs mt-2">
          {crumbs.map((c, i) => (
            <span key={c.path} className="row gap-1">
              {i > 0 && <span className="crumb-sep">/</span>}
              <button
                className={`crumb ${i === crumbs.length - 1 ? "current" : ""}`}
                onClick={() => open(c.path)}
              >
                {c.label}
              </button>
            </span>
          ))}
        </div>

        <div className="row gap-2 wrap mt-2">
          {roots.map((r) => (
            <button
              key={r.path}
              className={`chip ${path === r.path ? "on" : ""}`}
              disabled={!r.readable}
              title={r.readable ? r.path : t("{path} okunamıyor (izin yok)", { path: r.path })}
              onClick={() => open(r.path)}
            >
              {r.label}
            </button>
          ))}
        </div>
      </Card>

      <Card pad={false}>
        {loading ? (
          <div style={{ padding: 24 }}>
            <Spinner label={t("Klasör okunuyor…")} />
          </div>
        ) : !listing || listing.entries.length === 0 ? (
          <Empty
            icon="folder"
            title={t("Klasör boş")}
            text={t("Bu klasörde okunabilen bir dosya yok, ya da izinler göstermiyor.")}
          />
        ) : (
          <div className="table-wrap" style={{ border: "none", borderRadius: 0 }}>
            <div className="table-scroll" style={{ maxHeight: "58vh" }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>{t("Ad")}</th>
                    <th style={{ width: 100 }}>{t("Boyut")}</th>
                    <th style={{ width: 150 }}>{t("Değişti")}</th>
                    <th style={{ width: 110 }}>{t("İzin")}</th>
                    <th style={{ width: 170 }} />
                  </tr>
                </thead>
                <tbody>
                  {listing.entries.map((e) => (
                    <tr
                      key={e.path}
                      className={`file-row ${e.dir ? "dir" : ""}`}
                      onDoubleClick={() => (e.dir ? open(e.path) : readFile(e))}
                    >
                      <td>
                        <div className="file-name">
                          <Icon
                            name={e.dir ? "folder" : e.name.endsWith(".apk") ? "box" : "file"}
                            size={17}
                            className={e.dir ? "folder" : undefined}
                          />
                          <span className="mono small" title={e.path}>
                            {e.name}
                          </span>
                          {e.link && <Badge tone="muted">→ {e.link}</Badge>}
                        </div>
                      </td>
                      <td className="small muted nowrap">{e.dir ? "—" : bytes(e.size)}</td>
                      <td className="small muted nowrap">{e.modified}</td>
                      <td className="mono tiny muted">{e.perms}</td>
                      <td>
                        <div className="actions">
                          {e.dir ? (
                            <Button size="sm" variant="ghost" onClick={() => open(e.path)}>
                              {t("Dosyayı aç")}
                            </Button>
                          ) : (
                            <>
                              <Button
                                variant="bare"
                                size="sm"
                                title={t("Düzenle")}
                                loading={busy === e.path}
                                onClick={() => readFile(e)}
                              >
                                <Icon name="edit" size={15} />
                              </Button>
                              <Button
                                variant="bare"
                                size="sm"
                                title={t("Bilgisayara indir")}
                                onClick={() => pull(e)}
                              >
                                <Icon name="download" size={15} />
                              </Button>
                              {e.name.endsWith(".apk") && (
                                <Button variant="bare" size="sm" title={t("Kur")} onClick={() => install(e)}>
                                  <Icon name="box" size={15} />
                                </Button>
                              )}
                            </>
                          )}
                          <Button
                            variant="bare"
                            size="sm"
                            title={t("Sil")}
                            onClick={() => setConfirmDelete(e)}
                          >
                            <Icon name="trash" size={15} />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>

      <Card title={t("Nereye bakılır?")} subtitle={t("Yaygın klasörler")}>
        <div className="grid grid-3">
          <div className="stat">
            <span>/sdcard</span>
            <strong className="small mono">{t("İndirilenler, resimler")}</strong>
            <em>/sdcard/Download</em>
          </div>
          <div className="stat">
            <span>{t("Uygulama verisi")}</span>
            <strong className="small mono">/sdcard/Android/data</strong>
            <em>{t("tema dosyaları: com.gathin.tvhome/files")}</em>
          </div>
          <div className="stat">
            <span>{t("Kabuk alanı")}</span>
            <strong className="small mono">/data/local/tmp</strong>
            <em>{t("APK ve geçici dosyalar")}</em>
          </div>
        </div>
      </Card>

      <Modal
        open={!!editor}
        wide
        title={editor?.path.split("/").pop() ?? ""}
        subtitle={editor?.path}
        onClose={() => setEditor(null)}
        footer={
          <>
            <span className="small muted grow">
              {editor ? `${bytes(editor.size)} · ${editor.text.length} karakter` : ""}
            </span>
            <Button variant="ghost" onClick={() => setEditor(null)}>
              
              {t("Kapat")}
            </Button>
            <Button
              variant="primary"
              icon="check"
              loading={busy === "save"}
              disabled={!editor || editor.text === editor.original}
              onClick={saveFile}
            >
              
              {t("Kaydet")}
            </Button>
          </>
        }
      >
        {editor?.binary && (
          <div className="card card-warn" style={{ padding: 12, marginBottom: 12 }}>
            <div className="row gap-2">
              <Icon name="alert" size={16} />
              <span className="small">
                {t("Bu dosya ikili görünüyor. Metin olarak kaydetmek onu bozabilir; önce indirin.")}
              </span>
            </div>
          </div>
        )}
        <TextArea
          value={editor?.text ?? ""}
          onChange={(e) => editor && setEditor({ ...editor, text: e.target.value })}
          style={{ minHeight: 360 }}
          spellCheck={false}
        />
        <p className="tiny muted mt-2">
          {t("Kaydet, dosyayı TV'deki yerine yazar. Yedeği yoksa önce indirmeniz önerilir.")}
        </p>
      </Modal>

      <Modal
        open={mkdirOpen}
        title={t("Yeni klasör")}
        subtitle={path}
        onClose={() => setMkdirOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setMkdirOpen(false)}>
              {t("Vazgeç")}
            </Button>
            <Button
              variant="primary"
              onClick={async () => {
                if (!mkdirName.trim()) return;
                try {
                  await api.fsMkdir(`${path === "/" ? "" : path}/${mkdirName.trim()}`);
                  toast.push("ok", t("Klasör oluşturuldu"), mkdirName);
                  setMkdirName("");
                  setMkdirOpen(false);
                  await open(path);
                } catch (e) {
                  toast.error(e);
                }
              }}
            >
              {t("Oluştur")}
            </Button>
          </>
        }
      >
        <Field label={t("Klasör adı")}>
          <Input
            value={mkdirName}
            onChange={(e) => setMkdirName(e.target.value)}
            placeholder={t("Yeni klasör")}
            autoFocus
          />
        </Field>
      </Modal>

      <Modal
        open={!!confirmDelete}
        title={t("Silinsin mi?")}
        subtitle={confirmDelete?.path}
        onClose={() => setConfirmDelete(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(null)}>
              {t("Vazgeç")}
            </Button>
            <Button
              variant="danger"
              icon="trash"
              loading={busy === confirmDelete?.path}
              onClick={() => confirmDelete && remove(confirmDelete)}
            >
              
              {t("Sil")}
            </Button>
          </>
        }
      >
        <p className="small">
          {confirmDelete?.dir
            ? t("Klasör ve içindeki her şey silinecek. Bu işlem geri alınamaz.")
            : t("Dosya silinecek. Bu işlem geri alınamaz.")}
        </p>
        <Code className="mt-2">{confirmDelete?.path ?? ""}</Code>
      </Modal>
    </div>
  );
}
