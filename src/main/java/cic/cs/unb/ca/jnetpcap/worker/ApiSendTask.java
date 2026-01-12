package cic.cs.unb.ca.jnetpcap.worker;

import cic.cs.unb.ca.jnetpcap.FlowFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;

public class ApiSendTask implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(ApiSendTask.class);
    
    private static final AtomicInteger TOTAL_SENT = new AtomicInteger(0);
    private static final AtomicInteger TOTAL_FAILED = new AtomicInteger(0);
    private static final String CLIENT_ID = "cicflowmeter_" + UUID.randomUUID().toString().substring(0, 8);
    
    private String apiUrl;
    private String flowData;
    private String flowHeader;
    private long timestamp;
    private boolean enabled;
    
    public ApiSendTask(String apiUrl, String flowData, String flowHeader, boolean enabled) {
        this.apiUrl = apiUrl;
        this.flowData = flowData;
        this.flowHeader = flowHeader;
        this.timestamp = System.currentTimeMillis();
        this.enabled = enabled;
    }
    
    @Override
    public void run() {
        if (!enabled || apiUrl == null || apiUrl.trim().isEmpty()) {
            logger.debug("API发送已禁用或无API URL配置，跳过发送");
            return;
        }
        
        try {
            // 准备JSON请求体（结构化数据）
            Map<String, String> flowFeatures = parseFlowDataToJson(flowHeader, flowData);
            String structuredJson = buildStructuredJson(flowHeader, flowFeatures);
            logger.info("structuredJson: {}", structuredJson);
            System.err.println("[DEBUG] structuredJson前200字符: " + structuredJson.substring(0, Math.min(200, structuredJson.length())));
            
            // 构建预处理服务期望的格式
            String innerJson = String.format(
                "{\"timestamp\":%d,\"structured\":%s,\"csv_header\":\"%s\",\"flow_data\":\"%s\"}",
                timestamp,
                structuredJson,
                escapeJsonString(flowHeader),
                escapeJsonString(flowData)
            );
            logger.info("innerJson: {}", innerJson);
            System.err.println("[DEBUG] innerJson前200字符: " + innerJson.substring(0, Math.min(200, innerJson.length())));
            
            // 包装在data字段中，添加client_id
            String jsonBody = String.format(
                "{\"client_id\":\"%s\",\"data\":%s}",
                CLIENT_ID,
                innerJson
            );
            
            logger.info("Client ID: {}", CLIENT_ID);
            logger.info("发送JSON: {}", jsonBody);
            System.err.println("[DEBUG] Client ID: " + CLIENT_ID);
            System.err.println("[DEBUG] JSON前200字符: " + jsonBody.substring(0, Math.min(200, jsonBody.length())));
            
            // 调试：将JSON写入临时文件以便检查
            try {
                java.nio.file.Files.write(
                    java.nio.file.Paths.get("/tmp/api_debug.json"),
                    jsonBody.getBytes("utf-8")
                );
                logger.debug("JSON已写入 /tmp/api_debug.json");
                System.err.println("[DEBUG] JSON已写入 /tmp/api_debug.json");
            } catch (Exception e) {
                logger.debug("无法写入调试文件: {}", e.getMessage());
                System.err.println("[DEBUG] 无法写入调试文件: " + e.getMessage());
            }
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                TOTAL_SENT.incrementAndGet();
                logger.debug("API发送成功: 状态码={}, 数据长度={}", responseCode, flowData.length());
            } else {
                TOTAL_FAILED.incrementAndGet();
                logger.warn("API发送失败: 状态码={}, URL={}", responseCode, apiUrl);
                
                // 读取错误响应
                try (BufferedReader br = new BufferedReader(
                     new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    logger.warn("错误响应: {}", response.toString());
                }
            }
            
            conn.disconnect();
            
        } catch (IOException e) {
            TOTAL_FAILED.incrementAndGet();
            logger.warn("API发送异常: {} -> {}", apiUrl, e.getMessage());
        } catch (Exception e) {
            TOTAL_FAILED.incrementAndGet();
            logger.error("API发送未知异常: {}", e.getMessage(), e);
        }
    }
    
    private Map<String, String> parseFlowDataToJson(String header, String data) {
        Map<String, String> features = new LinkedHashMap<>();
        
        String[] headers = header.split(",");
        String[] values = data.split(",");
        
        // 确保headers和values长度匹配
        int minLength = Math.min(headers.length, values.length);
        for (int i = 0; i < minLength; i++) {
            String featureName = headers[i].trim();
            // 统一使用headerFieldToJsonKey方法转换字段名
            String jsonKey = headerFieldToJsonKey(featureName);
            String value = values[i].trim();
            features.put(jsonKey, value);
            logger.debug("parseFlowDataToJson: 存储字段 '{}' -> '{}' = '{}'", featureName, jsonKey, value);
        }
        
        return features;
    }
    
    private String headerFieldToJsonKey(String featureName) {
        // 直接使用原始特征名称，与主界面列表完全一致
        logger.debug("headerFieldToJsonKey: '{}' -> '{}' (保持原样)", featureName, featureName);
        return featureName;
    }
    
    private String buildStructuredJson(String header, Map<String, String> flowFeatures) {
        logger.debug("=== DEBUG: buildStructuredJson开始 ===");
        logger.debug("Header字段数: {}", header.split(",").length);
        logger.debug("Map大小: {}", flowFeatures.size());
        
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        
        String[] headers = header.split(",");
        logger.debug("前10个header字段:");
        for (int j = 0; j < Math.min(10, headers.length); j++) {
            logger.debug("  [{}] '{}'", j, headers[j].trim());
        }
        boolean first = true;
        
        for (int i = 0; i < headers.length; i++) {
            String featureName = headers[i].trim();
            String jsonKey = headerFieldToJsonKey(featureName);
            
            // 从Map中获取值
            String value = flowFeatures.get(jsonKey);
            if (value == null) {
                // 如果找不到对应的键，跳过该字段
                logger.info("跳过字段 {} (转换后键名: {})，在Map中未找到", featureName, jsonKey);
                continue;
            } else {
                logger.debug("找到字段 {} (转换后键名: {})，值: {}", featureName, jsonKey, value);
            }
            
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;
            
            // 检查值是否为数字
            boolean isNumeric = value.matches("-?\\d+(\\.\\d+)?");
            
            jsonBuilder.append("\"").append(jsonKey).append("\":");
            if (isNumeric) {
                jsonBuilder.append(value);
            } else {
                jsonBuilder.append("\"").append(escapeJsonString(value)).append("\"");
            }
        }
        
        jsonBuilder.append("}");
        String result = jsonBuilder.toString();
        logger.debug("=== DEBUG: buildStructuredJson结束 ===");
        logger.debug("生成的JSON长度: {}", result.length());
        logger.debug("生成的JSON前200字符: {}", result.substring(0, Math.min(200, result.length())));
        return result;
    }
    
    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    public static int getTotalSent() {
        return TOTAL_SENT.get();
    }
    
    public static int getTotalFailed() {
        return TOTAL_FAILED.get();
    }
    
    public static void resetCounters() {
        TOTAL_SENT.set(0);
        TOTAL_FAILED.set(0);
    }
    
    public static String getStatsString() {
        return String.format("已发送: %d, 失败: %d", getTotalSent(), getTotalFailed());
    }
}