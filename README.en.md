<p align="center">
  <img src="composeApp/src/jvmMain/composeResources/drawable/app_icon.png" width="128" alt="KSerial icon" />
</p>

<h1 align="center">KSerial</h1>

<p align="center">A cross-platform desktop serial workbench combining a serial console, device simulator, and serial bridge.</p>

<p align="center">
  <a href="README.md">简体中文</a> · <strong>English</strong> · <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <a href="https://github.com/yiaobang/KSerial/actions/workflows/release.yml"><img src="https://github.com/yiaobang/KSerial/actions/workflows/release.yml/badge.svg" alt="Build and Release" /></a>
  <a href="https://github.com/yiaobang/KSerial/releases/latest"><img src="https://img.shields.io/github/v/release/yiaobang/KSerial?display_name=tag&amp;sort=semver" alt="Latest release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/yiaobang/KSerial" alt="Apache-2.0 license" /></a>
  <img src="https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-4c8bf5" alt="Windows, macOS, and Linux" />
</p>

KSerial is built with Kotlin and Compose Multiplatform for embedded development, hardware bring-up, protocol verification, and serial device testing. Alongside everyday serial communication, it brings automatic replies, device behavior simulation, and multi-port routing into one tabbed workspace.

## Highlights

- Discover serial ports with friendly device names, refresh them manually, and configure baud rate, data bits, stop bits, parity, and flow control.
- Send and receive text or HEX data with live HEX validation, optional line endings, loop sending, quick commands, and `Ctrl+Enter` / `Enter` shortcuts.
- Display incoming data by time window, raw callback chunk, delimiter, or fixed length, with bounded buffering and visible drop counts under heavy traffic.
- Track explicit connection states, detect unexpected disconnects, optionally reconnect, control DTR/RTS/Break, and inspect CTS/DSR/DCD/RI signals.
- Reuse bounded send history with keyboard navigation, and calculate CRC16-Modbus, XOR, SUM8, or LRC checksums.
- Inspect TX/RX counters, search and filter logs, highlight keywords, copy entries, export TXT/CSV, and persist traffic to disk.
- Describe device response rules in HOCON `.conf` / `.hocon` files, with fixed-length and delimiter-based framing; reply delays can be a fixed millisecond value or a **randomized range** such as `"100~300"` to reproduce real-device jitter.
- Connect multiple serial endpoints with configurable one-to-one, one-to-many, and many-to-many routes.
- **Network endpoints**: any workspace accepts `tcp://host:port`, `tcp-listen://port`, `udp://host:port`, and `udp-listen://port` addresses alongside physical serial ports.
- **Serial recorder**: a dedicated 1-to-1 transparent workspace that forwards between a vendor software and the real device while recording request/response pairs, exportable straight to the device simulator config. When there is no vendor software and the device exposes a single port, switch to **manual mode** and drive the device from the built-in input box — the pairs are recorded the same way.
- **Test sequences**: declarative HOCON step scripts (send/expect with CONTAINS/EXACT/REGEX/SILENCE matching, ASCII/HEX encodings) with pass/fail reports.
- **Serial plotter**: live line chart of numeric streams (multiple channels per line, CSV-friendly).
- **Headless mode**: the same executable can run bridge, simulation, or test sequences from a terminal when started with arguments, ready for scripts and CI. See [Headless mode (CLI)](#headless-mode-cli).
- Work with multiple tabs, detach tabs into separate windows, merge them back, and prevent duplicate port ownership.
- Switch between Simplified Chinese, English, and Japanese, with light and dark themes.
- Download installers and portable distributions for Windows, macOS, and Linux, built by GitHub Actions.

## Six workspaces

| Workspace | Purpose |
| --- | --- |
| **Serial Console** | Everyday serial debugging: connect devices, exchange text or HEX data, loop or quick-send commands, filter logs, and save TX/RX traffic. |
| **Device Simulator** | Match incoming frames and reply from configuration, useful for simulating devices, testing host software, and repeating protocol scenarios. |
| **Serial Bridge** | Manage multiple endpoints and forwarding routes; endpoints can be physical serial ports or TCP/UDP addresses, for pass-through, relaying, remote serial access, and multi-device integration tests. |
| **Capture** | Forward bytes between two endpoints while recording command/response pairs, then export a simulator configuration; network endpoints and a manual mode are also supported. |
| **Test Sequence** | Send, wait, and assert step by step from a configuration file, producing a PASS/FAIL report for protocol regressions. |
| **Plotter** | Turn numeric text streams into multi-channel live curves for sensors and control-loop tuning. |

Each workspace supports multiple independent tabs. A tab can be detached into its own window and merged back into the main window later.

## Download

Download the latest build for your operating system from [GitHub Releases](https://github.com/yiaobang/KSerial/releases/latest):

| Platform | Installers | Portable distribution |
| --- | --- | --- |
| Windows | `.msi`, `.exe` | `.zip` |
| macOS | `.dmg`, `.pkg` | `.zip` |
| Linux | `.deb`, `.rpm` | `.tar.gz` |

Release packages include the application runtime, so end users normally do not need to install a JDK. Windows code signing and macOS signing/notarization are not configured yet, so the operating system may show a security warning on first launch.

## Quick start

1. Open **Serial Console** and select `+` to create a tab.
2. Choose a port and set its baud rate and other parameters under “Connection & Settings.”
3. Open the port and send text or HEX data from the input area at the bottom. Before sending, text mode fully decodes `\\`, `\'`, `\"`, `\?`, `\a`, `\b`, `\e`, `\f`, `\n`, `\r`, `\s`, `\t`, `\v`, octal `\ooo`, raw-byte `\xHH`, Unicode `\uHHHH` / `\UHHHHHHHH`, and control-character `\cX` escapes.
4. Press `Ctrl+Enter` to send, or enable Enter-to-send (`Shift+Enter` inserts a line break), and select LF, CR, or CRLF when the protocol needs a terminator.
5. Enable automatic reconnect, receive framing, loop sending, quick commands, or TX/RX persistence as needed. Search, keyword highlighting, and log filters live in the log header: the search box filters inline (`Ctrl+F` focuses it), and the two icons open the highlight and filter options.

To emulate a device, load a rule file in **Device Simulator**. To forward data across ports, load endpoint and route configuration in **Serial Bridge**.

### Serial ⇄ Network (TCP / UDP)

**Terminal**, **Plotter**, and **Recorder** all expose an address box, as does Serial Bridge endpoint configuration. Besides a serial port name they accept the following network forms (case-insensitive); each address box also has a *Detected ports* drop-down for picking a system port:

| Syntax | Behavior |
| --- | --- |
| `COM3` / `/dev/ttyUSB0` | Physical serial port (default) |
| `tcp://192.168.1.20:8080` | TCP client: connect to a remote serial server (ser2net, ESP-Link, USR-TCP232, etc.) |
| `tcp://:9000` / `tcp-listen://9000` | TCP server: listen locally for an incoming peer (one client at a time; a newer connection replaces the older one) |
| `udp://192.168.1.20:9000` | UDP: exchange datagrams with a fixed peer |
| `udp-listen://9000` | Listen on a UDP port; replies go to the peer address of the first received datagram |

**Typical scenario: operate computer B's serial port from computer A.**

1. Computer B (where the port lives): open **Serial Bridge**, load a configuration whose endpoints are its local port (e.g. `COM3`, baud rate set here) and `tcp-listen://9000`, with routes in both directions;
2. Computer A: type `tcp://<B's IP>:9000` into the address box of **Terminal** or **Plotter** (Recorder works the same way). Once open, sending, HEX, loop sending, recording, and plotting work exactly as with a local port;
3. Computer B can also run the same bridge configuration headlessly in the background — handy for a long-running "serial server".

**Notes:**

- Network endpoints have no baud-rate concept; serial parameters (baud rate, parity, …) are set on the side that owns the physical port.
- DTR / RTS / BREAK line control is a local electrical signal and does **not** traverse the network; data transfer is unaffected.
- Each listening port accepts a single client connection at a time.
- `udp-listen` replies to the peer address of the first received datagram; if the peer's address changes, a new datagram must arrive before replies follow it again.

For HOCON syntax and a complete example, see [`config_samples/10-network-endpoints.conf`](config_samples/10-network-endpoints.conf).

### Runtime directory and `settings.conf`

On first launch, KSerial uses `System.getProperty("user.dir")` as its runtime root, creates a `conf` directory, and writes:

- `settings.conf` for global settings;
- `01-simulator-minimal.conf` through `10-network-endpoints.conf` as progressive simulator, bridge, test sequence, and network endpoint examples.

Device Simulator and Serial Bridge file dialogs open this `conf` directory first. When upgrading, KSerial copies the legacy user settings file if the new directory does not yet contain `settings.conf`. Example files are created only when missing and never overwrite user edits.

KSerial writes `settings.conf` as comment-free HOCON. Manually added comments can be read, but they are removed on the next save. If KSerial is installed under a protected location such as `Program Files`, the current user must have write access to the runtime directory before `conf` can be created or updated.

| Field | Description |
| --- | --- |
| `schemaVersion` | Settings schema version; do not edit manually. |
| `appearance.darkTheme` | Whether to use the dark theme. |
| `appearance.alwaysOnTop` | Whether windows stay always on top. |
| `appearance.language` | UI language: `zh`, `en`, or `ja`. |
| `serial.selectedPort` | Last selected port; the selection is restored without opening it automatically. |
| `serial.baudRate` | Baud rate. |
| `serial.dataBits` | Data bits, from 5 through 8. |
| `serial.stopBitsIndex` | Stop-bits index: `0=1`, `1=1.5`, `2=2`. |
| `serial.parityIndex` | Parity index: `0=None`, `1=Odd`, `2=Even`, `3=Mark`, `4=Space`. |
| `serial.flowControlIndex` | Flow-control index: `0=None`, `1=RTS/CTS`, `2=DSR/DTR`, `3=XON/XOFF`. |
| `preferences.showRealTime` | Whether timestamps appear in logs. |
| `preferences.hexDisplay` | Whether received data is displayed as HEX. |
| `preferences.hexSend` | Whether send input is interpreted as HEX. |
| `preferences.receiveDisplayMode` | Receive display mode: `TIME_WINDOW`, `RAW_CHUNKS`, `DELIMITER`, or `FIXED_LENGTH`. |
| `preferences.receiveWindowMs` | Batch refresh interval in milliseconds for time-window mode. |
| `preferences.receiveDelimiter` | Text delimiter used by delimiter mode, with the same escape syntax as text send. |
| `preferences.receiveFrameSize` | Bytes per frame in fixed-length mode. |
| `preferences.sendLineEnding` | Text-send suffix: `NONE`, `LF`, `CR`, or `CRLF`; HEX send is unchanged. |
| `preferences.enterToSend` / `clearAfterSend` | Whether Enter sends and whether successful sends clear the input. |
| `preferences.autoReconnect` / `reconnectIntervalMs` | Whether to reconnect after an unexpected disconnect and the retry interval in milliseconds. |
| `preferences.saveSend` / `saveReceive` | Whether transmitted and received data are saved. |
| `preferences.quickCommandsText` | Quick-send commands, one per line, using the same escapes as text send. |
| `preferences.highlightEnabled` / `highlightIgnoreCase` / `highlightKeywords` | Keyword highlight switch, case rule, and keyword list. |
| `preferences.logQuery` / `logOnlyMatches` | Last log query and whether only matching entries are shown. |
| `preferences.logAutoScroll` | Whether logs automatically scroll to the bottom. |
| `preferences.logShowTx` / `logShowRx` / `logShowInfo` | Whether each log type is visible. |
| `sendHistory` | Newest-first, deduplicated send history with at most 100 entries. |

The send input, loop-send switch, and loop interval are not persisted, and no serial port is opened automatically at startup.

## Configuration-driven simulation and bridging

The repository contains a progressive set of HOCON examples, from a minimal simulator to multi-endpoint routing. See the [configuration guide](config_samples/README.en.md) for the learning path, field reference, and comment syntax:

- [Minimal Device Simulator](config_samples/01-simulator-minimal.conf)
- [Text Device Simulator](config_samples/02-simulator-text.conf)
- [Fixed-length HEX Device Simulator](config_samples/03-simulator-hex-fixed-length.conf)
- [One-way Serial Bridge](config_samples/04-bridge-one-way.conf)
- [Bidirectional Serial Bridge](config_samples/05-bridge-bidirectional.conf)
- [Multi-endpoint, multi-route Serial Bridge](config_samples/06-bridge-multi-route.conf)
- [Many-to-many full mesh bridge](config_samples/07-bridge-many-to-many.conf)
- [AT-style device simulator with reply delay](config_samples/08-simulator-device.conf)
- [Automated test sequence](config_samples/09-test-sequence.conf)
- [Serial ⇄ TCP network endpoint](config_samples/10-network-endpoints.conf)

HOCON `.conf` files accept `#` and `//` comments, so port roles, protocol notes, and debugging context can stay next to the configuration.

Device Simulator example:

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

Use `HEX` for `encode` when working with byte-oriented rules. `packSize` enables fixed-length framing, while `delimiter` enables delimiter-based framing; exactly one must be present. A simulator must define a complete endpoint, while a bridge must define at least two endpoints and one enabled route. Configuration loading reports invalid HEX, empty delimiters, missing ports, duplicate ports, and broken route references.

### Reply delay (fixed or randomized)

Replies can be delayed: `300` waits a fixed 300 ms, while `"100~300"` waits a **uniformly random** 100–300 ms per reply:

```hocon
simulator {
  responseDelay = "10~80"          # Global: every reply lands within 10–80 ms

  rules = [
    { request = "AT\r\n",         response = "OK\r\n" }
    { request = "AT+STATUS?\r\n", response = "STATUS:READY\r\n", delay = "200~500" }  # Per-rule override
    { request = "AT+ID\r\n",      response = "ID:42\r\n",       delay = 20 }           # Fixed 20 ms
  ]
}
```

- The unit is milliseconds and the range is `0`–`600000`; both `~` and `-` work as separators, and surrounding spaces are optional.
- Precedence: per-rule `delay` > global `responseDelay` > default `0` (reply immediately).
- **Randomized ranges exist to test boundaries**: real device timing jitters, and a fixed value never pushes host software against its timeout threshold. Set the range slightly above that threshold to see whether it retries correctly and avoids processing the same reply twice.
- Multiple delayed replies are written **FIFO**, matching a real device that handles one request at a time.
- Invalid values never fail silently: a bad `responseDelay` falls back to `0` with a warning, and a bad per-rule `delay` falls back to the global value with a warning.

## Automated test sequences

Device simulation answers "how does the device respond"; a test sequence answers "how do we verify the device". One HOCON file describes `send → expect → timeout`, then runs it step by step with PASS/FAIL results:

```hocon
test {
  connection { port = "COM2", baudRate = 115200 }
  defaults   { timeout = 1000, interval = 100 }

  steps = [
    { name = "Handshake", send = "AT\r\n",         expect = "OK" },
    { name = "Slow reply", send = "AT+STATUS?\r\n", expect = "STATUS:READY", timeout = 1500 },
    { name = "Silence",   match = "silence",       timeout = 500 },
    { name = "HEX",       encoding = "hex", send = "41 54 0D 0A", expect = "4F 4B" }
  ]
}
```

- **In the app**: open the **Test Sequence** workspace → load the configuration → Run. The port comes straight from `test.connection.port` in the file (there is no separate port picker, so change the configuration to switch ports). Each step shows PASS/FAIL, expected value, actual value, and elapsed time; failures distinguish "timed out with zero bytes" from "content mismatch".
- **On the command line**: `KSerial.exe test 09-test-sequence.conf --report result.txt`. A non-zero exit code means at least one step failed, so it can gate CI directly.
- Four match modes: `contains` (default, substring), `exact` (trimmed equality), `regex` (regex search), and `silence` (no byte may arrive within the window).
- A step passes as soon as received data matches instead of waiting out the timeout; only a full timeout counts as a failure.
- See [configuration reference](config_samples/README.en.md#test-sequence-fields) for the full field table and matching semantics, and [09-test-sequence.conf](config_samples/09-test-sequence.conf) for a runnable example.

## Serial plotter

Turn a device's numeric stream into live curves: **one sample per line, one channel per column** (up to 8 channels). Commas, spaces, and `KEY=VALUE` prefixes all parse.

```
23.5,61,1013      ← plain CSV
TEMP=23.5,HUM=61  ← labelled values
```

- **Usage**: open the **Serial Plotter** workspace → type a port name (or a `tcp://` / `udp://` address) in the address box and set the baud rate → Open. The chart starts scrolling immediately; the window holds 200, 500, 1000, or 2000 samples.
- The plotter address box accepts network addresses just like the terminal, so you can chart a remote device's numeric stream directly: enter `tcp://host:port` and open.
- Samples received while **paused** are discarded rather than buffered for later, and **Clear** resets curves, channel count, and byte counters.
- The Y axis auto-scales to the current window with the range shown in the corner, and the legend colors match the curves. Non-numeric lines (`OK`, error logs) are ignored, and binary streams without newlines are capped at 4096 bytes so memory stays bounded.
- **The classic trap**: a sample only exists once a line is terminated. When sending from the terminal workspace, set the line ending to LF or CRLF — otherwise the bytes arrive but never become a curve.
- **Diagnostics**: the chart header shows `RX N B`, adds `N B awaiting newline` when data is stuck, and the empty state explains whether newlines or numbers are missing.
- Only UTF-8 text is supported; binary data shown as HEX cannot be plotted.

## Headless mode (CLI)

The same executable **opens no window when started with arguments** — run it from a terminal, a script, or CI:

```powershell
# Windows (install directory or unpacked zip)
KSerial.exe gateway  07-bridge-many-to-many.conf --duration 60
KSerial.exe simulate 08-simulator-device.conf
KSerial.exe test     09-test-sequence.conf --report result.txt

# Linux (unpacked tar.gz)
./KSerial gateway 05-bridge-bidirectional.conf --duration 30

# macOS (inside the .app bundle)
/Applications/KSerial.app/Contents/MacOS/KSerial gateway 05-bridge-bidirectional.conf --duration 30
```

| Command | Alias | What it does |
| --- | --- | --- |
| `gateway` | `bridge` | Opens the endpoints in `gateway.endpoints` and forwards per `gateway.routes` |
| `simulate` | `simulator` | Opens the endpoint in `simulator.*` and auto-replies per the rules |
| `test` | `run-test` | Runs `test.steps` and reports per-step PASS/FAIL |
| `help` | `--help`, `-h` | Prints usage |

| Option | Applies to | Description |
| --- | --- | --- |
| `--duration <seconds>` / `-d` | `gateway`, `simulate` | Exits after the given duration; without it the process runs until Ctrl+C |
| `--report <path>` | `test` | Writes a per-step text report |
| `--lang zh\|en\|ja` | `gateway` | Language of configuration validation messages; defaults to the system locale |

Exit codes: `0` success, `1` failure (configuration not runnable, port cannot open, or a test step failed), `2` usage error.

Running from source works too, without packaging:

```powershell
.\gradlew.bat :composeApp:run --args="--help"
.\gradlew.bat :composeApp:run --args="test 09-test-sequence.conf --report result.txt"
```

Common pitfalls:

- Headless mode **only accepts endpoints from the configuration file**; there is no ad-hoc `COM3` argument — edit the `.conf` to change ports.
- `simulate` requires `simulator.endpoint.port` in the file; the GUI convenience of "reuse current page settings" does not exist headless.
- A `gateway` configuration without `routes` degrades to "forward everything received to all other endpoints".
- Endpoint addresses also accept `tcp://`, `tcp-listen://`, `udp://`, and `udp-listen://`, so a headless process can serve as a permanent serial-port server (see "Serial ⇄ network" above).
- Each log line carries a timestamp and goes to stdout, so redirecting keeps an audit trail: `KSerial.exe gateway x.conf > gateway.log 2>&1`.
- In CI, use `--duration` so the process exits on its own instead of relying on Ctrl+C.

## Run from source

Requirements:

- JDK 25
- The Gradle Wrapper included in this repository

Windows:

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

macOS / Linux:

```bash
./gradlew :composeApp:run
./gradlew :composeApp:jvmTest
./gradlew :composeApp:packageDistributionForCurrentOS
```

## Testing

After refreshing the Gradle project, the following tasks are available in the IDE under `composeApp > Tasks > KSerial Tests`:

| Task | Purpose |
| --- | --- |
| `coreTest` | Runs unit and Compose UI tests without opening real serial ports |
| `terminalHardwareTest` | Uses virtual-port pair 1 to verify real bidirectional terminal traffic, escape parsing, and loop sending |
| `simulatorHardwareTest` | Uses pairs 2 and 3 to verify ASCII delimiter framing and fixed-length HEX auto-replies |
| `gatewayHardwareTest` | Uses pairs 1–5 to verify configured routes, one-to-one, one-to-many, many-to-many, binary-transparent forwarding, and non-target isolation |
| `captureHardwareTest` | Verifies the Recorder: dual-port transparent recording and manual-mode Q&A recording against a single port |
| `recorderRoundTripHardwareTest` | Full loop: record → export configuration → load in the simulator → replay reproduces the device |
| `networkSerialBridgeHardwareTest` | Verifies serial ⇄ TCP hybrid bridging: transparent bidirectional traffic between a TCP server and a real serial port |
| `testRunnerHardwareTest` | Verifies real-device test sequences: CONTAINS / HEX matching, failure detection, and silence waits |
| `cliGatewayHardwareTest` | Verifies headless mode: the CLI gateway bridges TCP and a real serial port |
| `plotterHardwareTest` | Verifies the Plotter: multi-channel sampling across fragmented/coalesced chunks, window trimming, and pause semantics |
| `allHardwareTest` | Runs every real serial-port test strictly sequentially (virtual ports are a globally shared resource) |
| `allKSerialTest` | Runs unit/UI tests first, followed by every real serial-port test |

Slower observation tasks (`terminalHardwareObserve`, `simulatorHardwareObserve`, `gatewayHardwareObserve`, and the other `*HardwareObserve` tasks) pause around each real write and print `OPEN`, `SEND`, `WRITE RESULT`, and `RECEIVED` traces to the console, making traffic easy to verify with an external serial monitor.

Hardware tests use ten connected virtual-port pairs by default: `COM1↔COM2`, `COM3↔COM4`, …, `COM19↔COM20`. The first port in each pair acts as the external test peer, while KSerial opens the second. Override the topology with the `KSERIAL_PORT_PAIRS` environment variable using the format `portA:portB,portC:portD`.

```powershell
$env:KSERIAL_PORT_PAIRS = "COM21:COM22,COM23:COM24"
.\gradlew.bat :composeApp:allKSerialTest
```

Regular `jvmTest`, `coreTest`, and CI runs deliberately skip real serial-port tests so they do not claim local ports. Generate the JVM test coverage report with:

```powershell
.\gradlew.bat :composeApp:koverHtmlReportJvm
```

Open `composeApp/build/reports/kover/htmlJvm/index.html` to view the report.

## Automated releases

The application version is defined by `appVersion` in `gradle.properties`. Pushing a matching `v*` tag, such as `v1.0.0`, starts the `Build and Release` workflow. It tests and packages the application on Windows, macOS, and Linux, then creates or updates the matching GitHub Release with a `SHA256SUMS.txt` file.

Regular pushes to `master` and pull requests only run JVM unit/UI tests that do not require real serial ports; they do not package or publish the application. A manually started release workflow validates multi-platform packaging by default and creates or updates a Release only when its `publish` option is explicitly enabled.

## Technology

- Kotlin Multiplatform
- Compose Multiplatform / Material 3
- kotlinx.coroutines / Flow
- jSerialComm
- kotlinx.serialization
- Typesafe Config
- Gradle / jpackage


## License

KSerial is available under the [Apache License 2.0](LICENSE).

If KSerial helps you, please consider giving the project a ⭐ on GitHub. It is a small gesture that helps the project keep improving.
