package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MarkRepasseBulkPaidRequest extends MarkRepassePaidRequest {
    private List<String> ids;
}
