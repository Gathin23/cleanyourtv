"""Servidor da prévia: entrega esta pasta e repassa imagens em /img?u=... com CORS.

A página precisa ler os pixels da arte de fundo para saber se ela é clara (colagens com
bordas brancas). Os servidores de imagem não mandam CORS, então a leitura passa por aqui.
Na TV quem faz esse papel é a ponte Java (WebHomeActivity.fetch).
"""
import http.server
import os
import socketserver
import sys
import urllib.parse
import urllib.request

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 5178
ROOT = os.path.dirname(os.path.abspath(__file__))
ALLOWED = ('https://images.metahub.space/', 'https://episodes.metahub.space/', 'https://cdn.cloudflare.steamstatic.com/')


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def do_GET(self):
        if not self.path.startswith('/img?'):
            return super().do_GET()
        u = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query).get('u', [''])[0]
        if not u.startswith(ALLOWED):
            return self.send_error(400)
        try:
            with urllib.request.urlopen(urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'}), timeout=15) as r:
                body, ctype = r.read(), r.headers.get('Content-Type', 'image/jpeg')
        except Exception:
            return self.send_error(404)
        self.send_response(200)
        self.send_header('Content-Type', ctype)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'max-age=86400')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        # A prévia muda o tempo todo: sem cache para a página e os ícones (as imagens do /img mandam o próprio).
        if not self.path.startswith('/img?'):
            self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def log_message(self, *a):
        pass


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


if __name__ == '__main__':
    Server(('127.0.0.1', PORT), Handler).serve_forever()
