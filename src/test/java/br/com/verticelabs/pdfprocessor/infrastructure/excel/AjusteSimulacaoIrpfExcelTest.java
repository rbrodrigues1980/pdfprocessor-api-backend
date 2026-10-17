package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.AjusteSimulacaoIrpf;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AjusteSimulacaoIrpfExcelTest {

    @Test
    void bloco2MostraLinhaVermelhaEBloco1Nao() throws Exception {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2019")
                .exercicio("2020")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTitularPJ(new BigDecimal("100000.00"))
                .rendimentosTributaveisTotal(new BigDecimal("100000.00"))
                .impostoRetidoFonteTitular(new BigDecimal("18127.98"))
                .impostoPagoTotal(new BigDecimal("18127.98"))
                .ajusteSimulacaoRendimentosPjTitular(new BigDecimal("120000.00"))
                .ajusteSimulacaoImpostoDepositoJudicialCivel(new BigDecimal("6423.85"))
                .build();

        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        ExcelIrpfDeducoesResumoHelper deducoes = new ExcelIrpfDeducoesResumoHelper(motor, new IrPagamentosDeducaoAggregator());
        ExcelResumoGeralHelper resumo = new ExcelResumoGeralHelper(motor, mapper);
        ConsolidationExcelServiceImpl service = novoService(motor, mapper, deducoes, resumo);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet entregue = workbook.createSheet("entregue");
            Sheet simulacao = workbook.createSheet("simulacao");
            Person person = Person.builder().nome("LEONIDIO").cpf("19591250525").build();

            invocar(service, "addBlocoConformeDeclaracaoCompleta",
                    entregue, person, data, 0,
                    style(workbook), style(workbook), style(workbook), style(workbook),
                    style(workbook), style(workbook), style(workbook), style(workbook));
            IrParametrosAnuais params = IrParametrosAnuais.builder()
                    .deducaoDependente(new BigDecimal("2275.08"))
                    .limiteInstrucao(new BigDecimal("3561.50"))
                    .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                    .build();
            invocar(service, "addBlocoSimulacaoCompletaPlanilha",
                    simulacao, person, "2019", data, BigDecimal.ZERO, null,
                    Map.of("2019", List.of(IrTabelaTributacao.builder()
                            .anoCalendario(2019)
                            .tipoIncidencia("ANUAL")
                            .faixa(1)
                            .limiteInferior(BigDecimal.ZERO)
                            .aliquota(BigDecimal.ZERO)
                            .deducao(BigDecimal.ZERO)
                            .descricao("Isento")
                            .build())),
                    Map.of("2019", params), 0,
                    style(workbook), style(workbook), style(workbook), style(workbook),
                    style(workbook), style(workbook), style(workbook), style(workbook), style(workbook));

            Set<String> textosEntregue = textos(entregue);
            Set<String> textosSimulacao = textos(simulacao);

            assertFalse(textosEntregue.contains(AjusteSimulacaoIrpf.ROTULO_IMPOSTO_DEPOSITO), textosEntregue::toString);
            assertTrue(contemNumero(textosEntregue, "100000"), textosEntregue::toString);
            assertFalse(contemNumero(textosEntregue, "120000"), textosEntregue::toString);

            assertTrue(textosSimulacao.contains(AjusteSimulacaoIrpf.ROTULO_IMPOSTO_DEPOSITO), textosSimulacao::toString);
            assertTrue(contemNumero(textosSimulacao, "6423.85"), textosSimulacao::toString);
            assertTrue(contemNumero(textosSimulacao, "24551.83"), textosSimulacao::toString);
            assertTrue(contemNumero(textosSimulacao, "120000"), textosSimulacao::toString);
        }
    }

    private static org.apache.poi.ss.usermodel.CellStyle style(XSSFWorkbook workbook) {
        return workbook.createCellStyle();
    }

    private static ConsolidationExcelServiceImpl novoService(
            IrSimuladorMotorService motor,
            ExcelIrpfSimulacaoMapper mapper,
            ExcelIrpfDeducoesResumoHelper deducoes,
            ExcelResumoGeralHelper resumo) throws Exception {
        Constructor<?> ctor = ConsolidationExcelServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] tipos = ctor.getParameterTypes();
        for (int i = 0; i < tipos.length; i++) {
            if (tipos[i].equals(IrCalculoProgressivoService.class)) {
                args[i] = new IrCalculoProgressivoService();
            } else if (tipos[i].equals(IrSimuladorMotorService.class)) {
                args[i] = motor;
            } else if (tipos[i].equals(ExcelIrpfSimulacaoMapper.class)) {
                args[i] = mapper;
            } else if (tipos[i].equals(ExcelIrpfDeducoesResumoHelper.class)) {
                args[i] = deducoes;
            } else if (tipos[i].equals(ExcelResumoGeralHelper.class)) {
                args[i] = resumo;
            }
        }
        return (ConsolidationExcelServiceImpl) ctor.newInstance(args);
    }

    private static void invocar(Object alvo, String nome, Object... args) throws Exception {
        Method metodo = null;
        for (Method candidato : alvo.getClass().getDeclaredMethods()) {
            if (candidato.getName().equals(nome) && candidato.getParameterCount() == args.length) {
                metodo = candidato;
                break;
            }
        }
        if (metodo == null) {
            throw new NoSuchMethodException(nome);
        }
        metodo.setAccessible(true);
        try {
            metodo.invoke(alvo, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception causa) {
                throw causa;
            }
            throw ex;
        }
    }

    private static boolean contemNumero(Set<String> textos, String numero) {
        String digitos = numero.replace(".", "");
        return textos.stream().anyMatch(texto -> {
            String soDigitos = texto.replaceAll("[^0-9]", "");
            return soDigitos.equals(digitos) || soDigitos.equals(digitos + "00");
        });
    }

    private static Set<String> textos(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        Set<String> textos = new HashSet<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                String texto = formatter.formatCellValue(cell);
                if (texto != null && !texto.isBlank()) {
                    textos.add(texto.replace("\u00a0", "").replace(".", "").replace(",", ".").trim());
                    textos.add(formatter.formatCellValue(cell).trim());
                }
            }
        }
        return textos;
    }
}
