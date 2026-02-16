package br.com.verticelabs.pdfprocessor.interfaces.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ExcelExportUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/persons")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelExportUseCase excelExportUseCase;

    /**
     * GET /api/v1/persons/{cpf}/excel
     * Gera arquivo Excel com consolidação de todas as rubricas de uma pessoa (por CPF).
     * 
     * Query params opcionais:
     * - ano: gera Excel apenas de 1 ano (ex: "2017")
     * - origem: filtra CAIXA/FUNCEF
     * 
     * ⚠️ NOTA: Se você tem o personId disponível, use o endpoint /{personId}/excel-by-id
     * para evitar problemas com múltiplas pessoas com o mesmo CPF em diferentes tenants.
     */
    @GetMapping(value = "/{cpf}/excel", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<byte[]>> generateExcel(
            @PathVariable String cpf,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String origem) {
        
        log.debug("=== INÍCIO: GET /api/v1/persons/{}/excel ===", cpf);
        log.debug("Parâmetros recebidos - CPF: {}, Ano: {}, Origem: {}", cpf, ano, origem);

        return excelExportUseCase.generateExcel(cpf, ano, origem)
                .map(result -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", result.getFilename());
                    headers.setContentLength(result.getBytes().length);
                    
                    log.debug("=== SUCESSO: Excel gerado para CPF: {} ===", cpf);
                    log.debug("Tamanho do arquivo: {} bytes ({} KB)", result.getBytes().length, result.getBytes().length / 1024);
                    log.debug("Nome do arquivo: {}", result.getFilename());
                    
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(result.getBytes());
                })
                .onErrorResume(PersonNotFoundException.class, e -> {
                    log.warn("=== ERRO: Pessoa não encontrada ===");
                    log.warn("CPF: {}", cpf);
                    log.warn("Status: 404 NOT_FOUND");
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                })
                .onErrorResume(NoEntriesFoundException.class, e -> {
                    log.warn("=== AVISO: Nenhuma entrada encontrada ===");
                    log.warn("CPF: {}", cpf);
                    log.warn("Filtros aplicados - Ano: {}, Origem: {}", ano, origem);
                    log.warn("Status: 204 NO_CONTENT");
                    return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
                })
                .onErrorResume(InvalidYearException.class, e -> {
                    log.warn("=== ERRO: Ano inválido ===");
                    log.warn("Ano fornecido: {}", ano);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(InvalidOriginException.class, e -> {
                    log.warn("=== ERRO: Origem inválida ===");
                    log.warn("Origem fornecida: {}", origem);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(ExcelGenerationException.class, e -> {
                    log.error("=== ERRO: Falha ao gerar Excel ===", e);
                    log.error("CPF: {}", cpf);
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("=== ERRO CRÍTICO: Falha ao gerar Excel ===", e);
                    log.error("CPF: {}", cpf);
                    log.error("Ano: {}, Origem: {}", ano, origem);
                    log.error("Mensagem de erro: {}", e.getMessage());
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * GET /api/v1/persons/{personId}/excel-by-id
     * Gera arquivo Excel com consolidação de todas as rubricas de uma pessoa (por personId).
     * 
     * ⭐ RECOMENDADO: Use este endpoint quando você tem o personId disponível (vindo da lista de pessoas).
     * Este endpoint garante que apenas os dados da pessoa específica sejam usados,
     * mesmo quando há múltiplas pessoas com o mesmo CPF em diferentes tenants.
     * 
     * Query params opcionais:
     * - ano: gera Excel apenas de 1 ano (ex: "2017")
     * - origem: filtra CAIXA/FUNCEF
     */
    @GetMapping(value = "/{personId}/excel-by-id", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<byte[]>> generateExcelById(
            @PathVariable String personId,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String origem) {
        
        log.debug("=== INÍCIO: GET /api/v1/persons/{}/excel-by-id ===", personId);
        log.debug("Parâmetros recebidos - PersonId: {}, Ano: {}, Origem: {}", personId, ano, origem);

        return excelExportUseCase.generateExcelById(personId, ano, origem)
                .map(result -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", result.getFilename());
                    headers.setContentLength(result.getBytes().length);
                    
                    log.debug("=== SUCESSO: Excel gerado para personId: {} ===", personId);
                    log.debug("Tamanho do arquivo: {} bytes ({} KB)", result.getBytes().length, result.getBytes().length / 1024);
                    log.debug("Nome do arquivo: {}", result.getFilename());
                    
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(result.getBytes());
                })
                .onErrorResume(PersonNotFoundException.class, e -> {
                    log.warn("=== ERRO: Pessoa não encontrada ===");
                    log.warn("PersonId: {}", personId);
                    log.warn("Status: 404 NOT_FOUND");
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                })
                .onErrorResume(NoEntriesFoundException.class, e -> {
                    log.warn("=== AVISO: Nenhuma entrada encontrada ===");
                    log.warn("PersonId: {}", personId);
                    log.warn("Filtros aplicados - Ano: {}, Origem: {}", ano, origem);
                    log.warn("Status: 204 NO_CONTENT");
                    return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
                })
                .onErrorResume(InvalidYearException.class, e -> {
                    log.warn("=== ERRO: Ano inválido ===");
                    log.warn("Ano fornecido: {}", ano);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(InvalidOriginException.class, e -> {
                    log.warn("=== ERRO: Origem inválida ===");
                    log.warn("Origem fornecida: {}", origem);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(ExcelGenerationException.class, e -> {
                    log.error("=== ERRO: Falha ao gerar Excel ===", e);
                    log.error("PersonId: {}", personId);
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("=== ERRO CRÍTICO: Falha ao gerar Excel ===", e);
                    log.error("PersonId: {}", personId);
                    log.error("Ano: {}, Origem: {}", ano, origem);
                    log.error("Mensagem de erro: {}", e.getMessage());
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * GET /api/v1/persons/{cpf}/excel-by-tenant
     * Gera arquivo Excel com consolidação de todas as rubricas de uma pessoa (por CPF e tenantId).
     * 
     * ⭐ RECOMENDADO: Use este endpoint quando você tem o CPF e tenantId disponíveis (vindo da lista de pessoas).
     * Este endpoint garante que apenas os dados da pessoa específica sejam usados,
     * mesmo quando há múltiplas pessoas com o mesmo CPF em diferentes tenants.
     * 
     * Query params obrigatórios:
     * - tenantId: ID do tenant da pessoa
     * 
     * Query params opcionais:
     * - ano: gera Excel apenas de 1 ano (ex: "2017")
     * - origem: filtra CAIXA/FUNCEF
     */
    @GetMapping(value = "/{cpf}/excel-by-tenant", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<byte[]>> generateExcelByCpfAndTenant(
            @PathVariable String cpf,
            @RequestParam(required = true) String tenantId,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String origem) {
        
        log.debug("=== INÍCIO: GET /api/v1/persons/{}/excel-by-tenant ===", cpf);
        log.debug("Parâmetros recebidos - CPF: {}, TenantId: {}, Ano: {}, Origem: {}", cpf, tenantId, ano, origem);

        return excelExportUseCase.generateExcelByCpfAndTenant(cpf, tenantId, ano, origem)
                .map(result -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    
                    // Usar ContentDisposition explícito para garantir compatibilidade com frontend
                    String filename = result.getFilename();
                    headers.setContentDisposition(
                            org.springframework.http.ContentDisposition.attachment()
                                    .filename(filename)
                                    .build()
                    );
                    headers.setContentLength(result.getBytes().length);
                    
                    log.debug("=== SUCESSO: Excel gerado para CPF: {} no tenant: {} ===", cpf, tenantId);
                    log.debug("Tamanho do arquivo: {} bytes ({} KB)", result.getBytes().length, result.getBytes().length / 1024);
                    log.debug("Nome do arquivo: {}", filename);
                    log.debug("Content-Disposition header: {}", headers.getContentDisposition());
                    
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(result.getBytes());
                })
                .onErrorResume(PersonNotFoundException.class, e -> {
                    log.warn("=== ERRO: Pessoa não encontrada ===");
                    log.warn("CPF: {}, TenantId: {}", cpf, tenantId);
                    log.warn("Status: 404 NOT_FOUND");
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                })
                .onErrorResume(NoEntriesFoundException.class, e -> {
                    log.warn("=== AVISO: Nenhuma entrada encontrada ===");
                    log.warn("CPF: {}, TenantId: {}", cpf, tenantId);
                    log.warn("Filtros aplicados - Ano: {}, Origem: {}", ano, origem);
                    log.warn("Status: 204 NO_CONTENT");
                    return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
                })
                .onErrorResume(InvalidYearException.class, e -> {
                    log.warn("=== ERRO: Ano inválido ===");
                    log.warn("Ano fornecido: {}", ano);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(InvalidOriginException.class, e -> {
                    log.warn("=== ERRO: Origem inválida ===");
                    log.warn("Origem fornecida: {}", origem);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(ExcelGenerationException.class, e -> {
                    log.error("=== ERRO: Falha ao gerar Excel ===", e);
                    log.error("CPF: {}, TenantId: {}", cpf, tenantId);
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("=== ERRO CRÍTICO: Falha ao gerar Excel ===", e);
                    log.error("CPF: {}, TenantId: {}", cpf, tenantId);
                    log.error("Ano: {}, Origem: {}", ano, origem);
                    log.error("Mensagem de erro: {}", e.getMessage());
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}

