import { createContext, useContext } from "react";
import type {
  AdbStatus,
  Connection,
  DeviceInfo,
  RepoInfo,
  Settings,
} from "./api";

export type Page =
  | "connect"
  | "dashboard"
  | "debloat"
  | "apps"
  | "files"
  | "launcher"
  | "tv"
  | "privacy"
  | "settings";

export type AppContextValue = {
  page: Page;
  go: (page: Page) => void;
  adb: AdbStatus | null;
  conn: Connection | null;
  info: DeviceInfo | null;
  settings: Settings | null;
  repo: RepoInfo | null;
  refreshAdb: () => Promise<void>;
  refreshConn: () => Promise<void>;
  refreshInfo: () => Promise<void>;
  refreshRepo: (path?: string) => Promise<void>;
  saveSettings: (settings: Settings) => Promise<void>;
};

export const AppContext = createContext<AppContextValue | null>(null);

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp outside AppContext");
  return ctx;
}
