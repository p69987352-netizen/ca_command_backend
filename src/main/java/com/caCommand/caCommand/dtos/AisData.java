package com.caCommand.caCommand.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parsed AIS (Annual Information Statement) / TIS (Taxpayer Information Summary) data
 * fetched from the Income Tax e-portal via Python microservice.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AisData {

    private String pan;
    @JsonAlias("assessment_year")
    private String assessmentYear;   // e.g. "2024-25"
    @JsonAlias("taxpayer_name")
    private String taxpayerName;

    // ── Income Sources ─────────────────────────────────────
    @JsonAlias("salary_income")
    private Double salaryIncome       = 0.0;  // From employer
    @JsonAlias("fd_interest_income")
    private Double fdInterestIncome   = 0.0;  // FD/savings interest
    @JsonAlias("dividend_income")
    private Double dividendIncome     = 0.0;
    @JsonAlias("equity_capital_gains")
    private Double equityCapitalGains = 0.0;  // Short/long term equity gains
    @JsonAlias("mutual_fund_gains")
    private Double mutualFundGains    = 0.0;
    @JsonAlias("business_income")
    private Double businessIncome     = 0.0;  // PGBP
    @JsonAlias("professional_income")
    private Double professionalIncome = 0.0;  // 44ADA (consulting, etc.)
    @JsonAlias("crypto_income")
    private Double cryptoIncome       = 0.0;  // VDA transactions
    @JsonAlias("rental_income")
    private Double rentalIncome       = 0.0;
    @JsonAlias("other_income")
    private Double otherIncome        = 0.0;

    // ── TDS Summary ────────────────────────────────────────
    @JsonAlias("total_tds_deducted")
    private Double totalTdsDeducted   = 0.0;
    @JsonAlias("total_tax_paid")
    private Double totalTaxPaid       = 0.0;
    @JsonAlias("tax_payable")
    private Double taxPayable         = 0.0;  // Computed liability minus paid

    // ── Compliance Flags ───────────────────────────────────
    @JsonAlias("has_pending_demand")
    private Boolean hasPendingDemand  = false;
    @JsonAlias("pending_demand_amt")
    private Double  pendingDemandAmt  = 0.0;
    @JsonAlias("has_appeal")
    private Boolean hasAppeal         = false;
    @JsonAlias("appeal_details")
    private String  appealDetails     = "";
    @JsonAlias("has_tds_mismatch")
    private Boolean hasTdsMismatch    = false;
    @JsonAlias("has_crypto_activity")
    private Boolean hasCryptoActivity = false;
    @JsonAlias("has_capital_gains")
    private Boolean hasCapitalGains   = false;

    // ── AI Recommendations ─────────────────────────────────
    @JsonAlias("suggested_itr_form")
    private String suggestedItrForm   = "ITR-1";  // Auto-detected
    @JsonAlias("suggested_fee")
    private Double suggestedFee       = 999.0;    // Approx fee
    @JsonAlias("complexity_level")
    private String complexityLevel    = "SIMPLE"; // SIMPLE / MEDIUM / COMPLEX
    @JsonAlias("summary_message")
    private String summaryMessage     = "";       // WhatsApp-ready summary

    // ── Raw data ──────────────────────────────────────────
    @JsonAlias("raw_ais_json")
    private String rawAisJson         = "";       // Full JSON for debugging
    @JsonAlias("fetch_success")
    private boolean fetchSuccess      = false;
    @JsonAlias("fetch_error")
    private String fetchError         = "";
    @JsonAlias("pdf_base64")
    private String pdfBase64          = "";

    // ── Convenience: was there a real fetch or mocked? ────
    private boolean mocked            = false;

    /** Detect ITR form based on income sources */
    public void computeItrForm() {
        if (cryptoIncome > 0 || equityCapitalGains > 0 || mutualFundGains > 0 || rentalIncome > 0) {
            suggestedItrForm = "ITR-2";
        } else if (businessIncome > 0) {
            suggestedItrForm = "ITR-3";
        } else if (professionalIncome > 0) {
            suggestedItrForm = "ITR-4";
        } else {
            suggestedItrForm = "ITR-1";
        }

        // Mark flags
        hasCapitalGains   = equityCapitalGains > 0 || mutualFundGains > 0;
        hasCryptoActivity = cryptoIncome > 0;

        // Complexity
        if (hasCryptoActivity || businessIncome > 0 || hasAppeal) {
            complexityLevel = "COMPLEX";
        } else if (hasCapitalGains || professionalIncome > 0 || rentalIncome > 0) {
            complexityLevel = "MEDIUM";
        } else {
            complexityLevel = "SIMPLE";
        }
    }

    /** Compute approximate fee based on ITR form and complexity */
    public void computeFee() {
        suggestedFee = switch (suggestedItrForm) {
            case "ITR-2" -> hasCryptoActivity ? 2499.0 : 1499.0;
            case "ITR-3" -> 2999.0;
            case "ITR-4" -> 1499.0;
            default       -> 999.0;  // ITR-1
        };
        if ("COMPLEX".equals(complexityLevel)) suggestedFee += 500;
    }
}
