package com.caCommand.caCommand.services.config;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureFlagService {

    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    public FeatureFlagService() {
        // Defaults
        flags.put("AI_PROVIDER_GEMINI", true);
        flags.put("RULE_ENGINE_LOCAL", true);
        flags.put("CACHE_ENABLED", true);
        flags.put("PRICING_ENGINE_V2", false);
    }

    public boolean isEnabled(String flagName) {
        return flags.getOrDefault(flagName, false);
    }

    public void setFlag(String flagName, boolean value) {
        flags.put(flagName, value);
    }
}
