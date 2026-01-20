package cic.cs.unb.ca.ifm;

import cic.cs.unb.ca.health.HealthCheckServer;
import cic.cs.unb.ca.ApiConfigManager;
import cic.cs.unb.ca.TimeRuleManager;
import cic.cs.unb.ca.jnetpcap.BasicFlow;
import cic.cs.unb.ca.jnetpcap.FlowFeature;
import cic.cs.unb.ca.jnetpcap.FlowGenerator;
import cic.cs.unb.ca.jnetpcap.PacketReader;
import cic.cs.unb.ca.jnetpcap.worker.ApiSendTask;
import cic.cs.unb.ca.jnetpcap.worker.FlowGenListener;
import cic.cs.unb.ca.jnetpcap.worker.InsertCsvRow;
import cic.cs.unb.ca.flow.FlowMgr;
import org.apache.commons.cli.*;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapBpfProgram;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;
import org.jnetpcap.nio.JMemory.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CICFlowMeter 命令行主入口
 * 统一命令行界面，支持多种操作模式
 */
public class CLIMain {
    
    private static final Logger logger = LoggerFactory.getLogger(CLIMain.class);
    private static final String APP_NAME = "CICFlowMeter";
    private static final String VERSION = "4.1-enhanced";
    
    // 实时捕获统计
    private static final AtomicInteger packetsCaptured = new AtomicInteger(0);
    private static final AtomicInteger flowsGenerated = new AtomicInteger(0);
    private static final AtomicInteger apiSent = new AtomicInteger(0);
    private static final AtomicInteger apiFailed = new AtomicInteger(0);
    private static volatile long lastUpdateTime = System.currentTimeMillis();
    private static volatile long startTime = System.currentTimeMillis();
    private static volatile boolean applyTimeLabels = false;
    
    // 全局选项
    private static final Options GLOBAL_OPTIONS = new Options();
    
    // 子命令解析器
    private static final CommandLineParser parser = new DefaultParser();
    private static final HelpFormatter helpFormatter = new HelpFormatter();
    
    static {
        // 全局选项
        GLOBAL_OPTIONS.addOption("h", "help", false, "显示帮助信息");
        GLOBAL_OPTIONS.addOption("v", "version", false, "显示版本信息");
        GLOBAL_OPTIONS.addOption("c", "config", true, "配置文件目录（默认: ~/.cicflowmeter）");
        GLOBAL_OPTIONS.addOption("l", "log-level", true, "日志级别（DEBUG, INFO, WARN, ERROR）");
        GLOBAL_OPTIONS.addOption("p", "health-port", true, "健康检查服务器端口（默认: 8888）");
    }
    
    public static void main(String[] args) {
        try {
            // 解析全局选项
            CommandLine globalCmd = parser.parse(GLOBAL_OPTIONS, args, true);
            
            // 处理全局选项
            if (globalCmd.hasOption("help")) {
                printGlobalHelp();
                return;
            }
            
            if (globalCmd.hasOption("version")) {
                System.out.println(APP_NAME + " " + VERSION);
                System.out.println("增强功能：时间规则标签系统、API集成、健康检查、CLI界面");
                return;
            }
            
            // 设置系统属性
            if (globalCmd.hasOption("config")) {
                System.setProperty("cicflowmeter.config.dir", globalCmd.getOptionValue("config"));
            }
            
            if (globalCmd.hasOption("health-port")) {
                System.setProperty("cicflowmeter.health.port", globalCmd.getOptionValue("health-port"));
            }
            
            // 获取剩余参数（子命令）
            String[] remainingArgs = globalCmd.getArgs();
            if (remainingArgs.length == 0) {
                System.err.println("错误: 需要指定子命令");
                printGlobalHelp();
                System.exit(1);
            }
            
            // 分发到子命令
            String subCommand = remainingArgs[0];
            String[] subCommandArgs = new String[remainingArgs.length - 1];
            System.arraycopy(remainingArgs, 1, subCommandArgs, 0, subCommandArgs.length);
            
            dispatchSubCommand(subCommand, subCommandArgs);
            
        } catch (ParseException e) {
            System.err.println("命令行解析错误: " + e.getMessage());
            printGlobalHelp();
            System.exit(1);
        } catch (Exception e) {
            logger.error("执行命令时发生错误", e);
            System.exit(1);
        }
    }
    
    /**
     * 分发到子命令处理器
     */
    private static void dispatchSubCommand(String subCommand, String[] args) {
        switch (subCommand.toLowerCase()) {
            case "capture":
                handleCapture(args);
                break;
            case "analyze":
                handleAnalyze(args);
                break;
            case "config":
                handleConfig(args);
                break;
            case "status":
                handleStatus(args);
                break;
            case "daemon":
                handleDaemon(args);
                break;
            case "health":
                handleHealth(args);
                break;
            default:
                System.err.println("错误: 未知子命令: " + subCommand);
                System.err.println("可用子命令: capture, analyze, config, status, daemon, health");
                System.exit(1);
        }
    }
    
    /**
     * 处理实时捕获命令
     */
    private static void handleCapture(String[] args) {
        Options captureOptions = new Options();
        captureOptions.addOption("h", "help", false, "显示帮助信息");
        captureOptions.addOption("i", "interface", true, "网络接口名称（例如: eth0）");
        captureOptions.addOption("f", "filter", true, "BPF过滤表达式");
        captureOptions.addOption("o", "output", true, "输出目录（默认: 当前目录）");
        captureOptions.addOption("t", "timeout", true, "流量超时时间（毫秒，默认: 120000）");
        captureOptions.addOption("a", "activity-timeout", true, "活动超时时间（毫秒，默认: 5000）");
        captureOptions.addOption("health", "health-check", false, "启用健康检查服务器");
        captureOptions.addOption("no_api", "no-api", false, "禁用API发送");
        captureOptions.addOption("no_csv", "no-csv", false, "禁用CSV导出");
        captureOptions.addOption(null, "time_label", false, "应用时间规则标签到流量数据");
        
        try {
            CommandLine cmd = parser.parse(captureOptions, args);
            
            if (cmd.hasOption("help")) {
                printCommandHelp("capture", captureOptions);
                return;
            }
            
            logger.info("开始实时网络流量捕获...");
            
            // 检查网络接口
            String interfaceName = cmd.getOptionValue("interface", "eth0");
            logger.info("使用网络接口: {}", interfaceName);
            
            // 启动健康检查服务器（如果启用）
            if (cmd.hasOption("health-check")) {
                HealthCheckServer healthServer = HealthCheckServer.getInstance();
                // 如果服务器已经在运行，显示当前状态
                if (healthServer.isRunning()) {
                    logger.info("健康检查服务器已在运行，端口: {}", healthServer.getPort());
                    logger.info("访问地址: http://localhost:{}/health", healthServer.getPort());
                } else {
                    // 尝试启动，如果失败则尝试备用端口
                    boolean started = healthServer.start();
                    if (!started) {
                        logger.warn("端口 {} 被占用，尝试备用端口...", healthServer.getPort());
                        int originalPort = healthServer.getPort();
                        healthServer.setPort(originalPort + 1);
                        if (healthServer.start()) {
                            logger.info("健康检查服务器已启动在备用端口: {}", healthServer.getPort());
                        } else {
                            logger.error("无法启动健康检查服务器，请检查端口占用情况");
                        }
                    } else {
                        logger.info("健康检查服务器已启动，端口: {}", healthServer.getPort());
                    }
                }
            }
            
            // 配置API和CSV
            ApiConfigManager apiConfig = ApiConfigManager.getInstance();
            if (cmd.hasOption("no_api")) {
                apiConfig.setApiEnabled(false);
                logger.info("API发送已禁用");
            }
            
            if (cmd.hasOption("no_csv")) {
                apiConfig.setCsvEnabled(false);
                logger.info("CSV导出已禁用");
            }
            
            // 配置时间标签
            applyTimeLabels = cmd.hasOption("time_label");
            if (applyTimeLabels) {
                logger.info("时间规则标签: 启用");
            } else {
                logger.info("时间规则标签: 禁用");
            }
            
            // 获取捕获参数
            long flowTimeout = Long.parseLong(cmd.getOptionValue("timeout", "120000")) * 1000L; // 微秒
            long activityTimeout = Long.parseLong(cmd.getOptionValue("activity-timeout", "5000")) * 1000L; // 微秒
            String outputDir = cmd.getOptionValue("output", ".");
            String filter = cmd.getOptionValue("filter", "");
            
            logger.info("流量超时: {} 微秒, 活动超时: {} 微秒", flowTimeout, activityTimeout);
            logger.info("输出目录: {}", outputDir);
            if (!filter.isEmpty()) {
                logger.info("BPF过滤器: {}", filter);
            }
            
            // 创建线程池用于API发送
            ExecutorService apiExecutor = Executors.newFixedThreadPool(2);
            
            // 创建FlowGenerator和监听器
            FlowGenerator flowGen = new FlowGenerator(true, flowTimeout, activityTimeout);
            
            // CLI流量监听器
            FlowGenListener cliListener = new FlowGenListener() {
                @Override
                public void onFlowGenerated(BasicFlow flow) {
                    flowsGenerated.incrementAndGet();
                    lastUpdateTime = System.currentTimeMillis();
                    
                    // 提取流量特征
                    String flowDump = flow.dumpFlowBasedFeaturesEx();
                    String flowHeader = FlowFeature.getHeader();
                    
                    // 应用时间规则标签（如果启用）
                    if (applyTimeLabels) {
                        try {
                            long flowStartTime = flow.getFlowStartTime(); // 微秒时间戳
                            String timeLabel = TimeRuleManager.getInstance().getLabelForTimestamp(flowStartTime);
                            
                            // 替换CSV数据中的标签列（假设最后一列是Label）
                            flowDump = applyTimeLabelToCsv(flowDump, timeLabel);
                            
                        } catch (Exception e) {
                            logger.warn("应用时间标签失败: {}", e.getMessage());
                        }
                    }
                    
                    // 记录到控制台（每10个流量显示一次）
                    if (flowsGenerated.get() % 10 == 0) {
                        System.out.printf("\r[捕获统计] 流量: %d, 包: %d, API发送: %d, API失败: %d",
                            flowsGenerated.get(), packetsCaptured.get(),
                            ApiSendTask.getTotalSent(), ApiSendTask.getTotalFailed());
                    }
                    
                    // 发送到API（如果启用）
                    if (apiConfig.isApiEnabled()) {
                        ApiSendTask apiTask = new ApiSendTask(
                            apiConfig.getApiUrl(),
                            flowDump,
                            flowHeader,
                            true
                        );
                        apiExecutor.execute(apiTask);
                    }
                    
                    // 保存到CSV（如果启用）
                    if (apiConfig.isCsvEnabled()) {
                        try {
                            List<String> flowStringList = new ArrayList<>();
                            flowStringList.add(flowDump);
                            cic.cs.unb.ca.jnetpcap.worker.InsertCsvRow.insert(
                                flowHeader,
                                flowStringList,
                                outputDir,
                                "real_time_capture" + FlowMgr.FLOW_SUFFIX
                            );
                        } catch (Exception e) {
                            logger.warn("CSV保存失败: {}", e.getMessage());
                        }
                    }
                }
            };
            
            flowGen.addFlowListener(cliListener);
            
            // 启动统计显示线程
            Thread statsThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(5000); // 每5秒更新一次
                        printCaptureStats(apiConfig);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            statsThread.setDaemon(true);
            statsThread.start();
            
            // 启动实时捕获
            logger.info("开始实时捕获 {} 上的流量...", interfaceName);
            logger.info("按Ctrl+C停止捕获...");
            
            StringBuilder errbuf = new StringBuilder();
            int snaplen = 64 * 1024;
            int promiscous = Pcap.MODE_PROMISCUOUS;
            int timeout = 60 * 1000; // 毫秒
            
            Pcap pcap = Pcap.openLive(interfaceName, snaplen, promiscous, timeout, errbuf);
            if (pcap == null) {
                logger.error("无法打开接口 {}: {}", interfaceName, errbuf.toString());
                System.exit(1);
            }
            
            // 应用BPF过滤器（如果指定）
            if (!filter.isEmpty()) {
                try {
                    PcapBpfProgram program = new PcapBpfProgram();
                    int optimize = 0; // 0 = false
                    int netmask = 0;
                    if (pcap.compile(program, filter, optimize, netmask) != Pcap.OK) {
                        logger.warn("BPF过滤器编译失败: {}", pcap.getErr());
                    } else {
                        pcap.setFilter(program);
                        logger.info("应用BPF过滤器: {}", filter);
                    }
                } catch (Exception e) {
                    logger.warn("BPF过滤器应用失败: {}", e.getMessage());
                }
            }
            
            // 包处理器
            PcapPacketHandler<String> packetHandler = (packet, user) -> {
                packetsCaptured.incrementAndGet();
                lastUpdateTime = System.currentTimeMillis();
                
                // 创建永久包副本以避免线程问题
                PcapPacket permanent = new PcapPacket(Type.POINTER);
                packet.transferStateAndDataTo(permanent);
                
                // 处理包
                flowGen.addPacket(PacketReader.getBasicPacketInfo(permanent, true, false));
            };
            
            // 开始捕获循环
            logger.info("捕获已启动，正在接收数据包...");
            printCaptureStats(apiConfig);
            
            int ret = pcap.loop(Pcap.LOOP_INFINITE, packetHandler, interfaceName);
            
            if (ret == 0) {
                logger.info("捕获正常结束");
            } else if (ret == -1) {
                logger.error("捕获错误: {}", pcap.getErr());
            } else if (ret == -2) {
                logger.info("捕获被中断");
            }
            
            // 清理资源
            pcap.close();
            apiExecutor.shutdown();
            statsThread.interrupt();
            
        } catch (ParseException e) {
            System.err.println("捕获命令解析错误: " + e.getMessage());
            printCommandHelp("capture", captureOptions);
            System.exit(1);
        }
    }
    
    /**
     * 处理离线分析命令（现有Cmd.java功能）
     */
    private static void handleAnalyze(String[] args) {
        logger.info("启动离线PCAP分析...");
        
        if (args.length < 1) {
            System.err.println("错误: 需要指定PCAP文件或目录");
            System.err.println("用法: cfm analyze <pcap文件或目录> [输出目录]");
            System.exit(1);
        }
        
        // 调用现有的Cmd.main方法
        // 由于Cmd.main需要特定参数格式，我们直接传递参数
        String[] cmdArgs = args;
        Cmd.main(cmdArgs);
    }
    
    /**
     * 处理配置管理命令
     */
    private static void handleConfig(String[] args) {
        Options configOptions = new Options();
        configOptions.addOption("h", "help", false, "显示帮助信息");
        configOptions.addOption("l", "list", false, "列出当前配置");
        configOptions.addOption("s", "show", false, "显示详细信息");
        configOptions.addOption(null, "set", true, "设置配置值（格式: key=value）");
        configOptions.addOption(null, "get", true, "获取配置值");
        configOptions.addOption("i", "import", true, "从JSON文件导入规则");
        configOptions.addOption("e", "export", true, "导出规则到JSON文件");
        configOptions.addOption("r", "reset", false, "重置为默认配置");
        configOptions.addOption("g", "groups", false, "管理规则分组");
        
        try {
            CommandLine cmd = parser.parse(configOptions, args);
            
            if (cmd.hasOption("help")) {
                printCommandHelp("config", configOptions);
                return;
            }
            
            TimeRuleManager ruleManager = TimeRuleManager.getInstance();
            
            if (cmd.hasOption("list")) {
                ApiConfigManager apiConfig = ApiConfigManager.getInstance();
                
                System.out.println("配置概览:");
                System.out.println();
                
                System.out.println("API配置:");
                System.out.println("  API URL: " + apiConfig.getApiUrl());
                System.out.println("  API发送: " + (apiConfig.isApiEnabled() ? "启用" : "禁用"));
                System.out.println("  CSV导出: " + (apiConfig.isCsvEnabled() ? "启用" : "禁用"));
                System.out.println("  自定义CSV路径: " + (apiConfig.getCsvSavePath() != null ? apiConfig.getCsvSavePath() : "(使用默认)"));
                System.out.println();
                
                System.out.println("时间规则配置:");
                System.out.println("  规则总数: " + ruleManager.getTotalRuleCount());
                System.out.println("  启用规则: " + ruleManager.getEnabledRuleCount());
                System.out.println("  分组数量: " + ruleManager.getTotalGroupCount());
                System.out.println("  启用分组: " + ruleManager.getEnabledGroupCount());
                return;
            }
            
            if (cmd.hasOption("show")) {
                // 显示详细信息
                System.out.println("详细配置信息:");
                System.out.println("配置文件目录: " + System.getProperty("user.home") + "/.cicflowmeter");
                System.out.println();
                
                // API配置信息
                ApiConfigManager apiConfig = ApiConfigManager.getInstance();
                System.out.println("API配置:");
                System.out.println("  配置文件: " + System.getProperty("user.home") + "/.cicflowmeter/api_config.json");
                System.out.println("  API URL: " + apiConfig.getApiUrl());
                System.out.println("  API发送: " + (apiConfig.isApiEnabled() ? "启用" : "禁用"));
                System.out.println("  CSV导出: " + (apiConfig.isCsvEnabled() ? "启用" : "禁用"));
                System.out.println("  自定义CSV路径: " + (apiConfig.getCsvSavePath() != null ? apiConfig.getCsvSavePath() : "(使用默认)"));
                System.out.println();
                
                // 时间规则信息
                System.out.println("时间规则配置:");
                System.out.println("  配置文件: " + System.getProperty("user.home") + "/.cicflowmeter/time_rules.json");
                System.out.println("  规则总数: " + ruleManager.getTotalRuleCount());
                System.out.println("  启用规则: " + ruleManager.getEnabledRuleCount());
                System.out.println("  分组数量: " + ruleManager.getTotalGroupCount());
                System.out.println("  启用分组: " + ruleManager.getEnabledGroupCount());
                return;
            }
            
            if (cmd.hasOption("import")) {
                String importFile = cmd.getOptionValue("import");
                logger.info("从文件导入规则: {}", importFile);
                // TODO: 实现导入功能
                return;
            }
            
            if (cmd.hasOption("export")) {
                String exportFile = cmd.getOptionValue("export");
                logger.info("导出规则到文件: {}", exportFile);
                // TODO: 实现导出功能
                return;
            }
            
            if (cmd.hasOption("reset")) {
                logger.info("重置配置为默认值...");
                ruleManager.clearAllRules();
                ruleManager.clearAllGroups();
                ApiConfigManager.getInstance().resetToDefaults();
                logger.info("配置已重置");
                return;
            }
            
            if (cmd.hasOption("set")) {
                String setValue = cmd.getOptionValue("set");
                if (setValue != null && setValue.contains("=")) {
                    String[] parts = setValue.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    handleConfigSet(key, value);
                } else {
                    System.err.println("错误: --set 参数格式应为 key=value");
                    System.err.println("示例: --set api.url=\"http://127.0.0.1:5000/flows\"");
                    System.exit(1);
                }
                return;
            }
            
            if (cmd.hasOption("get")) {
                String key = cmd.getOptionValue("get");
                if (key != null && !key.trim().isEmpty()) {
                    handleConfigGet(key.trim());
                } else {
                    System.err.println("错误: --get 需要指定配置键");
                    System.err.println("示例: --get api.url");
                    System.exit(1);
                }
                return;
            }
            
            // 如果没有指定任何选项，显示帮助
            printCommandHelp("config", configOptions);
            
        } catch (ParseException e) {
            System.err.println("配置命令解析错误: " + e.getMessage());
            printCommandHelp("config", configOptions);
            System.exit(1);
        }
    }
    
    /**
     * 处理配置设置
     */
    private static void handleConfigSet(String key, String value) {
        ApiConfigManager apiConfig = ApiConfigManager.getInstance();
        
        switch (key.toLowerCase()) {
            case "api.url":
                apiConfig.setApiUrl(value);
                break;
            case "api.enabled":
                apiConfig.setApiEnabled(Boolean.parseBoolean(value));
                break;
            case "csv.enabled":
                apiConfig.setCsvEnabled(Boolean.parseBoolean(value));
                break;
            case "csv.path":
                apiConfig.setCsvSavePath(value.equals("null") ? null : value);
                break;
            default:
                System.err.println("错误: 未知配置键: " + key);
                System.err.println("可用配置键:");
                System.err.println("  api.url - API端点URL");
                System.err.println("  api.enabled - 启用/禁用API发送");
                System.err.println("  csv.enabled - 启用/禁用CSV导出");
                System.err.println("  csv.path - CSV保存路径（使用null重置为默认）");
                System.exit(1);
                return;
        }
        
        System.out.println("配置已更新: " + key + " = " + value);
    }
    
    /**
     * 处理配置获取
     */
    private static void handleConfigGet(String key) {
        ApiConfigManager apiConfig = ApiConfigManager.getInstance();
        
        switch (key.toLowerCase()) {
            case "api.url":
                System.out.println(apiConfig.getApiUrl());
                break;
            case "api.enabled":
                System.out.println(apiConfig.isApiEnabled());
                break;
            case "csv.enabled":
                System.out.println(apiConfig.isCsvEnabled());
                break;
            case "csv.path":
                String path = apiConfig.getCsvSavePath();
                System.out.println(path != null ? path : "null");
                break;
            default:
                System.err.println("错误: 未知配置键: " + key);
                System.err.println("可用配置键:");
                System.err.println("  api.url - API端点URL");
                System.err.println("  api.enabled - 启用/禁用API发送");
                System.err.println("  csv.enabled - 启用/禁用CSV导出");
                System.err.println("  csv.path - CSV保存路径");
                System.exit(1);
        }
    }
    
    /**
     * 处理状态查看命令
     */
    private static void handleStatus(String[] args) {
        Options statusOptions = new Options();
        statusOptions.addOption("h", "help", false, "显示帮助信息");
        
        try {
            CommandLine cmd = parser.parse(statusOptions, args);
            
            if (cmd.hasOption("help")) {
                printCommandHelp("status", statusOptions);
                return;
            }
            
            System.out.println("=== CICFlowMeter 系统状态 ===");
            System.out.println();
            
            // 系统信息
            Runtime runtime = Runtime.getRuntime();
            System.out.println("系统信息:");
            System.out.println("  内存使用: " + (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + 
                             "MB / " + runtime.maxMemory() / 1024 / 1024 + "MB");
            System.out.println("  可用处理器: " + runtime.availableProcessors());
            System.out.println();
            
            // 规则信息
            TimeRuleManager ruleManager = TimeRuleManager.getInstance();
            System.out.println("时间规则:");
            System.out.println("  规则总数: " + ruleManager.getTotalRuleCount());
            System.out.println("  启用规则: " + ruleManager.getEnabledRuleCount());
            System.out.println("  分组数量: " + ruleManager.getTotalGroupCount());
            System.out.println("  启用分组: " + ruleManager.getEnabledGroupCount());
            System.out.println();
            
            // API配置
            ApiConfigManager apiConfig = ApiConfigManager.getInstance();
            System.out.println("API配置:");
            System.out.println("  API URL: " + apiConfig.getApiUrl());
            System.out.println("  API发送: " + (apiConfig.isApiEnabled() ? "启用" : "禁用"));
            System.out.println("  CSV导出: " + (apiConfig.isCsvEnabled() ? "启用" : "禁用"));
            System.out.println("  自定义CSV路径: " + (apiConfig.getCsvSavePath() != null ? apiConfig.getCsvSavePath() : "(使用默认)"));
            System.out.println();
            
            // 健康检查状态
            HealthCheckServer healthServer = HealthCheckServer.getInstance();
            System.out.println("健康检查:");
            System.out.println("  服务器状态: " + (healthServer.isRunning() ? "运行中" : "已停止"));
            if (healthServer.isRunning()) {
                System.out.println("  访问地址: http://localhost:" + healthServer.getPort() + "/health");
            }
            
        } catch (ParseException e) {
            System.err.println("状态命令解析错误: " + e.getMessage());
            printCommandHelp("status", statusOptions);
            System.exit(1);
        }
    }
    
    /**
     * 处理守护进程命令
     */
    private static void handleDaemon(String[] args) {
        logger.info("守护进程模式正在开发中...");
        logger.info("使用systemd服务文件进行生产环境部署");
        System.out.println("参考 systemd/cicflowmeter.service 文件进行部署");
    }
    
    /**
     * 处理健康检查命令
     */
    private static void handleHealth(String[] args) {
        Options healthOptions = new Options();
        healthOptions.addOption("h", "help", false, "显示帮助信息");
        healthOptions.addOption("s", "start", false, "启动健康检查服务器");
        healthOptions.addOption("stop", false, "停止健康检查服务器");
        healthOptions.addOption("p", "port", true, "服务器端口（默认: 8888）");
        healthOptions.addOption("test", false, "测试健康检查端点");
        
        try {
            CommandLine cmd = parser.parse(healthOptions, args);
            
            if (cmd.hasOption("help")) {
                printCommandHelp("health", healthOptions);
                return;
            }
            
            HealthCheckServer server = HealthCheckServer.getInstance();
            
            if (cmd.hasOption("port")) {
                int port = Integer.parseInt(cmd.getOptionValue("port"));
                server.setPort(port);
                logger.info("设置健康检查端口: {}", port);
            }
            
            if (cmd.hasOption("start")) {
                if (server.start()) {
                    logger.info("健康检查服务器已启动");
                    logger.info("访问: http://localhost:{}/health", server.getPort());
                    
                    // 保持运行直到被中断
                    logger.info("按Ctrl+C停止服务器...");
                    Thread.sleep(Long.MAX_VALUE);
                }
                return;
            }
            
            if (cmd.hasOption("stop")) {
                server.stop();
                logger.info("健康检查服务器已停止");
                return;
            }
            
            if (cmd.hasOption("test")) {
                logger.info("测试健康检查端点...");
                // TODO: 实现HTTP客户端测试
                logger.info("请访问: http://localhost:{}/health", server.getPort());
                return;
            }
            
            // 默认显示帮助
            printCommandHelp("health", healthOptions);
            
        } catch (ParseException e) {
            System.err.println("健康命令解析错误: " + e.getMessage());
            printCommandHelp("health", healthOptions);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("服务器被中断");
        }
    }
    
    /**
     * 打印捕获统计信息
     */
    private static void printCaptureStats(ApiConfigManager apiConfig) {
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastUpdateTime) / 1000;
        
        System.out.println("\n=== 捕获统计信息 ===");
        System.out.printf("时间: %s\n", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        System.out.printf("运行时间: %d秒 (最后活动: %d秒前)\n", 
            (currentTime - startTime) / 1000, elapsedSeconds);
        System.out.printf("捕获包数: %d\n", packetsCaptured.get());
        System.out.printf("生成流量: %d\n", flowsGenerated.get());
        
        if (apiConfig.isApiEnabled()) {
            System.out.printf("API发送: %d (成功: %d, 失败: %d)\n",
                ApiSendTask.getTotalSent() + ApiSendTask.getTotalFailed(),
                ApiSendTask.getTotalSent(), ApiSendTask.getTotalFailed());
            System.out.printf("API地址: %s\n", apiConfig.getApiUrl());
        } else {
            System.out.println("API发送: 已禁用");
        }
        
        if (apiConfig.isCsvEnabled()) {
            System.out.println("CSV导出: 启用中");
        } else {
            System.out.println("CSV导出: 已禁用");
        }
        
        System.out.printf("时间规则标签: %s\n", applyTimeLabels ? "启用" : "禁用");
        System.out.println("==================\n");
    }
    
    /**
     * 应用时间标签到CSV格式的流量数据
     * 假设最后一列是Label列，用指定的时间标签替换
     */
    private static String applyTimeLabelToCsv(String csvData, String timeLabel) {
        if (csvData == null || csvData.isEmpty()) {
            return csvData;
        }
        
        // 分割CSV行
        String[] parts = csvData.split(",", -1); // 保留空值
        if (parts.length == 0) {
            return csvData;
        }
        
        // 替换最后一列（Label列）
        parts[parts.length - 1] = timeLabel;
        
        // 重新组合
        return String.join(",", parts);
    }
    

    
    /**
     * 打印全局帮助信息
     */
    private static void printGlobalHelp() {
        System.out.println("CICFlowMeter " + VERSION + " - 网络流量分析和特征提取工具");
        System.out.println();
        System.out.println("用法: cfm [全局选项] <子命令> [子命令选项]");
        System.out.println();
        System.out.println("全局选项:");
        helpFormatter.printHelp("cfm", GLOBAL_OPTIONS);
        System.out.println();
        System.out.println("可用子命令:");
        System.out.println("  capture     实时网络流量捕获");
        System.out.println("  analyze     离线PCAP文件分析（原Cmd功能）");
        System.out.println("  config      配置管理（时间规则、API设置、CSV导出等）");
        System.out.println("  status      查看系统状态");
        System.out.println("  daemon      守护进程管理");
        System.out.println("  health      健康检查服务器管理");
        System.out.println();
        System.out.println("完整命令示例（可直接复制使用）:");
        System.out.println();
        System.out.println("使用分发版本 (dist/bin/):");
        System.out.println("1. 实时捕获（带健康检查和API发送）:");
        System.out.println("   sudo ./cicflowmeter-cli capture --interface eth0 --health-check");
        System.out.println();
        System.out.println("2. 实时捕获（带时间标签和健康检查）:");
        System.out.println("   sudo ./cicflowmeter-cli capture --interface eth0 --health-check --time_label");
        System.out.println();
        System.out.println("3. 实时捕获（禁用API，仅本地CSV）:");
        System.out.println("   sudo ./cicflowmeter-cli capture --interface eth0 --health-check --no_api");
        System.out.println();
        System.out.println("4. 离线PCAP分析:");
        System.out.println("   ./cicflowmeter-cli analyze /path/to/pcap/file.pcap /output/dir/");
        System.out.println();
        System.out.println("5. 查看系统状态:");
        System.out.println("   ./cicflowmeter-cli status");
        System.out.println();
        System.out.println("6. 管理配置:");
        System.out.println("   ./cicflowmeter-cli config --list");
        System.out.println("   ./cicflowmeter-cli config --show");
        System.out.println("   ./cicflowmeter-cli config --set api.url=\"http://127.0.0.1:5000/flows\"");
        System.out.println("   ./cicflowmeter-cli config --set api.enabled=false");
        System.out.println("   ./cicflowmeter-cli config --set csv.path=\"/var/lib/cicflowmeter/csv/\"");
        System.out.println("   ./cicflowmeter-cli config --get api.url");
        System.out.println("   ./cicflowmeter-cli config --reset");
        System.out.println();
        System.out.println("7. 启动健康检查服务器:");
        System.out.println("   ./cicflowmeter-cli health --start --port 8888");
        System.out.println();
        System.out.println("使用Gradle开发模式 (需要源代码):");
        System.out.println("1. 实时捕获:");
        System.out.println("   sudo ./gradlew executeCLI -Pargs=\"capture --interface eth0 --health-check\"");
        System.out.println();
        System.out.println("2. 配置管理:");
        System.out.println("   ./gradlew executeCLI -Pargs=\"config --list\"");
        System.out.println("   ./gradlew executeCLI -Pargs=\"config --set api.url=http://127.0.0.1:5000/flows\"");
        System.out.println();
        System.out.println("3. 离线分析:");
        System.out.println("   ./gradlew executeCLI -Pargs=\"analyze /path/to/file.pcap /output/dir/\"");
        System.out.println();
        System.out.println("注意: Gradle模式下不需要sudo，但实时捕获需要网络权限");
    }
    
    /**
     * 打印子命令帮助信息
     */
    private static void printCommandHelp(String command, Options options) {
        System.out.println("用法: cfm " + command + " [选项]");
        helpFormatter.printHelp("cfm " + command, options);
        
        if (command.equals("config")) {
            System.out.println();
            System.out.println("配置示例:");
            System.out.println("  设置API URL: --set api.url=\"http://127.0.0.1:5000/flows\"");
            System.out.println("  启用API发送: --set api.enabled=true");
            System.out.println("  禁用API发送: --set api.enabled=false");
            System.out.println("  启用CSV导出: --set csv.enabled=true");
            System.out.println("  设置CSV路径: --set csv.path=\"/var/lib/cicflowmeter/csv/\"");
            System.out.println("  获取API URL: --get api.url");
            System.out.println("  查看所有配置: --list 或 --show");
        }
    }
}