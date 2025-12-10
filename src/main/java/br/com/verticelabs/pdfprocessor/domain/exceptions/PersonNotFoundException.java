package br.com.verticelabs.pdfprocessor.domain.exceptions;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(String identifier) {
        super("Pessoa não encontrada: " + identifier);
    }
    
    public PersonNotFoundException(String cpf, String tenantId) {
        super("Pessoa não encontrada com CPF: " + cpf + " no tenant: " + tenantId);
    }
}

