package com.caCommand.caCommand.services.pipeline.parsers.dictionary;

import java.util.Arrays;
import java.util.List;

/**
 * Category Dictionary v2.0 - Alias-based matching for TIS/AIS categories.
 * 
 * Each category has a list of known aliases (how it appears in various documents).
 * The SummaryTableParser extracts rows, then TisParser uses matchesCategory()
 * to map each row to the correct TaxProfile field.
 * 
 * Future-proof: To add a new variation, just add a string to the alias list.
 */
public class KeywordDictionary {

    // ===== SALARY =====
    public static final List<String> SALARY_ALIASES = Arrays.asList(
            "income from salary", "salary", "gross salary", "net salary",
            "salary income", "salary received", "wages"
    );

    // ===== INTEREST (all sub-types → will be SUMMED) =====
    public static final List<String> INTEREST_ALIASES = Arrays.asList(
            "interest from savings bank", "interest from saving bank",
            "interest from deposit", "interest from fixed deposit",
            "interest on income tax refund", "interest income",
            "interest from deposits", "interest other",
            "interest on securities", "interest from recurring",
            "interest from fd", "fd interest", "savings interest"
    );

    // ===== DIVIDEND =====
    public static final List<String> DIVIDEND_ALIASES = Arrays.asList(
            "dividend", "income from dividend", "dividend income",
            "dividend received"
    );

    // ===== RENT / HOUSE PROPERTY =====
    public static final List<String> RENT_ALIASES = Arrays.asList(
            "rent received", "income from house property",
            "rental income", "rent", "house property income"
    );

    // ===== CAPITAL GAINS =====
    public static final List<String> CAPITAL_GAIN_ALIASES = Arrays.asList(
            "sale of securities and units of mutual fund",
            "sale of land or building", "capital gain", "capital gains",
            "sale of shares", "sale of immovable property",
            "purchase of securities and units of mutual funds"
    );

    // ===== BUSINESS / GST =====
    public static final List<String> BUSINESS_ALIASES = Arrays.asList(
            "business receipts", "receipts from business",
            "professional fees", "gst turnover", "gst purchases",
            "business income", "professional income",
            "income from business", "purchase of time deposits"
    );

    // ===== IDENTITY FIELD KEYWORDS (for header parsing) =====
    public static final List<String> PAN_KEYWORDS = Arrays.asList(
            "PAN of the Assessee", "PAN", "Permanent Account Number"
    );

    public static final List<String> AY_KEYWORDS = Arrays.asList(
            "Assessment Year", "A.Y.", "AY"
    );

    public static final List<String> NAME_KEYWORDS = Arrays.asList(
            "Name of the Assessee", "Name of Assessee", "Assessee Name"
    );

    // ===== LEGACY KEYWORDS (kept for backward compatibility with AisParser) =====
    public static final List<String> SALARY_KEYWORDS = SALARY_ALIASES;
    public static final List<String> INTEREST_KEYWORDS = INTEREST_ALIASES;
    public static final List<String> DIVIDEND_KEYWORDS = DIVIDEND_ALIASES;
    public static final List<String> RENT_KEYWORDS = RENT_ALIASES;
    public static final List<String> CAPITAL_GAIN_KEYWORDS = CAPITAL_GAIN_ALIASES;
    public static final List<String> BUSINESS_RECEIPTS_KEYWORDS = BUSINESS_ALIASES;

    public static final List<String> SUMMARY_TABLE_HEADERS = Arrays.asList(
            "Information Category", "Processed Value", "Derived Value"
    );

    /**
     * Check if a category string matches any alias in the given list.
     * Uses contains() for flexible matching (handles partial matches).
     * 
     * @param category The category text from the parsed row (lowercased)
     * @param aliases The list of known aliases for a category
     * @return true if any alias is found within the category text
     */
    public static boolean matchesCategory(String category, List<String> aliases) {
        if (category == null || category.isBlank()) return false;
        String lower = category.toLowerCase().trim();
        for (String alias : aliases) {
            if (lower.contains(alias.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
