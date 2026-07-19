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

- シリアルポートを自動検出し、ボーレート、データビット、ストップビット、パリティ、フロー制御を設定。
- テキストまたは HEX の送受信、HEX のリアルタイム検証と整形、繰り返し送信、クイック送信に対応。
- 履歴のキーボード操作、CRC16-Modbus・XOR・SUM8・LRC の計算、汎用フレーム確認に対応。
- TX/RX カウンター、ログ検索と種類別フィルター、キーワード強調、コピー、TXT/CSV 出力、通信データの保存を提供。
- JSON または HOCON `.conf` で応答ルールを定義し、固定長および区切り文字ベースのフレーム分割に対応。
- 複数のシリアル端点を接続し、1 対 1、1 対多、多対多の転送ルートを設定可能。
- 複数タブ、タブの別ウィンドウ化と再統合に対応し、同じポートの重複使用を防止。
- 簡体字中国語、英語、日本語、およびライト・ダークテーマを内蔵。
- GitHub Actions により Windows、macOS、Linux 向けのインストーラーとポータブル版を自動生成。

## 3 つのワークスペース

| ワークスペース | 用途 |
| --- | --- |
| **シリアル端末** | 通常のシリアルデバッグ。機器への接続、テキストまたは HEX の送受信、繰り返し・クイック送信、ログの絞り込み、TX/RX データの保存を行います。 |
| **デバイス模擬** | 設定に従って受信フレームを照合し、自動応答します。シリアル機器の模擬、ホストソフトウェアの検証、反復的なプロトコル試験に使用できます。 |
| **シリアルブリッジ** | 複数のシリアル端点と転送ルートを管理し、透過転送、中継、複数機器の結合試験に使用します。 |

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
3. ポートを開き、画面下部の入力欄からテキストまたは HEX データを送信します。
4. 必要に応じて繰り返し送信、クイック送信、キーワード強調、ログフィルター、TX/RX 保存を有効にします。

機器を模擬する場合は **デバイス模擬** でルール設定を読み込み、ポート間でデータを転送する場合は **シリアルブリッジ** で端点とルートの設定を読み込みます。

初回起動時に、KSerial はユーザー設定ディレクトリへコメント可能な HOCON `settings.conf` を作成します。言語、テーマ、最前面表示、シリアル条件、表示・フィルター設定、クイック送信、送信履歴を復元します。送信入力欄、繰り返し送信の有効状態と間隔は保存せず、シリアルポートも自動では開きません。

| OS | グローバル設定の保存先 |
| --- | --- |
| Windows | `%APPDATA%\KSerial\settings.conf` |
| macOS | `~/Library/Application Support/KSerial/settings.conf` |
| Linux | `$XDG_CONFIG_HOME/kserial/settings.conf`、未設定時は `~/.config/kserial/settings.conf` |

## 設定によるデバイス模擬とブリッジ

リポジトリには、最小構成のデバイス模擬から複数端点のルーティングまで、段階的に学べる HOCON サンプルがあります。学習順序、フィールド一覧、コメント構文については [設定サンプルガイド](config_samples/README.ja.md) を参照してください。

- [最小のデバイス模擬](config_samples/01-simulator-minimal.conf)
- [テキスト用デバイス模擬](config_samples/02-simulator-text.conf)
- [固定長 HEX デバイス模擬](config_samples/03-simulator-hex-fixed-length.conf)
- [一方向シリアルブリッジ](config_samples/04-bridge-one-way.conf)
- [双方向シリアルブリッジ](config_samples/05-bridge-bidirectional.conf)
- [複数端点・複数ルートのシリアルブリッジ](config_samples/06-bridge-multi-route.conf)

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
    autoOpen = false
  }

  encode = "ASCII"
  delimiter = "\r\n"

  rules = [
    { request = "AT\r\n", response = "OK\r\n" }
  ]
}
```

バイト単位のルールには `encode = "HEX"` を使用します。`packSize` は固定長フレーム、`delimiter` は区切り文字フレームに使用し、少なくとも一方が必要です。設定の読み込み時には、不正な HEX、空の区切り文字、ポートの重複、無効なルート参照などを検出します。

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

## 自動リリース

アプリケーションのバージョンは `gradle.properties` の `appVersion` で管理します。バージョンと一致する `v*` タグ（例：`v1.0.0`）をプッシュすると `Build and Release` ワークフローが実行され、Windows、macOS、Linux でテストとパッケージ作成を行った後、`SHA256SUMS.txt` を含む GitHub Release を作成または更新します。

通常の `master` へのプッシュと Pull Request では JVM テストのみを実行し、パッケージ作成や公開は行いません。GitHub Actions から手動実行した場合は、既定ではマルチプラットフォームのパッケージ作成だけを検証し、`publish` オプションを明示的に有効にした場合のみ Release を作成または更新します。

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
