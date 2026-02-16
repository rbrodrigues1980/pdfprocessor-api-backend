
# Fluxogramas de Processamento

## 1. Upload e Identificação do Documento
```mermaid
flowchart TD
    A[Upload PDF] --> B{Arquivo é PDF?}
    B -->|Não| X[Erro: formato inválido]
    B -->|Sim| C[Salvar documento em Mongo com status=PENDING]
    C --> D[Detectar tipo: Caixa, Funcef ou Misto]
    D --> E[Atualizar documento: tipo detectado]
    E --> F[Iniciar processamento assíncrono]
```

## 2. Processamento de PDF
```mermaid
flowchart TD
    A[Carregar PDF] --> B[Extrair texto com Tika]
    B --> C[Separar por páginas]
    C --> D[Para cada página: identificar origem]
    D --> E[Extrair metadados da página]
    E --> F[Extrair rubricas via Regex]
    F --> G[Salvar PayrollEntry]
    G --> H{Próxima página?}
    H -->|Sim| D
    H -->|Não| I[Atualizar documento: status=PROCESSED]
```

## 3. Consolidação por CPF
```mermaid
flowchart TD
    A[Requisição: /consolidated/{cpf}] --> B[Buscar pessoa]
    B --> C[Buscar todos documentos dela]
    C --> D[Buscar todas PayrollEntry]
    D --> E[Organizar por rubrica → mês → valor]
    E --> F[Montar matriz consolidada]
    F --> G[Retornar JSON consolidado]
```
