# Filtro Global de Multi-Tenancy (Tenant Context Filter)

## Isolamento Total Entre Empresas — WebFlux + ReactiveContext

Este documento define exatamente como funciona o filtro global responsável por garantir que cada empresa (tenant) só consiga acessar seus próprios dados, aplicando isolamento horizontal (row-level security) em todo o sistema.

Ele é um dos pilares fundamentais do SaaS multi-tenant seguro.

---

# 🔐 1. Objetivo

O Tenant Filter garante:

✔ Toda request autenticada possui tenantId

✔ Queries do Mongo sempre filtram tenantId

✔ Usuários nunca acessam outro tenant

✔ SUPER_ADMIN pode trocar o tenant via header

✔ WebFlux injeta o tenant no ReactiveContext

✔ Proteção contra vazamentos de dados entre empresas

---

# 🧱 2. Fonte do Tenant

O tenant atual pode ser determinado por 3 mecanismos, em ordem de prioridade:

| Ordem | Fonte | Exemplo | Uso |
|-------|-------|---------|-----|
| 1 | JWT (obrigatório) | `"tenantId": "tenant123"` | padrão |
| 2 | Header X-Tenant-ID | `X-Tenant-ID: abc123` | usado pelo SUPER_ADMIN |
| 3 | Subdomínio | `empresaA.sistema.com` | modo SaaS avançado |

O filtro resolve o tenant usando essa ordem.

Caso o usuário não seja SUPER_ADMIN, o sistema ignora headers e subdomínio e usa somente o tenant do JWT.

---

# 📝 3. Estrutura do TenantContext

## Versão Java (ThreadLocal):

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

## Versão WebFlux (Reactive Context):

```kotlin
fun <T> Mono<T>.withTenant(tenantId: String): Mono<T> =
    this.contextWrite { ctx -> ctx.put("tenantId", tenantId) }
```

---

# 🧩 4. Estrutura do Filter (WebFlux)

Este filtro é executado antes de qualquer controller.

```java
@Component
public class TenantFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        return resolveTenant(exchange)
            .flatMap(tenantId -> 
                chain.filter(exchange)
                     .contextWrite(ctx -> ctx.put("tenantId", tenantId))
            );
    }

    private Mono<String> resolveTenant(ServerWebExchange exchange) {

        String forcedTenant = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");

        return ReactiveSecurityUtils.getAuthenticatedUser()
            .flatMap(user -> {

                // SUPER_ADMIN pode mudar tenant
                if (forcedTenant != null && user.isSuperAdmin()) {
                    return Mono.just(forcedTenant);
                }

                // Usuários normais usam tenantId do JWT
                return Mono.just(user.getTenantId());
            });
    }
}
```

---

# 📌 5. Como o Mongo Filtra por Tenant

Todas as collections possuem campo obrigatório:

```json
{
  "tenantId": "tenant123"
}
```

## Repositório exemplo:

```java
public Mono<Person> findByCpf(String cpf) {

    return ReactiveTenantContext.getTenantId()
        .flatMap(tid ->
            mongoTemplate.findOne(
                Query.query(
                    Criteria.where("tenantId").is(tid)
                            .and("cpf").is(cpf)
                ),
                Person.class
            )
        );
}
```

Nenhuma query pode ser executada sem tenantId.

Caso contrário → erro fatal e log crítico.

---

# 🧭 6. Fluxo Completo do Tenant Filter

```
flowchart TD
    A[Request] --> B[Extrair JWT]
    B --> C{JWT válido?}
    C -->|Não| Z[401 Unauthorized]
    C -->|Sim| D[Extrair tenantId do token]

    D --> E{X-Tenant-ID presente?}
    E -->|Sim e SUPER_ADMIN| F[Substituir tenantId]
    E -->|Não| G[Usar tenantId do JWT]

    F --> H[Validar Tenant Ativo]
    G --> H[Validar Tenant Ativo]

    H --> I[Injetar tenant no ReactiveContext]
    I --> J[Executar Handler]
    J --> K[Limpar TenantContext]
```

---

# 🛡️ 7. Regras de Segurança Essenciais

✔ Usuário só pode acessar seu próprio tenant

✔ SUPER_ADMIN acessa qualquer tenant via X-Tenant-ID

✔ Toda response deve estar filtrada por tenant

✔ Qualquer ausência de tenantId → request bloqueada

✔ Toda query deve ter tenantId obrigatório

✔ Collections sem tenant são consideradas inseguras

---

# 🧪 8. Testes Obrigatórios

## 🔍 Testes Positivos

- Login retorna JWT com tenantId

- Todas as queries retornam apenas dados do tenant

- SUPER_ADMIN acessa outro tenant via header

- Subdomínio identifica tenant corretamente

## 🔥 Testes Negativos

- Usuário tentando acessar outro tenant → 403

- Query sem tenantId → exceção

- Tenant desativado → 403

- X-Tenant-ID sendo usado por não-admin → 403

---

# 📚 9. Como Cada API Deve Respeitar Tenant

| API | Comportamento Multi-Tenant |
|-----|---------------------------|
| Auth | JWT inclui tenantId |
| Users | sempre filtrado por tenantId |
| Rubricas | globais + customizadas por tenant |
| Upload | documento armazenado com tenantId |
| Processamento PDF | apenas documentos do tenant |
| Consolidação | usa tenantId obrigatório |
| Excel Export | gera Excel apenas do tenant atual |

---

# 🎯 10. Recomendações Finais

🔒 TenantId deve aparecer obrigatoriamente em:

- JWT

- Refresh Token

- Login Response

- Todas collections do Mongo

- Todas queries do sistema

- Logs de auditoria

- Request debug

📌 Nunca permitir que a aplicação funcione sem tenantId.

📌 Nunca salvar documento sem tenantId.

📌 Nunca devolver dados de outro tenant.

