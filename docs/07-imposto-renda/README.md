# 07 — Imposto de Renda

> APIs de tributação IRPF, Taxa SELIC, extração de declarações IR com iText 8 e catálogo de rubricas IR.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - INCOME_TAX_RUBRICAS.md](./001%20-%20INCOME_TAX_RUBRICAS.md) | Catálogo das 37 rubricas extraídas de declarações IR, organizadas por categoria (dados básicos, imposto devido, deduções, etc.). Diferenças entre formatos 2016 e 2017+. |
| [002 - API_INCOMETAX_ITEXT8.md](./002%20-%20API_INCOMETAX_ITEXT8.md) | Extração de declarações de IR usando iText 8. Endpoints: upload com persistência, extração pura, texto raw e modo debug. Lista completa das 37 rubricas extraídas. |
| [003 - API_TRIBUTACAO_IRPF.md](./003%20-%20API_TRIBUTACAO_IRPF.md) | API de tabelas de tributação IRPF: alíquotas anuais, mensais e PLR (2016-2026). Endpoints para consulta por ano, fórmula de cálculo e estrutura dos DTOs. |
| [004 - API_TAXA_SELIC.md](./004%20-%20API_TAXA_SELIC.md) | API da Taxa SELIC do Banco Central. Endpoints: taxa atual, por data/ano, histórico, acumulada e formato Receita Federal. Sincronização diária automática com dados do BCB. |
| [005 - MOTOR_CALCULO_SIMULADOR_IRPF.md](./005%20-%20MOTOR_CALCULO_SIMULADOR_IRPF.md) | **Motor de cálculo IRPF** (Completo vs Simplificado), redução anual 2026, itens 7–10 da declaração, API `POST /tributacao/simular` e regras de negócio completas. |

---

## Ordem de leitura sugerida

1. `001 - INCOME_TAX_RUBRICAS.md` — Entenda quais dados são extraídos das declarações
2. `002 - API_INCOMETAX_ITEXT8.md` — Como a extração funciona tecnicamente
3. `003 - API_TRIBUTACAO_IRPF.md` — Tabelas de alíquotas para cálculos
4. `005 - MOTOR_CALCULO_SIMULADOR_IRPF.md` — Regras do simulador e motor Completo/Simplificado
5. `004 - API_TAXA_SELIC.md` — Taxas SELIC para correção monetária

---

[← Voltar ao índice](../README.md)
