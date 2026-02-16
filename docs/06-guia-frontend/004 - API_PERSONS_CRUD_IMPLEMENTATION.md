# 👥 API de Gestão de Pessoas (CRUD Completo) - Documentação de Implementação

Esta documentação descreve a implementação completa da API de gestão de pessoas (CRUD) no sistema PDF Processor API Backend.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Resumo da Implementação](#resumo-da-implementação)
- [Detalhamento Técnico](#detalhamento-técnico)
- [Endpoints Implementados](#endpoints-implementados)
- [Funcionalidades](#funcionalidades)
- [Segurança e Multi-tenancy](#segurança-e-multi-tenancy)
- [Upload de Documentos por PersonId](#-upload-de-documentos-por-personid)
- [Upload de Declarações de IR por PersonId](#-upload-de-declarações-de-ir-por-personid)
- [Exclusão de Documentos](#-exclusão-de-documentos)

---

## 🎯 Visão Geral

A API de gestão de pessoas foi implementada seguindo os princípios da **Clean Architecture**, proporcionando um CRUD completo com as seguintes operações:

- ✅ **Criar** pessoa
- ✅ **Listar** pessoas (com paginação e filtros)
- ✅ **Buscar** pessoa por ID
- ✅ **Atualizar** pessoa
- ✅ **Ativar/Desativar** pessoa
- ✅ **Excluir** definitivamente pessoa
- ✅ **Upload de documentos** por personId (único e múltiplo)
- ✅ **Upload de declarações de IR** por personId (único e múltiplo)
- ✅ **Excluir documentos** com remoção completa de referências

---

## 📝 Resumo da Implementação

### 1. Entidade Person

**Arquivo**: `src/main/java/br/com/verticelabs/pdfprocessor/domain/model/Person.java`

- ✅ Adicionado campo `ativo` (Boolean, padrão `true`)
- ✅ Mantidos campos existentes: `id`, `tenantId`, `cpf`, `nome`, `matricula`, `documentos`, `createdAt`, `updatedAt`

### 2. Exceções de Domínio

**Arquivos**:
- `src/main/java/br/com/verticelabs/pdfprocessor/domain/exceptions/PersonDuplicadaException.java` (novo)
- `src/main/java/br/com/verticelabs/pdfprocessor/domain/exceptions/PersonNotFoundException.java` (atualizado)

- ✅ `PersonDuplicadaException` — lançada quando já existe pessoa com o mesmo CPF no tenant
- ✅ `PersonNotFoundException` — atualizada para aceitar ID ou CPF como identificador

### 3. Repository

**Arquivos**:
- `src/main/java/br/com/verticelabs/pdfprocessor/domain/repository/PersonRepository.java`
- `src/main/java/br/com/verticelabs/pdfprocessor/infrastructure/mongodb/SpringDataPersonRepository.java`
- `src/main/java/br/com/verticelabs/pdfprocessor/infrastructure/mongodb/MongoPersonRepositoryAdapter.java`

**Métodos adicionados**:
- ✅ `findByTenantIdAndId(String tenantId, String id)` — busca pessoa por tenant e ID
- ✅ `deleteById(String id)` — exclusão definitiva de pessoa

### 4. UseCases Criados

**Pacote**: `src/main/java/br/com/verticelabs/pdfprocessor/application/persons/`

- ✅ **CreatePersonUseCase** — cria pessoa com validação de CPF e verificação de duplicatas
- ✅ **UpdatePersonUseCase** — atualiza nome e matrícula de pessoa existente
- ✅ **DeletePersonUseCase** — exclui definitivamente uma pessoa
- ✅ **ActivatePersonUseCase** — ativa uma pessoa (define `ativo = true`)
- ✅ **DeactivatePersonUseCase** — desativa uma pessoa (define `ativo = false`)
- ✅ **GetPersonByIdUseCase** — busca pessoa por ID com validação de acesso

**Pacote**: `src/main/java/br/com/verticelabs/pdfprocessor/application/documents/`

- ✅ **DocumentUploadUseCase.uploadByPersonId()** — upload de documento único por personId (busca CPF, nome e matrícula automaticamente)
- ✅ **BulkDocumentUploadUseCase.uploadBulkByPersonId()** — upload múltiplo de documentos por personId (busca CPF, nome e matrícula automaticamente)
- ✅ **DeleteDocumentUseCase** — exclui documento e todas as referências relacionadas (entries, GridFS, referência na Person)

**Pacote**: `src/main/java/br/com/verticelabs/pdfprocessor/application/incometax/`

- ✅ **IncomeTaxUploadUseCase.uploadIncomeTaxByPersonId()** — upload de declaração de IR única por personId (busca CPF automaticamente)

### 5. DTOs Criados

**Pacote**: `src/main/java/br/com/verticelabs/pdfprocessor/interfaces/persons/dto/`

- ✅ **CreatePersonRequest** — DTO para criação:
  - `cpf` (obrigatório)
  - `nome` (obrigatório)
  - `matricula` (opcional)

- ✅ **UpdatePersonRequest** — DTO para atualização:
  - `nome` (obrigatório)
  - `matricula` (opcional)

- ✅ **PersonResponse** — atualizado com campo `ativo`

### 6. Endpoints Implementados

**Arquivo**: `src/main/java/br/com/verticelabs/pdfprocessor/interfaces/persons/PersonController.java`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/v1/persons` | Criar nova pessoa |
| `GET` | `/api/v1/persons/{id}` | Buscar pessoa por ID |
| `PUT` | `/api/v1/persons/{id}` | Atualizar pessoa |
| `DELETE` | `/api/v1/persons/{id}` | Excluir definitivamente pessoa |
| `PATCH` | `/api/v1/persons/{id}/activate` | Ativar pessoa |
| `PATCH` | `/api/v1/persons/{id}/deactivate` | Desativar pessoa |
| `POST` | `/api/v1/persons/{personId}/documents/upload` | Upload de documento único |
| `POST` | `/api/v1/persons/{personId}/documents/bulk-upload` | Upload múltiplo de documentos |
| `DELETE` | `/api/v1/persons/{personId}/documents/{documentId}` | Excluir documento e todas as referências |
| `POST` | `/api/v1/persons/{personId}/income-tax/upload` | Upload de declaração de IR única |
| `POST` | `/api/v1/persons/{personId}/income-tax/bulk-upload` | Upload múltiplo de declarações de IR |

**Endpoints de Upload de Documentos (novos)**:
- `POST /api/v1/persons/{personId}/documents/upload` — Upload de um único documento para uma pessoa
- `POST /api/v1/persons/{personId}/documents/bulk-upload` — Upload múltiplo de documentos para uma pessoa

**Endpoints de Exclusão de Documentos (novos)**:
- `DELETE /api/v1/persons/{personId}/documents/{documentId}` — Excluir documento e todas as referências relacionadas

**Endpoints de Upload de Declarações de IR (novos)**:
- `POST /api/v1/persons/{personId}/income-tax/upload` — Upload de uma declaração de imposto de renda
- `POST /api/v1/persons/{personId}/income-tax/bulk-upload` — Upload múltiplo de declarações de imposto de renda

**Endpoints já existentes (mantidos)**:
- `GET /api/v1/persons` — Listar pessoas com paginação e filtros
- `GET /api/v1/persons/{cpf}/documents` — Listar documentos de uma pessoa
- `GET /api/v1/persons/{personId}/documents-by-id` — Listar documentos por personId
- `GET /api/v1/persons/{cpf}/rubricas` — Matriz de rubricas da pessoa
- `GET /api/v1/persons/{cpf}/entries` — Entries da pessoa

---

## 🔧 Detalhamento Técnico

### Validação de CPF

Todos os UseCases que lidam com CPF utilizam o `CpfValidationService` para:
- Normalizar CPF (remover caracteres especiais)
- Validar formato (11 dígitos)
- Validar dígitos verificadores (algoritmo Mod11 da Receita Federal)
- Rejeitar CPFs com todos os dígitos iguais

### Multi-tenancy

Todas as operações respeitam o isolamento multi-tenant:

- **TENANT_ADMIN / TENANT_USER**: Apenas podem acessar pessoas do seu próprio tenant
- **SUPER_ADMIN**: Pode acessar pessoas de todos os tenants

O `tenantId` é obtido automaticamente do contexto de segurança (JWT token) através do `ReactiveSecurityContextHelper`.

### Tratamento de Erros

Os endpoints retornam códigos HTTP apropriados:

| Código | Situação |
|--------|----------|
| `200 OK` | Operação bem-sucedida |
| `201 Created` | Pessoa criada com sucesso / Documento enviado com sucesso |
| `204 No Content` | Exclusão bem-sucedida (pessoa ou documento) |
| `400 Bad Request` | CPF inválido, dados inválidos ou PDF inválido |
| `404 Not Found` | Pessoa ou documento não encontrado |
| `409 Conflict` | Pessoa já existe (CPF duplicado) / Documento duplicado |
| `422 Unprocessable Entity` | CPF inválido (formato correto mas dígitos verificadores incorretos) |
| `500 Internal Server Error` | Erro interno do servidor |

---

## 🚀 Funcionalidades

### ✅ Validação de CPF

- Validação completa de CPF antes de criar pessoa
- Normalização automática (remove pontos, traços, espaços)
- Verificação de dígitos verificadores
- Rejeição de CPFs inválidos conhecidos

### ✅ Multi-tenancy

- Isolamento completo de dados por tenant
- CPF único por tenant (pode haver mesmo CPF em tenants diferentes)
- Validação automática de acesso baseada em tenant do usuário

### ✅ Permissões

- **SUPER_ADMIN**: Acesso a todas as pessoas de todos os tenants
- **TENANT_ADMIN / TENANT_USER**: Acesso apenas a pessoas do seu tenant
- Validação automática de permissões em todas as operações

### ✅ Pesquisa e Filtros

- Pesquisa por nome (parcial, case-insensitive)
- Pesquisa por CPF (parcial, case-insensitive)
- Pesquisa por matrícula (parcial, case-insensitive)
- Filtros podem ser combinados
- Paginação com `page` e `size`

### ✅ Listagem

- Paginação com `page` (padrão: 0) e `size` (padrão: 20)
- Retorna informações de paginação: `totalElements`, `totalPages`, `hasNext`, `hasPrevious`
- Filtros opcionais por nome, CPF e matrícula

### ✅ Upload de Documentos por PersonId

- **Upload único**: Envia um documento PDF para uma pessoa específica
- **Upload múltiplo**: Envia múltiplos documentos PDF em uma única requisição
- **Busca automática**: CPF, nome e matrícula são obtidos automaticamente da pessoa
- **Processamento automático**: Documentos são processados automaticamente após upload
- **Validação de acesso**: Apenas usuários com acesso à pessoa podem fazer upload
- **Validação de arquivo**: Verifica se é PDF válido antes de processar
- **Detecção de duplicatas**: Detecta documentos duplicados pelo hash SHA-256

### ✅ Upload de Declarações de IR por PersonId

- **Upload único**: Envia uma declaração de IR PDF para uma pessoa específica
- **Upload múltiplo**: Envia múltiplas declarações de IR em uma única requisição
- **Busca automática**: CPF é obtido automaticamente da pessoa
- **Processamento automático**: Declarações são processadas automaticamente após upload
- **Extração de metadata**: Extrai ano-calendário da declaração automaticamente
- **Validação de acesso**: Apenas usuários com acesso à pessoa podem fazer upload
- **Detecção de duplicatas**: Detecta declarações duplicadas pelo hash SHA-256
- **Múltiplas declarações**: Permite subir várias declarações de IR para a mesma pessoa

### ✅ Exclusão de Documentos

- **Exclusão completa**: Remove documento e todas as referências relacionadas
- **PayrollEntries**: Deleta todas as entries relacionadas ao documento
- **GridFS**: Remove arquivo de `fs.files` e `fs.chunks`
- **Referência na Person**: Remove `documentId` da lista de documentos da pessoa
- **PayrollDocument**: Deleta o documento em si
- **Validação de acesso**: Respeita isolamento multi-tenant
- **Logs detalhados**: Registra cada etapa da exclusão

---

## 🔐 Segurança e Multi-tenancy

### Validação de CPF

- ✅ Validação antes de criar pessoa
- ✅ Normalização automática
- ✅ Verificação de dígitos verificadores (Mod11)
- ✅ Rejeição de CPFs inválidos

### Verificação de Duplicatas

- ✅ Verificação de CPF duplicado por tenant antes de criar
- ✅ CPF é único apenas dentro do mesmo tenant
- ✅ Permite mesmo CPF em tenants diferentes

### Isolamento de Dados

- ✅ Todas as operações respeitam o isolamento por tenant
- ✅ Validação automática de acesso baseada em tenant do usuário
- ✅ SUPER_ADMIN tem acesso a todos os tenants

### Permissões por Role

| Role | Permissões |
|------|------------|
| `SUPER_ADMIN` | Pode criar, ler, atualizar, excluir e ativar/desativar pessoas de **todos os tenants** |
| `TENANT_ADMIN` | Pode criar, ler, atualizar, excluir e ativar/desativar pessoas do **seu tenant** |
| `TENANT_USER` | Pode ler pessoas do **seu tenant** (sem permissão para criar/atualizar/excluir) |

> **Nota**: As permissões de escrita (criar, atualizar, excluir) podem ser ajustadas conforme necessário através da configuração de segurança.

---

## 📚 Exemplos de Uso

### Criar Pessoa

```http
POST /api/v1/persons
Content-Type: application/json
Authorization: Bearer {accessToken}

{
  "cpf": "123.456.789-00",
  "nome": "João Silva",
  "matricula": "0437412"
}
```

### Buscar Pessoa por ID

```http
GET /api/v1/persons/{id}
Authorization: Bearer {accessToken}
```

### Atualizar Pessoa

```http
PUT /api/v1/persons/{id}
Content-Type: application/json
Authorization: Bearer {accessToken}

{
  "nome": "João Silva Santos",
  "matricula": "0437412"
}
```

### Ativar/Desativar Pessoa

```http
PATCH /api/v1/persons/{id}/activate
Authorization: Bearer {accessToken}
```

```http
PATCH /api/v1/persons/{id}/deactivate
Authorization: Bearer {accessToken}
```

### Excluir Pessoa

```http
DELETE /api/v1/persons/{id}
Authorization: Bearer {accessToken}
```

### Upload de Documento por PersonId

```http
POST /api/v1/persons/{personId}/documents/upload
Content-Type: multipart/form-data
Authorization: Bearer {accessToken}

file: [arquivo.pdf]
```

**Resposta (201 Created)**:
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PENDING",
  "tipoDetectado": "CAIXA"
}
```

### Upload Múltiplo de Documentos por PersonId

```http
POST /api/v1/persons/{personId}/documents/bulk-upload
Content-Type: multipart/form-data
Authorization: Bearer {accessToken}

files: [arquivo1.pdf, arquivo2.pdf, arquivo3.pdf]
```

**Resposta (201 Created)**:
```json
{
  "cpf": "12345678900",
  "totalArquivos": 3,
  "sucessos": 3,
  "falhas": 0,
  "resultados": [
    {
      "filename": "arquivo1.pdf",
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PROCESSING",
      "tipoDetectado": "CAIXA",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "arquivo2.pdf",
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PROCESSING",
      "tipoDetectado": "FUNCEF",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "arquivo3.pdf",
      "documentId": "507f1f77bcf86cd799439013",
      "status": "PROCESSING",
      "tipoDetectado": "CAIXA_FUNCEF",
      "sucesso": true,
      "erro": null
    }
  ]
}
```

**Vantagens dos endpoints por personId**:
- ✅ Não precisa informar CPF, nome e matrícula (obtidos automaticamente da pessoa)
- ✅ Garante que os documentos sejam associados à pessoa correta
- ✅ Evita erros de digitação de CPF
- ✅ Validação automática de acesso (SUPER_ADMIN ou tenant do usuário)

### Excluir Documento

```http
DELETE /api/v1/persons/{personId}/documents/{documentId}
Authorization: Bearer {accessToken}
```

**Resposta de Sucesso (204 No Content)**:
- Documento e todas as referências foram deletadas com sucesso

**Resposta de Erro (404 Not Found)**:
```json
{
  "status": 404,
  "error": "Documento não encontrado: {documentId}"
}
```

**O que é excluído**:
1. ✅ **PayrollEntries** relacionadas ao documento
2. ✅ **Arquivo no GridFS** (`fs.files` e `fs.chunks`)
3. ✅ **Referência na Person** (remove `documentId` da lista de documentos)
4. ✅ **PayrollDocument** (o documento em si)

**Fluxo de exclusão**:
1. Busca documento com validação de acesso
2. Deleta todas as PayrollEntries relacionadas
3. Deleta arquivo do GridFS
4. Remove referência do documento na lista da Person
5. Deleta o PayrollDocument

### Upload de Declaração de IR por PersonId

```http
POST /api/v1/persons/{personId}/income-tax/upload
Content-Type: multipart/form-data
Authorization: Bearer {accessToken}

file: [declaracao_ir_2024.pdf]
```

**Resposta (201 Created)**:
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "tipoDetectado": "INCOME_TAX"
}
```

### Upload Múltiplo de Declarações de IR por PersonId

```http
POST /api/v1/persons/{personId}/income-tax/bulk-upload
Content-Type: multipart/form-data
Authorization: Bearer {accessToken}

files: [declaracao_ir_2023.pdf, declaracao_ir_2024.pdf, declaracao_ir_2025.pdf]
```

**Resposta (201 Created)**:
```json
{
  "cpf": "12449709568",
  "totalArquivos": 3,
  "sucessos": 3,
  "falhas": 0,
  "resultados": [
    {
      "filename": "declaracao_ir_2023.pdf",
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "declaracao_ir_2024.pdf",
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "declaracao_ir_2025.pdf",
      "documentId": "507f1f77bcf86cd799439013",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    }
  ]
}
```

**Vantagens dos endpoints de IR por personId**:
- ✅ Não precisa informar CPF manualmente (obtido automaticamente da pessoa)
- ✅ Garante que as declarações sejam associadas à pessoa correta
- ✅ Permite múltiplas declarações de IR para a mesma pessoa
- ✅ Processamento automático após upload
- ✅ Extração automática de metadata (ano-calendário)

---

## ✅ Status da Implementação

A API está **funcional e pronta para uso**. Todos os endpoints seguem o padrão da arquitetura existente e respeitam o isolamento multi-tenant.

### Checklist de Implementação

- ✅ Entidade Person atualizada com campo `ativo`
- ✅ Exceções de domínio criadas/atualizadas
- ✅ Repository atualizado com novos métodos
- ✅ UseCases criados para todas as operações CRUD
- ✅ UseCases de upload de documentos por personId criados
- ✅ UseCases de upload de declarações de IR por personId criados
- ✅ UseCase de exclusão de documentos criado
- ✅ DTOs criados para requests e responses
- ✅ Endpoints CRUD implementados no Controller
- ✅ Endpoints de upload de documentos por personId implementados
- ✅ Endpoints de upload de declarações de IR por personId implementados
- ✅ Endpoint de exclusão de documentos implementado
- ✅ Validação de CPF implementada
- ✅ Multi-tenancy respeitado em todas as operações
- ✅ Validação de acesso em uploads por personId
- ✅ Upload de declarações de IR com processamento automático
- ✅ Exclusão completa de documentos com todas as referências
- ✅ Tratamento de erros adequado
- ✅ Logs implementados
- ✅ Sem erros de compilação

---

## 📖 Documentação Relacionada

- **[API de Pessoas - Frontend](./API_PERSONS_FRONTEND.md)** — Documentação completa para integração frontend
- **[Documentação Completa das APIs](./API_COMPLETA_E_ARQUITETURA.md)** — Visão geral de todas as APIs
- **[Arquitetura do Sistema](./ARCHITECTURE.md)** — Detalhes da Clean Architecture

---

---

## 📤 Upload de Documentos por PersonId

### Visão Geral

As APIs de upload de documentos por `personId` foram criadas para facilitar o envio de documentos quando já se tem acesso à pessoa específica. Diferente das APIs tradicionais de upload que exigem CPF, nome e matrícula, essas novas APIs buscam automaticamente essas informações da pessoa pelo `personId`.

### Endpoints de Upload

#### 1. Upload Único

**Endpoint**: `POST /api/v1/persons/{personId}/documents/upload`

**Descrição**: Faz upload de um único documento PDF para uma pessoa específica.

**Parâmetros**:
- `personId` (path, obrigatório) — ID da pessoa
- `file` (multipart/form-data, obrigatório) — Arquivo PDF

**Fluxo**:
1. Busca pessoa por `personId` com validação de acesso
2. Obtém CPF, nome e matrícula da pessoa
3. Valida CPF
4. Faz upload do documento
5. Associa documento à pessoa
6. Inicia processamento automático

**Resposta de Sucesso (201 Created)**:
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PENDING",
  "tipoDetectado": "CAIXA"
}
```

**Resposta de Erro (404 Not Found)**:
```json
{
  "status": 404,
  "error": "Pessoa não encontrada: ID: 507f1f77bcf86cd799439011"
}
```

#### 2. Upload Múltiplo

**Endpoint**: `POST /api/v1/persons/{personId}/documents/bulk-upload`

**Descrição**: Faz upload de múltiplos documentos PDF para uma pessoa específica.

**Parâmetros**:
- `personId` (path, obrigatório) — ID da pessoa
- `files` (multipart/form-data, obrigatório) — Lista de arquivos PDF

**Fluxo**:
1. Busca pessoa por `personId` com validação de acesso
2. Obtém CPF, nome e matrícula da pessoa
3. Valida CPF
4. Processa cada arquivo sequencialmente:
   - Faz upload do documento
   - Associa documento à pessoa
   - Inicia processamento automático
5. Retorna resultado detalhado de cada upload

**Resposta de Sucesso (201 Created)**:
```json
{
  "cpf": "12345678900",
  "totalArquivos": 3,
  "sucessos": 3,
  "falhas": 0,
  "resultados": [
    {
      "filename": "arquivo1.pdf",
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PROCESSING",
      "tipoDetectado": "CAIXA",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "arquivo2.pdf",
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PROCESSING",
      "tipoDetectado": "FUNCEF",
      "sucesso": true,
      "erro": null
    }
  ]
}
```

### Vantagens dos Endpoints por PersonId

1. **Simplicidade**: Não precisa informar CPF, nome e matrícula manualmente
2. **Segurança**: Garante que os documentos sejam associados à pessoa correta
3. **Precisão**: Evita erros de digitação de CPF
4. **Validação**: Valida automaticamente se o usuário tem acesso à pessoa
5. **Consistência**: Usa os dados exatos da pessoa cadastrada

### Validações e Segurança

- ✅ **Validação de acesso**: Apenas usuários com acesso à pessoa podem fazer upload
  - SUPER_ADMIN: Pode fazer upload para qualquer pessoa
  - TENANT_ADMIN / TENANT_USER: Apenas para pessoas do seu tenant
- ✅ **Validação de pessoa**: Retorna 404 se pessoa não encontrada
- ✅ **Validação de CPF**: Valida CPF da pessoa antes do upload
- ✅ **Validação de arquivo**: Verifica se é PDF válido
- ✅ **Detecção de duplicatas**: Detecta documentos duplicados pelo hash SHA-256
- ✅ **Processamento automático**: Inicia processamento após upload bem-sucedido

### Tratamento de Erros

| Código | Situação |
|--------|----------|
| `201 Created` | Upload bem-sucedido |
| `400 Bad Request` | PDF inválido ou parâmetros inválidos |
| `404 Not Found` | Pessoa não encontrada |
| `409 Conflict` | Documento duplicado (mesmo hash) |
| `422 Unprocessable Entity` | CPF inválido |
| `500 Internal Server Error` | Erro interno do servidor |

### Comparação com APIs Tradicionais

| Aspecto | API Tradicional | API por PersonId |
|---------|----------------|------------------|
| **CPF** | Obrigatório (informado manualmente) | ✅ Obtido automaticamente |
| **Nome** | Obrigatório (informado manualmente) | ✅ Obtido automaticamente |
| **Matrícula** | Obrigatório (informado manualmente) | ✅ Obtido automaticamente |
| **Validação de acesso** | Por tenant do CPF | ✅ Por personId (mais seguro) |
| **Risco de erro** | Alto (digitação manual) | ✅ Baixo (dados da pessoa) |
| **Uso recomendado** | Quando não se tem personId | ✅ Quando já se tem personId |

---

## 🗑️ Exclusão de Documentos

### Visão Geral

A API de exclusão de documentos foi criada para permitir a remoção completa de um documento e todas as suas referências relacionadas no sistema. Quando um documento é excluído, todas as dependências são removidas automaticamente para manter a integridade dos dados.

### Endpoint de Exclusão

**Endpoint**: `DELETE /api/v1/persons/{personId}/documents/{documentId}`

**Descrição**: Exclui um documento e todas as suas referências relacionadas de forma segura e completa.

**Parâmetros**:
- `personId` (path, obrigatório) — ID da pessoa
- `documentId` (path, obrigatório) — ID do documento a ser excluído

### O que é Excluído

Ao deletar um documento, o sistema remove automaticamente:

1. **PayrollEntries relacionadas**
   - Todas as entries com `documentoId` correspondente são deletadas
   - Usa `entryRepository.deleteByTenantIdAndDocumentoId()`
   - Garante que não fiquem entries órfãs no banco

2. **Arquivo no GridFS**
   - Arquivo deletado de `fs.files` e `fs.chunks`
   - Usa `gridFsService.deleteFile(originalFileId)`
   - Continua mesmo se o arquivo não existir mais (tolerante a falhas)

3. **Referência na Person**
   - Remove o `documentId` da lista `documentos` da Person
   - Atualiza a Person no banco de dados
   - Mantém a lista de documentos da pessoa sincronizada

4. **PayrollDocument**
   - O documento em si é deletado por último
   - Usa `documentRepository.deleteByTenantIdAndId()`
   - Garante que todas as referências sejam removidas antes

### Fluxo de Exclusão

O processo de exclusão segue uma ordem específica para garantir integridade:

```
1. Busca documento com validação de acesso
   ↓
2. Deleta todas as PayrollEntries relacionadas
   ↓
3. Deleta arquivo do GridFS (fs.files e fs.chunks)
   ↓
4. Remove referência do documento na lista da Person
   ↓
5. Deleta o PayrollDocument
```

### Segurança e Validação

- ✅ **Validação de acesso por tenant**
  - SUPER_ADMIN pode deletar documentos de qualquer tenant
  - TENANT_ADMIN/TENANT_USER só podem deletar documentos do seu tenant
- ✅ **Validação de existência**: Verifica se documento existe antes de deletar
- ✅ **Logs detalhados**: Registra cada etapa da exclusão para auditoria
- ✅ **Tolerante a falhas**: Continua mesmo se arquivo GridFS não existir mais

### Respostas da API

**Sucesso (204 No Content)**:
```
HTTP/1.1 204 No Content
```
- Documento e todas as referências foram deletadas com sucesso

**Erro (404 Not Found)**:
```json
{
  "status": 404,
  "error": "Documento não encontrado: {documentId}"
}
```

**Erro (500 Internal Server Error)**:
```json
{
  "status": 500,
  "error": "Erro ao excluir documento: {mensagem}"
}
```

### Exemplo de Uso

```http
DELETE /api/v1/persons/69357314d0dbe62eed95710f/documents/69357634d0dbe62eed957114
Authorization: Bearer {accessToken}
```

**Resposta de Sucesso**:
```
HTTP/1.1 204 No Content
```

### Logs de Exclusão

O sistema registra logs detalhados durante a exclusão:

```
=== INÍCIO DA EXCLUSÃO DE DOCUMENTO ===
DocumentId: 69357634d0dbe62eed957114
✓ Documento encontrado: ID=..., CPF=..., Tipo=..., Status=..., FileId=...
Deletando entries relacionadas ao documento: ...
✓ Entries deletadas com sucesso
Deletando arquivo do GridFS: ...
✓ Arquivo deletado do GridFS com sucesso
Removendo referência do documento na Person (CPF: ...)
✓ Person atualizada. Documento removido da lista
Deletando documento: ...
=== EXCLUSÃO DE DOCUMENTO CONCLUÍDA COM SUCESSO ===
✓ PayrollDocument deletado
✓ PayrollEntries deletadas
✓ Arquivo GridFS deletado
✓ Referência removida da Person
```

### Importante

⚠️ **Atenção**: A exclusão é **definitiva** e **irreversível**. Todos os dados relacionados ao documento serão permanentemente removidos do sistema. Certifique-se de que realmente deseja excluir o documento antes de executar a operação.

---

## 📋 Upload de Declarações de IR por PersonId

### Visão Geral

As APIs de upload de declarações de imposto de renda por `personId` foram criadas para facilitar o envio de declarações de IR quando já se tem acesso à pessoa específica. Diferente da API tradicional que exige CPF, essas novas APIs buscam automaticamente o CPF da pessoa pelo `personId`.

### Endpoints de Upload de Declarações de IR

#### 1. Upload Único de Declaração de IR

**Endpoint**: `POST /api/v1/persons/{personId}/income-tax/upload`

**Descrição**: Faz upload de uma declaração de imposto de renda PDF para uma pessoa específica.

**Parâmetros**:
- `personId` (path, obrigatório) — ID da pessoa
- `file` (multipart/form-data, obrigatório) — Arquivo PDF da declaração de IR

**Fluxo**:
1. Busca pessoa por `personId` com validação de acesso
2. Obtém CPF da pessoa
3. Valida CPF
4. Faz upload do documento
5. Extrai metadata (ano-calendário) da declaração
6. Associa documento à pessoa
7. Inicia processamento automático

**Resposta de Sucesso (201 Created)**:
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "tipoDetectado": "INCOME_TAX"
}
```

**Resposta de Erro (404 Not Found)**:
```json
{
  "status": 404,
  "error": "Pessoa não encontrada: ID: 507f1f77bcf86cd799439011"
}
```

#### 2. Upload Múltiplo de Declarações de IR

**Endpoint**: `POST /api/v1/persons/{personId}/income-tax/bulk-upload`

**Descrição**: Faz upload de múltiplas declarações de imposto de renda PDF para uma pessoa específica.

**Parâmetros**:
- `personId` (path, obrigatório) — ID da pessoa
- `files` (multipart/form-data, obrigatório) — Lista de arquivos PDF de declarações de IR

**Fluxo**:
1. Busca pessoa por `personId` com validação de acesso
2. Obtém CPF da pessoa
3. Valida CPF
4. Processa cada arquivo sequencialmente:
   - Faz upload do documento
   - Extrai metadata (ano-calendário)
   - Associa documento à pessoa
   - Inicia processamento automático
5. Retorna resultado detalhado de cada upload

**Resposta de Sucesso (201 Created)**:
```json
{
  "cpf": "12449709568",
  "totalArquivos": 3,
  "sucessos": 3,
  "falhas": 0,
  "resultados": [
    {
      "filename": "declaracao_ir_2023.pdf",
      "documentId": "507f1f77bcf86cd799439011",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "declaracao_ir_2024.pdf",
      "documentId": "507f1f77bcf86cd799439012",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    },
    {
      "filename": "declaracao_ir_2025.pdf",
      "documentId": "507f1f77bcf86cd799439013",
      "status": "PROCESSING",
      "tipoDetectado": "INCOME_TAX",
      "sucesso": true,
      "erro": null
    }
  ]
}
```

### Vantagens dos Endpoints de IR por PersonId

1. **Simplicidade**: Não precisa informar CPF manualmente
2. **Segurança**: Garante que as declarações sejam associadas à pessoa correta
3. **Precisão**: Evita erros de digitação de CPF
4. **Múltiplas declarações**: Permite subir várias declarações de IR para a mesma pessoa
5. **Validação**: Valida automaticamente se o usuário tem acesso à pessoa
6. **Extração automática**: Extrai ano-calendário da declaração automaticamente
7. **Processamento automático**: Inicia processamento após upload bem-sucedido

### Validações e Segurança

- ✅ **Validação de acesso**: Apenas usuários com acesso à pessoa podem fazer upload
  - SUPER_ADMIN: Pode fazer upload para qualquer pessoa
  - TENANT_ADMIN / TENANT_USER: Apenas para pessoas do seu tenant
- ✅ **Validação de pessoa**: Retorna 404 se pessoa não encontrada
- ✅ **Validação de CPF**: Valida CPF da pessoa antes do upload
- ✅ **Validação de arquivo**: Verifica se é PDF válido
- ✅ **Detecção de duplicatas**: Detecta declarações duplicadas pelo hash SHA-256
- ✅ **Extração de metadata**: Extrai ano-calendário da declaração automaticamente
- ✅ **Processamento automático**: Inicia processamento após upload bem-sucedido

### Tratamento de Erros

| Código | Situação |
|--------|----------|
| `201 Created` | Upload bem-sucedido |
| `400 Bad Request` | PDF inválido ou parâmetros inválidos |
| `404 Not Found` | Pessoa não encontrada |
| `409 Conflict` | Declaração duplicada (mesmo hash) |
| `422 Unprocessable Entity` | CPF inválido |
| `500 Internal Server Error` | Erro interno do servidor |

### Informações Extraídas da Declaração de IR

Quando uma declaração de IR é processada, o sistema extrai automaticamente:

- **Nome** do declarante
- **CPF** do declarante
- **Exercício** da declaração
- **Ano-Calendário** da declaração
- **Base de Cálculo do Imposto**
- **Imposto Devido**
- **Dedução de Incentivo**
- **Imposto Devido I**
- **Contribuição Prev. Empregador Doméstico**
- **Imposto Devido II**
- **Imposto Devido RRA**
- **Total do Imposto Devido**

Essas informações são salvas como **PayrollEntries** com códigos específicos (ex: `IR_NOME`, `IR_CPF`, `IR_IMPOSTO_DEVIDO`, etc.) e podem ser consultadas posteriormente.

### Comparação com API Tradicional

| Aspecto | API Tradicional | API por PersonId |
|---------|----------------|------------------|
| **CPF** | Obrigatório (informado manualmente) | ✅ Obtido automaticamente |
| **Validação de acesso** | Por tenant do CPF | ✅ Por personId (mais seguro) |
| **Risco de erro** | Alto (digitação manual) | ✅ Baixo (dados da pessoa) |
| **Múltiplas declarações** | Possível, mas manual | ✅ Facilitado com bulk-upload |
| **Uso recomendado** | Quando não se tem personId | ✅ Quando já se tem personId |

---

**Última atualização**: Dezembro 2025

