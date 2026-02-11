# PDF Processor API Backend

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-brightgreen)
![Reactive](https://img.shields.io/badge/Architecture-Reactive-blue)
![WebFlux](https://img.shields.io/badge/Stack-WebFlux-purple)


Bem-vindo ao projeto **PDF Processor API Backend**! Este projeto foi refatorado para seguir os princípios da **Clean Architecture**, oferecendo uma estrutura robusta, escalável e testável para processamento de documentos e autenticação segura.

## 🚀 Tecnologias Utilizadas

Este projeto utiliza uma stack moderna para alta performance e segurança:

*   **Core**:
    *   **Java 21**: Versão LTS mais recente.
    *   **Kotlin**: Usado em conjunto com Java.
    *   **Spring Boot 3.3.x**: Framework principal.
    *   **Spring WebFlux**: Arquitetura reativa não bloqueante.
*   **Banco de Dados**:
    *   **MongoDB Atlas**: Banco de dados NoSQL na nuvem.
    *   **Spring Data MongoDB Reactive**: Conexão reativa.
*   **Arquitetura**:
    *   **Clean Architecture**: Divisão em camadas (Domain, Application, Infrastructure, Interfaces).
*   **Processamento de Arquivos**:
    *   **Apache PDFBox** e **Apache Tika**: Extração de texto e metadados de PDFs.
    *   **Apache POI**: Geração de planilhas Excel (.xlsx).
*   **Segurança**:
    *   **Spring Security WebFlux**: Proteção da API.
    *   **JWT (JSON Web Token)**: Autenticação stateless com Access e Refresh Tokens.
    *   **Argon2**: Hashing de senhas seguro (via Bouncy Castle).
    *   **Multi-tenancy**: Sistema completo de isolamento de dados por tenant (empresa).
*   **Ferramentas**:
    *   **Gradle (Kotlin DSL)**: Build system.
    *   **Lombok**: Redução de boilerplate.
    *   **SpringDoc OpenAPI**: Documentação Swagger automática.
    *   **Logback**: Sistema de logs profissional com rotação automática.

---

## 📂 Estrutura do Projeto (Clean Architecture)

O código está organizado para separar responsabilidades e isolar o domínio:

*   `src/main/java/br/com/verticelabs/pdfprocessor`:
    *   **domain**: O coração do projeto. Contém as Entidades (`User`, `Document`, `Rubrica`) e interfaces de Repositórios/Serviços. **Não depende de frameworks**.
    *   **application**: Casos de uso da aplicação (`AuthUseCase`, `DocumentUseCase`). Orquestra a lógica de negócios.
    *   **infrastructure**: Implementações concretas. Configurações (`SecurityConfig`), adaptadores de banco (`MongoRepository`), serviços de terceiros (`PdfService`, `ExcelService`).
    *   **interfaces**: Camada de entrada. Controladores REST (`AuthController`, `DocumentController`) e DTOs.

---

## ⚙️ Configuração

### 1. Banco de Dados (MongoDB)
O projeto conecta ao MongoDB Atlas. A URI de conexão está em `src/main/resources/application.yml`.

### 2. Segurança (JWT)
As chaves de segurança devem vir de **variáveis de ambiente** (nunca fixas no código em produção). O `application.yml` já está preparado para usar placeholders:

```yaml
server:
  port: ${PORT:8081}

spring:
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:900000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:2592000000}
```

**Em produção, dados sensíveis (JWT, credenciais) devem ficar no Secret Manager** (por exemplo, no [Google Cloud Secret Manager](https://cloud.google.com/run/docs/configuring/secrets) ao usar Cloud Run).

---

## 🚀 Instalação rápida para teste (notebook / outro PC)

Para alguém que só quer **subir e parar** a aplicação sem instalar Java:

1. **Instalar Docker Desktop** (Windows): https://www.docker.com/products/docker-desktop/
2. Copiar a pasta do projeto para o PC e colocar na pasta o **mesmo `.env`** que você usa localmente (assim a API no Docker usa as mesmas credenciais de MongoDB e JWT que seus testes locais).
3. Na pasta do projeto:
   - **Subir:** dar dois cliques em **`INICIAR.bat`** (sobe só a API em container).
   - **Parar:** dar dois cliques em **`PARAR.bat`**.

Se não tiver o `.env`, copie `.env.example` para `.env` e preencha **MONGODB_URI** e **JWT_SECRET** (ou peça o `.env` a quem sobe localmente). A API fica em **http://localhost:8081** e o Swagger em **http://localhost:8081/swagger-ui.html**.

👉 Guia passo a passo: **[docs/INSTALACAO_RAPIDA.md](./docs/INSTALACAO_RAPIDA.md)**

---

## ▶️ Como Rodar a Aplicação (desenvolvimento)

### Pré-requisitos
*   Java JDK 21 instalado.

### Executando via Terminal
O projeto usa o **Gradle Wrapper**, não é necessário instalar o Gradle manualmente.

1.  **Windows**:
    ```powershell
    .\gradlew.bat bootRun
    ```
2.  **Linux/Mac**:
    ```bash
    ./gradlew bootRun
    ```

Por padrão a aplicação inicia na porta **8081**. Você pode sobrescrever com a variável de ambiente `PORT`.

---

## 📚 Documentação

### Documentação Completa das APIs e Arquitetura

👉 **[📖 Documentação Completa das APIs e Clean Architecture](./docs/API_COMPLETA_E_ARQUITETURA.md)**

Esta documentação inclui:
- ✅ **Todas as APIs** do projeto com exemplos detalhados
- ✅ **Guia completo da Clean Architecture** explicando cada camada
- ✅ **Comparação MVC vs Clean Architecture**
- ✅ **Mapeamento de componentes** (onde está cada coisa)
- ✅ **Fluxos de dados** e exemplos práticos

### Documentações Específicas para Frontend

- 👉 **[🔐 API de Autenticação](./docs/API_AUTH_FRONTEND.md)** - Guia completo de autenticação JWT, 2FA, refresh tokens
- 👉 **[📄 API de Documentos](./docs/API_DOCUMENTS_FRONTEND.md)** - Upload, processamento e gerenciamento de documentos PDF com isolamento multi-tenant
- 👉 **[🏢 API de Tenants](./docs/API_TENANTS_FRONTEND.md)** - Gerenciamento de tenants (empresas) com exemplos em React, Vue e Angular
- 👉 **[🏷️ API de Rubricas](./docs/API_RUBRICAS_FRONTEND.md)** - Gerenciamento de rubricas (tabela mestra) com isolamento multi-tenant
- 👉 **[👥 API de Pessoas](./docs/API_PERSONS_FRONTEND.md)** - Gerenciamento de pessoas com CRUD completo
- 👉 **[👥 API de Gestão de Pessoas - Implementação](./docs/API_PERSONS_CRUD_IMPLEMENTATION.md)** - Documentação técnica da implementação do CRUD de pessoas
- 👉 **[👤 API de Usuários](./docs/API_USERS_FRONTEND.md)** - Gerenciamento de usuários com roles e permissões
- 👉 **[📊 API de Consolidação](./docs/API_CONSOLIDATION_FRONTEND.md)** - Consolidação de dados e geração de relatórios
- 👉 **[💰 API de Imposto de Renda](./docs/API_INCOME_TAX_FRONTEND.md)** - Extração e processamento de declarações IRPF
- 👉 **[📈 API Taxa Selic](./docs/API_TAXA_SELIC.md)** - Consulta e gerenciamento de taxas Selic
- 👉 **[🤖 API Gemini AI](./docs/API_GEMINI_AI.md)** - Processamento de PDFs escaneados com IA
- 👉 **[⚙️ API Configuração de IA](./docs/API_AI_CONFIG_FRONTEND.md)** - Habilitar/desabilitar IA via frontend

### Guias Explicativos

- 👉 **[📋 Organização das APIs: Auth, Usuários e Tenants](./docs/ORGANIZACAO_APIS_AUTH_USUARIOS_TENANTS.md)** - Entenda a estrutura e separação das APIs de autenticação, criação de usuários e gerenciamento de tenants
- 👉 **[📋 Planejamento: Gerenciamento Completo de Usuários](./docs/PLANEJAMENTO_GERENCIAMENTO_USUARIOS.md)** - Planejamento detalhado para implementação de CRUD completo de usuários com permissões por role

### Documentação Interativa (Swagger)

Acesse a interface interativa para testar os endpoints:

👉 **[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)**

### Principais Endpoints:

*   **Autenticação**:
    *   `POST /api/v1/auth/login`: Login (retorna Access e Refresh Token).
*   **Pessoas**:
    *   `POST /api/v1/persons`: Criar pessoa.
    *   `GET /api/v1/persons`: Listar pessoas (com paginação e filtros).
    *   `GET /api/v1/persons/{id}`: Buscar pessoa por ID.
    *   `PUT /api/v1/persons/{id}`: Atualizar pessoa.
    *   `DELETE /api/v1/persons/{id}`: Excluir pessoa definitivamente.
    *   `PATCH /api/v1/persons/{id}/activate`: Ativar pessoa.
    *   `PATCH /api/v1/persons/{id}/deactivate`: Desativar pessoa.
*   **Documentos**:
    *   `POST /api/v1/documents/upload`: Upload de PDF (Multipart).
    *   `POST /api/v1/documents/bulk-upload`: Upload múltiplo de PDFs.
    *   `GET /api/v1/documents`: Listar documentos processados.

---

## 📝 Sistema de Logs

O projeto possui um sistema de logs configurado com **Logback**:

*   **Localização**: Os logs são salvos em `logs/fulllog.log`
*   **Formato**: Mesmo formato do console (ISO 8601 com timezone)
*   **Rotação Automática**: 
    *   Cada arquivo tem no máximo **10MB**
    *   Mantém até **5 arquivos** históricos
    *   Quando o 6º arquivo é criado, o mais antigo é removido automaticamente
*   **Estrutura dos arquivos**:
    *   `fulllog.log` (arquivo atual)
    *   `fulllog-YYYY-MM-DD.0.log` (arquivos históricos)

Os logs continuam sendo exibidos no console e também são salvos no arquivo simultaneamente.

**Em produção (ex.: Cloud Run)** o ambiente é stateless e não há disco persistente. Use **apenas stdout/stderr**: a plataforma coleta os logs automaticamente. Com o perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`), o Logback envia logs só para o console; arquivos em `logs/` não são usados e seriam perdidos em reinícios ou scale-to-zero.

## 🏢 Multi-tenancy

O sistema possui suporte completo a **multi-tenancy**, permitindo isolamento total de dados por empresa (tenant):

*   **Isolamento de dados**: Cada tenant possui seus próprios documentos, pessoas e entradas
*   **Roles de usuário**:
    *   `SUPER_ADMIN`: Acesso global, pode gerenciar todos os tenants
    *   `TENANT_ADMIN`: Administrador de uma empresa específica
    *   `TENANT_USER`: Usuário comum de uma empresa
*   **Contexto de tenant**: Resolvido automaticamente via JWT ou header `X-Tenant-ID` (apenas para SUPER_ADMIN)
*   **Índices únicos por tenant**: CPF e hash de arquivo são únicos apenas dentro do mesmo tenant

## 📋 Extração de Declarações de Imposto de Renda (iText 8)

O projeto inclui um serviço especializado para extração de informações de **Declarações de Imposto de Renda (IRPF)** usando a biblioteca **iText 8**.

### Visão Geral

Este serviço substitui a abordagem anterior baseada em regex por uma extração estruturada usando APIs avançadas do iText 8, oferecendo melhor suporte a:
- PDFs com layout de duas colunas (labels e valores separados)
- Formatos variáveis entre anos (2016 vs 2017+)
- Extração posicional complexa

> ⚠️ **Licença iText 8**: O iText 8 usa licença AGPL. Se a aplicação for distribuída comercialmente sem disponibilizar o código-fonte, será necessária uma licença comercial.

### Endpoints Disponíveis

| Endpoint | Método | Descrição |
|----------|--------|-----------|
| `/api/v1/incometax/extract` | POST | Extrai informações de um PDF de declaração de IR |
| `/api/v1/incometax/extract/raw` | POST | Retorna apenas o texto bruto extraído |
| `/api/v1/incometax/extract/page/{pageNumber}` | POST | Retorna texto de uma página específica |
| `/api/v1/incometax/extract/debug` | POST | Retorna texto bruto + informações extraídas (debug) |
| `/api/v1/incometax/find-resumo` | POST | Localiza a página RESUMO no PDF |
| `/api/v1/incometax/upload` | POST | Upload + persistência da declaração (com CPF) |
| `/api/v1/incometax/upload/person/{personId}` | POST | Upload + persistência (com ID da pessoa) |

### Arquitetura

```
IncomeTaxController
        │
        ▼
ITextIncomeTaxService (Interface)
        │
        ▼
ITextIncomeTaxServiceImpl
        │
        ├── iText 8 PdfReader / PdfDocument
        ├── PdfTextExtractor
        ├── LocationTextExtractionStrategy
        └── SimpleTextExtractionStrategy
        │
        ▼
IncomeTaxInfo DTO
```

### Campos Extraídos

O serviço extrai automaticamente os seguintes campos:

- **Dados Básicos**: Nome, CPF, Exercício, Ano-Calendário
- **Seção IMPOSTO DEVIDO**: Base de cálculo, Imposto devido, Deduções de incentivo, etc.
- **Seção DEDUÇÕES**: Contribuição previdência, Despesas médicas, Instrução, Dependentes, etc.
- **Seção IMPOSTO PAGO**: Imposto retido na fonte, Carnê-Leão, Imposto complementar, etc.
- **Seção RESULTADO**: Saldo a pagar, Imposto a restituir

### Exemplo de Uso

```bash
# Extrair informações de um PDF
curl -X POST "http://localhost:8081/api/v1/incometax/extract" \
  -F "file=@declaracao_ir_2023.pdf"

# Upload com persistência
curl -X POST "http://localhost:8081/api/v1/incometax/upload" \
  -F "file=@declaracao_ir_2023.pdf" \
  -F "cpf=12345678900"
```

### Documentação Completa

👉 **[📋 API de Imposto de Renda (iText 8)](./docs/API_INCOMETAX_ITEXT8.md)** - Documentação detalhada com todos os campos e exemplos

---

## 🐛 Solução de Problemas

*   **Erro de Build (Lombok)**: Se tiver problemas com o Lombok, tente rodar `.\gradlew.bat clean build`. O projeto usa uma versão específica do Lombok configurada no Gradle.
*   **Erro de Conexão Mongo**: Verifique se sua rede permite conexão com o MongoDB Atlas (algumas redes corporativas bloqueiam).
*   **Porta já em uso**: Se a porta 8081 estiver ocupada, altere em `src/main/resources/application.yml` na propriedade `server.port`.

---

## 🐳 Docker

O projeto está containerizado para facilitar deploy em qualquer ambiente.

### Arquivos Docker

| Arquivo | Descrição |
|---------|-----------|
| `Dockerfile` | Build multi-stage otimizado (Java 21 Alpine) |
| `docker-compose.yml` | Orquestração com opção de MongoDB local |
| `.dockerignore` | Otimiza build ignorando arquivos desnecessários |
| `.env.example` | Template de variáveis de ambiente |

### Características do Dockerfile

- ✅ **Multi-stage build** - Imagem final ~200MB (vs ~1GB sem otimização)
- ✅ **Java 21 JRE Alpine** - Imagem leve baseada em Alpine Linux
- ✅ **Usuário não-root** - Segurança aprimorada
- ✅ **Health check integrado** - Monitoramento automático
- ✅ **JVM otimizada** - Configurações para containers

### Como Usar Docker

#### Build e execução simples (MongoDB Atlas):

```bash
# Copiar e configurar variáveis
cp .env.example .env
# Editar .env com suas credenciais MongoDB e JWT

# Build e run
docker-compose up --build
```

#### Com MongoDB local (desenvolvimento):

```bash
docker-compose --profile with-mongodb up --build
```

#### Build manual da imagem:

```bash
# Build
docker build -t pdfprocessor-api:latest .

# Run
docker run -d \
  -p 8081:8081 \
  -e SPRING_DATA_MONGODB_URI="sua-uri-mongodb" \
  -e JWT_SECRET="sua-chave-secreta" \
  --name pdfprocessor \
  pdfprocessor-api:latest
```

---

## ☁️ Deploy no Google Cloud Run

A API pode ser publicada no **Google Cloud Run** usando **Cloud Build** (build da imagem), **Artifact Registry** (armazenamento da imagem) e **Cloud Run** (execução). Esse é o fluxo recomendado pela Google para aplicações containerizadas.

### Pré-requisitos

- **Projeto no Google Cloud** com [Billing](https://console.cloud.google.com/billing) habilitado (há free tier).
- **gcloud CLI** instalado e autenticado:
  ```bash
  gcloud auth login
  gcloud config set project SEU_PROJECT_ID
  ```
- **APIs habilitadas**: Cloud Run, Cloud Build, Artifact Registry (comando abaixo).

### Variáveis de ambiente (produção)

O Cloud Run injeta a variável **`PORT`** no container; a aplicação já está configurada com `server.port=${PORT:8081}`. É essencial **escutar na porta injetada** (o valor pode variar).

| Variável | Obrigatória | Descrição |
|----------|-------------|-----------|
| `PORT` | (injetada) | Cloud Run injeta automaticamente (valor pode variar). Local: default 8081. |
| `SPRING_PROFILES_ACTIVE` | Sim | Use `prod` em produção. |
| `SPRING_DATA_MONGODB_URI` | Sim | URI do MongoDB Atlas. |
| `JWT_SECRET` | Sim | Chave do JWT. Em produção use [Secret Manager](https://cloud.google.com/run/docs/configuring/secrets). |
| `JWT_EXPIRATION` | Não | Default `900000` (15 min), em ms. |
| `JWT_REFRESH_EXPIRATION` | Não | Default `2592000000` (30 dias), em ms. |

### Passo a passo (CLI)

#### 1. Definir região e variáveis

```bash
export REGION=southamerica-east1   # São Paulo
export REPO=pdfprocessor-repo
export SERVICE=pdfprocessor-api
export PROJECT_ID=$(gcloud config get-value project)
export IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/pdfprocessor-api:1.0.0
```

#### 2. Habilitar APIs

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

#### 3. Criar repositório Docker no Artifact Registry

```bash
gcloud artifacts repositories create $REPO \
  --repository-format=docker \
  --location=$REGION \
  --description="Imagens do PDF Processor API"
```

#### 4. Build e push da imagem (Cloud Build)

Na **raiz do projeto** (onde está o `Dockerfile`):

```bash
gcloud builds submit --tag $IMAGE .
```

#### 5. Deploy no Cloud Run

```bash
gcloud run deploy $SERVICE \
  --image $IMAGE \
  --region $REGION \
  --allow-unauthenticated \
  --memory 2Gi \
  --cpu 1 \
  --concurrency 10 \
  --timeout 900
```

- **`--allow-unauthenticated`**: serviço público; o acesso é controlado por JWT na aplicação.
- **`--memory 2Gi`**: PDFBox/Tika/POI usam bastante RAM; ajuste conforme necessidade.
- **`--timeout 900`**: até 15 min por requisição (Cloud Run permite até **60 min**; altere se precisar).
- **Custo**: use `--max-instances N` para limitar instâncias e evitar surpresas.
- **Cold start**: use `--min-instances 1` para manter uma instância sempre quente (aumenta custo).

#### 6. Configurar variáveis de ambiente

Use **`--update-env-vars`** (faz *merge* com as existentes). O comando **`--set-env-vars`** é destrutivo e remove todas as env vars que não estiverem na lista.

```bash
gcloud run services update $SERVICE --region $REGION \
  --update-env-vars "SPRING_PROFILES_ACTIVE=prod" \
  --update-env-vars "SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@cluster.mongodb.net/dbname" \
  --update-env-vars "JWT_SECRET=sua-chave-secreta"
```

Substitua os valores pelos reais. **Para dados sensíveis (JWT, credenciais), use Secret Manager em vez de env var plana** — veja o passo opcional abaixo.

##### Usar JWT no Secret Manager (recomendado em produção)

```bash
# 1) Criar o secret (uma vez)
printf "sua-chave-super-secreta" | gcloud secrets create jwt-secret --data-file=-

# 2) Dar permissão ao Cloud Run de acessar o secret (conta de serviço do serviço)
#    No Console: IAM & Admin → garantir "Secret Manager Secret Accessor" para a default compute SA
#    Ou via gcloud (ajuste PROJECT_NUMBER e REGION conforme seu projeto):
#    gcloud secrets add-iam-policy-binding jwt-secret \
#      --member="serviceAccount:PROJECT_NUMBER-compute@developer.gserviceaccount.com" \
#      --role="roles/secretmanager.secretAccessor"

# 3) Atualizar o serviço para usar o secret como variável de ambiente
gcloud run services update $SERVICE --region $REGION \
  --update-secrets "JWT_SECRET=jwt-secret:latest"
```

#### 7. URL e logs

```bash
# URL do serviço
gcloud run services describe $SERVICE --region $REGION --format="value(status.url)"

# Últimos logs
gcloud logs read --region $REGION --limit 100
```

### Fluxo resumido

```
Dockerfile → gcloud builds submit → Artifact Registry → gcloud run deploy → Cloud Run
```

### Healthcheck (Actuator)

Em produção, o Cloud Run (e outras plataformas) usam health checks para saber se o container está pronto. **Garanta que `/actuator/health` e `/actuator/info` estejam liberados sem autenticação** no `SecurityConfig` — se os checks estiverem configurados na plataforma e o health estiver bloqueado, o deploy pode falhar ou a revisão não ficar “ready”. O Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) também permanece público, se quiser documentação acessível.

### Upload de PDFs grandes (limite 32 MiB)

O Cloud Run tem [limite de tamanho de requisição HTTP/1](https://cloud.google.com/run/quotas) de **32 MiB**. Para arquivos maiores:

1. **Upload direto para Cloud Storage** (por exemplo, via URL assinada gerada pela sua aplicação).
2. A API recebe apenas o **caminho/identificador** do arquivo (bucket + object) e processa de forma síncrona ou assíncrona.

Assim você evita estourar o limite da requisição e melhora custo e desempenho.

### MongoDB Atlas (IP allowlist)

Se o cluster no Atlas estiver com **IP Access List** ativa, o Cloud Run usa **IPs de saída dinâmicos** por padrão. Para produção com allowlist:

- Configure **IP de saída estático** no Cloud Run usando **VPC + Cloud NAT** e inclua esse IP na allowlist do Atlas.
- Documentação oficial: [Static outbound IP address (Cloud Run)](https://cloud.google.com/run/docs/configuring/static-outbound-ip).

Em ambiente de teste, alguns times liberam `0.0.0.0/0` no Atlas; não recomendado para produção.

---

## ☸️ Kubernetes

Para deploy em produção com alta disponibilidade, escalabilidade automática e auto-healing.

### Estrutura de Arquivos K8s

```
k8s/
├── namespace.yaml     # Namespace isolado para a aplicação
├── secret.yaml        # Credenciais sensíveis (MongoDB, JWT)
├── configmap.yaml     # Configurações não-sensíveis
├── deployment.yaml    # Deploy com 2 réplicas + health checks
├── service.yaml       # Exposição interna (ClusterIP)
├── ingress.yaml       # Exposição externa (domínio HTTPS)
├── hpa.yaml           # Auto-scaling (2-10 pods)
└── monitoring/
    ├── prometheus.yaml  # Coleta de métricas
    └── grafana.yaml     # Dashboards visuais
```

### Fluxo Docker → Kubernetes

```
┌──────────────────────────────────────────────────────────────┐
│                      FLUXO DE DEPLOY                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   1. Dockerfile       →  Define a IMAGEM do container        │
│                                                              │
│   2. docker build     →  Gera imagem localmente              │
│                                                              │
│   3. docker push      →  Envia para registry (ECR/GCR/Hub)   │
│                                                              │
│   4. deployment.yaml  →  K8s baixa imagem e cria PODS        │
│                                                              │
│   5. service.yaml     →  Balanceia tráfego entre pods        │
│                                                              │
│   6. ingress.yaml     →  Expõe via domínio HTTPS             │
│                                                              │
│   7. hpa.yaml         →  Escala automaticamente (CPU/RAM)    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Arquitetura no Kubernetes

```
                        ┌─────────────────────┐
                        │      INTERNET       │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │       INGRESS       │
                        │ (api.example.com)   │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │       SERVICE       │
                        │  (ClusterIP :80)    │
                        └──────────┬──────────┘
                                   │
           ┌───────────────────────┼───────────────────────┐
           │                       │                       │
           ▼                       ▼                       ▼
   ┌───────────────┐       ┌───────────────┐       ┌───────────────┐
   │     POD 1     │       │     POD 2     │       │    POD N      │
   │ pdfprocessor  │       │ pdfprocessor  │       │ pdfprocessor  │
   │    :8081      │       │    :8081      │       │    :8081      │
   └───────────────┘       └───────────────┘       └───────────────┘
           │                       │                       │
           └───────────────────────┼───────────────────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │   MongoDB Atlas     │
                        │     (externo)       │
                        └─────────────────────┘
```

### Deploy Passo a Passo

#### 1. Build e push da imagem

```bash
# Build com tag de versão
docker build -t your-registry/pdfprocessor-api:v1.0.0 .

# Push para registry (Docker Hub, ECR, GCR, etc.)
docker push your-registry/pdfprocessor-api:v1.0.0
```

#### 2. Configurar credenciais

Edite `k8s/secret.yaml` com as credenciais reais:

```yaml
stringData:
  mongodb-uri: "mongodb+srv://user:password@cluster.mongodb.net/dbname"
  jwt-secret: "sua-chave-secreta-256-bits-minimo"
```

#### 3. Atualizar imagem no deployment

Edite `k8s/deployment.yaml`:

```yaml
image: your-registry/pdfprocessor-api:v1.0.0
```

#### 4. Aplicar manifests

```bash
# Aplicar todos os arquivos de uma vez
kubectl apply -f k8s/

# Ou em ordem específica
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/hpa.yaml
```

#### 5. Verificar status

```bash
# Ver pods
kubectl get pods -n pdfprocessor

# Ver logs em tempo real
kubectl logs -f deployment/pdfprocessor-api -n pdfprocessor

# Ver serviços
kubectl get svc -n pdfprocessor

# Ver HPA (auto-scaling)
kubectl get hpa -n pdfprocessor

# Descrever pod (troubleshooting)
kubectl describe pod <pod-name> -n pdfprocessor
```

### Recursos e Limites

| Componente | CPU Request | CPU Limit | Memory Request | Memory Limit |
|------------|-------------|-----------|----------------|--------------|
| API Pod    | 250m        | 1000m     | 512Mi          | 1Gi          |
| Prometheus | 100m        | 500m      | 256Mi          | 512Mi        |
| Grafana    | 50m         | 200m      | 128Mi          | 256Mi        |

Com HPA de 2-10 pods, o cluster precisa:
- **Mínimo**: 500m CPU, 1Gi RAM (2 pods)
- **Máximo**: 10 CPU, 10Gi RAM (10 pods)

---

## 📊 Monitoring com Prometheus + Grafana

O projeto inclui stack de observabilidade completa para monitorar a saúde da aplicação.

### O que é Monitorado

| Métrica | Descrição |
|---------|-----------|
| **HTTP Latency** | Tempo de resposta das requisições |
| **Requests/sec** | Taxa de requisições por segundo |
| **Error Rate** | Porcentagem de erros 5xx |
| **JVM Heap** | Uso de memória da JVM |
| **Pod Health** | Quantidade de pods saudáveis |
| **CPU/Memory** | Uso de recursos por pod |

### Arquitetura de Monitoring

```
┌─────────────────────────────────────────────────────────────┐
│                        MONITORING                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Spring Boot Actuator ──────► Prometheus ──────► Grafana   │
│   (/actuator/prometheus)       (coleta)         (dashboards)│
│                                                             │
│   Pod 1 ─────┐                                              │
│   Pod 2 ─────┼──► /actuator/prometheus ──► prometheus:9090  │
│   Pod N ─────┘                                              │
│                                                             │
│                               grafana:3000 ◄── Dashboard    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Deploy do Monitoring

```bash
# Aplicar Prometheus e Grafana
kubectl apply -f k8s/monitoring/prometheus.yaml
kubectl apply -f k8s/monitoring/grafana.yaml

# Verificar status
kubectl get pods -n pdfprocessor -l app=prometheus
kubectl get pods -n pdfprocessor -l app=grafana
```

### Acessar Dashboards

```bash
# Port-forward para Grafana (desenvolvimento)
kubectl port-forward svc/grafana 3000:3000 -n pdfprocessor

# Acessar: http://localhost:3000
# Login: admin / admin123 (mude em produção!)
```

### Métricas Disponíveis

O Spring Boot Actuator expõe automaticamente métricas em `/actuator/prometheus`:

```promql
# Latência média das requisições HTTP
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# Requests por segundo
sum(rate(http_server_requests_seconds_count[5m]))

# Uso de memória heap
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# Quantidade de erros 5xx
sum(http_server_requests_seconds_count{status=~"5.."})

# Pods ativos
count(up{job="pdfprocessor-api"} == 1)
```

### Dashboard Pré-configurado

O Grafana já vem com um dashboard pronto que mostra:

- 📈 **Latência HTTP** - Gauge com cores (verde < 0.5s, amarelo < 1s, vermelho > 1s)
- 📊 **Requests/sec** - Stat panel em tempo real
- 💾 **JVM Heap %** - Gauge de uso de memória
- ❌ **Erros 5xx** - Contador total de erros
- ✅ **Pods Ativos** - Quantidade de instâncias saudáveis

---

## 🔧 Configuração de Produção

### Checklist de Deploy

**Kubernetes (k8s):**
- [ ] Alterar credenciais em `k8s/secret.yaml`
- [ ] Alterar senha do Grafana em `k8s/monitoring/grafana.yaml`
- [ ] Configurar domínio real em `k8s/ingress.yaml`
- [ ] Habilitar TLS/SSL (cert-manager)
- [ ] Ajustar recursos de CPU/RAM conforme carga esperada
- [ ] Configurar alertas no Prometheus (AlertManager)
- [ ] Configurar backup do MongoDB

**Google Cloud Run:**  
- [ ] Ver seção [Deploy no Google Cloud Run](#-deploy-no-google-cloud-run)
- [ ] Definir `SPRING_PROFILES_ACTIVE=prod` e variáveis de ambiente (MongoDB, JWT)
- [ ] Preferir Secret Manager para `JWT_SECRET`; tratar MongoDB Atlas IP allowlist se aplicável

### Variáveis de Ambiente Importantes

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `PORT` | Cloud Run injeta automaticamente (valor pode variar). Local: default 8081. | — |
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB | `mongodb+srv://...` |
| `JWT_SECRET` | Chave para assinar tokens | `min-256-bits` |
| `JWT_EXPIRATION` | Tempo de expiração do access token | `900000` (15min) |
| `JWT_REFRESH_EXPIRATION` | Tempo de expiração do refresh token | `2592000000` (30d) |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | `local`, `docker` ou `prod` |

---

Bom código! 🚀
