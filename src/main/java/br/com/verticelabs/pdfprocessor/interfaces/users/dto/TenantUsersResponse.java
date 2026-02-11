package br.com.verticelabs.pdfprocessor.interfaces.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUsersResponse {
    private String tenantId;
    private String tenantNome;
    private List<UserResponse> content;
    private Long totalElements;
    private Integer totalPages;
}

