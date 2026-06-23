package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class EmpresaDuplicadaException extends RuntimeException {
    public EmpresaDuplicadaException(String sigla, String tenantId) {
        super("Empresa já cadastrada com sigla " + sigla + " no tenant " + tenantId);
    }
}
