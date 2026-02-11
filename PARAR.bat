@echo off
chcp 65001 >nul
title PDF Processor API - Parando

echo.
echo ========================================
echo   PDF Processor API - Parando
echo ========================================
echo.

docker-compose down

echo.
echo Aplicacao foi parada.
echo.
pause
