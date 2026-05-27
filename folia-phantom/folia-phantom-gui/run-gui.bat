@echo off
REM Folia Phantom GUI — pasta v1.0.0
REM JavaFX shaded JAR 起動スクリプト (Windows)

set "JAR=%~dp0target\folia-phantom-gui-1.0.0.jar"

if not exist "%JAR%" (
    echo [ERROR] JAR not found. Run "mvn clean package" first.
    pause
    exit /b 1
)

java ^
    --enable-native-access=ALL-UNNAMED ^
    --add-opens java.base/java.lang=ALL-UNNAMED ^
    --add-opens java.base/java.util=ALL-UNNAMED ^
    -Dorg.slf4j.simpleLogger.defaultLogLevel=info ^
    -Dorg.slf4j.simpleLogger.showDateTime=true ^
    -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss ^
    -jar "%JAR%"

pause
