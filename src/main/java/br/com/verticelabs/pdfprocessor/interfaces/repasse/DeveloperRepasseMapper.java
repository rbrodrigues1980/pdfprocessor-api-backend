package br.com.verticelabs.pdfprocessor.interfaces.repasse;

import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseResponse;
import org.springframework.stereotype.Component;

@Component
public class DeveloperRepasseMapper {

    public DeveloperRepasseResponse toResponse(DeveloperRepasse repasse) {
        return DeveloperRepasseResponse.builder()
                .id(repasse.getId())
                .personId(repasse.getPersonId())
                .tenantId(repasse.getTenantId())
                .tenantNome(repasse.getTenantNome())
                .cpf(repasse.getCpf())
                .nomeCliente(repasse.getNomeCliente())
                .mesReferencia(repasse.getMesReferencia())
                .validadoEm(repasse.getValidadoEm())
                .valorUnitario(repasse.getValorUnitario())
                .status(repasse.getStatus())
                .pagoEm(repasse.getPagoEm())
                .pagoPor(repasse.getPagoPor())
                .pagoParaNome(repasse.getPagoParaNome())
                .pagoParaEmail(repasse.getPagoParaEmail())
                .pagoParaCelular(repasse.getPagoParaCelular())
                .formaPagamento(repasse.getFormaPagamento())
                .referenciaPagamento(repasse.getReferenciaPagamento())
                .observacao(repasse.getObservacao())
                .createdAt(repasse.getCreatedAt())
                .build();
    }
}
