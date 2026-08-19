# EXTRATOR.md
# 🎯 Manual Oficial do Extrator de Contracheques (CAIXA + FUNCEF)

Este documento descreve **todo o comportamento do extrator**, incluindo:

- Regex completas  
- Heurísticas de detecção  
- Normalização (datas, valores, descrições)  
- Pipeline completo (página → linhas → entries)  
- Regras especiais de CAIXA e FUNCEF  
- Casos limites e comportamento esperado  

É a documentação **definitiva** para implementação do módulo de parsing.

---

# 1. OBJETIVO DO EXTRATOR

O extrator tem como função:

1. Ler o PDF (todas páginas)  
2. Determinar origem de cada página (CAIXA, FUNCEF, MISTO)  
3. Extrair metadados gerais  
4. Extrair rubricas linha por linha  
5. Normalizar todos os dados  
6. Gerar `payroll_entries` prontos para inserção no MongoDB  

---

# 2. PIPELINE COMPLETO DO EXTRATOR

```mermaid
flowchart TD
    A[PDF Input] --> B[Split páginas]
    B --> C[Detectar origem da página]
    C --> D[Extrair metadados da página]
    D --> E[Extrair linhas via regex]
    E --> F[Normalizar valores/datas]
    F --> G[Validar rubricas]
    G --> H[Gerar payroll_entries]
```

---

# 3. DETECÇÃO DO TIPO DE DOCUMENTO

## 3.1 Regras para Caixa

Detectar se contém *qualquer* dos termos:

- `"CONTRACHEQUE"`
- `"CAIXA ECONÔMICA"`
- `"CAIXA ECONÔMICA FEDERAL"`
- `"MÊS DE REFERÊNCIA"`
- `"MÊS/ANO"`

### Regex (case-insensitive)

```
(?i)(contracheque|caixa econômica|mês de referência)
```

---

## 3.2 Regras para Funcef

Detectar se contém:

- `"DEMONSTRATIVO DE PROVENTOS"`
- `"PREVIDENCIÁRIOS"`
- `"FUNCEF"`
- `"VALOR LÍQUIDO"`

### Regex

```
(?i)(previdenciários|funcef|demonstrativo de proventos)
```

---

## 3.3 Determinação Final do Tipo

| Caso encontrado | Tipo |
|-----------------|------|
| Apenas Caixa | CAIXA |
| Apenas Funcef | FUNCEF |
| Ambos | MISTO (páginas separadas) |

---

# 4. EXTRAÇÃO DE METADADOS

## 4.1 Caixa – Campos obrigatórios

| Campo | Padrão | Exemplo |
|-------|--------|---------|
| Nome | Linha superior | `FLAVIO JOSE PEREIRA ALMEIDA` |
| CPF | regex | `124.497.095-68` |
| Referência | `MM/YYYY` | `01/2017` |

### Regex CPF Caixa

```
([0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2})
```

### Regex Referência

```
([0-9]{2}\/[0-9]{4})
```

---

## 4.2 Funcef – Campos obrigatórios

| Campo | Padrão | Exemplo |
|-------|--------|---------|
| Nome | Linha superior | `FLAVIO JOSE PEREIRA ALMEIDA` |
| CPF | sem máscara | `12449709568` |
| Referência | inverter no final | `2018/01` |

### Regex CPF Funcef

```
([0-9]{11})
```

---

# 5. EXTRAÇÃO DE RUBRICAS

# 5.1 Regex CAIXA — COMPLETA

```
^([0-9]{3,4})\s+(.+?)\s+([0-9]{2}\/[0-9]{4}|[0-9]{4}\/[0-9]{2})\s+R\$?\s*([0-9\.,]+)
```

### Capturas

| Grupo | Valor | Significado |
|-------|--------|--------------|
| 1 | 4482 | Código |
| 2 | CONTRIBUIÇÃO EXTRAORDINÁRIA 2015 | Descrição |
| 3 | 2017/08 | Referência |
| 4 | 885,47 | Valor |

---

# 5.2 Regex FUNCEF — COMPLETA

Funcef varia mais. Possíveis linhas:

```
4236 FUNCEF NOVO PLANO           R$ 1.385,66
4482 CONTRIB... EXTRAORD...      R$ 424,10
```

### Regex consolidada

```
^([0-9]{3,4})\s+(.+?)\s+(R\$)?\s*([0-9\.,]+)
```

### Diferença crítica
Funcef não fornece a referência na mesma linha:

- a referência vem do cabeçalho
- replicar referência para todas as linhas da página

---

# 5.3 Regex FUNCEF — portal agrupado (multi-ano)

Layout do **portal de autoatendimento** Funcef (PDF digital agrupado, várias competências):

- Cabeçalho: `Mês/Ano Referência: YYYY/MM` + `Nº Benefício INSS`
- Linha: `2 187 2017/12 PROVENTOS INSS R$ 3.659,69` (código com espaço, ref `YYYY/MM`, valor com `R$` opcional)
- Competências em 2 páginas (`Página 1 de 2` / `Página 2 de 2`); a **página 2 não tem cabeçalho**

### Regras

| Tema | Comportamento |
|------|----------------|
| Tipo | Continua `FUNCEF` (sem novo `DocumentType`) |
| Detecção | Sinais `Mês/Ano Referência`, `Tipo / Rubrica` + logo Funcef |
| Mês | Priorizar `Mês/Ano Referência: YYYY/MM` em `MonthYearDetectionServiceImpl` |
| Parser | `FUNCEF_PATTERN` com `(?:R\$\s*)?`; pré-juntar linhas quebradas até achar valor |
| Continuação | Pág. 2 de 2: herdar origem `FUNCEF` e `mesPagamento` da página anterior |
| Totais | Ignorar blocos `Renda Base` / `Bruto` / `Líquido` / rodapé |

Classes: `DocumentTypeDetectionServiceImpl`, `MonthYearDetectionServiceImpl`, `PdfLineParser`, `DocumentUploadUseCase` / `DocumentProcessUseCase`.  
Regressão: `FuncefPortalAgrupadoParsingTest`.

Plano: **Funcef portal agrupado**. Catálogo: [008 - PLANOS_EXECUTADOS…](../10-planejamento/008%20-%20PLANOS_EXECUTADOS_PDFPROCESSOR.md).

---

# 5.4 SABESP — Demonstrativo de Pagamento (ativa)

Layout SAP da **Companhia de Saneamento Básico do Estado de São Paulo — SABESP**.

- Título: `DEMONSTRATIVO DE PAGAMENTO` + marca/razão `SABESP`
- Identificação: `PERÍODO` (`MM/YYYY`), `MATRÍC` (7–8 dígitos), `NOME DO EMPREGADO`
- **CPF:** o vínculo do documento é sempre o **CPF do cadastro do cliente** (upload por `personId` ou form). Layout de referência (Anselmo) **não** traz CPF no PDF. Se o PDF trouxer CPF e divergir do cadastro → `warn` (não bloqueia); só usa CPF do PDF se o form/cadastro estiver sem CPF.
- **Matrícula (8 dígitos):** chave auxiliar no cadastro (formulário aceita **7–9** dígitos). Extraída do PDF (`MATRÍC`); se o cadastro estiver vazio, preenche; se divergir → `warn` e mantém a matrícula do cadastro.
- Colunas: `CONTA | DESCRIÇÃO | QTDE. | VALOR UNIT. | VENCIMENTOS | DESCONTOS`
- Extrai linhas {@code CONTA + descrição + valor} (código 3–4 dígitos); **só persiste** se a rubrica existir e estiver ativa na tabela `rubricas` (mesmo fluxo CAIXA/FUNCEF). Rubricas SABESP (ex.: 3347/3349) = cadastro na UI/API — sem seed e sem lista fixa no parser.
- `DocumentType.SABESP` / `PayrollEntry.origem = "SABESP"`
- Totais Excel: soma dos meses (como CAIXA; sem regra Funcef FEV/NOV)
- **Simulação IRPF / Resumo Geral:** mesmo fluxo `prevCompl` dos demais contracheques — consolidação sem filtro de origem inclui entries `SABESP`; `PrevComplPlanilhaHelper.calcularPrevComplSimulacao` soma 3347+3349 no ano → bloco 2 e Resumo Geral (`ResumoGeralAssemblyService`)

Classes: `DocumentTypeDetectionServiceImpl.isSabespDemonstrativo`, `SabespPayslipMetadataExtractor`, `PdfLineParser` (`SABESP_LINE_PATTERN`), `RubricaValidator`.  
Regressão: `SabespPayslipParsingTest`, `PrevComplPlanilhaHelperTest` (3347+3349 → 758,75).

---

# 5.5 SABESPREV — Ficha Financeira de Pagamentos (aposentado)

PDF anual da **Fundação SABESP de Seguridade Social — SABESPREV** (substitui o contracheque mensal SABESP quando a pessoa se aposenta).

- Título: `FICHA FINANCEIRA DE PAGAMENTOS` + `SABESPREV`
- Cabeçalho: `ANO: YYYY`, `MATRÍCULA` (até 9 dígitos), `NOME` — sem CPF no PDF (vínculo = CPF do cadastro)
- Grade: `RUBRICA | DESCRIÇÃO | P/D | JAN…DEZ | TOTAL`
- Parser: interpreta layout PDFBox (`…valores TOTAL DEZ` colados + `CÓDIGO P/D` no fim da linha); meses com valor **> 0** → `PayrollEntry` (`referencia`/`mesPagamento` = `YYYY-MM`)
- **Whitelist:** tabela `rubricas` (cadastro UI/API — sem seed). Códigos típicos de rodapé: `7400`, `7401`, `7402`, `7404`, `9100`, `9102`, `9105`, `9106`, `9112`, `9115` (outras rubricas da ficha, ex. `1090`/`9001`/`9607`, entram na matriz se cadastradas, mas **não** no rodapé)
- `DocumentType.SABESPREV_FICHA` / `PayrollEntry.origem = "SABESPREV"`
- Detecção **antes** do demonstrativo mensal SABESP
- **Excel (aba do ano):** se `origem = SABESPREV` **ou** o ano tem ≥1 código da lista fechada do rodapé e nenhum holerite SABESP (`3347`/`3349`) — rodapé com 3 linhas:
  - **CONTRIBUIÇÃO** = soma de `7400`, `7401`, `7402`, `9105`, `9106`
  - **DEVOLUÇÃO** = soma de `7404`, `9100`, `9102`, `9112`, `9115`
  - **TOTAL** = CONTRIBUIÇÃO − DEVOLUÇÃO  
  A decisão é **por ano** (cliente com SABESP ativa + ficha no mesmo Excel continua correto).

Classes: `DocumentTypeDetectionServiceImpl.isSabesprevFichaFinanceira`, `SabesprevFichaMetadataExtractor`, `SabesprevFichaFinanceiraParser`, `SabesprevFichaTotaisHelper`, `RubricaValidator`.  
Regressão: `SabesprevFichaFinanceiraParsingTest`, `SabesprevFichaTotaisHelperTest` (Anselmo 2021 → líquido **333,16** com lista fechada).

---

# 6. NORMALIZAÇÃO

## 6.1 Números

### Regras

```
1.399,59 → 1399.59
885,47 → 885.47
```

### Algoritmo

1. remover pontos  
2. trocar vírgula por ponto  
3. converter double  

### Regex para capturar valores

```
([0-9\.]+,[0-9]{2})
```

---

# 6.2 Datas

Aceitar:

```
MM/YYYY
YYYY/MM
```

### Normalização

```
01/2017 → 2017-01
2017/01 → 2017-01
```

---

# 6.3 Descrição

Remover:

- múltiplos espaços
- caracteres estranhos
- sufixos desnecessários

Exemplo:

```
"CONTRIBUIÇÃO EXTRAORDINÁRIA 2015 " → "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015"
```

---

# 7. VALIDAÇÃO DE RUBRICAS

Ao capturar uma rubrica:

1. Ler código do PDF
2. Buscar no Mongo:

```
rubricas.findOne({ codigo, ativo: true })
```

3. Se não existir:

- marcar `ignoredRubrica`
- logar comportamento
- **não persistir entry**

---

# 8. GERANDO PAYROLL_ENTRIES

Entry final:

```json
{
  "documentId": "doc123",
  "rubricaCodigo": "4482",
  "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
  "referencia": "2017-08",
  "valor": 885.47,
  "pagina": 1,
  "origem": "CAIXA"
}
```

---

# 9. PIPELINE DE EXTRAÇÃO POR PÁGINA

```mermaid
flowchart TD
    A[Página de PDF] --> B[Detectar origem]
    B --> C[Extrair metadados]
    C --> D[Extrair linhas com regex]
    D --> E[Normalizar valores]
    E --> F[Validar rubricas]
    F --> G[Gerar Entries]
```

---

# 10. ERROS E CASOS LIMITES

| Caso | Ação |
|------|------|
| Página sem rubricas | Ignorar |
| Código inválido | Log + ignorar |
| Valor sem referência (Caixa) | Log + ignorar |
| Funcef sem referência | Buscar referência do cabeçalho |
| PDF corrompido | Throw → status=ERROR |
| Linha sem valor | Ignorar |

---

# 11. CLASSES DO MÓDULO EXTRATOR

- `DocumentTypeDetector`
- `CaixaMetadataExtractor`
- `FuncefMetadataExtractor`
- `PdfPageSplitter`
- `PdfLineParser`
- `PdfNormalizer`
- `RubricaValidator`
- `EntryBuilder`

---

# 12. ORDEM DE IMPLEMENTAÇÃO

1. Criar detectores
2. Criar extratores de metadados
3. Criar regex completas
4. Criar parser de linhas
5. Criar normalizadores
6. Criar validador de rubricas
7. Criar entry builder
8. Integrar pipeline reativo
9. Testar com PDFs fornecidos

---

Fim do documento EXTRATOR.md.