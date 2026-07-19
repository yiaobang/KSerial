# KSerial configuration examples

[简体中文](README.md) · **English** · [日本語](README.ja.md)

The `.conf` files in this directory are ordered from basic to advanced. Start by copying the closest example, then change its port names, communication parameters, and rules. Do not enable `autoOpen` before verifying the target device and port.

## Do `.conf` files support comments?

Yes. KSerial reads [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset) through Typesafe Config. Both `#` and `//` work for full-line and end-of-line comments:

```hocon
# This is a full-line comment.
port = "COM2" // This is an end-of-line comment.
baudRate = 115200 # This form works as well.
```

Quote actual values that contain `#`, `//`, spaces, or escape sequences so they are not interpreted as comments or split into tokens. This comment syntax applies to HOCON `.conf` / `.hocon` files; legacy JSON configuration does not support comments.

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
| `endpoint` | No | Serial connection settings. If omitted, the current page settings remain active and a warning is shown. |
| `encode` | No | `HEX` selects hexadecimal bytes; every other value is handled as UTF-8 text. Text is the default. |
| `delimiter` | One of two | Delimiter framing. Use a normal string in text mode or HEX bytes in HEX mode. |
| `packSize` | One of two | Fixed frame size from 1 to 4096 bytes. A valid value takes precedence over `delimiter`. |
| `rules` | Yes | A list of `request` / `response` pairs. Request values should be unique. |
| `highlight` | No | Log keywords, enabled state, and case-sensitivity settings. |

With `delimiter`, every rule's `request` must include the trailing delimiter, such as `request = "AT\r\n"`. With `packSize`, each decoded request must contain exactly the configured number of bytes.

## Serial Bridge fields

| Field | Required | Description |
| --- | --- | --- |
| `gateway.endpoints` | Yes | Serial endpoints. Every `id` must be unique, and a physical port should appear only once. |
| `gateway.routes` | No | Explicit forwarding routes. When routes exist, enabled routes determine forwarding. |
| `route.sources` | Yes | Endpoint IDs allowed to trigger the route. |
| `route.targets` | Yes | Endpoint IDs that receive forwarded data. |
| `route.enabled` | No | Enables the route; defaults to `true`. |
| `gateway.highlight` | No | Keyword highlighting for bridge logs. |

An endpoint may participate in multiple routes, and a source may forward to multiple targets. Every value in `sources` and `targets` must reference an ID declared in `endpoints`.

## Endpoint communication settings

Device Simulator `endpoint` and Serial Bridge `endpoints` share these fields:

| Field | Default or accepted values |
| --- | --- |
| `port` | System port name, such as `COM3` or `/dev/ttyUSB0` |
| `baudRate` | Default: `9600` |
| `dataBits` | Default: `8` |
| `stopBits` | `1`, `1.5`, `2` |
| `parity` | `none`, `odd`, `even`, `mark`, `space` |
| `flowControl` | `none`, `rts_cts`, `dsr_dtr`, `xon_xoff`; forms containing `/` are also accepted |
| `autoOpen` | Default: `false`; whether to open the port immediately after loading |

Keep `autoOpen = false` when trying an example for the first time. Enable it only after confirming the port and communication parameters.
