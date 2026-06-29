package com.caCommand.caCommand.services.pipeline.parsers.extractors;

import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Splits document text into named sections.
 * 
 * TIS: HEADER | SUMMARY | ANNEXURE
 * 26AS: HEADER | PART_A | PART_A1 | PART_B | PART_C | PART_D
 */
@Slf4j
public class SectionSplitter {

    // TIS stop markers (any of these ends the SUMMARY section)
    private static final List<String> TIS_SUMMARY_STOP_MARKERS = List.of(
            "annexure to taxpayer information summary",
            "the information details under each information category",
            "reported by source",
            "disclaimer",
            "notes:",
            "verification",
            "generated on",
            "end of summary"
    );

    // TIS summary start markers
    private static final List<String> TIS_SUMMARY_START_MARKERS = List.of(
            "taxpayer information summary",
            "information category"
    );

    /**
     * Splits TIS document into HEADER, SUMMARY, ANNEXURE sections.
     */
    public static Map<String, String> splitTIS(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            sections.put("HEADER", "");
            sections.put("SUMMARY", "");
            sections.put("ANNEXURE", "");
            return sections;
        }

        String lowerText = text.toLowerCase();

        // Find SUMMARY start
        int summaryStart = -1;
        for (String marker : TIS_SUMMARY_START_MARKERS) {
            int idx = lowerText.indexOf(marker);
            if (idx != -1 && (summaryStart == -1 || idx < summaryStart)) {
                summaryStart = idx;
            }
        }

        // Find SUMMARY end (first stop marker after summary start)
        int summaryEnd = text.length();
        if (summaryStart != -1) {
            for (String marker : TIS_SUMMARY_STOP_MARKERS) {
                int idx = lowerText.indexOf(marker, summaryStart + 10); // Skip a bit past the start
                if (idx != -1 && idx < summaryEnd) {
                    summaryEnd = idx;
                }
            }
        }

        if (summaryStart == -1) {
            // No summary section found, treat entire text as summary
            log.warn("TIS Section Splitter: No summary section markers found. Using full text.");
            sections.put("HEADER", "");
            sections.put("SUMMARY", text);
            sections.put("ANNEXURE", "");
        } else {
            sections.put("HEADER", text.substring(0, summaryStart).trim());
            sections.put("SUMMARY", text.substring(summaryStart, summaryEnd).trim());
            sections.put("ANNEXURE", summaryEnd < text.length() ? text.substring(summaryEnd).trim() : "");
        }

        log.info("TIS Section Split: HEADER={}chars, SUMMARY={}chars, ANNEXURE={}chars",
                sections.get("HEADER").length(),
                sections.get("SUMMARY").length(),
                sections.get("ANNEXURE").length());

        return sections;
    }

    /**
     * Splits Form 26AS into sections: HEADER, PART_A, PART_B, PART_C, PART_D.
     * Part A = TDS on Income (salary and non-salary)
     * Part B = TCS
     * Part C = Tax Paid (advance tax, self-assessment)
     * Part D = Paid Refund
     */
    public static Map<String, String> split26AS(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            sections.put("FULL", "");
            return sections;
        }

        String lowerText = text.toLowerCase();

        // Try to find Part markers
        int partA = findFirst(lowerText, "part a", "details of tax deducted");
        int partB = findFirst(lowerText, "part b", "details of tax collected");
        int partC = findFirst(lowerText, "part c", "details of tax paid");
        int partD = findFirst(lowerText, "part d", "details of paid refund");

        // If no parts found, return full text
        if (partA == -1 && partB == -1 && partC == -1 && partD == -1) {
            log.warn("26AS Section Splitter: No part markers found. Using full text.");
            sections.put("FULL", text);
            return sections;
        }

        // Build sections based on what we found
        int[] markers = { 
            partA != -1 ? partA : Integer.MAX_VALUE, 
            partB != -1 ? partB : Integer.MAX_VALUE, 
            partC != -1 ? partC : Integer.MAX_VALUE, 
            partD != -1 ? partD : Integer.MAX_VALUE,
            text.length() 
        };
        Arrays.sort(markers);

        // Header = everything before first part
        int firstPart = markers[0];
        if (firstPart < text.length()) {
            sections.put("HEADER", text.substring(0, firstPart).trim());
        }

        // Extract each part
        if (partA != -1) {
            int end = findNextMarker(text.length(), partA, partB, partC, partD);
            sections.put("PART_A", text.substring(partA, end).trim());
        }
        if (partB != -1) {
            int end = findNextMarker(text.length(), partB, partC, partD);
            sections.put("PART_B", text.substring(partB, end).trim());
        }
        if (partC != -1) {
            int end = findNextMarker(text.length(), partC, partD);
            sections.put("PART_C", text.substring(partC, end).trim());
        }
        if (partD != -1) {
            sections.put("PART_D", text.substring(partD).trim());
        }

        // Fallback: if no parts extracted, use full text
        if (sections.size() <= 1) {
            sections.put("FULL", text);
        }

        log.info("26AS Section Split: {} sections found", sections.size());
        return sections;
    }

    private static int findFirst(String lowerText, String... markers) {
        int best = -1;
        for (String m : markers) {
            int idx = lowerText.indexOf(m);
            if (idx != -1 && (best == -1 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static int findNextMarker(int textLength, int current, int... others) {
        int next = textLength;
        for (int o : others) {
            if (o > current && o < next) {
                next = o;
            }
        }
        return next;
    }
}
