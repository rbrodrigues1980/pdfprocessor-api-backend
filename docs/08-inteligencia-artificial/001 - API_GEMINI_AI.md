# Integração com Google Gemini AI 2.5

> **Versão**: 2.0 — Atualizado para Gemini 2.5 Flash/Pro  
> **Data**: 12/02/2026  
> **SDK**: `google-cloud-vertexai:1.43.0` (migração para `google-genai` planejada antes de Jun/2026)

---

## Índice

1. [Visão Geral](#1-visão-geral)
2. [Arquitetura de Modelos](#2-arquitetura-de-modelos)
3. [Configuração no Google Cloud Platform (GCP)](#3-configuração-no-google-cloud-platform-gcp)
4. [Configuração no Projeto](#4-configuração-no-projeto)
5. [Habilitação da IA](#5-habilitação-da-ia)
6. [API de Configuração](#6-api-de-configuração)
7. [Fluxo de Processamento](#7-fluxo-de-processamento)
8. [Estrutura de Arquivos](#8-estrutura-de-arquivos)
9. [Prompts e Tipos de Extração](#9-prompts-e-tipos-de-extração)
10. [Estimativa de Custos](#10-estimativa-de-custos)
11. [Deploy no Cloud Run](#11-deploy-no-cloud-run)
12. [Troubleshooting](#12-troubleshooting)
13. [Limitações e Considerações](#13-limitações-e-considerações)
14. [Migração Futura do SDK](#14-migração-futura-do-sdk)

---

## 1. Visão Geral

O Gemini AI é usado como **camada de extração inteligente** para processar PDFs que não podem ser lidos por extração nativa (iText 8 / PDFBox). Isso inclui:

- **PDFs escaneados** (baseados em imagem, sem texto embutido)
- **PDFs com layout complexo** (tabelas sobrepostas, colunas irregulares)
- **PDFs degradados** (baixa resolução, ruído, distorções)

### Fluxo Geral

```
┌─────────────────────────────────────────────────────────────────┐
│                        PDF Upload                                │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│           CAMADA 1: Extração Nativa (iText 8 / PDFBox)           │
│           Custo: ZERO  |  Precisão: ~99-100%                     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
            texto >= 100 chars      texto < 100 chars
            (PDF digital)           (PDF escaneado)
                    │                       │
                    ▼                       ▼
        ┌───────────────────┐   ┌─────────────────────────────────┐
        │  Processamento    │   │    CAMADA 2: Gemini AI 2.5       │
        │  normal           │   │    Flash (principal)              │
        │  (regex/parsing)  │   │    Pro (fallback)                 │
        └───────────────────┘   │    Custo: ~$0.003/pg              │
                                │    Precisão: ~93-95%              │
                                └─────────────────────────────────┘
```

---

## 2. Arquitetura de Modelos

O sistema utiliza **dois modelos Gemini** em uma estratégia de fallback:

| Modelo | ID na API | Uso | Custo/página | Precisão |
|--------|-----------|-----|-------------|----------|
| **Principal** | `gemini-2.5-flash` | Todas as extrações iniciais | ~$0.003 | ~93-95% |
| **Fallback** | `gemini-2.5-pro` | Quando Flash falha ou confiança é baixa | ~$0.011 | ~95-96% |

### Quando o modelo fallback é usado?

O modelo Pro é utilizado automaticamente nas seguintes situações:

1. O modelo Flash retorna resposta vazia ou inválida
2. A validação por regras (Fase 2) detecta inconsistências graves
3. A dupla extração (Fase 3) encontra divergências críticas

### Especificações dos Modelos

**Gemini 2.5 Flash:**
- Context window: 1.048.576 tokens (1M)
- Max output: 65.535 tokens
- Inputs: texto, código, imagens, áudio, vídeo
- Output: texto
- Pricing: $0.30/M input tokens + $2.50/M output tokens

**Gemini 2.5 Pro:**
- Context window: 1.048.576 tokens (1M)
- Max output: 65.535 tokens
- Inputs: texto, código, imagens, áudio, vídeo
- Output: texto
- Pricing: $1.25/M input tokens + $10.00/M output tokens

---

## 3. Configuração no Google Cloud Platform (GCP)

### 3.1. Pré-requisitos

- Conta no Google Cloud (crie em: https://cloud.google.com/free)
- Cartão de crédito vinculado (Google oferece $300 de crédito gratuito para novos usuários)
- Google Cloud CLI instalado na máquina

### 3.2. Configuração via Console Web (Método Visual)

Se você prefere configurar pelo navegador em vez do terminal:

#### Passo 1: Acessar o Console

1. Acesse https://console.cloud.google.com
2. Faça login com sua conta Google
3. No topo da página, verifique se o projeto correto está selecionado (ex: `RRR-Software-Solutions`)

#### Passo 2: Habilitar a Vertex AI API

1. No painel inicial, clique em **"APIs e serviços"** (no "Acesso rápido")
2. Clique em **"+ ATIVAR APIS E SERVIÇOS"** (botão no topo)
3. Na barra de pesquisa, digite: **`Vertex AI API`**
4. Clique no resultado **"Vertex AI API"** (por Google Enterprise API)
5. Clique no botão **"ATIVAR"**
6. Aguarde — quando aparecer o Status **"Ativadas"**, está pronto

> Se já estiver ativada, aparecerá "GERENCIAR" em vez de "ATIVAR".

#### Passo 3: Verificar faturamento

1. No menu lateral, clique em **"Faturamento"**
2. Confirme que existe uma conta de faturamento vinculada ao projeto
3. Se não houver, clique em "Vincular uma conta de faturamento"

#### Passo 4: Obter o ID do projeto

No Console, o ID do projeto aparece no painel inicial:
- **Nome do projeto**: (ex: RRR-Software-Solutions)
- **ID do projeto**: (ex: `rrr-software-solutions`) — este é o valor para `GOOGLE_CLOUD_PROJECT`
- **Número do projeto**: (ex: 177627167012)

> Anote o **ID do projeto** — você vai usar no `.env` da aplicação.

#### Passo 5: Configurar credenciais locais (Terminal)

Esta etapa precisa ser feita no terminal (PowerShell), pois cria o arquivo de credenciais na sua máquina:

```powershell
gcloud auth application-default login
```

1. O navegador abrirá a tela: **"O app Google Auth Library quer acessar sua Conta do Google"**
2. Marque **"Selecionar tudo"** nas permissões
3. Clique em **"Continuar"**
4. O terminal confirmará: `Credentials saved to file: [C:\Users\...\application_default_credentials.json]`

> A aplicação Java detecta este arquivo automaticamente. Não é necessário configurar `GOOGLE_APPLICATION_CREDENTIALS` para desenvolvimento local.

---

### 3.3. Configuração via Terminal (Método CLI Completo)

Se você prefere fazer tudo pelo terminal:

### 3.4. Instalar o Google Cloud CLI

#### Windows (PowerShell como Administrador)

```powershell
# Opção 1: Baixar e executar o instalador
(New-Object Net.WebClient).DownloadFile(
    "https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe",
    "$env:TEMP\GoogleCloudSDKInstaller.exe"
)
& $env:TEMP\GoogleCloudSDKInstaller.exe
```

Ou baixe manualmente: https://cloud.google.com/sdk/docs/install

**Após a instalação, reinicie o terminal.**

#### Linux/macOS

```bash
curl https://sdk.cloud.google.com | bash
exec -l $SHELL   # Reiniciar shell
```

#### Verificar instalação

```powershell
gcloud --version
```

Resultado esperado:
```
Google Cloud SDK 465.0.0
bq 2.1.3
core 2026.01.15
gcloud-crc32c 1.0.0
gsutil 5.29
```

### 3.3. Autenticar e Configurar Projeto

#### Passo 1: Login na conta Google

```powershell
gcloud auth login
```

Isso abrirá o navegador. Faça login com a conta que tem acesso ao projeto GCP.

#### Passo 2: Criar ou selecionar projeto

**Se já tem um projeto:**
```powershell
# Listar projetos existentes
gcloud projects list

# Selecionar projeto
gcloud config set project SEU-PROJECT-ID
```

**Se NÃO tem um projeto:**
```powershell
# Criar projeto (nome deve ser único globalmente)
gcloud projects create pdfprocessor-prod --name="PDF Processor"

# Selecionar como ativo
gcloud config set project pdfprocessor-prod
```

#### Passo 3: Vincular conta de faturamento (obrigatório)

```powershell
# Listar contas de faturamento
gcloud billing accounts list

# Vincular projeto à conta de faturamento
gcloud billing projects link pdfprocessor-prod --billing-account=BILLING-ACCOUNT-ID
```

Ou faça pelo Console: https://console.cloud.google.com/billing

#### Passo 4: Habilitar a API do Vertex AI

```powershell
gcloud services enable aiplatform.googleapis.com
```

Aguarde a conclusão:
```
Operation "operations/..." finished successfully.
```

### 3.5. Configurar Autenticação

Existem **duas opções** para autenticar a aplicação com o Google Cloud:

#### Opção A: Application Default Credentials — ADC (Recomendado para Desenvolvimento Local)

```powershell
gcloud auth application-default login
```

Isso abrirá o navegador novamente. Faça login e autorize o acesso.

O comando cria automaticamente um arquivo de credenciais em:
- **Windows**: `%APPDATA%\gcloud\application_default_credentials.json`
- **Linux/Mac**: `~/.config/gcloud/application_default_credentials.json`

A aplicação Java detecta este arquivo automaticamente. Não é necessário configurar `GOOGLE_APPLICATION_CREDENTIALS`.

#### Opção B: Service Account (Recomendado para Produção / Cloud Run)

1. **Crie uma Service Account:**

```powershell
gcloud iam service-accounts create gemini-pdf-processor \
    --display-name="PDF Processor Gemini AI"
```

2. **Adicione a role Vertex AI User:**

```powershell
gcloud projects add-iam-policy-binding SEU-PROJECT-ID \
    --member="serviceAccount:gemini-pdf-processor@SEU-PROJECT-ID.iam.gserviceaccount.com" \
    --role="roles/aiplatform.user"
```

3. **Gere a chave JSON (apenas para uso local):**

```powershell
gcloud iam service-accounts keys create ./credentials.json \
    --iam-account=gemini-pdf-processor@SEU-PROJECT-ID.iam.gserviceaccount.com
```

4. **Configure a variável de ambiente:**

```powershell
# PowerShell (sessão atual)
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\caminho\completo\credentials.json"

# Ou adicione ao arquivo .env do projeto
# GOOGLE_APPLICATION_CREDENTIALS=C:\caminho\completo\credentials.json
```

> **IMPORTANTE**: Nunca commite o arquivo `credentials.json` no repositório git!

### 3.5. Verificar Configuração

```powershell
# Verificar projeto configurado
gcloud config get-value project

# Verificar conta autenticada
gcloud auth list

# Verificar APIs habilitadas
gcloud services list --enabled --filter="aiplatform"

# Testar acesso ao Vertex AI
gcloud ai models list --region=us-central1 --limit=3
```

### 3.7. Resumo Rápido dos Comandos (CLI)

```powershell
# === CONFIGURAÇÃO COMPLETA VIA CLI (copie e cole) ===

# 1. Login
gcloud auth login

# 2. Selecionar/criar projeto
gcloud config set project SEU-PROJECT-ID

# 3. Habilitar API do Vertex AI
gcloud services enable aiplatform.googleapis.com

# 4. Configurar credenciais locais (desenvolvimento)
gcloud auth application-default login

# 5. Definir variável de ambiente do projeto
$env:GOOGLE_CLOUD_PROJECT = "SEU-PROJECT-ID"

# 6. Verificar tudo
gcloud config get-value project
gcloud services list --enabled --filter="aiplatform"
```

---

## 4. Configuração no Projeto

### 4.1. application.yml

```yaml
# Gemini AI Configuration (para PDFs escaneados e extração inteligente)
gemini:
  enabled: false                                    # Habilitar quando credenciais estiverem configuradas
  project-id: ${GOOGLE_CLOUD_PROJECT:}              # ID do projeto GCP (env var)
  location: ${GEMINI_LOCATION:us-central1}          # Região do Vertex AI
  model: ${GEMINI_MODEL:gemini-2.5-flash}           # Modelo principal (Flash)
  fallback-model: ${GEMINI_FALLBACK_MODEL:gemini-2.5-pro}  # Modelo fallback (Pro)
  max-output-tokens: 8192                           # Tokens máximos na resposta
  temperature: 0.1                                  # Baixo = determinístico
  timeout-seconds: 120                              # Timeout por chamada
```

### 4.2. Variáveis de Ambiente

| Variável | Obrigatória | Descrição | Default |
|----------|-------------|-----------|---------|
| `GOOGLE_CLOUD_PROJECT` | Sim | ID do projeto no GCP | — |
| `GOOGLE_APPLICATION_CREDENTIALS` | Apenas prod | Caminho para chave JSON da Service Account | ADC automático |
| `GEMINI_MODEL` | Não | Modelo principal | `gemini-2.5-flash` |
| `GEMINI_FALLBACK_MODEL` | Não | Modelo fallback | `gemini-2.5-pro` |
| `GEMINI_LOCATION` | Não | Região do Vertex AI | `us-central1` |

### 4.3. Arquivo .env (Desenvolvimento Local)

```env
# Google Cloud / Gemini AI
GOOGLE_CLOUD_PROJECT=seu-project-id
GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/credentials.json

# Modelos (opcional — defaults já configurados)
# GEMINI_MODEL=gemini-2.5-flash
# GEMINI_FALLBACK_MODEL=gemini-2.5-pro
# GEMINI_LOCATION=us-central1
```

### 4.4. Executar Localmente (com .env)

O Spring Boot **não carrega** o arquivo `.env` automaticamente quando executado via Gradle.
É necessário carregar as variáveis antes de rodar.

#### PowerShell (Windows):

```powershell
# Carregar variáveis do .env e executar a aplicação:
Get-Content .env | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
}
.\gradlew.bat bootRun
```

#### Bash (Linux/macOS):

```bash
# Carregar variáveis do .env e executar a aplicação:
export $(grep -v '^#' .env | xargs)
./gradlew bootRun
```

#### Via IDE (IntelliJ/Cursor):

Se usar a IDE para executar, configure as variáveis de ambiente na Run Configuration:
- `SPRING_DATA_MONGODB_URI` = sua URI do MongoDB Atlas
- `JWT_SECRET` = sua chave JWT
- `GOOGLE_CLOUD_PROJECT` = seu project ID do GCP

#### Verificação de Sucesso

Nos logs, procure por:
```
Inicializando clientes Gemini AI...
  Project ID: rrr-software-solutions
  Location: us-central1
  Modelo principal: gemini-2.5-flash
  Modelo fallback: gemini-2.5-pro
  ...
Cliente Gemini AI inicializado com sucesso - modelos: [gemini-2.5-flash] e [gemini-2.5-pro]
```

Se aparecer `Project ID não configurado`, a variável `GOOGLE_CLOUD_PROJECT` não foi carregada.

---

## 5. Habilitação da IA

A IA é controlada em **duas camadas**:

### 5.1. Camada Estática (application.yml)

A flag `gemini.enabled` controla se o cliente Gemini é **inicializado** na startup da aplicação.

```yaml
gemini:
  enabled: true  # Altere para true quando as credenciais estiverem prontas
```

### 5.2. Camada Dinâmica (API REST / MongoDB)

Mesmo com `gemini.enabled: true`, o uso efetivo da IA depende da configuração dinâmica no MongoDB (collection `system_config`, chave `ai.enabled`).

Isso permite habilitar/desabilitar a IA **sem restart** da aplicação, via API ou frontend.

### 5.3. Ambas as camadas devem estar ativas

Para a IA funcionar:

```
gemini.enabled (application.yml) = true
   +
ai.enabled (MongoDB/API) = true
   +
GOOGLE_CLOUD_PROJECT configurado
   +
Credenciais GCP válidas
   =
IA ATIVA ✅
```

---

## 6. API de Configuração

### 6.1. Obter Configuração Atual

```http
GET /api/v1/config/ai
Authorization: Bearer {token}
```

**Resposta:**
```json
{
  "enabled": true,
  "model": "gemini-2.5-flash",
  "fallbackModel": "gemini-2.5-pro",
  "credentialsConfigured": true,
  "projectId": "meu-projeto-gcp",
  "location": "us-central1",
  "updatedAt": "2026-02-12T10:30:00Z",
  "updatedBy": "admin@exemplo.com",
  "statusMessage": "IA habilitada e pronta. Modelo principal: gemini-2.5-flash, Fallback: gemini-2.5-pro."
}
```

### 6.2. Atualizar Configuração

```http
PUT /api/v1/config/ai
Authorization: Bearer {token}
Content-Type: application/json

{
  "enabled": true,
  "model": "gemini-2.5-flash",
  "fallbackModel": "gemini-2.5-pro"
}
```

> **Requer role**: `SUPER_ADMIN`
>
> **Importante**: O path correto é `/api/v1/config/ai` (com prefixo `/api/v1`). Chamadas para `/config/ai` retornam `404 NOT_FOUND`.

#### Exemplo completo com PowerShell (desenvolvimento local):

```powershell
# 1. Login como SUPER_ADMIN
$loginBody = '{"email":"superadmin@teste.com","password":"SuperAdmin123!"}'
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" `
    -Method POST -ContentType "application/json" -Body $loginBody
$token = $response.accessToken

# 2. Habilitar IA
$headers = @{Authorization="Bearer $token"}
$body = '{"enabled":true,"model":"gemini-2.5-flash","fallbackModel":"gemini-2.5-pro"}'
$result = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/config/ai" `
    -Method PUT -ContentType "application/json" -Headers $headers -Body $body
$result | ConvertTo-Json
```

### 6.3. Possíveis Status

| Status | Significado |
|--------|-------------|
| `IA desabilitada. PDFs escaneados não serão processados pela IA.` | `ai.enabled = false` |
| `IA habilitada, mas credenciais do Google Cloud não configuradas.` | Falta `GOOGLE_CLOUD_PROJECT` |
| `IA habilitada e pronta. Modelo principal: ...` | Tudo configurado |

---

## 7. Fluxo de Processamento

### 7.1. Contracheques (CAIXA / FUNCEF)

```
Upload PDF
    │
    ▼
PDFBox.extractText()
    │
    ├── texto >= 100 chars → Processamento normal (regex por linha)
    │
    └── texto < 100 chars → Gemini AI
                                │
                                ├── convertPdfPageToImage(300 DPI)
                                │
                                ├── Gemini Flash + prompt CONTRACHEQUE_EXTRACTION
                                │
                                └── Retorna JSON com nome, CPF, rubricas
```

### 7.2. Declaração de IR (IRPF)

```
Upload PDF
    │
    ▼
iText 8 (LocationTextExtractionStrategy)
    │
    ├── Encontra página RESUMO
    │
    ├── Extrai texto da página 1 e RESUMO
    │
    └── Parse via 37+ regex patterns
         │
         └── Se falha → Gemini AI + prompt IR_RESUMO_EXTRACTION
```

### 7.3. PDF Escaneado (Genérico)

```
Upload PDF
    │
    ▼
PDFBox.extractText()
    │
    └── texto < 100 chars → Gemini AI
                                │
                                ├── convertPdfPageToImage(300 DPI)
                                │
                                ├── Gemini Flash + prompt EXTRACAO_TEXTO_GENERICO
                                │
                                └── Retorna texto puro
```

---

## 8. Estrutura de Arquivos

```
src/main/java/br/com/verticelabs/pdfprocessor/
│
├── domain/
│   ├── model/
│   │   └── SystemConfig.java              # Modelo para config dinâmica (MongoDB)
│   ├── repository/
│   │   └── SystemConfigRepository.java    # Repositório reativo
│   └── service/
│       └── AiPdfExtractionService.java    # Interface do serviço de IA
│
├── infrastructure/
│   ├── ai/
│   │   ├── GeminiPdfServiceImpl.java      # Implementação (Flash + Pro)
│   │   └── GeminiPrompts.java             # Prompts otimizados por tipo de doc
│   └── config/
│       └── GeminiConfig.java              # Configuração (model, fallback, etc.)
│
└── interfaces/
    └── rest/
        ├── AiConfigController.java        # API REST para config de IA
        └── dto/
            ├── AiConfigRequest.java       # Request DTO
            └── AiConfigResponse.java      # Response DTO
```

---

## 9. Prompts e Tipos de Extração

### 9.1. Prompts Disponíveis

| Prompt | Constante | Tipo de Doc | Retorno |
|--------|-----------|-------------|---------|
| Extração de Contracheque | `CONTRACHEQUE_EXTRACTION` | CAIXA, FUNCEF | JSON (nome, CPF, rubricas) |
| Extração de Contracheque (Alt) | `CONTRACHEQUE_EXTRACTION_ALT` | CAIXA, FUNCEF | JSON (cross-validation) |
| Resumo de IR | `IR_RESUMO_EXTRACTION` | IRPF | JSON (37+ campos fiscais) |
| Resumo de IR (Alt) | `IR_RESUMO_EXTRACTION_ALT` | IRPF | JSON (cross-validation) |
| Validação de Contracheque | `VALIDACAO_CONTRACHEQUE` | CAIXA, FUNCEF | JSON (inconsistências) |
| Extração Genérica | `EXTRACAO_TEXTO_GENERICO` | Qualquer | Texto puro |

### 9.2. Técnicas de Prompt Engineering Aplicadas

| Técnica | Descrição | Exemplo |
|---------|-----------|---------|
| **Precisão > Completude** | Preferir null a adivinhar | "Se não estiver legível, retorne null" |
| **Auto-verificação** | Modelo confere antes de retornar | "VERIFIQUE: soma proventos = bruto" |
| **Formato explícito** | Especificar formato monetário | "Use PONTO como decimal (1234.56)" |
| **Contexto brasileiro** | Formatos BR específicos | "CPF no formato 000.000.000-00" |
| **JSON estrito** | Sem markdown na resposta | "Retorne APENAS JSON, sem markdown" |
| **Prompts alternativos** | Abordagem diferente para cross-check | Top-down vs bottom-up |

---

## 10. Estimativa de Custos

### 10.1. Custo por Página

| Modelo | Input tokens/pg | Output tokens/pg | Custo/página |
|--------|----------------|-------------------|-------------|
| Flash | ~2.500 | ~750 | **~$0.003** |
| Pro | ~2.500 | ~750 | **~$0.011** |

### 10.2. Cenários Mensais

> Nota: apenas PDFs processados pela IA têm custo. PDFs digitais (iText/PDFBox) são grátis.

| Volume total | PDFs escaneados (~20%) | Flash | Pro (fallback ~5%) | **Total/mês** |
|-------------|----------------------|-------|-------------------|--------------|
| 100 pgs | 20 pgs | $0.06 | $0.01 | **~$0.07** |
| 500 pgs | 100 pgs | $0.30 | $0.05 | **~$0.35** |
| 1.000 pgs | 200 pgs | $0.60 | $0.11 | **~$0.71** |
| 5.000 pgs | 1.000 pgs | $3.00 | $0.55 | **~$3.55** |
| 50.000 pgs | 10.000 pgs | $30.00 | $5.50 | **~$35.50** |

### 10.3. Custos Adicionais do GCP

| Serviço | Custo |
|---------|-------|
| Vertex AI API | Conforme tabela acima |
| Cloud Run | Depende do uso (ver docs/DEPLOY_GOOGLE_CLOUD_RUN.md) |
| Network Egress | Incluído no custo normal do Cloud Run |

---

## 11. Deploy no Cloud Run

### 11.1. Variáveis de Ambiente no Cloud Run

No Console do Cloud Run ou via CLI, configure:

```powershell
gcloud run services update pdfprocessor-api \
    --region=us-central1 \
    --set-env-vars="GOOGLE_CLOUD_PROJECT=seu-project-id" \
    --set-env-vars="GEMINI_MODEL=gemini-2.5-flash" \
    --set-env-vars="GEMINI_FALLBACK_MODEL=gemini-2.5-pro" \
    --set-env-vars="GEMINI_LOCATION=us-central1"
```

### 11.2. Autenticação no Cloud Run

No Cloud Run, a autenticação é automática via **Workload Identity**. A Service Account do Cloud Run precisa ter a role `Vertex AI User`:

```powershell
# Obter Service Account do Cloud Run
gcloud run services describe pdfprocessor-api \
    --region=us-central1 \
    --format="value(spec.template.spec.serviceAccountName)"

# Adicionar role (se necessário)
gcloud projects add-iam-policy-binding SEU-PROJECT-ID \
    --member="serviceAccount:SERVICE-ACCOUNT@SEU-PROJECT-ID.iam.gserviceaccount.com" \
    --role="roles/aiplatform.user"
```

> **NÃO é necessário** configurar `GOOGLE_APPLICATION_CREDENTIALS` no Cloud Run. A autenticação é automática.

### 11.3. Habilitar IA via API após Deploy

Após o deploy, habilite a IA via API:

```bash
# 1. Obter token de autenticação
TOKEN=$(curl -s -X POST https://SUA-URL/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"senha"}' | jq -r '.token')

# 2. Habilitar IA
curl -X PUT https://SUA-URL/api/v1/config/ai \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"enabled": true, "model": "gemini-2.5-flash", "fallbackModel": "gemini-2.5-pro"}'
```

---

## 12. Troubleshooting

### 12.1. Erros Comuns

| Erro | Causa | Solução |
|------|-------|---------|
| `Project ID não configurado` | Falta `GOOGLE_CLOUD_PROJECT` | Configure a variável de ambiente |
| `PERMISSION_DENIED` | Service Account sem permissão | Adicione role `Vertex AI User` |
| `aiplatform.googleapis.com has not been enabled` | API não habilitada | Execute `gcloud services enable aiplatform.googleapis.com` |
| `GOOGLE_APPLICATION_CREDENTIALS not set` | Credenciais não configuradas | Use ADC (`gcloud auth application-default login`) ou configure a variável |
| `Model not found` | Modelo não disponível na região | Verifique se o modelo existe na região configurada |
| `Deadline exceeded` | Timeout | Aumente `gemini.timeout-seconds` no application.yml |
| `RESOURCE_EXHAUSTED` | Rate limit atingido | Reduza volume ou solicite aumento de quota |

### 12.2. Logs de Diagnóstico

**Inicialização bem-sucedida:**
```
Inicializando clientes Gemini AI...
  Project ID: meu-projeto
  Location: us-central1
  Modelo principal: gemini-2.5-flash
  Modelo fallback: gemini-2.5-pro
  Max output tokens: 8192
  Temperature: 0.1
  Timeout: 120s
Cliente Gemini AI inicializado com sucesso - modelos: [gemini-2.5-flash] e [gemini-2.5-pro]
```

**Processamento de PDF:**
```
Processando página 1 com Gemini [gemini-2.5-flash]...
Gemini [gemini-2.5-flash] processou página 1 em 3200ms (2847 chars na resposta)
```

**IA desabilitada:**
```
Gemini AI desabilitado. Retornando vazio para página 1.
```

### 12.3. Testar Localmente

```powershell
# 1. Garantir que as credenciais estão configuradas
$env:GOOGLE_CLOUD_PROJECT = "seu-project-id"
gcloud auth application-default login

# 2. Iniciar a aplicação
.\gradlew.bat bootRun

# 3. Carregar variáveis do .env e iniciar a aplicação
Get-Content .env | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
}
.\gradlew.bat bootRun

# 4. Habilitar IA via API (precisa estar logado como SUPER_ADMIN)
# POST /api/v1/auth/login → obter accessToken
# PUT /api/v1/config/ai → {"enabled": true, "model": "gemini-2.5-flash", "fallbackModel": "gemini-2.5-pro"}
# (Veja seção 6.2 para exemplo completo com PowerShell)

# 5. Fazer upload de um PDF escaneado e verificar os logs
```

---

## 13. Limitações e Considerações

### 13.1. Limitações Técnicas

| Limitação | Impacto | Mitigação |
|-----------|---------|-----------|
| Latência por chamada (~2-5s/página) | Processamento mais lento que extração nativa | Apenas PDFs escaneados passam pela IA |
| VLMs podem "alucinar" dados | Dados inventados que não existem no PDF | Validação por regras (Fase 2), dupla extração (Fase 3) |
| Rate limits da API | ~60 req/min por default | Processamento assíncrono via WebFlux |
| Custo por volume | Acumula com alto volume | Otimização: só usa IA quando necessário |
| Depende de internet | Não funciona offline | iText 8/PDFBox continuam funcionando offline |

### 13.2. Segurança

- Credenciais do Google Cloud **nunca** devem ser commitadas no repositório
- Use variáveis de ambiente ou Secret Manager
- O arquivo `credentials.json` está no `.gitignore`
- Em produção (Cloud Run), use Workload Identity — não precisa de arquivo JSON
- A API de configuração requer autenticação JWT + role `SUPER_ADMIN`

---

## 14. Migração Futura do SDK

### Situação Atual

O SDK `com.google.cloud:google-cloud-vertexai` (pacote `com.google.cloud.vertexai.generativeai`) está **deprecado** e será removido após **24 de Junho de 2026**.

### SDK Substituto

```kotlin
// build.gradle.kts — migração futura
// DE:
implementation("com.google.cloud:google-cloud-vertexai:1.43.0")
// PARA:
implementation("com.google.genai:google-genai:1.38.0")
```

### O que muda no código

| Antes (SDK atual) | Depois (SDK novo) |
|-------------------|------------------|
| `new VertexAI(projectId, location)` | `Client.builder().vertexAI(true).project(projectId).location(location).build()` |
| `new GenerativeModel(model, vertexAI)` | `client.models.generateContent(model, content, config)` |
| `ContentMaker.fromMultiModalData(...)` | `Content.fromParts(Part.fromText(...), Part.fromBytes(...))` |
| `ResponseHandler.getText(response)` | `response.text()` |

### Quando migrar

- **Prazo final**: antes de 24/06/2026
- **Recomendação**: migrar quando iniciar a Fase 2 do plano de upgrade
- **Impacto**: apenas `GeminiPdfServiceImpl.java` precisa ser alterado
- **Guia oficial**: https://cloud.google.com/vertex-ai/generative-ai/docs/deprecations/genai-vertexai-sdk

---

## Referências

- [Gemini 2.5 Flash — Vertex AI Docs](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash)
- [Gemini 2.5 Pro — Vertex AI Docs](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro)
- [Vertex AI Pricing](https://cloud.google.com/vertex-ai/generative-ai/pricing)
- [SDK Migration Guide](https://cloud.google.com/vertex-ai/generative-ai/docs/deprecations/genai-vertexai-sdk)
- [Google Cloud CLI Install](https://cloud.google.com/sdk/docs/install)
- [Plano de Upgrade Completo](./PLANO_UPGRADE_GEMINI_AI.md)
