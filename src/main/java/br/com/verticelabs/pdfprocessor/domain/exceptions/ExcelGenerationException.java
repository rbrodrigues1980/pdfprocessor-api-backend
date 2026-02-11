package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class ExcelGenerationException extends RuntimeException {
    public ExcelGenerationException(String message) {
        super(message);
    }

    public ExcelGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

