# Command line reference

Entry point: `cicflowmeter-cli` from the release package, or
`java -Djava.library.path=<dir> -jar cicflowmeter-cli-4.1-enhanced.jar`.

```
用法: cfm [全局选项] <子命令> [子命令选项]
```

## Global options

| Option | Meaning |
|---|---|
| `-c, --config <dir>` | Config directory (default `~/.cicflowmeter`) |
| `-h, --help` | Help |
| `-l, --log-level <level>` | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `-p, --health-port <port>` | Health server port (default 8888) |
| `-v, --version` | Version |

## Subcommands

### capture

| Option | Meaning |
|---|---|
| `-i, --interface <name>` | Interface to listen on |
| `-f, --filter <bpf>` | BPF filter expression |
| `-o, --output <dir>` | Output directory |
| `-t, --timeout <ms>` | Flow timeout (default 120000) |
| `-a, --activity-timeout <ms>` | Activity timeout (default 5000) |
| `--health-check` | Start the health server alongside capture |
| `--time_label` | Apply time-rule labels to flows |
| `--no-api` | Do not send features to the API |
| `--no-csv` | Do not write CSV |

```bash
sudo cicflowmeter-cli capture --interface eth0 --health-check --time_label
cicflowmeter-cli capture -i eth0 -f "tcp port 443" -o /tmp/out
```

Capture needs raw sockets. Without them the process starts and libpcap fails on
open.

### analyze

```bash
cicflowmeter-cli analyze <pcap-file-or-dir> <output-dir>
```

Prints per-file progress and packet statistics; writes `<file>_Flow.csv` for
each input.

### config

| Option | Meaning |
|---|---|
| `-l, --list` | List configuration |
| `-s, --show` | Show details |
| `--set key=value` | Set a value |
| `--get <key>` | Read a value |
| `-i, --import <file>` | Import time rules from JSON |
| `-e, --export <file>` | Export time rules to JSON |
| `-r, --reset` | Reset to defaults |
| `-g, --groups` | Manage rule groups |

```bash
cicflowmeter-cli config --groups
cicflowmeter-cli config --export /tmp/rules.json
cicflowmeter-cli config --set api.url=http://127.0.0.1:5000/flows
```

### status

Prints rule counts, group counts, API configuration and health server state.
Read-only.

### daemon

Start or stop background operation. The same health and API settings apply.

### health

| Option | Meaning |
|---|---|
| `-s, --start` | Start the health server |
| `-p, --port <port>` | Port (default 8888) |
| `-stop` | Stop it |
| `-test` | Probe the endpoint |

```bash
cicflowmeter-cli health -s -p 8899 &
curl -s localhost:8899/health
curl -s localhost:8899/config/status
```

## Notes

- All output, including help and log lines, is in Chinese in this build.
- The config directory holds `time_rules.json` and `api_config.json`; both are
  also editable from the GUI dialogs.
- Exit status is non-zero when a subcommand fails to start (for example a
  missing jar, a bad option, or a library that cannot be loaded).
