import { invoke } from "@tauri-apps/api/core";

export type AdbStatus = {
  found: boolean;
  path: string;
  version: string;
  source: string;
  candidates: string[];
};

export type Connection = {
  connected: boolean;
  target: string;
  host: string;
  port: number;
};

export type DeviceLine = {
  serial: string;
  state: string;
  model: string;
  product: string;
  connection: string;
};

export type MdnsService = {
  name: string;
  kind: string;
  address: string;
};

export type ScanResult = {
  mdns: MdnsService[];
  devices: DeviceLine[];
  note: string;
};

export type DeviceInfo = {
  model: string;
  manufacturer: string;
  brand: string;
  device: string;
  board: string;
  android: string;
  sdk: number;
  build_id: string;
  fingerprint: string;
  serial: string;
  abi: string;
  uptime: string;
  storage_free: string;
  storage_total: string;
  screen: string;
  density: string;
  adb_secure: boolean;
  adb_enabled: boolean;
  wireless_debugging: boolean | null;
  developer_options: boolean;
  home: string;
  home_candidates: string[];
  focused: string;
  launcher_installed: boolean;
  launcher_version: string;
  transports: string[];
  usb_host_connected: boolean | null;
  private_dns_mode: string;
  private_dns_specifier: string;
};

export type Shot = {
  png: string;
  path: string;
  width: number;
  height: number;
};

export type PortState = { open: boolean; detail: string };

export type Step = { label: string; ok: boolean; detail: string };

export type CloseReport = {
  steps: Step[];
  port_open: boolean;
  port_detail: string;
  wireless_off: boolean | null;
  usb_off: boolean | null;
};

export type DnsState = { mode: string; specifier: string };

export type Settings = {
  adb_path: string | null;
  repo_path: string | null;
  backup_dir: string | null;
  last_host: string | null;
  last_port: number;
  ui_locale: string | null;
  /** `system`, `dark` or `light`; null follows the operating system. */
  ui_theme: string | null;
  confirm_before_reboot: boolean;
};

export type RepoInfo = {
  path: string | null;
  launcher: boolean;
  examples: number;
};

export type ExampleList = {
  folder: string;
  tv: string;
  os: string;
  brand: string;
  notes: string;
  path: string;
  packages: string[];
  raw: string;
};

export type RestoreScripts = { sh: string; ps1: string };

export type Kind =
  | "core"
  | "middleware"
  | "launcher"
  | "factory"
  | "telemetry"
  | "ads"
  | "media"
  | "voice"
  | "update"
  | "system"
  | "vendor"
  | "unknown";

export type Verdict = "recommended" | "askfirst" | "danger" | "refused";

export type Package = {
  name: string;
  apk: string;
  system: boolean;
  disabled: boolean;
  kind: Kind;
  verdict: Verdict;
  reason: string;
};

export type PackageList = {
  packages: Package[];
  disabled: number;
  total: number;
  launcher_installed: boolean;
};

export type ToggleResult = {
  name: string;
  disabled: boolean;
  verified: boolean;
  output: string;
  warning: string | null;
};

export type BatchItem = {
  name: string;
  ok: boolean;
  output: string;
  reason: string | null;
};

export type BatchReport = {
  results: BatchItem[];
  skipped: BatchItem[];
  aborted: string | null;
  disabled_now: number;
  still_enabled: string[];
};

export type Progress = {
  index: number;
  total: number;
  name: string;
  phase: string;
  ok: boolean;
  detail: string;
};

export type ExtractedApk = { local: string; size: number };

export type Manifest = {
  created: string;
  host: string;
  model: string;
  android: string;
  build: string;
  packages: number;
  disabled: string[];
  files: string[];
};

export type BackupResult = {
  path: string;
  disabled: string[];
  packages: number;
  files: string[];
};

export type BackupEntry = {
  path: string;
  created: string;
  host: string;
  model: string;
  android: string;
  disabled: number;
  packages: number;
};

export type RestoreItem = { name: string; ok: boolean; output: string };
export type RestoreReport = { results: RestoreItem[]; still_disabled: string[] };

export type Entry = {
  name: string;
  path: string;
  dir: boolean;
  size: number;
  perms: string;
  modified: string;
  link: string;
};

export type Listing = { path: string; entries: Entry[]; free: string; raw_empty: boolean };

export type FileText = {
  path: string;
  text: string;
  size: number;
  truncated: boolean;
  binary: boolean;
};

export type WriteResult = { path: string; size: number; detail: string };
export type LocalFile = { path: string; size: number; md5: string; detail: string };
export type PushResult = {
  path: string;
  size: number;
  md5: string;
  verified: boolean;
  detail: string;
};
export type Root = { path: string; label: string; readable: boolean };
export type InstallResult = { ok: boolean; output: string; fallback: boolean; sdk: number };
export type ExtractResult = { local: string; size: number };
export type SavedShot = { path: string; when: string };
export type PathInfo = { path: string; exists: boolean; size: number };

export type LauncherStatus = {
  installed: boolean;
  version: string;
  sdk: number;
  min_sdk: number;
  supported: boolean;
  home: string;
  candidates: string[];
  focused: string;
  is_home: boolean;
  overlay: string | null;
  overlay_exists: boolean;
  wireless: boolean | null;
  adb_secure: boolean;
};

export type ThemeKey = {
  key: string;
  type: "bool" | "int" | "string" | "color" | "packages" | "list" | "ints" | "select" | "json";
  group: string;
  /** Turkish name shown above the control. */
  label?: string;
  /** One line of Turkish help under the control. */
  help?: string;
  default?: boolean | number | string;
  min?: number;
  max?: number;
  options?: string[];
  /** Set to "config.xml" for a key the overlay cannot carry: it needs a build. */
  only?: string;
};

export type TvSettingKind = "bool" | "int" | "select" | "text";

export type TvSettingSpec = {
  key: string;
  namespace: string;
  kind: TvSettingKind;
  group: string;
  label: string;
  help: string;
  options: string[];
  min: number;
  max: number;
};

export type TvSettingValue = { key: string; value: string };
export type TvSettingWrite = { key: string; before: string; after: string; ok: boolean };

export type ThemeReport = { steps: Step[]; focused: string; overlay_exists: boolean };
export type HomeReport = {
  steps: Step[];
  home: string;
  focused: string;
  ok: boolean;
  cvte: boolean;
};

export type LocaleDir = { dir: string; label: string; config: boolean; strings: boolean };
export type RepoLayout = { repo: string; res: string; locales: LocaleDir[] };

export type ResEntry = {
  name: string;
  kind: string;
  value: string;
  items: string[];
  translatable: boolean | null;
};

export type ConfigDoc = {
  path: string;
  xml: string;
  entries: ResEntry[];
  backup: string;
};

export type StringsDoc = { path: string; entries: ResEntry[] };

export type BuildResult = {
  ok: boolean;
  apk: string;
  size: number;
  log: string;
  java: string;
  duration: number;
};

export type ImportedList = { name: string; raw: string };

const call = <T,>(cmd: string, args?: Record<string, unknown>) => invoke<T>(cmd, args);

/* device */
export const adbStatus = () => call<AdbStatus>("adb_status");
export const adbSetPath = (path: string) => call<AdbStatus>("adb_set_path", { path });
export const discover = () => call<ScanResult>("device_discover");
export const connection = () => call<Connection>("device_connection");
export const connect = (host: string, port: number) =>
  call<Connection>("device_connect", { host, port });
export const pair = (host: string, port: number, code: string) =>
  call<string>("device_pair", { host, port, code });
export const disconnect = () => call<void>("device_disconnect");
export const deviceInfo = () => call<DeviceInfo>("device_info");
export const screenshot = (save = true) => call<Shot>("device_screenshot", { save });
export const sendKey = (key: string) => call<string>("device_key", { key });
/** A click on the TV image: `u`/`v` are fractions of the screen, `mode` is tap or long. */
export const tap = (u: number, v: number, mode: "tap" | "long" = "tap") =>
  call<string>("device_tap", { u, v, mode });
export const sendText = (text: string) => call<string>("device_text", { text });
export const sync = () => call<string>("device_sync");
export const reboot = (waitForWrites = true) =>
  call<string>("device_reboot", { waitForWrites });
export const portCheck = (host: string, port: number) =>
  call<PortState>("port_check", { host, port });
export const closeAdb = (
  host: string,
  port: number,
  wireless: boolean,
  usb: boolean,
  portToCheck?: number,
) => call<CloseReport>("adb_close", { host, port, wireless, usb, portToCheck });
export const privateDnsGet = () => call<DnsState>("private_dns_get");
export const privateDnsSet = (mode: string, specifier: string) =>
  call<Step[]>("private_dns_set", { mode, specifier });

/* settings & repo */
export const settingsGet = () => call<Settings>("settings_get");
export const settingsSave = (settings: Settings) => call<Settings>("settings_save", { settings });
export const repoDetect = (path?: string) => call<RepoInfo>("repo_detect", { path: path ?? null });
export const examplesList = (repo: string) => call<ExampleList[]>("examples_list", { repo });
export const restoreScripts = (packages: string[], host: string) =>
  call<RestoreScripts>("restore_scripts", { packages, host });
export const importList = (file: string) => call<ImportedList>("import_list", { file });
export const saveText = (path: string, text: string) => call<string>("save_text", { path, text });

/* packages */
export const packagesList = () => call<PackageList>("packages_list");
export const packageDetails = (name: string) => call<string>("package_details", { name });
export const packageSetEnabled = (
  name: string,
  enabled: boolean,
  allowDanger = false,
  allowSetupwraith = false,
) => call<ToggleResult>("package_set_enabled", { name, enabled, allowDanger, allowSetupwraith });
export const packageLaunch = (name: string) => call<string>("package_launch", { name });
export const packageForceStop = (name: string) => call<string>("package_force_stop", { name });
export const batchDisable = (
  packages: string[],
  options: { allow: string[]; allowSetupwraith: boolean; screenshot: boolean },
) => call<BatchReport>("batch_disable", { packages, options });
export const pullApk = (name: string, directory: string) =>
  call<ExtractedApk>("package_pull_apk", { name, directory });

/* backup */
export const backupCreate = () => call<BackupResult>("backup_create");
export const backupState = () => call<boolean>("backup_state");
export const backupList = () => call<BackupEntry[]>("backup_list");
export const backupRestore = (path: string, packages?: string[]) =>
  call<RestoreReport>("backup_restore", { path, packages: packages ?? null });

/* files */
export const fsList = (path: string) => call<Listing>("fs_list", { path });
export const fsRead = (path: string) => call<FileText>("fs_read", { path });
export const fsWrite = (path: string, text: string) => call<WriteResult>("fs_write", { path, text });
export const fsPull = (remote: string, local: string) =>
  call<LocalFile>("fs_pull", { remote, local });
export const fsPush = (local: string, remote: string, verify = true) =>
  call<PushResult>("fs_push", { local, remote, verify });
export const fsMkdir = (path: string) => call<string>("fs_mkdir", { path });
export const fsDelete = (path: string) => call<string>("fs_delete", { path });
export const fsRoots = () => call<Root[]>("fs_roots");
export const apkInstall = (local: string) => call<InstallResult>("apk_install", { local });
export const fsPullTo = (remote: string, folder: string) =>
  call<ExtractResult>("fs_pull_to", { remote, folder });
export const shotsList = () => call<SavedShot[]>("shots_list");
export const fsStat = (path: string) => call<PathInfo>("fs_stat", { path });

/* launcher */
export const launcherStatus = () => call<LauncherStatus>("launcher_status");
export const themePush = (
  config: string,
  wallpaper?: string | null,
  wallpapers: string[] = [],
) => call<ThemeReport>("theme_push", { config, wallpaper: wallpaper ?? null, wallpapers });
export const themeReset = () => call<ThemeReport>("theme_reset");
export const themeKeys = () => call<ThemeKey[]>("theme_keys");
export const launcherSetHome = () => call<HomeReport>("launcher_set_home");
export const configLayout = (repo: string) => call<RepoLayout>("config_layout", { repo });
export const configRead = (repo: string, locale: string, file: string) =>
  call<ConfigDoc>("config_read", { repo, locale, file });
export const configWrite = (
  repo: string,
  path: string,
  name: string,
  kind: string,
  value: string,
  items: string[],
) => call<ConfigDoc>("config_write", { repo, path, name, kind, value, items });
export const configWriteRaw = (repo: string, path: string, xml: string) =>
  call<ConfigDoc>("config_write_raw", { repo, path, xml });
export const stringsRead = (repo: string, locale: string) =>
  call<StringsDoc>("strings_read", { repo, locale });
export const stringsWrite = (repo: string, locale: string, name: string, value: string) =>
  call<ConfigDoc>("strings_write", { repo, locale, name, value });
export const launcherBuild = (repo: string) => call<BuildResult>("launcher_build", { repo });
export const launcherInstall = (apk: string, setHome: boolean) =>
  call<HomeReport>("launcher_install", { apk, setHome });

/* TV settings */
export const tvSettingsSpecs = () => call<TvSettingSpec[]>("tv_settings_specs");
export const tvSettingsRead = () => call<TvSettingValue[]>("tv_settings_read");
export const tvSettingWrite = (key: string, value: string) =>
  call<TvSettingWrite>("tv_setting_write", { key, value });

export const message = (error: unknown) =>
  typeof error === "string"
    ? error
    : error instanceof Error
      ? error.message
      : JSON.stringify(error);
