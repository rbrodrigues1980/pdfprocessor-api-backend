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
As chaves de segurança também estão configuradas no `application.yml`.
**Importante**: Em produção, substitua os valores padrão por variáveis de ambiente.

```yaml
jwt:
  secret: <sua-chave-secreta-super-segura>
  expiration: 900000 # 15 minutos
  refresh-expiration: 2592000000 # 30 dias
```

---

## ▶️ Como Rodar a Aplicação

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

A aplicação iniciará na porta **8081**.

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

O projeto possui um sistema de logs profissional configurado com **Logback**:

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

- [ ] Alterar credenciais em `k8s/secret.yaml`
- [ ] Alterar senha do Grafana em `k8s/monitoring/grafana.yaml`
- [ ] Configurar domínio real em `k8s/ingress.yaml`
- [ ] Habilitar TLS/SSL (cert-manager)
- [ ] Ajustar recursos de CPU/RAM conforme carga esperada
- [ ] Configurar alertas no Prometheus (AlertManager)
- [ ] Configurar backup do MongoDB

### Variáveis de Ambiente Importantes

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `SPRING_DATA_MONGODB_URI` | URI do MongoDB | `mongodb+srv://...` |
| `JWT_SECRET` | Chave para assinar tokens | `min-256-bits` |
| `JWT_EXPIRATION` | Tempo de expiração do access token | `900000` (15min) |
| `JWT_REFRESH_EXPIRATION` | Tempo de expiração do refresh token | `2592000000` (30d) |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring | `docker` ou `prod` |

---

Bom código! 🚀
