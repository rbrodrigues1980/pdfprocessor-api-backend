# Plano: Automação de Declaração de Imposto de Renda via Programa da Receita Federal

## Índice

- [1. Visão Geral](#1-visão-geral)
- [2. Problema Arquitetural: Cloud Run vs Desktop Local](#2-problema-arquitetural-cloud-run-vs-desktop-local)
- [3. Arquitetura da Solução](#3-arquitetura-da-solução)
- [4. Contexto do Projeto](#4-contexto-do-projeto)
- [5. PARTE 1 — Backend (Cloud Run)](#5-parte-1--backend-cloud-run)
  - [5.1. Fase 1 — Extração de Dados do Informe de Rendimentos](#51-fase-1--extração-de-dados-do-informe-de-rendimentos)
  - [5.2. Fase 2 — API de Orquestração](#52-fase-2--api-de-orquestração)
- [6. PARTE 2 — Agente Local (Desktop do Usuário)](#6-parte-2--agente-local-desktop-do-usuário)
  - [6.1. Fase 3 — Agente Local e Comunicação](#61-fase-3--agente-local-e-comunicação)
  - [6.2. Fase 4 — Mapeamento dos Programas da Receita Federal](#62-fase-4--mapeamento-dos-programas-da-receita-federal)
  - [6.3. Fase 5 — Automação da Interface (RPA)](#63-fase-5--automação-da-interface-rpa)
- [7. Fluxo Completo de Ponta a Ponta](#7-fluxo-completo-de-ponta-a-ponta)
- [8. Decisão Técnica: Access Bridge vs Robot](#8-decisão-técnica-access-bridge-vs-robot)
- [9. Estrutura de Pacotes Final (Kotlin)](#9-estrutura-de-pacotes-final-kotlin)
- [10. Dependências Adicionais](#10-dependências-adicionais)
- [11. Riscos e Pontos de Atenção](#11-riscos-e-pontos-de-atenção)
- [12. Ordem de Implementação Sugerida](#12-ordem-de-implementação-sugerida)
- [Glossário](#glossário)

---

## 1. Visão Geral

Criar uma automação que permita ao usuário, **a partir do sistema web em produção**:

1. Fazer upload do **informe de rendimentos** (PDF) de uma pessoa
2. O backend extrai os dados estruturados via **Gemini AI** (Cloud Run)
3. O usuário revisa e confirma os dados extraídos
4. O usuário seleciona o **ano do programa IRPF** (2016 a 2025)
5. Um **agente local** no computador do usuário abre o programa e preenche a declaração automaticamente

---

## 2. Problema Arquitetural: Cloud Run vs Desktop Local

### O problema

```
❌ O que NÃO funciona:

  Navegador ──→ Cloud Run ──→ Programa IRPF ???
                (servidor)     (desktop do usuário)

  Cloud Run NÃO tem tela, NÃO tem mouse, NÃO tem os programas IRPF instalados.
  É um container Linux na nuvem. Não consegue interagir com o desktop do usuário.
```

### Por que nenhuma das opções (Access Bridge, Robot, SikuliX) funciona direto do Cloud Run?

| Tecnologia | Motivo de não funcionar no Cloud Run |
|---|---|
| **Java Access Bridge** | Precisa estar na mesma máquina que o programa Swing. Cloud Run é Linux, IRPF é Windows local |
| **java.awt.Robot** | Simula teclado/mouse do sistema local. Cloud Run não tem display |
| **SikuliX** | Precisa de tela para capturar imagem. Cloud Run é headless |

### A solução: Arquitetura de dois componentes

```
✅ O que FUNCIONA:

  ┌─────────────────────────────────┐      ┌──────────────────────────────────┐
  │       CLOUD RUN (Backend)       │      │   COMPUTADOR DO USUÁRIO (Local)  │
  │                                 │      │                                  │
  │  • Upload do PDF                │      │  • Agente Local (Kotlin JAR)     │
  │  • Extração via Gemini AI       │◄────►│  • Abre programa IRPF            │
  │  • Validação de dados           │ WSS  │  • Preenche formulários          │
  │  • Orquestração do fluxo        │      │  • Captura screenshots           │
  │  • Armazena status/resultado    │      │  • Reporta progresso             │
  │                                 │      │                                  │
  └─────────────────────────────────┘      └──────────────────────────────────┘
           ▲                                          ▲
           │ HTTPS                                    │ Desktop
           │                                          │
      ┌────┴────┐                              ┌──────┴──────┐
      │ Browser │                              │ Programa    │
      │ (React) │                              │ IRPF 2024   │
      └─────────┘                              └─────────────┘
```

---

## 3. Arquitetura da Solução

### Componentes

| Componente | Onde roda | Tecnologia | Responsabilidade |
|---|---|---|---|
| **Frontend** | Navegador | React (existente) | Upload PDF, selecionar ano, acompanhar status |
| **Backend API** | Cloud Run | Spring Boot + Kotlin | Extração IA, validação, orquestração |
| **Agente Local** | Desktop do usuário | Kotlin JAR (desktop) | RPA: abrir programa, preencher campos |

### Comunicação entre Backend e Agente Local

**Opção escolhida: WebSocket (WSS)**

O agente local mantém uma conexão WebSocket persistente com o backend. Isso permite:

- Backend enviar **comandos em tempo real** para o agente (ex: "preencha campo X com valor Y")
- Agente enviar **status em tempo real** para o backend (ex: "preenchendo campo 3/15")
- Usuário acompanhar **progresso ao vivo** no navegador

```
Fluxo de mensagens WebSocket:

Backend → Agente:  { "action": "OPEN_PROGRAM", "year": 2024 }
Agente  → Backend: { "status": "PROGRAM_OPENED", "screenshot": "base64..." }
Backend → Agente:  { "action": "FILL_FIELD", "field": "cpf", "value": "123.456.789-00" }
Agente  → Backend: { "status": "FIELD_FILLED", "field": "cpf", "progress": 25 }
...
```

### Alternativas de comunicação consideradas

| Alternativa | Prós | Contras | Decisão |
|---|---|---|---|
| **WebSocket (WSS)** | Tempo real, bidirecional, status ao vivo | Precisa manter conexão aberta | ✅ Escolhida |
| **Polling HTTP** | Simples, stateless | Delay, mais requests, sem tempo real | ❌ |
| **SSE (Server-Sent Events)** | Simples para server→client | Apenas unidirecional | ❌ |
| **gRPC** | Eficiente, tipado | Complexo, overkill | ❌ |

---

## 4. Contexto do Projeto

O projeto `pdfprocessor-api-backend` já possui recursos que serão reaproveitados:

| Recurso existente | Onde | Uso na automação |
|---|---|---|
| **Apache PDFBox 3.0.3** | `build.gradle.kts` | Leitura de PDFs no backend |
| **Apache Tika 2.9.2** | `build.gradle.kts` | Parser de PDFs como fallback |
| **Gemini AI (Vertex AI 1.43.0)** | `infrastructure/ai/` | Extração inteligente de dados |
| **Spring WebFlux** | Base do projeto | WebSocket reativo para comunicação com agente |
| **Clean Architecture** | Padrão do projeto | Organização dos novos módulos |
| **Pacote Kotlin** | `src/main/kotlin/` | Local da implementação |
| **Spring Boot 3.3.5 + Java 21** | Base do projeto | Runtime |

---

## 5. PARTE 1 — Backend (Cloud Run)

Tudo que roda no servidor. O backend é responsável pela **inteligência** (extração, validação) e **orquestração** (comandar o agente local).

---

### 5.1. Fase 1 — Extração de Dados do Informe de Rendimentos

#### Objetivo

Receber o PDF do informe de rendimentos (upload do usuário), extrair dados estruturados via Gemini AI.

#### Pacote

`br.com.verticelabs.pdfprocessor.application.irpfautomation.extraction`

#### Componentes

| Componente | Tipo | Descrição |
|---|---|---|
| `InformeRendimentosData` | `data class` | Todos os campos estruturados do informe |
| `DependenteInfo` | `data class` | Informações de dependentes |
| `InformeRendimentosExtractor` | `interface` | Contrato para extração |
| `GeminiInformeExtractor` | `@Service` | Extração via Gemini AI (principal) |
| `PdfBoxInformeExtractor` | `@Service` | Extração via PDFBox (fallback) |

#### Data Class — `InformeRendimentosData`

```kotlin
data class InformeRendimentosData(
    // === FONTE PAGADORA ===
    val cnpjFontePagadora: String,
    val nomeFontePagadora: String,

    // === PESSOA FÍSICA (BENEFICIÁRIO) ===
    val cpfBeneficiario: String,
    val nomeBeneficiario: String,

    // === ANO-CALENDÁRIO ===
    val anoCalendario: Int,

    // === RENDIMENTOS TRIBUTÁVEIS ===
    val totalRendimentosTributaveis: BigDecimal,
    val contribuicaoPrevidenciaria: BigDecimal,
    val impostoRetidoFonte: BigDecimal,
    val decimoTerceiroSalario: BigDecimal,
    val irsfDecimoTerceiro: BigDecimal,

    // === RENDIMENTOS ISENTOS E NÃO TRIBUTÁVEIS ===
    val parcelaIsentaProventos65Anos: BigDecimal?,
    val diariasAjudaCusto: BigDecimal?,
    val pensaoAposentadoriaReforma: BigDecimal?,
    val lucrosDividendos: BigDecimal?,
    val valoresPagesTitularSocio: BigDecimal?,
    val indenizacoesPorRescisao: BigDecimal?,
    val outrosRendimentosIsentos: BigDecimal?,

    // === RENDIMENTOS SUJEITOS À TRIBUTAÇÃO EXCLUSIVA ===
    val decimoTerceiroSalarioTribExclusiva: BigDecimal?,
    val participacaoLucrosResultados: BigDecimal?,
    val outrosRendimentosTribExclusiva: BigDecimal?,

    // === INFORMAÇÕES COMPLEMENTARES ===
    val pensaoAlimenticia: BigDecimal?,
    val previdenciaComplementar: BigDecimal?,
    val planoSaude: BigDecimal?,
    val dependentes: List<DependenteInfo>?
)

data class DependenteInfo(
    val nome: String,
    val cpf: String,
    val dataNascimento: String?,
    val relacao: String?
)
```

#### Por que Gemini AI como extrator principal?

Informes de rendimentos variam **enormemente** de formato entre bancos, empresas e órgãos públicos:

- **Bancos** (Itaú, Bradesco, BB) — Cada um tem layout próprio
- **Empresas** — Usam sistemas diferentes (ADP, TOTVS, SAP)
- **Governo** — Formato específico por órgão

A IA consegue interpretar layouts diferentes **sem precisar de templates fixos**, analisando o conteúdo semântico do documento.

---

### 5.2. Fase 2 — API de Orquestração

#### Objetivo

Endpoints REST para o frontend e WebSocket para comunicação com o agente local.

#### Pacote

`br.com.verticelabs.pdfprocessor.interfaces.irpfautomation`

#### Endpoints REST (Frontend → Backend)

```
POST   /api/irpf-automation/upload-and-extract   → Upload PDF + extração via IA
POST   /api/irpf-automation/validate             → Validar dados extraídos
POST   /api/irpf-automation/execute              → Disparar automação no agente local
GET    /api/irpf-automation/status/{sessionId}   → Status da automação em andamento
GET    /api/irpf-automation/agents               → Lista agentes locais conectados
GET    /api/irpf-automation/history               → Histórico de automações
```

#### WebSocket (Backend ↔ Agente Local)

```
WSS    /ws/irpf-agent    → Canal de comunicação com agente local
```

#### Protocolo de Mensagens WebSocket

```kotlin
// === Backend → Agente ===

sealed class AgentCommand {
    data class OpenProgram(
        val year: Int,
        val programPath: String?    // null = auto-detect
    ) : AgentCommand()

    data class CreateDeclaration(
        val cpf: String,
        val nome: String
    ) : AgentCommand()

    data class FillField(
        val section: String,        // ex: "rendimentos_tributaveis_pj"
        val field: String,          // ex: "cnpj_fonte_pagadora"
        val value: String,
        val fieldType: FieldType    // TEXT, CURRENCY, CPF, CNPJ, DATE
    ) : AgentCommand()

    data class NavigateToSection(
        val section: String
    ) : AgentCommand()

    data object SaveDeclaration : AgentCommand()
    data object TakeScreenshot : AgentCommand()
    data object Ping : AgentCommand()
}

// === Agente → Backend ===

sealed class AgentEvent {
    data class Connected(
        val agentId: String,
        val machineName: String,
        val installedPrograms: List<InstalledProgram>
    ) : AgentEvent()

    data class ProgramOpened(
        val year: Int,
        val screenshot: String?     // base64
    ) : AgentEvent()

    data class FieldFilled(
        val field: String,
        val success: Boolean,
        val progress: Int,          // 0-100
        val screenshot: String?
    ) : AgentEvent()

    data class StatusUpdate(
        val status: AutomationStatus,
        val message: String,
        val screenshot: String?
    ) : AgentEvent()

    data class Error(
        val code: String,
        val message: String,
        val screenshot: String?
    ) : AgentEvent()

    data object Pong : AgentEvent()
}

data class InstalledProgram(
    val year: Int,
    val path: String,
    val version: String?
)

enum class FieldType { TEXT, CURRENCY, CPF, CNPJ, DATE }

enum class AutomationStatus {
    IDLE, OPENING_PROGRAM, PROGRAM_READY, CREATING_DECLARATION,
    NAVIGATING, FILLING, SAVING, COMPLETED, ERROR
}
```

---

## 6. PARTE 2 — Agente Local (Desktop do Usuário)

Aplicação **Kotlin leve** que o usuário instala e executa na sua máquina. É o "braço" do sistema — faz o trabalho manual de abrir o programa e preencher campos.

### O que é o Agente Local?

- Um **JAR executável** (ou instalador Windows `.exe` via jpackage)
- Fica como **ícone na bandeja do sistema** (system tray)
- Conecta-se ao backend via **WebSocket seguro (WSS)**
- Recebe comandos e executa automação no desktop
- Envia status e screenshots de volta

```
┌─────────────────────────────────────────────────┐
│              AGENTE LOCAL                        │
│                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────────┐ │
│  │ WebSocket│──→│Orquestrador│──→│ RPA Engine  │ │
│  │ Client   │←──│ Local     │←──│(Access Bridge│ │
│  │          │   │           │   │ ou Robot)    │ │
│  └──────────┘   └──────────┘   └──────────────┘ │
│       ▲              │               │           │
│       │              ▼               ▼           │
│  ┌────┴────┐   ┌──────────┐   ┌──────────────┐  │
│  │ Backend │   │ Program  │   │ Screenshot   │  │
│  │ (Cloud  │   │ Locator  │   │ Capture      │  │
│  │  Run)   │   │          │   │              │  │
│  └─────────┘   └──────────┘   └──────────────┘  │
│                                                  │
│  [System Tray Icon: "IRPF Agent - Conectado"]    │
└─────────────────────────────────────────────────┘
```

---

### 6.1. Fase 3 — Agente Local e Comunicação

#### Objetivo

Criar o agente local que se conecta ao backend e gerencia a comunicação.

#### Pacote

`br.com.verticelabs.pdfprocessor.agent`

#### Componentes

| Componente | Tipo | Descrição |
|---|---|---|
| `IrpfAgentApplication` | `fun main()` | Entry point do agente (aplicação desktop) |
| `AgentWebSocketClient` | `class` | Cliente WebSocket que conecta ao backend |
| `AgentOrchestrator` | `class` | Recebe comandos do backend e despacha para o engine RPA |
| `AgentTrayIcon` | `class` | Ícone na bandeja do sistema (status, configurações) |
| `AgentConfig` | `data class` | Configuração local (URL backend, token, etc.) |

#### Configuração do Agente — `agent-config.yml`

```yaml
agent:
  backend-url: "wss://pdfprocessor-api-xxxxx.run.app/ws/irpf-agent"
  auth-token: "${AGENT_TOKEN}"
  machine-name: "${COMPUTERNAME}"
  auto-connect: true
  reconnect-interval-seconds: 5
  screenshot:
    enabled: true
    quality: 80
    max-width: 1280
```

#### Autenticação do Agente

O agente precisa se autenticar no backend para evitar conexões não autorizadas:

1. Usuário faz login no sistema web
2. Sistema gera um **token temporário** para o agente
3. Usuário configura o token no agente local (uma vez)
4. Agente usa o token para conectar via WebSocket

---

### 6.2. Fase 4 — Mapeamento dos Programas da Receita Federal

#### Objetivo

Localizar automaticamente os programas IRPF instalados na máquina do usuário.

#### Pacote

`br.com.verticelabs.pdfprocessor.agent.programs`

#### Componentes

| Componente | Tipo | Descrição |
|---|---|---|
| `IrpfProgramLocator` | `class` | Localiza programas nos caminhos padrão da Receita |
| `IrpfProgramLauncher` | `class` | Inicia o programa via `ProcessBuilder` |
| `InstalledProgram` | `data class` | Informações de um programa instalado |

#### Localizador Automático

O `IrpfProgramLocator` buscará programas nos caminhos padrão de instalação da Receita Federal:

```
C:\Arquivos de Programas RFB\IRPFxxxx\
C:\Program Files\Programas RFB\IRPFxxxx\
C:\Program Files (x86)\Programas RFB\IRPFxxxx\
%USERPROFILE%\Programas RFB\IRPFxxxx\
```

Ao conectar, o agente envia a lista de programas instalados para o backend, que repassa ao frontend para o usuário selecionar.

---

### 6.3. Fase 5 — Automação da Interface (RPA)

#### Objetivo

Automatizar a interação com o programa IRPF da Receita Federal (aplicação Java Swing).

#### Pacote

`br.com.verticelabs.pdfprocessor.agent.rpa`

#### Componentes

| Componente | Tipo | Descrição |
|---|---|---|
| `IrpfAutomationEngine` | `interface` | Contrato para automação da UI |
| `AccessBridgeEngine` | `class` | Implementação via Java Access Bridge (recomendada) |
| `RobotEngine` | `class` | Implementação via `java.awt.Robot` (fallback) |
| `IrpfFieldMapper` | `class` | Mapeamento de campos do informe → campos do programa por ano |
| `IrpfNavigator` | `class` | Navegação entre telas/abas do programa |
| `IrpfFormFiller` | `class` | Preenchimento automático dos formulários |
| `ScreenCapture` | `class` | Captura de tela para enviar ao backend |

#### Mapeamento de Campos por Ano

```kotlin
data class IrpfFieldMapping(
    val ano: Int,
    val campos: Map<CampoInforme, CampoIrpf>
)

data class CampoIrpf(
    val section: String,        // ex: "Rendimentos Tributáveis Recebidos de PJ"
    val fieldName: String,      // ex: "Rendimentos Recebidos"
    val fieldType: FieldType,   // TEXT, CURRENCY, CPF, CNPJ, DATE
    val accessibleName: String? // Nome do componente via Access Bridge (se disponível)
)

enum class CampoInforme {
    CNPJ_FONTE_PAGADORA,
    NOME_FONTE_PAGADORA,
    TOTAL_RENDIMENTOS_TRIBUTAVEIS,
    CONTRIBUICAO_PREVIDENCIARIA,
    IMPOSTO_RETIDO_FONTE,
    DECIMO_TERCEIRO_SALARIO,
    IRSF_DECIMO_TERCEIRO,
    // ... demais campos
}
```

---

## 7. Fluxo Completo de Ponta a Ponta

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          FLUXO COMPLETO DA AUTOMAÇÃO                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  FRONTEND (Navegador)          BACKEND (Cloud Run)         AGENTE LOCAL (Desktop)│
│  ═══════════════════          ════════════════════         ═══════════════════════│
│                                                                                 │
│  1. Upload PDF ──────────────→ 2. Recebe PDF                                    │
│                                3. Extrai texto (PDFBox)                          │
│                                4. Envia p/ Gemini AI                             │
│                                5. Retorna dados estruturados                     │
│  6. Exibe dados ◄──────────── 7. Retorna InformeRendimentosData                 │
│                                                                                 │
│  8. Usuário revisa dados                                                        │
│  9. Usuário confirma ───────→ 10. Valida dados                                  │
│  10. Seleciona ano (2016-2025)                                                  │
│                                                                                 │
│  11. Clica "Executar" ──────→ 12. Cria sessão de automação                      │
│                                13. Envia comando via WebSocket ──→ 14. Recebe    │
│                                                                     comando     │
│                                                                   15. Localiza  │
│                                                                     programa    │
│                                                                   16. Abre IRPF │
│                                                                   17. Aguarda   │
│                                                                     carregar    │
│  18. Vê "Programa ◄────────── 19. Repassa status ◄──────────────── 20. Envia    │
│      aberto"                                                        status +    │
│                                                                     screenshot  │
│                                                                                 │
│                                21. Envia comandos ───────────────→ 22. Navega   │
│                                    de preenchimento                  para aba    │
│                                    campo por campo                 23. Preenche  │
│                                                                      campo      │
│  24. Vê progresso ◄────────── 25. Repassa progresso ◄────────────── 26. Envia   │
│      ao vivo (3/15)                                                  progresso  │
│      + screenshots                                                              │
│                                                                                 │
│                                ... (repete 21-26 para cada campo) ...           │
│                                                                                 │
│                                27. Envia "salvar" ───────────────→ 28. Salva    │
│                                                                      declaração │
│  29. Vê "Concluído" ◄──────── 30. Repassa resultado ◄────────────── 31. Envia  │
│      + screenshots finais                                            resultado  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Decisão Técnica: Access Bridge vs Robot

### Contexto atualizado

Agora que sabemos que a automação roda **localmente no desktop do usuário** (e não no Cloud Run), a pergunta é: qual tecnologia usar **no agente local**?

### Cenário de uso

- O usuário **aciona a automação conscientemente** (não é background)
- O usuário **sabe que a tela será usada** pelo agente
- O agente roda no **Windows** do usuário
- Os programas IRPF são **Java Swing**

### Comparação para este cenário

| Critério | Access Bridge | java.awt.Robot |
|---|---|---|
| **Confiabilidade** | ⭐⭐⭐⭐⭐ Alta — acessa componentes por nome | ⭐⭐ Baixa — depende de coordenadas |
| **Simplicidade** | ⭐⭐ Complexa — precisa JNA + Access Bridge | ⭐⭐⭐⭐⭐ Simples — nativo do JDK |
| **Funciona em background?** | ✅ Sim — acessa componentes sem foco | ❌ Não — precisa da janela visível |
| **Depende de resolução?** | ❌ Não | ✅ Sim — coordenadas mudam |
| **Depende de DPI/escala?** | ❌ Não | ✅ Sim — 100% vs 125% vs 150% |
| **Setup do usuário** | Habilitar Access Bridge no Windows | Nenhum |
| **Velocidade** | ⭐⭐⭐⭐ Rápida | ⭐⭐⭐ Média (precisa de delays) |
| **Depende do programa ser Swing?** | ✅ Sim — só funciona com Java Swing | ❌ Não — funciona com qualquer app |

### Recomendação: `java.awt.Robot` para começar, com migração futura

**Justificativa:**

1. **POC rápida** — Robot é nativo, sem dependências, implementação em horas
2. **O usuário já sabe que a tela será usada** — O fato de ser bloqueante não é problema
3. **Validar conceito primeiro** — Antes de investir na complexidade do Access Bridge, precisamos validar que o fluxo todo funciona
4. **Migração gradual** — A interface `IrpfAutomationEngine` permite trocar a implementação sem mudar o resto

**Plano de evolução:**

```
Sprint 1-3: java.awt.Robot (POC + validação do fluxo)
Sprint 4+:  Access Bridge (robustez + independência de resolução)
```

> **Nota importante sobre Robot:** Para mitigar o problema de coordenadas, usaremos **navegação por Tab/atalhos de teclado** em vez de cliques por coordenadas. Os programas IRPF suportam Tab entre campos e atalhos de menu, o que torna o Robot muito mais confiável.

---

## 9. Estrutura de Pacotes Final (Kotlin)

### Backend (Cloud Run) — `src/main/kotlin/`

```
src/main/kotlin/br/com/verticelabs/pdfprocessor/
│
├── PdfProcessorApplication.kt                           # (existente)
│
├── application/
│   └── irpfautomation/
│       ├── IrpfAutomationUseCase.kt                     # Caso de uso: orquestra o fluxo
│       ├── IrpfAutomationSessionManager.kt              # Gerencia sessões de automação
│       ├── IrpfAutomationStatus.kt                      # Status da automação (sealed class)
│       ├── IrpfAutomationResult.kt                      # Resultado da automação
│       │
│       └── extraction/
│           ├── InformeRendimentosData.kt                 # Data class do informe
│           ├── DependenteInfo.kt                         # Data class de dependente
│           ├── InformeRendimentosExtractor.kt            # Interface de extração
│           ├── GeminiInformeExtractor.kt                 # Extração via Gemini AI
│           └── PdfBoxInformeExtractor.kt                 # Extração via PDFBox (fallback)
│
├── infrastructure/
│   ├── logging/
│   │   └── MongoAppender.kt                             # (existente)
│   │
│   └── irpfautomation/
│       └── websocket/
│           ├── AgentWebSocketHandler.kt                  # Handler do WebSocket no backend
│           ├── AgentCommand.kt                           # Comandos enviados ao agente
│           ├── AgentEvent.kt                             # Eventos recebidos do agente
│           └── AgentSessionRegistry.kt                   # Registro de agentes conectados
│
└── interfaces/
    └── irpfautomation/
        ├── IrpfAutomationController.kt                  # Controller REST
        └── dto/
            ├── IrpfAutomationRequest.kt                 # DTO de entrada
            ├── IrpfAutomationResponse.kt                # DTO de saída
            └── IrpfExtractionResponse.kt                # DTO de extração
```

### Agente Local (Desktop do Usuário) — `src/main/kotlin/` (módulo separado ou mesmo JAR)

```
src/main/kotlin/br/com/verticelabs/pdfprocessor/agent/
│
├── IrpfAgentApplication.kt                              # Entry point do agente desktop
├── AgentConfig.kt                                       # Configuração local
│
├── communication/
│   ├── AgentWebSocketClient.kt                          # Cliente WebSocket
│   └── AgentOrchestrator.kt                             # Despacha comandos → engine RPA
│
├── programs/
│   ├── IrpfProgramLocator.kt                            # Localiza programas instalados
│   ├── IrpfProgramLauncher.kt                           # Abre programa via ProcessBuilder
│   └── InstalledProgram.kt                              # Data class de programa instalado
│
├── rpa/
│   ├── IrpfAutomationEngine.kt                          # Interface de automação
│   ├── RobotEngine.kt                                   # Implementação via java.awt.Robot
│   ├── AccessBridgeEngine.kt                            # (futuro) Implementação via Access Bridge
│   ├── IrpfFieldMapper.kt                               # Mapeamento de campos por ano
│   ├── IrpfNavigator.kt                                 # Navegação entre telas/abas
│   ├── IrpfFormFiller.kt                                # Preenchimento automático
│   └── ScreenCapture.kt                                 # Captura de tela
│
└── ui/
    └── AgentTrayIcon.kt                                 # Ícone na bandeja do sistema
```

---

## 10. Dependências Adicionais

### Backend — `build.gradle.kts`

```kotlin
// WebSocket já está incluído no Spring WebFlux (existente)
// Nenhuma dependência nova necessária no backend
```

### Agente Local — `build.gradle.kts` (novo módulo ou build separado)

```kotlin
dependencies {
    // WebSocket client
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // Configuração
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")

    // JNA para Access Bridge (futuro)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
```

---

## 11. Riscos e Pontos de Atenção

### Riscos Técnicos

| Risco | Impacto | Mitigação |
|---|---|---|
| **Interface do IRPF muda a cada ano** | Alto | Mapeamento de campos separado por ano; testes por versão |
| **Navegação por Tab pode mudar entre versões** | Alto | Mapeamento de ordem de Tab por ano; testes |
| **Formatos variados de informes** | Médio | Gemini AI interpreta semanticamente; fallback PDFBox |
| **WebSocket desconecta no meio da automação** | Alto | Reconexão automática; estado da sessão persistido; retry |
| **Tempo de carga do programa IRPF** | Baixo | Waits inteligentes com polling de janela; timeout configurável |
| **Resolução/DPI diferente (se Robot)** | Médio | Usar Tab/teclado em vez de coordenadas de mouse |
| **Programas antigos (2016-2018) + Java 21** | Médio | Testar compatibilidade; talvez usar JRE do próprio programa |
| **Firewall/proxy bloqueia WebSocket** | Médio | Fallback para HTTPS polling; documentação de portas |

### Pontos de Atenção

1. **Agente precisa ser instalado** — O usuário precisa baixar e rodar o agente local (uma vez)
2. **Bloqueante com Robot** — Mouse/teclado ficam ocupados durante a automação
3. **Validação é essencial** — Dados mal extraídos geram declaração incorreta (risco fiscal)
4. **Segurança** — Dados de IR são sensíveis; WebSocket deve ser WSS (TLS); token de autenticação
5. **Cada ano tem peculiaridades** — Campos, abas e regras mudam entre versões do programa
6. **Cross-platform** — O agente é **apenas Windows** (programas IRPF são Windows)

---

## 12. Ordem de Implementação Sugerida

### Sprint 1 — Extração de Dados no Backend (Fase 1)

**Objetivo:** Extrair dados de informes de rendimentos via Gemini AI.

- [ ] Criar `InformeRendimentosData` e `DependenteInfo`
- [ ] Criar `InformeRendimentosExtractor` (interface)
- [ ] Implementar `GeminiInformeExtractor`
- [ ] Implementar `PdfBoxInformeExtractor` (fallback)
- [ ] Criar endpoint REST `POST /api/irpf-automation/upload-and-extract`
- [ ] Testar com informes de diferentes bancos/empresas

**Entregável:** Upload de PDF → dados estruturados extraídos via IA.

---

### Sprint 2 — WebSocket e Agente Local Base (Fases 2 e 3)

**Objetivo:** Estabelecer comunicação entre backend e agente local.

- [ ] Implementar `AgentWebSocketHandler` no backend
- [ ] Implementar protocolo de mensagens (`AgentCommand`, `AgentEvent`)
- [ ] Criar `IrpfAgentApplication` (entry point do agente)
- [ ] Implementar `AgentWebSocketClient`
- [ ] Implementar `AgentTrayIcon` (ícone na bandeja)
- [ ] Implementar autenticação via token
- [ ] Testar conexão backend ↔ agente

**Entregável:** Agente local conectado ao backend, trocando mensagens.

---

### Sprint 3 — Localização e Abertura de Programas (Fase 4)

**Objetivo:** Agente localiza e abre programas IRPF.

- [ ] Implementar `IrpfProgramLocator`
- [ ] Implementar `IrpfProgramLauncher`
- [ ] Ao conectar, agente envia lista de programas instalados
- [ ] Backend recebe e disponibiliza no frontend
- [ ] Testar abertura de programas de diferentes anos

**Entregável:** Agente abre o programa IRPF correto via comando do backend.

---

### Sprint 4 — Automação RPA com Robot (Fase 5 - POC)

**Objetivo:** Preencher campos no IRPF automaticamente.

- [ ] Implementar `IrpfAutomationEngine` (interface)
- [ ] Implementar `RobotEngine` (POC com java.awt.Robot)
- [ ] Implementar `ScreenCapture`
- [ ] Criar mapeamento de campos para **1 ano** (ex: 2024)
- [ ] Implementar navegação via Tab/atalhos de teclado
- [ ] Testar preenchimento no programa IRPF 2024
- [ ] Enviar screenshots e status ao vivo para o backend

**Entregável:** Preenchimento automático do IRPF 2024 com status em tempo real.

---

### Sprint 5 — Orquestração e Fluxo Completo (todas as fases)

**Objetivo:** Fluxo completo de ponta a ponta.

- [ ] Implementar `IrpfAutomationUseCase` (orquestrador no backend)
- [ ] Implementar `IrpfAutomationSessionManager`
- [ ] Implementar `AgentOrchestrator` (orquestrador no agente)
- [ ] Endpoint `POST /api/irpf-automation/execute`
- [ ] Endpoint `GET /api/irpf-automation/status/{sessionId}`
- [ ] Testar fluxo completo: Upload → Extração → Revisão → Automação → Resultado
- [ ] Expandir mapeamento para mais anos (2020-2025 primeiro)

**Entregável:** Automação completa, do upload do PDF à declaração preenchida.

---

### Sprint 6 — Robustez e Anos Restantes

**Objetivo:** Cobertura completa e tratamento de erros.

- [ ] Expandir mapeamento para todos os anos (2016-2019)
- [ ] Reconexão automática do WebSocket
- [ ] Retry de comandos que falharam
- [ ] Tratamento de erros robusto (programa travou, campo não encontrado, etc.)
- [ ] Logs e auditoria de cada automação
- [ ] (Futuro) Implementar `AccessBridgeEngine` para maior robustez

**Entregável:** Solução completa e robusta para todos os anos 2016-2025.

---

## Glossário

| Termo | Significado |
|---|---|
| **IRPF** | Imposto de Renda Pessoa Física |
| **Informe de Rendimentos** | Documento emitido por empresas/bancos com dados de rendimentos de uma pessoa |
| **Ano-calendário** | Ano em que os rendimentos foram recebidos |
| **Ano-exercício** | Ano seguinte ao ano-calendário (quando a declaração é feita) |
| **IRSF / IRRF** | Imposto de Renda Retido na Fonte |
| **RPA** | Robotic Process Automation — automação de processos via interface |
| **Agente Local** | Aplicação leve instalada no computador do usuário para executar a automação |
| **Java Access Bridge** | API do Windows para acessar componentes Swing programaticamente |
| **JNA** | Java Native Access — biblioteca para chamar APIs nativas do SO |
| **WSS** | WebSocket Secure — WebSocket com criptografia TLS |
| **System Tray** | Bandeja do sistema Windows (área de ícones próxima ao relógio) |
