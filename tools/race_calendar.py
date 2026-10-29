#!/usr/bin/env python3
"""Put this season's F1 calendar into a theme, for the launcher's race-weekend mode.

    race_calendar.py <theme folder>              fetch the current season and write race_calendar into
                                                 <theme folder>/config.json, keeping every other key
    race_calendar.py <theme folder> --season 2027

Then push the theme as usual: tools/theme.sh <theme folder>. The launcher never goes online; this script is
the only thing that does, once, on your computer. The data comes from Jolpica (https://jolpi.ca), the free
community successor to the Ergast API: no key, no account. Run it again when F1 moves a session or a new
season starts. Races that are already over are left out.
"""
import argparse
import datetime
import json
from pathlib import Path
import sys
import urllib.request

API = "https://api.jolpi.ca/ergast/f1/{season}.json?limit=100"
# Jolpica's session keys, in weekend order, and the names the launcher shows.
SESSIONS = (
    ("FirstPractice", "FP1"),
    ("SecondPractice", "FP2"),
    ("ThirdPractice", "FP3"),
    ("SprintQualifying", "SPRINT QUALI"),
    ("SprintShootout", "SPRINT QUALI"),   # the 2023 name for the same session
    ("Sprint", "SPRINT"),
    ("Qualifying", "QUALIFYING"),
)


def instant(block):
    """"2026-10-23" + "17:30:00Z" as one UTC timestamp; None when the time is not published yet."""
    if not block.get("date") or not block.get("time"):
        return None
    return f"{block['date']}T{block['time']}" if block["time"].endswith("Z") else f"{block['date']}T{block['time']}Z"


def calendar(races, now):
    out = []
    for race in races:
        sessions = []
        for key, name in SESSIONS:
            if key in race and (start := instant(race[key])):
                sessions.append({"name": name, "start": start})
        start = instant(race)
        if not start:
            continue
        sessions.append({"name": "RACE", "start": start})
        # The launcher keeps a weekend on screen for four hours after the start; a day covers that.
        if datetime.datetime.fromisoformat(start.replace("Z", "+00:00")) < now - datetime.timedelta(days=1):
            continue
        out.append({
            "round": int(race["round"]),
            "name": race["raceName"],
            "place": race.get("Circuit", {}).get("Location", {}).get("locality", ""),
            "sessions": sessions,
        })
    return out


def main():
    parser = argparse.ArgumentParser(description="Put this season's F1 calendar into a theme.")
    parser.add_argument("theme", help="theme folder with a config.json (created if missing)")
    parser.add_argument("--season", default="current", help="season year, default the current one")
    args = parser.parse_args()

    config = Path(args.theme) / "config.json"
    theme = json.loads(config.read_text(encoding="utf-8-sig")) if config.is_file() else {}
    request = urllib.request.Request(API.format(season=args.season), headers={"User-Agent": "cleanyourtv"})
    with urllib.request.urlopen(request, timeout=30) as response:
        table = json.load(response)["MRData"]["RaceTable"]
    now = datetime.datetime.now(datetime.timezone.utc)
    races = calendar(table["Races"], now)
    if not races:
        sys.exit(f"no upcoming races in the {table['season']} calendar; try --season with next year")
    theme["race_calendar"] = races
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(json.dumps(theme, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    first = races[0]
    print(f"{len(races)} races from the {table['season']} season -> {config}")
    print(f"next: round {first['round']} {first['name']}, race {first['sessions'][-1]['start']}")


if __name__ == "__main__":
    main()
