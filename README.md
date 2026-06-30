# macS File Search Tool

A lightweight file search tool for macOS (also works on Windows), built with **Java + Swing**. No external dependencies required.

一款适用于 macOS（Windows 也兼容）的轻量级文件搜索工具，使用 **Java + Swing** 构建，无任何外部依赖。

---

## ✨ Features / 功能特性

- **File Name Search** — Search files by name keywords
- **File Content Search** — Search file contents (supports all text file types that Sublime Text can open)
- **Extension Filter** — Multi-select checkboxes grouped by categories (Java, XML/Config, Scripts, Frontend, Backend, Documents)
- **Multi-threaded** — Fast parallel traversal using ForkJoinPool
- **Progress Bar** — Real-time progress with percentage
- **Large Directory Warning** — Prompts confirmation when >20,000 files match
- **Right-click Menu** — Open file, Reveal in Finder, Copy full path, Copy file name
- **Built-in Log Panel** — Real-time application log viewer (collapsible)
- **macOS Native Style** — Clean UI matching macOS Big Sur design

---

- **文件名搜索** — 按文件名关键词搜索
- **文件内容搜索** — 搜索文件内容（支持所有 Sublime Text 可打开的文本文件类型）
- **后缀过滤** — 分组多选框（Java、XML/配置、脚本、前端、后端、文档/数据）
- **多线程搜索** — 使用 ForkJoinPool 快速并行遍历
- **进度条** — 实时百分比进度
- **大目录提醒** — 匹配文件超过 2 万时弹出确认
- **右键菜单** — 打开文件、在 Finder 中显示、复制完整路径、复制文件名
- **内置日志面板** — 实时查看应用日志（可折叠）
- **macOS 风格 UI** — 模仿 macOS Big Sur 的简洁设计

---

## 📋 Requirements / 环境要求

- **Java 8 (JRE) or higher** — [Download from Adoptium](https://adoptium.net/temurin/releases/?version=8)
- macOS 10.10+, Windows 7+, Linux (untested but should work)

---

## 🚀 How to Use / 使用方法

### Method 1: Download the Release (Recommended)

1. Download the latest release package from the [Releases page](https://github.com/wangliuliu-cc/macS/releases)
2. Unzip and run:

| Platform | How to run |
|----------|-----------|
| **macOS** | Double-click `macS.app` or `启动文件搜索.command` |
| **Windows** | Double-click `启动文件搜索.bat` |
| **Any** | Open terminal: `java -jar macS-file-search.jar` |

### 方法一：下载发布包（推荐）

1. 从 [Releases 页面](https://github.com/wangliuliu-cc/macS/releases) 下载最新版本
2. 解压后运行：

| 平台 | 运行方式 |
|------|---------|
| **macOS** | 双击 `macS.app` 或 `启动文件搜索.command` |
| **Windows** | 双击 `启动文件搜索.bat` |
| **任意** | 终端执行：`java -jar macS-file-search.jar` |

### Method 2: Build from Source / 方法二：源码构建

```bash
git clone git@github.com:wangliuliu-cc/macS.git
cd macS

# Build with Maven
mvn clean package -DskipTests

# Run
java -jar target/macS-file-search.jar
```

---

## 🖥️ How to Search / 搜索操作指南

1. **Select Directory** — Click "浏览…" or enter path directly
2. **File Name** — Check "按文件名" box, enter name keyword
3. **File Content** — Check "按内容" box, enter content keyword
4. **Select File Types** — Click "选择类型…" to choose extensions
5. Click **"搜索"** to start

---

1. **选择搜索目录** — 点击"浏览…"或直接输入路径
2. **文件名** — 勾选"按文件名"并输入文件名关键词
3. **文件内容** — 勾选"按内容"并输入内容关键词
4. **选择文件类型** — 点击"选择类型…"选择要搜索的后缀
5. 点击 **"搜索"** 开始搜索

### Default Extensions / 默认选中后缀

`.java`, `.xml`, `.properties`, `.yml`, `.yaml`, `.sh`, `.shell`

### Right-click Result / 右键结果

| Action | Description |
|--------|-------------|
| Open file | Opens with default application |
| Reveal in Finder | Locates file in Finder |
| Copy full path | Copies absolute path to clipboard |
| Copy file name | Copies file name only |

---

## 📁 Project Structure / 项目结构

```
src/main/java/com/zto/macs/
├── Main.java                     # Entry point / 入口
├── search/
│   ├── FileSearcher.java         # Core search engine / 核心搜索引擎
│   └── SearchResult.java         # Result model / 结果模型
└── ui/
    ├── MainFrame.java            # Main window / 主窗口
    └── LogPanel.java             # Log panel component / 日志面板组件
```

---

## 🔧 Tech Stack / 技术栈

- Java 8
- Swing (Java standard GUI library)
- ForkJoinPool (parallel file traversal)
- java.util.logging
- Maven 3.6.3

---

## 📄 License / 许可

MIT License
