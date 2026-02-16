# 04 — API — Referência

> Documentação detalhada dos endpoints core do sistema, organizados por funcionalidade.
> Cada API inclui: endpoint, objetivo, request/response, regras de negócio e exemplos.

---

## Documentos nesta seção

| # | Documento | Endpoint Principal | Descrição |
|---|-----------|-------------------|-----------|
| — | [001 - API_SPEC.md](./001%20-%20API_SPEC.md) | Todos | Especificação resumida de todos os endpoints do sistema. Visão rápida e compacta. |
| — | [002 - MASTER_BY_API.md](./002%20-%20MASTER_BY_API.md) | Todos | Documentação consolidada por API com ordem de implementação recomendada. |
| 1 | [003 - API_1_RUBRICAS.md](./003%20-%20API_1_RUBRICAS.md) | `/api/v1/rubricas` | CRUD de rubricas (24 iniciais). Modelo MongoDB, endpoints POST/GET/PUT/DELETE, lista completa em JSON. Base para extração e consolidação. |
| 2 | [004 - API_2_UPLOAD.md](./004%20-%20API_2_UPLOAD.md) | `/api/v1/documents/upload` | Upload de PDFs com validação de tipo/tamanho/integridade. Detecta CAIXA/FUNCEF/MISTO, previne duplicidade via SHA-256. |
| 3 | [005 - API_3_PROCESS_DOCUMENT.md](./005%20-%20API_3_PROCESS_DOCUMENT.md) | `/api/v1/documents/{id}/process` | Processamento assíncrono de PDFs. Extração página por página com regex CAIXA/FUNCEF, normalização e validação. **Documento mais detalhado (~1.150 linhas).** |
| 4 | [006 - API_4_ENTRIES.md](./006%20-%20API_4_ENTRIES.md) | `/api/v1/entries` | Consulta de rubricas extraídas (payroll entries). Filtros por documento, CPF, rubrica, ano, mês, origem e valores. Paginação. |
| 5 | [007 - API_5_CONSOLIDATED.md](./007%20-%20API_5_CONSOLIDATED.md) | `/api/v1/consolidated` | Consolidação por pessoa: transforma entries em matriz ano/mês por rubrica. Calcula totais. Formato para Excel. |
| 6 | [008 - API_6_EXCEL_EXPORT.md](./008%20-%20API_6_EXCEL_EXPORT.md) | `/api/v1/excel/export` | Exportação Excel com Apache POI. Três abas: Consolidação, Totais Mensais, Metadados. Formatação e estilos. |
| 7 | [009 - API_7_DOCUMENTS_QUERY.md](./009%20-%20API_7_DOCUMENTS_QUERY.md) | `/api/v1/documents` | Consulta e gestão de documentos. Status (PENDING/PROCESSING/PROCESSED/ERROR), resumo de rubricas, reprocessamento. |
| — | [010 - MATRIZ_EXCEL_EXEMPLO.md](./010%20-%20MATRIZ_EXCEL_EXEMPLO.md) | — | Exemplo da estrutura da matriz Excel consolidada: 39 colunas, 18 linhas de rubricas, mapeamento JSON → planilha. |

---

## Fluxo de Processamento

```
Upload (API 2) → Processamento (API 3) → Consulta Entries (API 4)
                                              ↓
                                      Consolidação (API 5) → Excel (API 6)
```

## Ordem de leitura sugerida

1. `001 - API_SPEC.md` — Visão geral rápida dos endpoints
2. `003` → `009` na ordem numérica (APIs 1-7, seguem o fluxo do sistema)
3. `010 - MATRIZ_EXCEL_EXEMPLO.md` — Para entender o formato final do Excel

---

[← Voltar ao índice](../README.md)
