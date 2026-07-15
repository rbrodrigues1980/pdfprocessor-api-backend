# API_6_EXCEL_EXPORT.md
# 📗 API 6 — Exportação de Consolidação para Excel (Apache POI)

Esta API gera o **arquivo Excel final**, no mesmo formato da planilha consolidada usada nos relatórios financeiros.  
O Excel contém:

- Matriz ano/mês por rubrica
- Totais mensais
- Total geral
- Formatação idêntica ao modelo fornecido
- Separação por rubricas válidas
- Conversão automática para valores numéricos

---

# 1. ENDPOINT PRINCIPAL

## ▶️ GET /api/v1/persons/{cpf}/excel

### Query Params opcionais

| Param | Exemplo | Descrição |
|--------|----------|------------|
| ano | 2017 | Gera Excel apenas de 1 ano |
| origem | CAIXA | Filtra CAIXA/FUNCEF/MIX |

---

# 2. RESPONSE

### Cabeçalho
```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename=consolidado_{cpf}.xlsx
```

### Corpo
Binário do arquivo Excel.

---

# 3. ESTRUTURA DO ARQUIVO EXCEL

## Aba 1 — Consolidação

| Código | Rubrica | 2017/01 | 2017/02 | ... | 2017/12 | Total |
|--------|---------|----------|----------|------|-----------|--------|
| 4482 | CONTRIB EXTRA 2015 | 424,10 | 885,47 | 0 | ... | 1309,57 |

### Requisitos:

✔ Valores numéricos reais (não texto)  
✔ Totais calculados por fórmula ou preenchidos direto  
✔ Cabeçalho fixo com fundo cinza claro  
✔ Fonte padrão: Arial 10  
✔ Colunas ajustadas automaticamente  
✔ Linha superior congelada (freeze pane)  

---

## Aba 2 — Totais Mensais

| Mês/Ano | Total |
|---------|--------|
| 2017/01 | 700.31 |
| 2017/02 | 3363.20 |
| ... | ... |

---

## Aba 3 — Metadados

```json
{
  "cpf": "12449709568",
  "nome": "FLAVIO JOSE PEREIRA ALMEIDA",
  "anos": ["2016","2017","2018"],
  "origem": "MISTO",
  "geradoEm": "2024-03-10T22:41:10Z"
}
```

---

# 4. PIPELINE DE GERAÇÃO DO EXCEL

```mermaid
flowchart TD
    A[CPF] --> B[Buscar Consolidação via API 5]
    B --> C[Montar Workbook POI]
    C --> D[Criar planilha de matriz]
    D --> E[Preencher cabeçalho]
    E --> F[Iterar rubricas válidas]
    F --> G[Preencher meses 01..12]
    G --> H[Calcular totais]
    H --> I[Criar planilha de totais]
    I --> J[Criar planilha de metadados]
    J --> K[Retornar binário Excel]
```

---

# 5. FORMATAÇÃO OBRIGATÓRIA (Apache POI)

### Estilos:

- **headerStyle**
  - fundo: `GREY_25_PERCENT`
  - negrito
  - borda fina
- **numberStyle**
  - formato: `#,##0.00`
  - alinhamento à direita
- **defaultStyle**
  - fonte Arial 10
- **totalStyle**
  - fundo: `YELLOW`
  - negrito
  - borda dupla

### Celulas mescladas:
- Título principal da planilha (A1:G1)

---

# 6. DADOS OBRIGATÓRIOS

A API 6 usa diretamente o resultado da API 5:

```
ConsolidationResponse
  cpf
  nome
  anos
  meses
  rubricas[]
  totaisMensais{}
  totalGeral
```

---

# 7. LÓGICA DE PREPARAÇÃO DOS DADOS

1. Ordenar rubricas pela ordem da lista oficial (24 rubricas)  
2. Preencher meses faltantes com `0`  
3. Se `ano` for fornecido, descartar todos os demais  
4. Se `origem` for fornecido, somar apenas entries correspondentes  
5. Garantir matriz de:

```
totalRubricas * 12 meses
```

---

# 8. ERROS POSSÍVEIS

| Erro | Status | Descrição |
|------|--------|-----------|
| PERSON_NOT_FOUND | 404 | CPF inexistente |
| NO_ENTRIES_FOUND | 204 | Não há dados |
| ERROR_GENERATING_EXCEL | 500 | Falha no POI |
| INVALID_YEAR | 400 | Ano inválido |

---

# 9. ORDEM DE IMPLEMENTAÇÃO

1. Criar classe `ExcelExportService`
2. Criar builder de workbook POI
3. Implementar estilos (header, number, total)
4. Implementar planilha principal
5. Implementar planilha de totais
6. Implementar planilha de metadados
7. Testar com PDFs reais (multi anos)
8. Testar em arquivos grandes (5+ MB)
9. Criar controller `ExcelController`
10. Gerar download com `Mono<ResponseEntity<ByteArray>>`

---

# 10. CLASSES NECESSÁRIAS

- `ExcelExportService`
- `PoiWorkbookBuilder`
- `ExcelMatrixSheetBuilder`
- `ExcelTotalsSheetBuilder`
- `ExcelMetadataSheetBuilder`
- `ExcelController`

---

# 11. Simulação IRPF na aba anual (dois blocos)

Quando existe declaração IRPF importada para o ano-calendário da aba, `ConsolidationExcelServiceImpl` gera **dois blocos** distintos:

| # | Quando | Título | Conteúdo |
|---|--------|--------|----------|
| 1 | `tipoTributacao = SIMPLIFICADO` | `CONFORME DECLARAÇÃO ENTREGUE…` | **Espelho exato** do RESUMO simplificado (desconto simplificado, imposto devido/pago/resultado) — **sem motor**, valores do PDF |
| 1 | `tipoTributacao = COMPLETO` | `CONFORME DECLARAÇÃO ENTREGUE…` | **Espelho exato** do RESUMO com deduções legais (rendimentos, deduções, imposto devido/pago/resultado) — **sem motor**, valores do PDF |
| 2 | **Sempre** | `SIMULAÇÃO IRPF… COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS` | Modelo **Completo** via `IrSimuladorMotorService` com prev. complementar da **planilha de contracheques** (verde) |

### Bloco 1 — Declaração Simplificada entregue

- Rendimentos tributáveis (7 sub-linhas + total)
- Desconto simplificado, base, imposto devido, RRA, alíquota, total devido
- Imposto pago (8 linhas + total), restituição, saldo a pagar
- Fonte: campos de `IrpfDeclaracaoData` extraídos do PDF (sem recálculo)

### Bloco 1 — Declaração Completa entregue (deduções legais)

- Rendimentos tributáveis (7 sub-linhas + total)
- Deduções (10 linhas do RESUMO + nota 12% + **TOTAL**)
- Imposto devido (base, imposto devido, dedução incentivo, imposto devido I/II, RRA, alíquota, total)
- Imposto pago (8 linhas + total), restituição, saldo a pagar
- Fonte: `ExcelIrpfDeducoesResumoHelper.montarConformeDeclaracao` + campos de `IrpfDeclaracaoData` (sem motor)

### Bloco 2 — Simulação Completa + planilha

- Seção **RENDIMENTOS TRIBUTÁVEIS** (igual ao bloco 1)
- Seção **DEDUÇÕES** com rótulos do RESUMO IRPF (prev. oficial, prev. compl. limitada a 12%, médicas, etc.)
- **INSS doméstico** não entra nas deduções — aplica-se como **crédito** (Imposto devido II = Imposto devido I − INSS). Sem INSS doméstico, Imposto devido II exibe **0,00** no bloco 2 e é omitido no bloco 1; total devido = Imposto devido I + RRA.
- Imposto devido, pago (valores da declaração) e resultado (saldo = total devido − total pago)
- Prev. complementar da planilha destacada em verde

**Prev. complementar (Simulação 2):** `PrevComplPlanilhaHelper.calcularPrevComplSimulacao` = total dos contracheques **+** pagamentos cód. **36/37** da declaração (`pagamentosEfetuados[]`) cujo CNPJ **não** seja patronal (`00.436.923/0001-90` FUNCEF ou `00.360.305/0001-04` CAIXA). Ex.: Margarida AC 2016 — planilha 22.238,28 + CAIXA E VIDA 1.724,48 = **23.962,76** na linha verde. CNPJs patronais já constam nos contracheques e são ignorados na soma extra.

**Deduções:** `ExcelIrpfDeducoesResumoHelper` centraliza linhas do RESUMO; `ExcelIrpfSimulacaoMapper` com `preferirPrevidenciaPlanilha=true`; `IrpfPrevidenciaOficialResolver` soma prev. oficial das fontes pagadoras PJ.

**Motor:** flag `inssDomesticoComoCreditoImposto` em `SimuladorIrpfRequest` para alinhar ao RESUMO da Receita.

**Layout (colunas B:F):** título B:F; rótulos B:E, valor F; totais com fundo amarelo.

Classes envolvidas:

- `ConsolidationExcelServiceImpl` — `addBlocoConformeDeclaracaoSimplificada`, `addBlocoConformeDeclaracaoCompleta`, `addBlocoSimulacaoCompletaPlanilha`
- `ExcelIrpfDeducoesResumoHelper` / `ExcelIrpfDeducoesResumoDTO` — `montarConformeDeclaracao` (espelho) e `montar` (simulação planilha)
- `ExcelIrpfSimulacaoMapper` — mapeamento declaração → request
- `PrevComplPlanilhaHelper` — contracheques + pagamentos cód. 36/37 externos (Simulação 2 / `prevComplPorAno`)
- `ModoSimulacaoExcel` — `ESPELHO_ENTREGUE` | `SIMULACAO_COMPLETA_PLANILHA`
- `IrSimuladorMotorService` — cálculo progressivo + crédito INSS doméstico

Regressão: Elizete AC 2018 — `Elizete2018ExcelSimulacaoLayoutTest`; Elizete AC 2019 — `Elizete2019ExcelSimulacaoLayoutTest`; Elizabeth AC 2016 — `ExcelIrpfSimulacaoTest`; Resumo Geral — `ElizeteResumoGeralTest`; prev. compl. extras — `PrevComplPlanilhaHelperTest` (Margarida AC 2016).

---

# 12. Aba "Resumo Geral"

Quando existem declarações IRPF importadas **cujo ano-calendário coincide com um ano de contracheque processado**, `ConsolidationExcelServiceImpl` gera a aba **"Resumo Geral"** (após as abas anuais, antes de **Consolidação**).

**Regra de alinhamento:** apenas declarações cujo **ano-calendário** está presente na consolidação de contracheques entram no Excel. Ex.: contracheque de 2016 → usa declaração **exercício 2017 / ano-calendário 2016**; declaração **exercício 2016 / ano-calendário 2015** é ignorada se não houver contracheque de 2015.

### Colunas por ano-calendário (somente anos com contracheque e declaração)

| Coluna | Origem | Regra |
|--------|--------|-------|
| A — Calendário | ano da aba | Ano-calendário |
| B — Valores Restituídos / Pagos | Bloco 1 (declaração entregue) | Valor positivo entre `IMPOSTO A RESTITUIR` e `SALDO IMPOSTO A PAGAR` (ignora `0,00`) |
| C — Valor Devido e ou a Restituir | Bloco 2 (simulação planilha) | **Com impacto** → valor simulado (`IMPOSTO A RESTITUIR` ou `SALDO DE IMPOSTO A PAGAR`); **sem impacto** → **repete o valor da declaração (B)** |
| D — Principal PGFN | derivado | **Resultado líquido com sinal** (restituir = `+`, saldo a pagar = `−`): `D = max(simNet − declNet, 0)` |
| E — SELIC Acumulada RFB | `TaxaSelicService.calcularSelicReceitaFederal` | Taxa acumulada (%); só se `D > 0` |
| F — Valor Correção R$ | SELIC | `valorCorrigido − D` |
| G — Principal + Correção | derivado | `D + F` |
| H — Observações | derivado | `"Impacto financeiro"` se `D > 0`; senão `"Sem impacto financeiro - Sistema de tributação"` |

#### Regra de impacto financeiro (colunas C e D)

O **impacto** é o benefício que o contribuinte obteria ao refazer a declaração com o aproveitamento das contribuições. Usa-se o **resultado líquido com sinal**: restituir conta como positivo, saldo a pagar como negativo.

- `declNet = restituirDecl − saldoPagarDecl`
- `simNet  = restituirSim − saldoPagarSim`
- `principal (D) = max(simNet − declNet, 0)`

Só há **"Impacto financeiro"** quando a simulação **melhora** a situação (`D > 0`). Quando a simulação é **igual ou pior**, `D = 0`, a observação é **"Sem impacto financeiro"** e a **coluna C repete o valor da declaração (B)** — o contribuinte manteria o regime originalmente entregue. Regra simétrica para imposto a pagar e a restituir.

| # | Declaração (B) | Simulação (bloco 2) | C | D | Observação |
|---|----------------|---------------------|---|---|------------|
| 1 | Saldo a pagar R$ 1.000 | Saldo a pagar R$ 500 | 500 | 500 | Impacto |
| 2 | Saldo a pagar R$ 1.000 | Saldo a pagar R$ 1.500 | **1.000** (repete B) | 0 | Sem impacto |
| 3 | Saldo a pagar R$ 1.503,80 | Restituir R$ 4.044,88 | 4.044,88 | 5.548,68 | Impacto |
| 4 | Restituir R$ 1.000 | Restituir R$ 1.500 | 1.500 | 500 | Impacto |
| 5 | Restituir R$ 1.000 | Restituir R$ 900 | **1.000** (repete B) | 0 | Sem impacto |
| 6 | Restituir R$ 1.121,56 | **Passa a pagar** R$ 194,77 | **1.121,56** (repete B) | 0 | Sem impacto |

> Caso 6 (ex.: Célia AC 2017): a declaração restituía; a simulação completa passaria a pagar → sem benefício → coluna C repete a restituição da declaração e não marca impacto.

Implementação: `ExcelResumoGeralHelper.calcularValorColunaC`, `calcularPrincipal` e a repetição de B em `montarLinha` quando `principal == 0`.

#### Destaque de restituições (negrito)

Quando **a declaração entregue (B) e a simulação (C) resultam ambas em restituição** (`origem == IMPOSTO A RESTITUIR` nas duas colunas), as células numéricas da linha (B–G) são exibidas em **negrito** no Excel. Implementação: variantes de estilo em `ConsolidationExcelServiceImpl.addResumoGeralSheet` (`createBoldVariant`).

### Totais e rodapé

- **Total da diferença R$** — soma de D, F e G
- **Honorários Advocatícios — {sigla ou Contratual} — {N}%** — percentual sobre total G (padrão **12%** quando o cliente não tem empresa/percentual vinculado)
- **Valor a Receber** — total G − honorários

### Percentual de honorários por cliente

O percentual aplicado na linha de honorários é resolvido por `EmpresaHonorariosResolver` a partir do vínculo do cliente (`Person.empresaId` + `Person.percentualHonorarioId`):

1. Se o cliente possui empresa e percentual vigente cadastrados → usa o percentual da empresa (ex.: APCEF 15%).
2. Caso contrário → fallback **12%** (`ExcelResumoGeralHelper.PERCENTUAL_HONORARIOS_DEFAULT`).

O rótulo da linha segue o padrão `Honorários Advocatícios - {SIGLA} - {N}%` (ex.: `Honorários Advocatícios - APCEF - 12%`); sem empresa vinculada, usa `Contratual` no lugar da sigla.

Regressão: `ElizeteResumoGeralTest` (12% padrão e percentual customizado 15%).
- Rodapé com responsável técnico (constantes em `ExcelResumoGeralHelper`)

### SELIC

- **dataVencimento** — mapa estático por ano-calendário (`ExcelResumoGeralHelper.DATAS_VENCIMENTO`), exibido no cabeçalho "Datas para atualização"
- **dataPagamento (fim)** — `LocalDate.now()` na geração do Excel (colunas E/F/G variam com a data)

### Layout visual

- **Colunas A–G:** largura fixa ~105 px (15 caracteres); **coluna H:** ~280 px (40 caracteres)
- **Borda externa espessa** (`MEDIUM`) em torno do bloco principal (A1:H até "Valor a Receber")
- Bordas internas finas na tabela; linha dupla (`DOUBLE`) nas linhas de totais/honorários/valor a receber
- Rodapé (Responsável / CORECON) em caixa separada abaixo, com borda espessa própria
- Linha do economista: merge **B:F** com o texto CORECON; coluna **H** com data/hora de geração (`dd/MM/yyyy HH:mm`, fuso `America/Sao_Paulo`)
- Cabeçalho da tabela com fundo cinza e altura ~64 pt
- **Logo Origium** na área mesclada **G1:H8** (8 linhas × 2 colunas), recurso `excel/origium_logo.png`, ancorado com `MOVE_AND_RESIZE` para preencher a célula

### Classes envolvidas

- `ConsolidationExcelServiceImpl` — `addResumoGeralSheet`, orquestração reativa com `TaxaSelicService`
- `ResumoGeralAssemblyService` — montagem compartilhada (linhas + SELIC + honorários) usada pelo Excel e pelo endpoint JSON
- `ResumoGeralUseCase` — orquestra pessoa + consolidação + montagem para preview
- `ExcelResumoGeralHelper` — regras B–H, mapa de datas, totais e honorários
- `ExcelResumoGeralLinhaDTO` — linha por ano + `enriquecerComSelic`
- `ExcelResumoGeralLogoHelper` — merge G1:H8 e inserção do logo Origium

---

# 13. Preview JSON — Resumo Geral (modal web)

Endpoint para exibir o Resumo Geral no frontend **sem baixar o Excel**.

```
GET /api/v1/persons/{personId}/resumo-geral
Accept: application/json
```

| Status | Significado |
|--------|-------------|
| **200** | Payload completo (`ResumoGeralResponse`) |
| **204** | Pessoa existe, mas não há declarações IR alinhadas com contracheque |
| **404** | Pessoa não encontrada / sem permissão de tenant |

### Estrutura da resposta

- `nome`, `cpf`, `atualizacao` (`SELIC RECEITA FEDERAL`)
- `dataGeracao`, `dataPagamentoSelic`
- `datasVencimento` — mapa ano-calendário → data (mesmo de `ExcelResumoGeralHelper.DATAS_VENCIMENTO`)
- `linhas[]` — colunas B–H por ano (`ResumoGeralLinhaResponse`)
- `totais` — somas D/F/G, honorários e valor a receber
- `honorarios` — rótulo formatado (ex.: `Honorários Advocatícios - APCEF/SC - 12%`)
- `rodape` — responsável técnico CORECON

Regressão: `ExcelControllerResumoGeralTest`, `ResumoGeralUseCaseTest`.

Frontend: botão **Resumo Geral** na lista de clientes (`persons-page.tsx`) abre modal full-screen (`ResumoGeralModal`) com botões **Gerar PDF** e **Exportar Excel**.

---

# 14. Resumo Geral em PDF

Gera o **Resumo Geral** em PDF (A4 paisagem, 1 página), espelhando a aba Excel "Resumo Geral" (grid de datas, tabela por ano, totais amarelos, honorários, rodapé do responsável e logo Origium).

```
GET /api/v1/persons/{personId}/resumo-geral/pdf
Accept: application/pdf
```

| Status | Significado |
|--------|-------------|
| **200** | PDF (`application/pdf`) |
| **204** | Pessoa existe, mas sem declarações IR alinhadas com contracheque |
| **404** | Pessoa não encontrada / sem permissão |

Reaproveita a montagem compartilhada (`ResumoGeralUseCase.montarByPersonId` → `ResumoGeralAssemblyService`), então segue exatamente as mesmas regras de colunas B–H (incluindo a regra de impacto financeiro acima). Valores monetários sem prefixo `R$`, como no Excel.

Classes: `ResumoGeralPdfUseCase`, `ResumoGeralPdfGenerator` (iText), `ResumoGeralPdfResult`. Regressão: `ResumoGeralPdfGeneratorTest`.

---

Fim da documentação da API 6 — Exportação de Excel.
