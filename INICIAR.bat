@echo off
chcp 65001 >nul
title PDF Processor API - Iniciando

REM Ir para a pasta onde este .bat esta (evita abrir e fechar se rodar por atalho)
cd /d "%~dp0"

echo.
echo ========================================
echo   PDF Processor API - Iniciando
echo ========================================
echo.
echo   Pasta: %CD%
echo.

REM Verifica se o Docker esta rodando
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Docker nao esta rodando ou nao esta instalado.
    echo        Instale o Docker Desktop e inicie-o antes de rodar este script.
    echo        https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
)

REM Verifica se existe .env
if not exist ".env" (
    echo [AVISO] Arquivo .env nao encontrado.
    echo        Para usar as mesmas credenciais que quem sobe localmente:
    echo        copie o .env da raiz do projeto ^(da maquina do dev^) para esta pasta.
    if exist ".env.example" (
        echo.
        echo        Ou copie .env.example para .env e preencha MONGODB_URI e JWT_SECRET.
        copy ".env.example" ".env"
        echo        .env criado a partir do exemplo. Edite e configure antes de rodar de novo.
        pause
        exit /b 1
    ) else (
        echo        Crie um arquivo .env com MONGODB_URI e JWT_SECRET.
        pause
        exit /b 1
    )
)

echo Subindo API (usa o MongoDB ja configurado no .env)...
echo.
docker-compose up -d

if errorlevel 1 (
    echo.
    echo [ERRO] Falha ao subir os containers.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Aplicacao iniciada com sucesso!
echo ========================================
echo.
echo   API:        http://localhost:8081
echo   Swagger:    http://localhost:8081/swagger-ui.html
echo.
echo   Para parar: execute PARAR.bat ou feche esta janela.
echo   Os containers continuarao rodando em segundo plano.
echo.
pause
