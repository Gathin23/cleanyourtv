"""Add-on "Meu PC": os vídeos de uma pasta do PC viram mais uma fonte em Filmes e Séries na TV.

Fala o protocolo de add-on do Stremio (manifest / stream / subtitles), então a TV usa como qualquer outro:
  http://<ip do PC>:8765/manifest.json
Cada pasta de série é ligada ao título certo (id do IMDb) pela busca do Cinemeta; temporada e episódio saem
do nome do arquivo ("T02E05", "S2 Episódio 10", "2ª Temporada ... Episódio 01.02"). Uma legenda .srt com o
mesmo nome do vídeo vira legenda em português. Só a TV (tv_host) pode acessar; os arquivos servidos são
apenas os que estão no índice, nada fora da pasta.
"""
import json
import logging
import os
import re
import threading
import time
import urllib.parse
import urllib.request
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

log = logging.getLogger("agent")

VIDEO = (".mkv", ".mp4", ".avi", ".m4v", ".webm", ".mov")
MIN_SIZE = 20 * 1024 * 1024          # arquivos menores são propaganda de uploader, não episódio
CINEMETA = "https://v3-cinemeta.strem.io"


def _get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


def clean_title(name):
    """'Star Wars - The Clone Wars 2ª Temporada 720p (2009) Dual Áudio BluRay -- By Lucas Firmo' -> 'Star Wars The Clone Wars'"""
    t = re.split(r"\s--\s|\d+\s*ª\s*Temporada|\bTemporada\s*\d+|\bSeason\s*\d+|\bS\d+\b|\(\d{4}\)|\b\d{3,4}p\b",
                 name, flags=re.I)[0]
    t = re.sub(r"[_.\-]+", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def year_of(name):
    m = re.search(r"\((\d{4})\)", name)
    return m.group(1) if m else ""


def extras(path, root):
    """'720p · Dual Áudio' a partir do nome do arquivo e das pastas (a TV mostra qualidade e dublado)."""
    text = os.path.relpath(path, root)
    out = []
    m = re.search(r"\b(2160p|1080p|720p|480p)\b", text, re.I)
    if m:
        out.append(m.group(1).lower())
    if re.search(r"dual\s*[áa]udio", text, re.I):
        out.append("Dual Áudio")
    elif re.search(r"dublad", text, re.I):
        out.append("Dublado")
    return " · ".join(out) or "Arquivo do PC"


def episode_of(path, folder):
    """(temporada, [episódios]) do arquivo, ou None."""
    name = os.path.splitext(os.path.basename(path))[0]
    m = re.search(r"\b[ST](\d{1,2})\s*E(\d{1,3})\b", name, re.I)
    if m:
        return int(m.group(1)), [int(m.group(2))]
    m = re.search(r"\bS(\d{1,2})\b.*?Epis[oó]dio\s*(\d{1,3})", name, re.I)
    if m:
        return int(m.group(1)), [int(m.group(2))]
    m = re.search(r"(\d{1,2})\s*ª\s*Temporada", name + " " + folder, re.I)
    e = re.search(r"Epis[oó]dio\s*(\d{1,3})(?:\s*[.\-&]\s*(\d{1,3}))?", name, re.I)
    if m and e:
        eps = [int(e.group(1))] + ([int(e.group(2))] if e.group(2) else [])
        return int(m.group(1)), eps
    return None


class Library:
    def __init__(self, root, cache_path):
        self.root = root
        self.cache_path = cache_path
        self.lock = threading.Lock()
        self.stamp = None
        self.items = {}       # "tt123:2:5" ou "tt123" -> [arquivo, ...]
        self.files = {}       # token -> caminho
        try:
            self.ids = json.load(open(cache_path, encoding="utf-8"))
        except (OSError, ValueError):
            self.ids = {}     # nome da pasta -> id do IMDb ("" = não achado)

    def _stamp(self):
        s = []
        for dp, dns, fns in os.walk(self.root):
            s.append((dp, os.path.getmtime(dp), len(fns)))
        return s

    def _lookup(self, folder, kind):
        if folder in self.ids:
            return self.ids[folder]
        title, year = clean_title(folder), year_of(folder)
        imdb = ""
        try:
            metas = _get("%s/catalog/%s/top/search=%s.json" % (CINEMETA, kind, urllib.parse.quote(title))).get("metas", [])
            # Nome igual primeiro; o ano só desempata (em pasta de temporada o ano é o da temporada,
            # não o da série: "Clone Wars 3ª Temporada (2010)" não pode virar "The Clone" de 2010).
            want = norm(title)
            metas = sorted(metas, key=lambda m: (norm(m.get("name", "")) != want,
                                                 not (year and str(m.get("releaseInfo", "")).startswith(year))))
            imdb = metas[0]["id"] if metas else ""
            log.info("meu pc: %r -> %s (%s)", title, imdb, metas[0].get("name") if metas else "-")
        except Exception as e:
            log.warning("meu pc: busca %r: %s", title, e)
            return ""        # tenta de novo na próxima
        self.ids[folder] = imdb
        json.dump(self.ids, open(self.cache_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        return imdb

    def refresh(self):
        with self.lock:
            stamp = self._stamp()
            if stamp == self.stamp:
                return
            items, files = {}, {}
            for dp, _, fns in os.walk(self.root):
                for fn in sorted(fns):
                    path = os.path.join(dp, fn)
                    if not fn.lower().endswith(VIDEO) or os.path.getsize(path) < MIN_SIZE:
                        continue
                    rel = os.path.relpath(path, self.root)
                    parts = rel.split(os.sep)
                    is_series = parts[0].lower().startswith("serie")
                    folder = parts[1] if len(parts) > 2 else os.path.splitext(parts[-1])[0]
                    imdb = self._lookup(folder, "series" if is_series else "movie")
                    if not imdb:
                        continue
                    token = "%08x" % zlib.crc32(rel.encode("utf-8"))   # o mesmo entre reinícios
                    files[token] = path
                    if is_series:
                        se = episode_of(path, folder)
                        if not se:
                            continue
                        for ep in se[1]:
                            items.setdefault("%s:%d:%d" % (imdb, se[0], ep), []).append(token)
                    else:
                        items.setdefault(imdb, []).append(token)
            self.items, self.files, self.stamp = items, files, stamp
            log.info("meu pc: %d títulos/episódios, %d arquivos", len(items), len(files))

    def find(self, vid):
        self.refresh()
        return [(t, self.files[t]) for t in self.items.get(vid, [])]


def make_handler(lib, allowed, port):
    class H(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def _json(self, obj):
            body = json.dumps(obj).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _base(self):
            return "http://%s:%d" % (self.headers.get("Host", "").split(":")[0] or "127.0.0.1", port)

        def do_HEAD(self):
            self.do_GET(head=True)

        def do_GET(self, head=False):
            if self.client_address[0] not in allowed:
                self.send_error(403)
                return
            p = urllib.parse.unquote(self.path.split("?")[0])
            if p == "/manifest.json":
                return self._json({
                    "id": "local.meupc", "version": "1.0.0", "name": "Meu PC",
                    "description": "Vídeos da pasta %s" % lib.root,
                    "resources": ["stream", "subtitles"], "types": ["movie", "series"],
                    "idPrefixes": ["tt"], "catalogs": []})
            m = re.match(r"^/(stream|subtitles)/(movie|series)/([^/]+?)(?:/[^/]*)?\.json$", p)
            if m:
                found = lib.find(m.group(3))
                if m.group(1) == "stream":
                    return self._json({"streams": [{
                        "name": "Meu PC",
                        "title": "%s\n%s" % (os.path.basename(f), extras(f, lib.root)),
                        "description": "💾 %.2f GB" % (os.path.getsize(f) / 1e9),
                        "url": "%s/f/%s/%s" % (self._base(), t, urllib.parse.quote(os.path.basename(f))),
                        "behaviorHints": {"notWebReady": True, "bingeGroup": "meupc"},
                    } for t, f in found]})
                subs = []
                for t, f in found:
                    if os.path.isfile(os.path.splitext(f)[0] + ".srt"):
                        subs.append({"id": "meupc-" + t, "lang": "pt-BR", "url": "%s/s/%s.srt" % (self._base(), t)})
                return self._json({"subtitles": subs})
            m = re.match(r"^/(f|s)/([0-9a-f]{8})", p)
            if m and m.group(2) in lib.files:
                path = lib.files[m.group(2)]
                if m.group(1) == "s":
                    path = os.path.splitext(path)[0] + ".srt"
                return self._file(path, head)
            self.send_error(404)

        def _file(self, path, head):
            size = os.path.getsize(path)
            start, end = 0, size - 1
            rng = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
            if rng and (rng.group(1) or rng.group(2)):
                if rng.group(1):
                    start = int(rng.group(1))
                    end = int(rng.group(2)) if rng.group(2) else size - 1
                else:
                    start = size - int(rng.group(2))
                self.send_response(206)
                self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
            else:
                self.send_response(200)
            ext = os.path.splitext(path)[1].lower()
            ctype = {".mp4": "video/mp4", ".m4v": "video/mp4", ".mkv": "video/x-matroska", ".webm": "video/webm",
                     ".avi": "video/x-msvideo", ".srt": "application/x-subrip; charset=utf-8"}.get(ext, "application/octet-stream")
            self.send_header("Content-Type", ctype)
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            self.end_headers()
            if head:
                return
            with open(path, "rb") as fh:
                fh.seek(start)
                left = end - start + 1
                try:
                    while left > 0:
                        chunk = fh.read(min(1 << 20, left))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        left -= len(chunk)
                except (ConnectionError, OSError):
                    pass          # a TV fechou a conexão (pulou para outro ponto do vídeo)
    return H


def start(root, cache_path, tv_host, port=8765):
    lib = Library(root, cache_path)
    threading.Thread(target=lib.refresh, daemon=True).start()
    srv = ThreadingHTTPServer(("0.0.0.0", port), make_handler(lib, {tv_host, "127.0.0.1"}, port))
    srv.daemon_threads = True
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    log.info("meu pc: servindo %s na porta %d", root, port)
    return lib
