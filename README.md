# CICFlowMeter V4.1 增强版

[![Java](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE.txt)
[![Version](https://img.shields.io/badge/Version-4.1--enhanced-orange.svg)]()

**CICFlowMeter** 是一个功能强大的网络流量分析工具，能够从实时流量或PCAP文件中提取85个网络流量特征。支持时间规则标签系统、实时API发送、健康检查和企业级部署。

## ✨ 核心特性

- ✅ **实时流量监控** - 捕获网络接口数据包并提取特征
- ✅ **离线PCAP分析** - 分析现有的PCAP文件
- ✅ **85个流量特征** - 基于CICIDS2017数据集标准
- ✅ **时间规则标签系统** - 自动为流量打上时间标签
- ✅ **多运行模式** - GUI界面、命令行接口、服务器部署
- ✅ **API集成** - 实时发送特征数据到远程API
- ✅ **健康检查** - 内置健康检查服务器和监控端点
- ✅ **企业级部署** - 支持Docker、systemd、安全增强

## 🚀 快速开始

### 系统要求
- **Java**: OpenJDK 8 或更高版本 (推荐Java 11)
- **操作系统**: Linux (推荐), macOS, Windows
- **构建工具**: Gradle 或 Maven (可选，分发版本无需构建)

### 方式一：使用分发版本 (推荐)

#### 1. 下载并解压
```bash
# 下载最新分发版本
wget https://github.com/your-repo/CICFlowMeter/releases/download/v4.1/CICFlowMeter-4.1-enhanced.zip
unzip CICFlowMeter-4.1-enhanced.zip
cd CICFlowMeter-4.1-enhanced
```

#### 2. 授予网络抓包权限 (Linux/macOS)
```bash
sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))
```

#### 3. 运行应用程序
```bash
# 图形界面 (GUI)
./bin/cicflowmeter-gui

# 命令行界面 (CLI) - 实时监控
sudo ./bin/cicflowmeter-cli capture --interface eth0 --health-check

# 命令行界面 - 离线分析
./bin/cicflowmeter-cli analyze /path/to/pcaps/ /output/dir/
```

### 方式二：从源代码构建

#### 1. 克隆项目
```bash
git clone https://github.com/your-repo/CICFlowMeter_MG_V3.git
cd CICFlowMeter_MG_V3
```

#### 2. 安装jnetpcap本地库
```bash
# Linux
cd jnetpcap/linux/jnetpcap-1.4.r1425
mvn install:install-file -Dfile=jnetpcap.jar -DgroupId=org.jnetpcap -DartifactId=jnetpcap -Dversion=1.4.1 -Dpackaging=jar

# Windows
cd jnetpcap/win/jnetpcap-1.4.r1425
mvn install:install-file -Dfile=jnetpcap.jar -DgroupId=org.jnetpcap -DartifactId=jnetpcap -Dversion=1.4.1 -Dpackaging=jar
```

#### 3. 构建项目
```bash
# 使用Gradle (推荐)
./gradlew build

# 或使用Maven
mvn clean package
```

#### 4. 运行应用程序
```bash
# GUI界面
sudo ./gradlew execute

# CLI界面 - 实时监控
sudo ./gradlew executeCLI -Pargs="capture --interface eth0 --health-check"

# CLI界面 - 离线分析
./gradlew executeCLI -Pargs="analyze /path/to/pcaps/ /output/dir/"

# 传统命令行
./gradlew exeCMD -Pargs="/path/to/pcaps/ /output/dir/"
```

## 📖 详细使用指南

### 命令行界面 (CLI) 快速参考

#### 实时流量捕获
```bash
# 基本实时监控
sudo ./bin/cicflowmeter-cli capture --interface eth0 --health-check

# 监控特定端口 (HTTP/HTTPS)
sudo ./bin/cicflowmeter-cli capture --interface eth0 --filter "tcp port 80 or tcp port 443"

# 应用时间规则标签
sudo ./bin/cicflowmeter-cli capture --interface eth0 --health-check --time_label

# 自定义输出目录
sudo ./bin/cicflowmeter-cli capture --interface eth0 --output /var/log/cicflowmeter/csv/
```

#### 离线PCAP分析
```bash
# 分析单个PCAP文件
./bin/cicflowmeter-cli analyze capture.pcap ./output/

# 分析目录下所有PCAP文件
./bin/cicflowmeter-cli analyze /data/pcaps/ /data/results/

# 使用传统命令行 (相同功能)
./bin/cicflowmeter-legacy /data/pcaps/ /data/results/
```

#### 系统管理
```bash
# 查看系统状态
./bin/cicflowmeter-cli status

# 管理配置
./bin/cicflowmeter-cli config --list
./bin/cicflowmeter-cli config --export backup.json
./bin/cicflowmeter-cli config --import config.json

# 健康检查服务器
./bin/cicflowmeter-cli health --start --port 8888
curl http://localhost:8888/health
```

### 配置文件说明

#### 时间规则配置文件
位置: `~/.cicflowmeter/time_rules.json`

**示例配置:**
```json
{
  "rules": [
    {
      "ruleId": "uuid-here",
      "ruleName": "办公时间流量",
      "startDateTime": "2026-01-11 09:00:00",
      "endDateTime": "2026-01-11 18:00:00",
      "label": "BENIGN",
      "groupId": "default",
      "enabled": true,
      "priority": 10
    }
  ],
  "groups": [
    {
      "groupId": "default",
      "groupName": "默认分组",
      "description": "未指定分组的规则",
      "enabled": true
    }
  ]
}
```

#### 预定义标签 (15个)
```
BENIGN
FTP-Patator
SSH-Patator
DoS slowloris
DoS Slowhttptest
DoS Hulk
DoS GoldenEye
Heartbleed
Web Attack – Brute Force
Web Attack – XSS
Web Attack – SQL Injection
Infiltration
Bot
PortScan
DDoS
```

**无匹配时默认标签:** `NeedManualLabel`

### API集成

#### 默认API配置
- **API URL**: `http://127.0.0.1:5000/flows`
- **启用状态**: 默认启用
- **数据格式**: JSON (包含结构化数据和原始CSV)

#### 测试API服务器
```bash
# 启动Python测试服务器
python3 test_api_fixed.py

# 访问Web界面
# http://127.0.0.1:5000/
```

## 🐳 Docker 部署

### 快速启动
```bash
# 使用预构建镜像
docker run -d \
  --name cicflowmeter \
  --network host \
  --cap-add=NET_RAW --cap-add=NET_ADMIN \
  -v /var/lib/cicflowmeter:/var/lib/cicflowmeter \
  cicflowmeter:4.1 \
  capture --interface eth0 --health-check
```

### Docker Compose
```yaml
version: '3.8'
services:
  cicflowmeter:
    image: cicflowmeter:4.1
    network_mode: host
    cap_add:
      - NET_RAW
      - NET_ADMIN
    volumes:
      - cicflowmeter-data:/var/lib/cicflowmeter
    command: capture --interface eth0 --health-check --health-port 8888
    restart: unless-stopped
```

## 🏗️ 系统服务部署 (Linux)

### systemd服务安装
```bash
# 1. 复制服务文件
sudo cp config/systemd/cicflowmeter.service /etc/systemd/system/

# 2. 重新加载配置
sudo systemctl daemon-reload

# 3. 启用并启动服务
sudo systemctl enable cicflowmeter
sudo systemctl start cicflowmeter

# 4. 查看状态
sudo systemctl status cicflowmeter

# 5. 查看日志
sudo journalctl -u cicflowmeter -f
```

## 🔧 故障排除

### 常见问题

#### 1. 网络抓包权限不足
```bash
# 解决方案: 授予Java网络权限
sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))

# 或临时使用sudo
sudo ./bin/cicflowmeter-cli capture --interface eth0
```

#### 2. 端口冲突 (健康检查服务器)
```bash
# 使用不同端口
./bin/cicflowmeter-cli capture --interface eth0 --health-check --health-port 8889
```

#### 3. 配置文件权限错误
```bash
chmod 600 ~/.cicflowmeter/*.json
chmod 700 ~/.cicflowmeter
```

#### 4. Java版本问题
```bash
# 检查Java版本
java -version

# 安装Java 11 (Ubuntu/Debian)
sudo apt update
sudo apt install openjdk-11-jdk
```

#### 5. API配置问题
```bash
# 查看当前API配置
./bin/cicflowmeter-cli config --list

# 设置API端点
./bin/cicflowmeter-cli config --set api.url="http://your-server:5000/predict"

# 禁用API发送（仅本地分析）
./bin/cicflowmeter-cli config --set api.enabled=false

# 设置自定义CSV保存路径
./bin/cicflowmeter-cli config --set csv.path="/var/lib/cicflowmeter/csv/"

# 重置为默认配置
./bin/cicflowmeter-cli config --reset
```

**常见API配置错误:**
- **API连接失败**: 检查API服务器是否运行，网络是否可达
- **配置未保存**: 确保 `~/.cicflowmeter/` 目录有写权限
- **配置格式错误**: 使用 `config --reset` 重置，然后重新配置
- **URL包含引号**: 避免在URL周围使用引号，如 `http://example.com` 而不是 `"http://example.com"`

### 日志位置
- **应用程序日志**: `logs/cicflowmeter.log` (分发版本)
- **详细日志**: `logs/debug.log` (启用DEBUG级别时)
- **系统日志**: `journalctl -u cicflowmeter` (systemd服务)

## 📁 项目结构

```
CICFlowMeter_MG_V3/
├── src/                    # 源代码
├── dist/                   # 分发版本 (预构建)
│   ├── bin/               # 启动脚本
│   ├── config/            # 配置文件
│   ├── jars/              # 可执行JAR
│   └── lib/               # 依赖库
├── jnetpcap/              # jnetpcap本地库
├── data/                  # 示例数据
├── logs/                  # 日志目录
├── build.gradle           # Gradle构建配置
├── pom.xml                # Maven配置
├── CICFlowMeter_FULL_TUTORIAL.md  # 完整教程
├── TEST_API_ENHANCED_TUTORIAL.md  # API测试教程
├── SERVER_DEPLOYMENT.md   # 服务器部署指南
├── AGENTS.md              # AI代理指南
└── test_api*.py           # API测试脚本
```

## 📚 详细文档

| 文档 | 描述 | 链接 |
|------|------|------|
| **完整教程** | 详细功能说明和使用指南 | [CICFlowMeter_FULL_TUTORIAL.md](CICFlowMeter_FULL_TUTORIAL.md) |
| **API测试教程** | API集成和测试指南 | [TEST_API_ENHANCED_TUTORIAL.md](TEST_API_ENHANCED_TUTORIAL.md) |
| **服务器部署** | 生产环境部署指南 | [SERVER_DEPLOYMENT.md](SERVER_DEPLOYMENT.md) |
| **分发版本指南** | 预构建版本使用说明 | [dist/README.md](dist/README.md) |
| **AI代理指南** | 开发者和AI助手指南 | [AGENTS.md](AGENTS.md) |

## 🛠️ 开发与贡献

### 构建自定义版本
```bash
# 构建所有JAR文件
./gradlew fatJar

# 构建特定版本
./gradlew fatJarCli    # CLI版本
./gradlew fatJarGui    # GUI版本
./gradlew fatJarLegacy # 传统命令行版本

# 创建分发包
./gradlew distZip
```

### 运行测试
```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests cic.cs.unb.ca.TimeRuleManagerTest

# 运行特定测试方法
./gradlew test --tests *TimeRuleManagerTest.testSingleton
```

### 代码规范
- **Java版本**: Java 8 兼容
- **命名规范**: 类用PascalCase，方法/变量用camelCase
- **包结构**: `cic.cs.unb.ca.xxx`
- **缩进**: 4空格，无Tab

## 📞 支持与反馈

### 问题报告
1. 查看相关日志文件获取错误信息
2. 确认配置文件正确无误
3. 使用最小配置复现问题
4. 在GitHub Issues中提交问题报告

### 问题报告模板
```markdown
## 问题描述
[简要描述问题]

## 复现步骤
1. [步骤1]
2. [步骤2]
3. [步骤3]

## 环境信息
- 操作系统: [如 Ubuntu 20.04]
- Java版本: [java -version 输出]
- CICFlowMeter版本: [./bin/cicflowmeter-cli --version]

## 相关日志
[日志内容]
```

## 📄 许可证

本项目基于 MIT 许可证开源 - 查看 [LICENSE.txt](LICENSE.txt) 文件了解详情。

## 🙏 致谢

- 基于原始 CICFlowMeter 项目
- 使用 jnetpcap 进行网络抓包
- 包含 CICIDS2017 数据集特征定义
- 感谢所有贡献者和用户

---

**版本**: V4.1 增强版  
**最后更新**: 2026年1月11日  
**文档版本**: 2.0  

> 提示: 本文档会随项目更新而更新。最新版本请查看项目仓库。