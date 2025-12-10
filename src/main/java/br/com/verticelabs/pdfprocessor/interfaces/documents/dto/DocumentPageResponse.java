package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentPageResponse {
    private String documentId;
    private List<PageInfo> pages;

    @Data
    @Builder
    public static class PageInfo {
        private Integer page;
        private String origem;
    }
}

