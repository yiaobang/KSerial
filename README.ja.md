<p align="center">
  <img src="composeApp/src/jvmMain/composeResources/drawable/app_icon.png" width="128" alt="KSerial アイコン" />
</p>

<h1 align="center">KSerial</h1>

<p align="center">シリアル端末、デバイス模擬、シリアルブリッジをひとつにまとめたクロスプラットフォーム対応のデスクトップ向けシリアルワークベンチ。</p>

<p align="center">
  <a href="README.md">简体中文</a> · <a href="README.en.md">English</a> · <strong>日本語</strong>
</p>

<p align="center">
  <a href="https://github.com/yiaobang/KSerial/actions/workflows/release.yml"><img src="https://github.com/yiaobang/KSerial/actions/workflows/release.yml/badge.svg" alt="ビルドとリリース" /></a>
  <a href="https://github.com/yiaobang/KSerial/releases/latest"><img src="https://img.shields.io/github/v/release/yiaobang/KSerial?display_name=tag&amp;sort=semver" alt="最新リリース" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/yiaobang/KSerial" alt="Apache-2.0 ライセンス" /></a>
  <img src="https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-4c8bf5" alt="Windows、macOS、Linux 対応" />
</p>

KSerial は Kotlin と Compose Multiplatform で構築され、組み込み開発、ハードウェアのデバッグ、プロトコル検証、シリアル機器のテストを支援します。日常的なシリアル通信に加え、自動応答、機器動作の模擬、複数ポート間の転送をひとつのタブ型ワークスペースに統合しています。

## 主な機能

- 機器名付きでシリアルポートを検出し、手動更新、ボーレート、データビット、ストップビット、パリティ、フロー制御の設定に対応。
- テキストまたは HEX の送受信、HEX のリアルタイム検証、行末の選択、繰り返し送信、クイック送信、`Ctrl+Enter` / `Enter` 送信に対応。
- 時間ウィンドウ、受信チャンク、区切り文字、固定長の 4 種類で受信表示を分割し、高負荷時は有界バッファーと破棄数表示で UI を保護。
- 接続状態、異常切断、任意の自動再接続に加え、DTR・RTS・Break 制御と CTS・DSR・DCD・RI 信号表示に対応。
- 履歴のキーボード操作、CRC16-Modbus・XOR・SUM8・LRC の計算、汎用フレーム確認に対応。
- TX/RX カウンター、ログ検索と種類別フィルター、キーワード強調、コピー、TXT/CSV 出力、通信データの保存を提供。
- HOCON `.conf` / `.hocon` で応答ルールを定義し、固定長および区切り文字ベースのフレーム分割に対応。応答遅延は固定ミリ秒のほか、`"100~300"` のような**ランダム範囲**も指定でき、実機の処理時間のばらつきを再現できます。
- 複数のシリアル端点を接続し、1 対 1、1 対多、多対多の転送ルートを設定可能。
- **ネットワーク端点**：どのワークスペースでも物理シリアルポートに加えて `tcp://host:port`、`tcp-listen://port`、`udp://host:port`、`udp-listen://port` アドレスを利用可能。
- **シリアル録画**：ベンダーソフトと実機の間で 1 対 1 透過転送を行いながら質疑応答ペアを記録し、そのままデバイスシミュレータ設定へエクスポートできる専用ワークスペース。ベンダーソフトがなくデバイスのポートが 1 つしかない場合は**手動モード**に切り替え、ページ内の入力欄から直接コマンドを送っても同じようにペアを記録できます。
- **テストシーケンス**：HOCON 宣言型ステップスクリプト（CONTAINS/EXACT/REGEX/SILENCE マッチ、ASCII/HEX エンコード）と合格/不合格レポート。
- **シリアルプロッタ**：数値ストリームのライブ折れ線グラフ（1 行複数チャンネル対応）。
- **ヘッドレスモード**：同じ実行ファイルに引数を付けて起動すると、ウィンドウを開かずにブリッジ・模擬・テストシーケンスを実行でき、スクリプトや CI に組み込めます。詳細は[ヘッドレスモード（CLI）](#ヘッドレスモードcli)を参照してください。
- 複数タブ、タブの別ウィンドウ化と再統合に対応し、同じポートの重複使用を防止。
- 簡体字中国語、英語、日本語、およびライト・ダークテーマを内蔵。
- GitHub Actions により Windows、macOS、Linux 向けのインストーラーとポータブル版を自動生成。

## 6 つのワークスペース

| ワークスペース | 用途 |
| --- | --- |
| **シリアル端末** | 通常のシリアルデバッグ。機器への接続、テキストまたは HEX の送受信、繰り返し・クイック送信、ログの絞り込み、TX/RX データの保存を行います。 |
| **デバイス模擬** | 設定に従って受信フレームを照合し、自動応答します。シリアル機器の模擬、ホストソフトウェアの検証、反復的なプロトコル試験に使用できます。 |
| **シリアルブリッジ** | 複数の端点と転送ルートを管理します。端点は物理シリアルポートでも TCP/UDP アドレスでもよく、透過転送、中継、リモートシリアル、複数機器の結合試験に使用します。 |
| **キャプチャ** | 2 つの端点を双方向に透過転送しつつ「コマンド → 応答」を記録し、模擬設定として書き出します。ネットワーク端点と手動モードにも対応します。 |
| **テストシーケンス** | 設定ファイルの手順に沿って送信・待機・判定を行い、PASS/FAIL レポートを出力します。 |
| **プロッタ** | テキストの数値ストリームを複数チャンネルのリアルタイム曲線として描画します。 |

各ワークスペースでは複数の独立したタブを作成できます。タブは別ウィンドウに切り離し、必要に応じてメインウィンドウへ戻せます。

## ダウンロード

[GitHub Releases](https://github.com/yiaobang/KSerial/releases/latest) から、お使いの OS に対応する最新版をダウンロードしてください。

| OS | インストーラー | ポータブル版 |
| --- | --- | --- |
| Windows | `.msi`、`.exe` | `.zip` |
| macOS | `.dmg`、`.pkg` | `.zip` |
| Linux | `.deb`、`.rpm` | `.tar.gz` |

配布パッケージにはアプリケーションの実行環境が含まれるため、通常は JDK を別途インストールする必要はありません。現在、Windows のコード署名および macOS の署名・公証は未設定のため、初回起動時に OS のセキュリティ警告が表示される場合があります。

## クイックスタート

1. **シリアル端末**を開き、`+` を選択してタブを作成します。
2. 「接続と通信設定」でポートを選択し、ボーレートなどの通信条件を設定します。
3. ポートを開き、画面下部の入力欄からテキストまたは HEX データを送信します。テキストモードは送信前に `\\`、`\'`、`\"`、`\?`、`\a`、`\b`、`\e`、`\f`、`\n`、`\r`、`\s`、`\t`、`\v`、8 進 `\ooo`、生バイト `\xHH`、Unicode `\uHHHH` / `\UHHHHHHHH`、制御文字 `\cX` を展開します。
4. `Ctrl+Enter` で送信します。Enter 送信を有効にした場合は `Shift+Enter` で改行でき、必要に応じて LF、CR、CRLF を追加できます。
5. 必要に応じて自動再接続、受信フレーム分割、繰り返し送信、クイック送信、TX/RX 保存を有効にします。検索・キーワード強調・ログフィルターはログ領域のヘッダーにまとまっており、検索ボックスで直接絞り込み（`Ctrl+F` でフォーカス）、右側の 2 つのアイコンで強調とフィルターの設定を開きます。

機器を模擬する場合は **デバイス模擬** でルール設定を読み込み、ポート間でデータを転送する場合は **シリアルブリッジ** で端点とルートの設定を読み込みます。

### シリアル ⇄ ネットワーク（TCP / UDP）

**ターミナル**、**シリアルプロッタ**、**録画** の各ワークスペースにアドレス欄があり、シリアルブリッジの端点設定も同様です。シリアルポート名のほかに次のネットワーク形式を指定できます（大文字小文字は区別しません）。各アドレス欄の下には「検出されたポート」のドロップダウンもあります：

| 書き方 | 動作 |
| --- | --- |
| `COM3` / `/dev/ttyUSB0` | 物理シリアルポート（既定） |
| `tcp://192.168.1.20:8080` | TCP クライアント：リモートのシリアルサーバー（ser2net、ESP-Link、USR-TCP232 など）に接続 |
| `tcp://:9000` / `tcp-listen://9000` | TCP サーバー：ローカルで待ち受け、対向の接続を待つ（同時に 1 クライアント。後からの方が優先） |
| `udp://192.168.1.20:9000` | UDP：固定の対向先とデータグラムを送受信 |
| `udp-listen://9000` | UDP ポートで待ち受け、最初に受信したデータグラムの送信元アドレスに返信 |

**典型的な使い方：PC A から PC B のシリアルポートを操作する。**

1. PC B（ポートがある側）：**シリアルブリッジ** を開き、ローカルポート（例：`COM3`、ボーレートはここで設定）と `tcp-listen://9000` を両方向のルートで結んだ設定を読み込む；
2. PC A：**ターミナル** または **シリアルプロッタ** のアドレス欄に `tcp://<B の IP>:9000` と入力（録画でも同様）。開ければ送受信・HEX・繰り返し送信・録画・プロットはローカルポートとまったく同じ；
3. PC B は同じブリッジ設定をヘッドレスでバックグラウンド実行できるため、常設の「シリアルサーバー」に向いています。

**注意点：**

- ネットワークエンドポイントにはボーレートの概念がなく、シリアルパラメータ（ボーレート、パリティなど）は物理ポートを持つ側で設定します。
- DTR / RTS / BREAK のライン制御はローカルの電気信号であり、ネットワーク越しには**伝わりません**。データの送受信には影響しません。
- 待ち受けポートは同時に 1 クライアントのみ受け付けます。
- `udp-listen` の返信先は最初に受信したデータグラムの送信元アドレスです。対向のアドレスが変わった場合は、新しいデータグラムを受信するまで返信先は更新されません。

HOCON の書き方と完全な例は [`config_samples/10-network-endpoints.conf`](config_samples/10-network-endpoints.conf) を参照してください。

### 実行ディレクトリと `settings.conf`

初回起動時に、KSerial は `System.getProperty("user.dir")` を実行ルートとして `conf` ディレクトリを作成し、次のファイルを配置します。

- グローバル設定用の `settings.conf`。
- 段階的に学べるデバイス模擬・ブリッジ・テストシーケンス・ネットワーク端点の例 `01-simulator-minimal.conf` ～ `10-network-endpoints.conf`。

デバイス模擬とシリアルブリッジの設定ファイル選択画面は、最初にこの `conf` ディレクトリを開きます。旧版からの更新時は、新しい場所に `settings.conf` がなければ従来のユーザー設定をコピーします。サンプルは存在しない場合だけ作成し、ユーザーの編集を上書きしません。

`settings.conf` はコメントなしの HOCON として保存されます。手動で追加したコメントは読み込めますが、次回保存時に削除されます。`Program Files` など保護された場所にインストールした場合は、`conf` の作成・更新に必要な書き込み権限を実行ユーザーへ付与してください。

| フィールド | 説明 |
| --- | --- |
| `schemaVersion` | 設定スキーマのバージョン。手動で変更しないでください。 |
| `appearance.darkTheme` | ダークテーマを使用するか。 |
| `appearance.alwaysOnTop` | ウィンドウを常に最前面に表示するか。 |
| `appearance.language` | UI 言語：`zh`、`en`、`ja`。 |
| `serial.selectedPort` | 最後に選択したポート。選択だけを復元し、自動では開きません。 |
| `serial.baudRate` | ボーレート。 |
| `serial.dataBits` | データビット。5～8。 |
| `serial.stopBitsIndex` | ストップビットの索引：`0=1`、`1=1.5`、`2=2`。 |
| `serial.parityIndex` | パリティの索引：`0=None`、`1=Odd`、`2=Even`、`3=Mark`、`4=Space`。 |
| `serial.flowControlIndex` | フロー制御の索引：`0=None`、`1=RTS/CTS`、`2=DSR/DTR`、`3=XON/XOFF`。 |
| `preferences.showRealTime` | ログに時刻を表示するか。 |
| `preferences.hexDisplay` | 受信データを HEX 表示するか。 |
| `preferences.hexSend` | 送信入力を HEX として解釈するか。 |
| `preferences.receiveDisplayMode` | 受信表示モード：`TIME_WINDOW`、`RAW_CHUNKS`、`DELIMITER`、`FIXED_LENGTH`。 |
| `preferences.receiveWindowMs` | 時間ウィンドウモードの一括更新間隔（ミリ秒）。 |
| `preferences.receiveDelimiter` | 区切り文字モードのテキスト区切り。テキスト送信と同じエスケープ構文を使用。 |
| `preferences.receiveFrameSize` | 固定長モードの 1 フレーム当たりのバイト数。 |
| `preferences.sendLineEnding` | テキスト送信に追加する行末：`NONE`、`LF`、`CR`、`CRLF`。HEX 送信には追加しません。 |
| `preferences.enterToSend` / `clearAfterSend` | Enter で送信するか、および送信成功後に入力欄を消去するか。 |
| `preferences.autoReconnect` / `reconnectIntervalMs` | 異常切断後に再接続するか、および再試行間隔（ミリ秒）。 |
| `preferences.saveSend` / `saveReceive` | 送信・受信データを保存するか。 |
| `preferences.quickCommandsText` | クイック送信コマンド。1 行に 1 件、テキスト送信と同じエスケープ規則を使用。 |
| `preferences.highlightEnabled` / `highlightIgnoreCase` / `highlightKeywords` | キーワード強調の有効状態、大文字小文字の規則、キーワード一覧。 |
| `preferences.logQuery` / `logOnlyMatches` | 最後のログ検索文字列と、一致項目だけを表示するか。 |
| `preferences.logAutoScroll` | ログを自動的に末尾までスクロールするか。 |
| `preferences.logShowTx` / `logShowRx` / `logShowInfo` | 各ログ種別を表示するか。 |
| `sendHistory` | 新しい順、重複除外、最大 100 件の送信履歴。 |

送信入力欄、繰り返し送信スイッチ、送信間隔は保存せず、起動時にシリアルポートを自動では開きません。

## 設定によるデバイス模擬とブリッジ

リポジトリには、最小構成のデバイス模擬から複数端点のルーティングまで、段階的に学べる HOCON サンプルがあります。学習順序、フィールド一覧、コメント構文については [設定サンプルガイド](config_samples/README.ja.md) を参照してください。

- [最小のデバイス模擬](config_samples/01-simulator-minimal.conf)
- [テキスト用デバイス模擬](config_samples/02-simulator-text.conf)
- [固定長 HEX デバイス模擬](config_samples/03-simulator-hex-fixed-length.conf)
- [一方向シリアルブリッジ](config_samples/04-bridge-one-way.conf)
- [双方向シリアルブリッジ](config_samples/05-bridge-bidirectional.conf)
- [複数端点・複数ルートのシリアルブリッジ](config_samples/06-bridge-multi-route.conf)
- [多対多フルメッシュブリッジ](config_samples/07-bridge-many-to-many.conf)
- [AT 風デバイス模擬（応答遅延つき）](config_samples/08-simulator-device.conf)
- [自動テストシーケンス](config_samples/09-test-sequence.conf)
- [シリアル ⇄ TCP ネットワーク端点](config_samples/10-network-endpoints.conf)

HOCON の `.conf` では `#` と `//` のコメントを使用できるため、ポートの用途、プロトコルの説明、デバッグ時のメモを設定ファイル内に残せます。

デバイス模擬の例：

```hocon
simulator {
  endpoint {
    port = "COM2"
    baudRate = 9600
    dataBits = 8
    stopBits = 1
    parity = "none"
    flowControl = "none"
  }

  encode = "ASCII"
  delimiter = "\r\n"

  rules = [
    { request = "AT\r\n", response = "OK\r\n" }
  ]
}
```

バイト単位のルールには `encode = "HEX"` を使用します。`packSize` は固定長フレーム、`delimiter` は区切り文字フレームに使用し、必ずどちらか一方だけを指定します。デバイス模擬には完全な端点設定、シリアルブリッジには 2 つ以上の端点と 1 件以上の有効なルートが必要です。設定の読み込み時には、不正な HEX、空の区切り文字、ポート不足、ポート重複、無効なルート参照などを検出します。

### 応答遅延（固定値またはランダム範囲）

応答に遅延を付けられます。`300` は 300 ms 固定、`"100~300"` は応答ごとに 100～300 ms の**一様ランダム**です。

```hocon
simulator {
  responseDelay = "10~80"          # グローバル：各応答は 10～80 ms の範囲でランダム

  rules = [
    { request = "AT\r\n",         response = "OK\r\n" }
    { request = "AT+STATUS?\r\n", response = "STATUS:READY\r\n", delay = "200~500" }  # ルール単位で上書き
    { request = "AT+ID\r\n",      response = "ID:42\r\n",       delay = 20 }           # 20 ms 固定
  ]
}
```

- 単位はミリ秒で、範囲は `0`～`600000`。区切り文字は `~` と `-` のどちらも使え、前後の空白は省略できます。
- 優先順位：ルール単位の `delay` > グローバルの `responseDelay` > 既定値 `0`（即時応答）。
- **ランダム範囲の目的は境界の検証**です。実機の処理時間はばらつくため、固定値では上位ソフトのタイムアウトしきい値に到達できません。範囲をしきい値より少し大きくすると、正しく再送するか、同じ応答を二重処理しないかを確認できます。
- 遅延付きの応答が複数ある場合は **FIFO** で直列に送信され、1 度に 1 リクエストしか処理しない実機の動作と一致します。
- 誤った値も黙って無視されません。`responseDelay` が不正なら `0` として扱い警告を出し、ルール単位の `delay` が不正ならグローバル値へ戻して警告を出します。

## 自動テストシーケンス

デバイス模擬が「デバイスがどう応答するか」を担うのに対し、テストシーケンスは「デバイスをどう検証するか」を担います。1 つの HOCON ファイルに「送信 → 期待値 → タイムアウト」を記述し、ステップごとに実行して PASS/FAIL を出力します。

```hocon
test {
  connection { port = "COM2", baudRate = 115200 }
  defaults   { timeout = 1000, interval = 100 }

  steps = [
    { name = "ハンドシェイク", send = "AT\r\n",         expect = "OK" },
    { name = "遅い応答",       send = "AT+STATUS?\r\n", expect = "STATUS:READY", timeout = 1500 },
    { name = "無音チェック",   match = "silence",       timeout = 500 },
    { name = "HEX",            encoding = "hex", send = "41 54 0D 0A", expect = "4F 4B" }
  ]
}
```

- **画面**：**テストシーケンス**ワークスペースで設定を読み込み、実行します。接続ポートは設定の `test.connection.port` をそのまま使用し（画面にポート選択はないため、変更する場合は設定ファイルを編集します）、ステップごとに PASS/FAIL、期待値、実際の値、所要時間を表示します。失敗時は「タイムアウト（1 バイトも受信せず）」と「内容不一致」を区別します。
- **コマンドライン**：`KSerial.exe test 09-test-sequence.conf --report result.txt`。終了コードが非 0 なら失敗ステップがあるため、そのまま CI の判定に使えます。
- 判定は 4 種類：`contains`（既定、部分文字列）、`exact`（前後の空白を除いて完全一致）、`regex`（正規表現の部分一致）、`silence`（時間内に 1 バイトも受信しないこと）。
- 受信内容が一致した時点で成功となり、タイムアウト満了まで待ちません。失敗となるのはタイムアウトした場合だけです。
- フィールド一覧と判定仕様は[設定サンプルガイド](config_samples/README.ja.md#テストシーケンスのフィールド)、実行可能な例は [09-test-sequence.conf](config_samples/09-test-sequence.conf) を参照してください。

## シリアルプロッタ

デバイスが出力する数値ストリームをリアルタイムの曲線にします。**1 行が 1 サンプル、1 列が 1 チャンネル**（最大 8 チャンネル）。カンマ、空白、`KEY=VALUE` の接頭辞に対応します。

```
23.5,61,1013      ← 単純な CSV
TEMP=23.5,HUM=61  ← ラベル付き
```

- **使い方**：**シリアルプロッタ**ワークスペースのアドレス欄にポート名（または `tcp://` / `udp://` アドレス）を入力し、ボーレートを設定して「開く」を押すと曲線が流れ始めます。ウィンドウ長は 200 / 500 / 1000 / 2000 サンプルから選択できます。
- プロッタのアドレス欄はターミナルと同じくネットワークアドレスを受け付けるため、リモート機器の数値ストリームを直接描画できます。`tcp://ホスト:ポート` を入力して開くだけです。
- **一時停止**中に受信したデータは後で補完されず破棄されます。**クリア**は曲線・チャンネル数・バイト数をリセットします。
- 縦軸は現在のウィンドウに合わせて自動スケールし、左上に範囲を表示します。凡例の色は曲線と対応します。数値以外の行（`OK`、エラーログ）は無視され、改行のないバイナリ列は 4096 バイトで保護されます。
- **最も多い落とし穴**：行が改行で終わって初めて 1 サンプルになります。ターミナルから送信する場合は行末を LF か CRLF に設定してください。設定しないとバイトは届いても曲線になりません。
- **診断**：グラフ上部に「受信 N バイト」を表示し、行末待ちのデータがある場合は「N バイトが行末待ち」を追加表示します。サンプルが 1 つもない場合の空表示は、改行と数値のどちらが足りないかを示します。
- UTF-8 テキストの数値のみ対応します。HEX 表示のバイナリデータは描画できません。

## ヘッドレスモード（CLI）

同じ実行ファイルでも、**引数を付けて起動するとウィンドウを開かず**、ターミナル・スクリプト・CI から直接実行できます。

```powershell
# Windows（インストール先または zip 展開先）
KSerial.exe gateway  07-bridge-many-to-many.conf --duration 60
KSerial.exe simulate 08-simulator-device.conf
KSerial.exe test     09-test-sequence.conf --report result.txt

# Linux（tar.gz 展開先）
./KSerial gateway 05-bridge-bidirectional.conf --duration 30

# macOS（.app バンドル内）
/Applications/KSerial.app/Contents/MacOS/KSerial gateway 05-bridge-bidirectional.conf --duration 30
```

| サブコマンド | 別名 | 動作 |
| --- | --- | --- |
| `gateway` | `bridge` | `gateway.endpoints` / `gateway.routes` に従って端点を開き転送します |
| `simulate` | `simulator` | `simulator.*` の端点を開き、ルールに従って自動応答します |
| `test` | `run-test` | `test.steps` を実行し、ステップごとに PASS/FAIL を出力します |
| `help` | `--help`、`-h` | 使い方を表示します |

| オプション | 対象 | 説明 |
| --- | --- | --- |
| `--duration <秒>` / `-d` | `gateway`、`simulate` | 指定時間が経過したら終了します。省略すると Ctrl+C まで動作し続けます |
| `--report <パス>` | `test` | ステップごとの結果をテキストレポートとして書き出します |
| `--lang zh\|en\|ja` | `gateway` | 設定検証メッセージの言語。既定ではシステムロケールに従います |

終了コード：`0` 成功、`1` 失敗（設定が実行不可、ポートを開けない、失敗ステップあり）、`2` 使い方の誤り。

ソースから直接実行することもできます（パッケージ化は不要）。

```powershell
.\gradlew.bat :composeApp:run --args="--help"
.\gradlew.bat :composeApp:run --args="test 09-test-sequence.conf --report result.txt"
```

注意点：

- ヘッドレスモードは**設定ファイル内の端点のみ**を使用します。`COM3` のような一時指定はできず、ポートを変えるには `.conf` を編集します。
- `simulate` は設定内に `simulator.endpoint.port` が必要です。画面の「現在のページ設定を流用」はヘッドレスでは使えません。
- `gateway` に `routes` がない場合、「受信データを他のすべての端点へ転送」に退化します。
- 端点アドレスは `tcp://`、`tcp-listen://`、`udp://`、`udp-listen://` にも対応するため、ヘッドレスプロセスを常設のシリアルサーバーとして使えます（上記「シリアル ⇄ ネットワーク」参照）。
- 各ログ行はタイムスタンプ付きで stdout に出力されるため、リダイレクトで記録できます：`KSerial.exe gateway x.conf > gateway.log 2>&1`。
- CI では `--duration` を指定して自動終了させ、Ctrl+C に頼らないでください。

## ソースから実行

必要な環境：

- JDK 25
- リポジトリに同梱されている Gradle Wrapper

Windows：

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

macOS / Linux：

```bash
./gradlew :composeApp:run
./gradlew :composeApp:jvmTest
./gradlew :composeApp:packageDistributionForCurrentOS
```

## テスト

Gradle プロジェクトを更新すると、IDE の `composeApp > Tasks > KSerial Tests` グループから次のタスクを直接実行できます。

| タスク | 用途 |
| --- | --- |
| `coreTest` | 実際のシリアルポートを開かずに、単体テストと Compose UI テストを実行します |
| `terminalHardwareTest` | 仮想ポートの第 1 ペアで、シリアルターミナルの実際の双方向通信、エスケープ解析、ループ送信を検証します |
| `simulatorHardwareTest` | 第 2・第 3 ペアで、ASCII 区切り文字フレームと HEX 固定長フレームの自動応答を検証します |
| `gatewayHardwareTest` | 第 1～5 ペアで、設定ルート、1 対 1、1 対多、多対多、バイナリ透過転送、非対象ポートへの漏えい防止を検証します |
| `captureHardwareTest` | 録画を検証：2 ポート透過録画と、1 ポート相手への手動モード Q&A 録画 |
| `recorderRoundTripHardwareTest` | 完全ループ：録画 → 設定エクスポート → シミュレーターで読み込み → リプレイで再現 |
| `networkSerialBridgeHardwareTest` | シリアル ⇄ TCP 混合ブリッジを検証：TCP サーバーと実シリアルポート間の双方向透過転送 |
| `testRunnerHardwareTest` | テストシーケンスの実機実行を検証：CONTAINS / HEX マッチ、失敗判定、無待機 |
| `cliGatewayHardwareTest` | ヘッドレスモードを検証：CLI ゲートウェイが TCP と実シリアルポートをブリッジ |
| `plotterHardwareTest` | プロットを検証：分割・連結チャンクでの多チャンネルサンプリング、ウィンドウ切り詰め、一時停止の挙動 |
| `allHardwareTest` | すべての実シリアルポートテストを厳密に直列実行します（仮想ポートはグローバル共有リソース） |
| `allKSerialTest` | 単体/UI テストの後に、すべての実シリアルポートテストを実行します |

低速で確認できる `*HardwareObserve` タスク（`terminalHardwareObserve`、`simulatorHardwareObserve`、`gatewayHardwareObserve` など）も用意されています。各書き込みの前後で一時停止し、コンソールに `OPEN`、`SEND`、`WRITE RESULT`、`RECEIVED` の軌跡を出力するため、外部のシリアル監視ツールでも通信を確認できます。

実シリアルポートテストでは、既定で接続済みの 10 ペア `COM1↔COM2`、`COM3↔COM4`、…、`COM19↔COM20` を使用します。各ペアの先頭は外部テスト側、後方は KSerial が開くポートです。異なる構成を使う場合は、`KSERIAL_PORT_PAIRS` 環境変数を `portA:portB,portC:portD` 形式で指定します。

```powershell
$env:KSERIAL_PORT_PAIRS = "COM21:COM22,COM23:COM24"
.\gradlew.bat :composeApp:allKSerialTest
```

通常の `jvmTest`、`coreTest`、CI では、ローカルポートを占有しないよう実シリアルポートテストを意図的にスキップします。JVM テストのカバレッジレポートは次のコマンドで生成できます。

```powershell
.\gradlew.bat :composeApp:koverHtmlReportJvm
```

レポートは `composeApp/build/reports/kover/htmlJvm/index.html` から確認できます。

## 自動リリース

アプリケーションのバージョンは `gradle.properties` の `appVersion` で管理します。バージョンと一致する `v*` タグ（例：`v1.0.0`）をプッシュすると `Build and Release` ワークフローが実行され、Windows、macOS、Linux でテストとパッケージ作成を行った後、`SHA256SUMS.txt` を含む GitHub Release を作成または更新します。

通常の `master` へのプッシュと Pull Request では、実シリアルポートに依存しない JVM 単体/UI テストのみを実行し、パッケージ作成や公開は行いません。GitHub Actions から手動実行した場合は、既定ではマルチプラットフォームのパッケージ作成だけを検証し、`publish` オプションを明示的に有効にした場合のみ Release を作成または更新します。

## 技術スタック

- Kotlin Multiplatform
- Compose Multiplatform / Material 3
- kotlinx.coroutines / Flow
- jSerialComm
- kotlinx.serialization
- Typesafe Config
- Gradle / jpackage


## ライセンス

KSerial は [Apache License 2.0](LICENSE) の下で公開されています。

KSerial が役に立ったら、GitHub で ⭐ を付けていただけるとうれしいです。その応援が今後の改善につながります。
