# API_SPEC.md — Especificação Completa da API (CAIXA + FUNCEF Extractor)

## 📌 Visão Geral
API REST para:
- Upload de PDFs
- Processamento automático de contracheques
- Extração de rubricas
- Consulta de documentos por CPF
- Consolidação anual/mensal
- Exportação Excel

Versão: v1  
Formato: JSON  

---

# 📁 1. Upload de Documentos

## POST /api/v1/documents/upload
Upload de arquivos PDF.

Request (multipart/form-data):
- file: PDF
- cpf: String

Response:
```json
{ "documentId": "65f123abc", "status": "PENDING" }
```

---

# ⚙️ 2. Processamento

## POST /api/v1/documents/{id}/process
Inicia o processamento de um PDF.

Response:
```json
{ "documentId": "65f123abc", "status": "PROCESSING" }
```

---

# 📄 3. Consultas de Documentos

## GET /api/v1/persons/{cpf}/documents
Lista todos os documentos processados do CPF.

---

# 🧾 4. Entradas (Rubricas Extraídas)

## GET /api/v1/documents/{id}/entries
Retorna as linhas extraídas do contracheque.

---

# 📊 5. Consolidação

## GET /api/v1/persons/{cpf}/consolidated
Retorna matriz semelhante ao Excel final.

---

# 📥 6. Exportação Excel

## GET /api/v1/persons/{cpf}/excel
Download do arquivo Excel consolidado.

---

# 🔧 7. Rubricas

## GET /api/v1/rubricas
Lista as 24 rubricas configuradas.

## POST /api/v1/rubricas
Criação de rubrica.

## PUT /api/v1/rubricas/{codigo}
Atualização.

## DELETE /api/v1/rubricas/{codigo}
Soft delete.

---

# 🧪 8. Health Check

## GET /actuator/health
Status da aplicação.
