package br.com.verticelabs.pdfprocessor.infrastructure.storage;

import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GridFsServiceImpl implements GridFsService {

    private final ReactiveGridFsTemplate gridFsTemplate;

    @Override
    public Mono<String> storeFile(InputStream inputStream, String filename, String contentType) {
        // Para compatibilidade, chama storeFileWithHash sem hash (não faz deduplicação)
        return storeFileWithHash(inputStream, filename, contentType, null);
    }
    
    @Override
    public Mono<String> storeFileWithHash(InputStream inputStream, String filename, String contentType, String fileHash) {
        log.info("GridFS: Iniciando armazenamento do arquivo '{}' (tipo: {}, hash: {})", 
                filename, contentType, fileHash != null ? fileHash.substring(0, 16) + "..." : "não fornecido");
        
        // Se hash foi fornecido, verificar se arquivo já existe
        if (fileHash != null && !fileHash.isEmpty()) {
            return findFileByHash(fileHash)
                    .flatMap(existingFileId -> {
                        log.info("✅ GridFS: Arquivo com hash '{}' já existe! Reutilizando fileId: {}", 
                                fileHash.substring(0, 16) + "...", existingFileId);
                        return Mono.just(existingFileId);
                    })
                    .switchIfEmpty(
                            // Arquivo não existe, salvar novo
                            Mono.defer(() -> saveNewFile(inputStream, filename, contentType, fileHash))
                    );
        } else {
            // Sem hash, salvar diretamente (sem deduplicação)
            return saveNewFile(inputStream, filename, contentType, null);
        }
    }
    
    private Mono<String> findFileByHash(String fileHash) {
        Query query = new Query(Criteria.where("metadata.fileHash").is(fileHash));
        return gridFsTemplate.findOne(query)
                .map(file -> {
                    String fileId = file.getObjectId().toHexString();
                    log.debug("GridFS: Arquivo encontrado por hash. ID: {}", fileId);
                    return fileId;
                });
    }
    
    private Mono<String> saveNewFile(InputStream inputStream, String filename, String contentType, String fileHash) {
        return Mono.fromCallable(() -> {
            byte[] bytes = inputStream.readAllBytes();
            log.info("GridFS: Arquivo lido. Tamanho: {} bytes ({} KB)", bytes.length, bytes.length / 1024);
            return bytes;
        })
        .flatMap(bytes -> {
            Flux<DataBuffer> dataBufferFlux = Flux.just(
                    org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(bytes)
            );
            
            // Preparar metadados com hash (se fornecido)
            Document metadata = new Document();
            metadata.put("_contentType", contentType);
            if (fileHash != null && !fileHash.isEmpty()) {
                metadata.put("fileHash", fileHash);
            }
            
            log.info("GridFS: Salvando novo arquivo no MongoDB (collections fs.files e fs.chunks)...");
            return gridFsTemplate.store(dataBufferFlux, filename, contentType, metadata)
                    .map(objectId -> {
                        String fileId = objectId.toHexString();
                        log.info("✅ GridFS: Arquivo salvo com sucesso! ID: {}", fileId);
                        if (fileHash != null) {
                            log.info("GridFS: Hash '{}' armazenado nos metadados para deduplicação futura", 
                                    fileHash.substring(0, 16) + "...");
                        }
                        return fileId;
                    })
                    .doOnError(error -> log.error("GridFS: Erro ao salvar arquivo: {}", error.getMessage(), error));
        });
    }

    @Override
    public Mono<InputStream> retrieveFile(String fileId) {
        return gridFsTemplate.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(fileId))
        ))
        .flatMap(gridFsTemplate::getResource)
        .flatMap(resource -> {
            return DataBufferUtils.join(resource.getDownloadStream())
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new ByteArrayInputStream(bytes);
                    });
        });
    }

    @Override
    public Mono<Void> deleteFile(String fileId) {
        return gridFsTemplate.delete(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(fileId))
        ))
        .then();
    }
}

