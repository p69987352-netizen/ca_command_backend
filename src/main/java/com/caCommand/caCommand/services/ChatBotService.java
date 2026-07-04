package com.caCommand.caCommand.services;

import com.caCommand.caCommand.dtos.DocumentVerificationResult;
import com.caCommand.caCommand.entities.ChatSession;
import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.CustomDocumentRequest;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.ChatState;
import com.caCommand.caCommand.enums.TicketStatus;
import com.caCommand.caCommand.events.DocumentUploadedEvent;
import com.caCommand.caCommand.repositories.ChatSessionRepository;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.CustomDocumentRequestRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.services.GeminiService;
import com.caCommand.caCommand.services.LegacyDocumentExtractionService;
import com.caCommand.caCommand.services.LocationService;
import com.caCommand.caCommand.services.S3StorageService;
import com.caCommand.caCommand.services.WhatsAppMediaService;
import com.caCommand.caCommand.services.WhatsAppMessageSender;
import com.caCommand.caCommand.services.pipeline.PipelineOrchestrator;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatBotService {
    private static final Logger log = LoggerFactory.getLogger(ChatBotService.class);
    private static final String TEST_SERVICE = "ITR Filing";
    private static final List<String> ACTIVE_TICKET_STATUSES = List.of(TicketStatus.PENDING_ADMIN_APPROVAL.name(), TicketStatus.AWAITING_PAYMENT.name(), TicketStatus.PAYMENT_RECEIVED.name(), TicketStatus.PAYMENT_VERIFICATION_PENDING.name(), TicketStatus.IN_PROGRESS.name(), TicketStatus.ASSIGNED_TO_STAFF.name(), TicketStatus.PENDING_ADMIN_QC.name(), TicketStatus.CALL_PENDING.name(), TicketStatus.NORMAL_FLOW.name(), TicketStatus.WAITING_FOR_CLIENT_DOCUMENT.name(), TicketStatus.WAITING_FOR_ADMIN.name(), TicketStatus.UNDER_REVIEW.name(), "VALIDATING", "OCR_RUNNING", "CLASSIFYING", "EXTRACTING", "AI_ANALYSIS", "COMPLETED", "FAILED");
    private static final List<String> STAFF_ACTIVE_STATUSES = List.of(TicketStatus.ASSIGNED_TO_STAFF.name(), TicketStatus.PENDING_ADMIN_QC.name());
    private final ChatSessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;
    private final WhatsAppMessageSender whatsappMessageSender;
    private final WhatsAppMediaService whatsappMediaService;
    private final StaffRepository staffRepository;
    private final com.caCommand.caCommand.repositories.StaffSessionRepository staffSessionRepository;
    private final GeminiService geminiService;
    private final LocationService locationService;
    private final LegacyDocumentExtractionService documentExtractionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CustomDocumentRequestRepository customDocumentRequestRepository;
    private final S3StorageService s3StorageService;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final ApplicationEventPublisher eventPublisher;
    private final com.caCommand.caCommand.repositories.AttendanceRepository attendanceRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, ScheduledFuture<?>> pendingMessages = new ConcurrentHashMap();

    @Autowired
    public ChatBotService(ChatSessionRepository sessionRepository, ClientRepository clientRepository, TicketRepository ticketRepository, WhatsAppMessageSender whatsappMessageSender, WhatsAppMediaService whatsappMediaService, StaffRepository staffRepository, com.caCommand.caCommand.repositories.StaffSessionRepository staffSessionRepository, GeminiService geminiService, LocationService locationService, LegacyDocumentExtractionService documentExtractionService, SimpMessagingTemplate messagingTemplate, CustomDocumentRequestRepository customDocumentRequestRepository, S3StorageService s3StorageService, PipelineOrchestrator pipelineOrchestrator, ApplicationEventPublisher eventPublisher, com.caCommand.caCommand.repositories.AttendanceRepository attendanceRepository) {
        this.sessionRepository = sessionRepository;
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
        this.whatsappMessageSender = whatsappMessageSender;
        this.whatsappMediaService = whatsappMediaService;
        this.staffRepository = staffRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.geminiService = geminiService;
        this.locationService = locationService;
        this.documentExtractionService = documentExtractionService;
        this.messagingTemplate = messagingTemplate;
        this.customDocumentRequestRepository = customDocumentRequestRepository;
        this.s3StorageService = s3StorageService;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.eventPublisher = eventPublisher;
        this.attendanceRepository = attendanceRepository;
    }

    @PreDestroy
    public void cleanup() {
        this.scheduler.shutdown();
    }

    private void broadcastUpdate() {
        if (this.messagingTemplate != null) {
            this.messagingTemplate.convertAndSend("/topic/updates", (Object)Map.of("type", "CHAT_UPDATE", "timestamp", System.currentTimeMillis()));
        }
    }

    private Ticket saveAndBroadcast(Ticket ticket) {
        Ticket saved = (Ticket)this.ticketRepository.save(ticket);
        this.broadcastUpdate();
        return saved;
    }

    @Transactional
    public void processUserMessage(String phoneNumber, String messageType, String messageContent) {
        Staff staff = this.staffRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (staff != null) {
            this.handleStaffWorkflow(staff, messageType, messageContent);
            return;
        }
        if ("text".equals(messageType) && "hello testing".equalsIgnoreCase(messageContent.trim())) {
            Client client = this.clientRepository.findByPhoneNumber(phoneNumber).orElseGet(() -> this.createClient(phoneNumber));
            client.setName("Ajmer");
            client.setCity("Ajmer");
            client.setDob("21091976");
            client.setPan("AFXPT8550J");
            client.setItPassword("haresh1976@");
            client.setIncomeRange("BELOW_5L");
            this.clientRepository.save(client);
            ChatSession session = this.getOrCreateSession(phoneNumber);
            session.setClientName("Bhanu");
            session.setCurrentState(ChatState.SERVICE_SELECTION_SHOWN);
            this.sessionRepository.save(session);
            Ticket active = this.findActiveTicket(client);
            if (active != null) {
                active.setStatus(TicketStatus.TRASH.name());
                this.ticketRepository.save(active);
            }
            this.send(phoneNumber, session, "\ud83e\uddea *Testing Mode Activated*\n\nWelcome Bhanu (Ajmer).\nAll details auto-filled!\n\nPlease select your service:\n\n1\ufe0f\u20e3 ITR Filing\n2\ufe0f\u20e3 GST Services\n3\ufe0f\u20e3 Notice / Appeal\n4\ufe0f\u20e3 Tax Advisory");
            return;
        }
        Client client = this.clientRepository.findByPhoneNumber(phoneNumber).orElseGet(() -> this.createClient(phoneNumber));
        ChatSession session = this.getOrCreateSession(phoneNumber);
        Ticket activeTicket = this.findActiveTicket(client);
        if (activeTicket != null) {
            this.handleActiveTicketFlow(client, session, activeTicket, messageType, messageContent, phoneNumber);
        } else {
            this.handleNewClientFlow(client, session, messageType, messageContent, phoneNumber);
        }
    }

    private void handleNewClientFlow(Client client, ChatSession session, String messageType, String messageContent, String phoneNumber) {
        if (!"text".equals(messageType)) {
            this.send(phoneNumber, session, "Kripya pehle text message se apni details bhejein ya service select karein, documents uske baad bhejein.");
            return;
        }
        ChatState state = session.getCurrentState() == null ? ChatState.NEW : session.getCurrentState();
        String content = messageContent.trim();
        switch (state) {
            case NEW: {
                if (client.getName() != null && !client.getName().isBlank()) {
                    session.setClientName(client.getName());
                    session.setCurrentState(ChatState.SERVICE_SELECTION_SHOWN);
                    this.sessionRepository.save(session);
                    this.send(phoneNumber, session, "\ud83d\udcd6 " + getRandomGitaQuote() + "\n\nWelcome back " + client.getName() + " \ud83d\udc4b\nThis is ARJUN - the AI assistant to help you.\n\nHow can we assist you today?\n\n1\ufe0f\u20e3 ITR Filing\n2\ufe0f\u20e3 GST Services\n3\ufe0f\u20e3 Notice / Appeal\n4\ufe0f\u20e3 Tax Advisory");
                    break;
                }
                session.setCurrentState(ChatState.COLLECTING_NAME);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "\u2728 Welcome to Porwal CA");
                this.send(phoneNumber, session, "\ud83d\udcd6 " + getRandomGitaQuote() + "\n\nThis is ARJUN - the AI assistant to help you.");
                this.send(phoneNumber, session, "I'll help you with your tax and compliance requirements today.");
                this.send(phoneNumber, session, "To get started, may I know your full name?");
                break;
            }
            case COLLECTING_NAME: {
                if (content.length() > 2) {
                    session.setClientName(content);
                    session.setCurrentState(ChatState.COLLECTING_CITY);
                    this.sessionRepository.save(session);
                    this.send(phoneNumber, session, "Nice to meet you, " + content + ".\n\n\ud83d\udccd Which city are you currently based in?\n\nThis helps us assign the right CA expert for your case.");
                    break;
                }
                this.send(phoneNumber, session, "Could you please share your full name to proceed?");
                break;
            }
            case COLLECTING_CITY: {
                session.setClientCity(content);
                session.setCurrentState(ChatState.COLLECTING_PAN);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "Got it.\n\nNow, could you please share your **PAN Number**?");
                break;
            }
            case COLLECTING_PAN: {
                session.setClientPan(content.toUpperCase());
                session.setCurrentState(ChatState.COLLECTING_DOB);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "Thank you. Lastly, what is your **Date of Birth**? (Format: DD/MM/YYYY)");
                break;
            }
            case COLLECTING_DOB: {
                session.setClientDob(content);
                session.setCurrentState(ChatState.COLLECTING_PASSWORD);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "Great. Now please provide your **IT Portal Password** (it will be securely stored for our CA team to process your return).");
                break;
            }
            case COLLECTING_PASSWORD: {
                session.setClientItPassword(content);
                session.setCurrentState(ChatState.SERVICE_SELECTION_SHOWN);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "Perfect.\n\nPlease tell us how we can assist you today:\n\n1\ufe0f\u20e3 ITR Filing\n2\ufe0f\u20e3 GST Services\n3\ufe0f\u20e3 Notice / Appeal\n4\ufe0f\u20e3 Tax Advisory");
                break;
            }
            case SERVICE_SELECTION_SHOWN: {
                String selectedService = "";
                boolean isCallService = false;
                if (content.contains("1") || content.toLowerCase().contains("itr")) {
                    selectedService = TEST_SERVICE;
                    isCallService = false;
                } else if (content.contains("2") || content.toLowerCase().contains("gst")) {
                    selectedService = "GST Services";
                    isCallService = false;
                } else if (content.contains("3") || content.toLowerCase().contains("notice") || content.toLowerCase().contains("appeal")) {
                    selectedService = "Tax Notice / Appeal";
                    isCallService = false;
                } else if (content.contains("4") || content.toLowerCase().contains("advisory")) {
                    selectedService = "Tax Advisory";
                    isCallService = true;
                } else {
                    this.send(phoneNumber, session, "Kripya 1 se 4 ke beech koi option select karein.");
                    return;
                }
                if (this.isBlank(client.getName())) {
                    client.setName((String)(session.getClientName() != null ? session.getClientName() : "Client " + phoneNumber.substring(phoneNumber.length() - 4)));
                }
                if (this.isBlank(client.getCity()) && session.getClientCity() != null) {
                    client.setCity(session.getClientCity());
                }
                if (this.isBlank(client.getPan()) && session.getClientPan() != null) {
                    client.setPan(session.getClientPan());
                }
                if (this.isBlank(client.getDob()) && session.getClientDob() != null) {
                    client.setDob(session.getClientDob());
                }
                if (this.isBlank(client.getItPassword()) && session.getClientItPassword() != null) {
                    client.setItPassword(session.getClientItPassword());
                }
                this.clientRepository.save(client);
                if (isCallService) {
                    session.setExtractedService(selectedService);
                    session.setCurrentState(ChatState.AWAITING_CALL_SERVICE);
                    this.sessionRepository.save(session);
                    this.send(phoneNumber, session, "Could you please briefly describe your case or query?\n\nOur CA expert will review this before reaching out to you.");
                    break;
                } else if (!TEST_SERVICE.equals(selectedService)) {
                    // Non-ITR services: just say thank you and end
                    Ticket ticket = this.createTicket(client, selectedService, session);
                    ticket.setStatus(TicketStatus.CALL_PENDING.name());
                    ticket.setTicketCategory("CALL_SERVICE");
                    this.ticketRepository.save(ticket);
                    
                    session.setExtractedService(selectedService);
                    session.setCurrentState(ChatState.FINISHED);
                    this.sessionRepository.save(session);
                    
                    this.send(phoneNumber, session, "Thank you. Our tax expert will contact you shortly. Reference ID: " + ticket.getCaseId() + "\n\n\uD83D\uDCD6 " + getRandomGitaQuote());
                    break;
                }
                Ticket ticket = this.createTicket(client, selectedService, session);
                session.setExtractedService(selectedService);
                session.setCurrentState(ChatState.AWAITING_DOCS);
                this.sessionRepository.save(session);
                this.sendDocumentChecklist(phoneNumber, session, ticket, true);
                break;
            }
            case AWAITING_CALL_SERVICE: {
                Ticket callTicket = this.createTicket(client, session.getExtractedService(), session);
                callTicket.setStatus(TicketStatus.CALL_PENDING.name());
                callTicket.setTicketCategory("CALL_SERVICE");
                callTicket.setClientRequestLog(content);
                this.saveAndBroadcast(callTicket);
                session.setCurrentState(ChatState.FINISHED);
                this.sessionRepository.save(session);
                this.send(phoneNumber, session, "\u2705 Your " + session.getExtractedService() + " Consultation Has Been Scheduled\n\nOne of our tax specialists will review your case and reach out shortly.\nReference ID: " + callTicket.getCaseId() + "\n\n\uD83D\uDCD6 " + getRandomGitaQuote());
                break;
            }
            default: {
                if (client.getName() != null && !client.getName().isBlank()) {
                    session.setClientName(client.getName());
                    session.setCurrentState(ChatState.SERVICE_SELECTION_SHOWN);
                    this.sessionRepository.save(session);
                    this.send(phoneNumber, session, "Welcome back " + client.getName() + " \ud83d\udc4b\n\nThis is ARJUN - the AI assistant to help you\n\nHow can we assist you today?\n\n1\ufe0f\u20e3 ITR Filing\n2\ufe0f\u20e3 GST Services\n3\ufe0f\u20e3 Notice / Appeal\n4\ufe0f\u20e3 Tax Advisory\n\n\uD83D\uDCD6 " + getRandomGitaQuote());
                } else {
                    this.sendWelcomeNew(phoneNumber, session);
                    session.setCurrentState(ChatState.NEW);
                    this.sessionRepository.save(session);
                }
            }
        }
    }

    private void sendWelcomeNew(String phoneNumber, ChatSession session) {
        this.send(phoneNumber, session, "\ud83d\udc4b Welcome to Porwal CA");
        this.send(phoneNumber, session, "\ud83d\udcd6 " + getRandomGitaQuote() + "\n\nThis is ARJUN - the AI assistant to help you.");
        this.send(phoneNumber, session, "I'll help you with your tax and compliance requirements today.");
        this.send(phoneNumber, session, "To get started, may I know your full name?");
    }

    private void handleActiveTicketFlow(Client client, ChatSession session, Ticket ticket, String messageType, String messageContent, String phoneNumber) {
        if ("location".equals(messageType)) {
            this.handleLocationMessage(client, session, messageContent, phoneNumber);
            return;
        }
        if ("document".equals(messageType) || "image".equals(messageType)) {
            String status = ticket.getStatus();
            if (TicketStatus.PENDING_ADMIN_APPROVAL.name().equals(status) || ChatState.AWAITING_DOCS.equals((Object)session.getCurrentState()) || TicketStatus.NORMAL_FLOW.name().equals(status)) {
                this.verifyAndSaveClientDocument(session, ticket, messageContent, phoneNumber);
            } else if (TicketStatus.WAITING_FOR_CLIENT_DOCUMENT.name().equals(status)) {
                this.handleCustomDocumentUpload(session, ticket, messageContent, phoneNumber);
            } else if (TicketStatus.AWAITING_PAYMENT.name().equals(status)) {
                ticket.setPaymentProofUrl(messageContent);
                ticket.setStatus(TicketStatus.PAYMENT_VERIFICATION_PENDING.name());
                this.saveAndBroadcast(ticket);
                this.send(phoneNumber, session, "Screenshot receive ho gaya! \u2705\n\nHumari admin team payment verify karke aapka kaam shuru kar degi.\nAapko WhatsApp par update milega.\n");
            } else {
                this.send(phoneNumber, session, "\u2705 Document receive ho gaya. Team check karegi.");
            }
            return;
        }
        if (!"text".equals(messageType)) {
            this.sendDocumentChecklist(phoneNumber, session, ticket, false);
            return;
        }
        if ("REQUESTED".equals(ticket.getCredentialStatus()) && "text".equals(messageType) && this.looksLikeCredentials(messageContent)) {
            ticket.setCredentialData(messageContent.trim());
            ticket.setCredentialStatus("RECEIVED");
            ticket.setCredentialReceivedAt(LocalDateTime.now());
            this.saveAndBroadcast(ticket);
            String clientName = client.getName() != null ? client.getName() : "";
            this.send(phoneNumber, session, String.format("%s, credentials receive ho gaye! \u2705\n\nHumari team aapki file process karega.\nKuch time mein update milega. \ud83d\ude4f\n", clientName));
            log.info("Credentials received for ticket={} from phone={}", (Object)ticket.getId(), (Object)phoneNumber);
            return;
        }
        this.updateLanguagePreference(session, messageContent);
        String status = ticket.getStatus();
        if (TicketStatus.CALL_PENDING.name().equals(status)) {
            this.send(phoneNumber, session, "Aapki request already receive ho gayi hai. \u2705\n\nHamare CA expert 24 ghante ke andar aapko call karenge.\nUrgent note ho to yahin bhej dein, main team ko forward kar dunga.\n");
            ticket.setClientRequestLog(this.appendLog(ticket.getClientRequestLog(), messageContent));
            this.saveAndBroadcast(ticket);
            return;
        }
        if (TicketStatus.PENDING_ADMIN_APPROVAL.name().equals(status)) {
            List<String> missing = this.missingDocuments(ticket, session);
            if (!missing.isEmpty()) {
                this.send(phoneNumber, session, String.format("%s, aapki file review queue mein hai.\n\nLekin kuch documents abhi bhi pending hain:\n%s\n\nPlease clear photo ya PDF bhejein.\n", this.nullToDefault(client.getName(), ""), this.bulletList(missing)));
            } else {
                this.send(phoneNumber, session, String.format("%s, aapke saare documents submit ho gaye hain! \u2705\n\nPhorwal CA Firm team review kar rahi hai.\nFee aur payment link jald hi WhatsApp par milega.\n", this.nullToDefault(client.getName(), "")));
            }
            return;
        }
        if (TicketStatus.AWAITING_PAYMENT.name().equals(status)) {
            String msg = messageContent.toLowerCase(Locale.ROOT).trim();
            if (!(msg.length() <= 5 || msg.contains("hi") || msg.contains("hello") || msg.contains("help"))) {
                ticket.setPaymentProofUrl("TEXT: " + messageContent);
                ticket.setStatus(TicketStatus.PAYMENT_VERIFICATION_PENDING.name());
                this.saveAndBroadcast(ticket);
                this.send(phoneNumber, session, String.format("%s, detail receive ho gayi! \ud83d\ude4f\n\nHumari admin team payment verify karke aapka kaam shuru kar degi.\nAapko WhatsApp par update milega.\n", this.nullToDefault(client.getName(), "")));
            } else {
                this.send(phoneNumber, session, String.format("%s, payment abhi pending hai.\n\nKripya upar diye gaye QR code par payment karein aur yahan screenshot bhejein. \ud83d\udcb3\n\nProblem ho to reply karo \u2014 team help karegi.\n", this.nullToDefault(client.getName(), "")));
            }
            return;
        }
        if (TicketStatus.PAYMENT_VERIFICATION_PENDING.name().equals(status)) {
            this.send(phoneNumber, session, "Aapki payment ki verification admin team kar rahi hai. Kripya thoda intezaar karein. \ud83d\ude4f");
            return;
        }
        if (TicketStatus.PAYMENT_RECEIVED.name().equals(status)) {
            this.send(phoneNumber, session, String.format("%s, payment confirm ho gaya! \u2705\n\nTeam final verification ke baad kaam start karegi.\nJald hi update milega.\n", this.nullToDefault(client.getName(), "")));
            return;
        }
        if (TicketStatus.IN_PROGRESS.name().equals(status)) {
            this.send(phoneNumber, session, String.format("%s, aapka %s kaam chal raha hai! \ud83c\udfc3\n\nTeam actively work kar rahi hai.\nKoi specific sawaal ho to yahin type karo.\n", this.nullToDefault(client.getName(), ""), ticket.getServiceType()));
            return;
        }
        if (TicketStatus.ASSIGNED_TO_STAFF.name().equals(status)) {
            this.forwardClientMessageToStaff(ticket, client, phoneNumber, messageContent);
            return;
        }
        if (TicketStatus.PENDING_ADMIN_QC.name().equals(status)) {
            this.send(phoneNumber, session, String.format("%s, aapka %s final quality check mein hai! \ud83d\udd0d\n\nThoda wait karo \u2014 final document ready hote hi yahin share kar diya jayega.\n", this.nullToDefault(client.getName(), ""), ticket.getServiceType()));
            return;
        }
        if (TicketStatus.FINISHED.name().equals(status) || TicketStatus.COMPLETED.name().equals(status)) {
            this.resetSession(session);
            this.send(phoneNumber, session, String.format("%s, aapka previous %s complete ho chuka hai! \ud83c\udf89\n\nNayi service ke liye batao \u2014 Phorwal CA Firm hamesha ready hai.\n\n\uD83D\uDCD6 %s", this.nullToDefault(client.getName(), ""), ticket.getServiceType(), getRandomGitaQuote()));
            return;
        }
        this.sendDocumentChecklist(phoneNumber, session, ticket, false);
    }

    private void handleCustomDocumentUpload(ChatSession session, Ticket ticket, String mediaId, String phoneNumber) {
        String cloudinaryUrl = this.whatsappMediaService.downloadAndSaveMedia(mediaId, phoneNumber);
        if (cloudinaryUrl == null) {
            this.send(phoneNumber, session, "\u26a0 Document download failed. Please send again.");
            return;
        }
        List<CustomDocumentRequest> requests = this.customDocumentRequestRepository.findByTicketIdAndStatus(ticket.getId(), "PENDING");
        if (requests.isEmpty()) {
            this.send(phoneNumber, session, "\u2705 Document received. Our team will review it.");
            return;
        }
        CustomDocumentRequest req = requests.get(0);
        DocumentVerificationResult result = this.geminiService.verifyCustomDocument(cloudinaryUrl, req.getDocumentName());
        if (!result.valid()) {
            this.send(phoneNumber, session, String.format("\u26a0 Uploaded document does not match requested item (%s).\n\nReason: %s", req.getDocumentName(), result.reason()));
            return;
        }
        req.setStatus("FULFILLED");
        req.setFulfilledAt(LocalDateTime.now());
        this.customDocumentRequestRepository.save(req);
        this.appendClientDocument(ticket, req.getDocumentName(), cloudinaryUrl);
        ticket.setStatus(TicketStatus.UNDER_REVIEW.name());
        this.saveAndBroadcast(ticket);
        this.send(phoneNumber, session, String.format("\u2705 %s received and verified.\n\nYour case has been updated successfully.", req.getDocumentName()));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void verifyAndSaveClientDocument(ChatSession session, Ticket ticket, String messageContent, String phoneNumber) {
        String mediaId = messageContent;
        String filename = "";
        if (messageContent.contains("|")) {
            String[] parts = messageContent.split("\\|", 2);
            mediaId = parts[0];
            filename = parts[1];
        }

        String cloudinaryUrl = this.whatsappMediaService.downloadAndSaveMedia(mediaId, phoneNumber);
        if (cloudinaryUrl == null) {
            this.send(phoneNumber, session, "? Document download/upload fail ho gaya. Please clear photo/PDF dobara bhejein.");
            return;
        }
        
        DocumentVerificationResult result;
        String upperFileName = filename.toUpperCase();
        if (upperFileName.contains("TIS")) {
            result = new DocumentVerificationResult(true, "TIS Statement (PDF)", "TIS Statement identified by filename");
        } else if (upperFileName.contains("AIS")) {
            result = new DocumentVerificationResult(true, "AIS Statement (PDF)", "AIS Statement identified by filename");
        } else if (upperFileName.contains("26AS")) {
            result = new DocumentVerificationResult(true, "Form 26AS", "Form 26AS identified by filename");
        } else {
            result = this.geminiService.verifyClientDocument(cloudinaryUrl, ticket.getServiceType(), false);
        }

        if (!result.valid()) {
            this.send(phoneNumber, session, "? " + result.reason() + "\n\nKripya sahi document bhejein.");
            return;
        }
        String verifiedDoc = this.geminiService.canonicalizeDocumentName(result.documentType());
        String string = ticket.getId().toString().intern();
        synchronized (string) {
            Ticket freshTicket = this.ticketRepository.findByIdWithPessimisticLock(ticket.getId()).orElse(ticket);
            LinkedHashMap<Object, String> collectedDocs = new LinkedHashMap<Object, String>();
            if (freshTicket.getClientDocuments() != null && !freshTicket.getClientDocuments().isEmpty()) {
                String[] lines;
                for (String line : lines = freshTicket.getClientDocuments().split("\n")) {
                    String[] parts = line.split(" :: ");
                    if (parts.length < 1) continue;
                    String name = parts[0].trim().replace("[\"", "").replace("\"]", "");
                    String url = parts.length >= 2 ? parts[1].trim() : "";
                    collectedDocs.put(name, url);
                }
            }
            String uniqueKey = verifiedDoc + "##" + UUID.randomUUID().toString().substring(0, 8);
            collectedDocs.put(uniqueKey, cloudinaryUrl);
            StringBuilder sbDocs = new StringBuilder();
            ArrayList<String> collected = new ArrayList<String>();
            for (Map.Entry entry : collectedDocs.entrySet()) {
                sbDocs.append((String)entry.getKey()).append(" :: ").append((String)entry.getValue()).append("\n");
                collected.add((String)entry.getKey());
            }
            freshTicket.setClientDocuments(sbDocs.toString());
            this.saveAndBroadcast(freshTicket);
        }
        Runnable sendTask = () -> {
            try {
                Ticket latestTicket = this.ticketRepository.findById(ticket.getId()).orElse(ticket);
                List<String> reqDocs = this.geminiService.getRequiredDocuments(latestTicket.getServiceType());
                java.util.Set<String> coll = new java.util.LinkedHashSet<String>();
                if (latestTicket.getClientDocuments() != null && !latestTicket.getClientDocuments().isEmpty()) {
                    for (String line : latestTicket.getClientDocuments().split("\n")) {
                        String[] parts = line.split(" :: ");
                        if (parts.length < 1) continue;
                        String name = parts[0].trim().replace("[\"", "").replace("\"]", "");
                        if (name.contains("##")) {
                            name = name.substring(0, name.indexOf("##"));
                        }
                        coll.add(name);
                    }
                }
                ArrayList<String> miss = new ArrayList<String>(reqDocs);
                miss.removeAll(coll);
                StringBuilder sbuild = new StringBuilder();
                sbuild.append("\u2705 Document received and verified.\n\n");
                sbuild.append("\ud83d\udcca *Document Status*\n\n");
                sbuild.append("*Received:*\n");
                for (String c : coll) {
                    sbuild.append("\u2713 ").append(c).append("\n");
                }
                if (!miss.isEmpty()) {
                    sbuild.append("\n*Missing:*\n");
                    for (String m : miss) {
                        sbuild.append("\u26a0\ufe0f ").append(m).append("\n");
                    }
                }
                this.send(phoneNumber, session, sbuild.toString());
            }
            catch (Exception e) {
                log.error("Error sending debounced message", (Throwable)e);
            }
        };
        ScheduledFuture<?> existing = this.pendingMessages.get(phoneNumber);
        if (existing != null) {
            existing.cancel(false);
        }
        this.pendingMessages.put(phoneNumber, this.scheduler.schedule(sendTask, 4L, TimeUnit.SECONDS));
        this.eventPublisher.publishEvent((ApplicationEvent)new DocumentUploadedEvent(this, ticket.getId().toString(), phoneNumber));
    }

    private String bulletListWarning(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("\u26a0 ").append(item).append("\n");
        }
        return sb.toString().trim();
    }

    private void sendWelcome(String phoneNumber, ChatSession session, Client client) {
        String name = client.getName();
        String prefix = "\ud83d\ude4f Namaste" + (this.isBlank(name) ? "!" : " " + name + "!") + "\n" +
                        "Phorwal CA Firm mein aapka swagat hai.\n" +
                        "This is ARJUN - the AI assistant to help you\n\n";
        String suffix = "\n\n\uD83D\uDCD6 " + getRandomGitaQuote();
        
        if (!this.isBlank(name)) {
            this.send(phoneNumber, session, prefix + "Kaunsi CA service chahiye?\n\n\u2022 ITR Filing\n\u2022 GST Registration / GST Return\n\u2022 Balance Sheet / Audit\n\u2022 Company Registration\n\u2022 PAN Card Application\n\u2022 TDS Return Filing" + suffix);
        } else {
            this.send(phoneNumber, session, prefix + "Apna naam batao please \u2014 taaki main aapko personally guide kar sakoon." + suffix);
        }
    }

    private void sendDocumentChecklist(String phoneNumber, ChatSession session, Ticket ticket, boolean newlyCreated) {
        String message = this.getDetailedDocumentTemplate(ticket);
        this.send(phoneNumber, session, message);
        this.send(phoneNumber, session, "\u26a0\ufe0f *Please submit your documents one by one to avoid confusion.*");
    }

    private String getDetailedDocumentTemplate(Ticket ticket) {
        String serviceType = ticket.getServiceType();
        String caseIdStr = ticket.getCaseId() != null ? ticket.getCaseId() : "Pending";
        String clientName = ticket.getClient() != null ? this.nullToDefault(ticket.getClient().getName(), "") : "";
        String nameStr = this.isBlank(clientName) ? "Client" : clientName;
        
        switch (serviceType) {
            case "ITR Filing": {
                return String.format(
                        "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                        "\ud83d\udcc4 ITR Filing\n" +
                        "Reference %s\n" +
                        "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                        "Required\n" +
                        "\u2705 PAN\n" +
                        "\u2705 Aadhaar\n" +
                        "\u2705 Form 16\n" +
                        "\u2b1c AIS\n" +
                        "\u2b1c TIS\n" +
                        "\u2b1c 26AS\n" +
                        "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                        "\u26a0\ufe0f Important\n" +
                        "Upload only original PDF downloaded from Income Tax Portal.\n\n" +
                        "Do NOT send\n" +
                        "\u274c Screenshot\n" +
                        "\u274c Scan Copy\n" +
                        "\u274c Printed Photo\n" +
                        "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n" +
                        "\ud83d\udce5 Income Tax Portal\n" +
                        "https://www.incometax.gov.in\n",
                        caseIdStr
                );
            }
            case "Tax Notice / Appeal": {
                return String.format("\ud83d\udccb %s, Tax Notice / Appeal review ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 Notice PDF\n\u2713 Assessment Order\n\u2713 Previous ITR Copy\n\u2713 Previous Response Submitted (if any)\n\nAdditional Documents:\n\u2713 Demand Order\n\u2713 Appeal Order\n\u2713 Supporting Documents\n\u2713 CA Correspondence\n\n\ud83d\udcce Agar koi document available nahi hai to jo available ho wahi upload karein.\n\ud83e\udd16 Arjun AI notice risk, due dates aur suggested actions identify karega.\n", nameStr);
            }
            case "GST Services": {
                return String.format("\ud83d\udccb %s, GST Services ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 GST Certificate\n\u2713 GSTR-1\n\u2713 GSTR-3B\n\u2713 Sales Report\n\u2713 Purchase Report\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
            case "Audit & Compliance": {
                return String.format("\ud83d\udccb %s, Audit & Compliance ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 Financial Statements\n\u2713 Trial Balance\n\u2713 Profit & Loss Statement\n\u2713 Balance Sheet\n\u2713 GST Returns\n\u2713 Previous Audit Report\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
            case "Financial Planning": {
                return String.format("\ud83d\udccb %s, Financial Planning ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 Income Details\n\u2713 Existing Investments\n\u2713 Insurance Details\n\u2713 Loan Details\n\u2713 Bank Statements\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
            case "Business Registration": {
                return String.format("\ud83d\udccb %s, Business Registration ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 PAN Card\n\u2713 Aadhaar Card\n\u2713 Passport Size Photo\n\u2713 Mobile Number\n\u2713 Email ID\n\u2713 Business Address Proof\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
            case "Tax Advisory": {
                return String.format("\ud83d\udccb %s, Tax Advisory ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 Query Description\n\nOptional:\n\u2713 AIS Statement\n\u2713 TIS Statement\n\u2713 Previous ITR\n\u2713 Supporting Documents\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
            case "Legal Assistance (Arjun AI)": {
                return String.format("\ud83d\udccb %s, Legal Assistance ke liye following documents upload karein:\n\nRequired Documents:\n\u2713 Legal Notice / Order\n\u2713 Relevant Agreements\n\u2713 Supporting Documents\n\nOptional:\n\u2713 Previous Correspondence\n\u2713 Court/Authority Orders\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr);
            }
        }
        return String.format("\ud83d\udccb %s, %s ke liye documents upload karein:\n\nRequired Documents:\n%s\n\n\ud83d\udcce Aap documents photo ya PDF format mein upload kar sakte hain.\n", nameStr, serviceType, this.requiredDocsText(new Ticket(), false));
    }

    private void handleStaffWorkflow(Staff staff, String messageType, String messageContent) {
        String input = messageContent.trim();
        String inputUpper = input.toUpperCase(Locale.ROOT);
        
        java.time.LocalDate today = java.time.LocalDate.now();
        com.caCommand.caCommand.entities.Attendance attendance = this.attendanceRepository.findByStaffAndAttendanceDate(staff, today).orElse(null);

        com.caCommand.caCommand.entities.StaffSession staffSession = this.staffSessionRepository.findByStaffId(staff.getId())
            .orElseGet(() -> {
                com.caCommand.caCommand.entities.StaffSession s = new com.caCommand.caCommand.entities.StaffSession();
                s.setStaffId(staff.getId());
                return s;
            });

        boolean hasActiveSession = staffSession.getActiveCaseId() != null 
                && staffSession.getExpiresAt() != null 
                && staffSession.getExpiresAt().isAfter(LocalDateTime.now());

        // Attendance Logic - ONLY if not in an active case session
        if (attendance == null && !hasActiveSession) {
            if ("image".equals(messageType)) {
                String savedFilePath = this.whatsappMediaService.downloadAndSaveMedia(messageContent, staff.getPhoneNumber());
                if (savedFilePath != null) {
                    com.caCommand.caCommand.entities.Attendance newAttendance = new com.caCommand.caCommand.entities.Attendance();
                    newAttendance.setStaff(staff);
                    newAttendance.setAttendanceDate(today);
                    newAttendance.setStatus(com.caCommand.caCommand.enums.AttendanceStatus.PRESENT);
                    newAttendance.setPhotoUrl(savedFilePath);
                    this.attendanceRepository.save(newAttendance);
                    this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Aapki aaj ki attendance MARK ho gayi hai (PRESENT). Have a great day at work! 🏢");
                    return;
                }
            } else if ("text".equals(messageType) && inputUpper.startsWith("NO")) {
                com.caCommand.caCommand.entities.Attendance newAttendance = new com.caCommand.caCommand.entities.Attendance();
                newAttendance.setStaff(staff);
                newAttendance.setAttendanceDate(today);
                newAttendance.setStatus(com.caCommand.caCommand.enums.AttendanceStatus.ABSENT);
                String reason = input.substring(2).trim();
                if (!reason.isEmpty()) {
                    newAttendance.setReason(reason);
                }
                this.attendanceRepository.save(newAttendance);
                this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "❌ Aapki aaj ki attendance ABSENT mark kar di gayi hai. Admin has been informed.");
                return;
            }
        }

        // Global Commands
        if (inputUpper.equals("LIST")) {
            List<Ticket> activeTickets = this.ticketRepository.findByAssignedStaffIdAndStatusIn(staff.getId(), STAFF_ACTIVE_STATUSES);
            if (activeTickets.isEmpty()) {
                this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "ℹ️ Aapke paas abhi koi active assigned file nahi hai.");
                return;
            }
            StringBuilder sb = new StringBuilder("📋 *Pending Dashboard*\n\n");
            for (Ticket t : activeTickets) {
                String clientName = t.getClient() != null ? this.nullToDefault(t.getClient().getName(), t.getClient().getPhoneNumber()) : "-";
                sb.append(String.format("🔹 *%s* | %s\n", t.getCaseId() != null ? t.getCaseId() : "Pending", t.getServiceType()));
                sb.append(String.format("   Client: %s\n", clientName));
                sb.append(String.format("   Docs: %s\n", this.requiredDocsText(t, false).replaceAll("\n", ", ")));
            }
            sb.append("\nType *SELECT CASE-XXXX* to open a file and chat with the client.");
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), sb.toString());
            return;
        }

        if (inputUpper.startsWith("SELECT ")) {
            String caseId = inputUpper.substring(7).trim();
            Ticket ticket = this.ticketRepository.findByCaseId(caseId).orElse(null);
            if (ticket == null || !staff.getId().equals(ticket.getAssignedStaff() != null ? ticket.getAssignedStaff().getId() : null)) {
                this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "❌ Invalid Case ID or not assigned to you.");
                return;
            }
            staffSession.setActiveCaseId(caseId);
            staffSession.setSelectedAt(LocalDateTime.now());
            staffSession.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            this.staffSessionRepository.save(staffSession);
            
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Selected " + caseId + "\n\n" + this.staffStatusMessage(ticket));
            return;
        }

        // Generic Greetings
        if (inputUpper.equals("HI") || inputUpper.equals("HELLO") || inputUpper.equals("HEY") || inputUpper.equals("START") || inputUpper.equals("HII")) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), 
                "👋 Hello " + staff.getName() + "!\n\n" +
                "Welcome to the CA Command Staff Portal.\n\n" +
                "📌 Type *LIST* to view your assigned tasks.\n" +
                "📌 Send a *photo* to mark your daily attendance.\n" +
                "📌 Type *NO <reason>* (e.g., NO sick) if you are absent today.");
            return;
        }

        // Session validation for Context-aware commands
        if (staffSession.getActiveCaseId() == null || staffSession.getExpiresAt() == null || staffSession.getExpiresAt().isBefore(LocalDateTime.now())) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "⚠️ Session expired or no case selected.\nPlease type *LIST* and then *SELECT CASE-XXXX*.");
            return;
        }
        
        Ticket workingTicket = this.ticketRepository.findByCaseId(staffSession.getActiveCaseId()).orElse(null);
        if (workingTicket == null) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "❌ Case not found.");
            return;
        }
        
        // Refresh session
        staffSession.setSelectedAt(LocalDateTime.now());
        staffSession.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        staffSession.setLastCommand(inputUpper);
        this.staffSessionRepository.save(staffSession);

        String clientPhoneNumber = workingTicket.getClient().getPhoneNumber();
        String clientDisplay = this.nullToDefault(workingTicket.getClient().getName(), clientPhoneNumber);

        if ("document".equals(messageType) || "image".equals(messageType)) {
            String savedFilePath = this.whatsappMediaService.downloadAndSaveMedia(messageContent, staff.getPhoneNumber());
            if (savedFilePath == null) {
                this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "❌ Document upload fail ho gaya. Dobara try karo.");
                return;
            }
            workingTicket.setStaffSubmittedDocument(savedFilePath);
            workingTicket.setStatus(TicketStatus.PENDING_ADMIN_QC.name());
            workingTicket.setProgressPercent(90);
            this.saveAndBroadcast(workingTicket);
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Work submitted for admin QC review for " + workingTicket.getCaseId());
            
            // Auto-archive session
            staffSession.setActiveCaseId(null);
            this.staffSessionRepository.save(staffSession);
            return;
        }

        if (inputUpper.equals("INFO") || inputUpper.equals("STATUS")) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), this.staffStatusMessage(workingTicket));
        } else if (inputUpper.equals("NEED")) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "Select document to request:\n1. PAN Card\n2. Aadhaar Card\n3. AIS\n4. TIS\n5. Form 16\n6. Other\n\nReply with *NEED [Number]* or *NEED [Doc Name]*");
        } else if (inputUpper.startsWith("NEED ")) {
            String needed = input.substring(5).trim();
            switch (needed) {
                case "1": needed = "PAN Card"; break;
                case "2": needed = "Aadhaar Card"; break;
                case "3": needed = "AIS"; break;
                case "4": needed = "TIS"; break;
                case "5": needed = "Form 16"; break;
            }
            String clientMsg = String.format("📋 CA team ko aapki file ke liye ek document ki zarurat hai:\n\n*Document:* %s\n\nKripya yahi WhatsApp par uski photo/PDF bhej dijiye.", needed);
            this.whatsappMessageSender.sendMessage(clientPhoneNumber, clientMsg);
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Document request sent to client: " + clientDisplay);
        } else if (inputUpper.startsWith("ASK ")) {
            String query = input.substring(4).trim();
            String clientMsg = String.format("❓ CA team ka sawaal:\n\n%s\n\nPlease yahin reply karein.", query);
            this.whatsappMessageSender.sendMessage(clientPhoneNumber, clientMsg);
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Query sent to " + clientDisplay);
        } else if (inputUpper.startsWith("NOTE ")) {
            String note = input.substring(5).trim();
            workingTicket.setStaffUpdate(this.appendLog(workingTicket.getStaffUpdate(), note));
            this.saveAndBroadcast(workingTicket);
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "✅ Internal note saved to timeline.");
        } else if (inputUpper.startsWith("DONE") || inputUpper.startsWith("COMPLETE")) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "To mark DONE, please upload the final PDF/Image directly here in this chat.");
        } else if (inputUpper.equals("HELP")) {
            this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "🛠 *Commands:*\nLIST - Show pending\nSELECT CASE-XXXX - Open file\nINFO - Case details\nNEED [doc] - Request doc\nASK [query] - Ask client\nNOTE [msg] - Add internal note\nDONE - Upload final work");
        } else {
             this.whatsappMessageSender.sendMessage(staff.getPhoneNumber(), "⚠️ Invalid command. Type HELP for commands.");
        }
    }

    private void handleLocationMessage(Client client, ChatSession session, String messageContent, String phoneNumber) {
        String[] coords = messageContent.split(",");
        if (coords.length != 2) {
            this.send(phoneNumber, session, "\ud83d\udccd Location read nahi ho payi. Please apni city name type karo, jaise \"Ajmer\".");
            return;
        }
        String detectedCity = this.locationService.getCityFromCoordinates(coords[0].trim(), coords[1].trim());
        if (this.isBlank(detectedCity)) {
            this.send(phoneNumber, session, "\ud83d\udccd City detect nahi ho payi. Please apni city type karo.");
            return;
        }
        client.setCity(detectedCity);
        this.clientRepository.save(client);
        if (!this.isBlank(session.getExtractedService())) {
            Ticket ticket = this.findActiveTicket(client);
            if (ticket == null) {
                ticket = this.createTicket(client, session.getExtractedService(), session);
                session.setCurrentState(ChatState.AWAITING_DOCS);
                session.setVerifiedDocumentTypes("");
                this.sessionRepository.save(session);
            }
            this.sendDocumentChecklist(phoneNumber, session, ticket, true);
        } else {
            String name = this.nullToDefault(client.getName(), "");
            this.send(phoneNumber, session, String.format("\ud83d\udccd %s, aap %s se hain \u2014 noted!\n\nAb batao kaunsi CA service chahiye:\nITR Filing, GST, Balance Sheet, Company Registration, etc.\n", this.isBlank(name) ? "" : name + " ", detectedCity));
        }
    }

    private Ticket createTicket(Client client, String serviceType) {
        return this.createTicket(client, serviceType, null);
    }

    private Ticket createTicket(Client client, String serviceType, ChatSession session) {
        Ticket ticket = new Ticket();
        ticket.setClient(client);
        ticket.setServiceType(serviceType);
        ticket.setStatus(TicketStatus.PENDING_ADMIN_APPROVAL.name());
        
        String dateStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        String randomNum = String.format("%04d", new java.util.Random().nextInt(10000));
        ticket.setCaseId("CASE-" + dateStr + "-" + randomNum);
        ticket.setCaseStage(com.caCommand.caCommand.enums.CaseStage.ONBOARDING);
        
        Ticket saved = this.saveAndBroadcast(ticket);
        log.info("Created ticket caseId={} client={} service={}", new Object[]{saved.getCaseId(), client.getId(), serviceType});
        return saved;
    }

    private Ticket findActiveTicket(Client client) {
        return this.ticketRepository.findFirstByClientIdAndStatusInOrderByCreatedAtDesc(client.getId(), ACTIVE_TICKET_STATUSES).orElse(null);
    }

    private Client createClient(String phoneNumber) {
        Client client = new Client();
        client.setPhoneNumber(phoneNumber);
        return (Client)this.clientRepository.save(client);
    }

    private ChatSession getOrCreateSession(String phoneNumber) {
        return this.sessionRepository.findById(phoneNumber).orElseGet(() -> {
            ChatSession session = new ChatSession();
            session.setPhoneNumber(phoneNumber);
            session.setCurrentState(ChatState.NEW);
            session.setPreferredLanguage("HINGLISH");
            return (ChatSession)this.sessionRepository.save(session);
        });
    }

    private void appendClientDocument(Ticket ticket, String documentType, String url) {
        String entry = this.nullToDefault(documentType, "Document") + " :: " + url;
        String current = ticket.getClientDocuments();
        ticket.setClientDocuments(this.isBlank(current) ? entry : current + "\n" + entry);
    }

    private void appendVerifiedDocument(ChatSession session, String documentType) {
        String canonical = this.geminiService.canonicalizeDocumentName(documentType);
        LinkedHashSet<String> existing = new LinkedHashSet<String>(this.csvToList(session.getVerifiedDocumentTypes()));
        existing.add(canonical);
        session.setVerifiedDocumentTypes(String.join((CharSequence)",", existing));
    }

    public List<String> missingDocuments(Ticket ticket, ChatSession session) {
        boolean testMode = false;
        List<String> required = testMode ? List.of("PAN Card", "Aadhar Card") : this.geminiService.getRequiredDocuments(ticket.getServiceType());
        List<String> uploaded = this.csvToList(session.getVerifiedDocumentTypes());
        ArrayList<String> missing = new ArrayList<String>();
        for (String requiredDoc : required) {
            String requiredCanonical = this.geminiService.canonicalizeDocumentName(requiredDoc);
            boolean found = uploaded.stream().anyMatch(done -> {
                String doneCanonical = this.geminiService.canonicalizeDocumentName((String)done);
                return this.normalize(doneCanonical).equals(this.normalize(requiredCanonical));
            });
            if (found) continue;
            missing.add(requiredDoc);
        }
        return missing;
    }

    private String requiredDocsText(Ticket ticket, boolean testMode) {
        List<String> docs = testMode ? List.of("PAN Card", "Aadhar Card") : this.geminiService.getRequiredDocuments(ticket.getServiceType());
        return this.bulletList(docs);
    }

    private String documentLinksText(Ticket ticket) {
        if (this.isBlank(ticket.getClientDocuments())) {
            return "No documents saved yet.";
        }
        String[] links = ticket.getClientDocuments().split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        int num = 1;
        for (String link : links) {
            if (this.isBlank(link)) continue;
            String[] parts = link.split(" :: ");
            if (parts.length == 2) {
                builder.append(num++).append(". ").append(parts[0].trim()).append(" :: ").append(this.s3StorageService.getSignedUrl(parts[1].trim())).append("\n");
                continue;
            }
            builder.append(num++).append(". ").append(link.trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private String bulletList(List<String> items) {
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            builder.append("\u2022 ").append(item).append("\n");
        }
        return builder.toString().trim();
    }

    private String formatStaffDocuments(String documentsJson) {
        if (documentsJson == null || documentsJson.isEmpty() || documentsJson.equals("[]")) {
            return "No documents uploaded yet.";
        }
        try {
            StringBuilder sb = new StringBuilder();
            String[] lines = documentsJson.split("\n");
            int i = 1;
            for (String line : lines) {
                String[] parts = line.split(" :: ");
                if (parts.length == 2) {
                    String docTypeRaw = parts[0];
                    String rawUrl = parts[1].trim();
                    String docType = docTypeRaw.split("##")[0];
                    
                    // Convert s3:// to a clickable HTTP presigned URL
                    String clickableUrl = rawUrl.startsWith("s3://") ? this.s3StorageService.getSignedUrl(rawUrl) : rawUrl;
                    
                    sb.append(i++).append(". *").append(docType.trim()).append("*\n   Link: ").append(clickableUrl).append("\n\n");
                } else if (!line.trim().isEmpty()) {
                    sb.append(i++).append(". ").append(line.trim()).append("\n\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return documentsJson;
        }
    }

    private String staffStatusMessage(Ticket ticket) {
        String clientName = ticket.getClient() != null ? this.nullToDefault(ticket.getClient().getName(), ticket.getClient().getPhoneNumber()) : "-";
        return String.format("📋 *CURRENT ASSIGNMENT STATUS*\n\n" +
                "👤 *Client*: %s (%s)\n" +
                "📍 *City*: %s\n" +
                "💼 *Service*: %s\n" +
                "📊 *Status*: %s\n" +
                "⏳ *Progress*: %s%%\n\n" +
                "📄 *Client Documents*:\n%s\n\n" +
                "📝 *Admin Notes*:\n%s\n\n" +
                "🛠 *QUICK COMMANDS*:\n" +
                "• *NEED [doc]* (e.g., NEED PAN Card) to ask client\n" +
                "• *ASK [query]* to ask client a question\n" +
                "• *NOTE [msg]* to save an internal note\n" +
                "• *DONE* when you want to upload final work\n" +
                "• *LIST* to go back to your dashboard", 
                clientName, 
                ticket.getClient() != null ? ticket.getClient().getPhoneNumber() : "-", 
                this.nullToDefault(ticket.getClient() != null ? ticket.getClient().getCity() : null, "N/A"), 
                ticket.getServiceType(), 
                ticket.getStatus(), 
                ticket.getProgressPercent() != null ? ticket.getProgressPercent() : 0, 
                formatStaffDocuments(ticket.getClientDocuments()),
                this.nullToDefault(ticket.getAdminNotes(), "No admin notes"));
    }

    private void forwardClientMessageToStaff(Ticket ticket, Client client, String phoneNumber, String messageContent) {
        String staffPhone;
        String string = staffPhone = ticket.getAssignedStaff() != null ? ticket.getAssignedStaff().getPhoneNumber() : null;
        if (staffPhone == null) {
            this.send(null, null, null);
            this.whatsappMessageSender.sendMessage(phoneNumber, "Aapka message CA team ke paas pahunch gaya. Jald hi reply milega.");
            return;
        }
        String clientDisplay = this.nullToDefault(client.getName(), phoneNumber);
        this.whatsappMessageSender.sendMessage(staffPhone, "\ud83d\udcac Client (" + clientDisplay + ") message:\n" + messageContent);
        this.whatsappMessageSender.sendMessage(phoneNumber, "\u2705 Aapka message CA team ko forward kar diya gaya. Jald hi reply milega.");
    }

    private void send(String phoneNumber, ChatSession session, String message) {
        if (phoneNumber == null || message == null) {
            return;
        }
        this.whatsappMessageSender.sendMessage(phoneNumber, message.strip());
    }

    private void updateLanguagePreference(ChatSession session, String messageContent) {
        if (messageContent == null || session == null) {
            return;
        }
        String msg = messageContent.toLowerCase(Locale.ROOT);
        if ((msg.equals("english") || msg.equals("speak english") || msg.equals("in english")) && !"ENGLISH".equals(session.getPreferredLanguage())) {
            session.setPreferredLanguage("ENGLISH");
            this.sessionRepository.save(session);
        } else if ((msg.equals("hindi") || msg.equals("hinglish")) && !"HINGLISH".equals(session.getPreferredLanguage())) {
            session.setPreferredLanguage("HINGLISH");
            this.sessionRepository.save(session);
        }
    }

    private void resetSession(ChatSession session) {
        session.setCurrentState(ChatState.AI_CONVERSATION);
        session.setExtractedService(null);
        session.setVerifiedDocumentTypes("");
        this.sessionRepository.save(session);
    }

    private boolean isGreeting(String message) {
        if (message == null) {
            return false;
        }
        String msg = message.toLowerCase(Locale.ROOT).trim();
        return msg.equals("hi") || msg.equals("hii") || msg.equals("hello") || msg.equals("hey") || msg.equals("namaste") || msg.equals("helo") || msg.equals("hii!") || msg.equals("hi!") || msg.equals("hello!");
    }

    private int getTestModeIndex(String message) {
        if (message == null) {
            return -1;
        }
        String msg = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (msg.contains("test1") || msg.contains("test 1") || msg.contains("bhanu 1")) {
            return 1;
        }
        if (msg.contains("test2") || msg.contains("test 2") || msg.contains("bhanu 2")) {
            return 2;
        }
        if (msg.contains("test3") || msg.contains("test 3") || msg.contains("bhanu 3")) {
            return 3;
        }
        if (msg.contains("test4") || msg.contains("test 4") || msg.contains("bhanu 4")) {
            return 4;
        }
        if (msg.contains("test5") || msg.contains("test 5") || msg.contains("bhanu 5")) {
            return 5;
        }
        if (msg.contains("my self bhanu") || msg.contains("myself bhanu") || msg.contains("hi bhanu") || msg.contains("hii bhanu") || msg.contains("i am bhanu")) {
            return 1;
        }
        return -1;
    }

    private boolean isValidValue(String value) {
        return !this.isBlank(value) && !"NOT_PROVIDED".equalsIgnoreCase(value.trim()) && !"null".equalsIgnoreCase(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullToDefault(String value, String defaultValue) {
        return this.isBlank(value) ? defaultValue : value;
    }

    private List<String> csvToList(String value) {
        if (this.isBlank(value)) {
            return new ArrayList<String>();
        }
        String[] parts = value.split(",");
        ArrayList<String> result = new ArrayList<String>();
        for (String part : parts) {
            if (this.isBlank(part)) continue;
            result.add(part.trim());
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String appendLog(String current, String entry) {
        if (this.isBlank(entry)) {
            return current;
        }
        String stamped = String.valueOf(LocalDateTime.now()) + " - " + entry.trim();
        return this.isBlank(current) ? stamped : current + "\n" + stamped;
    }

    private boolean looksLikeCredentials(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean hasIdKeyword = lower.contains("id:") || lower.contains("user:") || lower.contains("login:") || lower.contains("username:") || lower.contains("userid:") || lower.contains("user id") || lower.contains("email:") || lower.contains("mobile:") || lower.contains("pan:") || lower.contains("id") && lower.contains("@");
        boolean hasPassKeyword = lower.contains("password:") || lower.contains("pass:") || lower.contains("pwd:") || lower.contains("passcode:") || lower.contains("password ") || lower.contains("pass ") || lower.contains("otp:");
        return hasIdKeyword && hasPassKeyword;
    }
    private String getRandomGitaQuote() {
        String[] quotes = {
            "कर्मण्येवाधिकारस्ते मा फलेषु कदाचन।\n(You have the right to perform your prescribed duty, but you are not entitled to the fruits of action.) - Bhagavad Gita",
            "क्रोधाद्भवति सम्मोहः सम्मोहात्स्मृतिविभ्रमः।\n(Anger leads to clouding of judgment, which results in bewilderment of the memory.) - Bhagavad Gita",
            "यदा यदा हि धर्मस्य ग्लानिर्भवति भारत।\n(Whenever there is a decline in righteousness, O Arjuna, I manifest myself on earth.) - Bhagavad Gita",
            "उद्धरेदात्मनात्मानं नात्मानमवसादयेत्।\n(Elevate yourself through the power of your mind, and not degrade yourself, for the mind can be the friend and also the enemy of the self.) - Bhagavad Gita"
        };
        return quotes[new java.util.Random().nextInt(quotes.length)];
    }
}
