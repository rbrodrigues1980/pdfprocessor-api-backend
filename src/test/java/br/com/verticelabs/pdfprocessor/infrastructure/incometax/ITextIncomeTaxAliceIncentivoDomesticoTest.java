package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Alice AC 2016: Imposto devido − total = Contribuição Prev. Empregador Doméstico.
 * Não inventar essa diferença como dedução de incentivo.
 */
@DisplayName("IR — incentivo não copia INSS doméstico (Alice 2016)")
class ITextIncomeTaxAliceIncentivoDomesticoTest {

    private static final String RESUMO_ALICE_2016 = """
            IMPOSTO DEVIDO
            Base de cálculo do imposto
            74.635,62
            Imposto devido
            10.092,47
            Dedução de incentivo
            0,00
            Imposto devido I
            10.092,47
            Contribuição Prev. Empregador Doméstico
            1.093,77
            Imposto devido II
            8.998,70
            Imposto devido RRA
            0,00
            Total do imposto devido
            8.998,70
            """;

    @Test
    @DisplayName("fixture: incentivo fica 0 quando a diferença é o doméstico")
    void fixtureNaoCopiaInssDomesticoComoIncentivo() throws Exception {
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();

        Method corrigir = ITextIncomeTaxServiceImpl.class.getDeclaredMethod(
                "corrigirCamposImpostoDevidoResumo",
                String.class, String.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class);
        corrigir.setAccessible(true);

        Object result = corrigir.invoke(
                service,
                RESUMO_ALICE_2016,
                "COMPLETO",
                new BigDecimal("74635.62"),
                new BigDecimal("10092.47"),
                BigDecimal.ZERO,
                new BigDecimal("10092.47"),
                BigDecimal.ZERO,
                new BigDecimal("1093.77"),
                new BigDecimal("8998.70"),
                null,
                new BigDecimal("8998.70"),
                new BigDecimal("100000.00"),
                new BigDecimal("25000.00"),
                null);

        Method deducaoGetter = result.getClass().getDeclaredMethod("deducaoIncentivo");
        Method iGetter = result.getClass().getDeclaredMethod("impostoDevidoI");
        BigDecimal deducao = (BigDecimal) deducaoGetter.invoke(result);
        BigDecimal impostoI = (BigDecimal) iGetter.invoke(result);

        assertEquals(0, BigDecimal.ZERO.compareTo(deducao == null ? BigDecimal.ZERO : deducao),
                "Incentivo não pode ser 1.093,77 (doméstico); era=" + deducao);
        assertEquals(0, new BigDecimal("10092.47").compareTo(impostoI),
                "Imposto devido I permanece 10.092,47; era=" + impostoI);
    }

    @Test
    @DisplayName("declaração real: incentivo 0, doméstico 1.093,77 e restituição 6.875,58")
    void declaracaoReal2016() {
        IncomeTaxInfo info = IrpfFixtures.parse("alice-2016");

        assertValor("0.00", info.getDeducaoIncentivo());
        assertValor("10092.47", info.getImpostoDevidoI());
        assertValor("1093.77", info.getContribuicaoPrevEmpregadorDomestico());
        assertValor("8998.70", info.getTotalImpostoDevido());
        assertValor("74635.62", info.getBaseCalculoImposto());
        assertValor("6875.58", info.getImpostoRestituir());
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual != null ? atual : BigDecimal.ZERO),
                () -> "esperado " + esperado + ", obtido " + atual);
    }
}
