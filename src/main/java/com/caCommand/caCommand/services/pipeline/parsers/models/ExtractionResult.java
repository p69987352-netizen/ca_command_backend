package com.caCommand.caCommand.services.pipeline.parsers.models;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExtractionResult {
    @Builder.Default
    private Map<String, ExtractedField> fields = new HashMap<>();
    
    private int overallConfidence; // 0 to 100
    
    @Builder.Default
    private List<String> missingFields = new ArrayList<>();
    
    // Rich object preservation (Enterprise v3)
    @Builder.Default
    private com.caCommand.caCommand.services.pipeline.parsers.models.DocumentMetadata metadata = new com.caCommand.caCommand.services.pipeline.parsers.models.DocumentMetadata();
    
    @Builder.Default
    private List<com.caCommand.caCommand.services.pipeline.parsers.models.DeductorEntry> deductors = new ArrayList<>();
    
    @Builder.Default
    private List<com.caCommand.caCommand.services.pipeline.parsers.models.SummaryTableRow> tisRows = new ArrayList<>();
    
    @Builder.Default
    private List<com.caCommand.caCommand.services.pipeline.parsers.models.AisTransaction> aisTransactions = new ArrayList<>();
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    private long processingTimeMs;
    private String parserVersion;
    private String documentVersion;

    public void addField(String key, ExtractedField field) {
        fields.put(key, field);
        recalculateOverallConfidence();
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addError(String error) {
        errors.add(error);
    }

    private void recalculateOverallConfidence() {
        if (fields.isEmpty()) {
            overallConfidence = 0;
            return;
        }
        int total = 0;
        for (ExtractedField f : fields.values()) {
            total += f.getConfidence();
        }
        overallConfidence = total / fields.size();
    }
    
    // Converts the rich map to a simple flat JSON for legacy/AI fallback components
    public Map<String, Object> toSimpleJsonMap() {
        Map<String, Object> simpleMap = new HashMap<>();
        
        // Ensure default structure matches AI fallback structure
        simpleMap.put("salaryIncome", 0.0);
        simpleMap.put("rentIncome", 0.0);
        simpleMap.put("dividendIncome", 0.0);
        simpleMap.put("interestIncome", 0.0);
        simpleMap.put("capitalGains", 0.0);
        simpleMap.put("businessIncome", 0.0);
        simpleMap.put("gstTurnover", 0.0);
        simpleMap.put("tds", 0.0);
        simpleMap.put("totalIncome", 0.0);
        simpleMap.put("panNumber", "");
        simpleMap.put("assesseeName", "");
        simpleMap.put("financialYear", "");

        for (Map.Entry<String, ExtractedField> entry : fields.entrySet()) {
            simpleMap.put(entry.getKey(), entry.getValue().getValue());
        }
        return simpleMap;
    }
}
