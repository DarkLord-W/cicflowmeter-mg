package cic.cs.unb.ca;

/**
 * 标签常量定义类
 * 包含15个预定义的网络流量标签
 */
public class LabelConstants {
    
    /**
     * 预定义标签数组 - 按用户提供的顺序
     */
    public static final String[] PREDEFINED_LABELS = {
        "BENIGN",
        "FTP-Patator",
        "SSH-Patator",
        "DoS slowloris",
        "DoS Slowhttptest",
        "DoS Hulk",
        "DoS GoldenEye",
        "Heartbleed",
        "Web Attack – Brute Force",
        "Web Attack – XSS",
        "Web Attack – SQL Injection",
        "Infiltration",
        "Bot",
        "PortScan",
        "DDoS"
    };
    
    /**
     * 默认标签 - 当没有规则匹配时使用
     */
    public static final String DEFAULT_LABEL = "NeedManualLabel";
    
    /**
     * 获取所有预定义标签的副本（避免直接修改数组）
     * @return 标签数组的副本
     */
    public static String[] getPredefinedLabels() {
        return PREDEFINED_LABELS.clone();
    }
    
    /**
     * 检查标签是否是预定义的有效标签
     * @param label 要检查的标签
     * @return 如果是预定义标签则返回true
     */
    public static boolean isValidLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return false;
        }
        
        for (String predefinedLabel : PREDEFINED_LABELS) {
            if (predefinedLabel.equals(label)) {
                return true;
            }
        }
        
        return label.equals(DEFAULT_LABEL);
    }
    
    /**
     * 获取标签在数组中的索引，如果不存在则返回-1
     * @param label 要查找的标签
     * @return 索引位置，如果不存在则返回-1
     */
    public static int getLabelIndex(String label) {
        if (label == null) {
            return -1;
        }
        
        for (int i = 0; i < PREDEFINED_LABELS.length; i++) {
            if (PREDEFINED_LABELS[i].equals(label)) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * 获取默认标签
     * @return 默认标签字符串
     */
    public static String getDefaultLabel() {
        return DEFAULT_LABEL;
    }
    
    /**
     * 将标签数组转换为逗号分隔的字符串
     * @return 逗号分隔的标签字符串
     */
    public static String getLabelsAsString() {
        return String.join(", ", PREDEFINED_LABELS);
    }
}