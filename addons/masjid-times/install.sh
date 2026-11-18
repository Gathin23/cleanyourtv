#!/usr/bin/env bash
# Masjid Times: the next prayer from your mosque's my-masjid.com timetable, under the clock on the Clean Your TV
# home screen. macOS / Linux twin of install.ps1.
#
# Usage (from the repo root): TV_HOST=192.168.1.50 addons/masjid-times/install.sh [city] [country]
#        city defaults to Gravesend, country to "United Kingdom". MASJID_GUID=<id> skips the search.
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV's IP address}"
export TV_HOST
city="${1:-Gravesend}"
country="${2:-United Kingdom}"
cd "$(dirname "$0")"
py=../../tools/venv/bin/python
[ -x "$py" ] || { echo "run from the repo root: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
config=app/src/main/res/values/config.xml

# Every my-masjid.com lookup and the config edit go through Python, so no jq is needed.
guid="${MASJID_GUID:-$(sed -n 's/.*<string name="masjid_guid"[^>]*>\([^<]*\)<\/string>.*/\1/p' "$config" | tr -d '[:space:]')}"
"$py" -I - "$config" "$guid" "$city" "$country" <<'PY'
import json, re, sys, urllib.parse, urllib.request
from datetime import date
from xml.sax.saxutils import escape

config, guid, city, country = sys.argv[1:5]
API = "https://time.my-masjid.com/api/"

def model(path):
    with urllib.request.urlopen(urllib.request.Request(API + path, headers={"Accept": "application/json"}), timeout=30) as r:
        m = json.load(r).get("model")
    if m is None:
        sys.exit(f"my-masjid.com gave no answer for {path}")
    return m

name = ""
if not guid:
    print(f"Looking up mosques in {city} on my-masjid.com...")
    countries = model("Country/GetAllCountries")
    c = next((x for p in (country, "United Kingdom", "UK", "England", "Britain")
              for x in countries if p.lower() in x["name"].lower()), None)
    if not c:
        sys.exit(f"no country like '{country}' on my-masjid.com")
    ct = next((x for x in model(f"City/GetCitiesByCountryId?CountryId={c['id']}") if city.lower() in x["name"].lower()), None)
    if not ct:
        sys.exit(f"no city like '{city}' in {c['name']} on my-masjid.com")
    mosques = model(f"Masjid/SearchMasjidByLocation?CountryId={c['id']}&CityId={ct['id']}")
    if not mosques:
        sys.exit(f"no mosques listed in {ct['name']}")
    print()
    for i, m in enumerate(mosques, 1):
        print(f"  [{i}] {m['name']}   {m.get('address') or ''}")
    print()
    with open("/dev/tty") as tty:
        sys.stdout.write("Type the number of your mosque and press Enter: ")
        sys.stdout.flush()
        pick = int(tty.readline().strip() or 0)
    if not 1 <= pick <= len(mosques):
        sys.exit("that number is not on the list")
    guid, name = mosques[pick - 1]["guidId"], mosques[pick - 1]["name"]

t = model("TimingsInfoScreen/GetMasjidTimings?GuidId=" + urllib.parse.quote(guid))
if not t.get("salahTimings"):
    sys.exit("my-masjid.com has no timetable for this mosque")
name = name or (t.get("masjidDetails") or {}).get("name", "")
print(f"Mosque: {name}")
today = date.today()
row = next((r for r in t["salahTimings"] if r.get("day") == today.day and r.get("month") == today.month), None)
if row:
    print("Today:  " + "  ".join(f"{p.capitalize()} {row.get(p)} ({row.get('iqamah_' + p.capitalize())})"
                                 for p in ("fajr", "zuhr", "asr", "maghrib", "isha")))

with open(config, encoding="utf-8") as f:
    xml = f.read()
xml = re.sub(r'(<string name="masjid_guid"[^>]*>)[^<]*(</string>)', lambda m: m.group(1) + guid + m.group(2), xml)
xml = re.sub(r'(<string name="masjid_name"[^>]*>)[^<]*(</string>)', lambda m: m.group(1) + escape(name) + m.group(2), xml)
with open(config, "w", encoding="utf-8") as f:
    f.write(xml)
PY

echo "Building (the first time takes a few minutes)..."
./gradlew assembleRelease -q --console=plain
apk=app/build/outputs/apk/release/app-release.apk
"$py" -I ../../tools/b64push.py "$apk" /data/local/tmp/masjidtimes.apk
out=$("$py" -I ../../tools/tv.py 'pm install -r /data/local/tmp/masjidtimes.apk; rm -f /data/local/tmp/masjidtimes.apk')
echo "$out"
case "$out" in *Success*) ;; *) echo "pm install failed" >&2; exit 1 ;; esac

../../tools/theme.sh theme
echo
echo "Done. Press Home on the remote: the next prayer shows under the clock."
