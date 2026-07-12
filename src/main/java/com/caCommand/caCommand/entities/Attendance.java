package com.caCommand.caCommand.entities;

import com.caCommand.caCommand.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendances", indexes = {
        @Index(name = "idx_attendance_date", columnList = "attendanceDate"),
        @Index(name = "idx_attendance_staff", columnList = "staff_id")
})
@Data
@lombok.EqualsAndHashCode(callSuper = false)
public class Attendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status = AttendanceStatus.NOT_MARKED;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "location_link", columnDefinition = "TEXT")
    private String locationLink;

    @Column(name = "exit_photo_url", columnDefinition = "TEXT")
    private String exitPhotoUrl;

    @Column(name = "exit_time")
    private java.time.LocalDateTime exitTime;

    @Column(name = "exit_location_link", columnDefinition = "TEXT")
    private String exitLocationLink;

    @Column(name = "is_verified_entry")
    private Boolean isVerifiedEntry;

    @Column(name = "is_verified_exit")
    private Boolean isVerifiedExit;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "marked_by_admin")
    private Boolean markedByAdmin = false;
    
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public String getCreatedAtIso() {
        if (this.createdAt == null) return null;
        return this.createdAt.atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public String getExitTimeIso() {
        if (this.exitTime == null) return null;
        return this.exitTime.atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
