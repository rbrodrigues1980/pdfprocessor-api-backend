# 📚 Documentação Completa das APIs e Arquitetura Clean Architecture

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Documentação Completa das APIs](#documentação-completa-das-apis)
3. [Clean Architecture - Guia Completo](#clean-architecture---guia-completo)
4. [Comparação: MVC vs Clean Architecture](#comparação-mvc-vs-clean-architecture)
5. [Mapeamento de Componentes](#mapeamento-de-componentes)
6. [Fluxo de Dados na Arquitetura](#fluxo-de-dados-na-arquitetura)

---

## 🎯 Visão Geral

Este projeto é uma API REST para processamento de documentos PDF (contracheques da CAIXA e FUNCEF), seguindo os princípios da **Clean Architecture**. A API está disponível em `/api/v1` e utiliza Spring WebFlux para processamento reativo.

**Base URL**: `http://localhost:8080/api/v1`

---

## 📡 Documentação Completa das APIs

### 🔐 1. Autenticação (`/api/v1/auth`)

#### POST `/api/v1/auth/login`

Realiza login e retorna tokens de acesso.

**Request Body:**
```json
{
  "username": "usuario@exemplo.com",
  "password": "senha123"
}
```

**Response 200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Códigos de Status:**
- `200 OK`: Login bem-sucedido
- `401 Unauthorized`: Credenciais inválidas

---

### 📄 2. Documentos (`/api/v1/documents`)

#### POST `/api/v1/documents/upload`

Faz upload de um único arquivo PDF.

**Content-Type**: `multipart/form-data`

**Parâmetros:**
- `file` (obrigatório): Arquivo PDF
- `cpf` (obrigatório): CPF da pessoa (com ou sem formatação)
- `nome` (opcional): Nome da pessoa

**Response 201 Created:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PENDING",
  "tipoDetectado": "CAIXA"
}
```

**Códigos de Status:**
- `201 Created`: Upload bem-sucedido
- `400 Bad Request`: Arquivo inválido ou não é PDF
- `422 Unprocessable Entity`: CPF inválido
- `409 Conflict`: Documento duplicado (mesmo hash)
- `500 Internal Server Error`: Erro interno

**Exemplo cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -F "file=@contracheque.pdf" \
  -F "cpf=12345678900" \
  -F "nome=João Silva"
```

---

#### POST `/api/v1/documents/bulk-upload`

Faz upload de múltiplos arquivos PDF para uma pessoa.

**Content-Type**: `multipart/form-data`

**Parâmetros:**
- `files` (obrigatório): Array de arquivos PDF
- `cpf` (obrigatório): CPF da pessoa
- `nome` (obrigatório): Nome da pessoa
- `matricula` (obrigatório): Matrícula da pessoa

**Response 201 Created:**
```json
{
  "cpf": "12345678900",
  "totalArquivos": 5,
  "sucessos": 4,
  "falhas": 1,
  "itens": [
    {
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PENDING",
      "tipoDetectado": "CAIXA",
      "nomeArquivo": "contracheque1.pdf"
    },
    {
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PENDING",
      "tipoDetectado": "FUNCEF",
      "nomeArquivo": "contracheque2.pdf"
    }
  ],
  "erros": [
    {
      "nomeArquivo": "arquivo_invalido.pdf",
      "erro": "Arquivo inválido. Deve ser um PDF válido."
    }
  ]
}
```

**Códigos de Status:**
- `201 Created`: Upload iniciado (pode ter sucessos e falhas)
- `400 Bad Request`: Parâmetros inválidos
- `422 Unprocessable Entity`: CPF inválido
- `500 Internal Server Error`: Erro interno

---

#### POST `/api/v1/documents/{id}/process`

Processa um documento que está com status `PENDING`.

**Parâmetros de URL:**
- `id`: ID do documento

**Response 202 Accepted:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "message": "Processamento iniciado"
}
```

**Códigos de Status:**
- `202 Accepted`: Processamento iniciado
- `404 Not Found`: Documento não encontrado
- `409 Conflict`: Status inválido para processamento
- `400 Bad Request`: PDF inválido
- `500 Internal Server Error`: Erro ao processar

---

#### POST `/api/v1/documents/{id}/reprocess`

Reprocessa um documento que já foi processado (status `PROCESSED` ou `ERROR`).

**Parâmetros de URL:**
- `id`: ID do documento

**Response 202 Accepted:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "message": "Reprocessamento iniciado"
}
```

**Códigos de Status:**
- `202 Accepted`: Reprocessamento iniciado
- `404 Not Found`: Documento não encontrado
- `409 Conflict`: Status inválido (ex: PENDING não pode ser reprocessado)
- `500 Internal Server Error`: Erro ao reprocessar

---

#### GET `/api/v1/documents/{id}`

Retorna detalhes completos de um documento.

**Parâmetros de URL:**
- `id`: ID do documento

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "cpf": "12345678900",
  "nome": "João Silva",
  "status": "PROCESSED",
  "tipo": "CAIXA",
  "ano": 2018,
  "mes": 1,
  "numeroPaginas": 4,
  "totalEntries": 25,
  "fileHash": "a1b2c3d4e5f6...",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Documento encontrado
- `404 Not Found`: Documento não encontrado
- `500 Internal Server Error`: Erro interno

---

#### GET `/api/v1/documents`

Lista documentos com filtros opcionais.

**Query Parameters:**
- `cpf` (opcional): Filtrar por CPF
- `ano` (opcional): Filtrar por ano (ex: 2018)
- `status` (opcional): Filtrar por status (`PENDING`, `PROCESSING`, `PROCESSED`, `ERROR`)
- `tipo` (opcional): Filtrar por tipo (`CAIXA`, `FUNCEF`, `MISTO`)
- `minEntries` (opcional): Mínimo de entries
- `maxEntries` (opcional): Máximo de entries

**Response 200 OK:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "cpf": "12345678900",
    "status": "PROCESSED",
    "tipo": "CAIXA",
    "ano": 2018
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "cpf": "12345678900",
    "status": "PROCESSED",
    "tipo": "FUNCEF",
    "ano": 2019
  }
]
```

**Exemplo:**
```bash
GET /api/v1/documents?cpf=12345678900&ano=2018&status=PROCESSED
```

---

#### GET `/api/v1/documents/{id}/pages`

Retorna informações sobre as páginas detectadas do documento.

**Parâmetros de URL:**
- `id`: ID do documento

**Response 200 OK:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "pages": [
    {
      "pageNumber": 1,
      "type": "CAIXA",
      "detected": true
    },
    {
      "pageNumber": 2,
      "type": "CAIXA",
      "detected": true
    },
    {
      "pageNumber": 3,
      "type": "FUNCEF",
      "detected": true
    }
  ]
}
```

---

#### GET `/api/v1/documents/{id}/summary`

Retorna resumo estatístico do documento (rubricas, totais, etc.).

**Parâmetros de URL:**
- `id`: ID do documento

**Response 200 OK:**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "entriesCount": 25,
  "rubricasResumo": [
    {
      "codigo": "3430",
      "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
      "total": 424.10,
      "ocorrencias": 1
    },
    {
      "codigo": "1001",
      "descricao": "SALÁRIO BASE",
      "total": 5000.00,
      "ocorrencias": 1
    }
  ],
  "totalGeral": 5424.10
}
```

---

#### GET `/api/v1/documents/{id}/entries`

Retorna todas as entries (rubricas extraídas) de um documento.

**Parâmetros de URL:**
- `id`: ID do documento

**Response 200 OK:**
```json
[
  {
    "id": "507f1f77bcf86cd799439013",
    "documentoId": "507f1f77bcf86cd799439011",
    "codigo": "3430",
    "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "referencia": "2018-01",
    "ano": 2018,
    "mes": 1,
    "valor": 424.10,
    "origem": "FUNCEF"
  },
  {
    "id": "507f1f77bcf86cd799439014",
    "documentoId": "507f1f77bcf86cd799439011",
    "codigo": "1001",
    "descricao": "SALÁRIO BASE",
    "referencia": "2018-01",
    "ano": 2018,
    "mes": 1,
    "valor": 5000.00,
    "origem": "CAIXA"
  }
]
```

**Códigos de Status:**
- `200 OK`: Entries encontradas
- `204 No Content`: Nenhuma entry encontrada
- `404 Not Found`: Documento não encontrado

---

#### GET `/api/v1/documents/{id}/entries/paged`

Retorna entries paginadas de um documento.

**Parâmetros de URL:**
- `id`: ID do documento

**Query Parameters:**
- `page` (opcional, padrão: 0): Número da página
- `size` (opcional, padrão: 20): Tamanho da página
- `sortBy` (opcional, padrão: "referencia"): Campo para ordenação
- `sortDirection` (opcional, padrão: "asc"): Direção (`asc` ou `desc`)

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": "507f1f77bcf86cd799439013",
      "codigo": "3430",
      "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
      "valor": 424.10,
      "referencia": "2018-01"
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

---

#### DELETE `/api/v1/documents/{id}`

Exclui um documento e todas as suas entries associadas.

**Parâmetros de URL:**
- `id`: ID do documento

**Códigos de Status:**
- `204 No Content`: Documento excluído com sucesso
- `404 Not Found`: Documento não encontrado
- `500 Internal Server Error`: Erro ao excluir

---

### 📊 3. Entries (Rubricas Extraídas) (`/api/v1/entries`)

#### GET `/api/v1/entries`

Lista entries com filtros opcionais.

**Query Parameters:**
- `cpf` (opcional): Filtrar por CPF
- `rubrica` (opcional): Filtrar por código de rubrica
- `ano` (opcional): Filtrar por ano
- `mes` (opcional): Filtrar por mês (1-12)
- `origem` (opcional): Filtrar por origem (`CAIXA`, `FUNCEF`)
- `documentoId` (opcional): Filtrar por ID do documento
- `minValor` (opcional): Valor mínimo
- `maxValor` (opcional): Valor máximo

**Response 200 OK:**
```json
[
  {
    "id": "507f1f77bcf86cd799439013",
    "documentoId": "507f1f77bcf86cd799439011",
    "codigo": "3430",
    "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "referencia": "2018-01",
    "ano": 2018,
    "mes": 1,
    "valor": 424.10,
    "origem": "FUNCEF"
  }
]
```

**Exemplo:**
```bash
GET /api/v1/entries?cpf=12345678900&ano=2018&origem=CAIXA
```

---

### 👤 4. Pessoas (`/api/v1/persons`)

#### GET `/api/v1/persons/{cpf}/documents`

Lista todos os documentos de uma pessoa.

**Parâmetros de URL:**
- `cpf`: CPF da pessoa

**Response 200 OK:**
```json
{
  "cpf": "12345678900",
  "nome": "João Silva",
  "matricula": "0437412",
  "documentos": [
    {
      "id": "507f1f77bcf86cd799439011",
      "status": "PROCESSED",
      "tipo": "CAIXA",
      "ano": 2018,
      "mes": 1
    },
    {
      "id": "507f1f77bcf86cd799439012",
      "status": "PROCESSED",
      "tipo": "FUNCEF",
      "ano": 2019,
      "mes": 2
    }
  ]
}
```

**Códigos de Status:**
- `200 OK`: Documentos encontrados
- `404 Not Found`: Pessoa não encontrada

---

#### GET `/api/v1/persons/{cpf}/entries`

Retorna todas as entries de todos os documentos de uma pessoa.

**Parâmetros de URL:**
- `cpf`: CPF da pessoa

**Response 200 OK:**
```json
{
  "cpf": "12345678900",
  "totalEntries": 50,
  "entries": [
    {
      "id": "507f1f77bcf86cd799439013",
      "documentoId": "507f1f77bcf86cd799439011",
      "codigo": "3430",
      "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
      "referencia": "2018-01",
      "valor": 424.10,
      "origem": "FUNCEF"
    }
  ]
}
```

---

#### GET `/api/v1/persons/{cpf}/consolidated`

Retorna consolidação matricial de todas as rubricas de uma pessoa.

**Parâmetros de URL:**
- `cpf`: CPF da pessoa

**Query Parameters:**
- `ano` (opcional): Filtrar por um ano específico (ex: "2018")
- `origem` (opcional): Filtrar por origem (`CAIXA` ou `FUNCEF`)

**Response 200 OK:**
```json
{
  "cpf": "12345678900",
  "nome": "João Silva",
  "anos": [2017, 2018, 2019],
  "totalGeral": 150000.00,
  "rubricas": [
    {
      "codigo": "3430",
      "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
      "total": 4241.00,
      "valoresPorReferencia": {
        "2017-01": 424.10,
        "2017-02": 424.10,
        "2018-01": 424.10
      }
    },
    {
      "codigo": "1001",
      "descricao": "SALÁRIO BASE",
      "total": 60000.00,
      "valoresPorReferencia": {
        "2017-01": 5000.00,
        "2017-02": 5000.00
      }
    }
  ]
}
```

**Códigos de Status:**
- `200 OK`: Consolidação gerada
- `204 No Content`: Nenhuma entry encontrada
- `404 Not Found`: Pessoa não encontrada
- `400 Bad Request`: Ano ou origem inválidos

**Exemplo:**
```bash
GET /api/v1/persons/12345678900/consolidated?ano=2018&origem=CAIXA
```

---

#### GET `/api/v1/persons/{cpf}/excel`

Gera arquivo Excel (.xlsx) com a consolidação de todas as rubricas de uma pessoa.

**Parâmetros de URL:**
- `cpf`: CPF da pessoa

**Query Parameters:**
- `ano` (opcional): Filtrar por um ano específico
- `origem` (opcional): Filtrar por origem (`CAIXA` ou `FUNCEF`)

**Response 200 OK:**
- **Content-Type**: `application/octet-stream`
- **Content-Disposition**: `attachment; filename="consolidacao_12345678900_2018.xlsx"`
- **Body**: Arquivo Excel binário

**Códigos de Status:**
- `200 OK`: Excel gerado com sucesso
- `204 No Content`: Nenhuma entry encontrada
- `404 Not Found`: Pessoa não encontrada
- `400 Bad Request`: Ano ou origem inválidos
- `500 Internal Server Error`: Erro ao gerar Excel

**Exemplo:**
```bash
curl -X GET "http://localhost:8080/api/v1/persons/12345678900/excel?ano=2018" \
  -H "Authorization: Bearer {token}" \
  -o consolidacao.xlsx
```

---

### 🏷️ 5. Rubricas (Tabela Mestra) (`/api/v1/rubricas`)

**⚠️ Isolamento Multi-Tenant**: SUPER_ADMIN vê todas as rubricas, cada tenant vê apenas globais + suas próprias.

#### POST `/api/v1/rubricas`

Cria uma nova rubrica na tabela mestra.

**Headers:**
```
Authorization: Bearer {accessToken}
X-Tenant-ID: GLOBAL  // Opcional: apenas para SUPER_ADMIN criar rubrica global
```

**Request Body:**
```json
{
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária"
}
```

**Response 201 Created:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "GLOBAL",
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária",
  "ativo": true
}
```

**Códigos de Status:**
- `201 Created`: Rubrica criada
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Usuário não tem permissão
- `409 Conflict`: Rubrica já existe (no mesmo escopo)

---

#### GET `/api/v1/rubricas`

Lista todas as rubricas ou apenas as ativas. Retorna rubricas globais + do tenant do usuário.

**Query Parameters:**
- `apenasAtivas` (opcional, padrão: false): Se `true`, retorna apenas rubricas ativas

**Response 200 OK:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "tenantId": "GLOBAL",
    "codigo": "3430",
    "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "categoria": "Extraordinária",
    "ativo": true
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "codigo": "1001",
    "descricao": "SALÁRIO BASE",
    "categoria": "Remuneração",
    "ativo": true
  }
]
```

**Exemplo:**
```bash
GET /api/v1/rubricas?apenasAtivas=true
```

---

#### GET `/api/v1/rubricas/{codigo}`

Busca uma rubrica específica por código.

**Parâmetros de URL:**
- `codigo`: Código da rubrica

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "GLOBAL",
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária",
  "ativo": true
}
```

**Códigos de Status:**
- `200 OK`: Rubrica encontrada
- `401 Unauthorized`: Token inválido
- `404 Not Found`: Rubrica não encontrada ou não acessível pelo tenant

---

#### PUT `/api/v1/rubricas/{codigo}`

Atualiza uma rubrica existente.

**Parâmetros de URL:**
- `codigo`: Código da rubrica

**Request Body:**
```json
{
  "descricao": "NOVA DESCRIÇÃO",
  "categoria": "Nova Categoria"
}
```

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "GLOBAL",
  "codigo": "3430",
  "descricao": "NOVA DESCRIÇÃO",
  "categoria": "Nova Categoria",
  "ativo": true
}
```

**Códigos de Status:**
- `200 OK`: Rubrica atualizada
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Usuário não tem permissão para editar esta rubrica
- `404 Not Found`: Rubrica não encontrada

---

#### DELETE `/api/v1/rubricas/{codigo}`

Desativa uma rubrica (soft delete - não remove do banco).

**Parâmetros de URL:**
- `codigo`: Código da rubrica

**Códigos de Status:**
- `200 OK`: Rubrica desativada
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Usuário não tem permissão para desativar esta rubrica
- `404 Not Found`: Rubrica não encontrada

**📖 Documentação completa para frontend:** [API_RUBRICAS_FRONTEND.md](./API_RUBRICAS_FRONTEND.md)

---

### 🏢 6. Tenants (`/api/v1/tenants`)

**⚠️ Requer role SUPER_ADMIN**

Gerenciamento de tenants (empresas) no sistema. Apenas usuários com role `SUPER_ADMIN` podem acessar estes endpoints.

#### GET `/api/v1/tenants`

Lista todos os tenants cadastrados no sistema.

**Headers:**
```
Authorization: Bearer {accessToken}
```

**Response 200 OK:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nome": "Empresa ABC Ltda",
    "dominio": "empresaabc.com.br",
    "ativo": true,
    "createdAt": "2024-01-15T10:30:00Z"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "nome": "Empresa XYZ S.A.",
    "dominio": "empresaxyz.com.br",
    "ativo": true,
    "createdAt": "2024-01-16T14:20:00Z"
  }
]
```

**Códigos de Status:**
- `200 OK`: Lista retornada com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)

---

#### POST `/api/v1/tenants`

Cria um novo tenant (empresa) no sistema.

**Request Body:**
```json
{
  "nome": "Nova Empresa Ltda",
  "dominio": "novaempresa.com.br"
}
```

**Campos:**
- `nome` (obrigatório): Nome da empresa
- `dominio` (opcional): Domínio da empresa

**Response 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "nome": "Nova Empresa Ltda",
  "dominio": "novaempresa.com.br",
  "ativo": true,
  "createdAt": "2024-01-17T09:15:00Z"
}
```

**Códigos de Status:**
- `201 Created`: Tenant criado com sucesso
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)
- `409 Conflict`: Tenant com este nome já existe
- `500 Internal Server Error`: Erro interno

---

#### GET `/api/v1/tenants/{id}`

Retorna os detalhes de um tenant específico.

**Parâmetros de URL:**
- `id`: ID único do tenant (UUID)

**Response 200 OK:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Empresa ABC Ltda",
  "dominio": "empresaabc.com.br",
  "ativo": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Tenant encontrado
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)
- `404 Not Found`: Tenant não encontrado

**📖 Documentação completa para frontend:** [API_TENANTS_FRONTEND.md](./API_TENANTS_FRONTEND.md)

---

### 🔧 7. Sistema (`/api/v1/system`)

#### GET `/api/v1/system/databases`

Lista todos os bancos de dados MongoDB disponíveis.

**Response 200 OK:**
```json
["pdfprocessor", "admin", "local"]
```

---

#### DELETE `/api/v1/system/clean-uploads`

**⚠️ ATENÇÃO: Operação destrutiva!**

Remove todos os dados de upload do sistema:
- Todos os documentos (`payroll_documents`)
- Todas as entries (`payroll_entries`)
- Todas as pessoas (`persons`)
- Todos os arquivos do GridFS

**NÃO remove rubricas** (tabela mestra é mantida).

**Response 200 OK:**
```json
{
  "status": "success",
  "message": "Todos os dados de upload foram removidos com sucesso. Rubricas foram mantidas.",
  "payroll_documents_deleted": 150,
  "payroll_entries_deleted": 3500,
  "persons_deleted": 25,
  "gridfs_files_deleted": 150
}
```

---

## 🏗️ Clean Architecture - Guia Completo

### O que é Clean Architecture?

A **Clean Architecture** (Arquitetura Limpa) é um padrão arquitetural que organiza o código em camadas concêntricas, onde:

1. **As camadas mais internas não dependem das camadas mais externas**
2. **A lógica de negócio está isolada de frameworks e tecnologias**
3. **A dependência sempre aponta para dentro** (camadas externas dependem das internas)

### Por que usar Clean Architecture?

- ✅ **Testabilidade**: Lógica de negócio pode ser testada sem banco de dados ou frameworks
- ✅ **Independência**: Pode trocar frameworks (Spring → Quarkus) sem afetar o domínio
- ✅ **Manutenibilidade**: Código organizado e fácil de entender
- ✅ **Escalabilidade**: Fácil adicionar novas funcionalidades

---

## 📐 Estrutura das Camadas

O projeto está organizado em 4 camadas principais:

```
┌─────────────────────────────────────────┐
│   INTERFACES (Camada Externa)          │  ← Controllers, DTOs
├─────────────────────────────────────────┤
│   APPLICATION (Casos de Uso)           │  ← Use Cases, Orquestração
├─────────────────────────────────────────┤
│   DOMAIN (Núcleo)                       │  ← Entidades, Regras de Negócio
├─────────────────────────────────────────┤
│   INFRASTRUCTURE (Implementações)       │  ← Repositórios, Serviços Externos
└─────────────────────────────────────────┘
```

### Direção das Dependências

```
Interfaces → Application → Domain ← Infrastructure
```

**Regra de Ouro**: A camada `Domain` **NUNCA** depende de nada externo. Ela é o coração do sistema.

---

## 🔍 Detalhamento de Cada Camada

### 1️⃣ DOMAIN (Camada de Domínio) - O Coração do Sistema

**Localização**: `src/main/java/br/com/verticelabs/pdfprocessor/domain/`

Esta é a camada mais importante e **não depende de NADA**. Ela contém:

#### 📦 O que está aqui:

**a) Entidades (Models)**
- `Person.java` - Representa uma pessoa
- `PayrollDocument.java` - Representa um documento de contracheque
- `PayrollEntry.java` - Representa uma rubrica extraída
- `Rubrica.java` - Representa uma rubrica da tabela mestra
- `User.java` - Representa um usuário do sistema

**Exemplo:**
```java
@Document(collection = "persons")
public class Person {
    @Id
    private String id;
    private String cpf;
    private String nome;
    private List<String> documentos;
}
```

**b) Interfaces de Repositórios**
- `PersonRepository.java` - Interface para buscar/salvar pessoas
- `PayrollDocumentRepository.java` - Interface para documentos
- `PayrollEntryRepository.java` - Interface para entries
- `RubricaRepository.java` - Interface para rubricas
- `UserRepository.java` - Interface para usuários

**Exemplo:**
```java
public interface PersonRepository {
    Mono<Person> findByCpf(String cpf);
    Mono<Person> save(Person person);
    Mono<Boolean> existsByCpf(String cpf);
}
```

**c) Interfaces de Serviços**
- `PdfService.java` - Interface para processar PDFs
- `ExcelService.java` - Interface para gerar Excel
- `GridFsService.java` - Interface para armazenar arquivos
- `CpfValidationService.java` - Interface para validar CPF
- `DocumentTypeDetectionService.java` - Interface para detectar tipo de documento
- `MonthYearDetectionService.java` - Interface para detectar mês/ano

**Exemplo:**
```java
public interface PdfService {
    Mono<String> extractText(InputStream inputStream);
    Mono<Map<String, String>> extractMetadata(InputStream inputStream);
    Mono<Integer> getTotalPages(InputStream inputStream);
}
```

**d) Exceções de Domínio**
- `DocumentNotFoundException.java`
- `PersonNotFoundException.java`
- `InvalidCpfException.java`
- `RubricaNotFoundException.java`
- etc.

**e) Enums e Value Objects**
- `DocumentStatus.java` - Status do documento (PENDING, PROCESSING, PROCESSED, ERROR)
- `DocumentType.java` - Tipo de documento (CAIXA, FUNCEF, MISTO)
- `DetectedPage.java` - Informações sobre páginas detectadas

#### 🎯 Características Importantes:

- ✅ **Não usa anotações do Spring** (exceto `@Document` do MongoDB, que é necessário)
- ✅ **Não conhece HTTP, REST, ou qualquer protocolo**
- ✅ **Contém apenas lógica de negócio pura**
- ✅ **Pode ser testada sem frameworks**

---

### 2️⃣ APPLICATION (Camada de Aplicação) - Casos de Uso

**Localização**: `src/main/java/br/com/verticelabs/pdfprocessor/application/`

Esta camada **orquestra** a lógica de negócio. Ela usa as interfaces do Domain para realizar operações.

#### 📦 O que está aqui:

**Use Cases (Casos de Uso)**
- `DocumentUploadUseCase.java` - Lógica de upload de documentos
- `DocumentProcessUseCase.java` - Lógica de processamento de documentos
- `DocumentQueryUseCase.java` - Lógica de consulta de documentos
- `BulkDocumentUploadUseCase.java` - Lógica de upload múltiplo
- `ConsolidationUseCase.java` - Lógica de consolidação
- `ExcelExportUseCase.java` - Lógica de exportação para Excel
- `RubricaUseCase.java` - Lógica de gerenciamento de rubricas
- `AuthUseCase.java` - Lógica de autenticação

**Exemplo de Use Case:**
```java
@Service
@RequiredArgsConstructor
public class DocumentUploadUseCase {
    
    // Depende apenas de INTERFACES do Domain
    private final GridFsService gridFsService;
    private final PdfService pdfService;
    private final PersonRepository personRepository;
    private final PayrollDocumentRepository documentRepository;
    
    public Mono<UploadDocumentResponse> upload(FilePart filePart, String cpf, String nome) {
        // 1. Validar CPF
        // 2. Validar arquivo
        // 3. Salvar no GridFS
        // 4. Criar documento no banco
        // 5. Retornar resposta
    }
}
```

#### 🎯 Características:

- ✅ **Depende apenas de interfaces do Domain**
- ✅ **Orquestra múltiplos serviços/repositórios**
- ✅ **Contém lógica de aplicação** (não de negócio puro)
- ✅ **Pode usar anotações do Spring** (`@Service`)

---

### 3️⃣ INFRASTRUCTURE (Camada de Infraestrutura) - Implementações

**Localização**: `src/main/java/br/com/verticelabs/pdfprocessor/infrastructure/`

Esta camada **implementa** as interfaces definidas no Domain.

#### 📦 O que está aqui:

**a) Implementações de Repositórios (Adapters)**
- `MongoPersonRepositoryAdapter.java` - Implementa `PersonRepository`
- `MongoPayrollDocumentRepositoryAdapter.java` - Implementa `PayrollDocumentRepository`
- `MongoPayrollEntryRepositoryAdapter.java` - Implementa `PayrollEntryRepository`
- etc.

**Exemplo:**
```java
@Component
@RequiredArgsConstructor
public class MongoPersonRepositoryAdapter implements PersonRepository {
    
    // Usa Spring Data MongoDB (framework específico)
    private final SpringDataPersonRepository repository;
    
    @Override
    public Mono<Person> findByCpf(String cpf) {
        return repository.findByCpf(cpf);
    }
}
```

**b) Implementações de Serviços**
- `PdfServiceImpl.java` - Implementa `PdfService` (usa Apache PDFBox)
- `ExcelServiceImpl.java` - Implementa `ExcelService` (usa Apache POI)
- `GridFsServiceImpl.java` - Implementa `GridFsService` (usa MongoDB GridFS)
- `CpfValidationServiceImpl.java` - Implementa `CpfValidationService`
- `DocumentTypeDetectionServiceImpl.java` - Implementa `DocumentTypeDetectionService`

**c) Configurações**
- `SecurityConfig.java` - Configuração de segurança (JWT, Spring Security)
- `WebConfig.java` - Configuração do WebFlux
- `OpenApiConfig.java` - Configuração do Swagger
- `DatabaseInitializer.java` - Inicialização do banco

**d) Extratores Específicos**
- `CaixaMetadataExtractor.java` - Extrai metadados de PDFs da CAIXA
- `FuncefMetadataExtractor.java` - Extrai metadados de PDFs da FUNCEF
- `PdfLineParser.java` - Faz parsing de linhas do PDF
- `RubricaValidator.java` - Valida rubricas extraídas

#### 🎯 Características:

- ✅ **Implementa interfaces do Domain**
- ✅ **Pode usar qualquer framework** (Spring, Apache PDFBox, etc.)
- ✅ **Isola detalhes técnicos** do resto do sistema
- ✅ **Pode ser trocada sem afetar outras camadas**

---

### 4️⃣ INTERFACES (Camada de Interface) - Entrada do Sistema

**Localização**: `src/main/java/br/com/verticelabs/pdfprocessor/interfaces/`

Esta camada é a **porta de entrada** do sistema. Recebe requisições HTTP e delega para os Use Cases.

#### 📦 O que está aqui:

**a) Controllers REST**
- `DocumentController.java` - Endpoints de documentos
- `AuthController.java` - Endpoints de autenticação
- `RubricaController.java` - Endpoints de rubricas
- `ConsolidationController.java` - Endpoints de consolidação
- `ExcelController.java` - Endpoints de exportação Excel
- `EntryController.java` - Endpoints de entries
- `PersonController.java` - Endpoints de pessoas
- `DatabaseController.java` - Endpoints de sistema

**Exemplo:**
```java
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {
    
    // Depende apenas de Use Cases (Application)
    private final DocumentUploadUseCase documentUploadUseCase;
    private final DocumentProcessUseCase documentProcessUseCase;
    
    @PostMapping("/upload")
    public Mono<ResponseEntity<Object>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart("cpf") String cpf) {
        
        // Delega para o Use Case
        return documentUploadUseCase.upload(file, cpf, null)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body((Object) response));
    }
}
```

**b) DTOs (Data Transfer Objects)**
- `LoginRequest.java`, `AuthResponse.java` - DTOs de autenticação
- `UploadDocumentResponse.java` - DTO de resposta de upload
- `DocumentResponse.java` - DTO de documento
- `EntryResponse.java` - DTO de entry
- etc.

**c) Mappers**
- `EntryMapper.java` - Converte entidades em DTOs

#### 🎯 Características:

- ✅ **Conhece HTTP e REST**
- ✅ **Valida entrada** (parâmetros, body)
- ✅ **Converte DTOs** para entidades do Domain
- ✅ **Trata erros HTTP** (404, 500, etc.)
- ✅ **Delega para Use Cases** (não contém lógica de negócio)

---

## 🔄 Comparação: MVC vs Clean Architecture

### MVC (Model-View-Controller)

```
┌──────────┐
│  View    │  ← Interface (HTML, JSON)
└────┬─────┘
     │
┌────▼─────┐
│Controller│  ← Recebe requisições
└────┬─────┘
     │
┌────▼─────┐
│  Model   │  ← Entidades + Lógica de Negócio + Acesso a Dados
└──────────┘
```

**Problemas do MVC tradicional:**
- ❌ Model geralmente mistura lógica de negócio com acesso a dados
- ❌ Controller pode conter lógica de negócio
- ❌ Difícil testar sem banco de dados
- ❌ Acoplado a frameworks

### Clean Architecture

```
┌─────────────────────────────────┐
│   INTERFACES (Controllers)      │  ← Equivalente ao Controller do MVC
├─────────────────────────────────┤
│   APPLICATION (Use Cases)       │  ← Equivalente ao Service do MVC
├─────────────────────────────────┤
│   DOMAIN (Entidades + Regras)   │  ← Equivalente ao Model do MVC
├─────────────────────────────────┤
│   INFRASTRUCTURE (Repositórios) │  ← Acesso a dados (não existe no MVC puro)
└─────────────────────────────────┘
```

**Vantagens:**
- ✅ Separação clara de responsabilidades
- ✅ Lógica de negócio isolada
- ✅ Fácil testar
- ✅ Independente de frameworks

---

## 🗺️ Mapeamento de Componentes

### Onde está cada coisa?

| Componente MVC | Onde está na Clean Architecture | Exemplo |
|----------------|--------------------------------|---------|
| **Controller** | `interfaces/*/Controller.java` | `DocumentController.java` |
| **Service** | `application/*/UseCase.java` | `DocumentUploadUseCase.java` |
| **Model/Entity** | `domain/model/*.java` | `Person.java`, `PayrollDocument.java` |
| **Repository** | `domain/repository/*.java` (interface)<br>`infrastructure/mongodb/*Adapter.java` (implementação) | `PersonRepository.java`<br>`MongoPersonRepositoryAdapter.java` |
| **DTO** | `interfaces/*/dto/*.java` | `UploadDocumentResponse.java` |
| **Exception** | `domain/exceptions/*.java` | `DocumentNotFoundException.java` |
| **Config** | `infrastructure/config/*.java` | `SecurityConfig.java` |

---

## 🔀 Fluxo de Dados na Arquitetura

### Exemplo: Upload de Documento

```
1. Cliente HTTP
   ↓ POST /api/v1/documents/upload
   
2. DocumentController (INTERFACES)
   ↓ Recebe FilePart, CPF, Nome
   ↓ Valida entrada
   ↓ Converte para formato interno
   
3. DocumentUploadUseCase (APPLICATION)
   ↓ Valida CPF
   ↓ Valida arquivo
   ↓ Orquestra operações:
      - Salva arquivo (GridFsService)
      - Cria documento (PayrollDocumentRepository)
      - Atualiza pessoa (PersonRepository)
   
4. MongoPersonRepositoryAdapter (INFRASTRUCTURE)
   ↓ Implementa PersonRepository
   ↓ Usa Spring Data MongoDB
   ↓ Salva no MongoDB
   
5. Domain (Person, PayrollDocument)
   ↓ Entidades puras
   ↓ Sem dependências externas
```

### Diagrama de Dependências

```
┌─────────────────────────────────────────┐
│   INTERFACES                            │
│   - DocumentController                  │
│   - AuthController                      │
│   ↓ depende de                          │
├─────────────────────────────────────────┤
│   APPLICATION                           │
│   - DocumentUploadUseCase               │
│   - DocumentProcessUseCase              │
│   ↓ depende de (interfaces)             │
├─────────────────────────────────────────┤
│   DOMAIN                                │
│   - Person, PayrollDocument             │
│   - PersonRepository (interface)        │
│   - PdfService (interface)              │
│   ← implementado por                    │
├─────────────────────────────────────────┤
│   INFRASTRUCTURE                        │
│   - MongoPersonRepositoryAdapter        │
│   - PdfServiceImpl                      │
└─────────────────────────────────────────┘
```

---

## 📝 Resumo: Onde Procurar Cada Coisa

### Quero adicionar um novo endpoint
→ Vá em `interfaces/*/Controller.java` e crie um novo método

### Quero adicionar uma nova regra de negócio
→ Vá em `application/*/UseCase.java` e adicione a lógica

### Quero criar uma nova entidade
→ Vá em `domain/model/` e crie a classe

### Quero criar um novo repositório
→ Crie a interface em `domain/repository/`
→ Implemente em `infrastructure/mongodb/*Adapter.java`

### Quero criar um novo serviço (ex: enviar email)
→ Crie a interface em `domain/service/`
→ Implemente em `infrastructure/*/ServiceImpl.java`

### Quero mudar como os dados são salvos
→ Modifique apenas `infrastructure/mongodb/*Adapter.java`
→ O resto do sistema não precisa mudar!

---

## 🎓 Princípios Importantes

### 1. Dependency Inversion Principle (DIP)

**Domain define interfaces, Infrastructure implementa.**

```java
// Domain define a interface
public interface PersonRepository {
    Mono<Person> findByCpf(String cpf);
}

// Infrastructure implementa
@Component
public class MongoPersonRepositoryAdapter implements PersonRepository {
    // implementação usando MongoDB
}
```

### 2. Single Responsibility Principle (SRP)

Cada classe tem uma única responsabilidade:
- `DocumentController` → Apenas recebe HTTP e delega
- `DocumentUploadUseCase` → Apenas orquestra o upload
- `PersonRepository` → Apenas define como buscar pessoas

### 3. Open/Closed Principle (OCP)

Pode adicionar novas funcionalidades sem modificar código existente:
- Adicionar novo tipo de documento? Crie novo `MetadataExtractor`
- Adicionar novo banco? Crie novo `Adapter`
- O Domain não precisa mudar!

---

## 🚀 Conclusão

A Clean Architecture pode parecer complexa no início, mas ela traz:

- ✅ **Código mais organizado**
- ✅ **Fácil manutenção**
- ✅ **Fácil teste**
- ✅ **Fácil evolução**

**Lembre-se:**
- **Domain** = Regras de negócio puras (não depende de nada)
- **Application** = Orquestra casos de uso
- **Infrastructure** = Implementa detalhes técnicos
- **Interfaces** = Recebe requisições HTTP

Cada camada tem sua responsabilidade bem definida! 🎯

