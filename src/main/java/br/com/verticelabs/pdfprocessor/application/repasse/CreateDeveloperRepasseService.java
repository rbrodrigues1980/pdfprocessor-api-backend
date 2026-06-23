package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateDeveloperRepasseService {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter MES_REFERENCIA = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DeveloperRepasseRepository repasseRepository;
    private final TenantRepository tenantRepository;
    private final RepasseValorService repasseValorService;

    public Mono<DeveloperRepasse> createForValidatedPerson(Person person) {
        return repasseRepository.existsByPersonId(person.getId())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return repasseRepository.findByPersonId(person.getId());
                    }
                    return buildRepasse(person);
                });
    }

    private Mono<DeveloperRepasse> buildRepasse(Person person) {
        Instant validadoEm = person.getValidadoEm() != null ? person.getValidadoEm() : Instant.now();
        String mesReferencia = MES_REFERENCIA.format(validadoEm.atZone(ZONE_BR));

        return tenantRepository.findById(person.getTenantId())
                .defaultIfEmpty(Tenant.builder()
                        .id(person.getTenantId())
                        .nome("Tenant desconhecido")
                        .build())
                .flatMap(tenant -> repasseValorService.getValorForInstant(validadoEm)
                        .flatMap(valor -> {
                            Instant now = Instant.now();
                            DeveloperRepasse repasse = DeveloperRepasse.builder()
                                    .personId(person.getId())
                                    .tenantId(person.getTenantId())
                                    .tenantNome(tenant.getNome())
                                    .cpf(person.getCpf())
                                    .nomeCliente(person.getNome())
                                    .mesReferencia(mesReferencia)
                                    .validadoEm(validadoEm)
                                    .valorUnitario(valor)
                                    .status(RepasseStatus.PENDENTE)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();

                            return repasseRepository.save(repasse)
                                    .doOnSuccess(r -> log.info(
                                            "Repasse criado: personId={}, status={}",
                                            person.getId(), r.getStatus()));
                        }));
    }
}
