# 👥 API de Gerenciamento de Usuários - Documentação para Frontend

Esta documentação descreve todos os endpoints de gerenciamento de usuários da API e como implementá-los no frontend.

## 📋 Índice

- [Configuração Base](#configuração-base)
- [Permissões e Roles](#permissões-e-roles)
- [Endpoints](#endpoints)
  - [POST /api/v1/users](#1-post-apiv1users)
  - [GET /api/v1/users](#2-get-apiv1users)
  - [GET /api/v1/users/{id}](#3-get-apiv1usersid)
  - [PUT /api/v1/users/{id}](#4-put-apiv1usersid)
  - [DELETE /api/v1/users/{id}](#5-delete-apiv1usersid)
  - [POST /api/v1/users/{id}/activate](#6-post-apiv1usersidactivate)
  - [PUT /api/v1/users/{id}/password](#7-put-apiv1usersidpassword)
  - [GET /api/v1/tenants/{tenantId}/users](#8-get-apiv1tenantstenantidusers)
- [Matriz de Permissões](#matriz-de-permissões)
- [Tratamento de Erros](#tratamento-de-erros)
- [Exemplos de Implementação](#exemplos-de-implementação)

---

## 🔧 Configuração Base

### Base URL
```
http://localhost:8081/api/v1
```

**Nota**: O prefixo `/api/v1` é adicionado automaticamente pelo backend através do `WebConfig`. Os controllers usam apenas o caminho relativo (ex: `/users`).

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
- Todos os endpoints de gerenciamento de usuários requerem autenticação
- O `accessToken` deve ser válido e o usuário deve ter as permissões adequadas
- O token expira em 15 minutos - use o refresh token quando necessário

---

## 🔐 Permissões e Roles

### Roles Disponíveis

| Role | Descrição |
|------|-----------|
| `SUPER_ADMIN` | Administrador global do sistema. Pode gerenciar todos os usuários e tenants. |
| `TENANT_ADMIN` | Administrador de um tenant específico. Pode gerenciar apenas usuários do seu tenant. |
| `TENANT_USER` | Usuário comum de um tenant. Não pode gerenciar outros usuários. |

### Matriz de Permissões

| Ação | SUPER_ADMIN | TENANT_ADMIN | TENANT_USER |
|------|------------|--------------|-------------|
| **Criar SUPER_ADMIN** | ✅ Sim | ❌ Não | ❌ Não |
| **Criar TENANT_ADMIN** | ✅ Sim (qualquer tenant) | ✅ Sim (seu tenant) | ❌ Não |
| **Criar TENANT_USER** | ✅ Sim (qualquer tenant) | ✅ Sim (seu tenant) | ❌ Não |
| **Listar Usuários** | ✅ Todos | ✅ Apenas do seu tenant | ❌ Não |
| **Buscar Usuário** | ✅ Qualquer | ✅ Apenas do seu tenant | ❌ Não |
| **Editar Usuário** | ✅ Qualquer | ✅ Apenas do seu tenant | ❌ Não |
| **Desativar Usuário** | ✅ Qualquer | ✅ Apenas do seu tenant | ❌ Não |
| **Reativar Usuário** | ✅ Qualquer | ✅ Apenas do seu tenant | ❌ Não |
| **Alterar Senha** | ✅ Qualquer | ✅ Apenas do seu tenant | ✅ Própria senha |

---

## 📡 Endpoints

### 1. POST /api/v1/users

Cria um novo usuário no sistema.

**URL**: `/api/v1/users`  
**Método**: `POST`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Request Body

```json
{
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "senha": "senha123",
  "roles": ["TENANT_ADMIN"],
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "telefone": "(11) 99999-9999"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `nome` | string | Sim | Nome completo do usuário |
| `email` | string | Sim | Email único (não pode existir no sistema) |
| `senha` | string | Sim | Senha do usuário (mínimo 8 caracteres) |
| `roles` | string[] | Sim | Roles do usuário. Valores: `SUPER_ADMIN`, `TENANT_ADMIN`, `TENANT_USER` |
| `tenantId` | string | Condicional | ID do tenant. Obrigatório para SUPER_ADMIN criar usuários de tenant. Opcional se criar SUPER_ADMIN (deve ser null). TENANT_ADMIN não pode especificar (vem do JWT) |
| `telefone` | string | Não | Telefone do usuário |

#### Regras de Validação

- **SUPER_ADMIN**:
  - Pode criar qualquer tipo de usuário
  - Se criar `SUPER_ADMIN`, `tenantId` deve ser `null`
  - Se criar `TENANT_ADMIN` ou `TENANT_USER`, `tenantId` é obrigatório
- **TENANT_ADMIN**:
  - Só pode criar `TENANT_ADMIN` ou `TENANT_USER`
  - Não pode criar `SUPER_ADMIN`
  - `tenantId` vem automaticamente do JWT (não pode especificar)

#### Response Success (201 Created)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "telefone": "(11) 99999-9999",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": null,
  "desativadoEm": null
}
```

#### Response Error (400 Bad Request)

O endpoint retorna status `400 Bad Request` quando:
- Email já está em uso
- Dados inválidos (validação de campos)
- Regras de negócio violadas (ex: TENANT_ADMIN tentando criar SUPER_ADMIN)

**Estrutura de erro retornada**:
```json
{
  "message": "Email já está em uso",
  "error": "RuntimeException"
}
```

**Para erros de validação** (campos obrigatórios, formato inválido, etc.):
```json
{
  "message": "Erro de validação: email: Email inválido, senha: Senha deve ter no mínimo 8 caracteres",
  "error": "ValidationError"
}
```

#### Response Error (403 Forbidden)

Retornado quando o usuário não tem permissão para realizar a operação.

#### Exemplo JavaScript/TypeScript

```typescript
async function createUser(userData: {
  nome: string;
  email: string;
  senha: string;
  roles: string[];
  tenantId?: string;
  telefone?: string;
}) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch('http://localhost:8081/api/v1/users', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(userData),
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao criar usuário';
    try {
      const error = await response.json();
      errorMessage = error.message || error.error || errorMessage;
    } catch {
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    throw new Error(errorMessage);
  }

  return await response.json();
}
```

---

### 2. GET /api/v1/users

Lista usuários com filtros opcionais e paginação.

**URL**: `/api/v1/users`  
**Método**: `GET`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Query Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `tenantId` | string | Não | Filtrar por tenant (apenas para SUPER_ADMIN) |
| `role` | string | Não | Filtrar por role (`SUPER_ADMIN`, `TENANT_ADMIN`, `TENANT_USER`) |
| `ativo` | boolean | Não | Filtrar por status (`true`, `false`). Padrão: `true` |
| `email` | string | Não | Buscar por email (busca parcial, case-insensitive) |
| `nome` | string | Não | Buscar por nome (busca parcial, case-insensitive) |
| `page` | number | Não | Número da página (padrão: 0) |
| `size` | number | Não | Tamanho da página (padrão: 20) |

#### Regras de Filtro

- **SUPER_ADMIN**: Pode filtrar por qualquer `tenantId` ou ver todos os usuários
- **TENANT_ADMIN**: Só vê usuários do seu tenant (filtro automático aplicado)
- **Filtro `ativo`**: Se não especificado, por padrão retorna apenas usuários ativos (`ativo=true`). Para ver usuários desativados, use `ativo=false`

#### Response Success (200 OK)

```json
{
  "content": [
    {
      "id": "507f1f77bcf86cd799439011",
      "tenantId": "550e8400-e29b-41d4-a716-446655440000",
      "tenantNome": "Empresa ABC Ltda",
      "nome": "João Silva",
      "email": "joao@empresa.com.br",
      "telefone": "(11) 99999-9999",
      "roles": ["TENANT_ADMIN"],
      "ativo": true,
      "twoFactorEnabled": false,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": null,
      "desativadoEm": null
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

#### Exemplo JavaScript/TypeScript

```typescript
interface ListUsersParams {
  tenantId?: string;
  role?: string;
  ativo?: boolean;
  email?: string;
  nome?: string;
  page?: number;
  size?: number;
}

async function listUsers(params: ListUsersParams = {}) {
  const token = localStorage.getItem('accessToken');
  
  const queryParams = new URLSearchParams();
  if (params.tenantId) queryParams.append('tenantId', params.tenantId);
  if (params.role) queryParams.append('role', params.role);
  if (params.ativo !== undefined) queryParams.append('ativo', String(params.ativo));
  if (params.email) queryParams.append('email', params.email);
  if (params.nome) queryParams.append('nome', params.nome);
  if (params.page !== undefined) queryParams.append('page', String(params.page));
  if (params.size !== undefined) queryParams.append('size', String(params.size));
  
  const response = await fetch(
    `http://localhost:8081/api/v1/users?${queryParams.toString()}`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    let errorMessage = 'Erro ao listar usuários';
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

### 3. GET /api/v1/users/{id}

Retorna detalhes completos de um usuário.

**URL**: `/api/v1/users/{id}`  
**Método**: `GET`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do usuário |

#### Regras de Acesso

- **SUPER_ADMIN**: Pode buscar qualquer usuário
- **TENANT_ADMIN**: Só pode buscar usuários do seu tenant

#### Response Success (200 OK)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "telefone": "(11) 99999-9999",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-16T14:20:00Z",
  "desativadoEm": null
}
```

#### Response Error (404 Not Found)

Retornado quando:
- Usuário não existe
- Usuário existe mas não pertence ao tenant do usuário autenticado (para TENANT_ADMIN)

**Nota**: A mensagem de erro pode variar, mas geralmente indica que o recurso não foi encontrado ou não está acessível.

#### Exemplo JavaScript/TypeScript

```typescript
async function getUserById(id: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/users/${id}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao buscar usuário';
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

### 4. PUT /api/v1/users/{id}

Atualiza dados de um usuário existente.

**URL**: `/api/v1/users/{id}`  
**Método**: `PUT`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do usuário |

#### Request Body

```json
{
  "nome": "João Silva Santos",
  "email": "joao.silva@empresa.com.br",
  "telefone": "(11) 88888-8888",
  "roles": ["TENANT_ADMIN"],
  "ativo": true
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `nome` | string | Sim | Nome completo do usuário |
| `email` | string | Sim | Email único (não pode existir em outro usuário) |
| `roles` | string[] | Sim | Roles do usuário |
| `telefone` | string | Não | Telefone do usuário |
| `ativo` | boolean | Não | Status do usuário (true = ativo, false = desativado) |

#### Regras de Validação

- Email pode ser alterado, mas deve ser único globalmente
- Roles podem ser alteradas (com validação de permissões)
- **TENANT_ADMIN** não pode alterar roles para `SUPER_ADMIN`
- Senha **não** pode ser alterada por este endpoint (usar endpoint específico)

#### Response Success (200 OK)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "nome": "João Silva Santos",
  "email": "joao.silva@empresa.com.br",
  "telefone": "(11) 88888-8888",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-17T09:15:00Z",
  "desativadoEm": null
}
```

#### Response Error (409 Conflict)

Retornado quando:
- Email já está em uso (ao atualizar)
- Tentativa de desativar o último SUPER_ADMIN
- Tentativa de desativar o último TENANT_ADMIN de um tenant
- Tentativa de desativar a si mesmo

#### Exemplo JavaScript/TypeScript

```typescript
async function updateUser(id: string, userData: {
  nome: string;
  email: string;
  roles: string[];
  telefone?: string;
  ativo?: boolean;
}) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/users/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(userData),
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao atualizar usuário';
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

### 5. DELETE /api/v1/users/{id}

Desativa um usuário (soft delete - não remove do banco).

**URL**: `/api/v1/users/{id}`  
**Método**: `DELETE`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do usuário |

#### Regras de Validação

- Não remove o usuário do banco de dados
- Apenas marca `ativo: false` e define `desativadoEm`
- Usuário desativado não pode fazer login
- Usuário desativado não aparece em listagens (a menos que filtro `ativo=false`)
- **Não pode desativar a si mesmo**
- **Não pode desativar o último SUPER_ADMIN**
- **Não pode desativar o último TENANT_ADMIN de um tenant**

#### Response Success (200 OK)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "telefone": "(11) 99999-9999",
  "roles": ["TENANT_ADMIN"],
  "ativo": false,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-17T10:30:00Z",
  "desativadoEm": "2024-01-17T10:30:00Z"
}
```

#### Response Error (409 Conflict)

Retornado quando:
- Tentativa de desativar o último SUPER_ADMIN do sistema
- Tentativa de desativar o último TENANT_ADMIN de um tenant
- Tentativa de desativar a si mesmo

**Mensagens de erro comuns**:
- "Não é possível desativar o último SUPER_ADMIN"
- "Não é possível desativar o último TENANT_ADMIN do tenant"
- "Não é possível desativar a si mesmo"

#### Exemplo JavaScript/TypeScript

```typescript
async function deactivateUser(id: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/users/${id}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao desativar usuário';
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

### 6. POST /api/v1/users/{id}/activate

Reativa um usuário desativado.

**URL**: `/api/v1/users/{id}/activate`  
**Método**: `POST`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do usuário |

#### Response Success (200 OK)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "telefone": "(11) 99999-9999",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-18T10:30:00Z",
  "desativadoEm": null
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function activateUser(id: string) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(`http://localhost:8081/api/v1/users/${id}/activate`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao reativar usuário';
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

### 7. PUT /api/v1/users/{id}/password

Altera a senha de um usuário.

**URL**: `/api/v1/users/{id}/password`  
**Método**: `PUT`  
**Autenticação**: Requerida

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `id` | string | Sim | ID do usuário |

#### Request Body

```json
{
  "senhaAtual": "senhaAntiga123",
  "novaSenha": "novaSenha123"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `senhaAtual` | string | Condicional | Senha atual (obrigatório se for próprio usuário) |
| `novaSenha` | string | Sim | Nova senha (mínimo 8 caracteres) |

#### Regras de Validação

- **Próprio usuário**: Deve informar `senhaAtual`
- **ADMIN (SUPER_ADMIN ou TENANT_ADMIN)**: Não precisa informar `senhaAtual`
- Nova senha será hasheada com Argon2

#### Response Success (200 OK)

```json
{
  "message": "Senha alterada com sucesso"
}
```

#### Response Error (400 Bad Request)

Retornado quando:
- Senha atual incorreta (quando alterando própria senha)
- Nova senha não atende aos requisitos (mínimo 8 caracteres)
- `senhaAtual` não informada quando necessário (próprio usuário)

**Nota**: Para ADMIN (SUPER_ADMIN ou TENANT_ADMIN) alterando senha de outro usuário, não é necessário informar `senhaAtual`.

#### Exemplo JavaScript/TypeScript

```typescript
async function changePassword(id: string, senhaAtual: string | null, novaSenha: string) {
  const token = localStorage.getItem('accessToken');
  
  const body: any = { novaSenha };
  if (senhaAtual) {
    body.senhaAtual = senhaAtual;
  }
  
  const response = await fetch(`http://localhost:8081/api/v1/users/${id}/password`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    let errorMessage = 'Erro ao alterar senha';
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

### 8. GET /api/v1/tenants/{tenantId}/users

Lista todos os usuários de um tenant específico.

**URL**: `/api/v1/tenants/{tenantId}/users`  
**Método**: `GET`  
**Autenticação**: Requerida (SUPER_ADMIN ou TENANT_ADMIN)

**Nota**: Este endpoint está localizado no `UserController`, mas usa o caminho `/tenants/{tenantId}/users` para manter consistência com a estrutura de recursos REST. Internamente, ele utiliza o mesmo `ListUsersUseCase` do endpoint `GET /api/v1/users`, aplicando automaticamente o filtro de `tenantId`.

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `tenantId` | string | Sim | ID do tenant |

#### Query Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `role` | string | Não | Filtrar por role |
| `ativo` | boolean | Não | Filtrar por status |
| `page` | number | Não | Número da página (padrão: 0) |
| `size` | number | Não | Tamanho da página (padrão: 20) |

#### Regras de Acesso

- **SUPER_ADMIN**: Pode listar usuários de qualquer tenant
- **TENANT_ADMIN**: Só pode listar usuários do seu próprio tenant (`tenantId` deve ser o seu)

#### Response Success (200 OK)

```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "content": [
    {
      "id": "507f1f77bcf86cd799439011",
      "tenantId": "550e8400-e29b-41d4-a716-446655440000",
      "tenantNome": "Empresa ABC Ltda",
      "nome": "João Silva",
      "email": "joao@empresa.com.br",
      "telefone": "(11) 99999-9999",
      "roles": ["TENANT_ADMIN"],
      "ativo": true,
      "twoFactorEnabled": false,
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": null,
      "desativadoEm": null
    }
  ],
  "totalElements": 10,
  "totalPages": 1
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function listTenantUsers(tenantId: string, params: {
  role?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
} = {}) {
  const token = localStorage.getItem('accessToken');
  
  const queryParams = new URLSearchParams();
  if (params.role) queryParams.append('role', params.role);
  if (params.ativo !== undefined) queryParams.append('ativo', String(params.ativo));
  if (params.page !== undefined) queryParams.append('page', String(params.page));
  if (params.size !== undefined) queryParams.append('size', String(params.size));
  
  const response = await fetch(
    `http://localhost:8081/api/v1/tenants/${tenantId}/users?${queryParams.toString()}`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    let errorMessage = 'Erro ao listar usuários do tenant';
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

## 🛡️ Matriz de Permissões

### Resumo Visual

```
┌─────────────────┬──────────────┬──────────────┬─────────────┐
│     Ação        │ SUPER_ADMIN  │ TENANT_ADMIN │ TENANT_USER │
├─────────────────┼──────────────┼──────────────┼─────────────┤
│ Criar usuário   │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Listar usuários │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Buscar usuário  │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Editar usuário  │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Desativar       │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Reativar        │ ✅ Todos     │ ✅ Seu tenant│ ❌ Não      │
│ Alterar senha   │ ✅ Todos     │ ✅ Seu tenant│ ✅ Própria  │
└─────────────────┴──────────────┴──────────────┴─────────────┘
```

---

## ⚠️ Tratamento de Erros

### Formato de Resposta de Erro

Os endpoints de gerenciamento de usuários retornam erros em formato JSON quando possível:

```json
{
  "message": "Mensagem de erro descritiva",
  "error": "NomeDaClasseDoErro"
}
```

**Importante**: 
- Nem todos os erros retornam corpo JSON (alguns retornam apenas status HTTP)
- Sempre verifique o `Content-Type` antes de tentar fazer `response.json()`
- Use try-catch ao processar a resposta de erro

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|-------------|------------------|
| 200 | Sucesso | Processar resposta normalmente |
| 201 | Criado | Processar resposta normalmente |
| 400 | Bad Request | Exibir mensagem de erro ao usuário |
| 401 | Unauthorized | Token inválido - fazer refresh ou redirecionar para login |
| 403 | Forbidden | Usuário não tem permissão - exibir mensagem |
| 404 | Not Found | Recurso não encontrado - exibir mensagem |
| 409 | Conflict | Recurso já existe ou regra de negócio violada - exibir mensagem |
| 500 | Internal Server Error | Erro do servidor - tentar novamente |

### Estrutura de Erro Padrão

Os endpoints de gerenciamento de usuários retornam erros no seguinte formato:

```json
{
  "message": "Mensagem de erro descritiva",
  "error": "NomeDaClasseDoErro"
}
```

**Exemplos de estruturas de erro**:

**Erro de validação**:
```json
{
  "message": "Erro de validação: nome: Nome é obrigatório, email: Email inválido",
  "error": "ValidationError"
}
```

**Erro de negócio**:
```json
{
  "message": "Email já está em uso",
  "error": "RuntimeException"
}
```

**Erro de permissão**:
```json
{
  "message": "TENANT_ADMIN não pode criar SUPER_ADMIN",
  "error": "RuntimeException"
}
```

**Nota**: Alguns endpoints podem retornar apenas o status HTTP sem corpo de resposta em caso de erro (especialmente 404 e 500). Sempre verifique o status code e trate adequadamente. Se houver corpo de resposta, ele seguirá o formato acima.

### Função de Tratamento de Erros

```typescript
async function handleApiError(response: Response) {
  if (!response.ok) {
    let errorMessage = 'Erro desconhecido';
    
    try {
      // Tentar ler o corpo da resposta como JSON
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const error = await response.json();
        // Priorizar a mensagem do erro
        errorMessage = error.message || error.error || `Erro ${response.status}`;
      } else {
        // Se não houver JSON, usar status e statusText
        errorMessage = `Erro ${response.status}: ${response.statusText}`;
      }
    } catch (e) {
      // Se não conseguir ler o JSON, usar status
      errorMessage = `Erro ${response.status}: ${response.statusText}`;
    }
    
    throw new Error(errorMessage);
  }
  
  return response;
}
```

**Exemplo de uso**:
```typescript
try {
  const user = await createUser(userData);
  console.log('Usuário criado:', user);
} catch (error) {
  // error.message conterá a mensagem de erro do servidor
  console.error('Erro ao criar usuário:', error.message);
  // Exibir ao usuário: error.message
}
```

---

## 📝 Exemplos de Implementação

### Exemplo Completo: Serviço de Gerenciamento de Usuários

```typescript
class UserService {
  private baseURL = 'http://localhost:8081/api/v1';

  private async getAuthHeaders(): Promise<HeadersInit> {
    const token = localStorage.getItem('accessToken');
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    };
  }

  async createUser(userData: {
    nome: string;
    email: string;
    senha: string;
    roles: string[];
    tenantId?: string;
    telefone?: string;
  }) {
    const response = await fetch(`${this.baseURL}/users`, {
      method: 'POST',
      headers: await this.getAuthHeaders(),
      body: JSON.stringify(userData),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async listUsers(params: {
    tenantId?: string;
    role?: string;
    ativo?: boolean;
    email?: string;
    nome?: string;
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
      `${this.baseURL}/users?${queryParams.toString()}`,
      {
        method: 'GET',
        headers: await this.getAuthHeaders(),
      }
    );

    await this.handleApiError(response);
    return await response.json();
  }

  async getUserById(id: string) {
    const response = await fetch(`${this.baseURL}/users/${id}`, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async updateUser(id: string, userData: {
    nome: string;
    email: string;
    roles: string[];
    telefone?: string;
    ativo?: boolean;
  }) {
    const response = await fetch(`${this.baseURL}/users/${id}`, {
      method: 'PUT',
      headers: await this.getAuthHeaders(),
      body: JSON.stringify(userData),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async deactivateUser(id: string) {
    const response = await fetch(`${this.baseURL}/users/${id}`, {
      method: 'DELETE',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async activateUser(id: string) {
    const response = await fetch(`${this.baseURL}/users/${id}/activate`, {
      method: 'POST',
      headers: await this.getAuthHeaders(),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async changePassword(id: string, senhaAtual: string | null, novaSenha: string) {
    const body: any = { novaSenha };
    if (senhaAtual) {
      body.senhaAtual = senhaAtual;
    }

    const response = await fetch(`${this.baseURL}/users/${id}/password`, {
      method: 'PUT',
      headers: await this.getAuthHeaders(),
      body: JSON.stringify(body),
    });

    await this.handleApiError(response);
    return await response.json();
  }

  async listTenantUsers(tenantId: string, params: {
    role?: string;
    ativo?: boolean;
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
      `${this.baseURL}/tenants/${tenantId}/users?${queryParams.toString()}`,
      {
        method: 'GET',
        headers: await this.getAuthHeaders(),
      }
    );

    await this.handleApiError(response);
    return await response.json();
  }

  private async handleApiError(response: Response) {
    if (!response.ok) {
      let errorMessage = 'Erro desconhecido';
      try {
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
          const error = await response.json();
          errorMessage = error.message || error.error || `Erro ${response.status}`;
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

export const userService = new UserService();
```

### Exemplo: Componente React para Listagem de Usuários

```typescript
import React, { useState, useEffect } from 'react';
import { userService } from './services/UserService';

interface User {
  id: string;
  nome: string;
  email: string;
  roles: string[];
  ativo: boolean;
}

function UserList() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    loadUsers();
  }, [page]);

  async function loadUsers() {
    try {
      setLoading(true);
      setError(null);
      const response = await userService.listUsers({ page, size: 20 });
      setUsers(response.content);
      setTotalPages(response.totalPages);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar usuários');
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Carregando...</div>;
  if (error) return <div>Erro: {error}</div>;

  return (
    <div>
      <h1>Usuários</h1>
      <table>
        <thead>
          <tr>
            <th>Nome</th>
            <th>Email</th>
            <th>Roles</th>
            <th>Status</th>
            <th>Ações</th>
          </tr>
        </thead>
        <tbody>
          {users.map(user => (
            <tr key={user.id}>
              <td>{user.nome}</td>
              <td>{user.email}</td>
              <td>{user.roles.join(', ')}</td>
              <td>{user.ativo ? 'Ativo' : 'Inativo'}</td>
              <td>
                <button onClick={() => handleEdit(user.id)}>Editar</button>
                {user.ativo ? (
                  <button onClick={() => handleDeactivate(user.id)}>Desativar</button>
                ) : (
                  <button onClick={() => handleActivate(user.id)}>Reativar</button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
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

  async function handleDeactivate(id: string) {
    if (!confirm('Tem certeza que deseja desativar este usuário?')) return;
    try {
      await userService.deactivateUser(id);
      loadUsers();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Erro ao desativar usuário');
    }
  }

  async function handleActivate(id: string) {
    try {
      await userService.activateUser(id);
      loadUsers();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Erro ao reativar usuário');
    }
  }

  function handleEdit(id: string) {
    // Navegar para página de edição
    window.location.href = `/users/${id}/edit`;
  }
}
```

---

## 🔑 Informações Importantes

### Soft Delete

- Usuários **não são excluídos fisicamente** do banco de dados
- Apenas são marcados como `ativo: false` e `desativadoEm` é preenchido com a data/hora
- Usuários desativados não podem fazer login
- Usuários desativados não aparecem em listagens por padrão (use filtro `ativo=false`)
- Usuários desativados podem ser reativados usando o endpoint `POST /api/v1/users/{id}/activate`

### Comportamento do Filtro `ativo`

O filtro `ativo` tem um comportamento especial:

- **Quando não especificado**: Retorna apenas usuários ativos (`ativo=true`)
- **Quando `ativo=true`**: Retorna apenas usuários ativos
- **Quando `ativo=false`**: Retorna apenas usuários desativados
- **Para ver todos**: Atualmente não há suporte direto. Você precisaria fazer duas requisições separadas ou modificar o backend para aceitar um valor especial (ex: `ativo=all`)

### Validações Importantes

- ✅ **Email único**: Email deve ser único globalmente (não pode existir em outro usuário)
- ✅ **Auto-desativação**: Não pode desativar a si mesmo
- ✅ **Último SUPER_ADMIN**: Não pode desativar o último SUPER_ADMIN do sistema
- ✅ **Último TENANT_ADMIN**: Não pode desativar o último TENANT_ADMIN de um tenant
- ✅ **Criação de SUPER_ADMIN**: Apenas SUPER_ADMIN pode criar outros SUPER_ADMIN
- ✅ **Alteração de roles**: TENANT_ADMIN não pode alterar roles para `SUPER_ADMIN`
- ✅ **Senha**: Deve ter no mínimo 8 caracteres
- ✅ **Tenant**: SUPER_ADMIN sem tenantId não pode ter tenantId (deve ser `null`)
- ✅ **Tenant obrigatório**: Usuários que não são SUPER_ADMIN devem ter `tenantId`

### Paginação

- Padrão: `page=0`, `size=20`
- Use `hasNext` e `hasPrevious` para controlar navegação
- `totalPages` indica o número total de páginas

### Busca e Filtros

- Busca por `email` e `nome` é case-insensitive e parcial (usa regex do MongoDB)
- Filtros podem ser combinados
- **Importante**: O filtro `ativo` tem comportamento padrão:
  - Se `ativo` não for especificado, retorna apenas usuários ativos (`ativo=true`)
  - Para ver usuários desativados, use explicitamente `ativo=false`
  - Para ver todos os usuários (ativos e inativos), você precisaria fazer duas requisições ou modificar o backend

---

## 🔍 Troubleshooting

### Problemas Comuns

#### 403 Forbidden ao listar usuários

**Causa**: Usuário não tem permissão (não é SUPER_ADMIN ou TENANT_ADMIN)

**Solução**: Verifique se o usuário autenticado tem uma das roles: `SUPER_ADMIN` ou `TENANT_ADMIN`

#### 404 Not Found ao buscar usuário

**Causa**: 
- Usuário não existe
- Usuário existe mas não pertence ao tenant do usuário autenticado (para TENANT_ADMIN)

**Solução**: 
- Verifique se o ID do usuário está correto
- Se for TENANT_ADMIN, verifique se o usuário pertence ao seu tenant

#### 409 Conflict ao desativar usuário

**Causa**: Tentativa de desativar:
- A si mesmo
- O último SUPER_ADMIN
- O último TENANT_ADMIN de um tenant

**Solução**: 
- Não é possível desativar a si mesmo - use outro usuário com permissão
- Crie outro SUPER_ADMIN antes de desativar o último
- Crie outro TENANT_ADMIN no tenant antes de desativar o último

#### Email já está em uso

**Causa**: Tentativa de criar ou atualizar usuário com email que já existe

**Solução**: Use um email diferente ou atualize o usuário existente

#### Erro ao alterar senha própria

**Causa**: `senhaAtual` não informada ou incorreta

**Solução**: 
- Sempre informe `senhaAtual` ao alterar sua própria senha
- Verifique se a senha atual está correta

#### Erro "No request body" ao criar usuário

**Causa**: O corpo da requisição não foi enviado ou está vazio

**Solução**: 
- Verifique se está enviando o `Content-Type: application/json` no header
- Verifique se o body está sendo serializado corretamente com `JSON.stringify()`
- Certifique-se de que todos os campos obrigatórios estão presentes no body

**Exemplo correto**:
```typescript
const response = await fetch('http://localhost:8081/api/v1/users', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json', // ⚠️ IMPORTANTE
    'Authorization': `Bearer ${token}`,
  },
  body: JSON.stringify(userData), // ⚠️ IMPORTANTE: usar JSON.stringify
});
```

### Dicas de Implementação

1. **Cache de permissões**: Considere cachear as roles do usuário no frontend para evitar requisições desnecessárias
2. **Validação client-side**: Valide campos antes de enviar (ex: email válido, senha mínima de 8 caracteres)
3. **Feedback visual**: Mostre mensagens claras de erro ao usuário
4. **Refresh automático**: Implemente refresh automático de token antes de expirar
5. **Tratamento de 401**: Sempre trate 401 redirecionando para login ou tentando refresh token

---

## 📞 Suporte

Para dúvidas ou problemas, consulte a documentação completa da API ou entre em contato com a equipe de desenvolvimento.

