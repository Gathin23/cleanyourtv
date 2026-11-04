#!/usr/bin/env python3
"""Keeps the TV healthy with an AI model, inside hard limits.

    python tv_ai_agent.py --setup [--lan]         open the page where the AI key and settings are entered
    python tv_ai_agent.py [tv ip]                 look and propose only (dry run, the default)
    python tv_ai_agent.py [tv ip] --apply         let the owner apply the proposal from the TV screen
    python tv_ai_agent.py [tv ip] --apply --auto  skip the "apply?" question for low-risk changes (the confirm-or-undo
                                                  countdown still runs)
    python tv_ai_agent.py [tv ip] --watch --apply stay running and answer the "Ask the assistant" row on the TV
    --revert-seconds N                            how long the owner has to confirm before everything is undone (60)
    python tv_ai_agent.py [tv ip] --details       send the add-on the full process table (name, uptime, CPU, RAM)
    python tv_ai_agent.py [tv ip] --show-payload  print exactly what is sent to the AI, then stop
    python tv_ai_agent.py [tv ip] --mock reply.json   use a saved AI reply instead of calling the API (tests)

What it does: reads a small snapshot from the TV over ADB (memory, the biggest processes, storage, which
suggested packages are still on), sends it to the AI, and gets back a list of actions. The AI never types a
command. It can only pick from three actions, and the code decides whether each one is allowed:

    disable_package  only a package named in res/raw/suggested.txt, never a protected one, at most --max a run
    force_stop       an app that is running; not a protected package, not the launcher; it just closes the app
    trim_caches      asks Android to drop cached data; the data comes back by itself

The answer is shown on the TV. The owner presses Apply there; the screen then says how many seconds there are to
press "All good", and if that time runs out every package is put back, by a watchdog running on the TV itself
(it survives this PC going away). A failed health check right after a change also puts everything back. Each run is written to journal/ with the exact
undo command. The AI key comes from the environment (ANTHROPIC_API_KEY) or, better, from `--setup`: a web page on this PC where it
is typed once and saved encrypted for this Windows user (never sent to the TV).

Sent to the AI: the Android version, the memory numbers, process names and sizes, and package names with
their on/off state. Nothing about the network, accounts, Wi-Fi names, IP addresses or what is watched.
"""
import argparse
import base64
import json
import os
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
RAW = os.path.join(HERE, "app", "src", "main", "res", "raw")
JOURNAL = os.path.join(HERE, "journal")
ADDON = "com.mauro.tv.monitor"
LAUNCHERS = ("com.gathin.tvhome", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx")

# Never touched by any action, whatever the AI says (AGENTS.md: what the TV needs to boot and work).
PROTECTED = [re.compile(p) for p in (
    r"^android$", r"^android\.ext\.(services|shared)$",
    r"^com\.android\.", r"^com\.google\.android\.(gms|gsf|vending|permissioncontroller|packageinstaller|ext\.|"
    r"modulemetadata|overlay\.modules|inputmethod|tungsten|apps\.tv\.launcherx|tvlauncher|webview)",
    r"^com\.google\.android\.tvlauncher$", r"^com\.mediatek\.", r"^com\.cltv\.mal$",
    r"factory", r"hkcfac", r"\.fac\.", r"^com\.gathin\.tvhome$", re.escape(ADDON),
    r"^org\.droidtv\.(xtv|fastip|eum|playtv|channels|settings|tvsystemui|tvpowermanagerservice|GlobalKey|epgdata|"
    r"tv\.tptvservice|usbbreakin|ambihuecommon|exoplayerservice|contentexplorer|dlna|oad|ci|teletext|euinstaller)",
)]


def protected(pkg):
    return any(p.search(pkg) for p in PROTECTED)


class Tv:
    def __init__(self, host):
        self.serial = host if ":" in host else host + ":5555"
        self.host = host.split(":")[0]
        self.logcat = None                  # the log command, once probed; see log_command()

    def sh(self, command, timeout=40):
        r = subprocess.run(["adb", "-s", self.serial, "shell", command], capture_output=True, text=True,
                           timeout=timeout, encoding="utf-8", errors="replace")
        return r.stdout.strip()

    def ok(self):
        try:
            return self.sh("echo up", 10) == "up"
        except (OSError, subprocess.TimeoutExpired):
            return False


def rows(name):
    out = []
    with open(os.path.join(RAW, name), encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("|", 2)
            if len(p) == 3 and p[0]:
                out.append(p)
    return out


def uptime_hours(tv):
    try:
        return round(float(tv.sh("cat /proc/uptime").split()[0]) / 3600, 1)
    except (ValueError, IndexError):
        return None


def snapshot(tv):
    meminfo = tv.sh("cat /proc/meminfo")
    mem = {}
    for key in ("MemTotal", "MemAvailable", "SwapTotal", "SwapFree"):
        m = re.search(key + r":\s+(\d+)", meminfo)
        mem[key + "_MB"] = int(m.group(1)) // 1024 if m else None
    biggest = []
    for line in tv.sh("ps -A -o RSS,NAME | sort -n -r | head -25").splitlines():
        parts = line.split(None, 1)
        if len(parts) == 2 and parts[0].isdigit():
            biggest.append({"mb": int(parts[0]) // 1024, "name": parts[1][:80]})
    df = tv.sh("df /data | tail -1").split()
    suggested = []
    for pkg, risk, why in rows("suggested.txt"):
        enabled = tv.sh("pm list packages -e " + pkg)
        suggested.append({"package": pkg, "risk": risk, "what": why.strip(),
                          "state": "on" if ("package:" + pkg) in enabled.splitlines() else "off_or_missing"})
    flagged_on = []
    for pkg, _, _ in rows("flagged.txt"):
        if ("package:" + pkg) in tv.sh("pm list packages -e " + pkg).splitlines():
            flagged_on.append(pkg)
    return {
        "language": tv.sh("getprop persist.sys.locale") or tv.sh("getprop ro.product.locale") or "en",
        "android": tv.sh("getprop ro.build.version.release"),
        "model": tv.sh("getprop ro.product.model"),
        "uptime_hours": uptime_hours(tv),
        "memory": mem,
        "data_partition_kb": {"size": int(df[1]), "free": int(df[3])} if len(df) >= 4 and df[1].isdigit() else None,
        "biggest_processes": biggest,
        "debloat_list_packages_back_on": flagged_on,
        "suggested_packages": suggested,
    }


SYSTEM = """You help keep an Android TV healthy. You receive a small snapshot of the TV. Reply with ONE JSON object
and nothing else, in this shape:
{"summary": "one or two plain sentences for the owner",
 "findings": ["short plain observation", ...],
 "actions": [{"type": "disable_package" | "force_stop" | "trim_caches", "package": "name or empty",
              "risk": "low" | "med", "reason": "why, in one sentence"}]}
Write summary, findings and reasons in the language given by "language" in the snapshot (a BCP-47 tag); if you
are not sure, write English. Rules: be conservative; when the TV looks fine, return no actions. disable_package only for a package listed
under suggested_packages with state "on". force_stop only for a large background app that is not needed now.
trim_caches only when storage is below about 15 percent free. Never act on system, Google Play, input, tuner,
launcher or settings packages. At most 5 actions. Anything else you want to say goes in findings."""


CONFIG_DIR = os.path.join(os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), ".config"), "tv-monitor")
CONFIG_PATH = os.path.join(CONFIG_DIR, "config.json")


def protect(secret):
    """Windows: encrypted for this user account with DPAPI. Elsewhere: a file only this user can read."""
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes

        class Blob(ctypes.Structure):
            _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

        raw = secret.encode("utf-8")
        src = Blob(len(raw), ctypes.cast(ctypes.create_string_buffer(raw, len(raw)), ctypes.POINTER(ctypes.c_char)))
        out = Blob()
        if not ctypes.windll.crypt32.CryptProtectData(ctypes.byref(src), "tv-monitor", None, None, None, 0, ctypes.byref(out)):
            raise OSError("Windows could not protect the key")
        data = ctypes.string_at(out.pbData, out.cbData)
        ctypes.windll.kernel32.LocalFree(out.pbData)
        return "dpapi:" + base64.b64encode(data).decode()
    return "plain:" + base64.b64encode(secret.encode("utf-8")).decode()


def unprotect(stored):
    kind, _, body = stored.partition(":")
    data = base64.b64decode(body)
    if kind == "dpapi" and os.name == "nt":
        import ctypes
        from ctypes import wintypes

        class Blob(ctypes.Structure):
            _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

        src = Blob(len(data), ctypes.cast(ctypes.create_string_buffer(data, len(data)), ctypes.POINTER(ctypes.c_char)))
        out = Blob()
        if not ctypes.windll.crypt32.CryptUnprotectData(ctypes.byref(src), None, None, None, None, 0, ctypes.byref(out)):
            raise OSError("Windows could not open the saved key: it was saved by another user or PC")
        plain = ctypes.string_at(out.pbData, out.cbData)
        ctypes.windll.kernel32.LocalFree(out.pbData)
        return plain.decode("utf-8")
    return data.decode("utf-8")


def load_config():
    try:
        with open(CONFIG_PATH, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_config(cfg):
    os.makedirs(CONFIG_DIR, exist_ok=True)
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)
    try:
        os.chmod(CONFIG_PATH, 0o600)
    except OSError:
        pass


def api_key():
    """The environment wins; otherwise the key saved from the setup page."""
    key = os.environ.get("ANTHROPIC_API_KEY")
    if key:
        return key
    stored = load_config().get("key")
    return unprotect(stored) if stored else None


PAGE = {
    "en": {"title": "TV assistant: connect your AI", "url": "API address", "model": "Model", "key": "API key",
           "host": "TV address", "secs": "Seconds to confirm a change before it is undone", "save": "Save and test",
           "note": "The key is stored encrypted for your user account on this PC only. It is never sent to the TV.",
           "lan": "Warning: this page is open to your network over plain HTTP, so the key crosses it unencrypted. Use it only on a network you trust, and close it as soon as you save.",
           "ok": "Saved. Test call: ", "bad": "Saved, but the test call failed: ", "keep": "Leave the key empty to keep the saved one."},
    "it": {"title": "Assistente TV: collega la tua AI", "url": "Indirizzo API", "model": "Modello", "key": "Chiave API",
           "host": "Indirizzo del TV", "secs": "Secondi per confermare una modifica prima che venga annullata",
           "save": "Salva e prova",
           "note": "La chiave viene salvata cifrata per il tuo account su questo PC. Non viene mai inviata al TV.",
           "lan": "Attenzione: questa pagina è aperta alla rete in HTTP semplice, quindi la chiave viaggia in chiaro. Usala solo su una rete fidata e chiudila appena salvi.",
           "ok": "Salvato. Chiamata di prova: ", "bad": "Salvato, ma la chiamata di prova è fallita: ",
           "keep": "Lascia vuota la chiave per tenere quella salvata."},
}


def test_call(cfg):
    """One tiny request to prove the key and address work. Returns (ok, message)."""
    try:
        key = api_key()
        if not key:
            return False, "no key"
        body = json.dumps({"model": cfg.get("model") or "claude-sonnet-5-5", "max_tokens": 8,
                           "messages": [{"role": "user", "content": "Reply with the word ok."}]}).encode()
        req = urllib.request.Request(cfg.get("url") or "https://api.anthropic.com/v1/messages", data=body,
                                     headers={"x-api-key": key, "anthropic-version": "2023-06-01",
                                              "content-type": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as r:
            json.load(r)
        return True, "ok"
    except urllib.error.HTTPError as e:
        return False, "HTTP %d" % e.code
    except (OSError, ValueError) as e:
        return False, str(e)[:120]


def setup_web(lan, port):
    """A one-page form to enter the AI key from a comfortable keyboard, served by this PC. By default it
    listens on this PC only; --lan opens it to the network behind a one-time token in the address."""
    import html
    import http.server
    import secrets
    import threading
    import urllib.parse

    token = secrets.token_urlsafe(16)
    done = threading.Event()
    cfg0 = load_config()

    def strings(handler):
        lang = (handler.headers.get("Accept-Language") or "en")[:2].lower()
        return PAGE.get(lang, PAGE["en"])

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *a):           # the request lines would carry the token; stay quiet
            pass

        def send(self, code, text):
            data = text.encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Referrer-Policy", "no-referrer")
            self.end_headers()
            self.wfile.write(data)

        def form(self, s, message=""):
            c = load_config()
            e = html.escape
            warn = "<p class=w>%s</p>" % e(s["lan"]) if lan else ""
            return ("<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                    "<title>%s</title><style>body{font:16px system-ui;background:#0b0b0d;color:#eee;max-width:34rem;margin:2rem auto;padding:0 1rem}"
                    "label{display:block;margin:1rem 0 .25rem;color:#aaa}input{width:100%%;box-sizing:border-box;padding:.6rem;border-radius:8px;border:1px solid #444;background:#151518;color:#eee}"
                    "button{margin-top:1.5rem;padding:.7rem 1.2rem;border:0;border-radius:8px;background:#34c759;color:#000;font-size:1rem}"
                    ".w{color:#ff9f0a}.m{color:#34c759}small{color:#888}</style><h1>%s</h1>%s<p class=m>%s</p>"
                    "<form method=post action=/save><input type=hidden name=t value='%s'>"
                    "<label>%s</label><input name=url value='%s'><label>%s</label><input name=model value='%s'>"
                    "<label>%s</label><input name=key type=password autocomplete=off placeholder='%s'>"
                    "<label>%s</label><input name=host value='%s'><label>%s</label><input name=secs type=number min=20 max=600 value='%s'>"
                    "<button>%s</button></form><p><small>%s<br>%s</small></p>") % (
                e(s["title"]), e(s["title"]), warn, e(message), e(token), e(s["url"]),
                e(c.get("url") or "https://api.anthropic.com/v1/messages"), e(s["model"]), e(c.get("model") or "claude-sonnet-5-5"),
                e(s["key"]), e("•••••• (saved)" if c.get("key") else "sk-..."), e(s["host"]), e(c.get("host") or ""),
                e(s["secs"]), e(str(c.get("revert_seconds") or 60)), e(s["save"]), e(s["note"]), e(s["keep"]))

        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if q.get("t", [""])[0] != token:
                return self.send(403, "Open the exact address printed in the terminal.")
            self.send(200, self.form(strings(self)))

        def do_POST(self):
            s = strings(self)
            n = min(int(self.headers.get("Content-Length") or 0), 4096)
            f = urllib.parse.parse_qs(self.rfile.read(n).decode("utf-8", "replace"))
            if f.get("t", [""])[0] != token or self.path != "/save":
                return self.send(403, "Forbidden")
            val = lambda k: f.get(k, [""])[0].strip()
            url, model, host = val("url"), val("model"), val("host")
            if not (url.startswith("https://") or url.startswith("http://127.0.0.1") or url.startswith("http://localhost")):
                return self.send(400, self.form(s, "The API address must start with https://"))
            if not re.fullmatch(r"[A-Za-z0-9_.:\-/]{1,80}", model):
                return self.send(400, self.form(s, "Model name not valid"))
            if host and not re.fullmatch(r"[A-Za-z0-9_.\-]+(:\d{1,5})?", host):
                return self.send(400, self.form(s, "TV address not valid"))
            cfg = load_config()
            cfg.update(url=url, model=model, host=host or cfg.get("host", ""))
            try:
                cfg["revert_seconds"] = max(20, min(int(val("secs") or 60), 600))
            except ValueError:
                cfg["revert_seconds"] = 60
            if val("key"):
                cfg["key"] = protect(val("key"))
            save_config(cfg)
            ok, msg = test_call(cfg)
            done.set()
            self.send(200, self.form(s, (s["ok"] if ok else s["bad"]) + msg))

    bind = "0.0.0.0" if lan else "127.0.0.1"
    server = http.server.HTTPServer((bind, port), Handler)
    where = "127.0.0.1"
    if lan:
        import socket
        try:
            where = socket.gethostbyname(socket.gethostname())
        except OSError:
            where = "THIS-PC-ADDRESS"
    print("Open this address in a browser%s:\n\n    http://%s:%d/?t=%s\n" % (
        " on any device in your network" if lan else " on this PC", where, port, token))
    if lan:
        print("WARNING: plain HTTP on your network: the key is readable by anyone who can watch it. Close this as soon as you save.")
    print("Waiting for the form. Ctrl+C to cancel.")
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        done.wait(600)
    except KeyboardInterrupt:
        pass
    server.shutdown()
    print("Saved to %s" % CONFIG_PATH if done.is_set() else "Nothing saved.")


def ask_ai(payload, mock):
    if mock:
        with open(mock, encoding="utf-8") as f:
            return json.load(f)
    key = api_key()
    if not key:
        sys.exit("No AI key yet. Run:  python tv_ai_agent.py --setup   (or set ANTHROPIC_API_KEY in this window).")
    cfg = load_config()
    body = json.dumps({"model": os.environ.get("TV_AI_MODEL") or cfg.get("model") or "claude-sonnet-5-5", "max_tokens": 1500,
                       "system": SYSTEM, "messages": [{"role": "user", "content": json.dumps(payload)}]}).encode()
    req = urllib.request.Request(os.environ.get("TV_AI_URL") or cfg.get("url") or "https://api.anthropic.com/v1/messages", data=body,
                                 headers={"x-api-key": key, "anthropic-version": "2023-06-01",
                                          "content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            answer = json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit("The AI API answered %d: %s" % (e.code, e.read().decode("utf-8", "replace")[:300]))
    except (urllib.error.URLError, ValueError) as e:
        sys.exit("Could not read the AI reply: %s" % e)
    # The reply is a list of blocks; the answer is the text one (a model may put other blocks, such as thinking, first).
    blocks = answer.get("content") if isinstance(answer, dict) else None
    text = "".join(b.get("text", "") for b in blocks or [] if isinstance(b, dict) and b.get("type") == "text")
    if not text:
        kinds = [b.get("type") for b in blocks or [] if isinstance(b, dict)]
        sys.exit("The AI reply had no text (blocks: %s, stop_reason: %s)." % (kinds or "none", answer.get("stop_reason") if isinstance(answer, dict) else "?"))
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        sys.exit("The AI did not return JSON.")
    try:
        reply = json.loads(m.group(0))
    except ValueError:
        sys.exit("The AI returned JSON that could not be read.")
    # Only the shape the guard expects gets through: an object, with lists of objects and strings.
    if not isinstance(reply, dict):
        sys.exit("The AI reply is not a JSON object.")
    actions = reply.get("actions")
    reply["actions"] = [a for a in actions if isinstance(a, dict)] if isinstance(actions, list) else []
    findings = reply.get("findings")
    reply["findings"] = [str(x) for x in findings if isinstance(x, (str, int, float))] if isinstance(findings, list) else []
    reply["summary"] = str(reply.get("summary", ""))
    return reply


def guard(reply, snap, max_actions):
    """Split the AI's actions into allowed and refused, with the reason for each refusal."""
    allowed_pkgs = {s["package"]: s for s in snap["suggested_packages"]}
    running = {p["name"].split(":")[0] for p in snap["biggest_processes"]}
    ok, refused, disabled = [], [], 0
    for a in reply.get("actions", [])[:10]:
        kind, pkg = str(a.get("type", "")), str(a.get("package", "")).strip()
        risk = "low" if a.get("risk") != "med" else "med"
        item = {"type": kind, "package": pkg, "risk": risk, "reason": str(a.get("reason", ""))[:200]}
        why = None
        if kind == "trim_caches":
            pass
        elif kind in ("disable_package", "force_stop"):
            if not re.fullmatch(r"[A-Za-z0-9_.]+", pkg):
                why = "not a package name"
            elif protected(pkg):
                why = "protected package"
            elif kind == "disable_package" and (pkg not in allowed_pkgs or allowed_pkgs[pkg]["state"] != "on"):
                why = "not on the suggested list, or already off"
            elif kind == "force_stop" and pkg not in running:
                why = "not among the running apps"
            elif kind == "disable_package":
                item["risk"] = allowed_pkgs[pkg]["risk"]       # the list's risk wins over the AI's
                disabled += 1
                if disabled > max_actions:
                    why = "more than --max packages in one run"
        else:
            why = "action type not allowed"
        (refused if why else ok).append(dict(item, refused=why) if why else item)
    return ok, refused


def health(tv):
    """True when the TV still answers, Home is a launcher, boot finished and no app is in an error dialog."""
    if not tv.ok():
        return False, "ADB does not answer"
    if tv.sh("getprop sys.boot_completed") != "1":
        return False, "boot not completed"
    home = tv.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1")
    if not any(home.startswith(l) for l in LAUNCHERS):
        return False, "Home resolves to " + home
    focus = tv.sh("dumpsys window | grep mCurrentFocus")
    if "Application Error" in focus or "Not Responding" in focus:
        return False, "error dialog: " + focus
    return True, "ok"


def run_tool(script, *args, host):
    env = dict(os.environ, TV_HOST=host)
    if os.name == "nt":
        cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", os.path.join(REPO, "tools", script + ".ps1")] + list(args)
    else:
        cmd = ["sh", os.path.join(REPO, "tools", script + ".sh")] + list(args)
    return subprocess.run(cmd, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")


TAG = "TvMonitor"


def log_command(tv):
    """The command that reads the add-on's log lines. Any app can write any tag to logcat, so where the TV allows it
    the read is limited to the add-on's own uid (logcat --uid): then no other app on the TV can forge the owner's
    answer, a request or a restart. The filter is probed once; a TV without it gets a warning and the plain read."""
    if tv.logcat is None:
        plain = "logcat -d -s %s:I" % TAG
        tv.logcat = plain
        m = re.search(r"uid:(\d+)", tv.sh("pm list packages -U " + ADDON))
        if m:
            narrow = "logcat -d --uid=%s -s %s:I" % (m.group(1), TAG)
            marker = "PROBE %d" % int(time.time())
            tv.sh("log -t %s %s" % (TAG, marker))             # written by the shell: a working filter drops it
            out = tv.sh(narrow + " 2>/dev/null; echo __rc=$?")
            if out.endswith("__rc=0") and marker not in out and marker in tv.sh(plain):
                tv.logcat = narrow
        if tv.logcat == plain:
            print("WARNING: this TV's logcat cannot filter by app. Another app on the TV could forge the owner's "
                  "answers; keep only apps you trust on it.")
    return tv.logcat


def decisions(tv):
    """Lines the add-on logged, as (id, word): 'DECISION <id> apply|cancel|keep|revert'."""
    out = tv.sh(log_command(tv))
    return re.findall(r"DECISION (\S+) (\w+)", out)


def await_decision(tv, ident, words, timeout):
    """Poll the TV's log for the owner's answer to this screen; None when the time runs out."""
    end = time.time() + timeout
    while time.time() < end:
        for did, word in decisions(tv):
            if did == ident and word in words:
                return word
        time.sleep(2)
    return None


def tv_now(tv):
    """The TV's own clock, in seconds: it can be a minute off this PC's, and the add-on counts down on its own."""
    try:
        return int(tv.sh("date +%s"))
    except ValueError:
        return int(time.time())


def push(tv, state, ident, summary, findings, actions, **extra):
    """Send the add-on what to show now: state is proposal, applying or result."""
    report = dict({"state": state, "id": ident, "at": tv_now(tv), "summary": str(summary)[:300],
                   "findings": [str(x)[:160] for x in findings[:6]],
                   "actions": [{"type": a["type"], "package": a.get("package", ""), "status": a["status"],
                                "reason": a.get("reason", "")[:120]} for a in actions]}, **extra)
    data = base64.b64encode(json.dumps(report, ensure_ascii=False).encode("utf-8")).decode()
    while len(data) > 12000 and report["findings"]:
        report["findings"].pop()
        data = base64.b64encode(json.dumps(report, ensure_ascii=False).encode("utf-8")).decode()
    tv.sh("am broadcast -n %s/.ReportReceiver -a %s.REPORT --es b64 %s" % (ADDON, ADDON, data))


def start_watchdog(tv, ident, packages, seconds, stamp):
    """Runs on the TV, not here: after `seconds` without a 'keep' it enables the packages again, and an
    'undo now' ends the wait early. It survives this PC and the ADB link going away. Returns False when it could
    not be started, so the caller does not open a countdown that nothing would honour."""
    script = (
        "i=0\n"
        "while [ $i -lt %d ]; do\n"
        "  l=$(%s)\n"
        "  case \"$l\" in *\"DECISION %s keep\"*) exit 0;; esac\n"
        "  case \"$l\" in *\"DECISION %s revert\"*) break;; esac\n"
        "  sleep 1; i=$((i+1))\n"
        "done\n"
        "for p in %s; do pm enable $p; done\n"
        "log -t %s \"WATCHDOG reverted %s\"\n" % (seconds, log_command(tv), ident, ident, " ".join(packages), TAG, ident))
    os.makedirs(JOURNAL, exist_ok=True)
    local = os.path.join(JOURNAL, stamp + "-watchdog.sh")
    with open(local, "w", encoding="utf-8", newline="\n") as f:
        f.write(script)
    remote = "/data/local/tmp/tvmon-wd-%s.sh" % stamp
    pushed = subprocess.run(["adb", "-s", tv.serial, "push", local, remote], capture_output=True)
    if pushed.returncode != 0 or tv.sh("[ -s %s ] && echo ok" % remote) != "ok":
        return False
    tv.sh("nohup sh %s >/dev/null 2>&1 &" % remote)
    time.sleep(1)
    return ("tvmon-wd-" + stamp) in tv.sh("ps -A -o PID,ARGS | grep tvmon-wd-%s | grep -v grep" % stamp)


def apply_actions(tv, actions, stamp):
    """Do the approved actions. Returns (results, disabled batch file or None)."""
    results, to_disable = [], []
    for a in actions:
        if a["type"] == "force_stop":
            tv.sh("am force-stop " + a["package"])
            results.append(dict(a, status="applied"))
        elif a["type"] == "trim_caches":
            df = tv.sh("df /data | tail -1").split()
            tv.sh("pm trim-caches %d" % ((int(df[3]) + 150 * 1024) * 1024))   # free space now, plus about 150 MB
            results.append(dict(a, status="applied"))
        elif a["type"] == "disable_package":
            to_disable.append(a)
    batch = None
    if to_disable:
        os.makedirs(JOURNAL, exist_ok=True)
        batch = os.path.join(JOURNAL, stamp + "-disable.txt")
        with open(batch, "w", encoding="utf-8", newline="\n") as f:
            f.write("# Disabled by tv_ai_agent.py on %s\n" % stamp)
            f.writelines(a["package"] + "\n" for a in to_disable)
        run_tool("disable", batch, host=tv.host)
        results.extend(dict(a, status="applied", undo="tools\\restore.ps1 " + batch) for a in to_disable)
    return results, batch


def etime_seconds(text):
    """ps ETIME ('1-07:16:38', '07:16:38', '16:38') to seconds."""
    days = 0
    if "-" in text:
        d, text = text.split("-", 1)
        days = int(d)
    parts = [int(x) for x in text.split(":")]
    while len(parts) < 3:
        parts.insert(0, 0)
    return days * 86400 + parts[0] * 3600 + parts[1] * 60 + parts[2]


def collect_details(tv):
    """Every process on the TV as [name, uptime_seconds, cpu_percent, ram_kb], from ps and top over ADB.
    This is what an app cannot read about other apps, which is why the PC does it."""
    cpu = {}
    for line in tv.sh("top -b -n 1 -m 600", 60).splitlines():
        w = line.split()
        if len(w) >= 9 and w[0].isdigit():
            try:
                cpu[int(w[0])] = float(w[8] if re.fullmatch(r"[\d.]+", w[8]) else w[7])
            except ValueError:
                pass
    rows = []
    for line in tv.sh("ps -A -o PID,ETIME,RSS,NAME", 30).splitlines()[1:]:
        w = line.split(None, 3)
        if len(w) == 4 and w[0].isdigit() and w[2].isdigit():
            try:
                rows.append([w[3][:70], etime_seconds(w[1]), round(cpu.get(int(w[0]), 0.0), 1), int(w[2])])
            except ValueError:
                pass
    return rows


def push_details(tv, rows):
    """Send the table compressed (zlib, then base64): a few hundred rows fit under the broadcast limits."""
    import zlib
    payload = json.dumps({"at": tv_now(tv), "rows": rows}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    data = base64.b64encode(zlib.compress(payload, 9)).decode()
    while len(data) > 60000 and rows:
        rows = sorted(rows, key=lambda r: -(r[3] + r[2] * 1000))[: int(len(rows) * 0.8)]
        payload = json.dumps({"at": tv_now(tv), "rows": rows}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        data = base64.b64encode(zlib.compress(payload, 9)).decode()
    tv.sh("am broadcast -n %s/.ReportReceiver -a %s.REPORT --es z64 %s" % (ADDON, ADDON, data), 60)
    return len(rows), len(data)


def reboot_tv(tv):
    """Restart the TV. Package changes are written to disk about ten seconds after they are made, so wait first;
    `adb reboot` over TCP can hang on the dropped connection, so it is given a few seconds and the server is reset."""
    tv.sh("sync")
    time.sleep(12)
    try:
        subprocess.run(["adb", "-s", tv.serial, "reboot"], capture_output=True, timeout=8)
    except subprocess.TimeoutExpired:
        pass
    subprocess.run(["adb", "kill-server"], capture_output=True)
    print("Restart sent to the TV.")


def question(snap):
    """What was asked, in the few numbers the add-on keeps in its history next to the answer."""
    mem, part = snap.get("memory") or {}, snap.get("data_partition_kb")
    return {"ram": [mem.get("MemAvailable_MB"), mem.get("MemTotal_MB")],
            "disk": [part["free"], part["size"]] if part else None,
            "flagged": len(snap.get("debloat_list_packages_back_on") or []),
            "top": [p["name"] for p in (snap.get("biggest_processes") or [])[:3]]}


def session(tv, args):
    """One round: look, ask the AI, show the proposal on the TV, apply on the owner's word, then the
    confirm-or-undo countdown."""
    stamp = time.strftime("%Y%m%d-%H%M%S")
    nonce = secrets.token_hex(4)                             # ids cannot be guessed from the clock
    snap = snapshot(tv)
    q = question(snap)
    try:
        reply = ask_ai(snap, args.mock)
    except SystemExit as e:                                  # no key, no network, unreadable reply: say so on the TV
        push(tv, "result", "r" + stamp + nonce, str(e) or "?", [], [], outcome="error", q=q)
        raise
    ok, refused = guard(reply, snap, args.max)
    summary, findings = reply.get("summary", ""), [str(x) for x in reply.get("findings", [])]
    print("AI summary:", summary)
    for f in findings[:8]:
        print("  -", f)
    for a in ok:
        print("  allowed: %-16s %-40s %s" % (a["type"], a["package"], a["reason"]))
    for a in refused:
        print("  REFUSED: %-16s %-40s (%s)" % (a["type"], a["package"], a["refused"]))
    journal = {"snapshot": snap, "reply": reply, "refused": refused}
    os.makedirs(JOURNAL, exist_ok=True)

    def finish(state_actions, outcome):
        push(tv, "result", "r" + stamp + nonce, summary, findings, state_actions, outcome=outcome, q=q,
             refused=[{"type": a["type"], "package": a["package"], "why": a["refused"]} for a in refused])
        with open(os.path.join(JOURNAL, stamp + ".json"), "w", encoding="utf-8") as f:
            json.dump(dict(journal, outcome=outcome, actions=state_actions), f, indent=2, ensure_ascii=False)
        print("Outcome:", outcome, "  Journal: journal\\%s.json" % stamp)

    if not ok:
        return finish([], "nothing_to_do")
    proposed = [dict(a, status="proposed") for a in ok]
    pid = "p" + stamp + nonce
    skip_question = args.auto and args.apply and all(a["risk"] == "low" for a in ok)
    if not skip_question:
        push(tv, "proposal", pid, summary, findings, proposed, revert_seconds=args.revert_seconds,
             can_apply=bool(args.apply))
        tv.sh("am start -n %s/.MonitorActivity" % ADDON)
        print("Proposal shown on the TV; waiting up to %d s for the owner." % args.wait)
        answer = await_decision(tv, pid, ("apply", "cancel"), args.wait)
        if answer != "apply" or not args.apply:
            return finish([dict(a, status="cancelled") for a in ok], "cancelled")
    results, batch = apply_actions(tv, ok, stamp)
    if not batch:
        return finish(results, "applied")

    time.sleep(12)                                           # Android writes package state after ~10 s
    good, why = health(tv)
    if not good:
        run_tool("restore", batch, host=tv.host)
        return finish([dict(a, status="reverted" if a["type"] == "disable_package" else a["status"], note=why)
                       for a in results], "reverted_health_check")
    packages = [a["package"] for a in results if a["type"] == "disable_package"]
    aid = pid + "k"
    deadline = tv_now(tv) + args.revert_seconds
    if not start_watchdog(tv, aid, packages, args.revert_seconds, stamp):
        run_tool("restore", batch, host=tv.host)              # no safety net on the TV: do not leave the change in place
        return finish([dict(a, status="reverted" if a["type"] == "disable_package" else a["status"],
                            note="watchdog did not start") for a in results], "reverted_no_watchdog")
    push(tv, "applying", aid, summary, findings, results, revert_seconds=args.revert_seconds, deadline=deadline)
    tv.sh("am start -n %s/.MonitorActivity" % ADDON)
    print("Changes applied. The owner has %d s to press All good on the TV." % args.revert_seconds)
    answer = await_decision(tv, aid, ("keep", "revert"), args.revert_seconds + 5)
    if answer == "keep":
        return finish([dict(a, status="kept") for a in results], "kept")
    run_tool("restore", batch, host=tv.host)                  # the watchdog has done it already; this is the belt
    time.sleep(3)
    return finish([dict(a, status="reverted" if a["type"] == "disable_package" else a["status"]) for a in results],
                  "reverted_by_owner" if answer == "revert" else "reverted_timeout")


def main():
    for stream in (sys.stdout, sys.stderr):                  # accented answers print the same in any console
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("host", nargs="?", default=None)
    ap.add_argument("--setup", action="store_true", help="open a web page on this PC to enter the AI key and settings")
    ap.add_argument("--lan", action="store_true", help="with --setup: reachable from other devices on the network (plain HTTP)")
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--auto", action="store_true")
    ap.add_argument("--watch", action="store_true")
    ap.add_argument("--max", type=int, default=3)
    ap.add_argument("--revert-seconds", type=int, default=None)
    ap.add_argument("--wait", type=int, default=300, help="seconds to wait for the owner's answer to the proposal")
    ap.add_argument("--show-payload", action="store_true")
    ap.add_argument("--reboot", action="store_true", help="restart the TV now")
    ap.add_argument("--details", action="store_true", help="send the add-on the full process table once and stop")
    ap.add_argument("--mock")
    args = ap.parse_args()
    if args.setup:
        setup_web(args.lan, args.port)
        return
    cfg = load_config()
    args.host = args.host or cfg.get("host")
    if not args.host:
        sys.exit("Give the TV address, e.g.  python tv_ai_agent.py 192.168.1.50  (or save it with --setup).")
    args.revert_seconds = max(20, min(args.revert_seconds or int(cfg.get("revert_seconds") or 60), 600))

    tv = Tv(args.host)
    if not tv.ok() and not args.watch:
        sys.exit("Cannot reach the TV over ADB at %s. Turn USB debugging on first." % tv.serial)
    if args.show_payload:
        print(json.dumps(snapshot(tv), indent=2))
        return
    if args.reboot:
        reboot_tv(tv)
        return
    if args.details:
        n, size = push_details(tv, collect_details(tv))
        print("Sent %d processes (%d bytes) to the add-on." % (n, size))
        return
    if not args.watch:
        session(tv, args)
        return
    print("Watching for 'Ask the assistant', 'Details' and 'Restart' on the TV. Ctrl+C to stop.")
    # USB debugging may be off right now: the add-on turns it on when the owner asks, so keep trying to connect.
    # A request is only read over ADB, so one left in the log from before we started is told apart by its age.
    seen = seen_details = seen_reboot = None
    while True:
        try:
            if not tv.ok():
                subprocess.run(["adb", "connect", tv.serial], capture_output=True, timeout=10)
                if not tv.ok():
                    time.sleep(3)
                    continue
            log = tv.sh(log_command(tv))
            if seen is None:
                tv_ms = int(tv.sh("date +%s").split()[0]) * 1000
                seen = {r for r in re.findall(r"REQUEST (\d+)", log) if int(r) < tv_ms - 120000}
                seen_details = set(re.findall(r"DETAILS (\d+)", log))
                seen_reboot = set(re.findall(r"REBOOT (\d+)", log))
        except (OSError, subprocess.TimeoutExpired, ValueError, IndexError):
            time.sleep(3)
            continue
        # One failed round (the TV went away mid-way, an odd reply) must not end the watch: say so and go on.
        try:
            for bid in re.findall(r"REBOOT (\d+)", log):
                if bid not in seen_reboot:
                    seen_reboot.add(bid)
                    reboot_tv(tv)
                    print("The TV is restarting; start --watch again once it is back and ADB is open.")
                    return
            for did in re.findall(r"DETAILS (\d+)", log):
                if did not in seen_details:
                    seen_details.add(did)
                    n, size = push_details(tv, collect_details(tv))
                    print("Details sent: %d processes." % n)
            for rid in re.findall(r"REQUEST (\d+)", log):
                if rid not in seen:
                    seen.add(rid)
                    try:
                        session(tv, args)
                    except SystemExit as e:
                        print(e)
        except (OSError, subprocess.TimeoutExpired, ValueError, KeyError, IndexError, TypeError) as e:
            print("Round failed: %s: %s" % (type(e).__name__, e))
            try:
                push(tv, "result", "e" + secrets.token_hex(4), "Error: %s" % type(e).__name__, [], [], outcome="error")
            except (OSError, subprocess.TimeoutExpired):
                pass
        time.sleep(3)


if __name__ == "__main__":
    main()
