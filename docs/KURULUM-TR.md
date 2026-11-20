# CleanYourTV hazır APK: ADB'siz kurulum (Türkçe)

Bu sayfa, bu fork'un GitHub Releases sayfasındaki hazır APK'yı bilgisayar ve ADB kullanmadan bir Android TV veya Google TV'ye kurmayı anlatır. Tek APK tüm bu cihazlar için aynıdır.

Uygulama TV'de **CleanYourTV** adıyla görünür. Paket adı `com.gathin.tvhome`. Erişilebilirlik ayarlarındaki yardımcının adı **CleanYourTV ana ekranı**'dır (TV İngilizceyse **CleanYourTV Home**).

## Önce bilmen gerekenler

- APK ana ekranın görünümünü değiştirir: reklam ve öneri satırları gider, uygulamaların sade bir kutu düzeninde durur.
- Gerçek hızlanma ve RAM kazancı **sağlamaz**. Stok ana ekran (Google TV'de launcherx) arka planda çalışmaya devam eder. Örnek ölçüm, Google TV Streamer: launcherx yaklaşık 240 MB, CleanYourTV yaklaşık 167 MB. Gerçek hızlanma için ADB ile gereksiz uygulamaları kapatmak gerekir (bkz. ana README).
- Her şey geri alınabilir. Sistem dosyalarına dokunulmaz, root gerekmez.

## 1. APK'yı indir

Releases sayfasından `cleanyourtv-<sürüm>.apk` dosyasını ve yanındaki `.sha256` dosyasını indir. İstersen bilgisayarda doğrula:

```
shasum -a 256 cleanyourtv-<sürüm>.apk     # macOS
sha256sum cleanyourtv-<sürüm>.apk         # Linux
certutil -hashfile cleanyourtv-<sürüm>.apk SHA256   # Windows
```

Çıkan değer `.sha256` dosyasındakiyle aynı olmalı.

## 2. APK'yı TV'ye aktar

Aşağıdakilerden biri yeterli:

- **USB bellek:** APK'yı belleğe kopyala, TV'ye tak, TV'deki bir dosya yöneticisiyle (ör. File Commander, X-plore) aç.
- **Telefondan "Send files to TV":** Uygulamayı hem telefona hem TV'ye kur (Play Store'da ücretsiz), ikisi aynı Wi-Fi'da olsun, telefondan APK'yı gönder, TV'de açıp kur.
- **Downloader (AFTVnews):** TV'de Downloader'ı Play Store'dan kur, Releases sayfasındaki APK bağlantısını adres çubuğuna yaz, indirince kur de.

## 3. Bilinmeyen kaynaklara izin ver

APK'yı açan uygulamaya (dosya yöneticisi, Send files to TV veya Downloader) kurulum izni vermen gerekir. Kurulum sırasında TV genellikle doğrudan o ekrana götürür. Elle açmak için yol cihaza göre değişir, en yaygınları:

- Ayarlar > Uygulamalar > Güvenlik ve kısıtlamalar > Bilinmeyen kaynaklar > ilgili uygulamayı aç
- Google TV: Ayarlar > Gizlilik (veya Uygulamalar) > Güvenlik ve kısıtlamalar > Bilinmeyen kaynaklar

Kurulum bitince bu izni tekrar kapatabilirsin.

## 4. Ana ekran olarak ayarla

### Klasik Android TV (Sony, Philips, Mi Box, çoğu Android TV)

1. Kumandada Home tuşuna bas.
2. Hangi ana ekranın kullanılacağı sorulursa **CleanYourTV**'yi seç, sonra **Her zaman**'a bas.
3. Soru gelmezse: Ayarlar > Uygulamalar > Varsayılan uygulamalar > Ana ekran uygulaması (menü adı markaya göre değişir) > CleanYourTV.
4. Stok ana ekran yine de geri geliyorsa aşağıdaki Google TV adımındaki erişilebilirlik ayarını da aç.

### Google TV (Google TV Streamer, Chromecast with Google TV, Google TV'li televizyonlar)

Google TV ana ekran seçtirmez. Bunun yerine uygulamanın erişilebilirlik yardımcısı Home tuşunu ve stok ana ekranı CleanYourTV'ye yönlendirir:

1. Ayarlar > Sistem > Erişilebilirlik (bazı cihazlarda doğrudan Ayarlar > Erişilebilirlik).
2. **CleanYourTV ana ekranı**'nı bul ve aç. Android "cihazın tam kontrolü" diye bir uyarı gösterir. Bu, Home tuşunu izleyebilmek için gereken standart erişilebilirlik uyarısıdır. Onayla.
3. Home tuşuna bas: Google TV ana ekranı bir an görünür, sonra CleanYourTV gelir. ADB'siz kurulumda bu kısa geçiş normaldir. Yeniden başlatınca da aynısı olur.

## Bilinen sınırlar

- **Vestel tabanlı TV'ler (tvlauncher):** Denenen bir cihazda varsayılan ana ekran ayarı, tvlauncher ADB ile kapatılana kadar tutmadı. ADB'siz çözüm yukarıdaki erişilebilirlik ayarı (CleanYourTV ana ekranı).
- **Bazı Çin pazarı TCL yazılımları** üçüncü parti ana ekranı hiç kabul etmez.
- **Kumandadaki uygulama tuşları** (YouTube, Netflix vb.) bu APK'da yeniden atanmaz. Bu sürüm, basılı tutulan ses tuşunun tekrar etmesi için tuş filtrelemeyi kapalı tutar (`key_filter=false`). Google TV'de bu tuşları stok ana ekran karşılamaya devam eder.
- Stok ana ekran zaten açıkken Home'a basılırsa (ör. yeniden başlatmadan hemen sonra) birkaç saniye stok ekranda kalabilir. Başka bir uygulama açılıp kapanınca veya tekrar Home'a basınca düzelir.
- **Sesli arama (Google TV):** "YouTube'da ... aç" gibi isteklerin sonuçları Google TV'nin kendi ekranında açılır, CleanYourTV bu ekranı kapatmaz. Oradan bir uygulama açıp Home'a basınca CleanYourTV'ye dönersin. "Müzik aç" gibi istekler uygulamayı (ör. Spotify) doğrudan açar.
- `restore_key_services` açık gelir ama ADB ile izin verilmeden bir işe yaramaz. Zararı da yoktur.

## Geri alma

1. Ayarlar > Erişilebilirlik > **CleanYourTV ana ekranı**'nı kapat (açtıysan).
2. Klasik Android TV'de: Ayarlar > Uygulamalar > Varsayılan uygulamalar > Ana ekran uygulaması > stok ana ekranı seç.
3. Ayarlar > Uygulamalar > **CleanYourTV** > Kaldır.

Home tuşu hemen eski ana ekrana döner. Başka hiçbir şey değişmemiş olur.

## Güncelleme

Yeni sürümü aynı yoldan kurman yeterli, ayarların ve kutu sıralaman korunur. Bu fork'un APK'ları hep aynı anahtarla imzalanır. Not: Upstream'in `build-and-install.sh` ile kurulmuş (debug imzalı) bir CleanYourTV'nin üstüne kurulamaz. Önce onu kaldırman gerekir, kutu sıralaması sıfırlanır.

## Emeği geçenler

CleanYourTV, Gathin ve katkıcıları tarafından geliştirilmiştir ve MIT lisanslıdır (bkz. `LICENSE`). Upstream proje bilerek hazır APK yayınlamaz, cihaza göre ayarlanmış kurulumu ADB ile yapar. Bu fork'taki hazır APK, ADB kullanamayanlar için ortak varsayılanlarla derlenmiş bir paketlemedir. Sorunlar için önce bu fork'a yaz, upstream'e değil.
