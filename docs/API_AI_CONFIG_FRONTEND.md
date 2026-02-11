# API de Configuração de IA

Esta documentação descreve os endpoints para gerenciar as configurações de Inteligência Artificial do sistema.

## Base URL
`/api/v1/config/ai`

## Autenticação
Todos os endpoints requerem autenticação JWT via header `Authorization: Bearer <token>`.

---

## Endpoints

### GET /api/v1/config/ai
Obtém o status atual da configuração de IA.

**Permissões:** Qualquer usuário autenticado

**Response:**
```json
{
  "enabled": false,
  "model": "gemini-1.5-flash-002",
  "credentialsConfigured": true,
  "projectId": "meu-projeto-gcp",
  "location": "us-central1",
  "updatedAt": "2024-12-29T15:30:00Z",
  "updatedBy": "admin@empresa.com",
  "statusMessage": "IA desabilitada. PDFs escaneados não serão processados."
}
```

**Campos da resposta:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `enabled` | boolean | Se a IA está habilitada para uso |
| `model` | string | Modelo de IA configurado |
| `credentialsConfigured` | boolean | Se as credenciais do Google Cloud estão configuradas |
| `projectId` | string | ID do projeto no Google Cloud |
| `location` | string | Região do Vertex AI |
| `updatedAt` | datetime | Data da última atualização |
| `updatedBy` | string | Usuário que fez a última atualização |
| `statusMessage` | string | Mensagem de status legível |

---

### PUT /api/v1/config/ai
Atualiza a configuração de IA.

**Permissões:** Apenas SUPER_ADMIN

**Request Body:**
```json
{
  "enabled": true,
  "model": "gemini-1.5-flash-002"
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `enabled` | boolean | Sim | Habilita/desabilita o uso de IA |
| `model` | string | Não | Modelo de IA a ser usado |

**Response:**
```json
{
  "enabled": true,
  "model": "gemini-1.5-flash-002",
  "credentialsConfigured": true,
  "projectId": "meu-projeto-gcp",
  "location": "us-central1",
  "updatedAt": "2024-12-29T15:35:00Z",
  "updatedBy": "admin@empresa.com",
  "statusMessage": "✅ IA habilitada e pronta para uso."
}
```

---

## Exemplos de Uso

### React/TypeScript

```typescript
// Tipos
interface AiConfigResponse {
  enabled: boolean;
  model: string;
  credentialsConfigured: boolean;
  projectId: string;
  location: string;
  updatedAt: string;
  updatedBy: string;
  statusMessage: string;
}

interface AiConfigRequest {
  enabled: boolean;
  model?: string;
}

// Serviço
const getAiConfig = async (): Promise<AiConfigResponse> => {
  const response = await fetch('/api/v1/config/ai', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
};

const updateAiConfig = async (config: AiConfigRequest): Promise<AiConfigResponse> => {
  const response = await fetch('/api/v1/config/ai', {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(config)
  });
  return response.json();
};

// Componente
const AiConfigToggle: React.FC = () => {
  const [config, setConfig] = useState<AiConfigResponse | null>(null);

  useEffect(() => {
    getAiConfig().then(setConfig);
  }, []);

  const toggleAi = async () => {
    if (!config) return;
    const updated = await updateAiConfig({ enabled: !config.enabled });
    setConfig(updated);
  };

  if (!config) return <div>Carregando...</div>;

  return (
    <div>
      <h3>Configuração de IA</h3>
      <label>
        <input
          type="checkbox"
          checked={config.enabled}
          onChange={toggleAi}
        />
        Habilitar IA para PDFs escaneados
      </label>
      <p>{config.statusMessage}</p>
    </div>
  );
};
```

### cURL

```bash
# Obter configuração
curl -X GET http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN"

# Habilitar IA
curl -X PUT http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'

# Desabilitar IA
curl -X PUT http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

---

## Status Messages

| Status | Mensagem |
|--------|----------|
| Desabilitada | "IA desabilitada. PDFs escaneados não serão processados." |
| Habilitada sem credenciais | "⚠️ IA habilitada, mas credenciais do Google Cloud não configuradas." |
| Habilitada e pronta | "✅ IA habilitada e pronta para uso." |

---

## Modelo de Dados

A configuração é armazenada na collection `system_config` do MongoDB:

```json
{
  "_id": "ObjectId(...)",
  "key": "ai.enabled",
  "value": "true",
  "description": "Habilita uso de IA para PDFs escaneados",
  "tenantId": null,
  "createdAt": "2024-12-29T15:00:00Z",
  "updatedAt": "2024-12-29T15:35:00Z",
  "updatedBy": "admin@empresa.com"
}
```

---

## Comportamento do Sistema

1. **IA Desabilitada (padrão):**
   - PDFs com texto < 100 caracteres são ignorados
   - Nenhuma chamada à API do Gemini é feita
   - Custo zero

2. **IA Habilitada:**
   - PDFs com texto < 100 caracteres são enviados ao Gemini
   - Extração de texto por IA
   - Custo por chamada (muito baixo)

3. **Fallback:**
   - Se as credenciais do GCP não estiverem configuradas, a IA permanece desabilitada mesmo se `enabled=true`
