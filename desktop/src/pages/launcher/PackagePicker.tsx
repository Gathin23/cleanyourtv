import { useEffect, useMemo, useState } from "react";
import * as api from "../../api";
import type { Package } from "../../api";
import { Button, Empty, Icon, Input, Modal, Spinner, useToast } from "../../ui";
import { t } from "../../i18n";

type Props = {
  open: boolean;
  title: string;
  subtitle?: string;
  /** Selected packages, in order when `ordered`. */
  value: string[];
  ordered?: boolean;
  onChange: (next: string[]) => void;
  onClose: () => void;
};

/**
 * Pick apps by name instead of typing package names: the TV's own package list, with a search box and,
 * for the dock, the order the owner wants them in.
 */
export default function PackagePicker({
  open,
  title,
  subtitle,
  value,
  ordered = false,
  onChange,
  onClose,
}: Props) {
  const toast = useToast();
  const [packages, setPackages] = useState<Package[] | null>(null);
  const [search, setSearch] = useState("");
  const [manual, setManual] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open || packages) return;
    setBusy(true);
    api
      .packagesList()
      .then((list) => setPackages(list.packages))
      .catch((e) => toast.error(e, t("Paket listesi okunamadı")))
      .finally(() => setBusy(false));
  }, [open, packages, toast]);

  const visible = useMemo(() => {
    const query = search.trim().toLowerCase();
    const all = packages ?? [];
    const installed = all.filter((p) => !p.disabled);
    if (!query) return installed;
    return installed.filter((p) => p.name.toLowerCase().includes(query));
  }, [packages, search]);

  const has = (name: string) => value.includes(name);

  const toggle = (name: string) => {
    if (has(name)) onChange(value.filter((v) => v !== name));
    else onChange(ordered ? [...value, name] : [...value, name]);
  };

  const move = (index: number, delta: number) => {
    const next = [...value];
    const to = index + delta;
    if (to < 0 || to >= next.length) return;
    [next[index], next[to]] = [next[to], next[index]];
    onChange(next);
  };

  return (
    <Modal
      open={open}
      wide
      title={title}
      subtitle={subtitle}
      onClose={onClose}
      footer={
        <>
          <span className="small muted grow">
            {t("{n} seçili", { n: value.length })}
            {ordered ? t(" · sıra dock'taki sıradır") : ""}
          </span>
          <Button variant="primary" onClick={onClose}>
            
            {t("Bitti")}
          </Button>
        </>
      }
    >
      <div className="row gap-3" style={{ alignItems: "flex-start" }}>
        <div className="col gap-3 grow" style={{ minWidth: 0 }}>
          <div className="search">
            <Icon name="search" size={16} />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("paket ara")}
            />
          </div>
          <div className="picker-list">
            {busy && !packages ? (
              <Spinner label={t("TV'nin paketleri okunuyor…")} />
            ) : visible.length === 0 ? (
              <Empty icon="search" title={t("Eşleşen paket yok")} text={t("Aramayı kısaltmayı deneyin.")} />
            ) : (
              visible.map((p) => (
                <button
                  key={p.name}
                  className={`picker-row ${has(p.name) ? "on" : ""}`}
                  onClick={() => toggle(p.name)}
                >
                  <span className={`picker-check ${has(p.name) ? "on" : ""}`}>
                    {has(p.name) && <Icon name="check" size={12} />}
                  </span>
                  <span className="col" style={{ minWidth: 0 }}>
                    <span className="mono small ellipsis">{p.name}</span>
                    <span className="tiny muted ellipsis">{p.kind}</span>
                  </span>
                </button>
              ))
            )}
          </div>
          <div className="row gap-2">
            <Input
              value={manual}
              onChange={(e) => setManual(e.target.value)}
              placeholder={t("listede yoksa paket adını yazın")}
            />
            <Button
              size="sm"
              variant="soft"
              icon="plus"
              onClick={() => {
                const name = manual.trim();
                if (!/^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$/.test(name)) {
                  toast.error(t("Bu bir paket adı değil: com.ornek.uygulama"));
                  return;
                }
                if (!has(name)) onChange([...value, name]);
                setManual("");
              }}
            >
              
              {t("Ekle")}
            </Button>
          </div>
        </div>

        <div className="col gap-2" style={{ width: 280, flex: "0 0 auto" }}>
          <span className="tiny muted">{t("Seçilenler")} {ordered ? t("(sırayla)") : ""}</span>
          {value.length === 0 ? (
            <p className="small muted">{t("Henüz paket seçilmedi.")}</p>
          ) : (
            value.map((name, i) => (
              <div key={name} className="picker-selected">
                {ordered && <span className="tiny muted mono">{i + 1}</span>}
                <span className="mono tiny ellipsis grow">{name}</span>
                {ordered && (
                  <>
                    <button className="icon-btn" title={t("Yukarı")} onClick={() => move(i, -1)}>
                      <Icon name="arrowUp" size={13} />
                    </button>
                    <button className="icon-btn" title={t("Aşağı")} onClick={() => move(i, 1)}>
                      <Icon name="down" size={13} />
                    </button>
                  </>
                )}
                <button
                  className="icon-btn"
                  title={t("Çıkar")}
                  onClick={() => onChange(value.filter((v) => v !== name))}
                >
                  <Icon name="x" size={13} />
                </button>
              </div>
            ))
          )}
          {value.length > 0 && (
            <Button size="sm" variant="ghost" icon="trash" onClick={() => onChange([])}>
              {t("Tümünü temizle")}
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
