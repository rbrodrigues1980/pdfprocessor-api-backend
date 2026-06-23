package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.EmpresaPercentualDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmpresaPercentualHelper {

    public Optional<EmpresaPercentual> findPercentual(Empresa empresa, String percentualId) {
        if (empresa == null || percentualId == null || empresa.getPercentuais() == null) {
            return Optional.empty();
        }
        return empresa.getPercentuais().stream()
                .filter(p -> percentualId.equals(p.getId()))
                .findFirst();
    }

    public boolean isPercentualVigente(EmpresaPercentual percentual, LocalDate referenceDate) {
        if (percentual == null || !Boolean.TRUE.equals(percentual.getAtivo())) {
            return false;
        }
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();
        if (percentual.getVigenciaInicio() != null && ref.isBefore(percentual.getVigenciaInicio())) {
            return false;
        }
        return percentual.getVigenciaFim() == null || !ref.isAfter(percentual.getVigenciaFim());
    }

    public void validatePercentualDto(EmpresaPercentualDTO dto) {
        if (dto.getVigenciaFim() != null && dto.getVigenciaInicio() != null
                && dto.getVigenciaFim().isBefore(dto.getVigenciaInicio())) {
            throw new IllegalArgumentException("Data fim da vigência não pode ser anterior à data início");
        }
    }

    public List<EmpresaPercentual> mapPercentuaisFromRequest(List<EmpresaPercentualDTO> dtos, List<EmpresaPercentual> existing) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(dto -> {
            validatePercentualDto(dto);
            String id = dto.getId();
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            return EmpresaPercentual.builder()
                    .id(id)
                    .descricao(dto.getDescricao().trim())
                    .percentual(dto.getPercentual())
                    .vigenciaInicio(dto.getVigenciaInicio())
                    .vigenciaFim(dto.getVigenciaFim())
                    .ativo(dto.getAtivo() == null ? Boolean.TRUE : dto.getAtivo())
                    .build();
        }).toList();
    }

    public String normalizeSigla(String sigla) {
        if (sigla == null || sigla.isBlank()) {
            return null;
        }
        return sigla.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return null;
        }
        return cnpj.replaceAll("\\D", "");
    }
}
