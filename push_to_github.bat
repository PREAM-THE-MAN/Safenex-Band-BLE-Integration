@echo off
title Push Safenex to GitHub
echo ===================================================
echo ?? Pushing Safenex-Band-BLE-Integration to GitHub...
echo Repository: https://github.com/PREAM-THE-MAN/Safenex-Band-BLE-Integration.git
echo ===================================================
set "PATH=%LOCALAPPDATA%\Programs\Git\cmd;%PATH%"
git push -u origin main
echo ===================================================
pause
