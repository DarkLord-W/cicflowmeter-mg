package cic.cs.unb.ca.flow.ui;

import cic.cs.unb.ca.Sys;
import cic.cs.unb.ca.ApiConfigManager;
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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
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
    private JFileChooser fileChooser;

    private ExecutorService csvWriterThread;
    private ExecutorService apiWriterThread;
    private JLabel lblApiStats;

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
        
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnSave);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnGraph);
        btnPane.add(Box.createHorizontalGlue());
        btnPane.add(btnConfig);
        btnPane.add(Box.createHorizontalGlue());

        btnPane.setBorder(BorderFactory.createRaisedSoftBevelBorder());

        return btnPane;
    }

    private JPanel initStatusPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new GridLayout(1, 3, 5, 0));
        lblStatus = new JLabel("Get ready");
        lblStatus.setForeground(SystemColor.desktop);
        lblFlowCnt = new JLabel("流量: 0");
        lblApiStats = new JLabel("API: 0/0");
        lblApiStats.setForeground(new Color(0x0066CC));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusPanel.add(lblStatus);
        
        JPanel flowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        flowPanel.add(lblFlowCnt);
        
        JPanel apiPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        apiPanel.add(lblApiStats);
        
        pane.add(statusPanel);
        pane.add(flowPanel);
        pane.add(apiPanel);

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
                            logger.debug(e.getMessage());
                        }
                        break;
                }
            }
        });
        task.execute();
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
                            logger.debug(e.getMessage());
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
            apiUrlField.setText("http://127.0.0.1:5000/predict");
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
}
