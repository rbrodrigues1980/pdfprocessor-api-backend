package br.com.verticelabs.pdfprocessor.application.selic;

import br.com.verticelabs.pdfprocessor.application.selic.dto.DetalhamentoMes;
import br.com.verticelabs.pdfprocessor.application.selic.dto.SelicReceitaCalculoResponse;
import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import br.com.verticelabs.pdfprocessor.infrastructure.mongodb.SpringDataSelicMensalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calculadora SELIC conforme regra de atualização monetária da Receita Federal
 * (Lei nº 9.250/1995): acumulação simples das taxas mensais (série 4390 BCB),
 * com 1% fixo no mês final do período. Período inclui o mês de vencimento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculadoraSelicReceitaService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal TAXA_ULTIMO_MES = new BigDecimal("1.00");
    private static final DateTimeFormatter MES_ANO_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");

    private final SpringDataSelicMensalRepository selicMensalRepository;

    public Mono<SelicReceitaCalculoResponse> calcular(
            YearMonth mesAnoInicio,
            YearMonth mesAnoFim,
            BigDecimal valorOriginal) {

        BigDecimal valorBase = valorOriginal != null ? valorOriginal : BigDecimal.ZERO;

        if (mesAnoFim.isBefore(mesAnoInicio)) {
            return Mono.just(respostaVazia(mesAnoInicio, mesAnoFim, valorBase));
        }

        return selicMensalRepository.findAllByOrderByAnoDescMesDesc()
                .collectList()
                .map(taxas -> calcularComTaxas(taxas, mesAnoInicio, mesAnoFim, valorBase));
    }

    private SelicReceitaCalculoResponse calcularComTaxas(
            List<SelicMensalEntity> taxasMensais,
            YearMonth mesAnoInicio,
            YearMonth mesAnoFim,
            BigDecimal valorOriginal) {

        Map<String, BigDecimal> mapaTaxas = new HashMap<>();
        for (SelicMensalEntity taxa : taxasMensais) {
            if (taxa.getTaxa() != null) {
                mapaTaxas.put(taxa.getAno() + "-" + taxa.getMes(), taxa.getTaxa());
            }
        }

        BigDecimal somaTaxasMesesAnteriores = BigDecimal.ZERO;
        List<DetalhamentoMes> detalhamento = new ArrayList<>();
        int totalMeses = 0;

        YearMonth mesAtual = mesAnoInicio;
        while (!mesAtual.isAfter(mesAnoFim)) {
            boolean primeiroMes = mesAtual.equals(mesAnoInicio);
            boolean ultimoMes = mesAtual.equals(mesAnoFim);
            BigDecimal taxaMes;

            if (ultimoMes) {
                taxaMes = TAXA_ULTIMO_MES;
            } else {
                String chave = mesAtual.getYear() + "-" + mesAtual.getMonthValue();
                taxaMes = mapaTaxas.getOrDefault(chave, BigDecimal.ZERO);
            }

            BigDecimal taxaAcumuladaExibicao;
            BigDecimal valorAtualizado;
            String memoriaCalculo;

            if (primeiroMes) {
                taxaAcumuladaExibicao = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                valorAtualizado = valorOriginal.setScale(2, RoundingMode.HALF_UP);
                memoriaCalculo = montarMemoriaCalculoInicial(valorOriginal);
            } else {
                taxaAcumuladaExibicao = TAXA_ULTIMO_MES
                        .add(somaTaxasMesesAnteriores)
                        .setScale(2, RoundingMode.HALF_UP);
                valorAtualizado = valorOriginal.compareTo(BigDecimal.ZERO) > 0
                        ? valorOriginal.multiply(fatorDe(taxaAcumuladaExibicao)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                memoriaCalculo = montarMemoriaCalculo(valorOriginal, taxaAcumuladaExibicao);
            }

            detalhamento.add(new DetalhamentoMes(
                    mesAtual.atDay(1).format(MES_ANO_FORMAT),
                    mesAtual.getYear(),
                    mesAtual.getMonthValue(),
                    taxaMes.setScale(2, RoundingMode.HALF_UP),
                    taxaAcumuladaExibicao,
                    valorAtualizado,
                    memoriaCalculo,
                    ultimoMes));

            somaTaxasMesesAnteriores = somaTaxasMesesAnteriores.add(taxaMes);
            totalMeses++;
            mesAtual = mesAtual.plusMonths(1);
        }

        BigDecimal taxaTotal = somaTaxasMesesAnteriores.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fator = fatorDe(taxaTotal).setScale(6, RoundingMode.HALF_UP);
        BigDecimal valorCorrigido = valorOriginal.compareTo(BigDecimal.ZERO) > 0
                ? valorOriginal.multiply(fator).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        log.info(
                "SELIC Receita Federal (Lei 9250/95): {} a {} = {}% ({} meses), valor corrigido={}",
                mesAnoInicio, mesAnoFim, taxaTotal, totalMeses, valorCorrigido);

        return new SelicReceitaCalculoResponse(
                mesAnoInicio,
                mesAnoFim,
                null,
                null,
                valorOriginal.setScale(2, RoundingMode.HALF_UP),
                taxaTotal,
                fator,
                valorCorrigido,
                totalMeses,
                detalhamento);
    }

    private String montarMemoriaCalculoInicial(BigDecimal valorOriginal) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        return moeda.format(valorOriginal);
    }

    private String montarMemoriaCalculo(BigDecimal valorOriginal, BigDecimal taxaAcumulada) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(LOCALE_BR);
        NumberFormat percentual = NumberFormat.getNumberInstance(LOCALE_BR);
        percentual.setMinimumFractionDigits(2);
        percentual.setMaximumFractionDigits(2);
        return moeda.format(valorOriginal) + " x " + percentual.format(taxaAcumulada) + "%";
    }

    private BigDecimal fatorDe(BigDecimal taxaAcumuladaPercentual) {
        return BigDecimal.ONE.add(
                taxaAcumuladaPercentual.divide(CEM, 10, RoundingMode.HALF_UP));
    }

    private SelicReceitaCalculoResponse respostaVazia(
            YearMonth mesAnoInicio,
            YearMonth mesAnoFim,
            BigDecimal valorOriginal) {
        return new SelicReceitaCalculoResponse(
                mesAnoInicio,
                mesAnoFim,
                null,
                null,
                valorOriginal.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP),
                valorOriginal.setScale(2, RoundingMode.HALF_UP),
                0,
                List.of());
    }
}
