package com.zto.macs.search;

import java.io.File;

/**
 * 搜索结果模型
 */
public class SearchResult {
    private final File file;
    private final String matchType;   // "文件名" 或 "文件内容"
    private final String matchContext; // 匹配的上下文（内容匹配时显示匹配行）

    public SearchResult(File file, String matchType, String matchContext) {
        this.file = file;
        this.matchType = matchType;
        this.matchContext = matchContext;
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return file.getName();
    }

    public String getFilePath() {
        return file.getAbsolutePath();
    }

    public String getFileSize() {
        long bytes = file.length();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public String getMatchType() {
        return matchType;
    }

    public String getMatchContext() {
        return matchContext;
    }

    public String getExtension() {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx) : "";
    }
}
