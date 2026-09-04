@echo off
setlocal
echo ===================================================
echo SAFENEX Android Build (CLI Standalone)
echo ===================================================

if exist "C:\Program Files\Processing\app\resources\jdk\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Processing\app\resources\jdk"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=C:\Users\pream\AppData\Local\Android\Sdk"
set "GRADLE_OPTS=-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m"

echo Using Java: %JAVA_HOME%
echo Using Android SDK: %ANDROID_HOME%
echo.

call .\gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo BUILD SUCCESSFUL!
    echo APK Location:
    echo %cd%\app\build\outputs\apk\debug\app-debug.apk
    echo ===================================================
) else (
    echo.
    echo BUILD FAILED.
)
