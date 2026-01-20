#!/usr/bin/env bash
# Build the runnable distributions of CICFlowMeter-MG.
#
# Output (in ./dist):
#   cicflowmeter-<version>-standalone-linux-x86_64.tar.gz
#   cicflowmeter-<version>-standalone-windows-x64.zip
#   SHA256SUMS.txt
#
# Requirements: JDK 8, network access for Gradle dependencies, tar, zip, sha256sum.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
STAGE_DIR="$PROJECT_DIR/.dist-stage"

cd "$PROJECT_DIR"

VERSION="$(sed -n "s/^version = '\(.*\)'/\1/p" build.gradle | head -1)"
if [ -z "$VERSION" ]; then
    echo "ERROR: cannot read 'version' from build.gradle" >&2
    exit 1
fi

APPS="cli gui legacy"
BASE="cicflowmeter-${VERSION}"

echo "== CICFlowMeter-MG packaging: version ${VERSION} =="

echo "-- [1/5] build fat jars"
./gradlew --no-daemon clean fatJarCli fatJarGui fatJarLegacy

for app in $APPS; do
    jar="$BUILD_DIR/libs/cicflowmeter-${app}-${VERSION}.jar"
    if [ ! -f "$jar" ]; then
        echo "ERROR: missing $jar" >&2
        exit 1
    fi
done

echo "-- [2/5] stage trees"
rm -rf "$DIST_DIR" "$STAGE_DIR"
mkdir -p "$DIST_DIR"

stage() {
    dst="$1"
    platform="$2"

    mkdir -p "$dst/jars" "$dst/lib/native" "$dst/bin" "$dst/config" "$dst/data/daily"

    for app in $APPS; do
        cp "$BUILD_DIR/libs/cicflowmeter-${app}-${VERSION}.jar" "$dst/jars/"
    done

    if [ "$platform" = "linux" ]; then
        cp jnetpcap/linux/jnetpcap-1.4.r1425/*.so "$dst/lib/native/" 2>/dev/null || true
        for app in $APPS; do
            printf '#!/bin/sh\nDIR=$(cd "$(dirname "$0")" && pwd)\nexec java -Djava.library.path="$DIR/../lib/native" -jar "$DIR/../jars/cicflowmeter-%s-%s.jar" "$@"\n' \
                "$app" "$VERSION" > "$dst/bin/cicflowmeter-${app}"
            chmod 755 "$dst/bin/cicflowmeter-${app}"
        done
    else
        cp jnetpcap/win/jnetpcap-1.4.r1425/*.dll "$dst/lib/native/" 2>/dev/null || true
        for app in $APPS; do
            printf '@echo off\r\nset "DIR=%%~dp0"\r\njava -Djava.library.path="%%DIR%%..\\lib\\native" -jar "%%DIR%%..\\jars\\cicflowmeter-%s-%s.jar" %%*\r\n' \
                "$app" "$VERSION" > "$dst/bin/cicflowmeter-${app}.bat"
        done
    fi

    cp -a src/main/resources/config-examples/. "$dst/config/" 2>/dev/null || true
    cp src/main/resources/log4j2.xml "$dst/config/" 2>/dev/null || true
    cp src/main/resources/log4j2-cli.xml "$dst/config/" 2>/dev/null || true
    if [ -d src/main/resources/log4j2-layouts ]; then
        cp -a src/main/resources/log4j2-layouts "$dst/config/"
    fi
    cp LICENSE.txt "$dst/"
    [ -f NOTICE ] && cp NOTICE "$dst/"
    [ -f README.md ] && cp README.md "$dst/" || true
    printf 'CSV output of a capture or analysis run lands here.\n' > "$dst/data/README.md"
    printf 'Daily CSV files land here.\n' > "$dst/data/daily/README.md"
}

stage "$STAGE_DIR/linux" linux
stage "$STAGE_DIR/windows" windows

echo "-- [3/5] archives"
tar -czf "$DIST_DIR/${BASE}-standalone-linux-x86_64.tar.gz" -C "$STAGE_DIR/linux" .
( cd "$STAGE_DIR/windows" && zip -qr "$DIST_DIR/${BASE}-standalone-windows-x64.zip" . )
rm -rf "$STAGE_DIR"

echo "-- [4/5] checksums"
( cd "$DIST_DIR" && sha256sum *.tar.gz *.zip > SHA256SUMS.txt )

echo "-- [5/5] done"
ls -lh "$DIST_DIR"
