package com.caCommand.caCommand.config;

import com.caCommand.caCommand.entities.ComplexityRule;
import com.caCommand.caCommand.entities.PricingRule;
import com.caCommand.caCommand.entities.TisCategoryMapping;
import com.caCommand.caCommand.enums.CategoryType;
import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.enums.IncomeCategory;
import com.caCommand.caCommand.repositories.ComplexityRuleRepository;
import com.caCommand.caCommand.repositories.PricingRuleRepository;
import com.caCommand.caCommand.repositories.TisCategoryMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder {

    private final PricingRuleRepository pricingRuleRepository;
    private final ComplexityRuleRepository complexityRuleRepository;
    private final TisCategoryMappingRepository tisCategoryMappingRepository;

    @Bean
    public CommandLineRunner initDatabase() {
        return args -> {
            seedPricingRules();
            seedComplexityRules();
            seedTisMappings();
        };
    }

    private void seedPricingRules() {
        if (pricingRuleRepository.count() == 0) {
            log.info("Seeding Pricing Rules...");
            PricingRule itr1 = new PricingRule();
            itr1.setServiceType("ITR-1 Filing");
            itr1.setItrType("ITR-1");
            itr1.setBaseFee(1500.0);
            itr1.setMinFee(999.0);
            itr1.setMaxFee(2500.0);
            itr1.setDefaultDiscount(20.0);
            itr1.setEffectiveFrom(LocalDateTime.now());
            pricingRuleRepository.save(itr1);

            PricingRule itr2 = new PricingRule();
            itr2.setServiceType("ITR-2 Filing");
            itr2.setItrType("ITR-2");
            itr2.setBaseFee(2500.0);
            itr2.setMinFee(1999.0);
            itr2.setMaxFee(4500.0);
            itr2.setDefaultDiscount(20.0);
            itr2.setEffectiveFrom(LocalDateTime.now());
            pricingRuleRepository.save(itr2);

            PricingRule itr3 = new PricingRule();
            itr3.setServiceType("ITR-3 Filing");
            itr3.setItrType("ITR-3");
            itr3.setBaseFee(3500.0);
            itr3.setMinFee(2999.0);
            itr3.setMaxFee(6500.0);
            itr3.setDefaultDiscount(20.0);
            itr3.setEffectiveFrom(LocalDateTime.now());
            pricingRuleRepository.save(itr3);
            
            PricingRule itr4 = new PricingRule();
            itr4.setServiceType("ITR-4 Filing");
            itr4.setItrType("ITR-4");
            itr4.setBaseFee(2500.0);
            itr4.setMinFee(1999.0);
            itr4.setMaxFee(4500.0);
            itr4.setDefaultDiscount(20.0);
            itr4.setEffectiveFrom(LocalDateTime.now());
            pricingRuleRepository.save(itr4);
        }
    }

    private void seedComplexityRules() {
        if (complexityRuleRepository.count() == 0) {
            log.info("Seeding Complexity Rules...");
            List<ComplexityRule> rules = Arrays.asList(
                    createCompRule("CAPITAL_GAIN", "Capital Gain Analysis", 1500.0, 1),
                    createCompRule("BUSINESS", "Business Income Assessment", 2500.0, 2),
                    createCompRule("FOREIGN_INCOME", "Foreign Asset / Income Review", 3000.0, 3),
                    createCompRule("CRYPTO", "Crypto / VDA Transactions", 2500.0, 4),
                    createCompRule("NOTICE", "Notice Response Preparation", 2000.0, 5),
                    createCompRule("INTEREST", "Interest & Dividend Reconciliation", 300.0, 6)
            );
            complexityRuleRepository.saveAll(rules);
        }
    }

    private ComplexityRule createCompRule(String code, String name, Double amount, int priority) {
        ComplexityRule r = new ComplexityRule();
        r.setCode(code);
        r.setDisplayName(name);
        r.setAmount(amount);
        r.setPriority(priority);
        r.setIsEnabled(true);
        return r;
    }

    private void seedTisMappings() {
        if (tisCategoryMappingRepository.count() == 0) {
            log.info("Seeding TIS Category Mappings...");
            // INCOME
            saveTisMap("salary", CategoryType.INCOME, IncomeCategory.SALARY, true, 1);
            saveTisMap("interest from savings bank", CategoryType.INCOME, IncomeCategory.INTEREST_SAVINGS, true, 2);
            saveTisMap("interest from deposit", CategoryType.INCOME, IncomeCategory.INTEREST_FD, true, 3);
            saveTisMap("interest from fd", CategoryType.INCOME, IncomeCategory.INTEREST_FD, true, 3);
            saveTisMap("interest other", CategoryType.INCOME, IncomeCategory.INTEREST_OTHER, true, 4);
            saveTisMap("interest income", CategoryType.INCOME, IncomeCategory.INTEREST_OTHER, true, 4);
            saveTisMap("dividend", CategoryType.INCOME, IncomeCategory.DIVIDEND, true, 5);
            saveTisMap("rent", CategoryType.INCOME, IncomeCategory.RENT, true, 6);
            saveTisMap("sale of securities and units of mutual fund", CategoryType.INCOME, IncomeCategory.CAPITAL_GAINS, true, 7);
            saveTisMap("sale of land or building", CategoryType.INCOME, IncomeCategory.CAPITAL_GAINS, true, 8);
            saveTisMap("sale of shares", CategoryType.INCOME, IncomeCategory.CAPITAL_GAINS, true, 9);
            saveTisMap("capital gain", CategoryType.INCOME, IncomeCategory.CAPITAL_GAINS, true, 10);
            saveTisMap("business receipts", CategoryType.INCOME, IncomeCategory.BUSINESS, true, 11);
            saveTisMap("receipts from business", CategoryType.INCOME, IncomeCategory.BUSINESS, true, 12);
            saveTisMap("professional fees", CategoryType.INCOME, IncomeCategory.BUSINESS, true, 13);
            saveTisMap("gst turnover", CategoryType.INCOME, IncomeCategory.GST_TURNOVER, true, 14);

            // INVESTMENT / IGNORED
            saveTisMap("purchase of securities and units of mutual funds", CategoryType.INVESTMENT, IncomeCategory.NONE, false, 50);
            saveTisMap("purchase of time deposits", CategoryType.INVESTMENT, IncomeCategory.NONE, false, 51);
            saveTisMap("purchase of immovable property", CategoryType.INVESTMENT, IncomeCategory.NONE, false, 52);
            saveTisMap("purchase of vehicle", CategoryType.INVESTMENT, IncomeCategory.NONE, false, 53);

            // TAX
            saveTisMap("tds", CategoryType.TAX, IncomeCategory.NONE, false, 100);
            saveTisMap("tax deducted", CategoryType.TAX, IncomeCategory.NONE, false, 101);
            saveTisMap("advance tax", CategoryType.TAX, IncomeCategory.NONE, false, 102);

            // INFORMATION
            saveTisMap("pan", CategoryType.INFORMATION, IncomeCategory.NONE, false, 200);
        }
    }

    private void saveTisMap(String keyword, CategoryType cType, IncomeCategory iType, boolean agg, int order) {
        TisCategoryMapping m = new TisCategoryMapping();
        m.setKeyword(keyword);
        m.setDocumentType(DocumentType.TIS); // Defaulting for TIS mapping
        m.setCategoryType(cType);
        m.setIncomeCategory(iType);
        m.setShouldAggregate(agg);
        m.setDisplayOrder(order);
        m.setEnabled(true);
        tisCategoryMappingRepository.save(m);
    }
}
