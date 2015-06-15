@echo off

if "%OW_HOME%" == "" set OW_HOME=%~dp0..
set BIN_DIR=%OW_HOME%\bin
set LIB_DIR=%OW_HOME%\lib
set TARGET_DIR=%OW_HOME%\target
set BUILD_DIR=%OW_HOME%\build

set CLASSPATH=%BUILD_DIR%;%BUILD_DIR%\main;%BUILD_DIR%\test;%TARGET_DIR%\overlayweaver.jar;%LIB_DIR%\je-6.3.8.jar;%LIB_DIR%\xmlrpc-common-3.1.3.jar;%LIB_DIR%\xmlrpc-server-3.1.3.jar;%LIB_DIR%\ws-commons-util-1.0.2.jar;%LIB_DIR%\commons-cli-1.2.jar
set LOGGING_CONFIG="%BIN_DIR%\logging.properties"

set JVM_OPTION=-Xss100k
rem for 32 bit JVM
rem set JVM_OPTION=-server -Xss80k -Xmx1750m
rem for 64 bit JVM
rem set JVM_OPTION=-server -Xss100k -Xmx7500m -XX:NewRatio=12
rem set JVM_OPTION=-server -Xss100k -Xmx15200m -XX:NewRatio=12
rem set JVM_OPTION=-server -Xss100k -Xmx31200m -XX:NewRatio=12

java %JVM_OPTION% -Djava.util.logging.config.file=%LOGGING_CONFIG% ow.tool.emulator.Main %*
