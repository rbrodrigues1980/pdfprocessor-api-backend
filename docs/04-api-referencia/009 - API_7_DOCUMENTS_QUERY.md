# API_7_DOCUMENTS_QUERY.md
# 📘 API 7 — Consulta e Gestão de Documentos PDF Enviados
Esta API fornece acesso a todos os documentos enviados para processamento:

- documentos PENDING, PROCESSING, PROCESSED ou ERROR  
- histórico por CPF  
- detalhes do documento  
- páginas identificadas (CAIXA/FUNCEF)  
- resumo das rubricas extraídas  
- estatísticas de processamento  

É a base para o painel administrativo monitorar o progresso.

---

# 1. OBJETIVO
Permitir:

- visualizar documentos enviados por CPF  
- checar status do processamento  
- ver resumo de anos e rubricas por documento  
- localizar erros  
- buscar documentos por filtros  
- reprocessar quando necessário  

---

# 2. MODELO COMPLETO DO DOCUMENTO (payroll_documents)

```json
{
  "id": "doc123",
  "cpf": "12449709568",
  "tipo": "CAIXA | FUNCEF | MISTO",
  "status": "PENDING | PROCESSING | PROCESSED | ERROR",
  "ano": 2017,
  "dataUpload": "2024-03-10T12:08:00Z",
  "paginas": [
    { "page": 1, "origem": "CAIXA" },
    { "page": 2, "origem": "CAIXA" },
    { "page": 3, "origem": "FUNCEF" }
  ],
  "resumoRubricas": [
    { "codigo": "4482", "quantidade": 3, "total": 1209.57 },
    { "codigo": "3430", "quantidade": 2, "total": 2753.94 }
  ],
  "entriesCount": 148,
  "mensagensErro": ["Página 3 sem referência FUNCEF"]
}
```

---

# 3. ENDPOINTS

# 3.1 ▶️ GET /api/v1/documents/{id}

Retorna **detalhes completos** de um documento.

### 📤 Response

```json
{
  "id": "doc123",
  "cpf": "12449709568",
  "status": "PROCESSED",
  "tipo": "MISTO",
  "ano": 2017,
  "entriesCount": 148
}
```

---

# 3.2 ▶️ GET /api/v1/persons/{cpf}/documents

Lista todos os documentos de um CPF.

### Resultado:

```json
{
  "cpf": "12449709568",
  "documentos": [
    { "id": "doc1", "ano": 2016, "status": "PROCESSED" },
    { "id": "doc2", "ano": 2017, "status": "PROCESSED" },
    { "id": "doc3", "ano": 2018, "status": "ERROR" }
  ]
}
```

---

# 3.3 ▶️ GET /api/v1/documents

Consulta geral de documentos com filtros.

### Query Params
| Param | Exemplo | Descrição |
|--------|---------|-----------|
| cpf | 12449709568 | filtra por CPF |
| ano | 2017 | filtra por ano |
| status | PROCESSED | filtra por status |
| tipo | CAIXA | filtro por origem |
| minEntries | 10 | mínimo de entries |
| maxEntries | 200 | máximo de entries |

### Exemplo:

```
GET /api/v1/documents?cpf=12449709568&status=ERROR
```

---

# 3.4 ▶️ GET /api/v1/documents/{id}/pages

Retorna identificação das páginas:

```json
{
  "documentId": "doc123",
  "pages": [
    { "page": 1, "origem": "CAIXA" },
    { "page": 2, "origem": "CAIXA" },
    { "page": 3, "origem": "FUNCEF" }
  ]
}
```

---

# 3.5 ▶️ GET /api/v1/documents/{id}/summary

Retorna resumo das rubricas e estatísticas.

```json
{
  "documentId": "doc123",
  "entriesCount": 148,
  "rubricasResumo": [
    { "codigo": "4482", "quantidade": 3, "total": 1209.57 },
    { "codigo": "3430", "quantidade": 2, "total": 2753.94 }
  ]
}
```

---

# 3.6 ▶️ POST /api/v1/documents/{id}/reprocess

Reprocessa um documento já enviado.

Retorno:

```json
{
  "documentId": "doc123",
  "status": "PROCESSING",
  "message": "Reprocessamento iniciado"
}
```

---

# 4. REGRAS DE NEGÓCIO

### ✔ Documento só pode ser reprocessado se:
- status = ERROR  
- ou status = PROCESSED (reprocessamento manual)  

### ✔ Status automáticos:
- PENDING  
- PROCESSING  
- PROCESSED  
- ERROR  

### ✔ Ano é calculado automaticamente pelo extrator  
Meses/anos não são inseridos manualmente.

### ✔ Resumo de rubricas é calculado após PROCESSAMENTO  

---

# 5. ERROS POSSÍVEIS

| Erro | Status | Descrição |
|--------|--------|------------|
| DOCUMENT_NOT_FOUND | 404 | ID inexistente |
| PERSON_NOT_FOUND | 404 | CPF sem documentos |
| INVALID_STATUS_TRANSITION | 409 | Não pode reprocessar PENDING |
| FILTER_ERROR | 400 | Filtro inválido |

---

# 6. ORDEM DE IMPLEMENTAÇÃO

1. Criar `PayrollDocumentRepository`
2. Criar `DocumentQueryService`
3. Criar builder de filtros dinâmicos
4. Criar endpoints detalhados
5. Criar métodos de resumo
6. Conectar com API 3 (processamento)
7. Criar testes unitários
8. Criar testes usando PDFs reais

---

# 7. CLASSES NECESSÁRIAS

- `PayrollDocumentRepository`
- `DocumentQueryService`
- `DocumentQueryController`
- `DocumentPageSummary`
- `DocumentRubricaSummary`
- `DocumentFilterBuilder`
- `DocumentDTO`
- `DocumentSummaryDTO`

---

Fim da documentação da API 7 — Consulta e Gestão de Documentos.
