package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class EmpresaNotFoundException extends RuntimeException {
    public EmpresaNotFoundException(String id) {
        super("Empresa não encontrada: " + id);
    }
}
