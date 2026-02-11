package br.com.verticelabs.pdfprocessor.infrastructure.bcb;

import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Client para API SGS do BCB que retorna taxas SELIC mensais.
 * Série 4390: Taxa de juros - Selic acumulada no mês.
 * 
 * Esta é a taxa utilizada pela Receita Federal para correção monetária.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BcbSelicMensalClient {

    // Série 4390: Taxa de juros - Selic acumulada no mês
    private static final String BCB_SGS_URL = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados?formato=json";

    private final WebClient.Builder webClientBuilder;

    /**
     * Busca todas as taxas SELIC mensais do BCB.
     */
    public Flux<SelicMensalEntity> fetchSelicMensal() {
        log.info("Buscando taxas SELIC mensais do BCB SGS (série 4390)...");

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(BCB_SGS_URL)
                .retrieve()
                .bodyToFlux(BcbSgsItem.class)
                .map(this::mapToEntity)
                .doOnComplete(() -> log.info("Taxas SELIC mensais carregadas com sucesso"))
                .doOnError(e -> log.error("Erro ao buscar SELIC mensal: {}", e.getMessage()));
    }

    /**
     * Mapeia item do BCB SGS para entidade.
     */
    private SelicMensalEntity mapToEntity(BcbSgsItem item) {
        // Data vem no formato "01/MM/YYYY"
        String[] partes = item.getData().split("/");
        int mes = Integer.parseInt(partes[1]);
        int ano = Integer.parseInt(partes[2]);

        return SelicMensalEntity.builder()
                .ano(ano)
                .mes(mes)
                .taxa(new BigDecimal(item.getValor().replace(",", ".")))
                .dataReferencia(item.getData())
                .syncedAt(LocalDateTime.now())
                .build();
    }

    /**
     * DTO para resposta do BCB SGS.
     */
    @Data
    public static class BcbSgsItem {
        @JsonProperty("data")
        private String data; // "01/MM/YYYY"

        @JsonProperty("valor")
        private String valor; // "1.23"
    }
}
