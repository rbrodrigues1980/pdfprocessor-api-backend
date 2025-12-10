package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class InvalidOriginException extends RuntimeException {
    public InvalidOriginException(String origin) {
        super("Origem inválida: " + origin + ". Valores válidos: CAIXA, FUNCEF");
    }
}

