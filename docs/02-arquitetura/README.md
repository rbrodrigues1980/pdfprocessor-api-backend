# 02 — Arquitetura

> Visão geral do sistema, decisões arquiteturais, diagramas e documentação do extrator de PDF.

---

## Documentos nesta seção

| Documento | Descrição |
|-----------|-----------|
| [001 - MASTER_DOCUMENTATION.md](./001%20-%20MASTER_DOCUMENTATION.md) | **Visão geral do sistema.** Documento master cobrindo arquitetura, modelos de dados, regras de processamento, APIs e backlog. Ideal para onboarding e entendimento inicial do projeto. |
| [002 - ARCHITECTURE.md](./002%20-%20ARCHITECTURE.md) | Decisões arquiteturais: por que MongoDB, WebFlux, PDFBox+Tika e Clean Architecture. Cobre camadas do sistema, fluxo de processamento e estrutura de pastas. |
| [003 - API_COMPLETA_E_ARQUITETURA.md](./003%20-%20API_COMPLETA_E_ARQUITETURA.md) | **Documento mais completo (~1.400 linhas).** Parte 1: APIs detalhadas com exemplos cURL e status codes. Parte 2: Clean Architecture — camadas (Domain, Application, Infrastructure, Interfaces), princípios DIP/SRP/OCP, e comparação com MVC. |
| [004 - DIAGRAMS.md](./004%20-%20DIAGRAMS.md) | Coleção de diagramas Mermaid: fluxo geral, sequência de upload/processamento, máquina de estados de documentos, ERD MongoDB, pipeline de extração e fluxo de consolidação Excel. |
| [005 - FLOWCHARTS.md](./005%20-%20FLOWCHARTS.md) | Fluxogramas focados no processamento: upload e identificação de documentos, processamento PDF e consolidação por CPF. |
| [006 - ERD.md](./006%20-%20ERD.md) | Diagrama de Entidade-Relacionamento (Mermaid) mostrando Person, PayrollDocument, PayrollEntry e Rubrica com seus campos. |
| [007 - EXTRATOR.md](./007%20-%20EXTRATOR.md) | **Manual técnico do extrator de PDF.** Padrões regex para CAIXA e FUNCEF, heurísticas de detecção, normalização (datas, valores, descrições), pipeline completo (página → linhas → entries), regras especiais e tratamento de edge cases. Referência essencial para manutenção do extrator. |

---

## Ordem de leitura sugerida

1. `001 - MASTER_DOCUMENTATION.md` — Entenda o contexto e escopo do sistema
2. `002 - ARCHITECTURE.md` — Compreenda as decisões técnicas e o porquê de cada escolha
3. `003 - API_COMPLETA_E_ARQUITETURA.md` — Mergulhe nos detalhes da Clean Architecture e APIs
4. `004 - DIAGRAMS.md` / `005 - FLOWCHARTS.md` / `006 - ERD.md` — Visualize os fluxos e modelos
5. `007 - EXTRATOR.md` — Se precisar entender ou manter a lógica de extração de PDF

---

[← Voltar ao índice](../README.md)
