@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "MVN_CMD=mvn.cmd") ELSE (SET "MVN_CMD=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%~dp0

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_PROPERTIES="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"

@SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

@FOR /F "usebackq tokens=1,2 delims==" %%A IN (%WRAPPER_PROPERTIES%) DO (
    @IF "%%A"=="wrapperUrl" SET DOWNLOAD_URL=%%B
)

@IF EXIST %WRAPPER_JAR% (
    @SET INIT_CALL=
) ELSE (
    @echo Downloading Maven Wrapper...
    @CALL :mvn_dl %DOWNLOAD_URL% %WRAPPER_JAR%
)

@SET JAVA_HOME_DIRS=C:\jdk1.8.0_291
@FOR %%J IN ("%JAVA_HOME_DIRS%") DO (
    @IF EXIST "%%~J\bin\java.exe" SET "JAVA_HOME=%%~J"
)

@IF NOT DEFINED JAVA_HOME (
    @FOR /F "tokens=2 delims==" %%I IN ('WMIC ENVIRONMENT WHERE "Name='JAVA_HOME'" GET VariableValue /VALUE 2^>nul') DO SET "JAVA_HOME=%%I"
)

@"%JAVA_HOME%\bin\java.exe" -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*
@GOTO :EOF

:mvn_dl
@powershell -Command "Invoke-WebRequest -Uri %~1 -OutFile %~2 -UseBasicParsing"
@GOTO :EOF
