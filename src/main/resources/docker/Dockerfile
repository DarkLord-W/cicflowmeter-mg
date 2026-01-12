# CICFlowMeter Dockerfile
# 多阶段构建：编译阶段 + 运行阶段

# 阶段1: 编译阶段
FROM eclipse-temurin:8-jdk AS builder

WORKDIR /build

# 复制构建文件
COPY build.gradle .
COPY gradlew .
COPY gradle/ gradle/
COPY src/ src/

# 设置权限
RUN chmod +x gradlew

# 下载依赖并构建
RUN ./gradlew build -x test

# 阶段2: 运行阶段
FROM eclipse-temurin:8-jre

# 创建非root用户
RUN groupadd -r cicflowmeter && \
    useradd -r -g cicflowmeter -d /var/lib/cicflowmeter -s /sbin/nologin cicflowmeter

# 安装必要的包（用于网络抓包）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpcap-dev \
    tcpdump \
    && rm -rf /var/lib/apt/lists/*

# 创建目录结构
RUN mkdir -p /usr/share/cicflowmeter /etc/cicflowmeter /var/lib/cicflowmeter /var/log/cicflowmeter

# 设置目录权限
RUN chown -R cicflowmeter:cicflowmeter /usr/share/cicflowmeter /etc/cicflowmeter /var/lib/cicflowmeter /var/log/cicflowmeter
RUN chmod 755 /usr/share/cicflowmeter && \
    chmod 755 /etc/cicflowmeter && \
    chmod 700 /var/lib/cicflowmeter && \
    chmod 755 /var/log/cicflowmeter

# 从构建阶段复制文件
COPY --from=builder /build/build/distributions/CICFlowMeter-4.1/lib/* /usr/share/cicflowmeter/lib/
COPY --from=builder /build/build/distributions/CICFlowMeter-4.1/bin/ /usr/share/cicflowmeter/bin/
COPY --from=builder /build/build/distributions/CICFlowMeter-4.1/lib/native/ /usr/share/cicflowmeter/lib/native/

# 复制配置文件
COPY src/main/resources/log4j2.xml /etc/cicflowmeter/
COPY src/main/resources/config-examples/ /etc/cicflowmeter/config-examples/

# 复制启动脚本
COPY src/main/resources/docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8888/health || exit 1

# 切换到非root用户
USER cicflowmeter

# 工作目录
WORKDIR /var/lib/cicflowmeter

# 环境变量
ENV JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV LOG4J_CONFIGURATION_FILE="/etc/cicflowmeter/log4j2.xml"
ENV CICFLOWMETER_CONFIG_DIR="/var/lib/cicflowmeter"
ENV CICFLOWMETER_HEALTH_PORT="8888"

# 暴露健康检查端口
EXPOSE 8888

# 入口点
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

# 默认命令
CMD ["capture", "--interface", "eth0", "--health-check"]