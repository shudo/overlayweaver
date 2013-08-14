@echo off

if "%OW_HOME%" == "" set OW_HOME=%~dp0..
set BIN_DIR=%OW_HOME%\bin
set LIB_DIR=%OW_HOME%\lib
set TARGET_DIR=%OW_HOME%\target
set BUILD_DIR=%OW_HOME%\build

set CLASSPATH="%BUILD_DIR%;%TARGET_DIR%\overlayweaver.jar;%LIB_DIR%\je-5.0.84.jar;%LIB_DIR%\commons-cli-1.2.jar;%LIB_DIR%\clink200.jar"
set LOGGING_CONFIG="%BIN_DIR%\logging.properties"

set JVM_OPTION=-Xss80k
rem set JVM_OPTION=-server -Xss80k -Xmx1750m

java %JVM_OPTION% -Djava.util.logging.config.file=%LOGGING_CONFIG% ow.tool.memcached.Main %*
