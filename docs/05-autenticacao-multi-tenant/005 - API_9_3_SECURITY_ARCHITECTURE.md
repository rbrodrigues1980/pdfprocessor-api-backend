# 🔐 Arquitetura de Segurança do Sistema — JWT, Refresh, 2FA, RBAC e Multi-Tenancy

Este documento descreve TODA a arquitetura de segurança do sistema, incluindo:

- Autenticação (JWT + Refresh Token rotativo)
- 2FA baseado em código de 6 dígitos
- Autorização (RBAC + níveis multi-empresa)
- Isolamento multi-tenant (Tenant Context)
- Hardening seguindo OWASP & NIST
- Proteções de API (rate limiting, brute force, CORS, headers)
- Segurança no armazenamento (MongoDB)
- Segurança da pipeline do PDF
- Logging seguro & auditoria

---

# 1. OBJETIVOS PRINCIPAIS DE SEGURANÇA

✔ Garantir que cada empresa tenha acesso somente a seus próprios dados

✔ Proteger a autenticação contra ataques de força bruta

✔ Garantir integridade dos tokens

✔ Evitar roubo de sessão via Refresh Token Rotativo

✔ Permitir auditoria completa de ações críticas

✔ Impedir vazamentos acidentais entre tenants

✔ Segurança consistente em ambiente WebFlux reativo

---

# 2. MODELO DE AUTENTICAÇÃO

A autenticação usa:

| Componente | Padrão |
|------------|--------|
| Access Token | JWT HS512/RS256 — 15 min |
| Refresh Token | UUID seguro — 30 dias |
| 2FA | código de 6 dígitos (email) |
| Password Hashing | Argon2id (OWASP recomendado) |
| Contexto de Segurança | ReactiveSecurityContextHolder |

## 2.1 JWT — Access Token

**Payload:**

```json
{
  "sub": "user123",
  "tenantId": "tenant123",
  "email": "admin@empresa.com",
  "roles": ["TENANT_ADMIN"],
  "iat": 1710000000,
  "exp": 1710000900,
  "jti": "uuid-jti-token"
}
```

**Regras:**

✔ Deve incluir tenantId

✔ Deve incluir roles

✔ Deve incluir jti (ID único para revogação)

✔ Não pode incluir dados sensíveis

✔ Expiração curta (15 min)

## 2.2 Refresh Token

**Implementação:**

- formato: uuid-v4
- armazenado no Mongo + data de expiração
- atrelado ao usuário (e tenant)
- rotativo → ao usar, gera outro e invalida o antigo

**Fluxo:**

1. Usuário envia refresh
2. Sistema valida no Mongo
3. Se válido → cria novo Refresh + novo Access
4. Se o token já tiver sido usado antes → logout global

## 2.3 2FA — Autenticação em Duas Etapas

**Geração:**

- código de 6 dígitos (000000–999999)
- expira em 5 minutos
- apenas 1 código ativo por usuário
- reenviar só após 60s

**Armazenamento:**

```json
{
  "twoFactorTempCode": "493201",
  "twoFactorTempCodeExpires": "2025-01-10T11:54:00Z",
  "twoFactorEnabled": true
}
```

**Cenários:**

| Configuração | Efeito |
|--------------|--------|
| twoFactorForceGlobal = true | Todo login deve passar 2FA |
| user.twoFactorEnabled = true | Somente usuários marcados |

---

# 3. MODELO DE AUTORIZAÇÃO (RBAC)

**Níveis:**

| Papel | Acesso |
|-------|--------|
| SUPER_ADMIN | controla plataforma inteira |
| TENANT_ADMIN | gerencia usuários da empresa |
| TENANT_USER | operações básicas |

**Regras:**

✔ SUPER_ADMIN ignora tenant

✔ TENANT_ADMIN não pode modificar outro tenant

✔ TENANT_USER não pode ver dados de outros usuários

---

# 4. MULTI-TENANCY — ISOLAMENTO COMPLETO

Cada request deve carregar tenantId, obtido de:

- JWT
- Header X-Tenant-ID (apenas SUPER_ADMIN)
- Subdomínio (modo SaaS avançado)

Todas as collections possuem obrigatoriamente:

```json
{
  "tenantId": "tenant123"
}
```

## 4.1 Enforcamento do Tenant (Obrigatório)

Toda query deve usar:

```
find({ tenantId: TenantContext.get() })
```

Toda gravação deve incluir:

```
payload.tenantId = TenantContext.get()
```

---

# 5. HARDENING DE SEGURANÇA

## 5.1 Hash de Senha (Argon2id)

**Parâmetros recomendados:**

- memória: 64 MB
- iterações: 3
- paralelismo: 4

## 5.2 Rate Limiting / Anti-Bruteforce

**Aplicado a:**

- `/auth/login`
- `/auth/verify-2fa`
- `/auth/refresh`

**Mecanismos:**

- X tentativas → bloqueio temporário
- rate-limit por IP + por email
- logs de tentativas

## 5.3 Headers de Segurança

**Adicionar via WebFilter:**

- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`
- `Cache-Control: no-store`
- `Permissions-Policy: ...`
- `Strict-Transport-Security: max-age=31536000`

## 5.4 Segurança CORS

**Aceitar apenas:**

- domínios autorizados por tenant
- métodos e headers específicos

---

# 6. SEGURANÇA NO TRATAMENTO DE PDF

✔ PDFBox + Apache Tika

✔ Timeouts para processamento

✔ Rejeitar PDFs com mais de X páginas

✔ Rejeitar PDFs acima de X MB

✔ Sanitização dos textos extraídos

✔ Nenhum arquivo é executado — apenas leitura binária

---

# 7. SEGURANÇA NO MONGO

**Mode recomendado:**

- usar índices por tenantId
- validar duplicação por tenant
- criptografar volumes do banco
- logs desabilitam dados sensíveis

---

# 8. SEGURANÇA DO PIPELINE REATIVO (WebFlux)

✔ Reactor Context carregando tenant

✔ JWT validado no filtro antes do handler

✔ Nunca bloquear threads (security-sensitive)

✔ Cancelar pipeline em caso de token inválido

---

# 9. LOGGING + AUDITORIA

Todos os eventos críticos devem ser registrados:

- login
- login inválido
- refresh usado
- refresh inválido
- tenant access override
- 2FA enviado
- 2FA falho
- criação de usuário
- exclusão de usuário
- upload de documento

**Formato:**

```json
{
  "timestamp": "...",
  "tenantId": "tenant123",
  "userId": "user123",
  "event": "LOGIN_FAILED",
  "metadata": { ... }
}
```

---

# 10. CHECKLIST OFICIAL DE SEGURANÇA

## 🔒 Autenticação

- [ ] JWT com expiração curta
- [ ] Refresh rotativo
- [ ] Argon2id
- [ ] 2FA opcional/global

## 🏢 Multi-Tenancy

- [ ] tenantId obrigatório
- [ ] ReactiveContext isolado
- [ ] SUPER_ADMIN limitado
- [ ] header X-Tenant-ID para override seguro

## 🧱 API Security

- [ ] Rate limiting
- [ ] CORS seguro
- [ ] Headers de segurança
- [ ] Logging de auditoria

## 📄 PDF & Dados

- [ ] validações de tamanho
- [ ] validações de extensão
- [ ] sanitização de texto

---

# 11. RECOMENDAÇÃO FINAL

✔ Segurança 100% alinhada com OWASP Top 10

✔ Suporte a SaaS multi-tenant seguro

✔ JWT + Refresh + 2FA + RBAC + Tenant Isolation

✔ Pipeline reativo seguro

