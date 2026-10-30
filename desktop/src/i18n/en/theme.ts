/** Turkish → English pairs. Filled in by the screen that owns them. */
export const themeStrings: Record<string, string> = {
  // The two paths
  "İki ayrı yol var": "There are two separate paths",
  "Anında tema (config.json)": "Instant theme (config.json)",
  "config.json — derleme gerektirmez, anında etki eder":
    "config.json — no build, takes effect at once",
  "Başlatıcı bu katmanı çalışma zamanında okur; APK derlemek gerekmez. Beğenmezseniz tek tuşla kaldırılır.":
    "The launcher reads this layer at runtime, so there is no APK to build. If you do not like it, one button removes it.",
  "config.xml — APK'nın içine derlenir": "config.xml — compiled into the APK",
  "Bu anahtarlar tema katmanıyla gönderilemez: APK'nın içine derlenirler. Değerleri":
    "These keys cannot travel in the theme layer: they are compiled into the APK. Change the values",
  "config.xml sekmesinde düzenle": "in the config.xml tab",
  "değiştirip Derle & kur adımını çalıştırın.": "and then run the Build & install step.",

  // The form
  "Tema ayarları": "Theme settings",
  "Tema ayarları okunuyor…": "Reading the theme settings…",
  "Tema şeması okunamadı": "Could not read the theme schema",
  "anahtar, ad veya açıklama ara": "search a key, a name or a description",
  "Aradığınızı yazın ya da soldan bir grup seçin.":
    "Type what you are looking for, or pick a group on the left.",
  "Eşleşen ayar yok": "No matching setting",
  "Tümü": "All",
  "Tümü ·": "All ·",
  "varsayılana dön": "back to the default",
  "Grubu sıfırla": "Reset the group",
  "JSON önizle": "Preview the JSON",
  "Gönderilecek config.json": "config.json to send",
  "{n} anahtar. Varsayılandan farklı olanlar gönderilir.":
    "{n} keys. Only the ones that differ from the default are sent.",
  "anahtar değişti": "keys changed",

  // Wallpaper
  "Duvar kağıdı": "Wallpaper",
  "Duvar kağıdı seç": "Choose a wallpaper",
  "Duvar kağıtları seç": "Choose wallpapers",
  "Ana duvar kağıdı": "Main wallpaper",
  "Görsel": "Image",
  "Koleksiyon": "Collection",
  "İsteğe bağlı; tema ile birlikte gönderilir": "Optional; sent along with the theme",
  "JPEG veya PNG. TV tarafında wallpaper.jpg olarak yazılır.":
    "JPEG or PNG. Written as wallpaper.jpg on the TV.",
  "wallpaper_rotation_minutes ile birlikte kullanın; dosyalar wallpapers/ altına gider.":
    "use it together with wallpaper_rotation_minutes; the files go under wallpapers/.",
  "Uygulama seç": "Choose an app",
  "seçilmedi": "not selected",

  // Applying
  "Temayı TV'ye uygula": "Apply the theme to the TV",
  "Tema katmanı TV'ye yazılır": "The theme layer is written to the TV",
  "Tema katmanı com.gathin.tvhome paketine yazılır; paket yoksa gönderilemez.":
    "The theme layer is written into the com.gathin.tvhome package; without that package it cannot be sent.",
  "Tema uygulandı": "Theme applied",
  "Tema uygulanamadı": "Could not apply the theme",
  "Tema gönderildi, Home doğrulanamadı": "Theme sent, but Home could not be checked",
  "Katmanı kaldır": "Remove the layer",
  "Katman kaldırıldı": "Layer removed",
  "Katman kaldırılamadı": "Could not remove the layer",
  "Şu anki tema katmanı": "Current theme layer",
  "Temiz başlatıcı kurulu değil": "The clean launcher is not installed",
  "Önce başlatıcıyı kurun": "Install the launcher first",
  "Anahtarları görmek ve göndermek için önce TV'ye bağlanın. Selamlama metinleri ve config.xml bu bağlantıyı gerektirmez.":
    "Connect to the TV first to see and send the keys. Greeting texts and config.xml do not need it.",
  "Nasıl çalışır?": "How does it work?",

  // Presets
  "Hazır başlangıçlar": "Ready-made starting points",
  "Bir tanesine basın, ayarlar forma dolar; uygulamadan önce istediğinizi değiştirebilirsiniz.":
    "Press one and the form fills in; change whatever you like before applying.",
  "config.json anahtarı": "config.json key",
  "ör. Kralım": "e.g. my friend",
  "config.xml sekmesinden": "from the config.xml tab",
  "JSON indir": "Download the JSON",
  "**Selamlama metinleri burada değişmez.** “Günaydın” gibi metinler APK içindeki `strings.xml` dosyasında; onlar için **Selamlama** sekmesini kullanın. Buradaki `greeting` yalnızca festival sırasında geçerlidir.": "**Greeting texts do not change here.** Texts like “Good morning” live in `strings.xml` inside the APK; use the **Greetings** tab for those. The `greeting` here only applies during a festival.",
  "**config.json (bu sekme).** Launcher'ın ThemeProvider'ına yazılır, `theme_revision` yenilenir ve Home yeniden başlar. Anında görürsünüz.": "**config.json (this tab).** Written into the launcher's ThemeProvider, `theme_revision` is bumped and Home restarts. You see it at once.",
  "**config.xml + strings.xml.** APK'nın içine derlenir. Değişiklik için **Derle** adımı gerekir.": "**config.xml + strings.xml.** Compiled into the APK. A change needs the **Build** step.",
  "**Katmanı kaldır.** Televizyonu fabrika ayarlarındaki görünüme döndürür; APK'ya dokunmaz.": "**Remove the layer.** Puts the TV back to its factory look; the APK is not touched.",
  "APK'ya derlenir": "compiled into the APK",
  "uygulama": "apps",
  "dosya": "files",
};
