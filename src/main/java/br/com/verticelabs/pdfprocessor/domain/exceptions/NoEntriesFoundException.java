package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class NoEntriesFoundException extends RuntimeException {
    public NoEntriesFoundException(String cpf) {
        super("Nenhuma entrada encontrada para a pessoa com CPF: " + cpf);
    }
}

