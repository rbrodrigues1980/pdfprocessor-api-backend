# 06 — Guia Frontend

> Documentação de integração para desenvolvedores frontend.
> Cada documento inclui exemplos em TypeScript, interfaces, hooks React, e tratamento de erros.

---

## Documentos nesta seção

| Módulo | Documento | Endpoint Base | Descrição |
|--------|-----------|---------------|-----------|
| **QA** | [000 - JORNADA_USUARIO_QA.md](./000%20-%20JORNADA_USUARIO_QA.md) | — | **Jornada completa do usuário** passo a passo, do login à exportação Excel. Roteiro QA com checklist de validação para cada etapa. |
| Auth | [001 - API_AUTH_FRONTEND.md](./001%20-%20API_AUTH_FRONTEND.md) | `/api/v1/auth` | Fluxo de login, 2FA, refresh token, logout, registro. Proteção de rotas e interceptors HTTP. |
| Documentos | [002 - API_DOCUMENTS_FRONTEND.md](./002%20-%20API_DOCUMENTS_FRONTEND.md) | `/api/v1/documents` | Upload, processamento, status, entries, paginação e isolamento multi-tenant. Documento extenso (~1.650 linhas). |
| Pessoas | [003 - API_PERSONS_FRONTEND.md](./003%20-%20API_PERSONS_FRONTEND.md) | `/api/v1/persons` | Listagem, filtros, documentos por CPF, entries, matriz de rubricas, consolidação e Excel. Documento extenso (~2.270 linhas). |
| Pessoas (CRUD) | [004 - API_PERSONS_CRUD_IMPLEMENTATION.md](./004%20-%20API_PERSONS_CRUD_IMPLEMENTATION.md) | `/api/v1/persons` | Detalhes de implementação backend do CRUD de pessoas. Upload por personId, IR por personId, deleção. |
| Consolidação | [005 - API_CONSOLIDATION_FRONTEND.md](./005%20-%20API_CONSOLIDATION_FRONTEND.md) | `/api/v1/persons/{cpf}/consolidated` | Matriz consolidada ano/mês por rubrica. Filtros por ano/origem, comparação com endpoint `/rubricas`. |
| Rubricas | [006 - API_RUBRICAS_FRONTEND.md](./006%20-%20API_RUBRICAS_FRONTEND.md) | `/api/v1/rubricas` | Gestão de rubricas: listar, criar, editar, desativar. Rubricas globais vs por tenant. Permissões. |
| Usuários | [007 - API_USERS_FRONTEND.md](./007%20-%20API_USERS_FRONTEND.md) | `/api/v1/users` | CRUD de usuários: criar, listar, editar, deletar, ativar/desativar, trocar senha. Matriz de permissões. |
| Tenants | [008 - API_TENANTS_FRONTEND.md](./008%20-%20API_TENANTS_FRONTEND.md) | `/api/v1/tenants` | Gestão de tenants (apenas SUPER_ADMIN): listar, criar, buscar por ID. |
| Config IA | [009 - API_AI_CONFIG_FRONTEND.md](./009%20-%20API_AI_CONFIG_FRONTEND.md) | `/api/v1/config/ai` | Ativar/desativar Gemini AI, selecionar modelo (Flash/Pro), status. Sugestões de UI. |
| Imposto Renda | [010 - API_INCOME_TAX_FRONTEND.md](./010%20-%20API_INCOME_TAX_FRONTEND.md) | `/api/v1/documents/upload-income-tax` | Upload de declaração IR com processamento automático. Exemplos React/Vue. |
| Sistema | [011 - API_SYSTEM_CLEAN_UPLOADS_FRONTEND.md](./011%20-%20API_SYSTEM_CLEAN_UPLOADS_FRONTEND.md) | `/api/v1/system/clean-uploads` | Limpar todos os uploads (exceto rubricas). Operação irreversível, com confirmação e estatísticas. |
| Secrets | [012 - API_SECRET_GENERATOR_FRONTEND.md](./012%20-%20API_SECRET_GENERATOR_FRONTEND.md) | `/system/secrets` | Gerador de chaves criptográficas fortes (JWT, API keys, tokens). Presets e customização. |
| **Processing Log** | [013 - API_PROCESSING_LOG_FRONTEND.md](./013%20-%20API_PROCESSING_LOG_FRONTEND.md) | `/api/v1/documents/{id}` | **Log de processamento / auditoria.** Timeline de eventos por página: dados extraídos pela IA, validações, cross-validation, escalação, rubricas não cadastradas. |

---

## Ordem de integração sugerida

```
1. Autenticação (Auth)     → Primeiro: proteger rotas e obter tokens
2. Documentos              → Upload e processamento de PDFs
3. Pessoas                 → Listar pessoas e seus documentos
4. Consolidação            → Visualizar matriz consolidada
5. Rubricas                → Gestão de rubricas (admin)
6. Usuários / Tenants      → Gestão de usuários e empresas (admin)
7. Config IA               → Configuração do Gemini (admin)
8. Imposto de Renda        → Upload de declarações IR
9. Sistema (Clean)         → Operações de manutenção (admin)
10. Gerador de Secrets     → Gerar chaves fortes (admin)
```

## Padrões comuns nos documentos

- **Interfaces TypeScript** para request/response
- **Hooks React** prontos para uso (`useDocuments`, `usePersons`, etc.)
- **Tratamento de erros** com códigos HTTP e mensagens
- **Exemplos cURL** para testes rápidos
- **Paginação** com `page`, `size`, `totalPages`, `totalElements`

---

[← Voltar ao índice](../README.md)
