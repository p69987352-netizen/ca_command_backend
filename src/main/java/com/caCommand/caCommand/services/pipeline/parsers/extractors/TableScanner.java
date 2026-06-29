package com.caCommand.caCommand.services.pipeline.parsers.extractors;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TableScanner {

    // Matches numbers like 1,00,000.00 or 50000
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    /**
     * Scans the text for a keyword, then looks for the next numeric amount.
     */
    public static Optional<Double> scanNextAmountAfterKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return Optional.empty();

        int bestIndex = -1;
        String matchedKeyword = null;

        for (String kw : keywords) {
            int index = text.toLowerCase().indexOf(kw.toLowerCase());
            if (index != -1 && (bestIndex == -1 || index < bestIndex)) {
                bestIndex = index;
                matchedKeyword = kw;
            }
        }

        if (bestIndex == -1) return Optional.empty();

        // Start scanning after the keyword
        String substring = text.substring(bestIndex + matchedKeyword.length());
        Matcher matcher = AMOUNT_PATTERN.matcher(substring);
        
        System.out.println("SCANNING AFTER KEYWORD: " + matchedKeyword);
        System.out.println("SUBSTRING (first 100 chars): " + substring.substring(0, Math.min(substring.length(), 100)));
        
        while (matcher.find()) {
            String rawMatch = matcher.group(1);
            System.out.println("REGEX FOUND MATCH: '" + rawMatch + "'");
            try {
                String amountStr = rawMatch.replace(",", "");
                if (amountStr.isBlank()) continue; // Skip lone commas
                
                double val = Double.parseDouble(amountStr);
                System.out.println("PARSED AS: " + val);
                
                // If it's a very small number like 1, 2, 3 it might be a serial number.
                // We should probably return the first valid amount, but let's just return it for now.
                return Optional.of(val);
            } catch (Exception e) {
                System.out.println("PARSE FAILED FOR: '" + rawMatch + "'");
            }
        }
        System.out.println("NO VALID AMOUNT FOUND AFTER KEYWORD.");
        return Optional.empty();
    }

    /**
     * Scans the text for ALL keywords, finds the next numeric amount after EACH keyword, and sums them.
     */
    public static Optional<Double> scanTotalAmountForKeywords(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return Optional.empty();

        double total = 0;
        boolean foundAny = false;

        for (String kw : keywords) {
            int startIndex = 0;
            while (true) {
                int index = text.toLowerCase().indexOf(kw.toLowerCase(), startIndex);
                if (index == -1) break;

                // Found an occurrence, get the amount after it
                String substring = text.substring(index + kw.length());
                Matcher matcher = AMOUNT_PATTERN.matcher(substring);
                if (matcher.find()) {
                    String rawMatch = matcher.group(1);
                    try {
                        String amountStr = rawMatch.replace(",", "");
                        if (!amountStr.isBlank()) {
                            double val = Double.parseDouble(amountStr);
                            total += val;
                            foundAny = true;
                            System.out.println("SUM SCAN FOUND: " + kw + " -> " + val);
                        }
                    } catch (Exception e) {}
                }
                
                // Jump past this occurrence to find next
                startIndex = index + kw.length();
            }
        }

        return foundAny ? Optional.of(total) : Optional.empty();
    }
}
