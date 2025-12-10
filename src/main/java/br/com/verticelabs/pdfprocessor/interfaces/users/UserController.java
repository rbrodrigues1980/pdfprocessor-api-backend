package br.com.verticelabs.pdfprocessor.interfaces.users;

import br.com.verticelabs.pdfprocessor.application.users.*;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.interfaces.users.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final GetUserByIdUseCase getUserByIdUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final ActivateUserUseCase activateUserUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("📥 POST /api/v1/users - Criar usuário: {}", request.getEmail());
        return createUserUseCase.execute(
                request.getNome(),
                request.getEmail(),
                request.getSenha(),
                request.getRoles(),
                request.getTenantId(),
                request.getTelefone())
                .flatMap(user -> {
                    // Buscar tenant se houver tenantId
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> userMapper.toResponse(user, tenant))
                                .switchIfEmpty(Mono.just(userMapper.toResponse(user)))
                                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body((Object) response));
                    }
                    return Mono
                            .just(ResponseEntity.status(HttpStatus.CREATED).body((Object) userMapper.toResponse(user)));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao criar usuário: {}", e.getMessage(), e);

                    // Tratar erros de validação
                    if (e instanceof WebExchangeBindException) {
                        WebExchangeBindException bindException = (WebExchangeBindException) e;
                        String validationErrors = bindException.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .collect(java.util.stream.Collectors.joining(", "));
                        Map<String, String> errorResponse = new HashMap<>();
                        errorResponse.put("message", "Erro de validação: " + validationErrors);
                        errorResponse.put("error", "ValidationError");
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body((Object) errorResponse));
                    }

                    // Retornar mensagem de erro mais detalhada
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "Erro ao criar usuário");
                    errorResponse.put("error", e.getClass().getSimpleName());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body((Object) errorResponse));
                });
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<UserListResponse>> listUsers(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("📥 GET /api/v1/users - Listar usuários (page={}, size={})", page, size);

        return listUsersUseCase.execute(tenantId, role, ativo, email, nome, page, size)
                .flatMap(result -> {
                    // Converter Users para UserResponses
                    List<UserResponse> userResponses = result.users().stream()
                            .map(userMapper::toResponse)
                            .collect(Collectors.toList());

                    UserListResponse response = UserListResponse.builder()
                            .content(userResponses)
                            .totalElements(result.total())
                            .totalPages(result.totalPages())
                            .currentPage(result.page())
                            .pageSize(result.size())
                            .hasNext(result.page() < result.totalPages() - 1)
                            .hasPrevious(result.page() > 0)
                            .build();

                    return Mono.just(ResponseEntity.ok(response));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao listar usuários: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<UserResponse>> getUserById(@PathVariable String id) {
        log.info("📥 GET /api/v1/users/{} - Buscar usuário", id);

        return getUserByIdUseCase.execute(id)
                .flatMap(user -> {
                    // Buscar tenant se houver tenantId
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> userMapper.toResponse(user, tenant))
                                .switchIfEmpty(Mono.just(userMapper.toResponse(user)))
                                .map(ResponseEntity::ok);
                    }
                    return Mono.just(ResponseEntity.ok(userMapper.toResponse(user)));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao buscar usuário: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<UserResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        log.info("📥 PUT /api/v1/users/{} - Atualizar usuário", id);

        return updateUserUseCase.execute(
                id,
                request.getNome(),
                request.getEmail(),
                request.getRoles(),
                request.getTelefone(),
                request.getAtivo())
                .flatMap(user -> {
                    // Buscar tenant se houver tenantId
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> userMapper.toResponse(user, tenant))
                                .switchIfEmpty(Mono.just(userMapper.toResponse(user)))
                                .map(ResponseEntity::ok);
                    }
                    return Mono.just(ResponseEntity.ok(userMapper.toResponse(user)));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao atualizar usuário: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    if (e.getMessage() != null && e.getMessage().contains("já está em uso")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<UserResponse>> deactivateUser(@PathVariable String id) {
        log.info("📥 DELETE /api/v1/users/{} - Desativar usuário", id);

        return deactivateUserUseCase.execute(id)
                .flatMap(user -> {
                    // Buscar tenant se houver tenantId
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> userMapper.toResponse(user, tenant))
                                .switchIfEmpty(Mono.just(userMapper.toResponse(user)))
                                .map(ResponseEntity::ok);
                    }
                    return Mono.just(ResponseEntity.ok(userMapper.toResponse(user)));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao desativar usuário: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    if (e.getMessage() != null
                            && (e.getMessage().contains("último") || e.getMessage().contains("si mesmo"))) {
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<UserResponse>> activateUser(@PathVariable String id) {
        log.info("📥 POST /api/v1/users/{}/activate - Reativar usuário", id);

        return activateUserUseCase.execute(id)
                .flatMap(user -> {
                    // Buscar tenant se houver tenantId
                    if (user.getTenantId() != null) {
                        return tenantRepository.findById(user.getTenantId())
                                .map(tenant -> userMapper.toResponse(user, tenant))
                                .switchIfEmpty(Mono.just(userMapper.toResponse(user)))
                                .map(ResponseEntity::ok);
                    }
                    return Mono.just(ResponseEntity.ok(userMapper.toResponse(user)));
                })
                .onErrorResume(e -> {
                    log.error("❌ Erro ao reativar usuário: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<Map<String, String>>> changePassword(
            @PathVariable String id,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("📥 PUT /api/v1/users/{}/password - Alterar senha", id);

        return changePasswordUseCase.execute(id, request.getSenhaAtual(), request.getNovaSenha())
                .then(Mono.fromCallable(() -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Senha alterada com sucesso");
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(e -> {
                    log.error("❌ Erro ao alterar senha: {}", e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("não encontrado")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    if (e.getMessage() != null && e.getMessage().contains("incorreta")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @GetMapping("/tenants/{tenantId}/users")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<TenantUsersResponse>> listTenantUsers(
            @PathVariable String tenantId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("📥 GET /api/v1/tenants/{}/users - Listar usuários do tenant", tenantId);

        return listUsersUseCase.execute(tenantId, role, ativo, null, null, page, size)
                .flatMap(result -> tenantRepository.findById(tenantId)
                        .map(tenant -> {
                            List<UserResponse> userResponses = result.users().stream()
                                    .map(userMapper::toResponse)
                                    .collect(Collectors.toList());

                            TenantUsersResponse response = TenantUsersResponse.builder()
                                    .tenantId(tenantId)
                                    .tenantNome(tenant.getNome())
                                    .content(userResponses)
                                    .totalElements(result.total())
                                    .totalPages(result.totalPages())
                                    .build();

                            return ResponseEntity.ok(response);
                        })
                        .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build())))
                .onErrorResume(e -> {
                    log.error("❌ Erro ao listar usuários do tenant: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
