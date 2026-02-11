# 🏢 API de Tenants - Documentação para Frontend

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Autenticação e Autorização](#autenticação-e-autorização)
3. [Endpoints](#endpoints)
4. [Modelos de Dados](#modelos-de-dados)
5. [Exemplos de Implementação](#exemplos-de-implementação)
6. [Tratamento de Erros](#tratamento-de-erros)

---

## 🎯 Visão Geral

A API de Tenants permite gerenciar empresas (tenants) no sistema. Cada tenant representa uma empresa isolada com seus próprios dados, usuários e documentos.

**Base URL**: `http://localhost:8081/api/v1`

**Versão da API**: `v1`

---

## 🔐 Autenticação e Autorização

### Requisitos

Todos os endpoints de Tenants requerem:

1. **Autenticação JWT**: Token de acesso válido no header `Authorization`
2. **Role de SUPER_ADMIN**: Apenas usuários com role `SUPER_ADMIN` podem gerenciar tenants

### Headers Obrigatórios

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### Exemplo de Requisição Autenticada

```javascript
const headers = {
  'Authorization': `Bearer ${accessToken}`,
  'Content-Type': 'application/json'
};
```

---

## 📡 Endpoints

### 1. Listar Todos os Tenants

**GET** `/api/v1/tenants`

Retorna uma lista de todos os tenants cadastrados no sistema.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Query Parameters:** Nenhum

#### Response

**Status:** `200 OK`

**Body:**
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

#### Códigos de Status

- `200 OK`: Lista retornada com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
async function listarTenants(accessToken: string): Promise<TenantResponse[]> {
  const response = await fetch('http://localhost:8081/api/v1/tenants', {
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
    if (response.status === 403) {
      throw new Error('Acesso negado. Apenas SUPER_ADMIN pode listar tenants.');
    }
    throw new Error(`Erro ao listar tenants: ${response.statusText}`);
  }

  return await response.json();
}
```

```javascript
// JavaScript puro
async function listarTenants(accessToken) {
  try {
    const response = await fetch('http://localhost:8081/api/v1/tenants', {
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
      if (response.status === 403) {
        throw new Error('Acesso negado. Apenas SUPER_ADMIN pode listar tenants.');
      }
      throw new Error(`Erro ao listar tenants: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Erro ao listar tenants:', error);
    throw error;
  }
}
```

---

### 2. Criar Novo Tenant

**POST** `/api/v1/tenants`

Cria um novo tenant (empresa) no sistema.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Body:**
```json
{
  "nome": "Nova Empresa Ltda",
  "dominio": "novaempresa.com.br"
}
```

**Campos:**
- `nome` (string, obrigatório): Nome da empresa
- `dominio` (string, opcional): Domínio da empresa (ex: "empresa.com.br")

#### Response

**Status:** `201 Created`

**Body:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "nome": "Nova Empresa Ltda",
  "dominio": "novaempresa.com.br",
  "ativo": true,
  "createdAt": "2024-01-17T09:15:00Z"
}
```

#### Códigos de Status

- `201 Created`: Tenant criado com sucesso
- `400 Bad Request`: Dados inválidos (nome vazio, formato incorreto)
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)
- `409 Conflict`: Tenant com este nome já existe
- `500 Internal Server Error`: Erro interno do servidor

#### Validações

- `nome` é obrigatório e não pode estar vazio
- `nome` deve ser único no sistema
- `dominio` é opcional

#### Exemplo de Implementação

```typescript
interface CreateTenantRequest {
  nome: string;
  dominio?: string;
}

interface TenantResponse {
  id: string;
  nome: string;
  dominio: string | null;
  ativo: boolean;
  createdAt: string;
}

async function criarTenant(
  accessToken: string,
  dados: CreateTenantRequest
): Promise<TenantResponse> {
  const response = await fetch('http://localhost:8081/api/v1/tenants', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(dados)
  });

  if (!response.ok) {
    if (response.status === 400) {
      const error = await response.json();
      throw new Error(`Dados inválidos: ${error.message || 'Verifique os campos enviados'}`);
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 403) {
      throw new Error('Acesso negado. Apenas SUPER_ADMIN pode criar tenants.');
    }
    if (response.status === 409) {
      throw new Error('Já existe um tenant com este nome.');
    }
    throw new Error(`Erro ao criar tenant: ${response.statusText}`);
  }

  return await response.json();
}

// Uso
const novoTenant = await criarTenant(accessToken, {
  nome: 'Nova Empresa Ltda',
  dominio: 'novaempresa.com.br'
});
```

```javascript
// JavaScript puro
async function criarTenant(accessToken, dados) {
  try {
    const response = await fetch('http://localhost:8081/api/v1/tenants', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(dados)
    });

    if (!response.ok) {
      if (response.status === 400) {
        const error = await response.json().catch(() => ({}));
        throw new Error(`Dados inválidos: ${error.message || 'Verifique os campos enviados'}`);
      }
      if (response.status === 401) {
        throw new Error('Token inválido ou expirado');
      }
      if (response.status === 403) {
        throw new Error('Acesso negado. Apenas SUPER_ADMIN pode criar tenants.');
      }
      if (response.status === 409) {
        throw new Error('Já existe um tenant com este nome.');
      }
      throw new Error(`Erro ao criar tenant: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Erro ao criar tenant:', error);
    throw error;
  }
}

// Uso
const novoTenant = await criarTenant(accessToken, {
  nome: 'Nova Empresa Ltda',
  dominio: 'novaempresa.com.br'
});
```

---

### 3. Buscar Tenant por ID

**GET** `/api/v1/tenants/{id}`

Retorna os detalhes de um tenant específico.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
```

**Path Parameters:**
- `id` (string, obrigatório): ID único do tenant (UUID)

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Empresa ABC Ltda",
  "dominio": "empresaabc.com.br",
  "ativo": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Códigos de Status

- `200 OK`: Tenant encontrado
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão (não é SUPER_ADMIN)
- `404 Not Found`: Tenant não encontrado
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
async function buscarTenantPorId(
  accessToken: string,
  tenantId: string
): Promise<TenantResponse> {
  const response = await fetch(
    `http://localhost:8081/api/v1/tenants/${tenantId}`,
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
    if (response.status === 403) {
      throw new Error('Acesso negado. Apenas SUPER_ADMIN pode buscar tenants.');
    }
    if (response.status === 404) {
      throw new Error('Tenant não encontrado.');
    }
    throw new Error(`Erro ao buscar tenant: ${response.statusText}`);
  }

  return await response.json();
}
```

```javascript
// JavaScript puro
async function buscarTenantPorId(accessToken, tenantId) {
  try {
    const response = await fetch(
      `http://localhost:8081/api/v1/tenants/${tenantId}`,
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
      if (response.status === 403) {
        throw new Error('Acesso negado. Apenas SUPER_ADMIN pode buscar tenants.');
      }
      if (response.status === 404) {
        throw new Error('Tenant não encontrado.');
      }
      throw new Error(`Erro ao buscar tenant: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Erro ao buscar tenant:', error);
    throw error;
  }
}
```

---

## 📊 Modelos de Dados

### CreateTenantRequest

```typescript
interface CreateTenantRequest {
  nome: string;        // Obrigatório: Nome da empresa
  dominio?: string;    // Opcional: Domínio da empresa
}
```

**Exemplo:**
```json
{
  "nome": "Empresa Exemplo Ltda",
  "dominio": "exemplo.com.br"
}
```

### TenantResponse

```typescript
interface TenantResponse {
  id: string;                    // UUID do tenant
  nome: string;                  // Nome da empresa
  dominio: string | null;        // Domínio (pode ser null)
  ativo: boolean;                // Status do tenant (sempre true ao criar)
  createdAt: string;            // Data de criação (ISO 8601)
}
```

**Exemplo:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nome": "Empresa Exemplo Ltda",
  "dominio": "exemplo.com.br",
  "ativo": true,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

## 💻 Exemplos de Implementação

### React com TypeScript

```typescript
import { useState, useEffect } from 'react';

interface Tenant {
  id: string;
  nome: string;
  dominio: string | null;
  ativo: boolean;
  createdAt: string;
}

interface CreateTenantData {
  nome: string;
  dominio?: string;
}

// Hook para listar tenants
export function useTenants(accessToken: string) {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchTenants() {
      try {
        setLoading(true);
        const response = await fetch('http://localhost:8081/api/v1/tenants', {
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          }
        });

        if (!response.ok) {
          throw new Error('Erro ao carregar tenants');
        }

        const data = await response.json();
        setTenants(data);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Erro desconhecido');
      } finally {
        setLoading(false);
      }
    }

    if (accessToken) {
      fetchTenants();
    }
  }, [accessToken]);

  return { tenants, loading, error };
}

// Função para criar tenant
export async function createTenant(
  accessToken: string,
  data: CreateTenantData
): Promise<Tenant> {
  const response = await fetch('http://localhost:8081/api/v1/tenants', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    if (response.status === 409) {
      throw new Error('Já existe um tenant com este nome');
    }
    throw new Error('Erro ao criar tenant');
  }

  return await response.json();
}

// Componente de exemplo
function TenantsList() {
  const accessToken = 'seu-token-aqui';
  const { tenants, loading, error } = useTenants(accessToken);

  const handleCreate = async () => {
    try {
      const novoTenant = await createTenant(accessToken, {
        nome: 'Nova Empresa',
        dominio: 'novaempresa.com.br'
      });
      console.log('Tenant criado:', novoTenant);
    } catch (err) {
      console.error('Erro:', err);
    }
  };

  if (loading) return <div>Carregando...</div>;
  if (error) return <div>Erro: {error}</div>;

  return (
    <div>
      <button onClick={handleCreate}>Criar Tenant</button>
      <ul>
        {tenants.map(tenant => (
          <li key={tenant.id}>
            {tenant.nome} - {tenant.dominio || 'Sem domínio'}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

### Vue.js com Composition API

```vue
<template>
  <div>
    <button @click="criarTenant">Criar Tenant</button>
    <div v-if="loading">Carregando...</div>
    <div v-else-if="erro">{{ erro }}</div>
    <ul v-else>
      <li v-for="tenant in tenants" :key="tenant.id">
        {{ tenant.nome }} - {{ tenant.dominio || 'Sem domínio' }}
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface Tenant {
  id: string;
  nome: string;
  dominio: string | null;
  ativo: boolean;
  createdAt: string;
}

const tenants = ref<Tenant[]>([]);
const loading = ref(true);
const erro = ref<string | null>(null);
const accessToken = 'seu-token-aqui';

async function carregarTenants() {
  try {
    loading.value = true;
    const response = await fetch('http://localhost:8081/api/v1/tenants', {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    });

    if (!response.ok) {
      throw new Error('Erro ao carregar tenants');
    }

    tenants.value = await response.json();
    erro.value = null;
  } catch (err) {
    erro.value = err instanceof Error ? err.message : 'Erro desconhecido';
  } finally {
    loading.value = false;
  }
}

async function criarTenant() {
  try {
    const response = await fetch('http://localhost:8081/api/v1/tenants', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        nome: 'Nova Empresa',
        dominio: 'novaempresa.com.br'
      })
    });

    if (!response.ok) {
      if (response.status === 409) {
        throw new Error('Já existe um tenant com este nome');
      }
      throw new Error('Erro ao criar tenant');
    }

    const novoTenant = await response.json();
    console.log('Tenant criado:', novoTenant);
    await carregarTenants(); // Recarregar lista
  } catch (err) {
    erro.value = err instanceof Error ? err.message : 'Erro desconhecido';
  }
}

onMounted(() => {
  carregarTenants();
});
</script>
```

### Angular Service

```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Tenant {
  id: string;
  nome: string;
  dominio: string | null;
  ativo: boolean;
  createdAt: string;
}

export interface CreateTenantRequest {
  nome: string;
  dominio?: string;
}

@Injectable({
  providedIn: 'root'
})
export class TenantService {
  private apiUrl = 'http://localhost:8081/api/v1/tenants';

  constructor(private http: HttpClient) {}

  private getHeaders(accessToken: string): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    });
  }

  listarTenants(accessToken: string): Observable<Tenant[]> {
    return this.http.get<Tenant[]>(this.apiUrl, {
      headers: this.getHeaders(accessToken)
    });
  }

  buscarTenantPorId(accessToken: string, id: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.apiUrl}/${id}`, {
      headers: this.getHeaders(accessToken)
    });
  }

  criarTenant(
    accessToken: string,
    dados: CreateTenantRequest
  ): Observable<Tenant> {
    return this.http.post<Tenant>(this.apiUrl, dados, {
      headers: this.getHeaders(accessToken)
    });
  }
}
```

---

## ⚠️ Tratamento de Erros

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|------------|------------------|
| `200` | Sucesso | Processar resposta normalmente |
| `201` | Criado | Tenant criado com sucesso |
| `400` | Bad Request | Validar dados enviados |
| `401` | Unauthorized | Token inválido ou expirado - fazer logout e reautenticar |
| `403` | Forbidden | Usuário não tem permissão - mostrar mensagem de acesso negado |
| `404` | Not Found | Tenant não encontrado - verificar ID |
| `409` | Conflict | Nome de tenant já existe - sugerir outro nome |
| `500` | Internal Server Error | Erro do servidor - tentar novamente ou contatar suporte |

### Exemplo de Tratamento de Erros

```typescript
async function gerenciarTenant(
  accessToken: string,
  operacao: 'listar' | 'criar' | 'buscar',
  dados?: any
) {
  try {
    let response: Response;
    
    switch (operacao) {
      case 'listar':
        response = await fetch('http://localhost:8081/api/v1/tenants', {
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          }
        });
        break;
        
      case 'criar':
        response = await fetch('http://localhost:8081/api/v1/tenants', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(dados)
        });
        break;
        
      case 'buscar':
        response = await fetch(
          `http://localhost:8081/api/v1/tenants/${dados.id}`,
          {
            headers: {
              'Authorization': `Bearer ${accessToken}`,
              'Content-Type': 'application/json'
            }
          }
        );
        break;
    }

    if (!response.ok) {
      switch (response.status) {
        case 400:
          throw new Error('Dados inválidos. Verifique os campos enviados.');
        case 401:
          // Token expirado - redirecionar para login
          window.location.href = '/login';
          throw new Error('Sessão expirada. Faça login novamente.');
        case 403:
          throw new Error('Acesso negado. Você não tem permissão para esta operação.');
        case 404:
          throw new Error('Tenant não encontrado.');
        case 409:
          throw new Error('Já existe um tenant com este nome. Escolha outro nome.');
        case 500:
          throw new Error('Erro interno do servidor. Tente novamente mais tarde.');
        default:
          throw new Error(`Erro desconhecido: ${response.statusText}`);
      }
    }

    return await response.json();
  } catch (error) {
    // Log do erro para monitoramento
    console.error('Erro na operação de tenant:', error);
    
    // Re-lançar para tratamento no componente
    throw error;
  }
}
```

---

## 📝 Notas Importantes

### 1. Permissões

- **Apenas SUPER_ADMIN** pode acessar os endpoints de tenants
- Usuários com outras roles (`TENANT_ADMIN`, `TENANT_USER`) receberão `403 Forbidden`

### 2. Validações

- O campo `nome` é obrigatório e deve ser único
- O campo `dominio` é opcional
- Ao criar um tenant, ele é automaticamente ativado (`ativo: true`)

### 3. IDs

- Os IDs dos tenants são UUIDs (formato: `550e8400-e29b-41d4-a716-446655440000`)
- Use o ID completo ao buscar um tenant específico

### 4. Datas

- As datas são retornadas no formato ISO 8601 (ex: `2024-01-15T10:30:00Z`)
- Use uma biblioteca como `date-fns` ou `moment.js` para formatar no frontend

### 5. CORS

- Certifique-se de que o backend está configurado para aceitar requisições do seu domínio frontend
- Verifique as configurações de CORS no `SecurityConfig.java`

---

## 🔗 Links Úteis

- [Documentação Completa das APIs](./API_COMPLETA_E_ARQUITETURA.md)
- [Documentação de Autenticação](./API_AUTH_FRONTEND.md)
- Swagger UI: `http://localhost:8081/swagger-ui.html`

---

**Última atualização**: Janeiro 2024

