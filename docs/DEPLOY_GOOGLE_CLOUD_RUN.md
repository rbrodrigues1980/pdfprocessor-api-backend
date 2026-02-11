# Deploy no Google Cloud Run — Guia Completo

> Documento criado em 11/02/2026.
> Registra todo o processo de deploy da API no Google Cloud Run, incluindo alterações no código, configurações no Console, problemas encontrados e soluções.

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Arquitetura do Deploy](#2-arquitetura-do-deploy)
3. [Alterações no Código-Fonte](#3-alterações-no-código-fonte)
4. [Configuração no Google Cloud Console](#4-configuração-no-google-cloud-console)
5. [Problemas Encontrados e Soluções](#5-problemas-encontrados-e-soluções)
6. [Como Testar a API em Produção](#6-como-testar-a-api-em-produção)
7. [CI/CD — Deploy Automático via Git](#7-cicd--deploy-automático-via-git)
8. [Build Otimizado com Kaniko Cache](#8-build-otimizado-com-kaniko-cache)
9. [Custos e Economia](#9-custos-e-economia)
10. [Comandos Úteis](#10-comandos-úteis)
11. [Checklist de Deploy](#11-checklist-de-deploy)

---

## 1. Visão Geral

### Por que Cloud Run?

A API é uma aplicação Spring Boot WebFlux containerizada (Docker) que conecta ao MongoDB Atlas. O Google Cloud Run é ideal porque:

- **Serverless**: não precisa gerenciar servidores ou clusters Kubernetes.
- **Container-native**: usa o mesmo `Dockerfile` do desenvolvimento local.
- **Escala automática**: de 0 a N instâncias conforme demanda.
- **Pay-per-use**: cobra por tempo de processamento (não por tempo ocioso, se min-instances=0).
- **CI/CD integrado**: deploy automático a cada push no GitHub.

### Dados do serviço em produção

| Item | Valor |
|------|-------|
| **Projeto GCP** | `RRR-Software-Solutions` |
| **Serviço Cloud Run** | `pdfprocessor-api-backend` |
| **Região** | `us-central1` (Iowa) — escolhida por custo (Tier 1) |
| **URL pública** | `https://pdfprocessor-api-backend-177627167012.us-central1.run.app` |
| **Repositório GitHub** | `rbrodrigues1980/pdfprocessor-api-backend` |
| **Branch de deploy** | `main` |
| **Perfil Spring** | `prod` (via `SPRING_PROFILES_ACTIVE=prod`) |

---

## 2. Arquitetura do Deploy

### Fluxo CI/CD (Continuous Deployment)

```
┌─────────────────────────────────────────────────────────────────────┐
│                     FLUXO DE DEPLOY AUTOMATIZADO                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   1. git push origin main     →  Push para GitHub                   │
│                                                                     │
│   2. Cloud Build (gatilho)    →  Detecta push, inicia pipeline      │
│                                                                     │
│   3. docker build (kaniko)    →  Builda imagem com cache de layers  │
│                                                                     │
│   4. Artifact Registry        →  Armazena imagem Docker             │
│                                                                     │
│   5. Cloud Run (nova revisão) →  Deploya automaticamente            │
│                                                                     │
│   Tempo médio: ~2-3 min (com cache) / ~7 min (sem cache)            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Arquitetura em produção

```
┌──────────────┐     HTTPS      ┌──────────────────┐
│   Frontend   │ ──────────────→│   Cloud Run      │
│   (browser)  │                │  pdfprocessor-api│
└──────────────┘                │    (1-3 pods)    │
                                └────────┬─────────┘
                                         │
                                         │ mongodb+srv://
                                         ▼
                                ┌──────────────────┐
                                │  MongoDB Atlas   │
                                │   (externo)      │
                                └──────────────────┘
```

---

## 3. Alterações no Código-Fonte

Antes de deployar, foram necessárias várias alterações para compatibilidade com Cloud Run.

### 3.1. `application.yml` — Porta, perfil e variáveis de ambiente

**Problema**: Cloud Run injeta a variável `PORT` e espera que o container escute nessa porta. Além disso, credenciais não podem ficar hardcoded no código em produção.

**Alterações feitas**:

```yaml
server:
  port: ${PORT:8081}                              # Cloud Run injeta PORT; local usa 8081
  forward-headers-strategy: framework             # Lê X-Forwarded-Proto do proxy (HTTPS)

spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}       # Perfil via env var
  data:
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI:uri-local}   # URI via env var em prod

jwt:
  secret: ${JWT_SECRET:chave-local}               # Secret via env var em prod
  expiration: ${JWT_EXPIRATION:43200000}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:2592000000}
```

**Por que `forward-headers-strategy: framework`**: Cloud Run é um proxy reverso HTTPS. Ele recebe HTTPS do cliente e repassa HTTP internamente, adicionando o header `X-Forwarded-Proto: https`. Sem essa config, o Spring gera URLs com `http://` (ex.: Swagger), causando erro de mixed content no navegador.

### 3.2. `Dockerfile` — Remover perfil hardcoded

**Problema**: O Dockerfile original tinha `-Dspring.profiles.active=docker` no `JAVA_OPTS`. No Spring Boot, **system property (`-D`) tem prioridade sobre variável de ambiente**. Isso impedia que `SPRING_PROFILES_ACTIVE=prod` (definido no Cloud Run) fosse respeitado.

**Antes** (quebrava no Cloud Run):

```dockerfile
ENV JAVA_OPTS="... -Dspring.profiles.active=docker"
```

**Depois** (funciona em qualquer ambiente):

```dockerfile
# JVM opts SEM perfil hardcoded
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Perfil padrão para Docker local; Cloud Run sobrescreve com SPRING_PROFILES_ACTIVE=prod
ENV SPRING_PROFILES_ACTIVE=docker
```

**Também ajustado**: healthcheck usa `${PORT:-8081}` para funcionar com porta dinâmica.

### 3.3. `SecurityConfig.java` — Liberar Actuator

**Problema**: Cloud Run usa health checks para saber se o container está pronto. Os endpoints do Actuator precisam estar acessíveis sem autenticação JWT.

**Alteração**: Adicionados `/actuator/health` e `/actuator/info` ao `permitAll()`:

```java
.pathMatchers("/v3/api-docs/**",
              "/swagger-ui/**",
              "/swagger-ui.html",
              "/webjars/**",
              "/favicon.ico",
              "/error",
              "/actuator/health",    // ← ADICIONADO
              "/actuator/info",      // ← ADICIONADO
              apiPrefix + "/system/**",
              apiPrefix + "/auth/**",
              apiPrefix + "/tenants")
.permitAll()
```

### 3.4. `logback-spring.xml` — Logs em produção

**Problema**: Cloud Run é stateless (sem disco persistente). Logs em arquivo (`logs/fulllog.log`) seriam perdidos em reinícios ou scale-to-zero.

**Solução**: Usar `<springProfile>` para separar o comportamento:

```xml
<!-- Perfil prod (Cloud Run): apenas console -->
<springProfile name="prod">
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</springProfile>

<!-- Outros perfis (local/docker): console + arquivo + mongo -->
<springProfile name="!prod">
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ASYNC_MONGO"/>
    </root>
</springProfile>
```

Cloud Run coleta `stdout/stderr` automaticamente e exibe nos logs do serviço.

### 3.5. `.gitignore` — Inclusão do `gradle-wrapper.jar`

**Problema**: O build no Cloud Build falhava com `Error: Unable to access jarfile /app/gradle/wrapper/gradle-wrapper.jar`. O arquivo não estava no repositório.

**Causa raiz**: No `.gitignore`, a regra `*.jar` (linha 44) vinha DEPOIS da exceção `!gradle/wrapper/gradle-wrapper.jar` (linha 4). **No gitignore, a última regra que bate ganha**, então o `*.jar` sobrescrevia a exceção.

**Correção**: Mover a exceção para DEPOIS do `*.jar`:

```gitignore
# Package Files
*.jar
*.war
*.nar
*.ear

# Exceção: Gradle Wrapper JAR (precisa estar no repo para builds funcionarem)
!gradle/wrapper/gradle-wrapper.jar
```

E forçar a inclusão do arquivo:

```bash
git add -f gradle/wrapper/gradle-wrapper.jar
```

---

## 4. Configuração no Google Cloud Console

### 4.1. APIs habilitadas

Três APIs precisam estar ativas no projeto:

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

### 4.2. Criação do serviço (Continuous Deployment)

Caminho: **Cloud Run** → **Criar serviço** → **"Realizar a implantação contínua com um repositório (GitHub)"**

1. **Conectar GitHub**: autorizar Cloud Build a acessar o repositório.
2. **Repositório**: `rbrodrigues1980/pdfprocessor-api-backend`
3. **Branch**: `main`
4. **Tipo de build**: Dockerfile (caminho: `/Dockerfile`)

### 4.3. Configuração do serviço

| Configuração | Valor | Justificativa |
|---|---|---|
| **Nome** | `pdfprocessor-api-backend` | Identificador do serviço |
| **Região** | `us-central1` (Iowa) | Tier 1 — menor custo |
| **Autenticação** | Permitir acesso público | A API controla acesso via JWT |
| **CPU** | 1 vCPU | Suficiente para processamento de PDFs |
| **Memória** | 2 GiB | PDFBox + Tika + POI + JVM precisam de RAM |
| **Concorrência** | 10 | Cada request de PDF consome muita RAM/CPU |
| **Timeout** | 900 segundos | PDFs grandes podem demorar |
| **Min instâncias** | 0 ou 1 | 0 = economiza; 1 = sem cold start |
| **Max instâncias** | 3 | Controle de custo |
| **Faturamento** | Baseado em solicitações | Mais barato para pouco tráfego |
| **Porta do contêiner** | 8080 | Cloud Run injeta PORT=8080 |
| **Otimização CPU de inicialização** | Ativada | Reduz cold start do Java/Spring |
| **Entrada (Ingress)** | Todos | Acesso público pela internet |

### 4.4. Variáveis de ambiente configuradas no Console

| Variável | Valor |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATA_MONGODB_URI` | `mongodb+srv://usuario:senha@seu-cluster.mongodb.net/pdfprocessor?...` |
| `JWT_SECRET` | (chave secreta — ideal migrar para Secret Manager) |

---

## 5. Problemas Encontrados e Soluções

### Problema 1: Build falhou — `gradle-wrapper.jar` ausente

**Erro**:
```
Error: Unable to access jarfile /app/gradle/wrapper/gradle-wrapper.jar
The command '/bin/sh -c ./gradlew bootJar --no-daemon -x test' returned a non-zero code: 1
```

**Causa**: `.gitignore` com `*.jar` sobrescrevendo a exceção `!gradle/wrapper/gradle-wrapper.jar` (ordem errada).

**Solução**: Mover a exceção para depois de `*.jar` e rodar `git add -f gradle/wrapper/gradle-wrapper.jar`.

**Commits**:
- `8703f33` — `fix: incluir gradle-wrapper.jar no repositorio para Cloud Build`

---

### Problema 2: Swagger gerando URLs `http://` (erro CORS / mixed content)

**Erro no Swagger**:
```
Failed to fetch. Possible Reasons: CORS, Network Failure.
URL scheme must be "http" or "https" for CORS request.
```

**Causa**: Cloud Run é proxy reverso HTTPS → HTTP. O Spring Boot não sabia que a requisição original era HTTPS e gerava URLs com `http://`. O navegador bloqueava por mixed content.

**Solução**: Adicionar `server.forward-headers-strategy: framework` em `application.yml`. Isso ativa o `ForwardedHeaderTransformer` no WebFlux, que lê `X-Forwarded-Proto: https` do Cloud Run.

**Commit**:
- `d84e389` — `fix: habilitar forward-headers-strategy para HTTPS correto no Cloud Run`

---

### Problema 3: Perfil Spring fixo no Dockerfile (`docker` em vez de `prod`)

**Causa**: `-Dspring.profiles.active=docker` no `JAVA_OPTS` do Dockerfile. System property (`-D`) tem prioridade sobre variável de ambiente no Spring Boot, impedindo que `SPRING_PROFILES_ACTIVE=prod` fosse respeitado.

**Solução**: Remover `-Dspring.profiles.active=docker` do `JAVA_OPTS` e usar `ENV SPRING_PROFILES_ACTIVE=docker` como default (que pode ser sobrescrito no Cloud Run).

**Commit**:
- `4778641` — `fix: remover profile hardcoded do Dockerfile para compatibilidade com Cloud Run`

---

## 6. Como Testar a API em Produção

### 6.1. Health check (app está no ar?)

```bash
curl https://pdfprocessor-api-backend-177627167012.us-central1.run.app/actuator/health
```

Resposta esperada:
```json
{"status":"UP"}
```

### 6.2. Swagger (documentação interativa)

Abra no navegador:
```
https://pdfprocessor-api-backend-177627167012.us-central1.run.app/swagger-ui.html
```

### 6.3. Login (autenticação JWT + MongoDB)

```bash
curl -X POST https://pdfprocessor-api-backend-177627167012.us-central1.run.app/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seu-email","password":"sua-senha"}'
```

Se retornar o token JWT, está tudo funcionando (Spring Boot + MongoDB Atlas + JWT).

### 6.4. OpenAPI spec

```bash
curl https://pdfprocessor-api-backend-177627167012.us-central1.run.app/v3/api-docs
```

### Checklist rápido

| Teste | Indica que... |
|---|---|
| `/actuator/health` → `{"status":"UP"}` | App subiu e Spring Boot rodando |
| `/swagger-ui.html` abre | Endpoints expostos corretamente |
| `/api/v1/auth/login` retorna token | MongoDB conectado + JWT funcional |

---

## 7. CI/CD — Deploy Automático via Git

### Como funciona

O Cloud Build possui um **gatilho** (trigger) conectado ao GitHub. A cada `git push origin main`:

1. Cloud Build clona o repositório
2. Builda a imagem Docker usando o `Dockerfile`
3. Publica no Artifact Registry
4. Deploya uma nova revisão no Cloud Run

**Não é necessário nenhum comando manual** — basta fazer push.

### Fluxo para deployar uma mudança

```bash
# 1. Faça suas alterações no código
# 2. Commit
git add .
git commit -m "feat: minha alteração"

# 3. Push para main → deploy automático
git push origin main

# 4. Acompanhe em: Cloud Build > Histórico (leva ~2-7 min)
```

### Onde monitorar

- **Cloud Build > Histórico**: progresso do build em tempo real
- **Cloud Run > Revisões**: lista de deploys com status
- **Cloud Run > Observabilidade/Logs**: logs da aplicação

---

## 8. Build Otimizado com Kaniko Cache

### O problema

Por padrão, o Cloud Build via Console roda `docker build --no-cache`. Isso baixa **todas as dependências do Gradle do zero** a cada push (~3 min só de download).

### A solução: `cloudbuild.yaml` com kaniko

O arquivo `cloudbuild.yaml` na raiz do projeto usa o [kaniko](https://cloud.google.com/build/docs/optimize-builds/kaniko-cache), que cacheia as layers do Docker no Artifact Registry.

```yaml
steps:
  - name: 'gcr.io/kaniko-project/executor:latest'
    args:
      - '--destination=${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_IMAGE_NAME}:$COMMIT_SHA'
      - '--destination=${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_IMAGE_NAME}:latest'
      - '--cache=true'
      - '--cache-ttl=168h'     # cache válido por 7 dias
      - '--dockerfile=Dockerfile'
      - '--context=.'
```

### Como ativar

1. Vá em **Cloud Build > Gatilhos** (Triggers)
2. Edite o gatilho existente
3. Troque **"Dockerfile"** por **"Arquivo de configuração do Cloud Build"**
4. Caminho: `cloudbuild.yaml`
5. Salve

### Resultado

| Build | Sem cache | Com kaniko |
|---|---|---|
| Primeiro build | ~7 min | ~7 min (cria cache) |
| Builds seguintes | ~7 min | **~2-3 min** |
| Só mudou código Java | ~7 min | **~1-2 min** |

---

## 9. Custos e Economia

### Cloud Build (builds)

| Recurso | Free tier | Seu uso |
|---|---|---|
| Tempo de build (e1-small) | 120 min/mês grátis | ~7 min/build → ~17 builds grátis |
| Após free tier | ~$0.003/min | ~$0.02 por build |

### Cloud Run (execução)

| Recurso | Free tier mensal |
|---|---|
| CPU | 180.000 vCPU-segundos |
| Memória | 360.000 GiB-segundos |
| Requests | 2 milhões |

### Dicas de economia

- **`min-instances: 0`**: não paga quando não tem tráfego (mas tem cold start ~15-30s)
- **`min-instances: 1`**: sem cold start, mas paga 24/7 por 1 instância
- **`max-instances: 3`**: limita escala e evita surpresas de custo
- **Faturamento baseado em solicitações**: CPU é limitada quando não há requests (mais barato)
- **Região Tier 1** (`us-central1`): ~30% mais barato que regiões Tier 2 (como `southamerica-east1`)

---

## 10. Comandos Úteis

### Ver URL do serviço

```bash
gcloud run services describe pdfprocessor-api-backend --region us-central1 --format="value(status.url)"
```

### Ver logs em tempo real

```bash
gcloud logs read --project=rrr-software-solutions --limit=50
```

### Atualizar variáveis de ambiente (merge)

```bash
gcloud run services update pdfprocessor-api-backend --region us-central1 \
  --update-env-vars "CHAVE=valor"
```

### Ver revisões ativas

```bash
gcloud run revisions list --service=pdfprocessor-api-backend --region=us-central1
```

### Forçar novo deploy (sem mudança de código)

```bash
gcloud run services update pdfprocessor-api-backend --region us-central1 \
  --update-labels="redeploy=$(date +%s)"
```

### Alterar memória/CPU

```bash
gcloud run services update pdfprocessor-api-backend --region us-central1 \
  --memory 2Gi --cpu 1
```

### Alterar instâncias min/max

```bash
gcloud run services update pdfprocessor-api-backend --region us-central1 \
  --min-instances 0 --max-instances 3
```

---

## 11. Checklist de Deploy

### Pré-deploy (código)

- [x] `application.yml`: `server.port: ${PORT:8081}`
- [x] `application.yml`: `forward-headers-strategy: framework`
- [x] `application.yml`: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`
- [x] `application.yml`: MongoDB e JWT via variáveis de ambiente com fallback local
- [x] `Dockerfile`: sem `-Dspring.profiles.active` no JAVA_OPTS
- [x] `Dockerfile`: `ENV SPRING_PROFILES_ACTIVE=docker` como default
- [x] `Dockerfile`: healthcheck com `${PORT:-8081}`
- [x] `SecurityConfig`: `/actuator/health` e `/actuator/info` em `permitAll()`
- [x] `logback-spring.xml`: perfil `prod` só com console
- [x] `.gitignore`: `gradle-wrapper.jar` com exceção APÓS `*.jar`
- [x] `gradle/wrapper/gradle-wrapper.jar`: presente no repositório
- [x] `cloudbuild.yaml`: kaniko com cache (opcional, para builds rápidos)

### Console (Google Cloud)

- [x] APIs habilitadas: Cloud Run, Cloud Build, Artifact Registry
- [x] Serviço criado com Continuous Deployment do GitHub
- [x] Região: `us-central1`
- [x] Autenticação: acesso público (API controla via JWT)
- [x] Memória: 2 GiB
- [x] CPU: 1
- [x] Concorrência: 10
- [x] Timeout: 900s
- [x] Instâncias: min 0-1, max 3
- [x] Variáveis: `SPRING_PROFILES_ACTIVE`, `SPRING_DATA_MONGODB_URI`, `JWT_SECRET`

### Pós-deploy (validação)

- [x] `/actuator/health` retorna `{"status":"UP"}`
- [x] `/swagger-ui.html` abre com URLs `https://`
- [x] `/v3/api-docs` retorna especificação OpenAPI
- [x] Login via `/api/v1/auth/login` retorna token JWT

---

## Histórico de Commits Relacionados

| Commit | Mensagem | O que resolveu |
|--------|----------|----------------|
| `c6356b0` | feat: integrar Gemini AI, preparar deploy Cloud Run | Alterações iniciais (application.yml, SecurityConfig, logback, README) |
| `4778641` | fix: remover profile hardcoded do Dockerfile | Perfil `docker` fixo impedia `prod` no Cloud Run |
| `8703f33` | fix: incluir gradle-wrapper.jar no repositório | Build falhava por falta do wrapper JAR |
| `d84e389` | fix: habilitar forward-headers-strategy para HTTPS | Swagger gerava URLs `http://` causando erro CORS |
| `cffe816` | feat: adicionar cloudbuild.yaml com kaniko cache | Otimização de tempo de build (~7 min → ~2-3 min) |

---

## Arquivos Modificados/Criados

| Arquivo | Tipo | Descrição |
|---------|------|-----------|
| `src/main/resources/application.yml` | Modificado | PORT, perfil, env vars, forward-headers |
| `Dockerfile` | Modificado | Remover profile hardcoded, PORT dinâmico |
| `src/.../SecurityConfig.java` | Modificado | Liberar actuator/health sem auth |
| `src/main/resources/logback-spring.xml` | Modificado | Perfil prod: só console |
| `.gitignore` | Modificado | Exceção do gradle-wrapper.jar após *.jar |
| `gradle/wrapper/gradle-wrapper.jar` | Adicionado | Necessário para build no Cloud Build |
| `cloudbuild.yaml` | Criado | Build com kaniko cache |
| `README.md` | Modificado | Seção completa de Cloud Run |
| `k8s/README.md` | Modificado | Referência ao Cloud Run |
| `docs/DEPLOY_GOOGLE_CLOUD_RUN.md` | Criado | Este documento |
