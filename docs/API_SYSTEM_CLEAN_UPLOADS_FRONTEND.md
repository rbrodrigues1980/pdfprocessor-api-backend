# API: Limpar Todos os Uploads do Sistema

## Endpoint

```
DELETE /api/v1/system/clean-uploads
```

## Descrição

Esta API limpa **TODAS** as collections do banco de dados MongoDB, **EXCETO** a collection `rubricas` que é preservada.

### Collections que serão limpas:
- ✅ `payroll_documents` - Documentos de folha de pagamento
- ✅ `payroll_entries` - Entradas de rubricas extraídas
- ✅ `persons` - Pessoas cadastradas
- ✅ `tenants` - Tenants (se existir)
- ✅ `users` - Usuários (se existir)
- ✅ `fs.files` e `fs.chunks` - Arquivos do GridFS (PDFs armazenados)

### Collections que serão preservadas:
- 🔒 `rubricas` - Tabela mestra de rubricas (NÃO será limpa)

## Autenticação

Requer autenticação via Bearer Token.

**Permissões necessárias:**
- `SUPER_ADMIN` (recomendado)
- `TENANT_ADMIN` (pode funcionar dependendo da configuração)

## Request

### Headers

```http
DELETE /api/v1/system/clean-uploads HTTP/1.1
Host: localhost:8081
Authorization: Bearer {seu_token_jwt}
Content-Type: application/json
```

### Body

Não requer body. A requisição é apenas um DELETE sem parâmetros.

## Response

### Sucesso (200 OK)

```json
{
  "status": "success",
  "message": "Limpeza concluída. 5 collections foram limpas. Collection 'rubricas' foi preservada.",
  "collections_preserved": ["rubricas"],
  "collections_cleaned": 5,
  "total_documents_deleted": 1234,
  "payroll_documents_deleted": 50,
  "payroll_entries_deleted": 1000,
  "persons_deleted": 10,
  "tenants_deleted": 0,
  "users_deleted": 0,
  "gridfs_files_deleted": 174
}
```

### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `status` | string | Status da operação: `"success"` ou `"error"` |
| `message` | string | Mensagem descritiva do resultado |
| `collections_preserved` | array | Lista de collections que foram preservadas (sempre inclui `"rubricas"`) |
| `collections_cleaned` | number | Número total de collections que foram limpas |
| `total_documents_deleted` | number | Total de documentos deletados em todas as collections |
| `{collection}_deleted` | number | Número de documentos deletados em cada collection específica |
| `gridfs_files_deleted` | number | Número de arquivos deletados do GridFS |

### Erro (500 Internal Server Error)

```json
{
  "status": "error",
  "message": "Erro ao limpar dados: {mensagem_do_erro}"
}
```

## Exemplo de Uso

### TypeScript/JavaScript

```typescript
interface CleanUploadsResponse {
  status: 'success' | 'error';
  message: string;
  collections_preserved: string[];
  collections_cleaned: number;
  total_documents_deleted: number;
  payroll_documents_deleted?: number;
  payroll_entries_deleted?: number;
  persons_deleted?: number;
  tenants_deleted?: number;
  users_deleted?: number;
  gridfs_files_deleted?: number;
}

async function cleanAllUploads(token: string): Promise<CleanUploadsResponse> {
  const response = await fetch('http://localhost:8081/api/v1/system/clean-uploads', {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return await response.json();
}

// Uso
try {
  const result = await cleanAllUploads('seu_token_jwt');
  console.log('Limpeza concluída:', result);
  console.log(`Total de documentos deletados: ${result.total_documents_deleted}`);
  console.log(`Collections limpas: ${result.collections_cleaned}`);
  console.log(`Collections preservadas: ${result.collections_preserved.join(', ')}`);
} catch (error) {
  console.error('Erro ao limpar uploads:', error);
}
```

### Axios

```typescript
import axios from 'axios';

async function cleanAllUploads(token: string) {
  try {
    const response = await axios.delete<CleanUploadsResponse>(
      'http://localhost:8081/api/v1/system/clean-uploads',
      {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      }
    );

    console.log('✅ Limpeza concluída:', response.data);
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      console.error('❌ Erro ao limpar uploads:', error.response?.data);
      throw error;
    }
    throw error;
  }
}
```

### React Hook Example

```typescript
import { useState } from 'react';

function useCleanUploads() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CleanUploadsResponse | null>(null);

  const cleanUploads = async (token: string) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await fetch('http://localhost:8081/api/v1/system/clean-uploads', {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error(`Erro HTTP: ${response.status}`);
      }

      const data = await response.json();
      setResult(data);
      return data;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Erro desconhecido';
      setError(errorMessage);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return { cleanUploads, loading, error, result };
}

// Uso no componente
function CleanUploadsButton() {
  const { cleanUploads, loading, error, result } = useCleanUploads();
  const token = 'seu_token_jwt'; // Obter do contexto de autenticação

  const handleClean = async () => {
    if (!confirm('⚠️ ATENÇÃO: Isso irá deletar TODOS os dados, exceto rubricas. Deseja continuar?')) {
      return;
    }

    try {
      await cleanUploads(token);
      alert('✅ Limpeza concluída com sucesso!');
    } catch (err) {
      alert('❌ Erro ao limpar dados');
    }
  };

  return (
    <div>
      <button onClick={handleClean} disabled={loading}>
        {loading ? 'Limpando...' : 'Limpar Todos os Uploads'}
      </button>
      {error && <p style={{ color: 'red' }}>Erro: {error}</p>}
      {result && (
        <div>
          <p>✅ {result.message}</p>
          <p>Collections limpas: {result.collections_cleaned}</p>
          <p>Total de documentos deletados: {result.total_documents_deleted}</p>
        </div>
      )}
    </div>
  );
}
```

## ⚠️ Avisos Importantes

1. **Operação Irreversível**: Esta operação deleta permanentemente todos os dados, exceto rubricas. Não há como desfazer.

2. **Confirmação Recomendada**: Sempre solicite confirmação do usuário antes de executar esta operação.

3. **Backup**: Recomenda-se fazer backup do banco de dados antes de executar esta limpeza.

4. **Permissões**: Apenas usuários com permissão `SUPER_ADMIN` devem ter acesso a esta funcionalidade.

5. **Rubricas Preservadas**: A collection `rubricas` é sempre preservada, pois contém a tabela mestra de rubricas do sistema.

## CURL Example

```bash
curl -X DELETE \
  'http://localhost:8081/api/v1/system/clean-uploads' \
  -H 'Authorization: Bearer seu_token_jwt' \
  -H 'Content-Type: application/json'
```

## Notas de Implementação

- A API lista dinamicamente todas as collections do banco de dados
- Filtra automaticamente a collection `rubricas` para preservação
- Limpa todas as outras collections encontradas
- Retorna estatísticas detalhadas de quantos documentos foram deletados em cada collection
- GridFS é limpo separadamente através do template específico

