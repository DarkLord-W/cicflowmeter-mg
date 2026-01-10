package cic.cs.unb.ca;

/**
 * 规则分组实体类
 * 支持规则的批量启用/禁用和管理
 */
public class RuleGroup {
    
    private String groupId;
    private String groupName;
    private String description;
    private boolean enabled;
    
    /**
     * 默认分组ID
     */
    public static final String DEFAULT_GROUP_ID = "default";
    
    /**
     * 默认构造函数（用于JSON反序列化）
     */
    public RuleGroup() {
        this.enabled = true;
        this.groupId = java.util.UUID.randomUUID().toString();
    }
    
    /**
     * 构造函数（自动生成分组ID）
     */
    public RuleGroup(String groupName, String description, boolean enabled) {
        this.groupId = java.util.UUID.randomUUID().toString();
        this.groupName = groupName;
        this.description = description;
        this.enabled = enabled;
    }
    
    /**
     * 构造函数（指定分组ID）
     */
    public RuleGroup(String groupId, String groupName, String description, boolean enabled) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
        this.enabled = enabled;
    }
    
    /**
     * 验证分组的有效性
     * @return 如果分组有效则返回null，否则返回错误信息
     */
    public String validate() {
        if (groupName == null || groupName.trim().isEmpty()) {
            return "分组名称不能为空";
        }
        
        if (groupId == null || groupId.trim().isEmpty()) {
            return "分组ID不能为空";
        }
        
        return null;
    }
    
    /**
     * 获取默认分组（用于未指定分组的规则）
     */
    public static RuleGroup getDefaultGroup() {
        RuleGroup defaultGroup = new RuleGroup();
        defaultGroup.setGroupId("default");
        defaultGroup.setGroupName("默认分组");
        defaultGroup.setDescription("未指定分组的规则");
        defaultGroup.setEnabled(true);
        return defaultGroup;
    }
    
    // Getter和Setter方法
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public String toString() {
        return String.format("RuleGroup{groupId='%s', groupName='%s', enabled=%s}",
            groupId, groupName, enabled);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleGroup ruleGroup = (RuleGroup) o;
        return groupId.equals(ruleGroup.groupId);
    }
    
    @Override
    public int hashCode() {
        return groupId.hashCode();
    }
}