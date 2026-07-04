package com.caCommand.caCommand.config;

import com.caCommand.caCommand.entities.Client;
import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.entities.Ticket;
import com.caCommand.caCommand.enums.TicketStatus;
import com.caCommand.caCommand.repositories.ClientRepository;
import com.caCommand.caCommand.repositories.StaffRepository;
import com.caCommand.caCommand.repositories.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StaffRepository staffRepository;
    private final ClientRepository clientRepository;
    private final TicketRepository ticketRepository;

    public DataSeeder(StaffRepository staffRepository, ClientRepository clientRepository, TicketRepository ticketRepository) {
        this.staffRepository = staffRepository;
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Temporary fix for already seeded tickets
        ticketRepository.findAll().forEach(t -> {
            if ("NEW".equals(t.getStatus())) {
                t.setStatus(TicketStatus.IN_PROGRESS.name());
                ticketRepository.save(t);
            }
        });

        if (ticketRepository.count() > 0) {
            log.info("Database already seeded. Skipping seeder.");
            return;
        }

//        log.info("Running Database Seeder to add 5 dummy tasks...");
//
//        // Create 1 Staff member if none exist
//        Staff staff = staffRepository.findAll().stream().findFirst().orElseGet(() -> {
//            Staff s = new Staff();
//            s.setName("Demo Staff");
//            s.setPhoneNumber("919999999999");
//            s.setIsActive(true);
//            return staffRepository.save(s);
//        });
//
//        // Create 1 Dummy Client
//        Client client = clientRepository.findByPhoneNumber("918888888888").orElseGet(() -> {
//            Client c = new Client();
//            c.setName("Dummy Client");
//            c.setPhoneNumber("918888888888");
//            return clientRepository.save(c);
//        });
//
//        // Create 5 dummy tasks (Tickets)
//        for (int i = 1; i <= 5; i++) {
//            Ticket t = new Ticket();
//            t.setClient(client);
//            t.setServiceType("ITR Filing");
//            t.setCaseId(String.format("CASE-%04d", 1000 + i));
//            t.setStatus(TicketStatus.IN_PROGRESS.name());
//            ticketRepository.save(t);
//        }
//
//        log.info("Successfully seeded 5 dummy tasks assigned to Staff: " + staff.getName());
    }
}
