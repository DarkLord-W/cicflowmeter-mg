# Deployment

Three ways to run CICFlowMeter-MG on a host: as a systemd service, as a
container, or straight from the packaged tarball.

## From the tarball

```bash
tar xzf cicflowmeter-4.1-enhanced-standalone-linux-x86_64.tar.gz -C /opt/cicflowmeter
/opt/cicflowmeter/bin/cicflowmeter-cli status
```

The launchers set `java.library.path` to `lib/native`, so no extra flags are
needed. CSV output goes to `data/` unless `-o` or the GUI setting says otherwise.

## systemd

The unit in `packaging/systemd/cicflowmeter.service` assumes the distribution is
unpacked at `/usr/share/cicflowmeter` and writes data to `/var/lib/cicflowmeter`.

```bash
sudo install -d /usr/share/cicflowmeter /var/lib/cicflowmeter /etc/cicflowmeter
sudo tar xzf cicflowmeter-4.1-enhanced-standalone-linux-x86_64.tar.gz -C /usr/share/cicflowmeter
sudo cp /usr/share/cicflowmeter/config/log4j2.xml /etc/cicflowmeter/
sudo cp packaging/systemd/cicflowmeter.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now cicflowmeter
systemctl status cicflowmeter
journalctl -u cicflowmeter -f
```

Capabilities are required for capture. Either run the service as root (the unit
does not set `User=` for capture use) or grant them to the JVM binary:

```bash
sudo setcap cap_net_raw,cap_net_admin=eip /usr/lib/jvm/*/bin/java
java -version   # must still work
```

Setting capabilities on a JVM binary puts the dynamic loader into secure mode.
Some JDK builds then fail to locate `libjli.so`; if `java -version` breaks,
remove the capability again with `sudo setcap -r <path-to-java>` and run with
`sudo` instead.

The unit sets `MemoryLimit=2G`, `CPUQuota=200%`, `ProtectSystem=strict` and
`ReadWritePaths=/var/lib/cicflowmeter /var/log/cicflowmeter`. Adjust the
`ExecStart` arguments to change the interface, filter or output directory.

## Container

```bash
docker build -f container/Dockerfile -t cicflowmeter:4.1-enhanced .
docker run -d --name cicflowmeter \
  --cap-add=NET_RAW --cap-add=NET_ADMIN \
  -p 8899:8888 \
  -v /srv/cicflowmeter/data:/var/lib/cicflowmeter \
  cicflowmeter:4.1-enhanced capture --interface eth0 --health-check
```

`NET_RAW` and `NET_ADMIN` are required; without them the process starts and then
fails when libpcap opens the interface. For live capture of a host interface on
Linux, use `--network host` instead of publishing ports.

For offline analysis, mount a pcap directory and override the command:

```bash
docker run --rm -v /srv/pcaps:/pcaps:ro -v /srv/cicflowmeter/data:/var/lib/cicflowmeter \
  cicflowmeter:4.1-enhanced analyze /pcaps /var/lib/cicflowmeter
```

## Health and status

| Endpoint | Meaning |
|---|---|
| `GET /health` | Process state and uptime |
| `GET /config/status` | Loaded configuration, resolved config directory, API and CSV flags |
| `GET /` | HTML index linking to both |

Port defaults to 8888. Override with `CIC_HEALTH_PORT` or
`-Dcicflowmeter.health.port=PORT`. Bind address defaults to loopback; set
`CIC_HEALTH_HOST` or `-Dcicflowmeter.health.host` to change it.

```bash
curl -s localhost:8888/health
curl -s localhost:8888/config/status
```

## Configuration directory

Resolution order for the config directory:

1. `-Dcicflowmeter.config.dir=...`
2. `CIC_CONFIG_DIR` environment variable
3. `~/.cicflowmeter`

It holds `time_rules.json` (labels) and `api_config.json` (streaming endpoint).
Both can be edited from the GUI dialogs or through `cicflowmeter-cli config`.
