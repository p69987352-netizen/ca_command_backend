package com.caCommand.caCommand.services;

import com.caCommand.caCommand.entities.Staff;
import com.caCommand.caCommand.exceptions.InvalidTicketStateException;
import com.caCommand.caCommand.repositories.StaffRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffServiceTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final StaffService staffService = new StaffService(staffRepository);

    @Test
    void addStaffMemberTrimsAndPersistsStaff() {
        when(staffRepository.findByPhoneNumber("919999999999")).thenReturn(Optional.empty());
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Staff staff = staffService.addStaffMember("  Riya  ", "919999999999");

        assertThat(staff.getName()).isEqualTo("Riya");
        assertThat(staff.getPhoneNumber()).isEqualTo("919999999999");
        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    void addStaffMemberRejectsDuplicatePhoneNumber() {
        when(staffRepository.findByPhoneNumber("919999999999")).thenReturn(Optional.of(new Staff()));

        assertThatThrownBy(() -> staffService.addStaffMember("Riya", "919999999999"))
                .isInstanceOf(InvalidTicketStateException.class)
                .hasMessageContaining("already exists");

        verify(staffRepository, never()).save(any(Staff.class));
    }
}
