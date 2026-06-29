package com.caCommand.caCommand.services.cache;

import com.caCommand.caCommand.entities.DocumentCache;
import com.caCommand.caCommand.repositories.DocumentCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final DocumentCacheRepository documentCacheRepository;

    // Level 1: Memory Cache
    private final Map<String, DocumentCache> memoryCache = new ConcurrentHashMap<>();

    public Optional<DocumentCache> getDocumentCache(String hash) {
        // Check L1
        DocumentCache l1Cache = memoryCache.get(hash);
        if (l1Cache != null) {
            log.info("L1 Cache Hit for hash {}", hash);
            return Optional.of(l1Cache);
        }

        // Check L3 (DB)
        Optional<DocumentCache> l3Cache = documentCacheRepository.findById(hash);
        if (l3Cache.isPresent()) {
            log.info("L3 Cache Hit for hash {}", hash);
            // Promote to L1
            memoryCache.put(hash, l3Cache.get());
            return l3Cache;
        }

        return Optional.empty();
    }

    public void saveDocumentCache(String hash, String ocrText, String structuredJson, String aiSummary) {
        DocumentCache cache = new DocumentCache();
        cache.setDocumentHash(hash);
        cache.setOcrText(ocrText);
        cache.setStructuredJson(structuredJson);
        cache.setAiSummary(aiSummary);
        cache.setProcessedAt(LocalDateTime.now());

        // Save L3
        documentCacheRepository.save(cache);
        
        // Save L1
        memoryCache.put(hash, cache);
        log.info("Saved Cache L1 & L3 for hash {}", hash);
    }
}
