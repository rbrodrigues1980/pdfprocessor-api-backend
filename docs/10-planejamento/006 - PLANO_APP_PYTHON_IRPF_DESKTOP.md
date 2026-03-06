# Plano: Aplicação Desktop Python — Automação IRPF

## Visão Geral do Projeto

Aplicação **Python desktop** instalada na máquina do usuário que:

1. Permite selecionar o **PDF de uma declaração IRPF já enviada** para a Receita Federal
2. **Extrai automaticamente** todos os dados da declaração usando IA (Google Gemini)
3. Permite ao usuário **revisar e corrigir** os dados extraídos antes de prosseguir
4. **Abre o programa IRPF** da Receita Federal do ano correspondente (2016 a 2025)
5. **Cria uma nova declaração** e preenche automaticamente com os dados extraídos
6. Gera **screenshots** de cada etapa como comprovante

### Caso de uso principal

> A pessoa tem a **declaração de IRPF de 2016 já enviada em PDF**. Quer abrir o programa IRPF 2016 e **criar uma nova declaração (retificação ou cópia)** com base naqueles mesmos dados. Repetir para qualquer ano de 2016 a 2025.

### Fonte dos dados: Declaração Completa em PDF

O PDF fonte **NÃO é um informe de rendimentos**. É o PDF da **declaração completa do IRPF** gerada pelo próprio programa da Receita Federal. Isso significa:

- O formato é **padronizado** (gerado pelo programa da Receita)
- Contém **TODAS as seções** da declaração (rendimentos, bens, dívidas, deduções, etc.)
- O layout é **consistente entre anos** (com variações menores)
- É muito mais **extenso** que um informe de rendimentos

A aplicação roda **100% local** na máquina Windows do usuário. A única chamada externa é para a API do Gemini (extração de dados do PDF).

---

## Índice

- [1. Arquitetura Geral](#1-arquitetura-geral)
- [2. Stack Tecnológica](#2-stack-tecnológica)
- [3. Seções de uma Declaração IRPF Completa](#3-seções-de-uma-declaração-irpf-completa)
- [4. Estrutura do Projeto](#4-estrutura-do-projeto)
- [5. Módulo 1 — Interface Gráfica (UI)](#5-módulo-1--interface-gráfica-ui)
- [6. Módulo 2 — Extração de Dados da Declaração PDF](#6-módulo-2--extração-de-dados-da-declaração-pdf)
- [7. Módulo 3 — Validação de Dados](#7-módulo-3--validação-de-dados)
- [8. Módulo 4 — Localização de Programas IRPF](#8-módulo-4--localização-de-programas-irpf)
- [9. Módulo 5 — Automação RPA (pywinauto)](#9-módulo-5--automação-rpa-pywinauto)
- [10. Módulo 6 — Screenshots e Logs](#10-módulo-6--screenshots-e-logs)
- [11. Fluxo Completo da Aplicação](#11-fluxo-completo-da-aplicação)
- [12. Modelo de Dados](#12-modelo-de-dados)
- [13. Configuração](#13-configuração)
- [14. Empacotamento e Distribuição](#14-empacotamento-e-distribuição)
- [15. Dependências Python](#15-dependências-python)
- [16. Riscos e Pontos de Atenção](#16-riscos-e-pontos-de-atenção)
- [17. Ordem de Implementação (Sprints)](#17-ordem-de-implementação-sprints)
- [Glossário](#glossário)

---

## 1. Arquitetura Geral

Tudo roda na máquina do usuário. A única dependência externa é a API do Gemini para extração inteligente de dados.

```
┌──────────────────────────────────────────────────────────────┐
│                   MÁQUINA DO USUÁRIO (Windows)               │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              APLICAÇÃO PYTHON DESKTOP                  │  │
│  │                                                        │  │
│  │  ┌──────────┐  ┌───────────┐  ┌─────────────────────┐ │  │
│  │  │    UI    │  │ Extração  │  │    RPA Engine       │ │  │
│  │  │ (PyQt6) │  │ de Dados  │  │   (pywinauto)       │ │  │
│  │  │         │  │           │  │                     │ │  │
│  │  │ • Sel.  │  │ • PDF     │  │ • Abre programa     │ │  │
│  │  │   PDF   │  │   leitura │  │   IRPF do ano       │ │  │
│  │  │ • Rev.  │  │ • Gemini  │  │ • Cria nova decl.   │ │  │
│  │  │   dados │  │   AI API  │  │ • Navega abas       │ │  │
│  │  │ • Prog. │  │ • Validaç.│  │ • Preenche campos   │ │  │
│  │  │   barra │  │           │  │ • Salva declaração   │ │  │
│  │  └──────────┘  └───────────┘  └─────────────────────┘ │  │
│  │         │              │               │               │  │
│  │         ▼              ▼               ▼               │  │
│  │  ┌──────────┐  ┌───────────┐  ┌─────────────────────┐ │  │
│  │  │ Config  │  │ API Gemini│  │ Programa IRPF       │ │  │
│  │  │ Local   │  │ (Google)  │  │ (Receita Federal)   │ │  │
│  │  │ (.env)  │  │ externo   │  │ Java Swing local    │ │  │
│  │  └──────────┘  └───────────┘  └─────────────────────┘ │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  Fonte: PDF da declaração IRPF já enviada                    │
│  Destino: Nova declaração no programa IRPF do mesmo ano      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Stack Tecnológica

| Camada | Tecnologia | Justificativa |
|---|---|---|
| **Linguagem** | Python 3.12+ | Ecossistema maduro para automação desktop e IA |
| **Interface gráfica** | PyQt6 | UI moderna, nativa Windows, rica em componentes |
| **Leitura de PDF** | pdfplumber | Excelente para extração de texto e tabelas de PDFs |
| **Extração IA** | Google Gemini API (`google-generativeai`) | Interpreta a declaração completa e retorna JSON estruturado |
| **Automação RPA** | pywinauto | Suporte nativo a Java Access Bridge + fallback keyboard |
| **Screenshots** | Pillow (PIL) | Captura e redimensiona screenshots |
| **Configuração** | python-dotenv + YAML | Configuração simples e segura |
| **Empacotamento** | PyInstaller | Gera .exe standalone para Windows |
| **Logging** | loguru | Logging simples e poderoso |

### Por que `pywinauto`?

`pywinauto` é a biblioteca Python para automação de desktop no Windows. O diferencial crucial:

- **Suporte nativo a Java Access Bridge** — Acessa componentes Swing por nome, sem depender de coordenadas
- **Fallback para keyboard/mouse** — Se Access Bridge não estiver disponível, usa teclado
- **API Pythônica e simples** — Muito mais fácil que JNA em Kotlin/Java
- **Madura** — Usada em produção há mais de 10 anos

### Vantagem do PDF ser da própria Receita

Diferente de informes de rendimentos (que variam por banco/empresa), o PDF da declaração é **gerado pelo programa IRPF**. Isso significa:

- Layout **padronizado** e **previsível**
- Mesma estrutura de seções todo ano (com variações menores)
- Gemini AI terá alta taxa de acerto na extração
- Possibilidade de **fallback por regex/posição** mais confiável que com informes variados

---

## 3. Seções de uma Declaração IRPF Completa

Uma declaração completa enviada à Receita Federal contém as seguintes seções. **Todas precisam ser extraídas e preenchidas:**

### Seções principais

| # | Seção | Descrição | Complexidade |
|---|---|---|---|
| 1 | **Identificação do Contribuinte** | CPF, nome, data nasc., título eleitor, endereço, ocupação, natureza da ocupação | Média |
| 2 | **Dependentes** | Lista de dependentes (nome, CPF, data nasc., parentesco) | Alta (lista variável) |
| 3 | **Alimentandos** | Beneficiários de pensão alimentícia | Média |
| 4 | **Rendimentos Tributáveis de PJ** | Rendimentos recebidos de pessoa jurídica (pode ter múltiplas fontes) | Alta (lista variável) |
| 5 | **Rendimentos Tributáveis de PF/Exterior** | Rendimentos recebidos de pessoa física ou do exterior, por mês | Alta (12 meses) |
| 6 | **Rendimentos Isentos e Não Tributáveis** | Lucros/dividendos, indenizações, bolsas, etc. (por tipo/linha) | Alta (muitas linhas) |
| 7 | **Rendimentos Tributação Exclusiva/Definitiva** | 13o salário, PLR, aplicações financeiras, etc. | Média |
| 8 | **Imposto Pago/Retido** | IRRF, carnê-leão, complementar, restituição anterior | Baixa |
| 9 | **Pagamentos Efetuados** | Saúde, educação, advogados, aluguel, etc. (cada com CNPJ/CPF e valor) | Alta (lista variável) |
| 10 | **Doações Efetuadas** | Doações a fundos e instituições | Média |
| 11 | **Bens e Direitos** | Imóveis, veículos, contas, investimentos (cada com código, descrição, valores) | Alta (lista variável) |
| 12 | **Dívidas e Ônus Reais** | Empréstimos, financiamentos (cada com código, descrição, valores) | Média (lista variável) |
| 13 | **Informações do Cônjuge** | CPF e rendimentos do cônjuge | Baixa |
| 14 | **Espólio** | Informações de espólio (se aplicável) | Baixa |
| 15 | **Doações a Partidos/Candidatos** | Doações eleitorais | Baixa |
| 16 | **Atividade Rural** | Receitas e despesas rurais (se aplicável) | Média |
| 17 | **Ganhos de Capital** | Alienação de bens (se aplicável) | Média |
| 18 | **Renda Variável** | Operações em bolsa (se aplicável) | Alta |

### Prioridade de implementação

```
🔴 CRÍTICO (Sprint 1-4): Seções 1-12 — Presentes em praticamente toda declaração
🟡 MÉDIO  (Sprint 5-6):  Seções 13-15 — Presentes em muitas declarações
🟢 BAIXO  (Sprint 7+):   Seções 16-18 — Presentes em declarações específicas
```

---

## 4. Estrutura do Projeto

```
irpf-automation/
│
├── main.py                          # Entry point da aplicação
├── requirements.txt                 # Dependências Python
├── setup.py                         # Setup para empacotamento
├── .env.example                     # Exemplo de configuração
├── README.md                        # Documentação do projeto
│
├── config/
│   ├── __init__.py
│   ├── settings.py                  # Carrega configurações (.env + YAML)
│   └── field_mappings/              # Mapeamento de campos por ano
│       ├── irpf_2016.yaml
│       ├── irpf_2017.yaml
│       ├── irpf_2018.yaml
│       ├── irpf_2019.yaml
│       ├── irpf_2020.yaml
│       ├── irpf_2021.yaml
│       ├── irpf_2022.yaml
│       ├── irpf_2023.yaml
│       ├── irpf_2024.yaml
│       └── irpf_2025.yaml
│
├── core/
│   ├── __init__.py
│   ├── models.py                    # Modelos de dados (dataclasses)
│   └── exceptions.py                # Exceções customizadas
│
├── extraction/
│   ├── __init__.py
│   ├── pdf_reader.py                # Leitura de texto do PDF (pdfplumber)
│   ├── gemini_extractor.py          # Extração de dados via Gemini AI
│   ├── section_parser.py            # Parser que divide o PDF em seções da declaração
│   ├── fallback_extractor.py        # Extração por regex (fallback offline)
│   └── validator.py                 # Validação dos dados extraídos
│
├── programs/
│   ├── __init__.py
│   ├── locator.py                   # Localiza programas IRPF instalados
│   └── launcher.py                  # Abre programa via subprocess
│
├── rpa/
│   ├── __init__.py
│   ├── engine.py                    # Interface/base para automação
│   ├── access_bridge_engine.py      # Implementação via pywinauto + Access Bridge
│   ├── keyboard_engine.py           # Implementação via teclado (fallback)
│   ├── navigator.py                 # Navegação entre telas/abas do IRPF
│   ├── form_filler.py               # Preenchimento automático dos campos
│   ├── list_filler.py               # Preenchimento de listas (bens, pagamentos, etc.)
│   ├── field_mapper.py              # Carrega mapeamento de campos do YAML
│   └── screenshot.py                # Captura de tela
│
├── ui/
│   ├── __init__.py
│   ├── main_window.py               # Janela principal
│   ├── extraction_panel.py          # Painel de upload e extração
│   ├── review_panel.py              # Painel de revisão de dados extraídos
│   ├── sections_review.py           # Revisão detalhada por seção (abas)
│   ├── automation_panel.py          # Painel de progresso da automação
│   ├── settings_dialog.py           # Diálogo de configurações
│   ├── resources/                   # Ícones, imagens
│   │   ├── icon.ico
│   │   └── logo.png
│   └── styles/
│       └── theme.qss                # Stylesheet Qt (tema visual)
│
├── utils/
│   ├── __init__.py
│   ├── cpf_cnpj.py                  # Validação e formatação de CPF/CNPJ
│   ├── currency.py                  # Formatação de valores monetários
│   └── logger.py                    # Configuração de logging
│
├── tests/
│   ├── __init__.py
│   ├── test_extraction.py           # Testes de extração
│   ├── test_section_parser.py       # Testes do parser de seções
│   ├── test_validation.py           # Testes de validação
│   ├── test_locator.py              # Testes do localizador
│   └── fixtures/
│       └── sample_declarations/     # PDFs de declarações para testes
│           ├── declaracao_2016.pdf
│           ├── declaracao_2020.pdf
│           └── declaracao_2024.pdf
│
└── dist/                            # (gerado pelo PyInstaller)
    └── irpf-automation.exe
```

---

## 5. Módulo 1 — Interface Gráfica (UI)

### Tecnologia: PyQt6

Interface moderna, nativa Windows, com 4 telas principais em um fluxo wizard (passo a passo).

### Tela 1 — Upload da Declaração PDF

```
┌─────────────────────────────────────────────────────────────┐
│  IRPF Automation                                     _ □ X  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ── Passo 1 de 4: Selecionar Declaração IRPF ────────────  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                                                       │  │
│  │      Arraste o PDF da declaração já enviada           │  │
│  │           ou clique para selecionar                    │  │
│  │                                                       │  │
│  │      📄 PDF da declaração completa do IRPF            │  │
│  │         (gerado pelo programa da Receita)             │  │
│  │                                                       │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  Arquivo: C:\Users\joao\IRPF\declaracao_2016.pdf            │
│                                                             │
│  ⓘ  O sistema vai ler esta declaração e recriar uma nova   │
│     declaração no programa IRPF do mesmo ano.               │
│                                                             │
│                              [ Extrair Dados → ]            │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  Status: Pronto                                             │
└─────────────────────────────────────────────────────────────┘
```

### Tela 2 — Revisão de Dados (com abas por seção)

```
┌─────────────────────────────────────────────────────────────┐
│  IRPF Automation                                     _ □ X  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ── Passo 2 de 4: Revisar Dados Extraídos ────────────────  │
│                                                             │
│  Declaração: IRPF 2016 | CPF: 123.456.789-00               │
│  Seções encontradas: 12 | Campos extraídos: 147             │
│                                                             │
│  ┌────────┬──────────┬────────┬──────────┬──────────┬─────┐ │
│  │ Ident. │ Rendim.  │ Bens e │ Pagam.  │ Dívidas  │ ... │ │
│  │        │ Trib. PJ │ Dir.   │ Efet.   │          │     │ │
│  ├────────┴──────────┴────────┴──────────┴──────────┴─────┤ │
│  │                                                        │ │
│  │  ── Rendimentos Tributáveis Recebidos de PJ ──         │ │
│  │                                                        │ │
│  │  Fonte 1 de 2:                              [+ Novo]   │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │ CNPJ:         [12.345.678/0001-90    ]           │  │ │
│  │  │ Nome:         [EMPRESA EXEMPLO LTDA  ]           │  │ │
│  │  │ Rendimentos:  [R$ 85.000,00          ]           │  │ │
│  │  │ Contrib.Prev: [R$  9.350,00          ]           │  │ │
│  │  │ IR Retido:    [R$ 12.750,00          ]           │  │ │
│  │  │ 13º Salário:  [R$  7.083,33          ]           │  │ │
│  │  │ IRRF 13º:     [R$    945,00          ]           │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │                                                        │ │
│  │  Fonte 2 de 2:                              [Remover]  │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │ CNPJ:         [98.765.432/0001-10    ]           │  │ │
│  │  │ ...                                              │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │                                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                             │
│  ⚠️  2 warnings encontrados (ver aba "Identificação")       │
│                                                             │
│  [ ← Voltar ]                   [ Confirmar e Avançar → ]   │
└─────────────────────────────────────────────────────────────┘
```

### Tela 3 — Seleção de Programa

```
┌─────────────────────────────────────────────────────────────┐
│  IRPF Automation                                     _ □ X  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ── Passo 3 de 4: Selecionar Programa IRPF ──────────────  │
│                                                             │
│  Ano da declaração detectado: 2016                          │
│                                                             │
│  Programas IRPF encontrados:                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ✅ IRPF 2016  C:\Prog. RFB\IRPF2016\irpf.jar  ◄── │   │
│  │  ✅ IRPF 2020  C:\Prog. RFB\IRPF2020\irpf.jar      │   │
│  │  ✅ IRPF 2024  C:\Prog. RFB\IRPF2024\irpf.jar      │   │
│  │  ...                                                  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  Programa selecionado: ✅ IRPF 2016 (corresponde ao ano)    │
│                                                             │
│  [ Localizar manualmente... ]                               │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  ⚠️  ATENÇÃO                                         │   │
│  │  • A automação vai controlar teclado e mouse          │   │
│  │  • Não mexa no computador durante o processo          │   │
│  │  • Uma NOVA declaração será criada no programa        │   │
│  │  • Você poderá revisar antes de transmitir            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  [ ← Voltar ]                    [ Iniciar Automação → ]    │
└─────────────────────────────────────────────────────────────┘
```

### Tela 4 — Progresso da Automação

```
┌─────────────────────────────────────────────────────────────┐
│  IRPF Automation                                     _ □ X  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ── Passo 4 de 4: Automação em Andamento ────────────────  │
│                                                             │
│  ████████████████░░░░░░░░░░  60%                            │
│  Seção 7 de 12: "Bens e Direitos"  |  Item 3 de 8          │
│                                                             │
│  Log:                                                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 14:32:01  ✅ Programa IRPF 2016 aberto               │   │
│  │ 14:32:05  ✅ Nova declaração criada                   │   │
│  │ 14:32:08  ✅ [Identificação] CPF: 123.456.789-00     │   │
│  │ 14:32:10  ✅ [Identificação] Nome, endereço, etc.     │   │
│  │ 14:32:15  ✅ [Rend.Trib.PJ] Fonte 1: preenchida      │   │
│  │ 14:32:20  ✅ [Rend.Trib.PJ] Fonte 2: preenchida      │   │
│  │ 14:32:25  ✅ [Rend.Isentos] 5 linhas preenchidas     │   │
│  │ 14:32:30  ✅ [Trib.Exclusiva] 3 itens preenchidos    │   │
│  │ 14:32:35  ✅ [Pagamentos] 12 pagamentos preenchidos  │   │
│  │ 14:32:40  ✅ [Dependentes] 2 dependentes cadastrados  │   │
│  │ 14:32:45  🔄 [Bens e Direitos] Item 3/8: Veículo...  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  Último screenshot:                                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ [miniatura do screenshot mais recente]                │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  [ Cancelar ]                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Módulo 2 — Extração de Dados da Declaração PDF

### Estratégia de extração

Como o PDF é gerado pelo próprio programa da Receita, a estratégia é:

1. **Extrair texto completo** do PDF com `pdfplumber`
2. **Dividir em seções** usando marcadores de seção (títulos padronizados)
3. **Enviar cada seção** separadamente ao Gemini AI (evita limite de tokens e melhora precisão)
4. **Montar o modelo completo** combinando as respostas de cada seção

```
PDF Declaração → pdfplumber → Texto Bruto → Section Parser → Seções Individuais
                                                                    │
                                      ┌─────────────────────────────┤
                                      ▼                             ▼
                               Seção "Identificação"    Seção "Bens e Direitos"
                                      │                             │
                                      ▼                             ▼
                               Gemini AI (prompt 1)     Gemini AI (prompt N)
                                      │                             │
                                      ▼                             ▼
                               JSON parcial             JSON parcial
                                      │                             │
                                      └────────────┬────────────────┘
                                                   ▼
                                          Modelo Completo
                                      (DeclaracaoIrpfData)
```

### `extraction/pdf_reader.py`

```python
import pdfplumber

def extract_text_from_pdf(pdf_path: str) -> str:
    """Extrai todo o texto de um PDF de declaração IRPF."""
    full_text = ""
    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            full_text += page.extract_text() or ""
            full_text += "\n--- PAGE BREAK ---\n"
    return full_text
```

### `extraction/section_parser.py`

Divide o texto em seções usando os títulos padronizados da Receita:

```python
import re
from typing import Dict

# Marcadores de seção encontrados nos PDFs de declaração
SECTION_MARKERS = [
    "IDENTIFICAÇÃO DO CONTRIBUINTE",
    "DEPENDENTES",
    "ALIMENTANDOS",
    "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA",
    "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PF/EXTERIOR",
    "RENDIMENTOS ISENTOS E NÃO TRIBUTÁVEIS",
    "RENDIMENTOS SUJEITOS À TRIBUTAÇÃO EXCLUSIVA",
    "IMPOSTO PAGO/RETIDO",
    "PAGAMENTOS EFETUADOS",
    "DOAÇÕES EFETUADAS",
    "BENS E DIREITOS",
    "DÍVIDAS E ÔNUS REAIS",
    "INFORMAÇÕES DO CÔNJUGE",
    "ESPÓLIO",
    "ATIVIDADE RURAL",
    "GANHOS DE CAPITAL",
    "RENDA VARIÁVEL",
]

def parse_sections(full_text: str) -> Dict[str, str]:
    """Divide o texto da declaração em seções."""
    sections = {}
    current_section = "HEADER"
    current_text = ""

    for line in full_text.split("\n"):
        # Verifica se a linha é um marcador de seção
        matched = False
        for marker in SECTION_MARKERS:
            if marker in line.upper():
                if current_text.strip():
                    sections[current_section] = current_text.strip()
                current_section = marker
                current_text = ""
                matched = True
                break
        if not matched:
            current_text += line + "\n"

    # Última seção
    if current_text.strip():
        sections[current_section] = current_text.strip()

    return sections
```

### `extraction/gemini_extractor.py`

Extrai dados de cada seção com prompts especializados:

```python
import google.generativeai as genai
import json
from typing import Dict

class GeminiDeclaracaoExtractor:
    """Extrai dados da declaração IRPF completa via Gemini AI."""

    def __init__(self, api_key: str):
        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel("gemini-2.0-flash")

    def extract_full_declaration(self, sections: Dict[str, str]) -> dict:
        """Extrai dados de todas as seções da declaração."""
        result = {}

        # Extrai cada seção com prompt especializado
        for section_name, section_text in sections.items():
            prompt = self._build_prompt(section_name, section_text)
            if prompt:
                response = self.model.generate_content(prompt)
                section_data = json.loads(response.text)
                result[section_name] = section_data

        return result

    def _build_prompt(self, section_name: str, text: str) -> str:
        """Monta o prompt especializado para cada seção."""
        prompts = {
            "IDENTIFICAÇÃO DO CONTRIBUINTE": PROMPT_IDENTIFICACAO,
            "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA": PROMPT_REND_TRIB_PJ,
            "BENS E DIREITOS": PROMPT_BENS_DIREITOS,
            "PAGAMENTOS EFETUADOS": PROMPT_PAGAMENTOS,
            "DÍVIDAS E ÔNUS REAIS": PROMPT_DIVIDAS,
            "DEPENDENTES": PROMPT_DEPENDENTES,
            # ... prompt para cada seção
        }
        template = prompts.get(section_name)
        if template:
            return template.format(texto=text)
        return None

# === PROMPTS POR SEÇÃO ===

PROMPT_IDENTIFICACAO = """
Extraia os dados de identificação do contribuinte do texto abaixo.
Retorne APENAS JSON, sem markdown.

TEXTO:
{texto}

JSON esperado:
{{
    "cpf": "",
    "nome": "",
    "data_nascimento": "",
    "titulo_eleitor": "",
    "endereco": {{
        "cep": "",
        "logradouro": "",
        "numero": "",
        "complemento": "",
        "bairro": "",
        "cidade": "",
        "uf": ""
    }},
    "natureza_ocupacao": "",
    "ocupacao_principal": "",
    "numero_recibo_anterior": ""
}}
"""

PROMPT_REND_TRIB_PJ = """
Extraia os rendimentos tributáveis recebidos de pessoa jurídica.
Pode haver MÚLTIPLAS fontes pagadoras. Retorne APENAS JSON.

TEXTO:
{texto}

JSON esperado:
{{
    "fontes": [
        {{
            "cnpj": "",
            "nome": "",
            "rendimentos_recebidos": 0.00,
            "contribuicao_previdenciaria": 0.00,
            "imposto_retido": 0.00,
            "decimo_terceiro": 0.00,
            "irrf_decimo_terceiro": 0.00
        }}
    ]
}}
"""

PROMPT_BENS_DIREITOS = """
Extraia todos os bens e direitos declarados.
Cada item tem código, grupo, descrição, situação em 31/12 do ano anterior e do ano atual.
Retorne APENAS JSON.

TEXTO:
{texto}

JSON esperado:
{{
    "itens": [
        {{
            "codigo": "",
            "grupo": "",
            "descricao": "",
            "situacao_anterior": 0.00,
            "situacao_atual": 0.00
        }}
    ]
}}
"""

PROMPT_PAGAMENTOS = """
Extraia todos os pagamentos efetuados (deduções).
Cada item tem código, descrição, CNPJ/CPF do beneficiário e valor pago.
Retorne APENAS JSON.

TEXTO:
{texto}

JSON esperado:
{{
    "pagamentos": [
        {{
            "codigo": "",
            "descricao": "",
            "cpf_cnpj_beneficiario": "",
            "nome_beneficiario": "",
            "valor_pago": 0.00,
            "parcela_nao_dedutivel": 0.00,
            "valor_dedutivel": 0.00
        }}
    ]
}}
"""

PROMPT_DIVIDAS = """
Extraia todas as dívidas e ônus reais.
Cada item tem código, descrição, situação em 31/12 do ano anterior e do ano atual.
Retorne APENAS JSON.

TEXTO:
{texto}

JSON esperado:
{{
    "dividas": [
        {{
            "codigo": "",
            "descricao": "",
            "situacao_anterior": 0.00,
            "situacao_atual": 0.00
        }}
    ]
}}
"""

PROMPT_DEPENDENTES = """
Extraia todos os dependentes declarados.
Retorne APENAS JSON.

TEXTO:
{texto}

JSON esperado:
{{
    "dependentes": [
        {{
            "tipo": "",
            "cpf": "",
            "nome": "",
            "data_nascimento": ""
        }}
    ]
}}
"""
```

---

## 7. Módulo 3 — Validação de Dados

### `extraction/validator.py`

Validação mais abrangente para declaração completa:

```python
@dataclass
class ValidationResult:
    is_valid: bool
    errors: list[str]
    warnings: list[str]
    sections_found: list[str]
    sections_missing: list[str]

def validate_declaration(data: dict) -> ValidationResult:
    errors = []
    warnings = []
    sections_found = list(data.keys())

    # Seções obrigatórias
    required_sections = ["IDENTIFICAÇÃO DO CONTRIBUINTE"]
    sections_missing = [s for s in required_sections if s not in data]
    for s in sections_missing:
        errors.append(f"Seção obrigatória não encontrada: {s}")

    # Validar identificação
    ident = data.get("IDENTIFICAÇÃO DO CONTRIBUINTE", {})
    if not ident.get("cpf"):
        errors.append("CPF do contribuinte não encontrado")
    if not ident.get("nome"):
        errors.append("Nome do contribuinte não encontrado")

    # Validar CPFs/CNPJs em todas as seções
    # ... (percorrer fontes PJ, pagamentos, dependentes, etc.)

    # Validar valores monetários
    # ... (verificar se são números válidos, não negativos)

    # Warnings para seções comuns ausentes
    common = ["BENS E DIREITOS", "PAGAMENTOS EFETUADOS",
              "RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA"]
    for s in common:
        if s not in data:
            warnings.append(f"Seção não encontrada no PDF: {s}")

    return ValidationResult(
        is_valid=len(errors) == 0,
        errors=errors,
        warnings=warnings,
        sections_found=sections_found,
        sections_missing=sections_missing
    )
```

---

## 8. Módulo 4 — Localização de Programas IRPF

### `programs/locator.py`

```python
import os
from pathlib import Path
from dataclasses import dataclass

DEFAULT_SEARCH_PATHS = [
    Path("C:/Arquivos de Programas RFB"),
    Path("C:/Program Files/Programas RFB"),
    Path("C:/Program Files (x86)/Programas RFB"),
    Path(os.path.expanduser("~/Programas RFB")),
]

YEAR_RANGE = range(2016, 2026)

@dataclass
class InstalledProgram:
    year: int
    path: Path
    jar_path: Path
    is_valid: bool

def find_installed_programs() -> list[InstalledProgram]:
    """Localiza todos os programas IRPF instalados."""
    programs = []
    for base_path in DEFAULT_SEARCH_PATHS:
        for year in YEAR_RANGE:
            folder = base_path / f"IRPF{year}"
            jar = folder / "irpf.jar"
            if folder.exists():
                programs.append(InstalledProgram(
                    year=year,
                    path=folder,
                    jar_path=jar,
                    is_valid=jar.exists()
                ))
    return programs

def find_program_for_year(year: int) -> InstalledProgram | None:
    """Encontra o programa IRPF de um ano específico."""
    programs = find_installed_programs()
    for p in programs:
        if p.year == year and p.is_valid:
            return p
    return None
```

### `programs/launcher.py`

```python
import subprocess
from pathlib import Path

def launch_irpf(jar_path: Path, java_path: str = "java") -> subprocess.Popen:
    """Abre o programa IRPF."""
    cmd = [java_path, "-jar", str(jar_path)]
    process = subprocess.Popen(cmd, cwd=str(jar_path.parent))
    return process
```

---

## 9. Módulo 5 — Automação RPA (pywinauto)

### Desafio especial: Listas variáveis

Diferente de um informe de rendimentos (campos fixos), uma declaração completa tem **listas de tamanho variável**:

- Múltiplas fontes pagadoras (PJ)
- Múltiplos bens e direitos (pode ter 20+)
- Múltiplos pagamentos efetuados (pode ter 30+)
- Múltiplas dívidas
- Múltiplos dependentes

Isso requer um componente especializado: `list_filler.py` — que sabe adicionar novos itens a listas no programa IRPF.

### `rpa/engine.py` — Interface base

```python
from abc import ABC, abstractmethod
from core.models import DeclaracaoIrpfData

class AutomationEngine(ABC):
    """Interface base para engines de automação."""

    @abstractmethod
    def open_program(self, year: int, program_path: str) -> bool:
        pass

    @abstractmethod
    def create_new_declaration(self, cpf: str, nome: str) -> bool:
        pass

    @abstractmethod
    def navigate_to_section(self, section: str) -> bool:
        pass

    @abstractmethod
    def fill_simple_fields(self, fields: dict) -> bool:
        """Preenche campos simples (texto, valor, data)."""
        pass

    @abstractmethod
    def fill_list_section(self, section: str, items: list[dict]) -> bool:
        """Preenche seção com lista de itens (bens, pagamentos, etc.)."""
        pass

    @abstractmethod
    def add_list_item(self) -> bool:
        """Clica em 'Novo' para adicionar item a uma lista."""
        pass

    @abstractmethod
    def save_declaration(self) -> bool:
        pass

    @abstractmethod
    def take_screenshot(self, label: str = "") -> str:
        pass
```

### `rpa/list_filler.py` — Preenchimento de listas

```python
class ListFiller:
    """Preenche seções com listas de itens variáveis."""

    def __init__(self, engine: AutomationEngine):
        self.engine = engine

    def fill_list(self, section: str, items: list[dict],
                  on_progress: callable = None) -> int:
        """
        Preenche uma lista de itens em uma seção.
        Exemplo: Bens e Direitos com 8 itens.

        Para cada item:
        1. Clica "Novo" para adicionar
        2. Preenche os campos do item
        3. Confirma/salva o item
        """
        filled = 0
        self.engine.navigate_to_section(section)

        for i, item in enumerate(items):
            self.engine.add_list_item()
            self.engine.fill_simple_fields(item)
            filled += 1
            if on_progress:
                on_progress(section, filled, len(items))

        return filled
```

### `rpa/navigator.py` — Navegação entre seções

O programa IRPF usa uma **árvore lateral** (tree view) para navegação entre seções. O navigator sabe clicar na seção correta:

```python
class IrpfNavigator:
    """Navega entre seções do programa IRPF."""

    # Mapeamento seção do modelo → nome na árvore do programa
    SECTION_TREE_NAMES = {
        "identificacao": "Identificação do Contribuinte",
        "dependentes": "Dependentes",
        "rendimentos_trib_pj": "Rend. Trib. Receb. de Pessoa Jurídica",
        "rendimentos_trib_pf": "Rend. Trib. Receb. de PF/Exterior",
        "rendimentos_isentos": "Rend. Isentos e Não Tributáveis",
        "rendimentos_trib_exclusiva": "Rend. Suj. à Tributação Exclusiva",
        "imposto_pago_retido": "Imposto Pago/Retido",
        "pagamentos_efetuados": "Pagamentos Efetuados",
        "doacoes_efetuadas": "Doações Efetuadas",
        "bens_direitos": "Bens e Direitos",
        "dividas_onus": "Dívidas e Ônus Reais",
    }

    def navigate(self, section_key: str) -> bool:
        tree_name = self.SECTION_TREE_NAMES.get(section_key)
        if not tree_name:
            return False
        # Clicar no item da árvore via pywinauto
        # ...
        return True
```

### Mapeamento de campos — `config/field_mappings/irpf_2024.yaml`

Agora mais abrangente, cobrindo todas as seções:

```yaml
year: 2024
program_title: "IRPF2024"

sections:

  identificacao:
    name: "Identificação do Contribuinte"
    tree_path: "Identificação do Contribuinte"
    type: "simple"        # campos fixos
    fields:
      - model_field: "identificacao.cpf"
        ui_accessible_name: "cpfContribuinte"
        ui_type: "cpf"
        tab_order: 1
      - model_field: "identificacao.nome"
        ui_accessible_name: "nomeContribuinte"
        ui_type: "text"
        tab_order: 2
      - model_field: "identificacao.data_nascimento"
        ui_accessible_name: "dataNascimento"
        ui_type: "date"
        tab_order: 3
      # ... demais campos

  dependentes:
    name: "Dependentes"
    tree_path: "Dependentes"
    type: "list"          # lista de itens
    new_button: "Novo"
    fields:
      - model_field: "tipo"
        ui_accessible_name: "tipoDependente"
        ui_type: "combo"
        tab_order: 1
      - model_field: "cpf"
        ui_accessible_name: "cpfDependente"
        ui_type: "cpf"
        tab_order: 2
      - model_field: "nome"
        ui_accessible_name: "nomeDependente"
        ui_type: "text"
        tab_order: 3
      - model_field: "data_nascimento"
        ui_accessible_name: "dataNascDependente"
        ui_type: "date"
        tab_order: 4

  rendimentos_trib_pj:
    name: "Rendimentos Tributáveis de PJ"
    tree_path: "Rend. Trib. Receb. de Pessoa Jurídica"
    type: "list"
    new_button: "Novo"
    fields:
      - model_field: "cnpj"
        ui_accessible_name: "cnpjFontePagadora"
        ui_type: "cnpj"
        tab_order: 1
      - model_field: "nome"
        ui_accessible_name: "nomeFontePagadora"
        ui_type: "text"
        tab_order: 2
      - model_field: "rendimentos_recebidos"
        ui_accessible_name: "rendimentosRecebidos"
        ui_type: "currency"
        tab_order: 3
      - model_field: "contribuicao_previdenciaria"
        ui_accessible_name: "contribPrevidenciaria"
        ui_type: "currency"
        tab_order: 4
      - model_field: "imposto_retido"
        ui_accessible_name: "impostoRetidoFonte"
        ui_type: "currency"
        tab_order: 5
      - model_field: "decimo_terceiro"
        ui_accessible_name: "decimoTerceiroSalario"
        ui_type: "currency"
        tab_order: 6
      - model_field: "irrf_decimo_terceiro"
        ui_accessible_name: "irrfDecimoTerceiro"
        ui_type: "currency"
        tab_order: 7

  bens_direitos:
    name: "Bens e Direitos"
    tree_path: "Bens e Direitos"
    type: "list"
    new_button: "Novo"
    fields:
      - model_field: "codigo"
        ui_accessible_name: "codigoBem"
        ui_type: "combo"
        tab_order: 1
      - model_field: "descricao"
        ui_accessible_name: "descricaoBem"
        ui_type: "text"
        tab_order: 2
      - model_field: "situacao_anterior"
        ui_accessible_name: "situacaoAnterior"
        ui_type: "currency"
        tab_order: 3
      - model_field: "situacao_atual"
        ui_accessible_name: "situacaoAtual"
        ui_type: "currency"
        tab_order: 4

  pagamentos_efetuados:
    name: "Pagamentos Efetuados"
    tree_path: "Pagamentos Efetuados"
    type: "list"
    new_button: "Novo"
    fields:
      - model_field: "codigo"
        ui_accessible_name: "codigoPagamento"
        ui_type: "combo"
        tab_order: 1
      - model_field: "cpf_cnpj_beneficiario"
        ui_accessible_name: "cpfCnpjBeneficiario"
        ui_type: "text"
        tab_order: 2
      - model_field: "nome_beneficiario"
        ui_accessible_name: "nomeBeneficiario"
        ui_type: "text"
        tab_order: 3
      - model_field: "valor_pago"
        ui_accessible_name: "valorPago"
        ui_type: "currency"
        tab_order: 4
      - model_field: "parcela_nao_dedutivel"
        ui_accessible_name: "parcelaNaoDedutivel"
        ui_type: "currency"
        tab_order: 5

  dividas_onus:
    name: "Dívidas e Ônus Reais"
    tree_path: "Dívidas e Ônus Reais"
    type: "list"
    new_button: "Novo"
    fields:
      - model_field: "codigo"
        ui_accessible_name: "codigoDivida"
        ui_type: "combo"
        tab_order: 1
      - model_field: "descricao"
        ui_accessible_name: "descricaoDivida"
        ui_type: "text"
        tab_order: 2
      - model_field: "situacao_anterior"
        ui_accessible_name: "situacaoAnteriorDivida"
        ui_type: "currency"
        tab_order: 3
      - model_field: "situacao_atual"
        ui_accessible_name: "situacaoAtualDivida"
        ui_type: "currency"
        tab_order: 4
```

---

## 10. Módulo 6 — Screenshots e Logs

### `rpa/screenshot.py`

```python
from PIL import ImageGrab
from datetime import datetime
from pathlib import Path

class ScreenCapture:
    def __init__(self, output_dir: str = "./screenshots"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.captures = []

    def capture(self, label: str = "") -> str:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        safe_label = label.replace(" ", "_").replace("/", "-")
        filename = f"{timestamp}_{safe_label}.png" if label else f"{timestamp}.png"
        filepath = self.output_dir / filename
        screenshot = ImageGrab.grab()
        screenshot.save(filepath)
        self.captures.append(str(filepath))
        return str(filepath)
```

### `utils/logger.py`

```python
from loguru import logger
import sys

def setup_logger(log_file: str = "irpf_automation.log"):
    logger.remove()
    logger.add(sys.stdout, level="INFO",
               format="<green>{time:HH:mm:ss}</green> | <level>{level}</level> | {message}")
    logger.add(log_file, level="DEBUG", rotation="10 MB")
    return logger
```

---

## 11. Fluxo Completo da Aplicação

```
┌────────────────────────────────────────────────────────────────────────┐
│                       FLUXO DA APLICAÇÃO                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  1. INICIAR APLICAÇÃO                                                  │
│     └── Exibir janela principal (Tela 1)                               │
│                                                                        │
│  2. SELECIONAR PDF DA DECLARAÇÃO JÁ ENVIADA                            │
│     ├── Usuário seleciona PDF da declaração completa                   │
│     └── Aplicação detecta o ano da declaração automaticamente          │
│                                                                        │
│  3. EXTRAIR DADOS                                                      │
│     ├── Ler texto do PDF (pdfplumber)                                  │
│     ├── Dividir em seções (section_parser)                             │
│     ├── Enviar cada seção ao Gemini AI (prompts especializados)        │
│     ├── Montar modelo completo (DeclaracaoIrpfData)                    │
│     ├── Validar dados (CPF, CNPJ, valores, seções)                    │
│     └── Navegar para Tela 2 (Revisão)                                  │
│                                                                        │
│  4. REVISAR DADOS POR SEÇÃO                                            │
│     ├── Exibir dados por abas (uma aba por seção)                      │
│     ├── Seções de lista: exibir tabela editável                        │
│     │   (bens, pagamentos, fontes PJ, dependentes, dívidas)            │
│     ├── Destacar campos com warnings/erros                             │
│     ├── Permitir adicionar/remover itens de listas                     │
│     ├── Usuário corrige se necessário                                  │
│     └── Usuário confirma dados                                         │
│                                                                        │
│  5. SELECIONAR PROGRAMA                                                │
│     ├── Auto-selecionar programa do ano detectado                      │
│     ├── Listar todos os programas IRPF encontrados                     │
│     ├── Permitir seleção manual se não encontrado                      │
│     └── Exibir aviso sobre controle de teclado/mouse                   │
│                                                                        │
│  6. EXECUTAR AUTOMAÇÃO                                                 │
│     ├── Abrir programa IRPF do ano                                     │
│     ├── Aguardar programa carregar                                     │
│     ├── Criar nova declaração                                          │
│     │                                                                  │
│     ├── SEÇÕES SIMPLES (campos fixos):                                 │
│     │   ├── Identificação do Contribuinte                              │
│     │   ├── Imposto Pago/Retido                                        │
│     │   └── Informações do Cônjuge                                     │
│     │                                                                  │
│     ├── SEÇÕES DE LISTA (itens variáveis):                             │
│     │   ├── Para cada seção de lista:                                  │
│     │   │   ├── Navegar até a seção na árvore lateral                  │
│     │   │   ├── Para cada item da lista:                               │
│     │   │   │   ├── Clicar "Novo"                                      │
│     │   │   │   ├── Preencher campos do item                           │
│     │   │   │   ├── Confirmar item                                     │
│     │   │   │   └── Screenshot do item                                 │
│     │   │   └── Screenshot da seção completa                           │
│     │   │                                                              │
│     │   ├── Rendimentos Tributáveis PJ (N fontes)                      │
│     │   ├── Dependentes (N dependentes)                                │
│     │   ├── Pagamentos Efetuados (N pagamentos)                        │
│     │   ├── Bens e Direitos (N bens)                                   │
│     │   ├── Dívidas e Ônus Reais (N dívidas)                           │
│     │   └── Rendimentos Isentos (N linhas)                             │
│     │                                                                  │
│     ├── Salvar declaração                                              │
│     ├── Screenshot final                                               │
│     └── Exibir resultado na Tela 4                                     │
│                                                                        │
│  7. RESULTADO                                                          │
│     ├── Exibir resumo (seções preenchidas, itens, tempo, screenshots)  │
│     ├── Botão para abrir pasta de screenshots                          │
│     └── Botão para nova automação                                      │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Modelo de Dados

### `core/models.py`

```python
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Optional
from enum import Enum

class AutomationStatus(Enum):
    IDLE = "idle"
    EXTRACTING = "extracting"
    PARSING_SECTIONS = "parsing_sections"
    VALIDATING = "validating"
    REVIEWING = "reviewing"
    OPENING_PROGRAM = "opening_program"
    CREATING_DECLARATION = "creating_declaration"
    FILLING_SECTION = "filling_section"
    FILLING_LIST_ITEM = "filling_list_item"
    SAVING = "saving"
    COMPLETED = "completed"
    ERROR = "error"
    CANCELLED = "cancelled"

# === IDENTIFICAÇÃO ===

@dataclass
class Endereco:
    cep: str = ""
    logradouro: str = ""
    numero: str = ""
    complemento: str = ""
    bairro: str = ""
    cidade: str = ""
    uf: str = ""

@dataclass
class Identificacao:
    cpf: str = ""
    nome: str = ""
    data_nascimento: str = ""
    titulo_eleitor: str = ""
    endereco: Endereco = field(default_factory=Endereco)
    natureza_ocupacao: str = ""
    ocupacao_principal: str = ""
    numero_recibo_anterior: str = ""

# === DEPENDENTES ===

@dataclass
class Dependente:
    tipo: str = ""                      # código do tipo (11=companheiro, 21=filho, etc.)
    cpf: str = ""
    nome: str = ""
    data_nascimento: str = ""

# === RENDIMENTOS TRIBUTÁVEIS PJ ===

@dataclass
class FontePagadoraPJ:
    cnpj: str = ""
    nome: str = ""
    rendimentos_recebidos: Decimal = Decimal("0")
    contribuicao_previdenciaria: Decimal = Decimal("0")
    imposto_retido: Decimal = Decimal("0")
    decimo_terceiro: Decimal = Decimal("0")
    irrf_decimo_terceiro: Decimal = Decimal("0")

# === RENDIMENTOS TRIBUTÁVEIS PF/EXTERIOR ===

@dataclass
class RendimentosPFExterior:
    mes_valores: dict[str, Decimal] = field(default_factory=dict)  # {"01": 1000.00, ...}
    total: Decimal = Decimal("0")

# === RENDIMENTOS ISENTOS ===

@dataclass
class RendimentoIsento:
    linha: int = 0                      # número da linha no formulário
    tipo: str = ""                      # descrição do tipo
    cnpj_fonte: str = ""
    nome_fonte: str = ""
    valor: Decimal = Decimal("0")

# === RENDIMENTOS TRIBUTAÇÃO EXCLUSIVA ===

@dataclass
class RendimentoTribExclusiva:
    linha: int = 0
    tipo: str = ""
    cnpj_fonte: str = ""
    nome_fonte: str = ""
    valor: Decimal = Decimal("0")

# === PAGAMENTOS EFETUADOS ===

@dataclass
class PagamentoEfetuado:
    codigo: str = ""                    # código do tipo (01=Médico, 02=Dentista, etc.)
    descricao: str = ""
    cpf_cnpj_beneficiario: str = ""
    nome_beneficiario: str = ""
    valor_pago: Decimal = Decimal("0")
    parcela_nao_dedutivel: Decimal = Decimal("0")
    valor_dedutivel: Decimal = Decimal("0")

# === BENS E DIREITOS ===

@dataclass
class BemDireito:
    grupo: str = ""                     # grupo do bem
    codigo: str = ""                    # código do tipo
    descricao: str = ""
    situacao_anterior: Decimal = Decimal("0")   # valor em 31/12 do ano anterior
    situacao_atual: Decimal = Decimal("0")      # valor em 31/12 do ano atual

# === DÍVIDAS E ÔNUS REAIS ===

@dataclass
class DividaOnus:
    codigo: str = ""
    descricao: str = ""
    situacao_anterior: Decimal = Decimal("0")
    situacao_atual: Decimal = Decimal("0")

# === IMPOSTO PAGO/RETIDO ===

@dataclass
class ImpostoPagoRetido:
    imposto_complementar: Decimal = Decimal("0")
    imposto_pago_exterior: Decimal = Decimal("0")
    carne_leao: Decimal = Decimal("0")
    imposto_retido_lei: Decimal = Decimal("0")

# === DOAÇÕES ===

@dataclass
class DoacaoEfetuada:
    codigo: str = ""
    descricao: str = ""
    cnpj: str = ""
    nome: str = ""
    valor: Decimal = Decimal("0")

# === CÔNJUGE ===

@dataclass
class InfoConjuge:
    cpf: str = ""
    nome: str = ""
    total_rendimentos: Decimal = Decimal("0")
    total_bens: Decimal = Decimal("0")

# === MODELO PRINCIPAL ===

@dataclass
class DeclaracaoIrpfData:
    """Modelo completo de uma declaração IRPF."""

    # Metadados
    ano_exercicio: int = 0              # ano do programa (ex: 2016)
    ano_calendario: int = 0             # ano dos rendimentos (ex: 2015)
    tipo_declaracao: str = ""           # "Original" ou "Retificadora"
    numero_recibo: str = ""

    # Seções
    identificacao: Identificacao = field(default_factory=Identificacao)
    dependentes: list[Dependente] = field(default_factory=list)
    fontes_pj: list[FontePagadoraPJ] = field(default_factory=list)
    rendimentos_pf_exterior: Optional[RendimentosPFExterior] = None
    rendimentos_isentos: list[RendimentoIsento] = field(default_factory=list)
    rendimentos_trib_exclusiva: list[RendimentoTribExclusiva] = field(default_factory=list)
    imposto_pago_retido: ImpostoPagoRetido = field(default_factory=ImpostoPagoRetido)
    pagamentos_efetuados: list[PagamentoEfetuado] = field(default_factory=list)
    doacoes_efetuadas: list[DoacaoEfetuada] = field(default_factory=list)
    bens_direitos: list[BemDireito] = field(default_factory=list)
    dividas_onus: list[DividaOnus] = field(default_factory=list)
    info_conjuge: Optional[InfoConjuge] = None

    # Resumo (para validação)
    @property
    def total_sections(self) -> int:
        count = 1  # identificação sempre presente
        if self.dependentes: count += 1
        if self.fontes_pj: count += 1
        if self.rendimentos_pf_exterior: count += 1
        if self.rendimentos_isentos: count += 1
        if self.rendimentos_trib_exclusiva: count += 1
        count += 1  # imposto pago/retido
        if self.pagamentos_efetuados: count += 1
        if self.doacoes_efetuadas: count += 1
        if self.bens_direitos: count += 1
        if self.dividas_onus: count += 1
        if self.info_conjuge: count += 1
        return count

    @property
    def total_list_items(self) -> int:
        return (len(self.dependentes) + len(self.fontes_pj) +
                len(self.rendimentos_isentos) + len(self.rendimentos_trib_exclusiva) +
                len(self.pagamentos_efetuados) + len(self.doacoes_efetuadas) +
                len(self.bens_direitos) + len(self.dividas_onus))

# === RESULTADO ===

@dataclass
class AutomationResult:
    status: AutomationStatus
    message: str
    sections_filled: int = 0
    total_sections: int = 0
    items_filled: int = 0
    total_items: int = 0
    screenshots: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    elapsed_seconds: float = 0.0

@dataclass
class InstalledProgram:
    year: int
    path: str
    jar_path: str
    is_valid: bool
```

---

## 13. Configuração

### `.env`

```env
# API Key do Google Gemini
GEMINI_API_KEY=sua-chave-aqui

# Diretório base dos programas da Receita Federal (opcional, auto-detectado)
# IRPF_PROGRAMS_DIR=C:/Arquivos de Programas RFB

# Diretório para screenshots
SCREENSHOTS_DIR=./screenshots

# Nível de log (DEBUG, INFO, WARNING, ERROR)
LOG_LEVEL=INFO

# Caminho do Java (se não estiver no PATH)
# JAVA_PATH=C:/Program Files/Java/jdk-21/bin/java.exe

# Delay entre ações do RPA (em milissegundos) — aumentar se programa estiver lento
RPA_ACTION_DELAY_MS=500

# Timeout para aguardar programa abrir (em segundos)
RPA_PROGRAM_TIMEOUT=60
```

---

## 14. Empacotamento e Distribuição

### PyInstaller — Gerar `.exe`

```bash
pyinstaller --onedir --windowed --icon=ui/resources/icon.ico --name="IRPF-Automation" main.py
```

### Resultado

```
dist/
└── IRPF-Automation/
    ├── IRPF-Automation.exe    # Executável principal
    ├── config/
    │   └── field_mappings/    # YAMLs de mapeamento por ano
    ├── .env                   # Configuração do usuário
    └── (dependências internas)
```

### Instalador (opcional, futuro)

Para experiência profissional, criar instalador com **Inno Setup** que:
- Instala em `C:\Program Files\IRPF Automation\`
- Cria atalho no Desktop e Menu Iniciar
- Registra no Painel de Controle para desinstalação

---

## 15. Dependências Python

### `requirements.txt`

```
# Interface gráfica
PyQt6>=6.6.0

# Leitura de PDF
pdfplumber>=0.11.0

# Extração via IA
google-generativeai>=0.8.0

# Automação desktop (RPA)
pywinauto>=0.6.8

# Screenshots e imagens
Pillow>=10.2.0

# Automação de teclado/mouse (fallback)
pyautogui>=0.9.54

# Configuração
python-dotenv>=1.0.0
PyYAML>=6.0.1

# Logging
loguru>=0.7.2

# Empacotamento
pyinstaller>=6.3.0
```

---

## 16. Riscos e Pontos de Atenção

### Riscos Técnicos

| Risco | Impacto | Mitigação |
|---|---|---|
| **Interface do IRPF muda a cada ano** | Alto | Mapeamento YAML separado por ano; fácil de atualizar |
| **PDF de declarações antigas (2016-2018)** | Alto | Layout pode ser diferente; testar parser de seções por ano |
| **Gemini não extrai campo corretamente** | Médio | Tela de revisão permite correção manual; fallback regex |
| **Listas grandes (20+ bens)** | Médio | list_filler com delay configurável; retry por item |
| **Java Access Bridge não habilitado** | Médio | Fallback keyboard; guia de ativação na app |
| **Programa IRPF antigo + Java 21** | Médio | Usar JRE que vem com o programa |
| **PyInstaller falso-positivo antivírus** | Médio | Assinar executável; whitelist |

### Pontos de Atenção

1. **Windows apenas** — Programas IRPF só rodam no Windows
2. **Java necessário** — Programas IRPF precisam de Java (geralmente incluem JRE)
3. **Bloqueante** — Teclado/mouse controlados durante automação
4. **Validação essencial** — Erros na declaração têm consequência fiscal
5. **Segurança** — Dados de IR são sensíveis; nada é enviado além do texto ao Gemini
6. **Chave Gemini** — Usuário precisa de chave API (gratuita até certo limite)
7. **Declaração completa é extensa** — Pode ter 100+ campos para preencher

---

## 17. Ordem de Implementação (Sprints)

### Sprint 1 — Setup + Leitura e Parser do PDF

**Objetivo:** Ler o PDF da declaração e dividir em seções.

- [ ] Criar estrutura do projeto Python
- [ ] Configurar `requirements.txt` e `.env`
- [ ] Implementar `core/models.py` (todos os dataclasses)
- [ ] Implementar `core/exceptions.py`
- [ ] Implementar `extraction/pdf_reader.py` (pdfplumber)
- [ ] Implementar `extraction/section_parser.py` (dividir em seções)
- [ ] Testar com PDFs de declarações reais de diferentes anos
- [ ] Implementar `utils/cpf_cnpj.py` e `utils/currency.py`
- [ ] Implementar `utils/logger.py`

**Entregável:** Script que lê PDF e retorna seções separadas do texto.

---

### Sprint 2 — Extração via Gemini AI

**Objetivo:** Extrair dados estruturados de cada seção.

- [ ] Implementar `extraction/gemini_extractor.py`
- [ ] Criar prompts especializados para cada seção
- [ ] Implementar mapeamento JSON → dataclasses
- [ ] Implementar `extraction/validator.py`
- [ ] Testar extração com declarações de 2016, 2020 e 2024
- [ ] Implementar `extraction/fallback_extractor.py` (regex)

**Entregável:** Script que recebe PDF e retorna `DeclaracaoIrpfData` completo.

---

### Sprint 3 — Interface Gráfica (Telas 1 e 2)

**Objetivo:** UI funcional para upload e revisão.

- [ ] Configurar PyQt6 e estrutura da UI
- [ ] Implementar `ui/main_window.py` (wizard)
- [ ] Implementar `ui/extraction_panel.py` (Tela 1: upload)
- [ ] Implementar `ui/review_panel.py` (Tela 2: revisão geral)
- [ ] Implementar `ui/sections_review.py` (abas por seção com tabelas editáveis)
- [ ] Criar tema visual `ui/styles/theme.qss`
- [ ] Integrar extração com UI
- [ ] Criar ícone da aplicação

**Entregável:** App com UI que extrai e exibe TODOS os dados da declaração para revisão.

---

### Sprint 4 — Localização de Programas + Tela 3

**Objetivo:** Encontrar e abrir programas IRPF.

- [ ] Implementar `programs/locator.py`
- [ ] Implementar `programs/launcher.py`
- [ ] Implementar Tela 3 (seleção de programa)
- [ ] Auto-detecção do ano a partir da declaração
- [ ] Testar abertura de programas de diferentes anos

**Entregável:** App encontra e abre programa IRPF do ano correto.

---

### Sprint 5 — Automação RPA (POC — 1 ano, campos simples)

**Objetivo:** Preencher seções de campos fixos no IRPF 2024.

- [ ] Implementar `rpa/engine.py` (interface)
- [ ] Implementar `rpa/access_bridge_engine.py` (pywinauto)
- [ ] Implementar `rpa/keyboard_engine.py` (fallback)
- [ ] Implementar `rpa/navigator.py` (navegar árvore lateral)
- [ ] Implementar `rpa/form_filler.py` (campos simples)
- [ ] Implementar `rpa/screenshot.py`
- [ ] Criar `config/field_mappings/irpf_2024.yaml`
- [ ] Testar: Identificação + Imposto Pago/Retido no IRPF 2024

**Entregável:** Preenche seções simples no IRPF 2024.

---

### Sprint 6 — Automação RPA (Listas variáveis)

**Objetivo:** Preencher seções de lista (bens, pagamentos, etc.).

- [ ] Implementar `rpa/list_filler.py`
- [ ] Testar: Rendimentos Tributáveis PJ (múltiplas fontes)
- [ ] Testar: Dependentes (múltiplos)
- [ ] Testar: Pagamentos Efetuados (múltiplos)
- [ ] Testar: Bens e Direitos (múltiplos)
- [ ] Testar: Dívidas e Ônus Reais (múltiplos)
- [ ] Implementar Tela 4 (progresso com log ao vivo)

**Entregável:** Preenche declaração completa no IRPF 2024 (todas as seções).

---

### Sprint 7 — Fluxo Completo + Mais Anos

**Objetivo:** End-to-end para múltiplos anos.

- [ ] Conectar todo o fluxo: Upload → Extração → Revisão → Automação → Resultado
- [ ] Criar mapeamentos YAML para IRPF 2020-2025
- [ ] Testar fluxo completo para cada ano
- [ ] Implementar `ui/settings_dialog.py`
- [ ] Tratamento de erros robusto

**Entregável:** Fluxo completo funcionando para 2020-2025.

---

### Sprint 8 — Anos Antigos + Empacotamento

**Objetivo:** Cobertura total e distribuição.

- [ ] Criar mapeamentos YAML para IRPF 2016-2019
- [ ] Testar compatibilidade com programas antigos
- [ ] Configurar PyInstaller → gerar `.exe`
- [ ] Testar `.exe` em máquina limpa
- [ ] Criar README com instruções de instalação
- [ ] (Opcional) Criar instalador com Inno Setup

**Entregável:** Executável distribuível cobrindo 2016-2025.

---

## Glossário

| Termo | Significado |
|---|---|
| **IRPF** | Imposto de Renda Pessoa Física |
| **Declaração IRPF** | Documento completo enviado à Receita Federal com todos os dados fiscais da pessoa |
| **Ano-calendário** | Ano em que os rendimentos foram recebidos (ex: 2015) |
| **Ano-exercício** | Ano do programa/entrega da declaração (ex: 2016 para rendimentos de 2015) |
| **Retificação** | Nova declaração que substitui uma anterior já enviada |
| **IRRF** | Imposto de Renda Retido na Fonte |
| **RPA** | Robotic Process Automation — automação de processos via interface |
| **pywinauto** | Biblioteca Python para automação de aplicações Windows |
| **Java Access Bridge** | API do Windows que expõe componentes Java Swing para automação |
| **PyQt6** | Framework Python para interfaces gráficas nativas |
| **pdfplumber** | Biblioteca Python para extração de texto/tabelas de PDFs |
| **Gemini AI** | Modelo de IA generativa do Google usado para extração inteligente |
| **PyInstaller** | Ferramenta que empacota aplicações Python em executáveis `.exe` |
| **Section Parser** | Componente que divide o PDF da declaração nas seções padronizadas da Receita |
