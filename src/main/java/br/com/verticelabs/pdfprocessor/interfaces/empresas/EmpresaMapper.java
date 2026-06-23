package br.com.verticelabs.pdfprocessor.interfaces.empresas;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.EmpresaPercentualDTO;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.EmpresaResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EmpresaMapper {

    public EmpresaResponse toResponse(Empresa empresa) {
        if (empresa == null) {
            return null;
        }
        return EmpresaResponse.builder()
                .id(empresa.getId())
                .tenantId(empresa.getTenantId())
                .nome(empresa.getNome())
                .sigla(empresa.getSigla())
                .cnpj(empresa.getCnpj())
                .percentuais(toPercentualDtos(empresa.getPercentuais()))
                .ativo(empresa.getAtivo())
                .createdAt(empresa.getCreatedAt())
                .updatedAt(empresa.getUpdatedAt())
                .build();
    }

    private List<EmpresaPercentualDTO> toPercentualDtos(List<EmpresaPercentual> percentuais) {
        if (percentuais == null) {
            return List.of();
        }
        List<EmpresaPercentualDTO> dtos = new ArrayList<>();
        for (EmpresaPercentual p : percentuais) {
            EmpresaPercentualDTO dto = new EmpresaPercentualDTO();
            dto.setId(p.getId());
            dto.setDescricao(p.getDescricao());
            dto.setPercentual(p.getPercentual());
            dto.setVigenciaInicio(p.getVigenciaInicio());
            dto.setVigenciaFim(p.getVigenciaFim());
            dto.setAtivo(p.getAtivo());
            dtos.add(dto);
        }
        return dtos;
    }
}
