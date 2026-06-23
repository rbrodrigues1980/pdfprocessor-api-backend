package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.DeveloperRepasseMapper;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListDeveloperRepasseUseCase {

    private final DeveloperRepasseRepository repasseRepository;
    private final DeveloperRepasseMapper mapper;

    public Mono<DeveloperRepasseListResponse> execute(RepasseListFilter filter, int page, int size) {
        return requireSuperAdmin()
                .then(repasseRepository.countByFilters(filter))
                .flatMap(total -> repasseRepository.findByFilters(filter, page, size)
                        .map(mapper::toResponse)
                        .collectList()
                        .map(content -> buildResponse(content, total, page, size)));
    }

    private DeveloperRepasseListResponse buildResponse(
            List<DeveloperRepasseResponse> content,
            long total,
            int page,
            int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return DeveloperRepasseListResponse.builder()
                .content(content)
                .totalElements(total)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode gerenciar repasses")));
    }
}
