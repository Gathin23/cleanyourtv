"""Central de Jogos: o PC atende a aba Jogos do launcher da TV.

Tudo passa pela conexão ADB que o PC abre com a TV (nenhuma porta é aberta no PC, então o firewall não
precisa de regra):
  * a biblioteca (Steam instalada + Hydra) vai para a TV como files/games.json;
  * a TV pede um jogo escrevendo uma linha no logcat (tag GameReq); este programa lê o logcat ao vivo;
  * para jogar: imagem só na TV em 1080p, áudio na TV, abre o jogo e troca a entrada da TV para o HDMI;
  * quando o jogo fecha: devolve a imagem e o áudio ao monitor e manda a TV de volta para a Home.
O estado ("jogando X", avisos) vai para files/game_status.json, que a aba Jogos mostra.
"""
import calendar
import ctypes
import ctypes.wintypes as wt
import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import winreg

import comtypes
import psutil

import displaycfg
import vdf
from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "config.json")
ASSETS_CACHE = os.path.join(HERE, "assets_cache.json")
TV_FILES = "/sdcard/Android/data/com.gathin.tvhome/files"

DEFAULTS = {
    "tv_host": "192.168.15.10",
    "tv_port": 5555,
    "adb_key": r"C:\tvh\tools\adbkey\adbkey",
    "tv_resolution": [1920, 1080, 60],
    "tv_audio_name": "Smart TV Pro",       # parte do nome da saída de áudio da TV no Windows
    "tv_monitor_name": "Smart TV Pro",     # parte do nome do monitor da TV no Windows
    "tv_monitor_id": "XXX8000",
    "tv_hdmi": "com.tcl.tvinput/.TvPassThroughService/HW15",   # entrada da TV onde o PC está (HDMI 1)            # código do monitor da TV (Gerenciador de Dispositivos → Monitores)
    "hydra_db": os.path.expandvars(r"%APPDATA%\hydralauncher\hydra-db"),
    # Pastas onde ficam os executáveis dos jogos (para saber quando o jogo fechou).
    "game_roots": [r"D:\Games", r"D:\SteamLibrary\steamapps\common",
                   r"C:\Program Files (x86)\Steam\steamapps\common", r"D:\Rockstar Games",
                   r"C:\Program Files\Rockstar Games", r"D:\Epic Games"],
    "skip_steam_apps": [228980],           # Steamworks Common Redistributables etc.
    # Jogos com launcher que pede clique: abre o executável do jogo direto com a opção que pula o launcher
    # (pela Steam, opções extras abrem um aviso no PC). Caminho relativo a uma das game_roots.
    "launch_direct": {"1091500": ["Cyberpunk 2077\\bin\\x64\\Cyberpunk2077.exe", "--launcher-skip -skipStartScreen"]},
    # Launcher que pede clique: depois desse tempo a TV mostra o PC para você clicar.
    "launcher_wait_s": 40,
    # Pasta de vídeos do PC servida para a TV como a fonte "Meu PC" (Séries\<série>\..., Filmes\...).
    "media_root": r"D:\Series e Filmes",
    "media_port": 8765,
    # Tempo para a TV firmar o sinal do HDMI antes de tirar a tela da Central de cima.
    "hdmi_settle_s": 3.5,
    "refresh_minutes": 10,
}

logging.basicConfig(filename=os.path.join(HERE, "agent.log"), level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("agent")


def load_config():
    cfg = dict(DEFAULTS)
    try:
        with open(CONFIG, encoding="utf-8") as f:
            cfg.update(json.load(f))
    except FileNotFoundError:
        with open(CONFIG, "w", encoding="utf-8") as f:
            json.dump(DEFAULTS, f, indent=2, ensure_ascii=False)
    return cfg


CFG = load_config()


# ---------------------------------------------------------------- TV (ADB)

def _signer():
    key = CFG["adb_key"]
    if not os.path.exists(key):
        key = os.path.join(os.path.expanduser("~"), ".android", "adbkey")
    with open(key) as f:
        priv = f.read()
    with open(key + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


class Tv:
    """One ADB connection for commands and pushes, reopened when it drops."""

    def __init__(self):
        self.dev = None
        self.lock = threading.Lock()

    def _conn(self):
        if self.dev is None or not self.dev.available:
            dev = AdbDeviceTcp(CFG["tv_host"], CFG["tv_port"], default_transport_timeout_s=20.0)
            dev.connect(rsa_keys=[_signer()], auth_timeout_s=20.0)
            self.dev = dev
        return self.dev

    def shell(self, cmd, timeout=30.0):
        with self.lock:
            try:
                return self._conn().shell(cmd, read_timeout_s=timeout)
            except Exception:
                self.dev = None
                return self._conn().shell(cmd, read_timeout_s=timeout)

    def push_text(self, name, text):
        tmp = os.path.join(tempfile.gettempdir(), "tvagent_" + name)
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(text)
        with self.lock:
            try:
                self._conn().push(tmp, TV_FILES + "/" + name, read_timeout_s=60.0)
            except Exception:
                self.dev = None
                self._conn().push(tmp, TV_FILES + "/" + name, read_timeout_s=60.0)


TV = Tv()


def tv_switch_to_hdmi(input_id):
    uri = "content://android.media.tv/passthrough/" + urllib.parse.quote(input_id, safe="")
    # Depois de trocar, "Voltar" fecha a barra "HDMI 1" da TCL sem sair da entrada.
    TV.shell('am start -a android.intent.action.VIEW -d "%s" >/dev/null 2>&1; sleep 1.2; '
             'dumpsys window | grep -q "mCurrentFocus.*com.tcl.tv" && input keyevent 4' % uri)


def tv_cover(mode, title="", text="", img="", delay=0):
    """Tela da Central de Jogos por cima de tudo na TV (GameCover no app): esconde as trocas de entrada."""
    def q(v):
        return "'" + str(v).replace("'", "") + "'"
    if mode == "show":
        cmd = "am broadcast -n com.gathin.tvhome/.GameCover --es mode show --es title %s --es text %s --es img %s" % (
            q(title), q(text), q(img))
    else:
        cmd = "am broadcast -n com.gathin.tvhome/.GameCover --es mode hide --ei delay %d" % delay
    try:
        TV.shell(cmd + " >/dev/null 2>&1")
    except Exception as e:
        log.warning("tv cover: %s", e)


def tv_back_home():
    # Só sai do HDMI se a TV ainda estiver nele (não atrapalha se você já voltou por conta própria).
    top = TV.shell("dumpsys activity activities | grep -m1 mResumedActivity")
    if "com.tcl.tv" in top:
        TV.shell("am start -n com.gathin.tvhome/.WebHomeActivity --es open games >/dev/null 2>&1")


# ---------------------------------------------------------------- telas (Windows)

class DEVMODEW(ctypes.Structure):
    _fields_ = [("dmDeviceName", wt.WCHAR * 32), ("dmSpecVersion", wt.WORD), ("dmDriverVersion", wt.WORD),
                ("dmSize", wt.WORD), ("dmDriverExtra", wt.WORD), ("dmFields", wt.DWORD),
                ("dmPositionX", wt.LONG), ("dmPositionY", wt.LONG), ("dmDisplayOrientation", wt.DWORD),
                ("dmDisplayFixedOutput", wt.DWORD), ("dmColor", ctypes.c_short), ("dmDuplex", ctypes.c_short),
                ("dmYResolution", ctypes.c_short), ("dmTTOption", ctypes.c_short), ("dmCollate", ctypes.c_short),
                ("dmFormName", wt.WCHAR * 32), ("dmLogPixels", wt.WORD), ("dmBitsPerPel", wt.DWORD),
                ("dmPelsWidth", wt.DWORD), ("dmPelsHeight", wt.DWORD), ("dmDisplayFlags", wt.DWORD),
                ("dmDisplayFrequency", wt.DWORD), ("dmICMMethod", wt.DWORD), ("dmICMIntent", wt.DWORD),
                ("dmMediaType", wt.DWORD), ("dmDitherType", wt.DWORD), ("dmReserved1", wt.DWORD),
                ("dmReserved2", wt.DWORD), ("dmPanningWidth", wt.DWORD), ("dmPanningHeight", wt.DWORD)]


class DISPLAY_DEVICEW(ctypes.Structure):
    _fields_ = [("cb", wt.DWORD), ("DeviceName", wt.WCHAR * 32), ("DeviceString", wt.WCHAR * 128),
                ("StateFlags", wt.DWORD), ("DeviceID", wt.WCHAR * 128), ("DeviceKey", wt.WCHAR * 128)]


user32 = ctypes.windll.user32
ATTACHED_TO_DESKTOP = 0x1


def active_displays():
    """\\\\.\\DISPLAYn that are part of the desktop now."""
    out, i = [], 0
    while True:
        dd = DISPLAY_DEVICEW(); dd.cb = ctypes.sizeof(dd)
        if not user32.EnumDisplayDevicesW(None, i, ctypes.byref(dd), 0):
            break
        if dd.StateFlags & ATTACHED_TO_DESKTOP:
            out.append(dd.DeviceName)
        i += 1
    return out


def tv_code():
    return CFG["tv_monitor_id"].lower()


def active_screens():
    """Nomes (com o código EDID) dos monitores acesos agora, pela API SetDisplayConfig."""
    return [n for n, on in displaycfg.monitors() if on]


def tv_connected():
    """A TV está ligada no cabo (mesmo que o Windows não a esteja usando)."""
    try:
        return any(tv_code() in n.lower() for n, _ in displaycfg.monitors())
    except Exception:
        return True   # sem como saber: tenta mesmo assim


def tv_only():
    act = active_screens()
    return len(act) == 1 and tv_code() in act[0].lower()


def set_resolution(device, w, h, hz):
    dm = DEVMODEW(); dm.dmSize = ctypes.sizeof(dm)
    if not user32.EnumDisplaySettingsW(device, -1, ctypes.byref(dm)):   # ENUM_CURRENT_SETTINGS
        return False
    if dm.dmPelsWidth == w and dm.dmPelsHeight == h and dm.dmDisplayFrequency == hz:
        return True
    dm.dmPelsWidth, dm.dmPelsHeight, dm.dmDisplayFrequency = w, h, hz
    dm.dmFields = 0x80000 | 0x100000 | 0x400000   # PELSWIDTH | PELSHEIGHT | DISPLAYFREQUENCY
    r = user32.ChangeDisplaySettingsExW(device, ctypes.byref(dm), None, 0x1, None)   # CDS_UPDATEREGISTRY
    log.info("resolution %s -> %dx%d@%d: %s", device, w, h, hz, r)
    return r == 0


# ---------------------------------------------------------------- áudio (Windows)

def _policy_config():
    from comtypes import COMMETHOD, GUID, HRESULT, IUnknown, CoCreateInstance, CLSCTX_ALL

    class IPolicyConfig(IUnknown):
        _iid_ = GUID("{f8679f50-850a-41cf-9c72-430f290290c8}")
        _methods_ = [COMMETHOD([], HRESULT, n) for n in (
            "GetMixFormat", "GetDeviceFormat", "ResetDeviceFormat", "SetDeviceFormat", "GetProcessingPeriod",
            "SetProcessingPeriod", "GetShareMode", "SetShareMode", "GetPropertyValue", "SetPropertyValue")] + [
            COMMETHOD([], HRESULT, "SetDefaultEndpoint",
                      (["in"], ctypes.c_wchar_p, "deviceId"), (["in"], ctypes.c_int, "role")),
            COMMETHOD([], HRESULT, "SetEndpointVisibility")]

    return CoCreateInstance(GUID("{870af99c-171d-4f9e-af0d-e63df40c2bc9}"), IPolicyConfig, CLSCTX_ALL)


def audio_default_id():
    from pycaw.pycaw import AudioUtilities
    try:
        return AudioUtilities.GetSpeakers().GetId()
    except Exception:
        return None


def audio_find(name_part):
    from pycaw.pycaw import AudioUtilities
    from pycaw.constants import AudioDeviceState
    for d in AudioUtilities.GetAllDevices():
        if d.FriendlyName and name_part.lower() in d.FriendlyName.lower() and d.state == AudioDeviceState.Active:
            return d.id
    return None


def audio_set_default(dev_id):
    if not dev_id:
        return
    try:
        import comtypes
        comtypes.CoInitialize()
        pc = _policy_config()
        for role in (0, 1, 2):   # console, multimedia, communications
            pc.SetDefaultEndpoint(dev_id, role)
    except Exception as e:
        log.warning("audio switch failed: %s", e)


# ---------------------------------------------------------------- biblioteca

STEAM = None


def steam_path():
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Valve\Steam") as k:
        return winreg.QueryValueEx(k, "SteamPath")[0].replace("/", "\\")


def steam_games():
    base = steam_path()
    libs = vdf.load(open(os.path.join(base, "steamapps", "libraryfolders.vdf"), encoding="utf-8"))["libraryfolders"]
    played = {}
    userdata = os.path.join(base, "userdata")
    for uid in os.listdir(userdata) if os.path.isdir(userdata) else []:
        lc = os.path.join(userdata, uid, "config", "localconfig.vdf")
        if os.path.exists(lc):
            try:
                apps = vdf.load(open(lc, encoding="utf-8", errors="replace"))["UserLocalConfigStore"]["Software"]["Valve"]["Steam"]["apps"]
                for a, v in apps.items():
                    played[a] = (int(v.get("LastPlayed", 0) or 0), int(v.get("Playtime", 0) or 0))
            except Exception as e:
                log.warning("localconfig %s: %s", lc, e)
    out = []
    for lib in libs.values():
        sa = os.path.join(lib["path"], "steamapps")
        for fn in os.listdir(sa) if os.path.isdir(sa) else []:
            if not (fn.startswith("appmanifest_") and fn.endswith(".acf")):
                continue
            try:
                st = vdf.load(open(os.path.join(sa, fn), encoding="utf-8", errors="replace"))["AppState"]
            except Exception:
                continue
            appid = int(st["appid"])
            if appid in CFG["skip_steam_apps"]:
                continue
            last, mins = played.get(str(appid), (0, 0))
            out.append({"id": "steam:%d" % appid, "src": "steam", "appid": appid, "title": st.get("name", ""),
                        "last": last, "mins": mins})
    return out


def hydra_games():
    """The Hydra library, read from a copy of its LevelDB (Hydra keeps the live one locked)."""
    import plyvel
    src = CFG["hydra_db"]
    if not os.path.isdir(src):
        return []
    tmp = os.path.join(tempfile.gettempdir(), "tvagent_hydra")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)
    for fn in os.listdir(src):
        if fn == "LOCK":
            continue
        with open(os.path.join(src, fn), "rb") as a, open(os.path.join(tmp, fn), "wb") as b:
            b.write(a.read())
    out = []
    db = plyvel.DB(tmp)
    try:
        for _, v in db.iterator(prefix=b"!games!"):
            try:
                g = json.loads(v)
            except Exception:
                continue
            if g.get("isDeleted"):
                continue
            last = 0
            if g.get("lastTimePlayed"):
                last = calendar.timegm(time.strptime(g["lastTimePlayed"][:19], "%Y-%m-%dT%H:%M:%S"))
            out.append({"id": "hydra:%s:%s" % (g.get("shop"), g.get("objectId")), "src": "hydra",
                        "shop": g.get("shop"), "objectId": g.get("objectId"),
                        "appid": int(g["objectId"]) if g.get("shop") == "steam" and str(g.get("objectId")).isdigit() else None,
                        "title": g.get("title", ""), "last": last,
                        "mins": int((g.get("playTimeInMilliseconds") or 0) / 60000),
                        "hero": g.get("customHeroImageUrl") or g.get("libraryHeroImageUrl"),
                        "logo": g.get("customLogoImageUrl") or g.get("logoImageUrl"),
                        "cover": g.get("customCoverImageUrl")})
    finally:
        db.close()
    return out


def steam_assets(appids):
    """Capa vertical, fundo, cabeçalho, nome e descrição em português (API pública da loja Steam), em cache."""
    try:
        cache = json.load(open(ASSETS_CACHE, encoding="utf-8"))
    except Exception:
        cache = {}
    need = [a for a in appids if str(a) not in cache or time.time() - cache[str(a)].get("_at", 0) > 7 * 86400]
    for i in range(0, len(need), 30):
        chunk = need[i:i + 30]
        q = json.dumps({"ids": [{"appid": a} for a in chunk], "context": {"language": "brazilian", "country_code": "BR"},
                        "data_request": {"include_assets": True, "include_basic_info": True}})
        url = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1?" + urllib.parse.urlencode({"input_json": q})
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                items = json.load(r)["response"].get("store_items", [])
        except Exception as e:
            log.warning("steam assets: %s", e)
            continue
        for it in items:
            a = it.get("assets") or {}
            fmt = "https://shared.steamstatic.com/store_item_assets/" + a.get("asset_url_format", "")

            def u(key):
                return fmt.replace("${FILENAME}", a[key]) if a.get(key) else None
            cache[str(it.get("appid"))] = {
                "_at": time.time(), "name": it.get("name"),
                "desc": (it.get("basic_info") or {}).get("short_description", ""),
                "cover": u("library_capsule_2x") or u("library_capsule"),
                "hero": u("library_hero_2x") or u("library_hero"),
                "header": u("header_2x") or u("header"),
            }
    json.dump(cache, open(ASSETS_CACHE, "w", encoding="utf-8"), ensure_ascii=False)
    return cache


def build_library():
    games = []
    try:
        games += steam_games()
    except Exception as e:
        log.exception("steam: %s", e)
    try:
        hydra = hydra_games()
    except Exception as e:
        log.exception("hydra: %s", e)
        hydra = []
    have = {g["appid"] for g in games}
    games += [g for g in hydra if g["appid"] is None or g["appid"] not in have]   # o da Steam vale mais
    assets = steam_assets([g["appid"] for g in games if g.get("appid")])
    for g in games:
        a = assets.get(str(g.get("appid")), {})
        g["cover"] = g.get("cover") or a.get("cover")
        g["hero"] = g.get("hero") or a.get("hero")
        g["header"] = a.get("header")
        g["desc"] = a.get("desc", "")
        if a.get("name"):
            g["title"] = a["name"]
        if not g.get("logo") and g.get("appid"):
            g["logo"] = "https://shared.steamstatic.com/store_item_assets/steam/apps/%d/logo.png" % g["appid"]
        g["title"] = g["title"].replace("\ufffd", "")
    games.sort(key=lambda g: (-g["last"], g["title"].lower()))
    return games


# ---------------------------------------------------------------- estado e jogo

STATE = {"running": None, "msg": "", "at": 0}
_last_pushed = {"games": None, "status": None}
_busy = threading.Lock()


def push_status(msg=None):
    if msg is not None:
        STATE["msg"] = msg
    STATE["at"] = int(time.time() * 1000)
    text = json.dumps(STATE, ensure_ascii=False)
    try:
        TV.push_text("game_status.json", text)
        _last_pushed["status"] = text
    except Exception as e:
        log.warning("push status: %s", e)


def push_library(force=False):
    games = build_library()
    text = json.dumps({"pc": os.environ.get("COMPUTERNAME", "PC"), "games": games}, ensure_ascii=False)
    if force or text != _last_pushed["games"]:
        TV.push_text("games.json", text)
        _last_pushed["games"] = text
        log.info("library pushed: %d games", len(games))
    return games


def find_game(gid):
    for g in build_library():
        if g["id"] == gid:
            return g
    return None


def launch_uri(g):
    if g["src"] == "steam":
        return "steam://rungameid/%d" % g["appid"]
    return "hydralauncher://run?" + urllib.parse.urlencode({"shop": g["shop"], "objectId": g["objectId"]})


def launch(g):
    direct = CFG["launch_direct"].get(str(g.get("appid") or g.get("objectId") or ""))
    if direct:
        for root in CFG["game_roots"]:
            exe = os.path.join(root, direct[0])
            if os.path.isfile(exe):
                log.info("direct launch %s %s", exe, direct[1])
                subprocess.Popen([exe] + direct[1].split(), cwd=os.path.dirname(exe))
                return
    os.startfile(launch_uri(g))


def under_roots(path):
    p = (path or "").lower()
    return any(p.startswith(r.lower().rstrip("\\") + "\\") for r in CFG["game_roots"])


def game_processes(since):
    found = []
    for pr in psutil.process_iter(["exe", "create_time", "name"]):
        try:
            if pr.info["create_time"] >= since - 2 and under_roots(pr.info["exe"]):
                found.append(pr)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
    return found


_LAUNCHERISH = ("launcher", "crash", "unins", "setup", "redist", "vc_redist", "dxsetup", "easyanticheat", "helper")


def game_window(procs):
    """True quando um dos processos do jogo (que não seja launcher) tem uma janela grande visível."""
    pids = {p.pid for p in procs if not any(k in (p.info["name"] or "").lower() for k in _LAUNCHERISH)}
    if not pids:
        return False
    found = []
    rect = wt.RECT()
    pid = wt.DWORD()

    @ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    def cb(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids and user32.GetWindowRect(hwnd, ctypes.byref(rect)):
                if rect.right - rect.left >= 1000 and rect.bottom - rect.top >= 560:
                    found.append(hwnd)
                    return False
        return True
    user32.EnumWindows(cb, 0)
    return found[0] if found else None


def fullscreen(hwnd):
    """A janela cobre a tela inteira (tela cheia ou janela sem borda)."""
    r = wt.RECT()
    if not user32.GetWindowRect(hwnd, ctypes.byref(r)):
        return False
    return (r.right - r.left) >= user32.GetSystemMetrics(0) and (r.bottom - r.top) >= user32.GetSystemMetrics(1)


VK_MENU, VK_RETURN, KEYUP = 0x12, 0x0D, 0x2


def bring_front(hwnd):
    """Põe o jogo em primeiro plano (o Windows só deixa depois de uma tecla 'falsa')."""
    if user32.IsIconic(hwnd):
        user32.ShowWindow(hwnd, 9)                    # SW_RESTORE
    user32.keybd_event(VK_MENU, 0, 0, 0)
    user32.keybd_event(VK_MENU, 0, KEYUP, 0)
    user32.SetForegroundWindow(hwnd)
    user32.BringWindowToTop(hwnd)
    return user32.GetForegroundWindow() == hwnd


def alt_enter():
    user32.keybd_event(VK_MENU, 0, 0, 0)
    user32.keybd_event(VK_RETURN, 0, 0, 0)
    user32.keybd_event(VK_RETURN, 0, KEYUP, 0)
    user32.keybd_event(VK_MENU, 0, KEYUP, 0)


# Quanto cada jogo levou para abrir da última vez (a barra de carregamento da TV usa isso).
LOAD_TIMES = os.path.join(HERE, "load_times.json")


def load_time(gid):
    try:
        return json.load(open(LOAD_TIMES, encoding="utf-8")).get(gid)
    except (OSError, ValueError):
        return None


def save_load_time(gid, secs):
    try:
        d = json.load(open(LOAD_TIMES, encoding="utf-8"))
    except (OSError, ValueError):
        d = {}
    d[gid] = round(secs, 1)
    json.dump(d, open(LOAD_TIMES, "w", encoding="utf-8"))


STEAM_LOG = r"C:\Program Files (x86)\Steam\logs\console_log.txt"


def steam_waiting(appid, since):
    """A Steam parou antes de abrir o jogo esperando uma resposta (opções de abertura, contrato etc.)."""
    try:
        with open(STEAM_LOG, "rb") as f:
            f.seek(0, 2)
            f.seek(max(0, f.tell() - 60000))
            tail = f.read().decode("utf-8", "replace")
    except OSError:
        return False
    stamp = time.strftime("[%Y-%m-%d %H:%M:%S]", time.localtime(since - 2))
    for line in tail.splitlines():
        if line[:21] >= stamp and ("AppID %d," % appid) in line and "waiting for user response" in line:
            return True
    return False


def steam_dialog_front():
    """Traz para a frente a janela da Steam que está perguntando algo."""
    pids = {p.pid for p in psutil.process_iter(["name"]) if (p.info["name"] or "").lower() == "steamwebhelper.exe"}
    wins = []
    pid = wt.DWORD()
    buf = ctypes.create_unicode_buffer(256)

    @ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    def cb(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids:
                user32.GetWindowTextW(hwnd, buf, 256)
                if buf.value:
                    wins.append((buf.value, hwnd))
        return True
    user32.EnumWindows(cb, 0)
    log.info("steam windows: %s", [t for t, _ in wins])
    # A pergunta vem numa janela própria; a principal se chama só "Steam".
    pick = [h for t, h in wins if t != "Steam"] or [h for _, h in wins]
    for h in pick[:1]:
        bring_front(h)
        return h


_cover = [None]


def cover_on():
    """Abre a tela de fundo (cover.py) atrás do jogo, para a área de trabalho nunca aparecer na TV."""
    cover_off()
    exe = os.path.join(os.path.dirname(sys.executable), "pythonw.exe")
    _cover[0] = subprocess.Popen([exe if os.path.isfile(exe) else sys.executable, os.path.join(HERE, "cover.py")])


def cover_behind(game_hwnd):
    """Põe a tela de fundo logo atrás do jogo: ao fechar, nada do Windows fica entre os dois."""
    h = user32.FindWindowW(None, "Central de Jogos")
    if h and game_hwnd:
        user32.SetWindowPos(h, game_hwnd, 0, 0, 0, 0, 0x0001 | 0x0002 | 0x0010)   # NOSIZE|NOMOVE|NOACTIVATE


def cover_front():
    h = user32.FindWindowW(None, "Central de Jogos")
    if h:
        bring_front(h)


def cover_off():
    if _cover[0] and _cover[0].poll() is None:
        _cover[0].kill()
    _cover[0] = None


def play(gid, hdmi):
    log.info("launch %s", gid)
    if not _busy.acquire(blocking=False):
        log.info("busy: another game is opening")
        push_status("Já tem um jogo abrindo no PC.")
        return
    before, switched = None, False
    try:
        try:
            comtypes.CoInitialize()     # o áudio usa COM, e esta tarefa roda fora da principal
        except OSError as e:
            log.warning("CoInitialize: %s", e)
        g = find_game(gid)
        if not g:
            log.info("game not found: %s", gid)
            push_status("Não achei esse jogo no PC.")
            return
        if not tv_connected():
            log.info("TV not seen on HDMI: %s", displaycfg.monitors())
            push_status("O PC não está vendo a TV pelo HDMI. Confira o cabo.")
            return
        before = active_screens()
        prev_audio = audio_default_id()
        log.info("play %s (%s) displays=%s", g["title"], gid, before)
        # Só mexe na resolução depois que o Windows terminou de trocar (mexer antes traz o monitor de volta).
        ok = False
        for attempt in range(2):
            displaycfg.only(CFG["tv_monitor_id"])
            for _ in range(20):
                time.sleep(0.5)
                if tv_only():
                    ok = True
                    break
            if ok:
                break
            log.info("switch to TV did not take (%s); trying again", active_screens())
        switched = True
        if not ok:
            restore_displays(before)
            push_status("O Windows não passou a imagem para a TV. Tente de novo.")
            return
        time.sleep(1)
        w, h, hz = CFG["tv_resolution"]
        set_resolution(active_displays()[0], w, h, hz)   # só a TV está acesa agora
        try:
            audio_set_default(audio_find(CFG["tv_audio_name"]))
        except Exception as e:      # sem trocar o áudio o jogo ainda abre
            log.warning("audio: %s", e)
        try:
            cover_on()
            time.sleep(1.5)         # a tela de fundo precisa estar aberta antes do jogo, para ficar atrás dele
        except Exception as e:
            log.warning("cover: %s", e)
        t0 = time.time()
        launch(g)
        # A TV fica na tela "Carregando" e só vai para o HDMI quando a janela do jogo aparece (ver watch).
        STATE["running"] = {"id": g["id"], "title": g["title"], "since": int(t0 * 1000), "phase": "loading",
                            "expect": load_time(g["id"]) or 45}
        push_status("")
        watch(g, t0, before, prev_audio, hdmi or CFG["tv_hdmi"])
    except Exception as e:
        log.exception("play: %s", e)
        # Nunca deixar o PC com a imagem só na TV se algo deu errado no caminho.
        cover_off()
        if switched:
            try:
                restore_displays(before)
            except Exception as e2:
                log.warning("restore after error: %s", e2)
        STATE["running"] = None
        push_status("Deu um erro ao abrir o jogo no PC.")
    finally:
        _busy.release()
        try:
            comtypes.CoUninitialize()
        except OSError:
            pass


threading.excepthook = lambda a: log.error("thread %s: %s", a.thread and a.thread.name, a.exc_value, exc_info=(a.exc_type, a.exc_value, a.exc_traceback))


RESTORE = threading.Event()
CLOSE = threading.Event()        # "Fechar jogo" pelo controle remoto da TV


def close_game(procs):
    """Pede para o jogo fechar (como clicar no X); quem não fechar em 10 s é encerrado."""
    pids = {p.pid for p in procs}
    pid = wt.DWORD()

    @ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    def cb(hwnd, _):
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if pid.value in pids and user32.IsWindowVisible(hwnd):
            user32.PostMessageW(hwnd, 0x0010, 0, 0)     # WM_CLOSE
        return True
    user32.EnumWindows(cb, 0)
    gone, alive = psutil.wait_procs(procs, timeout=10)
    for p in alive:
        log.info("terminating %s", p.info["name"])
        try:
            p.terminate()
        except psutil.Error:
            pass


def tv_awake():
    """False quando a TV foi desligada (fica em espera, mas o ADB continua respondendo)."""
    try:
        return "Awake" in TV.shell("dumpsys power | grep -m1 mWakefulness=")
    except Exception:
        return True       # sem resposta: não arrisca devolver a tela à toa


def watch(g, t0, before, prev_audio, hdmi):
    """Espera o jogo abrir e depois fechar; então devolve tudo como estava."""
    started = False
    shown = False
    gone_since = None
    RESTORE.clear()
    CLOSE.clear()
    asleep_since = None
    last_power = 0

    art = g.get("hero") or g.get("header") or ""

    def show(why):
        nonlocal shown
        shown = True
        log.info("TV -> HDMI (%s) after %.0f s", why, time.time() - t0)
        # A tela da Central cobre o "procurando sinal" da TV; some quando o sinal do PC já firmou.
        tv_cover("show", g["title"], "Abrindo o jogo…", art)
        STATE["running"]["phase"] = "playing"
        push_status("")
        tv_switch_to_hdmi(hdmi)
        time.sleep(CFG["hdmi_settle_s"])
        tv_cover("hide")

    covering = None          # quando a janela do jogo sumiu e a TV foi coberta
    seen_win = False         # a janela do jogo já apareceu na TV (launcher/pergunta da Steam não contam)
    last_gw = None

    win_since = None
    tried_alt_enter = False
    asked = 0
    while not RESTORE.is_set():
        procs = game_processes(t0)
        if CLOSE.is_set() and procs:
            CLOSE.clear()
            log.info("close requested from TV: %s", [p.info["name"] for p in procs])
            threading.Thread(target=close_game, args=(procs,), daemon=True).start()
        # TV desligada no meio do jogo: devolve a imagem ao monitor (o jogo continua aberto).
        if shown and time.time() - last_power > 5:
            last_power = time.time()
            if tv_awake():
                asleep_since = None
            else:
                asleep_since = asleep_since or time.time()
                if time.time() - asleep_since > 8:
                    log.info("TV turned off: giving the screen back to the PC")
                    break
        if procs:
            if not started:
                log.info("game running: %s", [p.info["name"] for p in procs])
                started_at = time.time()
            started, gone_since = True, None
            if shown and not RESTORE.is_set():
                # Janela do jogo sumiu = jogo fechando: cobre a TV na hora, antes de aparecer o Windows.
                gw = game_window(procs)
                if gw:
                    seen_win = True
                    if gw != last_gw:
                        cover_behind(gw)
                        last_gw = gw
                if seen_win and not gw:
                    if not covering:
                        covering = time.time()
                        log.info("game window gone: covering TV")
                        tv_cover("show", g["title"], "Voltando para a TV…", art)
                        cover_front()
                    elif time.time() - covering > 6:
                        log.info("game still closing; going back to TV")
                        break
                elif seen_win and covering:
                    log.info("game window back (mode change)")
                    covering = None
                    tv_cover("hide")
            if not shown:
                hwnd = game_window(procs)
                if hwnd:
                    # Só mostra na TV com o jogo na frente e em tela cheia (nada de área de trabalho).
                    win_since = win_since or time.time()
                    front = bring_front(hwnd)
                    full = fullscreen(hwnd)
                    if front and full:
                        time.sleep(1)
                        save_load_time(g["id"], time.time() - t0)
                        show("game window")
                    elif front and not tried_alt_enter and time.time() - win_since > 8:
                        log.info("window not fullscreen: Alt+Enter")
                        alt_enter()
                        tried_alt_enter = True
                    elif time.time() - win_since > 25:
                        show("game window (not fullscreen=%s front=%s)" % (full, front))
                elif time.time() - started_at > CFG["launcher_wait_s"]:
                    show("launcher waiting for a click")
        elif started:
            gone_since = gone_since or time.time()
            # Alguns jogos fecham o launcher e abrem o jogo; depois que o jogo apareceu, fecha rápido.
            if shown and not covering:
                covering = time.time()
                tv_cover("show", g["title"], "Voltando para a TV…", art)
                cover_front()
            if time.time() - gone_since > (1 if shown else 8):
                break
        elif not shown and g["src"] == "steam" and time.time() - t0 > 4 and steam_waiting(g["appid"], t0):
            # A Steam parou numa pergunta (em geral o aviso de controle detectado): aceita com Enter.
            # Se continuar parada, mostra o PC na TV para você responder.
            if not asked:
                asked = time.time()
                if steam_dialog_front():
                    time.sleep(0.5)
                    user32.keybd_event(VK_RETURN, 0, 0, 0)
                    user32.keybd_event(VK_RETURN, 0, KEYUP, 0)
                    log.info("steam dialog: pressed Enter")
            elif time.time() - asked > 15 and not game_processes(t0):
                steam_dialog_front()
                show("steam is asking something")
        elif time.time() - t0 > 240:                # nunca abriu
            log.info("game never started")
            break
        RESTORE.wait(1 if not shown else 0.2)
    if shown and not covering:
        tv_cover("show", g["title"], "Voltando para a TV…", art)
    restore(before, prev_audio)
    tv_cover("hide", delay=700)


def restore_displays(before):
    displaycfg.topology("extend" if before and len(before) > 1 else "internal")


def restore(before=None, prev_audio=None):
    # A TV volta para a home antes de o Windows mexer nas telas, para não mostrar a área de trabalho.
    STATE["running"] = None
    push_status("")
    try:
        tv_back_home()
    except Exception as e:
        log.warning("tv home: %s", e)
    log.info("restore displays (before=%s)", before)
    time.sleep(1)               # a TV já saiu do HDMI; a tela de fundo cobriu a troca
    cover_off()
    restore_displays(before)
    if prev_audio:
        try:
            audio_set_default(prev_audio)
        except Exception as e:
            log.warning("audio back: %s", e)
    try:
        push_library()
    except Exception as e:
        log.warning("library: %s", e)


# ---------------------------------------------------------------- pedidos da TV

def handle(req):
    a = req.get("a")
    if a == "launch":
        threading.Thread(target=play, args=(req.get("id"), req.get("hdmi")), daemon=True).start()
    elif a == "close":
        CLOSE.set()
    elif a == "restore":
        RESTORE.set()
        if not _busy.locked():
            restore()
    elif a == "refresh":
        threading.Thread(target=lambda: (push_library(force=True), push_status("")), daemon=True).start()


def listen():
    """Lê o logcat da TV ao vivo; cada pedido é uma linha JSON com a tag GameReq."""
    seen = set()
    while True:
        try:
            # Leitura longa: o logcat fica quieto até a TV pedir algo.
            dev = AdbDeviceTcp(CFG["tv_host"], CFG["tv_port"], default_transport_timeout_s=900.0)
            dev.connect(rsa_keys=[_signer()], transport_timeout_s=20.0, auth_timeout_s=20.0)
            # O relógio da TV não bate com o do PC (minutos de diferença): mede a diferença para julgar a idade do pedido.
            try:
                skew = int(TV.shell("date +%s%3N").strip()) - int(time.time() * 1000)
            except Exception:
                skew = 0
            log.info("listening to TV (clock skew %d ms)", skew)
            for line in dev.streaming_shell("logcat -v raw -s GameReq:I -T 1", read_timeout_s=10 ** 9):
                for part in line.splitlines():
                    part = part.strip()
                    if not part.startswith("{"):
                        continue
                    try:
                        req = json.loads(part)
                    except ValueError:
                        continue
                    key = (req.get("a"), req.get("id"), req.get("t"))
                    # -T 1 repete a última linha ao reconectar: pedido velho ou repetido não vale.
                    if key in seen or abs(time.time() * 1000 + skew - (req.get("t") or 0)) > 30000:
                        continue
                    seen.add(key)
                    log.info("request %s", req)
                    handle(req)
        except Exception as e:
            log.warning("listen: %s; retrying", e)
        time.sleep(3)


def refresher():
    while True:
        try:
            push_library()
            if _last_pushed["status"] is None:
                push_status("")
        except Exception as e:
            log.warning("refresh: %s", e)
        time.sleep(CFG["refresh_minutes"] * 60)


def main():
    if "--list" in sys.argv:
        for g in build_library():
            print(g["id"], "|", g["title"], "|", g["last"], g["mins"], "|", bool(g.get("cover")), bool(g.get("hero")))
        return
    # Uma cópia só (o atalho de inicialização e um clique manual não podem rodar juntos).
    ctypes.windll.kernel32.CreateMutexW(None, False, "CentralDeJogosTV")
    if ctypes.windll.kernel32.GetLastError() == 183:   # ERROR_ALREADY_EXISTS
        return
    log.info("agent start")
    threading.Thread(target=refresher, daemon=True).start()
    if CFG["media_root"] and os.path.isdir(CFG["media_root"]):
        try:
            import mediaserver      # add-on "Meu PC": os vídeos da pasta como fonte na TV
            mediaserver.start(CFG["media_root"], os.path.join(HERE, "media_ids.json"), CFG["tv_host"], CFG["media_port"])
        except Exception as e:
            log.exception("meu pc: %s", e)
    listen()


if __name__ == "__main__":
    main()
