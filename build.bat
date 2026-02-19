@echo off
echo Building CustomJukebox Plugin...
echo.

if not exist gradlew.bat (
    echo Gradle Wrapper nicht gefunden. Erstelle Wrapper...
    if exist "C:\Program Files\Gradle\gradle-8.5\bin\gradle.bat" (
        "C:\Program Files\Gradle\gradle-8.5\bin\gradle.bat" wrapper
    ) else if exist "%GRADLE_HOME%\bin\gradle.bat" (
        "%GRADLE_HOME%\bin\gradle.bat" wrapper
    ) else (
        echo FEHLER: Gradle ist nicht installiert!
        echo Bitte installieren Sie Gradle von https://gradle.org/install/
        echo Oder verwenden Sie einen Server mit installiertem Gradle.
        pause
        exit /b 1
    )
)

echo Building plugin...
call gradlew.bat shadowJar

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build erfolgreich!
    echo Plugin befindet sich unter: build\libs\CustomJukebox-1.0.0.jar
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Build fehlgeschlagen!
    echo ========================================
)

pause
