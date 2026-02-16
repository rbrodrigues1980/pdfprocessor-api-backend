# Documentação — PDF Processor API

> Sistema de Extração e Consolidação de Contracheques (CAIXA + FUNCEF)
>
> Spring Boot 3.x · WebFlux · MongoDB · Gemini AI · Clean Architecture

---

## Navegação Rápida

| #  | Seção | Descrição | Documentos |
|----|-------|-----------|------------|
| 01 | [Início Rápido](./01-inicio-rapido/) | Instalação local e configurações iniciais | 2 docs |
| 02 | [Arquitetura](./02-arquitetura/) | Visão geral, Clean Architecture, diagramas e extrator PDF | 7 docs |
| 03 | [Banco de Dados](./03-banco-de-dados/) | Modelagem MongoDB, dicionário de dados, troubleshooting | 4 docs |
| 04 | [API — Referência](./04-api-referencia/) | Endpoints core do sistema (APIs 1-7), especificação | 10 docs |
| 05 | [Autenticação & Multi-Tenant](./05-autenticacao-multi-tenant/) | JWT, 2FA, RBAC, isolamento por tenant | 7 docs |
| 06 | [Guia Frontend](./06-guia-frontend/) | Documentação de integração para desenvolvedores frontend | 11 docs |
| 07 | [Imposto de Renda](./07-imposto-renda/) | IRPF, Taxa SELIC, extração iText8, rubricas IR | 4 docs |
| 08 | [Inteligência Artificial](./08-inteligencia-artificial/) | Integração Gemini AI 2.5, configuração e plano de upgrade | 2 docs |
| 09 | [Deploy](./09-deploy/) | Deploy no Google Cloud Run | 1 doc |
| 10 | [Planejamento](./10-planejamento/) | Backlog, sprints, roadmap de funcionalidades | 4 docs |
| 11 | [Troubleshooting](./11-troubleshooting/) | Correções, problemas conhecidos, decisões técnicas | 3 docs |

---

## Por Onde Começar?

### Sou novo no projeto
1. Leia o [Início Rápido](./01-inicio-rapido/) para subir o ambiente local
2. Veja a [Visão Geral do Sistema](./02-arquitetura/001%20-%20MASTER_DOCUMENTATION.md) para entender o contexto
3. Explore a [Arquitetura Clean Architecture](./02-arquitetura/003%20-%20API_COMPLETA_E_ARQUITETURA.md) para entender as camadas

### Sou desenvolvedor backend
1. Consulte a [Referência de APIs](./04-api-referencia/) para detalhes dos endpoints
2. Veja a [Modelagem MongoDB](./03-banco-de-dados/001%20-%20MODELAGEM_MONGO.md) para entender os dados
3. Entenda o [Extrator de PDF](./02-arquitetura/007%20-%20EXTRATOR.md) para lógica de extração

### Sou desenvolvedor frontend
1. Vá direto ao [Guia Frontend](./06-guia-frontend/) — documentação feita para você
2. Cada endpoint tem exemplos em TypeScript, React, Vue e Angular
3. Comece pela [Autenticação](./06-guia-frontend/001%20-%20API_AUTH_FRONTEND.md), depois explore os módulos

### Preciso fazer deploy
1. Siga o [Guia de Deploy no Cloud Run](./09-deploy/001%20-%20DEPLOY_GOOGLE_CLOUD_RUN.md)
2. Configure o [Banco de Homologação](./03-banco-de-dados/003%20-%20BANCO_DADOS_HML.md) se necessário

### Preciso entender a IA
1. Veja a [Integração Gemini AI](./08-inteligencia-artificial/001%20-%20API_GEMINI_AI.md) para setup técnico
2. Consulte o [Plano de Upgrade](./08-inteligencia-artificial/002%20-%20PLANO_UPGRADE_GEMINI_AI.md) para decisões e resultados

---

## Stack Tecnológica

| Componente | Tecnologia |
|-----------|------------|
| Backend | Java 21 + Spring Boot 3.x + WebFlux (reativo) |
| Banco de Dados | MongoDB Atlas |
| Extração PDF | Apache PDFBox + Tika + iText 8 |
| IA | Google Gemini AI 2.5 (Flash + Pro) |
| Autenticação | JWT + Refresh Token + 2FA |
| Multi-Tenancy | Row-level isolation por tenantId |
| Deploy | Google Cloud Run + Docker |
| Excel | Apache POI |

---

## Estrutura de Diretórios

```
docs/
├── README.md                              ← Você está aqui
├── 01-inicio-rapido/                      # Setup e configuração local
├── 02-arquitetura/                        # Arquitetura, diagramas, extrator
├── 03-banco-de-dados/                     # MongoDB, modelagem, dicionário
├── 04-api-referencia/                     # APIs core (1-7) + especificação
├── 05-autenticacao-multi-tenant/          # JWT, 2FA, RBAC, Multi-tenancy
├── 06-guia-frontend/                      # Integração frontend (11 guias)
├── 07-imposto-renda/                      # IRPF, SELIC, rubricas IR
├── 08-inteligencia-artificial/            # Gemini AI 2.5
├── 09-deploy/                             # Google Cloud Run
├── 10-planejamento/                       # Backlog, sprints, roadmap
└── 11-troubleshooting/                    # Correções e problemas
```

---

> **Última atualização:** Fevereiro 2026
