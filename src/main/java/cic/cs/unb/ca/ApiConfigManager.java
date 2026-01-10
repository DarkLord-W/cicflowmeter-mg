package cic.cs.unb.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ApiConfigManager.class);
    
    private static ApiConfigManager Instance = new ApiConfigManager();
    
    private String apiUrl = "http://127.0.0.1:5000/predict"; // 默认API URL
    private boolean apiEnabled = true; // 默认启用
    private boolean csvEnabled = true; // 默认启用CSV导出
    private String csvSavePath = null; // 自定义CSV路径
    
    private ApiConfigManager() {}
    
    public static ApiConfigManager getInstance() {
        return Instance;
    }
    
    public String getApiUrl() {
        return apiUrl;
    }
    
    public void setApiUrl(String apiUrl) {
        if (apiUrl != null && !apiUrl.trim().isEmpty()) {
            this.apiUrl = apiUrl.trim();
            logger.info("API URL设置为: {}", this.apiUrl);
        } else {
            this.apiUrl = "";
            logger.info("清空API URL");
        }
    }
    
    public boolean isApiEnabled() {
        return apiEnabled && apiUrl != null && !apiUrl.trim().isEmpty();
    }
    
    public void setApiEnabled(boolean enabled) {
        this.apiEnabled = enabled;
        logger.info("API发送: {}", enabled ? "启用" : "禁用");
    }
    
    public boolean isCsvEnabled() {
        return csvEnabled;
    }
    
    public void setCsvEnabled(boolean enabled) {
        this.csvEnabled = enabled;
        logger.info("CSV导出: {}", enabled ? "启用" : "禁用");
    }
    
    public String getCsvSavePath() {
        return csvSavePath;
    }
    
    public void setCsvSavePath(String path) {
        this.csvSavePath = path;
        if (path != null && !path.trim().isEmpty()) {
            logger.info("自定义CSV保存路径: {}", path);
        } else {
            logger.info("清空自定义CSV保存路径");
        }
    }
    
    public String getCurrentCsvSavePath() {
        return csvSavePath;
    }
    
    public void resetToDefaults() {
        apiUrl = "http://127.0.0.1:5000/predict";
        apiEnabled = true;
        csvEnabled = true;
        csvSavePath = null;
        logger.info("重置为默认配置");
    }
    
    public String getStatusString() {
        StringBuilder status = new StringBuilder();
        status.append("API配置状态:\n");
        status.append("- API URL: ").append(apiUrl).append("\n");
        status.append("- API发送: ").append(apiEnabled ? "启用" : "禁用").append("\n");
        status.append("- CSV导出: ").append(csvEnabled ? "启用" : "禁用").append("\n");
        status.append("- 自定义CSV路径: ").append(csvSavePath != null ? csvSavePath : "(使用默认)").append("\n");
        return status.toString();
    }
}