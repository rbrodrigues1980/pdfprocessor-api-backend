# API de Extração de Declarações de IR (iText 8)

Base URL: `/api/v1/incometax`

---

## Endpoints de Upload (com persistência)

Estes endpoints salvam o documento no banco de dados e associam à pessoa.

### 1. Upload de Declaração de IR por CPF

Faz upload de um PDF de declaração de IR, salva no banco e associa à pessoa pelo CPF.

```
POST /api/v1/incometax/upload
Content-Type: multipart/form-data
```

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF da declaração de IR |
| `cpf` | String | Sim | CPF da pessoa (formato: 000.000.000-00 ou 00000000000) |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/upload" \
  -F "file=@declaracao_ir_2016.pdf" \
  -F "cpf=123.456.789-00"
```

**Response (201 Created):**
```json
{
  "documentId": "507f1f77bcf86cd799439011",
  "status": "PROCESSING",
  "tipoDetectado": "INCOME_TAX"
}
```

---

### 2. Upload de Declaração de IR por PersonId

Faz upload de um PDF para uma pessoa específica, buscando automaticamente o CPF pelo ID.

```
POST /api/v1/incometax/upload/person/{personId}
Content-Type: multipart/form-data
```

**Path Parameters:**
| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `personId` | String | ID da pessoa no banco |

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF da declaração de IR |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/upload/person/507f1f77bcf86cd799439011" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (201 Created):**
```json
{
  "documentId": "507f1f77bcf86cd799439022",
  "status": "PROCESSING",
  "tipoDetectado": "INCOME_TAX"
}
```

---

## Endpoints de Extração (apenas leitura)

Estes endpoints apenas extraem informações do PDF, sem salvar no banco.

### 3. Extrair Declaração de IR

Extrai todas as 37 rubricas de uma declaração de Imposto de Renda.

```
POST /api/v1/incometax/extract
Content-Type: multipart/form-data
```

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF da declaração de IR |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/extract" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Extração realizada com sucesso",
  "filename": "declaracao_ir_2016.pdf",
  "data": {
    "nome": "JOÃO DA SILVA",
    "cpf": "123.456.789-00",
    "anoCalendario": "2015",
    "exercicio": "2016",
    "baseCalculoImposto": 218730.27,
    "impostoDevido": 37015.19,
    "deducaoIncentivo": null,
    "impostoDevidoI": null,
    "contribuicaoPrevEmpregadorDomestico": null,
    "impostoDevidoII": null,
    "impostoDevidoRRA": null,
    "totalImpostoDevido": 37015.19,
    "saldoImpostoPagar": 8995.60,
    "rendimentosTributaveis": 246993.81,
    "deducoes": 28263.54,
    "impostoRetidoFonteTitular": 28019.59,
    "impostoPagoTotal": 28019.59,
    "impostoRestituir": null,
    "deducoesContribPrevOficial": 5189.82,
    "deducoesContribPrevRRA": null,
    "deducoesContribPrevCompl": null,
    "deducoesDependentes": 2275.08,
    "deducoesInstrucao": 10548.64,
    "deducoesMedicas": 10250.00,
    "deducoesPensaoJudicial": null,
    "deducoesPensaoEscritura": null,
    "deducoesPensaoRRA": null,
    "deducoesLivroCaixa": null,
    "impostoRetidoFonteDependentes": null,
    "carneLeaoTitular": null,
    "carneLeaoDependentes": null,
    "impostoComplementar": null,
    "impostoPagoExterior": null,
    "impostoRetidoFonteLei11033": null,
    "impostoRetidoRRA": null,
    "descontoSimplificado": null,
    "aliquotaEfetiva": null
  },
  "rawText": null,
  "extractionTimeMs": 245
}
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "message": "Erro: Página RESUMO não encontrada no PDF",
  "filename": "documento_invalido.pdf",
  "data": null,
  "rawText": null,
  "extractionTimeMs": 120
}
```

---

### 2. Extrair Texto Bruto

Retorna o texto bruto de todas as páginas do PDF.

```
POST /api/v1/incometax/extract/raw
Content-Type: multipart/form-data
```

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/extract/raw" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (200 OK):**
```json
{
  "success": true,
  "filename": "declaracao_ir_2016.pdf",
  "rawText": "=== PÁGINA 1 ===\nMINISTÉRIO DA FAZENDA\nSECRETARIA DA RECEITA FEDERAL...",
  "characterCount": 12345,
  "extractionTimeMs": 180
}
```

---

### 3. Extrair Texto de Página Específica

Retorna o texto bruto de uma página específica (1-indexed).

```
POST /api/v1/incometax/extract/page/{pageNumber}
Content-Type: multipart/form-data
```

**Path Parameters:**
| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `pageNumber` | int | Número da página (1-indexed) |

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/extract/page/2" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (200 OK):**
```json
{
  "success": true,
  "filename": "declaracao_ir_2016.pdf (Página 2)",
  "rawText": "RESUMO DA DECLARAÇÃO\nExercício 2016...",
  "characterCount": 3456,
  "extractionTimeMs": 95
}
```

---

### 4. Extração com Debug

Retorna as informações extraídas E o texto bruto da página RESUMO.

```
POST /api/v1/incometax/extract/debug
Content-Type: multipart/form-data
```

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF da declaração de IR |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/extract/debug" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Página RESUMO: 2",
  "filename": "declaracao_ir_2016.pdf",
  "data": {
    "nome": "JOÃO DA SILVA",
    "cpf": "123.456.789-00",
    ...
  },
  "rawText": "RESUMO DA DECLARAÇÃO\nExercício 2016\nAno-Calendário 2015\n...",
  "extractionTimeMs": 320
}
```

---

### 5. Encontrar Página RESUMO

Retorna o número da página que contém "RESUMO" no PDF.

```
POST /api/v1/incometax/find-resumo
Content-Type: multipart/form-data
```

**Request:**
| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo PDF |

**Exemplo cURL:**
```bash
curl -X POST "http://localhost:8080/api/v1/incometax/find-resumo" \
  -F "file=@declaracao_ir_2016.pdf"
```

**Response (200 OK):**
```json
{
  "success": true,
  "filename": "declaracao_ir_2016.pdf",
  "pageNumber": 2,
  "message": "Página RESUMO encontrada"
}
```

---

## TypeScript Interfaces

```typescript
// Response principal da extração
interface IncomeTaxExtractionResponse {
  success: boolean;
  message: string;
  filename: string;
  data: IncomeTaxInfoDto | null;
  rawText: string | null;
  extractionTimeMs: number;
}

// Dados extraídos da declaração
interface IncomeTaxInfoDto {
  // Dados Básicos
  nome: string | null;
  cpf: string | null;
  anoCalendario: string | null;
  exercicio: string | null;

  // IMPOSTO DEVIDO
  baseCalculoImposto: number | null;
  impostoDevido: number | null;
  deducaoIncentivo: number | null;
  impostoDevidoI: number | null;
  contribuicaoPrevEmpregadorDomestico: number | null;
  impostoDevidoII: number | null;
  impostoDevidoRRA: number | null;
  totalImpostoDevido: number | null;
  saldoImpostoPagar: number | null;

  // Rendimentos e Deduções
  rendimentosTributaveis: number | null;
  deducoes: number | null;
  impostoRetidoFonteTitular: number | null;
  impostoPagoTotal: number | null;
  impostoRestituir: number | null;

  // DEDUÇÕES Individuais
  deducoesContribPrevOficial: number | null;
  deducoesContribPrevRRA: number | null;
  deducoesContribPrevCompl: number | null;
  deducoesDependentes: number | null;
  deducoesInstrucao: number | null;
  deducoesMedicas: number | null;
  deducoesPensaoJudicial: number | null;
  deducoesPensaoEscritura: number | null;
  deducoesPensaoRRA: number | null;
  deducoesLivroCaixa: number | null;

  // IMPOSTO PAGO Individuais
  impostoRetidoFonteDependentes: number | null;
  carneLeaoTitular: number | null;
  carneLeaoDependentes: number | null;
  impostoComplementar: number | null;
  impostoPagoExterior: number | null;
  impostoRetidoFonteLei11033: number | null;
  impostoRetidoRRA: number | null;

  // Campos 2017+
  descontoSimplificado: number | null;
  aliquotaEfetiva: number | null;
}

// Response para texto bruto
interface RawTextResponse {
  success: boolean;
  filename: string;
  rawText: string;
  characterCount: number;
  extractionTimeMs: number;
}

// Response para página RESUMO
interface ResumoPageResponse {
  success: boolean;
  filename: string;
  pageNumber: number | null;
  message: string;
}
```

---

## Exemplo de Uso (React/TypeScript)

```typescript
// Service para chamar a API
export const incomeTaxService = {
  async extractIncomeTax(file: File): Promise<IncomeTaxExtractionResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/v1/incometax/extract', {
      method: 'POST',
      body: formData,
    });

    return response.json();
  },

  async extractWithDebug(file: File): Promise<IncomeTaxExtractionResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/v1/incometax/extract/debug', {
      method: 'POST',
      body: formData,
    });

    return response.json();
  },

  async extractRawText(file: File): Promise<RawTextResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/v1/incometax/extract/raw', {
      method: 'POST',
      body: formData,
    });

    return response.json();
  },
};
```

```tsx
// Exemplo de componente React
import { useState } from 'react';
import { incomeTaxService, IncomeTaxExtractionResponse } from './services/incomeTaxService';

function IncomeTaxUploader() {
  const [result, setResult] = useState<IncomeTaxExtractionResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setLoading(true);
    try {
      const response = await incomeTaxService.extractIncomeTax(file);
      setResult(response);
    } catch (error) {
      console.error('Erro na extração:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <input type="file" accept=".pdf" onChange={handleFileChange} />
      
      {loading && <p>Processando...</p>}
      
      {result && (
        <div>
          <h3>Resultado ({result.extractionTimeMs}ms)</h3>
          {result.success ? (
            <pre>{JSON.stringify(result.data, null, 2)}</pre>
          ) : (
            <p style={{ color: 'red' }}>{result.message}</p>
          )}
        </div>
      )}
    </div>
  );
}
```

---

## Campos Extraídos (37 Rubricas)

| # | Campo | Descrição | Tipo |
|---|-------|-----------|------|
| 1 | `nome` | Nome do contribuinte | string |
| 2 | `cpf` | CPF do contribuinte | string |
| 3 | `anoCalendario` | Ano-calendário (ex: 2017) | string |
| 4 | `exercicio` | Exercício fiscal (ex: 2018) | string |
| 5 | `baseCalculoImposto` | Base de cálculo do imposto | number |
| 6 | `impostoDevido` | Imposto devido | number |
| 7 | `deducaoIncentivo` | Dedução de incentivo | number |
| 8 | `impostoDevidoI` | Imposto devido I | number |
| 9 | `contribuicaoPrevEmpregadorDomestico` | Contrib. Prev. Empregador Doméstico | number |
| 10 | `impostoDevidoII` | Imposto devido II | number |
| 11 | `impostoDevidoRRA` | Imposto devido RRA | number |
| 12 | `totalImpostoDevido` | Total do imposto devido | number |
| 13 | `saldoImpostoPagar` | Saldo de imposto a pagar | number |
| 14 | `rendimentosTributaveis` | Total de rendimentos tributáveis | number |
| 15 | `deducoes` | Total de deduções | number |
| 16 | `impostoRetidoFonteTitular` | Imposto retido na fonte do titular | number |
| 17 | `impostoPagoTotal` | Total do imposto pago | number |
| 18 | `impostoRestituir` | Imposto a restituir | number |
| 19 | `deducoesContribPrevOficial` | Contribuição previdência oficial | number |
| 20 | `deducoesContribPrevRRA` | Contribuição previdência (RRA) | number |
| 21 | `deducoesContribPrevCompl` | Contribuição previdência complementar | number |
| 22 | `deducoesDependentes` | Dependentes | number |
| 23 | `deducoesInstrucao` | Despesas com instrução | number |
| 24 | `deducoesMedicas` | Despesas médicas | number |
| 25 | `deducoesPensaoJudicial` | Pensão alimentícia judicial | number |
| 26 | `deducoesPensaoEscritura` | Pensão por escritura pública | number |
| 27 | `deducoesPensaoRRA` | Pensão judicial (RRA) | number |
| 28 | `deducoesLivroCaixa` | Livro caixa | number |
| 29 | `impostoRetidoFonteDependentes` | Imposto retido (dependentes) | number |
| 30 | `carneLeaoTitular` | Carnê-Leão do titular | number |
| 31 | `carneLeaoDependentes` | Carnê-Leão dos dependentes | number |
| 32 | `impostoComplementar` | Imposto complementar | number |
| 33 | `impostoPagoExterior` | Imposto pago no exterior | number |
| 34 | `impostoRetidoFonteLei11033` | Imposto retido (Lei 11.033/2004) | number |
| 35 | `impostoRetidoRRA` | Imposto retido RRA | number |
| 36 | `descontoSimplificado` | Desconto simplificado (2017+) | number |
| 37 | `aliquotaEfetiva` | Alíquota efetiva % (2017+) | number |

---

## Notas

1. **Formatos Suportados:**
   - Declarações 2016 (layout duas colunas)
   - Declarações 2017+ (layout simplificado)

2. **Campos null:**
   - Campos exclusivos de 2016 serão `null` em declarações 2017+
   - Campos exclusivos de 2017+ (`descontoSimplificado`, `aliquotaEfetiva`) serão `null` em declarações antigas

3. **Swagger UI:**
   - Documentação interativa disponível em: `http://localhost:8080/swagger-ui.html`
   - Procure pela tag "Income Tax - iText 8"
