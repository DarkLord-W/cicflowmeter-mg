#!/bin/sh
# Entry point for the CICFlowMeter-MG image.
#
#   docker run ... ghcr.io/<owner>/cicflowmeter:4.1-enhanced capture -i eth0 --health-check
#   docker run ... ghcr.io/<owner>/cicflowmeter:4.1-enhanced cli --help
#   docker run ... ghcr.io/<owner>/cicflowmeter:4.1-enhanced legacy /pcaps /out
#
# The first argument selects the jar: cli (default), gui or legacy. Everything
# after it is passed to the program unchanged.

set -eu

JAR_DIR="${CIC_JAR_DIR:-/opt/cicflowmeter/jars}"
CONFIG_DIR="${CICFLOWMETER_CONFIG_DIR:-/var/lib/cicflowmeter}"
SAMPLE_DIR="/etc/cicflowmeter/config-examples"

mode="cli"
case "${1:-}" in
    cli|gui|legacy)
        mode="$1"
        shift
        ;;
esac

jar="$(ls "$JAR_DIR"/cicflowmeter-"$mode"-*.jar 2>/dev/null | head -1 || true)"
if [ -z "${jar:-}" ]; then
    echo "entrypoint: no jar for mode '$mode' under $JAR_DIR" >&2
    exit 2
fi

# Seed the config directory on first start; never overwrite existing files.
mkdir -p "$CONFIG_DIR"
if [ -d "$SAMPLE_DIR" ]; then
    for f in time_rules.example.json api_config.json; do
        [ -f "$SAMPLE_DIR/$f" ] || continue
        target="$CONFIG_DIR/${f%.example.json}.json"
        [ "$f" = "time_rules.example.json" ] && target="$CONFIG_DIR/time_rules.json"
        [ -f "$target" ] || cp "$SAMPLE_DIR/$f" "$target" 2>/dev/null || true
    done
fi

exec java ${JAVA_OPTS:-} \
    -Djava.library.path=/opt/cicflowmeter/lib/native \
    -Dlog4j.configurationFile="${LOG4J_CONFIGURATION_FILE:-/etc/cicflowmeter/log4j2.xml}" \
    -Dcicflowmeter.config.dir="$CONFIG_DIR" \
    -Dcicflowmeter.health.port="${CIC_HEALTH_PORT:-8888}" \
    -jar "$jar" "$@"
