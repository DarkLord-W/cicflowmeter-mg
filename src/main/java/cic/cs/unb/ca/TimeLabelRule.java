package cic.cs.unb.ca;

import cic.cs.unb.ca.jnetpcap.DateFormatter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间标签规则实体类
 * 定义特定时间范围内的流量标签规则
 */
public class TimeLabelRule {
    
    private String ruleId;
    private String ruleName;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String label;
    private String groupId;
    private boolean enabled;
    private int priority;
    
    // 日期时间格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 默认构造函数（用于JSON反序列化）
     */
    public TimeLabelRule() {
        this.enabled = true;
        this.priority = 0;
        this.groupId = "default";
        this.ruleId = java.util.UUID.randomUUID().toString();
    }
    
    /**
     * 全参数构造函数
     */
    public TimeLabelRule(String ruleName, LocalDateTime startDateTime, 
                        LocalDateTime endDateTime, String label, 
                        String groupId, boolean enabled, int priority) {
        this.ruleId = java.util.UUID.randomUUID().toString();
        this.ruleName = ruleName;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.label = label;
        this.groupId = groupId;
        this.enabled = enabled;
        this.priority = priority;
    }
    
    /**
     * 检查规则是否匹配给定的时间戳（微秒）
     * @param timestampMicros 微秒时间戳
     * @return 如果规则启用且时间范围匹配则返回true
     */
    public boolean matches(long timestampMicros) {
        if (!enabled) {
            return false;
        }
        
        // 将微秒时间戳转换为LocalDateTime
        LocalDateTime flowTime = convertMicrosToLocalDateTime(timestampMicros);
        
        // 检查时间范围（包含边界）
        return !flowTime.isBefore(startDateTime) && !flowTime.isAfter(endDateTime);
    }
    
    /**
     * 将微秒时间戳转换为LocalDateTime
     */
    private LocalDateTime convertMicrosToLocalDateTime(long timestampMicros) {
        // 微秒转毫秒
        long timestampMillis = timestampMicros / 1000L;
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestampMillis),
            ZoneId.systemDefault()
        );
    }
    
    /**
     * 验证规则的有效性
     * @return 如果规则有效则返回null，否则返回错误信息
     */
    public String validate() {
        if (ruleName == null || ruleName.trim().isEmpty()) {
            return "规则名称不能为空";
        }
        
        if (startDateTime == null || endDateTime == null) {
            return "开始时间或结束时间不能为空";
        }
        
        if (!endDateTime.isAfter(startDateTime)) {
            return "结束时间必须晚于开始时间";
        }
        
        if (label == null || label.trim().isEmpty()) {
            return "标签不能为空";
        }
        
        if (!LabelConstants.isValidLabel(label)) {
            return "无效的标签: " + label;
        }
        
        if (priority < 0 || priority > 10) {
            return "优先级必须在0-10之间";
        }
        
        return null;
    }
    
    /**
     * 检查时间范围是否与另一个规则重叠
     */
    public boolean overlapsWith(TimeLabelRule otherRule) {
        if (!this.groupId.equals(otherRule.groupId)) {
            return false; // 不同分组的规则不检查重叠
        }
        
        return this.startDateTime.isBefore(otherRule.endDateTime) &&
               this.endDateTime.isAfter(otherRule.startDateTime);
    }
    
    /**
     * 获取格式化的开始时间字符串
     */
    public String getFormattedStartTime() {
        return startDateTime.format(DATE_TIME_FORMATTER);
    }
    
    /**
     * 获取格式化的结束时间字符串
     */
    public String getFormattedEndTime() {
        return endDateTime.format(DATE_TIME_FORMATTER);
    }
    
    /**
     * 从格式化的字符串解析LocalDateTime
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
    }
    
    // Getter和Setter方法
    
    public String getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }
    
    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }
    
    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }
    
    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Override
    public String toString() {
        return String.format("TimeLabelRule{ruleName='%s', start=%s, end=%s, label='%s', enabled=%s, priority=%d}",
            ruleName, getFormattedStartTime(), getFormattedEndTime(), label, enabled, priority);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeLabelRule that = (TimeLabelRule) o;
        return ruleId.equals(that.ruleId);
    }
    
    @Override
    public int hashCode() {
        return ruleId.hashCode();
    }
}