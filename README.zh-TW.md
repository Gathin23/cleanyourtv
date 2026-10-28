<p align="center">
  <a href="https://cleanyour.tv"><img src="docs/banner.png" alt="CleanYourTV：讓電視主畫面回歸簡潔" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · 正體中文 · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a>
</p>

<p align="center">
  <a href="https://cleanyour.tv">專案網站</a> ·
  <a href="PROMPT.md">操作提示詞</a> ·
  <a href="examples/README.md">機型清單</a> ·
  <a href="SHOWCASE.md">社群作品</a>
</p>

# CleanYourTV 正體中文使用指南

透過區域網路連接 Android TV 或 Google TV，停用不需要的預載應用程式、廣告和遙測服務，並在相容的裝置上安裝簡潔的原生啟動器。無須 root、刷機或 USB 傳輸線；清理工具只停用應用程式，不解除安裝，並提供還原腳本。

本文介紹通用功能與操作方式。不同機型、地區和韌體的套件及限制可能不同，請先核對自己的裝置。最新功能以[英文 README](README.md)為準，各機型的測試紀錄請見[機型清單](examples/README.md)。

**Samsung、LG、Roku 或其他非 Android 電視？** CleanYourTV 無法在這些電視上執行，但仍可用遙控器逐步關閉追蹤功能：[cleanyour.tv/not-android](https://cleanyour.tv/not-android)。

![原廠 Google TV 主畫面與 CleanYourTV 主畫面對照](docs/stock-vs-new-side.jpg)

**官方連結：**專案網站為 [cleanyour.tv](https://cleanyour.tv)，程式碼、啟動器與工具僅在本儲存庫提供。其他使用相同名稱的網域（例如 cleanyourtv.com）並非本專案經營。主題、功能開關與附加元件可在 [cleanyour.tv/addons](https://cleanyour.tv/addons) 瀏覽。

## 運作原理

Android 電視和 Android 手機使用相同的偵錯橋接工具。在電視的開發人員選項中開啟 USB 偵錯，建立網路 ADB 連線並授權電腦後，即可完成以下步驟：

1. **備份**已安裝套件清單、已停用套件清單、`getprop` 輸出與所有設定，保存到 `backup/`。操作提示詞要求先完成這份備份；`tools/disable.sh` 另存的還原紀錄僅涵蓋套用清單所需的狀態，不能取代完整備份。
2. **依需求停用**廠商遙測、廣告服務、推薦內容和不需要的預載應用程式，使用的指令是 `pm disable-user --user 0`。
3. **安裝簡潔的啟動器**，並將它設為預設主畫面（Home）。

各品牌必須保留的系統元件不同。[操作提示詞](PROMPT.md)會引導程式開發助理先檢查實際裝置，再確認你和家人使用哪些應用程式與功能。

## 快速開始

| 你有的設備 | 操作方式 |
|---|---|
| 電腦與程式開發助理 | 按照下方步驟，將操作提示詞交給助理。 |
| 電腦，但沒有程式開發助理 | 依照[手動操作](#手動操作)自行執行。 |
| 只有 Android 手機 | 可使用 [@udhaykumarbala](https://github.com/udhaykumarbala) 開發的社群版 [Clean Your TV 應用程式](https://cleanyour.tv/app)，透過 Wi-Fi 連線、分析並清理電視。 |

使用助理時，請準備與電視連接同一個 Wi-Fi 網路、可執行終端機指令的電腦。可使用的助理包括 [Claude Code](https://claude.com/product/claude-code)、[Codex](https://github.com/openai/codex)、[Gemini CLI](https://github.com/google-gemini/gemini-cli) 或 [Antigravity CLI](https://antigravity.google/product/antigravity-cli)。

| 步驟 | 操作 |
|---|---|
| 1. 電視端 | 開啟「設定 → 裝置偏好設定 → 關於」，在「版本號碼／Build」上連按七次確定，返回後進入開發人員選項，開啟 USB 偵錯。如果另有網路偵錯或 ADB 偵錯開關，也要開啟。選單名稱會因機型而異。 |
| 2. 電腦端 | 複製儲存庫（`git clone https://github.com/Gathin23/cleanyourtv`），在助理中開啟該資料夾，再貼上 [`PROMPT.md`](PROMPT.md) 的內容並填入電視型號。 |
| 3. 確認需求 | 在電視上接受「允許 USB 偵錯？」授權，告訴助理常用的應用程式、快捷列的大小與排列順序，以及是否保留語音助理、Chromecast 和系統更新。 |
| 4. 檢查結果 | 按提示完成備份、清理和安裝，檢查應用程式啟動、遙控器、輸入來源與還原方式；最後[關閉偵錯並確認連接埠已關閉](#完成後關閉-adb)。 |

先複製儲存庫，助理才能直接使用本專案的 `tools/disable.sh` 和 `launcher/build-and-install.sh`。Home 保護機制與還原紀錄由這些既有工具處理；完整備份仍須依操作提示詞另行保存。

## 主畫面功能

啟動器以原生 Java 編寫，搭配 AndroidX RecyclerView 與 DynamicAnimation，專為只有 1 GB 記憶體的電視設計。它沒有網路權限、背景服務、模糊處理或影片背景。

| 功能 | 說明 |
|---|---|
| 簡潔主畫面 | 深色背景，預設以四個常用應用程式組成圓角快捷列。 |
| 焦點展示 | 以大字顯示目前選取的應用程式名稱，並從應用程式圖像擷取環境光色彩。 |
| 應用程式格線 | 按向下鍵即可瀏覽更多應用程式，可設定每列 4、5、6 或 7 個。 |
| 選取效果 | 玻璃質感的選取框在圖塊之間移動，選取的圖塊會浮起並顯示名稱。 |
| 常用入口 | 輸入來源（HDMI、AV、電視直播）、Wi-Fi／乙太網路與設定；HDMI 名稱沿用電視系統設定。 |
| 時鐘與日期 | 問候語、時間、日期，以及配套的時鐘螢幕保護程式。 |
| 主題 | 透過設定檔調整色彩、桌布、快捷列與欄數，不必重新編譯。 |
| 模組卡片與附加元件 | 其他應用程式可提供主畫面卡片，經你同意後才會顯示。 |

| | |
|---|---|
| ![目前選取的應用程式名稱與環境光](docs/focus-2-up-netflix.png) | ![輸入來源選單：HDMI、AV 與電視直播](docs/inputs-menu.jpg) |
| *焦點展示與環境光* | *輸入來源與連線狀態* |
| ![應用程式格線與玻璃質感選取框](docs/focus-1-fancode.png) | ![時鐘螢幕保護程式](docs/clock-screensaver.png) |
| *應用程式格線* | *緩慢移動的時鐘，避免烙印* |
| ![Aurora 主題](themes/aurora/screenshot.jpg) | ![六欄主題與六個應用程式的快捷列](themes/six-columns/screenshot.jpg) |
| *透過 `tools/theme.sh` 套用 Aurora 主題* | *同一個啟動器，使用不同的排列方式* |

### 在電視上調整設定

預設短按「設定」會開啟電視系統設定，長按確定則會顯示包含啟動器設定、應用程式排序等項目的選單。若在設定檔或主題啟用 `launcher_settings_button`，短按即可直接開啟啟動器設定；電視系統設定仍可從設定頁或應用程式圖塊進入。

| 選項 | 用途 |
|---|---|
| 每列應用程式數量 | 選擇 4、5、6 或 7 個；數量增加時圖塊會縮小。 |
| 快捷列下方列數 | 選擇 0 到 3 列，決定快捷列下方直接顯示多少列圖塊。 |
| 桌布 | 選擇主題中的圖片、不使用桌布或跟隨主題預設；選定單張圖片後會停止輪播。 |
| 動畫與進階效果 | 開啟或關閉動畫；進階效果包含圖塊浮起、圖示縮放與環境光色彩平滑過渡，可能增加記憶體用量。 |
| 跟隨主題 | 重設電視端的選擇，恢復目前主題或內建設定。推送或重設主題也會清除這些選擇。 |
| 選擇預設主畫面 | 開啟 Android 的主畫面選擇器，切換原廠啟動器或 CleanYourTV。 |

主畫面選擇器不會重新啟用已停用的原廠啟動器；若安裝腳本已將它停用，請先依照[還原步驟](#還原原廠主畫面與應用程式)處理。部分電視不提供主畫面選擇器。也可透過 `show_home_settings_shortcut` 顯示獨立入口。

### 排序與應用程式庫

在「設定」按鈕上長按確定，選擇應用程式排序。按確定拿起應用程式，用方向鍵移動，再按確定或返回放下；沒有拿起應用程式時，按返回會離開並儲存排列。頂端的獨立排序按鈕預設隱藏，可透過 `reorder_button` 開啟。

`tile_menu` 預設關閉；啟用後，在圖塊上長按確定可選擇移動、從主畫面移除或檢視應用程式資訊。未啟用時，長按會開啟應用程式資訊。

應用程式庫也預設關閉。啟用 `app_library` 後，入口位於主畫面應用程式的最後一列下方，可瀏覽具備電視或一般 Android 啟動入口的應用程式；一般 Android 應用程式可能仍需要觸控或滑鼠操作。

- 對尚未加入主畫面的應用程式按確定，可加入捷徑。
- 對已在主畫面的應用程式按確定，會開啟應用程式；長按可管理主畫面捷徑或檢視應用程式資訊。
- 從主畫面移除不會解除安裝應用程式。這些選擇在重新開機、應用程式更新及推送或重設主題後仍會保留。
- 另有預設關閉的 `app_library_selection`，啟用後按確定即可切換勾選，決定是否顯示在主畫面。

完整行為請見[應用程式庫說明](docs/app-library.md)。

## 支援哪些裝置

清理清單適用於 **Android TV／Google TV**；目前啟動器需要 **Android 8.0（API 26）或更新版本**。清單中也有低於此版本的裝置，因此有清理清單不代表能安裝啟動器。請先在[機型清單](examples/README.md)核對型號、地區和韌體，閱讀對應資料夾的說明後再操作。

| 平台 | 支援範圍 |
|---|---|
| Android TV、Google TV | 清理與啟動器；仍須符合最低 Android 版本及個別韌體限制。 |
| Fire TV | 僅清理部分；本專案的操作提示詞不適用於 Fire TV，Fire OS 也不允許將 Home 交給第三方啟動器。2015 年 Fire TV Box（Fire OS 5）連停用套件都會遭拒。 |
| 部分中國市場 TCL 機型 | 拒絕安裝第三方啟動器，限制會寫在該機型清單的開頭。不能僅憑品牌判斷相容性。 |
| Samsung Tizen、LG webOS、Roku、Vizio、Hisense VIDAA | 不支援，沒有本工具所需的 ADB。可改看[用遙控器關閉追蹤的指南](https://cleanyour.tv/not-android)。 |

各機型已驗證的清單集中在 [`examples/`](examples/README.md)。其他平台的社群移植可見 [`SHOWCASE.md`](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos)；Samsung 的介面調查見 [Tizen 機型筆記](docs/devices/samsung-tizen.md)。

## 手動操作

以下指令均從儲存庫根目錄執行。`192.168.1.50` 為範例位址，請換成電視的區域網路 IP。執行腳本前，請閱讀檔案開頭的使用方式及對應工具指南。

`tools/tv.py` 預設連接傳統 TCP ADB 的 5555 連接埠。若電視僅提供需要配對的「無線偵錯」，請先依照 [`PROMPT.md`](PROMPT.md) 的連線步驟配對並開啟 5555 連接埠，或使用[已配對的 Google ADB 後端](tools/native-adb.md)進行加密連線。

**本文後續指令範例以傳統 5555 連接埠為準。** 使用加密後端時，請依該指南保留 `TV_ADB_BINARY` 與包含實際連線埠的 `TV_HOST=<tv-ip>:<connection-port>`；後續範例中的 `TV_HOST=192.168.1.50` 與 `-TvHost 192.168.1.50` 都須改用這個完整位址，避免覆寫成不含連接埠的 IP。

### 1. 準備連線工具與備份

連線工具需要 Python 3。macOS／Linux：

```bash
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50

# 讀取型號、Android 版本與韌體
tools/venv/bin/python -I tools/tv.py --probe

# 查看已安裝套件
tools/venv/bin/python -I tools/tv.py 'pm list packages'
```

**第一次變更前，先依照 [`PROMPT.md`](PROMPT.md) 將完整套件清單、已停用清單、`getprop` 與設定備份到 `backup/`。** `tools/disable.sh` 的還原紀錄不包含完整的裝置快照。

### 2. 依需求套用停用清單

**下方小米路徑僅供示範，請換成與自己的電視相符、且已核對過的清單。不要直接套用其他機型的清單。**

```bash
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

清單路徑以執行指令時的目錄為準，也可使用絕對路徑。請保留實際套用的清單及 `~/.cleanyourtv/` 中的還原紀錄。依 [`PROMPT.md`](PROMPT.md) 小批次停用、逐次檢查截圖，先確認家人要保留的串流應用程式、語音助理、Chromecast、Miracast 與韌體更新功能。

不要停用電視與輸入服務、遙控器和藍牙配對、螢幕鍵盤、Play 商店、Play 服務或 Android 核心套件。不要啟動工廠或測試應用程式；完整保護規則以 [`PROMPT.md`](PROMPT.md) 和 [`AGENTS.md`](AGENTS.md) 為準。

### 3. 編譯並安裝啟動器

啟動器的套件名稱是 `com.gathin.tvhome`。編譯需要完整的 JDK 17（僅有 JRE 不足）、Android SDK Platform 34 與 Build Tools 34.0.0。macOS 可使用：

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Linux 安裝腳本會尋找 `~/Android/Sdk` 和 `PATH` 中的 Java。環境準備好後執行：

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

**安裝腳本會編譯及安裝 APK、切換預設 Home，並依偵測結果停用原廠啟動器。** Home 未成功切換時，腳本會嘗試回復。可自動識別的原廠套件為 `com.google.android.tvlauncher`、`com.google.android.apps.tv.launcherx`、`com.google.android.leanbacklauncher` 與 `com.mitv.tvhome`。

若需指定原廠套件，使用 `STOCK_LAUNCHER=<套件名稱>`；若要保留原廠啟動器的啟用狀態，使用：

```bash
TV_HOST=192.168.1.50 STOCK_LAUNCHER=none launcher/build-and-install.sh
```

`none` 代表不停用原廠啟動器，並不代表略過 Home 的設定。`tools/disable.sh` 也會保護 Home：尚無可用的替代啟動器時，會略過清單中目前擁有 Home 的套件。完成新啟動器的切換後，再套用同一份清單即可處理該項目。

**CVTE 主機板須特別留意：**只要有 `com.cvte.*` 套件，除非完全相符的機型範例明確停用原廠啟動器，否則應保留 `com.cvte.tv.launcher.*` 啟用。依 [`PROMPT.md`](PROMPT.md) 設定新 Home 後，先重新開機一次，確認 Home 與 ADB 都恢復，再繼續其他變更；不要在同一輪啟用遙控器按鍵輔助服務。

若電視拒絕所有停用指令、側載驗證失敗，或 Android 版本低於 API 26，請停止並查閱[疑難排解](docs/TROUBLESHOOTING.md)，不要反覆重試或降低 `minSdk`。

### Windows

儲存庫提供對應的 PowerShell 腳本。安裝 Python、Android Studio 與上述 JDK／SDK 元件後：

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
$env:TV_HOST = '192.168.1.50'
tools\venv\Scripts\python.exe -I tools\tv.py --probe
```

同樣先完成備份與清單核對，再套用停用清單、安裝啟動器。先執行停用工具，才能在原廠 Home 被變更前保存還原紀錄；Home 保護機制會略過尚無可用替代啟動器的 Home 項目。以下清單路徑仍須換成自己已核對的機型：

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
```

確認新啟動器已成為 Home 後，若第一次套用清單有略過 Home 項目，可再次執行同一個 `disable.ps1` 指令；最初的還原紀錄會保留。

PowerShell 安裝腳本以 `-StockLauncher <套件名稱>` 指定原廠啟動器，或以 `-StockLauncher none` 保留它的啟用狀態。還原請使用 [`tools/restore.ps1`](tools/restore.ps1)。

下文使用 macOS／Linux 虛擬環境路徑的 Python 指令，在 Windows 將 `tools/venv/bin/python -I tools/tv.py` 換成 `tools\venv\Scripts\python.exe -I tools\tv.py`。

### 工具索引

| 工具 | 用途 |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Python ADB 用戶端：執行指令、批次操作、上傳、下載、重新開機與擷取畫面，也可先模擬遙控器按鍵。 |
| [`tools/b64push.py`](tools/b64push.py) | 分塊上傳 APK 等大型檔案，並驗證檢查碼。 |
| [`tools/relay.py`](tools/relay.py) | 將本機連接埠轉送到電視的 ADB，供無法直接存取區域網路的助理使用；只在本機監聽。見[指南](tools/relay.md)。 |
| [`tools/disable.sh`](tools/disable.sh)／[`restore.sh`](tools/restore.sh) | 套用停用清單，或依變更前的紀錄還原。 |
| [`tools/recovery.py`](tools/recovery.py) | 儲存還原紀錄並產生還原指令。 |
| [`tools/check.py`](tools/check.py) | 比對電視與套用過的清單，檢查韌體更新後的變動；`--fix` 可重新套用。 |
| [`tools/adb-closed.py`](tools/adb-closed.py) | 確認關閉偵錯後，電視的網路 ADB 連接埠已關閉。 |
| [`tools/privacy.py`](tools/privacy.py) | 列出語音、內容辨識（ACR）與遙測候選套件，並呈現判斷依據。 |
| [`tools/theme.sh`](tools/theme.sh)／[`theme_restart.py`](tools/theme_restart.py) | 推送或重設主題，並重新啟動主畫面。 |
| [`tools/manage.py`](tools/manage.py) | 瀏覽附加元件、主題與功能開關，透過各作者的腳本安裝、更新或移除。 |
| [`tools/bench.py`](tools/bench.py) | 測量按鍵延遲、畫面卡頓、啟動、返回 Home 與閒置資源用量。 |
| [`tools/trace.py`](tools/trace.py)／[`trace_report.py`](tools/trace_report.py) | 記錄與分析 Perfetto 效能追蹤。需選用的效能套件，見[指南](tools/trace.md)。 |
| [`tools/flamechart.py`](tools/flamechart.py) | 將效能追蹤繪成前後比較火焰圖。見[指南](tools/flamechart.md)。 |
| [`tools/screendiff.py`](tools/screendiff.py) | 比較前後截圖，可遮罩時鐘等區域，並模糊 Wi-Fi 名稱。見[指南](tools/screendiff.md)。 |
| [`tools/race_calendar.py`](tools/race_calendar.py) | 從 Jolpica API 取得當季 F1 賽程，寫入主題的賽事週末模式。 |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | 編譯、安裝啟動器並切換 Home；切換失敗時嘗試回復。 |

## 麥克風與隱私檢查

僅憑套件名稱或 `RECORD_AUDIO` 權限，不能判定電視是否正在錄音。有些套件共用系統 UID，因此都顯示具有錄音權限；也有自動內容辨識（ACR）服務透過畫面辨識內容，根本不需要麥克風權限。

`tools/privacy.py` 會列出語音、ACR 與遙測候選套件，說明判斷是來自韌體設定的語音服務、已知元件，還是僅有名稱相符；也會顯示錄音權限、共用系統 UID 與 AppOps 回傳的麥克風存取紀錄：

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py
```

`--write privacy.txt` 可產生一般停用清單，再用既有的停用與還原工具處理。僅名稱相符的項目預設不會加入，應先以 `dumpsys package <name>` 確認。掃描結果不能證明錄音意圖或音訊用途；停用語音服務也可能失去遙控器麥克風按鈕或應用程式內的語音搜尋。

## 更多設定

### 版面與主題

內建設定位於 [`config.xml`](launcher/app/src/main/res/values/config.xml)，可調整快捷列順序、隱藏應用程式、電視直播套件、欄數、快捷列下方列數、網路按鈕使用的 VPN 應用程式，以及減少動畫的 `reduced_motion`。

`show_stats` 預設關閉，啟用後會在時鐘下顯示 CPU、可用記憶體與儲存空間，且只在主畫面顯示時更新；電視禁止讀取 CPU 資料時會省略該項。`greeting_name` 可設定問候語中的名字。

主題以 `config.json` 覆寫內建設定，也可提供背景色與桌布：

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
```

恢復內建主題：

```bash
TV_HOST=192.168.1.50 tools/theme.sh --reset
```

主題格式與可用設定鍵見 [`themes/README.md`](themes/README.md)，附加元件見 [`addons/README.md`](addons/README.md)。

### 語言、螢幕保護程式與遙控器

- **語言：**啟動器跟隨電視的系統語言，已包含正體中文（臺灣），沒有內建語言切換開關。Android 13 以上可只設定啟動器的語言：

  ```bash
  tools/venv/bin/python -I tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales zh-TW'
  ```

  查詢目前設定可用 `cmd locale get-app-locales com.gathin.tvhome`；改為英文時將 `zh-TW` 換成 `en`。Android 12 以下請變更電視系統語言。

- **時鐘螢幕保護程式：**在電視的螢幕保護程式設定中選擇「時鐘（Clock）」，或執行：

  ```bash
  tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'
  ```

- **輸入來源鍵：**macOS／Linux 安裝時可使用 `INPUT_KEY_SERVICE=1` 啟用無障礙服務，支援 Google TV 遙控器的輸入來源按鈕；Windows 使用 `-InputKeyService`。
- **Home／設定鍵與應用程式按鍵：**macOS／Linux 安裝時可使用 `HOME_KEY_SERVICE=1`，搭配 `config.xml` 或主題中的 `app_keys` 等設定；Windows 使用 `-HomeKeyService`。`settings_key_panel` 可讓設定鍵開啟快捷設定面板。詳細按鍵設定見[英文啟動器說明](README.md#the-launcher)。

無障礙按鍵服務必須用**實體遙控器**驗證，注入按鍵不算驗證。`am force-stop com.gathin.tvhome` 會關閉這些輔助服務，詳見[疑難排解](docs/TROUBLESHOOTING.md)。

### 模組卡片

其他電視應用程式可宣告自己的卡片名稱、說明與環境光色彩。啟動器預設隱藏卡片，詢問你是否顯示後，會依應用程式簽章與卡片名稱記住選擇。在「設定」按鈕上長按確定，選擇模組卡片選單即可管理。

開發說明見 [`docs/modules.md`](docs/modules.md)，介面規格見 [`docs/specs/card.md`](docs/specs/card.md)。附加元件是獨立應用程式，不會內建到啟動器。

## 還原原廠主畫面與應用程式

`tools/disable.sh` 在停用前記錄清單中各套件的狀態（已啟用、已停用或未安裝）、原本擁有 Home 的元件，以及三項系統動畫縮放設定。紀錄位於電腦的 `~/.cleanyourtv/`，每台電視與每份清單各自儲存。再次套用同一清單不會覆寫最初紀錄；新增項目會在停用前補入紀錄。

將下列路徑換成**實際套用過的清單**：

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

如需指定備用的原廠啟動器套件，可增加第二個參數。Google TV 常用 `com.google.android.apps.tv.launcherx`，仍應以實際裝置為準：

```bash
TV_HOST=192.168.1.50 tools/restore.sh "examples/<your tv>/debloat.txt" com.google.android.apps.tv.launcherx
```

- 有紀錄時，Home 優先還原給當時擁有它的元件；若當時沒有 Home，或由 CleanYourTV 擁有，才使用備用套件。未指定備用套件時，預設嘗試 `com.google.android.tvlauncher`。
- 原本已停用的套件會保持停用。原廠啟動器會重新啟用，但若紀錄顯示它原本已停用，便維持原狀；請先核對備用套件名稱。
- 沒有紀錄時，腳本會提示並改為啟用整份清單中的套件、將動畫縮放設回 `1.0`，並非精確還原變更前的狀態。
- 還原不會解除安裝 CleanYourTV，也不會撤銷清單之外的手動變更。若要移除啟動器，可從電視的「設定 → 應用程式」操作。
- 無法連接 ADB 時，也可嘗試在電視系統設定中重新啟用套件。

## 韌體更新後

韌體更新可能重新啟用已停用的套件、讓原廠啟動器重新取得 Home，或加入新的預載應用程式。請用 `tools/check.py` 比對當初套用的清單。

如果已關閉 ADB，先依[快速開始](#快速開始)重新開啟偵錯並接受授權。僅提供無線偵錯的電視，須依 [`PROMPT.md`](PROMPT.md) 再次配對並開啟 5555 連接埠，或使用[已配對的 ADB 後端](tools/native-adb.md)。使用加密後端時，下例的 `TV_HOST` 須包含目前的連線埠，設定方式見[手動操作](#手動操作)。

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py "examples/<your tv>/debloat.txt"
```

結束碼 `0` 表示符合清單，`1` 表示有變動，`2` 表示無法完成檢查（例如無法連線、授權逾時或清單無效）。`TV_HOST` 未設定會提前以結束碼 `1` 結束，因此執行時務必設定它。

`--fix` 會先處理 Home，再重新停用恢復啟用的項目，並讀回狀態確認；它會使用和停用工具相同的保護檢查。若已有最初的還原紀錄，會先補入新增套件狀態，不會另建一份把清理後狀態當成原始狀態的紀錄。

若採用 `stock_home_redirect` 並刻意保留原廠 Home，請依[英文更新後說明](README.md#after-a-firmware-update)使用對應清單，並把原廠套件作為第二個參數，避免誤判。若清單停用了韌體更新服務，須先重新啟用才能更新。檢查完成後，請再次關閉 ADB。

## 疑難排解

完整說明見 [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)。

- **macOS 顯示 `No route to host`：**檢查「區域網路」隱私權限，可嘗試透過 `tools/venv/bin/python -I tools/tv.py` 使用本專案的 Python ADB 工具。
- **Home 切換異常：**先確認新啟動器已安裝、可以啟動且已成為 Home，再停用原廠啟動器。請使用既有安裝腳本，並遵守 CVTE 機型的額外步驟。
- **Google TV 設定精靈搶占 Home：**`com.google.android.tungsten.setupwraith` 可能在停用原廠啟動器後搶占 Home 並造成重複當機。先檢查 `dumpsys wifi | grep -o 'cname=[^ ]*'`；若儲存的 Wi-Fi 網路屬於它，直接停用會刪除網路。清理工具會略過這種情況，須先在電視設定中忘記該網路並重新加入。在 Google 的 Chromecast 與 Google TV Streamer 上，停用它也會失去手機投放功能。若已發生異常，可嘗試重新啟用 Google TV 原廠啟動器：`tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`。
- **截圖空白：**電視直播、HDMI 與部分串流應用程式可能不允許擷取畫面，不代表電視受損。

## 完成後關閉 ADB

傳統網路 ADB 以明文在 5555 連接埠傳輸；部分韌體甚至不要求授權。清理完成後，請依序操作：

1. 在電視開發人員選項中關閉 **USB 偵錯、網路／ADB 偵錯與無線偵錯**（以機型提供的選項為準），並撤銷 USB 偵錯授權。
2. 無線偵錯使用另一個連接埠，僅關閉 USB 偵錯不一定會關掉它。關閉前先記下 `adb mdns services` 列出的 `_adb-tls-connect._tcp` 連接埠，關閉後、重新開機前立即執行 `python3 tools/adb-closed.py <tv ip> <port>`，確認它已關閉。重新開機後只檢查舊連接埠並不足夠，因為無線偵錯每次啟動都可能換連接埠。
3. 重新啟動電視，再執行 `python3 tools/adb-closed.py <tv ip>`，確認 5555 已關閉。無線偵錯須維持關閉，且 `adb mdns services` 不應再列出該電視的 `_adb-tls-connect._tcp`；若又出現，請關閉並檢查新的連接埠。

請將 `<tv ip>` 與 `<port>` 換成實際值。Windows 將 `python3` 換成 `python`。`adb-closed.py` 的結束碼 `0` 才表示連線遭拒、連接埠已關閉；`1` 表示仍開啟，`2` 表示無法確認。網路錯誤或逾時不能當成關閉的證明。啟動器在關閉偵錯後仍可正常使用。

## 常見問題

**會刷機或刪除系統應用程式嗎？會讓電視無法開機嗎？**

不會刷機、root 或刪除套件，但停用電視依賴的元件仍可能造成故障。請核對機型、小批次處理並保留還原方式。若電視已無法進入設定或連接 ADB，可能需要從復原選單恢復原廠設定；這會清除自己的應用程式與帳號。相關案例見[疑難排解](docs/TROUBLESHOOTING.md#working-on-the-tv)。

**系統更新和應用程式更新會受影響嗎？**

多數清單保留韌體更新服務，若所用清單將它停用，須重新啟用才能接收更新。串流應用程式仍可透過保留的 Play 商店更新。韌體更新後請依[更新後步驟](#韌體更新後)檢查。

**能遠端協助另一個住處的人嗎？**

可透過私有 [Tailscale](https://tailscale.com) 網路連線，詳見[遠端操作指南](docs/REMOTE.md)。不要在路由器上將 ADB 連接埠開放到網際網路。

**也能封鎖應用程式內的廣告嗎？支援 AirPlay 嗎？**

Android 9 以上的私人 DNS 是可選用的系統功能，並非啟動器本身的廣告過濾能力。DNS 解析服務會看見電視的 DNS 查詢，也無法移除和影片來自相同伺服器的 YouTube 廣告。AirPlay 並非內建功能，需要其他應用程式。這些選用方案與還原方式見[英文常見問題](README.md#questions-people-ask)。

**電視設定中還有哪些隱私選項可調整？**

Google TV 可檢查廣告個人化、位置、使用情況與診斷、持續掃描 Wi-Fi 等設定；選單位置依機型而異。部分選項由清單會停用的套件提供，應在清理前設定。原廠啟動器保留時也可考慮「僅限應用程式模式」，但會關閉搜尋與助理。各平台路徑和限制見[英文常見問題](README.md#questions-people-ask)。

**有現成的 APK 可以下載嗎？**

本儲存庫不提供預先編譯的 APK。請使用安裝腳本從原始碼編譯，以便確認安裝到電視上的內容。

## 參與貢獻

歡迎提交經過實機驗證的清理清單、啟動器改進、文件翻譯與主題。提交前請閱讀 [`CONTRIBUTING.md`](CONTRIBUTING.md) 和[注意事項](docs/DOS-AND-DONTS.md)，每個 PR 保持單一主題並說明驗證範圍。使用程式開發助理時，請讓它遵循 [`AGENTS.md`](AGENTS.md)。

主題放在 [`themes/`](themes/README.md)，獨立的模組卡片應用程式放在 [`addons/`](addons/README.md)。不同設計的啟動器、平台移植或大型衍生專案，請在自己的儲存庫開發，再登錄到 [`SHOWCASE.md`](SHOWCASE.md)。

如果英文說明有所變更，也歡迎同步修訂本文。機型與主題清單直接連到對應索引，避免重複維護自動產生的表格與數量。公開截圖前，請模糊或裁掉 Wi-Fi 名稱、人名、城市及帳號資訊；備份、ADB 金鑰與裝置識別資訊不得提交。

## 聯絡與安全通報

電視使用問題請到 [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) 討論。媒體與其他聯絡方式見[英文聯絡資訊](README.md#contact)。安全漏洞請透過儲存庫的 [Security 頁面](https://github.com/Gathin23/cleanyourtv/security)私下回報，詳見 [`SECURITY.md`](SECURITY.md)。

## 法律聲明

- CleanYourTV 是獨立開放原始碼專案，與任何電視廠商或平台沒有隸屬、背書或贊助關係。品牌與型號僅用來標示已測試的裝置，相關商標仍屬各自權利人所有。
- 本專案只使用電視提供給擁有者的設定與 Android 工具，不會 root、刷寫韌體或繞過鎖定、DRM 與複製保護；儲存庫不包含廠商軟體、韌體或 APK。
- 請在自己擁有的電視上操作並自行承擔風險；變更電視軟體可能影響保固或支援。
- 本專案依 MIT 授權條款按現狀提供，不作任何保證。

## 授權條款

專案採用 [MIT 授權條款](LICENSE)。Outfit 字型採用 [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt)。
