<p align="center">
  <a href="https://cleanyour.tv"><img src="docs/banner-halloween.png" alt="CleanYourTV：テレビのホーム画面をすっきりと" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · 日本語 · <a href="README.it.md">Italiano</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a>
</p>

<p align="center">
  <a href="https://cleanyour.tv">プロジェクトサイト</a> ·
  <a href="PROMPT.md">アシスタント用プロンプト</a> ·
  <a href="examples/README.md">機種リスト</a> ·
  <a href="SHOWCASE.md">コミュニティのプロジェクト</a>
</p>

# CleanYourTV 日本語ガイド

家庭の Wi-Fi 経由で Android TV や Google TV に接続し、不要なプリインストールアプリ、広告、テレメトリ（利用状況の送信）
サービスを無効化して、対応するテレビにはすっきりしたホーム画面（ランチャー）をインストールします。root 化、
ファームウェアの書き換え、USB ケーブルはいずれも不要です。システムアプリは **無効化** するだけでアンインストールは
せず、復元用のスクリプトも用意されています。

この文書では共通の機能と使い方を紹介します。機種、地域、ファームウェアによってアプリの構成や制限は異なるため、
まずお使いのテレビと照らし合わせてください。変更があった場合は[英語版 README](README.md) が正となります。
機種ごとの記録は[機種リスト](examples/README.md)にあります。

**Samsung、LG、Roku など Android 以外のテレビをお使いですか？** CleanYourTV は動きませんが、リモコンで
順番に操作してトラッキングをオフにできます: [cleanyour.tv/not-android](https://cleanyour.tv/not-android)

![標準の Google TV ホームと CleanYourTV ランチャーの比較](docs/stock-vs-new-side.jpg)

## 仕組み

テレビの開発者向けオプションで USB デバッグをオンにし、ネットワーク経由の ADB 接続をパソコンに一度だけ許可したら、
次の 3 ステップを行います：

1. **バックアップ**：インストール済みアプリの一覧と関連する設定を保存します。[アシスタント用プロンプト](PROMPT.md)を
   使う場合は、アシスタントが最初に完全なバックアップを取ります。`tools/disable.sh` を手動で使う場合、ツールが記録するのは
   そのリストを元に戻すのに必要な状態だけです。
2. **無効化**：テレメトリ、広告サービス、おすすめコンテンツの列、使わないプリインストールアプリを `pm disable-user` で
   無効化します。
3. **すっきりしたランチャーのインストール**：インストールして、デフォルトのホーム（HOME）に設定します。

残しておくべきシステムコンポーネントはメーカーごとに異なります。[アシスタント用プロンプト](PROMPT.md)は、
コーディングアシスタントにまず実機のテレビを調べさせ、そのうえでどのアプリや機能を使っているかをあなたに確認させます。

## クイックスタート

テレビと同じネットワークにつながったパソコンと、ターミナルのコマンドを実行できるコーディングアシスタントが必要です。
たとえば [Claude Code](https://claude.com/product/claude-code)、[Codex](https://github.com/openai/codex)、
[Gemini CLI](https://github.com/google-gemini/gemini-cli) などです。

| ステップ | やること |
|---|---|
| 1. テレビで | **設定 → システム（またはデバイス設定）→ 端末情報** を開き、**ビルド** の項目で OK を 7 回押します。戻って **開発者向けオプション** を開き、**USB デバッグ** をオンにします。ネットワークデバッグ / ADB デバッグのスイッチがあればそれもオンにします。メニューの名前は機種によって異なります。 |
| 2. パソコンで | リポジトリを複製してそのフォルダをアシスタントで開き（`git clone https://github.com/Gathin23/cleanyourtv`）、[`PROMPT.md`](PROMPT.md) の内容をアシスタントに貼り付けてテレビの機種名を記入します。 |
| 3. アシスタントに答える | テレビにデバッグの許可を求められたら **許可** を押し、よく使うアプリ、ドックに置きたいアプリ、音声アシスタント・Chromecast・システムアップデートを残すかどうかをアシスタントに伝えます。 |
| 4. 確認する | 指示に従ってバックアップ、無効化、インストールを行い、アプリの起動、リモコンのボタン、HDMI 入力の切り替え、復元方法を試します。 |

複製する理由：アシスタントは独自のスクリプトを書かず、このリポジトリの `tools/disable.sh` と `launcher/build-and-install.sh` を使います。バックアップ、ホーム画面の保護、復元用の記録はそこにあります。

## ランチャーの機能

- **シンプルなホーム画面**：暗い背景に、デフォルトでよく使う 4 つのアプリを下部のドックに並べます。
- **選択中のアプリ名**：アプリ名を大きな文字で表示し、そのアプリのバナー（バナーがなければアイコン）から取った色の
  光を背景に灯します。
- **アプリのグリッド**：下キーでさらにアプリを表示します。1 行あたり 4、5、6、7 タイルから選べます。
- **リモコンで並べ替え**：設定ボタンを長押ししてメニューを開き、並べ替えモードに入ります。
- **アプリライブラリ（オプション）**：インストール済みのアプリを一覧し、リモコンでホーム画面のアプリを追加・削除できます。
- **アニメーション**：エフェクトのオン・オフを切り替えられ、別途高度なエフェクトもあります。
- **よく使うショートカット**：Wi-Fi / 有線 LAN と設定。テレビに HDMI、AV、ライブ TV、または認識できる入力ソースが
  あれば入力切替ボタンも表示されます。HDMI ポートの名前は、テレビの設定で付けた名前がそのまま使われます。
- **時計とスクリーンセーバー**：あいさつ、時刻、日付、そしてお揃いの時計スクリーンセーバー。
- **テーマ**：色、壁紙、ドック、列数を設定ファイルで変更でき、リビルドは不要です。
- **モジュールカード**：他のアプリがホーム画面にカードを追加できます。あなたが許可した場合のみ表示されます。

![選択中のアプリと背景の光](docs/focus-2-up-netflix.png)

### テレビ上で設定を変更する

ホーム画面の **設定** ボタンを押すとテレビ本体の設定が開きます。**そのボタンを選択した状態でリモコンの OK を長押し**し、
ランチャー設定を選ぶと次の項目を変更できます：

| 項目 | 内容 |
|---|---|
| 1 行あたりのアプリ数 | 4、5、6、7 から選びます。列が多いほどタイルは小さくなります。 |
| アニメーション | ランチャーのエフェクトをオン・オフします。 |
| 高度なエフェクト | タイルの浮き上がり、アイコンの拡大、背景色の滑らかな切り替え。テレビによってはメモリを多く使います。 |
| テーマに従う（テレビ上の設定をクリア） | テレビ上で変更したレイアウトとエフェクトの選択をクリアし、現在のテーマまたは初期設定に戻します。テーマを反映・リセットした場合もクリアされます。 |
| デフォルトのホームへの入口 | Android のホーム選択画面を開き、元のランチャーと CleanYourTV を切り替えます。 |

デフォルトのホームへの入口では、インストールスクリプトが無効化した元のランチャーは再度有効になりません。その場合は
下記の手順で復元してください。ホーム選択画面がないテレビもあります。`show_home_settings_shortcut` を使うと、この入口を
独立したタイルとして表示することもできます。

### 並べ替えとアプリライブラリ

設定ボタンで OK を長押しし、メニューから並べ替えモードを選びます。OK で移動したいアプリを持ち上げ、矢印キーで
位置を入れ替え、OK または戻るで置きます。何も持っていないときに戻るを押すと、保存して終了します。上部バーの専用の
並べ替えボタンはデフォルトでは非表示で、設定またはテーマの `reorder_button` で表示できます。

アプリライブラリはデフォルトでオフです。設定またはテーマで `app_library` をオンにすると、ホーム画面の最後のアプリの
行の後に入口が表示されます。ライブラリにはテレビ向けアプリと通常の Android アプリの両方が表示されます。通常の
Android アプリは、マウスやタッチ操作が必要な場合があります。

- ホーム画面にまだないアプリで OK を押すと、ショートカットを追加します。
- ホーム画面にすでにあるアプリで OK を押すと起動します。OK を長押しすると、ショートカットの追加・削除やアプリ情報の
  メニューが開きます。
- ホーム画面から削除してもアプリはアンインストールされません。選択内容は再起動、アプリの更新、テーマの反映・リセット後も
  保持されます。

詳しくは[アプリライブラリのガイド](docs/app-library.md)を参照してください。

## 対応テレビ

無効化リストは **Android TV / Google TV** 向けです。ランチャーには現在 **Android 8.0（API 26）以降** が必要です。
コミュニティから多くのテレビやテレビボックスの設定が寄せられていますが、ランチャーの最低要件より古い Android で
動く機種もあるため、無効化リストがあってもランチャーがインストールできるとは限りません。[機種リスト](examples/README.md)
で機種、地域、ファームウェアを照らし合わせ、該当フォルダのメモを読んでから無効化リストを使ってください。

- Samsung Tizen と LG webOS では、このプロジェクトの Android ランチャーは動きません。
  [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) には、2021 年製の Tizen テレビがローカルネットワークで
  何を公開しているかを記録しています（ADB がないため、アプリの無効化はできません）。コミュニティによる他プラットフォームへの
  移植は[コミュニティのプロジェクト](SHOWCASE.md)にあります。Samsung、LG、Roku など Android 以外のテレビで
  トラッキングをオフにする手順は [cleanyour.tv/not-android](https://cleanyour.tv/not-android) にあります。
- Fire TV には手作業で進めるための無効化メモがあります。[アシスタント用プロンプト](PROMPT.md)は Fire TV には使えず、
  Fire OS ではデフォルトのホーム画面を変更できません。
- 中国本土で販売されている一部のテレビは、外部 APK やサードパーティのランチャーのインストールを拒否します。
  メーカー名だけで互換性を判断しないでください。
- お使いのテレビのリストがまだない場合は、アシスタントに実機を調べさせ、検証した設定を
  [コントリビュートガイド](CONTRIBUTING.md)に従って送ってください。

## 手作業でやる場合

以下のコマンドはリポジトリのルートディレクトリで実行します。`192.168.1.50` は例なので、テレビの IP アドレスに
置き換えてください（**設定 → ネットワークとインターネット** で接続中のネットワークを選ぶと確認できます）。
`tools/tv.py` はデフォルトで TCP のポート 5555 で ADB に接続します。テレビにペアリングコードが必要な
**ワイヤレスデバッグ** しかない場合は、先に[アシスタント用プロンプトの接続手順](PROMPT.md)に従ってペアリングし、
ポート 5555 を開いてください。

### 1. 接続ツールの準備

接続ツールには Python 3 が必要です：

```bash
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50

# 機種、Android バージョン、ファームウェアを確認
tools/venv/bin/python -I tools/tv.py --probe

# インストール済みのパッケージを確認
tools/venv/bin/python -I tools/tv.py 'pm list packages'
```

### 2. 無効化リストを適用する

**以下の Xiaomi のパスは例です。お使いのテレビに合った、内容を確認済みのリストに置き換えてください。**
別の機種のリストを自分のテレビに使わないでください。

```bash
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

リストのパスはコマンドを実行したディレクトリからの相対パスで、絶対パスも使えます。元に戻せるよう、リストとツールが
作成した復元用の記録は保管しておいてください。アプリと設定の完全なバックアップが欲しい場合は、無効化の前に自分で
保存してください。`tools/disable.sh` はそのバックアップを作成しません。

### 3. ランチャーをビルドしてインストールする

ランチャーは Java と AndroidX RecyclerView で書かれており、パッケージ名は `com.gathin.tvhome` です。JDK 17、
Android SDK Platform 34、Build Tools 34.0.0 が必要です。macOS では：

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Linux では、インストールスクリプトが `~/Android/Sdk` の SDK と `PATH` 上の Java を見つけます。準備ができたら実行します：

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

**このスクリプトは APK をインストールするだけでなく、デフォルトのホーム画面を変更し、認識できた元のランチャーを
無効化します。** スクリプトはまず新しいランチャーを試しに起動し、動作したら切り替えます。切り替えに失敗した場合は
元に戻そうとします。スクリプトが自動で認識するのは `com.google.android.tvlauncher`、
`com.google.android.apps.tv.launcherx`、`com.google.android.leanbacklauncher`、`com.mitv.tvhome` だけです。
認識できない場合はテレビを確認して `STOCK_LAUNCHER=<パッケージ名>` を指定するか、`none` を指定してください。

元のランチャーを有効のまま残したい場合：

```bash
TV_HOST=192.168.1.50 STOCK_LAUNCHER=none launcher/build-and-install.sh
```

`none` は元のランチャーを無効化しないという意味だけで、**CleanYourTV はデフォルトのホーム画面に設定されます**。

### Windows

リポジトリには対応する PowerShell スクリプトがあります。Python と Android Studio をインストールし、上記の JDK と
SDK コンポーネントがあることを確認してから実行します：

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
```

PowerShell スクリプトでは `-StockLauncher <パッケージ名>` で元のランチャーを指定し、`-StockLauncher none` で有効のまま
残します。PowerShell スクリプトは現在、新しいホーム画面を先に設定してから新しいランチャーが動くかを確認するため、
上記のシェルスクリプトとは順序が逆です。実行前に、復元のための ADB 接続ができることを確認してください。無効化と復元には
[`tools/disable.ps1`](tools/disable.ps1) と [`tools/restore.ps1`](tools/restore.ps1) を使います。

### ツール一覧

| ツール | 用途 |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Python 製の ADB クライアント：コマンドの実行、一括実行、ファイルの push と pull。 |
| [`tools/b64push.py`](tools/b64push.py) | APK などの大きなファイルを分割して送り、検証します。 |
| [`tools/disable.sh`](tools/disable.sh) / [`tools/restore.sh`](tools/restore.sh) | 無効化リストを適用、または作業前の記録に従って復元します。 |
| [`tools/recovery.py`](tools/recovery.py) | 復元用の記録を保存し、復元コマンドを生成します。 |
| [`tools/theme.sh`](tools/theme.sh) | テーマを反映またはリセットし、ホーム画面を再起動します。 |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | ランチャーをビルド・インストールし、ホーム画面を切り替えます。 |

## 追加の設定

### レイアウトとテーマ

初期設定は [`config.xml`](launcher/app/src/main/res/values/config.xml) にあります：ドックの順番、非表示アプリ、
ライブ TV のパッケージ名、列数、ドックの下に表示する行数、ネットワークボタンに紐付ける VPN アプリ、`reduced_motion` など。

`show_stats` は時計の下に CPU、空きメモリ、空きストレージを表示します。デフォルトはオフで、ホーム画面の表示中だけ更新
されます。CPU の読み取りをブロックするテレビでは CPU の項目は表示されません。`greeting_name` を設定すると、あいさつに
名前が追加されます。

テーマは `config.json` で初期設定を上書きし、背景色や壁紙を含めることもできます。例：

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
```

初期設定に戻す：

```bash
TV_HOST=192.168.1.50 tools/theme.sh --reset
```

テーマの形式と設定キーは [`themes/README.md`](themes/README.md) を参照してください。

### 言語、スクリーンセーバー、リモコン

以下の ADB コマンドは macOS/Linux の venv のパスを使っています。Windows では
`tools/venv/bin/python -I tools/tv.py` を `tools\venv\Scripts\python.exe -I tools\tv.py` に置き換えてください。

- **言語** はテレビのシステム言語に従います。現在、ランチャーには日本語の翻訳がないため、日本語環境では英語で表示されます。
  Android 13 以降では、ランチャーだけの言語を指定できます：
  `tools/venv/bin/python -I tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales en'`。
- **時計スクリーンセーバー**：テレビのスクリーンセーバー設定で Clock を選ぶか、次を実行します：
  `tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'`。
- **リモコンの入力切替ボタン**：macOS/Linux でインストールする際に `INPUT_KEY_SERVICE=1` を付けるとユーザー補助サービスが
  有効になり、Google TV のリモコンの入力切替ボタンが使えます。Windows では `-InputKeyService` を使います。
- **ホーム / 設定ボタン**：macOS/Linux でインストールする際に `HOME_KEY_SERVICE=1` を付け、一部のテレビでは `config.xml` の
  キー設定と組み合わせます。Windows では `-HomeKeyService` を使います。`settings_key_panel` でクイック設定パネル（テレビの設定、
  ディスプレイ、ネットワーク、アプリ、ランチャー設定）を有効にできます。再インストールのたびに、これらのサービスの
  オプションを付け直す必要があります。

### モジュールカード

アプリはカードの名前、説明、背景色を宣言できます。ランチャーは表示するかどうかをあなたに確認し、デフォルトでは非表示に
して、アプリの署名とカード名ごとに選択を保存します。管理するには、設定ボタンで OK を長押ししてモジュールカードのメニューを
選びます。

開発者向けのガイドは [`docs/modules.md`](docs/modules.md)、インターフェースの仕様は
[`docs/specs/card.md`](docs/specs/card.md) を参照してください。

## 元のランチャーとアプリを復元する

無効化の前に、`tools/disable.sh` はリスト内の各アプリの状態（有効、無効済み、未インストール）、その時点のデフォルトの
ホーム画面、ホームを持っていたパッケージ、システムの 3 つのアニメーション速度を記録します。アプリの一覧や設定全体は
保存しません。記録はパソコンの `~/.cleanyourtv/` に、テレビとリストごとに分けて保存されます。同じリストを再度実行しても
最初の記録は上書きされず、リストに新しく追加した項目だけが追記されます。

パスを **実際に使ったリスト** に置き換えて復元します：

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

予備の元ランチャーのパッケージを指定したい場合は、最後に引数を 1 つ追加します。Google TV では通常
`com.google.android.apps.tv.launcherx` ですが、お使いのテレビで確認してください：

```bash
TV_HOST=192.168.1.50 tools/restore.sh "examples/your-model/debloat.txt" com.google.android.apps.tv.launcherx
```

- 記録がある場合、スクリプトはホーム画面を以前ホームを持っていたコンポーネントに戻すことを優先します。その時点で
  特定できなかった場合や、ホームがすでに CleanYourTV だった場合は、指定した予備のパッケージを使います（指定がなければ
  `com.google.android.tvlauncher` を試します）。
- 無効化の前からすでに無効だったパッケージは、無効のまま残ります。スクリプトは予備のランチャーパッケージも有効にしようと
  します。そのパッケージが記録にない場合、元の状態は分からないため、先にパッケージ名を確認してください。
- 記録がない場合、スクリプトはその旨を表示し、リスト全体を再度有効にしてアニメーション速度を `1.0` に戻します。
  この場合、作業前とまったく同じ状態にはなりません。
- 復元スクリプトは CleanYourTV をアンインストールせず、リスト外で自分で行った変更も元に戻せません。ランチャーを削除するには、
  テレビの **設定 → アプリ** から行ってください。
- ADB で接続できない場合でも、テレビの設定からアプリを再度有効にできます。

## トラブルシューティング

完全なガイド（英語）は [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) にあります。

- **macOS で `No route to host` と表示される**：macOS のローカルネットワークの許可を確認してください。リポジトリにある Python 製の
  ADB ツールを `tools/venv/bin/python -I tools/tv.py` で使うこともできます。
- **入れ替え後にホーム画面がおかしくなった**：新しいランチャーがインストールされて動作することを確認してから、元のランチャーを
  無効化する必要があります。シェルスクリプトはこの順序で行います。Windows スクリプトの順序は上記を参照してください。
- **Google TV のセットアップウィザードがホーム画面を奪う**：`com.google.android.tungsten.setupwraith` は、元のランチャーを無効化した
  後にホーム画面を奪うことがあります。まずトラブルシューティングの文書に従って、現在の Wi-Fi ネットワークを持っていないか
  確認してください。すぐに無効化するとそのネットワークが削除され、テレビの接続が切れることがあります。この場合、無効化ツールは
  自動でスキップするので、先に設定で Wi-Fi ネットワークを削除して接続し直してください。すでに問題が起きている場合は、
  元の Google TV ランチャーを再度有効にしてみてください：
  `tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`。

## よくある質問

**ファームウェアを書き換えたり、システムアプリを削除したりしますか？**

いいえ。アプリを無効化するだけですが、システムコンポーネントを誤って無効化すると不具合が起きることはあるので、機種ごとに
照らし合わせ、少しずつ無効化し、常に復元できるようにしておいてください。

**システムやアプリのアップデートに影響はありますか？**

一部のリストはシステムアップデートのサービスを無効化するので、新しいファームウェアを受け取るには再度有効にする必要が
あります。動画配信アプリは、アプリストアを残していれば引き続き更新されます。テレビと使うリストによって異なります。

**離れた場所から他の人のテレビにもできますか？**

はい、プライベートな Tailscale ネットワーク経由でできます。[リモートでの手順](docs/REMOTE.md)を参照してください。
ルーターで ADB ポートをインターネットに公開しないでください。

**終わったらデバッグをオフにする必要がありますか？**

はい。ツールが使う TCP 経由の ADB 接続（デフォルトはポート 5555）は暗号化されておらず、ファームウェアによっては許可すら
求められません。作業が終わったら USB デバッグとネットワーク / ADB デバッグをオフにし、付与したデバッグの許可を取り消して、
[英語版 README の手順](README.md#questions-people-ask)に従ってポートが閉じていることを確認してください。

**プライベート DNS や AirPlay には対応していますか？**

プライベート DNS は一部のバージョンの Android システムの機能で、ランチャーの広告ブロック機能ではなく、動画内のすべての広告を
ブロックできるわけでもありません。AirPlay はランチャーには含まれておらず、別途インストールと設定が必要です。その他の選択肢は
[英語版のよくある質問](README.md#questions-people-ask)を参照してください。

**ビルド済みの APK はありますか？**

現在、リポジトリではテレビごとに設定を調整しやすいよう、インストールスクリプトでランチャーをビルド・インストールしています。

## コントリビュート

実機で検証したテレビのリスト、ランチャーの改善、ドキュメントの修正、新しいテーマを歓迎します。送る前に
[`CONTRIBUTING.md`](CONTRIBUTING.md) を読み、1 つの PR につき 1 つのテーマにして、どのテレビで試したかを明記してください。
派生プロジェクトや別スタイルのランチャーは [`SHOWCASE.md`](SHOWCASE.md) に登録できます。

英語版 README が更新されたら、この文書もあわせて更新していただけると助かります。機種数、対応リスト、テーマ一覧は、
修正箇所を減らすため各ディレクトリを直接参照してください。

## 連絡先とセキュリティの報告

テレビの使い方に関する質問は [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) でどうぞ。
取材や提携については[英語版の連絡先](README.md#contact)を参照してください。セキュリティの脆弱性は、リポジトリの
[Security ページ](https://github.com/Gathin23/cleanyourtv/security)から非公開で報告してください。[`SECURITY.md`](SECURITY.md) を参照してください。

## ライセンス

このプロジェクトは [MIT ライセンス](LICENSE)です。Outfit フォントは [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt) です。
