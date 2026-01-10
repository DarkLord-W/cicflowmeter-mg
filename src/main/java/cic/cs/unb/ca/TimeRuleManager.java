package cic.cs.unb.ca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import cic.cs.unb.ca.Sys;
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
    
    // 配置文件路径
    private static String getConfigDir() {
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
        
        // 初始化默认分组
        initializeDefaultGroups();
        
        // 加载保存的规则
        loadRulesFromFile();
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
     * 根据时间戳获取匹配的标签
     * @param timestampMicros 微秒时间戳
     * @return 匹配的标签，如果没有匹配则返回默认标签
     */
    public String getLabelForTimestamp(long timestampMicros) {
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
        
        return LabelConstants.getDefaultLabel();
    }
    
    /**
     * 获取所有启用的规则
     */
    public List<TimeLabelRule> getEnabledRules() {
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
        return new ArrayList<>(rules);
    }
    
    /**
     * 清除所有规则
     */
    public synchronized void clearAllRules() {
        rules.clear();
        logger.info("清除所有规则");
        saveRulesToFile();
    }
    
    /**
     * 清除所有分组（保留默认分组）
     */
    public synchronized void clearAllGroups() {
        groups.removeIf(group -> !group.getGroupId().equals(RuleGroup.DEFAULT_GROUP_ID));
        logger.info("清除所有非默认分组");
        saveRulesToFile();
    }
    
    /**
     * 添加规则
     */
    public synchronized boolean addRule(TimeLabelRule rule) {
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
        return rules.stream().anyMatch(rule -> rule.getRuleName().equals(ruleName));
    }
    
    /**
     * 检查时间重叠
     */
    private boolean hasTimeOverlap(TimeLabelRule newRule) {
        return rules.stream()
            .filter(rule -> rule.getGroupId().equals(newRule.getGroupId()))
            .anyMatch(newRule::overlapsWith);
    }
    
    /**
     * 分组管理方法
     */
    
    public List<RuleGroup> getAllGroups() {
        return new ArrayList<>(groups);
    }
    
    public synchronized boolean addGroup(RuleGroup group) {
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
        return groups.stream().anyMatch(group -> group.getGroupId().equals(groupId));
    }
    
    /**
     * 批量启用/禁用分组
     */
    public synchronized void setGroupEnabled(String groupId, boolean enabled) {
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
        return rules.size();
    }
    
    public int getEnabledRuleCount() {
        return (int) rules.stream().filter(TimeLabelRule::isEnabled).count();
    }
    
    public int getTotalGroupCount() {
        return groups.size();
    }
    
    public int getEnabledGroupCount() {
        return (int) groups.stream().filter(RuleGroup::isEnabled).count();
    }
    
    /**
     * 配置文件管理
     */
    
    @SuppressWarnings("unchecked")
    public synchronized void loadRulesFromFile() {
        // 调试信息
        System.err.println("[DEBUG] getConfigDir(): " + getConfigDir());
        System.err.println("[DEBUG] getRulesFile(): " + getRulesFile());
        System.err.println("[DEBUG] user.home: " + System.getProperty("user.home"));
        System.err.println("[DEBUG] Sys.FILE_SEP: " + Sys.FILE_SEP);
        
        // 确保配置目录存在
        try {
            Files.createDirectories(Paths.get(getConfigDir()));
            logger.debug("配置目录已确保存在: {}", getConfigDir());
        } catch (Exception e) {
            logger.error("创建配置目录失败: {}", getConfigDir(), e);
        }
        
        logger.debug("加载规则文件: {}", getRulesFile());
        File configFile = new File(getRulesFile());
        if (!configFile.exists()) {
            logger.info("配置文件不存在: {}", getRulesFile());
            return;
        }
        
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
                        logger.info("加载了 {} 个分组", groups.size());
                    }
                }
                
                // 加载规则
                if (config.containsKey("rules")) {
                    Type ruleListType = new TypeToken<List<TimeLabelRule>>(){}.getType();
                    List<TimeLabelRule> loadedRules = gson.fromJson(gson.toJson(config.get("rules")), ruleListType);
                    if (loadedRules != null) {
                        rules.clear();
                        rules.addAll(loadedRules);
                        logger.info("加载了 {} 条规则", rules.size());
                    }
                }
            }
            
            logger.info("配置文件加载成功: {}", getRulesFile());
            
        } catch (Exception e) {
            logger.error("加载配置文件失败: {}", getRulesFile(), e);
        }
    }
    
    public synchronized boolean saveRulesToFile() {
        File configDir = new File(getConfigDir());
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                logger.error("创建配置目录失败: {}", getConfigDir());
                return false;
            }
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("version", "1.0");
        config.put("lastModified", System.currentTimeMillis());
        config.put("groups", groups);
        config.put("rules", rules);
        
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(getRulesFile()), StandardCharsets.UTF_8)) {
            
            gson.toJson(config, writer);
            logger.info("配置文件保存成功: {} ({} 条规则, {} 个分组)", 
                getRulesFile(), rules.size(), groups.size());
            return true;
            
        } catch (Exception e) {
            logger.error("保存配置文件失败: {}", getRulesFile(), e);
            return false;
        }
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
}