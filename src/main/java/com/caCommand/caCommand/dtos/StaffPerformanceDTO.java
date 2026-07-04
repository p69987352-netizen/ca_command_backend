package com.caCommand.caCommand.dtos;

import lombok.Data;

@Data
public class StaffPerformanceDTO {
    private String staffId;
    private String staffName;
    private long totalCompleted;
    private long totalPending;
    private long totalReassigned;
    private long daysPresent;
    private long daysAbsent;
}
