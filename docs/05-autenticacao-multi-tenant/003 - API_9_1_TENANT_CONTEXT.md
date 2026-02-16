# API_9_1_TENANT_CONTEXT.md

# MULTI-TENANCY — TENANT CONTEXT + ISOLAMENTO TOTAL ENTRE EMPRESAS

Este documento define como o sistema gerencia o isolamento de dados entre empresas (tenants), garantindo

que **cada empresa tenha seus próprios usuários, documentos, PDFs, consolidações e configurações**, sem

impactar outras empresas que utilizam o sistema.

É um componente essencial para SaaS B2B seguro, escalável e multi-cliente.

---

# 1. MODELO DE TENANT (EMPRESA)

Cada empresa que contrata o sistema é um "Tenant".

```json
{
  "id": "tenant123",
  "nome": "Empresa XPTO",
  "dominio": "xpto.com.br",
  "ativo": true,
  "createdAt": "2025-02-01T12:00:00Z",
  "config": {
    "twoFactorRequired": false,
    "maxUsers": 20
  }
}
```

## Campos

| Campo | Descrição |
|-------|-----------|
| id | Identificador único (UUID) |
| nome | Nome legal ou fantasia da empresa |
| dominio | Usado para login restrito por domínio opcional |
| ativo | Indica se o tenant pode operar |
| config | Configurações específicas da empresa |

# 2. MODELO DE USUÁRIO COM TENANT

Todos os usuários pertencem a exatamente um tenant.

```json
{
  "id": "user123",
  "tenantId": "tenant123",
  "nome": "Admin XPTO",
  "email": "admin@xpto.com.br",
  "senhaHash": "argon2id-here",
  "roles": ["TENANT_ADMIN"],
  "twoFactorEnabled": true,
  "refreshTokens": []
}
```

**Importante:**

- O e-mail é globalmente único, mas o usuário tem acesso apenas ao tenant dele.
- Somente SUPER_ADMIN pode manipular tenants.
- O payload do JWT deve conter tenantId.

# 3. TENANT CONTEXT (REQUIREMENTO ESSENCIAL)

Toda request deve carregar informação sobre qual tenant está sendo acessado.

Há três formas de detectar o tenant:

## 3.1 TENANT POR JWT (mais seguro)

O JwtService deve incluir no token:

```json
{
  "sub": "user123",
  "tenantId": "tenant123",
  "roles": ["TENANT_ADMIN"],
  "exp": 1710090900
}
```

O filtro de segurança extrai:

```
TenantContext.setCurrentTenant(jwt.tenantId)
```

## 3.2 TENANT POR HEADER (suporte opcional)

Request deve incluir:

```
X-Tenant-ID: tenant123
```

Só válido para:

- processamento interno
- super-admin acessando outros tenants

## 3.3 TENANT POR DOMÍNIO (modo SaaS avançado)

Exemplo:

- empresaA.sistema.com → tenantA
- empresaB.sistema.com → tenantB

A aplicação extrai subdomínio → resolve tenantId.

# 4. ESTRUTURA DO TENANT CONTEXT (CÓDIGO)

```java
public final class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    public static void setTenant(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static String getTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}
```

Para WebFlux, usar ReactiveContext:

```kotlin
fun Mono<*>.withTenant(tenantId: String) =
    this.contextWrite { ctx -> ctx.put("tenantId", tenantId) }
```

# 5. COMO O MONGO ISOLA DADOS (MODO ROW-LEVEL SECURITY)

Cada collection deve conter tenantId.

## persons

```json
{
  "tenantId": "tenant123",
  "cpf": "12449709568",
  "nome": "FLAVIO ALMEIDA",
  "documentos": ["doc123"]
}
```

## payroll_documents

```json
{
  "id": "doc123",
  "tenantId": "tenant123",
  "cpf": "12449709568",
  "tipo": "CAIXA",
  "status": "PROCESSED"
}
```

## payroll_entries

```json
{
  "tenantId": "tenant123",
  "documentId": "doc123",
  "rubricaCodigo": "4482",
  "valor": 885.47
}
```

## rubricas

Rubricas normalmente são globais, mas podem ser multi-tenant:

- **Modo 1: globais** → mesmo conjunto para todos
- **Modo 2: cada tenant pode personalizar** → tenantId

**Recomendado:**

- `rubricas.globais` → SUPER_ADMIN controla
- `rubricas.custom` → tenantId específico

# 6. REGRAS DE ISOLAMENTO (FUNDAMENTAL)

✔ Um usuário NUNCA pode acessar dados de outro tenant

✔ tenantId é obrigatório em todas collections

✔ Todas queries precisam incluir filtro:

```
find({ tenantId: currentTenant })
```

✔ SUPER_ADMIN pode acessar qualquer tenant manualmente informando X-Tenant-ID

✔ Nenhum payload retornado deve incluir dados de outro tenant

# 7. MIDDLEWARE GLOBAL DE ENFORCEMENT (OBRIGATÓRIO)

Cada request passa pelo filtro:

```
flowchart TD
  A[Request] --> B[Extrair JWT]
  B --> C[Validar Token]
  C --> D[Extrair tenantId]
  D --> E[Set TenantContext]
  E --> F[Executar Handler]
  F --> G[Limpar TenantContext]
```

# 8. ENDPOINTS QUE DEVEM SER MULTI-TENANT AWARE

| API | Comportamento multi-tenant |
|-----|---------------------------|
| Rubricas | Se globais → ignorar tenantId. Se custom → filtrar. |
| Upload | Documento salvo com tenantId |
| Processamento PDF | Tudo filtrado por tenant |
| Consolidação | Apenas dados do tenant atual |
| Users | Apenas SUPER_ADMIN cria tenants |
| Auth | JWT inclui tenantId |

# 9. PERMISSÕES POR PERFIL

## SUPER_ADMIN (nível plataforma)

- Criar tenant
- Ativar/desativar tenant
- Criar usuário em qualquer tenant
- Ver dados de qualquer tenant
- Acessar logs globais

## TENANT_ADMIN (nível empresa)

- Gerenciar usuários do próprio tenant
- Gerenciar rubricas customizadas
- Processar documentos
- Gerar consolidações
- Ver relatórios da empresa

## TENANT_USER

- Upload documentos
- Visualizar seus próprios resultados
- Acesso limitado

# 10. RECOMENDAÇÃO FINAL DE IMPLEMENTAÇÃO

Modelo ideal:

✔ JWT carrega tenantId sempre

✔ Todas queries do Mongo incluem tenantId obrigatoriamente

✔ TenantContext baseado em ReactiveContext

✔ SUPER_ADMIN acessa tenants por header X-Tenant-ID

✔ Rubricas globais + customizadas

✔ Isolamento total garantido
