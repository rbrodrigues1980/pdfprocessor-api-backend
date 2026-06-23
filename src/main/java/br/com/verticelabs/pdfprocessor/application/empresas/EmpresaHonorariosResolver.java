package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class EmpresaHonorariosResolver {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final EmpresaRepository empresaRepository;
    private final EmpresaPercentualHelper percentualHelper;

    public record HonorariosConfig(
            BigDecimal percentualFracao,
            BigDecimal percentualExibicao,
            String empresaSigla,
            String empresaNome,
            String percentualDescricao) {

        public String formatLabelHonorarios() {
            String pct = percentualExibicao.stripTrailingZeros().toPlainString() + "%";
            if (empresaSigla != null && !empresaSigla.isBlank()) {
                return "Honorários Advocatícios - " + empresaSigla + " - " + pct;
            }
            return "Honorários Advocatícios - Contratual - " + pct;
        }
    }

    public Mono<HonorariosConfig> resolve(Person person) {
        if (person == null || person.getEmpresaId() == null || person.getEmpresaId().isBlank()) {
            return Mono.just(defaultConfig());
        }
        return empresaRepository.findById(person.getEmpresaId())
                .map(empresa -> buildFromEmpresa(empresa, person.getPercentualHonorarioId()))
                .defaultIfEmpty(defaultConfig());
    }

    private HonorariosConfig buildFromEmpresa(Empresa empresa, String percentualId) {
        EmpresaPercentual percentual = percentualHelper.findPercentual(empresa, percentualId).orElse(null);
        if (percentual == null || percentual.getPercentual() == null) {
            return defaultConfig();
        }
        BigDecimal pct = percentual.getPercentual();
        return new HonorariosConfig(
                pct.divide(BigDecimal.valueOf(100), 6, RM),
                pct.setScale(2, RM),
                empresa.getSigla(),
                empresa.getNome(),
                percentual.getDescricao());
    }

    private HonorariosConfig defaultConfig() {
        BigDecimal pct = ExcelResumoGeralHelper.PERCENTUAL_HONORARIOS_DEFAULT
                .multiply(BigDecimal.valueOf(100));
        return new HonorariosConfig(
                ExcelResumoGeralHelper.PERCENTUAL_HONORARIOS_DEFAULT,
                pct.setScale(2, RM),
                null,
                null,
                null);
    }
}
