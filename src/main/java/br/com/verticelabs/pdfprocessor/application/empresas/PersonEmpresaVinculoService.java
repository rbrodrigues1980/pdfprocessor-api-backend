package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonEmpresaVinculoInvalidoException;
import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PersonEmpresaVinculoService {

    private final EmpresaRepository empresaRepository;
    private final EmpresaPercentualHelper percentualHelper;

    public Mono<Void> validateAndApply(String tenantId, String empresaId, String percentualHonorarioId,
                                       Person person) {
        boolean hasEmpresa = empresaId != null && !empresaId.isBlank();
        boolean hasPercentual = percentualHonorarioId != null && !percentualHonorarioId.isBlank();

        if (!hasEmpresa && !hasPercentual) {
            person.setEmpresaId(null);
            person.setPercentualHonorarioId(null);
            return Mono.empty();
        }

        if (hasEmpresa != hasPercentual) {
            return Mono.error(new PersonEmpresaVinculoInvalidoException(
                    "Empresa e percentual de honorários devem ser informados juntos"));
        }

        return empresaRepository.findByTenantIdAndId(tenantId, empresaId)
                .switchIfEmpty(Mono.error(new PersonEmpresaVinculoInvalidoException(
                        "Empresa não encontrada: " + empresaId)))
                .flatMap(empresa -> {
                    EmpresaPercentual percentual = percentualHelper.findPercentual(empresa, percentualHonorarioId)
                            .orElseThrow(() -> new PersonEmpresaVinculoInvalidoException(
                                    "Percentual de honorários não encontrado na empresa selecionada"));

                    if (!percentualHelper.isPercentualVigente(percentual, LocalDate.now())) {
                        return Mono.error(new PersonEmpresaVinculoInvalidoException(
                                "Percentual selecionado não está vigente ou está inativo"));
                    }

                    person.setEmpresaId(empresaId);
                    person.setPercentualHonorarioId(percentualHonorarioId);
                    return Mono.empty();
                });
    }
}
