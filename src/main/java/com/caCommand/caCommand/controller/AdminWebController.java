package com.caCommand.caCommand.controller;

import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.entities.ExtractedData;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import com.caCommand.caCommand.repositories.ExtractedDataRepository;
import com.caCommand.caCommand.services.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminWebController {

    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;
    private final ExtractedDataRepository extractedDataRepository;
    private final PricingService pricingService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Client> clients = clientRepository.findAll();
        List<Ticket> tickets = ticketRepository.findAll();
        
        model.addAttribute("clients", clients);
        model.addAttribute("tickets", tickets);
        model.addAttribute("totalClients", clients.size());
        model.addAttribute("activeTickets", tickets.stream().filter(t -> !"COMPLETED".equals(t.getStatus())).count());
        
        return "dashboard"; // Renders dashboard.html
    }

    @GetMapping("/client/{id}")
    public String clientDetail(@PathVariable UUID id, Model model) {
        Client client = clientRepository.findById(id).orElse(null);
        if (client == null) {
            return "redirect:/admin/dashboard";
        }

        List<Ticket> tickets = ticketRepository.findAllByClientIdOrderByCreatedAtDesc(id);
        Ticket latestTicket = tickets.isEmpty() ? null : tickets.get(0);

        ExtractedData extractedData = extractedDataRepository.findFirstByClientIdOrderByCreatedAtDesc(id).orElse(null);

        // Apply ClearTax pricing dynamically if ticket exists
        if (latestTicket != null) {
            pricingService.applyClearTaxPricing(latestTicket, extractedData);
            ticketRepository.save(latestTicket); // Persist updated pricing
        }

        model.addAttribute("client", client);
        model.addAttribute("ticket", latestTicket);
        model.addAttribute("extractedData", extractedData);
        model.addAttribute("tickets", tickets);

        return "client-detail"; // Renders client-detail.html
    }
}
