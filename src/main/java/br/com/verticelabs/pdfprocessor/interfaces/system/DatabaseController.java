package br.com.verticelabs.pdfprocessor.interfaces.system;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class DatabaseController {

    private final MongoClient mongoClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final ReactiveGridFsTemplate gridFsTemplate;

    @GetMapping("/databases")
    public Flux<String> listDatabases() {
        return Flux.from(mongoClient.listDatabases())
                .map(doc -> doc.getString("name"));
    }

    /**
     * Limpa todos os dados de upload do sistema.
     * Remove TODAS as collections do banco de dados, EXCETO:
     * - rubricas (mantém a tabela mestra de rubricas)
     * 
     * Collections que serão limpas:
     * - payroll_documents
     * - payroll_entries
     * - persons
     * - tenants (se existir)
     * - users (se existir)
     * - fs.files e fs.chunks (GridFS)
     * - Qualquer outra collection que existir no banco
     */
    @DeleteMapping("/clean-uploads")
    public Mono<ResponseEntity<Map<String, Object>>> cleanAllUploads() {
        log.warn("=== INICIANDO LIMPEZA DE TODOS OS DADOS DE UPLOAD ===");
        log.warn("⚠️ ATENÇÃO: Todas as collections serão limpas, EXCETO 'rubricas'");
        
        // Collections que NÃO devem ser limpas
        List<String> collectionsToPreserve = Arrays.asList("rubricas");
        
        Map<String, Object> resultMap = new HashMap<>();
        
        // Obter o nome do banco de dados e listar collections
        return mongoTemplate.getMongoDatabase()
                .flatMap(mongoDatabase -> {
                    String databaseName = mongoDatabase.getName();
                    MongoDatabase database = mongoClient.getDatabase(databaseName);
                    return Flux.from(database.listCollectionNames())
                            .collectList();
                })
                .flatMap(allCollections -> {
                    log.debug("Collections encontradas no banco: {}", allCollections);
                    
                    // Filtrar collections que devem ser preservadas
                    List<String> collectionsToClean = new ArrayList<>();
                    for (String collectionName : allCollections) {
                        if (!collectionsToPreserve.contains(collectionName)) {
                            collectionsToClean.add(collectionName);
                        } else {
                            log.debug("Collection '{}' será PRESERVADA (não será limpa)", collectionName);
                        }
                    }
                    
                    log.debug("Collections que serão limpas: {}", collectionsToClean);
                    
                    if (collectionsToClean.isEmpty()) {
                        log.warn("Nenhuma collection para limpar (todas estão na lista de preservação)");
                        resultMap.put("status", "success");
                        resultMap.put("message", "Nenhuma collection foi limpa. Todas estão na lista de preservação.");
                        resultMap.put("collections_preserved", collectionsToPreserve);
                        resultMap.put("collections_cleaned", 0);
                        return Mono.just(ResponseEntity.ok(resultMap));
                    }
                    
                    // Limpar cada collection
                    return Flux.fromIterable(collectionsToClean)
                            .flatMap(collectionName -> {
                                log.debug("Limpando collection '{}'...", collectionName);
                                
                                // Para GridFS, usar o template específico
                                if (collectionName.equals("fs.files") || collectionName.equals("fs.chunks")) {
                                    // GridFS será tratado separadamente
                                    return Mono.just(new CollectionCleanResult(collectionName, 0, true));
                                }
                                
                                // Para outras collections, usar remove
                                return mongoTemplate.remove(new Query(), collectionName)
                                        .map(deleteResult -> {
                                            long deletedCount = deleteResult.getDeletedCount();
                                            log.debug("✅ Collection '{}': {} documentos deletados", collectionName, deletedCount);
                                            return new CollectionCleanResult(collectionName, deletedCount, false);
                                        })
                                        .onErrorResume(error -> {
                                            log.error("❌ Erro ao limpar collection '{}': {}", collectionName, error.getMessage());
                                            return Mono.just(new CollectionCleanResult(collectionName, 0, false));
                                        });
                            })
                            .collectList()
                            .flatMap(cleanResults -> {
                                // Limpar GridFS separadamente
                                log.debug("Limpando GridFS (fs.files e fs.chunks)...");
                                return gridFsTemplate.find(new Query())
                                        .collectList()
                                        .flatMap(files -> {
                                            if (files.isEmpty()) {
                                                log.debug("Nenhum arquivo encontrado no GridFS");
                                                resultMap.put("gridfs_files_deleted", 0);
                                                return Mono.just(cleanResults);
                                            }
                                            
                                            log.debug("Encontrados {} arquivos no GridFS. Deletando...", files.size());
                                            return Flux.fromIterable(files)
                                                    .flatMap(file -> gridFsTemplate.delete(new Query(
                                                            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(file.getId())
                                                    )))
                                                    .then()
                                                    .thenReturn(files.size())
                                                    .map(count -> {
                                                        resultMap.put("gridfs_files_deleted", count);
                                                        log.debug("✅ GridFS: {} arquivos deletados", count);
                                                        return cleanResults;
                                                    });
                                        });
                            })
                            .map(cleanResults -> {
                                // Adicionar resultados ao map
                                Map<String, Long> deletedCounts = new HashMap<>();
                                long totalDeleted = 0;
                                
                                for (CollectionCleanResult result : cleanResults) {
                                    if (!result.isGridFs()) {
                                        deletedCounts.put(result.getCollectionName() + "_deleted", result.getDeletedCount());
                                        totalDeleted += result.getDeletedCount();
                                    }
                                }
                                
                                resultMap.putAll(deletedCounts);
                                resultMap.put("status", "success");
                                resultMap.put("message", String.format(
                                        "Limpeza concluída. %d collections foram limpas. Collection 'rubricas' foi preservada.",
                                        cleanResults.size()));
                                resultMap.put("collections_preserved", collectionsToPreserve);
                                resultMap.put("collections_cleaned", cleanResults.size());
                                resultMap.put("total_documents_deleted", totalDeleted);
                                
                                log.warn("=== LIMPEZA CONCLUÍDA COM SUCESSO ===");
                                log.warn("Collections preservadas: {}", collectionsToPreserve);
                                log.warn("Total de collections limpas: {}", cleanResults.size());
                                
                                return ResponseEntity.ok(resultMap);
                            });
                })
                .onErrorResume(error -> {
                    log.error("Erro ao limpar dados de upload: {}", error.getMessage(), error);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", "Erro ao limpar dados: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResult));
                });
    }
    
    /**
     * Classe auxiliar para armazenar resultado da limpeza de uma collection
     */
    private static class CollectionCleanResult {
        private final String collectionName;
        private final long deletedCount;
        private final boolean isGridFs;
        
        public CollectionCleanResult(String collectionName, long deletedCount, boolean isGridFs) {
            this.collectionName = collectionName;
            this.deletedCount = deletedCount;
            this.isGridFs = isGridFs;
        }
        
        public String getCollectionName() {
            return collectionName;
        }
        
        public long getDeletedCount() {
            return deletedCount;
        }
        
        public boolean isGridFs() {
            return isGridFs;
        }
    }
}
