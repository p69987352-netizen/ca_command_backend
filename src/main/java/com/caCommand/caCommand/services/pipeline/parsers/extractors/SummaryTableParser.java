package com.caCommand.caCommand.services.pipeline.parsers.extractors;

import com.caCommand.caCommand.services.pipeline.parsers.models.SummaryTableRow;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses TIS/AIS summary table rows line-by-line.
 * 
 * Input: Section-split text containing ONLY the summary table.
 * Output: List of SummaryTableRow with category + amounts.
 * 
 * TIS Summary Table Format:
 *   SR. NO.  INFORMATION CATEGORY          PROCESSED BY SYSTEM  ACCEPTED BY TAXPAYER
 *   1        Rent received                 44,73,825            44,73,825
 *   2        Dividend                      1,07,547             1,07,547
 *   3        Interest from savings bank    35,257               35,257
 *   4        Interest from deposit         1,20,601             1,20,601
 */
@Slf4j
public class SummaryTableParser {

    // Pattern: starts with a number (sr no), then category text, then 1-2 amounts
    // Matches lines like: "1 Rent received 44,73,825 44,73,825"
    // or "3 Interest from savings bank 35,257 35,257"
    // Category can contain numbers and punctuation (e.g., "112A Capital Gains", "Interest u/s 244A")
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3})\\s+"                                 // SR. NO (group 1)
            + "([A-Za-z0-9][A-Za-z0-9\\s/()&.,\\-]+?)"           // CATEGORY (group 2) - lazy match
            + "\\s+([\\d,]+(?:\\.\\d{1,2})?)"                     // FIRST AMOUNT (group 3)
            + "(?:\\s+([\\d,]+(?:\\.\\d{1,2})?))?\\s*$",          // OPTIONAL SECOND AMOUNT (group 4)
            Pattern.MULTILINE
    );

    // Alternative pattern for lines where amounts may have more complex formatting
    private static final Pattern ALT_ROW_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3})\\s+"                                 // SR. NO
            + "([A-Za-z0-9][A-Za-z0-9\\s/()&.,\\-]+\\w)"         // CATEGORY
            + "\\s+((?:\\d{1,2},)*\\d{2},\\d{3}(?:\\.\\d{1,2})?)" // Indian number format
            + "(?:\\s+((?:\\d{1,2},)*\\d{2},\\d{3}(?:\\.\\d{1,2})?))?\\s*$",
            Pattern.MULTILINE
    );

    /**
     * Parse summary table text into rows.
     * Only parses the SUMMARY section (pre-split by SectionSplitter).
     */
    public static List<SummaryTableRow> parse(String summaryText) {
        List<SummaryTableRow> rows = new ArrayList<>();
        if (summaryText == null || summaryText.isBlank()) {
            return rows;
        }

        String[] lines = summaryText.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            
            // Skip header lines
            if (trimmed.toLowerCase().contains("information category") 
                || trimmed.toLowerCase().contains("processed by")
                || trimmed.toLowerCase().contains("accepted by")
                || trimmed.toLowerCase().contains("confirmed by")
                || trimmed.toLowerCase().contains("sr. no")
                || trimmed.toLowerCase().contains("sr.no")
                || trimmed.startsWith("---")
                || trimmed.startsWith("===")
                || trimmed.toLowerCase().contains("taxpayer information summary")
                || trimmed.toLowerCase().contains("all amount values")) {
                continue;
            }

            SummaryTableRow row = tryParseRow(trimmed);
            if (row != null) {
                row.setSection("SUMMARY");
                row.setConfidence(95);
                rows.add(row);
                log.debug("Parsed TIS row: {} → {}", row.getCategory(), row.getBestValue());
            }
        }

        log.info("SummaryTableParser: Extracted {} rows from summary section", rows.size());
        return rows;
    }

    private static SummaryTableRow tryParseRow(String line) {
        // Try primary pattern first
        Matcher m = ROW_PATTERN.matcher(line);
        if (m.find()) {
            return buildRow(m);
        }

        // Try alternative pattern
        m = ALT_ROW_PATTERN.matcher(line);
        if (m.find()) {
            return buildRow(m);
        }

        // Last resort: manual split approach
        return tryManualParse(line);
    }

    private static SummaryTableRow buildRow(Matcher m) {
        try {
            int srNo = Integer.parseInt(m.group(1));
            String category = m.group(2).trim();
            double processedValue = parseIndianNumber(m.group(3));
            double acceptedValue = m.group(4) != null ? parseIndianNumber(m.group(4)) : 0;

            // Skip rows where category is empty or just numbers
            if (category.isEmpty() || category.matches("\\d+")) return null;

            return SummaryTableRow.builder()
                    .srNo(srNo)
                    .category(category)
                    .processedValue(processedValue)
                    .acceptedValue(acceptedValue)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Manual parse for lines that don't match regex patterns.
     * Looks for: number, text, number(s) at end of line.
     */
    private static SummaryTableRow tryManualParse(String line) {
        // Must start with a digit (sr no)
        if (!Character.isDigit(line.charAt(0))) return null;

        // Find all number-like tokens in the line
        List<String> numbers = new ArrayList<>();
        List<Integer> numberPositions = new ArrayList<>();
        Matcher numMatcher = Pattern.compile("((?:\\d{1,2},)*\\d{2,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)").matcher(line);
        while (numMatcher.find()) {
            String num = numMatcher.group(1);
            // Only consider numbers with commas (Indian format) or > 2 digits as amounts
            if (num.contains(",") || num.length() > 3) {
                numbers.add(num);
                numberPositions.add(numMatcher.start());
            }
        }

        if (numbers.isEmpty()) return null;

        // First number could be part of the amounts
        // The sr no should be a small number at the start
        String srNoStr = line.substring(0, line.indexOf(' ') > 0 ? line.indexOf(' ') : 1).trim();
        int srNo;
        try {
            srNo = Integer.parseInt(srNoStr);
        } catch (NumberFormatException e) {
            return null;
        }

        if (srNo > 50) return null; // Not a serial number

        // Category = text between sr no and first amount
        if (numberPositions.isEmpty()) return null;
        int firstAmountPos = numberPositions.get(0);
        String category = line.substring(srNoStr.length(), firstAmountPos).trim();
        
        // Clean category
        category = category.replaceAll("\\s+", " ").trim();
        if (category.length() < 3) return null;

        double processedValue = parseIndianNumber(numbers.get(0));
        double acceptedValue = numbers.size() > 1 ? parseIndianNumber(numbers.get(1)) : 0;

        return SummaryTableRow.builder()
                .srNo(srNo)
                .category(category)
                .processedValue(processedValue)
                .acceptedValue(acceptedValue)
                .section("SUMMARY")
                .build();
    }

    /**
     * Parse Indian number format: "44,73,825" → 4473825.0
     */
    public static double parseIndianNumber(String numStr) {
        if (numStr == null || numStr.isBlank()) return 0;
        try {
            return Double.parseDouble(numStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
