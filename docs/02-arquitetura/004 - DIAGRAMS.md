# DIAGRAMS.md — Diagramas do Sistema (CAIXA + FUNCEF Extractor)

Este documento reúne os principais diagramas do sistema:  
Fluxo de processamento, sequência, estados do documento, e fluxo completo do PDF até o Excel.

Todos os diagramas estão no padrão **Mermaid**, compatível com GitHub, GitLab e editores Markdown modernos.

---

# 🧭 1. Diagrama de Fluxo Geral do Sistema

```mermaid
flowchart LR
    U[Usuário / Admin] -->|Upload PDF| API[API REST]
    API --> DB[(MongoDB)]
    API --> PROC[Service de Processamento PDF]

    PROC --> DETECT[Detector de Tipo de Documento<br>(CAIXA/FUNCEF/MISTO)]
    DETECT --> META[Extrator de Metadados]
    META --> PARSE[Parser de Rubricas]
    PARSE --> ENTRIES[Criação de PayrollEntries]

    ENTRIES --> DB

    DB --> CONS[Consolidador de Dados]
    CONS --> EXCEL[Gerador Excel]

    API --> FE[Frontend Admin (React)]
```

---

# 🔄 2. Diagrama de Sequência — Upload e Processamento

```mermaid
sequenceDiagram
    participant FE as Frontend Admin
    participant API as Backend API
    participant DB as MongoDB
    participant P as PDF Processor

    FE->>API: POST /upload (PDF + CPF)
    API->>DB: Salva documento (status=PENDING)
    API-->>FE: Retorna documentId

    FE->>API: POST /documents/{id}/process
    API->>DB: Atualiza status → PROCESSING
    API->>P: Iniciar processamento

    P->>P: Detectar tipo (CAIXA/FUNCEF/MISTO)
    P->>P: Extrair metadados
    P->>P: Ler páginas
    P->>P: Extrair rubricas
    P->>DB: Salvar PayrollEntries

    P->>DB: Atualiza documento status=PROCESSED
    API-->>FE: Retorna status final
```

---

# 🧬 3. Diagrama de Estados — Documento PDF

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING: Disparo manual ou automático
    PROCESSING --> PROCESSED: Sucesso
    PROCESSING --> ERROR: Falha ao processar
    ERROR --> PROCESSING: Retry manual
```

---

# 📦 4. Diagrama das Collections MongoDB

```mermaid
erDiagram
    PERSON {
        string cpf PK
        string nome
        string[] documentos
    }

    PAYROLL_DOCUMENT {
        string id PK
        string pessoaId FK
        string tipo
        int ano
        int paginas
        string status
    }

    PAYROLL_ENTRY {
        string id PK
        string documentoId FK
        int codigo
        string descricao
        int mes
        int ano
        double valor
        string origem
        int pagina
    }

    RUBRICA {
        int codigo PK
        string descricao
        string categoria
        bool ativo
    }

    PERSON ||--o{ PAYROLL_DOCUMENT : possui
    PAYROLL_DOCUMENT ||--o{ PAYROLL_ENTRY : gera
    RUBRICA ||--o{ PAYROLL_ENTRY : classifica
```

---

# 🧩 5. Diagrama do Processo de Extração (Pipeline Interno)

```mermaid
flowchart TD
    A[PDF Recebido] --> B[Detectar tipo<br>Caixa/Funcef/Misto]
    B --> C[Extrair metadados<br>Nome, CPF, Ano, Mes]
    C --> D[Ler páginas]
    D --> E[Determinar origem por página]
    E --> F[Extrair rubricas via Regex]
    F --> G[Normalizar valores<br>(decimal, datas)]
    G --> H[Salvar PayrollEntry no Mongo]
    H --> I[Finalizar status PROCESSED]
```

---

# 📥 6. Fluxo do Excel Consolidado

```mermaid
flowchart LR
    DB[(MongoDB)] --> AGGR[Agregação por Ano/Mês]
    AGGR --> MATRIX[Matriz consolidada<br>(rubricas x meses)]
    MATRIX --> TOTAL[Tabela de totais<br>(ano e geral)]
    TOTAL --> POI[Apache POI]
    POI --> FILE[Excel (.xlsx)]
    FILE --> USER[Download]
```

---

# ✔ Arquivo Completo Gerado

Este arquivo contém todos os diagramas essenciais para documentação técnica, revisões de arquitetura, onboarding e integração do time.

