# 03 — Banco de Dados

> Modelagem MongoDB, dicionário de dados, ambiente de homologação e troubleshooting de conexão.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - MODELAGEM_MONGO.md](./001%20-%20MODELAGEM_MONGO.md) | **Referência principal de banco de dados.** Modelagem completa com collections (persons, payroll_documents, payroll_entries, rubricas, logs_extracao, GridFS), schemas, dicionário de dados, relacionamentos, índices recomendados, regras de consistência e JSON completo das 24 rubricas. |
| [002 - DATA_DICTIONARY.md](./002%20-%20DATA_DICTIONARY.md) | Dicionário de dados conciso: collections, tipos de campos, campos obrigatórios, descrições e relacionamentos. Inclui diagrama ERD Mermaid. Versão resumida da modelagem. |
| [003 - BANCO_DADOS_HML.md](./003%20-%20BANCO_DADOS_HML.md) | Configuração do banco de homologação (`pdfprocessor-hml`) separado da produção. Scripts para clonar collections e índices, regras de configuração `.env` vs `.env.example`, e diretrizes de separação de ambientes. |
| [004 - MONGO_CONNECTION_TROUBLESHOOTING.md](./004%20-%20MONGO_CONNECTION_TROUBLESHOOTING.md) | Guia de troubleshooting para erros de conexão MongoDB. Explica warnings normais durante manutenção do connection pool, soluções aplicadas (timeouts, idle time, heartbeat, retry), e dicas de monitoramento. |

---

## Ordem de leitura sugerida

1. `001 - MODELAGEM_MONGO.md` — Referência completa da modelagem (leitura principal)
2. `002 - DATA_DICTIONARY.md` — Consulta rápida de campos e tipos
3. `003 - BANCO_DADOS_HML.md` — Quando precisar configurar ambiente de homologação
4. `004 - MONGO_CONNECTION_TROUBLESHOOTING.md` — Quando encontrar problemas de conexão

---

[← Voltar ao índice](../README.md)
