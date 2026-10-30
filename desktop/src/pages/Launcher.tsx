import { useState } from "react";
import { Tabs } from "../ui";
import StatusPage from "./launcher/Status";
import ThemePage from "./launcher/Theme";
import PreviewPage from "./launcher/Preview";
import ConfigXmlPage from "./launcher/ConfigXml";
import StringsPage from "./launcher/Strings";
import BuildPage from "./launcher/Build";
import { ThemeStore } from "./launcher/theme-store";

const tabs = [
  { id: "status", label: "Durum & Home", icon: "home" },
  { id: "theme", label: "Tema (anında)", icon: "sparkle" },
  { id: "preview", label: "Önizleme", icon: "eye" },
  { id: "config", label: "config.xml", icon: "file" },
  { id: "strings", label: "Selamlama & metinler", icon: "edit" },
  { id: "build", label: "Derle & kur", icon: "box" },
];

export default function Launcher() {
  const [tab, setTab] = useState("status");
  return (
    // Tema formu ve önizleme aynı değerleri okur; sekme değişince düzenlenenler kaybolmaz.
    <ThemeStore>
      <div className="stack gap-4">
        <Tabs items={tabs} value={tab} onChange={setTab} />
        {tab === "status" ? (
          <StatusPage go={setTab} />
        ) : tab === "theme" ? (
          <ThemePage go={setTab} />
        ) : tab === "preview" ? (
          <PreviewPage go={setTab} />
        ) : tab === "config" ? (
          <ConfigXmlPage go={setTab} />
        ) : tab === "strings" ? (
          <StringsPage go={setTab} />
        ) : (
          <BuildPage goTab={setTab} />
        )}
      </div>
    </ThemeStore>
  );
}
