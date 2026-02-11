package br.com.verticelabs.pdfprocessor.infrastructure.config;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializador de dados de tributação IRPF.
 * Popula o banco com dados oficiais da Receita Federal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IrTributacaoSeedInitializer {

    private static final String ANUAL = "ANUAL";

    private final IrTributacaoService tributacaoService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTributacao() {
        log.info("Verificando dados de tributação IRPF...");

        // Verificar se já existem dados para 2016 (ANUAL)
        tributacaoService.buscarFaixas(2016, ANUAL)
                .hasElements()
                .subscribe(hasData -> {
                    if (!hasData) {
                        log.info("Inicializando dados de tributação IRPF...");
                        populateAllYears();
                    } else {
                        log.info("Dados de tributação IRPF já existem");
                    }
                });
    }

    private void populateAllYears() {
        // Anos 2016-2022 (mesma tabela)
        for (int ano = 2016; ano <= 2022; ano++) {
            saveTributacao2016a2022(ano);
        }

        // Ano 2023
        saveTributacao2023();

        // Ano 2024
        saveTributacao2024();

        // Ano 2025
        saveTributacao2025();

        log.info("Dados de tributação IRPF inicializados com sucesso!");
    }

    /**
     * Tabela de 2016 a 2022 (Incidência Anual).
     * Fonte:
     * https://www.gov.br/receitafederal/pt-br/assuntos/meu-imposto-de-renda/tabelas/2016
     */
    private void saveTributacao2016a2022(int ano) {
        List<IrTabelaTributacao> faixas = new ArrayList<>();

        // Faixa 1: Até R$ 22.847,76 → Isento
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(1)
                .limiteInferior(BigDecimal.ZERO)
                .limiteSuperior(new BigDecimal("22847.76"))
                .aliquota(BigDecimal.ZERO)
                .deducao(BigDecimal.ZERO)
                .descricao("Isento")
                .build());

        // Faixa 2: De R$ 22.847,77 até R$ 33.919,80 → 7,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(2)
                .limiteInferior(new BigDecimal("22847.77"))
                .limiteSuperior(new BigDecimal("33919.80"))
                .aliquota(new BigDecimal("0.075"))
                .deducao(new BigDecimal("1713.58"))
                .descricao("7,5%")
                .build());

        // Faixa 3: De R$ 33.919,81 até R$ 45.012,60 → 15%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(3)
                .limiteInferior(new BigDecimal("33919.81"))
                .limiteSuperior(new BigDecimal("45012.60"))
                .aliquota(new BigDecimal("0.15"))
                .deducao(new BigDecimal("4257.57"))
                .descricao("15%")
                .build());

        // Faixa 4: De R$ 45.012,61 até R$ 55.976,16 → 22,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(4)
                .limiteInferior(new BigDecimal("45012.61"))
                .limiteSuperior(new BigDecimal("55976.16"))
                .aliquota(new BigDecimal("0.225"))
                .deducao(new BigDecimal("7633.51"))
                .descricao("22,5%")
                .build());

        // Faixa 5: Acima de R$ 55.976,16 → 27,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(5)
                .limiteInferior(new BigDecimal("55976.17"))
                .limiteSuperior(null) // Sem limite superior
                .aliquota(new BigDecimal("0.275"))
                .deducao(new BigDecimal("10432.32"))
                .descricao("27,5%")
                .build());

        // Salvar faixas
        tributacaoService.salvarFaixas(faixas).subscribe();

        // Salvar parâmetros
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .isencao65Anos(new BigDecimal("22847.76"))
                .build();

        tributacaoService.salvarParametros(params).subscribe();

        log.debug("Tributação {} (ANUAL) salva", ano);
    }

    /**
     * Tabela 2023 (Incidência Anual).
     */
    private void saveTributacao2023() {
        int ano = 2023;
        List<IrTabelaTributacao> faixas = new ArrayList<>();

        // Faixa 1: Até R$ 24.511,92 → Isento
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(1)
                .limiteInferior(BigDecimal.ZERO)
                .limiteSuperior(new BigDecimal("24511.92"))
                .aliquota(BigDecimal.ZERO)
                .deducao(BigDecimal.ZERO)
                .descricao("Isento")
                .build());

        // Faixa 2: De R$ 24.511,93 até R$ 33.919,80 → 7,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(2)
                .limiteInferior(new BigDecimal("24511.93"))
                .limiteSuperior(new BigDecimal("33919.80"))
                .aliquota(new BigDecimal("0.075"))
                .deducao(new BigDecimal("1838.39"))
                .descricao("7,5%")
                .build());

        // Faixa 3: De R$ 33.919,81 até R$ 45.012,60 → 15%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(3)
                .limiteInferior(new BigDecimal("33919.81"))
                .limiteSuperior(new BigDecimal("45012.60"))
                .aliquota(new BigDecimal("0.15"))
                .deducao(new BigDecimal("4382.38"))
                .descricao("15%")
                .build());

        // Faixa 4: De R$ 45.012,61 até R$ 55.976,16 → 22,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(4)
                .limiteInferior(new BigDecimal("45012.61"))
                .limiteSuperior(new BigDecimal("55976.16"))
                .aliquota(new BigDecimal("0.225"))
                .deducao(new BigDecimal("7758.32"))
                .descricao("22,5%")
                .build());

        // Faixa 5: Acima de R$ 55.976,16 → 27,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(5)
                .limiteInferior(new BigDecimal("55976.17"))
                .limiteSuperior(null)
                .aliquota(new BigDecimal("0.275"))
                .deducao(new BigDecimal("10557.13"))
                .descricao("27,5%")
                .build());

        tributacaoService.salvarFaixas(faixas).subscribe();

        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .isencao65Anos(new BigDecimal("24511.92"))
                .build();

        tributacaoService.salvarParametros(params).subscribe();

        log.debug("Tributação 2023 (ANUAL) salva");
    }

    /**
     * Tabela 2024 (Incidência Anual).
     */
    private void saveTributacao2024() {
        int ano = 2024;
        List<IrTabelaTributacao> faixas = new ArrayList<>();

        // Faixa 1: Até R$ 26.963,20 → Isento
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(1)
                .limiteInferior(BigDecimal.ZERO)
                .limiteSuperior(new BigDecimal("26963.20"))
                .aliquota(BigDecimal.ZERO)
                .deducao(BigDecimal.ZERO)
                .descricao("Isento")
                .build());

        // Faixa 2: De R$ 26.963,21 até R$ 33.919,80 → 7,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(2)
                .limiteInferior(new BigDecimal("26963.21"))
                .limiteSuperior(new BigDecimal("33919.80"))
                .aliquota(new BigDecimal("0.075"))
                .deducao(new BigDecimal("2022.24"))
                .descricao("7,5%")
                .build());

        // Faixa 3: De R$ 33.919,81 até R$ 45.012,60 → 15%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(3)
                .limiteInferior(new BigDecimal("33919.81"))
                .limiteSuperior(new BigDecimal("45012.60"))
                .aliquota(new BigDecimal("0.15"))
                .deducao(new BigDecimal("4566.23"))
                .descricao("15%")
                .build());

        // Faixa 4: De R$ 45.012,61 até R$ 55.976,16 → 22,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(4)
                .limiteInferior(new BigDecimal("45012.61"))
                .limiteSuperior(new BigDecimal("55976.16"))
                .aliquota(new BigDecimal("0.225"))
                .deducao(new BigDecimal("7942.17"))
                .descricao("22,5%")
                .build());

        // Faixa 5: Acima de R$ 55.976,16 → 27,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(5)
                .limiteInferior(new BigDecimal("55976.17"))
                .limiteSuperior(null)
                .aliquota(new BigDecimal("0.275"))
                .deducao(new BigDecimal("10740.98"))
                .descricao("27,5%")
                .build());

        tributacaoService.salvarFaixas(faixas).subscribe();

        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .isencao65Anos(new BigDecimal("26963.20"))
                .build();

        tributacaoService.salvarParametros(params).subscribe();

        log.debug("Tributação 2024 (ANUAL) salva");
    }

    /**
     * Tabela 2025 (Incidência Anual).
     */
    private void saveTributacao2025() {
        int ano = 2025;
        List<IrTabelaTributacao> faixas = new ArrayList<>();

        // Faixa 1: Até R$ 28.467,20 → Isento
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(1)
                .limiteInferior(BigDecimal.ZERO)
                .limiteSuperior(new BigDecimal("28467.20"))
                .aliquota(BigDecimal.ZERO)
                .deducao(BigDecimal.ZERO)
                .descricao("Isento")
                .build());

        // Faixa 2: De R$ 28.467,21 até R$ 33.919,80 → 7,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(2)
                .limiteInferior(new BigDecimal("28467.21"))
                .limiteSuperior(new BigDecimal("33919.80"))
                .aliquota(new BigDecimal("0.075"))
                .deducao(new BigDecimal("2135.04"))
                .descricao("7,5%")
                .build());

        // Faixa 3: De R$ 33.919,81 até R$ 45.012,60 → 15%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(3)
                .limiteInferior(new BigDecimal("33919.81"))
                .limiteSuperior(new BigDecimal("45012.60"))
                .aliquota(new BigDecimal("0.15"))
                .deducao(new BigDecimal("4679.03"))
                .descricao("15%")
                .build());

        // Faixa 4: De R$ 45.012,61 até R$ 55.976,16 → 22,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(4)
                .limiteInferior(new BigDecimal("45012.61"))
                .limiteSuperior(new BigDecimal("55976.16"))
                .aliquota(new BigDecimal("0.225"))
                .deducao(new BigDecimal("8054.97"))
                .descricao("22,5%")
                .build());

        // Faixa 5: Acima de R$ 55.976,16 → 27,5%
        faixas.add(IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .faixa(5)
                .limiteInferior(new BigDecimal("55976.17"))
                .limiteSuperior(null)
                .aliquota(new BigDecimal("0.275"))
                .deducao(new BigDecimal("10853.78"))
                .descricao("27,5%")
                .build());

        tributacaoService.salvarFaixas(faixas).subscribe();

        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .anoCalendario(ano)
                .tipoIncidencia(ANUAL)
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .isencao65Anos(new BigDecimal("28467.20"))
                .build();

        tributacaoService.salvarParametros(params).subscribe();

        log.debug("Tributação 2025 (ANUAL) salva");
    }
}
