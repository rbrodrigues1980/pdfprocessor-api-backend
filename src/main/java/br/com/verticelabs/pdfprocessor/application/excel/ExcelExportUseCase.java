package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.consolidation.ConsolidationUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.*;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportUseCase {

    private final ConsolidationUseCase consolidationUseCase;
    private final PersonRepository personRepository;
    private final ExcelExportService excelExportService;

    /**
     * Gera arquivo Excel com consolidação de uma pessoa (por CPF).
     * 
     * @param cpf CPF da pessoa
     * @param ano Ano opcional para filtrar (formato: "2017")
     * @param origem Origem opcional para filtrar ("CAIXA" ou "FUNCEF")
     * @return Mono com resultado contendo bytes e nome do arquivo
     */
    public Mono<ExcelExportResult> generateExcel(String cpf, String ano, String origem) {
        log.info("=== ExcelExportUseCase.generateExcel() INICIADO ===");
        log.info("CPF: {}, Ano: {}, Origem: {}", cpf, ano, origem);

        // 1. Buscar pessoa para obter nome e matrícula (com multi-tenancy)
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono;
                    if (isSuperAdmin) {
                        // SUPER_ADMIN: buscar pessoa sem filtrar por tenantId
                        log.debug("SUPER_ADMIN: buscando pessoa sem filtro de tenantId");
                        personMono = personRepository.findByCpf(cpf);
                    } else {
                        // Outros usuários: buscar pessoa filtrando por tenantId
                        log.debug("Usuário regular: buscando pessoa com filtro de tenantId");
                        personMono = ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findByTenantIdAndCpf(tenantId, cpf));
                    }

                    return personMono
                            .switchIfEmpty(Mono.defer(() -> {
                                log.error("Pessoa não encontrada para CPF: {}", cpf);
                                return Mono.error(new PersonNotFoundException(cpf));
                            }))
                            .flatMap(person -> {
                                log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                                log.debug("Matrícula: {}", person.getMatricula());

                                // 2. Buscar consolidação
                                log.info("Buscando dados consolidados...");
                                return consolidationUseCase.consolidate(cpf, ano, origem)
                            .flatMap(consolidatedResponse -> {
                                if (consolidatedResponse.getRubricas() == null || 
                                    consolidatedResponse.getRubricas().isEmpty()) {
                                    log.warn("Nenhuma rubrica para exportar");
                                    return Mono.error(new NoEntriesFoundException(cpf));
                                }

                                log.info("✓ Dados consolidados obtidos: {} rubricas, {} anos", 
                                        consolidatedResponse.getRubricas().size(),
                                        consolidatedResponse.getAnos().size());

                                // 3. Gerar nome do arquivo
                                String filename = generateFilename(person);
                                log.info("Nome do arquivo gerado: {}", filename);

                                // 4. Gerar Excel
                                log.info("Gerando arquivo Excel...");
                                return excelExportService.generateConsolidationExcel(
                                        person,
                                        consolidatedResponse,
                                        filename
                                )
                                .map(bytes -> {
                                    log.info("✓ Excel gerado com sucesso! Tamanho: {} bytes ({} KB)", 
                                            bytes.length, bytes.length / 1024);
                                    return new ExcelExportResult(bytes, filename);
                                })
                                .doOnError(error -> {
                                    log.error("Erro ao gerar Excel", error);
                                });
                            });
                });
                });
    }

    /**
     * Gera arquivo Excel com consolidação de uma pessoa (por personId).
     * Este método garante que apenas os dados da pessoa específica sejam usados,
     * mesmo quando há múltiplas pessoas com o mesmo CPF em diferentes tenants.
     * 
     * @param personId ID único da pessoa
     * @param ano Ano opcional para filtrar (formato: "2017")
     * @param origem Origem opcional para filtrar ("CAIXA" ou "FUNCEF")
     * @return Mono com resultado contendo bytes e nome do arquivo
     */
    public Mono<ExcelExportResult> generateExcelById(String personId, String ano, String origem) {
        log.info("=== ExcelExportUseCase.generateExcelById() INICIADO ===");
        log.info("PersonId: {}, Ano: {}, Origem: {}", personId, ano, origem);

        // 1. Buscar pessoa por ID (com validação de tenant para não-SUPER_ADMIN)
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono;
                    if (isSuperAdmin) {
                        // SUPER_ADMIN: buscar pessoa diretamente pelo ID
                        log.debug("SUPER_ADMIN: buscando pessoa por ID sem validação de tenant");
                        personMono = personRepository.findById(personId);
                    } else {
                        // Outros usuários: buscar pessoa e validar que pertence ao tenant
                        log.debug("Usuário regular: buscando pessoa por ID com validação de tenant");
                        personMono = ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findById(personId)
                                        .flatMap(person -> {
                                            if (!tenantId.equals(person.getTenantId())) {
                                                log.warn("Pessoa {} não pertence ao tenant {} (pertence a {})", 
                                                        personId, tenantId, person.getTenantId());
                                                return Mono.error(new PersonNotFoundException(personId));
                                            }
                                            return Mono.just(person);
                                        }));
                    }

                    return personMono
                            .switchIfEmpty(Mono.defer(() -> {
                                log.error("Pessoa não encontrada para personId: {}", personId);
                                return Mono.error(new PersonNotFoundException(personId));
                            }))
                            .flatMap(person -> {
                                log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                                log.debug("Matrícula: {}", person.getMatricula());

                                // 2. Buscar consolidação usando o CPF da pessoa encontrada
                                log.info("Buscando dados consolidados...");
                                return consolidationUseCase.consolidate(person.getCpf(), ano, origem)
                                        .flatMap(consolidatedResponse -> {
                                            if (consolidatedResponse.getRubricas() == null || 
                                                consolidatedResponse.getRubricas().isEmpty()) {
                                                log.warn("Nenhuma rubrica para exportar");
                                                return Mono.error(new NoEntriesFoundException(person.getCpf()));
                                            }

                                            log.info("✓ Dados consolidados obtidos: {} rubricas, {} anos", 
                                                    consolidatedResponse.getRubricas().size(),
                                                    consolidatedResponse.getAnos().size());

                                            // 3. Gerar nome do arquivo
                                            String filename = generateFilename(person);
                                            log.info("Nome do arquivo gerado: {}", filename);

                                            // 4. Gerar Excel
                                            log.info("Gerando arquivo Excel...");
                                            return excelExportService.generateConsolidationExcel(
                                                    person,
                                                    consolidatedResponse,
                                                    filename
                                            )
                                            .map(bytes -> {
                                                log.info("✓ Excel gerado com sucesso! Tamanho: {} bytes ({} KB)", 
                                                        bytes.length, bytes.length / 1024);
                                                return new ExcelExportResult(bytes, filename);
                                            })
                                            .doOnError(error -> {
                                                log.error("Erro ao gerar Excel", error);
                                            });
                                        });
                            });
                });
    }

    /**
     * Gera arquivo Excel com consolidação de uma pessoa (por CPF e tenantId).
     * Este método permite que o frontend passe explicitamente o CPF e tenantId da pessoa,
     * garantindo que apenas os dados da pessoa específica sejam usados.
     * 
     * @param cpf CPF da pessoa
     * @param tenantId ID do tenant da pessoa
     * @param ano Ano opcional para filtrar (formato: "2017")
     * @param origem Origem opcional para filtrar ("CAIXA" ou "FUNCEF")
     * @return Mono com resultado contendo bytes e nome do arquivo
     */
    public Mono<ExcelExportResult> generateExcelByCpfAndTenant(String cpf, String tenantId, String ano, String origem) {
        log.info("=== ExcelExportUseCase.generateExcelByCpfAndTenant() INICIADO ===");
        log.info("CPF: {}, TenantId: {}, Ano: {}, Origem: {}", cpf, tenantId, ano, origem);

        // 1. Buscar pessoa usando CPF e tenantId explicitamente
        return personRepository.findByTenantIdAndCpf(tenantId, cpf)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Pessoa não encontrada para CPF: {} no tenant: {}", cpf, tenantId);
                    return Mono.error(new PersonNotFoundException(cpf));
                }))
                .flatMap(person -> {
                    log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                    log.debug("Matrícula: {}, TenantId: {}", person.getMatricula(), person.getTenantId());

                    // 2. Buscar consolidação usando o CPF e tenantId da pessoa encontrada
                    log.info("Buscando dados consolidados...");
                    return consolidationUseCase.consolidate(person.getCpf(), person.getTenantId(), ano, origem)
                            .flatMap(consolidatedResponse -> {
                                if (consolidatedResponse.getRubricas() == null || 
                                    consolidatedResponse.getRubricas().isEmpty()) {
                                    log.warn("Nenhuma rubrica para exportar");
                                    return Mono.error(new NoEntriesFoundException(person.getCpf()));
                                }

                                log.info("✓ Dados consolidados obtidos: {} rubricas, {} anos", 
                                        consolidatedResponse.getRubricas().size(),
                                        consolidatedResponse.getAnos().size());

                                // 3. Gerar nome do arquivo
                                String filename = generateFilename(person);
                                log.info("Nome do arquivo gerado: {}", filename);

                                // 4. Gerar Excel
                                log.info("Gerando arquivo Excel...");
                                return excelExportService.generateConsolidationExcel(
                                        person,
                                        consolidatedResponse,
                                        filename
                                )
                                .map(bytes -> {
                                    log.info("✓ Excel gerado com sucesso! Tamanho: {} bytes ({} KB)", 
                                            bytes.length, bytes.length / 1024);
                                    return new ExcelExportResult(bytes, filename);
                                })
                                .doOnError(error -> {
                                    log.error("Erro ao gerar Excel", error);
                                });
                            });
                });
    }

    /**
     * Gera nome do arquivo no formato: YYYYMMDDHHMM_CPF_NOME.xlsx
     * Exemplo: 202512012124_12449709568_FLAVIO_JOSE_PEREIRA_ALMEIDA.xlsx
     */
    private String generateFilename(Person person) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        
        String dateTime = now.format(dateTimeFormatter);
        
        // Normalizar nome: remover acentos, espaços viram underscore, maiúsculas
        String nomeNormalizado = normalizeName(person.getNome());
        
        String cpf = person.getCpf();
        
        return String.format("%s_%s_%s.xlsx", dateTime, cpf, nomeNormalizado);
    }

    /**
     * Normaliza nome para uso em nome de arquivo:
     * - Remove acentos
     * - Espaços viram underscore
     * - Tudo maiúsculo
     * - Remove caracteres especiais
     */
    private String normalizeName(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            return "SEM_NOME";
        }
        
        // Remover acentos
        String normalized = removeAccents(nome.trim().toUpperCase());
        
        // Espaços viram underscore
        normalized = normalized.replaceAll("\\s+", "_");
        
        // Remove caracteres especiais (mantém apenas A-Z, 0-9 e _)
        normalized = normalized.replaceAll("[^A-Z0-9_]", "");
        
        // Remove underscores duplicados
        normalized = normalized.replaceAll("_{2,}", "_");
        
        // Remove underscore no início e fim
        normalized = normalized.replaceAll("^_|_$", "");
        
        // Limitar tamanho (nomes muito longos)
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
            // Garantir que não termina com underscore
            normalized = normalized.replaceAll("_$", "");
        }
        
        return normalized.isEmpty() ? "SEM_NOME" : normalized;
    }

    /**
     * Remove acentos de uma string.
     */
    private String removeAccents(String text) {
        if (text == null) {
            return "";
        }
        
        // Mapeamento de caracteres acentuados para não acentuados
        return text
                .replace("Á", "A").replace("À", "A").replace("Â", "A").replace("Ã", "A")
                .replace("É", "E").replace("È", "E").replace("Ê", "E")
                .replace("Í", "I").replace("Ì", "I").replace("Î", "I")
                .replace("Ó", "O").replace("Ò", "O").replace("Ô", "O").replace("Õ", "O")
                .replace("Ú", "U").replace("Ù", "U").replace("Û", "U")
                .replace("Ç", "C")
                .replace("á", "A").replace("à", "A").replace("â", "A").replace("ã", "A")
                .replace("é", "E").replace("è", "E").replace("ê", "E")
                .replace("í", "I").replace("ì", "I").replace("î", "I")
                .replace("ó", "O").replace("ò", "O").replace("ô", "O").replace("õ", "O")
                .replace("ú", "U").replace("ù", "U").replace("û", "U")
                .replace("ç", "C");
    }
}

