# CICFlowMeter-MG runtime image.
#
# Build from the repository root so the Java sources are in the context:
#   docker build -f container/Dockerfile -t cicflowmeter:4.1-enhanced .
#
# Stage 1 compiles the three fat jars; stage 2 only carries the jars plus the
# jnetpcap native library and the config samples. Nothing is copied in
# pre-built, so the image always matches the checked-out commit.

FROM eclipse-temurin:8-jdk AS builder

WORKDIR /src

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY jnetpcap/ jnetpcap/
COPY src/ src/

RUN chmod +x gradlew \
 && ./gradlew --no-daemon fatJarCli fatJarGui fatJarLegacy

FROM eclipse-temurin:8-jre

ARG VERSION=4.1-enhanced

LABEL org.opencontainers.image.title="CICFlowMeter-MG" \
      org.opencontainers.image.description="Network flow feature extractor (enhanced CICFlowMeter 4.1 fork)" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.authors="CICFlowMeter-MG Maintainers" \
      org.opencontainers.image.source="https://github.com/OWNER/cicflowmeter-mg"

# libpcap for capture, tcpdump for debugging, curl for the health check probe.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libpcap0.8 libpcap-dev tcpdump curl \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd -r cicflowmeter \
 && useradd -r -g cicflowmeter -d /var/lib/cicflowmeter -s /sbin/nologin cicflowmeter \
 && mkdir -p /opt/cicflowmeter/jars /opt/cicflowmeter/lib/native /etc/cicflowmeter \
             /var/lib/cicflowmeter /var/log/cicflowmeter \
 && chown -R cicflowmeter:cicflowmeter /var/lib/cicflowmeter /var/log/cicflowmeter

COPY --from=builder /src/build/libs/cicflowmeter-*.jar /opt/cicflowmeter/jars/
COPY --from=builder /src/jnetpcap/linux/jnetpcap-1.4.r1425/*.so /opt/cicflowmeter/lib/native/
COPY src/main/resources/log4j2.xml src/main/resources/log4j2-cli.xml /etc/cicflowmeter/
COPY src/main/resources/log4j2-layouts/ /etc/cicflowmeter/log4j2-layouts/
COPY src/main/resources/config-examples/ /etc/cicflowmeter/config-examples/
COPY container/entrypoint.sh /usr/local/bin/entrypoint.sh

RUN chmod 755 /usr/local/bin/entrypoint.sh

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${CIC_HEALTH_PORT}/health" || exit 1

ENV CIC_HOME=/opt/cicflowmeter \
    CICFLOWMETER_CONFIG_DIR=/var/lib/cicflowmeter \
    CIC_HEALTH_PORT=8888 \
    LOG4J_CONFIGURATION_FILE=/etc/cicflowmeter/log4j2.xml \
    JAVA_OPTS="-Xmx1g -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Capture needs NET_RAW / NET_ADMIN on the host:
#   docker run --cap-add=NET_RAW --cap-add=NET_ADMIN ...
USER cicflowmeter
WORKDIR /var/lib/cicflowmeter
EXPOSE 8888

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["--help"]
