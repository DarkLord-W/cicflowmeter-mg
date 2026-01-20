# Building from source

## Requirements

| Component | Version | Notes |
|---|---|---|
| JDK | 8 | The build uses Gradle 4.2, which does not run on newer JDKs |
| Gradle | 4.2 | Downloaded by `gradlew` on first run; no local install needed |
| Network | yes, first build only | Gradle fetches dependencies from Maven Central |
| libpcap | any recent | Needed at runtime for capture, not for compiling |

`gradlew` must be executable. If you cloned on Windows or the permission bits
were lost:

```bash
chmod +x gradlew
```

`jnetpcap` is not on Maven Central, so the build compiles against the jar that
ships in this repository (`jnetpcap/linux/jnetpcap-1.4.r1425/jnetpcap.jar`). The
Java classes are identical across platforms; only the native library differs, and
that is loaded at run time from `jnetpcap/<os>`. The Maven build in `pom.xml`
still refers to the old coordinate and is not the supported path — use Gradle.

## Build

```bash
export JAVA_HOME=/path/to/jdk8
./gradlew --no-daemon clean test fatJarCli fatJarGui fatJarLegacy
```

Three jars land in `build/libs`:

| Jar | Main class | Use |
|---|---|---|
| `cicflowmeter-cli-4.1-enhanced.jar` | `cic.cs.unb.ca.ifm.CLIMain` | Five subcommands, capture and offline analysis |
| `cicflowmeter-gui-4.1-enhanced.jar` | `cic.cs.unb.ca.ifm.App` | Swing interface |
| `cicflowmeter-legacy-4.1-enhanced.jar` | `cic.cs.unb.ca.ifm.Cmd` | Original two-argument command line |

`./gradlew build` alone also produces the application distribution and runs the
tests; the three tasks above are what the release builds.

## Run without packaging

```bash
java -Djava.library.path=jnetpcap/linux/jnetpcap-1.4.r1425 \
     -jar build/libs/cicflowmeter-cli-4.1-enhanced.jar --help
```

On Windows use `jnetpcap/win/jnetpcap-1.4.r1425` as the library path.

## Package the distributions

```bash
bash packaging/package.sh
```

Output in `dist/`:

```
cicflowmeter-4.1-enhanced-standalone-linux-x86_64.tar.gz
cicflowmeter-4.1-enhanced-standalone-windows-x64.zip
SHA256SUMS.txt
```

Each archive contains `jars/`, `lib/native/` (`.so` or `.dll`), `bin/` launchers,
`config/` samples and a `data/` directory for CSV output.

## Tests

```bash
./gradlew test
```

The suite covers the time-rule manager (`TimeRuleManagerTest`). It needs no
network capture and no pcap files.

## Container

```bash
docker build -f container/Dockerfile -t cicflowmeter:4.1-enhanced .
```

The Dockerfile compiles the jars inside the image, so the build context must be
the repository root. See [container/README.md](../container/README.md).

## Troubleshooting

**`Unsupported class file major version`** — Gradle 4.2 is running on a JDK newer
than 8. Point `JAVA_HOME` at a JDK 8 install.

**Gradle cannot reach Maven Central** — the build declares `mavenCentral()`; if
your network needs a mirror, add it to the `repositories` block in
`build.gradle`. Do not upgrade Gradle: the build uses the `compile` configuration,
which Gradle 7 removed.

**`Failed to load library jnetpcap`** — pass `-Djava.library.path` pointing at the
`jnetpcap` directory that matches your OS, or use the packaged launchers, which
set it for you.

**Permission denied on capture** — grant the JVM raw-socket capability:

```bash
sudo setcap cap_net_raw,cap_net_admin=eip "$(readlink -f "$(which java)")"
```
