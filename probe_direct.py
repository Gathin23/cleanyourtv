import json, subprocess, urllib.request, urllib.parse, concurrent.futures as cf, sys
addons = json.load(open('addons.json', encoding='utf-8'))
titles = [('movie','tt0111161'),('movie','tt0468569'),('series','tt0903747:1:1'),('movie','tt15398776')]
UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Safari/537.36'
def get(url):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        return json.load(urllib.request.urlopen(req, timeout=25))
    except Exception as e:
        return None
def streams(a, t, i):
    base = a['url'].rsplit('/manifest.json', 1)[0]
    j = get(f"{base}/stream/{t}/{i}.json") or {}
    out = []
    for s in j.get('streams', []):
        if s.get('url') and not s.get('infoHash') and 'youtube' not in s['url'] and 'exceptions' not in s['url']:
            out.append((a['name'], s))
    return out[:3]
jobs = []
with cf.ThreadPoolExecutor(8) as ex:
    futs = [ex.submit(streams, a, t, i) for a in addons for (t, i) in titles]
    for f in futs: jobs += f.result()
print('direct streams found:', len(jobs), file=sys.stderr)
def probe(item):
    name, s = item
    hdrs = ((s.get('behaviorHints') or {}).get('proxyHeaders') or {}).get('request') or {}
    hdr_arg = ''.join(f"{k}: {v}\r\n" for k, v in hdrs.items())
    cmd = ['ffprobe', '-v', 'error', '-rw_timeout', '15000000', '-user_agent', UA, '-show_entries',
           'format=format_name:stream=codec_type,codec_name,profile,pix_fmt', '-of', 'json']
    if hdr_arg: cmd += ['-headers', hdr_arg]
    cmd.append(s['url'])
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=40)
        if r.returncode != 0:
            return name, 'ERRO: ' + (r.stderr.strip().splitlines() or ['?'])[-1][:110]
        d = json.loads(r.stdout)
        v = [f"{x.get('codec_name')}{'/'+x['pix_fmt'] if x.get('pix_fmt') else ''}" for x in d.get('streams', []) if x.get('codec_type') == 'video']
        a = [x.get('codec_name') for x in d.get('streams', []) if x.get('codec_type') == 'audio']
        return name, f"{d.get('format', {}).get('format_name')} | video {v} | audio {a} | headers {bool(hdrs)}"
    except subprocess.TimeoutExpired:
        return name, 'ERRO: timeout'
with cf.ThreadPoolExecutor(6) as ex:
    for name, res in ex.map(probe, jobs):
        print(f"{name:28s} {res}")
