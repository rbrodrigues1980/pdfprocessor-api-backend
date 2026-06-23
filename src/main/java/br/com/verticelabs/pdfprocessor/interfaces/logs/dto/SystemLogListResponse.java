package br.com.verticelabs.pdfprocessor.interfaces.logs.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
public record SystemLogListResponse(
        List<SystemLogEntryResponse> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious
) {
}
