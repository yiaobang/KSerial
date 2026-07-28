# KSerial 設定サンプル

[简体中文](README.md) · [English](README.en.md) · **日本語**

このディレクトリの `.conf` ファイルは、基本から高度な順に並んでいます。目的に近いサンプルをコピーし、ポート名、通信条件、ルールを変更してください。設定の読み込みでは検証と準備だけを行い、画面で「実行」を押した後にシリアルポートを開きます。

## `.conf` でコメントは使えますか？

使えます。KSerial は Typesafe Config を通じて [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset) を読み込みます。`#` と `//` のどちらも、行全体または行末のコメントとして使用できます。

```hocon
# 行全体のコメント
port = "COM2" // 行末のコメント
baudRate = 115200 # この形式も使用可能
```

実際の値に `#`、`//`、空白、エスケープシーケンスが含まれる場合は、コメントや複数のトークンとして解釈されないようにダブルクォートで囲んでください。設定の読み込みで使用できるのは HOCON の `.conf` / `.hocon` ファイルのみです。

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
| `simulator.endpoint` | はい | この模擬で使用する完全なシリアル設定。ページに残った古い選択値は使用しません。 |
| `simulator.encode` | いいえ | `HEX` は 16 進バイトです。`ASCII`、`TEXT`、`UTF8`、`UTF-8` は UTF-8 テキストとして扱います。既定値は `UTF8` です。 |
| `simulator.delimiter` | どちらか一方のみ | 区切り文字によるフレーム分割。テキストモードでは通常の文字列、HEX モードでは HEX バイトを指定します。 |
| `simulator.packSize` | どちらか一方のみ | 1～4096 バイトの固定フレーム長。`delimiter` と同時には指定できません。 |
| `simulator.rules` | はい | 1 件以上の `request` / `response` ルール。`request` は重複しないようにしてください。 |
| `simulator.rules[].request` | はい | 1 フレームを受信した後に照合するリクエスト内容。 |
| `simulator.rules[].response` | はい | リクエスト一致時にシリアルポートへ書き込む応答内容。 |
| `simulator.highlight` | いいえ | ログのキーワード、有効状態、大文字・小文字を区別するかどうかの設定。 |

`delimiter` と `packSize` は必ずどちらか一方だけを指定します。`delimiter` を使用する場合、各 `request` には `request = "AT\r\n"` のように末尾の区切り文字を含める必要があります。`packSize` を使用する場合、デコード後の各リクエストは指定したバイト数と完全に一致する必要があります。

## シリアルブリッジのフィールド

| フィールド | 必須 | 説明 |
| --- | --- | --- |
| `gateway` | いいえ | 推奨されるルートオブジェクト。内部のフィールドをファイル直下に置くこともできます。 |
| `gateway.endpoints` | はい | 2 つ以上のシリアル端点。同じ物理ポートを重複して指定できません。 |
| `gateway.endpoints[].id` | はい | ルートから参照する一意の端点 ID。 |
| `gateway.endpoints[].name` | いいえ | ログに表示する端点名。既定値は `id` です。 |
| `gateway.routes` | はい | 空ではないルート一覧。1 件以上を有効にする必要があります。 |
| `gateway.routes[].name` | いいえ | 表示名。既定では `route1` などの名前を生成します。 |
| `gateway.routes[].sources` | はい | ルートを開始できる端点 ID の空ではないリスト。 |
| `gateway.routes[].targets` | はい | データの転送先となる端点 ID の空ではないリスト。 |
| `gateway.routes[].enabled` | いいえ | ルートを有効にする設定。既定値は `true` です。 |
| `gateway.highlight` | いいえ | ブリッジログのキーワード強調設定。 |

1 つの端点を複数のルートで使用でき、1 つの送信元から複数の転送先を指定できます。`sources` と `targets` は、すべて `endpoints` で宣言された ID を参照する必要があります。読み込みに失敗した場合は、古いルートを誤って実行しないよう以前のブリッジ設定を消去します。

## 端点の通信設定

デバイス模擬の `endpoint` とシリアルブリッジの `endpoints[]` は、次の通信フィールドを共有します。

| フィールド | 必須 | 既定値または指定可能な値 | 説明 |
| --- | --- | --- | --- |
| `port` | はい | 既定値なし | `COM3`、`/dev/ttyUSB0` などのシステム上のポート名。 |
| `baudRate` | はい | 0 より大きい整数 | `9600`、`115200` などのボーレート。 |
| `dataBits` | いいえ | 既定値 `8`、範囲 `5`～`8` | データビット数。 |
| `stopBits` | いいえ | 既定値 `1`、選択肢 `1`、`1.5`、`2` | ストップビット数。 |
| `parity` | いいえ | 既定値 `none` | `none`、`odd`、`even`、`mark`、`space`。 |
| `flowControl` | いいえ | 既定値 `none` | `none`、`rts_cts`、`dsr_dtr`、`xon_xoff`。`/` を含む形式も使用できます。 |

`autoOpen` は記述しないでください。設定の読み込みは検証と準備だけを行い、「実行」をクリックした後にシリアルポートを開きます。

## ログ強調フィールド

デバイス模擬では `simulator.highlight`、シリアルブリッジでは `gateway.highlight` を使用します。内部のフィールドは共通です。

| フィールド | 必須 | 既定値 | 説明 |
| --- | --- | --- | --- |
| `highlight.enabled` | いいえ | キーワード指定時は `true` | キーワード強調を有効にします。 |
| `highlight.ignoreCase` | いいえ | `true` | 照合時に大文字・小文字を区別しません。 |
| `highlight.keywords` | いいえ | 空のリスト | 強調表示するテキストキーワードの一覧。 |
