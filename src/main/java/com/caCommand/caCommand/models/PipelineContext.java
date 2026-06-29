package com.caCommand.caCommand.models;

import com.caCommand.caCommand.enums.PipelineStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
public class PipelineContext {
    private String pipelineExecutionId;
    private String ticketId;
    
    @Builder.Default
    private volatile boolean cancelled = false;
    
    @Builder.Default
    private volatile boolean retry = false;
    
    @Builder.Default
    private volatile boolean hasPendingChanges = false;
    
    private Instant startedAt;
    
    private PipelineStatus currentStage;
    
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    public void cancel() {
        this.cancelled = true;
    }
}
