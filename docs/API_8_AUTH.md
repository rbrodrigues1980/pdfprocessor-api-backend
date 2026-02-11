# API_8_AUTH.md
# AUTENTICAÇÃO — JWT + REFRESH TOKEN + 2FA (CÓDIGO DE 6 DÍGITOS)

## 1. OBJETIVO

Sistema robusto de autenticação que garanta:

- segurança máxima com **JWT + Refresh Tokens**
- fluxo opcional de **2FA**, aplicável:
  - globalmente
  - por usuário
- controle de tentativas
- compatível com **WebFlux (reativo)**
- capaz de integrar com **painel admin** e **mobile** futuramente

---

## 2. MODELO DE USUÁRIO (MongoDB)

```json
{
  "id": "user123",
  "nome": "Administrador",
  "email": "admin@sistema.com",
  "senhaHash": "$argon2id$v=19$m=65536,t=3,p=4$...",
  "permissoes": ["ADMIN"],

  "twoFactorEnabled": true,
  "twoFactorSecret": null,
  "twoFactorTempCode": "123456",
  "twoFactorTempCodeExpires": "2024-03-10T12:30:00Z",

  "refreshTokens": [
    {
      "token": "uuid-random",
      "createdAt": "2024-03-10T11:20:00Z",
      "expiresAt": "2024-04-10T11:20:00Z"
    }
  ]
}
```

---

## 3. ENDPOINTS DA AUTENTICAÇÃO

### 3.1 ▶️ POST `/api/v1/auth/register`

Cria usuário novo.

#### 📤 Request

```json
{
  "nome": "Administrador",
  "email": "admin@sistema.com",
  "senha": "SenhaUltraSegura123"
}
```

#### 📥 Response

```json
{
  "id": "user123",
  "email": "admin@sistema.com",
  "twoFactorEnabled": false
}
```

---

### 3.2 ▶️ POST `/api/v1/auth/login`

Primeira etapa de login.

#### 📤 Request

```json
{
  "email": "admin@sistema.com",
  "senha": "SenhaUltraSegura123"
}
```

#### 📥 Possíveis respostas

🔹 **Caso 1 — 2FA DESATIVADO**

```json
{
  "accessToken": "jwt-here",
  "refreshToken": "refresh-here"
}
```

🔹 **Caso 2 — 2FA ATIVADO (global ou por usuário)**

```json
{
  "requires2FA": true,
  "message": "Código de 6 dígitos enviado"
}
```

---

### 3.3 ▶️ POST `/api/v1/auth/verify-2fa`

Segunda etapa (somente se 2FA ativo).

#### 📤 Request

```json
{
  "email": "admin@sistema.com",
  "code": "573912"
}
```

#### 📥 Response

```json
{
  "accessToken": "jwt-here",
  "refreshToken": "refresh-here"
}
```

---

### 3.4 ▶️ POST `/api/v1/auth/refresh`

Gera novo access token a partir do refresh token.

#### 📤 Request

```json
{
  "refreshToken": "refresh-token-value"
}
```

#### 📥 Response

```json
{
  "accessToken": "new-access",
  "refreshToken": "new-refresh"
}
```

---

### 3.5 ▶️ POST `/api/v1/auth/logout`

Invalida o refresh token atual.

#### 📤 Request

```json
{
  "refreshToken": "refresh-token-value"
}
```

*(Resposta pode ser 204 No Content ou um JSON simples de confirmação.)*

---

### 3.6 ▶️ POST `/api/v1/auth/force-2fa`

Ativa/desativa 2FA globalmente (somente para ADMIN).

#### 📤 Request

```json
{
  "enabled": true
}
```

---

## 4. JWT — CONFIGURAÇÃO

### 4.1 Access Token (curto prazo)

| Campo      | Valor                                                   |
|------------|---------------------------------------------------------|
| Expiração  | **15 minutos**                                          |
| Assinatura | **HS512** (ou **RS256** para chave pública/privada)     |
| Conteúdo   | `id`, `email`, `permissoes`                             |

---

### 4.2 Refresh Token (longo prazo)

| Campo             | Valor                  |
|-------------------|------------------------|
| Expiração         | **30 dias**            |
| Armazenado no Mongo? | ✔ sim              |
| É rotativo?       | ✔ sim (refresh rotation) |

**Refresh rotation**:

- A cada uso válido de refresh, um novo refresh é emitido.
- Se um refresh antigo for reutilizado → é sinal de possível roubo → pode disparar logout global.

---

## 5. 2FA — DETALHES DO FLUXO

### 5.1 Quando 2FA é necessário?

#### Cenário 1 — Sistema configurado com 2FA obrigatório

```text
twoFactorForceGlobal = true
```

➡️ **TODOS os usuários** precisam do código.

#### Cenário 2 — Ativado individualmente

```text
user.twoFactorEnabled = true
```

➡️ Somente usuários ativados precisam.

---

### 5.2 Geração do código de 6 dígitos

- Código aleatório: `000000 → 999999`
- Expiração: **5 minutos**
- Entrega: **e-mail (SMTP)** (pode ser trocado para SMS/WhatsApp depois)
- Armazenado no usuário:

  - `twoFactorTempCode`
  - `twoFactorTempCodeExpires`

---

### 5.3 Regras de segurança

- ✔ Código expira após **5 minutos**
- ✔ Usuário só pode pedir **novo código** após **60 segundos**
- ✔ **5 tentativas** → bloqueio temporário do 2FA/login
- ✔ Todos os eventos críticos devem gerar **logs de auditoria**

---

## 6. HARDENING DE SEGURANÇA

### ✔ Hash de senha moderno: **Argon2id**

- Alta resistência a brute force e ataques com GPU
- Recomendada pela OWASP
- Parâmetros de custo pensados para servidor moderno (memória e tempo)

### ✔ Refresh token rotativo

- Evita uso prolongado de um token vazado
- Se um refresh antigo for usado, pode disparar:
  - invalidação de todos tokens daquele usuário
  - log de segurança

### ✔ Anti-bruteforce

- contador de tentativas por IP/usuário
- lockout temporário (ex.: 15 minutos) após X falhas

### ✔ JWT com:

- `audience` (`aud`)
- `issuer` (`iss`)
- `clock skew` limitado
- `jti` (ID único por token) para rastreio/revogação

---

## 7. ERROS PADRÃO

| Código               | Descrição            |
|----------------------|----------------------|
| `INVALID_CREDENTIALS`| senha incorreta      |
| `ACCOUNT_LOCKED`     | muitas tentativas    |
| `INVALID_2FA_CODE`   | código incorreto     |
| `EXPIRED_2FA_CODE`   | código expirado      |
| `INVALID_REFRESH`    | refresh inválido     |
| `EXPIRED_REFRESH`    | refresh expirado     |

---

## 8. ORDEM DE IMPLEMENTAÇÃO (PASSO A PASSO)

1. **UserRepository + modelos**
   - Criar coleção `users` no Mongo
   - Definir campos: id, nome, email, senhaHash, permissoes, flags de 2FA, refreshTokens

2. **PasswordService (Argon2id)**
   - Implementar serviço para:
     - hash de senhas novas
     - verificação de senha (login)
   - Garantir uso de Argon2id com parâmetros adequados.

3. **JwtService (access + refresh)**
   - Gerar e validar:
     - Access Token (15 min)
     - Refresh Token (30 dias)
   - Incluir `sub`, `email`, `roles`, `iat`, `exp`, `jti`.

4. **TwoFactorService**
   - Geração de código de 6 dígitos
   - Persistência temporária em `twoFactorTempCode` e `twoFactorTempCodeExpires`
   - Validação do código
   - Controle de tentativas e timeout

5. **AuthService**
   - Orquestrar:
     - registro
     - login (password)
     - fluxo 2FA (quando habilitado)
     - geração de tokens
     - refresh
     - logout

6. **Endpoints REST**
   - Implementar os controllers:
     - `/auth/register`
     - `/auth/login`
     - `/auth/verify-2fa`
     - `/auth/refresh`
     - `/auth/logout`
     - `/auth/force-2fa`

7. **Filtros WebFlux para autenticação**
   - Criar filtro de segurança que:
     - lê o `Authorization: Bearer <token>`
     - valida o JWT
     - popula o contexto de segurança (usuário logado)
     - bloqueia acesso não autorizado aos endpoints protegidos

8. **Integração com painel admin**
   - Garantir que o frontend admin:
     - faça login
     - armazene access/refresh tokens
     - faça refresh quando expirar o access
     - permita fluxo de 2FA (tela de código)

9. **Testes unitários e integração**
   - Testar:
     - login com senha correta/incorreta
     - fluxo de 2FA (código válido, inválido, expirado)
     - refresh válido/expirado
     - lockout após tentativas
     - revogação de refresh token

---

## 9. CLASSES NECESSÁRIAS (SUGESTÃO)

- `AuthController`
- `AuthService`
- `JwtService`
- `RefreshTokenStore`
- `TwoFactorService`
- `PasswordHashingService`
- `UserRepository`
- `AuthProperties` (config JWT/2FA)
- `EmailService` (envio do código)

---

## 10. FLUXO COMPLETO (LOGIN + 2FA + JWT)

```mermaid
flowchart TD
    A[Login: email + senha] --> B[Valida credenciais]
    B --> C{2FA obrigatório?}
    C -->|Não| D[Gerar Access + Refresh Token]
    C -->|Sim| E[Gerar código 6 dígitos]
    E --> F[Enviar código por email]
    F --> G[Usuário envia código /verify-2fa]
    G --> H{Código válido?}
    H -->|Sim| I[Gerar JWTs (Access + Refresh)]
    H -->|Não| J[Erro + tentar novamente]
```

---

## 11. EXEMPLO DE PAYLOAD JWT

```json
{
  "sub": "user123",
  "email": "admin@sistema.com",
  "roles": ["ADMIN"],
  "iat": 1710090000,
  "exp": 1710090900,
  "jti": "uuid-random"
}
```

---

Fim do arquivo **API_8_AUTH.md**.
