package com.caCommand.caCommand.services.pipeline.parsers.extractors;

import com.caCommand.caCommand.services.pipeline.parsers.models.DeductorEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Form 26AS deductor table rows.
 * 
 * Instead of searching for "Total Tax Deducted" and grabbing the next number (which 
 * catches serial numbers like "1"), this parser identifies each deductor block and 
 * extracts their individual TDS amounts, then sums them.
 * 
 * 26AS Part A format varies, but commonly contains rows like:
 *   Sl.No  Name of Deductor  TAN  Section  Amount Paid  Tax Deducted  Tax Deposited
 *   1      BANK OF BARODA    ...  194A     2,91,090     29,109        29,109
 */
@Slf4j
public class DeductorTableParser {

    // Pattern for amount: digits, optional commas, optional decimal. 
    // Uses lookarounds to ensure the number isn't part of a word/TAN (e.g. MUMBA12345B).
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?<![a-zA-Z\\d/.-])([\\d,]+(?:\\.\\d{1,2})?)(?![a-zA-Z\\d/.-])");
    
    // Pattern to match "Total" summary lines in 26AS
    private static final Pattern TOTAL_TDS_PATTERN = Pattern.compile(
            "(?i)total[^\\d]*?([\\d,]+(?:\\.\\d{1,2})?)"
    );

    /**
     * Parse 26AS text to extract all deductor entries and compute total TDS.
     * Works with both Part A (salary TDS) and Part A1 (non-salary TDS).
     */
    public static List<DeductorEntry> parse(String text) {
        List<DeductorEntry> entries = new ArrayList<>();
        if (text == null || text.isBlank()) return entries;

        String[] lines = text.split("\n");
        
        // Strategy 1: Look for structured table rows with multiple amounts
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            // Skip headers, separators, and child transaction lines (which contain dates)
            if (line.toLowerCase().contains("name of deductor") 
                || line.toLowerCase().contains("sl.no")
                || line.toLowerCase().contains("sl. no")
                || line.toLowerCase().contains("amount paid")
                || line.toLowerCase().contains("tax deducted")
                || line.toLowerCase().contains("details of tax")
                || line.toLowerCase().contains("part a")
                || line.startsWith("---")
                || line.startsWith("===")
                || line.matches(".*\\b\\d{1,2}-[a-zA-Z]{3}-\\d{4}\\b.*")
                || line.matches(".*\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b.*")) {
                continue;
            }
            
            // Find lines with multiple amounts (typical deductor row)
            List<Double> amounts = extractAllAmounts(line);
            
            // A valid deductor row typically has 2-4 amounts.
            // Since amounts are at the end of the line, we read them from right to left to avoid stray leading numbers.
            if (amounts.size() >= 2) {
                double amountPaid = amounts.size() >= 3 ? amounts.get(amounts.size() - 3) : amounts.get(0);
                double tdsDeducted = amounts.size() >= 3 ? amounts.get(amounts.size() - 2) : amounts.get(1);
                double tdsDeposited = amounts.size() >= 3 ? amounts.get(amounts.size() - 1) : amounts.get(1);

                DeductorEntry entry = DeductorEntry.builder()
                        .name(extractDeductorName(line))
                        .section(extractSection(line))
                        .amountPaid(amountPaid)
                        .tdsDeducted(tdsDeducted)
                        .tdsDeposited(tdsDeposited)
                        .confidence(85)
                        .build();
                
                // Filter out very small amounts that could be false positives
                if (entry.getTdsDeducted() > 0 || entry.getAmountPaid() >= 100) {
                    entries.add(entry);
                }
            }
        }

        // Strategy 2: If no structured rows found, look for "Total" lines
        if (entries.isEmpty() && text.toLowerCase().contains("total")) {
            log.info("No structured deductor rows found, trying Total line extraction");
            entries = extractFromTotalLines(text);
        }

        log.info("DeductorTableParser: Found {} deductor entries", entries.size());
        return entries;
    }

    /**
     * Compute total TDS from all deductor entries.
     */
    public static double computeTotalTDS(List<DeductorEntry> entries) {
        double total = 0;
        for (DeductorEntry e : entries) {
            total += e.getTdsDeducted();
        }
        return total;
    }

    /**
     * Extract all numeric amounts from a line (Indian format).
     * Filters out small numbers that are likely serial numbers, dates, or section codes.
     */
    private static List<Double> extractAllAmounts(String line) {
        List<Double> amounts = new ArrayList<>();
        // Pre-process line: remove dates like DD-MMM-YYYY or DD/MM/YYYY to avoid matching years as amounts
        String cleanedLine = line.replaceAll("\\b\\d{1,2}[/-](?:[A-Za-z]{3}|\\d{1,2})[/-]\\d{2,4}\\b", " ");
        
        Matcher m = AMOUNT_PATTERN.matcher(cleanedLine);
        while (m.find()) {
            String numStr = m.group(1);
            try {
                double val = Double.parseDouble(numStr.replace(",", ""));
                // Only consider it an amount if:
                // 1. It has a comma, OR
                // 2. It has a decimal point, OR
                // 3. It's a reasonably large number (>= 500)
                if (numStr.contains(",") || numStr.contains(".") || val >= 500) {
                    amounts.add(val);
                }
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return amounts;
    }

    /**
     * Try to extract deductor name from a line.
     */
    private static String extractDeductorName(String line) {
        // Name is typically the text portion before the numbers
        Matcher m = Pattern.compile("^(?:\\d+\\s+)?([A-Za-z][A-Za-z0-9\\s&.,()-]+?)\\s+(?:[A-Z]{4}\\d{5}[A-Z]|\\d)").matcher(line);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Fallback: take first alphabetic/alphanumeric portion
        m = Pattern.compile("([A-Za-z][A-Za-z0-9\\s&.,()-]+)").matcher(line);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.length() > 3) return name;
        }
        return "Unknown";
    }

    /**
     * Extract section code (194A, 194, 192, etc.)
     */
    private static String extractSection(String line) {
        Matcher m = Pattern.compile("\\b(19[24][A-Z]?)\\b").matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Fallback: extract TDS from "Total" summary lines.
     * Used when individual deductor rows can't be parsed.
     */
    private static List<DeductorEntry> extractFromTotalLines(String text) {
        List<DeductorEntry> entries = new ArrayList<>();
        String[] lines = text.split("\n");
        
        for (String line : lines) {
            String lower = line.trim().toLowerCase();
            // Look for lines containing "total" with a substantial amount
            if (lower.contains("total") && !lower.contains("total amount of") 
                    && !lower.contains("total no")) {
                Matcher m = TOTAL_TDS_PATTERN.matcher(line);
                if (m.find()) {
                    try {
                        double amount = Double.parseDouble(m.group(1).replace(",", ""));
                        if (amount > 0) {
                            entries.add(DeductorEntry.builder()
                                    .name("Total (from summary line)")
                                    .tdsDeducted(amount)
                                    .confidence(70)
                                    .build());
                            log.info("Found TDS from Total line: {}", amount);
                        }
                    } catch (NumberFormatException e) {
                        // skip
                    }
                }
            }
        }
        return entries;
    }
}
