package cic.cs.unb.ca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.FileSecurityUtils;
import cic.cs.unb.ca.jnetpcap.DateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 时间规则管理器（单例模式）
 * 负责时间规则的加载、保存、匹配和管理
 */
public class TimeRuleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TimeRuleManager.class);
    
    // 单例实例
    private static TimeRuleManager instance = new TimeRuleManager();
    
    // 规则和分组存储
    private List<TimeLabelRule> rules;
    private List<RuleGroup> groups;
    
    // JSON序列化工具
    private Gson gson;
    
    // 初始化状态
    private volatile boolean initialized = false;
    private volatile boolean backgroundLoadStarted = false;
    private volatile boolean backgroundLoadCompleted = false;
    
    // 配置文件路径
    private static String getConfigDir() {
        String envConfigDir = System.getenv("CIC_CONFIG_DIR");
        if (envConfigDir != null && !envConfigDir.trim().isEmpty()) {
            return envConfigDir.trim();
        }
        return System.getProperty("user.home") + Sys.FILE_SEP + ".cicflowmeter";
    }
    
    private static String getRulesFile() {
        return getConfigDir() + Sys.FILE_SEP + "time_rules.json";
    }
    
    /**
     * 私有构造函数（单例模式）
     */
    private TimeRuleManager() {
        this.rules = new CopyOnWriteArrayList<>();
        this.groups = new CopyOnWriteArrayList<>();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
        
        // 初始化默认分组（不加载文件，延迟加载）
        initializeDefaultGroups();
        
        // 在后台线程中预加载规则（不阻塞启动）
        startBackgroundLoad();
    }
    
    /**
     * 获取单例实例
     */
    public static TimeRuleManager getInstance() {
        return instance;
    }
    
    /**
     * 初始化默认分组
     */
    private void initializeDefaultGroups() {
        RuleGroup defaultGroup = RuleGroup.getDefaultGroup();
        if (!groupExists(defaultGroup.getGroupId())) {
            groups.add(defaultGroup);
        }
    }
    
    /**
     * 启动后台加载
     */
    private void startBackgroundLoad() {
        if (!backgroundLoadStarted) {
            synchronized (this) {
                if (!backgroundLoadStarted) {
                    backgroundLoadStarted = true;
                    Thread backgroundThread = new Thread(() -> {
                        try {
                            logger.debug("后台规则加载线程启动");
                            loadRulesFromFile();
                            backgroundLoadCompleted = true;
                            logger.debug("后台规则加载完成");
                        } catch (Exception e) {
                            logger.error("后台规则加载失败", e);
                        }
                    });
                    backgroundThread.setName("TimeRuleManager-BackgroundLoader");
                    backgroundThread.setDaemon(true);
                    backgroundThread.start();
                }
            }
        }
    }
    
    /**
     * 延迟初始化（线程安全）
     */
    private void initializeIfNeeded() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    // 如果后台加载已完成，直接使用结果
                    if (backgroundLoadCompleted) {
                        initialized = true;
                        return;
                    }
                    
                    // 否则同步加载（避免重复加载）
                    logger.debug("同步加载规则文件（后台加载未完成）");
                    loadRulesFromFile();
                    initialized = true;
                }
            }
        }
    }
    
    /**
     * 根据时间戳获取匹配的标签
     * @param timestampMicros 微秒时间戳
     * @return 匹配的标签，如果没有匹配则返回默认标签
     */
    public String getLabelForTimestamp(long timestampMicros) {
        // 快速检查：如果规则未初始化
        if (!initialized) {
            // 如果后台加载已完成，标记为已初始化
            if (backgroundLoadCompleted) {
                synchronized (this) {
                    if (!initialized) {
                        initialized = true;
                    }
                }
            } else {
                // 后台加载未完成，直接返回默认标签（不阻塞）
                return LabelConstants.getDefaultLabel();
            }
        }
        
        if (rules.isEmpty()) {
            return LabelConstants.getDefaultLabel();
        }
        
        // 获取启用的规则（考虑分组启用状态）
        List<TimeLabelRule> enabledRules = getEnabledRules();
        
        if (enabledRules.isEmpty()) {
            return LabelConstants.getDefaultLabel();
        }
        
        // 按优先级排序（高优先级优先）
        List<TimeLabelRule> sortedRules = enabledRules.stream()
            .sorted(Comparator.comparingInt(TimeLabelRule::getPriority).reversed())
            .collect(Collectors.toList());
        
        // 查找匹配的规则
        for (TimeLabelRule rule : sortedRules) {
            if (rule.matches(timestampMicros)) {
                logger.debug("规则匹配: {} -> {}", rule.getRuleName(), rule.getLabel());
                return rule.getLabel();
            }
        }
        
        // 调试信息：没有规则匹配
        if (logger.isDebugEnabled()) {
            String timeStr = DateFormatter.convertMilliseconds2String(timestampMicros / 1000L, "yyyy-MM-dd HH:mm:ss");
            logger.debug("没有规则匹配时间戳: {} ({}), 启用规则数: {}", timestampMicros, timeStr, enabledRules.size());
        }
        
        return LabelConstants.getDefaultLabel();
    }
    
    /**
     * 获取所有启用的规则
     */
    public List<TimeLabelRule> getEnabledRules() {
        initializeIfNeeded();
        
        // 获取启用的分组ID
        Set<String> enabledGroupIds = groups.stream()
            .filter(RuleGroup::isEnabled)
            .map(RuleGroup::getGroupId)
            .collect(Collectors.toSet());
        
        return rules.stream()
            .filter(rule -> rule.isEnabled() && enabledGroupIds.contains(rule.getGroupId()))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有规则
     */
    public List<TimeLabelRule> getAllRules() {
        initializeIfNeeded();
        return new ArrayList<>(rules);
    }
    
    /**
     * 清除所有规则
     */
    public synchronized void clearAllRules() {
        initializeIfNeeded();
        rules.clear();
        logger.info("清除所有规则");
        saveRulesToFile();
    }
    
    /**
     * 清除所有分组（保留默认分组）
     */
    public synchronized void clearAllGroups() {
        initializeIfNeeded();
        groups.removeIf(group -> !group.getGroupId().equals(RuleGroup.DEFAULT_GROUP_ID));
        logger.info("清除所有非默认分组");
        saveRulesToFile();
    }
    
    /**
     * 添加规则
     */
    public synchronized boolean addRule(TimeLabelRule rule) {
        initializeIfNeeded();
        String validationError = rule.validate();
        if (validationError != null) {
            logger.error("规则验证失败: {}", validationError);
            return false;
        }
        
        // 检查规则名称是否唯一
        if (ruleNameExists(rule.getRuleName())) {
            logger.error("规则名称已存在: {}", rule.getRuleName());
            return false;
        }
        
        // 检查时间重叠（同一分组内）
        if (hasTimeOverlap(rule)) {
            logger.warn("规则时间范围重叠: {}", rule.getRuleName());
            // 不阻止添加，但记录警告
        }
        
        rules.add(rule);
        logger.info("添加规则: {}", rule.getRuleName());
        
        // 自动保存
        saveRulesToFile();
        
        return true;
    }
    
    /**
     * 删除规则
     */
    public synchronized boolean removeRule(String ruleId) {
        initializeIfNeeded();
        boolean removed = rules.removeIf(rule -> rule.getRuleId().equals(ruleId));
        if (removed) {
            logger.info("删除规则: {}", ruleId);
            saveRulesToFile();
        }
        return removed;
    }
    
    /**
     * 更新规则
     */
    public synchronized boolean updateRule(TimeLabelRule updatedRule) {
        initializeIfNeeded();
        String validationError = updatedRule.validate();
        if (validationError != null) {
            logger.error("规则验证失败: {}", validationError);
            return false;
        }
        
        for (int i = 0; i < rules.size(); i++) {
            TimeLabelRule rule = rules.get(i);
            if (rule.getRuleId().equals(updatedRule.getRuleId())) {
                rules.set(i, updatedRule);
                logger.info("更新规则: {}", updatedRule.getRuleName());
                saveRulesToFile();
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查规则名称是否存在
     */
    public boolean ruleNameExists(String ruleName) {
        initializeIfNeeded();
        return rules.stream().anyMatch(rule -> rule.getRuleName().equals(ruleName));
    }
    
    /**
     * 检查时间重叠
     */
    private boolean hasTimeOverlap(TimeLabelRule newRule) {
        initializeIfNeeded();
        return rules.stream()
            .filter(rule -> rule.getGroupId().equals(newRule.getGroupId()))
            .anyMatch(newRule::overlapsWith);
    }
    
    /**
     * 分组管理方法
     */
    
    public List<RuleGroup> getAllGroups() {
        initializeIfNeeded();
        return new ArrayList<>(groups);
    }
    
    public synchronized boolean addGroup(RuleGroup group) {
        initializeIfNeeded();
        String validationError = group.validate();
        if (validationError != null) {
            logger.error("分组验证失败: {}", validationError);
            return false;
        }
        
        if (groupExists(group.getGroupId())) {
            logger.error("分组ID已存在: {}", group.getGroupId());
            return false;
        }
        
        groups.add(group);
        logger.info("添加分组: {}", group.getGroupName());
        saveRulesToFile();
        return true;
    }
    
    public synchronized boolean removeGroup(String groupId) {
        initializeIfNeeded();
        // 不能删除默认分组
        if ("default".equals(groupId)) {
            logger.error("不能删除默认分组");
            return false;
        }
        
        // 删除分组及其关联的规则
        boolean groupRemoved = groups.removeIf(group -> group.getGroupId().equals(groupId));
        boolean rulesRemoved = rules.removeIf(rule -> rule.getGroupId().equals(groupId));
        
        if (groupRemoved) {
            logger.info("删除分组: {} (移除了 {} 条规则)", groupId, rulesRemoved ? "所有" : "0");
            saveRulesToFile();
        }
        
        return groupRemoved;
    }
    
    public boolean groupExists(String groupId) {
        initializeIfNeeded();
        return groups.stream().anyMatch(group -> group.getGroupId().equals(groupId));
    }
    
    /**
     * 批量启用/禁用分组
     */
    public synchronized void setGroupEnabled(String groupId, boolean enabled) {
        initializeIfNeeded();
        groups.stream()
            .filter(group -> group.getGroupId().equals(groupId))
            .findFirst()
            .ifPresent(group -> group.setEnabled(enabled));
        
        logger.info("分组 {} 状态: {}", groupId, enabled ? "启用" : "禁用");
        saveRulesToFile();
    }
    
    /**
     * 统计数据
     */
    
    public int getTotalRuleCount() {
        initializeIfNeeded();
        return rules.size();
    }
    
    public int getEnabledRuleCount() {
        initializeIfNeeded();
        return (int) rules.stream().filter(TimeLabelRule::isEnabled).count();
    }
    
    public int getTotalGroupCount() {
        initializeIfNeeded();
        return groups.size();
    }
    
    public int getEnabledGroupCount() {
        initializeIfNeeded();
        return (int) groups.stream().filter(RuleGroup::isEnabled).count();
    }
    
    /**
     * 配置文件管理
     */
    
    @SuppressWarnings("unchecked")
    public synchronized void loadRulesFromFile() {
        long startTime = System.currentTimeMillis();
        logger.debug("开始加载规则文件");
        
        // 确保配置目录存在
        long dirCheckStart = System.currentTimeMillis();
        try {
            Files.createDirectories(Paths.get(getConfigDir()));
            logger.debug("配置目录已确保存在: {} (耗时 {}ms)", getConfigDir(), System.currentTimeMillis() - dirCheckStart);
        } catch (Exception e) {
            logger.error("创建配置目录失败: {}", getConfigDir(), e);
        }
        
        logger.debug("检查规则文件: {}", getRulesFile());
        File configFile = new File(getRulesFile());
        if (!configFile.exists()) {
            logger.info("配置文件不存在: {} (总耗时 {}ms)", getRulesFile(), System.currentTimeMillis() - startTime);
            return;
        }
        
        // 检查文件权限安全性
        long securityCheckStart = System.currentTimeMillis();
        if (!FileSecurityUtils.isFileSecure(configFile)) {
            logger.warn("配置文件权限不安全: {}", getRulesFile());
            // 尝试修复权限
            if (FileSecurityUtils.fixFilePermissionsIfInsecure(configFile)) {
                logger.info("已修复配置文件权限: {}", getRulesFile());
            }
        }
        logger.debug("安全检查完成 (耗时 {}ms)", System.currentTimeMillis() - securityCheckStart);
        
        long jsonLoadStart = System.currentTimeMillis();
        try (Reader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            
            Type configType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> config = gson.fromJson(reader, configType);
            
            if (config != null) {
                // 加载分组
                if (config.containsKey("groups")) {
                    Type groupListType = new TypeToken<List<RuleGroup>>(){}.getType();
                    List<RuleGroup> loadedGroups = gson.fromJson(gson.toJson(config.get("groups")), groupListType);
                    if (loadedGroups != null) {
                        groups.clear();
                        groups.addAll(loadedGroups);
                        logger.info("加载了 {} 个分组 (分组加载耗时 {}ms)", groups.size(), System.currentTimeMillis() - jsonLoadStart);
                    }
                }
                
                // 加载规则
                long ruleLoadStart = System.currentTimeMillis();
                if (config.containsKey("rules")) {
                    Type ruleListType = new TypeToken<List<TimeLabelRule>>(){}.getType();
                    List<TimeLabelRule> loadedRules = gson.fromJson(gson.toJson(config.get("rules")), ruleListType);
                    if (loadedRules != null) {
                        rules.clear();
                        rules.addAll(loadedRules);
                        logger.info("加载了 {} 条规则 (规则加载耗时 {}ms)", rules.size(), System.currentTimeMillis() - ruleLoadStart);
                    }
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("配置文件加载成功: {} (总耗时 {}ms)", getRulesFile(), totalTime);
            
        } catch (Exception e) {
            logger.error("加载配置文件失败: {} (总耗时 {}ms)", getRulesFile(), System.currentTimeMillis() - startTime, e);
        }
    }
    
    public synchronized boolean saveRulesToFile() {
        // 确保配置目录安全存在
        if (!FileSecurityUtils.ensureSecureConfigDir(getConfigDir())) {
            logger.error("无法确保安全配置目录: {}", getConfigDir());
            return false;
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("version", "1.0");
        config.put("lastModified", System.currentTimeMillis());
        config.put("groups", groups);
        config.put("rules", rules);
        
        // 将配置转换为JSON字符串
        String jsonContent = gson.toJson(config);
        
        // 使用安全文件保存
        boolean saved = FileSecurityUtils.saveSecureConfigFile(getRulesFile(), jsonContent);
        if (saved) {
            logger.info("安全配置文件保存成功: {} ({} 条规则, {} 个分组)", 
                getRulesFile(), rules.size(), groups.size());
        } else {
            logger.error("安全配置文件保存失败: {}", getRulesFile());
        }
        return saved;
    }
    
    /**
     * 导出规则为JSON字符串
     */
    public String exportRulesToJson() {
        Map<String, Object> config = new HashMap<>();
        config.put("version", "1.0");
        config.put("exportTime", System.currentTimeMillis());
        config.put("groups", groups);
        config.put("rules", rules);
        
        return gson.toJson(config);
    }
    
    /**
     * 从JSON字符串导入规则
     */
    public synchronized boolean importRulesFromJson(String json) {
        try {
            Type configType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> config = gson.fromJson(json, configType);
            
            if (config == null) {
                logger.error("导入的JSON格式无效");
                return false;
            }
            
            // 解析分组
            if (config.containsKey("groups")) {
                Type groupListType = new TypeToken<List<RuleGroup>>(){}.getType();
                List<RuleGroup> importedGroups = gson.fromJson(gson.toJson(config.get("groups")), groupListType);
                if (importedGroups != null) {
                    groups.clear();
                    groups.addAll(importedGroups);
                }
            }
            
            // 解析规则
            if (config.containsKey("rules")) {
                Type ruleListType = new TypeToken<List<TimeLabelRule>>(){}.getType();
                List<TimeLabelRule> importedRules = gson.fromJson(gson.toJson(config.get("rules")), ruleListType);
                if (importedRules != null) {
                    rules.clear();
                    rules.addAll(importedRules);
                }
            }
            
            logger.info("从JSON导入成功: {} 条规则, {} 个分组", rules.size(), groups.size());
            saveRulesToFile();
            return true;
            
        } catch (Exception e) {
            logger.error("从JSON导入失败", e);
            return false;
        }
    }
    
    /**
     * 重置为默认配置
     */
    public synchronized void resetToDefaults() {
        rules.clear();
        groups.clear();
        initializeDefaultGroups();
        logger.info("重置为默认配置");
        saveRulesToFile();
    }
    
    /**
     * 获取配置状态字符串（用于UI显示）
     */
    public String getStatusString() {
        return String.format("规则: %d/%d 启用, 分组: %d/%d 启用",
            getEnabledRuleCount(), getTotalRuleCount(),
            getEnabledGroupCount(), getTotalGroupCount());
    }
    
    /**
     * 重新加载规则文件（热加载支持）
     */
    public synchronized void reloadRules() {
        logger.info("重新加载时间规则配置...");
        loadRulesFromFile();
    }
    
    /**
     * 获取配置目录（公共访问）
     */
    public static String getConfigDirectory() {
        return getConfigDir();
    }
    
    /**
     * 获取规则文件路径（公共访问）
     */
    public static String getRulesFilePath() {
        return getRulesFile();
    }
}