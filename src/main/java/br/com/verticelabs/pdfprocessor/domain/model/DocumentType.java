package br.com.verticelabs.pdfprocessor.domain.model;

public enum DocumentType {
    /**
     * Tipo ainda não detectado — classificação e metadados de páginas ocorrem no processamento assíncrono.
     */
    UNKNOWN,
    CAIXA,
    FUNCEF,
    CAIXA_FUNCEF,
    INCOME_TAX,
    /**
     * FUNCEF "Demonstrativo de Pagamento" — layout diferente do FUNCEF padrão.
     * Colunas: Mês Ref. | Data Início (vazia) | Código (6 dígitos) | Descrição | Valor | Resíduo | Prazo
     * Código: apenas os 4 primeiros dígitos identificam a rubrica (ex: 436204 → 4362).
     */
    FUNCEF_DEMONSTRATIVO,
    /**
     * Comprovante / Informe de Rendimentos Funcef
     * ("COMPROVANTE DE RENDIMENTOS PAGOS E DE RETENÇÃO DE IMPOSTO DE RENDA NA FONTE").
     * Dados estruturados em {@link InformeRendimentosData} (seção 7 + cabeçalho).
     */
    INFORME_RENDIMENTOS,
    /**
     * Demonstrativo de Pagamento SABESP (ativa).
     * Identificação: PERÍODO, MATRÍC, NOME DO EMPREGADO (CPF opcional no PDF).
     * Rubricas: parser genérico + whitelist na tabela {@code rubricas}.
     */
    SABESP,
    /**
     * Ficha Financeira de Pagamentos SABESPREV (aposentado) — matriz anual rubrica × JAN–DEZ.
     * Identificação: FICHA FINANCEIRA + SABESPREV + ANO. Origem das entries: {@code SABESPREV}.
     */
    SABESPREV_FICHA;

    /**
     * Rótulo legível para logs de processamento e UI (evita expor enum cru, ex.: UNKNOWN).
     */
    public String getLogLabel() {
        return switch (this) {
            case UNKNOWN -> "Detectando tipo...";
            case CAIXA -> "CAIXA";
            case FUNCEF -> "FUNCEF";
            case CAIXA_FUNCEF -> "CAIXA + FUNCEF";
            case INCOME_TAX -> "IRPF";
            case FUNCEF_DEMONSTRATIVO -> "FUNCEF Demonstrativo";
            case INFORME_RENDIMENTOS -> "Informe de Rendimentos";
            case SABESP -> "Contracheque SABESP";
            case SABESPREV_FICHA -> "Ficha Financeira SABESPREV";
        };
    }
}