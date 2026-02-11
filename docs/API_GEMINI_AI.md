# API de Extração com Gemini AI

Este documento descreve a integração do **Google Gemini 1.5 Flash** para processamento de PDFs escaneados (baseados em imagem).

## Visão Geral

O Gemini AI é usado como **fallback** quando o PDFBox não consegue extrair texto suficiente de um PDF. Isso acontece quando o PDF é um documento escaneado (imagem).

### Fluxo de Processamento

```
┌─────────────────────────────────────────────────────────────────┐
│                        PDF UPLOAD                               │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PDFBox.extractText()                         │
│                 Extrai texto do PDF                             │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
            texto >= 100 chars      texto < 100 chars
                    │                       │
                    ▼                       ▼
        ┌───────────────────┐   ┌─────────────────────────────────┐
        │  Fluxo Normal     │   │     Gemini AI (Fallback)        │
        │  (PDFBox)         │   │  - Converte página para PNG     │
        └─────────┬─────────┘   │  - Envia para Gemini Vision     │
                  │             │  - Extrai texto da imagem       │
                  │             └───────────────┬─────────────────┘
                  │                             │
                  └──────────────┬──────────────┘
                                 │
                                 ▼
                  ┌─────────────────────────────────┐
                  │      Processamento Normal       │
                  │    (extração de rubricas, etc)  │
                  └─────────────────────────────────┘
```

## Configuração

### 1. Habilitar no application.yml

```yaml
gemini:
  enabled: true  # Altere para true
  project-id: ${GOOGLE_CLOUD_PROJECT}
  location: us-central1
  model: gemini-1.5-flash-002
  max-output-tokens: 8192
  temperature: 0.1
  timeout-seconds: 60
```

### 2. Configurar Credenciais do Google Cloud

#### Opção A: Usando Service Account (Recomendado para Produção)

1. Crie uma Service Account no [Google Cloud Console](https://console.cloud.google.com/iam-admin/serviceaccounts)
2. Adicione a role `Vertex AI User`
3. Baixe a chave JSON
4. Configure a variável de ambiente:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account.json"
export GOOGLE_CLOUD_PROJECT="seu-project-id"
```

#### Opção B: Usando gcloud CLI (Desenvolvimento Local) - Passo a Passo Detalhado

Este é o método recomendado para testar localmente na sua máquina.

##### Passo 1: Instalar o Google Cloud CLI

**Windows (PowerShell como Administrador):**
```powershell
# Baixar e executar o instalador
(New-Object Net.WebClient).DownloadFile("https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe", "$env:TEMP\GoogleCloudSDKInstaller.exe")
& $env:TEMP\GoogleCloudSDKInstaller.exe
```

Ou baixe manualmente: https://cloud.google.com/sdk/docs/install

Após instalação, **reinicie o terminal** para que o comando `gcloud` seja reconhecido.

##### Passo 2: Verificar instalação

```powershell
gcloud --version
```

Deve mostrar algo como:
```
Google Cloud SDK 458.0.0
bq 2.0.99
core 2024.01.05
gcloud-crc32c 1.0.0
gsutil 5.27
```

##### Passo 3: Inicializar e fazer login

```powershell
# Fazer login com sua conta Google
gcloud auth login
```

Isso abrirá o navegador para você fazer login com sua conta Google. Use a conta que tem acesso ao projeto no GCP.

##### Passo 4: Criar ou selecionar um projeto

**Se você JÁ TEM um projeto:**
```powershell
# Listar seus projetos
gcloud projects list

# Definir o projeto ativo
gcloud config set project SEU-PROJECT-ID
```

**Se você NÃO TEM um projeto, crie um:**
```powershell
# Criar novo projeto (nome único globalmente)
gcloud projects create pdfprocessor-gemini-dev --name="PDF Processor Dev"

# Definir como projeto ativo
gcloud config set project pdfprocessor-gemini-dev
```

##### Passo 5: Habilitar a API do Vertex AI

```powershell
gcloud services enable aiplatform.googleapis.com
```

Aguarde alguns segundos. Deve retornar:
```
Operation "operations/..." finished successfully.
```

##### Passo 6: Configurar Application Default Credentials (ADC)

Este é o passo mais importante para autenticação local:

```powershell
gcloud auth application-default login
```

Isso abrirá o navegador novamente. Faça login e autorize o acesso. 

O comando criará automaticamente um arquivo de credenciais em:
- Windows: `%APPDATA%\gcloud\application_default_credentials.json`
- Linux/Mac: `~/.config/gcloud/application_default_credentials.json`

##### Passo 7: Verificar configuração

```powershell
# Verificar projeto configurado
gcloud config get-value project

# Verificar conta autenticada
gcloud auth list
```

##### Passo 8: Configurar variável de ambiente do projeto

**PowerShell (sessão atual):**
```powershell
$env:GOOGLE_CLOUD_PROJECT = "SEU-PROJECT-ID"
```

**PowerShell (permanente - Perfil do usuário):**
```powershell
# Adicionar ao perfil do PowerShell
Add-Content $PROFILE "`n`$env:GOOGLE_CLOUD_PROJECT = 'SEU-PROJECT-ID'"
```

**Ou adicione no arquivo `.env` do projeto:**
```
GOOGLE_CLOUD_PROJECT=SEU-PROJECT-ID
```

##### Passo 9: Habilitar o Gemini no application.yml

Edite `src/main/resources/application.yml`:

```yaml
gemini:
  enabled: true  # <-- ALTERE PARA TRUE
  project-id: ${GOOGLE_CLOUD_PROJECT}
  location: us-central1
  model: gemini-1.5-flash-002
```

##### Passo 10: Testar a aplicação

```powershell
# Na pasta do projeto
.\gradlew.bat bootRun
```

Se tudo estiver configurado corretamente, você verá no log:
```
Inicializando cliente Gemini AI...
  - Project ID: SEU-PROJECT-ID
  - Location: us-central1
  - Model: gemini-1.5-flash-002
✅ Cliente Gemini AI inicializado com sucesso!
```

##### Passo 11: Testar com um PDF escaneado

Faça upload de um PDF que seja escaneado (imagem). Nos logs você verá:
```
🔍 Texto extraído muito pequeno (0 caracteres) na página 1. Tentando Gemini AI...
🤖 Usando Gemini AI para extrair texto da página 1...
✅ Gemini extraiu 2500 caracteres da página 1
```

---

##### Resumo dos comandos (Copie e cole):

```powershell
# 1. Login
gcloud auth login

# 2. Definir projeto
gcloud config set project SEU-PROJECT-ID

# 3. Habilitar API
gcloud services enable aiplatform.googleapis.com

# 4. Configurar credenciais locais
gcloud auth application-default login

# 5. Definir variável de ambiente
$env:GOOGLE_CLOUD_PROJECT = "SEU-PROJECT-ID"
```

---

### 3. Habilitar APIs no Google Cloud

```bash
gcloud services enable aiplatform.googleapis.com
```

## Estrutura de Arquivos

```
src/main/java/br/com/verticelabs/pdfprocessor/
├── domain/
│   └── service/
│       └── AiPdfExtractionService.java     # Interface
├── infrastructure/
│   ├── ai/
│   │   ├── GeminiPdfServiceImpl.java       # Implementação
│   │   └── GeminiPrompts.java              # Prompts otimizados
│   └── config/
│       └── GeminiConfig.java               # Configuração
└── application/
    └── documents/
        └── DocumentProcessUseCase.java     # Integração (fallback)
```

## Prompts Disponíveis

### 1. Extração de Texto Genérico
Extrai todo o texto visível de uma página escaneada.

### 2. Extração de Contracheque
Extrai dados estruturados (nome, CPF, rubricas) em formato JSON.

### 3. Extração de Declaração de IR
Extrai dados da página de resumo de declarações de imposto de renda.

### 4. Validação de Dados
Valida consistência dos dados extraídos (soma de proventos, etc).

## Estimativa de Custo

| Volume/mês | PDFs Escaneados | Tokens | Custo Gemini Flash |
|------------|-----------------|--------|-------------------|
| 100 PDFs   | 10 (10%)        | ~2.7M  | ~$0.20            |
| 1.000 PDFs | 100 (10%)       | ~27M   | ~$2.00            |
| 10.000 PDFs| 1.000 (10%)     | ~270M  | ~$20.00           |

## Logs

Quando o Gemini é usado, os seguintes logs são gerados:

```
🔍 Texto extraído muito pequeno (50 caracteres) na página 1. Tentando Gemini AI...
🤖 Usando Gemini AI para extrair texto da página 1...
✅ Gemini extraiu 2500 caracteres da página 1
```

Se o Gemini estiver desabilitado:

```
⚠️ Gemini AI desabilitado. Página 1 será ignorada.
```

## Troubleshooting

### Erro: "PERMISSION_DENIED"
- Verifique se a Service Account tem a role `Vertex AI User`
- Verifique se a API `aiplatform.googleapis.com` está habilitada

### Erro: "GOOGLE_APPLICATION_CREDENTIALS not set"
- Configure a variável de ambiente com o caminho do JSON da Service Account

### Erro: "Project not found"
- Verifique se a variável `GOOGLE_CLOUD_PROJECT` está configurada corretamente

## Limitações

1. **Latência**: Cada chamada ao Gemini adiciona ~2-5 segundos por página
2. **Custo**: Embora baixo, pode acumular com alto volume
3. **Rate Limits**: API tem limites de requisições por minuto
4. **Qualidade**: Depende da qualidade do PDF escaneado

## Segurança

- As credenciais do Google Cloud **nunca** devem ser commitadas no repositório
- Use variáveis de ambiente ou secrets manager
- Em Kubernetes, use o `k8s/secret.yaml` para armazenar credenciais
