# BACKLOG.md — Sistema de Extração e Consolidação de Contracheques (CAIXA + FUNCEF)

## 📌 Visão Geral do Sistema
Este sistema processa PDFs de contracheques da CAIXA e FUNCEF, extrai rubricas, metadados e valores, armazena tudo em MongoDB e permite gerar planilha consolidada para cada CPF.

---

## 🧱 Arquitetura Geral
- Backend: Spring Boot 3.x (Java 21 + Kotlin)
- Database: MongoDB reativo
- PDF parsing: Apache PDFBox + Apache Tika
- Infra: WebFlux, Clean Architecture, Gradle Kotlin DSL
- Exportação: Apache POI (Excel)

---

# 🟦 ÉPICO 1 — Infraestrutura do Backend
## Feature 1.1 — Configuração do Projeto
- Configurar Spring Boot
- Configurar Gradle Kotlin DSL
- Configurar WebFlux
- Configurar MongoDB
- Criar collections:
  - persons
  - payroll_documents
  - payroll_entries
  - rubricas

## Feature 1.2 — Upload de PDF
- Criar endpoint multipart
- Validar extensão e tamanho
- Salvar PDF
- Criar PayrollDocument com status PENDING

## Feature 1.3 — Logging
- Log JSON estruturado
- Logs de auditoria
- Actuator e healthcheck

---

# 🟦 ÉPICO 2 — Modelagem dos Dados
## Person
- cpf, nome, documentos

## PayrollDocument
- id, pessoaId, tipo, ano, status, páginas, resumo

## Rubrica
- codigo, descricao, categoria, ativo

## PayrollEntry
- documentoId, rubricaCodigo, descricao, mes, ano, valor, pagina, origem

---

# 🟦 ÉPICO 3 — Processamento de PDFs
## Identificação do Tipo
- Detectar CAIXA / FUNCEF / MISTO

## Extração de Metadados
- Nome, CPF, datas, agência/conta, valor líquido

## Extração de Rubricas
- Regex CAIXA
- Regex FUNCEF
- Parser genérico
- Normalização de valores

## Multipáginas
- Splits
- Origem por página
- Iterador de páginas

## Processamento Assíncrono
- Mudar status
- Retries
- Logging detalhado

---

# 🟦 ÉPICO 4 — APIs REST
## Upload
POST /api/v1/documents/upload

## Processar
POST /api/v1/documents/{id}/process

## Buscar documentos da pessoa
GET /api/v1/persons/{cpf}/documents

## Buscar rubricas extraídas
GET /api/v1/documents/{id}/entries

## Consolidado
GET /api/v1/persons/{cpf}/consolidated

## Exportar Excel
GET /api/v1/persons/{cpf}/excel

---

# 🟦 ÉPICO 5 — Regras CAIXA / FUNCEF
## Parametrização das rubricas
- Inserir lista inicial
- Ativar/desativar rubricas
- Validar rubricas desconhecidas

## Mesclagem
- Identificar documento misto
- Normalizar datas

---

# 🟦 ÉPICO 6 — Frontend Admin (Futuro)
- Tela de upload
- Lista de documentos
- Visualização de rubricas
- Tela de consolidação

---

# 🟦 ÉPICO 7 — Excel
- Montar matriz
- Totais
- Estilos iguais ao modelo enviado

---

# 🟦 ÉPICO 8 — Testes
- Unitários
- Testcontainers
- Swagger

---

# 🟩 Lista de Rubricas Parametrizadas

3362 — REP. TAXA ADMINISTRATIVA - SALDADO  
3394 — REP TAXA ADMINISTRATIVA BUA  
3396 — REP TAXA ADMINISTRATIVA BUA NOVO PLANO  
3430 — REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2014  
3477 — REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2015  
3513 — REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2016  
3961 — REP. TAXA ADMINISTRATIVA - NP  
4236 — FUNCEF NOVO PLANO  
4362 — TAXA ADMINISTRATIVA SALDADO  
4364 — TAXA ADMINISTRATIVA SALDADO 13º SAL  
4369 — FUNCEF NOVO PLANO GRAT NATAL  
4412 — FUNCEF CONTRIB EQU SALDADO 01  
4416 — FUNCEF CONTRIB EQU SALDADO 01 GRT NATAL  
4430 — CONTRIBUIÇÃO EXTRAORDINÁRIA 2014  
4432 — FUNCEF CONTRIB EQU SALDADO 02  
4436 — FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL  
4443 — FUNCEF CONTRIB EQU SALDADO 03  
4444 — FUNCEF CONTRIB EQU SALDADO 03 GRT NATAL  
4459 — CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2014  
4477 — CONTRIBUIÇÃO EXTRAORDINÁRIA 2015  
4482 — CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2015  
4513 — CONTRIBUIÇÃO EXTRAORDINÁRIA 2016  
4514 — CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2016  
4961 — TAXA ADMINISTRATIVA NOVO PLANO  
