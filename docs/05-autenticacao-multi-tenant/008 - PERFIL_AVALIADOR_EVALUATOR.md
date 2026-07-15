# 008 — Perfil "Avaliador" (EVALUATOR) com allowlist de clientes

> Perfil restrito para avaliação/homologação da aplicação por terceiros (ex.: escritório de advocacia), sem criar um novo ambiente/deploy. O SUPER_ADMIN atribui a cada avaliador uma lista de clientes específicos; o avaliador só enxerga esses clientes e só pode subir declaração de IR e gerar Excel/Resumo Geral.

---

## 1. Motivação

A aplicação é do Perito (dono). Para permitir que um escritório teste o sistema com um conjunto limitado de clientes reais — sem provisionar um novo ambiente no GCP — foi criado o perfil **EVALUATOR**. Ele é **somente-leitura no cadastro** e opera apenas sobre os clientes que o SUPER_ADMIN selecionar.

## 2. Modelo

- Nova role (string) **`EVALUATOR`**, adicionada às roles existentes (`SUPER_ADMIN`, `TENANT_ADMIN`, `TENANT_USER`).
- Novo campo em `User`: **`allowedPersonIds: Set<String>`** — IDs dos clientes (`Person`) que o avaliador pode acessar.
- O avaliador tem **`tenantId = null`**: seu escopo é a allowlist de clientes (que pode abranger clientes de tenants diferentes), e não um tenant específico.

```java
// User.java
private Set<String> allowedPersonIds = new HashSet<>();
public boolean isEvaluator() { return roles != null && roles.contains("EVALUATOR"); }
```

A allowlist fica **no banco** (não no JWT), então reatribuições feitas pelo SUPER_ADMIN passam a valer **imediatamente**, sem exigir novo login.

## 3. Permissões

| Ação | Avaliador |
|------|-----------|
| Listar/ver os clientes **atribuídos** | ✅ |
| Ver documentos/lançamentos desses clientes | ✅ |
| Subir **declaração de IR** (por cliente) | ✅ |
| Gerar **Excel** e **Resumo Geral** (Excel/PDF) desses clientes | ✅ |
| Criar / editar / desativar / ativar / excluir cliente | ❌ |
| Validar cliente | ❌ |
| Subir **contracheque** | ❌ |
| Excluir documento | ❌ |
| Gerenciar usuários / tenants / empresas / rubricas / repasse / logs | ❌ |
| Dashboard | ❌ |

## 4. Enforcement (2 camadas)

### 4.1. Filtro de ação — `EvaluatorAuthorizationFilter` (deny-by-default)

`WebFilter` que, para quem tem a role EVALUATOR, permite **somente** um conjunto explícito de endpoints (prefixo `/api/v1`):

- `GET /persons`, `GET /persons/{id}`
- `GET /persons/*/documents`, `/documents-by-id`, `/entries`, `/rubricas`
- `GET /persons/*/resumo-geral`, `/resumo-geral/pdf`
- `GET /persons/*/excel`, `/excel-by-id`, `/excel-by-tenant`
- `POST /persons/*/income-tax/upload`, `/income-tax/bulk-upload`
- `GET /documents/{id}` e subpaths de leitura (`/entries`, `/pages`, `/summary`, `/processing-status`, `/irpf-data`)

Qualquer outra combinação método+path retorna **403 Forbidden**. Isso bloqueia criação/edição/desativação/exclusão/validação de cliente, upload de contracheque, exclusão de documento, dashboard e toda a administração.

### 4.2. Escopo por cliente — `EvaluatorAccessService`

Mesmo nos endpoints permitidos, o acesso é restrito à allowlist:

- `currentAllowedPersonIds()` — carrega a allowlist do usuário atual (do banco).
- `assertPersonAccessible(personId)` — lança `ForbiddenOperationException` (403) se o cliente não está na allowlist; no-op para as demais roles.

Aplicado nos use cases:

| Use case | Comportamento para EVALUATOR |
|----------|------------------------------|
| `ListPersonsUseCase` | Filtra `_id IN allowedPersonIds` (ignora tenant); allowlist vazia → lista vazia |
| `GetPersonByIdUseCase` | `assertPersonAccessible` antes de retornar |
| `IncomeTaxUploadUseCase` / `ITextIncomeTaxUploadUseCase` (por personId) | Passam por `GetPersonByIdUseCase` → validado |
| `ExcelExportUseCase` (excel / excel-by-id / excel-by-tenant) | Resolve a pessoa e valida a allowlist |
| `ResumoGeralUseCase` / `ResumoGeralPdfUseCase` | `assertPersonAccessible` + consolidação com o tenant da própria pessoa |
| `DocumentQueryUseCase` (findById / findByPersonId / findByCpf) | Valida que o documento/cliente pertence à allowlist |

### 4.3. TenantFilter

O `TenantFilter` trata o EVALUATOR como contexto **`GLOBAL`** (como o SUPER_ADMIN), sem o warning de "tenant não encontrado" — o isolamento real vem da allowlist, não do tenant.

## 5. Gestão do avaliador (somente SUPER_ADMIN)

- `POST /api/v1/users` e `PUT /api/v1/users/{id}` aceitam `roles: ["EVALUATOR"]` + `allowedPersonIds: [...]`.
- Regras (`CreateUserUseCase` / `UpdateUserUseCase`):
  - Apenas **SUPER_ADMIN** pode criar/editar usuários EVALUATOR.
  - EVALUATOR é criado com `tenantId = null`.
  - `allowedPersonIds` só é persistido quando a role é EVALUATOR (limpo caso a role mude).
- `UserResponse` inclui `roles` e `allowedPersonIds`.

Exemplo de criação:

```json
POST /api/v1/users
{
  "nome": "Escritório - Avaliador",
  "email": "avaliador@escritorio.com",
  "senha": "********",
  "roles": ["EVALUATOR"],
  "allowedPersonIds": ["6a3c52ef1a7120735486e2ae", "68d94ba..."]
}
```

## 6. Frontend

- Hook `usePermissions` (`isEvaluator`, `canCreateClient`, `canEditClient`, `canUploadContracheque`, ...).
- Página de clientes (`persons-page.tsx`): esconde criar/editar/desativar/excluir/validar/contracheque; mantém **Declaração de IR**, **Exportar Excel** e o modal **Resumo Geral**. A query de empresas não é disparada para o avaliador (evita 403).
- Criar/editar usuário: opção de role **Avaliador** + multi-select de clientes (`ClientAllowlistSelector`) que popula `allowedPersonIds`.
- Navegação: o avaliador vê apenas **Clientes** (sem Dashboard); login e rota raiz redirecionam para `/persons`.

## 7. Testes

- `EvaluatorAuthorizationFilterTest` — matriz permitido/bloqueado por método+path.
- Escopo por cliente coberto indiretamente pelos testes dos use cases de Resumo Geral/Excel.

## 8. Arquivos-chave

| Camada | Arquivo |
|--------|---------|
| Modelo | `domain/model/User.java` (`allowedPersonIds`, `isEvaluator`) |
| Acesso | `application/security/EvaluatorAccessService.java` |
| Filtro | `infrastructure/security/EvaluatorAuthorizationFilter.java` |
| Helper | `infrastructure/security/ReactiveSecurityContextHelper.isEvaluator()` |
| Tenant | `infrastructure/tenant/TenantFilter.java` |
| Gestão | `application/users/CreateUserUseCase.java`, `UpdateUserUseCase.java` + DTOs |

---

[← Voltar ao índice](../README.md)
