# KSerial 設定サンプル

[简体中文](README.md) · [English](README.en.md) · **日本語**

このディレクトリの `.conf` ファイルは、基本から高度な順に並んでいます。目的に近いサンプルをコピーし、ポート名、通信条件、ルールを変更してください。対象機器とポートを確認する前に `autoOpen` を有効にしないでください。

## `.conf` でコメントは使えますか？

使えます。KSerial は Typesafe Config を通じて [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset) を読み込みます。`#` と `//` のどちらも、行全体または行末のコメントとして使用できます。

```hocon
# 行全体のコメント
port = "COM2" // 行末のコメント
baudRate = 115200 # この形式も使用可能
```

実際の値に `#`、`//`、空白、エスケープシーケンスが含まれる場合は、コメントや複数のトークンとして解釈されないようにダブルクォートで囲んでください。このコメント構文は HOCON の `.conf` / `.hocon` ファイルで使用できます。従来の JSON 設定ではコメントを使用できません。

## 学習順序

| レベル | サンプル | 内容 |
| --- | --- | --- |
| 1 | [01-simulator-minimal.conf](01-simulator-minimal.conf) | 1 つの端点、1 つのルール、改行区切りによる最小のテキスト用デバイス模擬。 |
| 2 | [02-simulator-text.conf](02-simulator-text.conf) | 通信条件、複数の AT ルール、キーワード強調を含むテキスト用デバイス模擬。 |
| 3 | [03-simulator-hex-fixed-length.conf](03-simulator-hex-fixed-length.conf) | 固定長フレームとバイト応答を使用する HEX デバイス模擬。 |
| 4 | [04-bridge-one-way.conf](04-bridge-one-way.conf) | 2 つの端点と 1 つのルートによる最小の一方向シリアルブリッジ。 |
| 5 | [05-bridge-bidirectional.conf](05-bridge-bidirectional.conf) | 完全な端点設定、逆方向ルート、ログ強調を含む双方向ブリッジ。 |
| 6 | [06-bridge-multi-route.conf](06-bridge-multi-route.conf) | 1 対多、戻り方向、無効化ルートを含む複数端点ブリッジ。 |

## デバイス模擬のフィールド

| フィールド | 必須 | 説明 |
| --- | --- | --- |
| `simulator` | いいえ | 推奨されるルートオブジェクト。内部のフィールドをファイル直下に置くこともできます。 |
| `endpoint` | いいえ | シリアル接続設定。省略すると現在のページ設定を維持し、警告を表示します。 |
| `encode` | いいえ | `HEX` は 16 進バイト、それ以外は UTF-8 テキストとして扱います。既定はテキストです。 |
| `delimiter` | どちらか一方 | 区切り文字によるフレーム分割。テキストモードでは通常の文字列、HEX モードでは HEX バイトを指定します。 |
| `packSize` | どちらか一方 | 1～4096 バイトの固定フレーム長。有効な場合は `delimiter` より優先されます。 |
| `rules` | はい | `request` / `response` のリスト。`request` は重複しないようにしてください。 |
| `highlight` | いいえ | ログのキーワード、有効状態、大文字・小文字を区別するかどうかの設定。 |

`delimiter` を使用する場合、各 `request` には `request = "AT\r\n"` のように末尾の区切り文字を含める必要があります。`packSize` を使用する場合、デコード後の各リクエストは指定したバイト数と完全に一致する必要があります。

## シリアルブリッジのフィールド

| フィールド | 必須 | 説明 |
| --- | --- | --- |
| `gateway.endpoints` | はい | シリアル端点のリスト。各 `id` は一意で、同じ物理ポートを重複して指定しないでください。 |
| `gateway.routes` | いいえ | 明示的な転送ルート。ルートが存在する場合、有効なルートによって転送先が決まります。 |
| `route.sources` | はい | ルートを開始できる端点 ID のリスト。 |
| `route.targets` | はい | データの転送先となる端点 ID のリスト。 |
| `route.enabled` | いいえ | ルートを有効にする設定。既定値は `true` です。 |
| `gateway.highlight` | いいえ | ブリッジログのキーワード強調設定。 |

1 つの端点を複数のルートで使用でき、1 つの送信元から複数の転送先を指定できます。`sources` と `targets` は、すべて `endpoints` で宣言された ID を参照する必要があります。

## 端点の通信設定

デバイス模擬の `endpoint` とシリアルブリッジの `endpoints` は、次のフィールドを共有します。

| フィールド | 既定値または指定可能な値 |
| --- | --- |
| `port` | `COM3`、`/dev/ttyUSB0` などのシステム上のポート名 |
| `baudRate` | 既定値：`9600` |
| `dataBits` | 既定値：`8` |
| `stopBits` | `1`、`1.5`、`2` |
| `parity` | `none`、`odd`、`even`、`mark`、`space` |
| `flowControl` | `none`、`rts_cts`、`dsr_dtr`、`xon_xoff`。`/` を含む形式も使用可能 |
| `autoOpen` | 既定値：`false`。設定読込後にポートを直ちに開くかどうか |

初めてサンプルを試す場合は `autoOpen = false` のままにし、ポートと通信条件を確認してから有効にしてください。
