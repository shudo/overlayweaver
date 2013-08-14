@echo off

if "%OW_HOME%" == "" set OW_HOME=%~dp0..
set LIB_DIR=%OW_HOME%\lib
set TARGET_DIR=%OW_HOME%\target
set BUILD_DIR=%OW_HOME%\build

set CLASSPATH=%BUILD_DIR%;%TARGET_DIR%\overlayweaver.jar

set JVM_OPTION=
rem set JVM_OPTION=-server

java %JVM_OPTION% ow.tool.msgcounter.Main %*
