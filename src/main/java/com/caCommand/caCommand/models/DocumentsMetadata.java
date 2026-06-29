package com.caCommand.caCommand.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentsMetadata {
    @Builder.Default private List<String> sourcesUsed = new ArrayList<>();
    @Builder.Default private boolean aisParsed = false;
    @Builder.Default private boolean tisParsed = false;
    @Builder.Default private boolean form26asParsed = false;
    
    // Extracted directly from documents (for debug/admin view)
    private int deductorCount;
    private int tisRowCount;
    private int aisTransactionCount;
}
