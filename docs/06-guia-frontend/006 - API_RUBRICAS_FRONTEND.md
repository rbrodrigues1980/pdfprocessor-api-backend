# 🏷️ API de Rubricas - Documentação para Frontend

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Autenticação e Autorização](#autenticação-e-autorização)
3. [Isolamento Multi-Tenant](#isolamento-multi-tenant)
4. [Endpoints](#endpoints)
5. [Modelos de Dados](#modelos-de-dados)
6. [Exemplos de Implementação](#exemplos-de-implementação)
7. [Tratamento de Erros](#tratamento-de-erros)

---

## 🎯 Visão Geral

A API de Rubricas permite gerenciar a tabela mestra de rubricas (códigos de contracheque) do sistema. As rubricas são usadas para validar e categorizar as entradas extraídas dos PDFs.

**Base URL**: `http://localhost:8081/api/v1`

**Versão da API**: `v1`

### Tipos de Rubricas

- **Rubricas Globais**: Rubricas compartilhadas por todos os tenants (criadas por SUPER_ADMIN)
- **Rubricas de Tenant**: Rubricas específicas de uma empresa (criadas por TENANT_ADMIN)

---

## 🔐 Autenticação e Autorização

### Requisitos

Todos os endpoints de Rubricas requerem:

1. **Autenticação JWT**: Token de acesso válido no header `Authorization`
2. **Roles permitidas**:
   - `SUPER_ADMIN`: Pode ver e gerenciar todas as rubricas (globais + de todos os tenants)
   - `TENANT_ADMIN`: Pode ver e gerenciar rubricas globais + rubricas do seu tenant
   - `TENANT_USER`: Pode apenas visualizar rubricas (globais + do seu tenant)

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

## 🏢 Isolamento Multi-Tenant

### Como Funciona

O sistema aplica isolamento automático baseado no tenant do usuário:

#### SUPER_ADMIN
- **Vê**: Todas as rubricas (globais + de todos os tenants)
- **Pode criar**: Rubricas globais (usando `X-Tenant-ID: GLOBAL`) ou rubricas de qualquer tenant (usando `X-Tenant-ID: {tenantId}`)
- **Pode editar/deletar**: Qualquer rubrica (global ou de tenant)

#### TENANT_ADMIN / TENANT_USER
- **Vê**: Apenas rubricas globais + rubricas do seu próprio tenant
- **Pode criar**: Apenas rubricas do seu próprio tenant (tenantId vem automaticamente do JWT)
- **Pode editar/deletar**: Apenas rubricas do seu próprio tenant (não pode editar/deletar globais)

### Header Especial para SUPER_ADMIN

Se você é `SUPER_ADMIN` e quer criar/visualizar rubricas de um tenant específico:

```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: força um tenant específico
```

**Nota**: Se não enviar `X-Tenant-ID`, o SUPER_ADMIN verá todas as rubricas.

### Exemplo de Comportamento

```
Tenant A tem rubricas: [1001, 1002, 2001]
Tenant B tem rubricas: [1001, 3001, 3002]
Rubricas globais: [3430, 4482]

SUPER_ADMIN vê: [3430, 4482, 1001, 1002, 2001, 3001, 3002] (todas)
TENANT_ADMIN do Tenant A vê: [3430, 4482, 1001, 1002, 2001] (globais + suas)
TENANT_ADMIN do Tenant B vê: [3430, 4482, 1001, 3001, 3002] (globais + suas)
```

---

## 📡 Endpoints

### 1. Listar Todas as Rubricas

**GET** `/api/v1/rubricas`

Retorna uma lista de rubricas. O resultado depende do role do usuário:
- **SUPER_ADMIN**: Todas as rubricas
- **TENANT_ADMIN/USER**: Rubricas globais + do seu tenant

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: apenas para SUPER_ADMIN
```

**Query Parameters:**
- `apenasAtivas` (opcional, padrão: `false`): Se `true`, retorna apenas rubricas ativas

#### Response

**Status:** `200 OK`

**Body:**
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
  },
  {
    "id": "507f1f77bcf86cd799439013",
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "codigo": "2001",
    "descricao": "BONUS ESPECIAL",
    "categoria": "Benefícios",
    "ativo": false
  }
]
```

#### Códigos de Status

- `200 OK`: Lista retornada com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `500 Internal Server Error`: Erro interno do servidor

#### Exemplo de Implementação

```typescript
interface Rubrica {
  id: string;
  tenantId: string;  // "GLOBAL" ou ID do tenant
  codigo: string;
  descricao: string;
  categoria: string | null;
  ativo: boolean;
}

async function listarRubricas(
  accessToken: string,
  apenasAtivas: boolean = false,
  tenantId?: string  // Apenas para SUPER_ADMIN
): Promise<Rubrica[]> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  // SUPER_ADMIN pode especificar um tenant
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const url = `http://localhost:8081/api/v1/rubricas?apenasAtivas=${apenasAtivas}`;
  
  const response = await fetch(url, {
    method: 'GET',
    headers
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    throw new Error(`Erro ao listar rubricas: ${response.statusText}`);
  }

  return await response.json();
}
```

```javascript
// JavaScript puro
async function listarRubricas(accessToken, apenasAtivas = false, tenantId = null) {
  const headers = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  // SUPER_ADMIN pode especificar um tenant
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const url = `http://localhost:8081/api/v1/rubricas?apenasAtivas=${apenasAtivas}`;
  
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('Token inválido ou expirado');
      }
      throw new Error(`Erro ao listar rubricas: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Erro ao listar rubricas:', error);
    throw error;
  }
}
```

---

### 2. Buscar Rubrica por Código

**GET** `/api/v1/rubricas/{codigo}`

Retorna os detalhes de uma rubrica específica.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: apenas para SUPER_ADMIN
```

**Path Parameters:**
- `codigo` (string, obrigatório): Código da rubrica (ex: "3430")

#### Response

**Status:** `200 OK`

**Body:**
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

#### Códigos de Status

- `200 OK`: Rubrica encontrada
- `401 Unauthorized`: Token inválido ou ausente
- `404 Not Found`: Rubrica não encontrada (ou não acessível pelo seu tenant)
- `500 Internal Server Error`: Erro interno do servidor

#### Comportamento de Busca

- **Rubricas Globais**: Sempre encontradas (se existirem)
- **Rubricas de Tenant**: Encontradas apenas se pertencerem ao tenant do usuário
- **SUPER_ADMIN**: Pode buscar qualquer rubrica (global ou de qualquer tenant)

#### Exemplo de Implementação

```typescript
async function buscarRubricaPorCodigo(
  accessToken: string,
  codigo: string,
  tenantId?: string  // Apenas para SUPER_ADMIN
): Promise<Rubrica> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch(
    `http://localhost:8081/api/v1/rubricas/${codigo}`,
    {
      method: 'GET',
      headers
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 404) {
      throw new Error(`Rubrica com código ${codigo} não encontrada ou não acessível.`);
    }
    throw new Error(`Erro ao buscar rubrica: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 3. Criar Nova Rubrica

**POST** `/api/v1/rubricas`

Cria uma nova rubrica no sistema.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
Content-Type: application/json
X-Tenant-ID: GLOBAL  // Opcional: apenas para SUPER_ADMIN criar rubrica global
```

**Body:**
```json
{
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária"
}
```

**Campos:**
- `codigo` (string, obrigatório): Código único da rubrica (ex: "3430", "1001")
- `descricao` (string, obrigatório): Descrição da rubrica (exatamente como aparece no PDF)
- `categoria` (string, opcional): Classificação da rubrica (ex: "Remuneração", "Benefícios", "Extraordinária")

#### Response

**Status:** `201 Created`

**Body:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária",
  "ativo": true
}
```

#### Códigos de Status

- `201 Created`: Rubrica criada com sucesso
- `400 Bad Request`: Dados inválidos (campos obrigatórios ausentes)
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão para criar rubricas
- `409 Conflict`: Já existe uma rubrica com este código (no mesmo escopo: global ou tenant)
- `500 Internal Server Error`: Erro interno do servidor

#### Regras de Criação

1. **SUPER_ADMIN**:
   - Pode criar rubricas globais (usando `X-Tenant-ID: GLOBAL`)
   - Pode criar rubricas de qualquer tenant (usando `X-Tenant-ID: {tenantId}`)
   - Se não enviar `X-Tenant-ID`, cria rubrica global

2. **TENANT_ADMIN**:
   - Pode criar apenas rubricas do seu próprio tenant
   - O `tenantId` é automaticamente obtido do JWT
   - Não pode criar rubricas globais

3. **TENANT_USER**:
   - Não pode criar rubricas (retorna `403 Forbidden`)

4. **Unicidade do Código**:
   - O código deve ser único dentro do mesmo escopo
   - Rubricas globais: código único globalmente
   - Rubricas de tenant: código único dentro do tenant (pode repetir códigos de outros tenants)

#### Exemplo de Implementação

```typescript
interface CreateRubricaRequest {
  codigo: string;
  descricao: string;
  categoria?: string;
}

async function criarRubrica(
  accessToken: string,
  dados: CreateRubricaRequest,
  tenantId?: string  // "GLOBAL" ou ID do tenant (apenas para SUPER_ADMIN)
): Promise<Rubrica> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  // SUPER_ADMIN pode especificar o tenant
  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch('http://localhost:8081/api/v1/rubricas', {
    method: 'POST',
    headers,
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
      throw new Error('Acesso negado. Você não tem permissão para criar rubricas.');
    }
    if (response.status === 409) {
      throw new Error(`Já existe uma rubrica com o código ${dados.codigo} neste escopo.`);
    }
    throw new Error(`Erro ao criar rubrica: ${response.statusText}`);
  }

  return await response.json();
}

// Exemplo de uso
// SUPER_ADMIN criando rubrica global
const rubricaGlobal = await criarRubrica(accessToken, {
  codigo: '3430',
  descricao: 'CONTRIBUIÇÃO EXTRAORDINÁRIA 2014',
  categoria: 'Extraordinária'
}, 'GLOBAL');

// TENANT_ADMIN criando rubrica do seu tenant (tenantId vem do JWT automaticamente)
const rubricaTenant = await criarRubrica(accessToken, {
  codigo: '1001',
  descricao: 'SALÁRIO BASE',
  categoria: 'Remuneração'
});
```

---

### 4. Atualizar Rubrica

**PUT** `/api/v1/rubricas/{codigo}`

Atualiza uma rubrica existente.

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
Content-Type: application/json
X-Tenant-ID: {tenantId}  // Opcional: apenas para SUPER_ADMIN
```

**Path Parameters:**
- `codigo` (string, obrigatório): Código da rubrica a ser atualizada

**Body:**
```json
{
  "descricao": "NOVA DESCRIÇÃO DA RUBRICA",
  "categoria": "Nova Categoria"
}
```

**Campos:**
- `descricao` (string, obrigatório): Nova descrição da rubrica
- `categoria` (string, opcional): Nova categoria

**Nota**: O código da rubrica não pode ser alterado.

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "tenantId": "GLOBAL",
  "codigo": "3430",
  "descricao": "NOVA DESCRIÇÃO DA RUBRICA",
  "categoria": "Nova Categoria",
  "ativo": true
}
```

#### Códigos de Status

- `200 OK`: Rubrica atualizada com sucesso
- `400 Bad Request`: Dados inválidos
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão para editar esta rubrica
- `404 Not Found`: Rubrica não encontrada (ou não acessível pelo seu tenant)
- `500 Internal Server Error`: Erro interno do servidor

#### Regras de Atualização

1. **SUPER_ADMIN**: Pode atualizar qualquer rubrica (global ou de qualquer tenant)
2. **TENANT_ADMIN**: Pode atualizar apenas rubricas do seu próprio tenant (não pode editar globais)
3. **TENANT_USER**: Não pode atualizar rubricas (retorna `403 Forbidden`)

#### Exemplo de Implementação

```typescript
interface UpdateRubricaRequest {
  descricao: string;
  categoria?: string;
}

async function atualizarRubrica(
  accessToken: string,
  codigo: string,
  dados: UpdateRubricaRequest,
  tenantId?: string  // Apenas para SUPER_ADMIN
): Promise<Rubrica> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch(
    `http://localhost:8081/api/v1/rubricas/${codigo}`,
    {
      method: 'PUT',
      headers,
      body: JSON.stringify(dados)
    }
  );

  if (!response.ok) {
    if (response.status === 400) {
      const error = await response.json().catch(() => ({}));
      throw new Error(`Dados inválidos: ${error.message || 'Verifique os campos enviados'}`);
    }
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 403) {
      throw new Error('Acesso negado. Você não tem permissão para editar esta rubrica.');
    }
    if (response.status === 404) {
      throw new Error(`Rubrica com código ${codigo} não encontrada ou não acessível.`);
    }
    throw new Error(`Erro ao atualizar rubrica: ${response.statusText}`);
  }

  return await response.json();
}
```

---

### 5. Desativar Rubrica

**DELETE** `/api/v1/rubricas/{codigo}`

Desativa uma rubrica (soft delete - não remove do banco, apenas marca como inativa).

#### Request

**Headers:**
```http
Authorization: Bearer {accessToken}
X-Tenant-ID: {tenantId}  // Opcional: apenas para SUPER_ADMIN
```

**Path Parameters:**
- `codigo` (string, obrigatório): Código da rubrica a ser desativada

#### Response

**Status:** `200 OK`

**Body:** Vazio

#### Códigos de Status

- `200 OK`: Rubrica desativada com sucesso
- `401 Unauthorized`: Token inválido ou ausente
- `403 Forbidden`: Usuário não tem permissão para desativar esta rubrica
- `404 Not Found`: Rubrica não encontrada (ou não acessível pelo seu tenant)
- `500 Internal Server Error`: Erro interno do servidor

#### Regras de Desativação

1. **SUPER_ADMIN**: Pode desativar qualquer rubrica (global ou de qualquer tenant)
2. **TENANT_ADMIN**: Pode desativar apenas rubricas do seu próprio tenant (não pode desativar globais)
3. **TENANT_USER**: Não pode desativar rubricas (retorna `403 Forbidden`)

**Nota**: Rubricas desativadas não aparecem em buscas com `apenasAtivas=true`, mas ainda existem no banco de dados.

#### Exemplo de Implementação

```typescript
async function desativarRubrica(
  accessToken: string,
  codigo: string,
  tenantId?: string  // Apenas para SUPER_ADMIN
): Promise<void> {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  };

  if (tenantId) {
    headers['X-Tenant-ID'] = tenantId;
  }

  const response = await fetch(
    `http://localhost:8081/api/v1/rubricas/${codigo}`,
    {
      method: 'DELETE',
      headers
    }
  );

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Token inválido ou expirado');
    }
    if (response.status === 403) {
      throw new Error('Acesso negado. Você não tem permissão para desativar esta rubrica.');
    }
    if (response.status === 404) {
      throw new Error(`Rubrica com código ${codigo} não encontrada ou não acessível.`);
    }
    throw new Error(`Erro ao desativar rubrica: ${response.statusText}`);
  }
}
```

---

## 📊 Modelos de Dados

### CreateRubricaRequest

```typescript
interface CreateRubricaRequest {
  codigo: string;        // Obrigatório: Código único da rubrica
  descricao: string;     // Obrigatório: Descrição da rubrica
  categoria?: string;    // Opcional: Classificação da rubrica
}
```

**Exemplo:**
```json
{
  "codigo": "3430",
  "descricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
  "categoria": "Extraordinária"
}
```

### UpdateRubricaRequest

```typescript
interface UpdateRubricaRequest {
  descricao: string;     // Obrigatório: Nova descrição
  categoria?: string;    // Opcional: Nova categoria
}
```

**Exemplo:**
```json
{
  "descricao": "NOVA DESCRIÇÃO DA RUBRICA",
  "categoria": "Nova Categoria"
}
```

### Rubrica (Response)

```typescript
interface Rubrica {
  id: string;                    // ID único do MongoDB
  tenantId: string;              // "GLOBAL" ou ID do tenant
  codigo: string;                // Código da rubrica (ex: "3430")
  descricao: string;             // Descrição da rubrica
  categoria: string | null;     // Categoria (pode ser null)
  ativo: boolean;                // Status da rubrica
}
```

**Exemplo:**
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

---

## 💻 Exemplos de Implementação

### React com TypeScript

```typescript
import { useState, useEffect } from 'react';

interface Rubrica {
  id: string;
  tenantId: string;
  codigo: string;
  descricao: string;
  categoria: string | null;
  ativo: boolean;
}

interface CreateRubricaData {
  codigo: string;
  descricao: string;
  categoria?: string;
}

// Hook para listar rubricas
export function useRubricas(accessToken: string, apenasAtivas: boolean = false) {
  const [rubricas, setRubricas] = useState<Rubrica[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchRubricas() {
      try {
        setLoading(true);
        const response = await fetch(
          `http://localhost:8081/api/v1/rubricas?apenasAtivas=${apenasAtivas}`,
          {
            headers: {
              'Authorization': `Bearer ${accessToken}`,
              'Content-Type': 'application/json'
            }
          }
        );

        if (!response.ok) {
          throw new Error('Erro ao carregar rubricas');
        }

        const data = await response.json();
        setRubricas(data);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Erro desconhecido');
      } finally {
        setLoading(false);
      }
    }

    if (accessToken) {
      fetchRubricas();
    }
  }, [accessToken, apenasAtivas]);

  return { rubricas, loading, error };
}

// Função para criar rubrica
export async function criarRubrica(
  accessToken: string,
  data: CreateRubricaData
): Promise<Rubrica> {
  const response = await fetch('http://localhost:8081/api/v1/rubricas', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    if (response.status === 409) {
      throw new Error('Já existe uma rubrica com este código');
    }
    throw new Error('Erro ao criar rubrica');
  }

  return await response.json();
}

// Componente de exemplo
function RubricasList() {
  const accessToken = 'seu-token-aqui';
  const { rubricas, loading, error } = useRubricas(accessToken, false);

  const handleCreate = async () => {
    try {
      const novaRubrica = await criarRubrica(accessToken, {
        codigo: '3430',
        descricao: 'CONTRIBUIÇÃO EXTRAORDINÁRIA 2014',
        categoria: 'Extraordinária'
      });
      console.log('Rubrica criada:', novaRubrica);
    } catch (err) {
      console.error('Erro:', err);
    }
  };

  if (loading) return <div>Carregando...</div>;
  if (error) return <div>Erro: {error}</div>;

  return (
    <div>
      <button onClick={handleCreate}>Criar Rubrica</button>
      <table>
        <thead>
          <tr>
            <th>Código</th>
            <th>Descrição</th>
            <th>Categoria</th>
            <th>Tipo</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rubricas.map(rubrica => (
            <tr key={rubrica.id}>
              <td>{rubrica.codigo}</td>
              <td>{rubrica.descricao}</td>
              <td>{rubrica.categoria || '-'}</td>
              <td>{rubrica.tenantId === 'GLOBAL' ? 'Global' : 'Tenant'}</td>
              <td>{rubrica.ativo ? 'Ativa' : 'Inativa'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

### Vue.js com Composition API

```vue
<template>
  <div>
    <button @click="criarRubrica">Criar Rubrica</button>
    <div v-if="loading">Carregando...</div>
    <div v-else-if="erro">{{ erro }}</div>
    <table v-else>
      <thead>
        <tr>
          <th>Código</th>
          <th>Descrição</th>
          <th>Categoria</th>
          <th>Tipo</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="rubrica in rubricas" :key="rubrica.id">
          <td>{{ rubrica.codigo }}</td>
          <td>{{ rubrica.descricao }}</td>
          <td>{{ rubrica.categoria || '-' }}</td>
          <td>{{ rubrica.tenantId === 'GLOBAL' ? 'Global' : 'Tenant' }}</td>
          <td>{{ rubrica.ativo ? 'Ativa' : 'Inativa' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface Rubrica {
  id: string;
  tenantId: string;
  codigo: string;
  descricao: string;
  categoria: string | null;
  ativo: boolean;
}

const rubricas = ref<Rubrica[]>([]);
const loading = ref(true);
const erro = ref<string | null>(null);
const accessToken = 'seu-token-aqui';

async function carregarRubricas() {
  try {
    loading.value = true;
    const response = await fetch(
      'http://localhost:8081/api/v1/rubricas?apenasAtivas=false',
      {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json'
        }
      }
    );

    if (!response.ok) {
      throw new Error('Erro ao carregar rubricas');
    }

    rubricas.value = await response.json();
    erro.value = null;
  } catch (err) {
    erro.value = err instanceof Error ? err.message : 'Erro desconhecido';
  } finally {
    loading.value = false;
  }
}

async function criarRubrica() {
  try {
    const response = await fetch('http://localhost:8081/api/v1/rubricas', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        codigo: '3430',
        descricao: 'CONTRIBUIÇÃO EXTRAORDINÁRIA 2014',
        categoria: 'Extraordinária'
      })
    });

    if (!response.ok) {
      if (response.status === 409) {
        throw new Error('Já existe uma rubrica com este código');
      }
      throw new Error('Erro ao criar rubrica');
    }

    const novaRubrica = await response.json();
    console.log('Rubrica criada:', novaRubrica);
    await carregarRubricas(); // Recarregar lista
  } catch (err) {
    erro.value = err instanceof Error ? err.message : 'Erro desconhecido';
  }
}

onMounted(() => {
  carregarRubricas();
});
</script>
```

### Angular Service

```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Rubrica {
  id: string;
  tenantId: string;
  codigo: string;
  descricao: string;
  categoria: string | null;
  ativo: boolean;
}

export interface CreateRubricaRequest {
  codigo: string;
  descricao: string;
  categoria?: string;
}

export interface UpdateRubricaRequest {
  descricao: string;
  categoria?: string;
}

@Injectable({
  providedIn: 'root'
})
export class RubricaService {
  private apiUrl = 'http://localhost:8081/api/v1/rubricas';

  constructor(private http: HttpClient) {}

  private getHeaders(accessToken: string, tenantId?: string): HttpHeaders {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    });

    if (tenantId) {
      return headers.set('X-Tenant-ID', tenantId);
    }

    return headers;
  }

  listarRubricas(
    accessToken: string,
    apenasAtivas: boolean = false,
    tenantId?: string
  ): Observable<Rubrica[]> {
    const params = new HttpParams().set('apenasAtivas', apenasAtivas.toString());
    
    return this.http.get<Rubrica[]>(this.apiUrl, {
      headers: this.getHeaders(accessToken, tenantId),
      params
    });
  }

  buscarRubricaPorCodigo(
    accessToken: string,
    codigo: string,
    tenantId?: string
  ): Observable<Rubrica> {
    return this.http.get<Rubrica>(`${this.apiUrl}/${codigo}`, {
      headers: this.getHeaders(accessToken, tenantId)
    });
  }

  criarRubrica(
    accessToken: string,
    dados: CreateRubricaRequest,
    tenantId?: string
  ): Observable<Rubrica> {
    return this.http.post<Rubrica>(this.apiUrl, dados, {
      headers: this.getHeaders(accessToken, tenantId)
    });
  }

  atualizarRubrica(
    accessToken: string,
    codigo: string,
    dados: UpdateRubricaRequest,
    tenantId?: string
  ): Observable<Rubrica> {
    return this.http.put<Rubrica>(`${this.apiUrl}/${codigo}`, dados, {
      headers: this.getHeaders(accessToken, tenantId)
    });
  }

  desativarRubrica(
    accessToken: string,
    codigo: string,
    tenantId?: string
  ): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${codigo}`, {
      headers: this.getHeaders(accessToken, tenantId)
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
| `201` | Criado | Rubrica criada com sucesso |
| `400` | Bad Request | Validar dados enviados |
| `401` | Unauthorized | Token inválido ou expirado - fazer logout e reautenticar |
| `403` | Forbidden | Usuário não tem permissão - mostrar mensagem de acesso negado |
| `404` | Not Found | Rubrica não encontrada ou não acessível pelo tenant |
| `409` | Conflict | Código de rubrica já existe - sugerir outro código |
| `500` | Internal Server Error | Erro do servidor - tentar novamente ou contatar suporte |

### Exemplo de Tratamento de Erros

```typescript
async function gerenciarRubrica(
  accessToken: string,
  operacao: 'listar' | 'criar' | 'buscar' | 'atualizar' | 'desativar',
  dados?: any
) {
  try {
    let response: Response;
    const headers: HeadersInit = {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    };

    if (dados?.tenantId) {
      headers['X-Tenant-ID'] = dados.tenantId;
    }
    
    switch (operacao) {
      case 'listar':
        response = await fetch(
          `http://localhost:8081/api/v1/rubricas?apenasAtivas=${dados?.apenasAtivas || false}`,
          { headers }
        );
        break;
        
      case 'criar':
        response = await fetch('http://localhost:8081/api/v1/rubricas', {
          method: 'POST',
          headers,
          body: JSON.stringify(dados)
        });
        break;
        
      case 'buscar':
        response = await fetch(
          `http://localhost:8081/api/v1/rubricas/${dados.codigo}`,
          { headers }
        );
        break;
        
      case 'atualizar':
        response = await fetch(
          `http://localhost:8081/api/v1/rubricas/${dados.codigo}`,
          {
            method: 'PUT',
            headers,
            body: JSON.stringify({ descricao: dados.descricao, categoria: dados.categoria })
          }
        );
        break;
        
      case 'desativar':
        response = await fetch(
          `http://localhost:8081/api/v1/rubricas/${dados.codigo}`,
          { method: 'DELETE', headers }
        );
        break;
    }

    if (!response.ok) {
      switch (response.status) {
        case 400:
          throw new Error('Dados inválidos. Verifique os campos enviados.');
        case 401:
          window.location.href = '/login';
          throw new Error('Sessão expirada. Faça login novamente.');
        case 403:
          throw new Error('Acesso negado. Você não tem permissão para esta operação.');
        case 404:
          throw new Error('Rubrica não encontrada ou não acessível pelo seu tenant.');
        case 409:
          throw new Error('Já existe uma rubrica com este código. Escolha outro código.');
        case 500:
          throw new Error('Erro interno do servidor. Tente novamente mais tarde.');
        default:
          throw new Error(`Erro desconhecido: ${response.statusText}`);
      }
    }

    return await response.json();
  } catch (error) {
    console.error('Erro na operação de rubrica:', error);
    throw error;
  }
}
```

---

## 📝 Notas Importantes

### 1. Permissões por Role

| Role | Ver | Criar | Editar | Desativar |
|------|-----|-------|--------|-----------|
| **SUPER_ADMIN** | Todas (globais + todos os tenants) | Globais ou de qualquer tenant | Qualquer rubrica | Qualquer rubrica |
| **TENANT_ADMIN** | Globais + do seu tenant | Apenas do seu tenant | Apenas do seu tenant | Apenas do seu tenant |
| **TENANT_USER** | Globais + do seu tenant | ❌ Não permitido | ❌ Não permitido | ❌ Não permitido |

### 2. Rubricas Globais vs de Tenant

- **Rubricas Globais** (`tenantId: "GLOBAL"`):
  - Visíveis para todos os tenants
  - Apenas SUPER_ADMIN pode criar/editar/deletar
  - Úteis para rubricas padrão do sistema

- **Rubricas de Tenant** (`tenantId: {tenantId}`):
  - Visíveis apenas para o tenant específico
  - TENANT_ADMIN pode criar/editar/deletar as suas
  - Úteis para rubricas customizadas por empresa

### 3. Unicidade de Código

- O código da rubrica deve ser único dentro do mesmo escopo:
  - **Globais**: Código único globalmente
  - **De Tenant**: Código único dentro do tenant (pode repetir códigos de outros tenants)

**Exemplo:**
```
Rubrica Global: código "3430" → única no sistema
Tenant A: código "3430" → pode existir (diferente da global)
Tenant B: código "3430" → pode existir (diferente da global e do Tenant A)
```

### 4. Desativação vs Exclusão

- A operação DELETE **desativa** a rubrica (soft delete)
- Rubricas desativadas não aparecem em buscas com `apenasAtivas=true`
- Rubricas desativadas ainda existem no banco de dados
- Para reativar, use PUT para atualizar `ativo: true` (se implementado)

### 5. Header X-Tenant-ID

- **Apenas para SUPER_ADMIN**: Permite especificar um tenant específico
- **Valores possíveis**:
  - `GLOBAL`: Para criar/visualizar rubricas globais
  - `{tenantId}`: Para criar/visualizar rubricas de um tenant específico
  - Não enviar: SUPER_ADMIN vê todas as rubricas

### 6. CORS

- Certifique-se de que o backend está configurado para aceitar requisições do seu domínio frontend
- Verifique as configurações de CORS no `SecurityConfig.java`

---

## 🔗 Links Úteis

- [Documentação Completa das APIs](./API_COMPLETA_E_ARQUITETURA.md)
- [Documentação de Autenticação](./API_AUTH_FRONTEND.md)
- [Documentação de Tenants](./API_TENANTS_FRONTEND.md)
- Swagger UI: `http://localhost:8081/swagger-ui.html`

---

**Última atualização**: Janeiro 2024

