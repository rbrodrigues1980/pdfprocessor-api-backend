package br.com.verticelabs.pdfprocessor.infrastructure.config;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Corrige/atualiza tabelas IRPF em bancos já populados (seed só roda quando 2016 está vazio).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class IrTributacaoMigrationInitializer {

    private static final String ANUAL = "ANUAL";
    private static final BigDecimal LIMITE_ISENTO_2026 = new BigDecimal("29145.60");

    private final IrTributacaoService tributacaoService;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        migrate2015()
                .then(migrate2026())
                .then(migrateLimiteInssDomestico())
                .subscribe(
                        v -> log.info("Migração de tributação IRPF concluída"),
                        e -> log.error("Erro na migração de tributação IRPF", e));
    }

    /** Preenche limiteInssDomestico em parâmetros ANUAL já existentes (2015–2026). */
    private Mono<Void> migrateLimiteInssDomestico() {
        Mono<Void> chain = Mono.empty();
        for (int ano = 2015; ano <= 2026; ano++) {
            final int anoFinal = ano;
            chain = chain.then(
                    tributacaoService.buscarParametros(anoFinal, ANUAL)
                            .flatMap(existing -> {
                                existing.setLimiteInssDomestico(
                                        IrTributacaoParametrosUtil.limiteInssDomestico(anoFinal));
                                return tributacaoService.salvarParametros(existing);
                            })
                            .then());
        }
        return chain;
    }

    private Mono<Void> migrate2015() {
        return tributacaoService.buscarFaixas(2015, ANUAL)
                .hasElements()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    log.info("Inserindo tabela IRPF ano-calendário 2015 (Ex. 2016)...");
                    return tributacaoService.salvarFaixas(faixas2015())
                            .then(tributacaoService.salvarParametros(parametros2015()))
                            .then();
                });
    }

    private Mono<Void> migrate2026() {
        return tributacaoService.buscarFaixas(2026, ANUAL)
                .filter(f -> f.getFaixa() != null && f.getFaixa() == 1)
                .next()
                .flatMap(faixa1 -> {
                    BigDecimal limite = faixa1.getLimiteSuperior();
                    if (limite != null && limite.compareTo(LIMITE_ISENTO_2026) == 0) {
                        return aplicarParametrosReducao2026().then();
                    }
                    log.info("Corrigindo tabela IRPF ano-calendário 2026 (Ex. 2027)...");
                    return tributacaoService.deleteFaixasOnly(2026, ANUAL)
                            .thenMany(tributacaoService.salvarFaixas(faixas2026()))
                            .then(aplicarParametros2026())
                            .then();
                })
                .switchIfEmpty(
                        tributacaoService.salvarFaixas(faixas2026())
                                .then(aplicarParametros2026())
                                .then());
    }

    private Mono<IrParametrosAnuais> aplicarParametros2026() {
        return tributacaoService.buscarParametros(2026, ANUAL)
                .flatMap(existing -> tributacaoService.salvarParametros(mergeParametros2026(existing)))
                .switchIfEmpty(tributacaoService.salvarParametros(parametros2026()));
    }

    private Mono<IrParametrosAnuais> aplicarParametrosReducao2026() {
        return tributacaoService.buscarParametros(2026, ANUAL)
                .flatMap(existing -> {
                    if (Boolean.TRUE.equals(existing.getReducaoAnualAtiva())) {
                        return Mono.just(existing);
                    }
                    return tributacaoService.salvarParametros(mergeParametros2026(existing));
                });
    }

    private IrParametrosAnuais mergeParametros2026(IrParametrosAnuais existing) {
        IrParametrosAnuais base = parametros2026();
        base.setId(existing.getId());
        base.setCreatedAt(existing.getCreatedAt());
        return base;
    }

    private List<IrTabelaTributacao> faixas2015() {
        int ano = 2015;
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(ano, 1, "0", "22499.13", "0", "0", "Isento"));
        faixas.add(faixa(ano, 2, "22499.14", "33477.72", "0.075", "1687.43", "7,5%"));
        faixas.add(faixa(ano, 3, "33477.73", "44476.74", "0.15", "4198.26", "15%"));
        faixas.add(faixa(ano, 4, "44476.75", "55373.55", "0.225", "7534.02", "22,5%"));
        faixas.add(faixa(ano, 5, "55373.56", null, "0.275", "10302.70", "27,5%"));
        return faixas;
    }

    private IrParametrosAnuais parametros2015() {
        return IrParametrosAnuais.builder()
                .anoCalendario(2015)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2015))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .isencao65Anos(new BigDecimal("22499.13"))
                .build();
    }

    private List<IrTabelaTributacao> faixas2026() {
        int ano = 2026;
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(ano, 1, "0", "29145.60", "0", "0", "Isento"));
        faixas.add(faixa(ano, 2, "29145.61", "33919.80", "0.075", "2185.92", "7,5%"));
        faixas.add(faixa(ano, 3, "33919.81", "45012.60", "0.15", "4729.91", "15%"));
        faixas.add(faixa(ano, 4, "45012.61", "55976.16", "0.225", "8105.85", "22,5%"));
        faixas.add(faixa(ano, 5, "55976.17", null, "0.275", "10904.66", "27,5%"));
        return faixas;
    }

    private IrParametrosAnuais parametros2026() {
        return IrParametrosAnuais.builder()
                .anoCalendario(2026)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2026))
                .limiteDescontoSimplificado(new BigDecimal("17640.00"))
                .isencao65Anos(new BigDecimal("29145.60"))
                .reducaoAnualAtiva(true)
                .reducaoRendimentoLimiteIsencao(new BigDecimal("60000.00"))
                .reducaoMaximaCompleta(new BigDecimal("2694.15"))
                .reducaoConstanteLinear(new BigDecimal("8429.73"))
                .reducaoCoeficienteLinear(new BigDecimal("0.095575"))
                .reducaoRendimentoLimiteSuperior(new BigDecimal("88200.00"))
                .build();
    }

    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded, String desc) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .descricao(desc)
                .build();
    }
}
