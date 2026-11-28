# SIHAT Prayer Times

Jadwal salat dari widget resmi SIHAT Kementerian Agama Republik Indonesia.

Add-on ini berjalan dalam Android WebView dan dapat dibuka melalui dock CleanYourTV maupun launcher Android TV.

## Features

- Menggunakan widget resmi SIHAT Kemenag.
- Tampilan khusus Android TV.
- Tidak memerlukan akun atau API key.
- Lokasi dapat dikonfigurasi melalui resource Android.
- Dapat dipasang berdampingan dengan add-on Prayer Times asli.

## Location Configuration

Edit:
`app/src/main/res/values/config.xml`

Ubah nilai berikut sesuai kode wilayah SIHAT yang diinginkan:

- `wilayah`: kode wilayah SIHAT Kemenag.
- `place`: nama lokasi yang ditampilkan.

Default:
- Wilayah: `340310`
- Lokasi: Kecamatan Ponjong, Gunungkidul.

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Memuat widget jadwal salat resmi dari SIHAT Kemenag. |

Add-on ini tidak meminta izin lokasi, alarm, atau akses boot perangkat.

## Build

Dari direktori add-on:

```bash
./gradlew assembleRelease
```

APK release dihasilkan di:

`app/build/outputs/apk/release/`

## Data Source

Widget jadwal salat disediakan oleh SIHAT Kementerian Agama Republik Indonesia:

https://sihat.kemenag.go.id/
