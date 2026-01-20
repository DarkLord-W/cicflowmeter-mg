# Container

Two ways to run the image: `docker compose` for a long-running capture, or a
plain `run` for a one-off command.

## Build

The Dockerfile compiles the sources, so the build context is the repository
root, not this directory.

```bash
docker build -f container/Dockerfile -t cicflowmeter:4.1-enhanced .
```

Three fat jars are produced in the builder stage; only the jars, the jnetpcap
`.so` and the config samples are copied into the runtime image. Nothing is
committed pre-built, so the image always matches the commit it was built from.

## Run

```bash
docker run --rm --cap-add=NET_RAW --cap-add=NET_ADMIN \
  -p 8899:8888 \
  -v "$PWD/data:/var/lib/cicflowmeter" \
  cicflowmeter:4.1-enhanced capture --interface eth0 --health-check
```

Capture needs `NET_RAW` and `NET_ADMIN`. Without them the container starts and
then fails when pcap tries to open the interface.

The first argument selects the entry point:

| Argument | Jar |
|---|---|
| *(none)* or `cli` | `cicflowmeter-cli-*.jar` |
| `gui` | `cicflowmeter-gui-*.jar` (needs an X11 socket) |
| `legacy` | `cicflowmeter-legacy-*.jar` |

Everything after it is passed through unchanged, so `... cli analyze /pcaps /out`
and `... capture -i eth0` both work.

## Offline analysis

```bash
docker run --rm -v "$PWD/pcaps:/pcaps:ro" -v "$PWD/data:/var/lib/cicflowmeter" \
  cicflowmeter:4.1-enhanced analyze /pcaps /var/lib/cicflowmeter
```

CSV files land in the mounted directory.

## Compose

```bash
cp container/.env.example container/.env
docker compose -f container/docker-compose.yml up -d
curl -s localhost:8899/health
```

`docker-compose.yml` publishes the health port, mounts `container/data` and
`container/pcaps`, and runs `capture --interface eth0 --health-check`. On Linux,
switch to host networking if you need to capture a real host interface:

```yaml
# network_mode: host
# ports: ...   # remove when using host networking
```

## Health check

```bash
$ curl -s localhost:8899/health
{"status":"UP", ...}
$ curl -s localhost:8899/config/status
```

`/health` reports the process state; `/config/status` reports the loaded
configuration. The port comes from `CIC_HEALTH_PORT` (default 8888).

## Configuration

The entry point seeds `/var/lib/cicflowmeter` with `time_rules.json` and
`api_config.json` from `/etc/cicflowmeter/config-examples/` on first start. It
never overwrites files that already exist, so mount that directory to keep
your rules across container recreations.

```bash
docker run --rm -v "$PWD/config:/var/lib/cicflowmeter" ... \
  cli config --import /var/lib/cicflowmeter/time_rules.json
```
