package br.com.ia.processor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IaDuplicateDetector {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();
    private static final int MAX_CACHE = 1000;

    public String hash(Object input) {
        try {
            String s = String.valueOf(input);
            if (s.length() > 100) s = s.substring(0, 100);
            return Integer.toHexString(s.hashCode());
        } catch (Exception e) {
            return "hash-error-" + System.nanoTime();
        }
    }

    public boolean isDuplicate(String h) {
        boolean dup = processed.contains(h);
        if (!dup && processed.size() > MAX_CACHE) {
            processed.clear();
            log.debug("[DEDUP] Cache cleared");
        }
        return dup;
    }

    public void markProcessed(String h) {
        processed.add(h);
        log.debug("[DEDUP] Marked as processed: {}", h);
    }
}
