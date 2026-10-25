# Changelog

All notable changes to this fork are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow the
`4.1-enhanced` scheme described in the README.

## [4.1-enhanced] — first public release

Base: CICFlowMeter 4.0 (upstream `98a5eba`). The fork's own history starts after
the 108 upstream commits, which are kept unchanged.

### Added

- **CLI** (`CLIMain`, `cicflowmeter-cli`): `capture`, `analyze`, `config`,
  `status` and `health` subcommands with per-command help.
- **Time-rule labels** (`TimeRuleManager`, `TimeLabelRule`, `RuleGroup`,
  `LabelConstants`, `LocalDateTimeAdapter`): rule groups, enable/disable, JSON
  import and export, and a unit test covering the manager.
- **Live feature streaming** (`ApiSendTask`, `ApiConfigManager`): posts each
  completed flow to an HTTP endpoint while capture runs.
- **Health endpoint** (`HealthCheckServer`): `/health` and `/config/status` on
  port 8888 by default, configurable through `CIC_HEALTH_PORT` or
  `-Dcicflowmeter.health.port`.
- **GUI**: interface list on the capture panel, `API设置` (API and CSV settings)
  and `时间规则设置` (time rules) dialogs, and a status bar showing flow count,
  API send counters and enabled rule count.
- **Configurable CSV directory** (`FlowMgr.setCustomSavePath`).
- **Cross-platform file permissions** (`FileSecurityUtils`).
- **Packaging**: `packaging/package.sh` builds three fat jars into a Linux
  tarball and a Windows zip, including the matching jnetpcap native library and
  launcher scripts.
- **Deployment**: multi-stage `container/Dockerfile`, a single-service
  `container/docker-compose.yml`, and a systemd unit. The Dockerfile uses
  fully-qualified base image names so Podman resolves them without a
  `registries.conf` short-name rule.

### Changed

- Logging moved from log4j 1 to log4j 2, with separate GUI and CLI
  configurations and custom layouts.
- `legacy` CMD gained `--help` and `--version`; running it without arguments now
  prints usage instead of a single log line.
- Build version set to `4.1-enhanced`; three fat jar tasks replace the single
  distribution.

### Fixed

- The About dialog read a hard-coded version string; it now reads the version
  from the jar manifest.
- The build declared `org.jnetpcap:jnetpcap:1.4.1` from Maven Central, where it
  does not exist, so a clean checkout could not be compiled. The dependency now
  uses the jar bundled under `jnetpcap/`, which is also what the launchers load
  the native library from.
- The bundled container sample `api_config.json` was not valid JSON (missing
  commas); it is now valid and defaults to `apiEnabled: false`.
- Removed a hard-coded private-network address from the CLI help examples.

### Notes

- Do not leave `setcap` on the JVM binary used for builds: the loader switches to
  secure mode and some JDK builds lose `libjli.so`. Run capture with `sudo`, or
  remove the capability before building (`sudo setcap -r <java>`).
- Command-line output is in Chinese. Localisation is tracked separately.
- The build still uses Gradle 4.2 and the `compile` configuration.
- The chart view is reachable from the capture panel: save the flow table as CSV,
  then press **Graphs**. The `Action → Visual` menu entry is commented out.
