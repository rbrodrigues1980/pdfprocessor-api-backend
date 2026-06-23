package br.com.verticelabs.pdfprocessor.domain.tributacao;

/**
 * Estratégia matemática de dedução para códigos oficiais de Pagamentos/Doações IRPF (SERPRO).
 */
public enum IrTipoRegraDeducao {
    /** 100% dedutível — saúde e pensão alimentícia. */
    SEM_LIMITE,
    /** Trava anual por CPF — instrução (códigos 01 e 02). */
    LIMITADO_POR_CPF,
    /** Trava global de 12% sobre rendimentos tributáveis — PGBL (36 e 37). */
    LIMITADO_RENDA_12PCT,
    /** Válido até AC 2018 com teto do ano; zero a partir de 2019 — código 50. */
    TEMPORAL_DOMESTICO,
    /** Abate direto do imposto devido (após redução anual) — doações 40–45. */
    DEDUCAO_DIRETA_IMPOSTO
}
