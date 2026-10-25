# Screenshots

The images in `docs/images/` were taken from the build produced by this
repository, on Linux x86-64, under a virtual X server. They are reproducible:
see `scripts/screenshots/capture.sh`.

## What was captured

| File | What it shows |
|---|---|
| `gui-main.png` | GUI after startup: menu bar, capture buttons, empty flow table, status counters, interface list |
| `gui-network-menu.png` | `NetWork` menu open (Offline / Realtime) |
| `gui-offline.png` | Offline panel with pcap and output directories filled in |
| `gui-offline-run.png` | Offline analysis finished: 46 flows, 1470 packets, 0 discarded |
| `gui-api-settings.png` | `API和CSV配置` dialog |
| `gui-time-rules.png` | `时间规则配置` dialog with a rule in the table |
| `gui-about.png` | About dialog; the version comes from the jar manifest |
| `cli-help.png` | `cicflowmeter-cli --help`, six subcommands |
| `cli-capture-help.png` | `cicflowmeter-cli capture --help`, all options |
| `cli-analyze.png` | `cicflowmeter-cli analyze` on a 282 KB pcap |
| `cli-status.png` | `cicflowmeter-cli status` |
| `health-endpoint.png` | `curl /health` against a standalone health server |

## What was not captured, and why

- **Live capture in the GUI** — opening an interface needs `CAP_NET_RAW`. The
  build machine has no passwordless root, so the capture could not be started;
  the offline path exercises the same flow table.
- **Chart panel** — only reachable after saving a live capture to CSV, which
  depends on the same permission.
- **Container** — no Docker or Podman daemon on the build machine. The image is
  built in CI instead (`.github/workflows/ci.yml`).
- **systemd service** — would need root to install the unit.

## How they were taken

- Virtual display: `Xvfb :99 -screen 0 1400x900x24 -dpi 96`, with `xfwm4` as a
  window manager so windows have real decorations.
- Window screenshots: `import -window <id>`; dialogs captured individually.
- Terminal shots: `qterminal` running a small script that prints the command and
  its output, then cropped from a root capture.
- Framing: 1 px `#d0d7de` border plus 12 px `#f6f8fa` padding, metadata stripped,
  PNG compression level 9. Window shots are scaled to 1280 px wide, dialogs to
  900, terminals to 1100.
- Nothing personal appears: paths are `/tmp/cicfm/...`, the prompt is `$ `, the
  interface is `lo` or absent, and all addresses are loopback.
