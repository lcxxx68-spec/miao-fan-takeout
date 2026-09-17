@echo off
cd /d "%~dp0"

echo.
echo   正在启动 nginx ...
start "" "%~dp0nginx.exe"

set /a n=0
:wait
%SystemRoot%\System32\netstat.exe -ano | %SystemRoot%\System32\findstr.exe /C:"LISTENING" | %SystemRoot%\System32\findstr.exe /C:":18001" >nul
if not errorlevel 1 goto ok
set /a n+=1
if %n% geq 10 goto fail
%SystemRoot%\System32\ping.exe -n 2 127.0.0.1 >nul
goto wait

:ok
echo   [成功] nginx 已在 18001 端口监听
echo.
echo   管理端地址   http://localhost:18001
echo   默认账号     admin / 123456
echo.
echo   注意   弹出的那个黑色 nginx 窗口不要关闭，关上 nginx 就停了
echo   停止   请运行同目录下的  停止nginx.bat
echo.
pause
exit /b

:fail
echo   [失败] nginx 没有成功监听 18001 端口
echo.
echo   可能原因   端口被占用，或配置文件有误
echo   详细错误   请查看本目录下的 logs\error.log
echo.
pause
