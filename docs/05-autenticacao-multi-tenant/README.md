# 05 — Autenticação & Multi-Tenant

> JWT, Refresh Token, 2FA, RBAC e isolamento completo entre empresas (Multi-Tenancy).

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - API_8_AUTH.md](./001%20-%20API_8_AUTH.md) | **Autenticação single-tenant.** JWT (15 min) + Refresh Token rotativo (30 dias) + 2FA via código de 6 dígitos por email. Modelo de usuário, endpoints (register/login/verify-2fa/refresh/logout), hash Argon2id e hardening. |
| [002 - API_9_MULTI_TENANT_AUTH.md](./002%20-%20API_9_MULTI_TENANT_AUTH.md) | **Autenticação multi-tenant.** Evolução do API_8 com isolamento por empresa, Super Admin global, roles (SUPER_ADMIN/TENANT_ADMIN/TENANT_USER), JWT com tenantId e 2FA hierárquico. |
| [003 - API_9_1_TENANT_CONTEXT.md](./003%20-%20API_9_1_TENANT_CONTEXT.md) | Tenant Context e isolamento total. Modelo de tenant, vinculação de usuários, três formas de detecção (JWT/header/subdomínio), TenantContext (ThreadLocal/ReactiveContext) e row-level security no MongoDB. |
| [004 - API_9_2_TENANT_FILTER.md](./004%20-%20API_9_2_TENANT_FILTER.md) | Filtro global WebFlux que garante isolamento. Resolve tenant, injeta no ReactiveContext, valida tenant ativo e filtra queries MongoDB. Código de exemplo e fluxo completo. |
| [005 - API_9_3_SECURITY_ARCHITECTURE.md](./005%20-%20API_9_3_SECURITY_ARCHITECTURE.md) | Arquitetura de segurança completa: autenticação, 2FA, RBAC, isolamento multi-tenant, hardening (Argon2id, rate limiting, headers), segurança PDF, checklist OWASP/NIST. |
| [006 - API_9_4_DOMAIN_MODELS_MULTI_TENANT.md](./006%20-%20API_9_4_DOMAIN_MODELS_MULTI_TENANT.md) | Modelos de domínio DDD adaptados para multi-tenant. Todos com tenantId obrigatório: Tenant, User, Person, PayrollDocument, PayrollEntry, Rubrica (global/custom), Consolidation, AuditLog. |
| [007 - API_9_5_CONSOLIDADO_MULTI_TENANT.md](./007%20-%20API_9_5_CONSOLIDADO_MULTI_TENANT.md) | **Documento consolidado (~1.230 linhas).** Reúne tudo sobre multi-tenancy: visão geral, modelos, autenticação, Tenant Context, Tenant Filter, segurança, endpoints, diagramas e checklist de implementação. |

---

## Hierarquia de Roles

```
SUPER_ADMIN          → Acesso global a todos os tenants
├── TENANT_ADMIN     → Administrador de um tenant específico
└── TENANT_USER      → Usuário comum de um tenant
```

## Ordem de leitura sugerida

### Leitura rápida (visão geral)
1. `007 - API_9_5_CONSOLIDADO_MULTI_TENANT.md` — Documento único que consolida tudo

### Leitura aprofundada (implementação)
1. `001 - API_8_AUTH.md` — Base da autenticação
2. `002` → `003` → `004` → `005` → `006` (evolução incremental do multi-tenant)

---

[← Voltar ao índice](../README.md)
