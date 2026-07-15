# API_3_PROCESS_DOCUMENT.md

## 🎯 Objetivo da API 3 — Processar Documento PDF  
Esta API inicia o processamento **reativo**, **assíncrono** e **não bloqueante** de um documento PDF previamente enviado via API_2.  
Ela aplica o pipeline:

1. Carregar documento (já com tipo detectado no upload)
2. Usar tipo já detectado (CAIXA, FUNCEF, CAIXA_FUNCEF)
3. Extrair metadados (nome, CPF, etc.)
4. Extrair rubricas página por página
5. Criar payroll_entries
6. Atualizar status para PROCESSED
7. Retornar resultado

---

# 1. ENDPOINT

## ▶️ POST /api/v1/documents/{id}/process

### 🔹 Request
Sem body.  
Apenas o ID do documento enviado no upload.

**Importante**: O `{id}` no path é o `_id` do documento na coleção `payroll_documents` (não é o ID da `persons`).

**Exemplo**:
- Documento na coleção `payroll_documents`:
  ```json
  {
    "_id": "692b7a795413c429c49cc319",
    "cpf": "12449709568",
    "tipo": "CAIXA",
    "status": "PENDING",
    ...
  }
  ```
- Request para processar:
  ```
  POST /api/v1/documents/692b7a795413c429c49cc319/process
  ```
  
**Nota**: O MongoDB retorna o `_id` como ObjectId (`{"$oid": "..."}`), mas na API usamos apenas a string hexadecimal (ex: `"692b7a795413c429c49cc319"`).

### 🔹 Response (assíncrono)
```json
{
  "documentId": "65f123abc",
  "status": "PROCESSING",
  "message": "Processamento iniciado."
}
```

### 🔹 Response final (buscando o documento depois)
```json
{
  "documentId": "65f123abc",
  "status": "PROCESSED",
  "entries": 148,
  "tipoDocumento": "CAIXA_FUNCEF"
}
```

---

# 1.1 Identificação do Documento

**O `documentId` usado na API é o `_id` do documento na coleção `payroll_documents`.**

**Estrutura do documento em `payroll_documents`**:
```json
{
  "_id": "692b7a795413c429c49cc319",  // ← Este é o documentId usado na API
  "cpf": "12449709568",
  "fileHash": "011b9258bb787f458dac27280a3445592857731d177a6bc1b020c2edc4eb4d9b",
  "tipo": "CAIXA",
  "status": "PENDING",
  "originalFileId": "692b7a785413c429c49cc315",
  ...
}
```

**Relacionamento com `persons`**:
- O documento `payroll_documents` está relacionado a uma `Person` através do campo `cpf`
- A `Person` mantém uma lista de IDs de documentos no campo `documentos`:
  ```json
  {
    "_id": "692b7a785413c429c49cc314",
    "cpf": "12449709568",
    "documentos": [
      "692b7a795413c429c49cc319",  // ← IDs dos documentos em payroll_documents
      "692b7a865413c429c49cc31c",
      ...
    ]
  }
  ```

**Para processar um documento, use o `_id` de `payroll_documents`**:
```
POST /api/v1/documents/692b7a795413c429c49cc319/process
```

---

# 1.2 Informações Já Disponíveis do Upload (API_2)

O documento já possui as seguintes informações pré-processadas:

- ✅ **Tipo detectado**: CAIXA, FUNCEF ou CAIXA_FUNCEF
- ✅ **Ano detectado**: Ano mais recente encontrado no PDF
- ✅ **Meses detectados**: Lista completa de meses no formato ["2016-01", "2016-02", ...]
- ✅ **Origem por página**: Cada página já tem sua origem identificada (CAIXA ou FUNCEF)
- ✅ **CPF validado**: CPF já foi validado e normalizado
- ✅ **Nome**: Nome do titular (se fornecido no upload)

**Otimização**: O processamento pode pular a detecção de tipo e origem das páginas, indo direto para extração de rubricas.

---

# 2. FLUXO COMPLETO DO PROCESSAMENTO

**Nota**: O fluxo abaixo assume que o documento já tem tipo e origem das páginas detectados no upload.

```mermaid
flowchart TD
    A[POST /process] --> B[Carregar documento do Mongo]
    B --> C{Existe?}
    C -->|Não| X[404 NOT FOUND]
    C -->|Sim| D[status=PROCESSING]
    D --> E[Carregar PDF (GridFS/Binary)]
    E --> F[Usar tipo já detectado no upload]
    F --> G[Extrair metadados (nome, CPF, etc.)]
    G --> H[Processar páginas (origem já detectada)]
    H --> I[Extrair rubricas via regex]
    I --> J[Normalizar valores/datas]
    J --> K[Validar rubricas com API 1]
    K --> L[Persistir payroll_entries]
    L --> M[status=PROCESSED]
    M --> N[Gerar resumo -> atualizar documento]
```

---

# 2.1 FLUXO DETALHADO PASSO A PASSO

## 📥 1. Recebimento da Requisição

```
POST /api/v1/documents/692b7a795413c429c49cc319/process
```

- **Request**: Apenas o ID do documento no path
  - O `{id}` é o `_id` do documento na coleção `payroll_documents`
  - Exemplo: `"692b7a795413c429c49cc319"` (string hexadecimal do ObjectId)
- **Body**: Não necessário (vazio)
- **Headers**: Padrão HTTP

**Exemplo real**:
```json
// Documento em payroll_documents
{
  "_id": "692b7a795413c429c49cc319",
  "cpf": "12449709568",
  "tipo": "CAIXA",
  "status": "PENDING",
  "originalFileId": "692b7a785413c429c49cc315",
  ...
}

// Request
POST /api/v1/documents/692b7a795413c429c49cc319/process
```

## ✅ 2. Validações Iniciais

### 2.1 Busca do Documento
- Busca o documento na coleção `payroll_documents` usando `findById(documentId)`
  - O `documentId` é o `_id` do documento (ex: `"692b7a795413c429c49cc319"`)
  - Busca na coleção `payroll_documents`, **não** na coleção `persons`
- **Se não existir** → Retorna `404 NOT FOUND` com mensagem: "Documento não encontrado: {id}"

**Exemplo de busca**:
```java
// documentRepository.findById("692b7a795413c429c49cc319")
// Busca em: db.payroll_documents.findOne({ _id: ObjectId("692b7a795413c429c49cc319") })
```

### 2.2 Verificação de Status
- Verifica se `document.status == PENDING`
- **Se já foi processado** (`status == PROCESSED` ou `status == PROCESSING`) → Retorna `409 CONFLICT` com mensagem: "Documento já foi processado. Status atual: {status}"
- **Se está PENDING** → Continua o processamento

## 🚀 3. Resposta Imediata (Assíncrono)

A API retorna **imediatamente** (202 Accepted) sem esperar o processamento terminar:

```json
{
  "documentId": "65f123abc",
  "status": "PROCESSING",
  "message": "Processamento iniciado."
}
```

**Importante**: O processamento continua em **background** de forma assíncrona.

## ⚙️ 4. Processamento em Background

### 4.1 Atualização de Status Inicial

```java
document.setStatus(DocumentStatus.PROCESSING);
documentRepository.save(document);
```

- Atualiza o documento no MongoDB
- Status muda de `PENDING` → `PROCESSING`

### 4.2 Carregamento do PDF do GridFS

```java
gridFsService.retrieveFile(document.getOriginalFileId())
```

- Busca o PDF no GridFS usando o `originalFileId` salvo no documento
- Lê o arquivo binário completo em memória
- **Se falhar** → Atualiza documento com `status = ERROR` e retorna `400 BAD REQUEST`

### 4.3 Processamento Página por Página

Para **cada página** do PDF (de 1 até totalPages):

#### a) Extração do Texto da Página

```java
pdfService.extractTextFromPage(pdfBytes, pageNumber)
```

- Usa Apache PDFBox para extrair texto da página
- Retorna o texto completo da página como String

#### b) Identificação da Origem da Página

```java
String origem = determinePageOrigin(document, pageNumber);
```

- Usa a informação já detectada no upload (`detectedPages`)
- Exemplo:
  - Página 1 → `origem = "CAIXA"`
  - Página 2 → `origem = "CAIXA"`
  - Página 3 → `origem = "FUNCEF"`
- Se não encontrar, usa o tipo geral do documento como fallback

#### c) Detecção de Mês/Ano da Página

```java
monthYearDetectionService.detectMonthYear(pageText)
```

- Procura padrões no texto:
  - **CAIXA**: "JANEIRO / 2016" ou "Mês/Ano de Pagamento: JANEIRO / 2016"
  - **FUNCEF**: "2018/01" ou "Ano Pagamento / Mês: 2018/01"
- Normaliza para formato `"YYYY-MM"` (ex: `"2016-01"`)
- **Se não encontrar**: Usa o primeiro mês da lista `mesesDetectados` do documento como fallback

#### d) Extração de Rubricas via Regex

**Para CAIXA:**

```
Regex: ^([0-9]\s*[0-9]\s*[0-9]\s*[0-9]?)\s+(.+?)\s+(?:([0-9]{3})\s+)?([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})([0-9]{2}/[0-9]{4})\s*$
```

**Exemplo de linha do PDF:**
```
1034 AC  APIP/IP - CONVERSAO 001 1.632,1301/2016
2002 SALARIO PADRAO 5.518,0001/2016
1090 AC  GRAT NATAL - 13 SALARIO 195,7012/2015
```

**Campos extraídos:**
- **Código**: `"1034"` (pode ter espaços, normalizado removendo espaços)
- **Descrição**: `"AC  APIP/IP - CONVERSAO"`
- **Prazo** (opcional): `"001"` (pode estar ausente)
- **Valor**: `"1.632,13"` (com data colada no final)
- **Referência**: `"01/2016"` (extraída do final do valor, formato MM/YYYY normalizado para `"2016-01"`)

**Nota**: 
- No formato CAIXA, a **data está colada no valor** (ex: `"1.632,1301/2016"`)
- O regex separa valor (`"1.632,13"`) e referência (`"01/2016"`)
- Prazo é opcional (3 dígitos)
- Código pode ter espaços (normalizado)

**Para FUNCEF:**

```
Regex: ^([0-9]\s*[0-9]\s*[0-9]\s*[0-9]?)\s+([0-9]{4}/[0-9]{2})\s+(.+?)\s+([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})\s*$
```

**Exemplo de linha do PDF:**
```
2 033 2018/01 SUPL. APOS. TEMPO CONTRIB. BENEF. SALD. 4.741,41
4 430 2018/01 CONTRIBUIÇÃO EXTRAORDINARIA 2014 131,81
4 362 2018/01 TAXA ADMINISTRATIVA - SALDADO 37,93
```

**Campos extraídos:**
- **Código**: `"2 033"` (normalizado para `"2033"` removendo espaços)
- **Referência**: `"2018/01"` (formato YYYY/MM, normalizado para `"2018-01"`)
- **Descrição**: `"SUPL. APOS. TEMPO CONTRIB. BENEF. SALD."`
- **Valor**: `"4.741,41"` (sem data colada, diferente de CAIXA)

**Nota**: 
- O formato FUNCEF tem a referência **na mesma linha**, antes da descrição
- Código pode ter espaços (ex: `"2 033"`, `"4 430"`) que são removidos na normalização
- Valor não tem data colada (diferente de CAIXA que tem formato `"1.632,1301/2016"`)

#### e) Normalização dos Dados

**Valores Monetários:**
```
"1.385,66" → 1385.66 (double)
"885,47" → 885.47 (double)
"R$ 424,10" → 424.10 (double)
```

- Remove `R$` e espaços
- Remove pontos (separadores de milhar)
- Substitui vírgula por ponto
- Converte para `Double`

**Datas/Referências:**
```
"01/2017" → "2017-01"
"2017/01" → "2017-01"
"2017-01" → "2017-01" (já normalizado)
```

- Detecta formato (MM/YYYY ou YYYY/MM)
- Normaliza para formato padrão `"YYYY-MM"`

**Descrições:**
```
"CONTRIBUIÇÃO   EXTRAORDINÁRIA" → "CONTRIBUIÇÃO EXTRAORDINÁRIA"
```
- Remove espaços extras
- Trim (remove espaços no início/fim)

#### f) Criação de PayrollEntry

Para cada rubrica extraída e normalizada, cria um objeto `PayrollEntry`:

```json
{
  "documentoId": "692b7a795413c429c49cc319",
  "rubricaCodigo": "4482",
  "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
  "referencia": "2017-08",
  "valor": 885.47,
  "origem": "CAIXA",
  "pagina": 3
}
```

**Validações durante criação:**
- Se referência não puder ser normalizada → Entry ignorada (log warning)
- Se valor não puder ser normalizado → Entry ignorada (log warning)
- Entry válida é adicionada à lista

### 4.4 Validação de Rubricas com API 1

Para **cada entry criada**, valida se a rubrica existe no banco:

```java
rubricaValidator.validateRubrica(entry.getRubricaCodigo(), entry.getRubricaDescricao())
```

**Processo de validação:**

1. **Busca a rubrica** na coleção `rubricas` usando o código:
   ```java
   rubricaRepository.findByCodigo("4482")
   ```

2. **Verifica se está ativa**:
   - Se `rubrica.ativo == false` → Entry ignorada (log warning)

3. **Regra especial — código + descrição obrigatórios** (somente estas rubricas):
   - `3396` → descrição esperada: `REP TAXA ADMINISTRATIVA BUA NOVO PLANO`
   - `4432` → descrição esperada: `FUNCEF CONTR. EQUACIONAMENTO2 SALDADO`
   - `4436` → descrição esperada: `FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL`
   - Comparação após normalização (trim, espaços colapsados, maiúsculas)
   - Se o código bater mas a descrição **não** → Entry **ignorada** (valor não é considerado)

4. **Demais rubricas**: validam só por código (e ativo). A descrição extraída não bloqueia.

5. **Resultado**:
   - **Se encontrar, estiver ativa e (quando aplicável) a descrição bater** → Entry mantida
   - **Se não encontrar, estiver inativa ou falhar a regra 3396/4432/4436** → Entry ignorada (log warning)

**Exemplo (regra especial):**
```
Entry extraída: código "4432", descrição "FUNCEF CONTR. EQUACIONAMENTO2 SALDADO"
Resultado: ✅ Entry válida

Entry extraída: código "4432", descrição "FUNCEF CONTRIB EQU SALDADO 02"
Resultado: ❌ Entry ignorada (descrição não bate)
```

### 4.5 Persistência no Banco de Dados

```java
entryRepository.saveAll(Flux.fromIterable(validEntries))
```

- Salva todas as entries válidas na coleção `payroll_entries`
- Usa `saveAll()` reativo (não bloqueia a thread)
- Conta quantas entries foram salvas

**Estrutura salva no MongoDB:**

```json
{
  "_id": "entry123",
  "documentoId": "692b7a795413c429c49cc319",
  "rubricaCodigo": "4482",
  "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
  "referencia": "2017-08",
  "valor": 885.47,
  "origem": "CAIXA",
  "pagina": 3
}
```

### 4.6 Atualização Final do Documento

```java
document.setStatus(DocumentStatus.PROCESSED);
document.setDataProcessamento(Instant.now());
document.setTotalEntries(entriesCount);
documentRepository.save(document);
```

**Campos atualizados:**
- `status`: `PROCESSING` → `PROCESSED`
- `dataProcessamento`: Data/hora atual (Instant)
- `totalEntries`: Quantidade de entries salvas (ex: 148)

**Resultado final do documento:**

```json
{
  "id": "65f123abc",
  "status": "PROCESSED",
  "totalEntries": 148,
  "tipo": "CAIXA_FUNCEF",
  "anoDetectado": 2018,
  "mesesDetectados": ["2016-01", "2016-02", "2017-01", "2018-01"],
  "dataProcessamento": "2024-01-15T10:30:00Z"
}
```

## 🚨 5. Tratamento de Erros

### 5.1 Erro em Etapa Crítica

Se ocorrer erro em etapas críticas (carregar PDF, salvar entries):

```java
document.setStatus(DocumentStatus.ERROR);
document.setErro(error.getMessage());
documentRepository.save(document);
```

- Atualiza documento com `status = ERROR`
- Salva mensagem de erro no campo `erro`
- Loga o erro completo para debug

### 5.2 Erro em Página Específica

Se ocorrer erro ao processar uma página específica:

```java
.onErrorResume(error -> {
    log.error("Erro ao processar página {}", pageNumber, error);
    return Mono.just(new ArrayList<>()); // Retorna lista vazia
})
```

- **Não interrompe** o processamento das outras páginas
- Loga o erro da página
- Continua processando as páginas restantes
- Entries da página com erro não são salvas

### 5.3 Rubricas Não Encontradas

Se uma rubrica extraída não existir na API 1:

- Entry é **ignorada** (não salva)
- Log warning: `"Rubrica {codigo} não encontrada ou inativa. Entry ignorada."`
- Processamento continua normalmente

## 📊 6. Exemplo Completo de Processamento

### Entrada
```
POST /api/v1/documents/692b7a795413c429c49cc319/process
```

**Onde `692b7a795413c429c49cc319` é o `_id` do documento em `payroll_documents`**

### Resposta Imediata (202 Accepted)
```json
{
  "documentId": "692b7a795413c429c49cc319",
  "status": "PROCESSING",
  "message": "Processamento iniciado."
}
```

### Processamento em Background

**Cenário**: PDF com 10 páginas

1. **Página 1 (CAIXA)**:
   - Extrai 15 rubricas
   - Valida: 15 válidas
   - Salva: 15 entries

2. **Página 2 (CAIXA)**:
   - Extrai 12 rubricas
   - Valida: 12 válidas
   - Salva: 12 entries

3. **Página 3 (FUNCEF)**:
   - Extrai 8 rubricas
   - Valida: 7 válidas (1 rubrica não encontrada)
   - Salva: 7 entries

4. **Páginas 4-10**: Processamento similar...

**Resultado Final:**
- Total extraído: 150 rubricas
- Após validação: 148 válidas (2 ignoradas)
- Entries salvas: 148

### Resultado Final (ao consultar o documento)

```json
{
  "id": "692b7a795413c429c49cc319",
  "status": "PROCESSED",
  "totalEntries": 148,
  "tipo": "CAIXA_FUNCEF",
  "anoDetectado": 2018,
  "mesesDetectados": ["2016-01", "2016-02", "2017-01", "2018-01"],
  "dataProcessamento": "2024-01-15T10:30:00Z"
}
```

**Nota**: O `id` retornado é o mesmo `_id` do documento em `payroll_documents`.

## 🎯 7. Características Importantes

### 7.1 Assíncrono
- Retorna resposta imediata (202 Accepted)
- Processamento continua em background
- Cliente não precisa esperar

### 7.2 Não Bloqueante
- Usa WebFlux/Reactor
- Não bloqueia threads
- Processa páginas em paralelo quando possível

### 7.3 Reativo
- Pipeline reativo com `Mono` e `Flux`
- Processamento página por página
- Validação e persistência reativas

### 7.4 Resiliente
- Erro em uma página não interrompe as outras
- Rubricas inválidas são ignoradas (não quebram o processamento)
- Logs detalhados para debug

### 7.5 Validado
- Só salva rubricas que existem na API 1
- Verifica se rubrica está ativa
- Match parcial de descrições

### 7.6 Rastreável
- Logs em cada etapa do processamento
- Status do documento atualizado em tempo real
- Campo `erro` para diagnóstico

## 📈 8. Resumo Visual do Fluxo

```
📄 PDF Upload (API 2)
    ↓
📋 Documento salvo (status: PENDING)
    ↓
🔄 POST /api/v1/documents/{id}/process (API 3)
    ↓
✅ Validações (existe? status PENDING?)
    ↓
🚀 Resposta imediata (202 Accepted)
    ↓
⚙️ Processamento em background
    ├─ 📄 Carrega PDF do GridFS
    ├─ 📑 Processa cada página
    │   ├─ 🔍 Extrai texto (PDFBox)
    │   ├─ 🎯 Identifica origem (CAIXA/FUNCEF)
    │   ├─ 📅 Detecta mês/ano
    │   ├─ 🔎 Extrai rubricas (regex)
    │   └─ ✏️ Normaliza dados
    ├─ ✅ Valida rubricas (API 1)
    ├─ 💾 Salva entries no MongoDB
    └─ 📊 Atualiza documento (status: PROCESSED)
```

**Resultado**: PDF transformado em dados estruturados prontos para consolidação e exportação.

---

# 3. PIPELINE REATIVO (WebFlux + Reactor)

O processamento é **assíncrono**, com etapas encadeadas usando **Mono** e **Flux**:

1. `findById(documentId)` - Carrega documento com tipo já detectado
2. Verifica se `status == PENDING` (não processar novamente se já foi processado)
3. Atualiza `status = PROCESSING`
4. `flatMap(processor::processDocument)` - Processa usando tipo e páginas já detectadas
5. Pipeline interno cria `Flux<PayrollEntry>` página por página
6. Valida rubricas com API 1 (Rubricas)
7. `repository.saveAll(entries)` - Salva todas as entries
8. Atualiza `status = PROCESSED` e `dataProcessamento`
9. Atualiza `Person.nome` se extraído e ainda não preenchido

Nenhuma etapa deve bloquear (`NO blocking IO`).

**Otimização**: Como o tipo e origem das páginas já foram detectados no upload, o processamento pode pular a detecção e ir direto para extração.

---

# 4. TIPO DO DOCUMENTO

## 4.1 Uso do Tipo Já Detectado

**Importante**: O tipo do documento já foi detectado durante o upload (API_2).  
O processamento **não precisa detectar novamente**, apenas usar o tipo já salvo no documento.

### Tipos Disponíveis:
- **CAIXA**: Documento exclusivamente da CAIXA
- **FUNCEF**: Documento exclusivamente da FUNCEF  
- **CAIXA_FUNCEF**: Documento misto (contém páginas de ambos os tipos)

### Informações Já Disponíveis do Upload:
- `tipo`: Tipo do documento (CAIXA, FUNCEF ou CAIXA_FUNCEF)
- `anoDetectado`: Ano mais recente encontrado
- `mesesDetectados`: Lista de meses no formato ["2016-01", "2016-02", ...]
- `detectedPages`: Lista com origem de cada página (CAIXA ou FUNCEF)

O processamento deve usar essas informações para otimizar a extração.

---

# 5. EXTRAÇÃO DE METADADOS

## 5.1 Informações Já Disponíveis

Do upload (API_2), já temos:
- `cpf`: CPF do titular (validado)
- `nome`: Nome do titular (se fornecido no upload, caso contrário será extraído)
- `anoDetectado`: Ano mais recente encontrado
- `mesesDetectados`: Lista completa de meses detectados
- `detectedPages`: Origem de cada página

## 5.2 Metadados Adicionais a Extrair

### 📌 Caixa — Metadados adicionais

| Campo | Exemplo | Origem |
|------|---------|--------|
| nome | "FLAVIO JOSE..." | Topo (se não foi fornecido no upload) |
| agenciaConta | "AG 004 - CC 123456" | Rodapé |
| siglaGIREC | "GIREC 123" | Cabeçalho |

**Nota**: `mesAnoPagamento` já foi detectado no upload por página.

---

### 📌 Funcef — Metadados adicionais

| Campo | Exemplo | Origem |
|-------|---------|--------|
| nome | "FLAVIO JOSE..." | Topo (se não foi fornecido no upload) |
| valorLiquido | "R$ 1.385,66" | Rodapé |
| numeroBeneficio | "123456789" | Cabeçalho |

**Nota**: `referencia` (mês/ano) já foi detectado no upload por página.

---

# 6. EXTRAÇÃO DE RUBRICAS

## 6.1 Regex Caixa

```
^([0-9]\s*[0-9]\s*[0-9]\s*[0-9]?)\s+(.+?)\s+(?:([0-9]{3})\s+)?([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})([0-9]{2}/[0-9]{4})\s*$
```

**Formato real encontrado:**
```
1034 AC  APIP/IP - CONVERSAO 001 1.632,1301/2016
2002 SALARIO PADRAO 5.518,0001/2016
1090 AC  GRAT NATAL - 13 SALARIO 195,7012/2015
```

Campos capturados:

1. **Código** (com espaços opcionais): `1034`, `2002` → normalizado removendo espaços
2. **Descrição**: Texto completo da rubrica
3. **Prazo** (opcional, 3 dígitos): `001` (pode estar ausente)
4. **Valor** (com data colada): `1.632,13` (extraído de `"1.632,1301/2016"`)
5. **Referência** (MM/YYYY): `01/2016` (extraída do final do valor, normalizado para `"2016-01"`)

**Nota**: No formato CAIXA, a data está **colada no valor** (sem espaço), então o regex separa valor e referência.

---

## 6.2 Regex Funcef

```
^([0-9]\s*[0-9]\s*[0-9]\s*[0-9]?)\s+([0-9]{4}/[0-9]{2})\s+(.+?)\s+([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2})\s*$
```

**Formato real encontrado:**
```
2 033 2018/01 SUPL. APOS. TEMPO CONTRIB. BENEF. SALD. 4.741,41
4 430 2018/01 CONTRIBUIÇÃO EXTRAORDINARIA 2014 131,81
4 362 2018/01 TAXA ADMINISTRATIVA - SALDADO 37,93
```

**Campos capturados:**
1. **Código** (com espaços opcionais): `2 033`, `4 430` → normalizado para `2033`, `4430`
2. **Referência** (YYYY/MM): `2018/01` → normalizado para `2018-01`
3. **Descrição**: Texto completo da rubrica
4. **Valor** (sem data colada): `4.741,41` → normalizado para `4741.41`

**Diferenças do formato CAIXA:**
- FUNCEF tem referência **separada** na linha (antes da descrição)
- FUNCEF não tem data colada no valor (CAIXA tem formato `"1.632,1301/2016"`)
- FUNCEF código pode ter espaços (normalizado removendo espaços)

---

# 7. NORMALIZAÇÃO

## 7.1 Valores

```
"1.385,66" → 1385.66
"885,47" → 885.47
```

## 7.2 Datas

```
"01/2017" → 2017-01
"2017/01" → 2017-01
```

## 7.3 Descrição da Rubrica

- match exato com banco
- ou match parcial (contains)
- log se não existir

---

# 8. SALVANDO ENTRIES

## 8.1 Estrutura da PayrollEntry

Cada rubrica extraída e validada gera um documento na coleção `payroll_entries`:

```json
{
  "_id": "entry123",
  "documentoId": "692b7a795413c429c49cc319",
  "rubricaCodigo": "4482",
  "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
  "referencia": "2017-08",
  "valor": 885.47,
  "origem": "CAIXA",
  "pagina": 3
}
```

### Campos da PayrollEntry

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `_id` | String | ✔ | ID único gerado pelo MongoDB |
| `documentoId` | String | ✔ | Referência ao documento (PayrollDocument) |
| `rubricaCodigo` | String | ✔ | Código da rubrica (ex: "4482") |
| `rubricaDescricao` | String | ✔ | Descrição extraída do PDF |
| `referencia` | String | ✔ | Mês/ano no formato "YYYY-MM" (ex: "2017-08") |
| `valor` | Double | ✔ | Valor numérico normalizado |
| `origem` | String | ✔ | Origem da rubrica: "CAIXA" ou "FUNCEF" |
| `pagina` | Integer | ❌ | Número da página onde foi extraída (1-indexed) |

## 8.2 Índices no MongoDB

Para otimizar consultas, são criados índices:

- **Índice em `documentoId`**: Para buscar todas as entries de um documento
- **Índice em `rubricaCodigo`**: Para buscar entries por rubrica
- **Índice em `referencia`**: Para buscar entries por mês/ano

## 8.3 Exemplo de Múltiplas Entries

Após processar um documento, a coleção `payroll_entries` pode conter:

```json
[
  {
    "_id": "entry001",
    "documentoId": "692b7a795413c429c49cc319",
    "rubricaCodigo": "4482",
    "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
    "referencia": "2017-08",
    "valor": 885.47,
    "origem": "CAIXA",
    "pagina": 1
  },
  {
    "_id": "entry002",
    "documentoId": "692b7a795413c429c49cc319",
    "rubricaCodigo": "3430",
    "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "referencia": "2017-08",
    "valor": 424.10,
    "origem": "CAIXA",
    "pagina": 1
  },
  {
    "_id": "entry003",
    "documentoId": "692b7a795413c429c49cc319",
    "rubricaCodigo": "4444",
    "rubricaDescricao": "FUNCEF CONTRIB EQU SALDADO 03 GRT NATAL",
    "referencia": "2018-01",
    "valor": 150.00,
    "origem": "FUNCEF",
    "pagina": 3
  }
]
```

---

# 9. ATUALIZAÇÃO FINAL DO DOCUMENTO

Após processamento:

```json
{
  "id": "doc123",
  "status": "PROCESSED",
  "totalEntries": 148,
  "tipo": "CAIXA_FUNCEF",
  "anoDetectado": 2018,
  "mesesDetectados": ["2016-01", "2016-02", "2017-01", "2018-01"]
}
```

---

# 10. ERROS POSSÍVEIS

## 10.1 Erros que Retornam Status HTTP

| Caso | Status HTTP | Descrição | Ação |
|------|-------------|-----------|------|
| Documento não existe | 404 NOT FOUND | ID inválido ou documento não encontrado | Verificar ID do documento |
| Documento já processado | 409 CONFLICT | Status != PENDING (já foi processado ou está processando) | Consultar documento para ver status atual |
| PDF inválido/corrompido | 400 BAD REQUEST | Não foi possível ler PDF do GridFS | Verificar integridade do arquivo no GridFS |
| Erro interno | 500 INTERNAL SERVER ERROR | Erro inesperado no processamento | Verificar logs do servidor |

## 10.2 Erros que Não Interrompem o Processamento

| Caso | Ação | Log |
|------|------|-----|
| Nenhuma rubrica encontrada em uma página | Página ignorada, continua processamento | WARN: "Nenhuma rubrica encontrada na página {X}" |
| Funcef sem referência | Usa referência do cabeçalho ou fallback | WARN: "Não foi possível detectar referência na página {X}" |
| Rubrica não encontrada na API 1 | Entry ignorada, continua processamento | WARN: "Rubrica {codigo} não encontrada ou inativa. Entry ignorada." |
| Regex não reconhece linha | Linha ignorada, continua processamento | DEBUG: "Linha não corresponde ao padrão: {linha}" |
| Erro ao processar uma página | Página ignorada, continua com outras páginas | ERROR: "Erro ao processar página {X}: {erro}" |

## 10.3 Tratamento de Erros em Background

Se ocorrer erro durante o processamento em background:

1. **Documento atualizado**:
   ```json
   {
     "id": "65f123abc",
     "status": "ERROR",
     "erro": "Mensagem do erro detalhada",
     "dataProcessamento": null
   }
   ```

2. **Logs detalhados**:
   - Stack trace completo
   - Contexto do erro (página, rubrica, etc.)
   - Timestamp do erro

3. **Entries parciais**:
   - Se o erro ocorrer após salvar algumas entries, elas permanecem no banco
   - Documento pode ser reprocessado (após correção do erro)

---

# 11. ORDEM DE IMPLEMENTAÇÃO

**Nota**: `DocumentTypeDetectionService` já existe e é usado no upload (API_2).  
O processamento deve **reutilizar** o tipo já detectado, não detectar novamente.

1. Criar `DocumentProcessorService` (use case principal)
2. Criar `CaixaMetadataExtractor` (extrai metadados específicos da CAIXA)
3. Criar `FuncefMetadataExtractor` (extrai metadados específicos da FUNCEF)
4. Criar `PdfLineParser` (extrai linhas de rubricas via regex)
5. Criar normalizadores (valores, datas, descrições)
6. Criar `PayrollEntryRepository` e modelo `PayrollEntry`
7. Integrar validação de rubricas com API 1 (Rubricas)
8. Persistência das entries (saveAll reativo)
9. Atualização do documento (status, dataProcessamento)
10. Atualização do Person.nome (se extraído)
11. Testes unitários + testes com PDFs reais

---

# 12. CLASSES QUE DEVEM EXISTIR

### Classes de Domínio:
- `PayrollEntry` (modelo de entrada extraída)
- `PayrollEntryRepository` (interface de repositório)

### Classes de Aplicação:
- `DocumentProcessUseCase` (use case principal do processamento)

### Classes de Infraestrutura:
- `CaixaMetadataExtractor` (extrai metadados específicos da CAIXA)
- `FuncefMetadataExtractor` (extrai metadados específicos da FUNCEF)
- `PdfLineParser` (extrai linhas de rubricas via regex)
- `PdfNormalizer` (normaliza valores, datas, descrições)
- `RubricaValidator` (valida rubricas com API 1)

### Classes Já Existentes (Reutilizar):
- `DocumentTypeDetectionService` ✅ (já existe, usado no upload)
- `PdfService` ✅ (já existe, extrai texto de PDFs)
- `MonthYearDetectionService` ✅ (já existe, detecta mês/ano)
- `PayrollDocumentRepository` ✅ (já existe)
- `PersonRepository` ✅ (já existe)

---

# 13. EXEMPLOS DE USO

## 13.1 Processar um Documento

### Request
```bash
curl -X POST http://localhost:8080/api/v1/documents/692b7a795413c429c49cc319/process
```

**Onde `692b7a795413c429c49cc319` é o `_id` do documento em `payroll_documents`**

### Response Imediata (202 Accepted)
```json
{
  "documentId": "692b7a795413c429c49cc319",
  "status": "PROCESSING",
  "message": "Processamento iniciado."
}
```

### Consultar Status do Processamento

Após alguns segundos/minutos, consultar o documento:

```bash
curl http://localhost:8080/api/v1/documents/692b7a795413c429c49cc319
```

### Response (se processado)
```json
{
  "id": "692b7a795413c429c49cc319",
  "status": "PROCESSED",
  "totalEntries": 148,
  "tipo": "CAIXA_FUNCEF",
  "dataProcessamento": "2024-01-15T10:30:00Z"
}
```

### Response (se ainda processando)
```json
{
  "id": "65f123abc",
  "status": "PROCESSING",
  "totalEntries": null,
  "dataProcessamento": null
}
```

### Response (se erro)
```json
{
  "id": "692b7a795413c429c49cc319",
  "status": "ERROR",
  "erro": "Erro ao ler PDF do GridFS: File not found",
  "dataProcessamento": null
}
```

## 13.2 Consultar Entries Extraídas

Após processamento bem-sucedido, consultar as entries:

```bash
curl http://localhost:8080/api/v1/documents/692b7a795413c429c49cc319/entries
```

### Response
```json
[
  {
    "id": "entry001",
    "documentoId": "692b7a795413c429c49cc319",
    "rubricaCodigo": "4482",
    "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2015",
    "referencia": "2017-08",
    "valor": 885.47,
    "origem": "CAIXA",
    "pagina": 1
  },
  {
    "id": "entry002",
    "documentoId": "692b7a795413c429c49cc319",
    "rubricaCodigo": "3430",
    "rubricaDescricao": "CONTRIBUIÇÃO EXTRAORDINÁRIA 2014",
    "referencia": "2017-08",
    "valor": 424.10,
    "origem": "CAIXA",
    "pagina": 1
  }
]
```

**Nota**: O campo `documentoId` nas entries referencia o `_id` do documento em `payroll_documents`.

## 13.3 Fluxo Completo de Uso

1. **Upload do PDF** (API 2):
   ```bash
   curl -X POST http://localhost:8080/api/v1/documents/upload \
     -F "file=@contracheque.pdf" \
     -F "cpf=12345678900" \
     -F "nome=João Silva"
   ```
   Response: `{ "documentId": "692b7a795413c429c49cc319", "status": "PENDING" }`
   
   **O `documentId` retornado é o `_id` do documento criado em `payroll_documents`**

2. **Processar o documento** (API 3):
   ```bash
   curl -X POST http://localhost:8080/api/v1/documents/692b7a795413c429c49cc319/process
   ```
   Response: `{ "documentId": "692b7a795413c429c49cc319", "status": "PROCESSING" }`
   
   **Usa o mesmo `_id` do documento em `payroll_documents`**

3. **Aguardar processamento** (polling ou webhook):
   - Consultar periodicamente o status do documento
   - Ou aguardar notificação (se implementado)

4. **Consultar resultado**:
   ```bash
   curl http://localhost:8080/api/v1/documents/692b7a795413c429c49cc319
   ```
   Response: `{ "status": "PROCESSED", "totalEntries": 148 }`
   
   **Consulta o documento em `payroll_documents` usando o `_id`**

5. **Usar dados extraídos**:
   - Consultar entries para consolidação (API 5)
   - Exportar para Excel (API 6)
   - Gerar relatórios

---

# 14. PERFORMANCE E OTIMIZAÇÕES

## 14.1 Tempo de Processamento

Tempos estimados (dependem do tamanho do PDF):

| Tamanho do PDF | Páginas | Tempo Estimado |
|----------------|---------|----------------|
| Pequeno (< 1MB) | 1-5 páginas | 5-15 segundos |
| Médio (1-5MB) | 6-20 páginas | 15-60 segundos |
| Grande (> 5MB) | 21+ páginas | 1-5 minutos |

## 14.2 Otimizações Implementadas

1. **Reutilização de dados do upload**:
   - Tipo do documento já detectado
   - Origem das páginas já identificada
   - Meses/anos já detectados
   - Evita reprocessamento desnecessário

2. **Processamento reativo**:
   - Não bloqueia threads
   - Processa páginas em paralelo quando possível
   - Validação e persistência assíncronas

3. **Validação eficiente**:
   - Valida rubricas em batch
   - Cache de rubricas (se implementado)
   - Ignora entries inválidas sem interromper

4. **Persistência otimizada**:
   - `saveAll()` reativo (bulk insert)
   - Índices no MongoDB para consultas rápidas

## 14.3 Limitações Conhecidas

1. **PDFs muito grandes** (> 50MB):
   - Podem consumir muita memória
   - Tempo de processamento pode ser longo

2. **PDFs com formatação não padrão**:
   - Regex pode não reconhecer algumas linhas
   - Requer ajuste dos padrões

3. **Rubricas não cadastradas**:
   - São ignoradas (não quebram o processamento)
   - Devem ser cadastradas na API 1 antes do processamento

---

Fim do arquivo.  
Este documento serve como *guia oficial* para a implementação e uso da API de PROCESSAMENTO.
