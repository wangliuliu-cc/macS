package com.zto.macs.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 核心文件搜索器 - 多线程并行搜索
 */
public class FileSearcher {

    private static final Logger LOG = Logger.getLogger(FileSearcher.class.getName());

    // 文本文件扩展名（可进行内容搜索）- 覆盖所有 Sublime Text 可编辑的格式
    public static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            // Java 生态
            ".java", ".jsp", ".jspx", ".tag", ".tld", ".dtd",
            // XML / 配置
            ".xml", ".xsd", ".xsl", ".xslt", ".wsdl", ".properties", ".yml", ".yaml",
            ".conf", ".ini", ".cfg", ".toml", ".gradle", ".pom",
            // 脚本
            ".sh", ".shell", ".bash", ".zsh", ".bat", ".cmd", ".ps1",
            // 前端
            ".js", ".jsx", ".ts", ".tsx", ".html", ".htm", ".css", ".scss", ".less",
            // 后端语言
            ".kt", ".kts", ".groovy", ".scala", ".py", ".rb", ".php", ".pl", ".pm",
            ".go", ".rs", ".swift", ".m", ".mm", ".h", ".c", ".cpp", ".cc", ".cxx",
            ".hpp", ".hxx", ".cs", ".fs", ".vue", ".svelte",
            // 模板
            ".ftl", ".vm", ".st", ".ejs", ".hbs", ".mustache",
            // 数据 / 文档
            ".txt", ".md", ".markdown", ".json", ".xml", ".sql", ".csv", ".tsv",
            ".log", ".yaml", ".yml",
            // 构建 / CI
            ".dockerfile", "dockerfile", "makefile", "gemfile",
            // Shell / 系统
            ".env", ".editorconfig", ".gitignore", ".gitattributes",
            // 其他常见文本
            ".cfg", ".cnf", ".lst", ".lst", ".sln", ".csproj"
    ));

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean running = false;

    /**
     * 预统计匹配的文件总数（快速扫描，不做内容搜索）
     *
     * @param rootDir   根目录
     * @param extensions 后缀过滤
     * @return 文件总数
     */
    public int countFiles(String rootDir, Set<String> extensions) {
        File root = new File(rootDir);
        if (!root.exists() || !root.isDirectory()) return 0;

        AtomicInteger count = new AtomicInteger(0);
        try {
            Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (dirName.startsWith(".") && !dirName.equals(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if ("node_modules".equals(dirName) || "target".equals(dirName)
                            || "build".equals(dirName) || ".idea".equals(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (cancelled.get()) return FileVisitResult.TERMINATE;

                    File file = path.toFile();
                    if (!file.isFile()) return FileVisitResult.CONTINUE;

                    // 后缀过滤
                    if (extensions != null && !extensions.isEmpty()) {
                        String ext = getExtension(file.getName()).toLowerCase();
                        if (!extensions.contains(ext)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    count.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "预统计文件数失败", e);
        }
        return count.get();
    }

    /**
     * 执行搜索
     *
     * @param rootDir          根目录
     * @param fileNameKeyword  文件名关键词
     * @param contentKeyword   文件内容关键词
     * @param extensions       文件后缀过滤
     * @param resultConsumer   结果消费者
     * @param progressCallback 进度回调 (已扫描数量, 总数)
     * @param completeCallback 完成回调
     */
    public void search(String rootDir,
                       String fileNameKeyword,
                       String contentKeyword,
                       Set<String> extensions,
                       Consumer<SearchResult> resultConsumer,
                       BiConsumer<Integer, Integer> progressCallback,
                       Runnable completeCallback) {
        cancelled.set(false);
        running = true;

        LOG.info("开始搜索 - 目录: " + rootDir
                + ", 文件名关键词: " + (fileNameKeyword.isEmpty() ? "(无)" : fileNameKeyword)
                + ", 内容关键词: " + (contentKeyword.isEmpty() ? "(无)" : contentKeyword)
                + ", 后缀过滤: " + (extensions == null || extensions.isEmpty() ? "(无)" : extensions));

        File root = new File(rootDir);
        if (!root.exists() || !root.isDirectory()) {
            LOG.warning("目录不存在: " + rootDir);
            running = false;
            if (completeCallback != null) completeCallback.run();
            return;
        }

        // 先预统计文件总数
        int totalFiles = countFiles(rootDir, extensions);
        LOG.info("预统计完成，待扫描文件数: " + totalFiles);
        final AtomicInteger scannedCount = new AtomicInteger(0);

        int processors = Runtime.getRuntime().availableProcessors();
        ForkJoinPool forkJoinPool = new ForkJoinPool(Math.max(processors, 4));

        CompletableFuture.runAsync(() -> {
            try {
                Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (cancelled.get()) {
                            LOG.fine("搜索已取消，终止遍历");
                            return FileVisitResult.TERMINATE;
                        }
                        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (dirName.startsWith(".") && !dirName.equals(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if ("node_modules".equals(dirName) || "target".equals(dirName)
                                || "build".equals(dirName) || ".idea".equals(dirName)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        if (cancelled.get()) return FileVisitResult.TERMINATE;

                        File file = path.toFile();
                        if (!file.isFile()) return FileVisitResult.CONTINUE;

                        // 后缀过滤
                        if (extensions != null && !extensions.isEmpty()) {
                            String ext = getExtension(file.getName()).toLowerCase();
                            if (!extensions.contains(ext)) {
                                return FileVisitResult.CONTINUE;
                            }
                        }

                        // 更新进度
                        int scanned = scannedCount.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(scanned, totalFiles);
                        }

                        String fileName = file.getName();

                        // 按文件名搜索
                        boolean nameMatched = false;
                        if (fileNameKeyword != null && !fileNameKeyword.isEmpty()) {
                            if (fileName.toLowerCase().contains(fileNameKeyword.toLowerCase())) {
                                nameMatched = true;
                                SearchResult sr = new SearchResult(
                                        file, "文件名", "文件名包含: " + fileNameKeyword);
                                if (resultConsumer != null) resultConsumer.accept(sr);
                                LOG.finest("文件名匹配: " + file.getAbsolutePath());
                            }
                        }

                        // 按内容搜索
                        if (contentKeyword != null && !contentKeyword.isEmpty() && !nameMatched) {
                            String ext = getExtension(fileName).toLowerCase();
                            if (TEXT_EXTENSIONS.contains(ext) || isLikelyTextFile(file)) {
                                String matchedLine = searchContent(file, contentKeyword);
                                if (matchedLine != null) {
                                    SearchResult sr = new SearchResult(
                                            file, "文件内容", matchedLine);
                                    if (resultConsumer != null) resultConsumer.accept(sr);
                                    LOG.finest("内容匹配: " + file.getAbsolutePath());
                                }
                            }
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        LOG.fine("无法访问文件: " + file + " - " + exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                });
                LOG.info("搜索完成，遍历结束");
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "遍历异常", e);
            } finally {
                running = false;
                if (completeCallback != null) completeCallback.run();
            }
        }, forkJoinPool);
    }

    /**
     * 在文件中搜索关键词，返回匹配的第一行内容
     */
    private String searchContent(File file, String keyword) {
        if (file.length() > 10 * 1024 * 1024) {
            LOG.finest("跳过超大文件: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            return null;
        }

        String lowerKeyword = keyword.toLowerCase();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.length() > 5000) continue;
                if (line.toLowerCase().contains(lowerKeyword)) {
                    String trimmed = line.trim();
                    int maxLen = 200;
                    if (trimmed.length() > maxLen) {
                        trimmed = trimmed.substring(0, maxLen) + "...";
                    }
                    return "第 " + lineNum + " 行: " + trimmed;
                }
            }
        } catch (Exception e) {
            LOG.finest("读取文件失败: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
        return null;
    }

    private boolean isLikelyTextFile(File file) {
        if (file.length() > 10 * 1024 * 1024) return false;
        if (file.length() == 0) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[(int) Math.min(file.length(), 512)];
            int read = fis.read(header);
            for (int i = 0; i < read; i++) {
                int b = header[i] & 0xFF;
                if (b == 0) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx).toLowerCase() : "";
    }

    public void cancel() {
        LOG.info("用户取消搜索");
        cancelled.set(true);
    }

    public boolean isRunning() {
        return running;
    }
}
