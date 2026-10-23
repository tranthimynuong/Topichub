param(
    [switch]$Fast
)

# Script khoi dong Spring Boot Server
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  TOPIC MANAGEMENT SYSTEM - KHOI DONG SERVER" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# 1. Kiem tra JAVA_HOME hien tai
$javaHome = $env:JAVA_HOME
if ($javaHome -and (Test-Path "$javaHome\bin\javac.exe")) {
    Write-Host "[*] Su dung JAVA_HOME: $javaHome" -ForegroundColor Green
} else {
    # 2. Do tim cac thu muc JDK pho bien tren may
    $candidatePaths = @(
        "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot",
        "C:\Program Files\Microsoft\jdk-*",
        "C:\Program Files\Eclipse Adoptium\jdk-*",
        "C:\Program Files\Java\jdk-*",
        "C:\Program Files\Amazon Corretto\jdk-*",
        "$env:USERPROFILE\.jdks\*"
    )
    
    $detected = $null
    foreach ($pattern in $candidatePaths) {
        $matches = Get-Item $pattern -ErrorAction SilentlyContinue | Where-Object { Test-Path "$($_.FullName)\bin\javac.exe" }
        if ($matches) {
            $detected = ($matches | Select-Object -First 1).FullName
            break
        }
    }
    
    if ($detected) {
        $env:JAVA_HOME = $detected
        $javaHome = $detected
        Write-Host "[*] Da tu dong do tim JDK: $detected" -ForegroundColor Green
    } else {
        Write-Host "[!] Khong tim thay JDK tren may. Vui long cai dat JDK 21!" -ForegroundColor Red
        exit 1
    }
}

$env:PATH = "$javaHome\bin;$env:PATH"

# Giai phong port 8080 neu co tien trinh cu dang chay
$oldPort = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($oldPort) {
    Write-Host "[*] Dang dong tien trinh cu PID $($oldPort.OwningProcess) dang chiem port 8080..." -ForegroundColor Yellow
    Stop-Process -Id $oldPort.OwningProcess -Force -ErrorAction SilentlyContinue
}

if ($Fast) {
    Write-Host "[*] Che do Fast: Bien dich va dong goi nhanh..." -ForegroundColor Cyan
    cmd.exe /c "mvnw.cmd package -DskipTests -Dmaven.test.skip=true"
} else {
    Write-Host "[*] Dang bien dich va dong goi du an..." -ForegroundColor Yellow
    cmd.exe /c "mvnw.cmd package -DskipTests"
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Bien dich that bai!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host "[*] Dang khoi dong ung dung web Spring Boot..." -ForegroundColor Green
Write-Host "[*] Truy cap sau khi khoi dong xong: http://localhost:8080" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Green
Write-Host ""

& "$javaHome\bin\java.exe" "-Dfile.encoding=UTF-8" -jar "target\topicmanagement-0.0.1-SNAPSHOT.jar"
