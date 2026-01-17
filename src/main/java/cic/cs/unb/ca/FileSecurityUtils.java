package cic.cs.unb.ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * 跨平台文件安全工具类
 * 提供文件权限设置功能，支持Linux/macOS (POSIX) 和 Windows
 */
public class FileSecurityUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FileSecurityUtils.class);
    
    // POSIX权限常量
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = 
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    
    private static final Set<PosixFilePermission> OWNER_ONLY_EXECUTE_PERMISSIONS = 
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, 
                   PosixFilePermission.OWNER_EXECUTE);
    
    /**
     * 检查系统是否支持POSIX文件权限
     */
    public static boolean isPosixSupported() {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查是否为Windows系统
     */
    public static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }
    
    /**
     * 设置文件仅所有者可读写（600权限）
     * 
     * @param file 要设置权限的文件
     * @return 是否成功设置权限
     */
    public static boolean setSecureFilePermissions(File file) {
        if (file == null) {
            logger.error("文件对象为null");
            return false;
        }
        
        if (!file.exists()) {
            logger.error("文件不存在: {}", file.getAbsolutePath());
            return false;
        }
        
        try {
            Path path = file.toPath();
            
            if (isPosixSupported()) {
                // Linux/macOS: 使用POSIX权限设置600
                Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS);
                logger.debug("设置POSIX权限600: {}", file.getAbsolutePath());
                return true;
                
            } else if (isWindows()) {
                // Windows: 设置仅所有者访问权限
                boolean success = true;
                success &= file.setReadable(false, false);  // 取消所有人读取权限
                success &= file.setReadable(true, true);    // 仅所有者可读
                success &= file.setWritable(false, false);  // 取消所有人写入权限
                success &= file.setWritable(true, true);    // 仅所有者可写
                success &= file.setExecutable(false, false); // 取消执行权限
                
                if (success) {
                    logger.debug("设置Windows权限（仅所有者）: {}", file.getAbsolutePath());
                } else {
                    logger.warn("Windows权限设置不完全成功: {}", file.getAbsolutePath());
                }
                return success;
                
            } else {
                // 其他不支持的系统，记录警告
                logger.warn("不支持的文件系统权限设置，文件保持原权限: {}", file.getAbsolutePath());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("设置文件权限失败: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 设置目录权限为700（所有者可读、写、执行）
     * 
     * @param dir 要设置权限的目录
     * @return 是否成功设置权限
     */
    public static boolean setSecureDirectoryPermissions(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            logger.error("目录不存在或不是目录: {}", dir != null ? dir.getAbsolutePath() : "null");
            return false;
        }
        
        // 检查是否在容器中运行（容器内权限设置可能失败）
        boolean isContainer = System.getenv("CONTAINER") != null || 
                            System.getenv("CIC_CONTAINER") != null ||
                            System.getenv("CIC_CONTAINER_MODE") != null ||
                            System.getProperty("cicflowmeter.container") != null;
        
        if (isContainer) {
            logger.debug("容器环境：跳过目录权限设置（挂载卷权限由外部管理）: {}", dir.getAbsolutePath());
            return true; // 在容器中返回true表示"跳过"
        }
        
        try {
            Path path = dir.toPath();
            
            if (isPosixSupported()) {
                // Linux/macOS: 设置目录权限为700
                Files.setPosixFilePermissions(path, OWNER_ONLY_EXECUTE_PERMISSIONS);
                logger.debug("设置目录POSIX权限700: {}", dir.getAbsolutePath());
                return true;
                
            } else if (isWindows()) {
                // Windows: 设置目录权限
                boolean success = true;
                success &= dir.setReadable(false, false);
                success &= dir.setReadable(true, true);
                success &= dir.setWritable(false, false);
                success &= dir.setWritable(true, true);
                success &= dir.setExecutable(false, false);
                success &= dir.setExecutable(true, true);
                
                if (success) {
                    logger.debug("设置Windows目录权限（仅所有者）: {}", dir.getAbsolutePath());
                }
                return success;
                
            } else {
                logger.warn("不支持的文件系统目录权限设置: {}", dir.getAbsolutePath());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("设置目录权限失败: {}", dir.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 确保配置目录安全存在
     * 1. 创建目录（如果不存在）
     * 2. 设置目录权限为700
     * 
     * @param dirPath 目录路径
     * @return 是否成功确保安全目录
     */
    public static boolean ensureSecureConfigDir(String dirPath) {
        if (dirPath == null || dirPath.trim().isEmpty()) {
            logger.error("目录路径为空");
            return false;
        }
        
        // 检查是否在容器中运行
        boolean isContainer = System.getenv("CONTAINER") != null || 
                            System.getenv("CIC_CONTAINER") != null ||
                            System.getenv("CIC_CONTAINER_MODE") != null ||
                            System.getProperty("cicflowmeter.container") != null;
        
        try {
            Path path = Paths.get(dirPath);
            File dir = path.toFile();
            
            // 创建目录（如果不存在）
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    if (isContainer) {
                        logger.warn("容器环境：无法创建目录，但继续执行: {}", dirPath);
                        return true; // 在容器中返回true表示"跳过"
                    } else {
                        logger.error("创建目录失败: {}", dirPath);
                        return false;
                    }
                }
                logger.info("创建配置目录: {}", dirPath);
            }
            
            // 设置目录权限（在容器中跳过）
            if (!isContainer && !setSecureDirectoryPermissions(dir)) {
                logger.warn("无法设置目录权限，但目录已存在: {}", dirPath);
                return false;
            } else if (isContainer) {
                logger.debug("容器环境：跳过目录权限设置: {}", dirPath);
            }
            
            logger.info("安全配置目录已确保: {}", dirPath);
            return true;
            
        } catch (Exception e) {
            if (isContainer) {
                logger.warn("容器环境：确保配置目录时遇到错误但继续执行: {}", dirPath, e);
                return true; // 在容器中返回true表示"跳过"
            } else {
                logger.error("确保安全配置目录失败: {}", dirPath, e);
                return false;
            }
        }
    }
    
    /**
     * 安全保存配置文件
     * 1. 确保父目录安全存在
     * 2. 写入文件内容
     * 3. 设置文件权限为600
     * 
     * @param filePath 文件路径
     * @param content 文件内容
     * @return 是否成功保存
     */
    public static boolean saveSecureConfigFile(String filePath, String content) {
        if (filePath == null || content == null) {
            logger.error("文件路径或内容为空");
            return false;
        }
        
        try {
            Path path = Paths.get(filePath);
            File file = path.toFile();
            
            // 确保父目录安全存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !ensureSecureConfigDir(parentDir.getAbsolutePath())) {
                logger.error("无法确保父目录安全: {}", parentDir.getAbsolutePath());
                return false;
            }
            
            // 写入文件内容
            Files.write(path, content.getBytes("UTF-8"));
            logger.debug("配置文件写入完成: {}", filePath);
            
            // 设置文件权限
            if (!setSecureFilePermissions(file)) {
                logger.warn("无法设置文件权限，但文件已保存: {}", filePath);
                return false;
            }
            
            logger.info("安全配置文件已保存: {}", filePath);
            return true;
            
        } catch (Exception e) {
            logger.error("保存安全配置文件失败: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 检查文件是否安全（仅所有者可读写）
     * 
     * @param file 要检查的文件
     * @return 如果文件安全返回true，否则false
     */
    public static boolean isFileSecure(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        try {
            if (isPosixSupported()) {
                // 检查POSIX权限
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file.toPath());
                
                // 检查是否只有所有者权限
                boolean ownerRead = permissions.contains(PosixFilePermission.OWNER_READ);
                boolean ownerWrite = permissions.contains(PosixFilePermission.OWNER_WRITE);
                boolean hasGroupOrOther = permissions.stream()
                    .anyMatch(p -> p != PosixFilePermission.OWNER_READ && 
                                  p != PosixFilePermission.OWNER_WRITE &&
                                  p != PosixFilePermission.OWNER_EXECUTE);
                
                return ownerRead && ownerWrite && !hasGroupOrOther;
                
            } else {
                // 在非POSIX系统上，我们无法准确检查权限
                // 返回true以避免误报
                return true;
            }
            
        } catch (Exception e) {
            logger.warn("检查文件安全状态失败: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 修复文件权限（如果发现不安全）
     * 
     * @param file 要修复的文件
     * @return 是否成功修复
     */
    public static boolean fixFilePermissionsIfInsecure(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        // 检查文件是否安全
        if (isFileSecure(file)) {
            logger.debug("文件权限已安全: {}", file.getAbsolutePath());
            return true;
        }
        
        logger.warn("文件权限不安全，正在修复: {}", file.getAbsolutePath());
        return setSecureFilePermissions(file);
    }
}