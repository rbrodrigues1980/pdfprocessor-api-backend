# Mapeamento de Extração — Declaração IRPF

> Referência: `DeclaraçãodeImpostodeRenda_CAIXA_2017.pdf` (Exercício 2017 / Ano-Calendário 2016)

---

## Legenda

| Símbolo | Significado |
|---------|-------------|
| ✅ | Extraído e salvo |
| ❌ | Presente no PDF, **não** salvo |
| ➖ | Sem Informações no PDF (não se aplica) |

---

## 1. Identificação do Contribuinte (Página 1)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Nome | `nomeTitular` | ✅ |
| CPF | `cpfTitular` | ✅ |
| Exercício | `exercicio` | ✅ |
| Ano-Calendário | `anoCalendario` | ✅ |
| Número de Controle | `controle` | ✅ |
| Data/Hora da Entrega | `dataHoraEntrega` | ✅ |
| Tipo de Declaração | `tipoDeclaracao` | ✅ |
| Tipo de Tributação | `tipoTributacao` | ✅ |
| Data de Nascimento | — | ❌ |
| Título Eleitoral | — | ❌ |
| Endereço completo (logradouro, número, complemento, bairro, município, UF, CEP) | — | ❌ |
| E-mail | — | ❌ |
| DDD/Telefone e DDD/Celular | — | ❌ |
| Natureza da Ocupação | — | ❌ |
| Ocupação Principal | — | ❌ |

---

## 2. Dependentes (Página 1)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Código | `dependentes[].codigo` | ✅ |
| Nome | `dependentes[].nome` | ✅ |
| Data de Nascimento | `dependentes[].dataNascimento` | ✅ |
| CPF | `dependentes[].cpf` | ✅ |
| Total de Dedução com Dependentes | `totalDeducaoDependentes` | ✅ |

---

## 3. Alimentandos (Página 1)

| Campo no PDF | Status |
|---|---|
| Sem Informações → lista vazia | ✅ |

---

## 4. Rendimentos Tributáveis Recebidos de PJ pelo Titular — Fontes Pagadoras (Página 1)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Nome da Fonte Pagadora | `rendimentosFontesTitular[].nome` | ✅ |
| CNPJ/CPF | `rendimentosFontesTitular[].cnpjCpf` | ✅ |
| Rend. Recebidos de Pes. Jurídica | `rendimentosFontesTitular[].rendRecebidosPJ` | ✅ |
| Contr. Previd. Oficial | `rendimentosFontesTitular[].contrPrevOficial` | ✅ |
| Imposto Retido na Fonte | `rendimentosFontesTitular[].impostoRetidoFonte` | ✅ |
| 13º Salário | `rendimentosFontesTitular[].decimoTerceiro` | ✅ |
| IRRF sobre 13º Salário | `rendimentosFontesTitular[].irrfDecimoTerceiro` | ✅ |

> Rendimentos de PJ pelos Dependentes: Sem Informações neste PDF ➖

---

## 5. Rendimentos Isentos e Não Tributáveis (Páginas 2–3)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| **TOTAL** (84.766,70) | `rendimentosIsentos` (via OUTRAS INFORMAÇÕES) | ✅ |
| Linhas individuais (itens 01 a 26) — FGTS, lucros/dividendos, poupança, etc. | — | ❌ |

---

## 6. Rendimentos Sujeitos à Tributação Exclusiva/Definitiva (Página 4)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| **TOTAL** (23.337,57) | `rendimentosTributacaoExclusiva` (via OUTRAS INFORMAÇÕES) | ✅ |
| Linhas individuais (itens 01 a 12) — 13º salário, ganhos de capital, renda variável, etc. | — | ❌ |

---

## 7. Imposto Pago / Retido (Página 4)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Imposto complementar | `impostoComplementar` | ✅ |
| Imposto pago no exterior | `impostoPagoExterior` | ✅ |
| Imposto retido na fonte (Lei 11.033/2004) | `impostoRetidoFonteLei11033` | ✅ |
| Imposto retido na fonte do titular | `impostoRetidoFonteTitular` | ✅ |
| Imposto retido na fonte dos dependentes | `impostoRetidoFonteDependentes` | ✅ |
| Carnê-Leão do titular | `carneLeaoTitular` | ✅ |
| Carnê-Leão dos dependentes | `carneLeaoDependentes` | ✅ |

---

## 8. Pagamentos Efetuados (Página 5)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| CÓD. | `pagamentosEfetuados[].codigo` | ✅ |
| Nome do Beneficiário | `pagamentosEfetuados[].nomeBeneficiario` | ✅ |
| CPF/CNPJ do Beneficiário | `pagamentosEfetuados[].cpfCnpj` | ✅ |
| Valor Pago | `pagamentosEfetuados[].valorPago` | ✅ |
| Parc. Não Dedutível | `pagamentosEfetuados[].parcNaoDedutivel` | ✅ |
| NIT Empregado Doméstico | `pagamentosEfetuados[].nitEmpregadoDomestico` | ✅ |

> **Nota:** o agrupamento por titular/dependente (ex: "Dependente: ELDA GRANJEIRO MENDES") não é salvo separadamente — todos os pagamentos ficam em uma lista única. O motor reconstrói o agrupamento por CPF do beneficiário via `IrPagamentosDeducaoAggregator`.

### Mapeamento código → dedução (motor)

| Código | Descrição | Campo simulador |
|--------|-----------|-----------------|
| 01–02 | Instrução | `despesasInstrucao*` (limite por CPF) |
| 09–22, 26 | Saúde | `despesasMedicas` |
| 30–34 | Pensão alimentícia | `pensaoAlimenticia` |
| 36–37 | PGBL / prev. fechada | `previdenciaPrivada` (teto 12%) |
| 50 | INSS patronal doméstico | `previdenciaEmpregadoDomestico` (AC ≤ 2018) |
| 40–43 | Doações incentivo | `deducaoIncentivo` (teto 6% imposto) |
| 44 | PRONON | `dedPronon` (teto 1%) |
| 45 | PRONAS/PCD | `dedPronas` (teto 1%) |

Fonte primária: lista granular. Fallback: totais do RESUMO quando `pagamentosEfetuados` estiver vazio.

---

## 9. Doações Efetuadas (Página ~4–5)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Sem Informações → lista vazia | `doacoesEfetuadas = []` | ✅ |
| Cód. / Nome / CPF-CNPJ / Valor pago | `doacoesEfetuadas[]` | ✅ |
| Layout multilinha (campo por linha) | ✅ | |
| Layout inline SERPRO (ex.: `41 NOME … CNPJ 1.000,00 0,00`) | ✅ | |

> Em declaração **Simplificado**, o RESUMO costuma trazer `deducaoIncentivo = 0`. A simulação Completa usa a lista `doacoesEfetuadas` (cód. 40–43) como fonte primária — por isso a extração inline é necessária.

Fonte primária do motor: lista granular. Fallback: `deducaoIncentivo` do RESUMO.

---

## 10. Declaração de Bens e Direitos (Páginas 5–6)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Linhas individuais (código, discriminação, situação 31/12/2015, situação 31/12/2016) | — | ❌ |
| **Total Bens e Direitos 31/12/2015** | Evolução Patrimonial (RESUMO) | ✅ |
| **Total Bens e Direitos 31/12/2016** | Evolução Patrimonial (RESUMO) | ✅ |

---

## 11. Dívidas e Ônus Reais (Página 7)

| Campo no PDF | Campo no sistema | Status |
|---|---|---|
| Linhas individuais (código, discriminação, situação 2015, situação 2016, valor pago) | — | ❌ |
| **Total Dívidas 31/12/2015** | Evolução Patrimonial (RESUMO) | ✅ |
| **Total Dívidas 31/12/2016** | Evolução Patrimonial (RESUMO) | ✅ |

---

## 12. Outras Seções da Página 7

| Seção | Status |
|---|---|
| Espólio (Sem Informações) | ❌ não extraído |
| Doações a Partidos Políticos (Sem Informações) | ✅ valor salvo via `doacoesPartidosPoliticos` no RESUMO |
| Doações Diretamente na Declaração — ECA (Sem Informações) | ❌ não extraído |

---

## 13. RESUMO — Rendimentos Tributáveis (Página 8)

| Linha no RESUMO | Campo no sistema | Status |
|---|---|---|
| Recebidos de PJ pelo titular | `rendimentosTributaveisTitularPJ` | ✅ |
| Recebidos de PJ pelos dependentes | `rendimentosTributaveisDependentesPJ` | ✅ |
| Recebidos de PF/Exterior pelo titular | `rendimentosTributaveisTitularPF` | ✅ |
| Recebidos de PF/Exterior pelos dependentes | `rendimentosTributaveisDependentesPF` | ✅ |
| Recebidos acumuladamente pelo titular | `rendimentosAcumuladosTitular` | ✅ |
| Recebidos acumuladamente pelos dependentes | `rendimentosAcumuladosDependentes` | ✅ |
| Resultado tributável da Atividade Rural | `resultadoAtividadeRural` | ✅ |
| **TOTAL** | `rendimentosTributaveisTotal` | ✅ |

> **Extração do TOTAL — dois layouts (`extractRendimentosTributaveisBreakdown`):** o TOTAL de rendimentos tributáveis é sempre a soma das 7 sub-linhas. O iText produz dois layouts, ambos tratados (só sobrescreve quando a soma das sub-linhas confere com o TOTAL):
>
> 1. **Agrupado (ex.: 2018+):** todos os rótulos juntos e depois todos os valores em sequência → remapeia por **posição** (7 sub-linhas + TOTAL como último valor). Verificado: AC 2017 → PJ titular 373.152,82, PJ dep 10.560,00, demais 0,00, TOTAL 383.712,82.
> 2. **Linha-a-linha (ex.: declaração simplificada):** cada sub-linha seguida do seu valor; o rótulo **"TOTAL DE RENDIMENTOS TRIBUTÁVEIS"** separa as sub-linhas do total. Corrige casos como Célia AC 2016 (PJ titular 67.963,80 + PF/Exterior 6.120,00 = **TOTAL 74.083,80**), em que o regex simples capturava só a 1ª linha.
>
> Regressão: `ITextIncomeTaxRendimentosBreakdownTest`.

---

## 14. RESUMO — Deduções (Página 8)

| Linha no RESUMO | Campo no sistema | Status |
|---|---|---|
| Contrib. à prev. oficial (até limite do patrocinador) | `deducoesContribPrevOficial` | ✅ |
| Contrib. à prev. oficial (Rendimentos acumulados) | — | ❌ |
| Contrib. à prev. complementar / Fapi | `deducoesContribPrevCompl` | ✅ |
| Dependentes | `deducoesDependentes` | ✅ |
| Despesas com instrução | `deducoesInstrucao` | ✅ |
| Despesas médicas | `deducoesMedicas` | ✅ |
| Pensão alimentícia judicial | `deducoesPensaoJudicial` | ✅ |
| Pensão alimentícia por escritura pública | `deducoesPensaoEscritura` | ✅ |
| Pensão alimentícia judicial (Rendimentos acumulados) | — | ❌ |
| Livro caixa | `deducoesLivroCaixa` | ✅ |
| **TOTAL** | `deducoesTotal` | ✅ |

---

## 15. RESUMO — Imposto Devido (Página 8)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Base de cálculo do imposto | `baseCalculoImposto` | ✅ |
| Imposto devido | `impostoDevido` | ✅ |
| Dedução de incentivo | `deducaoIncentivo` | ✅ |
| Imposto devido I | `impostoDevidoI` | ✅ |
| Contrib. Prev. Empregador Doméstico | `contribuicaoPrevEmpregadorDomestico` | ✅ |
| Imposto devido II | `impostoDevidoII` | ✅ |
| Imposto devido RRA | `impostoDevidoRRA` | ✅ |
| Total do imposto devido | `totalImpostoDevido` | ✅ |

> **Dedução de incentivo em layout embaralhado (`corrigirCamposImpostoDevidoResumo`):** em alguns PDFs (duas colunas) o iText emite os rótulos e valores do bloco IMPOSTO DEVIDO fora de ordem, e o regex por rótulo captura `Dedução de incentivo = 0,00` e `Imposto devido I` incorreto. Quando `impostoDevido` e `totalImpostoDevido` são confiáveis e há redução, os valores são **derivados aritmeticamente** (sem INSS doméstico): `impostoDevidoI = totalImpostoDevido − impostoDevidoRRA` e `deducaoIncentivo = impostoDevido − impostoDevidoI`. A dedução de incentivo corresponde às doações diretas na declaração (ECA/Idoso), que abatem o total do imposto devido. Ex.: Adriana AC 2021 → imposto devido 67.343,04, dedução de incentivo 4.040,58 (2×2.020,29), imposto devido I / total 63.302,46. Regressão: `ITextIncomeTaxDeducaoIncentivoTest`.

---

## 16. RESUMO — Imposto Pago (Página 8)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Imposto retido na fonte do titular | `impostoRetidoFonteTitular` | ✅ |
| Imp. retido na fonte dos dependentes | `impostoRetidoFonteDependentes` | ✅ |
| Carnê-Leão do titular | `carneLeaoTitular` | ✅ |
| Carnê-Leão dos dependentes | `carneLeaoDependentes` | ✅ |
| Imposto complementar | `impostoComplementar` | ✅ |
| Imposto pago no exterior | `impostoPagoExterior` | ✅ |
| Imposto retido na fonte (Lei 11.033/2004) | `impostoRetidoFonteLei11033` | ✅ |
| Imposto retido RRA | `impostoRetidoRRA` | ✅ |
| **Total do imposto pago** | `impostoPagoTotal` | ✅ |

---

## 17. RESUMO — Resultado Final (Página 8)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Imposto a restituir | `impostoRestituir` | ✅ |
| Saldo de imposto a pagar | `saldoImpostoPagar` | ✅ |
| Alíquota efetiva | `aliquotaEfetiva` | ✅ |
| Desconto simplificado | `descontoSimplificado` | ✅ |

---

## 18. RESUMO — Parcelamento (Página 8)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Valor da quota | — | ❌ |
| Número de quotas | — | ❌ |
| Data de vencimento da 1ª quota | — | ❌ |

---

## 19. RESUMO — Informações Bancárias (Página 8)

| Campo | Status |
|---|---|
| Banco | ❌ |
| Agência | ❌ |
| Conta para débito | ❌ |

---

## 20. RESUMO — Evolução Patrimonial (Página 9)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Bens e direitos em 31/12/2015 | campo evolução patrimonial | ✅ |
| Bens e direitos em 31/12/2016 | campo evolução patrimonial | ✅ |
| Dívidas e ônus reais em 31/12/2015 | campo evolução patrimonial | ✅ |
| Dívidas e ônus reais em 31/12/2016 | campo evolução patrimonial | ✅ |

---

## 21. RESUMO — Outras Informações (Página 9)

| Campo no RESUMO | Campo no sistema | Status |
|---|---|---|
| Rendimentos isentos e não tributáveis | `rendimentosIsentos` | ✅ |
| Rendimentos sujeitos à tributação exclusiva/definitiva | `rendimentosTributacaoExclusiva` | ✅ |
| Rendimentos tributáveis — imposto com exigibilidade suspensa | `rendimentosTributaveisExigSuspensa` | ✅ |
| Depósitos judiciais do imposto | `depositosJudiciais` | ✅ |
| Imposto pago sobre Ganhos de Capital | `impostoPagoGanhosCapital` | ✅ |
| Imposto pago Ganhos de Capital Moeda Estrangeira | `impostoPagoGanhosCapitalMoedaEstrangeira` | ✅ |
| Total imposto retido na fonte (Lei 11.033/2004) | `impostoRetidoFonteLei11033` | ✅ |
| Imposto pago sobre Renda Variável | `impostoPagoRendaVariavel` | ✅ |
| Doações a Partidos Políticos e Candidatos | `doacoesPartidosPoliticos` | ✅ |
| Imposto a pagar sobre Ganho de Capital — Moeda Estrangeira em Espécie | `impostoAPagarGanhosCapitalMoedaEstrangeira` | ✅ |
| Imposto diferido dos Ganhos de Capital | `impostoDiferidoGanhosCapital` | ✅ |
| Imposto devido sobre Ganhos de Capital | `impostoDevidoGanhosCapital` | ✅ |
| Imposto devido sobre ganhos líquidos em Renda Variável | `impostoDevidoGanhosLiquidosRendaVariavel` | ✅ |
| Imposto devido sobre Ganhos de Capital Moeda Estrangeira | `impostoDevidoGanhosCapitalMoedaEstrangeira` | ✅ |

---

## 22. Recibo de Entrega (Páginas 10–11)

| Campo | Status |
|---|---|
| Número do recibo (ex: 34.76.97.04.53-43) | ❌ |
| Demais dados do recibo | ❌ |

---

## Resumo dos Gaps

| Categoria | Campos não salvos |
|---|---|
| **Identificação** | Data nasc., título eleitoral, endereço, e-mail, telefone, ocupação |
| **Rendimentos isentos** | 26 linhas individuais (só o total é salvo) |
| **Tributação exclusiva** | 12 linhas individuais (só o total é salvo) |
| **Bens e direitos** | Lista detalhada com discriminação e valores por bem (só totais) |
| **Dívidas e ônus** | Lista detalhada (só totais) |
| **Parcelamento** | Valor da quota, nº de quotas, vencimento |
| **Informações bancárias** | Banco, agência, conta |
| **Doações ECA** | Seção inteira não extraída |
| **Recibo de entrega** | Número do recibo |
| **Deduções (sub-linhas)** | Contrib. prev. acumulados, pensão alimentícia acumulados |
| **Agrupamento de pagamentos** | Titular vs. dependente dentro da seção PAGAMENTOS EFETUADOS |