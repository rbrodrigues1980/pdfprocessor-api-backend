package br.com.verticelabs.pdfprocessor.infrastructure.bcb;

import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client para API SGS do BCB que retorna taxas SELIC mensais.
 * Série 4390: Taxa de juros - Selic acumulada no mês.
 *
 * <p>O BCB exige {@code dataInicial} e {@code dataFinal}; sem elas a API responde
 * {@code Content-Type: text/html} mesmo com corpo JSON, o que quebra o decoder do WebClient.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BcbSelicMensalClient {

    private static final String BCB_SGS_BASE_URL = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados";
    private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalDate SERIE_INICIO = LocalDate.of(1986, 1, 1);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Busca taxas SELIC mensais do BCB (série 4390) de 1986 até a data atual.
     */
    public Flux<SelicMensalEntity> fetchSelicMensal() {
        String dataInicial = SERIE_INICIO.format(BCB_DATE);
        String dataFinal = LocalDate.now().format(BCB_DATE);
        String url = UriComponentsBuilder.fromHttpUrl(BCB_SGS_BASE_URL)
                .queryParam("formato", "json")
                .queryParam("dataInicial", dataInicial)
                .queryParam("dataFinal", dataFinal)
                .build()
                .toUriString();

        log.info("Buscando taxas SELIC mensais do BCB SGS (série 4390) de {} a {}...",
                dataInicial, dataFinal);

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(this::parseResponse)
                .doOnComplete(() -> log.info("Taxas SELIC mensais carregadas com sucesso"))
                .doOnError(e -> log.error("Erro ao buscar SELIC mensal: {}", e.getMessage()));
    }

    private Flux<SelicMensalEntity> parseResponse(String body) {
        if (body == null || body.isBlank()) {
            log.warn("Resposta vazia do BCB SGS (série 4390)");
            return Flux.empty();
        }

        String trimmed = body.stripLeading();
        if (!trimmed.startsWith("[")) {
            log.error("Resposta inesperada do BCB SGS (esperado JSON array): {}",
                    trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
            return Flux.error(new IllegalStateException(
                    "BCB SGS retornou conteúdo inválido (não é JSON array)"));
        }

        try {
            List<BcbSgsItem> items = objectMapper.readValue(body, new TypeReference<>() {});
            return Flux.fromIterable(items).map(this::mapToEntity);
        } catch (Exception e) {
            return Flux.error(new IllegalStateException("Falha ao parsear JSON do BCB SGS", e));
        }
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
