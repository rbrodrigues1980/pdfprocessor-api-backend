package br.com.verticelabs.pdfprocessor.interfaces.api;

import br.com.verticelabs.pdfprocessor.application.selic.TaxaSelicService;
import br.com.verticelabs.pdfprocessor.application.selic.dto.SelicReceitaCalculoResponse;
import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Controller para consulta de taxas SELIC.
 */
@Slf4j
@RestController
@RequestMapping("/selic")
@RequiredArgsConstructor
@Tag(name = "Taxa SELIC", description = "Histórico de taxas SELIC do Banco Central")
public class TaxaSelicController {

    private final TaxaSelicService taxaSelicService;

    /**
     * Retorna a taxa SELIC vigente atualmente.
     */
    @GetMapping("/vigente")
    @Operation(summary = "Taxa SELIC vigente atual")
    public Mono<ResponseEntity<TaxaSelic>> getTaxaVigente() {
        return taxaSelicService.buscarTaxaVigente()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retorna a taxa SELIC vigente em uma data específica.
     */
    @GetMapping("/data/{data}")
    @Operation(summary = "Taxa SELIC vigente em uma data")
    public Mono<ResponseEntity<TaxaSelic>> getTaxaPorData(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return taxaSelicService.buscarTaxaEmData(data)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retorna a taxa SELIC de referência para um ano (usa 1º de julho).
     */
    @GetMapping("/ano/{ano}")
    @Operation(summary = "Taxa SELIC de referência para um ano")
    public Mono<ResponseEntity<TaxaSelic>> getTaxaPorAno(@PathVariable Integer ano) {
        return taxaSelicService.buscarTaxaPorAno(ano)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lista todo o histórico de taxas SELIC.
     */
    @GetMapping("/historico")
    @Operation(summary = "Histórico completo de taxas SELIC")
    public Flux<TaxaSelic> getHistorico() {
        return taxaSelicService.listarHistorico();
    }

    /**
     * Lista taxas de um período.
     */
    @GetMapping("/periodo")
    @Operation(summary = "Taxas SELIC de um período")
    public Flux<TaxaSelic> getTaxasPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return taxaSelicService.listarPorPeriodo(inicio, fim);
    }

    /**
     * Calcula a SELIC acumulada em um período.
     * Exemplo: /selic/acumulada?inicio=2023-01-01&fim=2024-10-31
     */
    @GetMapping("/acumulada")
    @Operation(summary = "SELIC acumulada em um período")
    public Mono<ResponseEntity<TaxaSelicService.SelicAcumuladaResult>> getSelicAcumulada(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        log.info("Solicitação de SELIC acumulada: {} a {}", inicio, fim);

        return taxaSelicService.calcularSelicAcumulada(inicio, fim)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Calcula a SELIC para Receita Federal (Lei nº 9.250/1995).
     * Acumulação simples (série 4390) + 1% fixo no mês final.
     *
     * Exemplo:
     * /selic/receita-federal?dataVencimento=2017-04-30&dataPagamento=2025-08-31&valorOriginal=346.13
     */
    @GetMapping("/receita-federal")
    @Operation(summary = "SELIC Receita Federal — Lei 9.250/1995")
    public Mono<ResponseEntity<SelicReceitaCalculoResponse>> getSelicReceitaFederal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataVencimento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPagamento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAtualizacao,
            @RequestParam(required = false, defaultValue = "0") BigDecimal valorOriginal) {

        LocalDate vencimento;
        LocalDate fim;

        if (dataVencimento != null) {
            vencimento = dataVencimento;
            fim = dataPagamento != null ? dataPagamento : dataAtualizacao;
        } else if (dataPagamento != null && dataAtualizacao != null) {
            // Compatibilidade: parâmetros legados (pagamento = vencimento, atualização = fim)
            vencimento = dataPagamento;
            fim = dataAtualizacao;
        } else {
            return Mono.error(new IllegalArgumentException(
                    "Informe dataVencimento e dataPagamento (ou dataPagamento e dataAtualizacao no formato legado)"));
        }

        if (fim == null) {
            return Mono.error(new IllegalArgumentException("Informe a data de pagamento/atualização"));
        }

        log.info("SELIC Receita Federal: vencimento={}, pagamento={}, valor={}", vencimento, fim, valorOriginal);

        return taxaSelicService.calcularSelicReceitaFederal(vencimento, fim, valorOriginal)
                .map(ResponseEntity::ok);
    }

    /**
     * Busca por número de reunião do COPOM.
     */
    @GetMapping("/reuniao/{numero}")
    @Operation(summary = "Taxa por número de reunião do COPOM")
    public Mono<ResponseEntity<TaxaSelic>> getTaxaPorReuniao(@PathVariable Integer numero) {
        return taxaSelicService.buscarPorReuniao(numero)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Força sincronização com BCB (manual).
     */
    @PostMapping("/sync")
    @Operation(summary = "Forçar sincronização com BCB")
    public Mono<ResponseEntity<TaxaSelicService.SyncResult>> forcarSincronizacao() {
        log.info("Sincronização manual solicitada");
        return taxaSelicService.sincronizarCompleto()
                .map(ResponseEntity::ok);
    }

    /**
     * Retorna estatísticas da collection.
     */
    @GetMapping("/stats")
    @Operation(summary = "Estatísticas da collection")
    public Mono<StatsResponse> getStats() {
        return taxaSelicService.contarRegistros()
                .zipWith(taxaSelicService.buscarTaxaVigente().defaultIfEmpty(new TaxaSelic()))
                .map(tuple -> new StatsResponse(
                        tuple.getT1(),
                        tuple.getT2().getMetaSelic(),
                        tuple.getT2().getNumeroReuniaoCopom()));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class StatsResponse {
        private Long totalRegistros;
        private java.math.BigDecimal selicAtual;
        private Integer ultimaReuniao;
    }
}
