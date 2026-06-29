package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.SummaryResponseDto;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGenerationService {

    public byte[] generateClientReport(SummaryResponseDto summary) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            // Add Header/Footer Event
            writer.setPageEvent(new PdfPageEventHelper() {
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte cb = writer.getDirectContent();
                    Phrase footer = new Phrase("Porwal CA Firm | Powered by Arjun AI", 
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, java.awt.Color.GRAY));
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                            footer,
                            (document.right() - document.left()) / 2 + document.leftMargin(),
                            document.bottom() - 20, 0);
                }
            });

            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, java.awt.Color.DARK_GRAY);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, java.awt.Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, java.awt.Color.BLACK);
            Font highlightFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, java.awt.Color.RED);
            Font greenFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new java.awt.Color(0, 153, 0));

            // --- Cover Page ---
            Paragraph title = new Paragraph("Client Tax Operations Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(100);
            document.add(title);

            Paragraph subtitle = new Paragraph("Powered by Arjun AI Insights", 
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 14, java.awt.Color.GRAY));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(50);
            document.add(subtitle);

            String clientName = summary.getClientProfile().getName() != null ? summary.getClientProfile().getName() : "Valued Client";
            Paragraph clientInfo = new Paragraph("Client: " + clientName + "\n" +
                                                 "Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")), sectionFont);
            clientInfo.setAlignment(Element.ALIGN_CENTER);
            document.add(clientInfo);

            document.newPage();

            // --- Executive Summary ---
            document.add(new Paragraph("1. Executive Summary", sectionFont));
            document.add(new Paragraph("Arjun AI Readiness Score: " + summary.getReadinessScore() + "/100", highlightFont));
            document.add(new Paragraph(summary.getRecommendedAction(), normalFont));
            document.add(new Paragraph("\n"));

            // --- Client Profile ---
            document.add(new Paragraph("2. Client Profile", sectionFont));
            document.add(new Paragraph("Name: " + clientName, normalFont));
            document.add(new Paragraph("City: " + (summary.getClientProfile().getCity() != null ? summary.getClientProfile().getCity() : "N/A"), normalFont));
            document.add(new Paragraph("Phone: " + summary.getClientProfile().getPhoneNumber(), normalFont));
            document.add(new Paragraph("\n"));

            // --- Document Status ---
            document.add(new Paragraph("3. Document Status", sectionFont));
            String receivedDocsStr = summary.getReceivedDocuments().stream()
                    .map(com.caCommand.caCommand.dtos.SummaryResponseDto.DocumentDto::getName)
                    .collect(java.util.stream.Collectors.joining(", "));
            document.add(new Paragraph("Received Documents: " + receivedDocsStr, normalFont));
            if (!summary.getMissingDocuments().isEmpty()) {
                document.add(new Paragraph("Missing Documents: " + String.join(", ", summary.getMissingDocuments()), highlightFont));
            } else {
                document.add(new Paragraph("All required documents received.", greenFont));
            }
            document.add(new Paragraph("\n"));

            // --- Financial & Tax Summary ---
            document.add(new Paragraph("4. Financial & Tax Summary", sectionFont));
            if (summary.getExtractedData() != null) {
                document.add(new Paragraph("Salary Income: ₹" + summary.getExtractedData().getSalaryIncome(), normalFont));
                document.add(new Paragraph("Interest Income: ₹" + summary.getExtractedData().getInterestIncome(), normalFont));
                document.add(new Paragraph("TDS Deducted: ₹" + summary.getExtractedData().getTds(), normalFont));
                document.add(new Paragraph("Refund Opportunity: ₹" + summary.getExtractedData().getRefundOpportunity(), greenFont));
                
                document.add(new Paragraph("\n5. Compliance Risks", sectionFont));
                if ("High".equalsIgnoreCase(summary.getExtractedData().getRiskScore())) {
                    document.add(new Paragraph("Risk Level: HIGH", highlightFont));
                    document.add(new Paragraph("Notice Detected: " + summary.getExtractedData().getNoticeType(), highlightFont));
                } else {
                    document.add(new Paragraph("Risk Level: " + summary.getExtractedData().getRiskScore(), normalFont));
                }
            } else {
                document.add(new Paragraph("No financial data extracted yet.", normalFont));
            }
            document.add(new Paragraph("\n"));

            // --- Arjun AI Recommendations ---
            document.add(new Paragraph("6. Arjun AI Recommendations", sectionFont));
            document.add(new Paragraph(summary.getRecommendedAction(), normalFont));
            document.add(new Paragraph("Please review the missing documents to proceed with filing.", normalFont));
            document.add(new Paragraph("\n"));
            
            // --- CA Notes ---
            document.add(new Paragraph("7. CA Notes", sectionFont));
            document.add(new Paragraph("This report was automatically generated by Arjun AI. Final review by a Chartered Accountant is required before submission to the Income Tax Department.", 
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, java.awt.Color.DARK_GRAY)));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
    }
}
