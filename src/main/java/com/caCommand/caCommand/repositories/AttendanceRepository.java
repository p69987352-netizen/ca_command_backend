package com.caCommand.caCommand.repositories;

import com.caCommand.caCommand.entities.Attendance;
import com.caCommand.caCommand.entities.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findByStaffOrderByAttendanceDateDesc(Staff staff);
    Optional<Attendance> findByStaffAndAttendanceDate(Staff staff, LocalDate attendanceDate);
    List<Attendance> findByStaffAndAttendanceDateBetweenOrderByAttendanceDateDesc(Staff staff, LocalDate startDate, LocalDate endDate);
    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);
    List<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateDesc(LocalDate startDate, LocalDate endDate);
}
