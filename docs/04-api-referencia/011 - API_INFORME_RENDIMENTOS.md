# 011 — API Informe de Rendimentos (Funcef)

> Tipo de documento `INFORME_RENDIMENTOS`: comprovante Funcef
> **COMPROVANTE DE RENDIMENTOS PAGOS E DE RETENÇÃO DE IR NA FONTE**.
>
> Extração determinística por regex (sem Gemini). Não gera `PayrollEntry`.
> Uso principal: crédito de **IRRF / IRRF 13º** via depósito judicial na **simulação anual** e no **Resumo Geral**.

**Última atualização:** Julho 2026  
**Planos:** Informe Rendimentos Funcef · Depósito Judicial Simulação

---

## 1. Visão geral

| Item | Valor |
|------|--------|
| Tipo | `DocumentType.INFORME_RENDIMENTOS` |
| Upload | Mesmo fluxo de contracheques (`POST …/persons/{id}/documents/bulk-upload` ou upload genérico) |
| Detecção | Automática no texto do PDF |
| Persistência | `PayrollDocument.informeRendimentosData` |
| Entries | Nenhuma (processamento grava só o POJO + status `PROCESSED`) |

Fluxo:

```
Upload PDF → detect INFORME_RENDIMENTOS → process → InformeRendimentosFuncefExtractor
         → PayrollDocument.informeRendimentosData → UI “Ver Informe” / Excel simulação
```

---

## 2. Detecção

Em `DocumentTypeDetectionServiceImpl`, sinais típicos:

- `COMPROVANTE DE RENDIMENTOS PAGOS` e/ou `INFORME` + `RENDIMENTOS`
- Seção `7. INFORMAÇÕES COMPLEMENTARES`
- Preferência por contexto Funcef (`FUNDAÇÃO DOS ECONOMIÁRIOS`, CNPJ `00.436.923/0001-90`)

Não confundir com contracheque Funcef (`DEMONSTRATIVO DE PROVENTOS…`).

---

## 3. Modelo de dados

`InformeRendimentosData`:

| Campo | Descrição |
|-------|-----------|
| `cnpjFontePagadora` | CNPJ mascarado |
| `razaoSocialFontePagadora` | Razão social |
| `anoCalendario` | Ex.: `"2018"` |
| `cpfBeneficiario` | CPF |
| `nomeBeneficiario` | Nome |
| `naturezaRendimento` | Opcional |
| `informacoesComplementaresRaw` | Texto bruto da seção 7 (auditoria) |
| `informacoesComplementares[]` | Lista estruturada |

Cada `InformacaoComplementarJudiciaria`:

| Campo | Exemplo (Paulo 2018) |
|-------|----------------------|
| `numeroProcesso` | `1007809-57.2017.4.01.3300` |
| `data` | `03/06/2018` |
| `codigo` | `10` |
| `varaOuLocal` | contém `CIVEL` |
| `contrExtr` | `12618.07` |
| `irrf` | `3470.00` |
| `contrExtr13` | `1904.02` |
| `irrf13` | `523.61` |

Classes: `InformeRendimentosData`, `InformeRendimentosFuncefExtractor`, branch em `DocumentProcessUseCase.processInformeRendimentosDocument`.

---

## 4. Endpoint

### GET `/api/v1/documents/{id}/informe-rendimentos-data`

Retorna o POJO persistido. `404` se o documento não for informe ou não tiver dados.

Uso no frontend: `documentsApi.getInformeRendimentosData(id)` → modal **Ver Informe** na lista de documentos do cliente.

---

## 5. Depósito judicial na simulação (Excel / Resumo Geral)

Fonte: informe do **mesmo ano-calendário** da aba anual.

### Bloco 2 apenas (`IMPOSTO PAGO — ESTUDO COM APROVEITAMENTO…`)

Ordem:

1. Linhas de imposto pago da DIRPF  
2. `Total do imposto pago` (1º total = DIRPF)  
3. Para cada info complementar com IRRF / IRRF 13º > 0:  
   `Imposto Pago - Através de Depósito Judicial - Processo Jud {n} - {data} - {codigo} - {vara}`  
4. `Total do imposto pago` (2º total = DIRPF + soma IRRF + IRRF 13º)  
5. `RESULTADO` usa o **2º total**

**Não** entram no bloco Imposto Pago: `Contr. Extr.` / `Contr. Extr. 13º`.  
**Não** altera o bloco 1 (declaração entregue).

### Resumo Geral

`ExcelResumoGeralHelper.calcularResultadoBloco2Simulacao(..., informe)`:

```
totalPago = totalImpostoPagoDIRPF + somarIrrfDepositoJudicial(informe)
```

Assim a coluna C/D da simulação refletem o crédito judicial.

Carregamento: mapa `anoCalendario → InformeRendimentosData` em `ConsolidationExcelServiceImpl` e `ResumoGeralUseCase` / `ResumoGeralAssemblyService` (merge se houver mais de um informe no ano).

Referência Paulo AC 2018: DIRPF 9.974,48 + 3.470 + 523,61 = **13.968,09**.

Detalhes de layout Excel: [008 - API_6_EXCEL_EXPORT.md](./008%20-%20API_6_EXCEL_EXPORT.md) §15.

---

## 6. UX (frontend)

- Botão de upload: **Contracheques e Informes** (mesmo endpoint; tipo detectado automaticamente).
- Label do tipo: `Informe de Rendimentos`.
- Documento `PROCESSED`: ação **Ver Informe**.

Arquivos: `persons-page.tsx`, `person-detail-page.tsx`, `documents-page.tsx`, `common/types/index.ts`.

---

## 7. Fora de escopo (MVP)

- Gemini / multi-layout de outros bancos  
- Gerar `PayrollEntry` a partir de Contr. Extr. / IRRF  
- Extração completa dos quadros 3–6 do comprovante  
- Rodapé Excel com texto bruto da seção 7  

---

## 8. Testes de regressão

| Classe | O que cobre |
|--------|-------------|
| `InformeRendimentosFuncefExtractorTest` | Fixture texto + PDF Paulo 2018 em `temp/` |
| `ExcelResumoGeralHelperDepositoJudicialTest` | Soma 3993,61, label, total 13968,09, resultado bloco 2 |

---

[← Voltar ao índice](./README.md)
