import { useState } from "react";
import type { ReactNode } from "react";
import type { Kind, Step, Verdict } from "./api";
import { t } from "./i18n";
import { Badge, Icon } from "./ui";

/** A list of command steps, the shape every guarded backend call returns. */
export function Steps({ steps }: { steps: Step[] }) {
  return (
    <div className="list">
      {steps.map((s, i) => (
        <div className="list-row" key={`${s.label}-${i}`}>
          <span
            className="icon-wrap"
            style={{ color: s.ok ? "var(--ok)" : "var(--danger)" }}
          >
            <Icon name={s.ok ? "check" : "alert"} size={17} />
          </span>
          <div className="col grow" style={{ minWidth: 0 }}>
            <span className="title">{s.label}</span>
            {s.detail && (
              <span className="sub" style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                {s.detail}
              </span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

export const verdictLabel: Record<Verdict, string> = {
  recommended: "Önerilen",
  askfirst: "Önce sor",
  danger: "Riskli",
  refused: "Korunuyor",
};

export function VerdictBadge({ verdict }: { verdict: Verdict }) {
  const tone =
    verdict === "recommended"
      ? "ok"
      : verdict === "askfirst"
        ? "warn"
        : verdict === "danger"
          ? "danger"
          : "muted";
  return (
    <Badge tone={tone} title={verdict}>
      {t(verdictLabel[verdict])}
    </Badge>
  );
}

export const kindLabel: Record<Kind, string> = {
  core: "çekirdek",
  middleware: "ara katman",
  launcher: "başlatıcı",
  factory: "fabrika",
  telemetry: "telemetri",
  ads: "reklam",
  media: "medya",
  voice: "ses",
  update: "güncelleme",
  system: "sistem",
  vendor: "üretici",
  unknown: "bilinmiyor",
};

/** The Turkish label in `kindLabel`, in the language the window is set to. */
export function kindText(kind: Kind) {
  return t(kindLabel[kind]);
}

/**
 * A sentence that mentions a command, a file name or a path in the middle, written as one
 * translatable string: `code` becomes a monospace span and **emphasis** a bold one. One string per
 * paragraph keeps the sentence in one piece for the translator, so English word order is not cut
 * into fragments that cannot be reordered.
 */
export function Prose({ text }: { text: string }) {
  const parts = t(text).split(/(\*\*[^*]+\*\*|`[^`]+`)/);
  return (
    <>
      {parts.map((part, i) => {
        if (part.startsWith("**") && part.endsWith("**")) return <strong key={i}>{part.slice(2, -2)}</strong>;
        if (part.startsWith("`") && part.endsWith("`")) return <span className="mono" key={i}>{part.slice(1, -1)}</span>;
        return part;
      })}
    </>
  );
}

export function Blocker({ children }: { children: ReactNode }) {
  return (
    <div className="row gap-2 small" style={{ color: "var(--warn)", alignItems: "flex-start" }}>
      <Icon name="alert" size={16} />
      <div>{children}</div>
    </div>
  );
}

export function CopyLine({ text, label }: { text: string; label?: string }) {
  const [done, setDone] = useState(false);
  return (
    <button
      className="btn btn-bare btn-sm"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setDone(true);
          window.setTimeout(() => setDone(false), 1600);
        } catch {
          /* the webview may refuse the clipboard; the text is still selectable */
        }
      }}
      title={label ?? t("Kopyala")}
    >
      <Icon name={done ? "check" : "copy"} size={15} />
    </button>
  );
}

export function PackageName({ name, reason }: { name: string; reason?: string }) {
  return (
    <div className="pkg-name">
      <span className="mono">{name}</span>
      {reason && <span className="sub">{reason}</span>}
    </div>
  );
}
