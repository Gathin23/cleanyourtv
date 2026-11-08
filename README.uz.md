<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Televizoringiz, reklamasiz. Har qanday Android TV yoki Google TV dagi ortiqcha dasturlarni Wi-Fi orqali tozalang va reklama toʻla bosh ekranni Apple TV ga oʻxshagan toza launcher bilan almashtiring. Root yoʻq, kabel yoʻq, hech narsa oʻchirilmaydi va bir qatorlik tiklash." width="800"></a>
</p>

<p align="center">
  Oʻqing: <a href="README.md">English</a> · <a href="README.he.md" lang="he" dir="rtl">עברית</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a> · Oʻzbekcha
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Yulduzlar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="Birlashtirilgan PR lar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Fork lar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Hissa qoʻshuvchilar"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Qoʻllab-quvvatlang, bir choy bering"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="Product Hunt da CleanYourTV"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="Haftaning 1-oʻrindagi trend ombori, 19–25 oktabr 2026, trendshift.io boʻyicha" width="360"></a>
</p>

# CleanYourTV: oʻzbekcha qoʻllanma

Android TV yoki Google TV ga uyingizdagi Wi-Fi orqali ulaning va oldindan oʻrnatilgan, siz ishlatmaydigan
dasturlar, reklamalar va maʼlumot yigʻuvchi xizmatlarni oʻchirib qoʻying. Soʻng mos televizorlarga toza
bosh ekran (launcher) oʻrnating.

Root, firmware ni qayta oʻrnatish yoki USB kabel kerak emas. Tizim dasturlari faqat **oʻchirib qoʻyiladi**,
hech qachon olib tashlanmaydi, va ularni qaytarish uchun har doim skript bor.

Bu sahifa asosiy imkoniyatlarni va ulardan qanday foydalanishni koʻrsatadi. Har bir TV modeli, mintaqa va
firmware versiyasining oʻz dasturlar roʻyxati va cheklovlari bor, shuning uchun avval televizoringizga oid
maʼlumotlarni tekshiring.

Farq boʻlsa, [inglizcha README](README.md) asosiy hisoblanadi. Har bir modelga oid izohlar
[qoʻllab-quvvatlanadigan televizorlar roʻyxatida](examples/README.md).

> [!WARNING]
> **Televizoringiz Samsung, LG, Roku yoki Android ishlatmaydigan boshqa brendmi?**
> <br>CleanYourTV ularda ishlamaydi, lekin kuzatuvni faqat pult yordamida bosqichma-bosqich oʻchirishingiz mumkin: [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

![Standart Google TV bosh ekrani va CleanYourTV launcheri taqqoslamasi](docs/stock-vs-new-side.jpg)

## Shu yerdan boshlang

| Sizda bor | Qayerga boring |
|---|---|
| **Android TV yoki Google TV** | [Tezkor boshlash](#tezkor-boshlash): ishni taxminan oʻn besh daqiqada kodlash yordamchisi bajaradi. |
| **Samsung, LG, Roku, Vizio yoki Hisense VIDAA televizor** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): CleanYourTV u yerda ishlamaydi, lekin kuzatuvni pult bilan oʻchirishingiz mumkin. |
| **Telefon yoki noutbuk, kodlash agentisiz** | Android telefonlar, Mac va Windows uchun mustaqil dasturchilar yaratgan [hamjamiyat ilovalaridan](https://cleanyour.tv/app) biri. |
| **Avval koʻrmoqchiman** | [Nima olasiz](#nima-olasiz): yangi bosh ekranning skrinshotlari. |

**Rasmiy havolalar:** [cleanyour.tv](https://cleanyour.tv) va bu ombor. Bu nomni ishlatadigan boshqa domenlar,
masalan cleanyourtv.com, bu loyihaga tegishli emas ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).
CleanYourTV hech qanday ilova doʻkonida ilova chiqarmaydi; clean-your-tv.net ham bizniki emas.

[Qanday ishlaydi](#qanday-ishlaydi) · [Tezkor boshlash](#tezkor-boshlash) · [Nima olasiz](#nima-olasiz) ·
[Qoʻllab-quvvatlanadigan televizorlar](#qoʻllab-quvvatlanadigan-televizorlar) · [Qoʻlda bajarish](#qoʻlda-bajarish) · [Launcher](#launcher) ·
[Tiklash](#televizoringizni-tiklash) · [Firmware yangilanishlari](#firmware-yangilanishidan-keyin) ·
[Savollar](#koʻp-soʻraladigan-savollar) · [Hissa qoʻshish](#hissa-qoʻshish)

## Qanday ishlaydi

Android TV larda Android telefonlardagi kabi debug bridge bor. Televizorning dasturchi
sozlamalarida USB debugging ni yoqing, kompyuteringizga bir marta ruxsat bering; qolgani standart Android
vositalari bilan bajariladi:

| Qadam | Nima boʻladi |
|---|---|
| **Zaxira** | Oʻrnatilgan paketlar roʻyxati va sozlamalar. |
| **Oʻchirish** | Ishlab chiqaruvchining telemetriyasi, reklama xizmatlari, kontent qatorlari va oldindan yuklangan ortiqcha dasturlar, `pm disable-user` bilan. Hech narsa oʻchirib tashlanmaydi, shuning uchun tiklash skripti har bir qadamni bekor qila oladi. |
| **Oʻrnatish** | Kichik mahalliy launcher oʻrnatiladi va standart bosh ekran qilinadi. |

**Root yoʻq, kabel yoʻq, hech narsa oʻchirilmaydi va fikringiz oʻzgarsa bir qatorlik tiklash.**

Ortiqcha dasturlar brendga qarab farq qiladi; shuning uchun prompt kodlash yordamchingizga ularni televizoringizda
oʻzi topishni va siz ishlatishingiz mumkin boʻlgan narsaga tegishdan oldin soʻrashni buyuradi.

## Tezkor boshlash

| Sizda bor | Bajariladigan ish |
|---|---|
| **Kompyuter** | Promptni kodlash yordamchisi bilan ishlating: quyidagi uch qadam. |
| **Kompyuter, lekin kodlash yordamchisiz** | Uch qadamni oʻzingiz bajaring: buyruqlar [Qoʻlda bajarish](#qoʻlda-bajarish) boʻlimida. |
| **Telefon yoki noutbuk, kodlash yordamchisiz** | [Hamjamiyat ilovalaridan](https://cleanyour.tv/app) birini tanlang: bu TV roʻyxatlari asosida mustaqil dasturchilar yaratgan bepul, ochiq kodli ilovalar. |

Quyidagi uch qadam kodlash yordamchisi uchun yozilgan: kompyuter televizor bilan bir xil Wi-Fi da boʻlishi,
unda shell, Python 3, 17 dan 22 gacha JDK va build-tools 34 bilan Android SDK
platform 34 boʻlishi kerak (yordamchi launcherni shu yerda yigʻadi).
Yordamchisiz [Qoʻlda bajarish](#qoʻlda-bajarish) xuddi shu ish, faqat siz bajarasiz; oʻrnatish
buyruqlari ham shu yerda.

Bularning hammasi ishlaydi: Claude Code ([0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme) yoki [Anthropic rejasi](https://claude.com/pricing) da), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli) yoki [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Qayerda | Nima qilish kerak |
|---|---|
| **1. Televizorda** | Sozlamalar → Qurilma afzalliklari → Haqida → **Build** ustida yetti marta OK bosing → orqaga → Dasturchi sozlamalari → **USB debugging** yoqilgan. Tarmoq yoki ADB debugging tugmasini koʻrsangiz, uni ham yoqing. Televizorni yoqib turing. |
| **2. Kompyuteringizda** | Omborni klonlang va shu papkani yordamchingizda oching (`git clone https://github.com/Gathin23/cleanyourtv`), soʻng [`PROMPT.md`](PROMPT.md) ni joylashtirib TV modelingizni yozing. |
| **3. Kuzatib boring** | Televizorda "USB debugging ga ruxsat berilsinmi?" soʻrovini qabul qiling, soʻng tekshiruv roʻyxatiga javob bering: qaysi ilovalarni ishlatasiz, dock oʻlchami va ilovalar tartibi, ovozli yordamchi, Chromecast va yangilanishlar qoladimi. |

Klonlash muhim: yordamchi oʻz skriptlarini yozish oʻrniga bu omborning `tools/disable.sh` va
`launcher/build-and-install.sh` skriptlarini qayta ishlatadi; zaxira, Home himoyasi va tiklash
yozuvi ham shu yerda.

Yordamchi zaxiralaydi, ortiqcha dasturlarni tozalaydi, launcherni yigʻib oʻrnatadi va har bir qadamni televizordan
olingan skrinshotlar bilan tekshiradi. Bu ombor kontekst sifatida berilganda taxminan oʻn besh daqiqa ketadi.

## Nima olasiz

Apple TV ga oʻxshagan va yoʻlingizdan chetga chiqadigan bosh ekran:

| Qism | Qanday koʻrinadi |
|---|---|
| **Qop-qora bosh ekran** | Sevimli toʻrt ilovangiz Apple TV dagi joyida, yumaloq lenta ichida joylashgan dock. |
| **Yuqori javon** | Fokusdagi ilova nomini katta shriftda koʻrsatadi; orqasida uning rasmidan olingan rangda muhit porlashi bor. |
| **Qolgan ilovalaringiz panjarada** | Pastga bosganingizda yuqoriga siljiydi. |
| **Bitta suzuvchi shisha taʼkid** | Katakchadan katakchaga sakrab oʻtadi. Fokusdagi katakcha koʻtariladi va nomini koʻrsatadi. |
| **Uchta muzli tugma** | Kirishlar (televizor sozlamalarida bergan nomlaringiz bilan HDMI portlar, AV, Jonli TV), tarmoq nomi bilan Wi-Fi yoki Ethernet, Sozlamalar. |
| **Salomlashuv, ingichka soat va sana** | [Outfit](https://fonts.google.com/specimen/Outfit) shriftida va mos Soat ekran himoyachisi. Soat ostida bitta qator uchun config yoki mavzuda `show_stats` ni yoqing: CPU, boʻsh xotira va boʻsh saqlash. Siz yoqmaguncha oʻchiq turadi va faqat Home ekranda boʻlganda yangilanadi. |
| **Mavzular va modul kartochkalari** | Mavzular ranglarni, fon rasmini, dock va ustunlarni qayta yigʻmasdan oʻzgartiradi; modul kartochkalari esa faqat sizning roziligingiz bilan boshqa ilovalarga katakcha qoʻshishga ruxsat beradi. |

Va sizga qarshi ishlashni toʻxtatgan televizor: homiylik qatorlari yoʻq, "Kashf etish" bezovtaliklari yoʻq, uyga
maʼlumot yuboradigan analitika yoʻq, hech soʻramagan ilovalaringiz yoʻq.

| | |
|---|---|
| ![Yuqori javon fokusdagi ilova nomini uning rangidagi muhit porlashi ustida koʻrsatadi](docs/focus-2-up-netflix.png) | ![Kirishlar oynasi: ulangan narsalar bilan HDMI portlar, AV va Jonli TV](docs/inputs-menu.jpg) |
| *Yuqori javon va muhit porlashi* | *Kirishlar: ulanish holati bilan HDMI, AV, Jonli TV* |
| ![Katakcha ustida shisha taʼkid va ostida nomi bilan panjara](docs/focus-1-fancode.png) | ![Soat ekran himoyachisi: porlash ustida siljiydigan ingichka soat](docs/clock-screensaver.png) |
| *Panjara, bitta shisha taʼkid, katakcha nomi* | *Soat ekran himoyachisi, iz qolmasin deb siljiydi* |
| ![Aurora mavzusi: qayta yigʻmasdan yuborilgan fon rasmi va ranglangan tugmalar](themes/aurora/screenshot.jpg) | ![Mavzuning config.json faylidan oltita ustun va oltita ilovali dock](themes/six-columns/screenshot.jpg) |
| *`tools/theme.sh` bilan yuborilgan Aurora mavzusi* | *Oltita ustun va oltita ilovali dock, xuddi shu launcher* |
| ![Katakchani koʻchirish: OK bosib turildi, Koʻchirish tanlandi, katakcha olindi va boshqalar xiralashdi](docs/tile-move.jpg) | ![Ilovalar kutubxonasi: oʻrnatilgan ilovalar, bir bosish bilan ilova Home ga qaytariladi](docs/app-library.jpg) |
| *Katakchada OK ni bosib turing, Koʻchirish: strelkalar uni koʻchiradi, Orqaga joyiga qoʻyadi* | *Ilovalar kutubxonasi: bir bosish olib tashlangan ilovani Home ga qaytaradi* |

## Qoʻllab-quvvatlanadigan televizorlar

**Android TV** yoki **Google TV** ishlaydigan hamma narsa.

| Platforma | Nima ishlaydi |
|---|---|
| **Android TV, Google TV** | Tozalash va launcher. |
| **Fire TV** | Faqat tozalash qismi, chunki Fire OS Home ni uchinchi tomon launcherga bermaydi. 2015 Fire TV Box da (Fire OS 5) bu qism ham muvaffaqiyatsiz: har bir `pm` yoʻli rad etiladi. |
| **Xitoy bozoriga moʻljallangan baʼzi TCL modellari** | Umuman hech qanday launcher oʻrnatishga ruxsat bermaydi; roʻyxatlarining sarlavhasida shu aytiladi. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | Qoʻllab-quvvatlanmaydi (ADB yoʻq). Buning oʻrniga kuzatuvni oʻchiring: [cleanyour.tv/not-android](https://cleanyour.tv/not-android). Toʻrtta webOS portdari uchun [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) ga, Tizen portini oʻylab koʻrayotgan boʻlsangiz 2021-yilgi Samsung televizor Wi-Fi orqali nima javob berishi uchun (ADB yoʻq, demak `pm disable-user` ham yoʻq) [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) ga qarang. |

Hissa qoʻshuvchilardan kelgan sinovdan oʻtgan roʻyxatlar, har bir model uchun bitta papka sifatida [`examples/`](examples/)
ichida.

**Televizoringizni toping:** [cleanyour.tv/tvs](https://cleanyour.tv/tvs) da qidiring yoki
[examples/README.md](examples/README.md) dagi toʻliq jadvalga qarang.

Televizoringiz bu yerda yoʻqmi? Prompt ortiqcha dasturlarni oʻzi topadi. Keyin roʻyxatingizni qoʻshing; sizning
modelingizga ega keyingi kishi bu qadamni oʻtkazib yubora olsin: [CONTRIBUTING.md](CONTRIBUTING.md).

## Qoʻlda bajarish

Yordamchi qilgan hamma narsani oʻzingiz ham bajarishingiz mumkin. Avval kompyuterda ikki narsa boʻlishi kerak:

| Kerak | Nima uchun |
|---|---|
| **Python 3** (har qanday 3.x) | Vositalar. Quyidagi venv buyrugʻi ular import qiladigan hamma narsani oʻrnatadi. |
| **JDK 17 dan 22 gacha** (`jlink` ni oʻz ichiga olgan toʻliq JDK) va **build-tools 34.0.0 yoki undan yangiroq har qanday 34.x** bilan **Android SDK platform 34** | Launcherni yigʻish. JDK 23 va undan keyingisi Gradle 8.9 ni toʻxtatadi. Yigʻish oʻqiydigan platforma 34; yangiroq platforma uning oʻrnini bosmaydi. macOS da bularning hech biri oʻrnatilmagan boʻlsa: `brew install openjdk@17 android-commandlinetools`, soʻng `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. Linux da oʻrnatish skripti `~/Android/Sdk` ga va PATH dagi `java` ga qarab chiqadi. |

`tools/tv.py` 5555-portdagi tarmoq ADB si bilan gaplashadi. Bu port yopiq qolsa va televizor juftlash
kodi bilan Wireless debugging taklif qilsa (Chromecast with Google TV va Google TV Streamer shunday qiladi),
bir marta standart `adb` bilan juftlashtiring. Soʻng portni ochish uchun `adb tcpip 5555` ni ishga tushiring —
odatda faqat televizor qayta yoqilgunga qadar saqlanadi — yoki yopiq qoldiring: `TV_ADB_BINARY` ni oʻsha
`adb` ga va `TV_HOST` ni Wireless debugging ekrani koʻrsatadigan portga yoʻnaltiring; vositalar televizorga
5555 da hech narsa tinglamay turib juftlashgan ulanish orqali yetadi
([tools/native-adb.md](tools/native-adb.md)). [Nosozliklarni bartaraf etish](docs/TROUBLESHOOTING.md#connecting)
ikkisini ham, juftlash qadamlari bilan, beradi.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
```

| Vosita | Nima qiladi |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Sof Python ADB mijozi: shell, toʻplam, push, pull, qayta yoqish va ixtiyoriy ravishda avval pult tugmalarini bosib skrinshot. Standart `adb` ikkilik fayli bloklangan joylarda ishlaydi. Shifrlangan Wireless debugging uchun ixtiyoriy [juftlashgan Google ADB backend](tools/native-adb.md). |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Launcherni yoki qoʻshimchani Docker da (JDK 17, Android SDK 34) yigʻadi; shuning uchun bu kompyuterda Docker dan boshqa hech narsa kerak emas. Natijani `SKIP_BUILD=1` bilan oʻrnating. [Qoʻllanma](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | APK kabi katta fayllarni base64 boʻlaklari bilan yuklaydi va nazorat yigʻindisini tekshiradi. |
| [`tools/relay.py`](tools/relay.py) | Sandbox tarmogʻingizga yetolmaydigan yordamchilar uchun mahalliy portni televizorning ADB portiga yoʻnaltiradi. Faqat shu kompyuterda tinglaydi. [Qoʻllanma](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Tozalash roʻyxatini qoʻllaydi yoki bekor qiladi; bekor qilish oʻzgarishdan oldin yozib olingan holatdan. |
| [`tools/recovery.py`](tools/recovery.py) | `disable.sh` oʻzini bekor qila olishi uchun kerakli yozuvni yozadi va tiklash toʻplamini chiqaradi. |
| [`tools/check.py`](tools/check.py) | Firmware yangilanishi nima bekor qilganini koʻrish uchun televizorni qoʻllangan roʻyxat bilan solishtiradi; `--fix` eski holatiga qaytaradi. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Debugging oʻchirilgandan keyin televizorning tarmoq ADB porti yopiqligini tekshiradi: televizor ulanishni rad etsa 0, port hali ochiq boʻlsa 1, hech narsa javob bermasa yoki tekshiruvning oʻzi ishlamasa 2 bilan chiqadi. `nc` kerak emas. |
| [`tools/philips-api.py`](tools/philips-api.py) | Faqat Philips: Home Assistant ishlatadigan JointSpace boshqaruv API si tozalashdan oldin va keyin hali javob berishini tekshiradi (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Avval HTTPS orqali 1926-port, soʻng 1925; faqat juftlashsiz javob berganlarini oʻqiydi. |
| [`tools/privacy.py`](tools/privacy.py) | Televizorni ovoz, ACR va telemetriya nomzodlari uchun tekshiradi va har biri uchun dalilni chiqaradi; `--write` topilmalarni `disable.sh` va `restore.sh` allaqachon tushunadigan roʻyxatga aylantiradi. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | [`themes/`](themes/) dagi papkani launcherga yuboradi va Home ni qayta ishga tushiradi. |
| [`tools/manage.py`](tools/manage.py) | Har bir qoʻshimcha, mavzu va kalitni skrinshoti bilan roʻyxatlaydi, televizorda nima borligini koʻrsatadi va yaratuvchisining oʻz skriptini ishga tushirib birini oʻrnatadi, yangilaydi yoki olib tashlaydi. |
| [`tools/bench.py`](tools/bench.py) | HOME ga javob beradigan launcherni oʻlchaydi: tugma kechikishi, fokus qanchalik siljigani, sekin kadrlar, sovuq ishga tushish, Home ga qaytish va boʻsh turganda sarflagan CPU va PSS. Televizorda hech narsani oʻzgartirmaydi va tugma boʻyicha xom qiymatlarni JSON ga yozadi; qoʻshimcha oʻrnatish kerak emas. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Launcherning Perfetto izlarini (Home ga qaytish, sovuq ishga tushish, D-pad) yozib oladi va vaqt qayerga ketishini, ishga tushirish boʻyicha va oldin/keyin solishtirib hisobot qiladi. Ixtiyoriy: [`requirements-perf.txt`](tools/requirements-perf.txt) kerak. [Qoʻllanma](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Pull request uchun shu izlardan oldin/keyin alanga diagrammasini chizadi: umumiy vaqt shkalasida ip boʻyicha bitta yoʻlak; oʻzgarish nishonga olgan ish jami bilan magenta rangda. Iz vositalari kabi ixtiyoriy. [Qoʻllanma](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Oldin va keyin skrinshotlarni pikselma-piksel solishtiradi; soatni yoki maskalagan narsangizni tashlab ketadi va juftni Wi-Fi nomingiz xiralashtirilgan holda yozadi. Iz vositalari kabi ixtiyoriy. [Qoʻllanma](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Poyga dam olish kuni rejimi uchun bu mavsumning F1 taqvimini bepul Jolpica API sidan mavzuga yozadi. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Kichraytirilgan reliz (taxminan 572 KB) yigʻadi, oʻrnatadi, Home ni almashtiradi va Home unga oʻtmasa orqaga qaytaradi. |

Skriptlar televizorni hech qachon Home ilovasiz qoldirmaydi: faqat taniydigan standart launcherni
oʻchiradi va boshqa launcher HOME ga javob berguncha roʻyxatdagi Home ilovasini oʻtkazib yuboradi.
Oʻz roʻyxatlaringiz ombor tashqarisida ham turishi mumkin: roʻyxat yoʻllari buyruqni ishga tushirgan
katalogga nisbatan, mutlaq yoʻllar ham ishlaydi.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Har bir skriptning yonida PowerShell jufti bor. Python 3 va 17 dan 22 gacha JDK oʻrnating; SDK Manager da
platform 34 va build-tools 34 ni (34.0.0 yoki undan yangiroq har qanday 34.x) qoʻshing. Skript shu oralikdagi
JDK ni koʻrsatayotgan boʻlsa `JAVA_HOME` ni, aks holda shu oralikda boʻlsa Android Studio bilan kelgan JDK ni ishlatadi:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

Oxirgi ikkitasi bekor qilish va mavzu yuborish; yuqoridagi boʻlimlar bash da bergan `tools/restore.sh` va
`tools/theme.sh` qatorlarining oʻxshashi. Google TV da bekor qilish standart launcherni ham argument sifatida oladi:
`-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>Skriptlar Home ni qanday himoya qiladi</b></summary>

Oʻrnatish skriptlari televizordan Home qaysi launcherga tegishli ekanini soʻraydi va faqat taniydiganlarini
oʻchiradi; bular `KNOWN_STOCK_LAUNCHERS` dagi toʻrt paket: `com.google.android.tvlauncher`,
`com.google.android.apps.tv.launcherx`, `com.google.android.leanbacklauncher` va Xiaomi ning
`com.mitv.tvhome` (PatchWall) paketi. Buni bekor qilish uchun `STOCK_LAUNCHER=<package>`
(`-StockLauncher`) bering; tegilmasin desangiz `none` yozing.

`tools/disable.sh` (`disable.ps1`) biror narsani oʻchirishdan oldin xuddi shu qoidani qoʻllaydi:

- Roʻyxat Home egasi boʻlgan ilovani oʻz ichiga olsa va televizordagi boshqa hech qanday ilova HOME ga javob bermasa,
  shu yozuvni oʻtkazib yuboradi, sababini aytadi va roʻyxatning qolganini oʻchiradi. Shunday qilib launcher
  topshirilishidan oldin qoʻllangan roʻyxat televizorni ishga tushiradigan hech narsasiz qoldira olmaydi.
- Sozlash sehrgari, sozlamalar ilovalari, sotuvchiga tegishli `FallbackHome` va zavod sinov ilovalari
  (Xiaomi ning `com.duokan.factorytest` paketi) ham HOME ga javob beradi, lekin hisobga olinmaydi.
- Google TV da setupwraith HOME ga javob berganda va yoqilgan holda qolganda (roʻyxatda yoʻq yoki Wi-Fi tarmogʻi
  uchun saqlangan) egasini ham saqlaydi: setupwraith hali Home boʻlmagan oʻrnatilgan launcherdan oldin javob beradi,
  shuning uchun Home ni egallardi.

`launcher/build-and-install.sh` yangi launcherni Home qilgandan keyin xuddi shu roʻyxatni qayta ishga tushiring,
yozuv ham qoʻllanadi; `FORCE_HOME=1` (`$env:FORCE_HOME`) uni baribir oʻchiradi.

</details>

## Mikrofon va u haqida nima deyish mumkin

"Bu paketlardan qaysi biri tinglayapti?" savolining rostgoʻy, bir qatorlik javobi yoʻq: ruxsatlar skani
hech narsa yozmaydigan va `RECORD_AUDIO` ruxsatiga ega ellikta tizim paketini koʻrsatadi; ACR mijozlari esa hech
qanday ovoz ruxsatisiz ekranni koʻradi. [`tools/privacy.py`](tools/privacy.py) har bir ovoz, ACR va telemetriya
nomzodi uchun dalili bilan televizor aslida nima deganini hisobot qiladi:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`; `--write` ularni oddiy tozalash
roʻyxatiga aylantiradi. Tekshiruv qila olmaydigan narsa — niyatni isbotlash: sababini [docs/privacy.md](docs/privacy.md)
tushuntiradi.

## Launcher

Android TV uchun kichik mahalliy bosh ekran: **taxminan 572 KB**, oddiy Java, 1 GB RAM li televizorlar uchun
qilingan. Xiralik yoʻq, video yoʻq, tarmoq ruxsati yoʻq, fon xizmatlari yoʻq. **Televizor** Android 8.0 (API 26)
yoki undan yangiroq boʻlishi kerak; bu [`launcher/app/build.gradle`](launcher/app/build.gradle) dagi `minSdk`.
Eskiroq qurilmada nima qolgani [Nosozliklarni bartaraf etish](docs/TROUBLESHOOTING.md) da.

| Nima | Qanday ishlaydi |
|---|---|
| **Sozlash** | Dock tartibi, yashirin ilovalar, Jonli TV ilovasi, qatoriga 4 dan 7 gacha katakcha, kamaytirilgan harakat. Hammasi [`config.xml`](launcher/app/src/main/res/values/config.xml) da |
| **Televizordagi sozlamalar** | Launcher sozlamalari uchun Sozlamalar ustida OK ni bosib turing. Ixtiyoriy `launcher_settings_button` oddiy bosish ularni toʻgʻridan-toʻgʻri ochishini taʼminlaydi; televizor sozlamalari sozlamalar sahifasida va oʻz ilova katakchasida qoladi. |
| **Mavzular** | Wi-Fi orqali yuborilgan `config.json` bilan fon, fon rasmi va joylashuv, qayta yigʻish yoʻq. Ichki `default/` bilan birga yigirma birta [`themes/`](themes/) da keladi |
| **31 til** | Televizor tiliga ergashadi; ivrit uchun toʻliq oynalashtirilgan, oʻngdan chapga joylashuv |
| **Ilovalar kutubxonasi** | Ixtiyoriy: oʻrnatilgan barcha ilovalarni koʻrib chiqing va Home yorliqlarini qoʻshing yoki olib tashlang. Qarang: [ilovalar kutubxonasi qoʻllanmasi](docs/app-library.md) |
| **Katakcha menyusi** | Standart boʻyicha yoqilgan (`tile_menu`): Koʻchirish va Ilova maʼlumoti uchun katakchada OK ni bosib turing; ilovalar kutubxonasi yoqilganda Home dan olib tashlash ham chiqadi. Oʻchiqda OK ni bosib turish Ilova maʼlumotini ochadi |
| **Modul kartochkalari** | Har qanday TV ilovasi oʻz katakchasini taklif qilishi mumkin; har birini bir marta tasdiqlaysiz. Qarang: [`docs/modules.md`](docs/modules.md) |
| **Qoʻshimchalar** | Modul kartochkasi sifatida ulanadigan ixtiyoriy ilovalar; [`addons/`](addons/) da saqlanadi va hech qachon launcherga yigʻilmaydi |
| **Soat ekran himoyachisi** | Iz qolmasin deb porlash ustida siljiydigan soat |
| **Pult tugmalari** | Standart launcher ketgandan keyin Kirish, YouTube va tishli tugmalar ishlashda davom etishini taʼminlaydi |

Mavzular, kalitlar va qoʻshimchalar birga, skrinshotlar bilan: [cleanyour.tv/addons](https://cleanyour.tv/addons).

**Koʻproq:** [docs/launcher.md](docs/launcher.md) launcher sozlamalari sahifasini qatorma-qator, standart
bosh ekranni oʻzgartirishni, tillarni, pult tugmalarini, Soat ekran himoyachisini, navigatsiyani va oʻzingiz
yigʻishni tushuntiradi.

## Televizoringizni tiklash

Qoʻllagan roʻyxatingizni saqlang. Bitta qator televizorni eski holatiga qaytaradi:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| Nima | Qanday |
|---|---|
| **Avval yozuv** | `tools/disable.sh` biror narsani oʻzgartirishdan oldin televizor qanday koʻrinishini `~/.cleanyourtv/` ga yozadi. |
| **Tiklash yozuvni qayta qoʻllaydi** | Oʻzingiz oʻchirgan paket oʻchiq qoladi va Home unga ega boʻlgan komponentga qaytadi. |
| **Launcher oʻrnatilgan holda qoladi** | Olib tashlamoqchi boʻlsangiz televizorning Sozlamalar → Ilovalar boʻlimidan olib tashlang. |
| **Kompyuter yoʻqmi?** | Televizorga yetib boʻlmasa, uning oʻz Sozlamalar → Ilovalar sahifasi ham paketlarni qayta yoqishi mumkin. |

**Koʻproq:** [docs/restore.md](docs/restore.md) yozuv nimalarni saqlashini, standart launcher argumenti
qachon muhimligini va yozuvsiz nima boʻlishini tushuntiradi.

## Firmware yangilanishidan keyin

Yangilanish tozalangan tizim ustiga oʻrnatiladi: roʻyxatdagi paketlar qaytadi, Home standart
launcherga oʻtadi va hech qanday roʻyxat tilga olmagan yangi oldindan yuklangan ilovalar paydo boʻladi.
[Tezkor boshlash](#tezkor-boshlash) 1-qadamidagidek ADB ni qayta oching, soʻng televizorni qoʻllagan
roʻyxatingiz bilan solishtiring; `--fix` eski holatiga qaytaradi:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

**Koʻproq:** [docs/firmware-updates.md](docs/firmware-updates.md) chiqish kodlarini, haftalik tekshiruvni,
`--fix` nima oʻzgartirishini, standart launcherni Home sifatida saqlaydigan televizorlarni va yangilovchini
oʻchiradigan roʻyxatlarni tushuntiradi.

## Nimadir notoʻgʻri ketganda

Boshimizga kelgan hamma narsaning toʻliq roʻyxati [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) da. Eng
muhim uchtasi:

- 🍎 **macOS port ochiq boʻlsa-da "No route to host" deb xabar beradi**: Mahalliy tarmoq maxfiyligi terminal ilovasini mahalliy pasttarmoqdan ushlab turadi va ruxsat mijozga emas, shu ilovaga tegishli; shuning uchun standart `adb` va `tools/tv.py` bir xil tarzda muvaffaqiyatsiz boʻladi (macOS 26 da koʻrilgan). System Settings → Privacy & Security → Local Network ostida Terminal, iTerm2 yoki VS Code ga ruxsat bering, soʻng uni yopib qayta oching ([Nosozliklarni bartaraf etish](docs/TROUBLESHOOTING.md#connecting)).
- ⚠️ **Standart launcherni faqat yangisi oʻrnatilib ishlagandan keyin oʻchiring**, aks holda Home zavod sinov ilovasiga tushadi. Oʻrnatish skripti bu tartibni siz uchun belgilaydi.
- 🔁 **Google TV da sozlash sehrgari** (`com.google.android.tungsten.setupwraith`) standart launcher oʻchirilgandan keyin Home ni egallab tizimni qulash tsikliga solishi mumkin. Uni ham oʻchiring va tiklash qaytarishi uchun `debloat.txt` ga qoʻshing.
  - Lekin avval `dumpsys wifi | grep -o 'cname=[^ ]*'` ni tekshiring: Wi-Fi tarmogʻi sozlash paytida saqlangan boʻlsa setupwraith ga tegishli va uni oʻchirish tarmoqni oʻchiradi (avval Sozlamalardan qayta ulang; siz buni qilmaguncha `tools/disable.sh` setupwraith ni oʻtkazib yuboradi).
  - Google ning oʻz Chromecast ida va Google TV Streamer da telefondan uzatishni ham buzadi, qarang: [Nosozliklarni bartaraf etish](docs/TROUBLESHOOTING.md).
  - Allaqachon tiqilib qoldingizmi? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Koʻp soʻraladigan savollar

<details>
<summary><b>Kod yozmayman va noutbukim yoʻq. Baribir qila olamanmi?</b></summary>

Ha. Mustaqil dasturchilar CleanYourTV ning TV roʻyxatlari asosida xuddi shu tozalashni Wi-Fi orqali kodlash
agentisiz bajaradigan bepul, ochiq kodli ilovalar yaratgan. Har biri avval reja koʻrsatadi, faqat ilovalarni
oʻchiradi va hamma narsani qaytara oladi:

| Ilova | Uchun | Muallif |
|---|---|---|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | Android telefon | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | Android telefon | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | Android telefon, Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac va Windows | [@wingedpanther](https://github.com/wingedpanther) |

Bu ilovalar mustaqil loyihalar; CleanYourTV yoki uning taʼminotchisi bilan bogʻliq emas va ular tomonidan
maʼqullanmagan. Har biri oʻz litsenziyasi bilan, boricha taqdim etiladi. Ilova muammolarini shu ilovaning
omboriga xabar qiling. Koʻproq: [cleanyour.tv/app](https://cleanyour.tv/app).

</details>

<details>
<summary><b>Bu xavfsizmi? Televizorni ishdan chiqarishi mumkinmi?</b></summary>

Hech narsa flash qilinmaydi, root qilinmaydi yoki oʻchirib tashlanmaydi; paketlar foydalanuvchi profilida
oʻchiriladi. Xavf — televizor tayanadigan narsani oʻchirish; shuning uchun roʻyxatlar tayyor tiklash skripti
bilan kichik partiyalarda qoʻllanadi. Televizorning oʻz Sozlamalar → Ilovalar sahifasi vositalarsiz ham
paketlarni qayta yoqishi mumkin. Televizor shunga yetarli ochilmasa, tiklash menyusidan zavod sozlamalariga
qaytarish odatda qaytish yoʻlidir.

Koʻproq: [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>Firmware yangilanishlari-chi?</b></summary>

Yangilovchi koʻp roʻyxatlarda yoqilgan holda qoldiriladi, shuning uchun firmware yangilanishlari odatiy
tartibda keladi. Baʼzi roʻyxatlar televizorning oʻz yangilovchisini oʻchiradi; ularda yangilanish faqat uni
qayta yoqqaningizda keladi. Yangilanish kelganda tozalangan tizim ustiga oʻrnatiladi, shuning uchun paket
qaytadi va skriptni qayta ishga tushirasiz: qarang [Firmware yangilanishidan keyin](#firmware-yangilanishidan-keyin).

</details>

<details>
<summary><b>Ilovalar buzilmaydimi?</b></summary>

Oqim ilovalari tegilmagan Play Store orqali yangilanishda davom etadi.
Launcher faqat oʻrnatilganlarni roʻyxatlaydi va ularni oʻn yildan beri barqaror intent lar bilan ishga tushiradi.

</details>

<details>
<summary><b>Ilovalarimni qanday qayta tartiblayman?</b></summary>

**Sozlamalar** (yuqori oʻngda) ustida OK ni bosib turing va **Ilovalarni qayta tartiblash** ni tanlang. OK
ilovani oladi, strelkalar uni koʻchiradi, Orqaga joyiga qoʻyadi va yana Orqaga tugatadi. Katakchada OK ni
bosib turish ham **Koʻchirish** (`tile_menu`, standart yoqilgan) ni taklif qiladi — shu ilovani darhol oladi.
Koʻproq: [docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>Buni boshqa uydagi kimdir uchun qila olamanmi?</b></summary>

Ha, bir xil Wi-Fi oʻrniga shaxsiy [Tailscale](https://tailscale.com) ulanishi orqali: yo ularning
televizorida Tailscale oʻrnatilgan va siz bilan ulashilgan, yo ularning uyidagi kompyuter yoki Apple TV
trafikni unga yoʻnaltiradi. Hech qachon ularning routerida ADB portini ochib emas. Qadamma-qadam
[docs/REMOTE.md](docs/REMOTE.md) da.

</details>

<details>
<summary><b>ADB kirishi xavfsizlik teshigi emasmi?</b></summary>

Televizorda bir marta, ochiqchasiga "bu kompyuterga ruxsat ber" demakdir; Android ilovasini ishlab chiqish
bilan bir xil. Tarmoq ADB si 5555-portda shifrsiz ishlaydi va baʼzi firmware da mahalliy tarmoqdagi har qanday
qurilma shell oladi; shuning uchun ishingiz tugagach yoping:

1. Televizorda: Dasturchi sozlamalari → **USB debugging**, **Network/ADB debugging** va
   **Wireless debugging**, televizorda qaysilari boʻlsa oʻchiring va USB debugging ruxsatlarini bekor qiling.
2. Televizorni qayta yoqing, soʻng port yopilganini kompyuteringizdan tasdiqlang:
   `python3 tools/adb-closed.py <tv ip>` port yopiqligini aytishi kerak.

Launcher debugging siz ham ishlashda davom etadi. Wireless debugging ning oʻz portini tekshirish bilan birga
koʻproq: [docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>Ilovalar ichidagi reklamalarni ham bloklashi mumkinmi?</b></summary>

Qisman, Android ning Private DNS i bilan (Android 9 va undan yangiroq). Uni [AdGuard DNS](https://adguard-dns.io)
kabi reklama bloklovchi resolver ga yoʻnaltiring; har bir ilovaning reklama va kuzatuv domenlari tizim boʻylab
hal qilinmaydigan boʻladi va bu qayta yoqishlardan keyin ham saqlanadi. Narxi: oʻsha resolver televizor
qiladigan har bir DNS soʻrovini koʻradi va YouTube reklamalarini olib tashlamaydi.

Ikki buyruq va qanday qaytish bilan birga koʻproq: [docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>Televizorning oʻz Sozlamalarida ham almashtirishga arziydigan kalitlar bormi?</b></summary>

Bir nechtasi bor va ADB talab qilmaydi: reklama identifikatori, Joylashuv, Foydalanish va diagnostika va
Doimo mavjud skanerlash, shuningdek standart launcher qoladigan joylarda Faqat ilovalar rejimi. Foydalanish
va diagnostikani tozalashdan oldin oʻzgartiring; baʼzi televizorlarda uning sahifasi tozalash bilan birga ketadi.

Google TV, Android TV va Fire OS uchun yoʻllar bilan birga koʻproq:
[docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>AirPlay?</b></summary>

Bu ombordan emas. Android telefonlar uchun ichki Chromecast qoladi. iPhone lar uchun
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) ishlaydi; uning uchun
oʻrnatish skripti [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84) da.

</details>

<details>
<summary><b>APK ni shunchaki yuklab olsam boʻladimi?</b></summary>

[Qoʻlda bajarish](#qoʻlda-bajarish) dagi bir qatorlik skript bilan yigʻing; bir daqiqa ketadi va televizoringizga
nima ketishini siz nazorat qilasiz. Buning uchun u yerda nomlangan JDK va Android SDK kerak. Tayyor ikkilik
fayllar yoʻq.

</details>

## Hikoya

2026-yil 17-oktabrda ota-onam televizorining Claude Code bilan Wi-Fi orqali tozalangan
[oldin va keyin holatini](https://x.com/gathint/status/2111379569030939102) joyladim. 8,6 million koʻrishga
yetdi, odamlar xuddi shuni oʻz televizorlariga qila boshladi va javoblari bu yerdagi roʻyxatlarga aylandi:
birinchi toʻrt kunda 200 dan ortiq hissa qoʻshuvchidan 170 dan ortiq TV modeli. Ombor 19-igacha tv-vibehack
deb ataldi; eski havolalar ishlaydi.

Ikki hafta oʻtib, 19–25 oktabr haftasida GitHub ning 1-oʻrindagi trend ombori boʻldi; 46 mingdan ortiq yulduz,
5,7 ming fork, 14 kunda 1,4 million koʻrish va 260 ming klon. Roʻyxatlar endi 457 TV modelini va 122 brendni
qamrab oladi. Matbuotdagi aks: 412 maqola (54 mamlakatdan) va YouTube hamda Bilibili da 229 video, hammasi
[docs/PRESS.md](docs/PRESS.md) da (raqamlar 2026-yil 31-oktabr holatiga).

## Hissa qoʻshish

Eng foydali hissa — televizoringizning tozalash roʻyxati; shunday qilib shu modelga ega keyingi kishi uni
qayta kashf qilishi shart emas. Format va soʻragan sinovlarimiz [CONTRIBUTING.md](CONTRIBUTING.md) da.
Kodlash agenti orqali hissa qoʻshyapsizmi? [AGENTS.md](AGENTS.md) unga xuddi shu qoidalarni beradi.

| Qanday yordam | Nima anglatadi |
|---|---|
| **Televizoringizning tozalash roʻyxati** | Eng foydalisi: shu modelga ega keyingi kishi kashfiyotni oʻtkazib yuboradi. |
| **Launcher va prompt** | Launcher yaxshilanishlari va promptning aniqroq ifodalanishi ham mamnuniyat bilan qabul qilinadi. |
| **Ustiga biror narsa qildingizmi?** | Launcher, port, mavzu: [SHOWCASE.md](SHOWCASE.md) ga qoʻshing; u yerda bosh ekranlar, fork lar, qoʻshimchalar, vositalar va boshqa platformalar boʻyicha tartiblangan, TV brendi boʻyicha indekslangan ikki yuz oʻn ikkita loyiha allaqachon yashaydi. |

Ishlari bu omborda joylashgan odamlar:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Hissa qoʻshuvchilar"></a>

<details>
<summary><strong>Yulduzlar tarixi</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Yulduzlar tarixi diagrammasi" width="640"></a>

</details>

## Aloqa

| Nima uchun | Qayerda |
|---|---|
| **Televizoringiz haqidagi savollar** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Matbuot va soʻrovlar** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Xavfsizlik muammolari** | [Security yorligʻi](https://github.com/Gathin23/cleanyourtv/security) orqali maxfiy xabar qiling, qarang [`SECURITY.md`](SECURITY.md) |

## Huquqiy maʼlumot

- CleanYourTV mustaqil ochiq kodli loyiha. Hech qanday televizor ishlab chiqaruvchisi yoki platforma bilan
  bogʻliq emas, ular tomonidan maʼqullanmaydi yoki homiylik qilinmaydi. Brend va model nomlari faqat qaysi
  televizorda roʻyxat sinovdan oʻtganini aytish uchun ishlatiladi va egalarning savdo belgilari boʻlib qoladi.
- Faqat televizorning oʻzi egasiga taklif qiladigan sozlamalar orqali ishlaydi: Dasturchi sozlamalari, ADB
  debugging va Android ning oʻz `pm disable-user` buyrugʻi; Sozlamalar > Ilovalar > Oʻchirish bilan bir xil
  kalit. Televizorni root qilmaydi, firmware flash qilmaydi, hech qanday qulf, DRM yoki nusxa himoyasini
  chetlab oʻtmaydi; ombor ham hech qanday ishlab chiqaruvchi dasturi, firmware yoki APK saqlamaydi.
- Hech narsa oʻchirib tashlanmaydi va `tools/restore.sh` hamma narsani qaytaradi. Buni egalik qilgan
  televizoringizda, xavf oʻzingizga tegishli holda ishga tushirasiz; televizor dasturini oʻzgartirish kafolat
  yoki qoʻllab-quvvatlashga taʼsir qilishi mumkin.
- Bu yerdagi hamma narsa quyidagi MIT litsenziyasi ostida, hech qanday kafolatsiz, boricha taqdim etiladi.

## Litsenziya

MIT, qarang [`LICENSE`](LICENSE). Outfit shrifti SIL Open Font License ostida, qarang
`launcher/FONT-LICENSE-OFL.txt`.
