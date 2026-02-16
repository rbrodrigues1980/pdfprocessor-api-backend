# API: Gerador de Chaves Criptográficas — Documentação para Frontend

> **Criado em**: 15/02/2026
> Documentação completa para integração frontend com a API de geração de secrets.

## Base URL
`/system/secrets`

## Autenticação
Todos os endpoints requerem autenticação JWT via header `Authorization: Bearer <token>`.

**Permissão necessária:** `SUPER_ADMIN`

---

## Endpoints

### GET /system/secrets/generate

Gera uma ou mais chaves criptográficas fortes usando `SecureRandom` com algoritmo DRBG (NIST SP 800-90A).

**Query Parameters:**

| Parâmetro | Tipo | Obrigatório | Default | Descrição |
|-----------|------|-------------|---------|-----------|
| `bits` | number | Não | `512` | Tamanho da chave em bits (256, 384, 512, 1024, 2048, 4096) |
| `format` | string | Não | `base64url` | Formato: `base64`, `base64url`, `hex` |
| `count` | number | Não | `1` | Quantidade de chaves (1-10) |

**Response (uma chave):**
```json
{
  "algorithm": "DRBG",
  "bits": 512,
  "bytes": 64,
  "format": "base64url",
  "generatedAt": "2026-02-15T14:30:00.123Z",
  "secret": "OKfOIPpgaVe39_IO05fiNL9pby25MDj9h_PysAwPO7v8213S_eN56f1Xem5-jK7u5dVs-oBPYhwDhsO0yL4v_g"
}
```

**Response (múltiplas chaves — `count=3`):**
```json
{
  "algorithm": "DRBG",
  "bits": 256,
  "bytes": 32,
  "format": "base64url",
  "generatedAt": "2026-02-15T14:30:00.123Z",
  "secrets": [
    "aB3cD4eF5gH6iJ7kL8mN9oP0qR1sT2u",
    "xY3zW4vU5tS6rQ7pO8nM9lK0jI1hG2f",
    "mN3bV4cX5zA6sD7fG8hJ9kL0pQ1wE2r"
  ]
}
```

**Campos da resposta:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `algorithm` | string | Algoritmo de entropia usado (`DRBG` ou `NativePRNG`) |
| `bits` | number | Tamanho da chave em bits |
| `bytes` | number | Tamanho da chave em bytes |
| `format` | string | Formato da chave gerada |
| `generatedAt` | string (ISO 8601) | Momento da geração |
| `secret` | string | A chave gerada (quando `count=1`) |
| `secrets` | string[] | Lista de chaves (quando `count>1`) |

**Erros possíveis:**

| HTTP Status | Causa |
|-------------|-------|
| 400 | `bits` fora do intervalo 256-4096 ou não múltiplo de 8 |
| 400 | `count` fora do intervalo 1-10 |
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não tem role `SUPER_ADMIN` |

---

### GET /system/secrets/generate/preset/{type}

Gera uma chave com configuração pré-definida para um caso de uso específico.

**Path Parameters:**

| Parâmetro | Descrição |
|-----------|-----------|
| `type` | Tipo de preset: `jwt`, `apikey`, `refresh`, `encryption` |

**Presets disponíveis:**

| Tipo | Bits | Formato | Uso recomendado |
|------|------|---------|-----------------|
| `jwt` | 512 | base64url | JWT_SECRET (HS256/HS384/HS512) |
| `apikey` | 256 | base64url | Chaves de API para integrações |
| `refresh` | 512 | base64url | Tokens de refresh |
| `encryption` | 256 | hex | Chaves AES-256 para criptografia |

**Exemplo:** `GET /system/secrets/generate/preset/jwt`

**Response:**
```json
{
  "type": "jwt",
  "secret": "OKfOIPpgaVe39_IO05fiNL9pby25MDj9h_PysAwPO7v8213S_eN56f1Xem5-jK7u5dVs-oBPYhwDhsO0yL4v_g",
  "algorithm": "DRBG",
  "bits": 512,
  "bytes": 64,
  "format": "base64url",
  "generatedAt": "2026-02-15T14:30:00.123Z",
  "recommendations": {
    "jwt_secret": "Mínimo 256 bits (HS256), recomendado 512 bits",
    "api_key": "Recomendado 256 bits em base64url",
    "refresh_token": "Recomendado 512 bits em base64url",
    "encryption_key_aes256": "Exatamente 256 bits"
  }
}
```

**Erros possíveis:**

| HTTP Status | Causa |
|-------------|-------|
| 400 | `type` inválido (não é jwt, apikey, refresh ou encryption) |
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não tem role `SUPER_ADMIN` |

---

## Exemplos cURL

### Gerar JWT secret (preset)

```bash
curl -X GET "http://localhost:8081/system/secrets/generate/preset/jwt" \
  -H "Authorization: Bearer {seu_token}"
```

### Gerar chave personalizada (1024 bits, hex)

```bash
curl -X GET "http://localhost:8081/system/secrets/generate?bits=1024&format=hex" \
  -H "Authorization: Bearer {seu_token}"
```

### Gerar 5 API keys

```bash
curl -X GET "http://localhost:8081/system/secrets/generate?bits=256&format=base64url&count=5" \
  -H "Authorization: Bearer {seu_token}"
```

---

## Interfaces TypeScript

```typescript
// === Request ===

interface SecretGenerateParams {
  bits?: number;       // 256-4096, default 512
  format?: 'base64' | 'base64url' | 'hex';  // default 'base64url'
  count?: number;      // 1-10, default 1
}

type SecretPresetType = 'jwt' | 'apikey' | 'refresh' | 'encryption';

// === Response ===

interface SecretGenerateResponse {
  algorithm: string;
  bits: number;
  bytes: number;
  format: string;
  generatedAt: string;
  secret?: string;     // quando count=1
  secrets?: string[];  // quando count>1
}

interface SecretPresetResponse {
  type: string;
  secret: string;
  algorithm: string;
  bits: number;
  bytes: number;
  format: string;
  generatedAt: string;
  recommendations: Record<string, string>;
}
```

---

## Service TypeScript

```typescript
import api from './api'; // seu axios/fetch configurado

const SECRET_BASE = '/system/secrets';

export const secretGeneratorService = {
  /**
   * Gera chave(s) criptográfica(s) com parâmetros customizados.
   */
  generate: async (params?: SecretGenerateParams): Promise<SecretGenerateResponse> => {
    const { data } = await api.get(`${SECRET_BASE}/generate`, { params });
    return data;
  },

  /**
   * Gera chave com preset para caso de uso específico.
   */
  generatePreset: async (type: SecretPresetType): Promise<SecretPresetResponse> => {
    const { data } = await api.get(`${SECRET_BASE}/generate/preset/${type}`);
    return data;
  },
};
```

---

## Hook React

```typescript
import { useState, useCallback } from 'react';
import { secretGeneratorService } from '../services/secretGeneratorService';

interface UseSecretGeneratorReturn {
  secret: string | null;
  secrets: string[];
  loading: boolean;
  error: string | null;
  metadata: SecretGenerateResponse | SecretPresetResponse | null;
  generate: (params?: SecretGenerateParams) => Promise<void>;
  generatePreset: (type: SecretPresetType) => Promise<void>;
  copyToClipboard: (value?: string) => Promise<boolean>;
  clear: () => void;
}

export function useSecretGenerator(): UseSecretGeneratorReturn {
  const [secret, setSecret] = useState<string | null>(null);
  const [secrets, setSecrets] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metadata, setMetadata] = useState<any>(null);

  const generate = useCallback(async (params?: SecretGenerateParams) => {
    setLoading(true);
    setError(null);
    try {
      const data = await secretGeneratorService.generate(params);
      setMetadata(data);
      if (data.secret) {
        setSecret(data.secret);
        setSecrets([]);
      } else if (data.secrets) {
        setSecrets(data.secrets);
        setSecret(null);
      }
    } catch (err: any) {
      setError(err.response?.data?.error || 'Erro ao gerar chave');
    } finally {
      setLoading(false);
    }
  }, []);

  const generatePreset = useCallback(async (type: SecretPresetType) => {
    setLoading(true);
    setError(null);
    try {
      const data = await secretGeneratorService.generatePreset(type);
      setMetadata(data);
      setSecret(data.secret);
      setSecrets([]);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Erro ao gerar chave');
    } finally {
      setLoading(false);
    }
  }, []);

  const copyToClipboard = useCallback(async (value?: string) => {
    const text = value || secret;
    if (!text) return false;
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      return false;
    }
  }, [secret]);

  const clear = useCallback(() => {
    setSecret(null);
    setSecrets([]);
    setMetadata(null);
    setError(null);
  }, []);

  return { secret, secrets, loading, error, metadata, generate, generatePreset, copyToClipboard, clear };
}
```

---

## Sugestão de UI

### Tela: Gerador de Secrets (em Configurações > Avançado ou Segurança)

```
┌─────────────────────────────────────────────────────┐
│  🔐 Gerador de Chaves Criptográficas               │
│  Gere chaves seguras para JWT, API keys e tokens.   │
│                                                     │
│  ┌─── Presets Rápidos ────────────────────────────┐ │
│  │  [🔑 JWT Secret]  [🔗 API Key]                │ │
│  │  [🔄 Refresh Token]  [🔒 Encryption Key]      │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  ┌─── Configuração Personalizada ─────────────────┐ │
│  │  Bits: [256 ▼] [384] [512 ●] [1024] [2048]     │ │
│  │  Formato: [base64url ●] [base64] [hex]         │ │
│  │  Quantidade: [1 ▼]                             │ │
│  │                                                │ │
│  │  [ Gerar Chave ]                               │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  ┌─── Resultado ──────────────────────────────────┐ │
│  │  OKfOIPpgaVe39_IO05fiNL9pby25MDj9h_PysAwPO7..  │ │
│  │                                  [📋 Copiar]   │ │
│  │                                                │ │
│  │  Algoritmo: DRBG  |  512 bits  |  base64url    │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  ⚠️ Nunca compartilhe chaves em canais inseguros.   │
│     Armazene em variáveis de ambiente ou vaults.    │
└─────────────────────────────────────────────────────┘
```

### Comportamento esperado

1. **Presets rápidos**: Ao clicar, gera imediatamente a chave e exibe no campo de resultado. O botão fica com estado `loading` durante a geração.

2. **Configuração personalizada**: Permite ajustar parâmetros antes de gerar. O select de bits pode ser um grupo de radio buttons ou chips para facilitar.

3. **Resultado**:
   - Exibir a chave com fonte monospace e texto truncado (com tooltip para ver completa)
   - Botão "Copiar" usa `navigator.clipboard.writeText()` com feedback visual (ícone muda para ✓ por 2s)
   - Exibir metadados abaixo: algoritmo, bits, formato

4. **Múltiplas chaves** (`count > 1`): Exibir como lista, cada uma com seu botão "Copiar".

5. **Segurança**:
   - Não armazenar chaves geradas em localStorage ou sessionStorage
   - Limpar o resultado ao sair da página (`clear()` no `useEffect` cleanup)
   - Exibir aviso sobre não compartilhar em canais inseguros

### Formatos — quando usar cada um

| Formato | Caracteres | Melhor para |
|---------|-----------|-------------|
| `base64url` | `A-Z`, `a-z`, `0-9`, `-`, `_` | JWT_SECRET, API keys, tokens (URL-safe) |
| `base64` | `A-Z`, `a-z`, `0-9`, `+`, `/` | Chaves internas não expostas em URLs |
| `hex` | `0-9`, `a-f` | Chaves AES, HMAC, depuração |

---

## Tratamento de Erros

```typescript
try {
  await generatePreset('jwt');
} catch (err) {
  if (err.response?.status === 401) {
    // Token expirado → redirecionar para login
    navigate('/login');
  } else if (err.response?.status === 403) {
    // Sem permissão → mostrar toast
    toast.error('Apenas SUPER_ADMIN pode gerar chaves.');
  } else if (err.response?.status === 400) {
    // Parâmetros inválidos → mostrar mensagem do backend
    toast.error(err.response.data.error);
  } else {
    toast.error('Erro ao gerar chave. Tente novamente.');
  }
}
```

---

## Notas Importantes

1. **Segurança**: As chaves são geradas no servidor usando `java.security.SecureRandom` com algoritmo DRBG (NIST SP 800-90A), garantindo entropia criptográfica de alta qualidade.

2. **Não persistidas**: Nenhuma chave gerada é salva no banco de dados ou em logs. Após o response, a chave existe apenas no frontend.

3. **HTTPS obrigatório**: Em produção, este endpoint só deve ser acessado via HTTPS para evitar interceptação das chaves em trânsito.

4. **Auditoria**: Cada geração é logada no servidor com bits, formato e count (mas NÃO a chave em si), para fins de auditoria.
