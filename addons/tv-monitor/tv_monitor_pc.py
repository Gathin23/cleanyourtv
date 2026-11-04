#!/usr/bin/env python3
"""Per-process monitor for the TV, drawn on the PC: reads `top` over ADB, colours each row by its CPU
share and marks the packages Clean Your TV flags (app/src/main/res/raw/flagged.txt) in red.

    python tv_monitor_pc.py <tv ip>          # port 5555 unless the address has one

ADB must be open on the TV (USB debugging on) and this PC authorised. It only reads: it changes nothing on the
TV. Close ADB again when you are done.
"""
import os
import subprocess
import sys
import tkinter as tk
from tkinter import ttk

HERE = os.path.dirname(os.path.abspath(__file__))
FLAGGED = os.path.join(HERE, "app", "src", "main", "res", "raw", "flagged.txt")
REFRESH_MS = 3000


def load_flagged(path=FLAGGED):
    out = {}
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("|", 2)
                if len(parts) == 3 and parts[0]:
                    out[parts[0]] = parts[2]
    except OSError:
        pass
    return out


def parse_top(text):
    """Rows (pid, name, cpu, res_mb) from toybox `top -b -n 1`; the columns are found from the header."""
    rows, cols = [], None
    for line in text.splitlines():
        words = line.split()
        if not words:
            continue
        if "PID" in words and "RES" in words:
            cols = ["S", "%CPU"] if False else [x for w in words for x in (("S", "%CPU") if w == "S[%CPU]" else (w,))]
            continue
        if cols is None or not words[0].isdigit() or len(words) < len(cols):
            continue
        cells = dict(zip(cols[:-1], words[:len(cols) - 1]))
        cells[cols[-1]] = " ".join(words[len(cols) - 1:])
        cpu_key = next((c for c in cells if "CPU" in c), None)
        try:
            cpu = float(cells.get(cpu_key, "0"))
        except ValueError:
            cpu = 0.0
        rows.append((int(words[0]), cells.get("ARGS", cells.get("COMMAND", "?")), cpu, res_mb(cells.get("RES", "0"))))
    return rows


def res_mb(value):
    value = value.strip()
    try:
        if value[-1] in "Gg":
            return float(value[:-1]) * 1024
        if value[-1] in "Mm":
            return float(value[:-1])
        if value[-1] in "Kk":
            return float(value[:-1]) / 1024
        return float(value) / 1024  # plain number: kilobytes
    except (ValueError, IndexError):
        return 0.0


def color_for(cpu):
    if cpu >= 40:
        return "#E53935"
    if cpu >= 15:
        return "#FFB300"
    return "#43A047"


def main():
    if len(sys.argv) < 2:
        sys.exit("Give the TV address, e.g.  python tv_monitor_pc.py 192.168.1.50")
    host = sys.argv[1]
    serial = host if ":" in host else host + ":5555"
    flagged = load_flagged()

    root = tk.Tk()
    root.title("TV Monitor - " + serial)
    root.configure(bg="#080C0A")
    status = tk.Label(root, text="", fg="white", bg="#080C0A", anchor="w", font=("Segoe UI", 11))
    status.pack(fill="x", padx=8, pady=4)
    tree = ttk.Treeview(root, columns=("pid", "cpu", "ram", "name", "flag"), show="headings", height=28)
    for key, title, width in (("pid", "PID", 70), ("cpu", "CPU %", 70), ("ram", "RAM MB", 80),
                              ("name", "Process", 420), ("flag", "Flagged by Clean Your TV", 380)):
        tree.heading(key, text=title)
        tree.column(key, width=width, anchor="w")
    tree.tag_configure("low", foreground="#43A047")
    tree.tag_configure("mid", foreground="#FFB300")
    tree.tag_configure("high", foreground="#E53935")
    tree.tag_configure("flag", background="#3A1010", foreground="#FF8A80")
    tree.pack(fill="both", expand=True, padx=8, pady=4)

    def refresh():
        try:
            out = subprocess.run(["adb", "-s", serial, "shell", "top -b -n 1 -m 60"],
                                 capture_output=True, text=True, timeout=20).stdout
            rows = parse_top(out)
        except (OSError, subprocess.TimeoutExpired):
            rows = []
        tree.delete(*tree.get_children())
        hits = 0
        for pid, name, cpu, ram in sorted(rows, key=lambda r: -r[2]):
            base = name.split(":")[0].split()[0]
            note = flagged.get(base)
            tag = "flag" if note is not None else ("high" if cpu >= 40 else "mid" if cpu >= 15 else "low")
            hits += note is not None
            tree.insert("", "end", values=(pid, "%.1f" % cpu, "%.0f" % ram, name, note or ""), tags=(tag,))
        status.config(text=("%d processes" % len(rows)) + ("   -   %d flagged still running" % hits if rows else
                      "   -   no data: ADB closed or TV not authorised?"))
        root.after(REFRESH_MS, refresh)

    refresh()
    root.mainloop()


if __name__ == "__main__":
    main()
