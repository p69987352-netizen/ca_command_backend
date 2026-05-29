package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.repositories.StaffRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    // 1. Add new staff
    public Staff addStaffMember(String name, String phoneNumber) {
        Staff newStaff = new Staff();
        newStaff.setName(name);
        newStaff.setPhoneNumber(phoneNumber); // WhatsApp Number

        Staff savedStaff = staffRepository.save(newStaff);

        System.out.println("✅ New Staff Added: " + savedStaff.getName() + " (" + savedStaff.getPhoneNumber() + ")");
        return savedStaff;
    }

    // 2. Get all staff (Admin ke dropdown menu ke liye)
    public List<Staff> getAllStaff() {
        return staffRepository.findAll();
    }
}