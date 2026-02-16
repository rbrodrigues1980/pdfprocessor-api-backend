# API_4_ENTRIES.md
# 📘 API 4 — Consultar Entradas Extraídas (Payroll Entries)

Esta API fornece acesso a todas as rubricas extraídas dos PDFs já processados.  
É usada pelo frontend admin e pelo módulo de consolidação.

**Status**: ✅ Implementada e funcional

---

# 1. OBJETIVO DA API

A API 4 permite:
- ✅ Visualizar todas as linhas extraídas de um documento
- ✅ Buscar por rubrica específica
- ✅ Filtrar por ano, mês, origem (CAIXA/FUNCEF)
- ✅ Integrar com consolidação anual/mensal
- ✅ Servir como base para reconstrução dos valores exibidos no Excel
- ✅ Buscar entries de uma pessoa (todos os documentos)
- ✅ Paginação para documentos grandes
- ✅ Busca global com múltiplos filtros combinados

---

# 2. MODELO DE DADOS

## 2.1 Estrutura da PayrollEntry

Cada entry representa uma rubrica extraída de uma página do PDF:

```json
{
  "_id": "692c2633df4f66028024ad9a",
  "documentoId": "692c261bdf4f66028024ad7a",
  "rubricaCodigo": "4412",
  "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
  "referencia": "2017-03",
  "valor": 101.26,
  "origem": "CAIXA",
  "pagina": 3,
  "_class": "br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry"
}
```

## 2.2 Campos da EntryResponse (resposta da API)

| Campo | Tipo | Obrigatório | Descrição | Exemplo |
|-------|------|-------------|-----------|---------|
| `id` | String | ✔ | ID único da entry (MongoDB ObjectId) | `"692c2633df4f66028024ad9a"` |
| `documentId` | String | ✔ | ID do documento de origem | `"692c261bdf4f66028024ad7a"` |
| `rubricaCodigo` | String | ✔ | Código da rubrica (3-4 dígitos) | `"4412"`, `"3430"` |
| `rubricaDescricao` | String | ✔ | Descrição extraída do PDF | `"FUNCEF CONTR. EQUACIONAMENTO1 SALDADO"` |
| `referencia` | String | ✔ | Mês/ano no formato YYYY-MM | `"2017-03"`, `"2018-01"` |
| `valor` | Double | ✔ | Valor numérico normalizado | `101.26`, `885.47` |
| `origem` | String | ✔ | Origem da rubrica: `"CAIXA"` ou `"FUNCEF"` | `"CAIXA"` |
| `pagina` | Integer | ❌ | Número da página onde foi extraída (1-indexed) | `3`, `8` |

---

# 3. ENDPOINTS DETALHADOS

## 3.1 ▶️ GET /api/v1/documents/{id}/entries

Retorna **todas as entries** extraídas de um documento específico.

### 📥 Request

**Path Parameters:**
- `id` (String, obrigatório) - ID do documento na coleção `payroll_documents`

**Exemplo:**
```
GET /api/v1/documents/692c261bdf4f66028024ad7a/entries
```

### 📤 Response

**Status 200 OK** - Entries encontradas:
```json
[
  {
    "id": "692c2633df4f66028024ad9a",
    "documentId": "692c261bdf4f66028024ad7a",
    "rubricaCodigo": "4412",
    "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
    "referencia": "2017-03",
    "valor": 101.26,
    "origem": "CAIXA",
    "pagina": 3
  },
  {
    "id": "692c2633df4f66028024ad8a",
    "documentId": "692c261bdf4f66028024ad7a",
    "rubricaCodigo": "3430",
    "rubricaDescricao": "REP. CONTRIBUIÇÃO EXTRAORDINARIA 2014",
    "referencia": "2017-08",
    "valor": 43.61,
    "origem": "FUNCEF",
    "pagina": 8
  }
]
```

**Status 204 No Content** - Documento existe mas não possui entries:
```json
[]
```

**Status 404 Not Found** - Documento não encontrado:
```json
null
```

### 💻 Exemplo com cURL

```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/documents/692c261bdf4f66028024ad7a/entries' \
  -H 'accept: */*'
```

### 📝 Casos de Uso

- **Visualizar todas as rubricas de um documento processado**
- **Validar se o processamento extraiu todas as rubricas esperadas**
- **Exportar dados de um documento específico**

---

## 3.2 ▶️ GET /api/v1/documents/{id}/entries/paged

Retorna entries **paginadas** de um documento (recomendado para documentos grandes).

### 📥 Request

**Path Parameters:**
- `id` (String, obrigatório) - ID do documento

**Query Parameters (todos opcionais):**
| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `page` | Integer | `0` | Número da página (começa em 0) |
| `size` | Integer | `20` | Quantidade de itens por página |
| `sortBy` | String | `"referencia"` | Campo para ordenar (`referencia`, `valor`, `rubricaCodigo`, `pagina`) |
| `sortDirection` | String | `"asc"` | Direção: `"asc"` ou `"desc"` |

**Exemplo:**
```
GET /api/v1/documents/692c261bdf4f66028024ad7a/entries/paged?page=0&size=20&sortBy=referencia&sortDirection=asc
```

### 📤 Response

**Status 200 OK:**
```json
{
  "content": [
    {
      "id": "692c2633df4f66028024ad9a",
      "documentId": "692c261bdf4f66028024ad7a",
      "rubricaCodigo": "4412",
      "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
      "referencia": "2017-03",
      "valor": 101.26,
      "origem": "CAIXA",
      "pagina": 3
    }
    // ... mais entries
  ],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

**Campos da resposta paginada:**
- `content` - Array com as entries da página atual
- `totalElements` - Total de entries no documento
- `totalPages` - Total de páginas disponíveis
- `currentPage` - Página atual (0-indexed)
- `pageSize` - Tamanho da página
- `hasNext` - Se existe próxima página
- `hasPrevious` - Se existe página anterior

### 💻 Exemplos com cURL

**Página básica:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/documents/692c261bdf4f66028024ad7a/entries/paged?page=0&size=20' \
  -H 'accept: */*'
```

**Ordenar por valor (maior para menor):**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/documents/692c261bdf4f66028024ad7a/entries/paged?page=0&size=20&sortBy=valor&sortDirection=desc' \
  -H 'accept: */*'
```

**Segunda página:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/documents/692c261bdf4f66028024ad7a/entries/paged?page=1&size=20' \
  -H 'accept: */*'
```

### 📝 Casos de Uso

- **Documentos com muitas entries (> 100)**
- **Interface web com paginação**
- **Exportação em lotes**
- **Análise de dados grandes sem sobrecarregar a memória**

---

## 3.3 ▶️ GET /api/v1/persons/{cpf}/entries

Retorna **todas as entries** de **todos os documentos** de uma pessoa.

### 📥 Request

**Path Parameters:**
- `cpf` (String, obrigatório) - CPF da pessoa (apenas números, sem formatação)

**Exemplo:**
```
GET /api/v1/persons/12449709568/entries
```

### 📤 Response

**Status 200 OK:**
```json
{
  "cpf": "12449709568",
  "totalEntries": 412,
  "entries": [
    {
      "id": "692c2633df4f66028024ad9a",
      "documentId": "692c261bdf4f66028024ad7a",
      "rubricaCodigo": "4412",
      "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
      "referencia": "2017-03",
      "valor": 101.26,
      "origem": "CAIXA",
      "pagina": 3
    },
    {
      "id": "692c2633df4f66028024ad8a",
      "documentId": "692c261bdf4f66028024ad7a",
      "rubricaCodigo": "3430",
      "rubricaDescricao": "REP. CONTRIBUIÇÃO EXTRAORDINARIA 2014",
      "referencia": "2017-08",
      "valor": 43.61,
      "origem": "FUNCEF",
      "pagina": 8
    }
    // ... todas as entries de todos os documentos dessa pessoa
  ]
}
```

**Status 204 No Content** - Pessoa existe mas não possui entries:
```json
{
  "cpf": "12449709568",
  "totalEntries": 0,
  "entries": []
}
```

**Status 404 Not Found** - Pessoa não encontrada:
```json
null
```

### 💻 Exemplo com cURL

```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/persons/12449709568/entries' \
  -H 'accept: */*'
```

### 📝 Casos de Uso

- **Consolidação anual/mensal de uma pessoa**
- **Visualizar histórico completo de rubricas**
- **Preparar dados para exportação Excel**
- **Análise financeira pessoal**

---

## 3.4 ▶️ GET /api/v1/entries

Endpoint **global** de entries com **filtros dinâmicos**. Permite combinar múltiplos filtros.

### 📥 Request

**Query Parameters (todos opcionais):**

| Parâmetro | Tipo | Descrição | Exemplo |
|-----------|------|-----------|---------|
| `cpf` | String | Filtra por CPF da pessoa | `12449709568` |
| `rubrica` | String | Filtra por código de rubrica | `4412`, `3430` |
| `ano` | Integer | Filtra por ano | `2017`, `2018` |
| `mes` | Integer | Filtra por mês (1-12) | `3`, `8`, `12` |
| `origem` | String | Filtra por origem: `CAIXA` ou `FUNCEF` | `CAIXA` |
| `documentoId` | String | Filtra por documento específico | `692c261bdf4f66028024ad7a` |
| `minValor` | Double | Valor mínimo (>=) | `100.0` |
| `maxValor` | Double | Valor máximo (<=) | `500.0` |

**Nota:** Todos os parâmetros podem ser combinados. A busca usa operador `AND` (todos os filtros devem ser satisfeitos).

### 📤 Response

**Status 200 OK:**
```json
[
  {
    "id": "692c2633df4f66028024ad9a",
    "documentId": "692c261bdf4f66028024ad7a",
    "rubricaCodigo": "4412",
    "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
    "referencia": "2017-03",
    "valor": 101.26,
    "origem": "CAIXA",
    "pagina": 3
  }
  // ... mais entries que atendem aos filtros
]
```

**Status 204 No Content** - Nenhuma entry encontrada:
```json
[]
```

**Status 404 Not Found** - CPF não encontrado (quando filtro por CPF):
```json
null
```

### 💻 Exemplos com cURL

**Buscar todas as entries de um CPF:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?cpf=12449709568' \
  -H 'accept: */*'
```

**Buscar entries por rubrica específica:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?rubrica=4412' \
  -H 'accept: */*'
```

**Buscar entries por ano e origem:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?ano=2017&origem=CAIXA' \
  -H 'accept: */*'
```

**Buscar entries por CPF, ano e origem:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?cpf=12449709568&ano=2018&origem=FUNCEF' \
  -H 'accept: */*'
```

**Buscar entries por faixa de valores:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?minValor=100&maxValor=500' \
  -H 'accept: */*'
```

**Buscar entries por mês específico:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?ano=2017&mes=3' \
  -H 'accept: */*'
```

**Buscar entries de um documento específico:**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?documentoId=692c261bdf4f66028024ad7a' \
  -H 'accept: */*'
```

**Busca complexa (múltiplos filtros):**
```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?cpf=12449709568&ano=2017&origem=CAIXA&minValor=50&maxValor=200' \
  -H 'accept: */*'
```

### 📝 Casos de Uso

- **Busca avançada com múltiplos critérios**
- **Relatórios personalizados**
- **Análise de dados específicos**
- **Integração com sistemas externos**
- **Filtros dinâmicos no frontend**

---

# 4. REGRAS DE NEGÓCIO

## 4.1 Validação de Rubricas

✅ **Sempre usar rubrica válida**
- Entry é ignorado se rubrica não existir na coleção `rubricas`
- Entry é ignorado se rubrica estiver inativa (`ativo = false`)
- Apenas entries com rubricas válidas são salvas no banco

## 4.2 Formato de Referência

✅ **Referência deve ser YYYY-MM**
- Normalização feita automaticamente pelo extrator
- Formato original do PDF: `01/2017` ou `2017/01`
- Formato normalizado: `2017-01`
- Usado para ordenação e filtros

## 4.3 Normalização de Valores

✅ **Valor deve ser numérico**
- Formato original: `"1.399,59"` (formato brasileiro)
- Formato normalizado: `1399.59` (double)
- Pontos (separadores de milhar) são removidos
- Vírgula (separador decimal) é substituída por ponto

## 4.4 Origem das Entries

✅ **Origem herdada da página**
- `CAIXA` - Entry extraída de página CAIXA
- `FUNCEF` - Entry extraída de página FUNCEF
- Documentos `MISTO` têm entries com origens diferentes

## 4.5 Documentos MISTO

✅ **Documentos MISTO são quebrados por página**
- Cada página mantém sua origem (CAIXA ou FUNCEF)
- Entries de páginas diferentes podem ter origens diferentes
- Campo `origem` reflete a origem da página específica

---

# 5. FILTROS E QUERIES

## 5.1 Como os Filtros Funcionam

A API converte filtros para consultas MongoDB usando `Criteria`:

### Filtro por CPF
```json
// Busca documentos da pessoa primeiro
{
  "documentoId": { "$in": ["doc1", "doc2", "doc3"] }
}
```

### Filtro por Ano
```json
{
  "referencia": { "$regex": "^2018-" }
}
```

### Filtro por Mês
```json
{
  "referencia": { "$regex": "-03$" }
}
```

### Filtro por Ano e Mês (exato)
```json
{
  "referencia": "2018-03"
}
```

### Filtro por Faixa de Valores
```json
{
  "valor": { "$gte": 100, "$lte": 500 }
}
```

### Filtro por Rubrica
```json
{
  "rubricaCodigo": "4412"
}
```

### Filtro por Origem
```json
{
  "origem": "CAIXA"
}
```

## 5.2 Combinação de Filtros

Todos os filtros são combinados com operador `AND`:

```json
// cpf=12449709568&ano=2017&origem=CAIXA
{
  "documentoId": { "$in": ["doc1", "doc2"] },
  "referencia": { "$regex": "^2017-" },
  "origem": "CAIXA"
}
```

---

# 6. TRATAMENTO DE ERROS

## 6.1 Códigos de Status HTTP

| Status | Significado | Quando Ocorre |
|--------|-------------|---------------|
| `200 OK` | Sucesso | Entries encontradas e retornadas |
| `204 No Content` | Sucesso sem conteúdo | Documento/pessoa existe mas não possui entries |
| `404 Not Found` | Não encontrado | Documento ou pessoa não existe |
| `500 Internal Server Error` | Erro interno | Erro inesperado no servidor |

## 6.2 Mensagens de Erro

**Documento não encontrado:**
```json
null
```
Status: `404 Not Found`

**Pessoa não encontrada:**
```json
null
```
Status: `404 Not Found`

**Nenhuma entry encontrada:**
```json
[]
```
Status: `204 No Content` ou `200 OK` (dependendo do endpoint)

## 6.3 Logs de Erro

Erros são logados no servidor com detalhes:
- ID do documento/pessoa
- Tipo de erro
- Stack trace completo
- Timestamp

---

# 7. EXEMPLOS PRÁTICOS COMPLETOS

## 7.1 Cenário: Visualizar Entries de um Documento

**Objetivo:** Ver todas as rubricas extraídas de um documento específico.

```bash
# 1. Buscar todas as entries
curl -X 'GET' \
  'http://localhost:8080/api/v1/documents/692c261bdf4f66028024ad7a/entries' \
  -H 'accept: */*'
```

**Resposta:**
```json
[
  {
    "id": "692c2633df4f66028024ad9a",
    "documentId": "692c261bdf4f66028024ad7a",
    "rubricaCodigo": "4412",
    "rubricaDescricao": "FUNCEF CONTR. EQUACIONAMENTO1 SALDADO",
    "referencia": "2017-03",
    "valor": 101.26,
    "origem": "CAIXA",
    "pagina": 3
  }
  // ... mais entries
]
```

## 7.2 Cenário: Buscar Entries de uma Pessoa por Ano

**Objetivo:** Ver todas as rubricas de uma pessoa em um ano específico.

```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?cpf=12449709568&ano=2017' \
  -H 'accept: */*'
```

## 7.3 Cenário: Análise de Rubrica Específica

**Objetivo:** Ver todas as ocorrências de uma rubrica específica.

```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?rubrica=4412' \
  -H 'accept: */*'
```

## 7.4 Cenário: Entries de Alto Valor

**Objetivo:** Encontrar entries com valores acima de um limite.

```bash
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?minValor=1000' \
  -H 'accept: */*'
```

## 7.5 Cenário: Comparar CAIXA vs FUNCEF

**Objetivo:** Ver entries apenas de uma origem específica.

```bash
# Entries CAIXA
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?origem=CAIXA' \
  -H 'accept: */*'

# Entries FUNCEF
curl -X 'GET' \
  'http://localhost:8080/api/v1/entries?origem=FUNCEF' \
  -H 'accept: */*'
```

---

# 8. PERFORMANCE E OTIMIZAÇÕES

## 8.1 Índices no MongoDB

Para otimizar consultas, são criados índices:

- **`documentoId`** - Índice para buscar entries por documento
- **`rubricaCodigo`** - Índice para buscar entries por rubrica
- **`referencia`** - Índice para buscar entries por mês/ano

## 8.2 Recomendações de Uso

### ✅ Use paginação para documentos grandes
```
GET /api/v1/documents/{id}/entries/paged?page=0&size=20
```

### ✅ Use filtros específicos quando possível
```
GET /api/v1/entries?cpf=...&ano=2017
```
É mais eficiente que buscar tudo e filtrar no cliente.

### ✅ Para consolidação, use endpoint de pessoa
```
GET /api/v1/persons/{cpf}/entries
```
Retorna todas as entries de todos os documentos de uma vez.

## 8.3 Limitações

- **Sem limite de tamanho** - Endpoints não paginados retornam todas as entries
- **Filtros combinados** - Todos os filtros são aplicados com `AND`
- **Sem busca por texto** - Não há busca por descrição (apenas código)

---

# 9. INTEGRAÇÃO COM OUTRAS APIs

## 9.1 Fluxo Completo

1. **Upload** (API 2):
   ```
   POST /api/v1/documents/upload
   → Retorna: { "documentId": "..." }
   ```

2. **Processar** (API 3):
   ```
   POST /api/v1/documents/{id}/process
   → Cria entries na coleção payroll_entries
   ```

3. **Consultar Entries** (API 4):
   ```
   GET /api/v1/documents/{id}/entries
   → Retorna todas as entries extraídas
   ```

## 9.2 Validação de Rubricas

Entries só são salvas se a rubrica existir na API 1:
```
GET /api/v1/rubricas
→ Lista rubricas válidas
```

---

# 10. ESTRUTURA TÉCNICA

## 10.1 Classes Implementadas

### Domain
- `PayrollEntry` - Modelo de domínio
- `PayrollEntryRepository` - Interface de repositório

### Application
- `EntryQueryUseCase` - Lógica de busca e filtros

### Infrastructure
- `MongoPayrollEntryRepositoryAdapter` - Implementação MongoDB
- `SpringDataPayrollEntryRepository` - Spring Data Repository

### Interfaces
- `EntryController` - Endpoint global `/entries`
- `DocumentController` - Endpoints `/documents/{id}/entries`
- `PersonController` - Endpoint `/persons/{cpf}/entries`
- `EntryMapper` - Conversão PayrollEntry → EntryResponse
- `EntryResponse` - DTO de resposta
- `PersonEntriesResponse` - DTO para entries de pessoa
- `PagedEntriesResponse` - DTO para resposta paginada

## 10.2 Tecnologias Utilizadas

- **Spring WebFlux** - APIs reativas
- **Reactor** - Programação reativa (Mono, Flux)
- **MongoDB** - Banco de dados
- **Spring Data MongoDB** - Abstração de acesso a dados
- **ReactiveMongoTemplate** - Queries dinâmicas reativas

---

# 11. TESTES E VALIDAÇÃO

## 11.1 Testes Recomendados

### Teste 1: Buscar entries de documento existente
```bash
curl http://localhost:8080/api/v1/documents/{documentId}/entries
```
✅ Deve retornar lista de entries

### Teste 2: Buscar entries de documento inexistente
```bash
curl http://localhost:8080/api/v1/documents/000000000000000000000000/entries
```
✅ Deve retornar 404

### Teste 3: Buscar entries com filtros
```bash
curl http://localhost:8080/api/v1/entries?cpf=12449709568&ano=2017
```
✅ Deve retornar entries filtradas

### Teste 4: Paginação
```bash
curl http://localhost:8080/api/v1/documents/{id}/entries/paged?page=0&size=10
```
✅ Deve retornar resposta paginada

---

# 12. TROUBLESHOOTING

## 12.1 Problemas Comuns

### Nenhuma entry retornada
**Causa:** Documento não foi processado ou processamento falhou
**Solução:** Verificar status do documento e reprocessar se necessário

### 404 ao buscar por CPF
**Causa:** Pessoa não existe ou CPF está incorreto
**Solução:** Verificar se pessoa foi criada no upload

### Entries não aparecem após processamento
**Causa:** Rubricas não foram validadas (não existem na API 1)
**Solução:** Verificar se rubricas estão cadastradas e ativas

### Paginação não funciona
**Causa:** Parâmetros inválidos
**Solução:** Verificar se `page` >= 0 e `size` > 0

---

# 13. CHANGELOG

## Versão 1.0 (Implementação Inicial)
- ✅ Endpoint `/documents/{id}/entries`
- ✅ Endpoint `/documents/{id}/entries/paged`
- ✅ Endpoint `/persons/{cpf}/entries`
- ✅ Endpoint `/entries` com filtros dinâmicos
- ✅ Suporte a paginação
- ✅ Filtros: CPF, rubrica, ano, mês, origem, documentoId, minValor, maxValor
- ✅ Tratamento de erros completo

---

**Fim da documentação da API 4.**

Para mais informações sobre outras APIs, consulte:
- `API_2_UPLOAD.md` - Upload de documentos
- `API_3_PROCESS_DOCUMENT.md` - Processamento de documentos
- `API_1_RUBRICAS.md` - Gestão de rubricas
