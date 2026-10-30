/** Turkish → English pairs. Filled in by the screen that owns them. */
export const privacyStrings: Record<string, string> = {
  "İş bitince ADB'yi kapatın": "Turn ADB off when you are done",
  "Port 5555 şifresizdir. Aynı ağdaki herkes, izin verdikten sonra TV'ye komut gönderebilir.":
    "Port 5555 is plaintext. Anyone on the same network can send commands to the TV once they are authorised.",
  "5555 portu, izin penceresi sormadan aynı ağdaki herkese kabuk verir. İş bitince hata ayıklamayı mutlaka kapatın.":
    "Port 5555 hands a shell to anyone on the same network, with no prompt. Always turn debugging off when you are done.",
  "TV'ye tekrar erişmek isterseniz Geliştirici seçeneklerinden hata ayıklamayı yeniden açmanız gerekir.":
    "To reach the TV again you will need to turn debugging back on in Developer options.",

  "Kablosuz hata ayıklamayı kapat": "Turn wireless debugging off",
  "USB hata ayıklamayı kapat": "Turn USB debugging off",
  "Kablosuz hata ayıklama (kapalı bırakılacak)": "Wireless debugging (left off)",
  "USB hata ayıklama (kapalı bırakılacak)": "USB debugging (left off)",
  "Kablosuz hata ayıklama her açılışta yeni bir port açar; kapatınca ADB tümden düşer.":
    "Wireless debugging opens a new port each time it starts; turning it off drops ADB entirely.",
  "Kapatma sonrası kontrol": "Check after turning it off",
  "Kontrol edilecek port": "Port to check",
  "Kapat ve doğrula": "Turn off and verify",
  "Kapatılıyor ve doğrulanıyor…": "Turning off and verifying…",
  "ADB kapatıldı": "ADB turned off",
  "ADB kapandı ve doğrulandı": "ADB is off and verified",
  "ADB kapatılsın mı?": "Turn ADB off?",
  "ADB'yi kapatmak için önce bağlanın.": "Connect first to turn ADB off.",
  "Kapatılamadı": "Could not turn it off",
  "Port hâlâ açık": "Port still open",
  "Yeniden bağlan": "Reconnect",
  "USB hata ayıklama": "USB debugging",
  "ayarları değiştirilecek. Ardından bağlantı düşecek ve port bu bilgisayardan denenip sonuç gösterilecek.":
    "settings will change. The connection then drops, and the port is tried from this computer so you see the result.",
  "Kişi açık kalmasını istemediyse kapatın. Bu komut genelde bağlantıyı anında düşürür.":
    "Turn it off unless the person wants it left on. This command usually drops the connection at once.",
  "Sıra: kapat → portu bu bilgisayardan dene → TV'yi yeniden başlat → portun hâlâ kapalı olduğunu doğrula. Yalnızca aracın “kapalı” demesi kanıttır.":
    "The order: turn it off → try the port from this computer → reboot the TV → confirm the port is still closed. Only the tool saying “closed” counts as proof.",
  "Bu komutları elle de çalıştırabilirsiniz": "You can run these commands by hand too",

  "Neden önemli?": "Why it matters",
  "Kurallar ve geri dönüş": "Rules and undoing it",
  "Reklam ve izleme alan adlarını TV genelinde susturur":
    "Silences ad and tracking domains across the TV",
  "Private DNS bazı uygulamalarda sorun çıkarabilir. Sorun olursa “Kapalı”ya alın. Bu app DNS kurmaz; yalnızca TV'nin kendi ayarını değiştirir.":
    "Private DNS can upset some apps. If it does, set it to “Off”. This app does not run a DNS; it only changes the TV's own setting.",
  "Mod": "Mode",
  "Sağlayıcı": "Provider",
  "Özel sağlayıcı (hostname)": "Custom provider (hostname)",
  "Fırsatçı (sağlayıcı desteklıyorsa)": "Opportunistic (if the provider supports it)",
  "Ör. dns.adguard-dns.com, dns.nextdns.io": "e.g. dns.adguard-dns.com, dns.nextdns.io",
  "sağlayıcı seçilmedi": "no provider selected",
  "şu an: {specifier}": "now: {specifier}",
  "Private DNS ayarlandı": "Private DNS set",
  "DNS ayarlanamadı": "Could not set the DNS",
  "ADB'yi kapat": "Turn ADB off",
  "bu oturum": "this session",
  "Bu TV'de ro.adb.secure=0.": "This TV has ro.adb.secure=0.",
  "Portu dene": "Try the port",
  "{wireless} ve {usb} {what}": "{wireless} and {usb} {what}",
  "**Paketler silinmez.** Yalnızca `pm disable-user` kullanılır; her biri `pm enable` ile geri döner.": "**Packages are never deleted.** Only `pm disable-user` is used, and `pm enable` undoes every one.",
  "**Çekirdek paketler korunur.** Sistem arayüzü, izin denetleyicisi, paket kurucu ve modül katmanları reddedilir.": "**Core packages are protected.** The system UI, the permission controller, the package installer and the module layers are refused.",
  "**Fabrika uygulamaları açılmaz.** Test menüleri bazı kartlarda geri alınamayan bir mod açıyor.": "**Factory apps are never opened.** On some boards a test menu flips a mode the shell cannot undo.",
  "**Home korunur.** Tek Home uygulaması kapatılamaz; yeni başlatıcı doğrulanmadan stok başlatıcıya dokunulmaz.": "**Home is protected.** The single Home app cannot be disabled, and the stock launcher is not touched before the new one is confirmed.",
  "Kablosuz hata ayıklama portu her açılışta değişir; `adb mdns services` ile `_adb-tls-connect._tcp` satırına bakın.": "The wireless debugging port changes on every start; look for the `_adb-tls-connect._tcp` line in `adb mdns services`.",
};
