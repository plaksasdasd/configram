@echo off
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"
set "TELEGRAM_DIR=%ROOT%\Telegram"

if not exist "%TELEGRAM_DIR%\configure.bat" (
  echo ERROR: %TELEGRAM_DIR%\configure.bat not found.
  echo Run this script from the repo root.
  exit /b 1
)

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (
  echo ERROR: vswhere.exe not found. Install VS Build Tools 2022.
  exit /b 1
)

for /f "usebackq delims=" %%i in (`"%VSWHERE%" -products * -requires Microsoft.Component.MSBuild -property installationPath`) do (
  if not defined VS_INSTALL set "VS_INSTALL=%%i"
)
if not defined VS_INSTALL (
  echo ERROR: Visual Studio Build Tools not found.
  exit /b 1
)

set "VCVARS64=%VS_INSTALL%\VC\Auxiliary\Build\vcvars64.bat"
if exist "%VCVARS64%" (
  call "%VCVARS64%" >nul
) else (
  call "%VS_INSTALL%\Common7\Tools\VsDevCmd.bat" -arch=x64 >nul
)
set "GYP_MSVS_VERSION=2022"
set "GYP_MSVS_OVERRIDE_PATH=%VS_INSTALL%"

if /i not "%Platform%"=="x64" (
  echo ERROR: Platform is not x64. Current: %Platform%
  exit /b 1
)

where cl >nul 2>nul
if errorlevel 1 (
  echo ERROR: MSVC not found in PATH. Check Build Tools installation.
  exit /b 1
)

set "PYTHON=python"
where python >nul 2>nul
if errorlevel 1 (
  where py >nul 2>nul
  if errorlevel 1 (
    echo ERROR: Python not found in PATH.
    exit /b 1
  )
  set "PYTHON=py -3.10"
)

where git >nul 2>nul
if errorlevel 1 (
  echo ERROR: git not found in PATH.
  exit /b 1
)
where cmake >nul 2>nul
if errorlevel 1 (
  echo ERROR: cmake not found in PATH.
  exit /b 1
)

if "%TDESKTOP_API_ID%"=="" (
  set /p TDESKTOP_API_ID=Enter TDESKTOP_API_ID:
)
if "%TDESKTOP_API_HASH%"=="" (
  set /p TDESKTOP_API_HASH=Enter TDESKTOP_API_HASH:
)
if "%TDESKTOP_API_ID%"=="" (
  echo ERROR: TDESKTOP_API_ID is empty.
  exit /b 1
)
if "%TDESKTOP_API_HASH%"=="" (
  echo ERROR: TDESKTOP_API_HASH is empty.
  exit /b 1
)

set "CONFIG=Debug"
if /i "%1"=="Release" set "CONFIG=Release"
if /i "%1"=="Debug" set "CONFIG=Debug"

cd /d "%TELEGRAM_DIR%"

%PYTHON% build\prepare\prepare.py silent
if errorlevel 1 exit /b 1

call configure.bat x64 -D TDESKTOP_API_ID=%TDESKTOP_API_ID% -D TDESKTOP_API_HASH=%TDESKTOP_API_HASH%
if errorlevel 1 exit /b 1

cmake --build ..\out --config %CONFIG% --target Telegram
if errorlevel 1 exit /b 1

echo Done. Binary: %ROOT%\out\%CONFIG%\Telegram.exe
