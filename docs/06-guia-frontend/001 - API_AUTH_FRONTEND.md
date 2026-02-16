# 🔐 API de Autenticação - Documentação para Frontend

Esta documentação descreve todos os endpoints de autenticação da API e como implementá-los no frontend.

## 📋 Índice

- [Configuração Base](#configuração-base)
- [Fluxo de Autenticação](#fluxo-de-autenticação)
- [Endpoints](#endpoints)
  - [POST /api/v1/auth/login](#1-post-apiv1authlogin)
  - [POST /api/v1/auth/verify-2fa](#2-post-apiv1authverify-2fa)
  - [POST /api/v1/auth/refresh](#3-post-apiv1authrefresh)
  - [POST /api/v1/auth/logout](#4-post-apiv1authlogout)
  - [POST /api/v1/auth/register/user](#5-post-apiv1authregisteruser)
  - [POST /api/v1/auth/register/admin](#6-post-apiv1authregisteradmin)
- [Proteção de Rotas](#proteção-de-rotas)
- [Armazenamento de Tokens](#armazenamento-de-tokens)
- [Tratamento de Erros](#tratamento-de-erros)
- [Exemplos de Implementação](#exemplos-de-implementação)

---

## 🔧 Configuração Base

### Base URL
```
http://localhost:8081/api/v1
```

### Headers Padrão
Todas as requisições devem incluir:
```javascript
{
  "Content-Type": "application/json",
  "Accept": "application/json"
}
```

### Headers para Requisições Autenticadas
Após o login, todas as requisições protegidas devem incluir:
```javascript
{
  "Authorization": "Bearer {accessToken}",
  "Content-Type": "application/json"
}
```

**Importante**: O `accessToken` expira em **15 minutos**. Use o `refreshToken` para obter um novo token antes que expire.

---

## 🔄 Fluxo de Autenticação

### Fluxo Completo

```
1. Usuário faz login → POST /api/v1/auth/login
   ↓
2. Se requires2FA = true:
   - Exibir tela de 2FA
   - Usuário insere código
   - POST /api/v1/auth/verify-2fa
   ↓
3. Recebe accessToken e refreshToken
   ↓
4. Armazenar tokens (localStorage/sessionStorage)
   ↓
5. Usar accessToken em todas as requisições protegidas
   ↓
6. Quando accessToken expirar (401):
   - Usar refreshToken → POST /api/v1/auth/refresh
   - Obter novo accessToken
   - Repetir requisição original
   ↓
7. Logout → POST /api/v1/auth/logout
```

### Fluxo de Refresh Token

```
Requisição protegida → 401 Unauthorized
   ↓
Interceptar erro 401
   ↓
POST /api/v1/auth/refresh com refreshToken
   ↓
Novo accessToken recebido
   ↓
Repetir requisição original com novo token
```

---

## 📡 Endpoints

### 1. POST /api/v1/auth/login

Realiza o login do usuário no sistema.

**URL**: `/api/v1/auth/login`  
**Método**: `POST`  
**Autenticação**: Não requerida

#### Request Body

```json
{
  "email": "usuario@exemplo.com",
  "password": "Senha123!"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `email` | string | Sim | Email único do usuário |
| `password` | string | Sim | Senha do usuário |

#### Response Success (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "requires2FA": false,
  "message": null
}
```

**Se 2FA estiver habilitado**:
```json
{
  "accessToken": null,
  "refreshToken": null,
  "requires2FA": true,
  "message": "Código de verificação enviado por e-mail"
}
```

#### Response Error (401 Unauthorized)

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Credenciais inválidas",
  "path": "/api/v1/auth/login"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function login(email: string, password: string) {
  const response = await fetch('http://localhost:8081/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Erro ao fazer login');
  }

  const data = await response.json();
  
  if (data.requires2FA) {
    // Redirecionar para tela de 2FA
    return { requires2FA: true, email, message: data.message };
  }

  // Armazenar tokens
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  
  return data;
}
```

---

### 2. POST /api/v1/auth/verify-2fa

Verifica o código de autenticação de dois fatores (2FA).

**URL**: `/api/v1/auth/verify-2fa`  
**Método**: `POST`  
**Autenticação**: Não requerida

#### Request Body

```json
{
  "email": "usuario@exemplo.com",
  "code": "123456"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `email` | string | Sim | Email do usuário que fez login |
| `code` | string | Sim | Código de 6 dígitos recebido por e-mail |

#### Response Success (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "requires2FA": false,
  "message": null
}
```

#### Response Error (401 Unauthorized)

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Código 2FA inválido ou expirado",
  "path": "/api/v1/auth/verify-2fa"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function verify2FA(email: string, code: string) {
  const response = await fetch('http://localhost:8081/api/v1/auth/verify-2fa', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, code }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Código inválido');
  }

  const data = await response.json();
  
  // Armazenar tokens
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  
  return data;
}
```

---

### 3. POST /api/v1/auth/refresh

Renova o access token usando o refresh token.

**URL**: `/api/v1/auth/refresh`  
**Método**: `POST`  
**Autenticação**: Não requerida (usa refreshToken no body)

#### Request Body

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `refreshToken` | string | Sim | Refresh token armazenado |

#### Response Success (200 OK)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "requires2FA": false,
  "message": null
}
```

**Nota**: O `refreshToken` pode ser o mesmo ou um novo, dependendo da implementação. Sempre atualize o armazenado.

#### Response Error (401 Unauthorized)

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token inválido ou expirado",
  "path": "/api/v1/auth/refresh"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function refreshToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  
  if (!refreshToken) {
    throw new Error('Refresh token não encontrado');
  }

  const response = await fetch('http://localhost:8081/api/v1/auth/refresh', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    // Refresh token inválido - fazer logout
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/login';
    throw new Error('Sessão expirada. Faça login novamente.');
  }

  const data = await response.json();
  
  // Atualizar tokens
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  
  return data.accessToken;
}
```

---

### 4. POST /api/v1/auth/logout

Realiza o logout do usuário, invalidando o refresh token.

**URL**: `/api/v1/auth/logout`  
**Método**: `POST`  
**Autenticação**: Não requerida (usa refreshToken no body)

#### Request Body

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `refreshToken` | string | Sim | Refresh token a ser invalidado |

#### Response Success (204 No Content)

Sem corpo de resposta.

#### Exemplo JavaScript/TypeScript

```typescript
async function logout() {
  const refreshToken = localStorage.getItem('refreshToken');
  
  if (refreshToken) {
    try {
      await fetch('http://localhost:8081/api/v1/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken }),
      });
    } catch (error) {
      console.error('Erro ao fazer logout:', error);
    }
  }

  // Limpar tokens localmente
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  
  // Redirecionar para login
  window.location.href = '/login';
}
```

---

### 5. POST /api/v1/auth/register/user

Registra um novo usuário no sistema.

**URL**: `/api/v1/auth/register/user`  
**Método**: `POST`  
**Autenticação**: Não requerida

#### Request Body

```json
{
  "nome": "João Silva",
  "email": "joao@exemplo.com",
  "senha": "Senha123!",
  "roles": ["TENANT_USER"]
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `nome` | string | Sim | Nome completo do usuário |
| `email` | string | Sim | Email único (não pode existir no sistema) |
| `senha` | string | Sim | Senha do usuário |
| `roles` | string[] | Não | Roles do usuário. Padrão: `["TENANT_USER"]`. Valores possíveis: `TENANT_USER`, `TENANT_ADMIN` |

#### Response Success (201 Created)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "33f275b4-a103-4336-be7b-3bb7e00565cb",
  "nome": "João Silva",
  "email": "joao@exemplo.com",
  "roles": ["TENANT_USER"],
  "twoFactorEnabled": false,
  "ativo": true,
  "createdAt": "2025-12-01T10:00:00Z"
}
```

**Nota**: O campo `senhaHash` não é retornado por segurança.

#### Response Error (400 Bad Request)

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Email já cadastrado",
  "path": "/api/v1/auth/register/user"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function registerUser(nome: string, email: string, senha: string, roles?: string[]) {
  const response = await fetch('http://localhost:8081/api/v1/auth/register/user', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      nome,
      email,
      senha,
      roles: roles || ['TENANT_USER'],
    }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Erro ao registrar usuário');
  }

  return await response.json();
}
```

---

### 6. POST /api/v1/auth/register/admin

Registra um novo administrador de tenant.

**URL**: `/api/v1/auth/register/admin`  
**Método**: `POST`  
**Autenticação**: Não requerida

#### Request Body

```json
{
  "tenantId": "33f275b4-a103-4336-be7b-3bb7e00565cb",
  "nome": "Admin Tenant",
  "email": "admin@exemplo.com",
  "senha": "Admin123!"
}
```

#### Campos

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `tenantId` | string | Sim | ID do tenant ao qual o admin pertence |
| `nome` | string | Sim | Nome completo do administrador |
| `email` | string | Sim | Email único (não pode existir no sistema) |
| `senha` | string | Sim | Senha do administrador |

#### Response Success (201 Created)

```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "33f275b4-a103-4336-be7b-3bb7e00565cb",
  "nome": "Admin Tenant",
  "email": "admin@exemplo.com",
  "roles": ["TENANT_ADMIN"],
  "twoFactorEnabled": false,
  "ativo": true,
  "createdAt": "2025-12-01T10:00:00Z"
}
```

#### Response Error (400 Bad Request)

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Email já cadastrado",
  "path": "/api/v1/auth/register/admin"
}
```

#### Exemplo JavaScript/TypeScript

```typescript
async function registerAdmin(tenantId: string, nome: string, email: string, senha: string) {
  const response = await fetch('http://localhost:8081/api/v1/auth/register/admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      tenantId,
      nome,
      email,
      senha,
    }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message || 'Erro ao registrar administrador');
  }

  return await response.json();
}
```

---

## 🛡️ Proteção de Rotas

### Implementação de Interceptor HTTP

Todas as requisições para endpoints protegidos devem incluir o `Authorization` header. Implemente um interceptor HTTP para adicionar automaticamente o token:

#### Exemplo com Axios

```typescript
import axios from 'axios';

// Criar instância do axios
const api = axios.create({
  baseURL: 'http://localhost:8081/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor para adicionar token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor para tratar 401 e fazer refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Se erro 401 e ainda não tentou refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        // Tentar refresh token
        const newToken = await refreshToken();
        
        // Atualizar header e repetir requisição
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh falhou - fazer logout
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

#### Exemplo com Fetch API

```typescript
async function fetchWithAuth(url: string, options: RequestInit = {}) {
  const token = localStorage.getItem('accessToken');
  
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let response = await fetch(`http://localhost:8081/api/v1${url}`, {
    ...options,
    headers,
  });

  // Se 401, tentar refresh
  if (response.status === 401) {
    try {
      const newToken = await refreshToken();
      headers['Authorization'] = `Bearer ${newToken}`;
      
      // Repetir requisição
      response = await fetch(`http://localhost:8081/api/v1${url}`, {
        ...options,
        headers,
      });
    } catch (error) {
      // Refresh falhou - fazer logout
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
      throw error;
    }
  }

  return response;
}
```

### Proteção de Rotas no Frontend

Implemente um guard/componente para proteger rotas que requerem autenticação:

#### Exemplo React (com React Router)

```typescript
import { Navigate } from 'react-router-dom';

function ProtectedRoute({ children }: { children: JSX.Element }) {
  const token = localStorage.getItem('accessToken');
  
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  
  return children;
}

// Uso
<Route
  path="/dashboard"
  element={
    <ProtectedRoute>
      <Dashboard />
    </ProtectedRoute>
  }
/>
```

#### Exemplo Vue (com Vue Router)

```typescript
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('accessToken');
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);

  if (requiresAuth && !token) {
    next('/login');
  } else {
    next();
  }
});
```

---

## 💾 Armazenamento de Tokens

### Recomendações

1. **localStorage**: Use para armazenar tokens (persiste entre sessões)
2. **sessionStorage**: Use se quiser que os tokens sejam limpos ao fechar a aba
3. **Cookies HttpOnly**: Mais seguro, mas requer configuração no backend

### Exemplo de Gerenciamento

```typescript
class TokenManager {
  private static ACCESS_TOKEN_KEY = 'accessToken';
  private static REFRESH_TOKEN_KEY = 'refreshToken';

  static setTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
  }

  static getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  static getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  static clearTokens() {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  static isAuthenticated(): boolean {
    return !!this.getAccessToken();
  }
}
```

---

## ⚠️ Tratamento de Erros

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|-------------|------------------|
| 200 | Sucesso | Processar resposta normalmente |
| 201 | Criado | Processar resposta normalmente |
| 204 | Sem conteúdo | Operação bem-sucedida (logout) |
| 400 | Bad Request | Exibir mensagem de erro ao usuário |
| 401 | Unauthorized | Tentar refresh token ou redirecionar para login |
| 403 | Forbidden | Usuário não tem permissão |
| 404 | Not Found | Recurso não encontrado |
| 409 | Conflict | Recurso já existe (ex: email duplicado) |
| 500 | Internal Server Error | Erro do servidor, tentar novamente |

### Estrutura de Erro Padrão

```json
{
  "timestamp": "2025-12-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Mensagem de erro descritiva",
  "path": "/api/v1/auth/login"
}
```

### Função de Tratamento de Erros

```typescript
async function handleApiError(response: Response) {
  if (!response.ok) {
    let errorMessage = 'Erro desconhecido';
    
    try {
      const error = await response.json();
      errorMessage = error.message || error.error || errorMessage;
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

### Exemplo Completo: Serviço de Autenticação

```typescript
class AuthService {
  private baseURL = 'http://localhost:8081/api/v1';

  async login(email: string, password: string) {
    const response = await fetch(`${this.baseURL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });

    await this.handleApiError(response);
    const data = await response.json();

    if (data.requires2FA) {
      return { requires2FA: true, email, message: data.message };
    }

    TokenManager.setTokens(data.accessToken, data.refreshToken);
    return data;
  }

  async verify2FA(email: string, code: string) {
    const response = await fetch(`${this.baseURL}/auth/verify-2fa`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, code }),
    });

    await this.handleApiError(response);
    const data = await response.json();

    TokenManager.setTokens(data.accessToken, data.refreshToken);
    return data;
  }

  async refreshToken() {
    const refreshToken = TokenManager.getRefreshToken();
    if (!refreshToken) {
      throw new Error('Refresh token não encontrado');
    }

    const response = await fetch(`${this.baseURL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      TokenManager.clearTokens();
      window.location.href = '/login';
      throw new Error('Sessão expirada');
    }

    const data = await response.json();
    TokenManager.setTokens(data.accessToken, data.refreshToken);
    return data.accessToken;
  }

  async logout() {
    const refreshToken = TokenManager.getRefreshToken();
    
    if (refreshToken) {
      try {
        await fetch(`${this.baseURL}/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
      } catch (error) {
        console.error('Erro ao fazer logout:', error);
      }
    }

    TokenManager.clearTokens();
  }

  private async handleApiError(response: Response) {
    if (!response.ok) {
      let errorMessage = 'Erro desconhecido';
      try {
        const error = await response.json();
        errorMessage = error.message || error.error || errorMessage;
      } catch {
        errorMessage = `Erro ${response.status}`;
      }
      throw new Error(errorMessage);
    }
  }
}

export const authService = new AuthService();
```

---

## 🔑 Informações Importantes

### Validade dos Tokens

- **Access Token**: Expira em **15 minutos**
- **Refresh Token**: Expira em **30 dias**

### Multi-Tenancy

- O sistema suporta multi-tenancy
- O `tenantId` é extraído automaticamente do JWT
- SUPER_ADMIN pode usar header `X-Tenant-ID` para operar em nome de um tenant específico

### Segurança

- ✅ Nunca exponha tokens no console ou logs
- ✅ Use HTTPS em produção
- ✅ Implemente refresh automático antes do token expirar
- ✅ Limpe tokens ao fazer logout
- ✅ Valide tokens no frontend antes de fazer requisições

---

## 📞 Suporte

Para dúvidas ou problemas, consulte a documentação completa da API ou entre em contato com a equipe de desenvolvimento.

