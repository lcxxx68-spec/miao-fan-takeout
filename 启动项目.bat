@echo off
chcp 936 >nul
setlocal
cd /d "%~dp0"

echo.
echo   ==================================================
echo     秒饭 miao-fan-takeout 一键启动
echo   ==================================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo   [失败] 没找到 java, 请先安装 JDK 17+ 并把它加入 PATH
    goto :fail
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo   [失败] 没找到 mvn, 请先安装 Maven 3.6+ 并把它加入 PATH
    goto :fail
)
netstat -ano | findstr :3306 | findstr LISTENING >nul
if errorlevel 1 echo   [警告] 3306 没在监听: MySQL 没启动, 后端会启动失败
netstat -ano | findstr :6379 | findstr LISTENING >nul
if errorlevel 1 echo   [警告] 6379 没在监听: Redis 没启动, 抢购接口不可用
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :18080 ^| findstr LISTENING') do (
    echo   [失败] 端口 18080 已被进程 %%p 占用, 后端可能已经在运行
    echo          要重启请先运行 停止项目.bat
    goto :fail
)
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :18001 ^| findstr LISTENING') do (
    echo   [失败] 端口 18001 已被进程 %%p 占用, nginx 可能已经在运行
    echo          要重启请先运行 停止项目.bat
    goto :fail
)

echo   [1/3] 构建后端, 首次比较慢, 之后走增量...
pushd backend\miao-fan-takeout
call mvn -q -DskipTests package
if errorlevel 1 (
    echo   [失败] 构建失败, 请看上面的报错
    popd
    goto :fail
)
popd

echo   [2/3] 启动后端 18080...
start "秒饭-后端" "%~dp0启动后端.bat"
call :wait_http http://localhost:18080/doc.html 90
if errorlevel 1 (
    echo   [失败] 后端 90 秒内没有就绪, 请到 "秒饭-后端" 窗口看报错
    goto :fail
)

echo   [3/3] 启动 nginx 18001...
pushd nginx
start "" nginx.exe
popd
call :wait_http http://localhost:18001 30
if errorlevel 1 (
    echo   [失败] nginx 30 秒内没有就绪
    goto :fail
)

echo.
echo   --------------------------------------------------
echo    管理端地址: http://localhost:18001
echo    接口文档:   http://localhost:18080/doc.html
echo    默认账号:   admin / 123456
echo   --------------------------------------------------
echo.
echo   停止服务: 运行 停止项目.bat
echo.
start http://localhost:18001
pause
exit /b 0

rem 等待 HTTP 就绪: %1 地址, %2 超时秒数
:wait_http
powershell -NoProfile -Command "$u='%~1'; for($i=0;$i -lt %~2;$i++){ try{ $r=Invoke-WebRequest -UseBasicParsing -Uri $u -TimeoutSec 3; if($r.StatusCode -lt 500){ exit 0 } } catch {}; Start-Sleep -Seconds 1 }; exit 1"
exit /b %errorlevel%

:fail
echo.
echo   启动没有完成, 处理上面的提示后重来一次
pause
exit /b 1
