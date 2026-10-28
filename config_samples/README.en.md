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
| 7 | [07-bridge-many-to-many.conf](07-bridge-many-to-many.conf) | Many-to-many bridge: 5 endpoint pairs forwarding among each other. |
| 8 | [08-simulator-device.conf](08-simulator-device.conf) | Device-replica simulator: full behavior from recorded request/response pairs, with fixed and randomized reply delays. |
| 9 | [09-test-sequence.conf](09-test-sequence.conf) | Test sequence: declarative steps (send/expect/match mode/timeout). |
| 10 | [10-network-endpoints.conf](10-network-endpoints.conf) | Network endpoints: TCP client/server and UDP address syntax, mixable with physical ports. |

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
| `simulator.rules[].delay` | No | Reply delay for this rule; overrides the global `responseDelay`. |
| `simulator.responseDelay` | No | Global reply delay. Defaults to `0` (reply immediately). |
| `simulator.highlight` | No | Log keywords, enabled state, and case-sensitivity settings. |

Exactly one of `delimiter` and `packSize` must be present. With `delimiter`, every rule's `request` must include the trailing delimiter, such as `request = "AT\r\n"`. With `packSize`, each decoded request must contain exactly the configured number of bytes.

## Reply delay syntax

`responseDelay` (global) and `rules[].delay` (per-rule override) accept the same two forms:

| Value | Behavior |
| --- | --- |
| `delay = 300` or `delay = "300"` | Replies after a fixed 300 ms. |
| `delay = "100~300"` or `delay = "100-300"` | Each reply waits a **uniformly random** 100–300 ms, reproducing the timing jitter of a real device. |

- The unit is milliseconds and the range is `0`–`600000`; both `~` and `-` work as range separators, and surrounding spaces are optional.
- Precedence: per-rule `delay` > global `responseDelay` > default `0`. Rules without a delay inherit the global value.
- Randomized ranges are not just cosmetic: set the range slightly above your host software's timeout threshold to verify that it retries correctly and does not process the same reply twice. A fixed delay never exercises that boundary.
- Multiple delayed replies are queued and written **FIFO**, matching a real device that handles one request at a time.
- Invalid values never fail silently: a bad `responseDelay` falls back to `0` with a warning, and a bad per-rule `delay` falls back to the global value with a warning. Both appear in the import issue list.

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

## Test sequence fields

| Field | Required | Default or accepted values | Description |
| --- | --- | --- | --- |
| `test` | No | Recommended root object | Its children may also be placed directly at the file root. |
| `test.connection` | Yes to run | No default | Connection settings using the same fields as "Endpoint communication settings". |
| `test.connection.port` | Yes | No default | Serial port name or network endpoint address. |
| `test.connection.baudRate` | No | Default: `115200` | Baud rate. |
| `test.defaults.timeout` | No | Default: `1000`; range `1`–`600000` | Wait limit used when a step omits `timeout`, in milliseconds. |
| `test.defaults.interval` | No | Default: `100`; range `0`–`10000` | Pre-send wait used when a step omits `delay`, in milliseconds. |
| `test.steps` | Yes | `1`–`500` steps | Ordered steps to execute. |
| `test.steps[].name` | No | Default: `Step N` | Step name shown in reports. |
| `test.steps[].send` | No | No default | Bytes to send; empty means this step only waits. |
| `test.steps[].expect` | No | No default | Expected content; empty means send-only with no assertion (unless `match = "silence"`). |
| `test.steps[].match` | No | Default: `contains` | `contains`, `exact`, `regex`, or `silence`. |
| `test.steps[].timeout` | No | Inherits `defaults.timeout` | Wait limit for this step. |
| `test.steps[].delay` | No | Inherits `defaults.interval` | Pre-send wait for this step. |
| `test.steps[].encoding` | No | Default: `ascii` | `ascii` or `hex`; controls both how `send` is parsed and how `expect` is compared. |

Match semantics:

- `contains` is a substring match, `exact` compares trimmed strings, `regex` uses a regex search, and `silence` requires **zero bytes** for the whole timeout window.
- Matching polls during the timeout window: as soon as received data matches, the step passes without waiting for the full `timeout`; a failure is reported only after the timeout elapses.
- With `encoding = "hex"`, `send` is parsed as byte text such as `"41 54 0D 0A"` and `expect` is compared as a space-stripped, uppercased HEX string. In `ascii` mode the comparison uses UTF-8 text with leading and trailing whitespace ignored.
- Configuration errors surface at load time instead of mid-run: a step with no `send` and no `expect`/`silence`, an invalid regex, invalid HEX, an empty step list, or more than 500 steps are all rejected.

## Endpoint communication settings

Device Simulator `endpoint` and Serial Bridge `endpoints[]` share these communication fields:

| Field | Required | Default or accepted values | Description |
| --- | --- | --- | --- |
| `port` | Yes | No default | A serial port name (`COM3`, `/dev/ttyUSB0`) or a network endpoint address; see "Network endpoint syntax" below. |
| `baudRate` | Serial only | Positive integer | Baud rate, such as `9600` or `115200`; may be omitted for network endpoints. |
| `dataBits` | No | Default: `8`; range: `5`–`8` | Number of data bits. |
| `stopBits` | No | Default: `1`; accepted: `1`, `1.5`, `2` | Number of stop bits. |
| `parity` | No | Default: `none` | `none`, `odd`, `even`, `mark`, or `space`. |
| `flowControl` | No | Default: `none` | `none`, `rts_cts`, `dsr_dtr`, or `xon_xoff`; forms containing `/` are also accepted. |

Do not add `autoOpen`. Importing only validates and prepares a configuration; serial ports open only after Run is clicked.

## Network endpoint syntax

Besides a serial port name, `port` accepts the following network addresses (case-insensitive):

| Syntax | Behavior |
| --- | --- |
| `tcp://192.168.1.20:8080` | TCP client: connect to a remote serial server (ser2net, ESP-Link, USR-TCP232, etc.) |
| `tcp://:9000` / `tcp-listen://9000` | TCP server: listen locally for an incoming peer (one client at a time; a newer connection replaces the older one) |
| `udp://192.168.1.20:9000` | UDP: exchange datagrams with a fixed peer (common for serial-to-UDP modules) |
| `udp-listen://9000` | Listen on a UDP port; replies go to the peer address of the first received datagram |

Network endpoints and physical serial ports can be mixed freely in one configuration; routes reference them by endpoint ID as usual. Notes:

- Network endpoints have no baud-rate concept, so `baudRate`, `parity`, and other serial parameters may be omitted entirely; parameters for a physical serial port are set in that endpoint's own fields.
- DTR / RTS / BREAK line control is a local electrical signal and does **not** traverse the network; data transfer is unaffected.
- Each listening port accepts a single client connection at a time; `udp-listen` replies to the peer address of the first received datagram.

See [10-network-endpoints.conf](10-network-endpoints.conf) for a complete bidirectional example.

## Log highlighting fields

Device Simulator uses `simulator.highlight`, while Serial Bridge uses `gateway.highlight`. Both contain the same fields:

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `highlight.enabled` | No | `true` when keywords are present | Enables keyword highlighting. |
| `highlight.ignoreCase` | No | `true` | Ignores letter case while matching. |
| `highlight.keywords` | No | Empty list | Text keywords to highlight. |
