@echo off
chcp 936 >nul
setlocal
cd /d "%~dp0"

echo.
echo   停止秒饭演示环境
echo   ----------------------------------------

set NGINX_RUNNING=0
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :18001 ^| findstr LISTENING') do set NGINX_RUNNING=1
if "%NGINX_RUNNING%"=="0" goto :nginx_done
echo   [1/2] 停止 nginx...
pushd nginx
nginx.exe -s stop
popd
ping 127.0.0.1 -n 3 >nul
:nginx_done
if "%NGINX_RUNNING%"=="0" echo   [1/2] nginx 没在运行

set BACKEND_RUNNING=0
rem netstat 会把同一个进程列两次(IPv4 与 IPv6), 这里按 PID 去重
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :18080 ^| findstr LISTENING') do (
    if not defined SEEN_%%a (
        set SEEN_%%a=1
        set BACKEND_RUNNING=1
        echo   [2/2] 停止后端进程 PID %%a ...
        taskkill /f /pid %%a >nul 2>&1
    )
)
if "%BACKEND_RUNNING%"=="0" echo   [2/2] 后端没在运行
ping 127.0.0.1 -n 2 >nul

netstat -ano | findstr :18001 | findstr LISTENING >nul
if errorlevel 1 (echo   端口 18001 已释放) else (echo   [警告] 18001 仍在监听, 可能不是本项目的 nginx)
netstat -ano | findstr :18080 | findstr LISTENING >nul
if errorlevel 1 (echo   端口 18080 已释放) else (echo   [警告] 18080 仍在监听)

echo.
echo   清理完成
echo.
pause
