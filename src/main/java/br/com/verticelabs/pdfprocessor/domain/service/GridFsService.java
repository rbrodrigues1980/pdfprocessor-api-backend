package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.io.InputStream;

public interface GridFsService {
    Mono<String> storeFile(InputStream inputStream, String filename, String contentType);
    
    /**
     * Armazena arquivo com deduplicação por hash.
     * Se já existir um arquivo com o mesmo hash, reutiliza o existente.
     * 
     * @param inputStream Stream do arquivo
     * @param filename Nome do arquivo
     * @param contentType Tipo MIME do arquivo
     * @param fileHash Hash SHA-256 do arquivo para deduplicação
     * @return ID do arquivo (novo ou existente)
     */
    Mono<String> storeFileWithHash(InputStream inputStream, String filename, String contentType, String fileHash);
    
    Mono<InputStream> retrieveFile(String fileId);
    
    Mono<Void> deleteFile(String fileId);
}

