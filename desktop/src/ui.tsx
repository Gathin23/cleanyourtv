import { t } from "./i18n";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";

/* ------------------------------------------------------------------ icons */

const paths: Record<string, ReactNode> = {
  plug: (
    <>
      <path d="M9 3v4M15 3v4" />
      <path d="M6.5 7h11v4a5.5 5.5 0 0 1-11 0V7Z" />
      <path d="M12 16.5V21" />
    </>
  ),
  tv: (
    <>
      <rect x="3" y="4" width="18" height="12.5" rx="2.5" />
      <path d="M8.5 21h7M12 16.5V21" />
    </>
  ),
  grid: (
    <>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.8" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.8" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.8" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.8" />
    </>
  ),
  broom: (
    <>
      <path d="m14.5 4.5 5 5" />
      <path d="M13.8 6.2 5 15v4h4l8.8-8.8" />
      <path d="M9 15h4" />
    </>
  ),
  folder: (
    <>
      <path d="M3.5 7.5A2 2 0 0 1 5.5 5.5h4l2 2.2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-10Z" />
    </>
  ),
  home: (
    <>
      <path d="M4 10.5 12 4l8 6.5" />
      <path d="M6 9.8V20h12V9.8" />
      <path d="M10 20v-5h4v5" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.5 19 6v5.5c0 4.3-2.8 7.6-7 9-4.2-1.4-7-4.7-7-9V6l7-2.5Z" />
      <path d="m9.2 12.2 2 2 3.8-4" />
    </>
  ),
  settings: (
    <>
      <path d="M4 7h10M18 7h2M4 17h2M10 17h10" />
      <circle cx="16" cy="7" r="2.2" />
      <circle cx="8" cy="17" r="2.2" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 11a8 8 0 1 0-1.6 5.5" />
      <path d="M20 4.5V11h-6" />
    </>
  ),
  x: <path d="M6 6l12 12M18 6 6 18" />,
  check: <path d="m4.5 12.5 5 5 10-11" />,
  alert: (
    <>
      <path d="M12 3.8 21 20H3L12 3.8Z" />
      <path d="M12 9v5M12 17.2v.2" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5M12 7.8v.2" />
    </>
  ),
  chevron: <path d="m9 5 7 7-7 7" />,
  down: <path d="m5 9 7 7 7-7" />,
  download: (
    <>
      <path d="M12 3.5v11M7.5 10l4.5 4.5 4.5-4.5" />
      <path d="M4.5 20h15" />
    </>
  ),
  upload: (
    <>
      <path d="M12 20.5v-11M7.5 14 12 9.5l4.5 4.5" />
      <path d="M4.5 4h15" />
    </>
  ),
  trash: (
    <>
      <path d="M4.5 7h15M9 7V4.5h6V7M6.5 7l1 13h9l1-13" />
      <path d="M10 11v5M14 11v5" />
    </>
  ),
  edit: (
    <>
      <path d="M4 20h4L20 8l-4-4L4 16v4Z" />
      <path d="m14.5 5.5 4 4" />
    </>
  ),
  play: <path d="M8 5.5v13l11-6.5L8 5.5Z" />,
  stop: <rect x="6" y="6" width="12" height="12" rx="2" />,
  camera: (
    <>
      <path d="M4 8.5h3l1.5-2h7L17 8.5h3v11H4v-11Z" />
      <circle cx="12" cy="13.5" r="3.2" />
    </>
  ),
  power: (
    <>
      <path d="M12 3.5v8" />
      <path d="M7 6.4a7.5 7.5 0 1 0 10 0" />
    </>
  ),
  key: (
    <>
      <circle cx="8" cy="12" r="4" />
      <path d="M12 12h9M17.5 12v3.5M20 12v2.5" />
    </>
  ),
  wifi: (
    <>
      <path d="M3.5 9.5a13 13 0 0 1 17 0M6.5 13a8.5 8.5 0 0 1 11 0M9.5 16.3a4 4 0 0 1 5 0" />
      <path d="M12 20v.2" />
    </>
  ),
  arrowLeft: <path d="M20 12H5M11 6l-6 6 6 6" />,
  arrowUp: <path d="M12 20V5M6 11l6-6 6 6" />,
  box: (
    <>
      <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" />
      <path d="m4 7.5 8 4.5 8-4.5M12 12v9" />
    </>
  ),
  file: (
    <>
      <path d="M6 3.5h7l5 5V20H6V3.5Z" />
      <path d="M13 3.5V9h5" />
    </>
  ),
  copy: (
    <>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M15 5.5H6a2 2 0 0 0-2 2V16" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  terminal: (
    <>
      <rect x="3" y="4.5" width="18" height="15" rx="2.5" />
      <path d="m7.5 10 3 2.5-3 2.5M13 15h4" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="10" width="14" height="10" rx="2.5" />
      <path d="M8.5 10V7.5a3.5 3.5 0 0 1 7 0V10" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17M12 3.5c2.5 2.4 2.5 14.6 0 17M12 3.5c-2.5 2.4-2.5 14.6 0 17" />
    </>
  ),
  layers: (
    <>
      <path d="m12 3 9 5-9 5-9-5 9-5Z" />
      <path d="m3 13 9 5 9-5" />
    </>
  ),
  link: (
    <>
      <path d="M9.5 14.5 14.5 9.5" />
      <path d="M8 12 6.2 13.8a3.9 3.9 0 0 0 5.5 5.5L13.5 17" />
      <path d="M16 12l1.8-1.8a3.9 3.9 0 0 0-5.5-5.5L10.5 6.5" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  sparkle: (
    <>
      <path d="M12 3.5 13.6 9l5.5 1.6-5.5 1.6L12 17.7l-1.6-5.5L4.9 10.6 10.4 9 12 3.5Z" />
      <path d="M18.5 15.5 19 17l1.5.5L19 18l-.5 1.5L18 18l-1.5-.5L18 17l.5-1.5Z" />
    </>
  ),
  sliders: (
    <>
      <path d="M4 7h7M15 7h5M4 12h3M11 12h9M4 17h9M17 17h3" />
      <circle cx="13" cy="7" r="2" />
      <circle cx="9" cy="12" r="2" />
      <circle cx="15" cy="17" r="2" />
    </>
  ),
  eye: (
    <>
      <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
      <circle cx="12" cy="12" r="2.8" />
    </>
  ),
  input: (
    <>
      <rect x="3" y="6" width="13" height="9" rx="2" />
      <path d="M16 10.5h3.5M19.5 10.5 17.8 8.8M19.5 10.5l-1.7 1.7M8 18.5h8" />
    </>
  ),
};

export function Icon({
  name,
  size = 20,
  className = "",
}: {
  name: keyof typeof paths | string;
  size?: number;
  className?: string;
}) {
  return (
    <svg
      className={`icon ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] ?? paths.info}
    </svg>
  );
}

/* ------------------------------------------------------------------ buttons */

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "ghost" | "soft" | "danger" | "bare";
  size?: "sm" | "md" | "lg";
  icon?: string;
  loading?: boolean;
};

export function Button({
  variant = "soft",
  size = "md",
  icon,
  loading,
  children,
  className = "",
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`btn btn-${variant} btn-${size} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <span className="btn-spin" /> : icon ? <Icon name={icon} size={size === "sm" ? 15 : 18} /> : null}
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ layout */

export function Card({
  title,
  subtitle,
  actions,
  children,
  className = "",
  tone,
  pad = true,
}: {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  tone?: "warn" | "danger" | "ok" | "accent";
  pad?: boolean;
}) {
  return (
    <section className={`card ${tone ? `card-${tone}` : ""} ${className}`}>
      {(title || actions) && (
        <header className="card-head">
          <div>
            {title && <h3>{title}</h3>}
            {subtitle && <p className="muted small">{subtitle}</p>}
          </div>
          {actions && <div className="row gap-2">{actions}</div>}
        </header>
      )}
      <div className={pad ? "card-body" : ""}>{children}</div>
    </section>
  );
}

export function Badge({
  tone = "neutral",
  children,
  title,
}: {
  tone?: "neutral" | "ok" | "warn" | "danger" | "accent" | "muted";
  children: ReactNode;
  title?: string;
}) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="spinner-row">
      <span className="spinner" />
      {label && <span className="muted small">{label}</span>}
    </span>
  );
}

export function Empty({
  icon = "box",
  title,
  text,
  action,
}: {
  icon?: string;
  title: string;
  text?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon name={icon} size={26} />
      </span>
      <strong>{title}</strong>
      {text && <p className="muted">{text}</p>}
      {action}
    </div>
  );
}

/* ------------------------------------------------------------------ fields */

export function Field({
  label,
  hint,
  children,
  htmlFor,
}: {
  label?: ReactNode;
  hint?: ReactNode;
  children: ReactNode;
  htmlFor?: string;
}) {
  return (
    <label className="field" htmlFor={htmlFor}>
      {label && <span className="field-label">{label}</span>}
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className="input" {...props} />;
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className="input textarea" {...props} />;
}

export function Select({
  options,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & {
  options: { value: string; label: string }[];
}) {
  return (
    <select className="input select" {...rest}>
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

export function Switch({
  checked,
  onChange,
  label,
  hint,
  disabled,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label?: ReactNode;
  hint?: ReactNode;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <div className={`switch-row ${disabled ? "is-disabled" : ""}`}>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        className={`switch ${checked ? "on" : ""}`}
        onClick={() => !disabled && onChange(!checked)}
        disabled={disabled}
      >
        <span />
      </button>
      {(label || hint) && (
        <label htmlFor={id} className="switch-text">
          {label && <span>{label}</span>}
          {hint && <span className="field-hint">{hint}</span>}
        </label>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ modal */

export function Modal({
  open,
  title,
  subtitle,
  onClose,
  children,
  footer,
  wide,
}: {
  open: boolean;
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal-wrap" role="dialog" aria-modal="true">
      <div className="modal-scrim" onClick={onClose} />
      <div className={`modal ${wide ? "modal-wide" : ""}`}>
        <header className="modal-head">
          <div>
            <h3>{title}</h3>
            {subtitle && <p className="muted small">{subtitle}</p>}
          </div>
          <Button variant="bare" size="sm" onClick={onClose} aria-label={t("Kapat")}>
            <Icon name="x" size={18} />
          </Button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-foot">{footer}</footer>}
      </div>
    </div>
  );
}

export function Confirm({
  open,
  title,
  body,
  confirmLabel,
  tone = "primary",
  onCancel,
  onConfirm,
  busy,
}: {
  open: boolean;
  title: string;
  body: ReactNode;
  confirmLabel?: string;
  tone?: "primary" | "danger";
  onCancel: () => void;
  onConfirm: () => void;
  busy?: boolean;
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            {t("Vazgeç")}
          </Button>
          <Button variant={tone === "danger" ? "danger" : "primary"} onClick={onConfirm} loading={busy}>
            {confirmLabel ?? t("Onayla")}
          </Button>
        </>
      }
    >
      <div className="stack">{body}</div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ toast */

type Toast = { id: number; tone: "ok" | "error" | "info"; title: string; text?: string };
type ToastCtx = {
  push: (tone: Toast["tone"], title: string, text?: string) => void;
  error: (e: unknown, title?: string) => void;
};

const toastContext = createContext<ToastCtx | null>(null);

export function useToast() {
  const ctx = useContext(toastContext);
  if (!ctx) throw new Error("useToast outside ToastProvider");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const idRef = useRef(0);

  const push = useCallback((tone: Toast["tone"], title: string, text?: string) => {
    const id = ++idRef.current;
    setItems((list) => [...list, { id, tone, title, text }]);
    window.setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 6000);
  }, []);

  const error = useCallback(
    (e: unknown, title = t("Bir şeyler ters gitti")) => {
      const text =
        typeof e === "string"
          ? e
          : e instanceof Error
            ? e.message
            : typeof e === "object" && e && "message" in e
              ? String((e as { message: unknown }).message)
              : JSON.stringify(e);
      push("error", title, text);
    },
    [push],
  );

  const value = useMemo(() => ({ push, error }), [push, error]);
  return (
    <toastContext.Provider value={value}>
      {children}
      <div className="toasts">
        {items.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            <Icon name={t.tone === "ok" ? "check" : t.tone === "error" ? "alert" : "info"} size={18} />
            <div>
              <strong>{t.title}</strong>
              {t.text && <p>{t.text}</p>}
            </div>
          </div>
        ))}
      </div>
    </toastContext.Provider>
  );
}

/* ------------------------------------------------------------------ misc */

export function Tabs({
  items,
  value,
  onChange,
}: {
  items: { id: string; label: string; icon?: string }[];
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((item) => (
        <button
          key={item.id}
          role="tab"
          aria-selected={value === item.id}
          className={`tab ${value === item.id ? "active" : ""}`}
          onClick={() => onChange(item.id)}
        >
          {item.icon && <Icon name={item.icon} size={16} />}
          {t(item.label)}
        </button>
      ))}
    </div>
  );
}

export function Code({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <pre className={`code ${className}`}>{children}</pre>;
}

export function Progress({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div className="progress">
      <span style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Row({ label, value }: { label: ReactNode; value: ReactNode }) {
  return (
    <div className="kv">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}

export function bytes(n: number) {
  if (!n) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let v = n;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function ago(secondsOrDate: string) {
  return secondsOrDate;
}
