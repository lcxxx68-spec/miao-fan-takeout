@echo off
chcp 936 >nul
setlocal
cd /d "%~dp0backend\miao-fan-takeout\mf-server"

echo.
echo   秒饭后端服务 / 端口 18080
echo   ----------------------------------------

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
for /f "tokens=5" %%p in ('netstat -ano ^| findstr :18080 ^| findstr LISTENING') do (
    echo   [失败] 端口 18080 已被进程 %%p 占用, 后端可能已经在运行
    echo          要重启请先运行 停止项目.bat
    goto :fail
)

if exist target\mf-server-1.0-SNAPSHOT.jar goto :run
echo   [1/2] 没找到可执行 jar, 先构建一次...
pushd "%~dp0backend\miao-fan-takeout"
call mvn -q -DskipTests package
if errorlevel 1 (
    echo   [失败] 构建失败, 请看上面的报错
    popd
    goto :fail
)
popd

:run
netstat -ano | findstr :3306 | findstr LISTENING >nul
if errorlevel 1 echo   [警告] 3306 没在监听, MySQL 可能没启动
netstat -ano | findstr :6379 | findstr LISTENING >nul
if errorlevel 1 echo   [警告] 6379 没在监听, Redis 可能没启动

echo   [2/2] 启动中, 首次约 20 秒; 出现 Started MiaoFanApplication 就是成功
echo         保持本窗口开着, 关掉它或用 停止项目.bat 都能停服务
echo.
java -jar target\mf-server-1.0-SNAPSHOT.jar
echo.
echo   后端进程已退出
pause
exit /b 0

:fail
echo.
pause
exit /b 1
