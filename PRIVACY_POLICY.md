# Privacy Policy for Norma Launcher

**Last Updated:** October 31, 2026

**Norma Launcher** ("we", "our", or "the application") respects your privacy. This Privacy Policy explains our practices regarding data collection, usage, and disclosure when you use Norma Launcher on your Android TV device.

---

### 1. Open Source Attribution & Project Lineage

**Norma Launcher** is an enhanced, customized fork and continuation built upon the open-source **CleanYourTV** project originally created by **gathin32** ([CleanYourTV on GitHub](https://github.com/gathin32/cleanyourtv)), distributed under the **MIT License**. 

We deeply appreciate and give full attribution to **gathin32** and all contributors of the upstream CleanYourTV project for establishing the lightweight foundation of this launcher.

---

### 2. Information Collection and Use

Norma Launcher is designed with privacy-first principles. We do **NOT** collect, harvest, sell, or transmit any personal identifying information (PII) to remote servers. All launcher features operate strictly on-device.

#### A. Installed Applications
- **Purpose:** To display your installed Android TV apps on the home screen dock and 5×5 app library grid, launch apps, and organize tiles.
- **Data Handling:** Application package names and labels are queried locally via Android's `PackageManager`. No list of your installed apps is ever transmitted off your device.

#### B. Location Information (Weather & Timezone)
- **Purpose:** To automatically show local weather conditions (temperature, weather status) and keep the home clock synchronized to your local timezone.
- **Data Handling:** Location coordinates (coarse/fine) are used exclusively to query publicly accessible meteorological APIs (such as Open-Meteo) directly from your device. We do not store or track your location history.

#### C. Local Storage (Custom Wallpapers)
- **Purpose:** To allow users to select and apply personal wallpaper images stored in the TV's internal storage (`/sdcard/` or USB storage).
- **Data Handling:** File browsing and image loading occur strictly within your local TV file system. No images or storage contents are ever uploaded or shared.

#### D. Device Sounds & UI Preferences
- **Purpose:** Settings like sound effects, column layout, screensaver selection (Countdown Solat or Clocks), and custom wallpapers.
- **Data Handling:** Saved exclusively in private Android `SharedPreferences` on your TV.

---

### 3. Third-Party Services

Norma Launcher does not embed third-party advertising SDKs, tracking frameworks, or commercial analytics. 

Any external network calls made by the launcher are limited to:
- **Public Weather Services** (e.g., Open-Meteo) to fetch temperature and weather symbols for your current area.

---

### 4. Children’s Privacy

Norma Launcher does not knowingly collect or solicit any personal information from children under the age of 13. The application is completely ad-free and safe for all ages.

---

### 5. Security

Because all settings, preferences, and data remain entirely on your local device, your data is protected by the standard Android application sandbox security model.

---

### 6. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be posted on this page with an updated "Last Updated" date.

---

### 7. Contact Us

If you have any questions or feedback regarding this Privacy Policy or Norma Launcher, please reach out via our GitHub repository issues page.
