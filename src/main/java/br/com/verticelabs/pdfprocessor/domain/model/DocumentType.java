package br.com.verticelabs.pdfprocessor.domain.model;

public enum DocumentType {
    CAIXA,
    FUNCEF,
    CAIXA_FUNCEF,
    INCOME_TAX,
    /**
     * FUNCEF "Demonstrativo de Pagamento" — layout diferente do FUNCEF padrão.
     * Colunas: Mês Ref. | Data Início (vazia) | Código (6 dígitos) | Descrição | Valor | Resíduo | Prazo
     * Código: apenas os 4 primeiros dígitos identificam a rubrica (ex: 436204 → 4362).
     */
    FUNCEF_DEMONSTRATIVO
}

