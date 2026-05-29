package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.GeminiAIResponse;
import com.caCommand.caCommand.entities.ChatSession;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.ChatState;
import com.caCommand.caCommand.repositories.ChatSessionRepository;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatBotService {

    private final ChatSessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final WhatsAppMediaService whatsappMediaService;
    private final StaffRepository staffRepository;
    private final GeminiService geminiService;
    private final LocationService locationService;

    public ChatBotService(ChatSessionRepository sessionRepository,
                          ClientRepository clientRepository,
                          TicketRepository ticketRepository,
                          WhatsAppMessageSender whatsappMessageSender,
                          WhatsAppMediaService whatsappMediaService,
                          StaffRepository staffRepository,
                          GeminiService geminiService,
                          LocationService locationService) {
        this.sessionRepository = sessionRepository;
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
        this.whatsappMessageSender = whatsappMessageSender;
        this.whatsappMediaService = whatsappMediaService;
        this.staffRepository = staffRepository;
        this.geminiService = geminiService;
        this.locationService = locationService;
    }

    // =========================================================
    // 🎯 MAIN ENTRY POINT
    // =========================================================
    public void processUserMessage(String phoneNumber, String messageType, String messageContent) {

        // STEP 1: Staff check - highest priority
        Staff staff = staffRepository.findByPhoneNumber(phoneNumber);
        if (staff != null) {
            handleStaffWorkflow(staff, messageType, messageContent);
            return;
        }

        // STEP 2: Client fetch or create
        Client client = clientRepository.findByPhoneNumber(phoneNumber);
        if (client == null) {
            client = new Client();
            client.setPhoneNumber(phoneNumber);
            client = clientRepository.save(client);
        }

        // STEP 3: Session fetch or create
        ChatSession session = sessionRepository.findById(phoneNumber).orElse(null);
        if (session == null) {
            session = new ChatSession();
            session.setPhoneNumber(phoneNumber);
            session.setCurrentState(ChatState.AI_CONVERSATION);
            session.setExtractedService(null);
            sessionRepository.save(session);
        }

        // STEP 4: Find the most recent active ticket for this client
        Ticket activeTicket = findActiveTicket(client);

        // =========================================================
        // 🔀 SMART ROUTING: Based on ticket status
        // =========================================================

        if (activeTicket != null) {
            // Client ke paas active ticket hai — route based on ticket's current status
            handleActiveTicketFlow(client, session, activeTicket, messageType, messageContent, phoneNumber);
        } else {
            // Koi active ticket nahi — AI se baat karo, service aur city collect karo
            handleNewClientFlow(client, session, messageType, messageContent, phoneNumber);
        }
    }

    // =========================================================
    // 🎟️ FLOW 1: Active Ticket exists — handle based on status
    // =========================================================
    private void handleActiveTicketFlow(Client client, ChatSession session, Ticket ticket,
                                        String messageType, String messageContent, String phoneNumber) {
        String status = ticket.getStatus();

        switch (status) {
            case "PENDING_ADMIN_APPROVAL":
                handlePendingApprovalState(ticket, messageType, messageContent, phoneNumber);
                break;

            case "ASSIGNED_TO_STAFF":
                handleAssignedToStaffState(ticket, messageType, messageContent, phoneNumber);
                break;

            case "PENDING_ADMIN_QC":
                handlePendingQCState(ticket, messageType, messageContent, phoneNumber);
                break;

            case "AWAITING_PAYMENT":
                handleAwaitingPaymentState(ticket, messageType, messageContent, phoneNumber);
                break;

            case "PAYMENT_RECEIVED":
                // Payment aane ke baad agar aur document bhej raha hai
                if ("document".equals(messageType) || "image".equals(messageType)) {
                    boolean isVerified = saveClientDocument(ticket, messageContent, phoneNumber, ticket.getServiceType() + " Official Document");
                    if(isVerified){
                        whatsappMessageSender.sendMessage(phoneNumber, "✅ Document Verified & Saved, thank you!");
                    } else {
                        whatsappMessageSender.sendMessage(phoneNumber, "⚠️ *AI Alert:* Aapne jo photo bheji hai wo galat document hai ya clear nahi hai. Kripya sahi document upload karein.");
                    }
                } else {
                    whatsappMessageSender.sendMessage(phoneNumber, "🔄 Payment verify ho chuki hai. Hamari team jaldi hi aage ki update degi.");
                }
                break;

            case "FINISHED":
            case "COMPLETED":
                whatsappMessageSender.sendMessage(phoneNumber,
                        "✅ Aapka pehla kaam complete ho chuka hai!\n\nNayi service ke liye batayein kya chahiye?");
                resetSession(session);
                break;

            default:
                whatsappMessageSender.sendMessage(phoneNumber,
                        "🔄 Aapki file process mein hai. Hamari team aapko jaldi update karegi.");
        }
    }

    // =========================================================
    // 📋 STATE: PENDING_ADMIN_APPROVAL
    // =========================================================
    private void handlePendingApprovalState(Ticket ticket, String messageType,
                                            String messageContent, String phoneNumber) {
        if ("document".equals(messageType) || "image".equals(messageType)) {
            // 🌟 AI VERIFICATION CALL
            boolean isVerified = saveClientDocument(ticket, messageContent, phoneNumber, ticket.getServiceType() + " Official Document");

            if (isVerified) {
                long docCount = ticket.getClientDocuments() != null ? ticket.getClientDocuments().split(",").length : 0;
                if (docCount <= 1) {
                    whatsappMessageSender.sendMessage(phoneNumber,
                            "✅ *Document Verified & Saved!*\n\nShukriya! Aapke documents hamari team ko mil gaye hain. Hamari CA team review karke aapko update degi. 🙏");
                } else {
                    whatsappMessageSender.sendMessage(phoneNumber, "✅ Document Verified & Saved, thank you!");
                }
            } else {
                // ❌ AI REJECTION MESSAGE
                whatsappMessageSender.sendMessage(phoneNumber,
                        "⚠️ *AI Alert:* Aapne jo photo bheji hai wo galat document hai ya clear nahi hai. Kripya sahi aur saaf document dobara upload karein.");
            }
        } else if ("text".equals(messageType)) {
            whatsappMessageSender.sendMessage(phoneNumber,
                    "🔄 Aapki request hamari team ke paas hai. Processing chal rahi hai, jaldi hi aapko update milegi.");
        } else if ("location".equals(messageType)) {
            handleLocationMessage(ticket.getClient(), messageContent, phoneNumber);
        }
    }

    // =========================================================
    // 👨‍💼 STATE: ASSIGNED_TO_STAFF
    // =========================================================
    private void handleAssignedToStaffState(Ticket ticket, String messageType,
                                            String messageContent, String phoneNumber) {
        String staffPhone = ticket.getAssignedStaff() != null ? ticket.getAssignedStaff().getPhoneNumber() : null;

        if ("document".equals(messageType) || "image".equals(messageType)) {
            boolean isVerified = saveClientDocument(ticket, messageContent, phoneNumber, ticket.getServiceType() + " Official Document");

            if (isVerified) {
                whatsappMessageSender.sendMessage(phoneNumber, "✅ *Document Verified!*\nAapka document CA team ko bhej diya gaya hai.");
                if (staffPhone != null) {
                    whatsappMessageSender.sendMessage(staffPhone, "📎 *Client (" + phoneNumber + ") ne naya document upload kiya hai.*\nType *STATUS* to view.");
                }
            } else {
                whatsappMessageSender.sendMessage(phoneNumber, "⚠️ *AI Alert:* Upload kiya gaya document match nahi hua. Kripya CA team dwara manga gaya sahi document bhejein.");
            }

        } else if ("text".equals(messageType)) {
            if (staffPhone != null) {
                whatsappMessageSender.sendMessage(staffPhone, "💬 *Client (" + phoneNumber + ") ka message:*\n👉 " + messageContent);
                whatsappMessageSender.sendMessage(phoneNumber, "✅ Aapka message CA team ko bhej diya gaya hai.");
            } else {
                whatsappMessageSender.sendMessage(phoneNumber, "🔄 Hamari team aapki file par kaam kar rahi hai.");
            }
        } else if ("location".equals(messageType)) {
            handleLocationMessage(ticket.getClient(), messageContent, phoneNumber);
        }
    }

    // =========================================================
    // 🔍 STATE: PENDING_ADMIN_QC
    // =========================================================
    private void handlePendingQCState(Ticket ticket, String messageType,
                                      String messageContent, String phoneNumber) {
        if ("document".equals(messageType) || "image".equals(messageType)) {
            boolean isVerified = saveClientDocument(ticket, messageContent, phoneNumber, ticket.getServiceType() + " Official Document");

            if (isVerified) {
                String staffPhone = ticket.getAssignedStaff() != null ? ticket.getAssignedStaff().getPhoneNumber() : null;
                if (staffPhone != null) {
                    whatsappMessageSender.sendMessage(staffPhone, "📎 *Client ne additional document bheja hai.*\nType *STATUS* to view.");
                }
                whatsappMessageSender.sendMessage(phoneNumber, "✅ Document mil gaya, thank you!");
            } else {
                whatsappMessageSender.sendMessage(phoneNumber, "⚠️ *AI Alert:* Document clear nahi hai. Kripya dobara bhejein.");
            }
        } else {
            whatsappMessageSender.sendMessage(phoneNumber, "⏳ *Almost Done!*\nAapka kaam final review stage mein hai. Thoda sa aur wait karein. 🙏");
        }
    }

    // =========================================================
    // 💰 STATE: AWAITING_PAYMENT
    // =========================================================
    private void handleAwaitingPaymentState(Ticket ticket, String messageType,
                                            String messageContent, String phoneNumber) {
        if ("text".equals(messageType)) {
            String msg = messageContent.toLowerCase().trim();
            if (msg.contains("paid") || msg.contains("done") || msg.contains("payment") || msg.contains("bhej diya")) {
                whatsappMessageSender.sendMessage(phoneNumber, "✅ *Payment Confirmation Received!*\nShukriya! Hamari team aapka payment verify karke aapka kaam complete kar degi.");
                ticket.setStatus("PAYMENT_RECEIVED");
                ticketRepository.save(ticket);
            } else {
                whatsappMessageSender.sendMessage(phoneNumber, "💳 *Payment Required*\nPayment ho jaane par *'Paid'* reply karein ya apna payment screenshot share karein.");
            }
        } else if ("document".equals(messageType) || "image".equals(messageType)) {
            // 🌟 PAYMENT SCREENSHOT VERIFICATION
            boolean isVerified = saveClientDocument(ticket, messageContent, phoneNumber, "Payment Screenshot or Transaction Receipt");

            if (isVerified) {
                whatsappMessageSender.sendMessage(phoneNumber, "✅ *Screenshot Verified!*\nHamari team payment final check karke aapko confirm karegi.");
                ticket.setStatus("PAYMENT_RECEIVED");
                ticketRepository.save(ticket);
            } else {
                whatsappMessageSender.sendMessage(phoneNumber, "⚠️ *AI Alert:* Yeh payment screenshot jaisa nahi lag raha. Kripya valid transaction receipt upload karein.");
            }
        }
    }

    // =========================================================
    // 🆕 FLOW 2: New client — No active ticket, use AI
    // =========================================================
    private void handleNewClientFlow(Client client, ChatSession session,
                                     String messageType, String messageContent, String phoneNumber) {

        if ("location".equals(messageType)) {
            handleLocationMessage(client, messageContent, phoneNumber);
            return;
        }

        if ("document".equals(messageType) || "image".equals(messageType)) {
            whatsappMessageSender.sendMessage(phoneNumber,
                    "Shukriya document ke liye! Par pehle mujhe batayein aapko kaunsi CA service chahiye? " +
                            "Jaise ITR filing, GST registration, company registration etc.");
            return;
        }

        if ("text".equals(messageType)) {
            String currentCity = client.getCity() != null ? client.getCity() : "";
            String currentService = session.getExtractedService() != null ? session.getExtractedService() : "";

            GeminiAIResponse aiResponse = geminiService.analyzeUserMessage(messageContent, currentCity, currentService);

            if (isValidValue(aiResponse.getCity())) {
                client.setCity(aiResponse.getCity());
                clientRepository.save(client);
            }

            if (isValidValue(aiResponse.getService())) {
                session.setExtractedService(aiResponse.getService());
            }

            boolean hasCity = client.getCity() != null && !client.getCity().isEmpty();
            boolean hasService = session.getExtractedService() != null && !session.getExtractedService().isEmpty();

            if (hasCity && hasService && session.getCurrentState() != ChatState.AWAITING_DOCS) {
                Ticket newTicket = new Ticket();
                newTicket.setClient(client);
                newTicket.setServiceType(session.getExtractedService());
                newTicket.setStatus("PENDING_ADMIN_APPROVAL");
                ticketRepository.save(newTicket);

                session.setCurrentState(ChatState.AWAITING_DOCS);
                sessionRepository.save(session);

                System.out.println("🎟️ Ticket created -> Service: " + session.getExtractedService() + " | City: " + client.getCity());
                whatsappMessageSender.sendMessage(phoneNumber, aiResponse.getReply_message());

            } else {
                sessionRepository.save(session);
                whatsappMessageSender.sendMessage(phoneNumber, aiResponse.getReply_message());
            }
        }
    }

    // =========================================================
    // 📍 LOCATION HANDLER (reusable)
    // =========================================================
    private void handleLocationMessage(Client client, String messageContent, String phoneNumber) {
        String[] coords = messageContent.split(",");
        if (coords.length == 2) {
            String detectedCity = locationService.getCityFromCoordinates(coords[0].trim(), coords[1].trim());
            if (detectedCity != null && !detectedCity.isEmpty()) {
                client.setCity(detectedCity);
                clientRepository.save(client);
                whatsappMessageSender.sendMessage(phoneNumber,
                        "📍 *Location Verified: " + detectedCity + "*\n\nPerfect! Aapki city note kar li gayi hai. Ab batayein, aapko kaunsi CA service chahiye?");
            }
        }
    }

    // =========================================================
    // 💾 DOCUMENT SAVE HELPER (UPDATED WITH AI OCR MAGIC 🤖)
    // =========================================================
    private boolean saveClientDocument(Ticket ticket, String mediaId, String phoneNumber, String expectedDocumentType) {
        String cloudinaryUrl = whatsappMediaService.downloadAndSaveMedia(mediaId, phoneNumber);
        if (cloudinaryUrl == null) return false;

        System.out.println("🤖 AI se verify karwa rahe hain: " + expectedDocumentType);
        boolean isValidDocument = geminiService.verifyDocumentType(cloudinaryUrl, expectedDocumentType);

        if (isValidDocument) {
            String currentDocs = ticket.getClientDocuments() != null ? ticket.getClientDocuments() : "";
            String newDocs = currentDocs.isEmpty() ? cloudinaryUrl : currentDocs + ", " + cloudinaryUrl;
            ticket.setClientDocuments(newDocs);
            ticketRepository.save(ticket);
            return true;
        } else {
            return false;
        }
    }

    // =========================================================
    // 🔍 FIND ACTIVE TICKET HELPER
    // =========================================================
    private Ticket findActiveTicket(Client client) {
        List<Ticket> activeTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getClient().getId().equals(client.getId()) &&
                        !("FINISHED".equals(t.getStatus()) || "COMPLETED".equals(t.getStatus())))
                .toList();
        return activeTickets.isEmpty() ? null : activeTickets.get(activeTickets.size() - 1);
    }

    // =========================================================
    // ✅ VALUE VALIDATOR HELPER
    // =========================================================
    private boolean isValidValue(String value) {
        return value != null && !value.trim().isEmpty() && !"NOT_PROVIDED".equalsIgnoreCase(value.trim());
    }

    // =========================================================
    // 🔄 SESSION RESET
    // =========================================================
    private void resetSession(ChatSession session) {
        session.setCurrentState(ChatState.AI_CONVERSATION);
        session.setExtractedService(null);
        sessionRepository.save(session);
    }

    // =========================================================
    // 👨‍💻 STAFF WORKFLOW
    // =========================================================
    private void handleStaffWorkflow(Staff staff, String messageType, String messageContent) {

        List<Ticket> activeTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getAssignedStaff() != null
                        && t.getAssignedStaff().getId().equals(staff.getId())
                        && ("ASSIGNED_TO_STAFF".equals(t.getStatus()) || "PENDING_ADMIN_QC".equals(t.getStatus())))
                .toList();

        if (activeTickets.isEmpty()) {
            whatsappMessageSender.sendMessage(staff.getPhoneNumber(),
                    "❌ Aapke paas abhi koi active assigned file nahi hai.");
            return;
        }

        Ticket workingTicket = activeTickets.get(0);
        String clientPhoneNumber = workingTicket.getClient().getPhoneNumber();

        if ("document".equals(messageType) || "image".equals(messageType)) {
            String savedFilePath = whatsappMediaService.downloadAndSaveMedia(messageContent, staff.getPhoneNumber());
            workingTicket.setStaffSubmittedDocument(savedFilePath);
            workingTicket.setStatus("PENDING_ADMIN_QC");
            ticketRepository.save(workingTicket);

            whatsappMessageSender.sendMessage(staff.getPhoneNumber(),
                    "✅ *Work Submitted for QC*\n\nBahut badhiya! Aapka document Admin ko final delivery ke liye bhej diya gaya hai.");
            return;
        }

        if ("text".equals(messageType)) {
            String upperContent = messageContent.toUpperCase().trim();
            String cleanCommand = upperContent.replace(" ", "");

            if (cleanCommand.startsWith("REQUEST:")) {
                String missingDocName = messageContent.substring(messageContent.indexOf(":") + 1).trim();
                String directClientMsg = "⚠️ *CA Team ko aapki madad chahiye*\n\nAapki *" + workingTicket.getServiceType() + "* file process karne ke liye:\n👉 *" + missingDocName + "* chahiye\n\nKripya yahan directly reply karein.";
                whatsappMessageSender.sendMessage(clientPhoneNumber, directClientMsg);
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "🚀 *Request Sent!* Client ko notify kar diya: " + missingDocName);
            }
            else if (cleanCommand.startsWith("QUERY:")) {
                String staffQuery = messageContent.substring(messageContent.indexOf(":") + 1).trim();
                String directClientQuery = "❓ *CA Team ka sawaal aapki file ke baare mein*\n\n💬 \"" + staffQuery + "\"\n\nKripya yahan reply karein.";
                whatsappMessageSender.sendMessage(clientPhoneNumber, directClientQuery);
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "🚀 *Query Sent!* Client ko notify kar diya.");
            }
            else if (cleanCommand.startsWith("PAYMENT:")) {
                String paymentDetails = messageContent.substring(messageContent.indexOf(":") + 1).trim();
                String paymentMsg = "💳 *Payment Required*\n\nAapki *" + workingTicket.getServiceType() + "* file ready hai!\n\n💰 *Amount:* " + paymentDetails + "\n\nPayment complete karne ke baad *'Paid'* reply karein ya payment screenshot share karein.";
                whatsappMessageSender.sendMessage(clientPhoneNumber, paymentMsg);

                workingTicket.setStatus("AWAITING_PAYMENT");
                ticketRepository.save(workingTicket);

                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ *Payment Request Sent!* Client ko payment details bhej diye gaye hain.");
            }
            else if (cleanCommand.equals("DONE")) {
                workingTicket.setStatus("COMPLETED");
                ticketRepository.save(workingTicket);

                whatsappMessageSender.sendMessage(clientPhoneNumber,
                        "🎉 *Kaam Ho Gaya!*\n\nAapki *" + workingTicket.getServiceType() + "* successfully complete ho gayi hai. Agar koi aur service chahiye toh batayein. Dhanyavaad! 🙏");
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Ticket marked as COMPLETED.");
            }
            else if (cleanCommand.equals("STATUS")) {
                Client c = workingTicket.getClient();
                String docList = workingTicket.getClientDocuments() != null ? workingTicket.getClientDocuments() : "Koi document nahi";
                String statusMsg = "📊 *Current File Status*\n\n• *Service:* " + workingTicket.getServiceType() + "\n• *Status:* " + workingTicket.getStatus() + "\n• *Client Phone:* " + c.getPhoneNumber() + "\n• *Client City:* " + (c.getCity() != null ? c.getCity() : "N/A") + "\n• *Documents:* " + docList + "\n\n*Commands:*\nREQUEST: [info] — client se maango\nQUERY: [question] — client se poochho\nPAYMENT: [amount] — payment request bhejo\nDONE — file complete mark karo";
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), statusMsg);
            }
            else {
                String helpMsg = "🤖 *Staff Commands*\n\n1️⃣ *STATUS* — Client ki file details dekho\n2️⃣ *REQUEST: [Info/OTP]* — Client se kuch maango\n3️⃣ *QUERY: [Question]* — Client se sawaal poochho\n4️⃣ *PAYMENT: [Amount]* — Payment request bhejo\n5️⃣ *DONE* — File complete mark karo\n6️⃣ *PDF/Image bhejo* — Final work submit karo";
                whatsappMessageSender.sendMessage(staff.getPhoneNumber(), helpMsg);
            }
        }
    }
}