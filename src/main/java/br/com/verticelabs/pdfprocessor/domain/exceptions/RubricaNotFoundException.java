package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class RubricaNotFoundException extends RuntimeException {
    public RubricaNotFoundException(String codigo) {
        super("Rubrica não encontrada com código: " + codigo);
    }
}

