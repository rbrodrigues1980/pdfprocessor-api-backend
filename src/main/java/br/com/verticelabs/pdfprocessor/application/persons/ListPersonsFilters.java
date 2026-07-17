package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;

import java.time.LocalDate;

/**
 * Filtros opcionais da listagem de clientes.
 */
public record ListPersonsFilters(
        String nome,
        String cpf,
        String matricula,
        Boolean validado,
        String empresaId,
        LocalDate cadastroDe,
        LocalDate cadastroAte,
        PersonStatus status
) {
    /**
     * Indica se há pelo menos um critério explícito (necessário para o relatório Excel).
     */
    public boolean hasAnyFilter() {
        return (nome != null && !nome.isBlank())
                || (cpf != null && !cpf.isBlank())
                || (matricula != null && !matricula.isBlank())
                || validado != null
                || (empresaId != null && !empresaId.isBlank())
                || cadastroDe != null
                || cadastroAte != null
                || status != null;
    }
}
