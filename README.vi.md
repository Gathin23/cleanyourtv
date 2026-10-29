<p align="center">
  <a href="https://cleanyour.tv"><img src="docs/banner.png" alt="CleanYourTV: trả lại cho TV một màn hình chính gọn gàng" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · Tiếng Việt · <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <a href="https://cleanyour.tv">Trang dự án</a> ·
  <a href="PROMPT.md">Câu lệnh cho trợ lý</a> ·
  <a href="examples/README.md">Danh sách TV</a> ·
  <a href="SHOWCASE.md">Dự án cộng đồng</a>
</p>

# CleanYourTV: hướng dẫn tiếng Việt

Kết nối với Android TV hoặc Google TV qua mạng Wi-Fi trong nhà, tắt các ứng dụng cài sẵn, quảng cáo và dịch vụ
thu thập dữ liệu mà bạn không cần, rồi cài một màn hình chính (launcher) gọn gàng trên những TV hỗ trợ. Không cần
root, không cần cài lại firmware, không cần cáp USB. Các ứng dụng hệ thống chỉ bị **tắt**, không bị gỡ, và luôn có
script để khôi phục.

Tài liệu này giới thiệu các tính năng chung và cách dùng. Mỗi dòng TV, mỗi khu vực và mỗi bản firmware có danh sách
ứng dụng và giới hạn riêng, nên hãy đối chiếu với TV của bạn trước. Khi có thay đổi, [README tiếng Anh](README.md)
là bản chuẩn; ghi chú cho từng dòng TV nằm trong [danh sách TV](examples/README.md).

![Màn hình Google TV gốc so với launcher CleanYourTV](docs/stock-vs-new-side.jpg)

## Cách hoạt động

Bật USB debugging (gỡ lỗi USB) trong tuỳ chọn nhà phát triển của TV, cho phép máy tính kết nối ADB qua mạng một lần,
rồi làm ba bước:

1. **Sao lưu** danh sách ứng dụng đã cài và các cài đặt liên quan. Nếu dùng [câu lệnh cho trợ lý](PROMPT.md), trợ lý
   sẽ lưu một bản sao lưu đầy đủ trước; nếu tự chạy `tools/disable.sh`, công cụ chỉ ghi lại những gì cần để khôi phục
   đúng danh sách đó.
2. **Tắt** các dịch vụ thu thập dữ liệu, quảng cáo, hàng gợi ý nội dung và ứng dụng cài sẵn bạn không dùng, bằng
   `pm disable-user`.
3. **Cài launcher gọn gàng** và đặt nó làm màn hình chính (HOME) mặc định.

Mỗi hãng cần giữ lại những thành phần hệ thống khác nhau. [Câu lệnh cho trợ lý](PROMPT.md) sẽ hướng dẫn trợ lý lập
trình kiểm tra TV thật trước, rồi hỏi bạn đang dùng những ứng dụng và tính năng nào.

## Bắt đầu nhanh

Bạn cần một máy tính nối cùng mạng với TV và một trợ lý lập trình chạy được lệnh terminal, ví dụ
[Claude Code](https://claude.com/product/claude-code), [Codex](https://github.com/openai/codex) hoặc
[Gemini CLI](https://github.com/google-gemini/gemini-cli).

| Bước | Làm gì |
|---|---|
| 1. Trên TV | Vào **Cài đặt → Hệ thống (hoặc Tuỳ chọn thiết bị) → Giới thiệu**, bấm OK bảy lần vào mục **Bản dựng / Build**, quay lại mở **Tuỳ chọn nhà phát triển** và bật **USB debugging**. Nếu có thêm công tắc gỡ lỗi mạng / ADB thì bật luôn. Tên menu khác nhau tuỳ máy. |
| 2. Trên máy tính | Dán nội dung [`PROMPT.md`](PROMPT.md) cho trợ lý và điền model TV của bạn. |
| 3. Trả lời trợ lý | Bấm **Allow** khi TV hỏi cho phép gỡ lỗi, rồi cho trợ lý biết bạn hay dùng ứng dụng nào, muốn để app nào trên thanh dock, có giữ trợ lý giọng nói, Chromecast và cập nhật hệ thống hay không. |
| 4. Kiểm tra | Làm theo hướng dẫn để sao lưu, dọn dẹp và cài đặt, rồi thử mở ứng dụng, nút remote, chuyển nguồn HDMI và cách khôi phục. |

## Launcher có gì

- **Màn hình chính tối giản**: nền tối, mặc định bốn ứng dụng hay dùng trên thanh dock phía dưới.
- **Tên ứng dụng đang chọn**: hiện tên ứng dụng chữ lớn, kèm ánh màu lấy từ banner của ứng dụng (không có banner thì
  lấy từ icon).
- **Lưới ứng dụng**: bấm xuống để xem thêm ứng dụng, mỗi hàng 4, 5, 6 hoặc 7 ô.
- **Sắp xếp bằng remote**: giữ nút Cài đặt để mở menu và vào chế độ sắp xếp.
- **Thư viện ứng dụng (tuỳ chọn)**: xem các ứng dụng đã cài, thêm hoặc bớt ứng dụng trên màn hình chính bằng remote.
- **Hiệu ứng chuyển động**: bật hoặc tắt hiệu ứng, có thêm mức hiệu ứng nâng cao riêng.
- **Lối tắt thường dùng**: Wi-Fi / Ethernet và Cài đặt; nếu TV có HDMI, AV, truyền hình trực tiếp hoặc bảng nguồn
  nhận dạng được thì có thêm nút Nguồn vào. Tên cổng HDMI giữ đúng tên bạn đặt trong cài đặt TV.
- **Đồng hồ và trình bảo vệ màn hình**: lời chào, giờ, ngày, và trình bảo vệ màn hình đồng hồ đi kèm.
- **Theme**: đổi màu, hình nền, thanh dock và số cột bằng file cấu hình, không cần build lại.
- **Thẻ module**: ứng dụng khác có thể thêm thẻ lên màn hình chính, chỉ hiện khi bạn đồng ý.

![Ứng dụng đang chọn và ánh màu nền](docs/focus-2-up-netflix.png)

### Chỉnh cài đặt ngay trên TV

Bấm nút **Cài đặt** trên màn hình chính để mở cài đặt của TV. **Giữ nút OK trên remote khi đang chọn nút đó** rồi chọn
mục cài đặt launcher để chỉnh:

| Mục | Tác dụng |
|---|---|
| Số ứng dụng mỗi hàng | Chọn 4, 5, 6 hoặc 7; càng nhiều cột thì ô càng nhỏ. |
| Hiệu ứng chuyển động | Bật hoặc tắt hiệu ứng của launcher. |
| Hiệu ứng nâng cao | Ô nhấc lên, icon phóng to, màu nền chuyển mượt hơn. Có thể tốn thêm bộ nhớ, tuỳ TV. |
| Theo theme (xoá cài đặt trên TV) | Xoá các lựa chọn bố cục và hiệu ứng đã chỉnh trên TV, quay về theme hiện tại hoặc cấu hình gốc. Đẩy hoặc reset theme cũng xoá các lựa chọn này. |
| Lối vào màn hình chính mặc định | Mở trang chọn màn hình chính của Android để chuyển giữa launcher gốc và CleanYourTV. |

Lối vào màn hình chính mặc định không bật lại launcher gốc nếu nó đã bị script cài đặt tắt; khi đó hãy khôi phục theo
hướng dẫn bên dưới. Một số TV không có trang chọn màn hình chính. Bạn cũng có thể hiện lối vào này thành một ô riêng
bằng `show_home_settings_shortcut`.

### Sắp xếp và thư viện ứng dụng

Giữ OK trên nút Cài đặt, chọn chế độ sắp xếp trong menu. Bấm OK để nhấc ứng dụng muốn di chuyển, dùng phím mũi tên để
đổi chỗ, rồi bấm OK hoặc Back để đặt xuống; khi không cầm ứng dụng nào, bấm Back để thoát và lưu. Nút sắp xếp riêng
trên thanh trên cùng mặc định bị ẩn, bật bằng `reorder_button` trong cấu hình hoặc theme.

Thư viện ứng dụng mặc định tắt. Bật `app_library` trong cấu hình hoặc theme thì lối vào hiện sau hàng ứng dụng cuối
cùng trên màn hình chính. Thư viện liệt kê cả ứng dụng cho TV lẫn ứng dụng Android thường; ứng dụng Android thường có
thể vẫn cần chuột hoặc màn hình cảm ứng.

- Bấm OK vào ứng dụng chưa có trên màn hình chính để thêm lối tắt.
- Bấm OK vào ứng dụng đã có trên màn hình chính để mở; giữ OK để mở menu thêm hoặc bớt lối tắt, hoặc xem thông tin
  ứng dụng.
- Bớt khỏi màn hình chính không gỡ ứng dụng. Lựa chọn được giữ qua khởi động lại, cập nhật ứng dụng, và khi đẩy hoặc
  reset theme.

Xem thêm [hướng dẫn thư viện ứng dụng](docs/app-library.md).

## TV nào dùng được

Danh sách dọn dẹp dành cho **Android TV / Google TV**; launcher hiện cần **Android 8.0 (API 26) trở lên**. Cộng đồng đã
đóng góp cấu hình cho nhiều dòng TV và TV box; một số máy chạy Android thấp hơn mức tối thiểu của launcher, nên có danh
sách dọn dẹp chưa chắc đã cài được launcher. Hãy đối chiếu model, khu vực và firmware trong
[danh sách TV](examples/README.md), đọc ghi chú trong thư mục tương ứng rồi mới dùng danh sách tắt ứng dụng.

- Samsung Tizen và LG webOS không chạy được launcher Android của dự án;
  [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) ghi lại những gì một TV Tizen đời 2021 mở ra trong
  mạng nội bộ (không có ADB, nên không tắt được ứng dụng). Các bản chuyển sang nền tảng khác của cộng đồng nằm trong
  [Dự án cộng đồng](SHOWCASE.md).
- Fire TV có các ghi chú dọn dẹp để tự làm theo; [câu lệnh cho trợ lý](PROMPT.md) không dùng cho Fire TV, và Fire OS
  không cho thay màn hình chính mặc định.
- Một số TV bán tại Trung Quốc đại lục từ chối cài APK ngoài hoặc launcher bên thứ ba. Đừng đoán khả năng tương thích
  chỉ dựa vào hãng.
- Chưa có danh sách cho TV của bạn thì để trợ lý kiểm tra TV thật, rồi gửi lại cấu hình đã kiểm chứng theo
  [hướng dẫn đóng góp](CONTRIBUTING.md).

### Lưu ý cho TV bán tại Việt Nam

- [TCL 55P8LS](examples/tcl-55p8ls/debloat.txt) đã có danh sách đầy đủ, cùng nền tảng G10 với
  [`tcl-g10-google-tv`](examples/tcl-g10-google-tv/debloat.txt).
- Trên nhiều máy Google TV, mạng Wi-Fi lưu lúc cài đặt ban đầu thuộc về trình cài đặt `setupwraith`. Hãy **quên mạng
  Wi-Fi rồi kết nối lại** trong Cài đặt trước khi tắt nó, nếu không TV sẽ mất mạng (xem
  [Xử lý sự cố](#xử-lý-sự-cố)).
- Đăng nhập tài khoản Google sau khi dọn dẹp, Play Store có thể tự cài các ứng dụng gợi ý theo khu vực (VTV Go, HBO
  Max, iQIYI…). Đó là ứng dụng thường, gỡ được; tắt **tự động cập nhật ứng dụng** trong cài đặt Play Store để hạn chế.

## Tự làm bằng tay

Các lệnh dưới đây chạy từ thư mục gốc của repo. `192.168.1.50` là địa chỉ ví dụ, hãy thay bằng IP của TV (xem trong
**Cài đặt → Mạng và Internet**, bấm vào mạng đang kết nối). `tools/tv.py` mặc định kết nối ADB qua TCP cổng 5555; nếu
TV chỉ có **Gỡ lỗi không dây** cần ghép đôi bằng mã, hãy ghép đôi và mở cổng 5555 theo
[các bước kết nối trong câu lệnh cho trợ lý](PROMPT.md) trước.

### 1. Chuẩn bị công cụ kết nối

Công cụ kết nối cần Python 3:

```bash
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50

# Xem model, phiên bản Android và firmware
tools/venv/bin/python -I tools/tv.py --probe

# Xem các gói ứng dụng đã cài
tools/venv/bin/python -I tools/tv.py 'pm list packages'
```

### 2. Áp dụng danh sách tắt ứng dụng

**Đường dẫn Xiaomi bên dưới chỉ là ví dụ, hãy thay bằng danh sách đúng với TV của bạn và đã đối chiếu kỹ.** Đừng dùng
danh sách của model khác cho TV mình.

```bash
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

Đường dẫn danh sách tính từ thư mục bạn đang đứng khi chạy lệnh, hoặc dùng đường dẫn tuyệt đối. Giữ lại danh sách và
bản ghi khôi phục mà công cụ tạo ra để có thể hoàn tác. Nếu muốn có bản sao lưu đầy đủ ứng dụng và cài đặt, hãy tự lưu
trước khi tắt; `tools/disable.sh` không tạo bản sao lưu đó.

### 3. Build và cài launcher

Launcher viết bằng Java và AndroidX RecyclerView, tên gói `com.gathin.tvhome`. Cần JDK 17, Android SDK Platform 34 và
Build Tools 34.0.0. Trên macOS:

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Trên Linux, script cài đặt tìm SDK ở `~/Android/Sdk` và Java trong `PATH`. Chuẩn bị xong thì chạy:

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

**Script này không chỉ cài APK mà còn đổi màn hình chính mặc định và tắt launcher gốc nếu nhận ra nó.** Script mở thử
launcher mới trước, chạy được mới chuyển; chuyển không thành công thì cố quay lại như cũ. Script chỉ tự nhận ra
`com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`, `com.google.android.leanbacklauncher` và
`com.mitv.tvhome`; không nhận ra thì kiểm tra TV rồi tự chỉ định `STOCK_LAUNCHER=<tên gói>`, hoặc dùng `none`.

Muốn giữ launcher gốc vẫn bật:

```bash
TV_HOST=192.168.1.50 STOCK_LAUNCHER=none launcher/build-and-install.sh
```

`none` chỉ có nghĩa là không tắt launcher gốc, **CleanYourTV vẫn được đặt làm màn hình chính mặc định**.

### Windows

Repo có script PowerShell tương ứng. Cài Python và Android Studio, kiểm tra đã có JDK và các thành phần SDK ở trên, rồi:

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
```

Script PowerShell dùng `-StockLauncher <tên gói>` để chỉ định launcher gốc, hoặc `-StockLauncher none` để giữ nó bật.
Script PowerShell hiện đặt màn hình chính mới trước rồi mới kiểm tra launcher mới có chạy không, ngược thứ tự với script
Shell ở trên; hãy chắc chắn vẫn kết nối ADB được để khôi phục trước khi chạy. Dọn dẹp và khôi phục dùng
[`tools/disable.ps1`](tools/disable.ps1) và [`tools/restore.ps1`](tools/restore.ps1).

### Danh sách công cụ

| Công cụ | Dùng để |
|---|---|
| [`tools/tv.py`](tools/tv.py) | ADB client viết bằng Python: chạy lệnh, chạy hàng loạt, đẩy và kéo file. |
| [`tools/b64push.py`](tools/b64push.py) | Đẩy file lớn như APK theo từng khúc và kiểm tra lại. |
| [`tools/disable.sh`](tools/disable.sh) / [`tools/restore.sh`](tools/restore.sh) | Áp dụng danh sách tắt ứng dụng, hoặc khôi phục theo bản ghi trước khi làm. |
| [`tools/recovery.py`](tools/recovery.py) | Lưu bản ghi khôi phục và tạo lệnh khôi phục. |
| [`tools/theme.sh`](tools/theme.sh) | Đẩy hoặc reset theme rồi khởi động lại màn hình chính. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Build, cài launcher và đổi màn hình chính. |

## Cấu hình thêm

### Bố cục và theme

Cấu hình gốc nằm trong [`config.xml`](launcher/app/src/main/res/values/config.xml): thứ tự thanh dock, ứng dụng ẩn,
tên gói truyền hình trực tiếp, số cột, số hàng hiện dưới dock, ứng dụng VPN gắn với nút mạng, `reduced_motion`, v.v.

`show_stats` hiện CPU, bộ nhớ trống và dung lượng trống dưới đồng hồ, mặc định tắt và chỉ cập nhật khi đang ở màn hình
chính; TV nào chặn đọc CPU thì bỏ mục CPU. `greeting_name` thêm tên vào lời chào, ví dụ "XIN CHÀO, ANH".

Theme dùng file `config.json` để ghi đè cấu hình gốc, có thể kèm màu nền và hình nền. Ví dụ:

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
```

Quay về cấu hình gốc:

```bash
TV_HOST=192.168.1.50 tools/theme.sh --reset
```

Định dạng theme và các khoá cấu hình xem trong [`themes/README.md`](themes/README.md).

### Ngôn ngữ, trình bảo vệ màn hình và remote

Các lệnh ADB dưới đây dùng đường dẫn venv của macOS/Linux; trên Windows đổi tiền tố
`tools/venv/bin/python -I tools/tv.py` thành `tools\venv\Scripts\python.exe -I tools\tv.py`.

- **Ngôn ngữ** đổi theo ngôn ngữ hệ thống của TV, có tiếng Việt. Từ Android 13 có thể đặt ngôn ngữ riêng cho launcher:
  `tools/venv/bin/python -I tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales vi-VN'`.
- **Trình bảo vệ màn hình đồng hồ**: chọn Clock trong cài đặt trình bảo vệ màn hình của TV, hoặc chạy
  `tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'`.
- **Nút Nguồn vào trên remote**: khi cài trên macOS/Linux thêm `INPUT_KEY_SERVICE=1` để bật dịch vụ trợ năng, giúp nút
  nguồn vào trên remote Google TV hoạt động; trên Windows dùng `-InputKeyService`.
- **Nút Home / Cài đặt**: khi cài trên macOS/Linux thêm `HOME_KEY_SERVICE=1`, kết hợp các khoá phím trong `config.xml`
  cho một số TV; trên Windows dùng `-HomeKeyService`. `settings_key_panel` bật bảng cài đặt nhanh (cài đặt TV, hiển
  thị, mạng, ứng dụng, cài đặt launcher). Mỗi lần cài lại cần truyền lại các tuỳ chọn dịch vụ này.

### Thẻ module

Ứng dụng có thể khai báo tên thẻ, mô tả và màu nền. Launcher sẽ hỏi bạn có muốn hiện không, mặc định ẩn, và lưu lựa
chọn theo chữ ký ứng dụng và tên thẻ. Giữ OK trên nút Cài đặt và chọn menu thẻ module để quản lý.

Hướng dẫn cho lập trình viên xem [`docs/modules.md`](docs/modules.md), quy ước giao tiếp xem
[`docs/specs/card.md`](docs/specs/card.md).

## Khôi phục launcher và ứng dụng gốc

Trước khi tắt, `tools/disable.sh` ghi lại trạng thái của từng ứng dụng trong danh sách (đang bật, đã tắt hoặc chưa
cài), màn hình chính mặc định lúc đó, gói đang giữ màn hình chính, và ba thông số tốc độ hiệu ứng của hệ thống. Công cụ
không lưu toàn bộ danh sách ứng dụng hay toàn bộ cài đặt. Bản ghi nằm trong `~/.cleanyourtv/` trên máy tính, tách riêng
theo TV và theo danh sách. Chạy lại cùng một danh sách không ghi đè bản ghi đầu tiên; mục mới thêm vào danh sách sẽ
được ghi bổ sung.

Thay đường dẫn bằng **đúng danh sách bạn đã dùng** rồi khôi phục:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

Muốn chỉ định gói launcher gốc dự phòng thì thêm một tham số cuối. Google TV thường là
`com.google.android.apps.tv.launcherx`, nhưng hãy kiểm tra trên TV của bạn:

```bash
TV_HOST=192.168.1.50 tools/restore.sh "examples/model-cua-ban/debloat.txt" com.google.android.apps.tv.launcherx
```

- Có bản ghi thì script ưu tiên trả màn hình chính về đúng thành phần đã giữ nó trước đó; nếu lúc đó không xác định
  được, hoặc màn hình chính đã thuộc CleanYourTV, thì dùng gói dự phòng bạn chỉ định (không chỉ định thì thử
  `com.google.android.tvlauncher`).
- Gói nào đã tắt từ trước khi dọn dẹp thì vẫn để tắt. Script cũng thử bật gói launcher dự phòng; nếu gói đó không có
  trong bản ghi thì script không biết trạng thái ban đầu của nó, nên hãy kiểm tra tên gói trước.
- Không có bản ghi thì script báo và chuyển sang bật lại toàn bộ danh sách, đặt tốc độ hiệu ứng về `1.0`; như vậy không
  hoàn toàn giống trạng thái trước khi làm.
- Script khôi phục không gỡ CleanYourTV và không hoàn tác được các thay đổi tự làm ngoài danh sách. Muốn gỡ launcher thì
  vào **Cài đặt → Ứng dụng** trên TV.
- Không kết nối ADB được thì vẫn có thể bật lại ứng dụng trong cài đặt của TV.

## Xử lý sự cố

Hướng dẫn đầy đủ (tiếng Anh) trong [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

- **macOS báo `No route to host`**: kiểm tra quyền Mạng cục bộ (Local Network) của macOS; có thể dùng công cụ ADB viết
  bằng Python của repo qua `tools/venv/bin/python -I tools/tv.py`.
- **Màn hình chính bị lỗi sau khi thay**: phải chắc launcher mới đã cài và chạy được rồi mới tắt launcher gốc. Script
  Shell làm đúng thứ tự này; thứ tự của script Windows xem ở trên.
- **Trình cài đặt Google TV chiếm màn hình chính**: `com.google.android.tungsten.setupwraith` có thể chiếm màn hình
  chính sau khi tắt launcher gốc. Trước tiên kiểm tra nó có đang giữ mạng Wi-Fi hiện tại không theo tài liệu xử lý sự
  cố; tắt nó ngay có thể xoá mạng đó và TV mất kết nối. Công cụ dọn dẹp tự bỏ qua nó trong trường hợp này, bạn cần
  quên mạng Wi-Fi rồi kết nối lại trong Cài đặt trước. Nếu đã gặp lỗi, thử bật lại launcher Google TV gốc:
  `tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Câu hỏi thường gặp

**Có cài lại firmware hay xoá ứng dụng hệ thống không?**

Không. Dọn dẹp chỉ tắt ứng dụng, nhưng tắt nhầm thành phần hệ thống vẫn có thể gây lỗi, nên hãy đối chiếu theo model,
tắt từng đợt nhỏ và luôn giữ cách khôi phục.

**Cập nhật hệ thống và ứng dụng có bị ảnh hưởng không?**

Một số danh sách có tắt dịch vụ cập nhật hệ thống, cần bật lại mới nhận được bản firmware mới. Ứng dụng xem phim vẫn
cập nhật qua kho ứng dụng nếu bạn giữ nó; tuỳ TV và danh sách bạn dùng.

**Có làm hộ người khác từ xa được không?**

Được, qua một mạng Tailscale riêng, xem [hướng dẫn làm từ xa](docs/REMOTE.md). Đừng mở cổng ADB ra Internet trên router.

**Làm xong có cần tắt gỡ lỗi không?**

Có. Kết nối ADB qua TCP (mặc định cổng 5555) mà công cụ dùng không được mã hoá, và một số firmware còn không đòi cho
phép. Làm xong hãy tắt USB debugging và gỡ lỗi mạng / ADB, thu hồi quyền gỡ lỗi đã cấp, rồi kiểm tra cổng đã đóng theo
[các bước trong README tiếng Anh](README.md#questions-people-ask).

**Có hỗ trợ DNS riêng hay AirPlay không?**

DNS riêng (Private DNS) là tính năng của hệ thống Android trên một số phiên bản, không phải tính năng chặn quảng cáo
của launcher, và không chặn được mọi quảng cáo trong video. AirPlay không có sẵn trong launcher, phải cài và cấu hình
riêng. Các lựa chọn thêm xem [câu hỏi thường gặp bản tiếng Anh](README.md#questions-people-ask).

**Có file APK cài sẵn không?**

Hiện tại repo build và cài launcher bằng script cài đặt, để tiện chỉnh cấu hình theo từng TV.

## Đóng góp

Rất hoan nghênh danh sách TV đã kiểm chứng trên máy thật, cải tiến launcher, sửa tài liệu và theme mới. Đọc
[`CONTRIBUTING.md`](CONTRIBUTING.md) trước khi gửi, mỗi PR một chủ đề và ghi rõ đã thử trên TV nào. Các dự án phái sinh
và launcher phong cách khác có thể đăng ký trong [`SHOWCASE.md`](SHOWCASE.md).

Khi README tiếng Anh thay đổi, rất mong bạn cập nhật luôn bản này. Số lượng model, danh sách hỗ trợ và danh sách theme
xem trực tiếp trong các thư mục tương ứng để đỡ phải sửa nhiều chỗ.

## Liên hệ và báo lỗi bảo mật

Câu hỏi khi dùng TV có thể trao đổi trên [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions).
Liên hệ báo chí và hợp tác xem [phần liên hệ tiếng Anh](README.md#contact). Lỗ hổng bảo mật xin báo riêng qua
[trang Security](https://github.com/Gathin23/cleanyourtv/security) của repo, xem [`SECURITY.md`](SECURITY.md).

## Giấy phép

Dự án dùng [giấy phép MIT](LICENSE). Font Outfit dùng [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt).
