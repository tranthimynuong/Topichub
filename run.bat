@echo off
setlocal enabledelayedexpansion
title Topic Management Web Server
chcp 65001 >nul

echo ===================================================
echo   TOPIC MANAGEMENT SYSTEM - KHOI DONG SERVER
echo ===================================================

:: 1. Kiem tra JAVA_HOME hien tai hop le
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        goto :FOUND_JAVA
    )
)

:: 2. Do tim cac thu muc JDK pho bien tren may
if exist "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
    goto :FOUND_JAVA
)

for /d %%D in ("C:\Program Files\Microsoft\jdk-*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :FOUND_JAVA
    )
)

for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :FOUND_JAVA
    )
)

for /d %%D in ("C:\Program Files\Java\jdk-*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :FOUND_JAVA
    )
)

for /d %%D in ("C:\Program Files\Amazon Corretto\jdk-*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :FOUND_JAVA
    )
)

for /d %%D in ("%USERPROFILE%\.jdks\*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVA_HOME=%%D"
        goto :FOUND_JAVA
    )
)

:FOUND_JAVA
if not defined JAVA_HOME (
    echo [LOI] Khong tim thay JDK hop le tren may cua ban!
    echo Vui long cai dat JDK 21 va thu lai.
    pause
    exit /b 1
)

echo [*] Su dung JAVA_HOME: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [*] Kiem tra va giai phong port 8080 neu co ung dung cu dang chay...
for /f "tokens=5" %%p in ('netstat -aon ^| findstr ":8080"') do (
    echo [*] Dang dong tien trinh cu PID %%p dang dung port 8080...
    taskkill /F /PID %%p >nul 2>&1
)

echo [*] Dang bien dich va dong goi du an...
call mvnw.cmd package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo [LOI] Bien dich du an that bai! Vui long kiem tra loi tren.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo [*] Dang khoi dong ung dung web Spring Boot...
echo [*] Truy cap sau khi khoi dong xong: http://localhost:8080
echo ===================================================
echo.

"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Dspring.thymeleaf.prefix=file:src/main/resources/templates/ -Dspring.web.resources.static-locations=file:src/main/resources/static/ -jar "target\topicmanagement-0.0.1-SNAPSHOT.jar"

pause
