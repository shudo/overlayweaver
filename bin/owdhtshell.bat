@echo off

if "%OW_HOME%" == "" set OW_HOME=%~dp0..
set BIN_DIR=%OW_HOME%\bin
set LIB_DIR=%OW_HOME%\lib
set TARGET_DIR=%OW_HOME%\target
set BUILD_DIR=%OW_HOME%\build

set CLASSPATH=%BUILD_DIR%;%TARGET_DIR%\overlayweaver.jar;%LIB_DIR%\je-5.0.84.jar;%LIB_DIR%\xmlrpc-common-3.1.3.jar;%LIB_DIR%\xmlrpc-server-3.1.3.jar;%LIB_DIR%\ws-commons-util-1.0.2.jar;%LIB_DIR%\commons-cli-1.2.jar;%LIB_DIR%\servlet-api-3.0.jar;%LIB_DIR%\jetty-server-9.0.5.v20130815.jar;%LIB_DIR%\jetty-servlet-9.0.5.v20130815.jar;%LIB_DIR%\jetty-util-9.0.5.v20130815.jar;%LIB_DIR%\jetty-http-9.0.5.v20130815.jar;%LIB_DIR%\jetty-security-9.0.5.v20130815.jar;%LIB_DIR%\jetty-io-9.0.5.v20130815.jar;%LIB_DIR%\jetty-continuation-9.0.5.v20130815.jar;%LIB_DIR%\clink200.jar
set LOGGING_CONFIG="%BIN_DIR%\logging.properties"

set JVM_OPTION=-Xss80k
rem set JVM_OPTION=-server -Xss80k -Xmx250m

java %JVM_OPTION% -Djava.util.logging.config.file=%LOGGING_CONFIG% ow.tool.dhtshell.Main %*
