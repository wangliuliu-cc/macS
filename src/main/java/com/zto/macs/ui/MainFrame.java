package com.zto.macs.ui;

import com.zto.macs.search.FileSearcher;
import com.zto.macs.search.SearchResult;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 主界面 - Swing 桌面应用
 */
public class MainFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());

    // 颜色常量
    private static final Color COLOR_PRIMARY = new Color(0x007AFF);
    private static final Color COLOR_BG = new Color(0xF5F5F7);
    private static final Color COLOR_CARD_BG = Color.WHITE;
    private static final Color COLOR_BORDER = new Color(0xD2D2D7);
    private static final Color COLOR_HEADER_BG = new Color(0xE8E8ED);
    private static final Color COLOR_TABLE_ALT = new Color(0xF8F8FA);
    private static final Color COLOR_TEXT_SECONDARY = new Color(0x86868B);

    // 大文件阈值
    private static final int LARGE_DIR_THRESHOLD = 20000;

    // 后缀分组定义
    private static final LinkedHashMap<String, List<String>> EXTENSION_GROUPS = new LinkedHashMap<>();

    static {
        EXTENSION_GROUPS.put("Java", Arrays.asList(".java", ".jsp", ".tag", ".tld"));
        EXTENSION_GROUPS.put("XML / 配置", Arrays.asList(".xml", ".properties", ".yml", ".yaml",
                ".conf", ".ini", ".cfg", ".gradle"));
        EXTENSION_GROUPS.put("脚本", Arrays.asList(".sh", ".shell", ".bash", ".bat", ".cmd"));
        EXTENSION_GROUPS.put("前端", Arrays.asList(".js", ".jsx", ".ts", ".tsx", ".html", ".css", ".scss", ".less"));
        EXTENSION_GROUPS.put("后端", Arrays.asList(".kt", ".groovy", ".py", ".go", ".rs",
                ".cpp", ".c", ".h", ".swift"));
        EXTENSION_GROUPS.put("文档 / 数据", Arrays.asList(".txt", ".md", ".json", ".sql", ".csv", ".log"));
    }

    // 默认选中的后缀
    private static final Set<String> DEFAULT_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".java", ".xml", ".properties", ".yml", ".yaml", ".sh", ".shell"
    ));

    private final JTextField dirField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField contentField = new JTextField();
    private final JButton searchBtn = new JButton("搜索");
    private final JButton cancelBtn = new JButton("取消");
    private final JButton selectDirBtn = new JButton("浏览…");
    private final JButton extSelectBtn = new JButton("选择类型…");
    private final JLabel statusLabel = new JLabel("就绪");
    private final JCheckBox nameCheckBox = new JCheckBox("按文件名", true);
    private final JCheckBox contentCheckBox = new JCheckBox("按内容", false);

    private final ResultTableModel tableModel = new ResultTableModel();
    private final JTable resultTable = new JTable(tableModel);
    private final FileSearcher fileSearcher = new FileSearcher();
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel();

    // 日志面板
    private final LogPanel logPanel = new LogPanel();
    private final JButton toggleLogBtn = new JButton("日志 ▸");
    private boolean logPanelVisible = false;
    private final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    // 当前选中的后缀
    private final Set<String> selectedExtensions = new HashSet<>(DEFAULT_EXTENSIONS);

    public MainFrame() {
        LOG.info("初始化主界面");
        initUI();
        updateExtButtonText();
    }

    private void initUI() {
        setTitle("macS 文件搜索工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        try {
            setIconImage(createAppIcon());
        } catch (Exception ignored) {
        }

        setUIFont(new Font("Helvetica Neue", Font.PLAIN, 13));

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        mainPanel.add(createSearchPanel(), BorderLayout.NORTH);

        // 搜索结果表格
        JScrollPane scrollPane = new JScrollPane(resultTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 0, 0, 0),
                BorderFactory.createLineBorder(COLOR_BORDER, 1, true)));
        scrollPane.setBackground(COLOR_CARD_BG);
        setupResultTable();

        // 日志面板（默认折叠）
        logPanel.setPreferredSize(new Dimension(0, 0));
        logPanel.setMinimumSize(new Dimension(0, 0));

        splitPane.setTopComponent(scrollPane);
        splitPane.setBottomComponent(logPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setBorder(null);
        splitPane.setBackground(COLOR_BG);
        splitPane.setContinuousLayout(true);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        add(mainPanel);
        bindEvents();
        LOG.info("主界面初始化完成");
    }

    private void setUIFont(Font font) {
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("CheckBox.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("TableHeader.font", font.deriveFont(Font.BOLD));
        UIManager.put("ProgressBar.font", font.deriveFont(10f));
        UIManager.put("TitledBorder.font", font.deriveFont(Font.BOLD));
    }

    private JPanel createSearchPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(COLOR_CARD_BG);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(COLOR_CARD_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 3, 3, 3);

        Font labelFont = new Font("Helvetica Neue", Font.PLAIN, 13);

        // 第1行：搜索目录
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        JLabel dirLabel = new JLabel("搜索目录");
        dirLabel.setFont(labelFont);
        dirLabel.setForeground(Color.BLACK);
        content.add(dirLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        dirField.setText(System.getProperty("user.home"));
        dirField.setPreferredSize(new Dimension(0, 28));
        dirField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        content.add(dirField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        selectDirBtn.setPreferredSize(new Dimension(70, 28));
        selectDirBtn.setBackground(COLOR_CARD_BG);
        selectDirBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        selectDirBtn.setFocusPainted(false);
        content.add(selectDirBtn, gbc);

        // 第2行：文件名
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel nameLabel = new JLabel("文件名");
        nameLabel.setFont(labelFont);
        nameLabel.setForeground(Color.BLACK);
        content.add(nameLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        nameField.setToolTipText("按文件名关键词搜索，留空则不按文件名搜索");
        nameField.setPreferredSize(new Dimension(0, 28));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        content.add(nameField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        nameCheckBox.setBackground(COLOR_CARD_BG);
        content.add(nameCheckBox, gbc);

        // 第3行：文件内容
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel contentLabel = new JLabel("文件内容");
        contentLabel.setFont(labelFont);
        contentLabel.setForeground(Color.BLACK);
        content.add(contentLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        contentField.setToolTipText("按文件内容关键词搜索（仅搜索文本文件）");
        contentField.setPreferredSize(new Dimension(0, 28));
        contentField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        content.add(contentField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        contentCheckBox.setBackground(COLOR_CARD_BG);
        content.add(contentCheckBox, gbc);

        // 第4行：后缀过滤 + 按钮区
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel extLabel = new JLabel("文件类型");
        extLabel.setFont(labelFont);
        extLabel.setForeground(Color.BLACK);
        content.add(extLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        extSelectBtn.setPreferredSize(new Dimension(0, 28));
        extSelectBtn.setBackground(COLOR_CARD_BG);
        extSelectBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        extSelectBtn.setFocusPainted(false);
        extSelectBtn.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(extSelectBtn, gbc);

        gbc.gridx = 2; gbc.weightx = 0;

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setBackground(COLOR_CARD_BG);

        searchBtn.setPreferredSize(new Dimension(72, 28));
        searchBtn.setBackground(COLOR_PRIMARY);
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setOpaque(true);
        searchBtn.setBorderPainted(false);
        searchBtn.setFocusPainted(false);

        cancelBtn.setPreferredSize(new Dimension(72, 28));
        cancelBtn.setBackground(COLOR_CARD_BG);
        cancelBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        cancelBtn.setFocusPainted(false);
        cancelBtn.setEnabled(false);

        btnPanel.add(searchBtn);
        btnPanel.add(cancelBtn);
        content.add(btnPanel, gbc);

        container.add(content, BorderLayout.CENTER);
        return container;
    }

    private void setupResultTable() {
        JTableHeader header = resultTable.getTableHeader();
        header.setBackground(COLOR_HEADER_BG);
        header.setForeground(Color.BLACK);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER));
        ((DefaultTableCellRenderer) header.getDefaultRenderer())
                .setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        resultTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(500);
        resultTable.getColumnModel().getColumn(4).setPreferredWidth(350);

        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        resultTable.setRowHeight(28);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setShowGrid(false);
        resultTable.setIntercellSpacing(new Dimension(0, 0));
        resultTable.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : COLOR_TABLE_ALT);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(COLOR_PRIMARY);
                    c.setForeground(Color.WHITE);
                }
                return c;
            }
        };
        for (int i = 0; i < resultTable.getColumnCount(); i++) {
            resultTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }

        // 右键菜单
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));

        JMenuItem openItem = new JMenuItem("打开文件");
        openItem.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row >= 0) openFile(tableModel.getResultAt(row).getFile());
        });
        popupMenu.add(openItem);

        JMenuItem revealItem = new JMenuItem("在 Finder 中显示");
        revealItem.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row >= 0) revealInFinder(tableModel.getResultAt(row).getFile());
        });
        popupMenu.add(revealItem);

        popupMenu.addSeparator();

        JMenuItem copyPathItem = new JMenuItem("复制完整路径");
        copyPathItem.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row >= 0) copyPath(tableModel.getResultAt(row).getFilePath());
        });
        popupMenu.add(copyPathItem);

        JMenuItem copyNameItem = new JMenuItem("复制文件名");
        copyNameItem.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row >= 0) copyPath(tableModel.getResultAt(row).getFileName());
        });
        popupMenu.add(copyNameItem);

        resultTable.setComponentPopupMenu(popupMenu);

        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int row = resultTable.getSelectedRow();
                    if (row >= 0) openFile(tableModel.getResultAt(row).getFile());
                }
            }
        });
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(COLOR_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));

        statusLabel.setFont(new Font("Helvetica Neue", Font.PLAIN, 12));
        statusLabel.setForeground(COLOR_TEXT_SECONDARY);
        panel.add(statusLabel, BorderLayout.WEST);

        progressLabel.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        progressLabel.setForeground(COLOR_TEXT_SECONDARY);
        progressLabel.setVisible(false);
        panel.add(progressLabel, BorderLayout.CENTER);

        progressBar.setPreferredSize(new Dimension(150, 14));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setForeground(COLOR_PRIMARY);
        progressBar.setBackground(new Color(0xE8E8ED));
        progressBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        progressBar.setFont(new Font("Helvetica Neue", Font.BOLD, 10));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setBackground(COLOR_BG);

        toggleLogBtn.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        toggleLogBtn.setForeground(COLOR_TEXT_SECONDARY);
        toggleLogBtn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        toggleLogBtn.setFocusPainted(false);
        toggleLogBtn.setContentAreaFilled(false);
        toggleLogBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightPanel.add(toggleLogBtn);

        rightPanel.add(progressBar);

        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 搜索执行时禁用/恢复所有搜索控件，防止误触
     */
    private void setSearchingUI(boolean searching) {
        dirField.setEnabled(!searching);
        nameField.setEnabled(!searching);
        contentField.setEnabled(!searching);
        selectDirBtn.setEnabled(!searching);
        extSelectBtn.setEnabled(!searching);
        nameCheckBox.setEnabled(!searching);
        contentCheckBox.setEnabled(!searching);
        searchBtn.setEnabled(!searching);
        cancelBtn.setEnabled(searching);
        resultTable.setEnabled(!searching);
    }

    private void bindEvents() {
        selectDirBtn.addActionListener(e -> chooseDirectory());
        searchBtn.addActionListener(e -> startSearch());
        cancelBtn.addActionListener(e -> cancelSearch());
        extSelectBtn.addActionListener(e -> showExtensionDialog());
        toggleLogBtn.addActionListener(e -> toggleLogPanel());

        KeyStroke enter = KeyStroke.getKeyStroke("ENTER");
        InputMap inputMap = ((JPanel) getContentPane()).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(enter, "search");
        ((JPanel) getContentPane()).getActionMap().put("search", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (searchBtn.isEnabled()) startSearch();
            }
        });
    }

    private void updateExtButtonText() {
        if (selectedExtensions.isEmpty()) {
            extSelectBtn.setText("全部文件");
        } else {
            extSelectBtn.setText("已选 " + selectedExtensions.size() + " 种类型");
        }
    }

    /**
     * 展开/折叠日志面板
     */
    private void toggleLogPanel() {
        logPanelVisible = !logPanelVisible;
        if (logPanelVisible) {
            logPanel.setPreferredSize(new Dimension(0, 180));
            logPanel.setMinimumSize(new Dimension(0, 60));
            splitPane.setDividerLocation(-1);
            splitPane.setResizeWeight(0.75);
            toggleLogBtn.setText("日志 ▾");
        } else {
            logPanel.setPreferredSize(new Dimension(0, 0));
            logPanel.setMinimumSize(new Dimension(0, 0));
            splitPane.setResizeWeight(1.0);
            toggleLogBtn.setText("日志 ▸");
        }
        splitPane.revalidate();
        splitPane.repaint();
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(dirField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择搜索目录");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * 弹出后缀多选对话框
     */
    private void showExtensionDialog() {
        // 深拷贝当前选中状态
        Set<String> tempSelected = new HashSet<>(selectedExtensions);

        JDialog dialog = new JDialog(this, "选择文件类型", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        // 分组复选框面板
        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));

        Map<String, Map<JCheckBox, String>> groupCheckBoxes = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> group : EXTENSION_GROUPS.entrySet()) {
            // 组标题
            JLabel groupLabel = new JLabel(group.getKey());
            groupLabel.setFont(new Font("Helvetica Neue", Font.BOLD, 12));
            groupLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
            checkBoxPanel.add(groupLabel);

            // 组内后缀 checkbox
            JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            Map<JCheckBox, String> cbMap = new LinkedHashMap<>();

            for (String ext : group.getValue()) {
                JCheckBox cb = new JCheckBox(ext);
                cb.setSelected(tempSelected.contains(ext));
                cb.setFont(new Font("Menlo", Font.PLAIN, 11));
                groupPanel.add(cb);
                cbMap.put(cb, ext);
            }
            groupCheckBoxes.put(group.getKey(), cbMap);
            checkBoxPanel.add(groupPanel);
        }

        // 全选/取消 按钮行
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton selectAllBtn = new JButton("全选");
        JButton deselectAllBtn = new JButton("清除");
        JButton defaultBtn = new JButton("恢复默认");

        selectAllBtn.addActionListener(e ->
                groupCheckBoxes.values().forEach(m -> m.keySet().forEach(cb -> cb.setSelected(true))));
        deselectAllBtn.addActionListener(e ->
                groupCheckBoxes.values().forEach(m -> m.keySet().forEach(cb -> cb.setSelected(false))));
        defaultBtn.addActionListener(e ->
                groupCheckBoxes.values().forEach(m ->
                        m.forEach((cb, ext) -> cb.setSelected(DEFAULT_EXTENSIONS.contains(ext)))));

        actionPanel.add(selectAllBtn);
        actionPanel.add(deselectAllBtn);
        actionPanel.add(defaultBtn);
        checkBoxPanel.add(Box.createVerticalStrut(4));
        checkBoxPanel.add(actionPanel);

        JScrollPane scrollPane = new JScrollPane(checkBoxPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        scrollPane.setPreferredSize(new Dimension(480, 320));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 确定/取消
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton okBtn = new JButton("确定");
        okBtn.setBackground(COLOR_PRIMARY);
        okBtn.setForeground(Color.WHITE);
        okBtn.setOpaque(true);
        okBtn.setBorderPainted(false);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setBackground(COLOR_CARD_BG);
        cancelBtn.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));

        okBtn.addActionListener(e -> {
            selectedExtensions.clear();
            for (Map<JCheckBox, String> m : groupCheckBoxes.values()) {
                m.forEach((cb, ext) -> {
                    if (cb.isSelected()) selectedExtensions.add(ext);
                });
            }
            updateExtButtonText();
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        bottomPanel.add(okBtn);
        bottomPanel.add(cancelBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void startSearch() {
        LOG.info("===== 搜索按钮点击 =====");

        String dir = dirField.getText().trim();
        if (dir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择搜索目录");
            return;
        }
        File dirFile = new File(dir);
        if (!dirFile.isDirectory()) {
            JOptionPane.showMessageDialog(this, "目录不存在: " + dir);
            return;
        }

        String nameKeyword = (nameCheckBox.isSelected() && !nameField.getText().trim().isEmpty())
                ? nameField.getText().trim() : "";
        String contentKeyword = (contentCheckBox.isSelected() && !contentField.getText().trim().isEmpty())
                ? contentField.getText().trim() : "";

        if (nameKeyword.isEmpty() && contentKeyword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "请输入文件名或文件内容关键词\n（勾选对应选项并填入关键词）",
                    "搜索条件不足", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 使用多选框选中的后缀
        final Set<String> extensions = selectedExtensions.isEmpty() ? null : new HashSet<>(selectedExtensions);

        LOG.info("搜索参数: 目录=" + dir + ", 文件名关键词=" + (nameKeyword.isEmpty() ? "(无)" : nameKeyword)
                + ", 内容关键词=" + (contentKeyword.isEmpty() ? "(无)" : contentKeyword)
                + ", 后缀过滤=" + (extensions == null ? "(全部)" : extensions));

        tableModel.clear();

        statusLabel.setText("正在统计文件数...");
        statusLabel.setForeground(COLOR_TEXT_SECONDARY);
        setSearchingUI(true);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false);
        progressLabel.setVisible(false);

        new SwingWorker<Void, Object>() {
            @Override
            protected Void doInBackground() {
                try {
                    LOG.info("开始预统计文件数...");
                    int totalFiles = fileSearcher.countFiles(dir, extensions);
                    LOG.info("预统计完成，共 " + totalFiles + " 个文件");

                    final boolean confirmed;
                    if (totalFiles > LARGE_DIR_THRESHOLD) {
                        final boolean[] ref = {false};
                        try {
                            SwingUtilities.invokeAndWait(() -> ref[0] = confirmLargeDir(totalFiles));
                        } catch (Exception ex) {
                            LOG.warning("确认对话框异常: " + ex.getMessage());
                        }
                        confirmed = ref[0];
                    } else {
                        confirmed = true;
                    }

                    if (!confirmed) {
                        LOG.info("用户取消搜索（目录过大）");
                        SwingUtilities.invokeLater(() -> {
                            setSearchingUI(false);
                            progressBar.setVisible(false);
                            progressLabel.setVisible(false);
                            statusLabel.setText("已取消搜索");
                        });
                        return null;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (totalFiles == 0) {
                            statusLabel.setText("未找到匹配的文件");
                            setSearchingUI(false);
                            progressBar.setVisible(false);
                            progressLabel.setVisible(false);
                            return;
                        }

                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum(totalFiles);
                        progressBar.setMinimum(0);
                        progressBar.setValue(0);
                        progressBar.setStringPainted(true);
                        progressBar.setString("0%");
                        progressLabel.setVisible(true);
                        statusLabel.setText("搜索中...");
                        statusLabel.setForeground(Color.BLACK);

                        fileSearcher.search(dir, nameKeyword, contentKeyword, extensions,
                                result -> SwingUtilities.invokeLater(() -> {
                                    tableModel.addResult(result);
                                    statusLabel.setText("搜索中... 已找到 " + tableModel.getRowCount() + " 个结果");
                                }),
                                (scanned, total) -> SwingUtilities.invokeLater(() -> {
                                    int pct = (int) ((double) scanned / total * 100);
                                    progressBar.setValue(scanned);
                                    progressBar.setString(pct + "%");
                                    progressLabel.setText(scanned + "/" + total);
                                }),
                                () -> SwingUtilities.invokeLater(() -> {
                                    setSearchingUI(false);
                                    progressBar.setVisible(false);
                                    progressLabel.setVisible(false);
                                    int count = tableModel.getRowCount();
                                    statusLabel.setText("搜索完成，共找到 " + count + " 个结果");
                                    statusLabel.setForeground(COLOR_PRIMARY);
                                })
                        );
                    });
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "搜索异常", e);
                    SwingUtilities.invokeLater(() -> {
                        setSearchingUI(false);
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                        statusLabel.setText("搜索异常: " + e.getMessage());
                        statusLabel.setForeground(Color.RED);
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "搜索过程中发生异常:\n" + e.getMessage(),
                                "搜索异常", JOptionPane.ERROR_MESSAGE);
                    });
                }
                return null;
            }
        }.execute();
    }

    private boolean confirmLargeDir(int totalFiles) {
        String sizeDesc;
        if (totalFiles > 100000) sizeDesc = "超过 10 万";
        else if (totalFiles > 50000) sizeDesc = "超过 5 万";
        else sizeDesc = "超过 2 万";

        int result = JOptionPane.showConfirmDialog(this,
                "<html><body style='width: 360px; padding: 8px;'>"
                        + "<b>搜索目录文件较多</b><br><br>"
                        + "该目录下匹配后缀的文件共 <b>" + totalFiles + "</b> 个（" + sizeDesc + "），"
                        + "搜索可能需要较长时间。<br><br>"
                        + "是否继续搜索？"
                        + "</body></html>",
                "确认搜索",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    private void cancelSearch() {
        fileSearcher.cancel();
        setSearchingUI(false);
        progressBar.setVisible(false);
        progressLabel.setVisible(false);
        statusLabel.setForeground(COLOR_TEXT_SECONDARY);
        statusLabel.setText("已取消搜索，共找到 " + tableModel.getRowCount() + " 个结果");
    }

    private void openFile(File file) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", file.getAbsolutePath()});
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法打开文件: " + e.getMessage());
        }
    }

    private void revealInFinder(File file) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", "-R", file.getAbsolutePath()});
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法在 Finder 中显示: " + e.getMessage());
        }
    }

    private void copyPath(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "复制失败", e);
        }
    }

    private Image createAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g2d.setColor(COLOR_PRIMARY);
        g2d.fill(new RoundRectangle2D.Float(4, 4, 56, 56, 12, 12));

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(17, 17, 20, 20);
        g2d.drawLine(32, 32, 44, 44);

        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int bx = 12, by = 48;
        g2d.drawLine(bx, by, bx + 14, by);
        g2d.drawLine(bx, by - 4, bx + 10, by - 4);

        g2d.dispose();
        return image;
    }

    // ===== 表格模型 =====
    static class ResultTableModel extends AbstractTableModel {
        private final String[] columns = {"文件名", "大小", "匹配类型", "匹配内容", "完整路径"};
        private final List<SearchResult> results = new CopyOnWriteArrayList<>();

        void addResult(SearchResult sr) {
            results.add(sr);
            int row = results.size() - 1;
            fireTableRowsInserted(row, row);
        }

        SearchResult getResultAt(int row) {
            return results.get(row);
        }

        void clear() {
            int size = results.size();
            results.clear();
            if (size > 0) fireTableRowsDeleted(0, size - 1);
        }

        @Override
        public int getRowCount() { return results.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            SearchResult sr = results.get(row);
            switch (col) {
                case 0: return sr.getFileName();
                case 1: return sr.getFileSize();
                case 2: return sr.getMatchType();
                case 3: return sr.getMatchContext();
                case 4: return sr.getFilePath();
                default: return "";
            }
        }
    }
}
