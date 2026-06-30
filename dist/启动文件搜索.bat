@echo off
chcp 65001 >nul 2>&1
title macS 文件搜索工具

REM 获取当前目录
set DIR=%~dp0
set JAR=%DIR%macS-file-search.jar

REM 检查 JAR 是否存在
if not exist "%JAR%" (
    echo 错误: 找不到 %JAR%
    echo 请确保该脚本与 macS-file-search.jar 放在同一目录
    pause
    exit /b 1
)

REM 检查 Java 环境
java -version >nul 2>&1
if %ERRORLEVEL% equ 0 (
    java -jar "%JAR%"
) else (
    echo 错误: 未找到 Java 运行环境
    echo.
    echo 请安装 JDK 或 JRE 8+:
    echo   https://www.oracle.com/java/technologies/javase-jre8-downloads.html
    echo   https://adoptium.net/temurin/releases/?version=8
    echo.
    pause
    exit /b 1
)
