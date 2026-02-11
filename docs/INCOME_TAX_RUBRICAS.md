# Documentação de Rubricas - Declaração de Imposto de Renda

Este documento lista todas as rubricas extraídas das declarações de Imposto de Renda (IRPF).

## Visão Geral

O sistema extrai informações da página RESUMO das declarações de IR e persiste como `PayrollEntry` no banco de dados.
Cada rubrica possui um código único (`rubricaCodigo`) e uma descrição (`rubricaNome`).

**Formatos Suportados:**
- Declarações de 2016 (formato com duas colunas)
- Declarações de 2017+ (formato com coluna única)

---

## Dados Básicos (4 rubricas)

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 1 | `IR_NOME` | Nome do contribuinte | String |
| 2 | `IR_CPF` | CPF do contribuinte | String |
| 3 | `IR_EXERCICIO` | Exercício fiscal (ex: 2018) | String |
| 4 | `IR_ANO_CALENDARIO` | Ano-calendário (ex: 2017) | String |

---

## IMPOSTO DEVIDO (9 rubricas)

Seção que contém os cálculos do imposto devido.

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 5 | `IR_BASE_CALCULO_IMPOSTO` | Base de cálculo do imposto | BigDecimal |
| 6 | `IR_IMPOSTO_DEVIDO` | Imposto devido | BigDecimal |
| 7 | `IR_DEDUCAO_INCENTIVO` | Dedução de incentivo | BigDecimal |
| 8 | `IR_IMPOSTO_DEVIDO_I` | Imposto devido I | BigDecimal |
| 9 | `IR_CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO` | Contribuição Prev. Empregador Doméstico | BigDecimal |
| 10 | `IR_IMPOSTO_DEVIDO_II` | Imposto devido II | BigDecimal |
| 11 | `IR_IMPOSTO_DEVIDO_RRA` | Imposto devido RRA | BigDecimal |
| 12 | `IR_TOTAL_IMPOSTO_DEVIDO` | Total do imposto devido | BigDecimal |
| 13 | `IR_SALDO_IMPOSTO_A_PAGAR` | Saldo de imposto a pagar | BigDecimal |

> **Nota:** Os campos 7-10 podem ser `null` em declarações com desconto simplificado (2017+).

---

## Rendimentos e Deduções Gerais (3 rubricas)

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 14 | `IR_RENDIMENTOS_TRIBUTAVEIS` | Rendimentos Tributáveis (Total) | BigDecimal |
| 15 | `IR_DEDUCOES` | Deduções Totais | BigDecimal |
| 16 | `IR_IMPOSTO_RESTITUIR` | Imposto a Restituir | BigDecimal |

---

## DEDUÇÕES Individuais (10 rubricas)

Seção detalhada com cada tipo de dedução. Disponível apenas em declarações completas (não simplificadas).

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 17 | `IR_DEDUCOES_CONTRIB_PREV_OFICIAL` | Contribuição à previdência oficial e complementar pública (limite patrocinador) | BigDecimal |
| 18 | `IR_DEDUCOES_CONTRIB_PREV_RRA` | Contribuição à previdência oficial (RRA) | BigDecimal |
| 19 | `IR_DEDUCOES_CONTRIB_PREV_COMPL` | Contribuição à previdência complementar/privada/Fapi | BigDecimal |
| 20 | `IR_DEDUCOES_DEPENDENTES` | Dependentes | BigDecimal |
| 21 | `IR_DEDUCOES_INSTRUCAO` | Despesas com instrução | BigDecimal |
| 22 | `IR_DEDUCOES_MEDICAS` | Despesas médicas | BigDecimal |
| 23 | `IR_DEDUCOES_PENSAO_JUDICIAL` | Pensão alimentícia judicial | BigDecimal |
| 24 | `IR_DEDUCOES_PENSAO_ESCRITURA` | Pensão alimentícia por escritura pública | BigDecimal |
| 25 | `IR_DEDUCOES_PENSAO_RRA` | Pensão alimentícia judicial (RRA) | BigDecimal |
| 26 | `IR_DEDUCOES_LIVRO_CAIXA` | Livro caixa | BigDecimal |

> **Nota:** Estes campos serão `null` em declarações com desconto simplificado.

---

## IMPOSTO PAGO (9 rubricas)

Seção que contém os impostos já pagos/retidos.

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 27 | `IR_IMPOSTO_RETIDO_FONTE` | Imposto retido na fonte do titular | BigDecimal |
| 28 | `IR_IMPOSTO_PAGO_TOTAL` | Total do imposto pago | BigDecimal |
| 29 | `IR_IMPOSTO_RETIDO_FONTE_DEPENDENTES` | Imp. retido na fonte dos dependentes | BigDecimal |
| 30 | `IR_CARNE_LEAO_TITULAR` | Carnê-Leão do titular | BigDecimal |
| 31 | `IR_CARNE_LEAO_DEPENDENTES` | Carnê-Leão dos dependentes | BigDecimal |
| 32 | `IR_IMPOSTO_COMPLEMENTAR` | Imposto complementar | BigDecimal |
| 33 | `IR_IMPOSTO_PAGO_EXTERIOR` | Imposto pago no exterior | BigDecimal |
| 34 | `IR_IMPOSTO_RETIDO_FONTE_LEI_11033` | Imposto retido na fonte (Lei nº 11.033/2004) | BigDecimal |
| 35 | `IR_IMPOSTO_RETIDO_RRA` | Imposto retido RRA | BigDecimal |

---

## Campos Exclusivos 2017+ (2 rubricas)

Campos disponíveis apenas em declarações com desconto simplificado (2017 em diante).

| # | Código | Descrição | Tipo |
|---|--------|-----------|------|
| 36 | `IR_DESCONTO_SIMPLIFICADO` | Desconto Simplificado | BigDecimal |
| 37 | `IR_ALIQUOTA_EFETIVA` | Alíquota efetiva (%) | BigDecimal |

> **Nota:** Estes campos serão `null` em declarações completas (2016 e anteriores).

---

## Resumo

| Categoria | Quantidade |
|-----------|------------|
| Dados Básicos | 4 |
| IMPOSTO DEVIDO | 9 |
| Rendimentos e Deduções Gerais | 3 |
| DEDUÇÕES Individuais | 10 |
| IMPOSTO PAGO | 9 |
| Campos 2017+ | 2 |
| **TOTAL** | **37 rubricas** |

---

## Arquivos Relacionados

- **DTO:** `IncomeTaxDeclarationService.java` → classe interna `IncomeTaxInfo`
- **Extração:** `IncomeTaxDeclarationServiceImpl.java` → padrões regex e método `extractIncomeTaxInfo()`
- **Persistência:** `DocumentProcessUseCase.java` → método que cria as `PayrollEntry`

---

## Diferenças entre Formatos

### Declaração 2016 (Completa)
- Layout com duas colunas
- Seção DEDUÇÕES com itens individuais
- "SALDO **DE** IMPOSTO A PAGAR"
- "RENDIMENTOS TRIBUTÁVEIS ... TOTAL"
- "Imposto pago no exterior **0,00**" (valor após label)
- **Sem:** Desconto Simplificado, Alíquota Efetiva

### Declaração 2017+ (Simplificada)
- Layout com coluna única
- Desconto simplificado (sem DEDUÇÕES individuais)
- "SALDO IMPOSTO A PAGAR" (sem "DE")
- "TOTAL DE RENDIMENTOS TRIBUTÁVEIS"
- "**0,00** Imposto pago no exterior" (valor antes do label)
- **Com:** Desconto Simplificado, Alíquota Efetiva

---

*Última atualização: 2024-12-19*

