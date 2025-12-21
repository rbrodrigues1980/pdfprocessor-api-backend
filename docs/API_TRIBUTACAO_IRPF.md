# API de Tributação IRPF

Documentação para integração frontend.

---

## Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/tributacao/anos?tipo=ANUAL` | Lista anos disponíveis |
| GET | `/tributacao/{ano}?tipo=ANUAL` | Busca faixas de um ano |
| POST | `/tributacao/{ano}?tipo=ANUAL` | Criar/atualizar tributação |
| DELETE | `/tributacao/{ano}?tipo=ANUAL` | Remover tributação de um ano |

### Parâmetro `tipo`
- `ANUAL` - Incidência anual (declaração de IR)
- `MENSAL` - Incidência mensal (retenção na fonte)
- `PLR` - Participação nos Lucros ou Resultados

---

## DTOs

### TributacaoAnoDTO (Response/Request)

```json
{
  "anoCalendario": 2024,
  "tipoIncidencia": "ANUAL",
  "faixas": [
    {
      "faixa": 1,
      "limiteInferior": 0,
      "limiteSuperior": 26963.20,
      "aliquota": 0,
      "deducao": 0,
      "descricao": "Isento"
    },
    {
      "faixa": 2,
      "limiteInferior": 26963.21,
      "limiteSuperior": 33919.80,
      "aliquota": 0.075,
      "deducao": 2022.24,
      "descricao": "7,5%"
    },
    {
      "faixa": 3,
      "limiteInferior": 33919.81,
      "limiteSuperior": 45012.60,
      "aliquota": 0.15,
      "deducao": 4566.23,
      "descricao": "15%"
    },
    {
      "faixa": 4,
      "limiteInferior": 45012.61,
      "limiteSuperior": 55976.16,
      "aliquota": 0.225,
      "deducao": 7942.17,
      "descricao": "22,5%"
    },
    {
      "faixa": 5,
      "limiteInferior": 55976.17,
      "limiteSuperior": null,
      "aliquota": 0.275,
      "deducao": 10740.98,
      "descricao": "27,5%"
    }
  ],
  "parametros": {
    "deducaoDependente": 2275.08,
    "limiteInstrucao": 3561.50,
    "limiteDescontoSimplificado": 16754.34,
    "isencao65Anos": 26963.20
  }
}
```

---

## Dados Pré-carregados

Ao iniciar a aplicação, os seguintes dados são populados automaticamente:

| Anos | Limite Isenção (Faixa 1) | Dedução Faixa 5 (27,5%) |
|------|--------------------------|-------------------------|
| 2016-2022 | R$ 22.847,76 | R$ 10.432,32 |
| 2023 | R$ 24.511,92 | R$ 10.557,13 |
| 2024 | R$ 26.963,20 | R$ 10.740,98 |
| 2025 | R$ 28.467,20 | R$ 10.853,78 |

---

## Exemplos de Uso

### Listar anos disponíveis

```bash
GET /tributacao/anos?tipo=ANUAL
```

**Response:**
```json
[2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025]
```

### Buscar tributação de um ano

```bash
GET /tributacao/2024?tipo=ANUAL
```

**Response:** Ver DTO `TributacaoAnoDTO` acima.

### Criar/Atualizar tributação

```bash
POST /tributacao/2026?tipo=ANUAL
Content-Type: application/json

{
  "faixas": [...],
  "parametros": {...}
}
```

### Remover tributação

```bash
DELETE /tributacao/2026?tipo=ANUAL
```

---

## Fórmula de Cálculo

O imposto é calculado usando a tabela progressiva:

```
Imposto = (Base de Cálculo × Alíquota) - Dedução
```

**Exemplo para 2024 com base de R$ 100.000,00:**
- Faixa 5: Acima de R$ 55.976,16 → 27,5%
- Imposto = (100.000 × 0,275) - 10.740,98 = **R$ 16.759,02**
