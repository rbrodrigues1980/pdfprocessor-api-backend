# 11 — Troubleshooting

> Correções aplicadas, problemas conhecidos e decisões técnicas de manutenção.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - CORRECAO_SALVAMENTO_DADOS.md](./001%20-%20CORRECAO_SALVAMENTO_DADOS.md) | **Correção de bug:** dados de Person (nome, CPF, matrícula) eram salvos duplicadamente durante upload E processamento. Corrigido para salvar apenas no upload via `ensurePersonExists()`. |
| [002 - COMO_DADOS_SAO_SALVOS.md](./002%20-%20COMO_DADOS_SAO_SALVOS.md) | Explica como dados de Person são normalizados: CPF com 11 dígitos, nome em UPPERCASE, matrícula com 7 dígitos. Nota: MongoDB não exibe campos null no JSON (comportamento esperado). |
| [003 - DIRETORIOS_VAZIOS_REMOVIDOS.md](./003%20-%20DIRETORIOS_VAZIOS_REMOVIDOS.md) | Lista de 11 diretórios vazios removidos para alinhar com Clean Architecture. Documenta a estrutura atual (domain, application, infrastructure, interfaces) e motivo de cada remoção. |

---

## Problemas comuns

| Problema | Documento | Seção |
|----------|-----------|-------|
| Erro de conexão MongoDB | [Banco de Dados](../03-banco-de-dados/004%20-%20MONGO_CONNECTION_TROUBLESHOOTING.md) | 03 |
| Person com dados duplicados | [001 - CORRECAO_SALVAMENTO_DADOS.md](./001%20-%20CORRECAO_SALVAMENTO_DADOS.md) | 11 |
| Campos null não aparecem no MongoDB | [002 - COMO_DADOS_SAO_SALVOS.md](./002%20-%20COMO_DADOS_SAO_SALVOS.md) | 11 |
| Diretórios vazios no projeto | [003 - DIRETORIOS_VAZIOS_REMOVIDOS.md](./003%20-%20DIRETORIOS_VAZIOS_REMOVIDOS.md) | 11 |

---

[← Voltar ao índice](../README.md)
