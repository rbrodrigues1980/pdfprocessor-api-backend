# MASTER_BY_API.md
## Documentação Consolidada por API – Sistema de Extração e Consolidação de Contracheques (CAIXA + FUNCEF)

--------------------------------------------------------------------------------
# API 1 — Upload de Documento
## Endpoint
POST /api/v1/documents/upload

## Objetivo
Receber PDF + CPF, validar, criar documento em MongoDB e iniciar pipeline de processamento.

## Request (multipart/form-data)
- file: PDF (obrigatório)
- cpf: string (obrigatório)

## Response
{
  "documentId": "65f123abc",
  "status": "PENDING"
}

## Regras de Negócio
- Validar PDF
- Criar pessoa se não existir
- Criar payroll_document com status=PENDING
- Salvar PDF (GridFS)
- Enfileirar processamento (Reactor)

## Fluxograma
flowchart TD
  A[Upload PDF] --> B{Arquivo é PDF?}
  B -->|Não| X[Erro: PDF inválido]
  B -->|Sim| C[Salvar documento status=PENDING]
  C --> D[Associar ao CPF]
  D --> E[Iniciar processamento]
  E --> F[Retornar documentId]

## Modelos Usados
- persons
- payroll_documents

--------------------------------------------------------------------------------
# API 2 — Processar Documento
## Endpoint
POST /api/v1/documents/{id}/process

## Objetivo
Reprocessar documento manualmente.

## Response
{
  "documentId": "abc123",
  "status": "PROCESSING"
}

## Regras
- Resetar status
- Limpar entries
- Reenviar para o pipeline

## Fluxograma
flowchart TD
  A[Reprocessar documento] --> B[Resetar status]
  B --> C[Excluir entries antigas]
  C --> D[Reinserir na fila Reactor]
  D --> E[Retornar PROCESSING]

## Modelos Usados
- payroll_documents
- payroll_entries

--------------------------------------------------------------------------------
# API 3 — Listar Documentos de um CPF
## Endpoint
GET /api/v1/persons/{cpf}/documents

## Objetivo
Retornar todos os documentos associados ao CPF.

## Regras
- Buscar CPF
- Retornar documentos ordenados por data

## Modelos Usados
- persons
- payroll_documents

--------------------------------------------------------------------------------
# API 4 — Listar Rubricas Extraídas
## Endpoint
GET /api/v1/documents/{id}/entries

## Objetivo
Retornar todas as linhas extraídas de um documento.

## Response
[
  {
    "rubricaCodigo": "4482",
    "descricao": "CONTRIB EXTRAORDINÁRIA 2015",
    "referencia": "2017-08",
    "valor": 885.47
  }
]

## Regras
- Ordenar por referência
- Validar id

## Modelos Usados
- payroll_entries

--------------------------------------------------------------------------------
# API 5 — Consolidação por CPF
## Endpoint
GET /api/v1/persons/{cpf}/consolidated

## Objetivo
Retornar matriz consolidada no formato da planilha.

## Response Exemplo
{
  "cpf": "...",
  "matriz": {
    "4482": {
      "2017-01": 0,
      "2017-02": 0,
      "2017-03": 41.17
    }
  }
}

## Regras
- Consolidar entries
- Agrupar por rubrica e referência
- Preencher meses ausentes com 0

## Modelos Usados
- payroll_entries
- rubricas

--------------------------------------------------------------------------------
# API 6 — Exportar Excel
## Endpoint
GET /api/v1/persons/{cpf}/excel

## Objetivo
Gerar Excel idêntico ao modelo fornecido.

## Regras
- 12 meses por rubrica
- Totais
- IRPF (se implementado)
- Formatação BRL

## Modelos usados
- payroll_entries

--------------------------------------------------------------------------------
# API 7 — CRUD de Rubricas
## Endpoints
GET /api/v1/rubricas  
POST /api/v1/rubricas  
PUT /api/v1/rubricas/{codigo}  
DELETE /api/v1/rubricas/{codigo}

## Objetivo
Gerenciar as 24 rubricas permitidas no sistema.

## Modelos usados
- rubricas

--------------------------------------------------------------------------------
# Ordem recomendada de implementação
1. Upload de documento
2. Processamento automático
3. Extrator Caixa/Funcef
4. Regex e normalização
5. Geração de payroll_entries
6. Consolidado por CPF
7. Exportação Excel
8. CRUD de rubricas

--------------------------------------------------------------------------------
Fim do arquivo.
