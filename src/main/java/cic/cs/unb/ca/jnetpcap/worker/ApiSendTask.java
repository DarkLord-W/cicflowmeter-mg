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

public class ApiSendTask implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(ApiSendTask.class);
    
    private static final AtomicInteger TOTAL_SENT = new AtomicInteger(0);
    private static final AtomicInteger TOTAL_FAILED = new AtomicInteger(0);
    
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
            String structuredJson = buildStructuredJson(flowFeatures);
            
            // 保持向后兼容，同时发送结构化JSON和原始CSV
            String jsonBody = String.format(
                "{\"timestamp\":%d,\"structured\":%s,\"csv_header\":\"%s\",\"flow_data\":\"%s\"}",
                timestamp,
                structuredJson,
                escapeJsonString(flowHeader),
                escapeJsonString(flowData)
            );
            
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
        Map<String, String> features = new HashMap<>();
        
        String[] headers = header.split(",");
        String[] values = data.split(",");
        
        // 确保headers和values长度匹配
        int minLength = Math.min(headers.length, values.length);
        for (int i = 0; i < minLength; i++) {
            String featureName = headers[i].trim();
            // 将特征名称转换为更标准的格式（移除空格，使用驼峰命名）
            String jsonKey = featureName.replaceAll("\\s+", "")
                                      .replaceAll("/", "_")
                                      .replaceAll("\\(", "")
                                      .replaceAll("\\)", "");
            
            // 特别处理Flow Duration
            if (featureName.equals("Flow Duration")) {
                features.put("FlowDuration", values[i].trim());
            }
            // 特别处理Total Fwd Packets
            else if (featureName.equals("Total Fwd Packet")) {
                features.put("TotalFwdPackets", values[i].trim());
            }
            // 特别处理Packet Length Std
            else if (featureName.equals("Packet Length Std")) {
                features.put("PacketLengthStd", values[i].trim());
            }
            // 特别处理Label
            else if (featureName.equals("Label")) {
                features.put("Label", values[i].trim());
            }
            // 通用处理其他特征
            else {
                features.put(jsonKey, values[i].trim());
            }
        }
        
        return features;
    }
    
    private String buildStructuredJson(Map<String, String> flowFeatures) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        
        boolean first = true;
        for (Map.Entry<String, String> entry : flowFeatures.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            first = false;
            
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 检查值是否为数字
            boolean isNumeric = value.matches("-?\\d+(\\.\\d+)?");
            
            jsonBuilder.append("\"").append(key).append("\":");
            if (isNumeric) {
                jsonBuilder.append(value);
            } else {
                jsonBuilder.append("\"").append(escapeJsonString(value)).append("\"");
            }
        }
        
        jsonBuilder.append("}");
        return jsonBuilder.toString();
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