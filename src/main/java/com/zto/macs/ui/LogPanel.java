package com.zto.macs.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * 日志面板 — 捕获 java.util.logging 输出并在 UI 中实时显示
 */
public class LogPanel extends JPanel {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    private final JTextPane logTextPane = new JTextPane();
    private final StyledDocument doc = logTextPane.getStyledDocument();

    // 日志级别对应的颜色
    private static final Color COLOR_INFO    = new Color(0x1A1A1A);
    private static final Color COLOR_WARNING = new Color(0xB8860B);
    private static final Color COLOR_SEVERE  = new Color(0xCC0000);
    private static final Color COLOR_DEBUG   = new Color(0x666666);

    // 样式定义
    private static final String STYLE_INFO    = "info";
    private static final String STYLE_WARNING = "warning";
    private static final String STYLE_SEVERE  = "severe";
    private static final String STYLE_DEBUG   = "debug";

    private final LogHandler logHandler;
    private int logCount = 0;
    private static final int MAX_LOG_LINES = 2000;

    public LogPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // 标题栏
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0xF0F0F0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD2D2D7)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel titleLabel = new JLabel("应用日志");
        titleLabel.setFont(new Font("Helvetica Neue", Font.BOLD, 12));
        header.add(titleLabel, BorderLayout.WEST);

        JButton clearBtn = new JButton("清除");
        clearBtn.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        clearBtn.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> clearLog());
        header.add(clearBtn, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // 日志文本区
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font("Menlo", Font.PLAIN, 11));
        logTextPane.setBackground(Color.WHITE);
        logTextPane.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        // 注册样式
        Style def = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style infoStyle = doc.addStyle(STYLE_INFO, def);
        StyleConstants.setForeground(infoStyle, COLOR_INFO);

        Style warnStyle = doc.addStyle(STYLE_WARNING, def);
        StyleConstants.setForeground(warnStyle, COLOR_WARNING);

        Style severeStyle = doc.addStyle(STYLE_SEVERE, def);
        StyleConstants.setForeground(severeStyle, COLOR_SEVERE);

        Style debugStyle = doc.addStyle(STYLE_DEBUG, def);
        StyleConstants.setForeground(debugStyle, COLOR_DEBUG);

        JScrollPane scrollPane = new JScrollPane(logTextPane);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);

        // 创建日志处理器并添加到 Root Logger
        logHandler = new LogHandler();
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.addHandler(logHandler);
    }

    /**
     * 追加一行日志
     */
    public void appendLog(Level level, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String levelName = level.getName();
        String line = String.format("[%s] %s  %s%n", timestamp, levelName, message);

        String styleName;
        if (level == Level.SEVERE) {
            styleName = STYLE_SEVERE;
        } else if (level == Level.WARNING) {
            styleName = STYLE_WARNING;
        } else if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) {
            styleName = STYLE_DEBUG;
        } else {
            styleName = STYLE_INFO;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // 限制行数
                if (logCount >= MAX_LOG_LINES) {
                    // 移除前 200 行
                    String text = logTextPane.getText();
                    int idx = indexOfNthNewline(text, 200);
                    if (idx > 0) {
                        doc.remove(0, idx + 1);
                        logCount -= 200;
                    }
                }

                doc.insertString(doc.getLength(), line, doc.getStyle(styleName));
                logCount++;

                // 自动滚动到底部
                logTextPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {
            }
        });
    }

    private static int indexOfNthNewline(String str, int n) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') {
                count++;
                if (count == n) return i;
            }
        }
        return -1;
    }

    public void clearLog() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
                logCount = 0;
            } catch (BadLocationException ignored) {
            }
        });
    }

    /**
     * 自定义 java.util.logging Handler，将日志转发到 LogPanel
     */
    private class LogHandler extends Handler {

        private final Formatter logFormatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage();
            }
        };

        LogHandler() {
            setFormatter(logFormatter);
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            // 跳过已经由我们自己的 Logger 直接输出的日志（防重复）
            if (record.getSourceClassName() != null
                    && record.getSourceClassName().contains("LogPanel")) {
                return;
            }

            String msg;
            try {
                msg = getFormatter().format(record);
            } catch (Exception e) {
                msg = record.getMessage();
            }

            // 如果有异常堆栈，追加
            Throwable thrown = record.getThrown();
            if (thrown != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintWriter pw = new PrintWriter(baos);
                thrown.printStackTrace(pw);
                pw.flush();
                msg += "\n" + baos.toString();
            }

            appendLog(record.getLevel(), msg);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }
}
