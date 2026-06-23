package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "empresas")
@CompoundIndex(name = "tenant_sigla_unique", def = "{'tenantId': 1, 'sigla': 1}", unique = true)
public class Empresa {
    @Id
    private String id;

    private String tenantId;

    private String nome;

    private String sigla;

    private String cnpj;

    @Builder.Default
    private List<EmpresaPercentual> percentuais = new ArrayList<>();

    @Builder.Default
    private Boolean ativo = true;

    private Instant createdAt;

    private Instant updatedAt;
}
