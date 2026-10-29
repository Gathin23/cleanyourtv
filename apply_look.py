#!/usr/bin/env python3
"""
Atur tampilan CleanYourTV mendekati desain mockup, hanya lewat konfigurasi.
Tidak mengubah kode Java. Jalankan dari folder utama repo:

    python3 apply_look.py --dry-run    # lihat dulu apa yang akan berubah
    python3 apply_look.py              # terapkan (cadangan: config.xml.bak)

Lalu build ulang seperti biasa:  TV_HOST=<IP TV> launcher/build-and-install.sh
Untuk membatalkan: salin config.xml.bak kembali ke config.xml.
"""
import re, shutil, sys

PATH = "launcher/app/src/main/res/values/config.xml"

# (jenis, nama, nilai baru). Ubah nilainya di sini kalau ada yang tidak Anda mau.
CHANGES = [
    ("integer", "columns", "5"),                  # dock berisi 5 aplikasi, selebar layar
    ("bool", "dock_row", "true"),                 # dock sebagai pita sendiri di bawah
    ("bool", "dock_glass", "true"),               # pita dock kaca buram terang
    ("bool", "dock_even_padding", "true"),        # jarak pita ke tile sama di semua sisi
    ("bool", "dock_title", "true"),               # nama aplikasi fokus besar di atas dock
    ("integer", "rows_below_dock", "0"),
    ("bool", "show_greeting", "false"),           # tanpa "Selamat pagi"
    ("bool", "icon_only_buttons", "true"),        # Wi-Fi dan Pengaturan jadi ikon bulat
    ("bool", "icon_only_inputs", "true"),         # Input juga ikon bulat
    ("bool", "show_stats", "true"),               # CPU, memori, penyimpanan di bawah jam
    ("bool", "app_glow", "true"),                 # sorot fokus berwarna sesuai aplikasi
    ("integer", "sky_intensity", "60"),           # latar siang/malam mengikuti jam
    ("string", "latitude", "0.5375"),             # pusat Kota Gorontalo
    ("string", "longitude", "123.0625"),
    ("integer", "home_idle_clock_minutes", "5"),  # screensaver jam setelah 5 menit diam
    ("bool", "clock_wallpaper", "true"),          # jam screensaver di atas wallpaper
    ("integer", "clock_wallpaper_dim", "40"),
    ("bool", "tile_menu", "true"),                # tahan OK pada tile: Pindah, Hapus, Info
    ("bool", "app_library", "true"),              # pustaka aplikasi (tambah Google Foto, dll.)
    ("bool", "launcher_settings_button", "true"), # klik Pengaturan membuka pengaturan launcher
]

def main():
    dry = "--dry-run" in sys.argv
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    path = args[0] if args else PATH
    try:
        text = open(path, encoding="utf-8").read()
    except OSError as e:
        sys.exit(f"Tidak bisa membuka {path}: {e}\nJalankan dari folder utama repo, atau beri path config.xml sebagai argumen.")
    out, notfound = text, []
    for kind, name, value in CHANGES:
        pat = re.compile(r'(<%s\s+name="%s"[^>]*>)([^<]*)(</%s>)' % (kind, re.escape(name), kind))
        m = pat.search(out)
        if not m:
            notfound.append(name)
            continue
        old = m.group(2).strip()
        mark = "   (sudah)" if old == value else ""
        print(f"{name:26} {old or '(kosong)':>10} -> {value}{mark}")
        out = pat.sub(lambda mm: mm.group(1) + value + mm.group(3), out, count=1)
    if notfound:
        print("\nTidak ditemukan di file (dilewati):", ", ".join(notfound))
    if dry:
        print("\n--dry-run: tidak ada yang ditulis.")
        return
    if out == text:
        print("\nTidak ada perubahan.")
        return
    shutil.copyfile(path, path + ".bak")
    open(path, "w", encoding="utf-8").write(out)
    print(f"\nTersimpan. Cadangan: {path}.bak")

main()
