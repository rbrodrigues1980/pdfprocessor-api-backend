# 📋 Organização das APIs: Autenticação, Usuários e Tenants

## 🎯 Resumo Executivo

**Sim, existe separação, mas com algumas particularidades:**

1. **`/api/v1/auth`** - Autenticação **E** criação de usuários
2. **`/api/v1/tenants`** - Gerenciamento de tenants (empresas)
3. **Não existe `/api/v1/users`** - Gerenciamento de usuários está no AuthController

---

## 📊 Estrutura Atual das APIs

### 1️⃣ API de Autenticação (`/api/v1/auth`)

**Controller**: `AuthController.java`

**Responsabilidades MISTAS:**
- ✅ Autenticação (login, logout, refresh, 2FA)
- ✅ **Criação de usuários** (register/admin, register/user)

#### Endpoints Disponíveis:

| Método | Endpoint | Descrição | Quem pode usar |
|--------|----------|-----------|----------------|
| `POST` | `/api/v1/auth/login` | Login no sistema | Qualquer usuário |
| `POST` | `/api/v1/auth/verify-2fa` | Verificar código 2FA | Usuário com 2FA ativado |
| `POST` | `/api/v1/auth/refresh` | Renovar tokens | Qualquer usuário autenticado |
| `POST` | `/api/v1/auth/logout` | Logout (invalida refresh token) | Qualquer usuário autenticado |
| `POST` | `/api/v1/auth/register/admin` | **Criar admin de tenant** | **Apenas SUPER_ADMIN** |
| `POST` | `/api/v1/auth/register/user` | **Criar usuário comum** | **TENANT_ADMIN** ou SUPER_ADMIN |

---

### 2️⃣ API de Tenants (`/api/v1/tenants`)

**Controller**: `TenantController.java`

**Responsabilidades:**
- ✅ Gerenciamento de tenants (empresas)
- ✅ CRUD completo de tenants

#### Endpoints Disponíveis:

| Método | Endpoint | Descrição | Quem pode usar |
|--------|----------|-----------|----------------|
| `GET` | `/api/v1/tenants` | Listar todos os tenants | **Apenas SUPER_ADMIN** |
| `POST` | `/api/v1/tenants` | Criar novo tenant | **Apenas SUPER_ADMIN** |
| `GET` | `/api/v1/tenants/{id}` | Buscar tenant por ID | **Apenas SUPER_ADMIN** |

---

### 3️⃣ API de Usuários

**⚠️ IMPORTANTE**: **NÃO existe um controller separado** (`UserController`)

**Gerenciamento de usuários está no `AuthController`:**

- ✅ **Criar usuário**: `/api/v1/auth/register/admin` ou `/api/v1/auth/register/user`
- ❌ **Listar usuários**: Não existe endpoint
- ❌ **Editar usuário**: Não existe endpoint
- ❌ **Deletar usuário**: Não existe endpoint
- ❌ **Buscar usuário por ID**: Não existe endpoint

---

## 🔍 Detalhamento dos Endpoints de Criação de Usuários

### POST `/api/v1/auth/register/admin`

**Cria um administrador de tenant (TENANT_ADMIN)**

**Quem pode usar**: Apenas `SUPER_ADMIN`

**Request Body:**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "senha": "senha123"
}
```

**Response 201 Created:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Características:**
- Cria usuário com role `TENANT_ADMIN`
- Vincula ao tenant especificado em `tenantId`
- Valida se o tenant existe e está ativo
- Valida se o email já está em uso (globalmente único)

---

### POST `/api/v1/auth/register/user`

**Cria um usuário comum (TENANT_USER ou role customizada)**

**Quem pode usar**: `TENANT_ADMIN` ou `SUPER_ADMIN`

**Request Body:**
```json
{
  "nome": "Maria Santos",
  "email": "maria@empresa.com.br",
  "senha": "senha123",
  "roles": ["TENANT_USER"]  // Opcional, padrão: TENANT_USER
}
```

**Response 201 Created:**
```json
{
  "id": "507f1f77bcf86cd799439012",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Maria Santos",
  "email": "maria@empresa.com.br",
  "roles": ["TENANT_USER"],
  "ativo": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Características:**
- Cria usuário com role `TENANT_USER` (padrão) ou roles customizadas
- **Vincula automaticamente ao tenant do usuário logado** (obtido do JWT)
- Se for SUPER_ADMIN, pode usar `X-Tenant-ID` header para especificar o tenant
- Valida se o email já está em uso (globalmente único)

---

## 🎯 Fluxo de Criação de Usuário pelo SUPER_ADMIN

### Cenário: SUPER_ADMIN quer criar um usuário para um tenant

**Passo 1**: Criar o tenant (se ainda não existe)
```http
POST /api/v1/tenants
Authorization: Bearer {superAdminToken}

{
  "nome": "Empresa ABC Ltda",
  "dominio": "empresaabc.com.br"
}
```

**Passo 2**: Criar o admin do tenant
```http
POST /api/v1/auth/register/admin
Authorization: Bearer {superAdminToken}

{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",  // ID do tenant criado
  "nome": "João Silva",
  "email": "joao@empresaabc.com.br",
  "senha": "senha123"
}
```

**Passo 3**: (Opcional) O TENANT_ADMIN pode criar usuários comuns
```http
POST /api/v1/auth/register/user
Authorization: Bearer {tenantAdminToken}  // Token do TENANT_ADMIN

{
  "nome": "Maria Santos",
  "email": "maria@empresaabc.com.br",
  "senha": "senha123",
  "roles": ["TENANT_USER"]
}
```

---

## ❓ Por que não existe UserController?

**Arquitetura atual:**
- A criação de usuários está no `AuthController` porque está relacionada ao processo de registro/autenticação
- **Não há endpoints para gerenciamento completo** (listar, editar, deletar usuários)

**Possíveis razões:**
1. **Foco inicial**: Sistema foi desenvolvido focando em autenticação e criação básica
2. **Simplicidade**: Gerenciamento completo de usuários pode ser feito via banco de dados ou interface administrativa
3. **Segurança**: Limitar endpoints reduz superfície de ataque

---

## 🔧 O que está faltando? (Funcionalidades não implementadas)

Se você precisa de gerenciamento completo de usuários, seria necessário criar:

### Endpoints que não existem:

1. **GET `/api/v1/users`** - Listar usuários
   - SUPER_ADMIN: todos os usuários
   - TENANT_ADMIN: usuários do seu tenant

2. **GET `/api/v1/users/{id}`** - Buscar usuário por ID

3. **PUT `/api/v1/users/{id}`** - Atualizar usuário
   - Editar nome, email, roles, status ativo/inativo

4. **DELETE `/api/v1/users/{id}`** - Deletar/desativar usuário

5. **GET `/api/v1/tenants/{id}/users`** - Listar usuários de um tenant específico

---

## 📝 Resumo: Qual API usar para cada coisa?

| Ação | Endpoint | Controller |
|------|----------|------------|
| **Login** | `POST /api/v1/auth/login` | AuthController |
| **Logout** | `POST /api/v1/auth/logout` | AuthController |
| **Refresh Token** | `POST /api/v1/auth/refresh` | AuthController |
| **Verificar 2FA** | `POST /api/v1/auth/verify-2fa` | AuthController |
| **Criar Tenant** | `POST /api/v1/tenants` | TenantController |
| **Listar Tenants** | `GET /api/v1/tenants` | TenantController |
| **Buscar Tenant** | `GET /api/v1/tenants/{id}` | TenantController |
| **Criar Admin de Tenant** | `POST /api/v1/auth/register/admin` | AuthController |
| **Criar Usuário Comum** | `POST /api/v1/auth/register/user` | AuthController |
| **Listar Usuários** | ❌ Não existe | - |
| **Editar Usuário** | ❌ Não existe | - |
| **Deletar Usuário** | ❌ Não existe | - |

---

## 🎯 Resposta à sua dúvida específica

> "eu tenho apis que cuidam dos tenant, autenticação e usuarios? se sim, quais sao? existe essa separação?"

### Resposta:

**Sim, existe separação parcial:**

1. **Tenants**: ✅ **Separado** - `TenantController` (`/api/v1/tenants`)
2. **Autenticação**: ✅ **Separado** - `AuthController` (`/api/v1/auth`)
3. **Usuários**: ⚠️ **Parcialmente no AuthController** - Apenas criação, sem gerenciamento completo

### Estrutura:

```
/api/v1/auth
  ├── Autenticação (login, logout, refresh, 2FA)
  └── Criação de Usuários (register/admin, register/user)

/api/v1/tenants
  └── Gerenciamento de Tenants (CRUD completo)

/api/v1/users  ❌ NÃO EXISTE
  └── Gerenciamento de Usuários (não implementado)
```

---

## 💡 Recomendação para o Frontend

### Para criar um usuário como SUPER_ADMIN:

**Use o endpoint:**
```http
POST /api/v1/auth/register/admin
Authorization: Bearer {superAdminToken}
Content-Type: application/json

{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Vanderson",
  "email": "vanderson@lbs.com.br",
  "senha": "senha123"
}
```

**Ou para criar usuário comum:**
```http
POST /api/v1/auth/register/user
Authorization: Bearer {superAdminToken}
X-Tenant-ID: {tenantId}  // Se SUPER_ADMIN, pode especificar tenant
Content-Type: application/json

{
  "nome": "Vanderson",
  "email": "vanderson@lbs.com.br",
  "senha": "senha123",
  "roles": ["TENANT_USER"]
}
```

---

## 📚 Documentações Relacionadas

- [API de Autenticação](./API_AUTH_FRONTEND.md) - Documentação completa de autenticação
- [API de Tenants](./API_TENANTS_FRONTEND.md) - Documentação completa de tenants

---

**Última atualização**: Janeiro 2024

