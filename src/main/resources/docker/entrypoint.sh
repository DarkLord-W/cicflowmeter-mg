#!/bin/bash
set -e

# CICFlowMeter Docker 入口点脚本

# 检查配置目录是否存在
if [ ! -d "${CICFLOWMETER_CONFIG_DIR}" ]; then
    echo "配置目录不存在: ${CICFLOWMETER_CONFIG_DIR}"
    exit 1
fi

# 检查是否包含配置文件
if [ ! -f "${CICFLOWMETER_CONFIG_DIR}/time_rules.json" ]; then
    echo "配置文件不存在，复制示例配置..."
    cp /etc/cicflowmeter/config-examples/time_rules.example.json \
       "${CICFLOWMETER_CONFIG_DIR}/time_rules.json"
    echo "示例配置文件已复制到 ${CICFLOWMETER_CONFIG_DIR}/time_rules.json"
fi

# 设置Java参数
JAVA_OPTS="${JAVA_OPTS} \
    -Djava.library.path=/usr/share/cicflowmeter/lib/native \
    -Dlog4j.configurationFile=${LOG4J_CONFIGURATION_FILE} \
    -Dcicflowmeter.config.dir=${CICFLOWMETER_CONFIG_DIR} \
    -Dcicflowmeter.health.port=${CICFLOWMETER_HEALTH_PORT}"

# 构建完整的Java命令
JAVA_CMD="java ${JAVA_OPTS} -jar /usr/share/cicflowmeter/lib/cicflowmeter-4.1.jar"

# 添加用户提供的参数
if [ $# -gt 0 ]; then
    FULL_CMD="${JAVA_CMD} $@"
else
    FULL_CMD="${JAVA_CMD}"
fi

echo "启动 CICFlowMeter..."
echo "命令: ${FULL_CMD}"
echo "配置目录: ${CICFLOWMETER_CONFIG_DIR}"
echo "健康检查端口: ${CICFLOWMETER_HEALTH_PORT}"
echo "日志配置: ${LOG4J_CONFIGURATION_FILE}"

# 执行命令
exec ${FULL_CMD}