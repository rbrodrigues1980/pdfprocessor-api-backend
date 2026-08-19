# 008 — Planos executados (PDF Processor)

> Catálogo dos planos Cursor implementados no monorepo **pdfprocessor** (backend + frontend).
> Planos de outros projetos (Portal LBS, Web Notepad, etc.) **não** entram aqui.
>
> Convenção de documentação do produto: pastas `docs/NN-tema/`, arquivos `NNN - NOME.md`,
> atualizar o `README.md` da seção ao criar documento novo.

**Última atualização:** Agosto 2026

---

## Como documentar após um plano

1. Escolher a pasta correta (`04-api-referencia`, `07-imposto-renda`, `02-arquitetura`, `06-guia-frontend`, …).
2. Criar ou atualizar o `.md` com o próximo número da pasta.
3. Incluir no mínimo: **visão geral**, **regras de negócio**, **classes/endpoints**, **fora de escopo**, **testes de regressão**.
4. Atualizar o `README.md` da seção (e o índice master `docs/README.md` se o contador mudar).
5. Registrar o plano nesta tabela com link para o doc operacional.

Fontes dos planos: `C:\Users\roger\.cursor\plans\*.plan.md` (status `todos` = completed).

---

## Onda SABESP / SABESPREV (jul–ago 2026)

Entregas recentes na ordem de implementação. Arquivos de plano em `%USERPROFILE%\.cursor\plans\`.

| # | Plano (arquivo) | Status | O que foi entregue | Doc operacional |
|---|-----------------|--------|--------------------|-----------------|
| 1 | `contracheque_sabesp_192c81a5.plan.md` | ✅ | Tipo `SABESP_HOLERITE`, origem `SABESP`, detecção + parser PDFBox + Excel sem regra FEV/NOV Funcef | [007 - EXTRATOR.md](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 · [008 Excel](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) § Aba consolidação |
| 2 | `sabesp_simulação_e_cpf_0305679a.plan.md` | ✅ | CPF opcional no holerite; `prevCompl` inclui códigos SABESP (ex. 3347/3349) na simulação IR; **sem seed** de rubricas | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 · motor / Excel prevCompl |
| 3 | *(follow-up no chat)* Parser SABESP genérico | ✅ | Removidos regex fixos `3347\|3349`; validação via Mongo `RubricaValidator`; removidos `SabespRubricaInitializer` / seed vazio | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 |
| 4 | *(follow-up)* Matrícula SABESP | ✅ | Normalização de matrícula **7–9** dígitos (`MatriculaNormalizer`) | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 |
| 5 | `ficha_financeira_sabesprev_d0e39e56.plan.md` | ✅ | `DocumentType.SABESPREV_FICHA`, origem `SABESPREV`, detecção antes do holerite SABESP, metadata + parser matriz PDFBox (código+P/D no fim da linha), meses 11+TOTAL+DEZ; rubricas só via UI | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 |
| 6 | `sabesprev_totais_excel_fbd29265.plan.md` | ✅ | Rodapé Excel por ano: CONTRIBUIÇÃO − DEVOLUÇÃO = TOTAL; detecção **por ano** (`SabesprevFichaTotaisHelper`) quando o cliente mistura SABESP + ficha | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 · [008 Excel](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) |
| 7 | Totais SABESPREV lista fechada | ✅ | Rodapé deixa de usar prefixos `7*`/`9*`: **soma** `7400`/`7401`/`7402`/`9105`/`9106`; **subtrai** `7404`/`9100`/`9102`/`9112`/`9115`; demais códigos só na matriz | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 · [008 Excel](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) |

**Decisões de produto nesta onda**

- Rubricas SABESP/SABESPREV: **cadastro manual na UI** — nenhum seed/initializer por empresa.
- Cliente misto (holerite SABESP + ficha SABESPREV): origem global pode ser `null`; totais da ficha usam heurística **só códigos 7\*/9\*** no ano.

---

## Excel / Resumo Geral / Simulação IRPF

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Aba Resumo Geral (`aba_resumo_geral_221b6632`) | ✅ | [008 - API_6_EXCEL_EXPORT.md](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) §12–14 |
| Coluna C / impacto financeiro (`coluna_c_resumo_geral_809a23dd`) | ✅ | mesmo doc §12 (regra C/D) |
| Resumo Geral impacto financeiro (`resumo_geral_impacto_financeiro_e953f4b6`) | ✅ | mesmo doc §12 |
| Resumo Geral Excel (`resumo_geral_excel_04f5a619`) | ✅ | mesmo doc §12 (Tema 1.224, cores, rodapé) |
| Modal Resumo Geral (`modal_resumo_geral_cca08d28`) | ✅ | mesmo doc §13–14 |
| Gestão Empresas Honorários (`gestão_empresas_honorários_b310537c`) | ✅ | mesmo doc §12 (percentual); CRUD empresas: ver backlog se API dedicada |
| Dupla simulação Excel IRPF (`dupla_simulação_excel_irpf_c8c1afe8`) | ✅ | [008](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) + [005 MOTOR](../07-imposto-renda/005%20-%20MOTOR_CALCULO_SIMULADOR_IRPF.md) |
| Refatorar simulação Excel IRPF (`refatorar_simulação_excel_irpf_c36f69d0`) | ✅ | mesmo |
| Bloco 1 Declaração Completa (`bloco_1_declaração_completa_1ba5afe8`) | ✅ | mesmo |
| Corrigir Imposto Devido II (`corrigir_imposto_devido_ii_0e75c606`) | ✅ | motor / layout Excel |
| INSS Doméstico Dedução (`inss_doméstico_deducao_19d76031`) | ✅ | motor |
| Prev compl extras DIRPF (`prev_compl_extras_dirpf_4f6a46c7`) | ✅ | Excel + `PrevComplPlanilhaHelper` |
| Relatório Excel clientes (`relatório_excel_clientes_45f35d5c`) | ✅ | API persons / relatório em lote |

## Informe de Rendimentos / depósito judicial

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Informe Rendimentos Funcef (`informe_rendimentos_funcef_954430b4`) | ✅ | [011 - API_INFORME_RENDIMENTOS.md](../04-api-referencia/011%20-%20API_INFORME_RENDIMENTOS.md) |
| Depósito Judicial Simulação (`depósito_judicial_simulação_b2404ea8`) | ✅ | [011](../04-api-referencia/011%20-%20API_INFORME_RENDIMENTOS.md) §5 + [008](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) §15 |

## Contracheques / Funcef / SABESP / SABESPREV

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Funcef portal agrupado (`funcef_portal_agrupado_7313543f`) | ✅ | [007 - EXTRATOR.md](../02-arquitetura/007%20-%20EXTRATOR.md) §5.3 |
| Contracheque SABESP | ✅ | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 |
| SABESP simulação + CPF | ✅ | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.4 |
| Ficha Financeira SABESPREV | ✅ | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 |
| Totais SABESPREV no Excel | ✅ | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 · [008 Excel](../04-api-referencia/008%20-%20API_6_EXCEL_EXPORT.md) |
| Totais SABESPREV lista fechada (soma/subtrai) | ✅ | [007](../02-arquitetura/007%20-%20EXTRATOR.md) §5.5 |
| Fix multi-page PDF extraction | ✅ | Extrator / Gemini (docs 02 e 08) |

## IRPF — extração e motor

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Revisão Simulador IRPF (`revisão_simulador_irpf_4da8c92c`) | ✅ | [005 - MOTOR_CALCULO…](../07-imposto-renda/005%20-%20MOTOR_CALCULO_SIMULADOR_IRPF.md) |
| Motor IRPF + Itens 7–10 (`simulador_itens_7-10_7811748c`) | ✅ | mesmo |
| Precisão RF IRPF (`precisão_rf_irpf_50901d6b`) | ✅ | mesmo + iText |
| Códigos Dedução IRPF (`códigos_dedução_irpf_d441dd94`) | ✅ | mesmo |
| Extrair dedução de incentivo (`extrair_deducao_de_incentivo_82b0af55`) | ✅ | iText / RESUMO |
| Salvar Pagamentos Elizete (`salvar_pagamentos_elizete_ecee2ba7`) | ✅ | `pagamentosEfetuados` |
| Fix Imposto RRA (`fix_imposto_rra_9cb7a3c0`) | ✅ | iText / Gemini |
| Fix IRPF dependentes médicas (`fix_irpf_dependentes_médicas_8087eda5`) | ✅ | iText / Gemini |

## Pessoas / clientes / acesso

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Perfil avaliador EVALUATOR (`perfil_avaliador_controle_acesso_25e960fb`) | ✅ | [008 - PERFIL_AVALIADOR…](../05-autenticacao-multi-tenant/008%20-%20PERFIL_AVALIADOR_EVALUATOR.md) |
| Status de clientes (`status_de_clientes_1f0e63af`) | ✅ | Persons API / frontend lista |
| Observações editáveis do cliente (`observações_editáveis_do_cliente_78d9feb4`) | ✅ | Persons + Excel clientes |
| Filtros Clientes (`filtros_clientes_bbd80d56`) | ✅ | Guia Persons |

## Infra / Cloud Run

| Plano | Status | Doc operacional |
|-------|--------|-----------------|
| Fix Direct Memory OOM (`fix_direct_memory_oom_beb9f25e`) | ✅ | [001 - DEPLOY…](../09-deploy/001%20-%20DEPLOY_GOOGLE_CLOUD_RUN.md) |
| Optimize Cloud Run Logging | ✅ | Deploy / ops |
| Processing Realtime Feedback | ✅ | [013 - API_PROCESSING_LOG…](../06-guia-frontend/013%20-%20API_PROCESSING_LOG_FRONTEND.md) |
| Dashboard Insights | ✅ | APIs analytics (se expostas) |

## Planos históricos / não operacionais

| Documento | Nota |
|-----------|------|
| [005 - PLANO_AUTOMACAO_IRPF.md](./005%20-%20PLANO_AUTOMACAO_IRPF.md) | **Histórico** — esboço Kotlin/Gemini; o MVP entregue é Java regex em `011 - API_INFORME_RENDIMENTOS.md` |

---

## Fora deste catálogo

Planos em `.cursor/plans` de **Portal LBS** (advogados, contratos, e-mail, Azure Blob MySQL), **Web Notepad** e stubs/duplicatas (`*_ae89357a`, `*_e8887aba`, `*_ce3b45a0`, `*_629bf77e` aberto vs `*_8087eda5` done) não são documentados neste repositório.

---

[← Voltar ao índice da seção](./README.md)
