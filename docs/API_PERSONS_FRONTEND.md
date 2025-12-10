# 👥 API de Pessoas - Documentação para Frontend

Esta documentação descreve todos os endpoints relacionados a pessoas, documentos e lançamentos (entries) da API e como implementá-los no frontend.

## 📋 Índice

- [⚠️ Mudanças Recentes](#-mudanças-recentes)
- [Configuração Base](#configuração-base)
- [Autenticação e Autorização](#autenticação-e-autorização)
- [Fluxo de Navegação](#fluxo-de-navegação)
- [Endpoints](#endpoints)
  - [GET /api/v1/persons](#1-get-apiv1persons)
  - [GET /api/v1/persons/{cpf}/documents](#2-get-apiv1personscpfdocuments)
  - [GET /api/v1/persons/{personId}/documents-by-id](#2b-get-apiv1personspersoniddocuments-by-id)
  - [GET /api/v1/documents/{id}/entries](#3-get-apiv1documentsidentries)
  - [GET /api/v1/persons/{cpf}/rubricas](#4-get-apiv1personscpfrubricas)
  - [GET /api/v1/persons/{cpf}/consolidated](#4b-get-apiv1personscpfconsolidated)
  - [GET /api/v1/persons/{cpf}/excel-by-tenant](#5-get-apiv1personscpfexcel-by-tenant)
  - [GET /api/v1/persons/{cpf}/entries](#6-get-apiv1personscpfentries)
- [Modelos de Dados](#modelos-de-dados)
- [Tratamento de Erros](#tratamento-de-erros)
- [Exemplos de Implementação](#exemplos-de-implementação)

---

## ⚠️ Mudanças Recentes

### Atualização do Campo `tipo` em DocumentListItemResponse

**Data**: Dezembro 2025

**Mudança**: O campo `tipo` no objeto `DocumentListItemResponse` agora retorna **`"IRPF"`** ao invés de `"INCOME_TAX"` para documentos de declaração de imposto de renda.

**O que mudou**:
- Antes: `tipo: "INCOME_TAX"`
- Agora: `tipo: "IRPF"`

**Valores possíveis do campo `tipo`**:
- `"CAIXA"` - Documentos da Caixa
- `"FUNCEF"` - Documentos do FUNCEF
- `"CAIXA_FUNCEF"` - Documentos mistos
- `"IRPF"` - Declarações de Imposto de Renda (novo)

**Ajustes necessários no Frontend**:

1. **Atualizar tipos TypeScript/Interfaces**:
   ```typescript
   // ANTES
   tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
   
   // AGORA
   tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF' | 'IRPF';
   ```

2. **Atualizar verificações de tipo**:
   ```typescript
   // ANTES
   if (documento.tipo === 'INCOME_TAX') { ... }
   
   // AGORA
   if (documento.tipo === 'IRPF') { ... }
   ```

3. **Atualizar exibição na UI**:
   - Onde exibir o tipo do documento, agora mostrará "IRPF" ao invés de "INCOME_TAX"
   - Garantir que filtros e buscas considerem "IRPF" como valor válido

**Duplicidade de Documentos**:
- A API já previne duplicidade de arquivos através do hash SHA-256
- Se o mesmo arquivo for enviado duas vezes, a API retornará erro `409 Conflict` com a mensagem "Este arquivo já foi enviado anteriormente"
- Documentos diferentes (mesmo tipo e ano) podem ser aceitos, pois podem representar retificações legítimas

### Atualização do EntryResponse para Entries de IRPF

**Data**: Dezembro 2025

**Mudança**: O objeto `EntryResponse` agora inclui campos opcionais para informações da rubrica (`rubricaCategoria` e `rubricaAtivo`). Para entries de documentos IRPF, esses campos serão `null` porque as rubricas de IRPF (como "IR_NOME", "IR_CPF", etc.) não existem na tabela de rubricas.

**Novos campos no EntryResponse**:
- `rubricaCategoria` (string | null) - Categoria da rubrica quando disponível (ex: "Administrativa", "Extraordinária")
- `rubricaAtivo` (boolean | null) - Indica se a rubrica está ativa quando disponível

**Ajustes necessários no Frontend**:

1. **Atualizar tipos TypeScript/Interfaces**:
   ```typescript
   interface EntryResponse {
     id: string;
     documentId: string;
     rubricaCodigo: string;
     rubricaDescricao: string;
     referencia: string;
     valor: number;
     origem: 'CAIXA' | 'FUNCEF' | 'INCOME_TAX';
     pagina: number;
     // Novos campos opcionais
     rubricaCategoria?: string | null;
     rubricaAtivo?: boolean | null;
   }
   ```

2. **Tratar entries sem rubrica válida (IRPF)**:
   ```typescript
   // Verificar se a rubrica existe antes de acessar propriedades
   const rubricaExiste = entry.rubricaCategoria !== null && entry.rubricaCategoria !== undefined;
   
   // OU usar optional chaining
   const categoria = entry.rubricaCategoria ?? 'Sem categoria';
   const ativo = entry.rubricaAtivo ?? false;
   
   // NUNCA fazer: entry.rubrica.variant (rubrica não existe para IRPF)
   // SEMPRE verificar: if (entry.rubricaCategoria) { ... }
   ```

3. **Exemplo de tratamento seguro**:
   ```typescript
   entries.map(entry => {
     // Para entries de IRPF, rubricaCategoria e rubricaAtivo serão null
     const temRubrica = entry.rubricaCategoria !== null;
     
     return {
       ...entry,
       categoria: entry.rubricaCategoria ?? 'IRPF', // Fallback para IRPF
       ativo: entry.rubricaAtivo ?? true, // Assumir ativo se não houver informação
       variant: temRubrica ? getVariantFromCategoria(entry.rubricaCategoria) : 'default'
     };
   });
   ```

**Importante**: 
- Entries de documentos IRPF usam códigos especiais (IR_NOME, IR_CPF, IR_IMPOSTO_DEVIDO, etc.) que não são rubricas válidas no sistema
- Essas entries não passam pela validação de rubricas e não têm informações adicionais da rubrica
- O frontend deve tratar graciosamente quando `rubricaCategoria` e `rubricaAtivo` são `null`

---

## 🔧 Configuração Base

### Base URL
```
http://localhost:8081/api/v1
```

**Nota**: O prefixo `/api/v1` é adicionado automaticamente pelo backend através do `WebConfig`. Os controllers usam apenas o caminho relativo (ex: `/persons`).

### Headers Padrão
Todas as requisições devem incluir:
```javascript
{
  "Content-Type": "application/json",
  "Accept": "application/json",
  "Authorization": "Bearer {accessToken}"
}
```

**Importante**: 
- Todos os endpoints requerem autenticação
- O `accessToken` deve ser válido e o usuário deve ter as permissões adequadas
- O token expira em 15 minutos - use o refresh token quando necessário

---

## 🔐 Autenticação e Autorização

### Roles Permitidas

| Role | Permissões |
|------|-----------|
| `SUPER_ADMIN` | Pode ver e gerenciar todas as pessoas, documentos e entries (de todos os tenants) |
| `TENANT_ADMIN` | Pode ver e gerenciar pessoas, documentos e entries do seu tenant |
| `TENANT_USER` | Pode visualizar pessoas, documentos e entries do seu tenant |

### Isolamento Multi-Tenant

O sistema aplica isolamento automático baseado no tenant do usuário:
- **SUPER_ADMIN**: Vê todos os dados (de todos os tenants)
- **TENANT_ADMIN / TENANT_USER**: Vê apenas dados do seu próprio tenant

O `tenantId` é extraído automaticamente do JWT token, não é necessário enviá-lo nas requisições.

---

## 🗺️ Fluxo de Navegação

O sistema segue o seguinte fluxo de navegação:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Página de Pessoas                                        │
│    GET /api/v1/persons                                      │
│    ↓ Mostra lista de pessoas com botões de upload          │
│    Cada pessoa tem: id, cpf, nome, matricula, documentos   │
└─────────────────────────────────────────────────────────────┘
                        ↓ (clicar em uma pessoa)
┌─────────────────────────────────────────────────────────────┐
│ 2. Detalhes da Pessoa                                       │
│    ⭐ RECOMENDADO: GET /api/v1/persons/{personId}/documents-by-id │
│    OU: GET /api/v1/persons/{cpf}/documents                  │
│    ↓ Mostra lista de documentos da pessoa                  │
│    ⚠️ Use personId quando disponível para evitar duplicatas│
└─────────────────────────────────────────────────────────────┘
                        ↓ (clicar em um documento)
┌─────────────────────────────────────────────────────────────┐
│ 3. Detalhes do Documento                                    │
│    GET /api/v1/documents/{id}/entries                      │
│    ↓ Mostra lista de entries (lançamentos) do documento     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 4. Matriz de Rubricas da Pessoa (opcional)                 │
│    GET /api/v1/persons/{cpf}/rubricas                      │
│    ↓ Mostra matriz de rubricas com totais                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 📡 Endpoints

### 1. GET /api/v1/persons

Lista todas as pessoas com paginação e filtros opcionais.

**URL**: `/api/v1/persons`  
**Método**: `GET`  
**Autenticação**: Requerida

#### Query Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `nome` | string | Não | Filtrar por nome (busca parcial, case-insensitive) |
| `cpf` | string | Não | Filtrar por CPF (busca parcial, case-insensitive) |
| `matricula` | string | Não | Filtrar por matrícula (busca parcial, case-insensitive) |
| `page` | number | Não | Número da página (padrão: 0) |
| `size` | number | Não | Tamanho da página (padrão: 20) |

#### Regras de Filtro

- Todos os filtros são opcionais e podem ser combinados
- Busca por `nome`, `cpf` e `matricula` é parcial e case-insensitive (usa regex do MongoDB)
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Vê pessoas de todos os tenants
- **TENANT_ADMIN / TENANT_USER**: Vê apenas pessoas do seu tenant

#### Response Success (200 OK)

```json
{
  "content": [
    {
      "id": "507f1f77bcf86cd799439011",
      "tenantId": "550e8400-e29b-41d4-a716-446655440000",
      "cpf": "12345678900",
      "nome": "João Silva",
      "matricula": "0437412",
      "documentos": [
        "507f1f77bcf86cd799439012",
        "507f1f77bcf86cd799439013"
      ],
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-16T14:20:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `content` | PersonResponse[] | Lista de pessoas na página atual |
| `totalElements` | number | Total de pessoas encontradas (considerando filtros) |
| `totalPages` | number | Número total de páginas |
| `currentPage` | number | Página atual (0-indexed) |
| `pageSize` | number | Tamanho da página |
| `hasNext` | boolean | Indica se há próxima página |
| `hasPrevious` | boolean | Indica se há página anterior |

#### Campos de PersonResponse

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | string | ID único da pessoa |
| `tenantId` | string | ID do tenant ao qual a pessoa pertence |
| `cpf` | string | CPF da pessoa |
| `nome` | string | Nome completo da pessoa |
| `matricula` | string | Matrícula da pessoa |
| `documentos` | string[] | Lista de IDs dos documentos associados |
| `createdAt` | string (ISO 8601) | Data de criação |
| `updatedAt` | string (ISO 8601) | Data da última atualização |

#### Exemplo JavaScript/TypeScript

```typescript
interface ListPersonsParams {
  nome?: string;
  cpf?: string;
  matricula?: string;
  page?: number;
  size?: number;
}

async function listPersons(params: ListPersonsParams = {}) {
  const token = localStorage.getItem('accessToken');
  
  const queryParams = new URLSearchParams();
  if (params.nome) queryParams.append('nome', params.nome);
  if (params.cpf) queryParams.append('cpf', params.cpf);
  if (params.matricula) queryParams.append('matricula', params.matricula);
  if (params.page !== undefined) queryParams.append('page', String(params.page));
  if (params.size !== undefined) queryParams.append('size', String(params.size));
  
  const response = await fetch(
    `http://localhost:8081/api/v1/persons?${queryParams.toString()}`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    let errorMessage = 'Erro ao listar pessoas';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.message || error.error || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  return await response.json();
}
```

---

### 2. GET /api/v1/persons/{cpf}/documents

Lista todos os documentos de uma pessoa específica (por CPF).

**URL**: `/api/v1/persons/{cpf}/documents`  
**Método**: `GET`  
**Autenticação**: Requerida

> ⚠️ **IMPORTANTE**: Se você tem o `personId` disponível (vindo da lista de pessoas), use o endpoint `/{personId}/documents-by-id` ao invés deste, pois garante que apenas os documentos da pessoa específica sejam retornados, mesmo quando há múltiplas pessoas com o mesmo CPF em diferentes tenants.

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação) |

#### Regras de Acesso

- O sistema valida se a pessoa existe antes de retornar os documentos
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Pode ver documentos de qualquer pessoa (retorna documentos de todas as pessoas com aquele CPF)
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas documentos de pessoas do seu tenant
- ⚠️ **Limitação**: Se houver múltiplas pessoas com o mesmo CPF em diferentes tenants, este endpoint pode retornar documentos de todas elas (para SUPER_ADMIN) ou apenas do tenant do usuário

#### Response Success (200 OK)

```json
{
  "cpf": "12345678900",
  "documentos": [
    {
      "id": "507f1f77bcf86cd799439012",
      "ano": 2017,
      "status": "PROCESSED",
      "tipo": "CAIXA",
      "mesesDetectados": ["2017-01", "2017-02", "2017-03", "2017-04", "2017-05", "2017-06", "2017-07", "2017-08", "2017-09", "2017-10", "2017-11", "2017-12"],
      "dataUpload": "2024-01-15T10:30:00Z",
      "dataProcessamento": "2024-01-15T10:35:00Z",
      "totalEntries": 132
    },
    {
      "id": "507f1f77bcf86cd799439013",
      "ano": 2018,
      "status": "PROCESSED",
      "tipo": "FUNCEF",
      "mesesDetectados": ["2018-01", "2018-02", "2018-03"],
      "dataUpload": "2024-01-16T14:20:00Z",
      "dataProcessamento": "2024-01-16T14:25:00Z",
      "totalEntries": 36
    }
  ]
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `cpf` | string | CPF da pessoa |
| `documentos` | DocumentListItemResponse[] | Lista de documentos da pessoa |

#### Campos de DocumentListItemResponse

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | string | ID único do documento |
| `ano` | number | Ano detectado no PDF |
| `status` | string | Status do documento: `PENDING`, `PROCESSING`, `PROCESSED`, `ERROR` |
| `tipo` | string | Tipo do documento: `CAIXA`, `FUNCEF`, `CAIXA_FUNCEF` ou `IRPF` |
| `mesesDetectados` | string[] | Lista de meses/anos detectados no formato `["2017-01", "2017-02", ...]` |
| `dataUpload` | string (ISO 8601) | Data e hora do upload do documento |
| `dataProcessamento` | string (ISO 8601) \| null | Data e hora do processamento do documento (null se ainda não processado) |
| `totalEntries` | number | Número total de entries (lançamentos) extraídas do documento |

#### Response Error (404 Not Found)

Retornado quando:
- Pessoa não encontrada com o CPF informado
- Pessoa existe mas não pertence ao tenant do usuário autenticado (para TENANT_ADMIN)

```json
{
  "status": 404,
  "error": "Pessoa não encontrada: 12345678900"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function getPersonDocuments(cpf: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/persons/${cpf}/documents`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao buscar documentos da pessoa';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  return await response.json();
}
```

---

### 2b. GET /api/v1/persons/{personId}/documents-by-id

Lista todos os documentos de uma pessoa específica (por personId).

**URL**: `/api/v1/persons/{personId}/documents-by-id`  
**Método**: `GET`  
**Autenticação**: Requerida

> ⭐ **RECOMENDADO**: Use este endpoint quando você tem o `personId` disponível (vindo da lista de pessoas). Este endpoint garante que apenas os documentos da pessoa específica sejam retornados, mesmo quando há múltiplas pessoas com o mesmo CPF em diferentes tenants.

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `personId` | string | Sim | ID único da pessoa (vindo do campo `id` na lista de pessoas) |

#### Regras de Acesso

- O sistema busca a pessoa pelo `personId` e valida que ela existe
- O sistema valida que a pessoa pertence ao tenant do usuário autenticado (exceto SUPER_ADMIN)
- **SUPER_ADMIN**: Pode ver documentos de qualquer pessoa
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas documentos de pessoas do seu tenant
- ✅ **Vantagem**: Garante que apenas os documentos da pessoa específica sejam retornados, evitando duplicatas

#### Response Success (200 OK)

```json
{
  "cpf": "12345678900",
  "documentos": [
    {
      "id": "507f1f77bcf86cd799439012",
      "ano": 2017,
      "status": "PROCESSED",
      "tipo": "CAIXA",
      "mesesDetectados": ["2017-01", "2017-02", "2017-03", "2017-04", "2017-05", "2017-06", "2017-07", "2017-08", "2017-09", "2017-10", "2017-11", "2017-12"],
      "dataUpload": "2024-01-15T10:30:00Z",
      "dataProcessamento": "2024-01-15T10:35:00Z",
      "totalEntries": 132
    },
    {
      "id": "507f1f77bcf86cd799439013",
      "ano": 2018,
      "status": "PROCESSED",
      "tipo": "FUNCEF",
      "mesesDetectados": ["2018-01", "2018-02", "2018-03"],
      "dataUpload": "2024-01-16T14:20:00Z",
      "dataProcessamento": "2024-01-16T14:25:00Z",
      "totalEntries": 36
    }
  ]
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `cpf` | string | CPF da pessoa |
| `documentos` | DocumentListItemResponse[] | Lista de documentos da pessoa |

#### Campos de DocumentListItemResponse

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | string | ID único do documento |
| `ano` | number | Ano detectado no PDF |
| `status` | string | Status do documento: `PENDING`, `PROCESSING`, `PROCESSED`, `ERROR` |
| `tipo` | string | Tipo do documento: `CAIXA`, `FUNCEF`, `CAIXA_FUNCEF` ou `IRPF` |
| `mesesDetectados` | string[] | Lista de meses/anos detectados no formato `["2017-01", "2017-02", ...]` |
| `dataUpload` | string (ISO 8601) | Data e hora do upload do documento |
| `dataProcessamento` | string (ISO 8601) \| null | Data e hora do processamento do documento (null se ainda não processado) |
| `totalEntries` | number | Número total de entries (lançamentos) extraídas do documento |

#### Response Error (404 Not Found)

Retornado quando:
- Pessoa não encontrada com o personId informado
- Pessoa existe mas não pertence ao tenant do usuário autenticado (para TENANT_ADMIN)

```json
{
  "status": 404,
  "error": "Pessoa não encontrada: 507f1f77bcf86cd799439011"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function getPersonDocumentsById(personId: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/persons/${personId}/documents-by-id`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao buscar documentos da pessoa';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  return await response.json();
}
```

#### Quando Usar Cada Endpoint

| Situação | Endpoint Recomendado | Motivo |
|----------|---------------------|--------|
| Você tem o `personId` (da lista de pessoas) | `/{personId}/documents-by-id` | ✅ Garante documentos apenas da pessoa específica |
| Você só tem o CPF | `/{cpf}/documents` | ⚠️ Pode retornar documentos de múltiplas pessoas se houver duplicatas |
| SUPER_ADMIN buscando todas as pessoas com um CPF | `/{cpf}/documents` | Retorna documentos de todas as pessoas com aquele CPF |

---

### 3. GET /api/v1/documents/{id}/entries

Lista todas as entries (lançamentos) de um documento específico.

**URL**: `/api/v1/documents/{id}/entries`  
**Método**: `GET`  
**Autenticação**: Requerida

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do documento |

#### Regras de Acesso

- O sistema valida se o documento existe antes de retornar as entries
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Pode ver entries de qualquer documento
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas entries de documentos do seu tenant

#### Response Success (200 OK)

```json
[
  {
    "id": "507f1f77bcf86cd799439014",
    "documentId": "507f1f77bcf86cd799439012",
    "rubricaCodigo": "4482",
    "rubricaDescricao": "SALÁRIO BASE",
    "referencia": "2017-08",
    "valor": 1500.00,
    "origem": "CAIXA",
    "pagina": 1
  },
  {
    "id": "507f1f77bcf86cd799439015",
    "documentId": "507f1f77bcf86cd799439012",
    "rubricaCodigo": "4482",
    "rubricaDescricao": "SALÁRIO BASE",
    "referencia": "2017-08",
    "valor": 1500.00,
    "origem": "FUNCEF",
    "pagina": 1
  }
]
```

#### Response Success (204 No Content)

Retornado quando o documento existe mas não possui entries ainda.

#### Campos de EntryResponse

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | string | ID único da entry |
| `documentId` | string | ID do documento ao qual a entry pertence |
| `rubricaCodigo` | string | Código da rubrica (ex: "4482") |
| `rubricaDescricao` | string | Descrição da rubrica extraída do PDF |
| `referencia` | string | Mês/ano no formato "YYYY-MM" (ex: "2017-08") |
| `valor` | number | Valor numérico da entry |
| `origem` | string | Origem da entry: `CAIXA` ou `FUNCEF` |
| `pagina` | number | Número da página onde foi extraída (1-indexed) |

#### Response Error (404 Not Found)

Retornado quando:
- Documento não encontrado
- Documento existe mas não pertence ao tenant do usuário autenticado

#### Exemplo JavaScript/TypeScript

```typescript
async function getDocumentEntries(documentId: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/documents/${documentId}/entries`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Documento não encontrado');
    }
    let errorMessage = 'Erro ao buscar entries do documento';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  // Se for 204 No Content, retornar array vazio
  if (response.status === 204) {
    return [];
  }

  return await response.json();
}
```

---

### 4. GET /api/v1/persons/{cpf}/rubricas

Retorna as rubricas de uma pessoa em formato de matriz, com totais por rubrica e total geral.

**URL**: `/api/v1/persons/{cpf}/rubricas`  
**Método**: `GET`  
**Autenticação**: Requerida

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação) |

#### Regras de Acesso

- O sistema valida se a pessoa existe antes de retornar as rubricas
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Pode ver rubricas de qualquer pessoa
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas rubricas de pessoas do seu tenant

#### Estrutura da Matriz

A matriz organiza as rubricas da seguinte forma:
- **Primeiro nível**: Código da rubrica (ex: "4482")
- **Segundo nível**: Referência (mês/ano no formato "YYYY-MM", ex: "2017-08")
- **Valor da célula**: Objeto com `referencia`, `valor` e `quantidade`

#### Response Success (200 OK)

```json
{
  "cpf": "12345678900",
  "nome": "João Silva",
  "matricula": "0437412",
  "matrix": {
    "4482": {
      "2017-08": {
        "referencia": "2017-08",
        "valor": 1500.00,
        "quantidade": 2
      },
      "2017-09": {
        "referencia": "2017-09",
        "valor": 1500.00,
        "quantidade": 2
      },
      "2017-10": {
        "referencia": "2017-10",
        "valor": 1500.00,
        "quantidade": 2
      }
    },
    "4483": {
      "2017-08": {
        "referencia": "2017-08",
        "valor": 500.00,
        "quantidade": 1
      },
      "2017-09": {
        "referencia": "2017-09",
        "valor": 500.00,
        "quantidade": 1
      }
    }
  },
  "rubricasTotais": {
    "4482": 4500.00,
    "4483": 1000.00
  },
  "totalGeral": 5500.00
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `cpf` | string | CPF da pessoa |
| `nome` | string | Nome completo da pessoa |
| `matricula` | string | Matrícula da pessoa |
| `matrix` | object | Matriz de rubricas: `rubricaCodigo -> referencia -> cell` |
| `rubricasTotais` | object | Totais por rubrica: `rubricaCodigo -> total` |
| `totalGeral` | number | Total geral de todas as rubricas de todas as referências |

#### Estrutura da Matriz

A matriz é um objeto aninhado com a seguinte estrutura:

```typescript
{
  [rubricaCodigo: string]: {
    [referencia: string]: {
      referencia: string;  // Mês/ano no formato "YYYY-MM"
      valor: number;        // Soma dos valores dessa rubrica/referência
      quantidade: number;   // Quantidade de entries para essa rubrica/referência
    }
  }
}
```

#### Campos de RubricaMatrixCell

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `referencia` | string | Mês/ano no formato "YYYY-MM" (ex: "2017-08") |
| `valor` | number | Soma dos valores de todas as entries dessa rubrica/referência |
| `quantidade` | number | Quantidade de entries (lançamentos) para essa rubrica/referência |

#### Exemplo de Uso da Matriz

A matriz permite visualizar:
- **Por rubrica**: Todas as referências (meses/anos) de uma rubrica específica
- **Por referência**: Todas as rubricas de um mês/ano específico
- **Totais**: Total por rubrica e total geral

**Exemplo de visualização em tabela**:

```
Rubrica | 2017-08 | 2017-09 | 2017-10 | Total
--------|---------|---------|---------|-------
4482    | 1500.00 | 1500.00 | 1500.00 | 4500.00
4483    |  500.00 |  500.00 |    0.00 | 1000.00
--------|---------|---------|---------|-------
Total   | 2000.00 | 2000.00 | 1500.00 | 5500.00
```

#### ⚠️ IMPORTANTE: Tratamento de Respostas

**O endpoint sempre retorna 200 OK com JSON**, mesmo em casos de erro ou quando não há dados. **Nunca trate como erro quando receber 200 OK**.

**Cenários possíveis:**

1. **✅ Sucesso com dados**: `200 OK` com `matrix` preenchida
2. **✅ Sucesso sem dados**: `200 OK` com `matrix` vazia (pessoa sem documentos processados) - **NÃO É ERRO**
3. **❌ Pessoa não encontrada**: `404 NOT_FOUND` com JSON vazio (campos vazios, `matrix: {}`)
4. **❌ Erro de autenticação**: `403 FORBIDDEN` com JSON vazio
5. **❌ Erro interno**: `500 INTERNAL_SERVER_ERROR` com JSON vazio

**Como verificar se há dados:**
```typescript
const data = await response.json();

// Verificar se a matriz está vazia
if (!data.matrix || Object.keys(data.matrix).length === 0) {
  // Pessoa sem rubricas processadas - mostrar mensagem amigável
  // NÃO é um erro, apenas não há dados ainda
}
```

#### Response Error (404 Not Found)

Retornado quando:
- Pessoa não encontrada com o CPF informado
- Pessoa existe mas não pertence ao tenant do usuário autenticado

**Formato da resposta (404):**
```json
{
  "cpf": "12345678900",
  "nome": "",
  "matricula": "",
  "matrix": {},
  "rubricasTotais": {},
  "totalGeral": 0.0
}
```

#### Response Error (403 Forbidden)

Retornado quando:
- Erro de autenticação/autorização

**Formato da resposta (403):**
```json
{
  "cpf": "12345678900",
  "nome": "",
  "matricula": "",
  "matrix": {},
  "rubricasTotais": {},
  "totalGeral": 0.0
}
```

#### Response Error (500 Internal Server Error)

Retornado quando:
- Erro interno do servidor

**Formato da resposta (500):**
```json
{
  "cpf": "12345678900",
  "nome": "",
  "matricula": "",
  "matrix": {},
  "rubricasTotais": {},
  "totalGeral": 0.0
}
```

#### Exemplo JavaScript/TypeScript (Atualizado)

```typescript
async function getPersonRubricasMatrix(cpf: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/persons/${cpf}/rubricas`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  // ⚠️ IMPORTANTE: O endpoint sempre retorna JSON, mesmo em caso de erro
  const data = await response.json();

  // Verificar status HTTP
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Pessoa não encontrada');
    }
    if (response.status === 403) {
      throw new Error('Acesso negado');
    }
    if (response.status === 500) {
      throw new Error('Erro interno do servidor');
    }
    throw new Error(`Erro ${response.status}: ${response.statusText}`);
  }

  // ⚠️ IMPORTANTE: Verificar se há dados na matriz
  // Matriz vazia NÃO é erro, apenas indica que não há dados processados ainda
  const hasData = data.matrix && Object.keys(data.matrix).length > 0;
  
  return {
    ...data,
    hasData, // Flag útil para o frontend saber se há dados
  };
}
```

#### Exemplo React Component (Atualizado)

```typescript
import React, { useState, useEffect } from 'react';

interface RubricasMatrixProps {
  cpf: string;
}

function RubricasMatrix({ cpf }: RubricasMatrixProps) {
  const [matrix, setMatrix] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadMatrix();
  }, [cpf]);

  async function loadMatrix() {
    try {
      setLoading(true);
      setError(null);
      const data = await getPersonRubricasMatrix(cpf);
      setMatrix(data);
      
      // ⚠️ IMPORTANTE: Matriz vazia não é erro
      if (!data.hasData) {
        // Pessoa sem rubricas processadas - isso é normal
        // Não definir error, apenas mostrar mensagem amigável
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar matriz');
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Carregando matriz de rubricas...</div>;
  
  // ⚠️ IMPORTANTE: Tratar erro apenas se realmente houver erro
  if (error) {
    return (
      <div className="error">
        <p>Erro: {error}</p>
        <button onClick={loadMatrix}>Tentar novamente</button>
      </div>
    );
  }
  
  // ⚠️ IMPORTANTE: Verificar se há dados antes de renderizar
  if (!matrix || !matrix.hasData) {
    return (
      <div className="empty-state">
        <p>Nenhuma rubrica encontrada</p>
        <p className="text-muted">
          Esta pessoa ainda não possui rubricas processadas. 
          Os dados serão exibidos após o processamento dos documentos.
        </p>
      </div>
    );
  }

  // Renderizar matriz normalmente
  // ... código de renderização da matriz ...
}
```

---

### 4b. GET /api/v1/persons/{cpf}/consolidated

Retorna a consolidação de todas as rubricas de uma pessoa em formato matricial, organizada por rubrica e mês/ano. Este endpoint é especialmente útil para gerar relatórios Excel com informações consolidadas da pessoa.

**URL**: `/api/v1/persons/{cpf}/consolidated`  
**Método**: `GET`  
**Autenticação**: Requerida

> 📊 **Uso**: Este endpoint consolida todas as entries (lançamentos) de uma pessoa, agrupando por rubrica e mês/ano. Os dados retornados podem ser usados para gerar planilhas Excel com informações consolidadas da pessoa.

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação) |

#### Query Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `ano` | string | Não | Filtrar por um ano específico (formato: "2017"). Deve estar entre 2000 e 2100 |
| `origem` | string | Não | Filtrar por origem: `CAIXA` ou `FUNCEF` |

#### Regras de Acesso

- O sistema valida se a pessoa existe antes de retornar a consolidação
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Pode ver consolidação de qualquer pessoa
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas consolidação de pessoas do seu tenant
- Se nenhum filtro for aplicado, retorna consolidação de todos os anos e origens
- Apenas entries de rubricas ativas são incluídas na consolidação

#### Response Success (200 OK)

```json
{
  "cpf": "12345678900",
  "nome": "João Silva",
  "anos": ["2016", "2017", "2018"],
  "meses": ["01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"],
  "rubricas": [
    {
      "codigo": "4482",
      "descricao": "SALÁRIO BASE",
      "valores": {
        "2017-01": 1500.00,
        "2017-02": 1500.00,
        "2017-03": 1500.00,
        "2017-08": 1500.00,
        "2017-09": 1500.00,
        "2017-10": 1500.00
      },
      "total": 9000.00
    },
    {
      "codigo": "4483",
      "descricao": "ADICIONAL",
      "valores": {
        "2017-01": 500.00,
        "2017-02": 500.00,
        "2017-03": 500.00
      },
      "total": 1500.00
    }
  ],
  "totaisMensais": {
    "2017-01": 2000.00,
    "2017-02": 2000.00,
    "2017-03": 2000.00,
    "2017-08": 1500.00,
    "2017-09": 1500.00,
    "2017-10": 1500.00
  },
  "totalGeral": 10500.00
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `cpf` | string | CPF da pessoa |
| `nome` | string | Nome completo da pessoa |
| `anos` | string[] | Lista de anos únicos encontrados nas entries (ex: `["2016", "2017", "2018"]`) |
| `meses` | string[] | Lista de meses (sempre `["01", "02", ..., "12"]`) |
| `rubricas` | ConsolidationRow[] | Lista de rubricas consolidadas, ordenadas por código |
| `totaisMensais` | object | Totais por mês/ano no formato `"YYYY-MM" -> valor` |
| `totalGeral` | number | Total geral de todas as rubricas de todas as referências |

#### Campos de ConsolidationRow

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `codigo` | string | Código da rubrica (ex: "4482") |
| `descricao` | string | Descrição da rubrica |
| `valores` | object | Valores consolidados por mês/ano no formato `"YYYY-MM" -> valor` |
| `total` | number | Total da rubrica (soma de todos os valores) |

#### Estrutura dos Valores

A estrutura `valores` em cada `ConsolidationRow` é um objeto onde:
- **Chave**: Referência no formato `"YYYY-MM"` (ex: `"2017-01"`)
- **Valor**: Soma de todos os valores dessa rubrica para aquele mês/ano

**Exemplo**:
```json
{
  "valores": {
    "2017-01": 1500.00,  // Soma de todas as entries da rubrica 4482 em janeiro/2017
    "2017-02": 1500.00,  // Soma de todas as entries da rubrica 4482 em fevereiro/2017
    "2017-08": 1500.00   // Soma de todas as entries da rubrica 4482 em agosto/2017
  }
}
```

#### Response Success (204 No Content)

Retornado quando:
- A pessoa existe mas não possui entries ainda
- Os filtros aplicados não retornaram nenhuma entry
- Nenhuma rubrica ativa foi encontrada

#### Response Error (400 Bad Request)

Retornado quando:
- Ano inválido (fora do range 2000-2100 ou formato inválido)
- Origem inválida (diferente de `CAIXA` ou `FUNCEF`)

```json
{
  "status": 400,
  "error": "Ano inválido: 1999"
}
```

#### Response Error (404 Not Found)

Retornado quando:
- Pessoa não encontrada com o CPF informado
- Pessoa existe mas não pertence ao tenant do usuário autenticado

```json
{
  "status": 404,
  "error": "Pessoa não encontrada: 12345678900"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function getPersonConsolidated(
  cpf: string, 
  ano?: string, 
  origem?: 'CAIXA' | 'FUNCEF'
) {
  const token = localStorage.getItem('accessToken');
  
  const queryParams = new URLSearchParams();
  if (ano) queryParams.append('ano', ano);
  if (origem) queryParams.append('origem', origem);
  
  const url = `http://localhost:8081/api/v1/persons/${cpf}/consolidated${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
  
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Pessoa não encontrada');
    }
    if (response.status === 400) {
      const error = await response.json();
      throw new Error(error.error || 'Parâmetros inválidos');
    }
    if (response.status === 204) {
      return null; // Nenhum dado consolidado
    }
    let errorMessage = 'Erro ao buscar consolidação';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  return await response.json();
}
```

#### Exemplo de Uso

```typescript
// Buscar consolidação completa (todos os anos e origens)
const consolidated = await getPersonConsolidated('12345678900');

// Buscar consolidação apenas de 2017
const consolidated2017 = await getPersonConsolidated('12345678900', '2017');

// Buscar consolidação apenas de CAIXA em 2017
const consolidatedCAIXA2017 = await getPersonConsolidated('12345678900', '2017', 'CAIXA');
```

#### Diferença entre `/rubricas` e `/consolidated`

| Aspecto | `/rubricas` | `/consolidated` |
|---------|-------------|-----------------|
| **Formato** | Matriz aninhada (objeto de objetos) | Lista de rubricas com valores em objeto |
| **Uso** | Visualização em tabela dinâmica | Geração de Excel/relatórios |
| **Estrutura** | `rubricaCodigo -> referencia -> cell` | `rubricas[]` com `valores: { referencia: valor }` |
| **Totais** | `rubricasTotais` e `totalGeral` | `totaisMensais` e `totalGeral` |
| **Filtros** | Não suporta filtros | Suporta `ano` e `origem` |
| **Ordenação** | Por código de rubrica | Por código de rubrica |

---

### 5. GET /api/v1/persons/{cpf}/excel-by-tenant

Gera e faz download de um arquivo Excel com consolidação de todas as rubricas de uma pessoa.

**URL**: `/api/v1/persons/{cpf}/excel-by-tenant`  
**Método**: `GET`  
**Autenticação**: Requerida  
**Content-Type**: `application/octet-stream` (arquivo binário)

> 📊 **Uso**: Este endpoint gera um arquivo Excel (.xlsx) com todas as rubricas consolidadas de uma pessoa. O nome do arquivo segue o formato: `YYYYMMDDHHMM_CPF_NOME.xlsx` (exemplo: `202512012132_12449709568_FLAVIO_JOSE_PEREIRA_ALMEIDA.xlsx`).

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação) |

#### Query Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `tenantId` | string | **Sim** | ID do tenant da pessoa (obrigatório para evitar duplicatas) |
| `ano` | string | Não | Filtrar por um ano específico (formato: "2017"). Deve estar entre 2000 e 2100 |
| `origem` | string | Não | Filtrar por origem: `CAIXA` ou `FUNCEF` |

#### Regras de Acesso

- O sistema valida se a pessoa existe antes de gerar o Excel
- O `tenantId` é obrigatório para garantir que a pessoa correta seja identificada
- **SUPER_ADMIN**: Pode gerar Excel de qualquer pessoa
- **TENANT_ADMIN / TENANT_USER**: Pode gerar Excel apenas de pessoas do seu tenant
- Se nenhum filtro for aplicado, o Excel contém todas as rubricas de todos os anos e origens

#### Response Success (200 OK)

**Content-Type**: `application/octet-stream`  
**Content-Disposition**: `attachment; filename="YYYYMMDDHHMM_CPF_NOME.xlsx"`

O corpo da resposta é um arquivo binário (bytes do arquivo Excel).

#### Response Errors

| Status | Descrição |
|--------|-----------|
| `404 NOT_FOUND` | Pessoa não encontrada com o CPF e tenantId fornecidos |
| `204 NO_CONTENT` | Nenhuma entrada encontrada para a pessoa (após aplicar filtros) |
| `400 BAD_REQUEST` | Ano ou origem inválidos |
| `500 INTERNAL_SERVER_ERROR` | Erro ao gerar o arquivo Excel |

#### Exemplo de Uso (TypeScript/React)

```typescript
// Função para fazer download do Excel
async function exportPersonToExcelByTenant(
  cpf: string, 
  tenantId: string, 
  ano?: string, 
  origem?: string
): Promise<{ blob: Blob; contentDisposition: string | null }> {
  const token = localStorage.getItem('accessToken');
  
  // Construir URL com query params
  const params = new URLSearchParams();
  params.append('tenantId', tenantId);
  if (ano) params.append('ano', ano);
  if (origem) params.append('origem', origem);
  
  const url = `${API_BASE_URL}/persons/${cpf}/excel-by-tenant?${params.toString()}`;
  
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });
  
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Pessoa não encontrada');
    }
    if (response.status === 204) {
      throw new Error('Nenhuma entrada encontrada');
    }
    throw new Error(`Erro ao exportar: ${response.statusText}`);
  }
  
  // Obter blob do arquivo
  const blob = await response.blob();
  
  // Extrair Content-Disposition header
  const contentDisposition = response.headers.get('Content-Disposition');
  
  return { blob, contentDisposition };
}

// Função auxiliar para extrair nome do arquivo do Content-Disposition
function extractFilenameFromContentDisposition(contentDisposition: string | null): string {
  if (!contentDisposition) {
    return 'consolidado.xlsx'; // Fallback
  }
  
  // Tentar diferentes formatos do Content-Disposition
  // Formato 1: attachment; filename="arquivo.xlsx"
  // Formato 2: attachment; filename*=UTF-8''arquivo.xlsx
  // Formato 3: attachment; filename=arquivo.xlsx
  
  let extractedName: string | null = null;
  
  // Tentar formato com aspas: filename="arquivo.xlsx"
  const quotedMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2)/);
  if (quotedMatch && quotedMatch[1]) {
    extractedName = quotedMatch[1].replace(/['"]/g, '');
  }
  
  // Se não encontrou, tentar formato sem aspas: filename=arquivo.xlsx
  if (!extractedName) {
    const unquotedMatch = contentDisposition.match(/filename[^;=\n]*=([^;\n]+)/);
    if (unquotedMatch && unquotedMatch[1]) {
      extractedName = unquotedMatch[1].trim();
    }
  }
  
  // Tentar formato UTF-8 encoded: filename*=UTF-8''arquivo.xlsx
  if (!extractedName) {
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;\n]+)/);
    if (utf8Match && utf8Match[1]) {
      extractedName = decodeURIComponent(utf8Match[1]);
    }
  }
  
  return extractedName || 'consolidado.xlsx';
}

// Exemplo de uso em um componente React
function PersonExcelButton({ personId, cpf, nome, tenantId }: {
  personId: string;
  cpf: string;
  nome: string;
  tenantId: string;
}) {
  const [isExporting, setIsExporting] = useState(false);
  
  const handleExport = async () => {
    try {
      setIsExporting(true);
      
      // Fazer requisição para obter o Excel
      const { blob, contentDisposition } = await exportPersonToExcelByTenant(cpf, tenantId);
      
      // Extrair nome do arquivo do header
      const fileName = extractFilenameFromContentDisposition(contentDisposition);
      
      // Criar URL temporária para o blob
      const url = window.URL.createObjectURL(blob);
      
      // Criar elemento <a> para download
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName; // ⭐ Usar nome extraído do header
      
      // Adicionar ao DOM, clicar e remover
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      
      // Limpar URL temporária
      window.URL.revokeObjectURL(url);
      
      toast({
        title: 'Exportação concluída',
        description: `Arquivo ${fileName} foi baixado com sucesso.`,
      });
    } catch (error) {
      toast({
        title: 'Erro na exportação',
        description: error instanceof Error ? error.message : 'Erro desconhecido',
        variant: 'destructive',
      });
    } finally {
      setIsExporting(false);
    }
  };
  
  return (
    <Button onClick={handleExport} disabled={isExporting}>
      {isExporting ? 'Exportando...' : 'Exportar Excel'}
    </Button>
  );
}
```

#### ⚠️ Importante: Extração do Nome do Arquivo

O backend envia o nome do arquivo no header `Content-Disposition` no formato:
```
Content-Disposition: attachment; filename="202512012132_12449709568_FLAVIO_JOSE_PEREIRA_ALMEIDA.xlsx"
```

**NUNCA use um fallback hardcoded como `consolidado_${cpf}_${nome}.xlsx`**. Sempre extraia o nome do arquivo do header `Content-Disposition` para garantir que o nome correto seja usado.

O formato do nome do arquivo é: `YYYYMMDDHHMM_CPF_NOME.xlsx`
- `YYYYMMDDHHMM`: Data e hora atual no formato ano, mês, dia, hora, minuto
- `CPF`: CPF da pessoa (sem formatação)
- `NOME`: Nome da pessoa normalizado (sem acentos, maiúsculas, espaços viram underscore)

---

### 6. GET /api/v1/persons/{cpf}/entries

Retorna todas as entries (lançamentos) de todos os documentos de uma pessoa.

**URL**: `/api/v1/persons/{cpf}/entries`  
**Método**: `GET`  
**Autenticação**: Requerida

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação) |

#### Regras de Acesso

- O sistema valida se a pessoa existe antes de retornar as entries
- O sistema filtra automaticamente por `tenantId` do usuário autenticado
- **SUPER_ADMIN**: Pode ver entries de qualquer pessoa
- **TENANT_ADMIN / TENANT_USER**: Pode ver apenas entries de pessoas do seu tenant

#### Response Success (200 OK)

```json
{
  "cpf": "12345678900",
  "totalEntries": 150,
  "entries": [
    {
      "id": "507f1f77bcf86cd799439014",
      "documentId": "507f1f77bcf86cd799439012",
      "rubricaCodigo": "4482",
      "rubricaDescricao": "SALÁRIO BASE",
      "referencia": "2017-08",
      "valor": 1500.00,
      "origem": "CAIXA",
      "pagina": 1
    }
    // ... mais entries
  ]
}
```

#### Response Success (204 No Content)

Retornado quando a pessoa existe mas não possui entries ainda.

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `cpf` | string | CPF da pessoa |
| `totalEntries` | number | Total de entries encontradas |
| `entries` | EntryResponse[] | Lista de todas as entries de todos os documentos da pessoa |

#### Exemplo JavaScript/TypeScript

```typescript
async function getPersonEntries(cpf: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/persons/${cpf}/entries`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Pessoa não encontrada');
    }
    let errorMessage = 'Erro ao buscar entries da pessoa';
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  // Se for 204 No Content, retornar objeto com array vazio
  if (response.status === 204) {
    return {
      cpf: cpf,
      totalEntries: 0,
      entries: []
    };
  }

  return await response.json();
}
```

---

## 📊 Modelos de Dados

### PersonResponse

```typescript
interface PersonResponse {
  id: string;
  tenantId: string;
  cpf: string;
  nome: string;
  matricula: string;
  documentos: string[];  // IDs dos documentos
  createdAt: string;      // ISO 8601
  updatedAt: string;      // ISO 8601
}
```

### PersonListResponse

```typescript
interface PersonListResponse {
  content: PersonResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
```

### DocumentListResponse

```typescript
interface DocumentListResponse {
  cpf: string;
  documentos: DocumentListItemResponse[];
}

interface DocumentListItemResponse {
  id: string;
  ano: number;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF' | 'IRPF';
  mesesDetectados: string[]; // Formato: ["2017-01", "2017-02", ...]
  dataUpload: string; // ISO 8601
  dataProcessamento: string | null; // ISO 8601 (null se ainda não processado)
  totalEntries: number; // Número total de entries extraídas
}
```

### EntryResponse

```typescript
interface EntryResponse {
  id: string;
  documentId: string;
  rubricaCodigo: string;
  rubricaDescricao: string;
  referencia: string;     // Formato: "YYYY-MM"
  valor: number;
  origem: 'CAIXA' | 'FUNCEF';
  pagina: number;
}
```

### PersonRubricasMatrixResponse

```typescript
interface PersonRubricasMatrixResponse {
  cpf: string;
  nome: string;
  matricula: string;
  matrix: {
    [rubricaCodigo: string]: {
      [referencia: string]: RubricaMatrixCell;
    };
  };
  rubricasTotais: {
    [rubricaCodigo: string]: number;
  };
  totalGeral: number;
}

interface RubricaMatrixCell {
  referencia: string;     // Formato: "YYYY-MM"
  valor: number;
  quantidade: number;
}
```

### ConsolidatedResponse

```typescript
interface ConsolidatedResponse {
  cpf: string;
  nome: string;
  anos: string[];              // ["2016", "2017", "2018"]
  meses: string[];             // ["01", "02", ..., "12"]
  rubricas: ConsolidationRow[];
  totaisMensais: {
    [referencia: string]: number;  // "2017-01" -> 2000.00
  };
  totalGeral: number;
}

interface ConsolidationRow {
  codigo: string;              // "4482"
  descricao: string;           // "SALÁRIO BASE"
  valores: {
    [referencia: string]: number;  // "2017-01" -> 1500.00
  };
  total: number;               // Total da rubrica
}
```

---

## ⚠️ Tratamento de Erros

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|-------------|------------------|
| 200 | Sucesso | Processar resposta normalmente |
| 204 | No Content | Retornar array/objeto vazio |
| 400 | Bad Request | Exibir mensagem de erro ao usuário |
| 401 | Unauthorized | Token inválido - fazer refresh ou redirecionar para login |
| 403 | Forbidden | Usuário não tem permissão - exibir mensagem |
| 404 | Not Found | Recurso não encontrado - exibir mensagem |
| 500 | Internal Server Error | Erro do servidor - tentar novamente |

### Estrutura de Erro Padrão

Os endpoints retornam erros no seguinte formato:

```json
{
  "status": 404,
  "error": "Pessoa não encontrada: 12345678900"
}
```

**Nota**: Alguns endpoints podem retornar apenas o status HTTP sem corpo de resposta em caso de erro. Sempre verifique o status code e trate adequadamente.

### Função de Tratamento de Erros

```typescript
async function handleApiError(response: Response) {
  if (!response.ok) {
    let errorMessage = 'Erro desconhecido';
    
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        errorMessage = error.error || error.message || errorMessage;
      } else {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    
    throw new Error(errorMessage);
  }
  
  return response;
}
```

---

## 📝 Exemplos de Implementação

### Exemplo Completo: Serviço de Pessoas

```typescript
class PersonService {
  private baseURL = 'http://localhost:8081/api/v1';

  private async getAuthHeaders(): Promise<HeadersInit> {
    const token = localStorage.getItem('accessToken');
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    };
  }

  async listPersons(params: {
    nome?: string;
    cpf?: string;
    matricula?: string;
    page?: number;
    size?: number;
  } = {}) {
    const queryParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        queryParams.append(key, String(value));
      }
    });

    const response = await fetch(
      `${this.baseURL}/persons?${queryParams.toString()}`,
      {
        method: 'GET',
        headers: await this.getAuthHeaders(),
      }
    );

    await this.handleApiError(response);
    return await response.json();
  }

  async getPersonDocuments(cpf: string) {
    const response = await fetch(`${this.baseURL}/persons/${cpf}/documents`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async getPersonDocumentsById(personId: string) {
    const response = await fetch(`${this.baseURL}/persons/${personId}/documents-by-id`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async getDocumentEntries(documentId: string) {
    const response = await fetch(`${this.baseURL}/documents/${documentId}/entries`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    
    if (response.status === 204) {
      return [];
    }
    
    return await response.json();
  }

  async getPersonRubricasMatrix(cpf: string) {
    const response = await fetch(`${this.baseURL}/persons/${cpf}/rubricas`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async getPersonConsolidated(cpf: string, ano?: string, origem?: 'CAIXA' | 'FUNCEF') {
    const queryParams = new URLSearchParams();
    if (ano) queryParams.append('ano', ano);
    if (origem) queryParams.append('origem', origem);
    
    const url = `${this.baseURL}/persons/${cpf}/consolidated${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
    
    const response = await fetch(url, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    
    if (response.status === 204) {
      return null; // Nenhum dado consolidado
    }
    
    return await response.json();
  }

  async getPersonEntries(cpf: string) {
    const response = await fetch(`${this.baseURL}/persons/${cpf}/entries`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    
    if (response.status === 204) {
      return {
        cpf: cpf,
        totalEntries: 0,
        entries: []
      };
    }
    
    return await response.json();
  }

  private async handleApiError(response: Response) {
    if (!response.ok) {
      let errorMessage = 'Erro desconhecido';
      try {
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
          const error = await response.json();
          errorMessage = error.error || error.message || errorMessage;
        } else {
          errorMessage = `Erro ${response.status}: ${response.statusText}`;
        }
      } catch {
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
      throw new Error(errorMessage);
    }
    return response;
  }
}

export const personService = new PersonService();
```

### Exemplo: Componente React para Listagem de Pessoas

```typescript
import React, { useState, useEffect } from 'react';
import { personService } from './services/PersonService';

interface Person {
  id: string;
  cpf: string;
  nome: string;
  matricula: string;
  documentos: string[];
}

function PersonList() {
  const [persons, setPersons] = useState<Person[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [filters, setFilters] = useState({
    nome: '',
    cpf: '',
    matricula: ''
  });

  useEffect(() => {
    loadPersons();
  }, [page, filters]);

  async function loadPersons() {
    try {
      setLoading(true);
      setError(null);
      const response = await personService.listPersons({
        ...filters,
        page,
        size: 20
      });
      setPersons(response.content);
      setTotalPages(response.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar pessoas');
    } finally {
      setLoading(false);
    }
  }

  function handlePersonClick(person: Person) {
    // ⭐ RECOMENDADO: Usar personId ao invés de CPF para evitar duplicatas
    // Navegar para página de detalhes da pessoa usando personId
    window.location.href = `/persons/${person.id}/documents`;
    // OU, se preferir usar CPF (não recomendado se houver duplicatas):
    // window.location.href = `/persons/${person.cpf}/documents`;
  }

  if (loading) return <div>Carregando...</div>;
  if (error) return <div>Erro: {error}</div>;

  return (
    <div>
      <h1>Pessoas</h1>
      
      {/* Filtros */}
      <div>
        <input
          type="text"
          placeholder="Nome"
          value={filters.nome}
          onChange={(e) => setFilters({ ...filters, nome: e.target.value })}
        />
        <input
          type="text"
          placeholder="CPF"
          value={filters.cpf}
          onChange={(e) => setFilters({ ...filters, cpf: e.target.value })}
        />
        <input
          type="text"
          placeholder="Matrícula"
          value={filters.matricula}
          onChange={(e) => setFilters({ ...filters, matricula: e.target.value })}
        />
      </div>

      {/* Lista de pessoas */}
      <table>
        <thead>
          <tr>
            <th>Nome</th>
            <th>CPF</th>
            <th>Matrícula</th>
            <th>Documentos</th>
            <th>Ações</th>
          </tr>
        </thead>
        <tbody>
          {persons.map(person => (
            <tr key={person.id}>
              <td>{person.nome}</td>
              <td>{person.cpf}</td>
              <td>{person.matricula}</td>
              <td>{person.documentos.length}</td>
              <td>
                <button onClick={() => handlePersonClick(person)}>
                  Ver Detalhes
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Paginação */}
      <div>
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>
          Anterior
        </button>
        <span>Página {page + 1} de {totalPages}</span>
        <button disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>
          Próxima
        </button>
      </div>
    </div>
  );
}
```

### Exemplo: Componente React para Matriz de Rubricas

```typescript
import React, { useState, useEffect } from 'react';
import { personService } from './services/PersonService';

interface RubricasMatrixProps {
  cpf: string;
}

function RubricasMatrix({ cpf }: RubricasMatrixProps) {
  const [matrix, setMatrix] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadMatrix();
  }, [cpf]);

  async function loadMatrix() {
    try {
      setLoading(true);
      setError(null);
      const data = await personService.getPersonRubricasMatrix(cpf);
      setMatrix(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar matriz');
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Carregando matriz de rubricas...</div>;
  if (error) return <div>Erro: {error}</div>;
  if (!matrix) return <div>Nenhum dado disponível</div>;

  // Extrair todas as referências únicas
  const referencias = new Set<string>();
  Object.values(matrix.matrix).forEach((rubrica: any) => {
    Object.keys(rubrica).forEach(ref => referencias.add(ref));
  });
  const referenciasArray = Array.from(referencias).sort();

  // Extrair todas as rubricas
  const rubricas = Object.keys(matrix.matrix);

  return (
    <div>
      <h2>Matriz de Rubricas - {matrix.nome}</h2>
      <p>CPF: {matrix.cpf} | Matrícula: {matrix.matricula}</p>

      <table>
        <thead>
          <tr>
            <th>Rubrica</th>
            {referenciasArray.map(ref => (
              <th key={ref}>{ref}</th>
            ))}
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {rubricas.map(rubricaCodigo => (
            <tr key={rubricaCodigo}>
              <td>{rubricaCodigo}</td>
              {referenciasArray.map(ref => {
                const cell = matrix.matrix[rubricaCodigo]?.[ref];
                return (
                  <td key={ref}>
                    {cell ? (
                      <div>
                        <div>R$ {cell.valor.toFixed(2)}</div>
                        <small>({cell.quantidade} entries)</small>
                      </div>
                    ) : (
                      <span>-</span>
                    )}
                  </td>
                );
              })}
              <td>
                <strong>R$ {matrix.rubricasTotais[rubricaCodigo]?.toFixed(2) || '0.00'}</strong>
              </td>
            </tr>
          ))}
          <tr>
            <td><strong>Total Geral</strong></td>
            {referenciasArray.map(ref => {
              // Calcular total por referência
              let totalRef = 0;
              rubricas.forEach(rubricaCodigo => {
                const cell = matrix.matrix[rubricaCodigo]?.[ref];
                if (cell) totalRef += cell.valor;
              });
              return (
                <td key={ref}>
                  <strong>R$ {totalRef.toFixed(2)}</strong>
                </td>
              );
            })}
            <td>
              <strong>R$ {matrix.totalGeral.toFixed(2)}</strong>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
```

---

## 🔍 Fluxo Completo de Navegação

### 1. Página Inicial - Lista de Pessoas

```typescript
// Carregar lista de pessoas
const persons = await personService.listPersons({ page: 0, size: 20 });

// Exibir lista com botões de upload
// Ao clicar em uma pessoa, navegar para detalhes
```

### 2. Detalhes da Pessoa - Lista de Documentos

```typescript
// ⭐ RECOMENDADO: Usar personId quando disponível
// Carregar documentos da pessoa usando personId (evita duplicatas)
const documents = await personService.getPersonDocumentsById(personId);

// OU, se você só tem o CPF (não recomendado se houver múltiplas pessoas com mesmo CPF):
// const documents = await personService.getPersonDocuments(cpf);

// Exibir lista de documentos
// Opção: Botão para ver matriz de rubricas
// Ao clicar em um documento, navegar para entries
```

### 3. Detalhes do Documento - Lista de Entries

```typescript
// Carregar entries do documento
const entries = await personService.getDocumentEntries(documentId);

// Exibir lista de entries (lançamentos)
```

### 4. Matriz de Rubricas (Opcional)

```typescript
// Carregar matriz de rubricas
const matrix = await personService.getPersonRubricasMatrix(cpf);

// Exibir matriz em formato de tabela
// Mostrar totais por rubrica e total geral
```

---

## 🔑 Informações Importantes

### Paginação

- Padrão: `page=0`, `size=20`
- Use `hasNext` e `hasPrevious` para controlar navegação
- `totalPages` indica o número total de páginas

### Busca e Filtros

- Busca por `nome`, `cpf` e `matricula` é case-insensitive e parcial
- Filtros podem ser combinados
- O sistema filtra automaticamente por `tenantId` do usuário autenticado

### Formato de Referência

- A referência (mês/ano) sempre vem no formato `"YYYY-MM"` (ex: `"2017-08"`)
- Use este formato para ordenação e agrupamento

### Valores Monetários

- Todos os valores são números decimais (Double)
- Formate para exibição usando `toFixed(2)` ou bibliotecas de formatação
- Exemplo: `valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })`

### Status de Documentos

| Status | Descrição |
|--------|-----------|
| `PENDING` | Documento enviado, aguardando processamento |
| `PROCESSING` | Documento sendo processado |
| `PROCESSED` | Documento processado com sucesso |
| `ERROR` | Erro durante o processamento |

---

## 🔍 Troubleshooting

### Problemas Comuns

#### 404 Not Found ao buscar pessoa

**Causa**: 
- Pessoa não existe
- Pessoa existe mas não pertence ao tenant do usuário autenticado

**Solução**: 
- Verifique se o CPF/personId está correto
- Verifique se a pessoa pertence ao seu tenant

#### Documentos duplicados ao buscar por CPF

**Causa**: 
- Existem múltiplas pessoas com o mesmo CPF em diferentes tenants
- O endpoint `/{cpf}/documents` retorna documentos de todas as pessoas com aquele CPF (para SUPER_ADMIN) ou apenas do tenant do usuário

**Solução**: 
- ⭐ **Use o endpoint `/{personId}/documents-by-id`** quando você tem o `personId` disponível (vindo da lista de pessoas)
- Isso garante que apenas os documentos da pessoa específica sejam retornados
- O `personId` está disponível no campo `id` da resposta de `GET /api/v1/persons`

#### 204 No Content ao buscar entries

**Causa**: Documento existe mas não possui entries ainda (não foi processado)

**Solução**: 
- Verifique o status do documento
- Se o status for `PENDING`, processe o documento primeiro
- Se o status for `PROCESSED` mas não há entries, pode haver um erro no processamento

#### Matriz de rubricas vazia

**Causa**: Pessoa não possui documentos processados ou documentos não têm entries

**Solução**: 
- Verifique se a pessoa tem documentos
- Verifique se os documentos foram processados (`status: PROCESSED`)
- Verifique se os documentos têm entries

### Dicas de Implementação

1. **Cache de dados**: Considere cachear dados de pessoas e documentos para melhor performance
2. **Loading states**: Sempre mostre estados de carregamento durante requisições
3. **Error boundaries**: Implemente tratamento de erros adequado
4. **Refresh automático**: Implemente refresh automático de token antes de expirar
5. **Validação client-side**: Valide CPF antes de enviar requisições
6. **Formatação**: Formate CPF, valores monetários e datas para melhor UX

---

## 📞 Suporte

Para dúvidas ou problemas, consulte a documentação completa da API ou entre em contato com a equipe de desenvolvimento.

