package cic.cs.unb.ca.health;

import cic.cs.unb.ca.ApiConfigManager;
import cic.cs.unb.ca.TimeRuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 健康检查HTTP服务器
 * 提供系统状态监控端点
 */
public class HealthCheckServer {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServer.class);
    
    // 单例实例
    private static HealthCheckServer instance = new HealthCheckServer();
    
    // HTTP服务器
    private com.sun.net.httpserver.HttpServer httpServer;
    
    // 服务器状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int port = 8888; // 默认端口
    
    // JSON序列化工具
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // 统计信息
    private long startTime = System.currentTimeMillis();
    private volatile long lastHealthCheckTime = 0;
    private volatile int totalHealthChecks = 0;
    
    /**
     * 私有构造函数（单例模式）
     */
    private HealthCheckServer() {
        // 从系统属性获取端口配置
        String portProp = System.getProperty("cicflowmeter.health.port");
        if (portProp != null && !portProp.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portProp.trim());
            } catch (NumberFormatException e) {
                logger.warn("无效的健康检查端口配置: {}, 使用默认端口 {}", portProp, port);
            }
        }
    }
    
    /**
     * 获取单例实例
     */
    public static HealthCheckServer getInstance() {
        return instance;
    }
    
    /**
     * 启动健康检查服务器
     * 
     * @return 是否成功启动
     */
    public synchronized boolean start() {
        if (running.get()) {
            logger.warn("健康检查服务器已经在运行");
            return true;
        }
        
        try {
            // 创建HTTP服务器
            httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
            
            // 注册健康检查端点
            httpServer.createContext("/health", exchange -> {
                try {
                    totalHealthChecks++;
                    lastHealthCheckTime = System.currentTimeMillis();
                    
                    // 构建健康响应
                    JsonObject response = buildHealthResponse();
                    String responseBody = gson.toJson(response);
                    
                    // 设置响应头
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                    exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
                    
                    // 发送响应
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                    }
                    
                } catch (Exception e) {
                    logger.error("处理健康检查请求时出错", e);
                    try {
                        exchange.sendResponseHeaders(500, 0);
                    } catch (IOException ignored) {}
                }
            });
            
            // 注册根路径重定向
            httpServer.createContext("/", exchange -> {
                String redirect = "<html><body><h1>CICFlowMeter Health Check</h1>" +
                                 "<p>可用端点:</p>" +
                                 "<ul><li><a href=\"/health\">/health</a> - 系统健康状态</li></ul>" +
                                 "</body></html>";
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, redirect.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(redirect.getBytes(StandardCharsets.UTF_8));
                }
            });
            
            // 启动服务器
            httpServer.start();
            running.set(true);
            
            logger.info("健康检查服务器启动成功，端口: {}", port);
            logger.info("健康检查端点: http://localhost:{}/health", port);
            
            return true;
            
        } catch (IOException e) {
            logger.error("启动健康检查服务器失败，端口: {}", port, e);
            return false;
        }
    }
    
    /**
     * 停止健康检查服务器
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("健康检查服务器已停止");
        }
        
        running.set(false);
    }
    
    /**
     * 构建健康检查响应
     */
    private JsonObject buildHealthResponse() {
        JsonObject response = new JsonObject();
        
        // 基础状态
        response.addProperty("status", "UP");
        response.addProperty("timestamp", Instant.now().toString());
        response.addProperty("version", "4.1-enhanced");
        response.addProperty("service", "CICFlowMeter");
        
        // 系统信息
        JsonObject system = new JsonObject();
        system.addProperty("uptime", System.currentTimeMillis() - startTime);
        
        // 内存使用
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        JsonObject memory = new JsonObject();
        memory.addProperty("heapUsedMB", memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024);
        memory.addProperty("heapMaxMB", memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024);
        memory.addProperty("nonHeapUsedMB", memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024);
        system.add("memory", memory);
        
        // 线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        JsonObject threads = new JsonObject();
        threads.addProperty("total", threadBean.getThreadCount());
        threads.addProperty("daemon", threadBean.getDaemonThreadCount());
        threads.addProperty("peak", threadBean.getPeakThreadCount());
        system.add("threads", threads);
        
        // JVM信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        JsonObject jvm = new JsonObject();
        jvm.addProperty("name", runtimeBean.getVmName());
        jvm.addProperty("vendor", runtimeBean.getVmVendor());
        jvm.addProperty("version", runtimeBean.getVmVersion());
        jvm.addProperty("startTime", DateTimeFormatter.ISO_INSTANT
            .format(Instant.ofEpochMilli(runtimeBean.getStartTime())));
        system.add("jvm", jvm);
        
        response.add("system", system);
        
        // 时间规则信息
        try {
            TimeRuleManager ruleManager = TimeRuleManager.getInstance();
            JsonObject rules = new JsonObject();
            rules.addProperty("total", ruleManager.getTotalRuleCount());
            rules.addProperty("enabled", ruleManager.getEnabledRuleCount());
            rules.addProperty("groups", ruleManager.getTotalGroupCount());
            rules.addProperty("enabledGroups", ruleManager.getEnabledGroupCount());
            response.add("rules", rules);
        } catch (Exception e) {
            logger.warn("获取规则信息失败", e);
        }
        
        // API配置信息
        try {
            ApiConfigManager apiConfig = ApiConfigManager.getInstance();
            JsonObject api = new JsonObject();
            api.addProperty("enabled", apiConfig.isApiEnabled());
            api.addProperty("url", apiConfig.getApiUrl());
            api.addProperty("csvEnabled", apiConfig.isCsvEnabled());
            response.add("api", api);
        } catch (Exception e) {
            logger.warn("获取API配置信息失败", e);
        }
        
        // 健康检查统计
        JsonObject healthStats = new JsonObject();
        healthStats.addProperty("totalChecks", totalHealthChecks);
        healthStats.addProperty("lastCheckTime", lastHealthCheckTime);
        healthStats.addProperty("serverUptime", System.currentTimeMillis() - startTime);
        response.add("healthStats", healthStats);
        
        // 捕获状态（需要从其他模块获取，这里留空）
        JsonObject capture = new JsonObject();
        capture.addProperty("active", false); // 默认值
        capture.addProperty("interface", "unknown");
        response.add("capture", capture);
        
        return response;
    }
    
    /**
     * 检查服务器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 获取服务器端口
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 设置服务器端口（必须在启动前设置）
     */
    public void setPort(int port) {
        if (running.get()) {
            logger.warn("无法更改运行中的服务器端口");
            return;
        }
        this.port = port;
    }
    
    /**
     * 获取服务器URL
     */
    public String getServerUrl() {
        return "http://localhost:" + port;
    }
}