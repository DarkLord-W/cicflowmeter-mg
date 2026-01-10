package cic.cs.unb.ca.ifm;

import cic.cs.unb.ca.ApiConfigManager;
import cic.cs.unb.ca.flow.FlowMgr;
import cic.cs.unb.ca.guava.GuavaMgr;
import cic.cs.unb.ca.ifm.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.*;

public class App {
	public static final Logger logger = LoggerFactory.getLogger(App.class);
	public static void init() {
		FlowMgr.getInstance().init();
		GuavaMgr.getInstance().init();
		
		// 初始化API配置管理器，打印默认配置状态
		ApiConfigManager config = ApiConfigManager.getInstance();
		logger.info("API配置初始化完成");
		logger.info("默认API URL: {}", config.getApiUrl());
		logger.info("API启用状态: {}", config.isApiEnabled() ? "已启用" : "已禁用");
		logger.info("CSV启用状态: {}", config.isCsvEnabled() ? "已启用" : "已禁用");
	}
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		/*try {
			//UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		} catch (InstantiationException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		} catch (UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}*/
		
		EventQueue.invokeLater(() -> {
            try {
                init();
                new MainFrame();
            } catch (Exception e) {
				logger.debug(e.getMessage());
            }
        });
	}
}
