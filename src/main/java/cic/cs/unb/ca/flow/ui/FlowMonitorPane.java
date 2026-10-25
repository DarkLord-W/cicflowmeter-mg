package cic.cs.unb.ca.flow.ui;

import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.ApiConfigManager;
import cic.cs.unb.ca.LabelConstants;
import cic.cs.unb.ca.TimeLabelRule;
import cic.cs.unb.ca.RuleGroup;
import cic.cs.unb.ca.TimeRuleManager;
import cic.cs.unb.ca.flow.FlowMgr;
import cic.cs.unb.ca.guava.Event.FlowVisualEvent;
import cic.cs.unb.ca.guava.GuavaMgr;
import cic.cs.unb.ca.jnetpcap.BasicFlow;
import cic.cs.unb.ca.jnetpcap.FlowFeature;
import cic.cs.unb.ca.jnetpcap.worker.ApiSendTask;
import cic.cs.unb.ca.jnetpcap.PcapIfWrapper;
import cic.cs.unb.ca.jnetpcap.worker.LoadPcapInterfaceWorker;
import cic.cs.unb.ca.jnetpcap.worker.TrafficFlowWorker;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jnetpcap.PcapIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cic.cs.unb.ca.jnetpcap.worker.InsertCsvRow;
import swing.common.InsertTableRow;
import swing.common.JTable2CSVWorker;
import swing.common.TextFileFilter;
import cic.cs.unb.ca.TimeRuleManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlowMonitorPane extends JPanel {
    protected static final Logger logger = LoggerFactory.getLogger(FlowMonitorPane.class);


    private JTable flowTable;
    private DefaultTableModel defaultTableModel;
    private JList<PcapIfWrapper> list;
    private DefaultListModel<PcapIfWrapper> listModel;
    private JLabel lblStatus;
    private JLabel lblFlowCnt;

    private TrafficFlowWorker mWorker;

    private JButton btnLoad;
    private JToggleButton btnStart;
    private JToggleButton btnStop;
    private ButtonGroup btnGroup;

    private JButton btnSave = new JButton();
    private File lastSave;
    private JButton btnGraph = new JButton();
    private JButton btnConfig = new JButton();
    private JButton btnRuleConfig = new JButton();
    private JFileChooser fileChooser;

    private ExecutorService csvWriterThread;
    private ExecutorService apiWriterThread;
    private JLabel lblApiStats;
    private JLabel lblRuleStats;

    public FlowMonitorPane() {
        init();

        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(initCenterPane(), BorderLayout.CENTER);
    }

    private void init() {
        csvWriterThread = Executors.newSingleThreadExecutor();
        apiWriterThread = Executors.newSingleThreadExecutor();
        ApiSendTask.resetCounters();
        updateRuleStatsOnInit();
    }

    public void destory() {
        csvWriterThread.shutdown();
        apiWriterThread.shutdown();
    }

    private JPanel initCenterPane(){
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,initFlowPane(), initNWifsPane());
        splitPane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(1.0);

        pane.add(splitPane,BorderLayout.CENTER);
        return pane;
    }

    private JPanel initFlowPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 5));
        pane.setBorder(BorderFactory.createLineBorder(new Color(0x555555)));

        pane.add(initTableBtnPane(), BorderLayout.NORTH);
        pane.add(initTablePane(), BorderLayout.CENTER);
        pane.add(initStatusPane(), BorderLayout.SOUTH);

        return pane;
    }

    private JPanel initTablePane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));


        String[] arrayHeader = StringUtils.split(FlowFeature.getHeader(), ",");
        defaultTableModel = new DefaultTableModel(arrayHeader,0);
        flowTable = new JTable(defaultTableModel);
        flowTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scrollPane = new JScrollPane(flowTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));


        pane.add(scrollPane,BorderLayout.CENTER);

        return pane;
    }

    private JPanel initTableBtnPane(){
        JPanel btnPane = new JPanel();
        btnPane.setLayout(new BoxLayout(btnPane, BoxLayout.X_AXIS));
        btnSave = new JButton("Save as");
        btnGraph = new JButton("Graphs");
        btnSave.setFocusable(false);
        btnSave.setEnabled(false);
        btnGraph.setFocusable(false);
        btnGraph.setEnabled(false);
        btnGraph.setToolTipText("可视化已保存的CSV文件（保存文件后启用）");

        String dataPath = FlowMgr.getInstance().getmDataPath();
        File initialDir = (dataPath != null) ? new File(dataPath) : new File(System.getProperty("user.dir"));
        fileChooser = new JFileChooser(initialDir);
        TextFileFilter csvChooserFilter = new TextFileFilter("csv file (*.csv)", new String[]{"csv"});
        fileChooser.setFileFilter(csvChooserFilter);

        btnSave.addActionListener(actionEvent -> {
            int action = fileChooser.showSaveDialog(FlowMonitorPane.this);
            if (action == JFileChooser.APPROVE_OPTION) {

                File selectedFile = fileChooser.getSelectedFile();
                String filename = selectedFile.getName();
                if (FilenameUtils.getExtension(filename).equalsIgnoreCase("csv")) {
                    //save name ok
                } else {
                    selectedFile = new File(selectedFile.getParentFile(), FilenameUtils.getBaseName(filename) + ".csv");
                }
                String title = "file conflict";
                String message = "Another file with the same name already exists,do you want to overwrite?";

                if (selectedFile.exists()) {

                    int reply = JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION);

                    if (reply == JOptionPane.YES_OPTION) {
                        JTable2CSVWorker worker = new JTable2CSVWorker(flowTable, selectedFile);
                        worker.execute();
                    } else {
                        btnSave.doClick();
                    }
                } else {
                    JTable2CSVWorker worker = new JTable2CSVWorker(flowTable, selectedFile);
                    worker.execute();
                }
                lastSave = selectedFile;
                btnGraph.setEnabled(true);
            }

        });

        btnGraph.addActionListener(actionEvent -> GuavaMgr.getInstance().getEventBus().post(new FlowVisualEvent(lastSave)));

        btnConfig = new JButton("API设置");
        btnConfig.setFocusable(false);
        btnConfig.addActionListener(actionEvent -> showApiConfigDialog());
        
        btnRuleConfig = new JButton("时间规则设置");
        btnRuleConfig.setFocusable(false);
        btnRuleConfig.addActionListener(actionEvent -> showTimeRuleConfigDialog());
        
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnSave);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnGraph);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnConfig);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnRuleConfig);
        btnPane.add(Box.createHorizontalGlue());

        btnPane.setBorder(BorderFactory.createRaisedSoftBevelBorder());

        return btnPane;
    }

    private JPanel initStatusPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new GridLayout(1, 4, 5, 0));
        lblStatus = new JLabel("Get ready");
        lblStatus.setForeground(SystemColor.desktop);
        lblFlowCnt = new JLabel("流量: 0");
        lblApiStats = new JLabel("API: 0/0");
        lblApiStats.setForeground(new Color(0x0066CC));
        lblRuleStats = new JLabel("规则: 0/0启用");
        lblRuleStats.setForeground(new Color(0x009900));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.add(lblStatus);
        
        JPanel flowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        flowPanel.add(lblFlowCnt);
        
        JPanel apiPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        apiPanel.add(lblApiStats);
        
        JPanel rulePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rulePanel.add(lblRuleStats);
        
        pane.add(statusPanel);
        pane.add(flowPanel);
        pane.add(apiPanel);
        pane.add(rulePanel);

        return pane;
    }

    private JPanel initNWifsPane() {
        JPanel pane = new JPanel(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createLineBorder(new Color(0x555555)));
        pane.add(initNWifsButtonPane(), BorderLayout.WEST);
        pane.add(initNWifsListPane(), BorderLayout.CENTER);

        return pane;
    }

    private JPanel initNWifsButtonPane() {
        JPanel pane = new JPanel();
        pane.setBorder(BorderFactory.createEmptyBorder(10,15,10,15));
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));

        Dimension d = new Dimension(80,48);

        btnLoad = new JButton("Load");
        btnLoad.setMinimumSize(d);
        btnLoad.setMaximumSize(d);
        btnLoad.addActionListener(actionEvent -> loadPcapIfs());

        btnStart = new JToggleButton("Start");
        btnStart.setMinimumSize(d);
        btnStart.setMaximumSize(d);
        btnStart.setEnabled(false);
        btnStart.addActionListener(actionEvent -> startTrafficFlow());

        btnStop = new JToggleButton("Stop");
        btnStop.setMinimumSize(d);
        btnStop.setMaximumSize(d);
        btnStop.setEnabled(false);
        btnStop.addActionListener(actionEvent -> stopTrafficFlow());

        btnGroup = new ButtonGroup();
        btnGroup.add(btnStart);
        btnGroup.add(btnStop);

        pane.add(Box.createVerticalGlue());
        pane.add(btnLoad);
        pane.add(Box.createVerticalGlue());
        pane.add(btnStart);
        pane.add(Box.createVerticalGlue());
        pane.add(btnStop);
        pane.add(Box.createVerticalGlue());

        return pane;
    }

    private JPanel initNWifsListPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));

        listModel = new DefaultListModel<>();
        listModel.addElement(new PcapIfWrapper("Click Load button to load network interfaces"));
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));

        pane.add(scrollPane,BorderLayout.CENTER);
        return pane;
    }

    private void loadPcapIfs() {
        LoadPcapInterfaceWorker task = new LoadPcapInterfaceWorker();
        task.addPropertyChangeListener(event -> {
            if ("state".equals(event.getPropertyName())) {
                LoadPcapInterfaceWorker task1 = (LoadPcapInterfaceWorker) event.getSource();
                switch (task1.getState()) {
                    case STARTED:
                        break;
                    case DONE:
                        try {
                            java.util.List<PcapIf> ifs = task1.get();
                            List<PcapIfWrapper> pcapiflist = PcapIfWrapper.fromPcapIf(ifs);

                            listModel.removeAllElements();
                            for(PcapIfWrapper pcapif :pcapiflist) {
                                listModel.addElement(pcapif);
                            }
                            btnStart.setEnabled(true);
                            btnGroup.clearSelection();

                            lblStatus.setText("pick one network interface to listening");
                            lblStatus.validate();

                        } catch (InterruptedException | ExecutionException e) {
                            reportTaskFailure(e, "加载网卡列表失败");
                        }
                        break;
                }
            }
        });
        task.execute();
    }


    /**
     * 把后台任务的失败显式报到界面上。之前这里只写 debug 日志，
     * 用户点 Load / Start 时看到的就是"没反应"。
     */
    private void reportTaskFailure(Exception e, String title) {
        Throwable cause = (e instanceof ExecutionException && e.getCause() != null) ? e.getCause() : e;
        String detail = cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
        logger.error("{}: {}", title, detail);

        String hint;
        if (cause instanceof UnsatisfiedLinkError
                || String.valueOf(cause.getMessage()).contains("jnetpcap")
                || String.valueOf(cause.getMessage()).contains("dlopen")) {
            hint = "\n\njnetpcap 原生库没有加载成功。用发行包里的 bin/cicflowmeter-gui 启动，" +
                   "或显式传入 -Djava.library.path=<jnetpcap 目录>；" +
                   "另外确认系统里存在 libpcap.so（libpcap-dev 提供）。";
        } else {
            hint = "\n\n若需要更多信息，用 -Dlog.level=DEBUG 启动查看日志。";
        }
        JOptionPane.showMessageDialog(this, title + "\n\n" + detail + hint,
                title, JOptionPane.ERROR_MESSAGE);
    }

    private void startTrafficFlow() {

        String ifName = list.getSelectedValue().name();

        if (mWorker != null && !mWorker.isCancelled()) {
            return;
        }

        mWorker = new TrafficFlowWorker(ifName);
        mWorker.addPropertyChangeListener(event -> {
            TrafficFlowWorker task = (TrafficFlowWorker) event.getSource();
            if("progress".equals(event.getPropertyName())){
                lblStatus.setText((String) event.getNewValue());
                lblStatus.validate();
            }else if (TrafficFlowWorker.PROPERTY_FLOW.equalsIgnoreCase(event.getPropertyName())) {
                insertFlow((BasicFlow) event.getNewValue());
            }else if ("state".equals(event.getPropertyName())) {
                switch (task.getState()) {
                    case STARTED:
                        break;
                    case DONE:
                        try {
                            lblStatus.setText(task.get());
                            lblStatus.validate();
                        } catch(CancellationException e){

                            lblStatus.setText("stop listening");
                            lblStatus.setForeground(SystemColor.GRAY);
                            lblStatus.validate();
                            logger.info("Pcap stop listening");

                        }catch (InterruptedException | ExecutionException e) {
                            reportTaskFailure(e, "启动抓包失败");
                        }
                        break;
                }
            }
        });
        mWorker.execute();
        lblStatus.setForeground(SystemColor.desktop);
        btnLoad.setEnabled(false);
        btnStop.setEnabled(true);
    }

    private void stopTrafficFlow() {

        if (mWorker != null) {
            mWorker.cancel(true);
        }

        //FlowMgr.getInstance().stopFetchFlow();

        btnLoad.setEnabled(true);


        String path = FlowMgr.getInstance().getAutoSaveFile();
        logger.info("path:{}", path);

        if(defaultTableModel.getRowCount()>0 && new File(path).exists()) {
            StringBuilder msg = new StringBuilder("数据导出完成");
            msg.append(Sys.LINE_SEP).append(Sys.LINE_SEP);
            msg.append("CSV文件: ").append(path);
            
            // 添加API统计信息
            int apiSent = ApiSendTask.getTotalSent();
            int apiFailed = ApiSendTask.getTotalFailed();
            int apiTotal = apiSent + apiFailed;
            
            if (ApiConfigManager.getInstance().isApiEnabled() && apiTotal > 0) {
                msg.append(Sys.LINE_SEP).append(Sys.LINE_SEP);
                msg.append("API发送统计:");
                msg.append(Sys.LINE_SEP);
                msg.append("- 发送成功: ").append(apiSent);
                msg.append(Sys.LINE_SEP);
                msg.append("- 发送失败: ").append(apiFailed);
                msg.append(Sys.LINE_SEP);
                msg.append("- 成功率: ").append(apiTotal > 0 ? String.format("%.1f%%", (apiSent * 100.0 / apiTotal)) : "0%");
            }

            UIManager.put("OptionPane.minimumSize",new Dimension(0, 0));
            JOptionPane.showMessageDialog(this.getParent(),msg.toString());
        }
    }

    private void insertFlow(BasicFlow flow) {
        List<String> flowStringList = new ArrayList<>();
        List<String[]> flowDataList = new ArrayList<>();
        String flowDump = flow.dumpFlowBasedFeaturesEx();
        flowStringList.add(flowDump);
        flowDataList.add(StringUtils.split(flowDump, ","));

        String header  = FlowFeature.getHeader();
        ApiConfigManager config = ApiConfigManager.getInstance();
        
        // CSV导出
        if (config.isCsvEnabled()) {
            String path = FlowMgr.getInstance().getSavePath();
            String filename = LocalDate.now().toString() + FlowMgr.FLOW_SUFFIX;
            csvWriterThread.execute(new InsertCsvRow(header, flowStringList, path, filename));
        }
        
        // API发送
        if (config.isApiEnabled() && config.getApiUrl() != null && !config.getApiUrl().trim().isEmpty()) {
            apiWriterThread.execute(new ApiSendTask(
                config.getApiUrl(), 
                flowDump, 
                header,
                true
            ));
        }

        //insert flows to JTable
        SwingUtilities.invokeLater(new InsertTableRow(defaultTableModel,flowDataList,lblFlowCnt));
        btnSave.setEnabled(true);
        
        // 更新API统计信息
        if (SwingUtilities.isEventDispatchThread()) {
            updateApiStats();
        } else {
            SwingUtilities.invokeLater(this::updateApiStats);
        }
    }
    
    private void updateApiStats() {
        int sent = ApiSendTask.getTotalSent();
        int failed = ApiSendTask.getTotalFailed();
        if (sent > 0 || failed > 0) {
            lblApiStats.setText(String.format("API: %d/%d ✓", sent, sent + failed));
        } else {
            lblApiStats.setText("API: 未启用");
        }
    }
    
    private void showApiConfigDialog() {
        JDialog configDialog = new JDialog((Frame)SwingUtilities.getWindowAncestor(this), "API和CSV配置", true);
        configDialog.setLayout(new BorderLayout());
        configDialog.setSize(500, 350);
        configDialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // API配置部分
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel apiTitle = new JLabel("API配置");
        apiTitle.setFont(apiTitle.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(apiTitle, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        mainPanel.add(new JLabel("API URL:"), gbc);
        
        JTextField apiUrlField = new JTextField(ApiConfigManager.getInstance().getApiUrl(), 30);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        mainPanel.add(apiUrlField, gbc);
        
        JCheckBox apiEnabledCheck = new JCheckBox("启用API发送");
        apiEnabledCheck.setSelected(ApiConfigManager.getInstance().isApiEnabled());
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        mainPanel.add(apiEnabledCheck, gbc);
        
        // CSV配置部分
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JLabel csvTitle = new JLabel("CSV导出配置");
        csvTitle.setFont(csvTitle.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(csvTitle, gbc);
        
        gbc.gridwidth = 1;
        gbc.gridy = 4;
        gbc.gridx = 0;
        mainPanel.add(new JLabel("CSV保存路径:"), gbc);
        
        String customPath = ApiConfigManager.getInstance().getCsvSavePath();
        JTextField csvPathField = new JTextField(customPath != null ? customPath : "", 25);
        gbc.gridx = 1;
        mainPanel.add(csvPathField, gbc);
        
        JButton browseBtn = new JButton("浏览...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择CSV保存目录");
            String currentPath = csvPathField.getText().trim();
            if (!currentPath.isEmpty()) {
                chooser.setCurrentDirectory(new File(currentPath));
            }
            if (chooser.showOpenDialog(configDialog) == JFileChooser.APPROVE_OPTION) {
                csvPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(browseBtn, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JCheckBox csvEnabledCheck = new JCheckBox("启用CSV导出");
        csvEnabledCheck.setSelected(ApiConfigManager.getInstance().isCsvEnabled());
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        mainPanel.add(csvEnabledCheck, gbc);
        
        JCheckBox useDefaultCsvPath = new JCheckBox("使用默认路径");
        useDefaultCsvPath.setSelected(customPath == null || customPath.isEmpty());
        useDefaultCsvPath.addActionListener(e -> {
            csvPathField.setEnabled(!useDefaultCsvPath.isSelected());
            browseBtn.setEnabled(!useDefaultCsvPath.isSelected());
        });
        gbc.gridy = 6;
        mainPanel.add(useDefaultCsvPath, gbc);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            // 保存API配置
            ApiConfigManager.getInstance().setApiUrl(apiUrlField.getText().trim());
            ApiConfigManager.getInstance().setApiEnabled(apiEnabledCheck.isSelected());
            
            // 保存CSV配置
            ApiConfigManager.getInstance().setCsvEnabled(csvEnabledCheck.isSelected());
            if (!useDefaultCsvPath.isSelected() && !csvPathField.getText().trim().isEmpty()) {
                ApiConfigManager.getInstance().setCsvSavePath(csvPathField.getText().trim());
                FlowMgr.getInstance().setCustomSavePath(csvPathField.getText().trim());
            } else {
                ApiConfigManager.getInstance().setCsvSavePath(null);
                FlowMgr.getInstance().setDefaultSavePath();
            }
            
            updateApiStats();
            configDialog.dispose();
            
            JOptionPane.showMessageDialog(configDialog, 
                "配置已保存\n\n" + ApiConfigManager.getInstance().getStatusString(),
                "配置已保存", 
                JOptionPane.INFORMATION_MESSAGE);
        });
        
        JButton resetBtn = new JButton("重置");
        resetBtn.addActionListener(e -> {
            apiUrlField.setText("http://127.0.0.1:5000/flows");
            apiEnabledCheck.setSelected(false);
            csvEnabledCheck.setSelected(true);
            csvPathField.setText("");
            useDefaultCsvPath.setSelected(true);
            csvPathField.setEnabled(false);
            browseBtn.setEnabled(false);
        });
        
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> configDialog.dispose());
        
        buttonPanel.add(saveBtn);
        buttonPanel.add(resetBtn);
        buttonPanel.add(cancelBtn);
        
        configDialog.add(mainPanel, BorderLayout.CENTER);
        configDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // 初始启用/禁用状态
        csvPathField.setEnabled(!useDefaultCsvPath.isSelected());
        browseBtn.setEnabled(!useDefaultCsvPath.isSelected());
        
        configDialog.setVisible(true);
    }
    
    private void updateFlowCountOnUI(int count) {
        lblFlowCnt.setText("流量: " + count);
    }
    
    /**
     * 显示时间规则配置对话框
     */
    private void showTimeRuleConfigDialog() {
        TimeRuleManager ruleManager = TimeRuleManager.getInstance();
        
        // 创建对话框
        JDialog configDialog = new JDialog(
            (Frame)SwingUtilities.getWindowAncestor(this), 
            "时间规则配置", 
            true
        );
        configDialog.setLayout(new BorderLayout(10, 10));
        configDialog.setSize(900, 700);
        configDialog.setLocationRelativeTo(this);
        
        // 主面板 - 使用GridBagLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // 1. 规则列表表格
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        
        String[] columnNames = {"启用", "规则名称", "开始时间", "结束时间", "标签", "优先级", "分组", "规则ID"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Boolean.class;
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // 只允许编辑"启用"列
            }
        };
        
        JTable ruleTable = new JTable(tableModel);
        ruleTable.setRowHeight(25);
        ruleTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        ruleTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        ruleTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        ruleTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        ruleTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        ruleTable.getColumnModel().getColumn(5).setPreferredWidth(60);
        ruleTable.getColumnModel().getColumn(6).setPreferredWidth(100);
        // 隐藏规则ID列（第7列）
        ruleTable.getColumnModel().getColumn(7).setMinWidth(0);
        ruleTable.getColumnModel().getColumn(7).setMaxWidth(0);
        ruleTable.getColumnModel().getColumn(7).setPreferredWidth(0);
        
        JScrollPane tableScrollPane = new JScrollPane(ruleTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "时间规则列表", 
            TitledBorder.LEFT, TitledBorder.TOP));
        
        mainPanel.add(tableScrollPane, gbc);
        
        // 2. 规则编辑区域
        gbc.gridy = 1;
        gbc.weighty = 0.3;
        
        JPanel editPanel = new JPanel();
        editPanel.setLayout(new GridBagLayout());
        editPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "规则编辑", 
            TitledBorder.LEFT, TitledBorder.TOP));
        
        GridBagConstraints gbcEdit = new GridBagConstraints();
        gbcEdit.fill = GridBagConstraints.HORIZONTAL;
        gbcEdit.insets = new Insets(3, 5, 3, 5);
        
        // 规则名称
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 0;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("规则名称:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JTextField ruleNameField = new JTextField(20);
        editPanel.add(ruleNameField, gbcEdit);
        
        // 开始时间
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 1;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("开始时间:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JSpinner startTimeSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor startEditor = new JSpinner.DateEditor(startTimeSpinner, "yyyy-MM-dd HH:mm:ss");
        startTimeSpinner.setEditor(startEditor);
        startTimeSpinner.setValue(new java.util.Date());
        editPanel.add(startTimeSpinner, gbcEdit);
        
        // 结束时间
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 2;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("结束时间:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JSpinner endTimeSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor endEditor = new JSpinner.DateEditor(endTimeSpinner, "yyyy-MM-dd HH:mm:ss");
        endTimeSpinner.setEditor(endEditor);
        endTimeSpinner.setValue(new java.util.Date());
        editPanel.add(endTimeSpinner, gbcEdit);
        
        // 标签选择
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 3;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("标签:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JComboBox<String> labelComboBox = new JComboBox<>(LabelConstants.getPredefinedLabels());
        labelComboBox.setSelectedItem("BENIGN");
        editPanel.add(labelComboBox, gbcEdit);
        
        // 优先级
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 4;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("优先级:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JSpinner prioritySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        editPanel.add(prioritySpinner, gbcEdit);
        
        // 分组选择
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 5;
        gbcEdit.weightx = 0.0;
        editPanel.add(new JLabel("分组:"), gbcEdit);
        
        gbcEdit.gridx = 1;
        gbcEdit.weightx = 1.0;
        JComboBox<String> groupComboBox = new JComboBox<>();
        updateGroupComboBox(groupComboBox, ruleManager);
        editPanel.add(groupComboBox, gbcEdit);
        
        // 启用复选框
        gbcEdit.gridx = 0;
        gbcEdit.gridy = 6;
        gbcEdit.gridwidth = 2;
        JCheckBox enabledCheck = new JCheckBox("启用规则", true);
        editPanel.add(enabledCheck, gbcEdit);
        
        mainPanel.add(editPanel, gbc);
        
        // 3. 按钮区域
        gbc.gridy = 2;
        gbc.weighty = 0.1;
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        // 加载规则到表格
        loadRulesToTable(tableModel, ruleManager);
        
        // 表格选择监听器
        ruleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = ruleTable.getSelectedRow();
                if (selectedRow >= 0) {
                    loadRuleFromTable(selectedRow, tableModel, ruleNameField, 
                                     startTimeSpinner, endTimeSpinner, labelComboBox,
                                     prioritySpinner, groupComboBox, enabledCheck);
                }
            }
        });
        
        // 添加规则按钮
        JButton addButton = new JButton("添加规则");
        addButton.addActionListener(e -> {
            if (validateAndAddRule(ruleNameField, startTimeSpinner, endTimeSpinner,
                                  labelComboBox, prioritySpinner, groupComboBox,
                                  enabledCheck, ruleManager)) {
                loadRulesToTable(tableModel, ruleManager);
                clearEditFields(ruleNameField, startTimeSpinner, endTimeSpinner,
                              labelComboBox, prioritySpinner, groupComboBox, enabledCheck);
                updateRuleStats();
            }
        });
        
        // 更新规则按钮
        JButton updateButton = new JButton("更新规则");
        updateButton.addActionListener(e -> {
            int selectedRow = ruleTable.getSelectedRow();
            if (selectedRow >= 0) {
                String ruleId = (String) tableModel.getValueAt(selectedRow, 7); // 隐藏的ruleId列
                if (validateAndUpdateRule(ruleId, ruleNameField, startTimeSpinner, endTimeSpinner,
                                         labelComboBox, prioritySpinner, groupComboBox,
                                         enabledCheck, ruleManager)) {
                    loadRulesToTable(tableModel, ruleManager);
                    updateRuleStats();
                }
            } else {
                JOptionPane.showMessageDialog(configDialog, "请先选择要更新的规则", "警告", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 删除规则按钮
        JButton deleteButton = new JButton("删除规则");
        deleteButton.addActionListener(e -> {
            int selectedRow = ruleTable.getSelectedRow();
            if (selectedRow >= 0) {
                String ruleId = (String) tableModel.getValueAt(selectedRow, 7); // 隐藏的ruleId列
                int confirm = JOptionPane.showConfirmDialog(configDialog, 
                    "确定要删除这条规则吗？", "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    if (ruleManager.removeRule(ruleId)) {
                        loadRulesToTable(tableModel, ruleManager);
                        updateRuleStats();
                        clearEditFields(ruleNameField, startTimeSpinner, endTimeSpinner,
                                      labelComboBox, prioritySpinner, groupComboBox, enabledCheck);
                        JOptionPane.showMessageDialog(configDialog, "规则删除成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(configDialog, "规则删除失败，规则可能不存在", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(configDialog, "请先选择要删除的规则", "警告", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 分组管理按钮
        JButton groupButton = new JButton("分组管理");
        groupButton.addActionListener(e -> showGroupManagementDialog(ruleManager, groupComboBox));
        
        // 导入/导出按钮
        JButton importButton = new JButton("导入");
        importButton.addActionListener(e -> importRules(ruleManager, tableModel));
        
        JButton exportButton = new JButton("导出");
        exportButton.addActionListener(e -> exportRules(ruleManager));
        
        // 关闭按钮
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> configDialog.dispose());
        
        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(groupButton);
        buttonPanel.add(importButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(closeButton);
        
        mainPanel.add(buttonPanel, gbc);
        
        // 添加主面板到对话框
        configDialog.add(mainPanel, BorderLayout.CENTER);
        
        // 状态标签
        JLabel statusLabel = new JLabel(ruleManager.getStatusString());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        configDialog.add(statusLabel, BorderLayout.SOUTH);
        
        configDialog.setVisible(true);
        
        // 更新规则状态显示
        updateRuleStats();
    }
    
    /**
     * 更新规则状态显示
     */
    private void updateRuleStats() {
        if (lblRuleStats == null) {
            return; // UI not initialized yet
        }
        TimeRuleManager manager = TimeRuleManager.getInstance();
        lblRuleStats.setText(String.format("规则: %d/%d启用",
            manager.getEnabledRuleCount(),
            manager.getTotalRuleCount()));
    }
    
    /**
     * 初始化时更新规则状态
     */
    public void updateRuleStatsOnInit() {
        updateRuleStats();
    }
    
    // ==================== 时间规则对话框辅助方法 ====================
    
    /**
     * 加载规则到表格
     */
    private void loadRulesToTable(DefaultTableModel tableModel, TimeRuleManager ruleManager) {
        tableModel.setRowCount(0); // 清空表格
        
        // 添加隐藏的ruleId列数据
        for (TimeLabelRule rule : ruleManager.getAllRules()) {
            Object[] rowData = {
                rule.isEnabled(),
                rule.getRuleName(),
                rule.getFormattedStartTime(),
                rule.getFormattedEndTime(),
                rule.getLabel(),
                String.valueOf(rule.getPriority()),
                rule.getGroupId(),
                rule.getRuleId() // 隐藏的ruleId，第7列
            };
            tableModel.addRow(rowData);
        }
    }
    
    /**
     * 从表格加载规则到编辑字段
     */
    private void loadRuleFromTable(int row, DefaultTableModel tableModel,
                                  JTextField ruleNameField, JSpinner startTimeSpinner,
                                  JSpinner endTimeSpinner, JComboBox<String> labelComboBox,
                                  JSpinner prioritySpinner, JComboBox<String> groupComboBox,
                                  JCheckBox enabledCheck) {
        try {
            ruleNameField.setText((String) tableModel.getValueAt(row, 1));
            
            // 解析时间字符串
            String startTimeStr = (String) tableModel.getValueAt(row, 2);
            String endTimeStr = (String) tableModel.getValueAt(row, 3);
            
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            startTimeSpinner.setValue(format.parse(startTimeStr));
            endTimeSpinner.setValue(format.parse(endTimeStr));
            
            labelComboBox.setSelectedItem((String) tableModel.getValueAt(row, 4));
            prioritySpinner.setValue(Integer.parseInt((String) tableModel.getValueAt(row, 5)));
            groupComboBox.setSelectedItem((String) tableModel.getValueAt(row, 6));
            enabledCheck.setSelected((Boolean) tableModel.getValueAt(row, 0));
            
        } catch (Exception e) {
            logger.error("加载规则数据失败", e);
            JOptionPane.showMessageDialog(null, "加载规则数据失败: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * 验证并添加规则
     */
    private boolean validateAndAddRule(JTextField ruleNameField, JSpinner startTimeSpinner,
                                      JSpinner endTimeSpinner, JComboBox<String> labelComboBox,
                                      JSpinner prioritySpinner, JComboBox<String> groupComboBox,
                                      JCheckBox enabledCheck, TimeRuleManager ruleManager) {
        try {
            // 获取字段值
            String ruleName = ruleNameField.getText().trim();
            if (ruleName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "规则名称不能为空", "验证错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            java.util.Date startDate = (java.util.Date) startTimeSpinner.getValue();
            java.util.Date endDate = (java.util.Date) endTimeSpinner.getValue();
            
            if (!endDate.after(startDate)) {
                JOptionPane.showMessageDialog(null, "结束时间必须晚于开始时间", "验证错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            String label = (String) labelComboBox.getSelectedItem();
            int priority = (Integer) prioritySpinner.getValue();
            String groupId = (String) groupComboBox.getSelectedItem();
            boolean enabled = enabledCheck.isSelected();
            
            // 转换为LocalDateTime
            java.time.LocalDateTime startDateTime = startDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            java.time.LocalDateTime endDateTime = endDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            
            // 创建规则对象
            TimeLabelRule rule = new TimeLabelRule(ruleName, startDateTime, endDateTime, 
                                                 label, groupId, enabled, priority);
            
            // 添加规则
            if (ruleManager.addRule(rule)) {
                JOptionPane.showMessageDialog(null, "规则添加成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } else {
                JOptionPane.showMessageDialog(null, "规则添加失败，请检查规则是否已存在", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("添加规则失败", e);
            JOptionPane.showMessageDialog(null, "添加规则失败: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    /**
     * 验证并更新规则
     */
    private boolean validateAndUpdateRule(String ruleId, JTextField ruleNameField, 
                                         JSpinner startTimeSpinner, JSpinner endTimeSpinner,
                                         JComboBox<String> labelComboBox, JSpinner prioritySpinner,
                                         JComboBox<String> groupComboBox, JCheckBox enabledCheck,
                                         TimeRuleManager ruleManager) {
        try {
            // 获取字段值
            String ruleName = ruleNameField.getText().trim();
            if (ruleName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "规则名称不能为空", "验证错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            java.util.Date startDate = (java.util.Date) startTimeSpinner.getValue();
            java.util.Date endDate = (java.util.Date) endTimeSpinner.getValue();
            
            if (!endDate.after(startDate)) {
                JOptionPane.showMessageDialog(null, "结束时间必须晚于开始时间", "验证错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            String label = (String) labelComboBox.getSelectedItem();
            int priority = (Integer) prioritySpinner.getValue();
            String groupId = (String) groupComboBox.getSelectedItem();
            boolean enabled = enabledCheck.isSelected();
            
            // 转换为LocalDateTime
            java.time.LocalDateTime startDateTime = startDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            java.time.LocalDateTime endDateTime = endDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
            
            // 创建更新后的规则对象
            TimeLabelRule updatedRule = new TimeLabelRule(ruleName, startDateTime, endDateTime, 
                                                        label, groupId, enabled, priority);
            updatedRule.setRuleId(ruleId); // 保持相同的ruleId
            
            // 更新规则
            if (ruleManager.updateRule(updatedRule)) {
                JOptionPane.showMessageDialog(null, "规则更新成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } else {
                JOptionPane.showMessageDialog(null, "规则更新失败，规则可能不存在", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("更新规则失败", e);
            JOptionPane.showMessageDialog(null, "更新规则失败: " + e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    /**
     * 清空编辑字段
     */
    private void clearEditFields(JTextField ruleNameField, JSpinner startTimeSpinner,
                                JSpinner endTimeSpinner, JComboBox<String> labelComboBox,
                                JSpinner prioritySpinner, JComboBox<String> groupComboBox,
                                JCheckBox enabledCheck) {
        ruleNameField.setText("");
        startTimeSpinner.setValue(new java.util.Date());
        endTimeSpinner.setValue(new java.util.Date());
        labelComboBox.setSelectedItem("BENIGN");
        prioritySpinner.setValue(0);
        groupComboBox.setSelectedIndex(0);
        enabledCheck.setSelected(true);
    }
    
    /**
     * 更新分组下拉框
     */
    private void updateGroupComboBox(JComboBox<String> groupComboBox, TimeRuleManager ruleManager) {
        groupComboBox.removeAllItems();
        for (RuleGroup group : ruleManager.getAllGroups()) {
            groupComboBox.addItem(group.getGroupId());
        }
    }
    
    /**
     * 显示分组管理对话框
     */
    private void showGroupManagementDialog(TimeRuleManager ruleManager, JComboBox<String> groupComboBox) {
        // 创建分组管理对话框
        JDialog groupDialog = new JDialog(
            (Frame)SwingUtilities.getWindowAncestor(this), 
            "分组管理", 
            true
        );
        groupDialog.setLayout(new BorderLayout(10, 10));
        groupDialog.setSize(700, 500);
        groupDialog.setLocationRelativeTo(this);
        
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        
        // 1. 分组列表表格
        String[] columnNames = {"分组ID", "分组名称", "描述", "启用"};
        DefaultTableModel groupTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 3) return Boolean.class;
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // 只允许编辑"启用"列
            }
        };
        
        JTable groupTable = new JTable(groupTableModel);
        groupTable.setRowHeight(25);
        groupTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        groupTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        groupTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        groupTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        
        // 加载分组数据
        loadGroupsToTable(groupTableModel, ruleManager);
        
        // 表格启用状态变化监听器
        groupTableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column == 3 && row >= 0) {
                String groupId = (String) groupTableModel.getValueAt(row, 0);
                boolean enabled = (Boolean) groupTableModel.getValueAt(row, 3);
                ruleManager.setGroupEnabled(groupId, enabled);
                updateGroupComboBox(groupComboBox, ruleManager);
            }
        });
        
        JScrollPane tableScrollPane = new JScrollPane(groupTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "分组列表", 
            TitledBorder.LEFT, TitledBorder.TOP));
        
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 2. 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        JButton addButton = new JButton("添加分组");
        addButton.addActionListener(e -> {
            showAddEditGroupDialog(null, ruleManager, groupTableModel, groupComboBox, groupDialog);
        });
        
        JButton editButton = new JButton("编辑分组");
        editButton.addActionListener(e -> {
            int selectedRow = groupTable.getSelectedRow();
            if (selectedRow >= 0) {
                String groupId = (String) groupTableModel.getValueAt(selectedRow, 0);
                RuleGroup selectedGroup = findGroupById(groupId, ruleManager);
                if (selectedGroup != null) {
                    showAddEditGroupDialog(selectedGroup, ruleManager, groupTableModel, groupComboBox, groupDialog);
                }
            } else {
                JOptionPane.showMessageDialog(groupDialog, "请先选择要编辑的分组", "警告", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        JButton deleteButton = new JButton("删除分组");
        deleteButton.addActionListener(e -> {
            int selectedRow = groupTable.getSelectedRow();
            if (selectedRow >= 0) {
                String groupId = (String) groupTableModel.getValueAt(selectedRow, 0);
                String groupName = (String) groupTableModel.getValueAt(selectedRow, 1);
                
                // 不能删除默认分组
                if ("default".equals(groupId)) {
                    JOptionPane.showMessageDialog(groupDialog, "不能删除默认分组", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                int confirm = JOptionPane.showConfirmDialog(groupDialog, 
                    String.format("确定要删除分组 '%s' 吗？\n该分组下的所有规则也将被删除！", groupName), 
                    "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                
                if (confirm == JOptionPane.YES_OPTION) {
                    if (ruleManager.removeGroup(groupId)) {
                        loadGroupsToTable(groupTableModel, ruleManager);
                        updateGroupComboBox(groupComboBox, ruleManager);
                        JOptionPane.showMessageDialog(groupDialog, "分组删除成功", "成功", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(groupDialog, "分组删除失败", "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(groupDialog, "请先选择要删除的分组", "警告", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> groupDialog.dispose());
        
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 添加主面板到对话框
        groupDialog.add(mainPanel, BorderLayout.CENTER);
        
        // 状态标签
        JLabel statusLabel = new JLabel(String.format("共 %d 个分组", ruleManager.getAllGroups().size()));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        groupDialog.add(statusLabel, BorderLayout.NORTH);
        
        groupDialog.setVisible(true);
    }
    
    /**
     * 加载分组到表格
     */
    private void loadGroupsToTable(DefaultTableModel tableModel, TimeRuleManager ruleManager) {
        tableModel.setRowCount(0); // 清空表格
        
        for (RuleGroup group : ruleManager.getAllGroups()) {
            Object[] rowData = {
                group.getGroupId(),
                group.getGroupName(),
                group.getDescription(),
                group.isEnabled()
            };
            tableModel.addRow(rowData);
        }
    }
    
    /**
     * 根据ID查找分组
     */
    private RuleGroup findGroupById(String groupId, TimeRuleManager ruleManager) {
        for (RuleGroup group : ruleManager.getAllGroups()) {
            if (group.getGroupId().equals(groupId)) {
                return group;
            }
        }
        return null;
    }
    
    /**
     * 显示添加/编辑分组对话框
     */
    private void showAddEditGroupDialog(RuleGroup group, TimeRuleManager ruleManager, 
                                      DefaultTableModel groupTableModel, 
                                      JComboBox<String> groupComboBox, 
                                      JDialog parentDialog) {
        boolean isEditMode = group != null;
        
        JDialog dialog = new JDialog(parentDialog, isEditMode ? "编辑分组" : "添加分组", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(parentDialog);
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // 分组ID（编辑模式下不可修改）
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("分组ID:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField groupIdField = new JTextField(20);
        if (isEditMode) {
            groupIdField.setText(group.getGroupId());
            groupIdField.setEditable(false);
        } else {
            // 新分组生成随机ID
            groupIdField.setText(java.util.UUID.randomUUID().toString().substring(0, 8));
        }
        mainPanel.add(groupIdField, gbc);
        
        // 分组名称
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("分组名称:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField groupNameField = new JTextField(20);
        if (isEditMode) {
            groupNameField.setText(group.getGroupName());
        }
        mainPanel.add(groupNameField, gbc);
        
        // 分组描述
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        mainPanel.add(new JLabel("描述:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextArea descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        if (isEditMode) {
            descriptionArea.setText(group.getDescription());
        }
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        mainPanel.add(descriptionScrollPane, gbc);
        
        // 启用状态
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JCheckBox enabledCheck = new JCheckBox("启用分组", true);
        if (isEditMode) {
            enabledCheck.setSelected(group.isEnabled());
        }
        mainPanel.add(enabledCheck, gbc);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton saveButton = new JButton("保存");
        saveButton.addActionListener(e -> {
            String groupId = groupIdField.getText().trim();
            String groupName = groupNameField.getText().trim();
            String description = descriptionArea.getText().trim();
            boolean enabled = enabledCheck.isSelected();
            
            // 验证
            if (groupId.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "分组ID不能为空", "验证错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "分组名称不能为空", "验证错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 创建或更新分组
            RuleGroup newGroup = new RuleGroup(groupId, groupName, description, enabled);
            
            boolean success;
            if (isEditMode) {
                // 更新分组 - 需要保持相同的groupId
                success = ruleManager.addGroup(newGroup); // addGroup会覆盖同ID分组
            } else {
                // 添加新分组
                success = ruleManager.addGroup(newGroup);
            }
            
            if (success) {
                loadGroupsToTable(groupTableModel, ruleManager);
                updateGroupComboBox(groupComboBox, ruleManager);
                dialog.dispose();
                JOptionPane.showMessageDialog(parentDialog, 
                    isEditMode ? "分组更新成功" : "分组添加成功", 
                    "成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialog, 
                    "操作失败，分组ID可能已存在", 
                    "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> dialog.dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }
    
    /**
     * 导入规则
     */
    private void importRules(TimeRuleManager ruleManager, DefaultTableModel tableModel) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择规则JSON文件");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON文件 (*.json)", "json"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // 读取JSON文件内容
                String json = new String(java.nio.file.Files.readAllBytes(selectedFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                
                // 导入规则
                if (ruleManager.importRulesFromJson(json)) {
                    // 刷新表格
                    loadRulesToTable(tableModel, ruleManager);
                    updateRuleStats();
                    JOptionPane.showMessageDialog(null, 
                        String.format("规则导入成功！\n导入规则数: %d\n导入分组数: %d", 
                            ruleManager.getTotalRuleCount(), 
                            ruleManager.getTotalGroupCount()),
                        "导入成功", 
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, 
                        "规则导入失败，请检查JSON文件格式是否正确", 
                        "导入失败", 
                        JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                logger.error("导入规则失败", e);
                JOptionPane.showMessageDialog(null, 
                    "导入规则失败: " + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * 导出规则
     */
    private void exportRules(TimeRuleManager ruleManager) {
        String json = ruleManager.exportRulesToJson();
        
        JTextArea textArea = new JTextArea(json, 20, 60);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        
        JOptionPane.showMessageDialog(null, scrollPane, "规则导出 (JSON格式)", 
            JOptionPane.INFORMATION_MESSAGE);
    }
}
