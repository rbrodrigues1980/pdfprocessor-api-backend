# API de Configuração de IA — Documentação para Frontend

> **Atualizado em**: 12/02/2026 (Fases 1, 2 e 3 — Gemini 2.5 Flash/Pro + Validação + Cross-Validation)

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
  "enabled": true,
  "model": "gemini-2.5-flash",
  "fallbackModel": "gemini-2.5-pro",
  "credentialsConfigured": true,
  "projectId": "rrr-software-solutions",
  "location": "us-central1",
  "updatedAt": "2026-02-13T01:54:12.670201Z",
  "updatedBy": "system",
  "statusMessage": "IA habilitada e pronta. Modelo principal: gemini-2.5-flash, Fallback: gemini-2.5-pro."
}
```

**Campos da resposta:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `enabled` | boolean | Se a IA está habilitada para uso |
| `model` | string | Modelo principal de IA (ex: `gemini-2.5-flash`) |
| `fallbackModel` | string | Modelo de reserva para casos complexos (ex: `gemini-2.5-pro`) |
| `credentialsConfigured` | boolean | Se as credenciais do Google Cloud estão configuradas |
| `projectId` | string | ID do projeto no Google Cloud |
| `location` | string | Região do Vertex AI |
| `updatedAt` | datetime (ISO 8601) | Data da última atualização |
| `updatedBy` | string | Usuário que fez a última atualização |
| `statusMessage` | string | Mensagem de status legível para exibir na UI |

---

### PUT /api/v1/config/ai
Atualiza a configuração de IA.

**Permissões:** Apenas `SUPER_ADMIN`

**Request Body:**
```json
{
  "enabled": true,
  "model": "gemini-2.5-flash",
  "fallbackModel": "gemini-2.5-pro"
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `enabled` | boolean | Sim | Habilita/desabilita o uso de IA |
| `model` | string | Não | Modelo principal de IA (default: `gemini-2.5-flash`) |
| `fallbackModel` | string | Não | Modelo de reserva (default: `gemini-2.5-pro`) |

**Response:** Mesma estrutura do GET.

**Erros possíveis:**

| HTTP Status | Causa |
|-------------|-------|
| `401 Unauthorized` | Token inválido ou expirado |
| `403 Forbidden` | Usuário não é `SUPER_ADMIN` |
| `400 Bad Request` | Body inválido ou ausente |

---

## Modelos Disponíveis

| Modelo | Tipo | Uso |
|--------|------|-----|
| `gemini-2.5-flash` | Principal | Rápido e barato — usado para extração JSON estruturada de PDFs escaneados |
| `gemini-2.5-pro` | Fallback | Mais preciso — acionado automaticamente quando campos críticos divergem (~1-3% dos docs) |

> **Todas as 4 fases implementadas.** O modelo principal (Flash) extrai dados em JSON estruturado. Uma validação por regras atribui um score de confiança. Se score < 0.85, cross-validation com prompt alternativo. Se campos críticos (CPF, salários) divergem, escalação automática para Gemini Pro.

---

## Status Messages (para exibir na UI)

| Cenário | `statusMessage` |
|---------|-----------------|
| IA desabilitada | `"IA desabilitada. PDFs escaneados não serão processados pela IA."` |
| IA habilitada, sem credenciais | `"IA habilitada, mas credenciais do Google Cloud não estão configuradas. Configure GOOGLE_CLOUD_PROJECT."` |
| IA habilitada e pronta (com fallback) | `"IA habilitada e pronta. Modelo principal: gemini-2.5-flash, Fallback: gemini-2.5-pro."` |
| IA habilitada e pronta (sem fallback) | `"IA habilitada e pronta. Modelo: gemini-2.5-flash."` |

---

## Exemplos de Uso — React/TypeScript

### Tipos

```typescript
interface AiConfigResponse {
  enabled: boolean;
  model: string;
  fallbackModel: string | null;
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
  fallbackModel?: string;
}
```

### Serviço (API calls)

```typescript
const API_BASE = '/api/v1';

const getAiConfig = async (token: string): Promise<AiConfigResponse> => {
  const response = await fetch(`${API_BASE}/config/ai`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!response.ok) throw new Error(`Erro ${response.status}`);
  return response.json();
};

const updateAiConfig = async (
  token: string,
  config: AiConfigRequest
): Promise<AiConfigResponse> => {
  const response = await fetch(`${API_BASE}/config/ai`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(config)
  });
  if (!response.ok) {
    if (response.status === 403) throw new Error('Sem permissão. Requer SUPER_ADMIN.');
    throw new Error(`Erro ${response.status}`);
  }
  return response.json();
};
```

### Componente de configuração

```tsx
const AiConfigPanel: React.FC = () => {
  const [config, setConfig] = useState<AiConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getAiConfig(token)
      .then(setConfig)
      .finally(() => setLoading(false));
  }, []);

  const toggleAi = async () => {
    if (!config) return;
    setSaving(true);
    try {
      const updated = await updateAiConfig(token, {
        enabled: !config.enabled,
        model: config.model,
        fallbackModel: config.fallbackModel ?? undefined
      });
      setConfig(updated);
    } catch (err) {
      console.error('Erro ao atualizar:', err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <Spinner />;
  if (!config) return <p>Erro ao carregar configuração.</p>;

  return (
    <div className="ai-config-panel">
      <h3>Configuração de IA</h3>

      {/* Toggle */}
      <label>
        <input
          type="checkbox"
          checked={config.enabled}
          onChange={toggleAi}
          disabled={saving}
        />
        Habilitar IA para PDFs escaneados
      </label>

      {/* Status */}
      <p className={config.enabled ? 'status-ok' : 'status-off'}>
        {config.statusMessage}
      </p>

      {/* Detalhes (somente leitura) */}
      {config.enabled && (
        <div className="ai-details">
          <p><strong>Modelo principal:</strong> {config.model}</p>
          {config.fallbackModel && (
            <p><strong>Modelo fallback:</strong> {config.fallbackModel}</p>
          )}
          <p><strong>Credenciais:</strong> {config.credentialsConfigured ? '✅ Configuradas' : '❌ Não configuradas'}</p>
          <p><strong>Projeto GCP:</strong> {config.projectId || 'Não definido'}</p>
          <p><strong>Região:</strong> {config.location}</p>
          <p><strong>Última atualização:</strong> {new Date(config.updatedAt).toLocaleString('pt-BR')}</p>
        </div>
      )}
    </div>
  );
};
```

---

## cURL — Exemplos

```bash
# Obter configuração
curl -X GET http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN"

# Habilitar IA com modelo principal e fallback
curl -X PUT http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true, "model": "gemini-2.5-flash", "fallbackModel": "gemini-2.5-pro"}'

# Desabilitar IA
curl -X PUT http://localhost:8081/api/v1/config/ai \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

---

## Modelo de Dados (MongoDB)

A configuração é armazenada na collection `system_config`:

```json
// Chave: ai.enabled
{"key": "ai.enabled", "value": "true"}

// Chave: ai.model
{"key": "ai.model", "value": "gemini-2.5-flash"}

// Chave: ai.fallback-model
{"key": "ai.fallback-model", "value": "gemini-2.5-pro"}
```

---

## Comportamento do Sistema

1. **IA Desabilitada (padrão):**
   - PDFs com texto < 100 caracteres por página **não são processados** pela IA
   - Nenhuma chamada à API do Gemini é feita
   - Custo: **$0,00**

2. **IA Habilitada — Fluxo de Processamento (Fases 1+2+3+4):**
   - PDFs digitais (com texto extraível) → **iText/PDFBox** (parser regex, sem IA, custo $0)
   - PDFs escaneados (texto < 100 chars) → **Gemini 2.5 Flash** com o seguinte fluxo:
     1. **Extração JSON estruturada** — Gemini retorna JSON com rubricas (~$0.003/página)
     2. **Validação por regras** — 6 regras de negócio verificam consistência (custo $0):
        - Soma proventos = bruto, soma descontos = total, bruto - descontos = líquido
        - CPF válido, competência válida, valores positivos
     3. **Score de confiança** (0.0 a 1.0) é atribuído ao documento:
        - `>= 0.85` → **ACCEPT** — dados confiáveis, usar direto
        - `0.60 a 0.84` → **REVIEW** — revisar manualmente
        - `< 0.60` → **REJECT** — dados não confiáveis
     4. **Cross-Validation** (se score < 0.85) — 2ª extração com prompt alternativo (~$0.003 extra):
        - Compara campo a campo (nome, CPF, salários, rubricas)
        - Campos que coincidem → alta confiança
        - Campos que divergem → flag para revisão manual
     5. **Escalação para Gemini Pro** (se campos críticos divergem) — ~$0.011/página:
        - Acionado automaticamente (~1-3% dos documentos)
        - Usa o modelo Pro, mais preciso, para uma extração definitiva
        - Se o Pro também falhar, mantém o resultado da cross-validation

3. **Novos campos no documento processado (PayrollDocument):**
   - `confidenceScore` (number | null) — Score de confiança da extração (0.0 a 1.0)
   - `validationRecommendation` (string | null) — `"ACCEPT"`, `"REVIEW"` ou `"REJECT"`
   - Estes campos são preenchidos apenas para PDFs escaneados processados com IA

4. **Credenciais não configuradas:**
   - Se `credentialsConfigured: false`, a IA **não funciona** mesmo com `enabled: true`
   - O `statusMessage` informa o problema
   - O frontend deve exibir um aviso ao SUPER_ADMIN

5. **Permissões:**
   - `GET /config/ai` → qualquer usuário autenticado pode consultar
   - `PUT /config/ai` → somente `SUPER_ADMIN` pode alterar
   - O frontend deve **esconder/desabilitar** o toggle para usuários que não são SUPER_ADMIN

---

## Sugestão de UI

### Tela de Configurações (SUPER_ADMIN)

- **Card/Seção "Inteligência Artificial"**
  - Toggle (switch) para habilitar/desabilitar
  - Badge de status (verde/vermelho) baseado no `statusMessage`
  - Informações de modelo, fallback, credenciais (somente leitura)
  - Se credenciais não configuradas, exibir alerta amarelo

### Tela de Documentos — Score de Confiança

Para documentos processados com IA, o frontend pode exibir o score de confiança:

```tsx
// Componente para exibir score de confiança
const ConfidenceBadge: React.FC<{ score: number | null; recommendation: string | null }> = ({
  score, recommendation
}) => {
  if (score === null || score === undefined) return null; // PDF digital (sem IA)

  const getColor = () => {
    if (recommendation === 'ACCEPT') return 'green';
    if (recommendation === 'REVIEW') return 'orange';
    return 'red';
  };

  const getLabel = () => {
    if (recommendation === 'ACCEPT') return 'Confiável';
    if (recommendation === 'REVIEW') return 'Revisar';
    return 'Baixa confiança';
  };

  return (
    <span style={{ color: getColor(), fontWeight: 'bold' }}>
      {getLabel()} ({(score * 100).toFixed(0)}%)
    </span>
  );
};
```

| Score | Cor | Label | Ação sugerida |
|-------|-----|-------|---------------|
| >= 85% | Verde | "Confiável" | Nenhuma — dados OK |
| 60-84% | Laranja | "Revisar" | Exibir aviso para o usuário revisar |
| < 60% | Vermelho | "Baixa confiança" | Exibir alerta — sugerir reprocessamento |
| `null` | — | (não exibir) | PDF digital processado sem IA |
