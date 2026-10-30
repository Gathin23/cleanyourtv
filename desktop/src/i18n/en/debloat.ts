/** Turkish → English pairs. Filled in by the screen that owns them. */
export const debloatStrings: Record<string, string> = {
  // The backup gate at the top of the screen
  "Yedek hazır": "Backup ready",
  "Önce yedek alın": "Take a backup first",
  "Önce yedek alındı. Bu adım geri alınabilir: paketler silinmez, yalnızca kapatılır.":
    "The backup is taken first. This step can be undone: packages are not deleted, only disabled.",
  "Paket listesi, kapalı paketler, getprop ve ayarlar bilgisayara yazılır. Temizlik bundan önce başlamaz.":
    "The package list, the disabled packages, getprop and the settings are written to the computer. Cleaning does not start before that.",
  "Bu oturumda bir yedek yazıldı{zaten}.": "A backup was written in this session{zaten}.",
  "Diskte henüz yedek yok.": "No backup on disk yet.",
  "Yedek alındı": "Backup taken",
  "Yedek alınamadı": "Could not take the backup",
  "Temizlik, geri dönüşü olan bir yedek olmadan başlamaz.":
    "Cleaning never starts without a backup you can roll back to.",
  "Yedekler ve geri yükleme": "Backups and restoring",

  // Picking a list
  "Hazır listeler": "Ready-made lists",
  "Hazır liste yok": "No ready-made list",
  "Debloat listesi seç (.txt)": "Choose a debloat list (.txt)",
  "Liste içe aktar": "Import a list",
  "Liste okunamadı": "Could not read the list",
  "Repo klasörü bulunamadı; kendi listenizi içe aktarabilirsiniz":
    "Repository folder not found; you can import your own list",
  "Ayarlar'dan CleanYourTV klasörünü gösterin ya da .txt bir liste içe aktarın.":
    "Point Settings at the CleanYourTV folder, or import a .txt list.",
  "{n} test edilmiş TV listesi · {path}": "{n} tested TV lists · {path}",
  "{total} paket, {disabled} kapalı paket · {path}": "{total} packages, {disabled} disabled · {path}",

  // The package table
  "paket adı veya açıklama ara": "search a package name or description",
  "Eşleşen paket yok": "No matching package",
  "Filtreyi ya da aramayı değiştirin.": "Change the filter or the search.",
  "Paket listesi okunamadı": "Could not read the package list",
  "Paket okunuyor…": "Reading the package…",
  "Zaten kapalı": "Already disabled",
  "hâlâ açık": "still on",
  "güvenli paket": "safe package",
  "kapalı paket": "disabled package",
  "kullanıcı": "user",
  "kapandı": "disabled",
  "kapatıldı": "disabled",
  "atlandı": "skipped",
  "olmadı": "failed",
  "{disabled} paket şu an kapalı · {selected} seçili":
    "{disabled} packages disabled right now · {selected} selected",
  "{done} kapatıldı · {skipped} atlandı": "{done} disabled · {skipped} skipped",
  "{name} seç": "select {name}",
  "{n} seçili": "{n} selected",

  // Selecting and running a batch
  "Önerilenleri seç": "Select the suggestions",
  "Seçimi temizle": "Clear the selection",
  "Telemetri ve reklam paketleri seçildi": "Telemetry and ad packages selected",
  "Kurallara göre güvenli olanlar": "The ones the rules call safe",
  "{n} önerilen paket seçildi": "{n} suggested packages selected",
  "{n} paket seçildi{missing}{blocked}.": "{n} packages selected{missing}{blocked}.",
  "{n} bu TV'de yok": "{n} not on this TV",
  "{n} korunuyor": "{n} protected",
  "{n} paket kapatılacak": "{n} packages will be disabled",
  "{n} paket kapatıldı{daha}.": "{n} packages disabled{daha}.",
  "{n} atlandı": "{n} skipped",
  "Uygulanıyor": "Applying",
  "İlk paket gönderiliyor…": "Sending the first package…",
  "Her paket tek tek gönderiliyor, sonra doğrulanıyor.":
    "Each package is sent on its own, then checked.",
  "Temizlik yapılamadı": "Could not clean",
  "Değiştirilemedi": "Could not change",
  "Başlatılamadı": "Could not start",

  // The rules
  "Çekirdek paketler, kurulum sihirbazı ve tek Home uygulaması asla kapatılmaz.":
    "Core packages, the setup wizard and the single Home app are never disabled.",
  "Bu paket korunuyor, kapatılamaz": "This package is protected and cannot be disabled",
  "Kapatma işlemi güvenlik kurallarından geçer; çekirdek paketler reddedilir.":
    "Every disable goes through the safety rules; core packages are refused.",
  "Kurulum sihirbazını yine de kapat": "Disable the setup wizard anyway",
  "Aşağıdaki paketler aile tarafından kullanılabilir. Yalnızca bu TV'de kullanmadığınızı bildiklerinizi işaretleyin.":
    "The family may use the packages below. Tick only the ones you know are unused on this TV.",
  "Wi-Fi ağının sahibi bu paketse kapatmak TV'yi ağdan ve ADB'den düşürür. Uygulama bunu önce kontrol eder ve sahibiyse yine de reddeder.":
    "If this package owns the Wi-Fi network, disabling it drops the TV off the network and off ADB. The app checks first, and still refuses if it owns the network.",

  // Launcher state shown to the person
  "Bu TV'ye uygun öneriler": "Suggestions for this TV",
  "Bu TV launcher için çok eski": "This TV is too old for the launcher",
  "Temiz başlatıcı en az Android 8.0 (SDK 26) ister. Debloat tarafı çalışır, başlatıcı kurulamaz.":
    "The clean launcher needs Android 8.0 (SDK 26) at least. Debloating works; the launcher cannot be installed.",
  "Temiz başlatıcı kurulu değil; stok başlatıcı silinmez, yalnızca Home devredilir.":
    "The clean launcher is not installed; the stock launcher is not deleted, only Home is handed over.",
  "Temiz başlatıcı kurulu. Home devri için Başlatıcı sayfasını kullanın.":
    "The clean launcher is installed. Use the Launcher page to hand Home over.",

  // The restore script
  "Geri yükleme betiği": "Restore script",
  "Geri yükleme betiğini kaydet": "Save the restore script",
  "Bu oturumda kapatılan paketleri geri açar; hiçbir şey silinmez.":
    "Re-enables the packages disabled in this session; nothing is deleted.",
  "Betik için paket yok": "No packages for the script",
  "Betik yazılamadı": "Could not write the script",
  "Çalıştırma:": "Run:",

  // Usage column and filters
  "paket seçildi": "packages selected",
  "Seç": "Select",
  "Kapat": "Close",
  "Kapat / aç": "Disable / enable",
  "Önizleme": "Preview",
  "Sonuç": "Result",
  "Kural": "Rule",
  "Durum": "State",
  "Telemetri + reklam": "Telemetry + ads",
  "yedek yok": "no backup",
  "Temizle": "Clear",
  "Uygula": "Apply",
  "Parti durduruldu": "Batch stopped",
  "Atlananlar ve nedeni": "Skipped, and why",
  "onay gerekiyor": "needs a decision",
  "atlanacak": "to be skipped",
  "paket": "packages",
  "dosya ·": "files ·",
  "Paketleri yenile": "Reload the packages",
  "Her paket tek tek gönderilir, sonra `sync` çalışır ve liste doğrulanır.": "Each package goes one at a time, then `sync` runs and the list is checked.",
  "TV'nin bir sonraki açılışta yeni durumu okuması için yeniden başlatın; `sync` zaten çalıştı.": "Reboot so the TV reads the new state on its next start; `sync` has already run.",
  "Paketler ({count})": "Packages ({count})",
};
