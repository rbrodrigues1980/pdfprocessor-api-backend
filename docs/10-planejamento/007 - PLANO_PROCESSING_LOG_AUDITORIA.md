# Plano: Log de Processamento e Auditoria de Documentos

## Problema

Quando um documento PDF é processado, o sistema executa diversas etapas complexas em background (extração de texto, detecção de fontes ilegíveis, Gemini AI, validação, cross-validation, escalação para Gemini Pro). Atualmente:

1. **O usuário no frontend não vê nada** — Não há feedback do que está acontecendo durante o processamento
2. **Não há registro no banco** — Informações valiosas de cada etapa ficam apenas no log do servidor e se perdem
3. **Não há auditoria** — Não é possível consultar depois o que aconteceu com um documento específico

### Exemplo real do que se perdia antes

Para um contracheque da APCEF/CAIXA de 2016 com fontes sem mapeamento Unicode:

```
📛 Texto com baixa proporção alfanumérica (40,5% de 2491 chars). Provável PDF com fontes sem mapeamento Unicode.
🔍 Texto extraído insuficiente ou ilegível. Tentando Gemini AI...
🤖 Usando Gemini AI [gemini-2.5-flash] para extração ESTRUTURADA (JSON) da página 2...
✅ Gemini [gemini-2.5-flash] extraiu 17 rubricas em 22734ms. nome=FERNANDO RIBEIRO MARCHINI, competencia=06/2016, bruto=12773.73
📊 Validação reprovada (score 0.53). Issues: SOMA_PROVENTOS_INCORRETA, SOMA_DESCONTOS_INCORRETA
🔄 Cross-validation iniciada (score 0.53 < 0.85). Concluída: 22/22 campos (100%)
⬆️ Escalação para Gemini Pro [gemini-2.5-pro] — campos críticos divergentes
⚠️ Gemini Pro: Erro ao parsear JSON
✅ 13 entries salvas. 124 extraídas, 111 ignoradas (rubricas não cadastradas)
```

Agora tudo isso é **salvo no banco** dentro do `PayrollDocument` e **visível para o frontend**.

---

## Visão Geral da Solução

### Princípios implementados

- **Documento embarcado** — `processingLog` é uma `List<ProcessingEvent>` dentro do `PayrollDocument` (não é collection separada)
- **Mínimo impacto** — Apenas emissão de eventos nos pontos já existentes do fluxo
- **Dados reais extraídos** — Cada evento inclui o que a IA realmente viu (nome, CPF, competência, bruto, descontos, líquido)
- **Recursos identificados** — Modelo de IA usado (`gemini-2.5-flash`, `gemini-2.5-pro`), tempo de processamento
- **Thread-safe** — Helpers usam `synchronized` para processamento paralelo de páginas
- **Compatibilidade** — Documentos antigos retornam lista vazia, nenhuma migração necessária
- **Exclusão completa** — Ao deletar um documento, o `processingLog` é excluído junto (está dentro do documento)

---

## 1. Modelo de Dados (Implementado)

### `ProcessingEvent.java` — Evento de processamento

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingEvent {
    private Instant timestamp;
    private ProcessingEventType type;
    private ProcessingEventLevel level;
    private Integer page;               // null = evento geral do documento
    private String message;             // Mensagem legível para o usuário
    private Map<String, Object> details; // Dados extras estruturados
}
```

### `ProcessingEventType.java` — 19 tipos de evento

```java
public enum ProcessingEventType {
    PROCESSING_STARTED, PROCESSING_COMPLETED, PROCESSING_FAILED,
    TEXT_EXTRACTED, TEXT_UNREADABLE, TEXT_TOO_SHORT,
    GEMINI_EXTRACTION_STARTED, GEMINI_EXTRACTION_COMPLETED, GEMINI_EXTRACTION_FAILED,
    VALIDATION_PASSED, VALIDATION_FAILED,
    CROSS_VALIDATION_STARTED, CROSS_VALIDATION_COMPLETED,
    ESCALATION_TO_PRO, ESCALATION_COMPLETED, ESCALATION_FAILED,
    ENTRIES_EXTRACTED, ENTRIES_SAVED,
    RUBRICA_NOT_FOUND
}
```

### `ProcessingEventLevel.java`

```java
public enum ProcessingEventLevel { INFO, WARN, ERROR }
```

### `PayrollDocument.java` — Novo campo

```java
@Builder.Default
private List<ProcessingEvent> processingLog = new ArrayList<>();
```

---

## 2. Pontos de emissão implementados no `DocumentProcessUseCase`

### Eventos gerais do documento

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| `processDocumentAsync()` início | `PROCESSING_STARTED` | INFO | — |
| `processPages()` | `PROCESSING_STARTED` | INFO | `{totalPages}` |
| `processDocumentAsync()` sucesso | `PROCESSING_COMPLETED` | INFO | `{totalEntries, processingTimeMs}` |
| `processDocumentAsync()` erro | `PROCESSING_FAILED` | ERROR | `{errorMessage, processingTimeMs}` |

### Eventos por página — Extração de texto

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| `processPageWithMetadata()` texto legível | `TEXT_EXTRACTED` | INFO | `{textLength}` |
| `processPageWithMetadata()` texto ilegível | `TEXT_UNREADABLE` | WARN | `{textLength}` |

### Eventos por página — Gemini AI (com dados reais extraídos)

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| Antes de chamar Gemini | `GEMINI_EXTRACTION_STARTED` | INFO | `{model}` |
| Gemini retorna com sucesso | `GEMINI_EXTRACTION_COMPLETED` | INFO | `{model, processingTimeMs, rubricasCount, responseLength, nome, cpf, matricula, competencia, salarioBruto, totalDescontos, salarioLiquido}` |
| Gemini falha | `GEMINI_EXTRACTION_FAILED` | ERROR | `{model, processingTimeMs, errorMessage}` |

### Eventos por página — Validação

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| Score >= 0.85 | `VALIDATION_PASSED` | INFO | `{score, recommendation, rubricasCount, salarioBruto, totalDescontos, salarioLiquido, issues[{type, message, expected, found}]}` |
| Score < 0.85 | `VALIDATION_FAILED` | WARN | `{score, recommendation, rubricasCount, salarioBruto, totalDescontos, salarioLiquido, issues[{type, message, expected, found}]}` |

### Eventos por página — Cross-validation e Escalação

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| Cross-validation iniciada | `CROSS_VALIDATION_STARTED` | INFO | — |
| Cross-validation concluída | `CROSS_VALIDATION_COMPLETED` | INFO | `{score, matchedFields, totalFields, requiresManualReview, consolidatedEntries}` |
| Escalação para Pro | `ESCALATION_TO_PRO` | WARN | `{model, reason}` |
| Escalação sucesso | `ESCALATION_COMPLETED` | INFO | `{model, processingTimeMs, rubricasCount, nome, cpf, competencia, salarioBruto, totalDescontos, salarioLiquido}` |
| Escalação falha | `ESCALATION_FAILED` | WARN/ERROR | `{processingTimeMs, errorMessage}` |

### Eventos de resultado

| Método | Evento | Level | Dados em `details` |
|---|---|---|---|
| Consolidação de páginas | `ENTRIES_EXTRACTED` | INFO | `{totalExtracted, totalPages}` |
| Rubrica não cadastrada | `RUBRICA_NOT_FOUND` | WARN | `{rubrica, occurrences}` |
| Entries salvas | `ENTRIES_SAVED` | INFO | `{totalSaved, totalExtracted, totalIgnored}` |

---

## 3. Helpers implementados no `DocumentProcessUseCase`

```java
private void addEvent(PayrollDocument document, ProcessingEventType type,
                      ProcessingEventLevel level, Integer page, String message,
                      Map<String, Object> details) {
    ProcessingEvent event = ProcessingEvent.builder()
            .timestamp(Instant.now())
            .type(type).level(level).page(page)
            .message(message).details(details)
            .build();
    synchronized (document.getProcessingLog()) {  // Thread-safe para processamento paralelo
        document.getProcessingLog().add(event);
    }
}

private void addInfoEvent(PayrollDocument doc, Integer page, ProcessingEventType type, String msg) { ... }
private void addInfoEvent(PayrollDocument doc, Integer page, ProcessingEventType type, String msg, Map<String, Object> details) { ... }
private void addWarnEvent(PayrollDocument doc, Integer page, ProcessingEventType type, String msg, Map<String, Object> details) { ... }
private void addErrorEvent(PayrollDocument doc, Integer page, ProcessingEventType type, String msg, Map<String, Object> details) { ... }
```

---

## 4. Alterações adicionais realizadas

### `AiPdfExtractionService.java` — Novos métodos na interface

```java
default String getPrimaryModelName() { return "unknown"; }
default String getFallbackModelName() { return "unknown"; }
```

Já implementados no `GeminiPdfServiceImpl` (retornam `config.getModel()` e `config.getFallbackModel()`).
Usados para identificar qual modelo de IA foi usado em cada evento.

### `DocumentController.java` — Fix na exclusão de documentos

O endpoint `DELETE /documents/{id}` foi corrigido para usar o `DeleteDocumentUseCase` (completo e tenant-aware) em vez do antigo `DocumentDeleteUseCase` (legado). Agora ambos os endpoints de exclusão fazem a limpeza completa:
- PayrollEntry (entries)
- GridFS (fs.files + fs.chunks)
- Referência na Person
- PayrollDocument (com processingLog embarcado)

### `DeleteDocumentUseCase.java` — Resiliência

Corrigido para não falhar quando a `Person` não é encontrada (ex: documento sem CPF). Agora faz log de warning e continua a exclusão.

---

## 5. Considerações Técnicas

### Tamanho do documento no MongoDB

- Cada `ProcessingEvent` tem ~300-800 bytes (maior agora com dados extraídos)
- Um PDF de 15 páginas gera ~50-150 eventos
- Total estimado: **40KB-120KB** por documento
- Limite do MongoDB: **16MB** — Impacto: **desprezível**

### Performance

- Eventos adicionados in-memory via `synchronized` durante processamento
- Salvos junto com o `PayrollDocument` no `save()` final
- Zero writes adicionais ao MongoDB durante processamento
- Para listagens, `processingLog` deve ser excluído via projection (Sprint 2)

### Compatibilidade

- Documentos antigos: `processingLog` retorna `[]`
- Campo usa `@Builder.Default` com `new ArrayList<>()`
- Nenhuma migração necessária

---

## 6. Estrutura de Arquivos

### Novos arquivos criados (Sprint 1)

```
src/main/java/br/com/verticelabs/pdfprocessor/
├── domain/model/
│   ├── ProcessingEvent.java           ✅ Criado
│   ├── ProcessingEventType.java       ✅ Criado
│   └── ProcessingEventLevel.java      ✅ Criado
```

### Arquivos alterados (Sprint 1)

```
src/main/java/br/com/verticelabs/pdfprocessor/
├── domain/model/
│   └── PayrollDocument.java           ✅ + campo processingLog
├── domain/service/
│   └── AiPdfExtractionService.java    ✅ + getPrimaryModelName(), getFallbackModelName()
├── application/documents/
│   ├── DocumentProcessUseCase.java    ✅ + helpers + 16 pontos de emissão
│   ├── DeleteDocumentUseCase.java     ✅ Fix resiliência (Person não encontrada)
│   └── DocumentDeleteUseCase.java     ✅ Marcado @Deprecated
├── interfaces/documents/
│   └── DocumentController.java        ✅ Fix: usa DeleteDocumentUseCase (completo)
```

### Documentação criada

```
docs/
├── 06-guia-frontend/
│   ├── README.md                                  ✅ Atualizado (link para 013)
│   └── 013 - API_PROCESSING_LOG_FRONTEND.md       ✅ Criado (guia completo para frontend)
```

---

## 7. Ordem de Implementação

### Sprint 1 — Modelo e emissão de eventos ✅ CONCLUÍDA

- [x] Criar `ProcessingEvent.java`
- [x] Criar `ProcessingEventType.java`
- [x] Criar `ProcessingEventLevel.java`
- [x] Adicionar campo `processingLog` no `PayrollDocument`
- [x] Criar métodos helper (`addEvent`, `addInfoEvent`, `addWarnEvent`, `addErrorEvent`) com `synchronized`
- [x] Adicionar emissão de eventos em todos os pontos mapeados (16 pontos)
- [x] Enriquecer eventos com dados reais extraídos (nome, CPF, competência, bruto, descontos, líquido)
- [x] Adicionar nome do modelo de IA (`gemini-2.5-flash`, `gemini-2.5-pro`) nos eventos
- [x] Expor `getPrimaryModelName()` e `getFallbackModelName()` na interface `AiPdfExtractionService`
- [x] Fix: `DocumentController` usar `DeleteDocumentUseCase` (exclusão completa)
- [x] Fix: `DeleteDocumentUseCase` resiliente quando Person não encontrada
- [x] Compilação OK, zero erros

**Entregável:** Eventos com dados reais sendo salvos no MongoDB para cada documento processado.

### Sprint 2 — Expor processingLog via API ✅ CONCLUÍDA

- [x] Adicionar campo `processingLog` ao `DocumentResponse` (DTO do `GET /documents/{id}`)
- [x] Mapear `processingLog` no `DocumentQueryUseCase.findById()`
- [x] Excluir `processingLog` das listagens — `toDocumentResponse()` não mapeia (campo fica `null`), `DocumentListItemResponse` não tem o campo

**Comportamento por endpoint:**

| Endpoint | `processingLog` | Motivo |
|---|---|---|
| `GET /documents/{id}` | **Incluso** (lista de eventos) | Detalhe — mapeado em `findById()` |
| `GET /documents` (filtros) | **null** | Listagem — `toDocumentResponse()` não mapeia |
| `GET /persons/{cpf}/documents` | **Ausente** | Listagem — `DocumentListItemResponse` não tem o campo |
| `GET /persons/{personId}/documents-by-id` | **Ausente** | Listagem — `DocumentListItemResponse` não tem o campo |

**Arquivos alterados:**
- `DocumentResponse.java` — `+ List<ProcessingEvent> processingLog`
- `DocumentQueryUseCase.java` — `findById()` mapeia `processingLog` do documento

> **Nota:** O resumo (`ProcessingSummary`) é calculado no frontend a partir do `processingLog` (conforme guia `013 - API_PROCESSING_LOG_FRONTEND.md`). Não foi necessário criar DTO de resumo no backend.

**Entregável:** `processingLog` acessível via API no detalhe do documento, oculto nas listagens.

### Sprint 3 — Frontend ← PRÓXIMA

- [x] Criar documentação da API para o frontend (`013 - API_PROCESSING_LOG_FRONTEND.md`)
- [ ] Implementar componente `ProcessingTimeline` (timeline vertical com ícones)
- [ ] Implementar componente `ProcessingSummaryCard` (resumo rápido)
- [ ] Implementar componente `PageEventsAccordion` (eventos agrupados por página)
- [ ] Implementar polling durante `status === 'PROCESSING'`
- [ ] Implementar filtros por nível, página e tipo de evento
- [ ] Tratar `processingLog` como opcional (documentos antigos)

**Entregável:** Visibilidade completa no frontend.
