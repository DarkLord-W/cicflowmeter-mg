# CICFlowMeter-MG

CICFlowMeter reads packets, groups them into flows, and writes one row of 85
features per flow. Upstream ships a Swing interface and a bare command-line
entry point. This fork keeps both, adds a proper CLI with six subcommands,
attaches time-window labels to flows, and can stream results to an HTTP endpoint
while a capture is running. Packaging for Docker, Podman and systemd lives in
this repository rather than in a separate deployment project.

**English** · [简体中文](README.zh-CN.md)

![The CICFlowMeter-MG interface on startup](docs/images/gui-main.png)
*The GUI on startup. `NetWork` switches between live capture and offline pcap analysis.*

## What this fork changes

This is not a repackaging. The changes run through the whole path from capture to
output, and each row below names the file the change lives in so it can be checked
against the history.

| Area | Upstream 4.0 | This fork |
|---|---|---|
| Entry points | Swing GUI, legacy `Cmd` | GUI, a six-subcommand CLI, legacy `Cmd` with `--help` / `--version` (`CLIMain.java`, `Cmd.java`) |
| Flow labelling | none | Time-rule labels: groups, rules, enable/disable, JSON import and export (`TimeRuleManager.java`, `TimeLabelRule.java`, `RuleGroup.java`) |
| Data output | CSV only | CSV plus live HTTP streaming of each completed flow (`ApiSendTask.java`, `ApiConfigManager.java`) |
| CSV location | fixed | Configurable output directory, also settable from the GUI (`FlowMgr.setCustomSavePath`) |
| Observability | none | HTTP health endpoint with `/health` and `/config/status` (`HealthCheckServer.java`) |
| Runtime status | none | Status bar showing flow count, API send counters and enabled rule count (`FlowMonitorPane.java`) |
| Configuration | file editing | Two GUI dialogs — API/CSV settings and time rules — plus `config` CLI verbs (`FlowMonitorPane.java`, `CLIMain.java`) |
| Logging | log4j1, one config | log4j2, separate GUI and CLI configs, custom layouts (`log4j2.xml`, `log4j2-cli.xml`, `log4j2-layouts/`) |
| File handling | none | Cross-platform permission helper for POSIX and Windows (`FileSecurityUtils.java`) |
| Build | single distribution | Three fat jars, launcher scripts, native libraries shipped side by side (`build.gradle`) |
| Packaging | manual | `packaging/package.sh` producing `jars/ lib/native/ bin/ config/` and a tarball |
| Deployment | none | Multi-stage Dockerfile, compose file, systemd unit, container entrypoint |

### Time-rule labels

Rules live in `time_rules.json` in the config directory and are applied while
flows are still in memory, so labels end up in the CSV column instead of being
joined afterwards. Groups separate working hours, off hours and one-off events;
each group can be switched off without deleting its rules.

```bash
cicflowmeter-cli config --import rules.json
cicflowmeter-cli config --groups
cicflowmeter-cli capture -i eth0 --time_label --health-check
```

### Live feature streaming

Every completed flow can be posted to an HTTP endpoint while capture runs, so a
downstream detector does not have to poll for CSV files.

```json
{
  "apiUrl": "http://127.0.0.1:5000/api/v1/process_stream",
  "apiEnabled": false,
  "csvEnabled": true,
  "csvSavePath": null
}
```

`csvSavePath: null` keeps the default output directory. With `apiEnabled` off the
capture path is untouched and nothing is sent. Both are editable from
`API设置` in the capture panel or from `cicflowmeter-cli config --set`.

### Health endpoint

```console
$ curl -s localhost:8888/health
{
  "status": "UP",
  "version": "4.1-enhanced",
  "service": "CICFlowMeter",
  "system": { "uptime": 4445, "memory": { "heapUsedMB": 25 } }
}
```

`/config/status` reports the resolved configuration directory and the API/CSV
flags. The root path is a small HTML index linking to both.

### Command line

| Subcommand | Purpose |
|---|---|
| `capture` | Live capture on an interface, optional health server and API streaming |
| `analyze` | Offline analysis of a pcap file or directory |
| `config` | Inspect and change settings, import/export time rules, manage groups |
| `status` | Rule counts, group counts, API settings, health server state |
| `daemon` | Start and stop background operation |
| `health` | Start, stop and probe the health endpoint independently |

## Requirements

- JDK 8 (the build uses Gradle 4.2, which does not run on newer JDKs)
- libpcap at runtime; jnetpcap's native library links against `libpcap.so`
- Linux or Windows. Capture needs raw-socket permission.

## Install

### Release package

```bash
tar xzf cicflowmeter-4.1-enhanced-standalone-linux-x86_64.tar.gz -C /opt/cicflowmeter
/opt/cicflowmeter/bin/cicflowmeter-cli status
```

The Windows zip has the same layout with `.bat` launchers and the `.dll` native
library.

### Container image

```bash
docker run --rm --cap-add=NET_RAW --cap-add=NET_ADMIN \
  -p 8899:8888 -v "$PWD/data:/var/lib/cicflowmeter" \
  ghcr.io/DarkLord-W/cicflowmeter:4.1-enhanced \
  capture --interface eth0 --health-check
```

### From source

```bash
git clone https://github.com/DarkLord-W/cicflowmeter-mg.git
cd cicflowmeter-mg
export JAVA_HOME=/path/to/jdk8
./gradlew --no-daemon clean test fatJarCli fatJarGui fatJarLegacy
```

Three jars land in `build/libs`. See [docs/BUILD.md](docs/BUILD.md) for the
longer version, including packaging and troubleshooting.

## Running

### GUI

```bash
./bin/cicflowmeter-gui
```

The capture panel lists interfaces (`Load`), starts and stops capture
(`Start` / `Stop`), and writes the flow table to CSV (`Save as`). Charts are
reachable after a save: press `Graphs` to open the visual panel for the file you
just wrote. `API设置` and `时间规则设置` open the two configuration dialogs.

### CLI

```bash
sudo ./bin/cicflowmeter-cli capture --interface eth0 --health-check
./bin/cicflowmeter-cli analyze /pcaps /out
./bin/cicflowmeter-cli capture --help
```

### Legacy command line

```bash
./bin/cicflowmeter-legacy /pcaps /out
./bin/cicflowmeter-legacy --version
```

## Screens

| Live capture panel | Time-rule editor |
|---|---|
| ![Capture panel](docs/images/gui-main.png) | ![Time rule dialog](docs/images/gui-time-rules.png) |
| Interfaces on the left, flow table in the middle, counters along the bottom. | Rules in a table, editor below; groups can be toggled without deleting rules. |

| API and CSV settings | Offline analysis |
|---|---|
| ![API settings dialog](docs/images/gui-api-settings.png) | ![Offline panel](docs/images/gui-offline.png) |
| Where captured features are posted and where CSVs land. | Point at a pcap directory, set the two timeouts, press OK. |

| Analysis result | Menu |
|---|---|
| ![Offline run finished](docs/images/gui-offline-run.png) | ![NetWork menu](docs/images/gui-network-menu.png) |
| Flow and packet counters for the completed file. | The two working modes are one menu away. |

| CLI overview | `capture` options |
|---|---|
| ![CLI help](docs/images/cli-help.png) | ![Capture help](docs/images/cli-capture-help.png) |
| Six subcommands, each with its own help. | Interface, BPF filter, timeouts, health check, API/CSV switches. |

| Offline analysis from the CLI | Health endpoint |
|---|---|
| ![CLI analyze](docs/images/cli-analyze.png) | ![Health endpoint](docs/images/health-endpoint.png) |
| One pcap in, one CSV out, with packet statistics. | JSON state plus memory and thread counters. |

## Docker and Podman

```bash
docker build -f container/Dockerfile -t cicflowmeter:4.1-enhanced .
docker compose -f container/docker-compose.yml up -d
curl -s localhost:8899/health
```

The image compiles the jars in a builder stage and copies only jars, the
`libjnetpcap.so` native library, config samples and the entrypoint into the
runtime stage. The first argument selects the jar: `cli` (default), `gui` or
`legacy`. Details in [container/README.md](container/README.md).

`NET_RAW` and `NET_ADMIN` are required for capture. Without them the container
starts and then fails when libpcap opens the interface.

## systemd

```bash
sudo install -d /usr/share/cicflowmeter /var/lib/cicflowmeter /etc/cicflowmeter
sudo tar xzf cicflowmeter-4.1-enhanced-standalone-linux-x86_64.tar.gz -C /usr/share/cicflowmeter
sudo cp /usr/share/cicflowmeter/config/log4j2.xml /etc/cicflowmeter/
sudo cp packaging/systemd/cicflowmeter.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now cicflowmeter
```

See [docs/DEPLOY.md](docs/DEPLOY.md) for capabilities, resource limits and log
locations.

## Configuration

The config directory is resolved in this order:

1. `-Dcicflowmeter.config.dir=...`
2. `CIC_CONFIG_DIR` environment variable
3. `~/.cicflowmeter`

It holds `time_rules.json` (labels) and `api_config.json` (streaming endpoint).
The health port comes from `CIC_HEALTH_PORT` or `-Dcicflowmeter.health.port`
(default 8888).

## Troubleshooting

**`Failed to load library jnetpcap` / `dlopen` error** — the JVM cannot find the
native library or its `libpcap.so` dependency. Use the packaged launchers (they
set `java.library.path`), and install the libpcap development package so the
`libpcap.so` symlink exists.

**`Unsupported class file major version`** — Gradle 4.2 is running on a JDK newer
than 8. Point `JAVA_HOME` at JDK 8.

**Capture fails with a permission error** — grant raw sockets:

```bash
sudo setcap cap_net_raw,cap_net_admin=eip "$(readlink -f "$(which java)")"
```

**The GUI does not start on a server** — it needs an X display; use
`xvfb-run` or run the CLI instead.

## Known limitations

- Command-line messages, including help text and log lines, are in Chinese.
  An English locale is future work.
- Only x86-64 native libraries are bundled; on arm64 the capture path has no
  working `libjnetpcap.so`.
- The GUI needs an X display. There is no headless GUI mode.
- Charts open from the capture panel after saving the flow table; the
  `Action → Visual` menu entry exists in the source but is commented out.
- The container image is built and exercised in CI; the source build and the CLI
  documented here were run on Linux x86-64 only.

## Build and test

```bash
./gradlew test                             # TimeRuleManagerTest
./gradlew fatJarCli fatJarGui fatJarLegacy  # three runnable jars
bash packaging/package.sh                  # Linux tarball + Windows zip + checksums
docker build -f container/Dockerfile .     # image
```

## Project layout

```
src/main/java/cic/cs/unb/ca/     capture, flow handling, labels, API, CLI, health
src/main/resources/             log4j2 configs, config samples, systemd unit
src/test/java/                  rule manager test
container/                      Dockerfile, entrypoint, compose file
packaging/                      package script and systemd unit
examples/                       API test server, helper scripts
docs/                           build, deploy, fork notes, screenshots
```

## Upstream, credits and license

This is an **unofficial fork**, not a release of the University of New Brunswick
or the Canadian Institute for Cybersecurity.

- Upstream: <https://github.com/ahlashkari/CICFlowMeter>,
  <https://github.com/CanadianInstituteForCybersecurity/CICFlowMeter>
- Starting point: CICFlowMeter 4.0. This fork is released as `4.1-enhanced`.
- The first 108 commits in this repository are upstream's, kept with their
  original authors and messages.
- Upstream is MIT licensed; this fork keeps the same licence (`LICENSE.txt`,
  with the fork's copyright line appended).
- `MG` is the label carried over from this fork's earlier internal revisions
  (V1–V7); it is not an abbreviation of an upstream project or component.

CICFlowMeter (formerly ISCXFlowMeter) was built at the Canadian Institute for
Cybersecurity, University of New Brunswick. Thanks to the original authors and
contributors:

- **Arash Habibi Lashkari** — researcher and developer (founder)
- **Gerard Drapper Gil** — researcher and developer (co-founder)
- **Muhammad Saiful Islam** — researcher and developer (co-founder)

and to the CIC team for the CICIDS2017 datasets; the 85-feature set extracted
here follows their specification.

If you use this tool in academic work, cite the upstream papers:

- Arash Habibi Lashkari, Gerard Draper-Gil, Mohammad Saiful Islam Mamun and
  Ali A. Ghorbani, "Characterization of Tor Traffic Using Time Based Features",
  ICISSP 2017, Porto, Portugal.
- Gerard Drapper Gil, Arash Habibi Lashkari, Mohammad Mamun, Ali A. Ghorbani,
  "Characterization of Encrypted and VPN Traffic Using Time-Related Features",
  ICISSP 2016, Rome, Italy, pp. 407–414.

Issues and pull requests belong here, not upstream. Please only capture traffic
on networks you are authorised to observe.

## Versioning

Releases use `4.1-enhanced` as the version and `v4.1-enhanced` as the tag, with
`.1`, `.2` suffixes for follow-up releases. The Release workflow refuses to run
when the tag and `build.gradle` version disagree.
