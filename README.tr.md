<p align="center">
  <a href="https://cleanyour.tv/halloween"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner-halloween.png" alt="CleanYourTV, Cadılar Bayramı sürümü. Televizyonunuz, reklamlar hariç. Herhangi bir Android TV veya Google TV'deki gereksiz yazılımları Wi-Fi üzerinden temizleyin ve reklam dolu ana ekranı Apple TV'ye benzeyen temiz bir başlatıcıyla değiştirin. Root yok, kablo yok, hiçbir şey kaldırılmaz ve tek satırla geri yükleme. Artık bir balkabağıyla." width="800"></a>
</p>

<p align="center">
  Diğer diller: <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · Türkçe
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Yıldızlar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="Birleştirilen PR'lar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Fork'lar"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Katkıda bulunanlar"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Destek olun, bir kahve ısmarlayın"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="Product Hunt'ta CleanYourTV"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="Haftanın 1 numaralı trend deposu, 19–25 Ekim 2026, trendshift.io'ya göre" width="360"></a>
</p>

<p align="center">
  <a href="docs/PRESS.md"><img src="docs/card-halloween.jpg" alt="398 TV modeli · 104 marka · 31 dil · 572 KB başlatıcı. Yer aldığı yayınlar: TechCrunch, TechRadar, Tom's Guide, Mashable, Android Authority, XDA, FlatpanelsHD, It's FOSS, Phandroid, GIGAZINE, Xataka, WinFuture, Frandroid, Macitynet, Techaeris, TechPP ve 396 yayın daha: 54 ülkeden 412 makale. Önce: sponsorlu reklam içeren standart Google TV ana ekranı. Sonra: Clean Your TV başlatıcısı." width="800"></a>
</p>

## Buradan başlayın

| Elinizde olan | Gidilecek yer |
|---|---|
| **Bir Android TV veya Google TV** | [Hızlı başlangıç](#hızlı-başlangıç): işi yaklaşık on beş dakikada bir kodlama asistanı yapar. |
| **Samsung, LG, Roku, Vizio veya Hisense VIDAA bir TV** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): CleanYourTV orada çalışamaz, ancak takibi kumandanızla kapatabilirsiniz. |
| **Yalnızca bir Android telefon** | Topluluk tarafından geliştirilen [Clean Your TV uygulaması](https://cleanyour.tv/app): dizüstü bilgisayar yok, kodlama asistanı yok. |
| **Önce görmek istiyorum** | [Neler elde edersiniz](#neler-elde-edersiniz): yeni ana ekranın ekran görüntüleri. |

**Resmî bağlantılar:** [cleanyour.tv](https://cleanyour.tv) ve bu depo. Bu adı kullanan diğer alan adları,
örneğin cleanyourtv.com, bu projeye ait değildir ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).
CleanYourTV hiçbir uygulama mağazasında uygulama yayınlamaz; clean-your-tv.net de bize ait değildir.

[Nasıl çalışır](#nasıl-çalışır) · [Hızlı başlangıç](#hızlı-başlangıç) · [Neler elde edersiniz](#neler-elde-edersiniz) ·
[Desteklenen TV'ler](#desteklenen-tvler) · [Elle yapmak](#elle-yapmak) · [Başlatıcı](#başlatıcı) ·
[Geri yükleme](#tvnizi-geri-yükleme) · [Bellenim güncellemeleri](#bir-bellenim-güncellemesinden-sonra) ·
[Sık sorulan sorular](#sık-sorulan-sorular) · [Katkıda bulunma](#katkıda-bulunma)

## Nasıl çalışır

Android TV'lerde Android telefonlardaki hata ayıklama köprüsünün (ADB) aynısı bulunur. TV'nin geliştirici
seçeneklerinden USB hata ayıklamayı açın, bilgisayarınıza bir kez izin verin; gerisi standart Android
araçlarıyla yapılır:

| Adım | Ne olur |
|---|---|
| **Yedekleme** | Yüklü paketlerin listesi ve ayarlar. |
| **Devre dışı bırakma** | Üreticinin telemetrisi, reklam hizmetleri, içerik satırları ve önceden yüklenmiş gereksiz yazılımlar, `pm disable-user` ile. Hiçbir şey kaldırılmaz, bu yüzden bir geri yükleme betiği her adımı geri alabilir. |
| **Kurulum** | Küçük, yerel bir başlatıcı kurulur ve varsayılan ana ekran yapılır. |

**Root yok, kablo yok, hiçbir şey kaldırılmaz ve fikrinizi değiştirirseniz tek satırla geri yükleme.**

Gereksiz yazılımlar markaya göre değişir; bu yüzden istem (prompt), kodlama asistanınızın bunları TV'nizde
kendisinin bulmasını ve kullanıyor olabileceğiniz bir şeye dokunmadan önce size sormasını sağlar.

## Hızlı başlangıç

| Elinizde olan | Yapılacak |
|---|---|
| **Bir bilgisayar** | İstemi bir kodlama asistanıyla kullanın: aşağıdaki üç adım. |
| **Bir bilgisayar, ama kodlama asistanı yok** | Üç adımı kendiniz çalıştırın: komutlar [Elle yapmak](#elle-yapmak) bölümünde. |
| **Yalnızca bir Android telefon** | [@udhaykumarbala](https://github.com/udhaykumarbala) tarafından geliştirilen topluluk uygulaması [Clean Your TV](https://cleanyour.tv/app)'yi kullanın: bağlan, analiz et, temizle, yaklaşık beş dakikada. Dizüstü bilgisayar yok, kodlama asistanı yok. |

Aşağıdaki üç adım bir kodlama asistanı için yazılmıştır: bilgisayarın TV ile aynı Wi-Fi ağında olması,
üzerinde bir kabuk (shell), Python 3, 17 ile 22 arası bir JDK ve build-tools 34 ile birlikte Android SDK
platform 34 bulunması gerekir (asistan başlatıcıyı orada derler).
Asistan olmadan [Elle yapmak](#elle-yapmak) aynı iştir, yalnızca siz çalıştırırsınız; kurulum
komutları da oradadır.

Şunların hepsi çalışır: Claude Code ([0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme) veya bir [Anthropic planı](https://claude.com/pricing) üzerinde), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli) veya [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Nerede | Ne yapılır |
|---|---|
| **1. TV'de** | Ayarlar → Cihaz Tercihleri → Hakkında → **Derleme** (Build) üzerinde yedi kez OK'a basın → geri → Geliştirici seçenekleri → **USB hata ayıklama** açık. Ağ veya ADB hata ayıklama anahtarı görürseniz onu da açın. TV'yi açık tutun. |
| **2. Bilgisayarınızda** | Depoyu klonlayın ve o klasörü asistanınızda açın (`git clone https://github.com/Gathin23/cleanyourtv`), ardından [`PROMPT.md`](PROMPT.md) dosyasını yapıştırıp TV modelinizi yazın. |
| **3. Takip edin** | TV'de "USB hata ayıklamaya izin verilsin mi?" sorusunu onaylayın, ardından kontrol listesini yanıtlayın: hangi uygulamaları kullandığınız, dock boyutu ve uygulama sırası, sesli asistanın, Chromecast'in ve güncellemelerin kalıp kalmayacağı. |

Klonlamak önemlidir: asistan kendi betiklerini yazmak yerine bu deponun `tools/disable.sh` ve
`launcher/build-and-install.sh` betiklerini yeniden kullanır; yedekleme, Ana ekran koruması ve geri yükleme
kaydı da oradadır.

Asistan yedekler, gereksiz yazılımları temizler, başlatıcıyı derleyip kurar ve her adımı TV'den alınan ekran
görüntüleriyle kontrol eder. Bu depo bağlam olarak verildiğinde yaklaşık on beş dakika sürer.

## Neler elde edersiniz

Apple TV'ye benzeyen ve yolunuza çıkmayan bir ana ekran:

| Bölüm | Nasıl görünür |
|---|---|
| **Kapkara bir ana ekran** | En sevdiğiniz dört uygulamanın, Apple TV'deki yerinde, yuvarlatılmış bir şerit içinde durduğu bir dock. |
| **Bir üst raf** | Odaklanılan uygulamanın adını büyük yazıyla gösterir; arkasında uygulamanın görselinden alınan renkte bir ortam ışıltısı vardır. |
| **Diğer uygulamalarınız bir ızgarada** | Aşağı tuşuna bastığınızda yukarı kayar. |
| **Tek bir yüzen cam vurgu** | Kutucuktan kutucuğa yaylanarak geçer. Odaklanılan kutucuk yükselir ve adını gösterir. |
| **Üç buzlu cam düğme** | Girişler (TV ayarlarında verdiğiniz adlarla HDMI portları, AV, Canlı TV), ağ adıyla birlikte Wi-Fi veya Ethernet, Ayarlar. |
| **Bir selamlama, ince bir saat ve tarih** | [Outfit](https://fonts.google.com/specimen/Outfit) yazı tipinde ve buna uygun bir Saat ekran koruyucusu. Saatin altında tek satır için config'de veya bir temada `show_stats` ayarlayın: CPU, boş bellek ve boş depolama. Siz açmadıkça kapalı kalır ve yalnızca Ana ekran görünürken yenilenir. |
| **Temalar ve modül kartları** | Temalar renkleri, duvar kâğıdını, dock'u ve sütunları yeniden derlemeden değiştirir; modül kartları ise yalnızca sizin onayınızla diğer uygulamaların bir kutucuk eklemesine izin verir. |

Ve size karşı çalışmayı bırakan bir TV: sponsorlu satırlar yok, "Keşfet" dürtmeleri yok, eve veri gönderen
analitik yok, hiç istemediğiniz uygulamalar yok.

| | |
|---|---|
| ![Üst raf, odaklanılan uygulamanın adını kendi rengindeki ortam ışıltısının üzerinde gösterir](docs/focus-2-up-netflix.png) | ![Girişler açılır penceresi: bağlı olanlarla HDMI portları, AV ve Canlı TV](docs/inputs-menu.jpg) |
| *Üst raf ve ortam ışıltısı* | *Girişler: bağlantı durumuyla HDMI, AV, Canlı TV* |
| ![Bir kutucuğun üzerinde cam vurgu ve altında adıyla ızgara](docs/focus-1-fancode.png) | ![Saat ekran koruyucusu: ışıltının üzerinde kayan ince bir saat](docs/clock-screensaver.png) |
| *Izgara, tek cam vurgu, kutucuğun adı* | *Saat ekran koruyucusu, ekran izi kalmasın diye kayar* |
| ![Aurora teması: yeniden derlemeden gönderilen bir duvar kâğıdı ve renk tonlu düğmeler](themes/aurora/screenshot.jpg) | ![Bir temanın config.json dosyasından altı sütun ve altı uygulamalık dock](themes/six-columns/screenshot.jpg) |
| *`tools/theme.sh` ile gönderilen Aurora teması* | *Altı sütun ve altı uygulamalık dock, aynı başlatıcı* |
| ![Bir kutucuğu taşımak: OK basılı tutuldu, Taşı seçildi, kutucuk alındı ve diğerleri soluklaştı](docs/tile-move.jpg) | ![Uygulama kitaplığı: yüklü uygulamalar, tek basışla bir uygulama Ana ekrana geri eklenir](docs/app-library.jpg) |
| *`tile_menu` ile bir kutucukta OK'u basılı tutun, Taşı: oklar onu taşır, Geri yerine bırakır* | *Uygulama kitaplığı: tek basış, kaldırılmış bir uygulamayı Ana ekrana geri koyar* |

## Desteklenen TV'ler

**Android TV** veya **Google TV** çalıştıran her şey.

| Platform | Ne çalışır |
|---|---|
| **Android TV, Google TV** | Gereksiz yazılım temizliği ve başlatıcı. |
| **Fire TV** | Yalnızca temizlik kısmı, çünkü Fire OS Ana ekranı üçüncü taraf bir başlatıcıya bırakmaz. 2015 Fire TV Box'ta (Fire OS 5) bu kısım bile başarısız olur: her `pm` yolu reddedilir. |
| **Çin pazarına yönelik birkaç TCL modeli** | Hiçbir başlatıcının kurulmasına izin vermezler; listelerinin başlığında bu belirtilir. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | Desteklenmez (ADB yok). Bunun yerine takibi kapatın: [cleanyour.tv/not-android](https://cleanyour.tv/not-android). Dört webOS uyarlaması için [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) dosyasına, bir Tizen uyarlaması düşünüyorsanız 2021 model bir Samsung TV'nin Wi-Fi üzerinden neye yanıt verdiği için (ADB yok, dolayısıyla `pm disable-user` da yok) [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) dosyasına bakın. |

Katkıda bulunanlardan gelen test edilmiş listeler, her model için bir klasör olarak [`examples/`](examples/)
içindedir. En çok listesi olan markalar, her biri web sitesindeki modellerine bağlantılı:

<!-- tv-brands:start: generated by tools/gen_tables.py from examples/*/tv.json; edit those, not this -->

| | | | | | |
|---|---|---|---|---|---|
| [TCL](https://cleanyour.tv/tvs?q=TCL) · 80 | [Sony](https://cleanyour.tv/tvs?q=Sony) · 44 | [Xiaomi](https://cleanyour.tv/tvs?q=Xiaomi) · 37 | [Philips](https://cleanyour.tv/tvs?q=Philips) · 21 | [Skyworth](https://cleanyour.tv/tvs?q=Skyworth) · 15 | [Hisense](https://cleanyour.tv/tvs?q=Hisense) · 14 |
| [Amazon Fire TV](https://cleanyour.tv/tvs?q=Amazon%20Fire%20TV) · 9 | [SHARP](https://cleanyour.tv/tvs?q=SHARP) · 9 | [BenQ](https://cleanyour.tv/tvs?q=BenQ) · 6 | [JVC](https://cleanyour.tv/tvs?q=JVC) · 6 | [Panasonic](https://cleanyour.tv/tvs?q=Panasonic) · 6 | [Haier](https://cleanyour.tv/tvs?q=Haier) · 5 |
| [OnePlus](https://cleanyour.tv/tvs?q=OnePlus) · 4 | [Thomson](https://cleanyour.tv/tvs?q=Thomson) · 4 | [Toshiba](https://cleanyour.tv/tvs?q=Toshiba) · 4 | [Acer](https://cleanyour.tv/tvs?q=Acer) · 3 | [Google](https://cleanyour.tv/tvs?q=Google) · 3 | [Grundig](https://cleanyour.tv/tvs?q=Grundig) · 3 |
| [Motorola](https://cleanyour.tv/tvs?q=Motorola) · 3 | [NVIDIA Shield](https://cleanyour.tv/tvs?q=NVIDIA%20Shield) · 3 | [RCA](https://cleanyour.tv/tvs?q=RCA) · 3 | [Redmi](https://cleanyour.tv/tvs?q=Redmi) · 3 | [Vu](https://cleanyour.tv/tvs?q=Vu) · 3 | [Artel](https://cleanyour.tv/tvs?q=Artel) · 2 |
| [CHiQ](https://cleanyour.tv/tvs?q=CHiQ) · 2 | [EKO](https://cleanyour.tv/tvs?q=EKO) · 2 | [FFalcon](https://cleanyour.tv/tvs?q=FFalcon) · 2 | [Hitachi](https://cleanyour.tv/tvs?q=Hitachi) · 2 | [Insignia Fire TV](https://cleanyour.tv/tvs?q=Insignia%20Fire%20TV) · 2 | [Kalley](https://cleanyour.tv/tvs?q=Kalley) · 2 |

<details>
<summary>+60 more brands and 14 projector brands</summary>

**Kogan** 2 · **Master-G** 2 · **Nokia** 2 · **onn** 2 · **PRISM+** 2 · **RockTek** 2 · **Sankey** 2 · **Sber** 2 ·
**Vidda** 2 ·
AiPro2K · AIWA · AQUA · ASUS · Beko · Bell Canada · BGH · Caxium · Cecotec · Challenger · Compac · Dcolor · DEXP ·
Dialog · DYON · Freebox · Hikeen · Home+ · Impex · INCLO · Karbonn · Koryo · KTC · Liberton · Lumio · LYNCAST ·
Magyar Telekom · MECOOL · MTC · Next · Noblex · OK. · OPPO · Panavox · PEAQ · Polytron · PPTV · Prosonic · Salora ·
SANYO · Seleco · Super General · Síragon · TD Systems · Tele System · TiVo · Tornado · Turkcell · Tuvio · Verizon ·
Vision ·
Alwtniet, Anker Nebula, BYINTEK, Epson, Formovie, Gadnic, HOTACK, HY320, JMGO, Magcubic, Portronics, Stellamore, Wanbo and XGIMI projectors

</details>

<!-- tv-brands:end -->

**TV'nizi bulun:** [cleanyour.tv/tvs](https://cleanyour.tv/tvs) üzerinde arayın veya
[examples/README.md](examples/README.md) dosyasındaki tam tabloya bakın.

TV'niz burada yok mu? İstem gereksiz yazılımları kendisi bulur. Ardından listenizi ekleyin; sizinle aynı modele
sahip bir sonraki kişi bu adımı atlayabilsin: [CONTRIBUTING.md](CONTRIBUTING.md).

## Elle yapmak

Asistanın yaptığı her şeyi kendiniz de çalıştırabilirsiniz. Önce bilgisayarda iki şey bulunmalıdır:

| Gereken | Ne için |
|---|---|
| **Python 3** (herhangi bir 3.x) | Araçlar. Aşağıdaki venv komutu, araçların içe aktardığı her şeyi kurar. |
| **JDK 17 ile 22 arası** (`jlink` dahil tam bir JDK) ve **build-tools 34.0.0 veya daha yeni herhangi bir 34.x** ile **Android SDK platform 34** | Başlatıcıyı derlemek. JDK 23 ve sonrası Gradle 8.9'u durdurur. Derlemenin okuduğu platform 34'tür; daha yeni bir platform onun yerini tutmaz. macOS'ta bunların hiçbiri kurulu değilse: `brew install openjdk@17 android-commandlinetools`, ardından `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. Linux'ta kurulum betiği `~/Android/Sdk` dizinine ve PATH'inizdeki `java`'ya bakar. |

`tools/tv.py`, 5555 numaralı porttaki ağ ADB'si ile konuşur. Bu port kapalı kalıyorsa ve TV eşleştirme
koduyla Kablosuz hata ayıklama sunuyorsa (Chromecast with Google TV ve Google TV Streamer bunu yapar),
[Sorun giderme](docs/TROUBLESHOOTING.md#connecting) belgesinde anlatıldığı gibi standart `adb` ile bir kez
eşleştirin ve önce `adb tcpip 5555` komutunu çalıştırın.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # TV'nizin IP adresi

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android sürümü, bellenim
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # TV'de tek bir kabuk komutu
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # bir temizlik listesini uygula
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # geri al, standart başlatıcı geri gelir
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # bir bellenim güncellemesinin neyi geri aldığı
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # başlatıcıyı derle ve Ana ekran yap
tools/theme.sh themes/aurora                                 # bir tema gönder; --reset onu kaldırır
```

| Araç | Ne yapar |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Saf Python ADB istemcisi: kabuk, toplu komut, gönderme (push), çekme (pull), yeniden başlatma ve isteğe bağlı olarak önce kumanda tuşlarına basarak ekran görüntüsü. Standart `adb` ikili dosyasının engellendiği yerlerde çalışır. Şifreli Kablosuz hata ayıklama için isteğe bağlı [eşleştirilmiş Google ADB arka ucu](tools/native-adb.md). |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Başlatıcıyı veya bir eklentiyi Docker'da (JDK 17, Android SDK 34) derler; böylece bu bilgisayarda Docker dışında hiçbir şeye gerek kalmaz. Sonucu `SKIP_BUILD=1` ile kurun. [Kılavuz](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | APK gibi büyük dosyaları base64 parçaları hâlinde yükler ve sağlama toplamını doğrular. |
| [`tools/relay.py`](tools/relay.py) | Korumalı alanı (sandbox) ağınıza erişemeyen asistanlar için yerel bir portu TV'nin ADB portuna yönlendirir. Yalnızca bu bilgisayarda dinler. [Kılavuz](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Bir temizlik listesini uygular veya geri alır; geri alma, değişiklikten önce kaydedilen durumdan yapılır. |
| [`tools/recovery.py`](tools/recovery.py) | `disable.sh`'nin kendini geri alabilmesi için gereken kaydı yazar ve geri yükleme komut grubunu yazdırır. |
| [`tools/check.py`](tools/check.py) | Bir bellenim güncellemesinin neyi geri aldığını görmek için TV'yi uygulanan listeyle karşılaştırır; `--fix` eski hâline getirir. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Hata ayıklama kapatıldıktan sonra TV'nin ağ ADB portunun kapalı olduğunu kontrol eder: TV bağlantıyı reddederse 0, port hâlâ açıksa 1, hiçbir şey yanıt vermezse veya kontrolün kendisi çalışamazsa 2 ile çıkar. `nc` gerekmez. |
| [`tools/philips-api.py`](tools/philips-api.py) | Yalnızca Philips: Home Assistant'ın kullandığı JointSpace kontrol API'sinin temizlikten önce ve sonra hâlâ yanıt verdiğini kontrol eder (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Önce HTTPS üzerinden 1926 portu, sonra 1925; yalnızca eşleştirme olmadan yanıt verenleri okur. |
| [`tools/privacy.py`](tools/privacy.py) | TV'yi ses, ACR ve telemetri adayları için denetler ve her biri için kanıtı yazdırır; `--write`, bulguları `disable.sh` ve `restore.sh`'nin zaten anladığı bir listeye dönüştürür. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | [`themes/`](themes/) içindeki bir klasörü başlatıcıya gönderir ve Ana ekranı yeniden başlatır. |
| [`tools/manage.py`](tools/manage.py) | Her eklentiyi, temayı ve anahtarı ekran görüntüsüyle listeler, TV'de neyin olduğunu gösterir ve oluşturucusunun kendi betiğini çalıştırarak birini kurar, günceller veya kaldırır. |
| [`tools/bench.py`](tools/bench.py) | HOME'a yanıt veren başlatıcıyı ölçer: tuş gecikmesi, odağın ne kadar ilerlediği, takılan kareler, soğuk başlatma, Ana ekrana dönüş ve boştayken harcadığı CPU ile PSS. TV'de hiçbir şeyi değiştirmez ve tuş başına ham değerleri JSON'a yazar; ek kurulum gerekmez. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Başlatıcının Perfetto izlerini (Ana ekrana dönüş, soğuk başlatma, D-pad) kaydeder ve zamanın nereye gittiğini, çalıştırma başına ve önce/sonra karşılaştırmalı olarak raporlar. İsteğe bağlı: [`requirements-perf.txt`](tools/requirements-perf.txt) gerekir. [Kılavuz](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Bir pull request için bu izlerden önce/sonra alev grafiği çizer: ortak bir zaman ölçeğinde iş parçacığı başına bir şerit; bir değişikliğin hedeflediği iş, toplamıyla birlikte macenta renkte. İz araçları gibi isteğe bağlıdır. [Kılavuz](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Önce ve sonra ekran görüntülerini piksel piksel karşılaştırır; saati veya maskelediğiniz her şeyi dışarıda bırakır ve çifti Wi-Fi adınız bulanıklaştırılmış olarak yazar. İz araçları gibi isteğe bağlıdır. [Kılavuz](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Yarış hafta sonu modu için bu sezonun F1 takvimini ücretsiz Jolpica API'sinden bir temaya yazar. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Küçültülmüş bir sürüm (yaklaşık 572 KB) derler, kurar, Ana ekranı değiştirir ve Ana ekran ona geçmezse geri alır. |

Betikler TV'yi asla Ana ekran uygulaması olmadan bırakmaz: yalnızca tanıdıkları standart bir başlatıcıyı devre
dışı bırakırlar ve başka bir başlatıcı HOME'a yanıt verene kadar listedeki Ana ekran uygulamasını atlarlar.
Kendi listeleriniz deponun dışında da durabilir: liste yolları komutu çalıştırdığınız dizine göredir, mutlak
yollar da çalışır.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Her betiğin yanında bir PowerShell ikizi vardır. Python 3 ve 17 ile 22 arası bir JDK kurun; SDK Manager'da
platform 34'ü ve build-tools 34'ü (34.0.0 veya daha yeni herhangi bir 34.x) ekleyin. Betik, bu aralıktaki bir
JDK'yı gösteriyorsa `JAVA_HOME`'u, aksi hâlde aynı aralıktaysa Android Studio ile gelen JDK'yı kullanır:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

Son ikisi geri alma ve tema gönderimidir; yukarıdaki bölümlerin bash ile verdiği `tools/restore.sh` ve
`tools/theme.sh` satırlarının karşılığıdır. Google TV'de geri alma, standart başlatıcıyı da argüman olarak alır:
`-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>Betikler Ana ekranı nasıl korur</b></summary>

Kurulum betikleri TV'ye Ana ekranın hangi başlatıcıya ait olduğunu sorar ve yalnızca tanıdıklarını devre dışı
bırakır; bunlar `KNOWN_STOCK_LAUNCHERS` içindeki dört pakettir: `com.google.android.tvlauncher`,
`com.google.android.apps.tv.launcherx`, `com.google.android.leanbacklauncher` ve Xiaomi'nin
`com.mitv.tvhome` (PatchWall) paketi. Bunu geçersiz kılmak için `STOCK_LAUNCHER=<package>`
(`-StockLauncher`) verin; dokunulmamasını istiyorsanız `none` yazın.

`tools/disable.sh` (`disable.ps1`), herhangi bir şeyi devre dışı bırakmadan önce aynı kuralı uygular:

- Liste Ana ekranın sahibi olan uygulamayı içeriyorsa ve TV'deki başka hiçbir uygulama HOME'a yanıt vermiyorsa,
  o girdiyi atlar, nedenini söyler ve listenin geri kalanını devre dışı bırakır. Böylece başlatıcı devrinden
  önce uygulanan bir liste, TV'yi başlatacak hiçbir şey olmadan bırakamaz.
- Kurulum sihirbazı, ayarlar uygulamaları, satıcıya ait bir `FallbackHome` ve fabrika test uygulamaları
  (Xiaomi'nin `com.duokan.factorytest` paketi) da HOME'a yanıt verir, ancak sayılmazlar.
- Google TV'de, setupwraith HOME'a yanıt verdiği ve etkin kaldığı sürece (listelenmemişse veya Wi-Fi ağı için
  tutulmuşsa) sahibini de korur: setupwraith, henüz Ana ekran olmamış kurulu bir başlatıcıdan önce yanıt verir,
  dolayısıyla Ana ekranı ele geçirirdi.

`launcher/build-and-install.sh` yeni başlatıcıyı Ana ekran yaptıktan sonra aynı listeyi yeniden çalıştırın,
girdi de uygulanır; `FORCE_HOME=1` (`$env:FORCE_HOME`) onu her durumda devre dışı bırakır.

</details>

## Mikrofon ve onun hakkında söylenebilecekler

"Bu paketlerden hangisi dinliyor?" sorusunun dürüst, tek satırlık bir yanıtı yoktur: bir izin taraması,
hiçbir şey kaydetmeyen ve `RECORD_AUDIO` iznine sahip elli sistem paketi gösterir; ACR istemcileri ise hiçbir ses
izni olmadan ekranı görür. [`tools/privacy.py`](tools/privacy.py), her ses, ACR ve telemetri adayı için kanıtıyla
birlikte TV'nin gerçekte ne söylediğini raporlar:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`; `--write` bunları normal bir temizlik
listesine dönüştürür. Denetimin yapamayacağı şey niyeti kanıtlamaktır: nedenini [docs/privacy.md](docs/privacy.md)
açıklıyor.

## Başlatıcı

Android TV için küçük, yerel bir ana ekran: **yaklaşık 572 KB**, düz Java, 1 GB RAM'li TV'ler için yapıldı.
Bulanıklık yok, video yok, ağ izni yok, arka plan hizmeti yok. **TV**'nin Android 8.0 (API 26) veya daha yeni
olması gerekir; bu, [`launcher/app/build.gradle`](launcher/app/build.gradle) içindeki `minSdk` değeridir. Daha eski
bir cihazda neyin kaldığı [Sorun giderme](docs/TROUBLESHOOTING.md) belgesindedir.

| Ne | Nasıl çalışır |
|---|---|
| **Yapılandırma** | Dock sırası, gizli uygulamalar, Canlı TV uygulaması, satır başına 4 ile 7 arası kutucuk, azaltılmış hareket. Hepsi [`config.xml`](launcher/app/src/main/res/values/config.xml) içinde |
| **TV'deki ayarlar** | Başlatıcı ayarları için Ayarlar üzerinde OK'u basılı tutun. İsteğe bağlı `launcher_settings_button`, normal bir tıklamanın onları doğrudan açmasını sağlar; TV ayarları ayarlar sayfasında ve kendi uygulama kutucuğunda kalır. |
| **Temalar** | Wi-Fi üzerinden gönderilen bir `config.json` ile arka plan, duvar kâğıdı ve düzen, yeniden derleme yok. Yerleşik `default/` dahil 15 tema [`themes/`](themes/) içinde gelir |
| **31 dil** | TV'nin diline uyar; İbranice için tamamen aynalanmış, sağdan sola bir düzen sunar |
| **Uygulama kitaplığı** | İsteğe bağlı: yüklü tüm uygulamalara göz atın ve Ana ekran kısayolları ekleyin veya kaldırın. Bkz. [uygulama kitaplığı kılavuzu](docs/app-library.md) |
| **Kutucuk menüsü** | İsteğe bağlı (`tile_menu`): Taşı, Ana ekrandan kaldır ve Uygulama bilgisi için bir kutucukta OK'u basılı tutun. Kapalıyken OK'u basılı tutmak Uygulama bilgisini açar |
| **Modül kartları** | Herhangi bir TV uygulaması kendi kutucuğunu sunabilir; her birini bir kez onaylarsınız. Bkz. [`docs/modules.md`](docs/modules.md) |
| **Eklentiler** | Modül kartı olarak bağlanan isteğe bağlı uygulamalar; [`addons/`](addons/) içinde tutulur ve asla başlatıcıya derlenmez |
| **Saat ekran koruyucusu** | Ekran izi kalmasın diye ışıltının üzerinde kayan bir saat |
| **Kumanda tuşları** | Standart başlatıcı gittikten sonra Giriş, YouTube ve dişli tuşlarının çalışmaya devam etmesini sağlar |

Temalar, anahtarlar ve eklentiler bir arada, ekran görüntüleriyle: [cleanyour.tv/addons](https://cleanyour.tv/addons).

**Daha fazlası:** [docs/launcher.md](docs/launcher.md) başlatıcı ayarları sayfasını satır satır, varsayılan
ana ekranı değiştirmeyi, dilleri, kumanda tuşlarını, Saat ekran koruyucusunu, gezinmeyi ve kendiniz derlemeyi
anlatır.

## TV'nizi geri yükleme

Uyguladığınız listeyi saklayın. Tek satır TV'yi eski hâline getirir:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| Ne | Nasıl |
|---|---|
| **Önce bir kayıt** | `tools/disable.sh` herhangi bir şeyi değiştirmeden önce TV'nin nasıl göründüğünü `~/.cleanyourtv/` içine yazar. |
| **Geri yükleme kaydı yeniden uygular** | Kendiniz devre dışı bıraktığınız bir paket devre dışı kalır ve Ana ekran ona sahip olan bileşene geri döner. |
| **Başlatıcı kurulu kalır** | Kaldırmak istiyorsanız TV'nin Ayarlar → Uygulamalar bölümünden kaldırın. |
| **Bilgisayar yok mu?** | TV'ye erişilemiyorsa kendi Ayarlar → Uygulamalar sayfası da paketleri yeniden etkinleştirebilir. |

**Daha fazlası:** [docs/restore.md](docs/restore.md) kaydın neleri içerdiğini, standart başlatıcı argümanının
ne zaman önemli olduğunu ve kayıt olmadan ne olacağını anlatır.

## Bir bellenim güncellemesinden sonra

Bir güncelleme temizlenmiş sistemin üzerine kurulur: listedeki paketler geri gelir, Ana ekran standart
başlatıcıya geçer ve hiçbir listenin bahsetmediği yeni ön yüklü uygulamalar belirir.
[Hızlı başlangıç](#hızlı-başlangıç) 1. adımdaki gibi ADB'yi yeniden açın, ardından TV'yi uyguladığınız listeyle karşılaştırın; `--fix` eski hâline getirir:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

**Daha fazlası:** [docs/firmware-updates.md](docs/firmware-updates.md) çıkış kodlarını, haftalık bir kontrolü,
`--fix`'in neyi değiştirdiğini, standart başlatıcıyı Ana ekran olarak tutan TV'leri ve güncelleyiciyi devre
dışı bırakan listeleri anlatır.

## Bir şeyler ters giderse

Başımıza gelen her şeyin tam listesi [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) içinde. En
önemli üçü:

- 🍎 **macOS, Yerel Ağ gizliliği altında `adb connect` komutunu engeller** ("No route to host"). `tools/tv.py` bu ikili dosyayı kullanmaz.
- ⚠️ **Standart başlatıcıyı yalnızca yenisi kurulup çalıştıktan sonra devre dışı bırakın**, aksi hâlde Ana ekran bir fabrika test uygulamasına düşer. Kurulum betiği bu sırayı sizin için ayarlar.
- 🔁 **Google TV'de kurulum sihirbazı** (`com.google.android.tungsten.setupwraith`), standart başlatıcı devre dışı bırakıldıktan sonra Ana ekranı ele geçirip sistemi çökme döngüsüne sokabilir. Onu da devre dışı bırakın ve geri yüklemenin onu geri getirmesi için `debloat.txt` dosyanıza ekleyin.
  - Ama önce `dumpsys wifi | grep -o 'cname=[^ ]*'` komutunu kontrol edin: Wi-Fi ağı kurulum sırasında kaydedildiyse setupwraith'e aittir ve onu devre dışı bırakmak ağı siler (önce Ayarlar'dan ağa yeniden katılın; siz bunu yapana kadar `tools/disable.sh` setupwraith'i atlar).
  - Google'ın kendi Chromecast'inde ve Google TV Streamer'da telefondan yayın yapmayı da bozar, bkz. [Sorun giderme](docs/TROUBLESHOOTING.md).
  - Zaten takıldınız mı? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Sık sorulan sorular

<details>
<summary><b>Kod yazmayı bilmiyorum ve dizüstü bilgisayarım yok. Yine de yapabilir miyim?</b></summary>

Evet. [@udhaykumarbala](https://github.com/udhaykumarbala), aynı temizliği Wi-Fi üzerinden yapan, Android için
ücretsiz ve açık kaynaklı bir [telefon uygulaması](https://cleanyour.tv/app) geliştirdi: TV'ye bağlanır, önce bir plan
gösterir, bir geri yükleme noktası kaydeder ve yalnızca uygulamaları devre dışı bırakır; böylece her şey geri alınabilir.
Topluluk tarafından geliştirilmiştir.

</details>

<details>
<summary><b>Bu güvenli mi? TV'yi kullanılamaz hâle getirebilir mi?</b></summary>

Hiçbir şey flaşlanmaz, root'lanmaz veya silinmez; paketler kullanıcı profilinde devre dışı bırakılır. Risk,
TV'nin dayandığı bir şeyi devre dışı bırakmaktır; bu yüzden listeler, hazırda bir geri yükleme betiğiyle küçük
gruplar hâlinde uygulanır. TV'nin kendi Ayarlar → Uygulamalar sayfası, araçlar olmadan da paketleri yeniden
etkinleştirebilir. TV buna yetecek kadar açılmıyorsa, kurtarma menüsünden fabrika ayarlarına sıfırlamak genellikle
geri dönüş yoludur.

Daha fazlası: [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>Bellenim güncellemeleri ne olacak?</b></summary>

Güncelleyici çoğu listede açık bırakılır, bu yüzden bellenim güncellemeleri her zamanki gibi gelir. Bazı listeler
TV'nin kendi güncelleyicisini devre dışı bırakır; bunlarda güncelleme ancak onu yeniden etkinleştirdiğinizde
gelir. Bir güncelleme geldiğinde temizlenmiş sistemin üzerine kurulur, dolayısıyla bir paket geri gelir ve betiği
yeniden çalıştırırsınız: bkz. [Bir bellenim güncellemesinden sonra](#bir-bellenim-güncellemesinden-sonra).

</details>

<details>
<summary><b>Uygulamalar bozulmaz mı?</b></summary>

Yayın uygulamaları, dokunulmadan bırakılan Play Store üzerinden güncellenmeye devam eder.
Başlatıcı yalnızca yüklü olanları listeler ve onları on yıldır değişmeyen intent'lerle başlatır.

</details>

<details>
<summary><b>Uygulamalarımı nasıl yeniden düzenlerim?</b></summary>

**Ayarlar** (sağ üstte) üzerinde OK'u basılı tutun ve **Uygulamaları yeniden sırala**'yı seçin. OK bir uygulamayı
alır, oklar onu taşır, Geri yerine koyar ve tekrar Geri bitirir. `tile_menu` açıkken bir kutucukta OK'u basılı
tutmak, o uygulamayı hemen alan **Taşı** seçeneğini sunar. Daha fazlası: [docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>Bunu başka bir evdeki biri için yapabilir miyim?</b></summary>

Evet, aynı Wi-Fi yerine özel bir [Tailscale](https://tailscale.com) bağlantısı üzerinden: ya onların TV'sinde
Tailscale kurulu olur ve sizinle paylaşılır ya da onların evindeki bir bilgisayar veya Apple TV trafiği TV'ye
yönlendirir. Asla onların modeminde ADB portunu açarak değil. Adım adım anlatımı
[docs/REMOTE.md](docs/REMOTE.md) içinde.

</details>

<details>
<summary><b>ADB erişimi bir güvenlik açığı değil mi?</b></summary>

TV'de bir kez, açıkça "bu bilgisayara izin ver" demektir; bir Android uygulaması geliştirmekle aynıdır. Ağ ADB'si
5555 numaralı portta şifresiz çalışır ve bazı bellenimlerde yerel ağdaki herhangi bir cihaz kabuk erişimi alır; bu
yüzden işiniz bitince kapatın:

1. TV'de: Geliştirici seçenekleri → **USB hata ayıklama**, **Ağ/ADB hata ayıklama** ve
   **Kablosuz hata ayıklama**, TV'de hangileri varsa kapatın ve USB hata ayıklama yetkilerini iptal edin.
2. TV'yi yeniden başlatın, ardından portun kapandığını bilgisayarınızdan doğrulayın:
   `python3 tools/adb-closed.py <tv ip>` portun kapalı olduğunu söylemelidir.

Başlatıcı hata ayıklama olmadan da çalışmaya devam eder. Kablosuz hata ayıklamanın kendi portunu kontrol etmek dahil
daha fazlası: [docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>Uygulamaların içindeki reklamları da engelleyebilir mi?</b></summary>

Kısmen, Android'in Özel DNS özelliğiyle (Android 9 ve sonrası). Onu [AdGuard DNS](https://adguard-dns.io) gibi
reklam engelleyen bir çözümleyiciye yönlendirin; her uygulamanın reklam ve izleyici alan adları sistem genelinde
çözümlenmez hâle gelir ve bu, yeniden başlatmalardan sonra da kalır. Bedeli: o çözümleyici TV'nin yaptığı her DNS
sorgusunu görür ve YouTube reklamlarını kaldırmaz.

İki komut ve nasıl geri dönüleceğiyle birlikte daha fazlası: [docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>TV'nin kendi Ayarlar menüsünde de değiştirmeye değer anahtarlar var mı?</b></summary>

Birkaç tane var ve ADB gerektirmezler: reklam kimliğini, Konum'u, Kullanım ve teşhis'i ve Her zaman kullanılabilir
tarama'yı kapatın; standart başlatıcının kaldığı yerlerde ayrıca Yalnızca uygulamalar modunu açın. Kullanım ve teşhis ayarını
temizlikten önce değiştirin; bazı TV'lerde sayfası temizlikle birlikte gider.

Google TV, Android TV ve Fire OS için yollarla birlikte daha fazlası:
[docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>AirPlay?</b></summary>

Bu depodan değil. Android telefonlar için yerleşik Chromecast kalır. iPhone'lar için
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) çalışır; onun için bir kurulum
betiği [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84) içinde.

</details>

<details>
<summary><b>APK'yı doğrudan indirebilir miyim?</b></summary>

[Elle yapmak](#elle-yapmak) bölümündeki tek satırlık betikle derleyin; bir dakika sürer ve TV'nize ne gittiğini
siz kontrol edersiniz. Bunun için orada adı geçen JDK ve Android SDK gerekir. Önceden derlenmiş ikili dosya yoktur.

</details>

## Hikâye

17 Ekim 2026'da annemle babamın TV'sinin, Claude Code ile Wi-Fi üzerinden temizlenmiş
[önce ve sonra hâlini](https://x.com/gathint/status/2111379569030939102) paylaştım. 8,6 milyon görüntülenmeye
ulaştı, insanlar aynısını kendi TV'lerine yapmaya başladı ve yanıtları buradaki listelere dönüştü: ilk dört günde
200'den fazla katkıda bulunandan 170'ten fazla TV modeli. Depo 19 Ekim'e kadar tv-vibehack adını taşıyordu; eski
bağlantılar çalışır.

İki hafta sonra, 19–25 Ekim haftasında GitHub'ın 1 numaralı trend deposu oldu; 46 binden fazla yıldız, 5,7 bin
fork, 14 günde 1,4 milyon görüntülenme ve 260 bin klon. Listeler artık 398 TV modelini ve 104 markayı kapsıyor.
Basındaki karşılığı: 412 makale (54 ülkeden) ve YouTube ile Bilibili'de 229 video, hepsi [docs/PRESS.md](docs/PRESS.md)
içinde (rakamlar 30 Ekim 2026 itibarıyla).

## Katkıda bulunma

En faydalı katkı, TV'nizin temizlik listesidir; böylece aynı modele sahip bir sonraki kişi onu yeniden keşfetmek
zorunda kalmaz. Biçim ve istediğimiz testler [CONTRIBUTING.md](CONTRIBUTING.md) içinde.
Bir kodlama asistanı aracılığıyla mı katkıda bulunuyorsunuz? [AGENTS.md](AGENTS.md) ona aynı kuralları verir.

| Nasıl yardım edilir | Ne anlama gelir |
|---|---|
| **TV'nizin temizlik listesi** | En faydalısı: aynı modele sahip bir sonraki kişi keşif adımını atlar. |
| **Başlatıcı ve istem** | Başlatıcı iyileştirmeleri ve istemin daha net ifade edilmesi de memnuniyetle karşılanır. |
| **Üzerine bir şey mi yaptınız?** | Bir başlatıcı, bir uyarlama, bir tema: [SHOWCASE.md](SHOWCASE.md) dosyasına ekleyin; orada ana ekranlar, fork'lar, eklentiler, araçlar ve diğer platformlar olarak sınıflandırılmış, TV markasına göre dizinli projeler zaten bulunuyor. |

Çalışmaları bu depoda yer alan kişiler:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Katkıda bulunanlar"></a>

<details>
<summary><strong>Yıldız geçmişi</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Yıldız geçmişi grafiği" width="640"></a>

</details>

## Bize ulaşın

| Konu | Nereye |
|---|---|
| **TV'nizle ilgili sorular** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Basın ve sorular** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Güvenlik sorunları** | [Security sekmesi](https://github.com/Gathin23/cleanyourtv/security) üzerinden gizli olarak bildirin, bkz. [`SECURITY.md`](SECURITY.md) |

## Yasal

- CleanYourTV bağımsız, açık kaynaklı bir projedir. Hiçbir TV üreticisi veya platformuyla bağlantılı değildir,
  onlar tarafından onaylanmaz veya desteklenmez. Marka ve model adları yalnızca bir listenin hangi TV'de test
  edildiğini belirtmek için kullanılır ve sahiplerinin ticari markaları olarak kalır.
- Yalnızca TV'nin sahibine sunduğu ayarlar üzerinden çalışır: Geliştirici seçenekleri, ADB hata ayıklama ve
  Android'in kendi `pm disable-user` komutu; Ayarlar > Uygulamalar > Devre dışı bırak ile aynı anahtar. TV'yi
  root'lamaz, bellenim flaşlamaz, herhangi bir kilidi, DRM'yi veya kopya korumasını aşmaz; depo da hiçbir üretici
  yazılımı, bellenimi veya APK'sı içermez.
- Hiçbir şey kaldırılmaz ve `tools/restore.sh` her şeyi geri koyar. Bunu sahibi olduğunuz bir TV'de, riski size ait
  olmak üzere çalıştırırsınız; bir TV'nin yazılımını değiştirmek garantisini veya destek hakkını etkileyebilir.
- Buradaki her şey, aşağıdaki MIT lisansı kapsamında, hiçbir garanti olmaksızın "olduğu gibi" sunulur.

## Lisans

MIT, bkz. [`LICENSE`](LICENSE). Outfit yazı tipi SIL Open Font License kapsamındadır, bkz.
`launcher/FONT-LICENSE-OFL.txt`.
