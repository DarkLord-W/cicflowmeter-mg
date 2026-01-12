package cic.cs.unb.ca.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 健康检查服务器测试类
 */
public class TestHealthCheck {
    
    private static final Logger logger = LoggerFactory.getLogger(TestHealthCheck.class);
    
    public static void main(String[] args) {
        logger.info("启动健康检查服务器测试...");
        
        // 获取健康检查服务器实例
        HealthCheckServer server = HealthCheckServer.getInstance();
        
        // 设置端口（可选）
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                server.setPort(port);
                logger.info("设置健康检查端口: {}", port);
            } catch (NumberFormatException e) {
                logger.warn("无效的端口参数: {}, 使用默认端口 {}", args[0], server.getPort());
            }
        }
        
        // 启动服务器
        boolean started = server.start();
        if (!started) {
            logger.error("健康检查服务器启动失败");
            System.exit(1);
        }
        
        logger.info("健康检查服务器已启动");
        logger.info("访问: http://localhost:{}/health", server.getPort());
        logger.info("按Ctrl+C停止服务器");
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("正在停止健康检查服务器...");
            server.stop();
            logger.info("健康检查服务器已停止");
        }));
        
        // 保持运行
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("测试被中断");
        }
    }
}