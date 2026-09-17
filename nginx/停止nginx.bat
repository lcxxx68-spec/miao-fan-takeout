@echo off
cd /d "%~dp0"

echo.
echo   正在停止 nginx ...
"%~dp0nginx.exe" -s stop

set /a n=0
:wait
%SystemRoot%\System32\netstat.exe -ano | %SystemRoot%\System32\findstr.exe /C:"LISTENING" | %SystemRoot%\System32\findstr.exe /C:":18001" >nul
if errorlevel 1 goto ok
set /a n+=1
if %n% geq 10 goto still
%SystemRoot%\System32\ping.exe -n 2 127.0.0.1 >nul
goto wait

:ok
echo   [成功] nginx 已停止
echo.
pause
exit /b

:still
echo   [警告] 18001 端口仍在监听，可能没停干净
echo   可以在任务管理器里手动结束 nginx.exe 进程
echo.
pause
