# 📋 Planejamento: Gerenciamento Completo de Usuários

## 🎯 Objetivo

Implementar CRUD completo de usuários com permissões baseadas em roles, permitindo que:
- **SUPER_ADMIN**: Gerencie todos os usuários (incluindo outros SUPER_ADMIN)
- **TENANT_ADMIN**: Gerencie usuários do seu próprio tenant

**Regra importante**: Não há exclusão física, apenas **desativação** (soft delete).

---

## 📊 Matriz de Permissões

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

---

## 🏗️ Estrutura Proposta: UserController

### Localização
```
src/main/java/br/com/verticelabs/pdfprocessor/interfaces/users/
  ├── UserController.java
  └── dto/
      ├── CreateUserRequest.java
      ├── UpdateUserRequest.java
      ├── UserResponse.java
      └── UserListResponse.java
```

### Base Path
```
/api/v1/users
```

---

## 📡 Endpoints Propostos

### 1. Criar Usuário

**POST** `/api/v1/users`

Cria um novo usuário no sistema.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode criar qualquer tipo de usuário em qualquer tenant
- `TENANT_ADMIN`: Pode criar usuários apenas no seu tenant

**Request Body:**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",  // Opcional para SUPER_ADMIN
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "senha": "senha123",
  "roles": ["TENANT_ADMIN"],  // SUPER_ADMIN, TENANT_ADMIN, TENANT_USER
  "telefone": "(11) 99999-9999"  // Opcional
}
```

**Regras:**
- Se `SUPER_ADMIN` não enviar `tenantId`, pode criar `SUPER_ADMIN` (sem tenant)
- Se `SUPER_ADMIN` enviar `tenantId`, cria usuário daquele tenant
- Se `TENANT_ADMIN`, o `tenantId` vem automaticamente do JWT (não pode especificar)
- Email deve ser único globalmente
- Senha será hasheada com Argon2

**Response 201 Created:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "telefone": "(11) 99999-9999",
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Códigos de Status:**
- `201 Created`: Usuário criado
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão
- `409 Conflict`: Email já existe
- `404 Not Found`: Tenant não encontrado (se especificado)

---

### 2. Listar Usuários

**GET** `/api/v1/users`

Lista usuários com filtros opcionais.

**Quem pode usar:**
- `SUPER_ADMIN`: Vê todos os usuários
- `TENANT_ADMIN`: Vê apenas usuários do seu tenant

**Query Parameters:**
- `tenantId` (opcional): Filtrar por tenant (apenas para SUPER_ADMIN)
- `role` (opcional): Filtrar por role (`SUPER_ADMIN`, `TENANT_ADMIN`, `TENANT_USER`)
- `ativo` (opcional): Filtrar por status (`true`, `false`)
- `email` (opcional): Buscar por email (busca parcial)
- `nome` (opcional): Buscar por nome (busca parcial)
- `page` (opcional, padrão: 0): Número da página
- `size` (opcional, padrão: 20): Tamanho da página

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": "507f1f77bcf86cd799439011",
      "tenantId": "550e8400-e29b-41d4-a716-446655440000",
      "nome": "João Silva",
      "email": "joao@empresa.com.br",
      "roles": ["TENANT_ADMIN"],
      "telefone": "(11) 99999-9999",
      "ativo": true,
      "twoFactorEnabled": false,
      "createdAt": "2024-01-15T10:30:00Z"
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

**Códigos de Status:**
- `200 OK`: Lista retornada
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão

---

### 3. Buscar Usuário por ID

**GET** `/api/v1/users/{id}`

Retorna detalhes completos de um usuário.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode buscar qualquer usuário
- `TENANT_ADMIN`: Pode buscar apenas usuários do seu tenant

**Path Parameters:**
- `id` (string, obrigatório): ID do usuário

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "telefone": "(11) 99999-9999",
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-16T14:20:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Usuário encontrado
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão ou usuário não pertence ao seu tenant
- `404 Not Found`: Usuário não encontrado

---

### 4. Atualizar Usuário

**PUT** `/api/v1/users/{id}`

Atualiza dados de um usuário existente.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode atualizar qualquer usuário
- `TENANT_ADMIN`: Pode atualizar apenas usuários do seu tenant

**Path Parameters:**
- `id` (string, obrigatório): ID do usuário

**Request Body:**
```json
{
  "nome": "João Silva Santos",
  "email": "joao.silva@empresa.com.br",
  "telefone": "(11) 88888-8888",
  "roles": ["TENANT_ADMIN"],
  "ativo": true
}
```

**Regras:**
- Email pode ser alterado, mas deve ser único globalmente
- Roles podem ser alteradas (com validação de permissões)
- Senha **não** pode ser alterada por este endpoint (usar endpoint específico)
- `ativo` pode ser alterado (desativar/reativar)

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva Santos",
  "email": "joao.silva@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "telefone": "(11) 88888-8888",
  "ativo": true,
  "twoFactorEnabled": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-17T09:15:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Usuário atualizado
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão ou usuário não pertence ao seu tenant
- `404 Not Found`: Usuário não encontrado
- `409 Conflict`: Email já existe (se alterado)

---

### 5. Desativar Usuário

**DELETE** `/api/v1/users/{id}`

Desativa um usuário (soft delete - não remove do banco).

**Quem pode usar:**
- `SUPER_ADMIN`: Pode desativar qualquer usuário
- `TENANT_ADMIN`: Pode desativar apenas usuários do seu tenant

**Path Parameters:**
- `id` (string, obrigatório): ID do usuário

**Regras:**
- Não remove o usuário do banco de dados
- Apenas marca `ativo: false`
- Usuário desativado não pode fazer login
- Usuário desativado não aparece em listagens (a menos que filtro `ativo=false`)

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "ativo": false,
  "desativadoEm": "2024-01-17T10:30:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Usuário desativado
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão ou usuário não pertence ao seu tenant
- `404 Not Found`: Usuário não encontrado
- `409 Conflict`: Não pode desativar a si mesmo

---

### 6. Reativar Usuário

**POST** `/api/v1/users/{id}/activate`

Reativa um usuário desativado.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode reativar qualquer usuário
- `TENANT_ADMIN`: Pode reativar apenas usuários do seu tenant

**Path Parameters:**
- `id` (string, obrigatório): ID do usuário

**Response 200 OK:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "João Silva",
  "email": "joao@empresa.com.br",
  "roles": ["TENANT_ADMIN"],
  "ativo": true,
  "reativadoEm": "2024-01-18T10:30:00Z"
}
```

**Códigos de Status:**
- `200 OK`: Usuário reativado
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão ou usuário não pertence ao seu tenant
- `404 Not Found`: Usuário não encontrado

---

### 7. Alterar Senha (Endpoint Adicional)

**PUT** `/api/v1/users/{id}/password`

Altera a senha de um usuário.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode alterar senha de qualquer usuário
- `TENANT_ADMIN`: Pode alterar senha apenas de usuários do seu tenant
- **Próprio usuário**: Pode alterar sua própria senha (com senha atual)

**Path Parameters:**
- `id` (string, obrigatório): ID do usuário

**Request Body:**
```json
{
  "senhaAtual": "senhaAntiga123",  // Obrigatório se for próprio usuário
  "novaSenha": "novaSenha123"
}
```

**Regras:**
- Se for próprio usuário, deve informar `senhaAtual`
- Se for ADMIN, não precisa informar `senhaAtual`
- Nova senha será hasheada com Argon2

**Response 200 OK:**
```json
{
  "message": "Senha alterada com sucesso"
}
```

**Códigos de Status:**
- `200 OK`: Senha alterada
- `400 Bad Request`: Senha atual incorreta (se próprio usuário)
- `401 Unauthorized`: Token inválido
- `403 Forbidden`: Sem permissão
- `404 Not Found`: Usuário não encontrado

---

### 8. Listar Usuários de um Tenant (Endpoint Adicional)

**GET** `/api/v1/tenants/{tenantId}/users`

Lista todos os usuários de um tenant específico.

**Quem pode usar:**
- `SUPER_ADMIN`: Pode listar usuários de qualquer tenant
- `TENANT_ADMIN`: Pode listar apenas usuários do seu próprio tenant (tenantId deve ser o seu)

**Path Parameters:**
- `tenantId` (string, obrigatório): ID do tenant

**Query Parameters:**
- `role` (opcional): Filtrar por role
- `ativo` (opcional): Filtrar por status
- `page` (opcional): Número da página
- `size` (opcional): Tamanho da página

**Response 200 OK:**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantNome": "Empresa ABC Ltda",
  "content": [
    {
      "id": "507f1f77bcf86cd799439011",
      "nome": "João Silva",
      "email": "joao@empresa.com.br",
      "roles": ["TENANT_ADMIN"],
      "ativo": true
    }
  ],
  "totalElements": 10,
  "totalPages": 1
}
```

---

## 📦 Modelos de Dados (DTOs)

### CreateUserRequest

```java
@Data
public class CreateUserRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
    private String senha;
    
    @NotEmpty(message = "Roles são obrigatórias")
    private Set<String> roles;  // SUPER_ADMIN, TENANT_ADMIN, TENANT_USER
    
    private String tenantId;  // Opcional para SUPER_ADMIN
    
    private String telefone;  // Opcional
}
```

### UpdateUserRequest

```java
@Data
public class UpdateUserRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;
    
    @NotEmpty(message = "Roles são obrigatórias")
    private Set<String> roles;
    
    private String telefone;
    
    private Boolean ativo;
}
```

### UserResponse

```java
@Data
@Builder
public class UserResponse {
    private String id;
    private String tenantId;
    private String tenantNome;  // Nome do tenant (se houver)
    private String nome;
    private String email;
    private String telefone;
    private Set<String> roles;
    private Boolean ativo;
    private Boolean twoFactorEnabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant desativadoEm;  // Se desativado
}
```

### ChangePasswordRequest

```java
@Data
public class ChangePasswordRequest {
    private String senhaAtual;  // Obrigatório se for próprio usuário
    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 8, message = "Nova senha deve ter no mínimo 8 caracteres")
    private String novaSenha;
}
```

---

## 🔐 Regras de Validação e Segurança

### 1. Validação de Roles

**SUPER_ADMIN pode criar:**
- `SUPER_ADMIN` (sem tenantId ou com tenantId null)
- `TENANT_ADMIN` (com tenantId válido)
- `TENANT_USER` (com tenantId válido)

**TENANT_ADMIN pode criar:**
- `TENANT_ADMIN` (do seu tenant)
- `TENANT_USER` (do seu tenant)
- ❌ **NÃO pode criar** `SUPER_ADMIN`

### 2. Validação de Tenant

- `SUPER_ADMIN` pode especificar qualquer `tenantId` ou criar sem tenant (para SUPER_ADMIN)
- `TENANT_ADMIN` não pode especificar `tenantId` - vem automaticamente do JWT
- Tenant deve existir e estar ativo

### 3. Validação de Email

- Email deve ser único globalmente
- Não pode alterar para um email já existente

### 4. Proteções Especiais

- Usuário não pode desativar a si mesmo
- Último SUPER_ADMIN não pode ser desativado
- Último TENANT_ADMIN de um tenant não pode ser desativado

### 5. Isolamento Multi-Tenant

- `TENANT_ADMIN` só vê/gerencia usuários do seu tenant
- `SUPER_ADMIN` vê todos os usuários
- Filtros automáticos aplicados baseados no role

---

## 🏗️ Estrutura de Implementação

### Use Cases Necessários

```
src/main/java/br/com/verticelabs/pdfprocessor/application/users/
  ├── CreateUserUseCase.java
  ├── ListUsersUseCase.java
  ├── GetUserByIdUseCase.java
  ├── UpdateUserUseCase.java
  ├── DeactivateUserUseCase.java
  ├── ActivateUserUseCase.java
  └── ChangePasswordUseCase.java
```

### Repository

O `UserRepository` já existe, mas pode precisar de métodos adicionais:

```java
public interface UserRepository {
    // Métodos existentes
    Mono<User> findByEmail(String email);
    Mono<User> findById(String id);
    
    // Métodos adicionais necessários
    Flux<User> findAllByTenantId(String tenantId);
    Flux<User> findAllByTenantIdAndAtivo(String tenantId, Boolean ativo);
    Mono<Boolean> existsByEmail(String email);
    Mono<User> save(User user);
    Mono<Long> countByTenantId(String tenantId);
    Mono<Long> countByTenantIdAndRole(String tenantId, String role);
}
```

---

## 📋 Checklist de Implementação

### Fase 1: Estrutura Base
- [ ] Criar `UserController.java`
- [ ] Criar DTOs (`CreateUserRequest`, `UpdateUserRequest`, `UserResponse`, etc.)
- [ ] Criar Use Cases básicos
- [ ] Adicionar métodos necessários no `UserRepository`

### Fase 2: Endpoints CRUD
- [ ] POST `/api/v1/users` - Criar usuário
- [ ] GET `/api/v1/users` - Listar usuários (com paginação)
- [ ] GET `/api/v1/users/{id}` - Buscar usuário por ID
- [ ] PUT `/api/v1/users/{id}` - Atualizar usuário
- [ ] DELETE `/api/v1/users/{id}` - Desativar usuário
- [ ] POST `/api/v1/users/{id}/activate` - Reativar usuário

### Fase 3: Endpoints Adicionais
- [ ] PUT `/api/v1/users/{id}/password` - Alterar senha
- [ ] GET `/api/v1/tenants/{tenantId}/users` - Listar usuários de um tenant

### Fase 4: Validações e Segurança
- [ ] Implementar validação de permissões por role
- [ ] Implementar isolamento multi-tenant
- [ ] Implementar proteções especiais (não desativar a si mesmo, etc.)
- [ ] Adicionar validações de negócio

### Fase 5: Testes
- [ ] Testes unitários dos Use Cases
- [ ] Testes de integração dos endpoints
- [ ] Testes de permissões e isolamento

### Fase 6: Documentação
- [ ] Atualizar documentação da API
- [ ] Criar documentação para frontend
- [ ] Exemplos de uso

---

## 🔄 Fluxos de Trabalho

### Fluxo 1: SUPER_ADMIN cria TENANT_ADMIN

```
1. SUPER_ADMIN faz login
2. SUPER_ADMIN cria tenant (se não existe)
3. SUPER_ADMIN cria TENANT_ADMIN:
   POST /api/v1/users
   {
     "tenantId": "xxx",
     "nome": "Admin",
     "email": "admin@tenant.com",
     "senha": "senha123",
     "roles": ["TENANT_ADMIN"]
   }
4. TENANT_ADMIN recebe credenciais e pode fazer login
```

### Fluxo 2: TENANT_ADMIN cria usuários do seu tenant

```
1. TENANT_ADMIN faz login
2. TENANT_ADMIN cria usuário:
   POST /api/v1/users
   {
     "nome": "Usuário",
     "email": "user@tenant.com",
     "senha": "senha123",
     "roles": ["TENANT_USER"]
   }
   // tenantId vem automaticamente do JWT
3. Usuário recebe credenciais e pode fazer login
```

### Fluxo 3: Desativar usuário

```
1. ADMIN (SUPER ou TENANT) lista usuários
2. ADMIN seleciona usuário para desativar
3. ADMIN chama:
   DELETE /api/v1/users/{id}
4. Usuário é marcado como inativo
5. Usuário não pode mais fazer login
6. Usuário pode ser reativado posteriormente
```

---

## 📝 Notas de Implementação

### 1. Migração dos Endpoints Existentes

Os endpoints atuais em `AuthController`:
- `POST /api/v1/auth/register/admin`
- `POST /api/v1/auth/register/user`

**Podem ser mantidos** para compatibilidade ou **migrados** para o novo `UserController`.

**Recomendação**: Manter ambos por um período de transição, depois deprecar os do `AuthController`.

### 2. Campo `telefone` no Model User

O modelo `User` atual não tem campo `telefone`. Será necessário:
- Adicionar campo `telefone` no modelo `User`
- Atualizar repositório e adapters
- Criar migration se necessário

### 3. Campo `desativadoEm` no Model User

Adicionar campo para rastrear quando o usuário foi desativado:
```java
private Instant desativadoEm;
```

### 4. Paginação

Usar Spring Data paginação reativa:
```java
Pageable pageable = PageRequest.of(page, size);
```

### 5. Busca e Filtros

Implementar busca por nome e email usando regex ou texto completo do MongoDB.

---

## 🎯 Resumo Executivo

### O que será implementado:

1. **Novo Controller**: `UserController` em `/api/v1/users`
2. **CRUD Completo**: Create, Read, Update, Delete (soft delete)
3. **Permissões por Role**: SUPER_ADMIN e TENANT_ADMIN com permissões diferentes
4. **Isolamento Multi-Tenant**: TENANT_ADMIN só gerencia seu tenant
5. **Endpoints Adicionais**: Alterar senha, reativar usuário, listar por tenant

### Benefícios:

- ✅ Gerenciamento completo de usuários via API
- ✅ Interface administrativa pode ser construída
- ✅ Separação clara de responsabilidades
- ✅ Flexibilidade para diferentes tipos de usuários

---

**Próximos Passos**: Revisar este planejamento e iniciar implementação seguindo o checklist.

