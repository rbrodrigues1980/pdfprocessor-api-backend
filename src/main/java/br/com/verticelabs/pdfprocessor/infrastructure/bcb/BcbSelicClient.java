package br.com.verticelabs.pdfprocessor.infrastructure.bcb;

import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Client para API do Banco Central do Brasil.
 * Busca histórico de taxas de juros (SELIC).
 * 
 * Endpoint: https://www.bcb.gov.br/api/servico/sitebcb/historicotaxasjuros
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BcbSelicClient {

    private static final String BCB_API_URL = "https://www.bcb.gov.br/api/servico/sitebcb/historicotaxasjuros";

    private final WebClient.Builder webClientBuilder;

    /**
     * Busca todo o histórico de taxas SELIC do BCB.
     */
    public Flux<TaxaSelic> fetchHistoricoSelic() {
        log.info("Buscando histórico de taxas SELIC do BCB...");

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(BCB_API_URL)
                .retrieve()
                .bodyToMono(BcbResponse.class)
                .flatMapMany(response -> {
                    if (response == null || response.getConteudo() == null) {
                        log.warn("Resposta vazia do BCB");
                        return Flux.empty();
                    }

                    log.info("Recebidos {} registros do BCB", response.getConteudo().size());

                    // Log primeiro item para debug
                    if (!response.getConteudo().isEmpty()) {
                        BcbTaxaItem first = response.getConteudo().get(0);
                        log.debug("Primeiro item: reuniao={}, metaSelic={}",
                                first.getNumeroReuniaoCopom(), first.getMetaSelic());
                    }

                    return Flux.fromIterable(response.getConteudo())
                            .filter(item -> item.getNumeroReuniaoCopom() != null)
                            .map(this::mapToTaxaSelic);
                })
                .doOnError(e -> log.error("Erro ao buscar dados do BCB: {}", e.getMessage(), e))
                .onErrorResume(e -> Flux.empty());
    }

    /**
     * Mapeia resposta do BCB para entidade TaxaSelic.
     */
    private TaxaSelic mapToTaxaSelic(BcbTaxaItem item) {
        TaxaSelic taxa = TaxaSelic.builder()
                .numeroReuniaoCopom(item.getNumeroReuniaoCopom() != null
                        ? item.getNumeroReuniaoCopom().intValue()
                        : null)
                .dataReuniaoCopom(parseInstant(item.getDataReuniaoCopom()))
                .reuniaoExtraordinaria(item.getReuniaoExtraordinaria())
                .vies(item.getVies())
                .usoMetaSelic(item.getUsoMetaSelic())
                .dataInicioVigencia(parseInstant(item.getDataInicioVigencia()))
                .dataFimVigencia(parseInstant(item.getDataFimVigencia()))
                .metaSelic(item.getMetaSelic())
                .taxaTban(item.getTaxaTban())
                .taxaSelicEfetivaVigencia(item.getTaxaSelicEfetivaVigencia())
                .taxaSelicEfetivaAnualizada(item.getTaxaSelicEfetivaAnualizada())
                .decisaoMonocraticaPres(item.getDescisaoMonocraticaPres())
                .build();

        log.trace("Mapeado: reuniao={}, meta={}", taxa.getNumeroReuniaoCopom(), taxa.getMetaSelic());
        return taxa;
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            log.warn("Erro ao parsear data: {}", dateStr);
            return null;
        }
    }

    // ========== DTOs internos ==========

    @Data
    public static class BcbResponse {
        private List<BcbTaxaItem> conteudo;
    }

    @Data
    public static class BcbTaxaItem {
        @JsonProperty("NumeroReuniaoCopom")
        private Double numeroReuniaoCopom;

        @JsonProperty("ReuniaoExtraordinaria")
        private Boolean reuniaoExtraordinaria;

        @JsonProperty("DataReuniaoCopom")
        private String dataReuniaoCopom;

        @JsonProperty("Vies")
        private String vies;

        @JsonProperty("UsoMetaSelic")
        private Boolean usoMetaSelic;

        @JsonProperty("DataInicioVigencia")
        private String dataInicioVigencia;

        @JsonProperty("DataFimVigencia")
        private String dataFimVigencia;

        @JsonProperty("MetaSelic")
        private BigDecimal metaSelic;

        @JsonProperty("TaxaTban")
        private BigDecimal taxaTban;

        @JsonProperty("TaxaSelicEfetivaVigencia")
        private BigDecimal taxaSelicEfetivaVigencia;

        @JsonProperty("TaxaSelicEfetivaAnualizada")
        private BigDecimal taxaSelicEfetivaAnualizada;

        @JsonProperty("descisaoMonocraticaPres")
        private Boolean descisaoMonocraticaPres;
    }
}
