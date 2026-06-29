package com.caCommand.caCommand.services.pipeline.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.services.pipeline.parsers.dictionary.KeywordDictionary;
import com.caCommand.caCommand.services.pipeline.parsers.extractors.TableScanner;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractedField;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import com.caCommand.caCommand.services.pipeline.parsers.validators.AmountValidator;
import com.caCommand.caCommand.services.pipeline.parsers.validators.PanValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AisParser implements DocumentParser {

    private static final String PARSER_VERSION = "AIS_PARSER_V1.0";

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.AIS;
    }

    @Override
    public ExtractionResult parse(DocumentContext context) {
        long startTime = System.currentTimeMillis();
        log.info("AIS Parser Started");

        ExtractionResult.ExtractionResultBuilder builder = ExtractionResult.builder()
                .parserVersion(PARSER_VERSION)
                .documentVersion("Unknown");

        ExtractionResult result = builder.build();

        String rawText = context.getRawText();
        if (rawText == null || rawText.isBlank()) {
            result.addError("Raw text is empty");
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }

        // Phase 1: Normalize
        long normalizeStart = System.currentTimeMillis();
        String normalizedText = normalize(rawText);
        log.info("AIS Normalize phase took {} ms", (System.currentTimeMillis() - normalizeStart));

        // Phase 2 & 3: Scan and Extract
        long extractStart = System.currentTimeMillis();

        // 1. PAN Extraction
        extractPan(normalizedText, result);

        // 2. Financial Amounts Extraction
        extractAmount(normalizedText, "salaryIncome", KeywordDictionary.SALARY_KEYWORDS, result);
        extractAmount(normalizedText, "interestIncome", KeywordDictionary.INTEREST_KEYWORDS, result);
        extractAmount(normalizedText, "dividendIncome", KeywordDictionary.DIVIDEND_KEYWORDS, result);
        extractAmount(normalizedText, "capitalGains", KeywordDictionary.CAPITAL_GAIN_KEYWORDS, result);
        extractAmount(normalizedText, "businessIncome", KeywordDictionary.BUSINESS_RECEIPTS_KEYWORDS, result);

        // 3. TDS Extraction
        extractTds(normalizedText, result);

        log.info("AIS Extraction phase took {} ms", (System.currentTimeMillis() - extractStart));

        // Phase 4: Validate
        long validateStart = System.currentTimeMillis();
        AmountValidator.validateAmount(result, "salaryIncome", getDoubleValue(result, "salaryIncome"));
        AmountValidator.validateAmount(result, "interestIncome", getDoubleValue(result, "interestIncome"));
        AmountValidator.validateAmount(result, "dividendIncome", getDoubleValue(result, "dividendIncome"));
        AmountValidator.validateAmount(result, "tds", getDoubleValue(result, "tds"));
        log.info("AIS Validation phase took {} ms", (System.currentTimeMillis() - validateStart));

        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        log.info("AIS Parser Completed in {} ms with {}% confidence", result.getProcessingTimeMs(), result.getOverallConfidence());
        
        return result;
    }

    private String normalize(String text) {
        // Remove excessive whitespace, normalize newlines
        return text.replaceAll("\\r\\n", "\n").replaceAll(" +", " ").trim();
    }

    private void extractPan(String text, ExtractionResult result) {
        // Look for PAN keyword
        int bestIndex = -1;
        for (String kw : KeywordDictionary.PAN_KEYWORDS) {
            int index = text.toLowerCase().indexOf(kw.toLowerCase());
            if (index != -1 && (bestIndex == -1 || index < bestIndex)) {
                bestIndex = index;
            }
        }

        int confidence = 0;
        String extractedPan = null;
        
        if (bestIndex != -1) {
            String substring = text.substring(bestIndex);
            Matcher m = Pattern.compile("([A-Z]{5}[0-9]{4}[A-Z]{1})").matcher(substring);
            if (m.find()) {
                extractedPan = m.group(1);
                confidence = 90; // Keyword proximity match
            }
        } else {
            // Global scan
            Matcher m = Pattern.compile("([A-Z]{5}[0-9]{4}[A-Z]{1})").matcher(text);
            if (m.find()) {
                extractedPan = m.group(1);
                confidence = 60; // Global regex fallback
            }
        }

        if (extractedPan != null) {
            if (PanValidator.isValid(extractedPan)) {
                confidence = Math.min(100, confidence + 10);
            } else {
                result.addWarning("Found PAN format but failed validation: " + extractedPan);
                confidence = 0;
            }
            if (confidence > 0) {
                result.addField("panNumber", ExtractedField.of(extractedPan, confidence, "PAN Section", "PanExtractor"));
            }
        }
    }

    private void extractAmount(String text, String fieldName, java.util.List<String> keywords, ExtractionResult result) {
        Optional<Double> amountOpt = TableScanner.scanNextAmountAfterKeyword(text, keywords);
        if (amountOpt.isPresent()) {
            Double amount = amountOpt.get();
            int confidence = 95; // Table scan match is highly reliable
            result.addField(fieldName, ExtractedField.of(amount, confidence, "Table Content", "AmountExtractor"));
        } else {
            // Document doesn't have this section. We won't add it to fields to avoid penalizing average confidence.
            // toSimpleJsonMap() will populate missing fields with 0.
        }
    }

    private void extractTds(String text, ExtractionResult result) {
        // Look for TDS sections or simply scan for "Total TDS Deducted" / "TDS"
        Optional<Double> amountOpt = TableScanner.scanNextAmountAfterKeyword(text, java.util.Arrays.asList("Total TDS", "TDS Deducted", "Tax Deducted at Source", "TDS"));
        if (amountOpt.isPresent()) {
            result.addField("tds", ExtractedField.of(amountOpt.get(), 90, "TDS Section", "TDSExtractor"));
        } else {
            // Missing TDS means 0 TDS.
        }
    }

    private Double getDoubleValue(ExtractionResult result, String key) {
        if (result.getFields().containsKey(key)) {
            Object val = result.getFields().get(key).getValue();
            if (val instanceof Double) return (Double) val;
        }
        return null;
    }
}
