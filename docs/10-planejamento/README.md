# 10 — Planejamento

> Backlog do produto, plano de sprints, roadmap de funcionalidades e organização de APIs.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - BACKLOG.md](./001%20-%20BACKLOG.md) | Backlog organizado em 8 épicos: Infraestrutura Backend, Modelagem de Dados, Processamento PDF, APIs REST, Regras CAIXA/FUNCEF, Frontend Admin, Exportação Excel e Testes. Inclui lista completa das 24 rubricas parametrizadas. |
| [002 - SPRINT_PLAN.md](./002%20-%20SPRINT_PLAN.md) | Plano de 4 sprints principais (2 semanas cada) + 2 opcionais. Sprint 1: Infraestrutura & Upload. Sprint 2: Processamento PDF. Sprint 3: Consolidação & Query. Sprint 4: Excel & Admin. User stories com checkboxes. |
| [003 - PLANEJAMENTO_GERENCIAMENTO_USUARIOS.md](./003%20-%20PLANEJAMENTO_GERENCIAMENTO_USUARIOS.md) | Planejamento do CRUD completo de usuários. Matriz de permissões SUPER_ADMIN/TENANT_ADMIN, endpoints propostos (criar, listar, editar, desativar, reativar), DTOs, validações e checklist de implementação. |
| [004 - ORGANIZACAO_APIS_AUTH_USUARIOS_TENANTS.md](./004%20-%20ORGANIZACAO_APIS_AUTH_USUARIOS_TENANTS.md) | Organização atual das APIs: autenticação em `/api/v1/auth`, tenants em `/api/v1/tenants`. Documenta funcionalidades existentes e faltantes (CRUD de usuários). |

---

## Visão geral dos épicos

```
✅ Épico 1: Infraestrutura Backend (Spring Boot, MongoDB, Docker)
✅ Épico 2: Modelagem de Dados (Collections, Schemas, Índices)
✅ Épico 3: Processamento PDF (PDFBox, Regex, Extrator)
✅ Épico 4: APIs REST (Upload, Process, Query, Entries)
✅ Épico 5: Regras CAIXA/FUNCEF (Normalização, Validação)
🔲 Épico 6: Frontend Admin (Painel administrativo)
✅ Épico 7: Exportação Excel (Apache POI, Matriz)
🔲 Épico 8: Testes (Unitários, Integração, E2E)
```

---

[← Voltar ao índice](../README.md)
