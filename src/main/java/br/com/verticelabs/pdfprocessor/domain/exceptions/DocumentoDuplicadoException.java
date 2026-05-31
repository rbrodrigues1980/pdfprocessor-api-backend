package br.com.verticelabs.pdfprocessor.domain.exceptions;

import lombok.Getter;

@Getter
public class DocumentoDuplicadoException extends RuntimeException {

    private final String existingDocumentId;

    public DocumentoDuplicadoException(String existingDocumentId) {
        super("Este arquivo já foi enviado anteriormente. Exclua o documento existente e envie novamente, "
                + "ou use replace=true no upload para substituir automaticamente.");
        this.existingDocumentId = existingDocumentId;
    }

    public DocumentoDuplicadoException(String existingDocumentId, String message) {
        super(message);
        this.existingDocumentId = existingDocumentId;
    }
}
