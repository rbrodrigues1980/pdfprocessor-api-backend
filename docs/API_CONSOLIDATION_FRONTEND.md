# 📊 API de Consolidação - Documentação para Frontend

Esta documentação descreve o endpoint de consolidação de rubricas de uma pessoa, que retorna dados organizados em formato matricial para visualização e geração de relatórios.

## 📋 Índice

- [Configuração Base](#configuração-base)
- [Autenticação e Autorização](#autenticação-e-autorização)
- [Endpoint](#endpoint)
- [Modelos de Dados](#modelos-de-dados)
- [Tratamento de Erros](#tratamento-de-erros)
- [Exemplos de Implementação](#exemplos-de-implementação)
- [Casos de Uso](#casos-de-uso)

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
| `SUPER_ADMIN` | Pode ver consolidação de qualquer pessoa (de todos os tenants) |
| `TENANT_ADMIN` | Pode ver consolidação de pessoas do seu tenant |
| `TENANT_USER` | Pode visualizar consolidação de pessoas do seu tenant |

### Isolamento Multi-Tenant

O sistema aplica isolamento automático baseado no tenant do usuário:
- **SUPER_ADMIN**: Vê todos os dados (de todos os tenants)
- **TENANT_ADMIN / TENANT_USER**: Vê apenas dados do seu próprio tenant

O `tenantId` é extraído automaticamente do JWT token, não é necessário enviá-lo nas requisições.

---

## 📡 Endpoint

### GET /api/v1/persons/{cpf}/consolidated

Retorna a consolidação de todas as rubricas de uma pessoa em formato matricial, organizada por rubrica e mês/ano. Este endpoint é especialmente útil para gerar relatórios e visualizações consolidadas.

**URL**: `/api/v1/persons/{cpf}/consolidated`  
**Método**: `GET`  
**Autenticação**: Requerida

#### Path Parameters

| Parâmetro | Tipo | Obrigatório | Descrição |
|-----------|------|-------------|-----------|
| `cpf` | string | Sim | CPF da pessoa (sem formatação, apenas números) |

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

#### Validações

- **Ano**: Se fornecido, deve ser um número entre 2000 e 2100
- **Origem**: Se fornecida, deve ser exatamente `CAIXA` ou `FUNCEF` (case-sensitive)
- **CPF**: Deve ser válido e a pessoa deve existir no sistema

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

**Corpo da resposta**: Pode conter um objeto vazio ou o objeto de resposta com arrays vazios.

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

```json
{
  "status": 400,
  "error": "Origem inválida: INVALIDO"
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

#### Response Error (500 Internal Server Error)

Retornado quando ocorre um erro interno do servidor durante o processamento.

```json
{
  "status": 500,
  "error": "Erro interno ao processar consolidação"
}
```

---

## 📊 Modelos de Dados

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
```

### ConsolidationRow

```typescript
interface ConsolidationRow {
  codigo: string;              // "4482"
  descricao: string;           // "SALÁRIO BASE"
  valores: {
    [referencia: string]: number;  // "2017-01" -> 1500.00
  };
  total: number;               // Total da rubrica
}
```

### ErrorResponse

```typescript
interface ErrorResponse {
  status: number;
  error: string;
}
```

---

## ⚠️ Tratamento de Erros

### Códigos de Status HTTP

| Código | Significado | Ação Recomendada |
|--------|-------------|------------------|
| 200 | Sucesso | Processar resposta normalmente |
| 204 | No Content | Pessoa existe mas não há dados consolidados - exibir mensagem informativa |
| 400 | Bad Request | Exibir mensagem de erro ao usuário (ano ou origem inválidos) |
| 401 | Unauthorized | Token inválido - fazer refresh ou redirecionar para login |
| 403 | Forbidden | Usuário não tem permissão - exibir mensagem |
| 404 | Not Found | Pessoa não encontrada - exibir mensagem |
| 500 | Internal Server Error | Erro do servidor - tentar novamente ou exibir mensagem de erro |

### Função de Tratamento de Erros

```typescript
async function handleConsolidationError(response: Response) {
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

### Exemplo Completo: Serviço de Consolidação

```typescript
class ConsolidationService {
  private baseURL = 'http://localhost:8081/api/v1';

  private async getAuthHeaders(): Promise<HeadersInit> {
    const token = localStorage.getItem('accessToken');
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    };
  }

  async getConsolidated(
    cpf: string, 
    ano?: string, 
    origem?: 'CAIXA' | 'FUNCEF'
  ): Promise<ConsolidatedResponse | null> {
    const queryParams = new URLSearchParams();
    if (ano) queryParams.append('ano', ano);
    if (origem) queryParams.append('origem', origem);
    
    const url = `${this.baseURL}/persons/${cpf}/consolidated${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
    
    const response = await fetch(url, {
      method: 'GET',
      headers: await this.getAuthHeaders(),
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
}

export const consolidationService = new ConsolidationService();
```

### Exemplo: Componente React para Visualização de Consolidação

```typescript
import React, { useState, useEffect } from 'react';
import { consolidationService } from './services/ConsolidationService';

interface ConsolidationViewProps {
  cpf: string;
}

function ConsolidationView({ cpf }: ConsolidationViewProps) {
  const [consolidated, setConsolidated] = useState<ConsolidatedResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    ano: '',
    origem: '' as 'CAIXA' | 'FUNCEF' | ''
  });

  useEffect(() => {
    loadConsolidated();
  }, [cpf, filters]);

  async function loadConsolidated() {
    try {
      setLoading(true);
      setError(null);
      const data = await consolidationService.getConsolidated(
        cpf,
        filters.ano || undefined,
        filters.origem || undefined
      );
      setConsolidated(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar consolidação');
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Carregando consolidação...</div>;
  if (error) return <div>Erro: {error}</div>;
  if (!consolidated) return <div>Nenhum dado consolidado disponível</div>;

  // Extrair todas as referências únicas (meses/anos)
  const referencias = new Set<string>();
  consolidated.rubricas.forEach(rubrica => {
    Object.keys(rubrica.valores).forEach(ref => referencias.add(ref));
  });
  const referenciasArray = Array.from(referencias).sort();

  return (
    <div>
      <h2>Consolidação - {consolidated.nome}</h2>
      <p>CPF: {consolidated.cpf}</p>
      
      {/* Filtros */}
      <div style={{ marginBottom: '20px' }}>
        <input
          type="text"
          placeholder="Ano (ex: 2017)"
          value={filters.ano}
          onChange={(e) => setFilters({ ...filters, ano: e.target.value })}
        />
        <select
          value={filters.origem}
          onChange={(e) => setFilters({ ...filters, origem: e.target.value as 'CAIXA' | 'FUNCEF' | '' })}
        >
          <option value="">Todas as origens</option>
          <option value="CAIXA">CAIXA</option>
          <option value="FUNCEF">FUNCEF</option>
        </select>
      </div>

      {/* Tabela de consolidação */}
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th>Código</th>
            <th>Descrição</th>
            {referenciasArray.map(ref => (
              <th key={ref}>{ref}</th>
            ))}
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {consolidated.rubricas.map(rubrica => (
            <tr key={rubrica.codigo}>
              <td>{rubrica.codigo}</td>
              <td>{rubrica.descricao}</td>
              {referenciasArray.map(ref => {
                const valor = rubrica.valores[ref];
                return (
                  <td key={ref}>
                    {valor ? valor.toLocaleString('pt-BR', { 
                      style: 'currency', 
                      currency: 'BRL' 
                    }) : '-'}
                  </td>
                );
              })}
              <td>
                <strong>
                  {rubrica.total.toLocaleString('pt-BR', { 
                    style: 'currency', 
                    currency: 'BRL' 
                  })}
                </strong>
              </td>
            </tr>
          ))}
          {/* Linha de totais mensais */}
          <tr style={{ fontWeight: 'bold', backgroundColor: '#f0f0f0' }}>
            <td colSpan={2}>Total Mensal</td>
            {referenciasArray.map(ref => {
              const total = consolidated.totaisMensais[ref] || 0;
              return (
                <td key={ref}>
                  {total.toLocaleString('pt-BR', { 
                    style: 'currency', 
                    currency: 'BRL' 
                  })}
                </td>
              );
            })}
            <td>
              {consolidated.totalGeral.toLocaleString('pt-BR', { 
                style: 'currency', 
                currency: 'BRL' 
              })}
            </td>
          </tr>
        </tbody>
      </table>

      {/* Resumo */}
      <div style={{ marginTop: '20px' }}>
        <p><strong>Anos encontrados:</strong> {consolidated.anos.join(', ')}</p>
        <p><strong>Total Geral:</strong> {consolidated.totalGeral.toLocaleString('pt-BR', { 
          style: 'currency', 
          currency: 'BRL' 
        })}</p>
      </div>
    </div>
  );
}

export default ConsolidationView;
```

### Exemplo: Hook React para Consolidação

```typescript
import { useState, useEffect } from 'react';
import { consolidationService } from './services/ConsolidationService';

interface UseConsolidationOptions {
  cpf: string;
  ano?: string;
  origem?: 'CAIXA' | 'FUNCEF';
  autoLoad?: boolean;
}

export function useConsolidation({ 
  cpf, 
  ano, 
  origem, 
  autoLoad = true 
}: UseConsolidationOptions) {
  const [consolidated, setConsolidated] = useState<ConsolidatedResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await consolidationService.getConsolidated(cpf, ano, origem);
      setConsolidated(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar consolidação');
      setConsolidated(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (autoLoad && cpf) {
      load();
    }
  }, [cpf, ano, origem, autoLoad]);

  return {
    consolidated,
    loading,
    error,
    reload: load
  };
}
```

---

## 🎯 Casos de Uso

### 1. Visualização de Consolidação Completa

```typescript
// Buscar consolidação completa (todos os anos e origens)
const consolidated = await consolidationService.getConsolidated('12345678900');

// Exibir em tabela com todas as rubricas e meses
```

### 2. Filtro por Ano

```typescript
// Buscar consolidação apenas de 2017
const consolidated2017 = await consolidationService.getConsolidated('12345678900', '2017');

// Útil para relatórios anuais
```

### 3. Filtro por Origem

```typescript
// Buscar consolidação apenas de CAIXA
const consolidatedCAIXA = await consolidationService.getConsolidated('12345678900', undefined, 'CAIXA');

// Útil para análises por origem
```

### 4. Filtro Combinado

```typescript
// Buscar consolidação de CAIXA em 2017
const consolidated = await consolidationService.getConsolidated('12345678900', '2017', 'CAIXA');

// Útil para análises específicas
```

### 5. Integração com Exportação Excel

```typescript
// 1. Buscar consolidação
const consolidated = await consolidationService.getConsolidated('12345678900', '2017');

// 2. Gerar Excel usando o endpoint de exportação
const excelResponse = await fetch(
  `http://localhost:8081/api/v1/persons/12345678900/excel?ano=2017`,
  {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  }
);

// 3. Fazer download
const blob = await excelResponse.blob();
const url = window.URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = 'consolidado.xlsx';
a.click();
```

---

## 🔍 Diferença entre `/rubricas` e `/consolidated`

| Aspecto | `/rubricas` | `/consolidated` |
|---------|-------------|-----------------|
| **Formato** | Matriz aninhada (objeto de objetos) | Lista de rubricas com valores em objeto |
| **Uso** | Visualização em tabela dinâmica | Geração de Excel/relatórios |
| **Estrutura** | `rubricaCodigo -> referencia -> cell` | `rubricas[]` com `valores: { referencia: valor }` |
| **Totais** | `rubricasTotais` e `totalGeral` | `totaisMensais` e `totalGeral` |
| **Filtros** | Não suporta filtros | Suporta `ano` e `origem` |
| **Ordenação** | Por código de rubrica | Por código de rubrica |
| **Quantidade** | Inclui quantidade de entries | Não inclui quantidade, apenas valores |

---

## 📌 Informações Importantes

### Formato de Referência

- A referência (mês/ano) sempre vem no formato `"YYYY-MM"` (ex: `"2017-01"`)
- Use este formato para ordenação e agrupamento
- Os meses são sempre `["01", "02", ..., "12"]` na resposta

### Valores Monetários

- Todos os valores são números decimais (Double)
- Formate para exibição usando `toLocaleString` ou bibliotecas de formatação
- Exemplo: `valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })`

### Rubricas Ativas

- Apenas entries de rubricas ativas são incluídas na consolidação
- Rubricas inativas são automaticamente filtradas

### Ordenação

- As rubricas são ordenadas por código (crescente)
- Os anos são ordenados (crescente)
- As referências (meses/anos) devem ser ordenadas pelo frontend se necessário

---

## 🔍 Troubleshooting

### Problemas Comuns

#### 404 Not Found ao buscar consolidação

**Causa**: 
- Pessoa não existe
- Pessoa existe mas não pertence ao tenant do usuário autenticado

**Solução**: 
- Verifique se o CPF está correto
- Verifique se a pessoa pertence ao seu tenant

#### 204 No Content

**Causa**: 
- Pessoa existe mas não possui entries ainda
- Filtros aplicados não retornaram nenhuma entry
- Nenhuma rubrica ativa foi encontrada

**Solução**: 
- Verifique se a pessoa tem documentos processados
- Verifique se os documentos têm entries
- Tente remover os filtros para ver todos os dados

#### 400 Bad Request - Ano inválido

**Causa**: 
- Ano fora do range válido (2000-2100)
- Formato de ano inválido

**Solução**: 
- Use apenas números de 4 dígitos entre 2000 e 2100
- Exemplo: "2017" (não "17" ou "20170")

#### 400 Bad Request - Origem inválida

**Causa**: 
- Origem diferente de `CAIXA` ou `FUNCEF`
- Case-sensitive (deve ser exatamente `CAIXA` ou `FUNCEF`)

**Solução**: 
- Use exatamente `CAIXA` ou `FUNCEF` (maiúsculas)
- Não use variações como "caixa", "Caixa", "MIX", etc.

### Dicas de Implementação

1. **Cache de dados**: Considere cachear dados de consolidação para melhor performance
2. **Loading states**: Sempre mostre estados de carregamento durante requisições
3. **Error boundaries**: Implemente tratamento de erros adequado
4. **Validação client-side**: Valide CPF, ano e origem antes de enviar requisições
5. **Formatação**: Formate valores monetários e datas para melhor UX
6. **Filtros**: Implemente filtros no frontend para melhorar a experiência do usuário
7. **Tabelas responsivas**: Use tabelas responsivas ou scroll horizontal para muitos meses

---

## 📞 Suporte

Para dúvidas ou problemas, consulte a documentação completa da API ou entre em contato com a equipe de desenvolvimento.

---

## 📚 Referências Relacionadas

- [API de Pessoas](./API_PERSONS_FRONTEND.md) - Endpoints relacionados a pessoas
- [API de Exportação Excel](./API_6_EXCEL_EXPORT.md) - Geração de arquivos Excel
- [API de Autenticação](./API_AUTH_FRONTEND.md) - Autenticação e autorização

