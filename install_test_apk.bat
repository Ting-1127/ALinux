@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "MAIN_ACTIVITY=com.android.alinux/.MainActivity"

:: ── 解析命令行参数（支持 "123 release" 或 "release 123" 顺序任意）──
set "SELECTION="
set "BUILD_TYPE=debug"

for %%A in (%*) do (
  echo %%A | findstr /r "^[0-9][0-9]*$" >nul && set "SELECTION=%%A"
  if /i "%%A"=="debug"   set "BUILD_TYPE=debug"
  if /i "%%A"=="release" set "BUILD_TYPE=release"
)

:: ── 无参数时显示菜单 ──
if "%SELECTION%"=="" (
  echo =======================================
  echo   1 - Build APK
  echo   2 - Install APK
  echo   3 - Launch App
  echo ---------------------------------------
  echo Examples: 1  12  123  23  13
  echo =======================================
  set /p SELECTION=Choose operations:
  echo.
  echo   d - Debug ^(default^)
  echo   r - Release
  echo =======================================
  set /p BUILD_CHOICE=Build type [d/r, default=d]:
  if /i "!BUILD_CHOICE!"=="r" set "BUILD_TYPE=release"
)

:: ── 根据构建类型设置 Gradle task 和 APK 路径 ──
if /i "%BUILD_TYPE%"=="release" (
  set "GRADLE_TASK=assembleRelease"
  set "APK_PATH=%ROOT_DIR%app\build\outputs\apk\release\app-release.apk"
  set "TYPE_LABEL=Release"
) else (
  set "GRADLE_TASK=assembleDebug"
  set "APK_PATH=%ROOT_DIR%app\build\outputs\apk\debug\app-debug.apk"
  set "TYPE_LABEL=Debug"
)

set "RUN_BUILD=0"
set "RUN_INSTALL=0"
set "RUN_LAUNCH=0"

echo %SELECTION%| findstr /c:"1" >nul && set "RUN_BUILD=1"
echo %SELECTION%| findstr /c:"2" >nul && set "RUN_INSTALL=1"
echo %SELECTION%| findstr /c:"3" >nul && set "RUN_LAUNCH=1"

if "%RUN_BUILD%%RUN_INSTALL%%RUN_LAUNCH%"=="000" (
  echo Invalid selection: %SELECTION%
  exit /b 1
)

echo [Type] %TYPE_LABEL%

:: ── 1. 编译 ──
if "%RUN_BUILD%"=="1" (
  echo [Build] Building %TYPE_LABEL% APK...
  call "%ROOT_DIR%gradlew.bat" --no-daemon %GRADLE_TASK%
  if errorlevel 1 (
    echo Build failed.
    exit /b 1
  )
)

:: ── 2. 安装 ──
if "%RUN_INSTALL%"=="1" (
  if not exist "%APK_PATH%" (
    echo APK not found: %APK_PATH%
    echo Run option 1 first to build, or ensure the %TYPE_LABEL% APK already exists.
    exit /b 1
  )
  echo [Install] Installing %TYPE_LABEL% APK...
  adb install -r "%APK_PATH%"
  if errorlevel 1 (
    echo Install failed. Make sure adb is available and a device is connected.
    exit /b 1
  )
)

:: ── 3. 启动 ──
if "%RUN_LAUNCH%"=="1" (
  echo [Launch] Starting app...
  adb shell am start -n %MAIN_ACTIVITY%
  if errorlevel 1 (
    echo Launch failed. Make sure adb is available and a device is connected.
    exit /b 1
  )
)

echo Done.
endlocal
exit /b 0
