# 📄 API de Upload de Declaração de Imposto de Renda - Documentação para Frontend

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Autenticação e Autorização](#autenticação-e-autorização)
3. [Endpoint](#endpoint)
4. [Modelos de Dados](#modelos-de-dados)
5. [Fluxo de Funcionamento](#fluxo-de-funcionamento)
6. [Exemplos de Implementação](#exemplos-de-implementação)
7. [Tratamento de Erros](#tratamento-de-erros)
8. [Casos de Uso](#casos-de-uso)

---

## 🎯 Visão Geral

A API de Upload de Declaração de Imposto de Renda permite enviar um PDF de declaração de ajuste anual do IRPF, salvar o documento no sistema e **processar automaticamente** para extrair as informações de imposto.

**Base URL**: `http://localhost:8081/api/v1`

**Versão da API**: `v1`

### O que a API faz:

1. **Recebe** o PDF da declaração de IR
2. **Valida** o CPF e verifica se a pessoa existe
3. **Calcula** o hash do arquivo para evitar duplicidade
4. **Salva** o arquivo no GridFS (MongoDB)
5. **Extrai** metadata (Ano-Calendário) da página RESUMO
6. **Cria** um documento do tipo `INCOME_TAX`
7. **Associa** o documento ao CPF da pessoa
8. **Inicia processamento automático** para extrair valores de imposto
9. **Retorna** o ID do documento com status `PROCESSING`

### Dados Extraídos Automaticamente:

- Base de cálculo do imposto
- Imposto devido
- Dedução de incentivo
- Imposto devido I, II, RRA
- Contribuição Prev. Empregador Doméstico
- Total do imposto devido

### Importante:

- O documento é **processado automaticamente** após o upload
- O status retornado será `PROCESSING` (não é necessário chamar endpoint de processamento)
- Não é necessário chamar `/documents/{id}/process` manualmente
- O documento é associado ao CPF, assim como os contracheques
- Para gerar Excel com os dados consolidados, use o endpoint de exportação de Excel

---

## 🔐 Autenticação e Autorização

### Requisitos

O endpoint requer:

1. **Autenticação JWT**: Token de acesso válido no header `Authorization`
2. **Roles permitidas**:
   - `SUPER_ADMIN`: Pode fazer upload para qualquer pessoa
   - `TENANT_ADMIN`: Pode fazer upload para pessoas do seu tenant
   - `TENANT_USER`: Pode fazer upload para pessoas do seu tenant

### Headers Obrigatórios

```http
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

### Exemplo de Requisição Autenticada

```javascript
const headers = {
  'Authorization': `Bearer ${accessToken}`
  // Content-Type será definido automaticamente pelo navegador para multipart/form-data
};
```

---

## 📡 Endpoint

### Upload de Declaração de Imposto de Renda

```
POST /api/v1/documents/upload-income-tax
```

#### Descrição

Faz upload de um PDF de declaração de imposto de renda e **processa automaticamente** para extrair informações da página RESUMO (valores de imposto devido, base de cálculo, etc.).

#### Parâmetros (Multipart Form Data)

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File (PDF) | ✔ | Arquivo PDF da declaração de ajuste anual do IRPF |
| `cpf` | String | ✔ | CPF da pessoa (formato: com ou sem formatação) |

#### Request Body

```javascript
const formData = new FormData();
formData.append('file', pdfFile); // File object do PDF
formData.append('cpf', '12449709568'); // CPF sem formatação ou com formatação
```

#### Response Sucesso (201 CREATED)

```json
{
  "documentId": "65f123abc",
  "status": "PROCESSING",
  "tipoDetectado": "INCOME_TAX"
}
```

**Campos da Resposta:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `documentId` | string | ID único do documento criado no banco de dados |
| `status` | string | Status do documento: `PROCESSING` (processamento automático iniciado) |
| `tipoDetectado` | string | Tipo do documento: `INCOME_TAX` |

#### Possíveis Erros

| Código HTTP | Motivo | Response Body |
|-------------|--------|---------------|
| 400 | PDF inválido ou arquivo corrompido | `{"status": 400, "error": "Arquivo inválido. Deve ser um PDF válido."}` |
| 404 | Pessoa não encontrada para o CPF informado | `{"status": 404, "error": "Pessoa não encontrada para CPF: 12449709568"}` |
| 409 | Arquivo duplicado (mesmo hash já existe) | `{"status": 409, "error": "Este arquivo já foi enviado anteriormente. DocumentId: 65f123abc"}` |
| 422 | CPF inválido (não passa na validação da Receita Federal) | `{"status": 422, "error": "CPF inválido: 12345678900"}` |
| 500 | Erro interno do servidor | `{"status": 500, "error": "Erro ao processar declaração de IR: {mensagem}"}` |

---

## 📊 Modelos de Dados

### Response (JSON)

```typescript
interface UploadDocumentResponse {
  documentId: string;         // ID único do documento
  status: string;            // "PENDING" | "PROCESSING" | "PROCESSED" | "ERROR"
  tipoDetectado: string;     // "INCOME_TAX"
}
```

### Error Response

```typescript
interface ErrorResponse {
  status: number;              // Código HTTP do erro
  error: string;               // Mensagem de erro descritiva
}
```

---

## 🔄 Fluxo de Funcionamento

### Diagrama de Fluxo

```
1. Frontend envia PDF + CPF
   ↓
2. Backend valida CPF
   ↓
3. Backend verifica se pessoa existe
   ↓
4. Backend lê arquivo e calcula hash SHA-256
   ↓
5. Backend verifica duplicidade (mesmo hash)
   ↓
6. Backend salva arquivo no GridFS (MongoDB)
   ↓
7. Backend extrai metadata (Ano-Calendário) da página RESUMO
   ↓
8. Backend cria documento tipo INCOME_TAX
   ↓
9. Backend associa documento ao CPF da pessoa
   ↓
10. Backend INICIA PROCESSAMENTO AUTOMÁTICO
   ↓
11. Backend retorna ID com status PROCESSING
```

### Detalhamento

1. **Validação**: CPF é validado conforme regras da Receita Federal
2. **Verificação de Pessoa**: A pessoa deve existir no sistema (ter pelo menos um contracheque cadastrado)
3. **Deduplicação**: Hash SHA-256 é calculado para evitar uploads duplicados
4. **Armazenamento**: Arquivo é salvo no GridFS (MongoDB) com deduplicação
5. **Extração de Metadata**: Extrai Ano-Calendário da página RESUMO
6. **Criação do Documento**: Documento é criado com:
   - Tipo: `INCOME_TAX`
   - Ano detectado: Ano-Calendário extraído
7. **Associação**: Documento é adicionado à lista de documentos da pessoa
8. **Processamento Automático**: Backend inicia extração de valores (Base cálculo, Imposto devido, etc.)

---

## 💻 Exemplos de Implementação

### JavaScript/TypeScript (Fetch API)

```typescript
interface UploadDocumentResponse {
  documentId: string;
  status: string;
  tipoDetectado: string;
}

async function uploadIncomeTaxDeclaration(
  pdfFile: File, 
  cpf: string, 
  accessToken: string
): Promise<UploadDocumentResponse> {
  const formData = new FormData();
  formData.append('file', pdfFile);
  formData.append('cpf', cpf);

  const response = await fetch(
    'http://localhost:8081/api/v1/documents/upload-income-tax',
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`
        // NÃO definir Content-Type manualmente para FormData
      },
      body: formData
    }
  );

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || `Erro ${response.status}`);
  }

  const data = await response.json();
  return data;
}

// Uso:
try {
  const result = await uploadIncomeTaxDeclaration(
    pdfFile, 
    '12449709568', 
    accessToken
  );

  console.log('Documento criado:', result.documentId);
  console.log('Status:', result.status);
  console.log('Tipo:', result.tipoDetectado);
  
  alert(`Declaração de IR enviada com sucesso! DocumentId: ${result.documentId}`);
} catch (error) {
  console.error('Erro ao fazer upload:', error);
  alert(`Erro: ${error.message}`);
}
```

### React com Axios

```typescript
import axios from 'axios';

interface UploadDocumentResponse {
  documentId: string;
  status: string;
  tipoDetectado: string;
}

async function uploadIncomeTaxDeclaration(
  pdfFile: File,
  cpf: string,
  accessToken: string
): Promise<UploadDocumentResponse> {
  const formData = new FormData();
  formData.append('file', pdfFile);
  formData.append('cpf', cpf);

  try {
    const response = await axios.post<UploadDocumentResponse>(
      'http://localhost:8081/api/v1/documents/upload-income-tax',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${accessToken}`
          // Axios define Content-Type automaticamente para FormData
        }
      }
    );

    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      // Tentar extrair mensagem de erro do JSON
      const errorData = error.response.data;
      if (typeof errorData === 'object' && errorData.error) {
        throw new Error(errorData.error);
      }
      throw new Error(`Erro ${error.response.status}: ${error.response.statusText}`);
    }
    throw error;
  }
}

// Componente React
function IncomeTaxUploadForm() {
  const [file, setFile] = useState<File | null>(null);
  const [cpf, setCpf] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false); // Flag para evitar dupla submissão
  const { accessToken } = useAuth(); // Hook de autenticação

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // ⚠️ IMPORTANTE: Evitar múltiplas submissões
    if (isSubmitting) return;
    
    if (!file || !cpf) {
      alert('Por favor, selecione um arquivo e informe o CPF');
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await uploadIncomeTaxDeclaration(file, cpf, accessToken);
      
      alert(`Declaração de IR enviada com sucesso!\nDocumentId: ${result.documentId}\nStatus: ${result.status}`);
    } catch (error) {
      alert(`Erro: ${error.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        type="file"
        accept=".pdf"
        onChange={(e) => setFile(e.target.files?.[0] || null)}
        disabled={isSubmitting}
      />
      <input
        type="text"
        placeholder="CPF"
        value={cpf}
        onChange={(e) => setCpf(e.target.value)}
        disabled={isSubmitting}
      />
      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Enviando...' : 'Enviar Declaração'}
      </button>
    </form>
  );
}
```

### Vue.js com Axios

```vue
<template>
  <form @submit.prevent="handleSubmit">
    <input
      type="file"
      accept=".pdf"
      @change="handleFileChange"
    />
    <input
      type="text"
      v-model="cpf"
      placeholder="CPF"
    />
    <button type="submit" :disabled="loading">
      {{ loading ? 'Enviando...' : 'Enviar Declaração' }}
    </button>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import axios from 'axios';

interface UploadDocumentResponse {
  documentId: string;
  status: string;
  tipoDetectado: string;
}

const file = ref<File | null>(null);
const cpf = ref('');
const loading = ref(false);
const accessToken = ref(''); // Obter do store/auth

const handleFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement;
  file.value = target.files?.[0] || null;
};

const handleSubmit = async () => {
  if (!file.value || !cpf.value) {
    alert('Por favor, selecione um arquivo e informe o CPF');
    return;
  }

  loading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file.value);
    formData.append('cpf', cpf.value);

    const response = await axios.post<UploadDocumentResponse>(
      'http://localhost:8081/api/v1/documents/upload-income-tax',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${accessToken.value}`
        }
      }
    );

    alert(`Declaração de IR enviada com sucesso!\nDocumentId: ${response.data.documentId}`);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response) {
      const errorData = error.response.data;
      if (typeof errorData === 'object' && errorData.error) {
        alert(`Erro: ${errorData.error}`);
      } else {
        alert(`Erro ${error.response.status}`);
      }
    } else {
      alert('Erro ao processar declaração');
    }
  } finally {
    loading.value = false;
  }
};
</script>
```

---

## ⚠️ Tratamento de Erros

### Erros Comuns e Como Tratá-los

#### 1. CPF Inválido (422)

```typescript
if (response.status === 422) {
  const error = await response.json();
  // error.error = "CPF inválido: 12345678900"
  alert('CPF inválido. Por favor, verifique o CPF informado.');
}
```

#### 2. Pessoa Não Encontrada (404)

```typescript
if (response.status === 404) {
  const error = await response.json();
  // error.error = "Pessoa não encontrada para CPF: 12449709568"
  alert('Pessoa não encontrada. Certifique-se de que já existe pelo menos um contracheque cadastrado para este CPF.');
}
```

#### 3. PDF Inválido (400)

```typescript
if (response.status === 400) {
  const error = await response.json();
  // error.error = "Arquivo inválido. Deve ser um PDF válido."
  alert('O arquivo enviado não é um PDF válido.');
}
```

#### 4. Arquivo Duplicado (409)

```typescript
if (response.status === 409) {
  const error = await response.json();
  // error.error = "Este arquivo já foi enviado anteriormente. DocumentId: 65f123abc"
  alert('Este arquivo já foi enviado anteriormente.');
}
```

#### 5. Erro Interno (500)

```typescript
if (response.status === 500) {
  const error = await response.json();
  // error.error = "Erro ao processar declaração de IR: {detalhes}"
  alert('Erro interno do servidor. Tente novamente mais tarde.');
  console.error('Erro detalhado:', error);
}
```

### Função Auxiliar para Tratamento de Erros

```typescript
async function handleIncomeTaxUploadError(error: unknown): Promise<string> {
  if (axios.isAxiosError(error) && error.response) {
    const status = error.response.status;
    let errorMessage = '';

    try {
      const errorData = await error.response.data.text();
      const errorJson = JSON.parse(errorData);
      errorMessage = errorJson.error || `Erro ${status}`;
    } catch {
      errorMessage = `Erro ${status}: ${error.response.statusText}`;
    }

    // Mapear códigos de erro para mensagens amigáveis
    switch (status) {
      case 400:
        return 'Não foi possível extrair informações da declaração. Verifique se o PDF contém a página RESUMO.';
      case 404:
        return 'Pessoa não encontrada. Certifique-se de que já existe pelo menos um contracheque cadastrado para este CPF.';
      case 422:
        return 'CPF inválido. Por favor, verifique o CPF informado.';
      case 500:
        return 'Erro interno do servidor. Tente novamente mais tarde.';
      default:
        return errorMessage;
    }
  }

  return 'Erro desconhecido ao processar declaração de IR';
}
```

---

## 📝 Casos de Uso

### Caso 1: Upload Bem-Sucedido

**Cenário**: Pessoa existe, PDF válido, documento processado com sucesso

**Resultado**: 
- Documento criado com ID único
- Status: `PROCESSING` (processamento automático iniciado)
- Tipo: `INCOME_TAX`
- Documento associado ao CPF da pessoa
- Extração de valores iniciada automaticamente
- Response JSON com `documentId`, `status` e `tipoDetectado`

### Caso 2: Pessoa Não Existe

**Cenário**: CPF válido, mas pessoa não existe no sistema

**Resultado**:
- Erro 404
- Mensagem: "Pessoa não encontrada para CPF: {cpf}"
- Frontend deve informar que é necessário cadastrar contracheques primeiro

### Caso 3: Arquivo Duplicado

**Cenário**: Arquivo com o mesmo hash já foi enviado anteriormente

**Resultado**:
- Erro 409
- Mensagem: "Este arquivo já foi enviado anteriormente. DocumentId: {id}"
- Frontend deve informar que o arquivo já foi enviado

### Caso 4: PDF Inválido

**Cenário**: Arquivo enviado não é um PDF válido ou está corrompido

**Resultado**:
- Erro 400
- Mensagem: "Arquivo inválido. Deve ser um PDF válido."
- Frontend deve solicitar um PDF válido

**Nota**: A extração de metadata (Ano-Calendário) é opcional. Se não conseguir extrair, o documento ainda é salvo normalmente.

---

## 🔍 Validações no Frontend (Recomendadas)

### Validação de CPF

```typescript
function validateCPF(cpf: string): boolean {
  // Remove formatação
  const cleanCPF = cpf.replace(/[^\d]/g, '');
  
  // Verifica se tem 11 dígitos
  if (cleanCPF.length !== 11) return false;
  
  // Verifica se não são todos iguais
  if (/^(\d)\1{10}$/.test(cleanCPF)) return false;
  
  // Validação básica (validação completa é feita no backend)
  return true;
}
```

### Validação de Arquivo

```typescript
function validatePDFFile(file: File): { valid: boolean; error?: string } {
  // Verificar extensão
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    return { valid: false, error: 'Arquivo deve ser um PDF' };
  }
  
  // Verificar tamanho (ex: máximo 20MB)
  const maxSize = 20 * 1024 * 1024; // 20MB
  if (file.size > maxSize) {
    return { valid: false, error: 'Arquivo muito grande. Máximo: 20MB' };
  }
  
  // Verificar tipo MIME
  if (file.type !== 'application/pdf') {
    return { valid: false, error: 'Tipo de arquivo inválido' };
  }
  
  return { valid: true };
}
```

---

## 📌 Notas Importantes

1. **Formato do CPF**: O backend aceita CPF com ou sem formatação. Recomenda-se enviar sem formatação para evitar problemas.

2. **Tamanho do Arquivo**: Limite de **20MB** por arquivo no upload.

3. **Formato do PDF**: Deve ser um PDF válido com texto extraível (não apenas imagens escaneadas, a menos que o sistema tenha OCR configurado).

4. **Pessoa Deve Existir**: A pessoa (CPF) deve existir no sistema antes de fazer upload da declaração de IR. Isso significa que pelo menos um contracheque deve ter sido enviado anteriormente.

5. **Processamento Automático**: Após o upload, o documento é **processado automaticamente**. O status retornado será `PROCESSING`. **Não é necessário chamar o endpoint de processamento manualmente** (`POST /api/v1/documents/{id}/process`).

6. **Deduplicação**: O sistema verifica duplicidade usando hash SHA-256. Se o mesmo arquivo for enviado novamente, retornará erro 409.

7. **Extração de Valores**: O backend extrai automaticamente da página RESUMO: Base de cálculo, Imposto devido, Dedução de incentivo, Imposto devido I/II/RRA, Contribuição, e Total do imposto devido.

8. **Geração de Excel**: Esta API **NÃO gera Excel**. Para gerar Excel com os dados consolidados (incluindo informações de imposto de renda), use o endpoint de exportação de Excel (`GET /api/v1/persons/{cpf}/excel`).

9. **Evitar Dupla Submissão**: O frontend deve desabilitar o botão de submit durante o upload para evitar chamadas duplicadas. Use uma flag `isSubmitting` como mostrado nos exemplos.

---

## 🔗 Endpoints Relacionados

- **Upload de Contracheques**: `POST /api/v1/documents/upload` (ver `API_2_UPLOAD.md`)
- **Processamento de Documentos**: `POST /api/v1/documents/{id}/process` (ver `API_3_PROCESS_DOCUMENT.md`)
- **Geração de Excel**: `GET /api/v1/persons/{cpf}/excel` (ver `API_6_EXCEL_EXPORT.md`)
- **Consulta de Documentos**: `GET /api/v1/documents` (ver `API_DOCUMENTS_FRONTEND.md`)
- **Consulta de Pessoas**: `GET /api/v1/persons` (ver `API_PERSONS_FRONTEND.md`)

---

## 📞 Suporte

Em caso de dúvidas ou problemas, consulte:
- Documentação geral: `API_COMPLETA_E_ARQUITETURA.md`
- Documentação de documentos: `API_DOCUMENTS_FRONTEND.md`
- Documentação de Excel: `API_6_EXCEL_EXPORT.md`

---

**Última atualização**: Dezembro 2024

