# 013 — Processing Log (Log de Processamento / Auditoria)

> Documentação **completa** para o frontend implementar a visualização do histórico de processamento de documentos PDF.
> Inclui: interfaces TypeScript, componentes React, helpers, exemplos cURL, e guia de implementação passo a passo.

---

## 1. O que é o Processing Log?

Cada documento PDF processado agora carrega um **log detalhado** de tudo que aconteceu durante o processamento, salvo no campo `processingLog`. Esse log contém:

- Qual recurso foi usado em cada página (PDFBox texto, Gemini Flash, Gemini Pro)
- O que a IA extraiu (nome, CPF, competência, salário bruto, descontos, líquido)
- Resultado da validação (score 0-1, issues, recomendação ACCEPT/REVIEW/REJECT)
- Cross-validation (campos comparados, divergências)
- Escalação para Gemini Pro (quando Flash não foi suficiente)
- Rubricas não cadastradas no sistema
- Resultado final (entries salvas, ignoradas, tempo total)

---

## 2. Onde o `processingLog` aparece

| Endpoint | `processingLog` | Comportamento |
|---|---|---|
| `GET /api/v1/documents/{id}` | **Presente** | Lista completa de eventos |
| `GET /api/v1/documents/{id}/processing-status` | **Presente** | **NOVO** — Endpoint leve para polling em tempo real |
| `GET /api/v1/documents` (filtros) | `null` | Não retornado em listagens |
| `GET /api/v1/persons/{cpf}/documents` | **Ausente** | DTO `DocumentListItemResponse` não tem o campo |
| `GET /api/v1/persons/{personId}/documents-by-id` | **Ausente** | DTO `DocumentListItemResponse` não tem o campo |

**Regra:** O `processingLog` só vem quando você busca o **detalhe** de um documento específico ou usa o endpoint de polling.

### 2.1. Endpoint de polling — `GET /api/v1/documents/{id}/processing-status`

**Novo endpoint otimizado para polling durante processamento.** Retorna apenas os campos necessários para acompanhar o progresso:

```typescript
interface ProcessingStatusResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  totalPages: number | null;       // Total de páginas do PDF (extraído do evento PROCESSING_STARTED)
  erro: string | null;             // Mensagem de erro (se status === 'ERROR')
  processingLog: ProcessingEvent[];  // Lista de eventos acumulados até o momento
}
```

**Importante:** O processingLog cresce em tempo real. Cada página processada gera um save intermediário no MongoDB, então o frontend pode fazer polling a cada 3-5 segundos e ver novos eventos aparecendo.

```bash
# Teste rápido — polling do status
curl -s -H "Authorization: Bearer SEU_TOKEN" \
  http://localhost:8081/api/v1/documents/SEU_DOCUMENT_ID/processing-status | jq '{status, totalPages, eventos: (.processingLog | length)}'
```

---

## 3. Teste rápido com cURL

### Buscar documento com processingLog

```bash
curl -s -H "Authorization: Bearer SEU_TOKEN" \
  http://localhost:8081/api/v1/documents/SEU_DOCUMENT_ID | jq '.processingLog'
```

### Verificar que listagem NÃO retorna processingLog

```bash
curl -s -H "Authorization: Bearer SEU_TOKEN" \
  http://localhost:8081/api/v1/documents | jq '.[0].processingLog'
# Resultado esperado: null
```

---

## 4. Interfaces TypeScript

### 4.1. Tipos base

```typescript
// ============================================================
// PROCESSING LOG — Tipos e Interfaces
// Copiar para: src/types/processingLog.ts
// ============================================================

/** Tipos de evento de processamento */
export type ProcessingEventType =
  // Início/Fim
  | 'PROCESSING_STARTED'
  | 'PROCESSING_COMPLETED'
  | 'PROCESSING_FAILED'
  // Extração de texto
  | 'TEXT_EXTRACTED'
  | 'TEXT_UNREADABLE'
  | 'TEXT_TOO_SHORT'
  // Gemini AI
  | 'GEMINI_EXTRACTION_STARTED'
  | 'GEMINI_EXTRACTION_COMPLETED'
  | 'GEMINI_EXTRACTION_FAILED'
  // Validação
  | 'VALIDATION_PASSED'
  | 'VALIDATION_FAILED'
  // Cross-validation
  | 'CROSS_VALIDATION_STARTED'
  | 'CROSS_VALIDATION_COMPLETED'
  // Escalação
  | 'ESCALATION_TO_PRO'
  | 'ESCALATION_COMPLETED'
  | 'ESCALATION_FAILED'
  // Multi-page retry
  | 'MULTIPAGE_RETRY_STARTED'
  | 'MULTIPAGE_RETRY_COMPLETED'
  | 'MULTIPAGE_RETRY_FAILED'
  // Resultado
  | 'ENTRIES_EXTRACTED'
  | 'ENTRIES_SAVED'
  // Rubricas
  | 'RUBRICA_NOT_FOUND';

/** Nível de severidade do evento */
export type ProcessingEventLevel = 'INFO' | 'WARN' | 'ERROR';

/** Evento de processamento */
export interface ProcessingEvent {
  timestamp: string;                          // ISO 8601
  type: ProcessingEventType;
  level: ProcessingEventLevel;
  page: number | null;                        // null = evento geral do documento
  message: string;                            // Mensagem legível para o usuário
  details: Record<string, any> | null;        // Dados extras estruturados
}

/** Resumo calculado a partir do processingLog */
export interface ProcessingSummary {
  totalPages: number;
  pagesWithGemini: number;
  pagesWithText: number;
  totalExtracted: number;
  totalSaved: number;
  totalIgnored: number;
  totalUnregisteredRubricas: number;
  totalErrors: number;
  totalWarnings: number;
  processingTimeMs: number;
  hadCrossValidation: boolean;
  hadEscalationToPro: boolean;
  success: boolean;
  extractionMethod: string;                   // "REGEX", "GEMINI_FLASH", "GEMINI_PRO", "MIXED"
}
```

### 4.2. DocumentResponse atualizado

```typescript
export interface DocumentResponse {
  id: string;
  cpf: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo: 'CAIXA' | 'FUNCEF' | 'CAIXA_FUNCEF' | 'INCOME_TAX';
  ano: number | null;
  entriesCount: number | null;
  dataUpload: string;
  dataProcessamento: string | null;
  erro: string | null;
  // --- NOVO CAMPO ---
  processingLog?: ProcessingEvent[] | null;   // Presente apenas em GET /documents/{id}
}
```

### 4.3. DocumentListItemResponse atualizado (listagem com progresso)

> **NOVO** — O DTO de listagem de documentos agora inclui campos de progresso em tempo real quando o documento está em `PROCESSING`. O backend calcula esses campos automaticamente a partir do `processingLog` salvo intermediariamente.

```typescript
export interface DocumentListItemResponse {
  id: string;
  ano: number | null;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  tipo: string;
  mesesDetectados: string[];
  dataUpload: string;
  dataProcessamento: string | null;
  totalEntries: number | null;

  // === Campos de progresso (presentes quando status === 'PROCESSING') ===
  totalPages?: number;             // Total de páginas do PDF
  pagesProcessed?: number;         // Páginas já processadas
  progressPercent?: number;        // Percentual de progresso (0-100)
  lastEventMessage?: string;       // Última mensagem (ex: "Gemini [flash] extraiu 17 rubricas...")
  lastEventType?: string;          // Tipo do último evento (ex: "GEMINI_EXTRACTION_COMPLETED")
  eventsCount?: number;            // Total de eventos no processingLog
}
```

**Exemplo de resposta quando `PROCESSING`:**

```json
{
  "id": "69939480600d151a455ace9b",
  "ano": 2016,
  "status": "PROCESSING",
  "tipo": "CAIXA",
  "totalPages": 15,
  "pagesProcessed": 7,
  "progressPercent": 47,
  "lastEventMessage": "Gemini [gemini-2.5-flash] extraiu 17 rubricas em 22734ms. nome=FERNANDO RIBEIRO MARCHINI, competencia=06/2016",
  "lastEventType": "GEMINI_EXTRACTION_COMPLETED",
  "eventsCount": 45
}
```

O frontend pode usar esses campos para:
1. Mostrar a barra de progresso (47%)
2. Mostrar "Página 7 de 15"
3. Mostrar a última mensagem como "créditos" rolando
4. Fazer polling da listagem de documentos a cada 4-5 segundos quando algum documento está em PROCESSING

---

## 5. Estrutura dos `details` por tipo de evento

Cada evento tem um campo `details` com dados estruturados. Abaixo, o que esperar de cada tipo:

### Início/Fim

| Tipo | Campos em `details` |
|---|---|
| `PROCESSING_STARTED` | `{ totalPages: number }` |
| `PROCESSING_COMPLETED` | `{ totalEntries: number, processingTimeMs: number }` |
| `PROCESSING_FAILED` | `{ errorMessage: string, processingTimeMs: number }` |

### Extração de texto

| Tipo | Campos em `details` |
|---|---|
| `TEXT_EXTRACTED` | `{ textLength: number }` |
| `TEXT_UNREADABLE` | `{ textLength: number }` |

### Gemini AI — **contém dados reais extraídos + todas as rubricas**

| Tipo | Campos em `details` |
|---|---|
| `GEMINI_EXTRACTION_STARTED` | `{ model: string }` |
| `GEMINI_EXTRACTION_COMPLETED` | `{ model, processingTimeMs, rubricasCount, responseLength, nome?, cpf?, matricula?, competencia?, salarioBruto?, totalDescontos?, salarioLiquido?, rubricas?: ExtractedRubrica[] }` |
| `GEMINI_EXTRACTION_FAILED` | `{ model, processingTimeMs, errorMessage }` |

> **NOVO:** O campo `rubricas` contém **TODAS** as rubricas extraídas pela IA naquela página (não apenas as validadas/salvas). Isso permite auditoria completa sem perda de dados. Cada rubrica tem a estrutura:

```typescript
interface ExtractedRubrica {
  codigo: string;       // Código da rubrica (ex: "4346")
  descricao?: string;   // Descrição (ex: "FUNCEF - NOVO PLANO")
  valor?: number;       // Valor monetário
  referencia?: string;  // Referência/competência (ex: "2016-01")
}
```

### Validação

| Tipo | Campos em `details` |
|---|---|
| `VALIDATION_PASSED` | `{ score, recommendation, rubricasCount, salarioBruto?, totalDescontos?, salarioLiquido?, issues?: [{type, message, expected?, found?}] }` |
| `VALIDATION_FAILED` | Mesma estrutura de VALIDATION_PASSED |

### Cross-validation

| Tipo | Campos em `details` |
|---|---|
| `CROSS_VALIDATION_STARTED` | Sem details (apenas message) |
| `CROSS_VALIDATION_COMPLETED` | `{ score, matchedFields, totalFields, requiresManualReview, consolidatedEntries }` |

### Escalação para Gemini Pro

| Tipo | Campos em `details` |
|---|---|
| `ESCALATION_TO_PRO` | `{ model, reason }` |
| `ESCALATION_COMPLETED` | `{ model, processingTimeMs, rubricasCount, nome?, cpf?, competencia?, salarioBruto?, totalDescontos?, salarioLiquido?, rubricas?: ExtractedRubrica[] }` |
| `ESCALATION_FAILED` | `{ processingTimeMs, errorMessage? }` |

> **NOVO:** O campo `rubricas` no `ESCALATION_COMPLETED` também contém todas as rubricas extraídas pelo Gemini Pro.

### Multi-page retry

| Tipo | Campos em `details` |
|---|---|
| `MULTIPAGE_RETRY_STARTED` | `{ strategy: string }` |
| `MULTIPAGE_RETRY_COMPLETED` | `{ rubricasCount, processingTimeMs, strategy, pages? }` |
| `MULTIPAGE_RETRY_FAILED` | `{ strategy, errorMessage? }` |

### Resultado final

| Tipo | Campos em `details` |
|---|---|
| `ENTRIES_EXTRACTED` | `{ totalExtracted, totalPages }` |
| `ENTRIES_SAVED` | `{ totalSaved, totalExtracted, totalIgnored }` |
| `RUBRICA_NOT_FOUND` | `{ rubrica: string, occurrences: number }` |

---

## 6. Funções Helper

### 6.1. Calcular resumo a partir do log

```typescript
// Copiar para: src/utils/processingLogHelpers.ts

import { ProcessingEvent, ProcessingSummary } from '../types/processingLog';

/**
 * Calcula um resumo a partir da lista de eventos.
 * Usar para exibir no card/resumo do documento sem precisar de endpoint extra.
 */
export function buildProcessingSummary(log: ProcessingEvent[]): ProcessingSummary {
  if (!log || log.length === 0) {
    return {
      totalPages: 0, pagesWithGemini: 0, pagesWithText: 0,
      totalExtracted: 0, totalSaved: 0, totalIgnored: 0,
      totalUnregisteredRubricas: 0, totalErrors: 0, totalWarnings: 0,
      processingTimeMs: 0, hadCrossValidation: false,
      hadEscalationToPro: false, success: false, extractionMethod: 'N/A',
    };
  }

  const startedWithPages = log.find(
    e => e.type === 'PROCESSING_STARTED' && e.details?.totalPages
  );
  const completed = log.find(e => e.type === 'PROCESSING_COMPLETED');
  const failed = log.find(e => e.type === 'PROCESSING_FAILED');
  const saved = log.find(e => e.type === 'ENTRIES_SAVED');
  const extracted = log.find(e => e.type === 'ENTRIES_EXTRACTED');
  const rubricasNotFound = log.filter(e => e.type === 'RUBRICA_NOT_FOUND');
  const geminiPages = log.filter(e => e.type === 'GEMINI_EXTRACTION_COMPLETED');
  const textPages = log.filter(e => e.type === 'TEXT_EXTRACTED');
  const errors = log.filter(e => e.level === 'ERROR');
  const warnings = log.filter(e => e.level === 'WARN');

  // Determinar método de extração predominante
  let extractionMethod = 'N/A';
  if (geminiPages.length > 0 && textPages.length > 0) {
    extractionMethod = 'MIXED';
  } else if (geminiPages.length > 0) {
    const hasProEscalation = log.some(e => e.type === 'ESCALATION_COMPLETED');
    extractionMethod = hasProEscalation ? 'GEMINI_PRO' : 'GEMINI_FLASH';
  } else if (textPages.length > 0) {
    extractionMethod = 'REGEX';
  }

  return {
    totalPages: startedWithPages?.details?.totalPages ?? 0,
    pagesWithGemini: geminiPages.length,
    pagesWithText: textPages.length,
    totalExtracted: extracted?.details?.totalExtracted ?? saved?.details?.totalExtracted ?? 0,
    totalSaved: saved?.details?.totalSaved ?? completed?.details?.totalEntries ?? 0,
    totalIgnored: saved?.details?.totalIgnored ?? 0,
    totalUnregisteredRubricas: rubricasNotFound.length,
    totalErrors: errors.length,
    totalWarnings: warnings.length,
    processingTimeMs: completed?.details?.processingTimeMs ?? failed?.details?.processingTimeMs ?? 0,
    hadCrossValidation: log.some(e => e.type === 'CROSS_VALIDATION_STARTED'),
    hadEscalationToPro: log.some(e => e.type === 'ESCALATION_TO_PRO'),
    success: !!completed,
    extractionMethod,
  };
}
```

### 6.2. Agrupar eventos por página

```typescript
/**
 * Agrupa eventos por número de página.
 * Retorna Map onde key=null são eventos gerais e key=número são de páginas específicas.
 */
export function groupEventsByPage(
  log: ProcessingEvent[]
): Map<number | null, ProcessingEvent[]> {
  const groups = new Map<number | null, ProcessingEvent[]>();

  for (const event of log) {
    const key = event.page;
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key)!.push(event);
  }

  return groups;
}

/**
 * Variante que retorna array ordenado para renderizar.
 * Ordem: Geral primeiro, depois páginas em ordem numérica, rubricas não encontradas por último.
 */
export function getGroupedEventsForDisplay(log: ProcessingEvent[]): Array<{
  label: string;
  page: number | null;
  events: ProcessingEvent[];
}> {
  const grouped = groupEventsByPage(log);
  const result: Array<{ label: string; page: number | null; events: ProcessingEvent[] }> = [];

  // 1. Eventos gerais (page === null), exceto RUBRICA_NOT_FOUND
  const generalEvents = (grouped.get(null) || []).filter(e => e.type !== 'RUBRICA_NOT_FOUND');
  if (generalEvents.length > 0) {
    result.push({ label: 'Geral', page: null, events: generalEvents });
  }

  // 2. Páginas em ordem numérica
  const pageNumbers = Array.from(grouped.keys())
    .filter((k): k is number => k !== null)
    .sort((a, b) => a - b);

  for (const pageNum of pageNumbers) {
    result.push({
      label: `Página ${pageNum}`,
      page: pageNum,
      events: grouped.get(pageNum)!,
    });
  }

  // 3. Rubricas não cadastradas (agrupadas no final)
  const rubricaEvents = (grouped.get(null) || []).filter(e => e.type === 'RUBRICA_NOT_FOUND');
  if (rubricaEvents.length > 0) {
    result.push({ label: `Rubricas não cadastradas (${rubricaEvents.length})`, page: null, events: rubricaEvents });
  }

  return result;
}
```

### 6.3. Helpers de formatação

```typescript
/** Formata milliseconds para leitura humana */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.round((ms % 60000) / 1000);
  return `${minutes}min ${seconds}s`;
}

/** Retorna cor CSS pelo nível do evento */
export function getLevelColor(level: ProcessingEventLevel): string {
  switch (level) {
    case 'INFO': return '#3b82f6';   // blue-500
    case 'WARN': return '#f59e0b';   // amber-500
    case 'ERROR': return '#ef4444';  // red-500
    default: return '#6b7280';       // gray-500
  }
}

/** Retorna cor de background pelo nível do evento */
export function getLevelBgColor(level: ProcessingEventLevel): string {
  switch (level) {
    case 'INFO': return '#eff6ff';   // blue-50
    case 'WARN': return '#fffbeb';   // amber-50
    case 'ERROR': return '#fef2f2';  // red-50
    default: return '#f9fafb';       // gray-50
  }
}

/** Retorna ícone pelo nível */
export function getLevelIcon(level: ProcessingEventLevel): string {
  switch (level) {
    case 'INFO': return 'ℹ️';
    case 'WARN': return '⚠️';
    case 'ERROR': return '❌';
    default: return '•';
  }
}

/** Retorna label legível pelo tipo de evento */
export function getEventTypeLabel(type: ProcessingEventType): string {
  const labels: Record<ProcessingEventType, string> = {
    PROCESSING_STARTED: 'Processamento iniciado',
    PROCESSING_COMPLETED: 'Processamento concluído',
    PROCESSING_FAILED: 'Processamento falhou',
    TEXT_EXTRACTED: 'Texto extraído (PDFBox)',
    TEXT_UNREADABLE: 'Texto ilegível',
    TEXT_TOO_SHORT: 'Texto muito curto',
    GEMINI_EXTRACTION_STARTED: 'Gemini AI iniciado',
    GEMINI_EXTRACTION_COMPLETED: 'Gemini AI concluído',
    GEMINI_EXTRACTION_FAILED: 'Gemini AI falhou',
    VALIDATION_PASSED: 'Validação aprovada',
    VALIDATION_FAILED: 'Validação reprovada',
    CROSS_VALIDATION_STARTED: 'Cross-validation iniciada',
    CROSS_VALIDATION_COMPLETED: 'Cross-validation concluída',
    ESCALATION_TO_PRO: 'Escalação para Gemini Pro',
    ESCALATION_COMPLETED: 'Escalação concluída',
    ESCALATION_FAILED: 'Escalação falhou',
    MULTIPAGE_RETRY_STARTED: 'Retry multi-página iniciado',
    MULTIPAGE_RETRY_COMPLETED: 'Retry multi-página concluído',
    MULTIPAGE_RETRY_FAILED: 'Retry multi-página falhou',
    ENTRIES_EXTRACTED: 'Rubricas extraídas',
    ENTRIES_SAVED: 'Entries salvas',
    RUBRICA_NOT_FOUND: 'Rubrica não cadastrada',
  };
  return labels[type] || type;
}

/** Formata timestamp para exibição local */
export function formatEventTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** Formata timestamp completo */
export function formatEventDateTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** Método de extração para texto legível */
export function getExtractionMethodLabel(method: string): string {
  switch (method) {
    case 'REGEX': return 'Parser Regex (texto digital)';
    case 'GEMINI_FLASH': return 'Gemini AI (Flash)';
    case 'GEMINI_PRO': return 'Gemini AI (Flash + Pro)';
    case 'MIXED': return 'Misto (Regex + Gemini AI)';
    default: return method;
  }
}
```

---

## 7. Hook React — Polling durante processamento

### 7.1. Hook `useProcessingStatus` — **USA O ENDPOINT LEVE**

```typescript
// Copiar para: src/hooks/useProcessingStatus.ts

import { useState, useEffect, useCallback, useRef } from 'react';
import { ProcessingEvent } from '../types/processingLog';
import { api } from '../services/api';

interface ProcessingStatusResponse {
  documentId: string;
  status: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  totalPages: number | null;
  erro: string | null;
  processingLog: ProcessingEvent[];
}

interface UseProcessingStatusOptions {
  /** ID do documento */
  documentId: string;
  /** Habilitar polling (true = ativo) */
  enabled: boolean;
  /** Intervalo em ms (padrão: 4000) */
  intervalMs?: number;
  /** Callback quando processamento terminar */
  onComplete?: (status: ProcessingStatusResponse) => void;
  /** Callback quando processamento falhar */
  onError?: (status: ProcessingStatusResponse) => void;
}

export function useProcessingStatus({
  documentId,
  enabled,
  intervalMs = 4000,
  onComplete,
  onError,
}: UseProcessingStatusOptions) {
  const [status, setStatus] = useState<ProcessingStatusResponse | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const previousEventsCount = useRef(0);

  const fetchStatus = useCallback(async () => {
    try {
      // Usa o endpoint leve, otimizado para polling
      const response = await api.get<ProcessingStatusResponse>(
        `/documents/${documentId}/processing-status`
      );
      setStatus(response.data);

      // Verificar se processamento terminou
      if (response.data.status === 'PROCESSED') {
        setIsPolling(false);
        onComplete?.(response.data);
      } else if (response.data.status === 'ERROR') {
        setIsPolling(false);
        onError?.(response.data);
      }

      return response.data;
    } catch (err) {
      console.error('Erro ao buscar status de processamento:', err);
      return null;
    }
  }, [documentId, onComplete, onError]);

  useEffect(() => {
    if (!enabled) {
      setIsPolling(false);
      return;
    }

    setIsPolling(true);

    // Buscar imediatamente
    fetchStatus();

    // Polling com intervalo
    const interval = setInterval(fetchStatus, intervalMs);

    return () => {
      clearInterval(interval);
      setIsPolling(false);
    };
  }, [enabled, fetchStatus, intervalMs]);

  // Calcular progresso baseado nos eventos
  const processingLog = status?.processingLog ?? [];
  const totalPages = status?.totalPages ?? 0;
  const completedPages = new Set(
    processingLog
      .filter(e => e.type === 'GEMINI_EXTRACTION_COMPLETED' && e.page !== null)
      .map(e => e.page)
  ).size;
  const progressPercent = totalPages > 0 ? Math.round((completedPages / totalPages) * 100) : 0;

  // Detectar novos eventos (para animação)
  const hasNewEvents = processingLog.length > previousEventsCount.current;
  previousEventsCount.current = processingLog.length;

  // Último evento (para mostrar status atual)
  const latestEvent = processingLog.length > 0
    ? processingLog[processingLog.length - 1]
    : null;

  return {
    status,
    isPolling,
    /** Progresso como percentual (0-100) */
    progressPercent,
    /** Total de páginas do PDF */
    totalPages,
    /** Páginas já processadas */
    completedPages,
    /** Último evento do log */
    latestEvent,
    /** Se houve novos eventos desde o último poll */
    hasNewEvents,
    /** Número de eventos no log atual */
    eventsCount: processingLog.length,
    /** Últimos N eventos */
    latestEvents: (n: number) => processingLog.slice(-n),
    /** Status do documento */
    documentStatus: status?.status ?? 'PENDING',
    /** Erro (se houver) */
    erro: status?.erro ?? null,
  };
}
```

### 7.2. Hook legado `useDocumentPolling` — Usa GET /documents/{id}

> **Nota:** Prefira `useProcessingStatus` (Seção 7.1) para polling durante processamento. Este hook retorna o documento completo e é mais pesado.

```typescript
// Copiar para: src/hooks/useDocumentPolling.ts

import { useState, useEffect, useCallback } from 'react';
import { DocumentResponse } from '../types/document';
import { api } from '../services/api';

interface UseDocumentPollingOptions {
  documentId: string;
  enabled: boolean;
  intervalMs?: number;
  onComplete?: (document: DocumentResponse) => void;
  onError?: (document: DocumentResponse) => void;
}

export function useDocumentPolling({
  documentId,
  enabled,
  intervalMs = 4000,
  onComplete,
  onError,
}: UseDocumentPollingOptions) {
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [isPolling, setIsPolling] = useState(false);

  const fetchDocument = useCallback(async () => {
    try {
      const response = await api.get<DocumentResponse>(`/documents/${documentId}`);
      setDocument(response.data);

      if (response.data.status === 'PROCESSED') {
        setIsPolling(false);
        onComplete?.(response.data);
      } else if (response.data.status === 'ERROR') {
        setIsPolling(false);
        onError?.(response.data);
      }

      return response.data;
    } catch (err) {
      console.error('Erro ao buscar documento:', err);
      return null;
    }
  }, [documentId, onComplete, onError]);

  useEffect(() => {
    if (!enabled) {
      setIsPolling(false);
      return;
    }

    setIsPolling(true);
    fetchDocument();
    const interval = setInterval(fetchDocument, intervalMs);

    return () => {
      clearInterval(interval);
      setIsPolling(false);
    };
  }, [enabled, fetchDocument, intervalMs]);

  return {
    document,
    isPolling,
    eventsCount: document?.processingLog?.length ?? 0,
    latestEvents: (n: number) =>
      document?.processingLog?.slice(-(n)) ?? [],
  };
}
```

---

## 8. Componentes React — Implementação de referência

### 8.1. ProcessingSummaryCard — Resumo rápido no card do documento

```tsx
// Copiar para: src/components/ProcessingSummaryCard.tsx

import React from 'react';
import { ProcessingEvent } from '../types/processingLog';
import {
  buildProcessingSummary,
  formatDuration,
  getExtractionMethodLabel,
} from '../utils/processingLogHelpers';

interface Props {
  processingLog: ProcessingEvent[] | null | undefined;
  status: string;
}

export const ProcessingSummaryCard: React.FC<Props> = ({ processingLog, status }) => {
  if (!processingLog || processingLog.length === 0) {
    if (status === 'PENDING') return <p>Documento aguardando processamento.</p>;
    if (status === 'PROCESSING') return <p>Processamento em andamento...</p>;
    return <p>Sem informações de processamento disponíveis.</p>;
  }

  const summary = buildProcessingSummary(processingLog);

  return (
    <div style={{ padding: 16, border: '1px solid #e5e7eb', borderRadius: 8 }}>
      <h3>Resumo do Processamento</h3>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 12 }}>
        <div>
          <strong>Método:</strong> {getExtractionMethodLabel(summary.extractionMethod)}
        </div>
        <div>
          <strong>Páginas:</strong> {summary.totalPages}
          {summary.pagesWithGemini > 0 && ` (${summary.pagesWithGemini} via IA)`}
          {summary.pagesWithText > 0 && ` (${summary.pagesWithText} via texto)`}
        </div>
        <div>
          <strong>Tempo:</strong> {formatDuration(summary.processingTimeMs)}
        </div>
        <div>
          <strong>Rubricas:</strong> {summary.totalExtracted} extraídas →{' '}
          {summary.totalSaved} salvas
          {summary.totalIgnored > 0 && `, ${summary.totalIgnored} ignoradas`}
        </div>
      </div>

      {/* Alertas */}
      <div style={{ marginTop: 12 }}>
        {summary.totalUnregisteredRubricas > 0 && (
          <div style={{ color: '#f59e0b' }}>
            ⚠️ {summary.totalUnregisteredRubricas} rubricas não cadastradas no sistema
          </div>
        )}
        {summary.totalErrors > 0 && (
          <div style={{ color: '#ef4444' }}>
            ❌ {summary.totalErrors} erro(s) durante o processamento
          </div>
        )}
        {summary.hadCrossValidation && (
          <div style={{ color: '#3b82f6' }}>
            ℹ️ Cross-validation foi acionada
          </div>
        )}
        {summary.hadEscalationToPro && (
          <div style={{ color: '#f59e0b' }}>
            ⚠️ Escalação para Gemini Pro foi necessária
          </div>
        )}
      </div>

      {!summary.success && status === 'ERROR' && (
        <div style={{ marginTop: 12, color: '#ef4444', fontWeight: 'bold' }}>
          ❌ Processamento falhou
        </div>
      )}
    </div>
  );
};
```

### 8.2. ProcessingTimeline — Timeline vertical de eventos

```tsx
// Copiar para: src/components/ProcessingTimeline.tsx

import React, { useState, useMemo } from 'react';
import { ProcessingEvent, ProcessingEventLevel, ProcessingEventType } from '../types/processingLog';
import {
  formatEventTime,
  getLevelColor,
  getLevelBgColor,
  getLevelIcon,
  getEventTypeLabel,
} from '../utils/processingLogHelpers';

interface Props {
  events: ProcessingEvent[];
}

export const ProcessingTimeline: React.FC<Props> = ({ events }) => {
  const [filterLevel, setFilterLevel] = useState<ProcessingEventLevel | 'ALL'>('ALL');
  const [filterPage, setFilterPage] = useState<number | null | 'ALL'>('ALL');

  // Páginas disponíveis para filtro
  const availablePages = useMemo(() => {
    const pages = new Set<number>();
    events.forEach(e => { if (e.page !== null) pages.add(e.page); });
    return Array.from(pages).sort((a, b) => a - b);
  }, [events]);

  // Eventos filtrados
  const filteredEvents = useMemo(() => {
    return events.filter(event => {
      if (filterLevel !== 'ALL' && event.level !== filterLevel) return false;
      if (filterPage !== 'ALL') {
        if (filterPage === null && event.page !== null) return false;
        if (filterPage !== null && event.page !== filterPage) return false;
      }
      return true;
    });
  }, [events, filterLevel, filterPage]);

  return (
    <div>
      {/* Filtros */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <select
          value={filterLevel}
          onChange={e => setFilterLevel(e.target.value as any)}
        >
          <option value="ALL">Todos os níveis</option>
          <option value="INFO">ℹ️ INFO</option>
          <option value="WARN">⚠️ WARN</option>
          <option value="ERROR">❌ ERROR</option>
        </select>

        <select
          value={filterPage === null ? 'null' : filterPage === 'ALL' ? 'ALL' : String(filterPage)}
          onChange={e => {
            const val = e.target.value;
            setFilterPage(val === 'ALL' ? 'ALL' : val === 'null' ? null : Number(val));
          }}
        >
          <option value="ALL">Todas as páginas</option>
          <option value="null">Geral (sem página)</option>
          {availablePages.map(p => (
            <option key={p} value={p}>Página {p}</option>
          ))}
        </select>

        <span style={{ color: '#6b7280', fontSize: 14 }}>
          {filteredEvents.length} de {events.length} eventos
        </span>
      </div>

      {/* Timeline */}
      <div style={{ borderLeft: '2px solid #e5e7eb', paddingLeft: 16 }}>
        {filteredEvents.map((event, idx) => (
          <div
            key={idx}
            style={{
              marginBottom: 12,
              padding: '8px 12px',
              borderRadius: 6,
              backgroundColor: getLevelBgColor(event.level),
              borderLeft: `3px solid ${getLevelColor(event.level)}`,
            }}
          >
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12, color: '#6b7280' }}>
              <span>{getLevelIcon(event.level)}</span>
              <span style={{ fontFamily: 'monospace' }}>{formatEventTime(event.timestamp)}</span>
              {event.page !== null && (
                <span style={{
                  backgroundColor: '#e5e7eb',
                  padding: '1px 6px',
                  borderRadius: 4,
                  fontSize: 11,
                }}>
                  Pág. {event.page}
                </span>
              )}
              <span style={{ color: getLevelColor(event.level), fontWeight: 500 }}>
                {getEventTypeLabel(event.type)}
              </span>
            </div>
            <div style={{ marginTop: 4, fontSize: 14 }}>
              {event.message}
            </div>

            {/* Dados extraídos (GEMINI_EXTRACTION_COMPLETED) */}
            {event.type === 'GEMINI_EXTRACTION_COMPLETED' && event.details && (
              <div style={{ marginTop: 6, fontSize: 12, color: '#374151', fontFamily: 'monospace' }}>
                {event.details.nome && <div>👤 {event.details.nome}</div>}
                {event.details.competencia && <div>📅 {event.details.competencia}</div>}
                {event.details.salarioBruto != null && (
                  <div>💰 Bruto: {Number(event.details.salarioBruto).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</div>
                )}
                {event.details.totalDescontos != null && (
                  <div>📉 Descontos: {Number(event.details.totalDescontos).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</div>
                )}
                {event.details.salarioLiquido != null && (
                  <div>💵 Líquido: {Number(event.details.salarioLiquido).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</div>
                )}
              </div>
            )}

            {/* Issues de validação */}
            {(event.type === 'VALIDATION_FAILED' || event.type === 'VALIDATION_PASSED') &&
              event.details?.issues && event.details.issues.length > 0 && (
              <div style={{ marginTop: 6, fontSize: 12 }}>
                {event.details.issues.map((issue: any, i: number) => (
                  <div key={i} style={{ color: '#dc2626' }}>
                    • [{issue.type}] {issue.message}
                    {issue.expected && ` (esperado: ${issue.expected}, encontrado: ${issue.found})`}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
```

### 8.3. PageEventsAccordion — Eventos agrupados por página

```tsx
// Copiar para: src/components/PageEventsAccordion.tsx

import React, { useState } from 'react';
import { ProcessingEvent } from '../types/processingLog';
import { getGroupedEventsForDisplay, getLevelColor, getLevelIcon, formatEventTime } from '../utils/processingLogHelpers';

interface Props {
  events: ProcessingEvent[];
}

export const PageEventsAccordion: React.FC<Props> = ({ events }) => {
  const groups = getGroupedEventsForDisplay(events);
  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set(['Geral']));

  const toggleGroup = (label: string) => {
    setOpenGroups(prev => {
      const next = new Set(prev);
      if (next.has(label)) next.delete(label);
      else next.add(label);
      return next;
    });
  };

  return (
    <div>
      {groups.map((group) => {
        const isOpen = openGroups.has(group.label);
        const warnCount = group.events.filter(e => e.level === 'WARN').length;
        const errorCount = group.events.filter(e => e.level === 'ERROR').length;

        return (
          <div key={group.label} style={{ marginBottom: 8, border: '1px solid #e5e7eb', borderRadius: 6 }}>
            {/* Cabeçalho do grupo */}
            <div
              onClick={() => toggleGroup(group.label)}
              style={{
                padding: '10px 14px',
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                backgroundColor: '#f9fafb',
                borderRadius: isOpen ? '6px 6px 0 0' : 6,
              }}
            >
              <div style={{ fontWeight: 600 }}>
                {isOpen ? '▼' : '▶'} {group.label}
                <span style={{ color: '#6b7280', fontWeight: 400, marginLeft: 8 }}>
                  ({group.events.length} eventos)
                </span>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                {warnCount > 0 && <span style={{ color: '#f59e0b', fontSize: 12 }}>⚠️ {warnCount}</span>}
                {errorCount > 0 && <span style={{ color: '#ef4444', fontSize: 12 }}>❌ {errorCount}</span>}
              </div>
            </div>

            {/* Eventos do grupo */}
            {isOpen && (
              <div style={{ padding: '8px 14px' }}>
                {group.events.map((event, idx) => (
                  <div key={idx} style={{ padding: '4px 0', fontSize: 13, borderBottom: '1px solid #f3f4f6' }}>
                    <span style={{ fontFamily: 'monospace', color: '#6b7280', marginRight: 8 }}>
                      {formatEventTime(event.timestamp)}
                    </span>
                    <span style={{ color: getLevelColor(event.level), marginRight: 6 }}>
                      {getLevelIcon(event.level)}
                    </span>
                    <span>{event.message}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
```

---

## 9. Integração — Aba "Log de Processamento" no detalhe do documento

Exemplo de como usar os componentes na tela de detalhe:

```tsx
// Na tela de detalhe do documento (ex: DocumentDetailPage.tsx)

import { ProcessingSummaryCard } from '../components/ProcessingSummaryCard';
import { ProcessingTimeline } from '../components/ProcessingTimeline';
import { PageEventsAccordion } from '../components/PageEventsAccordion';
import { useDocumentPolling } from '../hooks/useDocumentPolling';

const DocumentDetailPage: React.FC<{ documentId: string }> = ({ documentId }) => {
  const [activeTab, setActiveTab] = useState<'dados' | 'entries' | 'log'>('dados');
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [viewMode, setViewMode] = useState<'timeline' | 'grouped'>('grouped');

  // Polling durante processamento
  const polling = useDocumentPolling({
    documentId,
    enabled: document?.status === 'PROCESSING',
    onComplete: (doc) => setDocument(doc),
    onError: (doc) => setDocument(doc),
  });

  // Usar documento do polling se disponível, senão o carregado
  const currentDoc = polling.document ?? document;
  const log = currentDoc?.processingLog ?? [];

  return (
    <div>
      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #e5e7eb', marginBottom: 16 }}>
        <button onClick={() => setActiveTab('dados')}>Dados</button>
        <button onClick={() => setActiveTab('entries')}>Rubricas</button>
        <button onClick={() => setActiveTab('log')}>
          Log de Processamento
          {log.length > 0 && <span> ({log.length})</span>}
        </button>
      </div>

      {/* Aba Log de Processamento */}
      {activeTab === 'log' && (
        <div>
          {/* Resumo sempre visível */}
          <ProcessingSummaryCard
            processingLog={log}
            status={currentDoc?.status ?? 'PENDING'}
          />

          {log.length > 0 && (
            <>
              {/* Toggle de visualização */}
              <div style={{ margin: '16px 0', display: 'flex', gap: 8 }}>
                <button
                  onClick={() => setViewMode('grouped')}
                  style={{ fontWeight: viewMode === 'grouped' ? 700 : 400 }}
                >
                  Por Página
                </button>
                <button
                  onClick={() => setViewMode('timeline')}
                  style={{ fontWeight: viewMode === 'timeline' ? 700 : 400 }}
                >
                  Timeline
                </button>
              </div>

              {/* Visualização */}
              {viewMode === 'timeline' ? (
                <ProcessingTimeline events={log} />
              ) : (
                <PageEventsAccordion events={log} />
              )}
            </>
          )}

          {/* Indicador de polling */}
          {polling.isPolling && (
            <div style={{ textAlign: 'center', padding: 16, color: '#3b82f6' }}>
              🔄 Processando... ({polling.eventsCount} eventos registrados)
            </div>
          )}
        </div>
      )}
    </div>
  );
};
```

---

## 10. Compatibilidade e edge cases

| Cenário | `processingLog` | O que exibir |
|---|---|---|
| Documento **PENDING** | `[]` | "Documento aguardando processamento." |
| Documento **PROCESSING** | `[...]` (parcial, crescendo) | Timeline + indicador de polling |
| Documento **PROCESSED** | `[...]` (completo) | Resumo + Timeline/Accordion |
| Documento **ERROR** | `[...]` (com PROCESSING_FAILED) | Resumo + Timeline (destacar erro) |
| Documento **antigo** (sem log) | `null` ou `[]` | "Sem informações de processamento disponíveis." |
| Documento **INCOME_TAX** | `[]` | Processamento via iText, sem eventos Gemini |

---

## 11. Exemplo completo de `processingLog` — Contracheque com 15 páginas

```json
[
  {
    "timestamp": "2026-02-16T15:12:00.000Z",
    "type": "PROCESSING_STARTED",
    "level": "INFO",
    "page": null,
    "message": "Processamento iniciado. Tipo: CAIXA_FUNCEF",
    "details": null
  },
  {
    "timestamp": "2026-02-16T15:12:00.100Z",
    "type": "PROCESSING_STARTED",
    "level": "INFO",
    "page": null,
    "message": "PDF com 15 páginas. Iniciando extração.",
    "details": { "totalPages": 15 }
  },
  {
    "timestamp": "2026-02-16T15:12:01.000Z",
    "type": "TEXT_UNREADABLE",
    "level": "WARN",
    "page": 1,
    "message": "Texto ilegível (2491 chars). Fontes sem mapeamento Unicode ou PDF escaneado.",
    "details": { "textLength": 2491 }
  },
  {
    "timestamp": "2026-02-16T15:12:01.100Z",
    "type": "GEMINI_EXTRACTION_STARTED",
    "level": "INFO",
    "page": 1,
    "message": "Extração via Gemini AI [gemini-2.5-flash] iniciada.",
    "details": { "model": "gemini-2.5-flash" }
  },
  {
    "timestamp": "2026-02-16T15:12:23.000Z",
    "type": "GEMINI_EXTRACTION_COMPLETED",
    "level": "INFO",
    "page": 1,
    "message": "Gemini [gemini-2.5-flash] extraiu 17 rubricas em 21900ms. nome=FERNANDO RIBEIRO MARCHINI, cpf=null, competencia=01/2016, bruto=12773.73, descontos=6289.83, líquido=6483.9",
    "details": {
      "model": "gemini-2.5-flash",
      "processingTimeMs": 21900,
      "rubricasCount": 17,
      "responseLength": 2992,
      "nome": "FERNANDO RIBEIRO MARCHINI",
      "competencia": "01/2016",
      "salarioBruto": 12773.73,
      "totalDescontos": 6289.83,
      "salarioLiquido": 6483.9,
      "rubricas": [
        { "codigo": "4346", "descricao": "FUNCEF - NOVO PLANO", "valor": 627.92, "referencia": "2016-01" },
        { "codigo": "4412", "descricao": "FUNCEF CONTR. EQUACIONAMENTO 1 SALDADO", "valor": 113.51, "referencia": "2016-01" },
        { "codigo": "4771", "descricao": "DESCONTO XYZ", "valor": 250.00, "referencia": "2016-01" }
      ]
    }
  },
  {
    "timestamp": "2026-02-16T15:12:23.500Z",
    "type": "VALIDATION_FAILED",
    "level": "WARN",
    "page": 1,
    "message": "Validação reprovada (score 0.53). 2 issues: SOMA_PROVENTOS_INCORRETA: Soma dos proventos difere; SOMA_DESCONTOS_INCORRETA: Soma dos descontos difere",
    "details": {
      "score": 0.53,
      "recommendation": "REJECT",
      "rubricasCount": 17,
      "salarioBruto": 12773.73,
      "totalDescontos": 6289.83,
      "salarioLiquido": 6483.9,
      "issues": [
        { "type": "SOMA_PROVENTOS_INCORRETA", "message": "Soma dos proventos (19063.56) difere do salário bruto informado (12773.73)", "expected": "12773.73", "found": "19063.56" },
        { "type": "SOMA_DESCONTOS_INCORRETA", "message": "Soma dos descontos (0) difere do total informado (6289.83)", "expected": "6289.83", "found": "0" }
      ]
    }
  },
  {
    "timestamp": "2026-02-16T15:12:24.000Z",
    "type": "CROSS_VALIDATION_STARTED",
    "level": "INFO",
    "page": 1,
    "message": "Cross-validation iniciada (score 0.53 < 0.85).",
    "details": null
  },
  {
    "timestamp": "2026-02-16T15:12:45.000Z",
    "type": "CROSS_VALIDATION_COMPLETED",
    "level": "INFO",
    "page": 1,
    "message": "Cross-validation concluída. 22/22 campos coincidem (100%). Revisão manual: não.",
    "details": {
      "score": 1.0,
      "matchedFields": 22,
      "totalFields": 22,
      "requiresManualReview": false,
      "consolidatedEntries": 15
    }
  },
  {
    "timestamp": "2026-02-16T15:18:00.000Z",
    "type": "ENTRIES_EXTRACTED",
    "level": "INFO",
    "page": null,
    "message": "124 rubricas extraídas de 15 páginas.",
    "details": { "totalExtracted": 124, "totalPages": 15 }
  },
  {
    "timestamp": "2026-02-16T15:18:00.200Z",
    "type": "RUBRICA_NOT_FOUND",
    "level": "WARN",
    "page": null,
    "message": "Rubrica não cadastrada: 4771 - DESCONTO XYZ (4x)",
    "details": { "rubrica": "4771 - DESCONTO XYZ", "occurrences": 4 }
  },
  {
    "timestamp": "2026-02-16T15:18:00.300Z",
    "type": "RUBRICA_NOT_FOUND",
    "level": "WARN",
    "page": null,
    "message": "Rubrica não cadastrada: 4461 - CONTRIBUICAO ABC (3x)",
    "details": { "rubrica": "4461 - CONTRIBUICAO ABC", "occurrences": 3 }
  },
  {
    "timestamp": "2026-02-16T15:18:01.000Z",
    "type": "ENTRIES_SAVED",
    "level": "INFO",
    "page": null,
    "message": "13 entries salvas. 124 extraídas, 111 ignoradas (rubricas não cadastradas).",
    "details": { "totalSaved": 13, "totalExtracted": 124, "totalIgnored": 111 }
  },
  {
    "timestamp": "2026-02-16T15:18:01.500Z",
    "type": "PROCESSING_COMPLETED",
    "level": "INFO",
    "page": null,
    "message": "Processamento concluído. 13 entries salvas em 361500ms.",
    "details": { "totalEntries": 13, "processingTimeMs": 361500 }
  }
]
```

---

## 12. Modal/Componente de Progresso em Tempo Real

> **NOVO** — Este componente deve ser exibido após o upload do PDF, enquanto o status for `PROCESSING`. Ele mostra o progresso da extração página por página, usando polling no endpoint leve `GET /documents/{id}/processing-status`.

### 12.1. Fluxo do usuário

1. Usuário faz upload do PDF (via modal "Enviar Múltiplos Documentos")
2. Backend retorna `201 { id, status: 'PROCESSING' }` imediatamente
3. Frontend abre o **Modal de Progresso** e inicia polling a cada 4 segundos
4. A cada poll, novos eventos aparecem no processingLog (backend salva a cada página)
5. Modal mostra: barra de progresso, página atual, último evento, timeline de eventos
6. Quando `status === 'PROCESSED'` ou `'ERROR'`, polling para e modal mostra resultado final

### 12.2. Componente `ProcessingProgressModal`

```tsx
// Copiar para: src/components/ProcessingProgressModal.tsx

import React from 'react';
import { useProcessingStatus } from '../hooks/useProcessingStatus';
import { formatEventTime, getEventTypeLabel } from '../utils/processingLogHelpers';

interface Props {
  documentId: string;
  isOpen: boolean;
  onClose: () => void;
}

export const ProcessingProgressModal: React.FC<Props> = ({ documentId, isOpen, onClose }) => {
  const {
    status,
    isPolling,
    progressPercent,
    totalPages,
    completedPages,
    latestEvent,
    eventsCount,
    latestEvents,
    documentStatus,
    erro,
  } = useProcessingStatus({
    documentId,
    enabled: isOpen,
    intervalMs: 4000,
    onComplete: () => {
      // Opcional: notificar usuário, tocar som, etc.
    },
    onError: (s) => {
      console.error('Processamento falhou:', s.erro);
    },
  });

  if (!isOpen) return null;

  const isProcessing = documentStatus === 'PROCESSING';
  const isComplete = documentStatus === 'PROCESSED';
  const isError = documentStatus === 'ERROR';
  const recentEvents = latestEvents(8);

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: 600, maxHeight: '80vh', overflow: 'auto' }}>

        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Processamento do Documento</h2>
          {!isProcessing && (
            <button onClick={onClose}>Fechar</button>
          )}
        </div>

        {/* Status Badge */}
        <div style={{ marginTop: 12 }}>
          {isProcessing && <span className="badge badge-processing">Processando...</span>}
          {isComplete && <span className="badge badge-success">Concluído</span>}
          {isError && <span className="badge badge-error">Erro</span>}
        </div>

        {/* Barra de progresso */}
        {totalPages > 0 && (
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
              <span>Página {completedPages} de {totalPages}</span>
              <span>{progressPercent}%</span>
            </div>
            <div style={{
              width: '100%', height: 8, backgroundColor: '#e5e7eb',
              borderRadius: 4, marginTop: 4
            }}>
              <div style={{
                width: `${progressPercent}%`, height: '100%',
                backgroundColor: isError ? '#ef4444' : isComplete ? '#22c55e' : '#3b82f6',
                borderRadius: 4, transition: 'width 0.5s ease'
              }} />
            </div>
          </div>
        )}

        {/* Último evento */}
        {latestEvent && (
          <div style={{
            marginTop: 16, padding: 12, backgroundColor: '#f9fafb',
            borderRadius: 8, borderLeft: '4px solid #3b82f6'
          }}>
            <div style={{ fontSize: 12, color: '#6b7280' }}>
              {formatEventTime(latestEvent.timestamp)}
            </div>
            <div style={{ fontSize: 14, fontWeight: 500, marginTop: 4 }}>
              {latestEvent.message}
            </div>
          </div>
        )}

        {/* Timeline de eventos recentes */}
        {recentEvents.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <h4>Últimos eventos ({eventsCount} total)</h4>
            <div style={{ maxHeight: 300, overflowY: 'auto' }}>
              {recentEvents.map((event, idx) => (
                <div key={idx} style={{
                  padding: '8px 12px', borderBottom: '1px solid #e5e7eb',
                  fontSize: 13
                }}>
                  <span style={{ color: '#6b7280', marginRight: 8 }}>
                    {formatEventTime(event.timestamp)}
                  </span>
                  <span style={{
                    fontWeight: 500,
                    color: event.level === 'ERROR' ? '#ef4444'
                         : event.level === 'WARN' ? '#f59e0b'
                         : '#374151'
                  }}>
                    {getEventTypeLabel(event.type)}
                  </span>
                  {event.page && (
                    <span style={{ color: '#6b7280', marginLeft: 8 }}>
                      (Pg. {event.page})
                    </span>
                  )}
                  <div style={{ color: '#6b7280', marginTop: 2, fontSize: 12 }}>
                    {event.message}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Erro */}
        {isError && erro && (
          <div style={{
            marginTop: 16, padding: 12, backgroundColor: '#fef2f2',
            borderRadius: 8, color: '#dc2626', border: '1px solid #fecaca'
          }}>
            <strong>Erro:</strong> {erro}
          </div>
        )}

        {/* Resultado final */}
        {isComplete && status?.processingLog && (
          <div style={{
            marginTop: 16, padding: 12, backgroundColor: '#f0fdf4',
            borderRadius: 8, border: '1px solid #bbf7d0'
          }}>
            <strong>Processamento concluído com sucesso!</strong>
            <p style={{ margin: '4px 0 0', fontSize: 14 }}>
              O documento foi processado. Você pode fechar este modal e ver os resultados.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
```

### 12.3. Integração com o modal de upload

Após o upload, guardar o `documentId` retornado e abrir o modal de progresso:

```tsx
// Exemplo de integração no componente de upload
const [processingDocId, setProcessingDocId] = useState<string | null>(null);

const handleUpload = async (files: File[], cpf: string, nome: string) => {
  const response = await api.post('/documents/bulk-upload', formData);
  const results = response.data.resultados;

  // Pegar o primeiro documento que está processando
  const processingDoc = results.find((r: any) => r.status === 'PROCESSING');
  if (processingDoc) {
    setProcessingDocId(processingDoc.documentId);
  }
};

// No render:
{processingDocId && (
  <ProcessingProgressModal
    documentId={processingDocId}
    isOpen={!!processingDocId}
    onClose={() => setProcessingDocId(null)}
  />
)}
```

### 12.4. Estratégia de polling

| Situação | Intervalo | Ação |
|---|---|---|
| `status === 'PROCESSING'` | 4 segundos | Polling ativo |
| `status === 'PROCESSED'` | **Parar** | Mostrar resultado final |
| `status === 'ERROR'` | **Parar** | Mostrar mensagem de erro |
| `status === 'PENDING'` | 4 segundos | Pode continuar polling (aguardando início) |
| Erro de rede | 8 segundos | Retry com backoff |

---

## 13. Checklist de implementação frontend

### Fase 1 — Tipos e Helpers (copiar/colar)

- [ ] Criar `src/types/processingLog.ts` com todas as interfaces (Seção 4), incluindo `ExtractedRubrica`
- [ ] Atualizar `DocumentResponse` para incluir `processingLog` (Seção 4.2)
- [ ] Criar interface `ProcessingStatusResponse` (Seção 2.1)
- [ ] Criar `src/utils/processingLogHelpers.ts` com todos os helpers (Seção 6)

### Fase 2 — Componentes

- [ ] Criar `ProcessingSummaryCard` (Seção 8.1) — resumo rápido
- [ ] Criar `ProcessingTimeline` (Seção 8.2) — timeline com filtros
- [ ] Criar `PageEventsAccordion` (Seção 8.3) — agrupado por página

### Fase 3 — Integração

- [ ] Adicionar aba "Log de Processamento" na tela de detalhe do documento (Seção 9)
- [ ] Implementar toggle entre visualizações (Timeline vs Por Página)
- [ ] Tratar edge cases: documento antigo, PENDING, INCOME_TAX (Seção 10)

### Fase 4 — Polling + Modal de Progresso em Tempo Real (PRIORIDADE)

- [ ] Criar hook `useProcessingStatus` (Seção 7.1) — **usa endpoint leve `/processing-status`**
- [ ] Criar `ProcessingProgressModal` (Seção 12.2) — modal de progresso com barra, timeline, status
- [ ] Integrar modal na tela de upload: após upload, abrir modal com polling (Seção 12.3)
- [ ] Integrar polling na tela de detalhe quando `status === 'PROCESSING'`
- [ ] Mostrar indicador visual de processamento em andamento

### Fase 5 — Refinamentos

- [ ] Estilizar componentes com o design system do projeto (Tailwind, MUI, etc.)
- [ ] Adicionar animações na timeline (novos eventos aparecendo)
- [ ] Responsividade mobile
- [ ] Testes unitários nos helpers (`buildProcessingSummary`, `groupEventsByPage`)

---

[← Voltar ao índice](./README.md)
