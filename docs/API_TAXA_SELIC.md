# API Taxa SELIC - Banco Central

Documentação para integração frontend.

---

## Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/selic/vigente` | Taxa SELIC vigente atual |
| GET | `/selic/data/{data}` | Taxa vigente em uma data específica |
| GET | `/selic/ano/{ano}` | Taxa de referência para um ano |
| GET | `/selic/historico` | Histórico completo |
| GET | `/selic/periodo?inicio=&fim=` | Taxas de um período |
| GET | `/selic/acumulada?inicio=&fim=` | SELIC acumulada (juros compostos por período COPOM) |
| GET | `/selic/receita-federal?dataPagamento=&dataAtualizacao=` | **SELIC para Receita Federal (mensal)** |
| GET | `/selic/reuniao/{numero}` | Taxa por número de reunião COPOM |
| POST | `/selic/sync` | Força sincronização com BCB |
| GET | `/selic/stats` | Estatísticas da collection |

---

## Exemplos

### Taxa vigente atual

```bash
GET /selic/vigente
```

**Response:**
```json
{
  "id": "...",
  "numeroReuniaoCopom": 275,
  "dataReuniaoCopom": "2025-12-10T03:00:00Z",
  "reuniaoExtraordinaria": false,
  "vies": "n/a",
  "usoMetaSelic": false,
  "dataInicioVigencia": "2025-12-11T03:00:00Z",
  "dataFimVigencia": null,
  "metaSelic": 15.00,
  "taxaTban": null,
  "taxaSelicEfetivaVigencia": null,
  "taxaSelicEfetivaAnualizada": null,
  "decisaoMonocraticaPres": false,
  "syncedAt": "2025-12-20T13:50:00"
}
```

### Taxa por data

```bash
GET /selic/data/2024-06-15
```

### Taxa por ano (usa 1º de julho como referência)

```bash
GET /selic/ano/2024
```

### Histórico de um período

```bash
GET /selic/periodo?inicio=2024-01-01&fim=2024-12-31
```

### Estatísticas

```bash
GET /selic/stats
```

**Response:**
```json
{
  "totalRegistros": 275,
  "selicAtual": 15.00,
  "ultimaReuniao": 275
}
```

### SELIC Acumulada (Juros Compostos)

```bash
GET /selic/acumulada?inicio=2023-01-01&fim=2024-10-31
```

**Response:**
```json
{
  "dataInicio": "2023-01-01",
  "dataFim": "2024-10-31",
  "taxaAcumuladaPercentual": 25.4832,
  "fatorAcumulado": 1.25483200,
  "diasTotais": 670,
  "periodos": [
    {
      "inicio": "2023-01-01",
      "fim": "2023-02-01",
      "dias": 32,
      "metaSelic": 13.75,
      "taxaAplicada": 1.12,
      "reuniaoCopom": 252
    }
  ]
}
```

**Campos do resultado:**
- `taxaAcumuladaPercentual`: Taxa acumulada em % (25.48% no exemplo)
- `fatorAcumulado`: Fator multiplicador (R$ 1.000 × 1.2548 = R$ 1.254,83)
- `periodos`: Detalhamento de cada período de vigência

### SELIC para Receita Federal (Restituição/Débitos)

Use este endpoint para calcular a SELIC no formato usado pela Receita Federal.

```bash
GET /selic/receita-federal?dataPagamento=2017-04-30&dataAtualizacao=2025-08-31
```

**Response:**
```json
{
  "dataPagamento": "2017-04-30",
  "dataAtualizacao": "2025-08-31",
  "taxaAcumuladaPercentual": 67.58,
  "fatorAcumulado": 1.67580000,
  "totalMeses": 100,
  "meses": [
    { "ano": 2017, "mes": 5, "taxa": 0.93 },
    { "ano": 2017, "mes": 6, "taxa": 0.81 },
    ...
  ]
}
```

**Diferença entre os endpoints:**

| Endpoint | Metodologia | Uso |
|----------|-------------|-----|
| `/selic/acumulada` | Períodos COPOM exatos | Cálculo técnico/financeiro |
| `/selic/receita-federal` | **Mensal (mês seguinte ao pagamento)** | **Restituição/débitos RFB** |

> [!IMPORTANT]
> O endpoint `/selic/receita-federal` usa taxas SELIC **mensais** da série SGS 4390 do BCB.
> Estas são as mesmas taxas utilizadas pela Receita Federal para correção de restituições e débitos.

**Cálculo da correção:**
```
valorOriginal = R$ 346,13
selicAcumulada = 67.58%
correcao = valorOriginal × (selicAcumulada / 100) = R$ 233,91
valorTotal = valorOriginal + correcao = R$ 580,04
```

---

## Sincronização

### Automática
- **Ao iniciar a aplicação**: Sincroniza SELIC COPOM e SELIC mensal
- **Diariamente às 06:00**: Atualiza com novos dados do BCB

### Dados Sincronizados
| Collection | Fonte | Quantidade |
|------------|-------|------------|
| `taxa_selic` | Histórico COPOM | ~275 registros |
| `selic_mensal` | Série SGS 4390 | ~470 registros (desde 1986) |

### Manual
```bash
POST /selic/sync
```

---

## Fonte dos Dados

APIs do Banco Central do Brasil:

| Endpoint | API BCB | Uso |
|----------|---------|-----|
| SELIC COPOM | `https://www.bcb.gov.br/api/servico/sitebcb/historicotaxasjuros` | Períodos de vigência |
| **SELIC Mensal** | `https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados` | **Receita Federal** |

---

## Campos Principais

### SELIC por Período COPOM

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `metaSelic` | Decimal | Meta SELIC (% a.a.) |
| `taxaSelicEfetivaAnualizada` | Decimal | Taxa efetiva anualizada |
| `taxaSelicEfetivaVigencia` | Decimal | Taxa efetiva do período |
| `dataInicioVigencia` | DateTime | Início da vigência |
| `dataFimVigencia` | DateTime | Fim da vigência (null = vigente) |
| `numeroReuniaoCopom` | Integer | Número da reunião (1-275+) |

### SELIC Mensal (RFB)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `ano` | Integer | Ano de referência |
| `mes` | Integer | Mês de referência (1-12) |
| `taxa` | Decimal | Taxa SELIC do mês (ex: 0.93%) |
