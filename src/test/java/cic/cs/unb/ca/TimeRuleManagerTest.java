package cic.cs.unb.ca;

import org.junit.Before;
import org.junit.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.Assert.*;

public class TimeRuleManagerTest {
    
    private TimeRuleManager manager;
    
    @Before
    public void setUp() {
        manager = TimeRuleManager.getInstance();
        manager.clearAllRules(); // 清理现有规则
        manager.clearAllGroups(); // 清理分组
    }
    
    @Test
    public void testSingleton() {
        TimeRuleManager instance1 = TimeRuleManager.getInstance();
        TimeRuleManager instance2 = TimeRuleManager.getInstance();
        assertSame("单例模式应返回相同实例", instance1, instance2);
    }
    
    @Test
    public void testAddAndMatchRule() {
        // 创建测试规则：当前时间前后10分钟
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusMinutes(5);
        LocalDateTime endTime = now.plusMinutes(5);
        
        TimeLabelRule rule = new TimeLabelRule(
            "test-rule",
            startTime,
            endTime,
            "BENIGN",
            "default",
            true,
            5
        );
        
        manager.addRule(rule);
        
        // 匹配当前时间（应在规则范围内）
        long timestampMicros = System.currentTimeMillis() * 1000L; // 转换为微秒
        String label = manager.getLabelForTimestamp(timestampMicros);
        
        assertEquals("应匹配BENIGN标签", "BENIGN", label);
    }
    
    @Test
    public void testNoMatchReturnsDefault() {
        // 创建一个过去的规则
        LocalDateTime pastStart = LocalDateTime.now().minusDays(2);
        LocalDateTime pastEnd = LocalDateTime.now().minusDays(1);
        
        TimeLabelRule rule = new TimeLabelRule(
            "past-rule",
            pastStart,
            pastEnd,
            "DDoS",
            "default",
            true,
            5
        );
        
        manager.addRule(rule);
        
        // 当前时间不应匹配
        long timestampMicros = System.currentTimeMillis() * 1000L;
        String label = manager.getLabelForTimestamp(timestampMicros);
        
        assertEquals("无匹配时应返回NeedManualLabel", LabelConstants.getDefaultLabel(), label);
    }
    
    @Test
    public void testPriorityOrder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMinutes(10);
        LocalDateTime end = now.plusMinutes(10);
        
        // 低优先级规则
        TimeLabelRule lowPriorityRule = new TimeLabelRule(
            "low-priority",
            start,
            end,
            "BENIGN",
            "default",
            true,
            1
        );
        
        // 高优先级规则（相同时间范围）
        TimeLabelRule highPriorityRule = new TimeLabelRule(
            "high-priority",
            start,
            end,
            "DDoS",
            "default",
            true,
            9
        );
        
        manager.addRule(lowPriorityRule);
        manager.addRule(highPriorityRule);
        
        long timestampMicros = System.currentTimeMillis() * 1000L;
        String label = manager.getLabelForTimestamp(timestampMicros);
        
        assertEquals("高优先级规则应覆盖低优先级", "DDoS", label);
    }
    
    @Test
    public void testDisabledRule() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusMinutes(5);
        LocalDateTime end = now.plusMinutes(5);
        
        TimeLabelRule disabledRule = new TimeLabelRule(
            "disabled-rule",
            start,
            end,
            "Bot",
            "default",
            false, // 禁用
            5
        );
        
        manager.addRule(disabledRule);
        
        long timestampMicros = System.currentTimeMillis() * 1000L;
        String label = manager.getLabelForTimestamp(timestampMicros);
        
        assertEquals("禁用规则不应匹配", LabelConstants.getDefaultLabel(), label);
    }
    
    @Test
    public void testSaveAndLoadRules() {
        // 创建测试规则
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now.plusHours(1);
        
        TimeLabelRule rule = new TimeLabelRule(
            "save-load-test",
            start,
            end,
            "PortScan",
            "default",
            true,
            5
        );
        
        manager.addRule(rule);
        
        // 保存规则
        boolean saveResult = manager.saveRulesToFile();
        assertTrue("保存规则应成功", saveResult);

        // 重新加载规则
        manager.loadRulesFromFile();
        List<TimeLabelRule> loadedRules = manager.getAllRules();
        
        assertEquals("加载后应恢复1条规则", 1, loadedRules.size());
        TimeLabelRule loadedRule = loadedRules.get(0);
        assertEquals("规则标签应匹配", "PortScan", loadedRule.getLabel());
        assertEquals("规则名称应匹配", "save-load-test", loadedRule.getRuleName());
    }
    
    @Test
    public void testGroupEnabledStatus() {
        // 创建分组
        RuleGroup group = new RuleGroup("test-group", "测试分组", "测试分组", true);
        manager.addGroup(group);
        
        // 创建属于该分组的规则
        LocalDateTime now = LocalDateTime.now();
        TimeLabelRule rule = new TimeLabelRule(
            "group-rule",
            now.minusMinutes(10),
            now.plusMinutes(10),
            "BENIGN",
            "test-group",
            true,
            5
        );
        
        manager.addRule(rule);
        
        // 分组启用时应匹配
        long timestampMicros = System.currentTimeMillis() * 1000L;
        String label = manager.getLabelForTimestamp(timestampMicros);
        assertEquals("分组启用时应匹配规则", "BENIGN", label);
        
        // 禁用分组
        group.setEnabled(false);
        
        // 分组禁用后不应匹配
        label = manager.getLabelForTimestamp(timestampMicros);
        assertEquals("分组禁用后不应匹配规则", LabelConstants.getDefaultLabel(), label);
    }
}