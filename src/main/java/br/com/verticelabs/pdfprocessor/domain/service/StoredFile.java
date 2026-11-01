package br.com.verticelabs.pdfprocessor.domain.service;

import java.io.InputStream;

/**
 * Arquivo lido do GridFS, com o nome e o tipo gravados no upload.
 */
public record StoredFile(InputStream inputStream, String filename, String contentType, long length) {
}
