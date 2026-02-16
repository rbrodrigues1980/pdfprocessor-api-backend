# 08 — Inteligência Artificial

> Integração com Google Gemini AI 2.5 para extração inteligente de PDFs.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - API_GEMINI_AI.md](./001%20-%20API_GEMINI_AI.md) | **Documentação técnica completa (~876 linhas).** Setup GCP, configuração do SDK, estratégia Flash/Pro com fallback, prompts de extração, estimativa de custos, deploy no Cloud Run e troubleshooting. Inclui notas de migração do SDK. |
| [002 - PLANO_UPGRADE_GEMINI_AI.md](./002%20-%20PLANO_UPGRADE_GEMINI_AI.md) | **Plano de upgrade (~1.297 linhas).** Migração do Gemini 1.5 Flash 002 para 2.5 Flash/Pro em 4 fases: upgrade do modelo, validação de regras de negócio, cross-validation com dupla extração, e escalação automática para Pro. Registros de implementação e resultados de testes. Todas as fases concluídas (02/2026). |

---

## Arquitetura da IA

```
PDF Upload → Gemini Flash (rápido/barato)
                  ↓
           Validação de regras
                  ↓
         Cross-validation OK? ──→ Sim: Salvar resultado
                  ↓ Não
         Escalação → Gemini Pro (preciso/caro)
                  ↓
           Salvar resultado final
```

## Ordem de leitura sugerida

1. `001 - API_GEMINI_AI.md` — Entenda a integração técnica e configuração
2. `002 - PLANO_UPGRADE_GEMINI_AI.md` — Decisões, fases e resultados do upgrade

---

[← Voltar ao índice](../README.md)
