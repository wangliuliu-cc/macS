package com.zto.macs;

import com.zto.macs.ui.MainFrame;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * macS 文件搜索工具 - 入口
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // 全局未捕获异常处理器 — 弹窗提示后退出
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOG.log(Level.SEVERE, "未捕获的异常", throwable);
            SwingUtilities.invokeLater(() -> {
                try {
                    JOptionPane.showMessageDialog(null,
                            "<html><body style='width: 400px; padding: 8px;'>"
                                    + "<b>程序发生未预期的错误</b><br><br>"
                                    + "错误信息: " + throwable.getMessage() + "<br><br>"
                                    + "程序即将退出，请检查日志后重试。"
                                    + "</body></html>",
                            "程序错误",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    System.exit(1);
                }
            });
        });

        // 在事件调度线程中启动 GUI
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
