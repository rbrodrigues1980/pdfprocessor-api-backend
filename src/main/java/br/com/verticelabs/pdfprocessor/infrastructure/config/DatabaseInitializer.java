package br.com.verticelabs.pdfprocessor.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(1) // Executa antes do RubricaDataInitializer
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

    private final ReactiveMongoTemplate mongoTemplate;
    
    private static final String DATABASE_NAME = "pdfprocessor";

    @Override
    public void run(String... args) {
        log.info("Inicializando database '{}' e collections...", DATABASE_NAME);
        
        // Inicializar collection rubricas com índice único
        initializeRubricasCollection();
        
        // Inicializar collection payroll_documents com índices
        initializePayrollDocumentsCollection();
        
        // Inicializar collection persons com índice único
        initializePersonsCollection();
        
        // Inicializar collection users com índice único em email
        initializeUsersCollection();
    }

    private void initializeRubricasCollection() {
        String collectionName = "rubricas";
        log.info("Criando índices para collection '{}'...", collectionName);
        
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps(collectionName);
        Index codigoIndex = new Index().on("codigo", org.springframework.data.domain.Sort.Direction.ASC).unique();
        
        indexOps.ensureIndex(codigoIndex)
                .doOnSuccess(indexName -> log.info("Collection '{}' inicializada. Índice único 'codigo' criado: {}", 
                        collectionName, indexName))
                .doOnError(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("duplicate")) {
                        log.info("Índice 'codigo' já existe na collection '{}'", collectionName);
                    } else {
                        log.warn("Aviso ao criar índice na collection '{}': {}", collectionName, error.getMessage());
                    }
                })
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    private void initializePayrollDocumentsCollection() {
        String collectionName = "payroll_documents";
        log.info("Criando índices para collection '{}'...", collectionName);
        
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps(collectionName);
        
        // Remover índice antigo único em fileHash (se existir)
        indexOps.getIndexInfo()
                .filter(index -> index.getName().equals("fileHash_1"))
                .next()
                .flatMap(oldIndex -> {
                    log.info("Removendo índice antigo 'fileHash_1' da collection '{}'...", collectionName);
                    return indexOps.dropIndex("fileHash_1")
                            .doOnSuccess(v -> log.info("Índice antigo 'fileHash_1' removido com sucesso"))
                            .onErrorResume(e -> {
                                log.warn("Não foi possível remover índice antigo 'fileHash_1': {}", e.getMessage());
                                return Mono.empty();
                            });
                })
                .then(
                        // Criar novos índices
                        Mono.defer(() -> {
                            // Índice em tenantId
                            Index tenantIdIndex = new Index().on("tenantId", org.springframework.data.domain.Sort.Direction.ASC);
                            // Índice em cpf
                            Index cpfIndex = new Index().on("cpf", org.springframework.data.domain.Sort.Direction.ASC);
                            // Índice em status
                            Index statusIndex = new Index().on("status", org.springframework.data.domain.Sort.Direction.ASC);
                            // Índice em dataUpload (descendente para ordenar por mais recente)
                            Index dataUploadIndex = new Index().on("dataUpload", org.springframework.data.domain.Sort.Direction.DESC);
                            // Índice em tipo
                            Index tipoIndex = new Index().on("tipo", org.springframework.data.domain.Sort.Direction.ASC);
                            // Índice composto único em (tenantId, fileHash) - fileHash único por tenant
                            Index tenantFileHashIndex = new Index()
                                    .on("tenantId", org.springframework.data.domain.Sort.Direction.ASC)
                                    .on("fileHash", org.springframework.data.domain.Sort.Direction.ASC)
                                    .unique();
                            
                            return Mono.when(
                                    indexOps.ensureIndex(tenantIdIndex),
                                    indexOps.ensureIndex(cpfIndex),
                                    indexOps.ensureIndex(statusIndex),
                                    indexOps.ensureIndex(dataUploadIndex),
                                    indexOps.ensureIndex(tipoIndex),
                                    indexOps.ensureIndex(tenantFileHashIndex)
                            )
                            .doOnSuccess(v -> log.info("Collection '{}' inicializada com índices: tenantId, cpf, status, dataUpload, tipo, (tenantId, fileHash) único", collectionName))
                            .doOnError(error -> log.warn("Aviso ao criar índices na collection '{}': {}", collectionName, error.getMessage()))
                            .onErrorResume(error -> Mono.empty());
                        })
                )
                .switchIfEmpty(
                        // Se não havia índice antigo, criar diretamente
                        Mono.defer(() -> {
                            Index tenantIdIndex = new Index().on("tenantId", org.springframework.data.domain.Sort.Direction.ASC);
                            Index cpfIndex = new Index().on("cpf", org.springframework.data.domain.Sort.Direction.ASC);
                            Index statusIndex = new Index().on("status", org.springframework.data.domain.Sort.Direction.ASC);
                            Index dataUploadIndex = new Index().on("dataUpload", org.springframework.data.domain.Sort.Direction.DESC);
                            Index tipoIndex = new Index().on("tipo", org.springframework.data.domain.Sort.Direction.ASC);
                            Index tenantFileHashIndex = new Index()
                                    .on("tenantId", org.springframework.data.domain.Sort.Direction.ASC)
                                    .on("fileHash", org.springframework.data.domain.Sort.Direction.ASC)
                                    .unique();
                            
                            return Mono.when(
                                    indexOps.ensureIndex(tenantIdIndex),
                                    indexOps.ensureIndex(cpfIndex),
                                    indexOps.ensureIndex(statusIndex),
                                    indexOps.ensureIndex(dataUploadIndex),
                                    indexOps.ensureIndex(tipoIndex),
                                    indexOps.ensureIndex(tenantFileHashIndex)
                            )
                            .doOnSuccess(v -> log.info("Collection '{}' inicializada com índices: tenantId, cpf, status, dataUpload, tipo, (tenantId, fileHash) único", collectionName))
                            .doOnError(error -> log.warn("Aviso ao criar índices na collection '{}': {}", collectionName, error.getMessage()))
                            .onErrorResume(error -> Mono.empty());
                        })
                )
                .subscribe();
    }

    private void initializePersonsCollection() {
        String collectionName = "persons";
        log.info("Criando índices para collection '{}'...", collectionName);
        
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps(collectionName);
        
        // Remover índice antigo único em cpf (se existir)
        indexOps.getIndexInfo()
                .filter(index -> index.getName().equals("cpf_1"))
                .next()
                .flatMap(oldIndex -> {
                    log.info("Removendo índice antigo 'cpf_1' da collection '{}'...", collectionName);
                    return indexOps.dropIndex("cpf_1")
                            .doOnSuccess(v -> log.info("Índice antigo 'cpf_1' removido com sucesso"))
                            .onErrorResume(e -> {
                                log.warn("Não foi possível remover índice antigo 'cpf_1': {}", e.getMessage());
                                return Mono.empty();
                            });
                })
                .then(
                        // Criar novos índices
                        Mono.defer(() -> {
                            // Índice composto único: (tenantId, cpf) - CPF único por tenant
                            Index tenantCpfIndex = new Index()
                                    .on("tenantId", org.springframework.data.domain.Sort.Direction.ASC)
                                    .on("cpf", org.springframework.data.domain.Sort.Direction.ASC)
                                    .unique();
                            
                            // Índice simples em tenantId para consultas rápidas
                            Index tenantIdIndex = new Index().on("tenantId", org.springframework.data.domain.Sort.Direction.ASC);
                            
                            return Mono.when(
                                    indexOps.ensureIndex(tenantCpfIndex),
                                    indexOps.ensureIndex(tenantIdIndex)
                            )
                            .doOnSuccess(v -> log.info("Collection '{}' inicializada. Índices criados: (tenantId, cpf) único, tenantId", 
                                    collectionName))
                            .doOnError(error -> {
                                if (error.getMessage() != null && error.getMessage().contains("duplicate")) {
                                    log.info("Índices já existem na collection '{}'", collectionName);
                                } else {
                                    log.warn("Aviso ao criar índices na collection '{}': {}", collectionName, error.getMessage());
                                }
                            })
                            .onErrorResume(error -> Mono.empty());
                        })
                )
                .switchIfEmpty(
                        // Se não havia índice antigo, criar diretamente
                        Mono.defer(() -> {
                            Index tenantCpfIndex = new Index()
                                    .on("tenantId", org.springframework.data.domain.Sort.Direction.ASC)
                                    .on("cpf", org.springframework.data.domain.Sort.Direction.ASC)
                                    .unique();
                            
                            Index tenantIdIndex = new Index().on("tenantId", org.springframework.data.domain.Sort.Direction.ASC);
                            
                            return Mono.when(
                                    indexOps.ensureIndex(tenantCpfIndex),
                                    indexOps.ensureIndex(tenantIdIndex)
                            )
                            .doOnSuccess(v -> log.info("Collection '{}' inicializada. Índices criados: (tenantId, cpf) único, tenantId", 
                                    collectionName))
                            .doOnError(error -> {
                                if (error.getMessage() != null && error.getMessage().contains("duplicate")) {
                                    log.info("Índices já existem na collection '{}'", collectionName);
                                } else {
                                    log.warn("Aviso ao criar índices na collection '{}': {}", collectionName, error.getMessage());
                                }
                            })
                            .onErrorResume(error -> Mono.empty());
                        })
                )
                .subscribe();
    }

    private void initializeUsersCollection() {
        String collectionName = "users";
        log.info("Criando índices para collection '{}'...", collectionName);
        
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps(collectionName);
        Index emailIndex = new Index().on("email", org.springframework.data.domain.Sort.Direction.ASC).unique();
        
        indexOps.ensureIndex(emailIndex)
                .doOnSuccess(indexName -> log.info("Collection '{}' inicializada. Índice único 'email' criado: {}", 
                        collectionName, indexName))
                .doOnError(error -> {
                    if (error.getMessage() != null && error.getMessage().contains("duplicate")) {
                        log.info("Índice 'email' já existe na collection '{}'", collectionName);
                    } else {
                        log.warn("Aviso ao criar índice na collection '{}': {}", collectionName, error.getMessage());
                    }
                })
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}

