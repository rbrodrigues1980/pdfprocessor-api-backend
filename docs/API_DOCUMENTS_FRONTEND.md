# 📄 API de Documentos - Documentação para Frontend

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Autenticação e Autorização](#autenticação-e-autorização)
3. [Isolamento Multi-Tenant](#isolamento-multi-tenant)
4. [Status dos Documentos](#status-dos-documentos)
5. [Endpoints](#endpoints)
6. [Modelos de Dados](#modelos-de-dados)
7. [Exemplos de Implementação](#exemplos-de-implementação)
8. [Tratamento de Erros](#tratamento-de-erros)

---

## 🎯 Visão Geral

A API de Documentos permite gerenciar o ciclo de vida completo de documentos PDF (contracheques da CAIXA e FUNCEF), desde o upload até o processamento e extração de dados.

**Base URL**: `http://localhost:8081/api/v1`

**Versão da API**: `v1`

### Fluxo de Trabalho

1. **Upload**: Enviar PDF → Documento criado com status `PENDING`
2. **Processamento**: Processar documento → Extrai rubricas → Status `PROCESSED`
3. **Consulta**: Visualizar documento, entries, resumo, páginas
4. **Reprocessamento**: Reprocessar documento já processado (se necessário)

---

## 🔐 Autenticação e Autorização

### Requisitos

Todos os endpoints de Documentos requerem:

1. **Autenticação JWT**: Token de acesso válido no header `Authorization`
2. **Roles permitidas**:
   - `SUPER_ADMIN`: Pode ver e gerenciar todos os documentos (de todos os tenants)
   - `TENANT_ADMIN`: Pode ver e gerenciar documentos do seu tenant
   - `TENANT_USER`: Pode visualizar documentos do seu tenant

### Headers Obrigatórios

```http
Authorization: Bearer {accessToken}
Content-Type: application/json  // Para endpoints JSON
Content-Type: multipart/form-data  // Para uploads
```

### Exemplo de Requisição Autenticada

```javascript
const headers = {
  'Authorization': `Bearer ${accessToken}`,
  'Content-Type': 'application/json'
};
```

---

## 🏢 Isolamento Multi-Tenant

### Como Funciona

O sistema aplica isolamento automático baseado no tenant do usuário:

#### SUPER_ADMIN
- **Vê**: Todos os documentos (de todos os tenants)
- **Pode criar**: Documentos para qualquer tenant (usando `X-Tenant-ID: {tenantId}`)
- **Pode processar/deletar**: Qualquer documento

#### TENANT_ADMIN / TENANT_USER
- **Vê**: Apenas documentos do seu próprio tenant
- **Pode criar**: Apenas documentos do seu próprio tenant (tenantId vem automaticamente do JWT)
- **Pode processar/deletar**: Apenas documentos do seu próprio tenant

### Header Especial para SUPER_ADMIN

Se você é `SUPER_ADMIN` e quer criar/visualizar documentos de um tenant específico:

```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: força um tenant específico
```

**Nota**: Se não enviar `X-Tenant-ID`, o SUPER_ADMIN verá todos os documentos.

---

## 📊 Status dos Documentos

Os documentos passam por diferentes status durante seu ciclo de vida:

| Status | Descrição | Ações Possíveis |
|--------|-----------|-----------------|
| `PENDING` | Documento enviado, aguardando processamento | Processar, Deletar |
| `PROCESSING` | Documento sendo processado | Aguardar conclusão |
| `PROCESSED` | Documento processado com sucesso | Visualizar, Reprocessar, Deletar |
| `ERROR` | Erro durante o processamento | Visualizar erro, Reprocessar, Deletar |

### Transições de Status

```
PENDING → PROCESSING → PROCESSED
PENDING → PROCESSING → ERROR
PROCESSED → PROCESSING → PROCESSED (reprocessamento)
ERROR → PROCESSING → PROCESSED (reprocessamento)
```

**Nota**: Documentos com status `PENDING` não podem ser reprocessados diretamente. Use `/process` primeiro.

---

## 📡 Endpoints

### 1. Upload de Documento Único

**POST** `/api/v1/documents/upload`

Faz upload de um único arquivo PDF.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

**Form Data:**
- `file` (obrigatório): Arquivo PDF
- `cpf` (obrigatório): CPF da pessoa (com ou sem formatação)
- `nome` (opcional): Nome da pessoa

**Limitações:**
- Tamanho máximo: 10MB
- Formato: PDF válido

#### Response

**Status:** `201 Created`

**Body:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PENDING",
  "tipoDetectado": "CAIXA"
}
```

#### Códigos de Status

- `201 Created`: Upload bem-sucedido
- `400 Bad Request`: Arquivo inválido ou não é PDF
- `401 Unauthorized`: Token inválido ou ausente
- `409 Conflict`: Documento duplicado (mesmo hash no mesmo tenant)
- `422 Unprocessable Entity`: CPF inválido
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface UploadDocumentResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipoDetectado: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
}

async function uploadDocument(
  accessToken: string,
  file: File,
  cpf: string,
  nome?: string
): Promise<UploadDocumentResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('cpf', cpf);
  if (nome) {
    formData.append('nome', nome);
  }

  const response = await fetch('http://localhost:8081/api/v1/documents/upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`
      // NÃO incluir Content-Type - o browser define automaticamente com boundary
    },
    body: formData
  });

  if (!response.ok) {
    if (response.status === 400) {
      throw new Error('Arquivo inválido. Deve ser um PDF válido.');
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 409) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Este arquivo já foi enviado anteriormente.');
    }
    if (response.status === 422) {
      throw new Error('CPF inválido. Verifique o formato.');
    }
    throw new Error(`Erro ao fazer upload: ${response.statusText}`);
  }

  return await response.json();
}
```

```javascript
// JavaScript puro
async function uploadDocument(accessToken, file, cpf, nome) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('cpf', cpf);
  if (nome) {
    formData.append('nome', nome);
  }

  try {
    const response = await fetch('http://localhost:8081/api/v1/documents/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      },
      body: formData
    });

    if (!response.ok) {
      if (response.status === 400) {
        throw new Error('Arquivo inválido. Deve ser um PDF válido.');
      }
      if (response.status === 401) {
        throw new Error('Token inválido ou expirado');
      }
      if (response.status === 409) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || 'Este arquivo já foi enviado anteriormente.');
      }
      if (response.status === 422) {
        throw new Error('CPF inválido. Verifique o formato.');
      }
      throw new Error(`Erro ao fazer upload: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Erro ao fazer upload:', error);
    throw error;
  }
}
```

---

### 2. Upload Múltiplo de Documentos

**POST** `/api/v1/documents/bulk-upload`

Faz upload de múltiplos arquivos PDF para uma pessoa.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

**Form Data:**
- `files` (obrigatório): Array de arquivos PDF
- `cpf` (obrigatório): CPF da pessoa
- `nome` (obrigatório): Nome da pessoa
- `matricula` (obrigatório): Matrícula da pessoa

#### Response

**Status:** `201 Created`

**Body:**
```json
{
  "cpf": "12345678900",
  "totalArquivos": 5,
  "sucessos": 4,
  "falhas": 1,
  "resultados": [
    {
      "filename": "contracheque1.pdf",
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PENDING",
      "tipoDetectado": "CAIXA",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "contracheque2.pdf",
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PENDING",
      "tipoDetectado": "FUNCEF",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "arquivo_invalido.pdf",
      "documentId": null,
      "status": null,
      "tipoDetectado": null,
      "sucesso": false,
      "erro": "Arquivo inválido. Deve ser um PDF válido."
    }
  ]
}
```

#### Códigos de Status

- `201 Created`: Upload iniciado (pode ter sucessos e falhas)
- `400 Bad Request`: Parâmetros inválidos
- `401 Unauthorized`: Token inválido ou ausente
- `422 Unprocessable Entity`: CPF inválido
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface BulkUploadItemResponse {
  filename: string;
  documentId: string | null;
  status: string | null;
  tipoDetectado: string | null;
  sucesso: boolean;
  erro: string | null;
}

interface BulkUploadResponse {
  cpf: string;
  totalArquivos: number;
  sucessos: number;
  falhas: number;
  resultados: BulkUploadItemResponse[];
}

async function bulkUploadDocuments(
  accessToken: string,
  files: File[],
  cpf: string,
  nome: string,
  matricula: string
): Promise<BulkUploadResponse> {
  const formData = new FormData();
  
  // Adicionar todos os arquivos
  files.forEach(file => {
    formData.append('files', file);
  });
  
  formData.append('cpf', cpf);
  formData.append('nome', nome);
  formData.append('matricula', matricula);

  const response = await fetch('http://localhost:8081/api/v1/documents/bulk-upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`
    },
    body: formData
  });

  if (!response.ok) {
    if (response.status === 400) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Parâmetros inválidos');
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 422) {
      throw new Error('CPF inválido. Verifique o formato.');
    }
    throw new Error(`Erro ao fazer upload múltiplo: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 3. Processar Documento

**POST** `/api/v1/documents/{id}/process`

Inicia o processamento de um documento com status `PENDING`.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `202 Accepted`

**Body:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "message": "Processamento iniciado"
}
```

#### Códigos de Status

- `202 Accepted`: Processamento iniciado
- `400 Bad Request`: PDF inválido
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado
- `409 Conflict`: Status inválido para processamento (ex: já está PROCESSING)
- `500 Internal Server Error`: Erro ao processar

#### Exemplo de Implementação

```typescript
interface ProcessDocumentResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  message: string;
  entries?: number;
  tipoDocumento?: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
}

async function processDocument(
  accessToken: string,
  documentId: string
): Promise<ProcessDocumentResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}/process`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 400) {
      throw new Error('PDF inválido ou corrompido.');
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    if (response.status === 409) {
      throw new Error('Documento não pode ser processado no status atual.');
    }
    throw new Error(`Erro ao processar documento: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 4. Reprocessar Documento

**POST** `/api/v1/documents/{id}/reprocess`

Reprocessa um documento que já foi processado (status `PROCESSED` ou `ERROR`).

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `202 Accepted`

**Body:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "message": "Reprocessamento iniciado"
}
```

#### Códigos de Status

- `202 Accepted`: Reprocessamento iniciado
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado
- `409 Conflict`: Status inválido (ex: PENDING não pode ser reprocessado)
- `500 Internal Server Error`: Erro ao reprocessar

**Nota**: Documentos com status `PENDING` não podem ser reprocessados. Use `/process` primeiro.

#### Exemplo de Implementação

```typescript
interface ReprocessResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  message: string;
}

async function reprocessDocument(
  accessToken: string,
  documentId: string
): Promise<ReprocessResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}/reprocess`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    if (response.status === 409) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Documento não pode ser reprocessado no status atual.');
    }
    throw new Error(`Erro ao reprocessar documento: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 5. Listar Documentos

**GET** `/api/v1/documents`

Lista documentos com filtros opcionais. Retorna apenas documentos do tenant do usuário (ou todos se SUPER_ADMIN).

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: apenas para SUPER_ADMIN
```

**Query Parameters:**
- `cpf` (opcional): Filtrar por CPF
- `ano` (opcional): Filtrar por ano (ex: 2018)
- `status` (opcional): Filtrar por status (`PENDING`, `PROCESSING`, `PROCESSED`, `ERROR`)
- `tipo` (opcional): Filtrar por tipo (`CAIXA`, `FUNCEF`, `CAIXA_FUNCEF`)
- `minEntries` (opcional): Mínimo de entries
- `maxEntries` (opcional): Máximo de entries

#### Response

**Status:** `200 OK`

**Body:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "cpf": "12345678900",
    "status": "PROCESSED",
    "tipo": "CAIXA",
    "ano": 2018,
    "entriesCount": 25,
    "dataUpload": "2024-01-15T10:30:00Z",
    "dataProcessamento": "2024-01-15T10:35:00Z",
    "erro": null
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "cpf": "12345678900",
    "status": "PROCESSED",
    "tipo": "FUNCEF",
    "ano": 2019,
    "entriesCount": 30,
    "dataUpload": "2024-01-16T14:20:00Z",
    "dataProcessamento": "2024-01-16T14:25:00Z",
    "erro": null
  }
]
```

#### Exemplo de Implementação

```typescript
interface DocumentResponse {
  id: string;
  cpf: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
  ano: number | null;
  entriesCount: number | null;
  dataUpload: string;
  dataProcessamento: string | null;
  erro: string | null;
}

interface DocumentFilters {
  cpf?: string;
  ano?: number;
  status?: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo?: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
  minEntries?: number;
  maxEntries?: number;
}

async function listDocuments(
  accessToken: string,
  filters?: DocumentFilters,
  tenantId?: string  // Apenas para SUPER_ADMIN
): Promise<DocumentResponse[]> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  // Construir query string
  const params = new URLSearchParams();
  if (filters?.cpf) params.append('cpf', filters.cpf);
  if (filters?.ano) params.append('ano', filters.ano.toString());
  if (filters?.status) params.append('status', filters.status);
  if (filters?.tipo) params.append('tipo', filters.tipo);
  if (filters?.minEntries) params.append('minEntries', filters.minEntries.toString());
  if (filters?.maxEntries) params.append('maxEntries', filters.maxEntries.toString());

  const url = `http://localhost:8081/api/v1/documents${params.toString() ? '?' + params.toString() : ''}`;

  const response = await fetch(url, {
    method: 'GET',
    headers
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 400) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Filtros inválidos');
    }
    throw new Error(`Erro ao listar documentos: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 6. Buscar Documento por ID

**GET** `/api/v1/documents/{id}`

Retorna detalhes completos de um documento.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "cpf": "12345678900",
  "status": "PROCESSED",
  "tipo": "CAIXA",
  "ano": 2018,
  "entriesCount": 25,
  "dataUpload": "2024-01-15T10:30:00Z",
  "dataProcessamento": "2024-01-15T10:35:00Z",
  "erro": null
}
```

#### Códigos de Status

- `200 OK`: Documento encontrado
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado ou não acessível pelo tenant
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
async function getDocumentById(
  accessToken: string,
  documentId: string
): Promise<DocumentResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado ou não acessível.');
    }
    throw new Error(`Erro ao buscar documento: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 7. Deletar Documento

**DELETE** `/api/v1/documents/{id}`

Exclui um documento e todas as suas entries associadas.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `204 No Content`

**Body:** Vazio

#### Códigos de Status

- `204 No Content`: Documento excluído com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado ou não acessível pelo tenant
- `500 Internal Server Error`: Erro ao excluir

#### Exemplo de Implementação

```typescript
async function deleteDocument(
  accessToken: string,
  documentId: string
): Promise<void> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}`,
    {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado ou não acessível.');
    }
    throw new Error(`Erro ao excluir documento: ${response.statusText}`);
  }
}
```

---

### 8. Obter Resumo do Documento

**GET** `/api/v1/documents/{id}/summary`

Retorna resumo estatístico do documento (rubricas, totais, etc.).

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "entriesCount": 25,
  "rubricasResumo": [
    {
      "codigo": "3430",
      "quantidade": 1,
      "total": 424.10
    },
    {
      "codigo": "1001",
      "quantidade": 1,
      "total": 5000.00
    }
  ]
}
```

#### Códigos de Status

- `200 OK`: Resumo gerado com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface DocumentRubricaSummary {
  codigo: string;
  quantidade: number;
  total: number;
}

interface DocumentSummaryResponse {
  documentId: string;
  entriesCount: number;
  rubricasResumo: DocumentRubricaSummary[];
}

async function getDocumentSummary(
  accessToken: string,
  documentId: string
): Promise<DocumentSummaryResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}/summary`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    throw new Error(`Erro ao obter resumo: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 9. Obter Páginas do Documento

**GET** `/api/v1/documents/{id}/pages`

Retorna informações sobre as páginas detectadas do documento.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "pages": [
    {
      "page": 1,
      "origem": "CAIXA"
    },
    {
      "page": 2,
      "origem": "CAIXA"
    },
    {
      "page": 3,
      "origem": "FUNCEF"
    }
  ]
}
```

#### Códigos de Status

- `200 OK`: Páginas encontradas
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface PageInfo {
  page: number;
  origem: 'CAIXA' | 'FUNCEF';
}

interface DocumentPageResponse {
  documentId: string;
  pages: PageInfo[];
}

async function getDocumentPages(
  accessToken: string,
  documentId: string
): Promise<DocumentPageResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}/pages`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    throw new Error(`Erro ao obter páginas: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 10. Obter Entries do Documento

**GET** `/api/v1/documents/{id}/entries`

Retorna todas as entries (rubricas extraídas) de um documento.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

#### Response

**Status:** `200 OK` ou `204 No Content`

**Body:**
```json
[
  {
    "id": "507f1f77bcf86cd799439013",
    "documentId": "507f1f77bcf86cd799439011",
    "rubricaCodigo": "3430",
    "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "referencia": "2018-01",
    "valor": 424.10,
    "origem": "FUNCEF",
    "pagina": 1
  },
  {
    "id": "507f1f77bcf86cd799439014",
    "documentId": "507f1f77bcf86cd799439011",
    "rubricaCodigo": "1001",
    "rubricaDescricao": "SALÁRIO BASE",
    "referencia": "2018-01",
    "valor": 5000.00,
    "origem": "CAIXA",
    "pagina": 1
  }
]
```

#### Códigos de Status

- `200 OK`: Entries encontradas
- `204 No Content`: Nenhuma entry encontrada
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Documento não encontrado
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface EntryResponse {
  id: string;
  documentId: string;
  rubricaCodigo: string;
  rubricaDescricao: string;
  referencia: string;  // Formato: YYYY-MM
  valor: number;
  origem: 'CAIXA' | 'FUNCEF';
  pagina: number;
}

async function getDocumentEntries(
  accessToken: string,
  documentId: string
): Promise<EntryResponse[]> {
  const response = await fetch(
    `http://localhost:8081/api/v1/documents/${documentId}/entries`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );

  if (!response.ok) {
    if (response.status === 204) {
      return []; // Nenhuma entry encontrada
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    throw new Error(`Erro ao obter entries: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 11. Obter Entries Paginadas do Documento

**GET** `/api/v1/documents/{id}/entries/paged`

Retorna entries paginadas de um documento.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID do documento

**Query Parameters:**
- `page` (opcional, padrão: 0): Número da página (0-indexed)
- `size` (opcional, padrão: 20): Tamanho da página
- `sortBy` (opcional, padrão: "referencia"): Campo para ordenação
- `sortDirection` (opcional, padrão: "asc"): Direção (`asc` ou `desc`)

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "content": [
    {
      "id": "507f1f77bcf86cd799439013",
      "documentId": "507f1f77bcf86cd799439011",
      "rubricaCodigo": "3430",
      "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
      "referencia": "2018-01",
      "valor": 424.10,
      "origem": "FUNCEF",
      "pagina": 1
    }
  ],
  "totalElements": 25,
  "totalPages": 2,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

#### Exemplo de Implementação

```typescript
interface PagedEntriesResponse {
  content: EntryResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

interface PaginationParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}

async function getDocumentEntriesPaged(
  accessToken: string,
  documentId: string,
  pagination?: PaginationParams
): Promise<PagedEntriesResponse> {
  const params = new URLSearchParams();
  if (pagination?.page !== undefined) params.append('page', pagination.page.toString());
  if (pagination?.size !== undefined) params.append('size', pagination.size.toString());
  if (pagination?.sortBy) params.append('sortBy', pagination.sortBy);
  if (pagination?.sortDirection) params.append('sortDirection', pagination.sortDirection);

  const url = `http://localhost:8081/api/v1/documents/${documentId}/entries/paged${params.toString() ? '?' + params.toString() : ''}`;

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error('Documento não encontrado.');
    }
    throw new Error(`Erro ao obter entries paginadas: ${response.statusText}`);
  }

  return await response.json();
}
```

---

## 📊 Modelos de Dados

### UploadDocumentResponse

```typescript
interface UploadDocumentResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipoDetectado: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
}
```

### BulkUploadResponse

```typescript
interface BulkUploadItemResponse {
  filename: string;
  documentId: string | null;
  status: string | null;
  tipoDetectado: string | null;
  sucesso: boolean;
  erro: string | null;
}

interface BulkUploadResponse {
  cpf: string;
  totalArquivos: number;
  sucessos: number;
  falhas: number;
  resultados: BulkUploadItemResponse[];
}
```

### DocumentResponse

```typescript
interface DocumentResponse {
  id: string;
  cpf: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
  ano: number | null;
  entriesCount: number | null;
  dataUpload: string;  // ISO 8601
  dataProcessamento: string | null;  // ISO 8601
  erro: string | null;
}
```

### ProcessDocumentResponse

```typescript
interface ProcessDocumentResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  message: string;
  entries?: number;
  tipoDocumento?: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF';
}
```

### ReprocessResponse

```typescript
interface ReprocessResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  message: string;
}
```

### DocumentSummaryResponse

```typescript
interface DocumentRubricaSummary {
  codigo: string;
  quantidade: number;
  total: number;
}

interface DocumentSummaryResponse {
  documentId: string;
  entriesCount: number;
  rubricasResumo: DocumentRubricaSummary[];
}
```

### DocumentPageResponse

```typescript
interface PageInfo {
  page: number;
  origem: 'CAIXA' | 'FUNCEF';
}

interface DocumentPageResponse {
  documentId: string;
  pages: PageInfo[];
}
```

### EntryResponse

```typescript
interface EntryResponse {
  id: string;
  documentId: string;
  rubricaCodigo: string;
  rubricaDescricao: string;
  referencia: string;  // Formato: YYYY-MM
  valor: number;
  origem: 'CAIXA' | 'FUNCEF';
  pagina: number;
}
```

### PagedEntriesResponse

```typescript
interface PagedEntriesResponse {
  content: EntryResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
```

---

## 💻 Exemplos de Implementação

### React com TypeScript - Hook para Upload

```typescript
import { useState } from 'react';

export function useDocumentUpload(accessToken: string) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upload = async (file: File, cpf: string, nome?: string) => {
    setUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('cpf', cpf);
      if (nome) {
        formData.append('nome', nome);
      }

      const response = await fetch('http://localhost:8081/api/v1/documents/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`
        },
        body: formData
      });

      if (!response.ok) {
        if (response.status === 409) {
          throw new Error('Este arquivo já foi enviado anteriormente.');
        }
        throw new Error('Erro ao fazer upload');
      }

      const result = await response.json();
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erro desconhecido';
      setError(message);
      throw err;
    } finally {
      setUploading(false);
    }
  };

  return { upload, uploading, error };
}

// Componente de exemplo
function DocumentUploadForm() {
  const accessToken = 'seu-token-aqui';
  const { upload, uploading, error } = useDocumentUpload(accessToken);
  const [file, setFile] = useState<File | null>(null);
  const [cpf, setCpf] = useState('');
  const [nome, setNome] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;

    try {
      const result = await upload(file, cpf, nome);
      console.log('Upload bem-sucedido:', result);
      alert(`Documento ${result.documentId} enviado com sucesso!`);
    } catch (err) {
      console.error('Erro no upload:', err);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="file"
        accept=".pdf"
        onChange={(e) => setFile(e.target.files?.[0] || null)}
      />
      <input
        type="text"
        placeholder="CPF"
        value={cpf}
        onChange={(e) => setCpf(e.target.value)}
      />
      <input
        type="text"
        placeholder="Nome (opcional)"
        value={nome}
        onChange={(e) => setNome(e.target.value)}
      />
      <button type="submit" disabled={uploading || !file}>
        {uploading ? 'Enviando...' : 'Enviar'}
      </button>
      {error && <div style={{ color: 'red' }}>{error}</div>}
    </form>
  );
}
```

### React com TypeScript - Hook para Listar Documentos

```typescript
import { useState, useEffect } from 'react';

export function useDocuments(accessToken: string, filters?: DocumentFilters) {
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchDocuments() {
      try {
        setLoading(true);
        const docs = await listDocuments(accessToken, filters);
        setDocuments(docs);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Erro desconhecido');
      } finally {
        setLoading(false);
      }
    }

    if (accessToken) {
      fetchDocuments();
    }
  }, [accessToken, JSON.stringify(filters)]);

  return { documents, loading, error };
}
```

---

## ⚠️ Tratamento de Erros

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|------------|------------------|
| `200` | Sucesso | Processar resposta normalmente |
| `201` | Criado | Documento criado com sucesso |
| `202` | Accepted | Processamento iniciado (aguardar conclusão) |
| `204` | No Content | Operação bem-sucedida (sem conteúdo) |
| `400` | Bad Request | Validar dados enviados (arquivo, parâmetros) |
| `401` | Unauthorized | Token inválido ou expirado - fazer logout e reautenticar |
| `404` | Not Found | Documento não encontrado ou não acessível |
| `409` | Conflict | Documento duplicado ou status inválido |
| `422` | Unprocessable Entity | CPF inválido |
| `500` | Internal Server Error | Erro do servidor - tentar novamente ou contatar suporte |

### Exemplo de Tratamento de Erros

```typescript
async function gerenciarDocumento(
  accessToken: string,
  operacao: string,
  dados?: any
) {
  try {
    let response: Response;
    const headers: HeadersInit = {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    };

    switch (operacao) {
      case 'listar':
        response = await fetch('http://localhost:8081/api/v1/documents', {
          headers
        });
        break;
        
      case 'processar':
        response = await fetch(
          `http://localhost:8081/api/v1/documents/${dados.id}/process`,
          { method: 'POST', headers }
        );
        break;
        
      // ... outros casos
    }

    if (!response.ok) {
      switch (response.status) {
        case 400:
          throw new Error('Dados inválidos. Verifique os campos enviados.');
        case 401:
          window.location.href = '/login';
          throw new Error('Sessão expirada. Faça login novamente.');
        case 404:
          throw new Error('Documento não encontrado ou não acessível.');
        case 409:
          const error = await response.json().catch(() => ({}));
          throw new Error(error.error || 'Conflito: documento duplicado ou status inválido.');
        case 422:
          throw new Error('CPF inválido. Verifique o formato.');
        case 500:
          throw new Error('Erro interno do servidor. Tente novamente mais tarde.');
        default:
          throw new Error(`Erro desconhecido: ${response.statusText}`);
      }
    }

    return await response.json();
  } catch (error) {
    console.error('Erro na operação de documento:', error);
    throw error;
  }
}
```

---

## 📝 Notas Importantes

### 1. Upload de Arquivos

- **Formato**: Use `FormData` para uploads
- **Tamanho máximo**: 10MB por arquivo
- **Tipo**: Apenas PDFs válidos
- **Content-Type**: Não defina manualmente - o browser define automaticamente com boundary

### 2. Duplicidade de Arquivos

- O sistema verifica duplicidade por hash SHA-256
- Arquivos duplicados são detectados **por tenant**
- Mesmo arquivo pode existir em tenants diferentes
- Tentativa de upload duplicado retorna `409 Conflict`

### 3. Processamento Assíncrono

- O processamento é **assíncrono** e não bloqueante
- Após chamar `/process`, o status muda para `PROCESSING`
- Use polling ou WebSockets para verificar quando o status muda para `PROCESSED` ou `ERROR`
- Recomendado: verificar status a cada 2-5 segundos durante processamento

### 4. Paginação

- Use `/entries/paged` para documentos com muitas entries
- Tamanho padrão da página: 20 itens
- Campos ordenáveis: `referencia`, `valor`, `rubricaCodigo`

### 5. Status e Transições

- Documentos `PENDING` devem ser processados antes de reprocessar
- Documentos `PROCESSED` ou `ERROR` podem ser reprocessados
- Documentos `PROCESSING` não podem ser processados novamente até concluir

### 6. CORS

- Certifique-se de que o backend está configurado para aceitar requisições do seu domínio frontend
- Verifique as configurações de CORS no `SecurityConfig.java`

---

## 🔗 Links Úteis

- [Documentação Completa das APIs](./API_COMPLETA_E_ARQUITETURA.md)
- [Documentação de Autenticação](./API_AUTH_FRONTEND.md)
- [Documentação de Tenants](./API_TENANTS_FRONTEND.md)
- [Documentação de Rubricas](./API_RUBRICAS_FRONTEND.md)
- Swagger UI: `http://localhost:8081/swagger-ui.html`

---

**Última atualização**: Janeiro 2024

