package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.Getter;

/**
 * Status operacional do cliente (independente do campo {@code validado}).
 */
@Getter
public enum PersonStatus {
    EM_PROCESSAMENTO("Em processamento"),
    AGUARDANDO_DOC_COMPLEMENTAR("Aguardando documentação complementar"),
    AGUARDANDO_DOC_EXERCICIO("Aguardando documentação Exercício: 2026 Ano-Calendario: 2025"),
    ANOTACAO("Anotação"),
    FINALIZADO("Finalizado");

    private final String label;

    PersonStatus(String label) {
        this.label = label;
    }

    /**
     * Clientes sem status persistido são tratados como {@link #EM_PROCESSAMENTO}.
     */
    public static PersonStatus fromNullable(PersonStatus status) {
        return status != null ? status : EM_PROCESSAMENTO;
    }
}
