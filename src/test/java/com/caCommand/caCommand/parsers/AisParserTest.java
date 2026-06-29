package com.caCommand.caCommand.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.services.pipeline.parsers.AisParser;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AisParserTest {

    private final AisParser parser = new AisParser();

    @Test
    public void testAisExtraction_ValidDocument() {
        String mockAisText = """
                Annual Information Statement (AIS)
                Name of the Assessee: ROHIT KUMAR
                PAN of the Assessee: ABCDE1234F
                Assessment Year: 2024-25
                
                Part B
                Information Category    Processed Value
                Income from Salary      12,50,000
                Interest from Deposit   45,000.50
                Dividend Income         5,000
                Sale of Securities      0
                
                TDS Deducted            1,20,000
                """;

        DocumentContext context = DocumentContext.builder()
                .rawText(mockAisText)
                .type(DocumentType.AIS)
                .build();

        ExtractionResult result = parser.parse(context);

        assertTrue(result.getOverallConfidence() > 80);
        
        assertEquals("ABCDE1234F", result.getFields().get("panNumber").getValue());
        assertEquals(1250000.0, result.getFields().get("salaryIncome").getValue());
        assertEquals(45000.50, result.getFields().get("interestIncome").getValue());
        assertEquals(5000.0, result.getFields().get("dividendIncome").getValue());
        assertEquals(120000.0, result.getFields().get("tds").getValue());
    }
    
    @Test
    public void testAisExtraction_CorruptedDocument_WithFallback() {
        String mockAisText = "Some garbled text without any tabular data or PAN.";
        
        DocumentContext context = DocumentContext.builder()
                .rawText(mockAisText)
                .type(DocumentType.AIS)
                .build();

        ExtractionResult result = parser.parse(context);
        
        // Confidence should be low due to missing exact keywords and PAN
        assertTrue(result.getOverallConfidence() < 80);
        assertEquals(0.0, result.toSimpleJsonMap().get("salaryIncome"));
    }
}
