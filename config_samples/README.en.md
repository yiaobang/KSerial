# KSerial configuration examples

[简体中文](README.md) · **English** · [日本語](README.ja.md)

The `.conf` files in this directory are ordered from basic to advanced. Start by copying the closest example, then change its port names, communication parameters, and rules. Loading a configuration only validates and prepares it; serial ports open only after you click **Run**.

## Do `.conf` files support comments?

Yes. KSerial reads [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset) through Typesafe Config. Both `#` and `//` work for full-line and end-of-line comments:

```hocon
# This is a full-line comment.
port = "COM2" // This is an end-of-line comment.
baudRate = 115200 # This form works as well.
```

Quote actual values that contain `#`, `//`, spaces, or escape sequences so they are not interpreted as comments or split into tokens. Configuration import accepts HOCON `.conf` / `.hocon` files only.

## Learning path

| Level | Example | What it demonstrates |
| --- | --- | --- |
| 1 | [01-simulator-minimal.conf](01-simulator-minimal.conf) | Minimal text Device Simulator with one endpoint, one rule, and newline framing. |
| 2 | [02-simulator-text.conf](02-simulator-text.conf) | Complete text simulation with serial parameters, multiple AT rules, and keyword highlighting. |
| 3 | [03-simulator-hex-fixed-length.conf](03-simulator-hex-fixed-length.conf) | HEX simulation with fixed-length frames and byte responses. |
| 4 | [04-bridge-one-way.conf](04-bridge-one-way.conf) | Minimal one-way Serial Bridge with two endpoints and one route. |
| 5 | [05-bridge-bidirectional.conf](05-bridge-bidirectional.conf) | Bidirectional bridge with full endpoint settings, reverse routes, and log highlighting. |
| 6 | [06-bridge-multi-route.conf](06-bridge-multi-route.conf) | Multi-endpoint bridge with fan-out, return, and disabled routes. |

## Device Simulator fields

| Field | Required | Description |
| --- | --- | --- |
| `simulator` | No | Recommended root object. Its children may also be placed directly at the file root. |
| `simulator.endpoint` | Yes | Complete serial settings for this simulation; stale page selections are never used. |
| `simulator.encode` | No | `HEX` selects hexadecimal bytes. `ASCII`, `TEXT`, `UTF8`, and `UTF-8` use UTF-8 text. Default: `UTF8`. |
| `simulator.delimiter` | Exactly one | Delimiter framing. Use a normal string in text mode or HEX bytes in HEX mode. |
| `simulator.packSize` | Exactly one | Fixed frame size from 1 to 4096 bytes. It cannot be combined with `delimiter`. |
| `simulator.rules` | Yes | At least one `request` / `response` rule. Request values should be unique. |
| `simulator.rules[].request` | Yes | Request content matched after a complete frame is received. |
| `simulator.rules[].response` | Yes | Response bytes written after the request matches. |
| `simulator.highlight` | No | Log keywords, enabled state, and case-sensitivity settings. |

Exactly one of `delimiter` and `packSize` must be present. With `delimiter`, every rule's `request` must include the trailing delimiter, such as `request = "AT\r\n"`. With `packSize`, each decoded request must contain exactly the configured number of bytes.

## Serial Bridge fields

| Field | Required | Description |
| --- | --- | --- |
| `gateway` | No | Recommended root object. Its children may also be placed directly at the file root. |
| `gateway.endpoints` | Yes | At least two serial endpoints. A physical port may appear only once. |
| `gateway.endpoints[].id` | Yes | Unique endpoint ID referenced by routes. |
| `gateway.endpoints[].name` | No | Endpoint name shown in logs. Defaults to `id`. |
| `gateway.routes` | Yes | A non-empty route list with at least one enabled route. |
| `gateway.routes[].name` | No | Display name. Defaults to generated names such as `route1`. |
| `gateway.routes[].sources` | Yes | Non-empty list of endpoint IDs allowed to trigger the route. |
| `gateway.routes[].targets` | Yes | Non-empty list of endpoint IDs that receive forwarded data. |
| `gateway.routes[].enabled` | No | Enables the route; defaults to `true`. |
| `gateway.highlight` | No | Keyword highlighting for bridge logs. |

An endpoint may participate in multiple routes, and a source may forward to multiple targets. Every value in `sources` and `targets` must reference an ID declared in `endpoints`. A failed import clears the previous bridge configuration so stale routes cannot be run accidentally.

## Endpoint communication settings

Device Simulator `endpoint` and Serial Bridge `endpoints[]` share these communication fields:

| Field | Required | Default or accepted values | Description |
| --- | --- | --- | --- |
| `port` | Yes | No default | System port name, such as `COM3` or `/dev/ttyUSB0`. |
| `baudRate` | Yes | Positive integer | Baud rate, such as `9600` or `115200`. |
| `dataBits` | No | Default: `8`; range: `5`–`8` | Number of data bits. |
| `stopBits` | No | Default: `1`; accepted: `1`, `1.5`, `2` | Number of stop bits. |
| `parity` | No | Default: `none` | `none`, `odd`, `even`, `mark`, or `space`. |
| `flowControl` | No | Default: `none` | `none`, `rts_cts`, `dsr_dtr`, or `xon_xoff`; forms containing `/` are also accepted. |

Do not add `autoOpen`. Importing only validates and prepares a configuration; serial ports open only after Run is clicked.

## Log highlighting fields

Device Simulator uses `simulator.highlight`, while Serial Bridge uses `gateway.highlight`. Both contain the same fields:

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `highlight.enabled` | No | `true` when keywords are present | Enables keyword highlighting. |
| `highlight.ignoreCase` | No | `true` | Ignores letter case while matching. |
| `highlight.keywords` | No | Empty list | Text keywords to highlight. |
