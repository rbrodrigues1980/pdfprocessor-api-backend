# SPRINT_PLAN.md — Plano de Sprints do Projeto (CAIXA + FUNCEF Extractor)

Este documento define o plano inicial de sprints para o desenvolvimento completo do sistema, incluindo backend, processamento de PDF, consolidação, exportação e frontend admin.

Total inicial: **4 sprints (2 semanas cada)**  
Metodologia: **Scrum**  
Foco: **Entrega incremental e funcional**

---

# 🟧 Sprint 1 — Infraestrutura & Upload de PDF
Duração: 2 semanas  
Objetivo: Criar a base do projeto, endpoints de upload e armazenamento dos PDFs.

## **Histórias**
### ✔ 1.1 Criar projeto Spring Boot (Java + Kotlin)
- Configuração inicial (Gradle + Kotlin DSL)
- WebFlux configurado
- MongoDB reativo configurado
- Actuator habilitado
- OpenAPI configurado

### ✔ 1.2 Criar collections iniciais no MongoDB
- persons  
- payroll_documents  
- rubricas  
- payroll_entries  

### ✔ 1.3 Endpoint: Upload de PDF
`POST /api/v1/documents/upload`
- Receber multipart
- Validar extensão/tamanho
- Associar CPF
- Persistir documento no Mongo
- Status: PENDING

### ✔ 1.4 Logging estruturado (JSON)
- Interceptadores
- Logs de auditoria

### ✔ 1.5 Popular rubricas iniciais
- 24 códigos configurados
- `GET /rubricas` implementado

---

# 🟦 Sprint 2 — Processamento do PDF (Core do Sistema)
Duração: 2 semanas  
Objetivo: Conseguir ler e entender PDFs da Caixa e da Funcef.

## **Histórias**
### ✔ 2.1 Identificar tipo de documento
- CAIXA  
- FUNCEF  
- MISTO  
- Heurísticas por palavras-chave

### ✔ 2.2 Extrair metadados
- Nome, CPF
- Datas (Mês/Ano de Pagamento)
- Número de páginas

### ✔ 2.3 Extrair linhas de rubricas
- Regex CAIXA
- Regex FUNCEF
- Normalização dos valores
- Conversão decimal BR → EN

### ✔ 2.4 Processamento multipáginas
- Detectar origem por página
- Associar página no PayrollEntry

### ✔ 2.5 Atualização de status
- PENDING → PROCESSING → PROCESSED → ERROR

---

# 🟩 Sprint 3 — Consolidação & APIs de Consultas
Duração: 2 semanas  
Objetivo: Criar matriz consolidada por CPF e disponibilizar as APIs necessárias ao frontend.

## **Histórias**
### ✔ 3.1 Endpoint: Listar documentos da pessoa
`GET /api/v1/persons/{cpf}/documents`

### ✔ 3.2 Endpoint: Listar entries de um documento
`GET /api/v1/documents/{id}/entries`

### ✔ 3.3 Consolidação mensal/anual
- Construção da matriz:
  ```
  Código | Rubrica | 2017/01 | 2017/02 | ... | 2017/12
  ```
- Suporte a múltiplos anos

### ✔ 3.4 Endpoint: Consolidado completo por CPF
`GET /api/v1/persons/{cpf}/consolidated`

---

# 🟧 Sprint 4 — Exportação Excel e Painel Admin (React)
Duração: 2 semanas  
Objetivo: Entregar a exportação funcional do Excel e iniciar painel web.

## **Histórias**
### ✔ 4.1 Exportar matriz para Excel
`GET /api/v1/persons/{cpf}/excel`
- Apache POI
- Layout idêntico ao modelo enviado
- Totais e subtotais
- Cálculos de IRPF (se aplicável)

### ✔ 4.2 Criar projeto frontend admin (React)
- Vite + React + TS
- Tailwind + shadcn/ui
- React Query + Axios
- Zustand

### ✔ 4.3 Tela: Upload de PDF
- Upload com CPF
- Lista de status

### ✔ 4.4 Tela: Documentos processados
- Listagem do ano
- Acesso às rubricas extraídas

### ✔ 4.5 Tela: Consolidação
- Visualização da matriz
- Botão de exportar Excel

---

# 🧠 Sprint Futura — Autenticação & Permissões
Duração: 1 semana  
Objetivo: Acesso restrito para administradores.

## **Histórias**
### 🔒 5.1 Autenticação JWT ou Keycloak
### 🔒 5.2 Perfis: Administrador / Operador
### 🔒 5.3 Auditoria detalhada por usuário

---

# 🧩 Sprint Futura — Melhorias de Extração de Texto (Opcional)
Duração: 1 semana  
Objetivo: Melhorias no suporte a PDFs escaneados (imagem).

## **Histórias**
### 🔍 6.1 Otimizar extração de texto via Tesseract
### 🔍 6.2 Reconstrução das linhas
### 🔍 6.3 Correção de ruído

---

# 📌 Conclusão
Este plano de sprints entrega **todo o sistema**, desde upload, processamento, extração, consolidação, exportação e painel administrativo, em **4 sprints principais** com **dois opcionais** para funcionalidades avançadas.

