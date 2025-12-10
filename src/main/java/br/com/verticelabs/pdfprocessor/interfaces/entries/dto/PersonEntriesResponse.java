package br.com.verticelabs.pdfprocessor.interfaces.entries.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonEntriesResponse {
    private String cpf;
    private Long totalEntries;
    private List<EntryResponse> entries;
}

