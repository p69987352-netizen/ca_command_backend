package com.caCommand.caCommand.services.pipeline.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.services.pipeline.parsers.dictionary.KeywordDictionary;
import com.caCommand.caCommand.services.pipeline.parsers.extractors.SectionSplitter;
import com.caCommand.caCommand.services.pipeline.parsers.extractors.SummaryTableParser;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractedField;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;
import com.caCommand.caCommand.services.pipeline.parsers.models.SummaryTableRow;
import com.caCommand.caCommand.services.pipeline.parsers.validators.AmountValidator;
import com.caCommand.caCommand.services.pipeline.parsers.validators.PanValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TIS Parser v2.0 - Section-based, Table-parsing Enterprise Architecture.
 * 
 * Flow:
 *   1. Normalize text
 *   2. SectionSplitter → HEADER | SUMMARY | ANNEXURE
 *   3. Header Parser → PAN, Name, Financial Year
 *   4. SummaryTableParser → List<SummaryTableRow> (ONLY from SUMMARY section)
 *   5. CategoryDictionary mapping → TaxProfile fields
 *   6. Validation
 *   7. Return ExtractionResult with per-field confidence
 * 
 * KEY: Parser NEVER touches Annexure for financial amounts. No duplicate counting.
 */
@Slf4j
@Component
public class TisParser implements DocumentParser {

    private static final String PARSER_VERSION = "TIS_PARSER_V2.0";

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.TIS;
    }

    @Override
    public ExtractionResult parse(DocumentContext context) {
        long startTime = System.currentTimeMillis();
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║         TIS PARSER v2.0 - SECTION BASED         ║");
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
        long phaseStart = System.currentTimeMillis();
        String normalizedText = normalize(rawText);
        long normalizeTime = System.currentTimeMillis() - phaseStart;
        log.info("Phase 1 - Normalize: {} chars in {} ms", normalizedText.length(), normalizeTime);

        // ===== PHASE 2: SECTION SPLIT =====
        phaseStart = System.currentTimeMillis();
        Map<String, String> sections = SectionSplitter.splitTIS(normalizedText);
        String headerText = sections.getOrDefault("HEADER", "");
        String summaryText = sections.getOrDefault("SUMMARY", "");
        String annexureText = sections.getOrDefault("ANNEXURE", "");
        long splitTime = System.currentTimeMillis() - phaseStart;
        
        log.info("Phase 2 - Section Split: {} ms", splitTime);
        log.info("  ├─ HEADER:   {} chars", headerText.length());
        log.info("  ├─ SUMMARY:  {} chars", summaryText.length());
        log.info("  └─ ANNEXURE: {} chars (IGNORED for extraction)", annexureText.length());

        // ===== PHASE 3: HEADER PARSING (PAN, Name, FY) =====
        phaseStart = System.currentTimeMillis();
        // Search both header AND full text for identity fields (they may appear in various locations)
        String searchText = headerText.isEmpty() ? normalizedText : headerText + "\n" + summaryText;
        extractPan(searchText, result);
        extractName(searchText, result);
        extractFinancialYear(searchText, result);
        long headerTime = System.currentTimeMillis() - phaseStart;
        
        log.info("Phase 3 - Header Parse: {} ms", headerTime);
        log.info("  ├─ PAN:  {}", getFieldValue(result, "panNumber"));
        log.info("  ├─ Name: {}", getFieldValue(result, "assesseeName"));
        log.info("  └─ FY:   {}", getFieldValue(result, "financialYear"));

        // ===== PHASE 4: SUMMARY TABLE PARSING =====
        phaseStart = System.currentTimeMillis();
        List<SummaryTableRow> rows = SummaryTableParser.parse(summaryText);
        long tableTime = System.currentTimeMillis() - phaseStart;
        
        log.info("Phase 4 - Summary Table Parse: {} rows in {} ms", rows.size(), tableTime);
        log.info("┌────┬─────────────────────────────────────────────┬────────────────┬────────────────┐");
        log.info("│ Sr │ Category                                    │ Processed      │ Accepted       │");
        log.info("├────┼─────────────────────────────────────────────┼────────────────┼────────────────┤");
        for (SummaryTableRow row : rows) {
            log.info(String.format("│ %2s │ %-43s │ %14s │ %14s │",
                    row.getSrNo(),
                    truncate(row.getCategory(), 43),
                    formatAmount(row.getProcessedValue()),
                    formatAmount(row.getAcceptedValue())));
        }
        log.info("└────┴─────────────────────────────────────────────┴────────────────┴────────────────┘");

        result.setTisRows(rows);

        // ===== PHASE 6: COUNT CAPITAL GAINS LINES IN ANNEXURE =====
        int cgLineCount = countCGLines(annexureText);
        result.getFields().put("capitalGainsLineCount", ExtractedField.builder()
                .value(String.valueOf(cgLineCount))
                .confidence(100)
                .extractorUsed(PARSER_VERSION)
                .build());

        // ===== SET CONFIDENCE =====
        result.setOverallConfidence(rows.isEmpty() ? 50 : 95);
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        // ===== PRETTY PRINT FINAL RESULT =====
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║           TIS EXTRACTION RESULT v2.0            ║");
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("║ PAN:           {:<34}║", getFieldValue(result, "panNumber"));
        log.info("║ Name:          {:<34}║", getFieldValue(result, "assesseeName"));
        log.info("║ FY:            {:<34}║", getFieldValue(result, "financialYear"));
        log.info("║──────────────────────────────────────────────────║");
        log.info(String.format("║ CG Lines:      %14s                  ║", cgLineCount));
        log.info(String.format("║ Confidence:    %14s%%                 ║", result.getOverallConfidence()));
        log.info(String.format("║ Time:          %14s ms               ║", result.getProcessingTimeMs()));
        log.info(String.format("║ Parser:        %-34s║", PARSER_VERSION));
        log.info("╠══════════════════════════════════════════════════╣");
        log.info("╚══════════════════════════════════════════════════╝");

        return result;
    }

    // ========== CATEGORY MAPPING ==========

    /**
     * Maps parsed summary table rows to ExtractionResult fields using CategoryDictionary.
     * Interest = sum of all interest categories (savings + deposit + refund).
     */
    private void mapCategoriesToFields(List<SummaryTableRow> rows, ExtractionResult result) {
        // Accumulators for sub-categories
        double interestTotal = 0;
        double capitalGainsTotal = 0;

        for (SummaryTableRow row : rows) {
            String category = row.getCategory().toLowerCase().trim();
            double value = row.getBestValue();

            if (value <= 0) continue;

            // SALARY
            if (KeywordDictionary.matchesCategory(category, KeywordDictionary.SALARY_ALIASES)) {
                putField(result, "salaryIncome", value, row.getConfidence());
            }
            // INTEREST (accumulate all sub-types)
            else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.INTEREST_ALIASES)) {
                interestTotal += value;
                log.info("  Interest sub-category: '{}' = {} (running total: {})", row.getCategory(), formatAmount(value), formatAmount(interestTotal));
            }
            // DIVIDEND
            else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.DIVIDEND_ALIASES)) {
                putField(result, "dividendIncome", value, row.getConfidence());
            }
            // RENT
            else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.RENT_ALIASES)) {
                putField(result, "rentalIncome", value, row.getConfidence());
            }
            // CAPITAL GAINS (accumulate - may have Sale of Securities + Sale of Land)
            else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.CAPITAL_GAIN_ALIASES)) {
                capitalGainsTotal += value;
            }
            // BUSINESS / GST
            else if (KeywordDictionary.matchesCategory(category, KeywordDictionary.BUSINESS_ALIASES)) {
                if (category.contains("gst")) {
                    putField(result, "gstTurnover", value, row.getConfidence());
                    // GST Turnover is also business income
                    putField(result, "businessIncome", value, row.getConfidence());
                } else if (category.contains("purchase")) {
                    // GST Purchases — store separately, don't count as income
                    putField(result, "gstPurchases", value, row.getConfidence());
                } else {
                    putField(result, "businessIncome", value, row.getConfidence());
                }
            }
            // OTHER / UNMATCHED
            else {
                log.debug("  Unmapped category: '{}' = {}", row.getCategory(), formatAmount(value));
            }
        }

        // Store accumulated totals
        if (interestTotal > 0) {
            putField(result, "interestIncome", interestTotal, 95);
        }
        if (capitalGainsTotal > 0) {
            putField(result, "capitalGains", capitalGainsTotal, 90);
        }
    }

    // ========== HEADER EXTRACTORS ==========

    private void extractPan(String text, ExtractionResult result) {
        // First try near PAN keywords
        for (String kw : KeywordDictionary.PAN_KEYWORDS) {
            int index = text.toLowerCase().indexOf(kw.toLowerCase());
            if (index != -1) {
                String searchArea = text.substring(index, Math.min(text.length(), index + 200));
                Matcher m = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]").matcher(searchArea);
                if (m.find() && PanValidator.isValid(m.group())) {
                    putField(result, "panNumber", m.group(), 100);
                    return;
                }
            }
        }
        // Fallback: global PAN search
        Matcher m = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b").matcher(text);
        if (m.find() && PanValidator.isValid(m.group())) {
            putField(result, "panNumber", m.group(), 60);
        }
    }

    private void extractName(String text, ExtractionResult result) {
        // Strategy 1: Look for "Name of the Assessee" or "Name of Assessee" → next line
        Pattern namePattern = Pattern.compile(
                "(?i)(?:name of (?:the )?assessee)[:\\s]*([A-Za-z][A-Za-z0-9\\s.-]+?)\\s*(?:PAN|Date|Financial|\\d|$)",
                Pattern.MULTILINE);
        Matcher m = namePattern.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() > 2 && name.length() < 100) {
                putField(result, "assesseeName", name, 95);
                return;
            }
        }

        // Strategy 2: Line-based — find keyword, get next line with only uppercase letters
        for (String kw : List.of("Name of the Assessee", "Name of Assessee")) {
            int idx = text.toLowerCase().indexOf(kw.toLowerCase());
            if (idx == -1) continue;
            String after = text.substring(idx + kw.length());
            String[] lines = after.split("\n");
            for (String line : lines) {
                String cleaned = line.replaceAll("[^A-Za-z\\s.-]", "").trim();
                if (cleaned.length() > 3 && cleaned.length() < 80
                        && !cleaned.toLowerCase().contains("assessee")
                        && !cleaned.toLowerCase().contains("name")
                        && !cleaned.toLowerCase().contains("pan")
                        && !cleaned.toLowerCase().contains("date")) {
                    putField(result, "assesseeName", cleaned, 80);
                    return;
                }
            }
        }
    }

    private void extractFinancialYear(String text, ExtractionResult result) {
        // Pattern: "Financial Year 2025-26" or "F.Y. 2025-26"
        Matcher m = Pattern.compile("(?i)(?:financial year|f\\.?y\\.?)\\s*:?\\s*(\\d{4}[\\-–]\\d{2,4})").matcher(text);
        if (m.find()) {
            putField(result, "financialYear", m.group(1), 95);
            return;
        }
        // Try: "FY 2025-26"
        m = Pattern.compile("(?i)FY\\s*(\\d{4}[\\-–]\\d{2,4})").matcher(text);
        if (m.find()) {
            putField(result, "financialYear", m.group(1), 85);
        }
    }

    // ========== HELPERS ==========

    private int countCGLines(String annexureText) {
        if (annexureText == null || annexureText.isEmpty()) return 0;
        int count = 0;
        String lower = annexureText.toLowerCase();
        int idx = 0;
        String keyword = "sale of securities and units of mutual fund";
        while ((idx = lower.indexOf(keyword, idx)) != -1) {
            count++;
            idx += keyword.length();
        }
        return count;
    }

    private String normalize(String text) {
        return text.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n").replaceAll(" +", " ").trim();
    }

    private void putField(ExtractionResult result, String key, Object value, int confidence) {
        result.getFields().put(key, ExtractedField.builder()
                .value(value)
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
        try {
            return Double.parseDouble(f.getValue().toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
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
