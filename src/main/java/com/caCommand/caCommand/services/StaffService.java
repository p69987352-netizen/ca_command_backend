package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.exceptions.InvalidTicketStateException;
import com.caCommand.caCommand.repositories.StaffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Transactional
    public Staff addStaffMember(String name, String phoneNumber) {
        staffRepository.findByPhoneNumber(phoneNumber).ifPresent(existing -> {
            throw new InvalidTicketStateException("A staff member already exists with this phone number");
        });

        Staff newStaff = new Staff();
        newStaff.setName(name.trim());
        newStaff.setPhoneNumber(phoneNumber.trim());

        Staff savedStaff = staffRepository.save(newStaff);
        log.info("Added staff member id={} phone={}", savedStaff.getId(), savedStaff.getPhoneNumber());
        return savedStaff;
    }

    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }
}
