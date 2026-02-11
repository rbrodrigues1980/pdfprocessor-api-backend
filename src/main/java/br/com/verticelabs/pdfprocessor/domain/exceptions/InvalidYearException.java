package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class InvalidYearException extends RuntimeException {
    public InvalidYearException(String year) {
        super("Ano inválido: " + year);
    }
}

