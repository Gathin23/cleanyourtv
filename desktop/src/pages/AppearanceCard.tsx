import { Card, Icon } from "../ui";
import { t, type LangMode } from "../i18n";
import type { ThemeMode } from "../prefs";

type Choice<T extends string> = { value: T; label: string; hint?: string };

function ChoiceRow<T extends string>({
  legend,
  value,
  options,
  onChange,
}: {
  legend: string;
  value: T;
  options: Choice<T>[];
  onChange: (next: T) => void;
}) {
  return (
    <div className="col gap-2">
      <span className="field-label">{legend}</span>
      <div className="choice-row">
        {options.map((option) => (
          <button
            key={option.value}
            className={`choice ${value === option.value ? "on" : ""}`}
            onClick={() => onChange(option.value)}
            title={option.hint}
            aria-pressed={value === option.value}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/**
 * Language and light/dark. Both start on "system", which is what a new install should do: an
 * English laptop opens in English, a Turkish one in Turkish, and a light desktop gets the light
 * window. The choice is saved with the rest of the settings by the card around it.
 */
export default function AppearanceCard({
  locale,
  theme,
  systemLang,
  systemTheme,
  onLocale,
  onTheme,
}: {
  locale: LangMode;
  theme: ThemeMode;
  systemLang: string;
  systemTheme: string;
  onLocale: (next: LangMode) => void;
  onTheme: (next: ThemeMode) => void;
}) {
  return (
    <Card
      title={t("Görünüm")}
      subtitle={t("Dil ve tema. İkisi de varsayılan olarak bu bilgisayarın ayarını izler.")}
      actions={<Icon name="eye" size={17} />}
    >
      <div className="grid grid-2 gap-4">
        <ChoiceRow<LangMode>
          legend={t("Arayüz dili")}
          value={locale}
          onChange={onLocale}
          options={[
            { value: "system", label: t("Sistem"), hint: t("Bu bilgisayarın dili") },
            { value: "tr", label: t("Türkçe") },
            { value: "en", label: "English" },
          ]}
        />
        <ChoiceRow<ThemeMode>
          legend={t("Tema")}
          value={theme}
          onChange={onTheme}
          options={[
            { value: "system", label: t("Sistem"), hint: t("Bu bilgisayarın açık/koyu ayarı") },
            { value: "dark", label: t("Koyu") },
            { value: "light", label: t("Açık") },
          ]}
        />
      </div>
      <p className="tiny muted mt-3">
        {t("Şu an: {lang} · {theme} tema.", {
          lang: locale === "system" ? `${t("sistem")} (${systemLang})` : locale.toUpperCase(),
          theme:
            theme === "system"
              ? `${t("sistem")} (${systemTheme === "dark" ? t("Koyu") : t("Açık")})`
              : theme === "dark"
                ? t("Koyu")
                : t("Açık"),
        })}
      </p>
      <p className="tiny muted">
        {t("Seçtiğiniz an uygulanır ve kaydedilir; ayrıca Kaydet'e basmanız gerekmez.")}
      </p>
    </Card>
  );
}
