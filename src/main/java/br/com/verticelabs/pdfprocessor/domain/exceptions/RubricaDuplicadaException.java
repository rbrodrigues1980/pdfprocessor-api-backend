package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class RubricaDuplicadaException extends RuntimeException {
    public RubricaDuplicadaException(String codigo) {
        super("Rubrica já cadastrada com código: " + codigo);
    }
}

