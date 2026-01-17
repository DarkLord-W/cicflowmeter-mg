package cic.cs.unb.ca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.FileSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ApiConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ApiConfigManager.class);
    
    private static ApiConfigManager Instance = new ApiConfigManager();
    
    private String apiUrl = "http://127.0.0.1:5000/flows"; // 默认API URL
    private boolean apiEnabled = true; // 默认启用
    private boolean csvEnabled = true; // 默认启用CSV导出
    private String csvSavePath = null; // 自定义CSV路径
    
    private Gson gson;
    
    // 配置文件路径
    public static String getConfigDir() {
        String envConfigDir = System.getenv("CIC_CONFIG_DIR");
        if (envConfigDir != null && !envConfigDir.trim().isEmpty()) {
            return envConfigDir.trim();
        }
        return System.getProperty("user.home") + Sys.FILE_SEP + ".cicflowmeter";
    }
    
    public static String getConfigFile() {
        return getConfigDir() + Sys.FILE_SEP + "api_config.json";
    }
    
    private ApiConfigManager() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        loadConfigFromFile();
    }
    
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
        saveConfigToFile();
    }
    
    public boolean isApiEnabled() {
        return apiEnabled && apiUrl != null && !apiUrl.trim().isEmpty();
    }
    
    public void setApiEnabled(boolean enabled) {
        this.apiEnabled = enabled;
        logger.info("API发送: {}", enabled ? "启用" : "禁用");
        saveConfigToFile();
    }
    
    public boolean isCsvEnabled() {
        return csvEnabled;
    }
    
    public void setCsvEnabled(boolean enabled) {
        this.csvEnabled = enabled;
        logger.info("CSV导出: {}", enabled ? "启用" : "禁用");
        saveConfigToFile();
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
        saveConfigToFile();
    }
    
    public String getCurrentCsvSavePath() {
        return csvSavePath;
    }
    
    /**
     * 从文件加载配置
     */
    private void loadConfigFromFile() {
        String configFile = getConfigFile();
        logger.debug("加载API配置文件: {}", configFile);
        
        // 首先检查环境变量（最高优先级）
        boolean envConfigApplied = false;
        String envApiUrl = System.getenv("CIC_API_URL");
        String envApiEnabled = System.getenv("CIC_API_ENABLED");
        String envCsvEnabled = System.getenv("CIC_CSV_ENABLED");
        
        if (envApiUrl != null && !envApiUrl.trim().isEmpty()) {
            this.apiUrl = envApiUrl.trim();
            logger.info("从环境变量CIC_API_URL设置API URL: {}", this.apiUrl);
            envConfigApplied = true;
        }
        
        if (envApiEnabled != null && !envApiEnabled.trim().isEmpty()) {
            this.apiEnabled = Boolean.parseBoolean(envApiEnabled.trim());
            logger.info("从环境变量CIC_API_ENABLED设置API发送: {}", this.apiEnabled);
            envConfigApplied = true;
        }
        
        if (envCsvEnabled != null && !envCsvEnabled.trim().isEmpty()) {
            this.csvEnabled = Boolean.parseBoolean(envCsvEnabled.trim());
            logger.info("从环境变量CIC_CSV_ENABLED设置CSV导出: {}", this.csvEnabled);
            envConfigApplied = true;
        }
        
        // 如果环境变量已设置，跳过文件读取（除非文件不存在时需要创建目录）
        if (envConfigApplied) {
            logger.debug("环境变量配置已应用，跳过文件读取");
            // 但仍需确保配置目录存在
            FileSecurityUtils.ensureSecureConfigDir(getConfigDir());
            return;
        }
        
        // 确保配置目录安全存在
        if (!FileSecurityUtils.ensureSecureConfigDir(getConfigDir())) {
            logger.error("无法确保安全配置目录: {}", getConfigDir());
            return;
        }
        
        File file = new File(configFile);
        if (!file.exists()) {
            logger.info("API配置文件不存在: {}，使用默认配置", configFile);
            return;
        }
        
        // 检查文件权限安全性
        if (!FileSecurityUtils.isFileSecure(file)) {
            logger.warn("API配置文件权限不安全: {}", configFile);
            // 尝试修复权限
            if (FileSecurityUtils.fixFilePermissionsIfInsecure(file)) {
                logger.info("已修复API配置文件权限: {}", configFile);
            }
        }
        
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            
            ConfigData config = gson.fromJson(reader, ConfigData.class);
            if (config != null) {
                this.apiUrl = config.apiUrl != null ? config.apiUrl : "http://127.0.0.1:5000/flows";
                this.apiEnabled = config.apiEnabled;
                this.csvEnabled = config.csvEnabled;
                this.csvSavePath = config.csvSavePath;
                logger.info("API配置加载成功: {}", configFile);
            }
        } catch (Exception e) {
            logger.error("加载API配置文件失败: {}", configFile, e);
        }
    }
    
    /**
     * 保存配置到文件
     */
    private synchronized void saveConfigToFile() {
        // 确保配置目录安全存在
        if (!FileSecurityUtils.ensureSecureConfigDir(getConfigDir())) {
            logger.error("无法确保安全配置目录: {}", getConfigDir());
            return;
        }
        
        ConfigData config = new ConfigData();
        config.apiUrl = this.apiUrl;
        config.apiEnabled = this.apiEnabled;
        config.csvEnabled = this.csvEnabled;
        config.csvSavePath = this.csvSavePath;
        config.version = "1.0";
        config.lastModified = System.currentTimeMillis();
        
        String jsonContent = gson.toJson(config);
        
        // 使用安全文件保存
        boolean saved = FileSecurityUtils.saveSecureConfigFile(getConfigFile(), jsonContent);
        if (saved) {
            logger.debug("API配置文件保存成功: {}", getConfigFile());
        } else {
            logger.error("API配置文件保存失败: {}", getConfigFile());
        }
    }
    
    /**
     * 配置数据内部类（用于JSON序列化）
     */
    private static class ConfigData {
        String version;
        long lastModified;
        String apiUrl;
        boolean apiEnabled;
        boolean csvEnabled;
        String csvSavePath;
    }
    
    public void resetToDefaults() {
        apiUrl = "http://127.0.0.1:5000/flows";
        apiEnabled = true;
        csvEnabled = true;
        csvSavePath = null;
        logger.info("重置为默认配置");
        saveConfigToFile();
    }
    
    /**
     * 重新加载配置文件（热加载支持）
     */
    public synchronized void reloadConfig() {
        logger.info("重新加载API配置...");
        loadConfigFromFile();
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