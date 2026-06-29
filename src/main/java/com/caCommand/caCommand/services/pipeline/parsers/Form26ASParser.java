package com.caCommand.caCommand.services.pipeline.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.services.pipeline.parsers.dictionary.KeywordDictionary;
import com.caCommand.caCommand.services.pipeline.parsers.extractors.DeductorTableParser;
import com.caCommand.caCommand.services.pipeline.parsers.extractors.SectionSplitter;
import com.caCommand.caCommand.services.pipeline.parsers.models.DeductorEntry;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractedField;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import com.caCommand.caCommand.services.pipeline.parsers.validators.AmountValidator;
import com.caCommand.caCommand.services.pipeline.parsers.validators.PanValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Form 26AS Parser v2.0 - Deductor-based Table Parsing.
 * 
 * Flow:
 *   1. Normalize text
 *   2. SectionSplitter → HEADER | PART_A | PART_B | PART_C | PART_D
 *   3. DeductorTableParser → List<DeductorEntry> for each part
 *   4. totalTDS = sum(all deductor.tdsDeducted)
 *   5. Extract Advance Tax, Self-Assessment Tax from Part C
 *   6. Extract Refund from Part D
 *   7. Validate
 * 
 * KEY FIX: TDS is now sum of all deductor rows, NOT first regex match after keyword.
 */
@Slf4j
@Component
public class Form26ASParser implements DocumentParser {

    private static final String PARSER_VERSION = "FORM26AS_PARSER_V2.0";

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.FORM26AS;
    }

    @Override
    public ExtractionResult parse(DocumentContext context) {
        long startTime = System.currentTimeMillis();
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║       FORM 26AS PARSER v2.0 - TABLE BASED       ║");
        log.info("╚══════════════════════════════════════════════════╝");

        ExtractionResult result = ExtractionResult.builder()
                .parserVersion(PARSER_VERSION)
                .documentVersion("Unknown")
                .build();

        String rawText = context.getRawText();
        if (rawText == null || rawText.isBlank()) {
            result.addError("Raw text is empty");
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            return result;
        }

        // ===== PHASE 1: NORMALIZE =====
        String normalizedText = normalize(rawText);
        log.info("Phase 1 - Normalize: {} chars", normalizedText.length());

        // ===== PHASE 2: SECTION SPLIT =====
        long phaseStart = System.currentTimeMillis();
        Map<String, String> sections = SectionSplitter.split26AS(normalizedText);
        long splitTime = System.currentTimeMillis() - phaseStart;
        log.info("Phase 2 - Section Split: {} ms, {} sections found", splitTime, sections.size());
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            log.info("  ├─ {}: {} chars", entry.getKey(), entry.getValue().length());
        }

        // ===== PHASE 3: EXTRACT PAN FROM HEADER =====
        phaseStart = System.currentTimeMillis();
        String headerText = sections.getOrDefault("HEADER", normalizedText);
        extractPan(headerText, result);
        extractName26AS(headerText, result);
        extractAssessmentYear(normalizedText, result);
        long headerTime = System.currentTimeMillis() - phaseStart;
        log.info("Phase 3 - Header Parse: {} ms", headerTime);
        log.info("  ├─ PAN:  {}", getFieldValue(result, "panNumber"));
        log.info("  ├─ Name: {}", getFieldValue(result, "assesseeName"));
        log.info("  └─ AY:   {}", getFieldValue(result, "assessmentYear"));

        // ===== PHASE 4: DEDUCTOR TABLE PARSING (TDS) =====
        phaseStart = System.currentTimeMillis();
        // Get Part A text (or full text if no sections found)
        String tdsText = sections.getOrDefault("PART_A", sections.getOrDefault("FULL", normalizedText));
        
        List<DeductorEntry> deductors = DeductorTableParser.parse(tdsText);
        result.setDeductors(deductors); 
        long tableTime = System.currentTimeMillis() - phaseStart;
        double totalTDS = DeductorTableParser.computeTotalTDS(deductors);

        log.info("Phase 4 - Deductor Table Parse: {} deductors in {} ms", deductors.size(), tableTime);
        if (!deductors.isEmpty()) {
            log.info("┌──────────────────────────────────┬────────┬──────────────┬────────────┬────────────┐");
            log.info("│ Deductor Name                    │ Section│ Amount Paid  │ TDS        │ Deposited  │");
            log.info("├──────────────────────────────────┼────────┼──────────────┼────────────┼────────────┤");
            for (DeductorEntry d : deductors) {
                log.info(String.format("│ %-32s │ %-6s │ %12s │ %10s │ %10s │",
                        truncate(d.getName(), 32),
                        d.getSection() != null ? d.getSection() : "-",
                        formatAmount(d.getAmountPaid()),
                        formatAmount(d.getTdsDeducted()),
                        formatAmount(d.getTdsDeposited())));
            }
            log.info("├──────────────────────────────────┼────────┼──────────────┼────────────┼────────────┤");
            log.info(String.format("│ TOTAL TDS                        │        │              │ %10s │            │", formatAmount(totalTDS)));
            log.info("└──────────────────────────────────┴────────┴──────────────┴────────────┴────────────┘");
        }

        // Deductors are preserved in ExtractionResult as raw list.

        // ===== PHASE 5: ADVANCE TAX & SELF-ASSESSMENT (Part C) =====
        phaseStart = System.currentTimeMillis();
        String partC = sections.getOrDefault("PART_C", "");
        if (!partC.isEmpty()) {
            extractTaxPaid(partC, result);
        }
        // Also try full text if Part C wasn't found
        if (partC.isEmpty()) {
            extractTaxPaid(normalizedText, result);
        }
        long taxTime = System.currentTimeMillis() - phaseStart;
        log.info("Phase 5 - Tax Paid Parse: {} ms", taxTime);
        log.info("  ├─ Advance Tax:      {}", formatAmount(getDoubleField(result, "advanceTaxPaid")));
        log.info("  └─ Self-Assessment:  {}", formatAmount(getDoubleField(result, "selfAssessmentTaxPaid")));

        // ===== PHASE 6: REFUND (Part D) =====
        String partD = sections.getOrDefault("PART_D", "");
        if (!partD.isEmpty()) {
            extractRefund(partD, result);
        }

        // ===== PHASE 7: VALIDATION =====
        AmountValidator.validateAmount(result, "tds", totalTDS);
        
        // If PAN was extracted, it's a valid 26AS even if there's 0 TDS
        boolean hasPan = result.getFields().containsKey("panNumber");
        result.setOverallConfidence(hasPan ? 95 : (deductors.isEmpty() ? 50 : 90));
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        // ===== PRETTY PRINT FINAL RESULT =====
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║         26AS EXTRACTION RESULT v2.0             ║");
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("║ PAN:              {:<30}║", getFieldValue(result, "panNumber"));
        log.info("║ Name:             {:<30}║", getFieldValue(result, "assesseeName"));
        log.info("║ AY:               {:<30}║", getFieldValue(result, "assessmentYear"));
        log.info("║──────────────────────────────────────────────────║");
        log.info("║ Total TDS:        {:>14}                ║", formatAmount(totalTDS));
        log.info("║ Advance Tax:      {:>14}                ║", formatAmount(getDoubleField(result, "advanceTaxPaid")));
        log.info("║ Self-Assessment:  {:>14}                ║", formatAmount(getDoubleField(result, "selfAssessmentTaxPaid")));
        log.info("║ Refund:           {:>14}                ║", formatAmount(getDoubleField(result, "refundAmount")));
        log.info("║ Deductors Found:  {:>14}                ║", deductors.size());
        log.info("║──────────────────────────────────────────────────║");
        log.info("║ Confidence:       {:>14}%               ║", result.getOverallConfidence());
        log.info("║ Time:             {:>14} ms             ║", result.getProcessingTimeMs());
        log.info("║ Parser:           {:<30}║", PARSER_VERSION);
        log.info("╚══════════════════════════════════════════════════╝");

        return result;
    }

    // ========== EXTRACTORS ==========

    private void extractPan(String text, ExtractionResult result) {
        for (String kw : KeywordDictionary.PAN_KEYWORDS) {
            int index = text.toLowerCase().indexOf(kw.toLowerCase());
            if (index != -1) {
                String area = text.substring(index, Math.min(text.length(), index + 200));
                Matcher m = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]").matcher(area);
                if (m.find() && PanValidator.isValid(m.group())) {
                    putField(result, "panNumber", m.group(), 100);
                    return;
                }
            }
        }
        Matcher m = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b").matcher(text);
        if (m.find() && PanValidator.isValid(m.group())) {
            putField(result, "panNumber", m.group(), 60);
        }
    }

    private void extractName26AS(String text, ExtractionResult result) {
        // 26AS: Look for "Name" or "Name of the Assessee"
        Pattern p = Pattern.compile("(?i)(?:name of (?:the )?assessee|name)[:\\s]+([A-Za-z][A-Za-z0-9\\s.-]+?)\\s*(?:PAN|Address|Flat|Assessment|\\d|$)", Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() > 2 && name.length() < 100) {
                putField(result, "assesseeName", name, 85);
            }
        }
    }

    private void extractAssessmentYear(String text, ExtractionResult result) {
        Matcher m = Pattern.compile("(?i)(?:assessment year|a\\.?y\\.?)\\s*:?\\s*(\\d{4}[\\-–]\\d{2,4})").matcher(text);
        if (m.find()) {
            putField(result, "assessmentYear", m.group(1), 95);
        }
    }

    private void extractTaxPaid(String text, ExtractionResult result) {
        // Look for Advance Tax amount
        Pattern advPattern = Pattern.compile("(?i)advance\\s*tax[^\\d]*((?:\\d{1,2},)*\\d{2,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)");
        Matcher m = advPattern.matcher(text);
        if (m.find()) {
            double val = parseAmount(m.group(1));
            if (val > 0) putField(result, "advanceTaxPaid", val, 85);
        }

        // Self-Assessment Tax
        Pattern satPattern = Pattern.compile("(?i)self[\\-\\s]*assessment\\s*tax[^\\d]*((?:\\d{1,2},)*\\d{2,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)");
        m = satPattern.matcher(text);
        if (m.find()) {
            double val = parseAmount(m.group(1));
            if (val > 0) putField(result, "selfAssessmentTaxPaid", val, 85);
        }
    }

    private void extractRefund(String text, ExtractionResult result) {
        Pattern refundPattern = Pattern.compile("(?i)refund[^\\d]*((?:\\d{1,2},)*\\d{2,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)");
        Matcher m = refundPattern.matcher(text);
        if (m.find()) {
            double val = parseAmount(m.group(1));
            if (val > 0) putField(result, "refundAmount", val, 80);
        }
    }

    // ========== HELPERS ==========

    private String normalize(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n").replaceAll(" +", " ").trim();
    }

    private void putField(ExtractionResult result, String key, Object value, int confidence) {
        result.getFields().put(key, ExtractedField.builder()
                .value(value instanceof Double ? String.valueOf(value) : value)
                .confidence(confidence)
                .extractorUsed(PARSER_VERSION)
                .build());
    }

    private String getFieldValue(ExtractionResult result, String key) {
        ExtractedField f = result.getFields().get(key);
        if (f == null || f.getValue() == null) return "(not found)";
        return f.getValue().toString();
    }

    private double getDoubleField(ExtractionResult result, String key) {
        ExtractedField f = result.getFields().get(key);
        if (f == null || f.getValue() == null) return 0.0;
        try { return Double.parseDouble(f.getValue().toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private double parseAmount(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Double.parseDouble(s.replace(",", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String formatAmount(double amount) {
        if (amount == 0) return "0";
        return String.format("%,.0f", amount);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }
}
