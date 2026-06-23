package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepassePaidRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RepassePaymentApplier {

    public void apply(DeveloperRepasse repasse, String userId, MarkRepassePaidRequest request) {
        MarkRepassePaidRequest req = request != null ? request : new MarkRepassePaidRequest();
        Instant now = Instant.now();

        String pagoParaNome = trimToNull(req.getPagoParaNome());
        if (pagoParaNome == null) {
            throw new IllegalArgumentException("Informe o nome de quem recebeu o pagamento");
        }

        repasse.setStatus(RepasseStatus.PAGO);
        repasse.setPagoEm(req.getPagoEm() != null ? req.getPagoEm() : now);
        repasse.setPagoPor(userId);
        repasse.setPagoParaNome(pagoParaNome);
        repasse.setPagoParaEmail(trimToNull(req.getPagoParaEmail()));
        repasse.setPagoParaCelular(trimToNull(req.getPagoParaCelular()));
        repasse.setFormaPagamento(trimToNull(req.getFormaPagamento()));
        repasse.setReferenciaPagamento(trimToNull(req.getReferenciaPagamento()));
        repasse.setObservacao(trimToNull(req.getObservacao()));
        repasse.setUpdatedAt(now);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
