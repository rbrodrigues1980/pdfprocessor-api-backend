# Plano de Upgrade: Gemini AI 2.5 para Extração de PDFs

> **Data**: 12/02/2026
>
> | Versão | Status | Data |
> |--------|--------|------|
> | 1.0 | Aprovado para implementação | 12/02/2026 |
> | 1.1 | Fase 1 implementada e validada | 12/02/2026 |
> | 1.2 | IA habilitada via API (Passo 4 concluído) | 12/02/2026 |
> | 1.3 | Teste com PDF escaneado — Gemini funcional, parser regex identificado como gargalo | 12/02/2026 |
> | 2.0 | Fase 2 implementada — Extração JSON estruturada + Validação por regras de negócio | 12/02/2026 |
> | 3.0 | Fase 3 implementada — Dupla Extração com Cross-Validation | 12/02/2026 |
> | 4.0 | Fase 4 implementada — Escalação Automática para Gemini Pro | 12/02/2026 |

---

## Índice

1. [Resumo Executivo](#1-resumo-executivo)
2. [Estado Atual do Projeto](#2-estado-atual-do-projeto)
3. [Análise de Decisão: Por que Gemini 2.5 Flash](#3-análise-de-decisão-por-que-gemini-25-flash)
4. [Arquitetura Alvo (Pós-Upgrade)](#4-arquitetura-alvo-pós-upgrade)
5. [Fase 1 — Upgrade do Modelo Gemini](#5-fase-1--upgrade-do-modelo-gemini)
6. [Fase 2 — Validação por Regras de Negócio](#6-fase-2--validação-por-regras-de-negócio)
7. [Fase 3 — Dupla Extração com Cross-Validation](#7-fase-3--dupla-extração-com-cross-validation)
8. [Fase 4 — Escalação Automática para Gemini Pro](#8-fase-4--escalação-automática-para-gemini-pro)
9. [Estimativa de Custos](#9-estimativa-de-custos)
10. [Riscos e Mitigações](#10-riscos-e-mitigações)

---

## 1. Resumo Executivo

### Objetivo

Evoluir a camada de IA do projeto para utilizar o **Google Gemini 2.5 Flash** (substituindo o Gemini 1.5 Flash 002), implementando um sistema robusto de extração e validação que minimize erros na leitura de PDFs.

### Decisão Estratégica

| Aspecto | Decisão |
|---------|---------|
| **Quantidade de IAs** | Uma só (Gemini) — não usar Document AI nem OCR separado |
| **Modelo principal** | Gemini 2.5 Flash (custo-benefício ideal) |
| **Modelo fallback** | Gemini 2.5 Pro (apenas quando Flash falha na validação) |
| **Camada primária** | iText 8 + PDFBox (mantida, grátis, para PDFs digitais) |
| **Camada secundária** | Gemini 2.5 Flash (para PDFs escaneados e validação) |

### Resultado Esperado

- **PDFs digitais**: precisão ~99-100% (iText 8/PDFBox — sem alteração)
- **PDFs escaneados**: precisão ~93-95% (Gemini 2.5 Flash)
- **Com validação por regras**: precisão ~97-99% (Fases 2-3)
- **Custo estimado**: ~$1-5/mês para 500 páginas

---

## 2. Estado Atual do Projeto

### 2.1 Arquitetura de Extração Atual

```
┌──────────────────────────────────────────────────────────────────┐
│                        PDF Upload                                │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│  CONTRACHEQUES (CAIXA/FUNCEF)              │  IRPF (Imposto de   │
│  ─────────────────────────────             │  Renda)             │
│  Tecnologia: PDFBox 3.0.3 + Tika 2.9.2     │  Tecnologia: iText 8│
│  Classe: PdfServiceImpl.java               │  Classe:            │
│  Extração: PDFTextStripper                 │  ITextIncomeTax     │
│  Parsing: Regex por tipo de doc            │  ServiceImpl.java   │
│                                            │  Extração: Location │
│  Se texto < 100 chars (PDF escaneado):     │  TextExtraction     │
│  → Fallback para Gemini 1.5 Flash 002      │  Strategy           │
│  → Converte página para PNG (300 DPI)      │  Parsing: 37+ regex │
│  → Envia para Gemini Vision                │  patterns           │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Arquivos Existentes Relevantes

#### Camada de IA (Gemini)

| Arquivo | Localização | Função |
|---------|-------------|--------|
| `GeminiPdfServiceImpl.java` | `infrastructure/ai/` | Implementação do serviço Gemini. Converte PDF→PNG, envia para Gemini Vision, processa resposta |
| `AiPdfExtractionService.java` | `domain/service/` | Interface do domínio para serviço de IA |
| `GeminiConfig.java` | `infrastructure/config/` | Configuração do Gemini (project-id, model, temperature, etc.) |
| `GeminiPrompts.java` | `infrastructure/ai/` | Prompts para cada tipo de extração (contracheque, IR, genérico, validação) |
| `AiConfigController.java` | `interfaces/rest/` | Endpoint REST para habilitar/desabilitar IA dinamicamente |
| `SystemConfig.java` | `domain/model/` | Modelo para configuração dinâmica no MongoDB |
| `SystemConfigRepository.java` | `domain/repository/` | Repositório reativo para configurações |

#### Camada de Extração (PDFBox/iText)

| Arquivo | Localização | Função |
|---------|-------------|--------|
| `PdfServiceImpl.java` | `infrastructure/pdf/` | Extração com PDFBox (texto, metadados, páginas) |
| `ITextIncomeTaxServiceImpl.java` | `infrastructure/incometax/` | Extração de IR com iText 8 (37+ campos, regex) |
| `DocumentTypeDetectionServiceImpl.java` | `infrastructure/pdf/` | Detecção automática do tipo de documento (CAIXA/FUNCEF/IR) |

#### Use Cases

| Arquivo | Localização | Função |
|---------|-------------|--------|
| `DocumentUploadUseCase.java` | `application/documents/` | Upload e processamento de contracheques |
| `DocumentProcessUseCase.java` | `application/documents/` | Processamento completo (extração de rubricas) |
| `ITextIncomeTaxUploadUseCase.java` | `application/incometax/` | Upload de IR via iText 8 |
| `IncomeTaxUploadUseCase.java` | `application/incometax/` | Upload de IR (fluxo alternativo) |

### 2.3 Configuração Atual

**application.yml:**
```yaml
gemini:
  enabled: false                    # Desabilitado por padrão
  project-id: ${GOOGLE_CLOUD_PROJECT:}
  location: us-central1
  model: gemini-1.5-flash-002      # ← Modelo atual (será substituído)
  max-output-tokens: 8192
  temperature: 0.1
  timeout-seconds: 60
```

**Dependência atual (build.gradle.kts):**
```kotlin
implementation("com.google.cloud:google-cloud-vertexai:1.2.0")
```

### 2.4 Prompts Atuais

O projeto já possui 4 prompts estruturados em `GeminiPrompts.java`:

| Prompt | Constante | Uso |
|--------|-----------|-----|
| Extração de contracheque | `CONTRACHEQUE_EXTRACTION` | Retorna JSON com nome, CPF, matrícula, rubricas |
| Resumo de IR | `IR_RESUMO_EXTRACTION` | Retorna JSON com dados fiscais (exercício, deduções, etc.) |
| Validação de contracheque | `VALIDACAO_CONTRACHEQUE` | Verifica soma de proventos/descontos |
| Extração genérica | `EXTRACAO_TEXTO_GENERICO` | Extrai texto puro de qualquer PDF escaneado |

### 2.5 Fluxo Atual de Fallback para IA

No `DocumentProcessUseCase.java`, o fallback para Gemini acontece quando:

```java
// Condição atual: texto extraído < 100 caracteres = PDF escaneado
if (extractedText.length() < 100 && aiService.isEnabled()) {
    // Converte página para PNG (300 DPI)
    // Envia para Gemini Vision
    // Processa resposta JSON
}
```

A habilitação é controlada em duas camadas:
1. **Estática**: `application.yml` → `gemini.enabled`
2. **Dinâmica**: MongoDB → `system_config` collection → chave `ai.enabled`

### 2.6 Tipos de Documentos Processados

| Tipo | Enum | Detecção | Campos Extraídos |
|------|------|----------|------------------|
| Contracheque CAIXA | `CAIXA` | Keywords: "DEMONSTRATIVO DE PAGAMENTO", "CAIXA ECONÔMICA FEDERAL" | Nome, CPF, matrícula, competência, rubricas (código, descrição, valor) |
| Demonstrativo FUNCEF | `FUNCEF` | Keywords: "DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS", "FUNCEF" | Mesmos campos, layout diferente |
| Misto | `CAIXA_FUNCEF` | Ambos os tipos no mesmo PDF | Processado página a página |
| Declaração de IR | `INCOME_TAX` | Via endpoint separado | 37+ campos (dados básicos, imposto devido, deduções, imposto pago, resultado) |

---

## 3. Análise de Decisão: Por que Gemini 2.5 Flash

### 3.1 Alternativas Avaliadas

| Solução | Custo/1K pgs | Precisão | Extrai estrutura? | Já integrado? |
|---------|-------------|----------|-------------------|---------------|
| Google Document AI (OCR) | $1.50 | ~99% texto | Não (só texto bruto) | Não |
| Google Document AI (Custom Extractor) | **$30.00** | ~99% | Sim, mas precisa treinar | Não |
| **Gemini 2.5 Flash** | **$2.63** | **~93-95%** | **Sim, via prompt** | **Parcialmente** |
| Gemini 2.5 Pro | $10.63 | ~95-96% | Sim, via prompt | Parcialmente |
| iText pdfOCR (ONNX) | Licença | ~90-93% | Não | Não |
| Tesseract 5.x | Grátis | ~85-90% | Não | Não |
| AWS Textract | $1.50 | ~95% | Sim | Não |
| Azure Document Intelligence | $10.00 | ~96% | Sim | Não |

### 3.2 Por que Gemini 2.5 Flash vence

1. **Custo**: $2.63/1K páginas vs $30/1K do Document AI Custom Extractor — **11x mais barato**
2. **Sem treinamento**: Document AI Custom Extractor exige dias treinando com documentos reais. Gemini usa prompt engineering (minutos)
3. **Já integrado**: O projeto já tem `GeminiPdfServiceImpl.java`, `GeminiConfig.java`, `GeminiPrompts.java`. É um upgrade, não uma integração do zero
4. **Uma chamada = OCR + Estrutura**: Gemini faz reconhecimento de texto E extração de dados estruturados numa única chamada. Document AI precisaria de 2 processadores separados
5. **Flexibilidade**: Novo tipo de documento? Muda o prompt. Não precisa retreinar modelo
6. **Contexto brasileiro**: Gemini entende naturalmente documentos em português, formatos de CPF, valores em R$, etc.
7. **Mesmo cloud provider**: Projeto já roda no GCP (Cloud Run) — sem adicionar AWS ou Azure

### 3.3 Por que NÃO usar duas IAs

Usar Document AI + Gemini (como inicialmente sugerido) seria:
- **Mais caro**: $30 + $2.63 = $32.63/1K pgs vs apenas $2.63
- **Mais complexo**: duas integrações, dois SDKs, dois sistemas de billing
- **Desnecessário**: Gemini sozinho faz OCR + extração estruturada + validação

### 3.4 Limitação honesta do Gemini

- **VLMs podem alucinar**: podem "inventar" dados que não existem no PDF
- **Mitigação**: Fases 2-3 do plano (validação por regras + dupla extração)
- **Para PDFs digitais**: iText 8/PDFBox não alucinam — extração byte a byte, 0% erro

---

## 4. Arquitetura Alvo (Pós-Upgrade)

### 4.1 Fluxo Completo

```
┌──────────────────────────────────────────────────────────────────────┐
│                          PDF Upload                                  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  CAMADA 1: Extração Nativa (GRÁTIS)                                  │
│                                                                      │
│  ┌─────────────────────────────┐  ┌────────────────────────────────┐ │
│  │  Contracheques              │  │  Declaração de IR              │ │
│  │  PDFBox 3.0.3 + Tika 2.9.2  │  │  iText 8.0.1                   │ │
│  │  PdfServiceImpl.java        │  │  ITextIncomeTaxServiceImpl     │ │
│  │  PDFTextStripper            │  │  LocationTextExtraction        │ │
│  │                             │  │  Strategy                      │ │
│  │  Precisão: ~99-100%         │  │  Precisão: ~99-100%            │ │
│  │  Custo: ZERO                │  │  Custo: ZERO                   │ │
│  └──────────────┬──────────────┘  └──────────────┬─────────────────┘ │
│                 │                                │                   │
│          texto < 100 chars?                resultado ok?             │
│            OU IA habilitada                                          │
│            para extração direta?                                     │
└─────────────────┬────────────────────────────────┬───────────────────┘
                  │ SIM                            │ NÃO (segue normal)
                  ▼                                ▼
┌──────────────────────────────────────────┐  ┌──────────────────────┐
│  CAMADA 2: Gemini 2.5 Flash              │  │  Processamento       │
│                                          │  │  normal (regex/      │
│  GeminiPdfServiceImpl.java               │  │  parsing)            │
│  Modelo: gemini-2.5-flash                │  └──────────┬───────────┘
│                                          │             │
│  1. Converte PDF → PNG (300 DPI)         │             │
│  2. Envia para Gemini Vision             │             │
│  3. Extrai texto + dados estruturados    │             │
│  4. Retorna JSON tipado                  │             │
│                                          │             │
│  Precisão: ~93-95%                       │             │
│  Custo: ~$0.003/página                   │             │
└────────────────────┬─────────────────────┘             │
                     │                                   │
                     ▼                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  CAMADA 3: Validação por Regras (GRÁTIS - Fase 2)                    │
│                                                                      │
│  ExtractionValidationService.java (NOVO)                             │
│                                                                      │
│  Contracheques:                                                      │
│  ✓ Soma proventos = salário bruto?                                   │
│  ✓ Soma descontos = total descontos?                                 │
│  ✓ Bruto - descontos = líquido?                                      │
│  ✓ CPF válido (dígito verificador)?                                  │
│  ✓ Competência no formato MM/YYYY?                                   │
│                                                                      │
│  Declaração de IR:                                                   │
│  ✓ CPF válido?                                                       │
│  ✓ Ano exercício = ano calendário + 1?                               │
│  ✓ Imposto devido - imposto pago = saldo/restituição?                │
│  ✓ Soma deduções individuais = total deduções?                       │
│  ✓ Valores monetários >= 0?                                          │
│                                                                      │
│  Resultado: confidence score (0.0 a 1.0)                             │
└────────────────────┬─────────────────────────────────────────────────┘
                     │
              confidence < 0.85?
                     │ SIM
                     ▼
┌──────────────────────────────────────────────────────────────────────┐
│  CAMADA 4: Dupla Extração (Fase 3)                                    │
│                                                                       │
│  1. Chama Gemini 2.5 Flash novamente com prompt diferente             │
│  2. Compara resultado A vs resultado B                                │
│  3. Campos que coincidem = alta confiança                             │
│  4. Campos que divergem = flag para revisão manual                    │
│                                                                       │
│  Se ainda falhar → Escala para Gemini 2.5 Pro (Fase 4)               │
│  Custo extra: ~$0.003/página (só quando necessário)                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 5. Fase 1 — Upgrade do Modelo Gemini

### 5.1 Objetivo

Atualizar o modelo Gemini de `gemini-1.5-flash-002` para `gemini-2.5-flash` e melhorar os prompts para maior precisão.

### 5.2 Escopo de Alterações

#### 5.2.1 `build.gradle.kts` — Atualizar SDK do Vertex AI

**O que muda:**
```kotlin
// DE:
implementation("com.google.cloud:google-cloud-vertexai:1.2.0")

// PARA (última versão estável):
implementation("com.google.cloud:google-cloud-vertexai:LATEST_STABLE")
```

**Por quê**: A versão 1.2.0 pode não suportar Gemini 2.5. Precisamos da versão mais recente do SDK.

#### 5.2.2 `application.yml` — Atualizar modelo padrão

**O que muda:**
```yaml
gemini:
  enabled: false
  project-id: ${GOOGLE_CLOUD_PROJECT:}
  location: us-central1
  model: gemini-2.5-flash          # ← Era: gemini-1.5-flash-002
  max-output-tokens: 8192
  temperature: 0.1
  timeout-seconds: 120             # ← Era: 60 (2.5 pode ser mais lento)
```

#### 5.2.3 `.env.example` — Documentar novo modelo

**O que muda:**
```env
# Google Cloud / Gemini AI (para PDFs escaneados e validação)
GOOGLE_CLOUD_PROJECT=your-gcp-project-id
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
GEMINI_MODEL=gemini-2.5-flash     # Opções: gemini-2.5-flash, gemini-2.5-pro
```

#### 5.2.4 `GeminiConfig.java` — Adicionar suporte a modelo fallback

**O que muda**: Adicionar campo para modelo Pro (usado na Fase 4):

```java
// Campos existentes mantidos...

/**
 * Modelo Gemini principal (rápido e barato).
 * Default: gemini-2.5-flash
 */
private String model = "gemini-2.5-flash";

/**
 * Modelo Gemini de fallback (mais preciso, mais caro).
 * Usado quando o modelo principal falha na validação.
 * Default: gemini-2.5-pro
 */
private String fallbackModel = "gemini-2.5-pro";
```

#### 5.2.5 `GeminiPdfServiceImpl.java` — Refatorar para suportar múltiplos modelos

**O que muda**:

1. Criar um `GenerativeModel` para Flash e outro para Pro
2. Melhorar logging com informações do modelo usado
3. Adicionar método para usar modelo fallback
4. Manter compatibilidade total com interface existente

```java
// Novos campos:
private GenerativeModel flashModel;     // gemini-2.5-flash (principal)
private GenerativeModel proModel;       // gemini-2.5-pro (fallback)

// Novo método:
public Mono<String> processWithFallbackModel(byte[] pdfBytes, int pageNumber, String prompt) {
    // Usa gemini-2.5-pro em vez de flash
}
```

#### 5.2.6 `GeminiPrompts.java` — Melhorar prompts para Gemini 2.5

**O que muda**: Reescrever prompts com técnicas avançadas de prompt engineering:

**Melhorias nos prompts:**

1. **Instrução de precisão**: Adicionar "Se um campo não estiver claramente legível, retorne null em vez de adivinhar"
2. **Exemplos (few-shot)**: Incluir um exemplo real de output esperado em cada prompt
3. **Formato monetário**: Especificar claramente o formato brasileiro (1.234,56)
4. **Hierarquia de seções**: Para IR, especificar a ordem das seções do RESUMO
5. **Validação inline**: Pedir ao Gemini para verificar somas antes de retornar
6. **Confiança**: Pedir ao Gemini para incluir um campo `confidence` (0.0-1.0) por campo extraído

**Exemplo do novo prompt de contracheque:**

```
Você é um especialista em extração de dados de contracheques brasileiros.
Analise esta imagem com EXTREMA PRECISÃO.

REGRAS CRÍTICAS:
- Se um valor não estiver claramente legível, retorne null (NUNCA adivinhe)
- Valores monetários: use ponto como decimal (ex: 1234.56, não 1.234,56)
- Verifique: soma dos proventos deve ser igual ao salário bruto
- Verifique: salário bruto - total descontos = salário líquido
- Se as somas não baterem, revise os valores extraídos

[... formato JSON esperado com campo "confidence" por campo ...]
```

#### 5.2.7 `AiConfigResponse.java` — Adicionar informações do modelo

**O que muda**: Incluir campo `fallbackModel` na resposta:

```java
public record AiConfigResponse(
    Boolean enabled,
    String model,
    String fallbackModel,        // ← NOVO
    Boolean credentialsConfigured,
    String projectId,
    String location,
    Instant updatedAt,
    String updatedBy,
    String statusMessage
) {}
```

### 5.3 Arquivos Alterados na Fase 1

| Arquivo | Tipo de Alteração |
|---------|-------------------|
| `build.gradle.kts` | Atualizar versão do SDK Vertex AI |
| `application.yml` | Mudar modelo para `gemini-2.5-flash`, aumentar timeout |
| `.env.example` | Documentar variável `GEMINI_MODEL` |
| `GeminiConfig.java` | Adicionar campo `fallbackModel` |
| `GeminiPdfServiceImpl.java` | Suportar 2 modelos (flash + pro), melhorar logging |
| `GeminiPrompts.java` | Reescrever todos os 4 prompts com técnicas avançadas |
| `AiConfigResponse.java` | Adicionar campo `fallbackModel` |
| `AiConfigController.java` | Retornar `fallbackModel` na resposta |
| `AiConfigRequest.java` | Aceitar `fallbackModel` na request |

### 5.4 Riscos da Fase 1

| Risco | Probabilidade | Mitigação |
|-------|--------------|-----------|
| SDK novo incompatível | Baixa | Testar em branch separada antes de merge |
| Gemini 2.5 Flash indisponível na região | Baixa | Configurar `location` como variável de ambiente |
| Prompts novos piores que os antigos | Média | Testar com os mesmos PDFs e comparar resultados |
| Timeout maior (120s) | Baixa | Monitorar latência nos logs |

---

## 6. Fase 2 — Validação por Regras de Negócio

### 6.1 Objetivo

Criar um serviço de validação que verifica se os dados extraídos (tanto por iText/PDFBox quanto por Gemini) são consistentes, atribuindo um **score de confiança**.

### 6.2 Escopo de Alterações

#### 6.2.1 Novo: `ExtractionValidationService.java` (Interface)

**Localização**: `domain/service/`

```java
public interface ExtractionValidationService {

    /**
     * Valida dados extraídos de um contracheque.
     * @return ValidationResult com score de confiança e lista de inconsistências
     */
    Mono<ValidationResult> validatePayrollExtraction(PayrollExtractionData data);

    /**
     * Valida dados extraídos de uma declaração de IR.
     * @return ValidationResult com score de confiança e lista de inconsistências
     */
    Mono<ValidationResult> validateIncomeTaxExtraction(IncomeTaxInfo data);
}
```

#### 6.2.2 Novo: `ValidationResult.java` (Modelo)

**Localização**: `domain/model/`

```java
public record ValidationResult(
    double confidenceScore,          // 0.0 a 1.0
    boolean isValid,                 // true se score >= 0.85
    List<ValidationIssue> issues,    // Lista de problemas encontrados
    String recommendation            // "ACCEPT", "REVIEW", "REJECT"
) {}

public record ValidationIssue(
    String field,                    // Nome do campo com problema
    String type,                     // "SOMA_INCORRETA", "CPF_INVALIDO", "VALOR_NEGATIVO", etc.
    String expected,                 // Valor esperado
    String found,                    // Valor encontrado
    String message                   // Descrição legível
) {}
```

#### 6.2.3 Novo: `ExtractionValidationServiceImpl.java` (Implementação)

**Localização**: `infrastructure/validation/`

**Regras de validação para Contracheques:**

| Regra | Descrição | Peso no Score |
|-------|-----------|---------------|
| `SOMA_PROVENTOS` | Soma das rubricas de provento = salário bruto | 20% |
| `SOMA_DESCONTOS` | Soma das rubricas de desconto = total descontos | 20% |
| `LIQUIDO_CORRETO` | Bruto - descontos = líquido | 25% |
| `CPF_VALIDO` | CPF passa validação de dígito verificador | 15% |
| `COMPETENCIA_VALIDA` | Formato MM/YYYY, mês entre 01-13 (13 = abono) | 10% |
| `VALORES_POSITIVOS` | Nenhum valor negativo em rubricas | 10% |

**Regras de validação para Declaração de IR:**

| Regra | Descrição | Peso no Score |
|-------|-----------|---------------|
| `CPF_VALIDO` | CPF passa validação de dígito verificador | 10% |
| `ANO_COERENTE` | Exercício = Ano Calendário + 1 | 10% |
| `SOMA_DEDUCOES` | Soma deduções individuais ≈ total deduções (tolerância 1%) | 20% |
| `SOMA_IMPOSTO_PAGO` | Soma impostos pagos individuais ≈ total imposto pago | 20% |
| `RESULTADO_COERENTE` | Total imposto devido - total imposto pago = saldo ou restituição | 25% |
| `VALORES_POSITIVOS` | Todos os valores monetários >= 0 | 10% |
| `CAMPOS_OBRIGATORIOS` | Nome, CPF, exercício, ano calendário preenchidos | 5% |

#### 6.2.4 Alteração: `DocumentProcessUseCase.java`

**O que muda**: Após extração, chamar `ExtractionValidationService` e incluir o score no resultado.

```java
// Após extração com PDFBox ou Gemini:
ValidationResult validation = validationService.validatePayrollExtraction(data).block();

if (!validation.isValid()) {
    log.warn("⚠️ Extração com baixa confiança: score={}, issues={}",
        validation.confidenceScore(), validation.issues());
    // Na Fase 3: trigger dupla extração
}
```

#### 6.2.5 Alteração: `ITextIncomeTaxUploadUseCase.java`

**O que muda**: Validar dados de IR após extração com iText 8.

### 6.3 Arquivos da Fase 2

| Arquivo | Tipo | Alteração |
|---------|------|-----------|
| `ExtractionValidationService.java` | **NOVO** | Interface de validação |
| `ValidationResult.java` | **NOVO** | Modelo de resultado |
| `ValidationIssue.java` | **NOVO** | Modelo de problema |
| `ExtractionValidationServiceImpl.java` | **NOVO** | Implementação das regras |
| `CpfValidator.java` | **NOVO** | Utilitário para validar CPF |
| `DocumentProcessUseCase.java` | Alterado | Chamar validação após extração |
| `ITextIncomeTaxUploadUseCase.java` | Alterado | Chamar validação após extração de IR |
| `PayrollDocument.java` | Alterado | Adicionar campo `confidenceScore` |

### 6.4 Custo da Fase 2

**$0** — são apenas regras de negócio em código Java, sem chamadas externas.

---

## 7. Fase 3 — Dupla Extração com Cross-Validation

### 7.1 Objetivo

Quando a validação da Fase 2 retorna `confidenceScore < 0.85`, executar uma segunda extração com prompt diferente e comparar os resultados.

### 7.2 Escopo de Alterações

#### 7.2.1 Novo: `CrossValidationService.java`

**Localização**: `domain/service/`

```java
public interface CrossValidationService {
    /**
     * Executa dupla extração e compara resultados.
     * Chamado apenas quando a primeira extração tem confiança < 0.85.
     *
     * @param pdfBytes      bytes do PDF
     * @param pageNumber    número da página
     * @param firstResult   resultado da primeira extração
     * @param documentType  tipo do documento
     * @return resultado consolidado (campos que coincidem = alta confiança)
     */
    Mono<CrossValidationResult> crossValidate(
        byte[] pdfBytes,
        int pageNumber,
        String firstResult,
        DocumentType documentType
    );
}
```

#### 7.2.2 Novo: `CrossValidationResult.java`

```java
public record CrossValidationResult(
    String consolidatedJson,            // JSON com os dados finais
    double confidenceScore,             // Score consolidado
    List<FieldComparison> comparisons,  // Comparação campo a campo
    boolean requiresManualReview        // true se campos críticos divergem
) {}

public record FieldComparison(
    String field,
    String valueA,           // Valor da 1ª extração
    String valueB,           // Valor da 2ª extração
    boolean match,           // true se são iguais
    String finalValue        // Valor escolhido (maioria ou mais confiável)
) {}
```

#### 7.2.3 `GeminiPrompts.java` — Adicionar prompts alternativos

Para cada tipo de documento, criar um **segundo prompt** com abordagem diferente:

| Prompt Original | Prompt Alternativo |
|----------------|-------------------|
| "Extraia TODOS os dados..." (top-down) | "Liste as seções do documento e para cada seção..." (bottom-up) |
| Formato JSON livre | Formato CSV (para forçar extração diferente) |

#### 7.2.4 Lógica de Cross-Validation

```
Resultado A (1ª extração) vs Resultado B (2ª extração):

Campo "nome":      A="JOÃO SILVA"    B="JOÃO SILVA"    → MATCH ✅ (confiança 1.0)
Campo "cpf":       A="123.456.789-00" B="123.456.789-00" → MATCH ✅ (confiança 1.0)
Campo "salarioBruto": A="5432.10"     B="5432.10"       → MATCH ✅ (confiança 1.0)
Campo "rubrica001":   A="1234.56"     B="1234.56"       → MATCH ✅ (confiança 1.0)
Campo "rubrica015":   A="89.90"       B="98.90"         → DIVERGE ⚠️ (flag revisão)
```

### 7.3 Custo da Fase 3

- Custo Gemini **dobra** apenas nos casos onde `confidenceScore < 0.85`
- Estimativa: ~5-15% dos documentos precisam de dupla extração
- Custo real: ~$0.003 extra por página reprocessada

---

## 8. Fase 4 — Escalação Automática para Gemini Pro

### 8.1 Objetivo

Quando mesmo a dupla extração (Fase 3) resulta em campos divergentes, escalar para o modelo `gemini-2.5-pro` que é mais preciso.

### 8.2 Escopo de Alterações

#### 8.2.1 Alteração: `GeminiPdfServiceImpl.java`

Adicionar método que usa o modelo Pro:

```java
/**
 * Processa com modelo Pro (mais preciso, mais caro).
 * Chamado apenas quando Flash + Cross-Validation falham.
 */
public Mono<String> processWithProModel(byte[] pdfBytes, int pageNumber, String prompt) {
    return processWithModel(proModel, pdfBytes, pageNumber, prompt);
}
```

#### 8.2.2 Alteração: `CrossValidationService`

Após cross-validation, se `requiresManualReview = true`:

```java
if (crossResult.requiresManualReview()) {
    log.warn("⚠️ Escalando para Gemini 2.5 Pro...");
    return geminiService.processWithProModel(pdfBytes, pageNumber, prompt);
}
```

### 8.3 Custo da Fase 4

- Gemini 2.5 Pro: ~$0.011/página (vs $0.003 do Flash)
- Usado apenas em ~1-3% dos documentos (os mais problemáticos)
- Custo real: praticamente negligível

---

## 9. Estimativa de Custos

### 9.1 Cenário: 500 páginas/mês

| Fase | Estimativa de Uso | Custo/mês |
|------|-------------------|-----------|
| Camada 1 (iText/PDFBox) | 500 páginas | **$0.00** |
| Camada 2 (Gemini Flash) | ~100 páginas (20% escaneados) | **$0.26** |
| Camada 3 (Dupla extração) | ~15 páginas (15% da camada 2) | **$0.04** |
| Camada 4 (Gemini Pro) | ~3 páginas (3% da camada 2) | **$0.03** |
| **TOTAL** | | **~$0.33/mês** |

### 9.2 Cenário: 5.000 páginas/mês

| Fase | Estimativa de Uso | Custo/mês |
|------|-------------------|-----------|
| Camada 1 (iText/PDFBox) | 5.000 páginas | **$0.00** |
| Camada 2 (Gemini Flash) | ~1.000 páginas (20% escaneados) | **$2.63** |
| Camada 3 (Dupla extração) | ~150 páginas | **$0.39** |
| Camada 4 (Gemini Pro) | ~30 páginas | **$0.33** |
| **TOTAL** | | **~$3.35/mês** |

### 9.3 Cenário: 50.000 páginas/mês

| Fase | Estimativa de Uso | Custo/mês |
|------|-------------------|-----------|
| Camada 1 (iText/PDFBox) | 50.000 páginas | **$0.00** |
| Camada 2 (Gemini Flash) | ~10.000 páginas | **$26.30** |
| Camada 3 (Dupla extração) | ~1.500 páginas | **$3.95** |
| Camada 4 (Gemini Pro) | ~300 páginas | **$3.30** |
| **TOTAL** | | **~$33.55/mês** |

> **Nota**: Estes custos são apenas da API do Gemini. Os custos de infraestrutura (Cloud Run, MongoDB Atlas, etc.) são separados.

---

## 10. Riscos e Mitigações

| # | Risco | Impacto | Probabilidade | Mitigação |
|---|-------|---------|---------------|-----------|
| 1 | Gemini 2.5 Flash alucina dados | Alto | Média | Fases 2-3 (validação + dupla extração) |
| 2 | Google muda preços do Gemini | Médio | Baixa | Arquitetura desacoplada — fácil trocar modelo |
| 3 | Gemini 2.5 Flash sai do ar | Alto | Muito baixa | Fallback para Gemini 2.5 Pro; extração nativa continua funcionando |
| 4 | Formato de resposta do Gemini muda | Médio | Baixa | `cleanResponse()` já trata variações; prompts pedem JSON estrito |
| 5 | PDFs com qualidade muito baixa | Alto | Média | Fase 4 escala para Pro; documentar limite de qualidade aceitável |
| 6 | Latência alta do Gemini 2.5 | Baixo | Média | Timeout configurável; processamento assíncrono (WebFlux) |
| 7 | SDK Vertex AI incompatível | Médio | Baixa | Testar em branch; manter versão anterior como fallback |

---

## 11. Registro de Implementação — Fase 1

> **Data de execução**: 12/02/2026
> **Branch**: `feat/optimize-logging-cloud-run`

### 11.1 Arquivos Alterados

| # | Arquivo | Alteração Realizada |
|---|---------|---------------------|
| 1 | `build.gradle.kts` | SDK `google-cloud-vertexai` de **1.2.0** → **1.43.0** |
| 2 | `application.yml` | Modelo `gemini-2.5-flash`, fallback `gemini-2.5-pro`, timeout **60s→120s**, variáveis de ambiente para model/location |
| 3 | `.env.example` | Adicionadas variáveis `GEMINI_MODEL`, `GEMINI_FALLBACK_MODEL`, `GEMINI_LOCATION` |
| 4 | `.env` | Adicionado `GOOGLE_CLOUD_PROJECT=rrr-software-solutions`, corrigido `MONGODB_URI`→`SPRING_DATA_MONGODB_URI` |
| 5 | `GeminiConfig.java` | Novo campo `fallbackModel` (default: `gemini-2.5-pro`), documentação JavaDoc completa |
| 6 | `GeminiPdfServiceImpl.java` | Refatoração completa: 2 modelos (`primaryModel` + `fallbackModel`), métodos `*WithFallback()`, método genérico `processWithModel()`, logging com nome do modelo |
| 7 | `GeminiPrompts.java` | 4 prompts reescritos com auto-verificação, formato monetário explícito, contexto BR. 2 prompts alternativos (`_ALT`) adicionados para cross-validation futura |
| 8 | `AiConfigResponse.java` | Novo campo `fallbackModel` no record |
| 9 | `AiConfigRequest.java` | Novo campo `fallbackModel` no record |
| 10 | `AiConfigController.java` | Suporte para salvar `fallbackModel` via API, mensagens de status atualizadas |
| 11 | `SystemConfig.java` | Nova constante `KEY_AI_FALLBACK_MODEL = "ai.fallback-model"` |

### 11.2 Configuração do GCP (Realizada em 12/02/2026)

#### O que foi feito via Console do Google Cloud (https://console.cloud.google.com):

1. **Projeto usado**: `RRR-Software-Solutions` (ID: `rrr-software-solutions`, Número: `177627167012`)
2. **Vertex AI API**: Ativada via Console → APIs e serviços → Biblioteca → "Vertex AI API" → Ativar
   - Status confirmado: "Ativadas" (nome do serviço: `aiplatform.googleapis.com`)
3. **Faturamento**: Conta de faturamento já vinculada ao projeto

#### O que foi feito via Terminal (PowerShell):

```powershell
# Comando executado para configurar credenciais locais (ADC):
gcloud auth application-default login
```

- Abriu o navegador na tela "O app Google Auth Library quer acessar sua Conta do Google"
- Conta usada: `rbrodrigues@gmail.com`
- Permissões concedidas: "Selecionar tudo" (ver/editar dados do Google Cloud + ver instâncias do Cloud SQL)
- Credenciais salvas automaticamente em: `%APPDATA%\gcloud\application_default_credentials.json`

#### O que foi configurado no `.env`:

```env
GOOGLE_CLOUD_PROJECT=rrr-software-solutions
```

### 11.3 Validação da Fase 1

**Comando de build:**
```powershell
.\gradlew.bat compileJava
# Resultado: BUILD SUCCESSFUL in 27s (apenas warnings de deprecação do SDK — esperados)
```

**Comando de execução:**
```powershell
# Carregamento do .env + execução:
Get-Content .env | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
}
.\gradlew.bat bootRun
```

**Log de inicialização do Gemini (confirmado em 12/02/2026 às 22:42):**
```
2026-02-12T22:42:11.257 INFO  GeminiPdfServiceImpl : Inicializando clientes Gemini AI...
2026-02-12T22:42:11.257 INFO  GeminiPdfServiceImpl :   Project ID: rrr-software-solutions
2026-02-12T22:42:11.259 INFO  GeminiPdfServiceImpl :   Location: us-central1
2026-02-12T22:42:11.259 INFO  GeminiPdfServiceImpl :   Modelo principal: gemini-2.5-flash
2026-02-12T22:42:11.259 INFO  GeminiPdfServiceImpl :   Modelo fallback: gemini-2.5-pro
2026-02-12T22:42:11.259 INFO  GeminiPdfServiceImpl :   Max output tokens: 8192
2026-02-12T22:42:11.259 INFO  GeminiPdfServiceImpl :   Temperature: 0.1
2026-02-12T22:42:11.260 INFO  GeminiPdfServiceImpl :   Timeout: 120s
2026-02-12T22:42:11.342 INFO  GeminiPdfServiceImpl : Cliente Gemini AI inicializado com sucesso - modelos: [gemini-2.5-flash] e [gemini-2.5-pro]
```

**Aplicação rodando**: `http://localhost:8081` (Netty started on port 8081)

### 11.4 Habilitação da IA via API (Passo 4 — Realizado em 12/02/2026)

> **Pré-requisitos**: Aplicação rodando em `http://localhost:8081` com Gemini inicializado.

#### Passo 1 — Login como SUPER_ADMIN

**Endpoint:** `POST /api/v1/auth/login`

```powershell
# Login para obter o token JWT:
$loginBody = '{"email":"superadmin@teste.com","password":"SuperAdmin123!"}'
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" `
    -Method POST -ContentType "application/json" -Body $loginBody
```

**Resposta recebida:**
```json
{
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...(token JWT truncado)",
    "refreshToken": "f9ac9b0e-d8a9-46c7-af86-86502bc01ca6",
    "requires2FA": null,
    "message": null
}
```

> **Nota**: O `accessToken` é um JWT com role `SUPER_ADMIN`, necessário para acessar o endpoint de configuração.

#### Passo 2 — Habilitar IA via PUT /config/ai

**Endpoint:** `PUT /api/v1/config/ai`

**Headers necessários:**
- `Authorization: Bearer {accessToken}`
- `Content-Type: application/json`

```powershell
# Habilitar IA com modelo principal e fallback:
$token = "<accessToken obtido no Passo 1>"
$headers = @{Authorization="Bearer $token"}
$body = '{"enabled":true,"model":"gemini-2.5-flash","fallbackModel":"gemini-2.5-pro"}'
$result = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/config/ai" `
    -Method PUT -ContentType "application/json" -Headers $headers -Body $body
```

**Resposta recebida (sucesso):**
```json
{
    "enabled": true,
    "model": "gemini-2.5-flash",
    "fallbackModel": "gemini-2.5-pro",
    "credentialsConfigured": true,
    "projectId": "rrr-software-solutions",
    "location": "us-central1",
    "updatedAt": "2026-02-13T01:54:12.670201Z",
    "updatedBy": "system",
    "statusMessage": "IA habilitada e pronta. Modelo principal: gemini-2.5-flash, Fallback: gemini-2.5-pro."
}
```

#### Observações Importantes

| Campo | Significado |
|-------|-------------|
| `enabled: true` | IA está **ligada** — PDFs escaneados serão processados pelo Gemini |
| `credentialsConfigured: true` | Credenciais ADC do GCP estão configuradas corretamente |
| `model: gemini-2.5-flash` | Modelo principal usado para extração (rápido e barato) |
| `fallbackModel: gemini-2.5-pro` | Modelo de reserva para casos complexos (Fase 4 futura) |

> **Nota sobre o path da API**: O path correto é `/api/v1/config/ai` (com prefixo `/api/v1`). Uma tentativa com `/config/ai` (sem prefixo) retornou `404 NOT_FOUND`.

### 11.5 Teste com PDF Escaneado (Realizado em 12/02/2026)

> **Arquivo**: `CONTRACHEQUESCAIXA2024.pdf` (12 páginas, 2.6 MB, 100% escaneado/imagem)
> **Tipo detectado**: CAIXA
> **Status final**: PROCESSED

#### Endpoints chamados no teste

**1. Login como SUPER_ADMIN:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"superadmin@teste.com","password":"SuperAdmin123!"}
```

**2. Upload do PDF:**
```http
POST /api/v1/documents/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: CONTRACHEQUESCAIXA2024.pdf
cpf: 12345678909
nome: Vanderson Teste
```

Resposta:
```json
{"documentId":"698e8618a1e15c4662530a9e","status":"PENDING","tipoDetectado":"CAIXA"}
```

**3. Disparar processamento:**
```http
POST /api/v1/documents/698e8618a1e15c4662530a9e/process
Authorization: Bearer {token}
```

Resposta:
```json
{"documentId":"698e8618a1e15c4662530a9e","status":"PROCESSING","message":"Processamento iniciado."}
```

**4. Consultar status do documento:**
```http
GET /api/v1/documents/698e8618a1e15c4662530a9e
Authorization: Bearer {token}
```

Resposta:
```json
{"id":"698e8618a1e15c4662530a9e","cpf":"12345678909","status":"PROCESSED","tipo":"CAIXA","entriesCount":4,"dataProcessamento":"2026-02-13T02:05:45.169Z"}
```

**5. Consultar entries extraídas:**
```http
GET /api/v1/documents/698e8618a1e15c4662530a9e/entries
Authorization: Bearer {token}
```

Resposta (4 entries da página 10):
```json
[
  {"rubricaCodigo":"4346","rubricaDescricao":"FUNCEF - NOVO PLANO","referencia":"2024-10","valor":1130.33},
  {"rubricaCodigo":"4443","rubricaDescricao":"FUNCEF CONTR. EQUACIONAMENTO3 SALDADO","referencia":"2024-10","valor":511.09},
  {"rubricaCodigo":"4432","rubricaDescricao":"FUNCEF CONTR. EQUACIONAMENTO2 SALDADO","referencia":"2024-10","valor":380.11},
  {"rubricaCodigo":"4412","rubricaDescricao":"FUNCEF CONTR. EQUACIONAMENTO1 SALDADO","referencia":"2024-10","valor":133.12}
]
```

#### Comandos PowerShell usados

```powershell
# Login
$loginBody = '{"email":"superadmin@teste.com","password":"SuperAdmin123!"}'
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" `
    -Method POST -ContentType "application/json" -Body $loginBody
$token = $response.accessToken

# Upload (usando curl.exe para multipart)
curl.exe -s -X POST "http://localhost:8081/api/v1/documents/upload" `
    -H "Authorization: Bearer $token" `
    -F "file=@C:\caminho\para\CONTRACHEQUESCAIXA2024.pdf" `
    -F "cpf=12345678909" -F "nome=Vanderson Teste"

# Processar
curl.exe -s -X POST "http://localhost:8081/api/v1/documents/{documentId}/process" `
    -H "Authorization: Bearer $token" -H "Content-Type: application/json"

# Consultar status
curl.exe -s "http://localhost:8081/api/v1/documents/{documentId}" `
    -H "Authorization: Bearer $token"

# Consultar entries
curl.exe -s "http://localhost:8081/api/v1/documents/{documentId}/entries" `
    -H "Authorization: Bearer $token"
```

#### Resultado do Gemini por página

O PDF é 100% escaneado (baseado em imagem). O PDFBox extraiu apenas 2 caracteres por página, então o **Gemini 2.5 Flash foi acionado em todas as páginas**:

| Página | Mês/Ano | Tempo Gemini | Chars extraídos | Rubricas parser | Entries |
|--------|---------|-------------|----------------|----------------|---------|
| 1 | Jan/2024 | 17.5s | 1.903 | 0 | 0 |
| 2 | Fev/2024 | 16.9s | 2.457 | 0 | 0 |
| 3 | Mar/2024 | 12.0s | 1.946 | 0 | 0 |
| 4 | Abr/2024 | 14.0s | 1.954 | 0 | 0 |
| 5 | Mai/2024 | 31.0s | 128.140 | 0 | 0 |
| 6 | Jun/2024 | 15.3s | 2.067 | 0 | 0 |
| 7 | Jul/2024 | 14.8s | 2.162 | 0 | 0 |
| 8 | Ago/2024 | 16.6s | 1.907 | 0 | 0 |
| 9 | Set/2024 | 16.8s | 2.421 | 0 | 0 |
| 10 | Out/2024 | 18.0s | 5.655 | **19** | **4** |
| 11 | Nov/2024 | 15.7s | 2.521 | 0 | 0 |
| 12 | Dez/2024 | ~16s | ~2.000 | 0 | 0 |
| **Total** | | **~3 min 35s** | | **19** | **4** |

> **Tempo total de processamento**: ~3 min 37s (23:02:08 a 23:05:45)

#### O que funcionou

1. **Gemini acionado corretamente** — todas as 12 páginas escaneadas foram detectadas (texto < 100 chars) e enviadas ao Gemini 2.5 Flash
2. **Extração de texto bem-sucedida** — Gemini extraiu entre ~1.900 e ~5.600 chars por página com dados reais: SALARIO PADRAO (R$10.985,00), ADICIONAL TEMPO DE SERVICO (R$3.734,90), FUNCEF, INSS, IRPF, etc.
3. **Página 10 (Out/2024)** — 19 rubricas extraídas pelo parser, 4 salvas como entries (FUNCEF Novo Plano + 3 Equacionamentos)
4. **Integração end-to-end funcional** — upload → processamento → Gemini → parser → MongoDB

#### Problema identificado: Parser Regex vs Texto do Gemini

O **parser de regex** (`PdfLineParser`) não conseguiu converter o texto extraído pelo Gemini em rubricas nas páginas 1-9 e 11-12. A causa raiz:

- O texto do Gemini vem em formato linear (uma informação por linha), diferente do layout tabular do PDF original
- Os 5 padrões regex do parser esperam o formato: `CÓDIGO  DESCRIÇÃO  COMPETÊNCIA  PRAZO  VALOR` em uma única linha
- O Gemini separa código, descrição, competência e valor em linhas diferentes (ex: linha 52=`2002`, linha 53=`SALARIO PADRAO`, linha 54=`11/2024`, linha 55=`11.495,00`)
- A página 10 funcionou porque o Gemini retornou com formato diferente que casou com os regex

**Exemplo do formato extraído pelo Gemini (página 11):**
```
LINHA[52]: 2002
LINHA[53]: SALARIO PADRAO
LINHA[54]: 11/2024
LINHA[55]: 11.495,00
LINHA[56]: 2007
LINHA[57]: ADICIONAL TEMPO DE SERVICO
LINHA[58]: 11/2024
LINHA[59]: 4.023,25
```

**Formato esperado pelo parser (uma linha):**
```
2002  SALARIO PADRAO  11/2024  11.495,00
```

#### Conclusão e Próximo Passo

A Fase 1 provou que:
- O Gemini 2.5 Flash **funciona e extrai texto corretamente** das imagens escaneadas
- A integração está **100% operacional**
- O **gargalo está no parser regex**, que não foi projetado para texto OCR do Gemini

**Solução (Fase 2)**: Usar o prompt `CONTRACHEQUE_EXTRACTION` do `GeminiPrompts.java` para pedir ao Gemini que retorne **JSON estruturado** diretamente (código, descrição, competência, valor), eliminando a dependência do parser regex para páginas escaneadas. Isso já está preparado nos prompts da Fase 1.

### 11.6 Pendências da Fase 1

| Pendência | Status | Descrição |
|-----------|--------|-----------|
| ~~Habilitar IA via API~~ | **Concluído** | `PUT /api/v1/config/ai` — IA habilitada em 12/02/2026 |
| ~~Testar com PDF escaneado~~ | **Concluído** | Gemini funcional, parser regex precisa de adaptação (ver 11.5) |
| ~~Extração JSON estruturada~~ | **Concluído** | Gemini agora retorna JSON ao invés de texto cru (Fase 2) |
| ~~Validação por regras de negócio~~ | **Concluído** | Score de confiança 0.0-1.0 com 6 regras para contracheques (Fase 2) |
| ~~Cross-Validation (Dupla Extração)~~ | **Concluído** | 2ª extração com prompt alternativo quando score < 0.85 (Fase 3) |
| ~~Escalação para Gemini Pro~~ | **Concluído** | Auto-escala para Pro quando campos críticos divergem (Fase 4) |
| Testar com PDF digital | Pendente | Confirmar que PDFs digitais continuam sendo processados por iText/PDFBox |
| Testar todas as fases com PDF escaneado | Pendente | Verificar fluxo completo: JSON + validação + cross-validation + Pro |
| Migração do SDK | Futuro | `google-cloud-vertexai` → `google-genai` (prazo: antes de Jun/2026) |

---

## 12. Registro de Implementação — Fase 2

> **Data de execução**: 12/02/2026
> **Branch**: `feat/optimize-logging-cloud-run`

### 12.1 O que foi feito

A Fase 2 resolveu dois problemas simultaneamente:

1. **Extração JSON Estruturada (correção do gargalo)**: O Gemini agora retorna JSON estruturado com rubricas, ao invés de texto cru que passava pelo parser regex.
2. **Validação por Regras de Negócio**: Após a extração, os dados são validados com regras de negócio (soma proventos, CPF, competência, etc.) e um score de confiança é atribuído.

### 12.2 Arquivos Criados

| # | Arquivo | Tipo | Descrição |
|---|---------|------|-----------|
| 1 | `domain/model/ValidationResult.java` | **NOVO** | Record com `confidenceScore` (0.0-1.0), `isValid`, `issues`, `recommendation` (ACCEPT/REVIEW/REJECT) |
| 2 | `domain/model/ValidationIssue.java` | **NOVO** | Record com `field`, `type`, `expected`, `found`, `message`. Constantes para tipos de validação |
| 3 | `domain/service/ExtractionValidationService.java` | **NOVO** | Interface com `validatePayrollExtraction()` e `validateIncomeTaxExtraction()` |
| 4 | `infrastructure/validation/ExtractionValidationServiceImpl.java` | **NOVO** | Implementação com 6 regras para contracheques e 5 regras para IR, cada uma com peso ponderado |
| 5 | `infrastructure/ai/GeminiResponseParser.java` | **NOVO** | Parser JSON → PayrollEntry. Converte resposta estruturada do Gemini em entries do sistema |

### 12.3 Arquivos Alterados

| # | Arquivo | Alteração |
|---|---------|-----------|
| 1 | `domain/model/PayrollDocument.java` | Adicionados campos `confidenceScore` (Double) e `validationRecommendation` (String) |
| 2 | `application/documents/DocumentProcessUseCase.java` | Refatoração do `processPageWithMetadata()`: páginas escaneadas agora usam `extractPayrollData()` (JSON) ao invés de `extractTextFromScannedPage()` (texto cru). Integrado `ExtractionValidationService`. Método `processPageTextWithParser()` extraído para reutilização como fallback |

### 12.4 Fluxo Antes vs Depois

**Antes (Fase 1):**
```
PDF escaneado → Gemini (texto cru) → Parser Regex → 0 entries (falha)
```

**Depois (Fase 2):**
```
PDF escaneado → Gemini (JSON estruturado) → GeminiResponseParser → N entries ✅
                                          → ExtractionValidationService → score de confiança
```

**PDFs digitais** continuam usando o fluxo tradicional:
```
PDF digital → PDFBox (texto) → Parser Regex → entries (sem mudança)
```

### 12.5 Regras de Validação Implementadas

**Contracheques (6 regras, peso total 100%):**

| Regra | Peso | Validação |
|-------|------|-----------|
| SOMA_PROVENTOS | 20% | Soma rubricas de provento ≈ salário bruto (tolerância 1%) |
| SOMA_DESCONTOS | 20% | Soma rubricas de desconto ≈ total descontos (tolerância 1%) |
| LIQUIDO_CORRETO | 25% | Bruto - descontos ≈ líquido (tolerância 1%) |
| CPF_VALIDO | 15% | CPF passa validação Mod11 |
| COMPETENCIA_VALIDA | 10% | Formato MM/YYYY com mês 01-13 |
| VALORES_POSITIVOS | 10% | Sem valores negativos |

**Declaração de IR (5 regras + neutro):**

| Regra | Peso | Validação |
|-------|------|-----------|
| CPF_VALIDO | 10% | CPF passa validação Mod11 |
| ANO_COERENTE | 10% | Exercício = Ano Calendário + 1 |
| RESULTADO_COERENTE | 25% | Devido - Pago = Saldo/Restituição |
| VALORES_POSITIVOS | 10% | Valores monetários >= 0 |
| CAMPOS_OBRIGATORIOS | 5% | Nome, CPF, exercício, ano calendário preenchidos |

### 12.6 Custo da Fase 2

- **Validação**: $0 (regras Java puras)
- **Extração JSON**: Mesmo custo da Fase 1 (usa o mesmo Gemini Flash), porém com prompt diferente (`CONTRACHEQUE_EXTRACTION` ao invés de `EXTRACAO_TEXTO_GENERICO`)

---

## 13. Registro de Implementação — Fase 3

> **Data de execução**: 12/02/2026
> **Branch**: `feat/optimize-logging-cloud-run`

### 13.1 O que foi feito

Implementação da **Dupla Extração com Cross-Validation**. Quando a validação da Fase 2 retorna `confidenceScore < 0.85`, uma segunda extração é executada com prompt alternativo (bottom-up) e os resultados são comparados campo a campo.

### 13.2 Arquivos Criados

| # | Arquivo | Tipo | Descrição |
|---|---------|------|-----------|
| 1 | `domain/model/FieldComparison.java` | **NOVO** | Record com comparação campo a campo (`field`, `valueA`, `valueB`, `match`, `finalValue`) |
| 2 | `domain/model/CrossValidationResult.java` | **NOVO** | Record com resultado consolidado: entries, score, comparisons, `requiresManualReview` |
| 3 | `domain/service/CrossValidationService.java` | **NOVO** | Interface `crossValidate(pdfBytes, page, entries, json, docId, tenant, origem)` |
| 4 | `infrastructure/validation/CrossValidationServiceImpl.java` | **NOVO** | Implementação: 2ª extração com `CONTRACHEQUE_EXTRACTION_ALT`, comparação de metadados e rubricas |

### 13.3 Arquivos Alterados

| # | Arquivo | Alteração |
|---|---------|-----------|
| 1 | `DocumentProcessUseCase.java` | Injeção de `CrossValidationService`. Quando `validation.isValid() == false` (score < 0.85), aciona `crossValidationService.crossValidate()` automaticamente |

### 13.4 Fluxo Completo (Fase 1 + 2 + 3)

```
PDF escaneado → PDFBox (texto < 100 chars)
    ↓
Gemini Flash (CONTRACHEQUE_EXTRACTION — JSON estruturado)  [Fase 1+2]
    ↓
GeminiResponseParser → entries
    ↓
ExtractionValidationService → score de confiança  [Fase 2]
    ↓
┌─── score >= 0.85 → ACCEPT ✅ → usar entries direto
└─── score < 0.85  → CROSS-VALIDATION  [Fase 3]
        ↓
    Gemini Flash (CONTRACHEQUE_EXTRACTION_ALT — prompt bottom-up)
        ↓
    Comparação campo a campo:
     • Metadados: nome, CPF, matrícula, competência, salários
     • Rubricas: por código → compara valor
        ↓
    ┌─── match → alta confiança, usar valor
    └─── diverge → flag ⚠️, usar valor da 1ª extração
        ↓
    Entries consolidadas
```

### 13.5 Lógica de Comparação

**Metadados comparados:**
- `nome`, `cpf` (crítico), `matricula`, `competencia`
- `salarioBruto` (crítico), `totalDescontos`, `salarioLiquido` (crítico)

**Rubricas comparadas:**
- Indexadas por código (union de ambas extrações)
- Valores comparados com tolerância de R$ 0,01 (centavos)
- Rubricas encontradas em apenas uma extração são incluídas com flag

**Campos críticos** (CPF, salário bruto, líquido): se divergirem, `requiresManualReview = true`.

### 13.6 Custo da Fase 3

- **Custo extra**: ~$0.003 por página reprocessada (1 chamada adicional ao Gemini Flash)
- **Quando acionado**: Apenas quando `confidenceScore < 0.85` (~5-15% dos documentos)
- **Custo zero se score >= 0.85** (maioria dos casos)

---

## 14. Registro de Implementação — Fase 4

> **Data de execução**: 12/02/2026
> **Branch**: `feat/optimize-logging-cloud-run`

### 14.1 O que foi feito

Implementação da **Escalação Automática para Gemini Pro**. Quando a cross-validation (Fase 3) sinaliza `requiresManualReview = true` (campos críticos como CPF, salário bruto ou líquido divergem entre as duas extrações), o sistema automaticamente escala para o modelo `gemini-2.5-pro`, que é mais preciso (~$0.011/página vs ~$0.003 do Flash).

### 14.2 Arquivos Alterados

| # | Arquivo | Alteração |
|---|---------|-----------|
| 1 | `domain/service/AiPdfExtractionService.java` | Adicionado método `extractPayrollDataWithFallback()` na interface |
| 2 | `application/documents/DocumentProcessUseCase.java` | Após cross-validation com `requiresManualReview = true`, chama `extractPayrollDataWithFallback()` (Gemini Pro). Se Pro falhar, mantém resultado da cross-validation como fallback |

### 14.3 Fluxo Completo Final (Fases 1+2+3+4)

```
PDF escaneado → PDFBox (texto < 100 chars)
    ↓
Gemini Flash (CONTRACHEQUE_EXTRACTION — JSON)  [Fase 1+2]
    ↓
GeminiResponseParser → entries
    ↓
ExtractionValidationService → score  [Fase 2]
    ↓
┌─── score >= 0.85 → ACCEPT ✅ → usar entries
└─── score < 0.85  → CROSS-VALIDATION  [Fase 3]
        ↓
    Gemini Flash (CONTRACHEQUE_EXTRACTION_ALT)
        ↓
    Comparação campo a campo
        ↓
    ┌─── requiresManualReview = false → usar entries consolidadas
    └─── requiresManualReview = true  → ESCALAR PARA PRO  [Fase 4]
            ↓
        Gemini Pro (CONTRACHEQUE_EXTRACTION — JSON)
            ↓
        GeminiResponseParser → entries (resultado final)
```

### 14.4 Quando o Gemini Pro é acionado

O Pro é acionado **apenas** quando campos **críticos** divergem entre as duas extrações do Flash:
- **CPF** difere entre extração A e B
- **Salário Bruto** difere entre extração A e B
- **Salário Líquido** difere entre extração A e B

Estimativa: ~1-3% dos documentos escaneados chegam a este ponto.

### 14.5 Custo da Fase 4

- **Gemini Pro**: ~$0.011/página
- **Quando acionado**: ~1-3% dos documentos (os mais problemáticos)
- **Custo real mensal (500 páginas)**: ~$0.03/mês
- Se o Pro também falhar, o resultado da cross-validation (Flash) é mantido como fallback

---

## Cronograma

| Fase | Esforço Estimado | Status | Data |
|------|-----------------|--------|------|
| **Fase 1** — Upgrade Gemini | 1-2 dias | **Implementada** | 12/02/2026 |
| **Fase 2** — Validação + JSON Estruturado | 2-3 dias | **Implementada** | 12/02/2026 |
| **Fase 3** — Dupla Extração + Cross-Validation | 1-2 dias | **Implementada** | 12/02/2026 |
| **Fase 4** — Escalação Pro | 0.5-1 dia | **Implementada** | 12/02/2026 |
| **Total** | **~5-8 dias** | **TODAS CONCLUÍDAS** | 12/02/2026 |

---

## Referências

- [Google Gemini 2.5 Flash — Vertex AI Docs](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash)
- [Google Gemini 2.5 Pro — Vertex AI Docs](https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro)
- [Vertex AI Pricing](https://cloud.google.com/vertex-ai/generative-ai/pricing)
- [iText 8 — Text Extraction](https://kb.itextpdf.com/itext/extracting-text-from-pdf-files)
- [PDFBox 3.0 — Text Extraction](https://pdfbox.apache.org/3.0/migration.html)
